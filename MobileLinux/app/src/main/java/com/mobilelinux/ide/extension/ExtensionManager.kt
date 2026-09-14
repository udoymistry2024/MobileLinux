package com.mobilelinux.ide.extension

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

/**
 * Manages the lifecycle, installation, persistence, and state of Code IDE extensions.
 */
class ExtensionManager(private val context: Context) {

    companion object {
        private const val TAG = "ExtensionManager"
        private const val PREFS_NAME = "ide_extensions_prefs"
        private const val EXTENSIONS_DIR_NAME = "ide_extensions"

        @Volatile
        private var instance: ExtensionManager? = null

        fun getInstance(context: Context): ExtensionManager {
            return instance ?: synchronized(this) {
                instance ?: ExtensionManager(context.applicationContext).also { instance = it }
            }
        }
    }

    val extensionsDir: File by lazy {
        val dir = File(context.filesDir, EXTENSIONS_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Scans and returns all currently installed extensions.
     */
    fun getInstalledExtensions(): List<InstalledExtension> {
        val list = mutableListOf<InstalledExtension>()
        val subdirs = extensionsDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()

        for (dir in subdirs) {
            val manifestFile = File(dir, "manifest.json")
            if (manifestFile.exists() && manifestFile.isFile) {
                try {
                    val content = manifestFile.readText(Charsets.UTF_8)
                    val manifest = ExtensionManifest.fromJson(content)
                    val isEnabled = prefs.getBoolean("enabled_${manifest.id}", true)
                    val timestamp = manifestFile.lastModified()
                    list.add(InstalledExtension(manifest, dir, isEnabled, timestamp))
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load extension from ${dir.name}", e)
                }
            }
        }

        return list.sortedBy { it.name.lowercase() }
    }

    /**
     * Returns only currently enabled extensions.
     */
    fun getEnabledExtensions(): List<InstalledExtension> {
        return getInstalledExtensions().filter { it.isEnabled }
    }

    /**
     * Toggles an extension's active state.
     */
    fun setExtensionEnabled(extensionId: String, enabled: Boolean) {
        prefs.edit().putBoolean("enabled_$extensionId", enabled).apply()
    }

    /**
     * Deletes an installed extension completely from disk and preferences.
     */
    fun deleteExtension(extensionId: String): Boolean {
        val ext = getInstalledExtensions().find { it.id == extensionId } ?: return false
        val deleted = ext.installDir.deleteRecursively()
        prefs.edit().remove("enabled_$extensionId").apply()
        return deleted
    }

    /**
     * Imports an extension from a .mle or .zip file Uri (picked from file picker or storage).
     *
     * @throws IllegalArgumentException on invalid package or missing manifest.json
     */
    fun importExtensionFromUri(uri: Uri): InstalledExtension {
        val stream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Could not open selected file stream")
        return importExtensionFromStream(stream)
    }

    /**
     * Imports an extension from an input stream.
     */
    fun importExtensionFromStream(inputStream: InputStream): InstalledExtension {
        val tempDir = File(context.cacheDir, "ext_extract_${System.currentTimeMillis()}")
        if (!tempDir.exists()) tempDir.mkdirs()

        try {
            unzip(inputStream, tempDir)

            // Locate manifest.json (either directly in root or in a single subfolder)
            var manifestFile = File(tempDir, "manifest.json")
            var baseDir = tempDir

            if (!manifestFile.exists()) {
                val subdirs = tempDir.listFiles()?.filter { it.isDirectory } ?: emptyList()
                if (subdirs.size == 1 && File(subdirs[0], "manifest.json").exists()) {
                    baseDir = subdirs[0]
                    manifestFile = File(baseDir, "manifest.json")
                }
            }

            if (!manifestFile.exists()) {
                throw IllegalArgumentException("Invalid extension package: manifest.json not found in archive.")
            }

            val manifestContent = manifestFile.readText(Charsets.UTF_8)
            val manifest = ExtensionManifest.fromJson(manifestContent)

            if (manifest.id.isBlank()) {
                throw IllegalArgumentException("Invalid extension: 'id' field is required in manifest.json.")
            }

            val mainFile = File(baseDir, manifest.main)
            if (!mainFile.exists()) {
                throw IllegalArgumentException("Invalid extension: Entry point '${manifest.main}' not found in archive.")
            }

            // Target installation directory: sanitized extension id
            val sanitizedFolderName = manifest.id.replace(Regex("[^a-zA-Z0-9._-]"), "_")
            val targetInstallDir = File(extensionsDir, sanitizedFolderName)

            if (targetInstallDir.exists()) {
                targetInstallDir.deleteRecursively()
            }
            targetInstallDir.mkdirs()

            // Copy all files from baseDir to targetInstallDir
            baseDir.copyRecursively(targetInstallDir, overwrite = true)

            // Enable by default
            setExtensionEnabled(manifest.id, true)

            return InstalledExtension(manifest, targetInstallDir, isEnabled = true)
        } finally {
            try {
                tempDir.deleteRecursively()
            } catch (ignored: Exception) {}
        }
    }

    /**
     * Unzips an archive into a destination directory.
     */
    private fun unzip(inputStream: InputStream, destDir: File) {
        val zipStream = ZipInputStream(BufferedInputStream(inputStream))
        var entry: ZipEntry? = zipStream.nextEntry

        val destCanonicalPath = destDir.canonicalPath

        while (entry != null) {
            val file = File(destDir, entry.name)
            // Prevent zip slip vulnerability
            if (!file.canonicalPath.startsWith(destCanonicalPath)) {
                throw SecurityException("Zip entry is attempting directory traversal: ${entry.name}")
            }

            if (entry.isDirectory) {
                file.mkdirs()
            } else {
                file.parentFile?.mkdirs()
                BufferedOutputStream(FileOutputStream(file)).use { out ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (zipStream.read(buffer).also { read = it } != -1) {
                        out.write(buffer, 0, read)
                    }
                }
            }
            zipStream.closeEntry()
            entry = zipStream.nextEntry
        }
        zipStream.close()
    }

    fun getActiveIconTheme(): InstalledExtension? {
        val activeThemeId = prefs.getString("active_icon_theme_id", null)
        val enabled = getEnabledExtensions()
        if (activeThemeId != null) {
            enabled.find { it.id == activeThemeId && it.contributes.iconThemes.isNotEmpty() }?.let { return it }
        }
        return enabled.firstOrNull { it.contributes.iconThemes.isNotEmpty() }
    }

    fun setActiveIconTheme(themeId: String?) {
        if (themeId == null) {
            prefs.edit().remove("active_icon_theme_id").apply()
        } else {
            prefs.edit().putString("active_icon_theme_id", themeId).apply()
        }
    }
}
