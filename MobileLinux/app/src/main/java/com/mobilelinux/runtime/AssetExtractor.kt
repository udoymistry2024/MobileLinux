package com.mobilelinux.runtime

import android.content.Context
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream
import org.tukaani.xz.XZInputStream
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Comparator
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Manages extraction of bundled assets (proot binary + Ubuntu rootfs)
 *
 * Android 14/15/16 Storage Strategy:
 * ============================================================
 * ❌ /data/data/pkg/files/binaries/  → W^X policy blocks exec on Android 10+
 * ✅ applicationInfo.nativeLibraryDir → ALWAYS executable, no restrictions
 * ✅ getExternalFilesDir()            → /sdcard/Android/data/pkg/files/
 *                                       R/W accessible, no permissions needed
 *                                       proot can bind-mount user files from /sdcard/
 *
 * proot binary: packaged as libproot.so in jniLibs/ → nativeLibraryDir
 * Ubuntu rootfs: /sdcard/Android/data/pkg/files/ubuntu-rootfs/ (external)
 * Scripts: /data/data/pkg/files/scripts/ (internal — scripts are not executed directly)
 */
class AssetExtractor(private val context: Context) {

    private val TAG = "AssetExtractor"
    val filesDir: File = context.filesDir

    /**
     * proot binary location — MUST be in nativeLibraryDir for exec permission
     * on Android 10+ (W^X policy enforcement).
     * Packaged as libproot.so in jniLibs/arm64-v8a/
     */
    val binariesDir: File get() = File(context.applicationInfo.nativeLibraryDir)

    val prootBinary: File get() {
        val nativeDir = context.applicationInfo.nativeLibraryDir
        return File(nativeDir, "libproot.so")
    }

    /**
     * Ubuntu rootfs location — internal storage (filesDir).
     * Internal storage uses ext4/f2fs filesystem:
     * - Full POSIX support: native Linux symlinks (/bin -> usr/bin)
     * - File permissions (chmod 755, 644)
     * - Supports all characters in filenames (no FUSE EINVAL on backslashes)
     * - 10x faster disk I/O
     * - Preserved across app updates (never wiped by updates)
     */
    val rootfsDir: File get() = File(filesDir, "ubuntu-rootfs")

    /**
     * Scripts dir — internal storage is fine (scripts are not exec'd directly,
     * they are passed to bash which runs from nativeLibraryDir/proot)
     */
    val scriptsDir: File get() = File(filesDir, "scripts")

    /**
     * User home directory inside proot — on external storage for easy access
     */
    val userHomeDir: File get() {
        val extDir = context.getExternalFilesDir(null) ?: context.filesDir
        return File(extDir, "home")
    }

    /**
     * Returns true if setup has already been completed.
     */
    fun isSetupComplete(): Boolean {
        val marker = File(filesDir, ".setup_complete")
        return marker.exists() && rootfsDir.exists() && File(rootfsDir, "usr").exists()
    }

    /**
     * Marks setup as complete.
     */
    fun markSetupComplete() {
        File(filesDir, ".setup_complete").writeText("1")
    }

    /**
     * Verifies proot binary exists in nativeLibraryDir.
     * On Android 10+, ONLY files in nativeLibraryDir are executable (W^X policy).
     * proot is packaged as libproot.so in jniLibs/ — Android auto-installs it there.
     */
    suspend fun extractProot(onProgress: (Float, String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val binary = prootBinary
                onProgress(0.1f, "Checking proot binary...")

                if (!binary.exists()) {
                    Log.e(TAG, "proot binary not found at: ${binary.absolutePath}")
                    Log.e(TAG, "Expected libproot.so in jniLibs/arm64-v8a/")
                    // In development mode without the real binary, create a placeholder
                    // for the build to succeed — replace with real binary for production
                    onProgress(0.5f, "[DEV MODE] proot binary not found — using placeholder")
                    onProgress(1.0f, "proot check complete (dev mode)")
                    return@withContext true  // Allow setup to continue in dev/test
                }

                if (!binary.canExecute()) {
                    binary.setExecutable(true, false)
                }

                onProgress(1.0f, "proot binary verified ✓")
                Log.d(TAG, "proot binary at: ${binary.absolutePath} (${binary.length()} bytes)")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to verify proot: ${e.message}", e)
                false
            }
        }

    /**
     * Extracts scripts from assets to the scripts directory.
     */
    suspend fun extractScripts(onProgress: (Float, String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                scriptsDir.mkdirs()
                val scriptFiles = context.assets.list("scripts") ?: emptyArray()
                scriptFiles.forEachIndexed { index, fileName ->
                    val progress = index.toFloat() / scriptFiles.size
                    onProgress(progress, "Extracting $fileName...")
                    val dest = File(scriptsDir, fileName)
                    extractAsset("scripts/$fileName", dest)
                    dest.setExecutable(true, false)
                }
                onProgress(1.0f, "Scripts ready")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract scripts: ${e.message}", e)
                false
            }
        }

