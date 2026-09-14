package com.mobilelinux.ide

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.annotation.ColorRes
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import com.mobilelinux.R

/**
 * Provides authentic, high-quality programming language and file-type icons
 * for the Code IDE file tree, open tabs, and folder pickers.
 */
object FileIconProvider {

    data class IconInfo(
        @DrawableRes val iconRes: Int,
        @ColorRes val tintColorRes: Int? = null // null means preserve native multi-color vector branding
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
            // Python
            "py", "pyw", "ipynb" -> IconInfo(R.drawable.ic_file_python)

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

    /**
     * Applies the authentic file icon to an ImageView, properly resetting or applying tint.
     */
    fun applyToFileImageView(imageView: ImageView, fileName: String, isDirectory: Boolean = false) {
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
