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
    val permissions: List<String> = emptyList(),
    val contributes: ExtensionContributions = ExtensionContributions()
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

            val contributes = ExtensionContributions.fromJson(obj.optJSONObject("contributes"))

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
                permissions = permissions,
                contributes = contributes
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
        obj.put("contributes", contributes.toJson())
        return obj
    }
}

/**
 * Contributions declared by an extension in manifest.json.
 */
data class ExtensionContributions(
    val customEditors: List<CustomEditorContribution> = emptyList(),
    val iconThemes: List<IconThemeContribution> = emptyList(),
    val themes: List<EditorThemeContribution> = emptyList(),
    val commands: List<CommandContribution> = emptyList()
) {
    companion object {
        fun fromJson(obj: JSONObject?): ExtensionContributions {
            if (obj == null) return ExtensionContributions()

            val customEditors = mutableListOf<CustomEditorContribution>()
            obj.optJSONArray("customEditors")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { ce ->
                        val id = ce.optString("id", "")
                        val displayName = ce.optString("displayName", id)
                        val patterns = mutableListOf<String>()
                        ce.optJSONArray("selector")?.let { selArr ->
                            for (j in 0 until selArr.length()) {
                                selArr.optJSONObject(j)?.optString("filenamePattern")?.let { pat ->
                                    if (pat.isNotBlank()) patterns.add(pat)
                                }
                            }
                        }
                        if (patterns.isEmpty()) {
                            ce.optJSONArray("filenamePatterns")?.let { fpArr ->
                                for (j in 0 until fpArr.length()) {
                                    patterns.add(fpArr.getString(j))
                                }
                            }
                        }
                        val priority = ce.optString("priority", "default")
                        if (id.isNotBlank()) {
                            customEditors.add(CustomEditorContribution(id, displayName, patterns, priority))
                        }
                    }
                }
            }

            val iconThemes = mutableListOf<IconThemeContribution>()
            obj.optJSONArray("iconThemes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { itObj ->
                        val id = itObj.optString("id", "")
                        val label = itObj.optString("label", id)
                        val path = itObj.optString("path", "")
                        if (id.isNotBlank() && path.isNotBlank()) {
                            iconThemes.add(IconThemeContribution(id, label, path))
                        }
                    }
                }
            }

            val themes = mutableListOf<EditorThemeContribution>()
            obj.optJSONArray("themes")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { tObj ->
                        val id = tObj.optString("id", "")
                        val label = tObj.optString("label", id)
                        val uiTheme = tObj.optString("uiTheme", "vs-dark")
                        val path = tObj.optString("path", "")
                        if (id.isNotBlank() && path.isNotBlank()) {
                            themes.add(EditorThemeContribution(id, label, uiTheme, path))
                        }
                    }
                }
            }

            val commands = mutableListOf<CommandContribution>()
            obj.optJSONArray("commands")?.let { arr ->
                for (i in 0 until arr.length()) {
                    arr.optJSONObject(i)?.let { cmdObj ->
                        val id = cmdObj.optString("id", "")
                        val title = cmdObj.optString("title", id)
                        val icon = cmdObj.optString("icon", "").takeIf { it.isNotBlank() }
                        if (id.isNotBlank()) {
                            commands.add(CommandContribution(id, title, icon))
                        }
                    }
                }
            }

            return ExtensionContributions(
                customEditors = customEditors,
                iconThemes = iconThemes,
                themes = themes,
                commands = commands
            )
        }
    }

    fun toJson(): JSONObject {
        val obj = JSONObject()
        val ceArr = JSONArray()
        customEditors.forEach { ce ->
            val ceObj = JSONObject()
            ceObj.put("id", ce.id)
            ceObj.put("displayName", ce.displayName)
            val selArr = JSONArray()
            ce.filenamePatterns.forEach { p ->
                val sObj = JSONObject()
                sObj.put("filenamePattern", p)
                selArr.put(sObj)
            }
            ceObj.put("selector", selArr)
            ceObj.put("priority", ce.priority)
            ceArr.put(ceObj)
        }
        obj.put("customEditors", ceArr)

        val itArr = JSONArray()
        iconThemes.forEach { it ->
            val itObj = JSONObject()
            itObj.put("id", it.id)
            itObj.put("label", it.label)
            itObj.put("path", it.path)
            itArr.put(itObj)
        }
        obj.put("iconThemes", itArr)

        val thArr = JSONArray()
        themes.forEach { t ->
            val tObj = JSONObject()
            tObj.put("id", t.id)
            tObj.put("label", t.label)
            tObj.put("uiTheme", t.uiTheme)
            tObj.put("path", t.path)
            thArr.put(tObj)
        }
        obj.put("themes", thArr)

        val cmdArr = JSONArray()
        commands.forEach { c ->
            val cObj = JSONObject()
            cObj.put("id", c.id)
            cObj.put("title", c.title)
            if (c.icon != null) cObj.put("icon", c.icon)
            cmdArr.put(cObj)
        }
        obj.put("commands", cmdArr)

        return obj
    }
}

data class CustomEditorContribution(
    val id: String,
    val displayName: String,
    val filenamePatterns: List<String>,
    val priority: String = "default"
) {
    fun matches(filename: String): Boolean {
        val lowerName = filename.lowercase()
        for (pattern in filenamePatterns) {
            val cleanPat = pattern.trim().lowercase()
            if (cleanPat == "*" || cleanPat == "*.*") return true
            if (cleanPat.startsWith("*.")) {
                val ext = cleanPat.substring(1) // e.g. .ipynb
                if (lowerName.endsWith(ext)) return true
            } else if (cleanPat == lowerName) {
                return true
            }
        }
        return false
    }
}

data class IconThemeContribution(
    val id: String,
    val label: String,
    val path: String
)

data class EditorThemeContribution(
    val id: String,
    val label: String,
    val uiTheme: String = "vs-dark",
    val path: String
)

data class CommandContribution(
    val id: String,
    val title: String,
    val icon: String? = null
)

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
    val contributes: ExtensionContributions get() = manifest.contributes

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

    fun getCustomEditorForFile(filename: String): CustomEditorContribution? {
        if (!isEnabled) return null
        return contributes.customEditors.firstOrNull { it.matches(filename) }
    }

    fun getIconThemeConfigFile(): File? {
        if (!isEnabled) return null
        val itContrib = contributes.iconThemes.firstOrNull() ?: return null
        return File(installDir, itContrib.path).takeIf { it.exists() }
    }
}
