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
            val isFinalLine = line.contains("complete", ignoreCase = true) || line.contains("successfully", ignoreCase = true)
            val effectivePercent = if (foundPercent >= 98 && !isFinalLine) 95 else foundPercent
            currentPercent = maxOf(currentPercent, effectivePercent)
        }

        // 2. Map line text to friendly stage & forward progress
        val lower = line.lowercase()
        val stage: String = when {
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
                val target = line.substringAfter("collecting ").substringBefore(" ").trim()
                currentPercent = maxOf(currentPercent, 25)
                if (target.isNotEmpty()) "Collecting $target..." else "Collecting dependencies..."
            }
            lower.contains("downloading ") -> {
                currentPercent = maxOf(currentPercent, 40)
                val target = line.substringAfter("downloading ").substringBefore(" ").trim()
                if (target.isNotEmpty()) "Downloading $target..." else "Downloading dependencies..."
            }
            lower.contains("preparing to unpack") || lower.contains("unpacking ") -> {
                currentPercent = maxOf(currentPercent, 70)
                val target = line.substringAfter("unpacking ").substringBefore(" ").trim()
                if (target.isNotEmpty()) "Unpacking $target..." else "Unpacking files..."
            }
            lower.contains("setting up ") -> {
                currentPercent = maxOf(currentPercent, 85)
                val target = line.substringAfter("setting up ").substringBefore(" ").trim()
                if (target.isNotEmpty()) "Configuring $target..." else "Configuring package..."
            }
            lower.contains("installing collected packages") -> {
                currentPercent = maxOf(currentPercent, 88)
                "Installing collected packages..."
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
            lower.contains("successfully installed") || lower.contains("installation complete") || lower.contains("sync complete") -> {
                currentPercent = 100
                "Installation complete"
            }
            else -> {
                lastStage
            }
        }

        lastStage = stage
        return ProgressUpdate(currentPercent, stage)
    }

    private fun extractDebName(line: String): String {
        val parts = line.split(" ")
        val debPart = parts.firstOrNull { it.contains("python3-") || it.contains(packageName.lowercase()) }
        return debPart ?: ""
    }

    data class ProgressUpdate(
        val percent: Int,
        val stage: String
    )
}
