package com.mobilelinux.terminal

import android.graphics.Color
import android.util.Log
import com.mobilelinux.util.TerminalColors
import kotlin.math.max
import kotlin.math.min

/**
 * Terminal line data structure holding character data and visual attributes.
 */
data class TerminalLine(
    var chars: CharArray,
    var fg: IntArray,
    var bg: IntArray,
    var attr: IntArray
) {
    fun resizeCols(newCols: Int) {
        if (chars.size == newCols) return
        val newChars = CharArray(newCols) { ' ' }
        val newFg = IntArray(newCols) { TerminalColors.DEFAULT_FG }
        val newBg = IntArray(newCols) { TerminalColors.DEFAULT_BG }
        val newAttr = IntArray(newCols) { 0 }
        val copyLen = min(chars.size, newCols)
        System.arraycopy(chars, 0, newChars, 0, copyLen)
        System.arraycopy(fg, 0, newFg, 0, copyLen)
        System.arraycopy(bg, 0, newBg, 0, copyLen)
        System.arraycopy(attr, 0, newAttr, 0, copyLen)
        chars = newChars
        fg = newFg
        bg = newBg
        attr = newAttr
    }
}

/**
 * Buffer position in absolute line coordinates.
 */
data class TerminalPos(val row: Int, val col: Int) : Comparable<TerminalPos> {
    override fun compareTo(other: TerminalPos): Int {
        return if (this.row != other.row) this.row.compareTo(other.row)
        else this.col.compareTo(other.col)
    }
}

/**
 * Persistent in-memory terminal emulator state and VT100/xterm-256color parser.
 * Supports full-screen curses applications (Nano, Vim, Htop, Less) including
 * DECSTBM scrolling regions, alternate screen buffer, and cursor state management.
 */
