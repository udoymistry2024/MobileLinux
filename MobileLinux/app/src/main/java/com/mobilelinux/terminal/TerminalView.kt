package com.mobilelinux.terminal

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.AttributeSet
import android.util.Log
import android.view.*
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.OverScroller
import android.widget.PopupWindow
import android.widget.TextView
import android.widget.Toast
import com.mobilelinux.util.TerminalColors
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Terminal emulator view — renders VT100/xterm-256color output from the Linux process.
 *
 * Backed by decoupled, thread-safe [TerminalBuffer], preserving all screen output,
 * scrollback history, and cursor position across view lifecycle, session switching, and app minimization.
 *
 * Features:
 * - Decoupled in-memory buffer backing (Termux architecture)
 * - Dynamic scrollback buffer (up to 5000 lines) with gesture & fling scrolling
 * - Visual scrollbar & floating scroll distance indicator pill
 * - Word & multi-line text selection via long-press and drag handles
 * - Clipboard Copy, Paste, Select All, and Share via Android ActionMode & custom popup fallback
 * - Hardware keyboard shortcuts (Ctrl+Shift+C / Ctrl+Shift+V / Shift+Insert)
 * - Safe screen resizing on software keyboard open/close without dropping lines
 */
class TerminalView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "TerminalView"
        private const val ID_COPY = 1001
        private const val ID_PASTE = 1002
        private const val ID_SELECT_ALL = 1003
        private const val ID_SHARE = 1004
    }

    // -------------------------------------------------------------------------
    // Decoupled Terminal Buffer
    // -------------------------------------------------------------------------
    var buffer: TerminalBuffer = TerminalBuffer(80, 24)
        private set

    val cols: Int get() = buffer.cols
    val rows: Int get() = buffer.rows
    val cursorRow: Int get() = buffer.cursorRow
    val cursorCol: Int get() = buffer.cursorCol
    val history: ArrayList<TerminalLine> get() = buffer.history
    val screen: Array<CharArray> get() = buffer.screen
    val fgColors: Array<IntArray> get() = buffer.fgColors
    val bgColors: Array<IntArray> get() = buffer.bgColors
    val attributes: Array<IntArray> get() = buffer.attributes
    val bracketedPasteMode: Boolean get() = buffer.bracketedPasteMode

    fun attachBuffer(newBuffer: TerminalBuffer) {
        if (buffer !== newBuffer) {
            buffer.onUpdate = null
            buffer.onBell = null
            buffer.onHistoryCleared = null
            buffer = newBuffer
        }
        setupBufferCallbacks(buffer)
        if (charWidth > 0 && charHeight > 0 && width > 0 && height > 0) {
            val newCols = max(1, (width / charWidth).toInt())
            val newRows = max(1, (height / charHeight).toInt())
            buffer.resize(newCols, newRows)
            onTerminalResize?.invoke(newCols, newRows)
        } else {
            post {
                if (charWidth > 0 && charHeight > 0 && width > 0 && height > 0) {
                    val newCols = max(1, (width / charWidth).toInt())
                    val newRows = max(1, (height / charHeight).toInt())
                    buffer.resize(newCols, newRows)
                    onTerminalResize?.invoke(newCols, newRows)
                    postInvalidate()
                }
            }
        }
        scrollOffset = 0
        clearSelection()
        postInvalidate()
    }

    var onTerminalResize: ((cols: Int, rows: Int) -> Unit)? = null

    // -------------------------------------------------------------------------
    // Configuration
    // -------------------------------------------------------------------------
    var textSizeSp: Float = 14f
        set(value) {
            field = value.coerceIn(8f, 32f)
            updateFontMetrics()
            invalidate()
        }

    var onInputListener: ((ByteArray) -> Unit)? = null
    var onTitleChangeListener: ((String) -> Unit)? = null
    var bellEnabled: Boolean = false

    var isCtrlActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                onModifierChanged?.invoke("Ctrl", value)
            }
        }

    var isAltActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                onModifierChanged?.invoke("Alt", value)
            }
        }

    var isShiftActive: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                onModifierChanged?.invoke("Shift", value)
            }
        }

    var onModifierChanged: ((String, Boolean) -> Unit)? = null

    fun toggleCtrl(): Boolean {
        isCtrlActive = !isCtrlActive
        return isCtrlActive
    }

    fun toggleAlt(): Boolean {
        isAltActive = !isAltActive
        return isAltActive
    }

    fun toggleShift(): Boolean {
        isShiftActive = !isShiftActive
        return isShiftActive
    }

    fun setColorScheme(schemeId: String) {
        val scheme = TerminalColors.getColorScheme(schemeId)
        TerminalColors.currentScheme = scheme
        setBackgroundColor(scheme.bg)
        buffer.currentFg = scheme.fg
        buffer.currentBg = scheme.bg
        postInvalidate()
    }

    fun applyPreferences() {
        try {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val fontSize = prefs.getInt("pref_font_size", 14).toFloat()
            textSizeSp = fontSize

            val scheme = prefs.getString("pref_color_scheme", "github_dark") ?: "github_dark"
            setColorScheme(scheme)

            bellEnabled = prefs.getBoolean("pref_bell", false)
            keepScreenOn = prefs.getBoolean("pref_keep_screen_on", false)
        } catch (e: Exception) {
            Log.w(TAG, "applyPreferences error: ${e.message}")
        }
    }

    // -------------------------------------------------------------------------
    // Font & rendering
    // -------------------------------------------------------------------------
    private val typeface: Typeface = try {
        Typeface.createFromAsset(context.assets, "fonts/JetBrainsMono-Regular.ttf")
    } catch (e: Exception) {
        Typeface.MONOSPACE
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = this@TerminalView.typeface
        isSubpixelText = true
    }

    private val selectionPaint = Paint().apply {
        style = Paint.Style.FILL
        color = Color.argb(130, 56, 139, 253) // Semi-transparent accent blue
    }

    private val scrollbarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(160, 180, 180, 180)
    }

    private val pillBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(220, 24, 28, 36)
    }

    private val pillTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.DEFAULT_BOLD
        color = Color.WHITE
    }

    private var charWidth: Float = 0f
    private var charHeight: Float = 0f
    private var charAscent: Float = 0f

    // Scrollback offset: 0 = at bottom (live view), >0 = scrolled back in history
    var scrollOffset: Int = 0
        private set

    // Cursor blink & terminal modes
    private var cursorVisible = true
    private val cursorBlinkRunnable = object : Runnable {
        override fun run() {
            cursorVisible = !cursorVisible
            if (scrollOffset == 0) invalidate()
            postDelayed(this, 500)
        }
    }

    // -------------------------------------------------------------------------
    // Text Selection & Clipboard State
    // -------------------------------------------------------------------------
    var isSelecting: Boolean = false
        private set
    var selectionStart: TerminalPos? = null
        private set
    var selectionEnd: TerminalPos? = null
        private set

    private var actionMode: ActionMode? = null
    private var customPopup: PopupWindow? = null
    private val pillRect = RectF()

    // Interactive Selection Handles
    private val handlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#58A6FF")
    }
    private val handleLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#58A6FF")
        strokeWidth = 4f
    }
    private val handleBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#0D1117")
        strokeWidth = 3f
    }
    private enum class DraggingHandle { NONE, START, END }
    private var activeDraggingHandle = DraggingHandle.NONE
    private val startHandleTouchRect = RectF()
    private val endHandleTouchRect = RectF()

    // -------------------------------------------------------------------------
    // Touch, Gestures & Scrolling
    // -------------------------------------------------------------------------
    private val scroller = OverScroller(context)
    private var accumulatedScrollY = 0f
    private val gestureDetector = GestureDetector(context, TerminalGestureListener())
    private val scaleGestureDetector = ScaleGestureDetector(context, TerminalScaleListener())

    // =========================================================================
    // Initialization
    // =========================================================================

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        setLayerType(LAYER_TYPE_HARDWARE, null)
        setupBufferCallbacks(buffer)
        applyPreferences()
        updateFontMetrics()
    }

    private val renderHandler = Handler(Looper.getMainLooper())
    private val isRenderPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private val renderRunnable = Runnable {
        isRenderPending.set(false)
        invalidate()
    }

    private fun scheduleThrottledRedraw() {
        if (isRenderPending.compareAndSet(false, true)) {
            renderHandler.postDelayed(renderRunnable, 16) // Cap at ~60fps for maximum smoothness
        }
    }

    private fun setupBufferCallbacks(targetBuffer: TerminalBuffer) {
        targetBuffer.onUpdate = { scheduleThrottledRedraw() }
        targetBuffer.onBell = {
            if (bellEnabled) {
                try {
                    performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                } catch (ignored: Exception) {}
            }
        }
        targetBuffer.onHistoryCleared = {
            scrollOffset = 0
            post {
                scroller.abortAnimation()
                clearSelection()
                invalidate()
            }
            postInvalidate()
        }
    }

    private fun updateFontMetrics() {
        paint.textSize = textSizeSp * resources.displayMetrics.scaledDensity
        val fm = paint.fontMetrics
        charHeight = fm.descent - fm.ascent
        charAscent = -fm.ascent
        charWidth = paint.measureText("M")
        pillTextPaint.textSize = 12f * resources.displayMetrics.scaledDensity

        if (width > 0 && height > 0 && charWidth > 0 && charHeight > 0) {
            val newCols = max(1, (width / charWidth).toInt())
            val newRows = max(1, (height / charHeight).toInt())
            if (newCols != cols || newRows != rows) {
                resize(newCols, newRows)
                onTerminalResize?.invoke(newCols, newRows)
            }
        }
    }

    private fun getLine(absRow: Int): TerminalLine? = buffer.getLine(absRow)

    // =========================================================================
    // Layout & sizing
    // =========================================================================

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (charWidth <= 0 || charHeight <= 0) return
        val newCols = max(1, (w / charWidth).toInt())
        val newRows = max(1, (h / charHeight).toInt())
        if (newCols != cols || newRows != rows) {
            resize(newCols, newRows)
            onTerminalResize?.invoke(newCols, newRows)
        }
    }

    fun resize(newCols: Int, newRows: Int) {
        if (newCols <= 0 || newRows <= 0) return
        buffer.resize(newCols, newRows)
        scrollOffset = scrollOffset.coerceIn(0, buffer.history.size)
        postInvalidate()
    }

    /**
     * Feed raw bytes into the terminal emulator buffer.
     */
    fun processOutput(data: ByteArray) {
        buffer.processOutput(data)
        postInvalidate()
    }

    // =========================================================================
    // Drawing
    // =========================================================================

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (charWidth <= 0 || charHeight <= 0) return

        canvas.drawColor(TerminalColors.DEFAULT_BG)

        synchronized(buffer) {
            val hSize = buffer.history.size
            if (scrollOffset > hSize) {
                scrollOffset = hSize
            }
            val rows = buffer.rows
            val cols = buffer.cols
            val cursorRow = buffer.cursorRow
            val cursorCol = buffer.cursorCol
            val startIndex = hSize - scrollOffset

            // 1. Draw text and background cells
            for (visualRow in 0 until rows) {
                val absRow = startIndex + visualRow
                if (absRow < 0 || absRow >= hSize + rows) continue

                val rowChars: CharArray
                val rowFg: IntArray
                val rowBg: IntArray
                val rowAttr: IntArray

                if (absRow < hSize) {
                    val hLine = buffer.history[absRow]
                    rowChars = hLine.chars
                    rowFg = hLine.fg
                    rowBg = hLine.bg
                    rowAttr = hLine.attr
                } else {
                    val screenRow = absRow - hSize
                    if (screenRow >= buffer.screen.size) continue
                    rowChars = buffer.screen[screenRow]
                    rowFg = buffer.fgColors[screenRow]
                    rowBg = buffer.bgColors[screenRow]
                    rowAttr = buffer.attributes[screenRow]
                }

                val y = visualRow * charHeight + charAscent
                val maxCols = min(cols, rowChars.size)

                for (col in 0 until maxCols) {
                    val ch = rowChars[col]
                    val fg = rowFg[col]
                    val bg = rowBg[col]
                    val attr = rowAttr[col]

                    val isReversed = (attr and 16) != 0
                    val rawFg = if (isReversed) bg else fg
                    val rawBg = if (isReversed) fg else bg

                    val drawBg = rawBg
                    val drawFg = TerminalColors.ensureContrasting(rawFg, drawBg)

                    val x = col * charWidth

                    // Draw background cell
                    if (drawBg != TerminalColors.DEFAULT_BG) {
                        paint.color = drawBg
                        paint.style = Paint.Style.FILL
                        canvas.drawRect(x, visualRow * charHeight, x + charWidth, (visualRow + 1) * charHeight, paint)
                    }

                    // Draw character
                    if (ch != ' ' && ch != '\u0000') {
                        paint.color = drawFg
                        paint.style = Paint.Style.FILL
                        paint.isFakeBoldText = (attr and 1) != 0
                        paint.isUnderlineText = (attr and 8) != 0
                        canvas.drawText(ch.toString(), x, y, paint)
                        paint.isFakeBoldText = false
                        paint.isUnderlineText = false
                    }
                }
            }

            // 2. Draw selection highlight & interactive drag handles
            if (isSelecting && selectionStart != null && selectionEnd != null) {
                val (minP, maxP) = if (selectionStart!! <= selectionEnd!!) selectionStart!! to selectionEnd!! else selectionEnd!! to selectionStart!!
                val density = resources.displayMetrics.density
                val handleRadius = (12f * density).coerceAtLeast(18f)
                val touchPad = 32f * density

                startHandleTouchRect.setEmpty()
                endHandleTouchRect.setEmpty()

                for (visualRow in 0 until rows) {
                    val absRow = startIndex + visualRow
                    if (absRow in minP.row..maxP.row) {
                        val selStartCol = if (absRow == minP.row) minP.col else 0
                        val selEndCol = if (absRow == maxP.row) maxP.col else cols - 1
                        if (selStartCol <= selEndCol) {
                            val x1 = selStartCol * charWidth
                            val x2 = (selEndCol + 1) * charWidth
                            val y1 = visualRow * charHeight
                            val y2 = (visualRow + 1) * charHeight
                            canvas.drawRect(x1, y1, x2, y2, selectionPaint)
                        }
                    }
                }

                // Draw start handle (at start of selection minP)
                val visualMinRow = minP.row - startIndex
                if (visualMinRow in 0 until rows) {
                    val startX = minP.col * charWidth
                    val topY = visualMinRow * charHeight
                    val botY = (visualMinRow + 1) * charHeight
                    canvas.drawLine(startX, topY, startX, botY + handleRadius * 0.4f, handleLinePaint)
                    val circleCenterX = startX - handleRadius * 0.35f
                    val circleCenterY = botY + handleRadius * 0.7f
                    canvas.drawCircle(circleCenterX, circleCenterY, handleRadius, handlePaint)
                    canvas.drawCircle(circleCenterX, circleCenterY, handleRadius, handleBorderPaint)

                    startHandleTouchRect.set(
                        circleCenterX - touchPad,
                        topY - touchPad * 0.5f,
                        circleCenterX + touchPad,
                        circleCenterY + touchPad
                    )
                }

                // Draw end handle (at end of selection maxP)
                val visualMaxRow = maxP.row - startIndex
                if (visualMaxRow in 0 until rows) {
                    val endX = (maxP.col + 1) * charWidth
                    val topY = visualMaxRow * charHeight
                    val botY = (visualMaxRow + 1) * charHeight
                    canvas.drawLine(endX, topY, endX, botY + handleRadius * 0.4f, handleLinePaint)
                    val circleCenterX = endX + handleRadius * 0.35f
                    val circleCenterY = botY + handleRadius * 0.7f
                    canvas.drawCircle(circleCenterX, circleCenterY, handleRadius, handlePaint)
                    canvas.drawCircle(circleCenterX, circleCenterY, handleRadius, handleBorderPaint)

                    endHandleTouchRect.set(
                        circleCenterX - touchPad,
                        topY - touchPad * 0.5f,
                        circleCenterX + touchPad,
                        circleCenterY + touchPad
                    )
                }
            } else {
                startHandleTouchRect.setEmpty()
                endHandleTouchRect.setEmpty()
            }

            // 3. Draw cursor (only when viewing live prompt at bottom)
            if (scrollOffset == 0 && cursorVisible && buffer.isCursorVisible && isFocused) {
                val cx = cursorCol * charWidth
                val cy = cursorRow * charHeight
                paint.color = TerminalColors.CURSOR_COLOR
                paint.style = Paint.Style.FILL
                canvas.drawRect(cx, cy, cx + charWidth, cy + charHeight, paint)

                val ch = buffer.screen.getOrNull(cursorRow)?.getOrNull(cursorCol) ?: ' '
                if (ch != ' ' && ch != '\u0000') {
                    paint.color = TerminalColors.ensureContrasting(TerminalColors.DEFAULT_BG, TerminalColors.CURSOR_COLOR)
                    canvas.drawText(ch.toString(), cx, cy + charAscent, paint)
                }
            }

            // 4. Draw scrollbar & floating scroll distance indicator pill
            if (hSize > 0 && scrollOffset > 0) {
                val total = hSize + rows
                val viewH = height.toFloat()
                val thumbH = (viewH * rows / total).coerceIn(36f * resources.displayMetrics.density, viewH * 0.6f)
                val scrollRatio = (hSize - scrollOffset).toFloat() / hSize
                val thumbY = scrollRatio * (viewH - thumbH)

                val sbWidth = 4f * resources.displayMetrics.density
                val sbRight = width.toFloat() - 3f * resources.displayMetrics.density
                val sbLeft = sbRight - sbWidth
                canvas.drawRoundRect(sbLeft, thumbY, sbRight, thumbY + thumbH, sbWidth / 2, sbWidth / 2, scrollbarPaint)

                // Floating "↓ N" pill badge at bottom-right
                val pillText = "↓ $scrollOffset"
                val textW = pillTextPaint.measureText(pillText)
                val pillPaddingH = 14f * resources.displayMetrics.density
                val pillW = textW + pillPaddingH * 2
                val pillH = 30f * resources.displayMetrics.density
                val pillX = width - pillW - 14f * resources.displayMetrics.density
                val pillY = height - pillH - 14f * resources.displayMetrics.density

                pillRect.set(pillX, pillY, pillX + pillW, pillY + pillH)

                pillBgPaint.style = Paint.Style.FILL
                pillBgPaint.color = Color.argb(220, 24, 28, 36)
                canvas.drawRoundRect(pillRect, pillH / 2, pillH / 2, pillBgPaint)

                pillBgPaint.style = Paint.Style.STROKE
                pillBgPaint.strokeWidth = 1.5f * resources.displayMetrics.density
                pillBgPaint.color = Color.argb(160, 88, 166, 255)
                canvas.drawRoundRect(pillRect, pillH / 2, pillH / 2, pillBgPaint)

                val textY = pillY + (pillH / 2) - ((pillTextPaint.descent() + pillTextPaint.ascent()) / 2)
                canvas.drawText(pillText, pillX + pillPaddingH, textY, pillTextPaint)
            } else {
                pillRect.setEmpty()
            }
        }
    }

    // =========================================================================
    // Scrolling physics
    // =========================================================================

    override fun computeScroll() {
        super.computeScroll()
        if (scroller.computeScrollOffset()) {
            val newOffset = scroller.currY.coerceIn(0, buffer.history.size)
            if (newOffset != scrollOffset) {
                scrollOffset = newOffset
                postInvalidateOnAnimation()
            }
        }
    }

    fun scrollToBottom() {
        if (scrollOffset != 0) {
            scrollOffset = 0
            accumulatedScrollY = 0f
            scroller.abortAnimation()
            invalidate()
        }
    }

    // =========================================================================
    // Text Selection & Clipboard Operations
    // =========================================================================

    private fun selectWordAt(x: Float, y: Float) {
        if (charWidth <= 0 || charHeight <= 0) return
        val vRow = (y / charHeight).toInt().coerceIn(0, rows - 1)
        val col = (x / charWidth).toInt().coerceIn(0, cols - 1)
        val hSize = buffer.history.size
        val absRow = (hSize - scrollOffset + vRow).coerceIn(0, hSize + rows - 1)

        val line = getLine(absRow) ?: return
        val lineChars = line.chars
        if (col >= lineChars.size) return

        fun isWordChar(c: Char): Boolean =
            c.isLetterOrDigit() || c == '_' || c == '-' || c == '.' || c == '/' || c == '~' || c == ':' || c == '@' || c == '=' || c == '+'

        val targetChar = lineChars[col]
        var startCol = col
        var endCol = col

        if (isWordChar(targetChar)) {
            while (startCol > 0 && isWordChar(lineChars[startCol - 1])) {
                startCol--
            }
            while (endCol < cols - 1 && endCol + 1 < lineChars.size && isWordChar(lineChars[endCol + 1])) {
                endCol++
            }
        } else if (targetChar != ' ' && targetChar != '\u0000') {
            startCol = col
            endCol = col
        } else {
            while (startCol > 0 && (lineChars[startCol - 1] == ' ' || lineChars[startCol - 1] == '\u0000')) {
                startCol--
            }
            while (endCol < cols - 1 && endCol + 1 < lineChars.size && (lineChars[endCol + 1] == ' ' || lineChars[endCol + 1] == '\u0000')) {
                endCol++
            }
        }

        selectionStart = TerminalPos(absRow, startCol)
        selectionEnd = TerminalPos(absRow, endCol)
        isSelecting = true
        try {
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        } catch (ignored: Exception) {}
        invalidate()
        showSelectionMenu(x, y)
    }

    fun getSelectedText(): String {
        val start = selectionStart ?: return ""
        val end = selectionEnd ?: return ""
        val (minP, maxP) = if (start <= end) start to end else end to start

        val sb = StringBuilder()
        synchronized(buffer) {
            for (r in minP.row..maxP.row) {
                val line = getLine(r) ?: continue
                val lineChars = line.chars
                val cStart = if (r == minP.row) minP.col.coerceIn(0, lineChars.size - 1) else 0
                val cEnd = if (r == maxP.row) maxP.col.coerceIn(0, lineChars.size - 1) else min(cols - 1, lineChars.size - 1)

                if (cStart <= cEnd) {
                    val lineSlice = StringBuilder()
                    for (c in cStart..cEnd) {
                        val ch = lineChars[c]
                        if (ch != '\u0000') {
                            lineSlice.append(ch)
                        } else {
                            lineSlice.append(' ')
                        }
                    }
                    var text = lineSlice.toString()
                    if (cEnd >= cols - 1 || r < maxP.row) {
                        text = text.trimEnd(' ')
                    }
                    sb.append(text)
                }
                if (r < maxP.row) {
                    sb.append("\n")
                }
            }
        }
        return sb.toString()
    }

    fun copySelection() {
        val text = getSelectedText()
        if (text.isNotEmpty()) {
            try {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                val clip = ClipData.newPlainText("Terminal Text", text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.w(TAG, "Copy failed: ${e.message}")
            }
        }
        clearSelection()
    }

    fun pasteClipboard() {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            val clip = clipboard?.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
                if (text.isNotEmpty()) {
                    pasteText(text)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Paste failed: ${e.message}")
        }
        clearSelection()
    }

    /**
     * Safely pastes text into the terminal:
     * - Normalizes CRLF and CR to standard newline
     * - Strips all trailing newlines and carriage returns so pasted commands NEVER auto-execute
     * - Wraps with bracketed paste mode escape sequences (\e[200~ ... \e[201~) when enabled by shell
     */
    fun pasteText(rawText: String) {
        if (rawText.isEmpty()) return
        val normalized = rawText.replace("\r\n", "\n").replace("\r", "\n")

        // CRITICAL: Strip any trailing newlines or carriage returns so commands DO NOT execute automatically
        val cleanText = normalized.trimEnd('\n', '\r')
        if (cleanText.isEmpty()) return

        // If bracketed paste mode is active (DECSET 2004), wrap in \e[200~ and \e[201~
        val payload = if (bracketedPasteMode) {
            "\u001B[200~$cleanText\u001B[201~"
        } else {
            cleanText
        }

        sendInput(payload)
        scrollToBottom()
    }

    fun selectAll() {
        val totalLines = buffer.history.size + rows
        selectionStart = TerminalPos(0, 0)
        selectionEnd = TerminalPos(totalLines - 1, cols - 1)
        isSelecting = true
        invalidate()
        actionMode?.invalidate()
    }

    fun shareSelection() {
        val text = getSelectedText()
        if (text.isNotEmpty()) {
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, text)
                type = "text/plain"
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(sendIntent, "Share Terminal Text").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        }
        clearSelection()
    }

    fun clearSelection() {
        if (isSelecting || selectionStart != null || selectionEnd != null) {
            isSelecting = false
            selectionStart = null
            selectionEnd = null
            activeDraggingHandle = DraggingHandle.NONE
            startHandleTouchRect.setEmpty()
            endHandleTouchRect.setEmpty()
            actionMode?.finish()
            actionMode = null
            dismissCustomPopup()
            invalidate()
        }
    }

    fun clearHistory() {
        buffer.clearHistory()
        scrollOffset = 0
        scroller.abortAnimation()
        clearSelection()
        postInvalidate()
    }

    // -------------------------------------------------------------------------
    // Floating Context Menu & Action Mode
    // -------------------------------------------------------------------------

    private fun showSelectionMenu(touchX: Float, touchY: Float) {
        if (actionMode != null) {
            actionMode?.invalidate()
            return
        }

        val callback = object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                mode.title = null
                menu.add(Menu.NONE, ID_COPY, 1, context.getString(android.R.string.copy))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(Menu.NONE, ID_PASTE, 2, context.getString(android.R.string.paste))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
                menu.add(Menu.NONE, ID_SELECT_ALL, 3, context.getString(android.R.string.selectAll))
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                menu.add(Menu.NONE, ID_SHARE, 4, "Share")
                    .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                menu.findItem(ID_PASTE)?.isEnabled = clipboard?.hasPrimaryClip() == true
                menu.findItem(ID_COPY)?.isEnabled = getSelectedText().isNotEmpty()
                return true
            }

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    ID_COPY -> { copySelection(); mode.finish(); return true }
                    ID_PASTE -> { pasteClipboard(); mode.finish(); return true }
                    ID_SELECT_ALL -> { selectAll(); return true }
                    ID_SHARE -> { shareSelection(); mode.finish(); return true }
                }
                return false
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                actionMode = null
                clearSelection()
            }

            override fun onGetContentRect(mode: ActionMode?, view: View?, outRect: Rect?) {
                if (outRect != null && selectionStart != null && selectionEnd != null) {
                    val (minP, maxP) = if (selectionStart!! <= selectionEnd!!) selectionStart!! to selectionEnd!! else selectionEnd!! to selectionStart!!
                    val startIndex = buffer.history.size - scrollOffset
                    val vStartRow = (minP.row - startIndex).coerceIn(0, rows - 1)
                    val vEndRow = (maxP.row - startIndex).coerceIn(0, rows - 1)

                    val left = (min(minP.col, maxP.col) * charWidth).toInt().coerceIn(0, width)
                    val right = ((max(minP.col, maxP.col) + 1) * charWidth).toInt().coerceIn(0, width)
                    val top = (vStartRow * charHeight).toInt().coerceIn(0, height)
                    val bottom = ((vEndRow + 1) * charHeight).toInt().coerceIn(0, height)

                    outRect.set(min(left, right), min(top, bottom), max(left, right), max(top, bottom))
                } else {
                    outRect?.set(0, 0, width, (charHeight * 2).toInt())
                }
            }
        }

        try {
            actionMode = startActionMode(callback, ActionMode.TYPE_FLOATING)
            if (actionMode == null) {
                actionMode = startActionMode(callback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to start action mode: ${e.message}")
        }

        if (actionMode == null) {
            showCustomPopup(touchX, touchY)
        }
    }

    private fun showCustomPopup(x: Float, y: Float) {
        dismissCustomPopup()
        val density = resources.displayMetrics.density
        val popupView = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            val pad = (6 * density).toInt()
            setPadding(pad, pad / 2, pad, pad / 2)
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#21262D"))
                cornerRadius = 10 * density
                setStroke((1 * density).toInt(), Color.parseColor("#388BFD"))
            }
            elevation = 12 * density

            fun createBtn(title: String, onClick: () -> Unit) {
                val btn = TextView(context).apply {
                    text = title
                    setTextColor(Color.WHITE)
                    textSize = 13f
                    setTypeface(null, Typeface.BOLD)
                    val p = (10 * density).toInt()
                    setPadding(p, p / 2, p, p / 2)
                    isClickable = true
                    isFocusable = true
                    setOnClickListener {
                        onClick()
                        dismissCustomPopup()
                    }
                }
                addView(btn)
            }

            createBtn("Copy") { copySelection() }
            createBtn("Paste") { pasteClipboard() }
            createBtn("Select All") { selectAll() }
            createBtn("Share") { shareSelection() }
        }

        customPopup = PopupWindow(
            popupView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            showAtLocation(this@TerminalView, Gravity.NO_GRAVITY, x.toInt(), max(0, y.toInt() - (54 * density).toInt()))
        }
    }

    fun dismissCustomPopup() {
        customPopup?.dismiss()
        customPopup = null
    }

    // =========================================================================
    // Touch & Gesture Handling
    // =========================================================================

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Handle dragging selection handles or updating selection
        if (isSelecting && selectionStart != null && selectionEnd != null) {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (startHandleTouchRect.contains(event.x, event.y)) {
                        activeDraggingHandle = DraggingHandle.START
                        dismissCustomPopup()
                        actionMode?.hide(ActionMode.DEFAULT_HIDE_DURATION.toLong())
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    } else if (endHandleTouchRect.contains(event.x, event.y)) {
                        activeDraggingHandle = DraggingHandle.END
                        dismissCustomPopup()
                        actionMode?.hide(ActionMode.DEFAULT_HIDE_DURATION.toLong())
                        parent?.requestDisallowInterceptTouchEvent(true)
                        return true
                    } else {
                        // Tapped outside handles — clear selection and allow normal gesture
                        clearSelection()
                    }
                }
                MotionEvent.ACTION_MOVE -> {
                    if (activeDraggingHandle != DraggingHandle.NONE) {
                        val vRow = (event.y / charHeight).toInt().coerceIn(0, rows - 1)
                        val col = (event.x / charWidth).toInt().coerceIn(0, cols - 1)
                        val hSize = buffer.history.size
                        val absRow = (hSize - scrollOffset + vRow).coerceIn(0, hSize + rows - 1)
                        val newPos = TerminalPos(absRow, col)

                        if (activeDraggingHandle == DraggingHandle.START) {
                            if (newPos != selectionStart) {
                                selectionStart = newPos
                                invalidate()
                            }
                        } else if (activeDraggingHandle == DraggingHandle.END) {
                            if (newPos != selectionEnd) {
                                selectionEnd = newPos
                                invalidate()
                            }
                        }
                        return true
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (activeDraggingHandle != DraggingHandle.NONE) {
                        activeDraggingHandle = DraggingHandle.NONE
                        parent?.requestDisallowInterceptTouchEvent(false)
                        invalidate()
                        showSelectionMenu(event.x, event.y)
                        return true
                    }
                }
            }
        }

        if (event.actionMasked == MotionEvent.ACTION_DOWN) {
            scroller.abortAnimation()
            accumulatedScrollY = 0f
            if (isSelecting) {
                clearSelection()
            }
        }

        // Tap on floating "↓ N" pill badge jumps directly to bottom
        if (event.actionMasked == MotionEvent.ACTION_UP && scrollOffset > 0) {
            if (pillRect.contains(event.x, event.y)) {
                scrollToBottom()
                return true
            }
        }

        val gestureHandled = gestureDetector.onTouchEvent(event)
        val scaleHandled = scaleGestureDetector.onTouchEvent(event)

        if (event.action == MotionEvent.ACTION_UP) {
            if (!isSelecting) {
                requestFocus()
            }
        }
        return gestureHandled || scaleHandled || true
    }

    private inner class TerminalGestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, dx: Float, dy: Float): Boolean {
            if (isSelecting || buffer.isAlternateBuffer) return false
            accumulatedScrollY += dy
            val rowDelta = (accumulatedScrollY / charHeight).toInt()
            if (rowDelta != 0) {
                val newOffset = (scrollOffset - rowDelta).coerceIn(0, buffer.history.size)
                if (newOffset != scrollOffset) {
                    scrollOffset = newOffset
                    accumulatedScrollY -= rowDelta * charHeight
                    invalidate()
                } else {
                    accumulatedScrollY = 0f
                }
            }
            return true
        }

        override fun onFling(e1: MotionEvent?, e2: MotionEvent, velocityX: Float, velocityY: Float): Boolean {
            if (isSelecting || buffer.isAlternateBuffer) return false
            scroller.abortAnimation()
            scroller.fling(
                0, scrollOffset,
                0, (velocityY / charHeight).roundToInt(),
                0, 0,
                0, buffer.history.size
            )
            postInvalidateOnAnimation()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            selectWordAt(e.x, e.y)
        }

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            if (isSelecting) {
                clearSelection()
                return true
            }
            requestFocus()
            showSoftKeyboard()
            return true
        }

        override fun onDoubleTap(e: MotionEvent): Boolean {
            if (scrollOffset > 0) {
                scrollToBottom()
                return true
            }
            return false
        }
    }

    private inner class TerminalScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            textSizeSp = (textSizeSp * detector.scaleFactor).coerceIn(8f, 32f)
            return true
        }
    }

    // =========================================================================
    // Focus, Keyboard & IME
    // =========================================================================

    override fun onFocusChanged(gainFocus: Boolean, direction: Int, previouslyFocusedRect: Rect?) {
        super.onFocusChanged(gainFocus, direction, previouslyFocusedRect)
        if (gainFocus) {
            showSoftKeyboard()
            post(cursorBlinkRunnable)
        } else {
            removeCallbacks(cursorBlinkRunnable)
            cursorVisible = true
            invalidate()
        }
    }

    fun showSoftKeyboard() {
        post {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
        }
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = InputType.TYPE_NULL
        outAttrs.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return TerminalInputConnection(this)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    fun sendInput(text: String) {
        scrollToBottom()
        onInputListener?.invoke(text.toByteArray(Charsets.UTF_8))
    }

    fun sendInput(bytes: ByteArray) {
        scrollToBottom()
        onInputListener?.invoke(bytes)
    }

    fun sendKey(key: SpecialKey) {
        scrollToBottom()
        onInputListener?.invoke(key.bytes)
    }

    fun sendCtrl(char: Char) {
        scrollToBottom()
        val code = (char.uppercaseChar().code and 0x1F).toByte()
        onInputListener?.invoke(byteArrayOf(code))
    }

    fun sendSpecialKey(key: SpecialKey) {
        scrollToBottom()
        if (key == SpecialKey.TAB && isShiftActive) {
            isShiftActive = false
            sendInput("\u001B[Z") // Back-tab / Reverse-tab
            return
        }
        if (isCtrlActive) {
            isCtrlActive = false
            when (key) {
                SpecialKey.ARROW_UP -> sendInput("\u001B[1;5A")
                SpecialKey.ARROW_DOWN -> sendInput("\u001B[1;5B")
                SpecialKey.ARROW_RIGHT -> sendInput("\u001B[1;5C")
                SpecialKey.ARROW_LEFT -> sendInput("\u001B[1;5D")
                SpecialKey.HOME -> sendInput("\u001B[1;5H")
                SpecialKey.END -> sendInput("\u001B[1;5F")
                SpecialKey.DELETE -> sendInput("\u001B[3;5~")
                SpecialKey.BACKSPACE -> sendInput("\u0017")
                else -> sendKey(key)
            }
            return
        }
        if (isAltActive) {
            isAltActive = false
            when (key) {
                SpecialKey.ARROW_UP -> sendInput("\u001B[1;3A")
                SpecialKey.ARROW_DOWN -> sendInput("\u001B[1;3B")
                SpecialKey.ARROW_RIGHT -> sendInput("\u001B[1;3C")
                SpecialKey.ARROW_LEFT -> sendInput("\u001B[1;3D")
                SpecialKey.BACKSPACE -> sendInput("\u001B\u007F")
                else -> {
                    sendInput(byteArrayOf(27) + key.bytes)
                }
            }
            return
        }
        if (buffer.applicationCursorKeys) {
            when (key) {
                SpecialKey.ARROW_UP -> { sendInput("\u001BOA"); return }
                SpecialKey.ARROW_DOWN -> { sendInput("\u001BOB"); return }
                SpecialKey.ARROW_RIGHT -> { sendInput("\u001BOC"); return }
                SpecialKey.ARROW_LEFT -> { sendInput("\u001BOD"); return }
                else -> {}
            }
        }
        sendKey(key)
    }

    fun handleCharacterInput(ch: Char) {
        scrollToBottom()
        if (isCtrlActive) {
            isCtrlActive = false
            val ctrlByte: Byte = when (ch) {
                in 'a'..'z' -> (ch.code - 'a'.code + 1).toByte()
                in 'A'..'Z' -> (ch.code - 'A'.code + 1).toByte()
                '@', ' ' -> 0
                '[' -> 27
                '\\' -> 28
                ']' -> 29
                '^' -> 30
                '_' -> 31
                '?' -> 127
                else -> (ch.uppercaseChar().code and 0x1F).toByte()
            }
            onInputListener?.invoke(byteArrayOf(ctrlByte))
            return
        }
        if (isAltActive) {
            isAltActive = false
            val bytes = byteArrayOf(27) + ch.toString().toByteArray(Charsets.UTF_8)
            onInputListener?.invoke(bytes)
            return
        }
        if (isShiftActive) {
            isShiftActive = false
            val shifted = when (ch) {
                in 'a'..'z' -> ch.uppercaseChar()
                '1' -> '!'
                '2' -> '@'
                '3' -> '#'
                '4' -> '$'
                '5' -> '%'
                '6' -> '^'
                '7' -> '&'
                '8' -> '*'
                '9' -> '('
                '0' -> ')'
                '-' -> '_'
                '=' -> '+'
                '[' -> '{'
                ']' -> '}'
                '\\' -> '|'
                ';' -> ':'
                '\'' -> '"'
                ',' -> '<'
                '.' -> '>'
                '/' -> '?'
                '`' -> '~'
                else -> ch
            }
            onInputListener?.invoke(shifted.toString().toByteArray(Charsets.UTF_8))
            return
        }
        onInputListener?.invoke(ch.toString().toByteArray(Charsets.UTF_8))
    }

    fun handleKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_MULTIPLE && event.keyCode == KeyEvent.KEYCODE_UNKNOWN) {
            event.characters?.let { chars ->
                if (chars.length > 1) {
                    pasteText(chars)
                } else {
                    for (ch in chars) {
                        handleCharacterInput(ch)
                    }
                }
                return true
            }
        }
        if (event.action != KeyEvent.ACTION_DOWN) return false

        if (event.isCtrlPressed) isCtrlActive = true
        if (event.isAltPressed) isAltActive = true
        if (event.isShiftPressed) isShiftActive = true

        // Hardware keyboard shortcuts: Ctrl+Shift+C (Copy), Ctrl+Shift+V / Shift+Insert (Paste)
        if (isCtrlActive && isShiftActive && event.keyCode == KeyEvent.KEYCODE_C) {
            isCtrlActive = false
            isShiftActive = false
            copySelection()
            return true
        }
        if ((isCtrlActive && isShiftActive && event.keyCode == KeyEvent.KEYCODE_V) ||
            (isShiftActive && event.keyCode == KeyEvent.KEYCODE_INSERT)) {
            isCtrlActive = false
            isShiftActive = false
            pasteClipboard()
            return true
        }

        return when (event.keyCode) {
            KeyEvent.KEYCODE_ENTER -> {
                isCtrlActive = false
                isAltActive = false
                sendKey(SpecialKey.ENTER)
                true
            }
            KeyEvent.KEYCODE_TAB -> {
                sendSpecialKey(SpecialKey.TAB)
                true
            }
            KeyEvent.KEYCODE_DEL -> {
                if (isCtrlActive) {
                    isCtrlActive = false
                    sendInput("\u0017")
                } else {
                    sendKey(SpecialKey.BACKSPACE)
                }
                true
            }
            KeyEvent.KEYCODE_FORWARD_DEL -> { sendSpecialKey(SpecialKey.DELETE); true }
            KeyEvent.KEYCODE_ESCAPE -> { sendSpecialKey(SpecialKey.ESC); true }
            KeyEvent.KEYCODE_DPAD_UP -> { sendSpecialKey(SpecialKey.ARROW_UP); true }
            KeyEvent.KEYCODE_DPAD_DOWN -> { sendSpecialKey(SpecialKey.ARROW_DOWN); true }
            KeyEvent.KEYCODE_DPAD_LEFT -> { sendSpecialKey(SpecialKey.ARROW_LEFT); true }
            KeyEvent.KEYCODE_DPAD_RIGHT -> { sendSpecialKey(SpecialKey.ARROW_RIGHT); true }
            KeyEvent.KEYCODE_PAGE_UP -> { sendSpecialKey(SpecialKey.PAGE_UP); true }
            KeyEvent.KEYCODE_PAGE_DOWN -> { sendSpecialKey(SpecialKey.PAGE_DOWN); true }
            KeyEvent.KEYCODE_MOVE_HOME -> { sendSpecialKey(SpecialKey.HOME); true }
            KeyEvent.KEYCODE_MOVE_END -> { sendSpecialKey(SpecialKey.END); true }
            else -> {
                val uchar = event.keyCharacterMap.get(event.keyCode, event.metaState)
                if (uchar > 0) {
                    handleCharacterInput(uchar.toChar())
                    true
                } else {
                    false
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (handleKeyEvent(event)) return true
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyMultiple(keyCode: Int, repeatCount: Int, event: KeyEvent): Boolean {
        if (handleKeyEvent(event)) return true
        return super.onKeyMultiple(keyCode, repeatCount, event)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (isFocused) post(cursorBlinkRunnable)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        removeCallbacks(cursorBlinkRunnable)
        renderHandler.removeCallbacks(renderRunnable)
        isRenderPending.set(false)
        dismissCustomPopup()
    }

    fun getTerminalCols() = cols
    fun getTerminalRows() = rows
}

/**
 * InputConnection for the terminal — routes keyboard input to the process.
 * Inherits from BaseInputConnection with fullEditor = false (dummyMode)
 * so software keyboards (GBoard, Samsung, Xiaomi) do not get trapped in composing mode.
 */
private class TerminalInputConnection(
    private val terminalView: TerminalView
) : BaseInputConnection(terminalView, false) {

    override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (!text.isNullOrEmpty()) {
            if (text.length > 1) {
                // Multi-character input from IME (clipboard paste, suggestion strip, etc.)
                terminalView.pasteText(text.toString())
            } else {
                terminalView.handleCharacterInput(text[0])
            }
        }
        return true
    }

    override fun setComposingText(text: CharSequence?, newCursorPosition: Int): Boolean {
        if (!text.isNullOrEmpty()) {
            if (text.length > 1 && (text.contains('\n') || text.contains('\r'))) {
                terminalView.pasteText(text.toString())
            } else {
                for (i in 0 until text.length) {
                    terminalView.handleCharacterInput(text[i])
                }
            }
        }
        return true
    }

    override fun setComposingRegion(start: Int, end: Int): Boolean = true

    override fun finishComposingText(): Boolean = true

    override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
        if (terminalView.isCtrlActive) {
            terminalView.isCtrlActive = false
            terminalView.sendInput("\u0017")
            return true
        }
        repeat(beforeLength) {
            terminalView.sendKey(SpecialKey.BACKSPACE)
        }
        return true
    }

    override fun sendKeyEvent(event: KeyEvent?): Boolean {
        if (event == null) return false
        return terminalView.handleKeyEvent(event) || super.sendKeyEvent(event)
    }
}
