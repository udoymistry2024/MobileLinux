package com.mobilelinux.util

import android.graphics.Color

/**
 * Terminal color definitions and themes.
 * Implements standard ANSI 16-color palette + xterm 256-color with multiple theme options.
 */
object TerminalColors {

    data class ColorScheme(
        val id: String,
        val name: String,
        val bg: Int,
        val fg: Int,
        val cursor: Int,
        val ansi16: IntArray
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as ColorScheme
            return id == other.id
        }
        override fun hashCode(): Int = id.hashCode()
    }

    // 1. GitHub Dark (Default)
    val GITHUB_DARK = ColorScheme(
        id = "github_dark",
        name = "GitHub Dark",
        bg = Color.parseColor("#0D1117"),
        fg = Color.parseColor("#E6EDF3"),
        cursor = Color.parseColor("#58A6FF"),
        ansi16 = intArrayOf(
            Color.parseColor("#6E7681"), // 0 Black (adjusted for dark bg legibility)
            Color.parseColor("#FF7B72"), // 1 Red
            Color.parseColor("#3FB950"), // 2 Green
            Color.parseColor("#D29922"), // 3 Yellow
            Color.parseColor("#58A6FF"), // 4 Blue
            Color.parseColor("#BC8CFF"), // 5 Magenta
            Color.parseColor("#39C5CF"), // 6 Cyan
            Color.parseColor("#B1BAC4"), // 7 White
            Color.parseColor("#8B949E"), // 8 Bright Black
            Color.parseColor("#FFA198"), // 9 Bright Red
            Color.parseColor("#56D364"), // 10 Bright Green
            Color.parseColor("#E3B341"), // 11 Bright Yellow
            Color.parseColor("#79C0FF"), // 12 Bright Blue
            Color.parseColor("#D2A8FF"), // 13 Bright Magenta
            Color.parseColor("#56D4DD"), // 14 Bright Cyan
            Color.parseColor("#F0F6FC")  // 15 Bright White
        )
    )

    // 2. Dracula
    val DRACULA = ColorScheme(
        id = "dracula",
        name = "Dracula",
        bg = Color.parseColor("#282A36"),
        fg = Color.parseColor("#F8F8F2"),
        cursor = Color.parseColor("#BD93F9"),
        ansi16 = intArrayOf(
            Color.parseColor("#6272A4"), // 0
            Color.parseColor("#FF5555"), // 1
            Color.parseColor("#50FA7B"), // 2
            Color.parseColor("#F1FA8C"), // 3
            Color.parseColor("#BD93F9"), // 4
            Color.parseColor("#FF79C6"), // 5
            Color.parseColor("#8BE9FD"), // 6
            Color.parseColor("#F8F8F2"), // 7
            Color.parseColor("#9DA8C7"), // 8
            Color.parseColor("#FF6E6E"), // 9
            Color.parseColor("#69FF94"), // 10
            Color.parseColor("#FFFFA5"), // 11
            Color.parseColor("#D6ACFF"), // 12
            Color.parseColor("#FF92DF"), // 13
            Color.parseColor("#A4FFFF"), // 14
            Color.parseColor("#FFFFFF")  // 15
        )
    )

    // 3. Monokai
    val MONOKAI = ColorScheme(
        id = "monokai",
        name = "Monokai",
        bg = Color.parseColor("#272822"),
        fg = Color.parseColor("#F8F8F2"),
        cursor = Color.parseColor("#F92672"),
        ansi16 = intArrayOf(
            Color.parseColor("#75715E"), // 0
            Color.parseColor("#F92672"), // 1
            Color.parseColor("#A6E22E"), // 2
            Color.parseColor("#F4BF75"), // 3
            Color.parseColor("#66D9EF"), // 4
            Color.parseColor("#AE81FF"), // 5
            Color.parseColor("#A1EFE4"), // 6
            Color.parseColor("#F8F8F2"), // 7
            Color.parseColor("#A6A28E"), // 8
            Color.parseColor("#F92672"), // 9
            Color.parseColor("#A6E22E"), // 10
            Color.parseColor("#F4BF75"), // 11
            Color.parseColor("#66D9EF"), // 12
            Color.parseColor("#AE81FF"), // 13
            Color.parseColor("#A1EFE4"), // 14
            Color.parseColor("#F9F8F5")  // 15
        )
    )

    // 4. Solarized Dark
    val SOLARIZED_DARK = ColorScheme(
        id = "solarized_dark",
        name = "Solarized Dark",
        bg = Color.parseColor("#002B36"),
        fg = Color.parseColor("#839496"),
        cursor = Color.parseColor("#268BD2"),
        ansi16 = intArrayOf(
            Color.parseColor("#586E75"), // 0
            Color.parseColor("#DC322F"), // 1
            Color.parseColor("#859900"), // 2
            Color.parseColor("#B58900"), // 3
            Color.parseColor("#268BD2"), // 4
            Color.parseColor("#D33682"), // 5
            Color.parseColor("#2AA198"), // 6
            Color.parseColor("#EEE8D5"), // 7
            Color.parseColor("#839496"), // 8
            Color.parseColor("#CB4B16"), // 9
            Color.parseColor("#859900"), // 10
            Color.parseColor("#B58900"), // 11
            Color.parseColor("#268BD2"), // 12
            Color.parseColor("#6C71C4"), // 13
            Color.parseColor("#2AA198"), // 14
            Color.parseColor("#FDF6E3")  // 15
        )
    )

    // 5. One Dark
    val ONE_DARK = ColorScheme(
        id = "one_dark",
        name = "One Dark",
        bg = Color.parseColor("#282C34"),
        fg = Color.parseColor("#ABB2BF"),
        cursor = Color.parseColor("#61AFEF"),
        ansi16 = intArrayOf(
            Color.parseColor("#5C6370"), // 0
            Color.parseColor("#E06C75"), // 1
            Color.parseColor("#98C379"), // 2
            Color.parseColor("#E5C07B"), // 3
            Color.parseColor("#61AFEF"), // 4
            Color.parseColor("#C678DD"), // 5
            Color.parseColor("#56B6C2"), // 6
            Color.parseColor("#ABB2BF"), // 7
            Color.parseColor("#828997"), // 8
            Color.parseColor("#E06C75"), // 9
            Color.parseColor("#98C379"), // 10
            Color.parseColor("#E5C07B"), // 11
            Color.parseColor("#61AFEF"), // 12
            Color.parseColor("#C678DD"), // 13
            Color.parseColor("#56B6C2"), // 14
            Color.parseColor("#FFFFFF")  // 15
        )
    )

    var currentScheme: ColorScheme = GITHUB_DARK
        set(value) {
            field = value
            try {
                contrastCache.clear()
            } catch (ignored: Exception) {}
        }

    fun getColorScheme(id: String): ColorScheme {
        return when (id) {
            "dracula" -> DRACULA
            "monokai" -> MONOKAI
            "solarized_dark" -> SOLARIZED_DARK
            "one_dark" -> ONE_DARK
            else -> GITHUB_DARK
        }
    }

    val DEFAULT_BG get() = currentScheme.bg
    val DEFAULT_FG get() = currentScheme.fg
    val CURSOR_COLOR get() = currentScheme.cursor

    fun ansi16(index: Int): Int {
        return currentScheme.ansi16.getOrElse(index) { currentScheme.fg }
    }

    fun color256(index: Int): Int {
        return when (index) {
            in 0..15 -> currentScheme.ansi16[index]
            in 16..231 -> {
                val i = index - 16
                val r = i / 36
                val g = (i % 36) / 6
                val b = i % 6
                Color.rgb(
                    if (r == 0) 0 else 55 + r * 40,
                    if (g == 0) 0 else 55 + g * 40,
                    if (b == 0) 0 else 55 + b * 40
                )
            }
            in 232..255 -> {
                // Adjust darkest 232-238 grays to ensure readability against dark backgrounds
                val gray = if (index < 239) {
                    60 + (index - 232) * 8
                } else {
                    8 + (index - 232) * 10
                }
                Color.rgb(gray, gray, gray)
            }
            else -> currentScheme.fg
        }
    }

    // Fast contrast cache: packs (fg, bg) -> high-contrast fg
    private val contrastCache = android.util.LongSparseArray<Int>(256)

    /**
     * Ensures foreground text has sufficient perceptual contrast against the background cell.
     * Prevents invisible or hard-to-read text on dark backgrounds (e.g. Google Antigravity CLI,
     * Gemini CLI, Claude, Python Rich, Chalk output).
     */
    fun ensureContrasting(fg: Int, bg: Int, minDifference: Int = 90): Int {
        if (fg == DEFAULT_FG && bg == DEFAULT_BG) return fg

        val key = (fg.toLong() shl 32) or (bg.toLong() and 0xFFFFFFFFL)
        val cached = contrastCache.get(key)
        if (cached != null) return cached

        val bgR = (bg shr 16) and 0xFF
        val bgG = (bg shr 8) and 0xFF
        val bgB = bg and 0xFF
        val bgY = (299 * bgR + 587 * bgG + 114 * bgB) / 1000

        val fgR = (fg shr 16) and 0xFF
        val fgG = (fg shr 8) and 0xFF
        val fgB = fg and 0xFF
        val fgY = (299 * fgR + 587 * fgG + 114 * fgB) / 1000

        val diff = kotlin.math.abs(fgY - bgY)
        val result = if (diff >= minDifference) {
            fg
        } else if (bgY < 128) {
            // Dark background: brighten foreground to at least bgY + minDifference
            val targetY = kotlin.math.min(255, bgY + minDifference)
            if (fgY <= 15) {
                // Pure black or near-black on dark bg: boost to crisp legible neutral slate
                Color.rgb(150, 160, 175)
            } else {
                val boost = targetY.toFloat() / fgY.toFloat()
                val r = kotlin.math.min(255, (fgR * boost).toInt())
                val g = kotlin.math.min(255, (fgG * boost).toInt())
                val b = kotlin.math.min(255, (fgB * boost).toInt())
                Color.rgb(r, g, b)
            }
        } else {
            // Light background: darken foreground
            val targetY = kotlin.math.max(0, bgY - minDifference)
            if (fgY >= 240) {
                Color.rgb(30, 30, 30)
            } else {
                val scale = targetY.toFloat() / fgY.toFloat()
                val r = kotlin.math.max(0, (fgR * scale).toInt())
                val g = kotlin.math.max(0, (fgG * scale).toInt())
                val b = kotlin.math.max(0, (fgB * scale).toInt())
                Color.rgb(r, g, b)
            }
        }

        if (contrastCache.size() > 512) {
            contrastCache.clear()
        }
        contrastCache.put(key, result)
        return result
    }

    // Attr flags
    const val ATTR_BOLD = 1
    const val ATTR_DIM = 2
    const val ATTR_ITALIC = 4
    const val ATTR_UNDERLINE = 8
    const val ATTR_REVERSE = 16
    const val ATTR_BLINK = 32
}