class TerminalBuffer(
    var cols: Int = 80,
    var rows: Int = 24
) {
    companion object {
        const val MAX_SCROLLBACK = 5000
        private const val TAG = "TerminalBuffer"
    }

    // Main screen buffer
    var screen = Array(rows) { CharArray(cols) { ' ' } }
    var fgColors = Array(rows) { IntArray(cols) { TerminalColors.DEFAULT_FG } }
    var bgColors = Array(rows) { IntArray(cols) { TerminalColors.DEFAULT_BG } }
    var attributes = Array(rows) { IntArray(cols) { 0 } }
    val history = ArrayList<TerminalLine>()

    // Alternate screen buffer storage (used by nano, vim, less, htop)
    var isAlternateBuffer: Boolean = false
        private set
    private var mainScreen: Array<CharArray>? = null
    private var mainFg: Array<IntArray>? = null
    private var mainBg: Array<IntArray>? = null
    private var mainAttr: Array<IntArray>? = null
    private var mainCursorRow: Int = 0
    private var mainCursorCol: Int = 0
    private var mainScrollTop: Int = 0
    private var mainScrollBottom: Int = 0

    // Cursor and text attributes
    var cursorRow: Int = 0
    var cursorCol: Int = 0
    var currentFg: Int = TerminalColors.DEFAULT_FG
    var currentBg: Int = TerminalColors.DEFAULT_BG
    var currentAttr: Int = 0 // bold=1, underline=2, blink=4, reverse=8, italic=16

    var savedCursorRow: Int = 0
    var savedCursorCol: Int = 0
    var bracketedPasteMode: Boolean = false
    var isCursorVisible: Boolean = true
    var applicationCursorKeys: Boolean = false
    var applicationKeypad: Boolean = false

    // DECSTBM Scrolling margins (0-based inclusive indices)
    var scrollTop: Int = 0
    var scrollBottom: Int = rows - 1

    // Parser state
    private val escBuffer = StringBuilder()
    private var parsingEscape = false
    private var parsingCSI = false
    private var parsingOSC = false
    private var oscEscSeen = false
    private var parsingCharset = false

    var onUpdate: (() -> Unit)? = null
    var onBell: (() -> Unit)? = null
    var onResponse: ((ByteArray) -> Unit)? = null
    var onHistoryCleared: (() -> Unit)? = null

    @Synchronized
    fun processOutput(data: ByteArray) {
        val str = String(data, Charsets.UTF_8)
        for (ch in str) {
            processChar(ch)
        }
        onUpdate?.invoke()
    }

    private fun processChar(ch: Char) {
        if (parsingEscape) {
            handleEscapeSequence(ch)
            return
        }

        when (ch) {
            '\u001B' -> { // ESC
                parsingEscape = true
                escBuffer.clear()
                parsingCSI = false
            }
            '\r' -> { cursorCol = 0 }
            '\n' -> { newLine() }
            '\t' -> { // Tab stop (every 8 cols)
                cursorCol = ((cursorCol / 8) + 1) * 8
                if (cursorCol >= cols) cursorCol = cols - 1
            }
            '\u0007' -> { // BEL
                onBell?.invoke()
            }
            '\u0008' -> { // Backspace
                if (cursorCol > 0) cursorCol--
            }
            else -> {
                if (ch.code >= 32) {
                    putChar(cursorRow, cursorCol, ch)
                    cursorCol++
                    if (cursorCol >= cols) {
                        cursorCol = 0
                        newLine()
                    }
                }
            }
        }
    }

    private fun putChar(row: Int, col: Int, ch: Char) {
        if (row !in 0 until screen.size || col !in 0 until cols) return
        screen[row][col] = ch
        fgColors[row][col] = currentFg
        bgColors[row][col] = currentBg
        attributes[row][col] = currentAttr
    }

    private fun newLine() {
        if (cursorRow == scrollBottom) {
            scrollUp()
        } else if (cursorRow < rows - 1) {
            cursorRow++
        }
    }

    /**
     * Scrolls lines up within the active scrolling margins (scrollTop..scrollBottom).
     * Only pushes lines to scrollback history when the full screen is scrolling and
     * the alternate buffer is NOT active.
     */
    private fun scrollUp() {
        val top = scrollTop.coerceIn(0, rows - 1)
        val bottom = scrollBottom.coerceIn(top, rows - 1)

        if (top == 0 && bottom == rows - 1 && !isAlternateBuffer) {
            val line = TerminalLine(
                chars = screen[0].clone(),
                fg = fgColors[0].clone(),
                bg = bgColors[0].clone(),
                attr = attributes[0].clone()
            )
            history.add(line)
            if (history.size > MAX_SCROLLBACK) {
                history.removeAt(0)
            }
        }

        for (r in top until bottom) {
            screen[r] = screen[r + 1]
            fgColors[r] = fgColors[r + 1]
            bgColors[r] = bgColors[r + 1]
            attributes[r] = attributes[r + 1]
        }
        screen[bottom] = CharArray(cols) { ' ' }
        fgColors[bottom] = IntArray(cols) { TerminalColors.DEFAULT_FG }
        bgColors[bottom] = IntArray(cols) { TerminalColors.DEFAULT_BG }
        attributes[bottom] = IntArray(cols) { 0 }
    }

    /**
     * Scrolls lines down within the active scrolling margins (scrollTop..scrollBottom).
     */
    private fun scrollDown() {
        val top = scrollTop.coerceIn(0, rows - 1)
        val bottom = scrollBottom.coerceIn(top, rows - 1)

        for (r in bottom downTo top + 1) {
            screen[r] = screen[r - 1]
            fgColors[r] = fgColors[r - 1]
            bgColors[r] = bgColors[r - 1]
            attributes[r] = attributes[r - 1]
        }
        screen[top] = CharArray(cols) { ' ' }
        fgColors[top] = IntArray(cols) { TerminalColors.DEFAULT_FG }
        bgColors[top] = IntArray(cols) { TerminalColors.DEFAULT_BG }
        attributes[top] = IntArray(cols) { 0 }
    }

    private fun clearLine(r: Int) {
        if (r !in 0 until rows) return
        screen[r].fill(' ')
        fgColors[r].fill(TerminalColors.DEFAULT_FG)
        bgColors[r].fill(TerminalColors.DEFAULT_BG)
        attributes[r].fill(0)
    }

    private fun eraseDisplay(mode: Int) {
        when (mode) {
            0 -> {
                for (c in cursorCol until cols) putChar(cursorRow, c, ' ')
                for (r in cursorRow + 1 until rows) clearLine(r)
            }
            1 -> {
                for (r in 0 until cursorRow) clearLine(r)
                for (c in 0..min(cursorCol, cols - 1)) putChar(cursorRow, c, ' ')
            }
            2 -> {
                // CSI 2 J: Erase entire visible display
                for (r in 0 until rows) clearLine(r)
                cursorRow = 0
                cursorCol = 0
            }
            3 -> {
                // CSI 3 J: Erase saved lines (scrollback history) - xterm E3 capability
                history.clear()
                for (r in 0 until rows) clearLine(r)
                cursorRow = 0
                cursorCol = 0
                onHistoryCleared?.invoke()
            }
        }
    }

    private fun eraseLine(mode: Int) {
        when (mode) {
            0 -> for (c in cursorCol until cols) putChar(cursorRow, c, ' ')
            1 -> for (c in 0..min(cursorCol, cols - 1)) putChar(cursorRow, c, ' ')
            2 -> clearLine(cursorRow)
        }
    }

    private fun insertLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val maxShift = scrollBottom - cursorRow + 1
        val n = min(count, maxShift)
        for (r in scrollBottom downTo cursorRow + n) {
            screen[r] = screen[r - n]
            fgColors[r] = fgColors[r - n]
            bgColors[r] = bgColors[r - n]
            attributes[r] = attributes[r - n]
        }
        for (r in cursorRow until min(cursorRow + n, scrollBottom + 1)) {
            clearLine(r)
        }
    }

    private fun deleteLines(count: Int) {
        if (cursorRow !in scrollTop..scrollBottom) return
        val maxShift = scrollBottom - cursorRow + 1
        val n = min(count, maxShift)
        for (r in cursorRow until scrollBottom - n + 1) {
            screen[r] = screen[r + n]
            fgColors[r] = fgColors[r + n]
            bgColors[r] = bgColors[r + n]
            attributes[r] = attributes[r + n]
        }
        for (r in max(cursorRow, scrollBottom - n + 1)..scrollBottom) {
            clearLine(r)
        }
    }

    private fun deleteChars(count: Int) {
        val r = cursorRow
        val n = min(count, cols - cursorCol)
        for (c in cursorCol until cols - n) {
            screen[r][c] = screen[r][c + n]
            fgColors[r][c] = fgColors[r][c + n]
            bgColors[r][c] = bgColors[r][c + n]
            attributes[r][c] = attributes[r][c + n]
        }
        for (c in cols - n until cols) {
            putChar(r, c, ' ')
        }
    }

    private fun insertChars(count: Int) {
        val r = cursorRow
        val n = min(count, cols - cursorCol)
        for (c in cols - 1 downTo cursorCol + n) {
            screen[r][c] = screen[r][c - n]
            fgColors[r][c] = fgColors[r][c - n]
            bgColors[r][c] = bgColors[r][c - n]
            attributes[r][c] = attributes[r][c - n]
        }
        for (c in cursorCol until min(cursorCol + n, cols)) {
            putChar(r, c, ' ')
        }
    }

    private fun eraseChars(count: Int) {
        val r = cursorRow
        for (c in cursorCol until min(cursorCol + count, cols)) {
            putChar(r, c, ' ')
        }
    }

    private fun enterAlternateBuffer() {
        if (!isAlternateBuffer) {
            mainScreen = screen.map { it.clone() }.toTypedArray()
            mainFg = fgColors.map { it.clone() }.toTypedArray()
            mainBg = bgColors.map { it.clone() }.toTypedArray()
            mainAttr = attributes.map { it.clone() }.toTypedArray()
            mainCursorRow = cursorRow
            mainCursorCol = cursorCol
            mainScrollTop = scrollTop
            mainScrollBottom = scrollBottom

            isAlternateBuffer = true
            for (r in 0 until rows) clearLine(r)
            cursorRow = 0
            cursorCol = 0
            scrollTop = 0
            scrollBottom = rows - 1
        }
    }

    private fun exitAlternateBuffer() {
        if (isAlternateBuffer) {
            mainScreen?.let {
                screen = it
                mainScreen = null
            }
            mainFg?.let {
                fgColors = it
                mainFg = null
            }
            mainBg?.let {
                bgColors = it
                mainBg = null
            }
            mainAttr?.let {
                attributes = it
                mainAttr = null
            }
            cursorRow = mainCursorRow.coerceIn(0, rows - 1)
            cursorCol = mainCursorCol.coerceIn(0, cols - 1)
            scrollTop = mainScrollTop.coerceIn(0, rows - 1)
            scrollBottom = mainScrollBottom.coerceIn(0, rows - 1)
            isAlternateBuffer = false
        }
    }

    private fun handleEscapeSequence(ch: Char) {
        if (parsingOSC) {
            if (ch == '\u0007') { // BEL terminates OSC
                parsingOSC = false
                parsingEscape = false
                escBuffer.clear()
                return
            }
            if (ch == '\u001B') {
                oscEscSeen = true
                return
            }
            if (oscEscSeen && ch == '\\') { // ESC \ terminates OSC
                parsingOSC = false
                parsingEscape = false
                oscEscSeen = false
                escBuffer.clear()
                return
            }
            oscEscSeen = false
            if (escBuffer.length < 512) {
                escBuffer.append(ch)
            }
            return
        }

        if (parsingCharset) {
            parsingCharset = false
            parsingEscape = false
            escBuffer.clear()
            return
        }

        when {
            !parsingCSI && ch == '[' -> {
                parsingCSI = true
                escBuffer.append(ch)
            }
            !parsingCSI && ch == ']' -> {
                parsingOSC = true
                oscEscSeen = false
                escBuffer.clear()
            }
            !parsingCSI && (ch == '(' || ch == ')') -> {
                parsingCharset = true
            }
            parsingCSI && (ch.code in 0x40..0x7E) -> {
                escBuffer.append(ch)
                processCSI(escBuffer.toString())
                parsingEscape = false
                parsingCSI = false
                escBuffer.clear()
            }
            !parsingCSI && ch == 'c' -> {
                reset()
                parsingEscape = false
            }
            !parsingCSI && ch == 'D' -> { // IND - Index
                newLine()
                parsingEscape = false
            }
            !parsingCSI && ch == 'E' -> { // NEL - Next Line
                cursorCol = 0
                newLine()
                parsingEscape = false
            }
            !parsingCSI && ch == 'M' -> { // RI - Reverse Index
                if (cursorRow == scrollTop) {
                    scrollDown()
                } else if (cursorRow > 0) {
                    cursorRow--
                }
                parsingEscape = false
            }
            !parsingCSI && ch == '=' -> { // Application keypad
                applicationKeypad = true
                parsingEscape = false
            }
            !parsingCSI && ch == '>' -> { // Normal keypad
                applicationKeypad = false
                parsingEscape = false
            }
            !parsingCSI && ch == '7' -> { // Save cursor
                savedCursorRow = cursorRow
                savedCursorCol = cursorCol
                parsingEscape = false
            }
            !parsingCSI && ch == '8' -> { // Restore cursor
                cursorRow = savedCursorRow.coerceIn(0, rows - 1)
                cursorCol = savedCursorCol.coerceIn(0, cols - 1)
                parsingEscape = false
            }
            else -> {
                escBuffer.append(ch)
                if (escBuffer.length > 20 && !parsingCSI) {
                    parsingEscape = false
                    escBuffer.clear()
                }
            }
        }
    }

    private fun processCSI(seq: String) {
        val inner = seq.substring(1, seq.length - 1)
        val cmd = seq.last()
        val params = if (inner.isEmpty()) emptyList() else inner.removePrefix("?").split(";").map { it.toIntOrNull() ?: 0 }

        fun p(i: Int, default: Int = 0): Int {
            val v = params.getOrNull(i) ?: default
            return if (v <= 0 && default > 0) default else v
        }

        when (cmd) {
            'A' -> cursorRow = max(0, cursorRow - max(1, p(0, 1)))
            'B' -> cursorRow = min(rows - 1, cursorRow + max(1, p(0, 1)))
            'C' -> cursorCol = min(cols - 1, cursorCol + max(1, p(0, 1)))
            'D' -> cursorCol = max(0, cursorCol - max(1, p(0, 1)))
            'E' -> { cursorRow = min(rows - 1, cursorRow + max(1, p(0, 1))); cursorCol = 0 }
            'F' -> { cursorRow = max(0, cursorRow - max(1, p(0, 1))); cursorCol = 0 }
            'G', '`' -> cursorCol = (p(0, 1) - 1).coerceIn(0, cols - 1) // HPA / CHA
            'd' -> cursorRow = (p(0, 1) - 1).coerceIn(0, rows - 1)      // VPA
            'H', 'f' -> {
                cursorRow = (p(0, 1) - 1).coerceIn(0, rows - 1)
                cursorCol = (p(1, 1) - 1).coerceIn(0, cols - 1)
            }
            'J' -> eraseDisplay(p(0, 0))
            'K' -> eraseLine(p(0, 0))
            'S' -> repeat(max(1, p(0, 1))) { scrollUp() }
            'T' -> repeat(max(1, p(0, 1))) { scrollDown() }
            'L' -> insertLines(max(1, p(0, 1)))
            'M' -> deleteLines(max(1, p(0, 1)))
            'P' -> deleteChars(max(1, p(0, 1)))
            '@' -> insertChars(max(1, p(0, 1)))
            'X' -> eraseChars(max(1, p(0, 1)))
            'r' -> { // DECSTBM - Set Top and Bottom Margins (1-based)
                val top = p(0, 1)
                val bottom = p(1, rows)
                if (top in 1..rows && bottom in 1..rows && top < bottom) {
                    scrollTop = top - 1
                    scrollBottom = bottom - 1
                } else {
                    scrollTop = 0
                    scrollBottom = rows - 1
                }
                cursorRow = scrollTop
                cursorCol = 0
            }
            'm' -> processSGR(params)
            's' -> { savedCursorRow = cursorRow; savedCursorCol = cursorCol }
            'u' -> {
                cursorRow = savedCursorRow.coerceIn(0, rows - 1)
                cursorCol = savedCursorCol.coerceIn(0, cols - 1)
            }
            'n' -> {
                when (p(0, 0)) {
                    5 -> onResponse?.invoke("\u001B[0n".toByteArray(Charsets.UTF_8)) // Device status OK
                    6 -> {
                        // Cursor position report: ESC [ row ; col R (1-based)
                        val r = cursorRow + 1
                        val c = cursorCol + 1
                        onResponse?.invoke("\u001B[$r;${c}R".toByteArray(Charsets.UTF_8))
                    }
                }
            }
            'h' -> {
                if (inner.startsWith("?")) {
                    val modes = inner.removePrefix("?").split(";").mapNotNull { it.toIntOrNull() }
                    for (mode in modes) {
                        when (mode) {
                            1 -> applicationCursorKeys = true
                            25 -> isCursorVisible = true
                            47, 1047, 1049 -> enterAlternateBuffer()
                            2004 -> bracketedPasteMode = true
                        }
                    }
                }
            }
            'l' -> {
                if (inner.startsWith("?")) {
                    val modes = inner.removePrefix("?").split(";").mapNotNull { it.toIntOrNull() }
                    for (mode in modes) {
                        when (mode) {
                            1 -> applicationCursorKeys = false
                            25 -> isCursorVisible = false
                            47, 1047, 1049 -> exitAlternateBuffer()
                            2004 -> bracketedPasteMode = false
                        }
                    }
                }
            }
            't', 'q' -> {
                // Window operations / cursor shape — safely ignore
            }
            else -> Log.v(TAG, "Unhandled CSI: $seq")
        }
    }

    private fun processSGR(params: List<Int>) {
        if (params.isEmpty()) {
            currentFg = TerminalColors.DEFAULT_FG
            currentBg = TerminalColors.DEFAULT_BG
            currentAttr = 0
            return
        }
        var i = 0
        while (i < params.size) {
            when (val p = params[i]) {
                0 -> { currentFg = TerminalColors.DEFAULT_FG; currentBg = TerminalColors.DEFAULT_BG; currentAttr = 0 }
                1 -> currentAttr = currentAttr or 1  // bold
                2 -> currentAttr = currentAttr or 2  // dim
                3 -> currentAttr = currentAttr or 4  // italic
                4 -> currentAttr = currentAttr or 8  // underline
                7 -> currentAttr = currentAttr or 16 // reverse
                22 -> currentAttr = currentAttr and 1.inv() and 2.inv()
                23 -> currentAttr = currentAttr and 4.inv()
                24 -> currentAttr = currentAttr and 8.inv()
                27 -> currentAttr = currentAttr and 16.inv()
                in 30..37 -> currentFg = TerminalColors.ansi16(p - 30)
                38 -> {
                    if (params.getOrNull(i + 1) == 5 && i + 2 < params.size) {
                        currentFg = TerminalColors.color256(params[i + 2])
                        i += 2
                    } else if (params.getOrNull(i + 1) == 2 && i + 4 < params.size) {
                        currentFg = Color.rgb(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }
                39 -> currentFg = TerminalColors.DEFAULT_FG
                in 40..47 -> currentBg = TerminalColors.ansi16(p - 40)
                48 -> {
                    if (params.getOrNull(i + 1) == 5 && i + 2 < params.size) {
                        currentBg = TerminalColors.color256(params[i + 2])
                        i += 2
                    } else if (params.getOrNull(i + 1) == 2 && i + 4 < params.size) {
                        currentBg = Color.rgb(params[i + 2], params[i + 3], params[i + 4])
                        i += 4
                    }
                }
                49 -> currentBg = TerminalColors.DEFAULT_BG
                in 90..97 -> currentFg = TerminalColors.ansi16(p - 90 + 8)
                in 100..107 -> currentBg = TerminalColors.ansi16(p - 100 + 8)
            }
            i++
        }
    }

    @Synchronized
    fun reset() {
        exitAlternateBuffer()
        for (r in 0 until rows) clearLine(r)
        cursorRow = 0
        cursorCol = 0
        scrollTop = 0
        scrollBottom = rows - 1
        currentFg = TerminalColors.DEFAULT_FG
        currentBg = TerminalColors.DEFAULT_BG
        currentAttr = 0
        bracketedPasteMode = false
        isCursorVisible = true
        applicationCursorKeys = false
        applicationKeypad = false
        history.clear()
        onHistoryCleared?.invoke()
    }

    @Synchronized
    fun clearHistory() {
        history.clear()
        onHistoryCleared?.invoke()
    }

    @Synchronized
    fun resize(newCols: Int, newRows: Int) {
        if (newCols <= 0 || newRows <= 0) return
        if (newCols == cols && newRows == rows) return

        for (line in history) {
            line.resizeCols(newCols)
        }

        val newScreen = Array(newRows) { CharArray(newCols) { ' ' } }
        val newFg = Array(newRows) { IntArray(newCols) { TerminalColors.DEFAULT_FG } }
        val newBg = Array(newRows) { IntArray(newCols) { TerminalColors.DEFAULT_BG } }
        val newAttr = Array(newRows) { IntArray(newCols) { 0 } }

        if (newRows < rows) {
            val overflow = max(0, cursorRow - (newRows - 1))
            for (r in 0 until overflow) {
                if (r < screen.size && !isAlternateBuffer) {
                    val lineChars = CharArray(newCols) { ' ' }
                    val lineFg = IntArray(newCols) { TerminalColors.DEFAULT_FG }
                    val lineBg = IntArray(newCols) { TerminalColors.DEFAULT_BG }
                    val lineAttr = IntArray(newCols) { 0 }
                    val copyLen = min(cols, newCols)
                    System.arraycopy(screen[r], 0, lineChars, 0, copyLen)
                    System.arraycopy(fgColors[r], 0, lineFg, 0, copyLen)
                    System.arraycopy(bgColors[r], 0, lineBg, 0, copyLen)
                    System.arraycopy(attributes[r], 0, lineAttr, 0, copyLen)
                    val line = TerminalLine(lineChars, lineFg, lineBg, lineAttr)
                    history.add(line)
                    if (history.size > MAX_SCROLLBACK) {
                        history.removeAt(0)
                    }
                }
            }
            for (r in 0 until newRows) {
                val oldR = r + overflow
                if (oldR < screen.size) {
                    val copyCols = min(cols, newCols)
                    System.arraycopy(screen[oldR], 0, newScreen[r], 0, copyCols)
                    System.arraycopy(fgColors[oldR], 0, newFg[r], 0, copyCols)
                    System.arraycopy(bgColors[oldR], 0, newBg[r], 0, copyCols)
                    System.arraycopy(attributes[oldR], 0, newAttr[r], 0, copyCols)
                }
            }
            cursorRow = (cursorRow - overflow).coerceIn(0, newRows - 1)
        } else {
            val copyRows = min(rows, newRows)
            val copyCols = min(cols, newCols)
            for (r in 0 until copyRows) {
                System.arraycopy(screen[r], 0, newScreen[r], 0, copyCols)
                System.arraycopy(fgColors[r], 0, newFg[r], 0, copyCols)
                System.arraycopy(bgColors[r], 0, newBg[r], 0, copyCols)
                System.arraycopy(attributes[r], 0, newAttr[r], 0, copyCols)
            }
            cursorRow = cursorRow.coerceIn(0, newRows - 1)
        }

        cols = newCols
        rows = newRows
        screen = newScreen
        fgColors = newFg
        bgColors = newBg
        attributes = newAttr

        // Reset scroll margins to full screen for the new dimensions
        scrollTop = 0
        scrollBottom = rows - 1
    }

    @Synchronized
    fun getLine(absRow: Int): TerminalLine? {
        val hSize = history.size
        return when {
            absRow < 0 -> null
            absRow < hSize -> history[absRow]
            absRow < hSize + rows -> {
                val screenRow = absRow - hSize
                if (screenRow in 0 until rows) {
                    TerminalLine(screen[screenRow], fgColors[screenRow], bgColors[screenRow], attributes[screenRow])
                } else null
            }
            else -> null
        }
    }
}
