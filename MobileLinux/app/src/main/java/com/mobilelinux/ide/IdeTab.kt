package com.mobilelinux.ide

import java.io.File

/**
 * Represents an open tab in the Code IDE.
 */
data class IdeTab(
    val file: File,
    var title: String = file.name,
    var isDirty: Boolean = false,
    var mode: String = detectMode(file.name),
    var scrollLine: Int = 0,
    var cursorRow: Int = 1,
    var cursorCol: Int = 1
) {
    companion object {
        fun detectMode(fileName: String): String {
            val ext = fileName.substringAfterLast('.', "").lowercase()
            return when (ext) {
                "py" -> "python"
                "js", "mjs", "cjs" -> "javascript"
                "ts" -> "typescript"
                "c", "h" -> "c_cpp"
                "cpp", "hpp", "cc", "cxx" -> "c_cpp"
                "sh", "bash", "zsh" -> "sh"
                "html", "htm" -> "html"
                "css" -> "css"
                "json" -> "json"
                "md", "markdown" -> "markdown"
                "yaml", "yml" -> "yaml"
                "rs" -> "rust"
                "go" -> "golang"
                "php" -> "php"
                "sql" -> "sql"
                else -> "text"
            }
        }
    }
}