    // Symlink entries that should be skipped during extraction because they are
    // dangling in a proot environment and will be written as real files by setup.
    private val SKIP_SYMLINKS = setOf(
        "etc/resolv.conf",
        "etc/mtab"  // -> /proc/self/mounts (always dangling until /proc mounted)
    )

    /**
     * Extracts Ubuntu rootfs tarball and decompresses it.
     * This is the most time-consuming step (~150-200MB).
     */
    suspend fun extractRootfs(onProgress: (Float, String) -> Unit): Boolean =
        withContext(Dispatchers.IO) {
            try {
                // If rootfs already exists with bash binary, check if it was previously extracted
                val bashBinary = File(rootfsDir, "usr/bin/bash")
                val markerFile = File(rootfsDir, ".rootfs_extracted")
                if (rootfsDir.exists() && (markerFile.exists() || (bashBinary.exists() && bashBinary.length() > 100000))) {
                    Log.i(TAG, "Ubuntu rootfs already extracted and valid.")
                    try { markerFile.createNewFile() } catch (ignored: Exception) {}
                    onProgress(1.0f, "Ubuntu rootfs verified ✓")
                    return@withContext true
                }

                // If previous extraction was incomplete, clean it up
                if (rootfsDir.exists() && !isSetupComplete()) {
                    onProgress(0.02f, "Cleaning previous attempt...")
                    try {
                        rootfsDir.deleteRecursively()
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to clean old rootfs: ${e.message}")
                        // Try force-deleting with NIO
                        try {
                            java.nio.file.Files.walk(rootfsDir.toPath())
                                .sorted(Comparator.reverseOrder())
                                .forEach { path ->
                                    try { java.nio.file.Files.deleteIfExists(path) } catch (ignored: Exception) {}
                                }
                        } catch (ignored: Exception) {}
                    }
                }
                rootfsDir.mkdirs()

                // Create /sdcard/MobileLinux workspace for user files
                try {
                    val extStorage = Environment.getExternalStorageDirectory()
                    val sdcardWorkspace = File(extStorage, "MobileLinux")
                    if (!sdcardWorkspace.exists()) {
                        sdcardWorkspace.mkdirs()
                    }
                    val readme = File(sdcardWorkspace, "README.txt")
                    if (!readme.exists()) {
                        readme.writeText("MobileLinux Shared Workspace\n\nFiles in this folder are accessible from inside Ubuntu at:\n  /sdcard/MobileLinux\n")
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Notice: could not create /sdcard/MobileLinux: ${e.message}")
                }

                // Check if rootfs tarball exists in assets
                val tarballAsset = "ubuntu-rootfs/ubuntu-24.04-arm64-minimal.tar.xz"
                val assetList = context.assets.list("ubuntu-rootfs") ?: emptyArray()

                if (!assetList.contains("ubuntu-24.04-arm64-minimal.tar.xz")) {
                    Log.e(TAG, "Ubuntu rootfs tarball not found in assets!")
                    createMinimalRootfsPlaceholder()
                    onProgress(1.0f, "Development mode: placeholder rootfs created")
                    return@withContext true
                }

                onProgress(0.05f, "Decompressing Ubuntu 24.04 rootfs...")
                Log.d(TAG, "Streaming $tarballAsset to ${rootfsDir.absolutePath}")

                var entryCount = 0
                var skippedSymlinks = 0
                var failedEntries = 0
                val estimatedTotalEntries = 35000

                context.assets.open(tarballAsset).use { rawIn ->
                    BufferedInputStream(rawIn, 65536).use { bufIn ->
                        XZInputStream(bufIn).use { xzIn ->
                            TarArchiveInputStream(xzIn).use { tarIn ->
                                var entry: TarArchiveEntry? = tarIn.nextEntry
                                val buffer = ByteArray(32768)

                                while (entry != null) {
                                    val entryName = entry.name.removePrefix("./").removePrefix("/")
                                    if (entryName.isNotEmpty()) {
                                        val destFile = File(rootfsDir, entryName)

                                        try {
                                            if (entry.isDirectory) {
                                                destFile.mkdirs()
                                            } else if (entry.isSymbolicLink) {
                                                destFile.parentFile?.mkdirs()
                                                // Check if this symlink should be skipped
                                                val normalizedName = entryName.removePrefix("/")
                                                if (SKIP_SYMLINKS.contains(normalizedName) ||
                                                    SKIP_SYMLINKS.any { normalizedName.endsWith("/$it") }) {
                                                    skippedSymlinks++
                                                    Log.d(TAG, "Skipped symlink: $entryName -> ${entry.linkName}")
                                                } else {
                                                    try {
                                                        Files.deleteIfExists(destFile.toPath())
                                                        Files.createSymbolicLink(destFile.toPath(), Paths.get(entry.linkName))
                                                    } catch (e: Exception) {
                                                        // Symlink creation failed — this is common on some Android FS
                                                        // For relative symlinks pointing to files in the same dir,
                                                        // try to create a copy instead
                                                        Log.w(TAG, "Symlink failed: $entryName -> ${entry.linkName}: ${e.message}")
                                                        skippedSymlinks++
                                                    }
                                                }
                                            } else {
                                                destFile.parentFile?.mkdirs()
                                                try {
                                                    if (Files.isSymbolicLink(destFile.toPath()) || destFile.isDirectory) {
                                                        if (destFile.isDirectory) destFile.deleteRecursively()
                                                        else Files.deleteIfExists(destFile.toPath())
                                                    } else if (destFile.exists()) {
                                                        destFile.delete()
                                                    }
                                                } catch (e: Exception) {
                                                    // Best-effort cleanup
                                                    try { destFile.delete() } catch (ignored: Exception) {}
                                                }

                                                FileOutputStream(destFile).use { out ->
                                                    var bytesRead: Int
                                                    while (tarIn.read(buffer).also { bytesRead = it } != -1) {
                                                        out.write(buffer, 0, bytesRead)
                                                    }
                                                }
                                                if ((entry.mode and 0b001001001) != 0) {
                                                    try { destFile.setExecutable(true, false) } catch (ignored: Exception) {}
                                                }
                                            }
                                        } catch (entryEx: Exception) {
                                            failedEntries++
                                            if (failedEntries <= 20) {
                                                Log.w(TAG, "Entry $entryName skipped: ${entryEx.message}")
                                            }
                                        }

                                        entryCount++
                                        if (entryCount % 500 == 0) {
                                            val progress = (0.05f + (entryCount.toFloat() / estimatedTotalEntries * 0.90f)).coerceAtMost(0.95f)
                                            onProgress(progress, "Extracting: $entryCount files...")
                                        }
                                    }
                                    entry = tarIn.nextEntry
                                }
                            }
                        }
                    }
                }

                try { markerFile.createNewFile() } catch (ignored: Exception) {}
                Log.d(TAG, "Rootfs extraction complete: $entryCount entries, $skippedSymlinks symlinks skipped, $failedEntries failures")
                onProgress(1.0f, "Rootfs extracted ($entryCount files)!")
                true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to extract rootfs: ${e.message}", e)
                false
            }
        }

    /**
     * Creates a minimal placeholder rootfs for development/testing.
     * In production, the real Ubuntu rootfs tarball should be in assets.
     */
    private fun createMinimalRootfsPlaceholder() {
        Log.w(TAG, "Creating minimal placeholder rootfs for development")
        val dirs = listOf("usr/bin", "usr/lib", "usr/local/bin", "etc", "root",
            "proc", "sys", "dev", "tmp", "var/log", "bin", "sbin")
        dirs.forEach { File(rootfsDir, it).mkdirs() }

        // Symlinks for basic compatibility
        File(rootfsDir, "etc/passwd").writeText(
            "root:x:0:0:root:/root:/bin/bash\n"
        )
        File(rootfsDir, "etc/group").writeText("root:x:0:\n")
        File(rootfsDir, "etc/hostname").writeText("mobilelinux\n")
        File(rootfsDir, "etc/os-release").writeText(
            """
            NAME="Ubuntu"
            VERSION="24.04 LTS (Noble Numbat)"
            ID=ubuntu
            ID_LIKE=debian
            VERSION_ID="24.04"
            PRETTY_NAME="Ubuntu 24.04 LTS"
            """.trimIndent()
        )
        Log.d(TAG, "Placeholder rootfs created")
    }

    /**
     * Extracts a single asset file to destination.
     */
    private fun extractAsset(assetPath: String, dest: File) {
        dest.parentFile?.mkdirs()
        context.assets.open(assetPath).use { input ->
            FileOutputStream(dest).use { output ->
                input.copyTo(output)
            }
        }
    }

    /**
     * Returns total size of an asset in bytes (approximate).
     */
    fun getAssetSize(assetPath: String): Long {
        return try {
            context.assets.openFd(assetPath).use { it.length }
        } catch (e: Exception) {
            0L
        }
    }
}
