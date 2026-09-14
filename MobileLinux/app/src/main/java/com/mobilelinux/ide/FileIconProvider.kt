package com.mobilelinux.ide

import android.content.res.ColorStateList
import android.graphics.BitmapFactory
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.mobilelinux.R
import com.mobilelinux.ide.extension.ExtensionManager
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides authentic, high-quality programming language and file-type icons
 * for the Code IDE file tree, open tabs, and folder pickers. Supports dynamic
 * custom icon themes installed via MobileLinux extensions.
 */
object FileIconProvider {

    data class IconInfo(
        @DrawableRes val iconRes: Int,
        @ColorRes val tintColorRes: Int? = null // null means preserve native multi-color vector branding
    )

    private var cachedThemeId: String? = null
    private var cachedThemeConfig: ThemeConfig? = null

    private data class ThemeConfig(
        val baseDir: File,
        val iconDefs: Map<String, String>,
        val fileExtensions: Map<String, String>,
        val fileNames: Map<String, String>,
        val folderNames: Map<String, String>
    )

    fun getIconForFile(fileName: String, isDirectory: Boolean = false): IconInfo {
        if (isDirectory) {
            return IconInfo(R.drawable.ic_folder, R.color.accent_blue)
        }

        val lowerName = fileName.lowercase()

        // 1. Exact match for common tool / config files
        when (lowerName) {
            "dockerfile", "docker-compose.yml", "docker-compose.yaml", ".dockerignore" ->
                return IconInfo(R.drawable.ic_file_docker)
            ".gitignore", ".gitmodules", ".gitattributes" ->
                return IconInfo(R.drawable.ic_file_git)
            "makefile", "cmakelists.txt" ->
                return IconInfo(R.drawable.ic_file_c)
            "package.json", "package-lock.json" ->
                return IconInfo(R.drawable.ic_file_javascript)
            "tsconfig.json" ->
                return IconInfo(R.drawable.ic_file_typescript)
            "cargo.toml", "cargo.lock" ->
                return IconInfo(R.drawable.ic_file_rust)
        }

        // 2. Extension matching
        val ext = lowerName.substringAfterLast('.', "")
        return when (ext) {
            // Jupyter Notebook
            "ipynb" -> IconInfo(R.drawable.ic_file_jupyter)

            // Python
            "py", "pyw" -> IconInfo(R.drawable.ic_file_python)

            // HTML / Web Markup
            "html", "htm", "xhtml" -> IconInfo(R.drawable.ic_file_html)

            // CSS / Styling
            "css", "scss", "sass", "less" -> IconInfo(R.drawable.ic_file_css)

            // JavaScript
            "js", "mjs", "cjs" -> IconInfo(R.drawable.ic_file_javascript)

            // TypeScript
            "ts", "mts", "cts", "tsx", "jsx" -> IconInfo(R.drawable.ic_file_typescript)

            // C
            "c", "h" -> IconInfo(R.drawable.ic_file_c)

            // C++
            "cpp", "hpp", "cc", "cxx", "c++", "h++", "tpp" -> IconInfo(R.drawable.ic_file_cpp)

            // Java
            "java", "jar", "class" -> IconInfo(R.drawable.ic_file_java)

            // Kotlin
            "kt", "kts" -> IconInfo(R.drawable.ic_file_kotlin)

            // Rust
            "rs" -> IconInfo(R.drawable.ic_file_rust)

            // Go
            "go" -> IconInfo(R.drawable.ic_file_go)

            // Shell / Script
            "sh", "bash", "zsh", "fish" -> IconInfo(R.drawable.ic_file_sh)

            // Structured Data & Config
            "json" -> IconInfo(R.drawable.ic_file_json)
            "yaml", "yml" -> IconInfo(R.drawable.ic_file_yaml)
            "xml" -> IconInfo(R.drawable.ic_file_xml)
            "sql", "db", "sqlite" -> IconInfo(R.drawable.ic_file_sql)

            // Documentation / Notes
            "md", "markdown" -> IconInfo(R.drawable.ic_file_markdown)

            // Plain Text & Logs
            "txt", "log", "conf", "cfg", "ini", "env" -> IconInfo(R.drawable.ic_file_text)

            // Compressed Archives
            "zip", "tar", "gz", "7z", "rar", "bz2", "xz", "mle" -> IconInfo(R.drawable.ic_file_archive)

            // Images
            "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "bmp" -> IconInfo(R.drawable.ic_file_image)

            // Media
            "mp3", "wav", "ogg", "flac", "mp4", "mkv", "webm", "avi" -> IconInfo(R.drawable.ic_file_media)

            // Fallback
            else -> IconInfo(R.drawable.ic_file, R.color.text_secondary)
        }
    }

