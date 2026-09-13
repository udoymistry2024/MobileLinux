package com.mobilelinux.ide.extension

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Extension manifest metadata parsed from manifest.json inside the .mle / .zip package.
 */
data class ExtensionManifest(
    val id: String,
    val name: String,
    val version: String,
    val description: String = "",
    val author: String = "",
    val icon: String? = null,
    val main: String = "main.js",
    val styles: String? = null,
    val minIdeVersion: String? = null,
    val permissions: List<String> = emptyList()
) {
    companion object {
        fun fromJson(jsonStr: String): ExtensionManifest {
            val obj = JSONObject(jsonStr)
            val id = obj.getString("id").trim()
            val name = obj.optString("name", id).trim()
            val version = obj.optString("version", "1.0.0").trim()
            val description = obj.optString("description", "").trim()
            val author = obj.optString("author", "").trim()
            val icon = obj.optString("icon", "").takeIf { it.isNotEmpty() }
            val main = obj.optString("main", "main.js").trim()
            val styles = obj.optString("styles", "").takeIf { it.isNotEmpty() }
            val minIdeVersion = obj.optString("minIdeVersion", "").takeIf { it.isNotEmpty() }

            val permissions = mutableListOf<String>()
            val permArray = obj.optJSONArray("permissions")
            if (permArray != null) {
                for (i in 0 until permArray.length()) {
                    permissions.add(permArray.getString(i))
                }
            }

            return ExtensionManifest(
                id = id,
                name = name,
                version = version,
                description = description,
                author = author,
                icon = icon,
                main = main,
                styles = styles,
                minIdeVersion = minIdeVersion,
                permissions = permissions
            )
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("name", name)
        obj.put("version", version)
        obj.put("description", description)
        obj.put("author", author)
        if (icon != null) obj.put("icon", icon)
        obj.put("main", main)
        if (styles != null) obj.put("styles", styles)
        if (minIdeVersion != null) obj.put("minIdeVersion", minIdeVersion)
        val permArray = JSONArray()
        permissions.forEach { permArray.put(it) }
        obj.put("permissions", permArray)
        return obj
    }
}

/**
 * Representation of an installed extension on disk.
 */
data class InstalledExtension(
    val manifest: ExtensionManifest,
    val installDir: File,
    var isEnabled: Boolean = true,
    val installTimestamp: Long = System.currentTimeMillis()
) {
    val id: String get() = manifest.id
    val name: String get() = manifest.name
    val version: String get() = manifest.version
    val description: String get() = manifest.description
    val author: String get() = manifest.author

    val iconFile: File?
        get() = manifest.icon?.let { File(installDir, it).takeIf { f -> f.exists() } }

    val mainScriptFile: File
        get() = File(installDir, manifest.main)

    val stylesFile: File?
        get() = manifest.styles?.let { File(installDir, it).takeIf { f -> f.exists() } }

    val readmeFile: File?
        get() {
            val names = listOf("README.md", "readme.md", "README.txt", "readme.txt")
            return names.map { File(installDir, it) }.firstOrNull { it.exists() }
        }
}
