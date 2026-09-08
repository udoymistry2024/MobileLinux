package com.mobilelinux.util

/**
 * Intelligent real-time parser for package manager outputs (APT, dpkg, pip, git, etc.).
 * Extracts exact percentages or computes smooth forward progression from live terminal logs.
 */
class PackageProgressParser(
    private val packageName: String
) {
    var currentPercent: Int = 0
        private set

    var lastStage: String = "Starting installation..."
        private set

    private val percentRegex = Regex("""(?:\[|\(|\s|^)(\d{1,3})%(?:\]|\)|\s|$)""")
    private val pipProgressRegex = Regex("""(\d{1,3})%\s*\|""")
    private val dpkgProgressRegex = Regex("""Progress:\s*\[\s*(\d{1,3})%""")
    private val wgetRegex = Regex("""(\d{1,3})%\s*\[""")

    fun parseLine(rawLine: String): ProgressUpdate {
        // Strip ANSI escape codes
        val line = rawLine.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "").trim()
        if (line.isEmpty()) {
            return ProgressUpdate(currentPercent, lastStage)
        }

        // 1. Look for explicit percentage in line
        var foundPercent: Int? = null
        val dpkgMatch = dpkgProgressRegex.find(line)
        if (dpkgMatch != null) {
            foundPercent = dpkgMatch.groupValues[1].toIntOrNull()
        } else {
            val pipMatch = pipProgressRegex.find(line)
            if (pipMatch != null) {
                foundPercent = pipMatch.groupValues[1].toIntOrNull()
            } else {
                val wgetMatch = wgetRegex.find(line)
                if (wgetMatch != null) {
                    foundPercent = wgetMatch.groupValues[1].toIntOrNull()
                } else {
                    val generalMatch = percentRegex.find(line)
                    if (generalMatch != null) {
                        foundPercent = generalMatch.groupValues[1].toIntOrNull()
                    }
                }
            }
        }

        if (foundPercent != null && foundPercent in 1..100) {
            val isFinalLine = line.contains("complete", ignoreCase = true) || line.contains("installed successfully", ignoreCase = true)
            val effectivePercent = if (foundPercent >= 98 && !isFinalLine) 95 else foundPercent
            currentPercent = maxOf(currentPercent, effectivePercent)
        }

        // 2. Map line text to friendly stage & forward progress
        val lower = line.lowercase()
        val stage: String = when {
            lower.startsWith("e: ") || lower.contains("dpkg: error") || lower.contains("could not get lock") -> {
                "Resolving package manager state..."
            }
            lower.contains("reading package lists") || lower.contains("building dependency tree") -> {
                currentPercent = maxOf(currentPercent, 10)
                "Resolving dependencies..."
            }
            lower.contains("hit:") || (lower.contains("get:") && lower.contains("inrelease")) || lower.contains("apt-get update") -> {
                currentPercent = maxOf(currentPercent, 20)
                "Updating repository lists..."
            }
            lower.contains("get:") && (lower.contains(".deb") || lower.contains("ubuntu-ports")) -> {
                currentPercent = maxOf(currentPercent, 35)
                val debName = extractDebName(line)
                if (debName.isNotEmpty()) "Downloading $debName..." else "Downloading packages..."
            }
            lower.contains("collecting ") -> {
                val target = extractTarget(line, "collecting ")
                currentPercent = if (currentPercent >= 95) 30 else maxOf(currentPercent, 25)
                if (target.isNotEmpty()) "Collecting $target..." else "Collecting dependencies..."
            }
            lower.contains("downloading ") -> {
                currentPercent = when {
                    currentPercent in 35..64 -> currentPercent + 2
                    currentPercent < 35 -> 35
                    else -> currentPercent
                }
                when {
                    lower.contains("conda") || lower.contains("miniforge") -> "Downloading Conda installer..."
                    else -> {
                        val target = extractTarget(line, "downloading ")
                        if (target.isNotEmpty()) "Downloading $target..." else "Downloading dependencies..."
                    }
                }
            }
            lower.contains("preparing to unpack") || lower.contains("unpacking ") || lower.contains("unpacking conda") || lower.contains("extracting conda") -> {
                currentPercent = maxOf(currentPercent, 65)
                if (lower.contains("conda") || lower.contains("miniforge")) {
                    "Unpacking Conda packages (please wait)..."
                } else {
                    val target = extractTarget(line, "unpacking ")
                    if (target.isNotEmpty()) "Unpacking $target..." else "Unpacking files..."
                }
            }
            lower.contains("configuring conda") || lower.contains("initializing bash shell") || lower.contains("conda init") -> {
                currentPercent = maxOf(currentPercent, 88)
                "Configuring Conda & auto-activation..."
            }
            lower.contains("setting up ") -> {
                currentPercent = maxOf(currentPercent, 85)
                val target = extractTarget(line, "setting up ")
                if (target.isNotEmpty()) "Configuring $target..." else "Configuring package..."
            }
            lower.contains("installing collected packages") -> {
                currentPercent = maxOf(currentPercent, 75)
                "Unpacking & installing package files (please wait)..."
            }
            lower.contains("registering conda") || lower.contains("conda base kernel") -> {
                currentPercent = maxOf(currentPercent, 90)
                "Registering environment kernels..."
            }
            lower.contains("mobile touch patch") || lower.contains("patched:") || lower.contains("fixing jupyter") -> {
                currentPercent = maxOf(currentPercent, 95)
                "Applying mobile optimizations..."
            }
            lower.contains("processing triggers") -> {
                currentPercent = maxOf(currentPercent, 94)
                "Finalizing system triggers..."
            }
            lower.contains("building wheel") -> {
                currentPercent = maxOf(currentPercent, 80)
                "Building package binary..."
            }
            lower.contains("cloning into") -> {
                currentPercent = maxOf(currentPercent, 35)
                "Cloning git repository..."
            }
            lower.contains("resolving deltas") -> {
                currentPercent = maxOf(currentPercent, 75)
                "Resolving git deltas..."
            }
            lower.contains("npm http fetch") -> {
                currentPercent = maxOf(currentPercent, 50)
                "Fetching npm package..."
            }
            (lower.contains("added ") && lower.contains("package")) || lower.contains("installation complete") || lower.contains("sync complete") || lower.contains("installed successfully") || lower.contains("✓ jupyter") || lower.contains("✓ miniforge") || lower.contains("✓ conda") || lower.contains("conda successfully installed") || lower.contains("conda installed successfully") -> {
                currentPercent = 100
                "Installation complete"
            }
            lower.contains("successfully installed") -> {
                currentPercent = maxOf(currentPercent, 92)
                "Finalizing installation..."
            }
            else -> {
                lastStage
            }
        }

        lastStage = stage
        return ProgressUpdate(currentPercent, stage)
    }

    /**
     * Parses output from package uninstallation scripts and returns clean progress updates.
     */
    fun parseUninstallLine(rawLine: String): ProgressUpdate {
        val line = rawLine.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "").trim()
        if (line.isEmpty()) {
            return ProgressUpdate(currentPercent, lastStage)
        }
        val lower = line.lowercase()
        val stage: String = when {
            lower.contains("deactivating and removing conda") || lower.contains("purging") || lower.contains("removing apt") || lower.contains("removing package") -> {
                currentPercent = maxOf(currentPercent, 25)
                if (lower.contains("conda")) "Deactivating and removing Conda..." else "Removing package files..."
            }
            lower.contains("removing conda directories") || lower.contains("removing from system pip") || lower.contains("pip uninstall") || lower.contains("uninstalling") -> {
                currentPercent = maxOf(currentPercent, 50)
                if (lower.contains("conda")) "Removing Conda directories..." else "Uninstalling Python modules..."
            }
            lower.contains("cleaning shell") || lower.contains("cleaning python site-packages") || lower.contains("site-packages") -> {
                currentPercent = maxOf(currentPercent, 70)
                if (lower.contains("shell") || lower.contains("bashrc")) "Cleaning shell configuration..." else "Cleaning library directories..."
            }
            lower.contains("cleaning conda") || lower.contains("conda") -> {
                currentPercent = maxOf(currentPercent, 85)
                "Cleaning Conda environments..."
            }
            lower.contains("permanently uninstalled") || lower.contains("successfully uninstalled") || lower.contains("purged from all environments") || lower.contains("conda permanently uninstalled") || lower.contains("conda successfully removed") -> {
                currentPercent = 100
                "Uninstallation complete"
            }
            else -> lastStage
        }
        lastStage = stage
        return ProgressUpdate(currentPercent, stage)
    }

    private fun extractTarget(line: String, prefix: String): String {
        val clean = line.replace(Regex("""^\[MobileLinux\]\s*""", RegexOption.IGNORE_CASE), "")
        val idx = clean.indexOf(prefix, ignoreCase = true)
        if (idx == -1) return ""
        val after = clean.substring(idx + prefix.length).trim()
        val target = after.substringBefore(" ").trim()
        return if (!target.startsWith("[")) target else ""
    }

    private fun extractDebName(line: String): String {
        val parts = line.split(" ").filter { it.isNotBlank() }
        val debPart = parts.firstOrNull { it.contains("python3-") || it.contains(packageName.lowercase()) }
        if (debPart != null) return debPart
        val archIdx = parts.indexOfFirst { it == "arm64" || it == "all" || it == "amd64" || it == "armhf" }
        if (archIdx != -1 && archIdx + 1 < parts.size) {
            val candidate = parts[archIdx + 1]
            if (candidate.matches(Regex("[a-zA-Z0-9_.+-]+"))) {
                return candidate
            }
        }
        return ""
    }

    data class ProgressUpdate(
        val percent: Int,
        val stage: String
    )
}