    private fun getActiveThemeConfig(context: android.content.Context): ThemeConfig? {
        val extManager = ExtensionManager.getInstance(context)
        val activeExt = extManager.getActiveIconTheme() ?: run {
            cachedThemeId = null
            cachedThemeConfig = null
            return null
        }

        if (cachedThemeId == activeExt.id && cachedThemeConfig != null) {
            return cachedThemeConfig
        }

        val configFile = activeExt.getIconThemeConfigFile() ?: return null
        return try {
            val json = JSONObject(configFile.readText(Charsets.UTF_8))
            val baseDir = configFile.parentFile ?: activeExt.installDir

            val iconDefs = mutableMapOf<String, String>()
            json.optJSONObject("iconDefinitions")?.let { defs ->
                val keys = defs.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val iconPath = defs.optJSONObject(k)?.optString("iconPath", "") ?: defs.optString(k, "")
                    if (iconPath.isNotBlank()) iconDefs[k] = iconPath
                }
            }

            val fileExts = mutableMapOf<String, String>()
            json.optJSONObject("fileExtensions")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next().lowercase()
                    fileExts[k] = obj.getString(k)
                }
            }

            val fileNames = mutableMapOf<String, String>()
            json.optJSONObject("fileNames")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next().lowercase()
                    fileNames[k] = obj.getString(k)
                }
            }

            val folderNames = mutableMapOf<String, String>()
            json.optJSONObject("folderNames")?.let { obj ->
                val keys = obj.keys()
                while (keys.hasNext()) {
                    val k = keys.next().lowercase()
                    folderNames[k] = obj.getString(k)
                }
            }

            ThemeConfig(baseDir, iconDefs, fileExts, fileNames, folderNames).also {
                cachedThemeId = activeExt.id
                cachedThemeConfig = it
            }
        } catch (e: Exception) {
            null
        }
    }

    private val bitmapLruCache = object : android.util.LruCache<String, android.graphics.Bitmap>(150) {}

    /**
     * Applies the authentic file icon to an ImageView, checking active icon theme first,
     * and properly resetting or applying tint.
     */
    fun applyToFileImageView(imageView: ImageView, fileName: String, isDirectory: Boolean = false) {
        val theme = getActiveThemeConfig(imageView.context)
        if (theme != null) {
            val lowerName = fileName.lowercase()
            val iconDefKey = if (isDirectory) {
                theme.folderNames[lowerName] ?: theme.iconDefs["folder"]?.let { "folder" }
            } else {
                theme.fileNames[lowerName] ?: theme.fileExtensions[lowerName.substringAfterLast('.', "")]
            }

            if (iconDefKey != null) {
                val relPath = theme.iconDefs[iconDefKey] ?: iconDefKey
                val iconFile = File(theme.baseDir, relPath)
                if (iconFile.exists()) {
                    val cached = bitmapLruCache.get(iconFile.absolutePath)
                    if (cached != null) {
                        imageView.setImageBitmap(cached)
                        imageView.imageTintList = null
                        imageView.clearColorFilter()
                        return
                    }
                    val bitmap = BitmapFactory.decodeFile(iconFile.absolutePath)
                    if (bitmap != null) {
                        bitmapLruCache.put(iconFile.absolutePath, bitmap)
                        imageView.setImageBitmap(bitmap)
                        imageView.imageTintList = null
                        imageView.clearColorFilter()
                        return
                    }
                }
            }
        }

        val info = getIconForFile(fileName, isDirectory)
        imageView.setImageResource(info.iconRes)
        if (info.tintColorRes != null) {
            val color = ContextCompat.getColor(imageView.context, info.tintColorRes)
            imageView.imageTintList = ColorStateList.valueOf(color)
        } else {
            imageView.imageTintList = null
            imageView.clearColorFilter()
        }
    }
}
