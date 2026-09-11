package com.mobilelinux.terminal

import android.content.Context
import android.util.Log
import com.mobilelinux.runtime.UbuntuRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Manages all active terminal sessions.
 * Acts as the single source of truth for session state.
 */
class TerminalManager(private val context: Context) {

    private val TAG = "TerminalManager"
    private val runtime = UbuntuRuntime.getInstance(context)

    private val _sessions = MutableStateFlow<List<TerminalSession>>(emptyList())
    val sessions: StateFlow<List<TerminalSession>> = _sessions.asStateFlow()

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _sessionReadyFlow = MutableSharedFlow<String>(replay = 1, extraBufferCapacity = 16)
    val sessionReadyFlow: SharedFlow<String> = _sessionReadyFlow.asSharedFlow()

    // Map of session ID -> active process
    private val sessionProcesses = mutableMapOf<String, SessionProcess>()

    /**
     * Estimates dynamic terminal dimensions based on the physical device screen and font size.
     * Prevents processes from starting in legacy 80x24 mode on mobile portrait viewports.
     */
    fun calculateDefaultDimensions(): Pair<Int, Int> {
        return try {
            val dm = context.resources.displayMetrics
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val fontSizeSp = prefs.getInt("pref_font_size", 14).toFloat().coerceIn(8f, 32f)

            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                try {
                    typeface = android.graphics.Typeface.createFromAsset(context.assets, "fonts/JetBrainsMono-Regular.ttf")
                } catch (e: Exception) {
                    typeface = android.graphics.Typeface.MONOSPACE
                }
                textSize = fontSizeSp * dm.scaledDensity
                isSubpixelText = true
            }
            val fm = paint.fontMetrics
            val charHeight = fm.descent - fm.ascent
            val charWidth = paint.measureText("M")

            val widthPx = dm.widthPixels
            // Reserved UI height: toolbar (~56dp) + extra keys bar (~88dp) + system bars (~48dp)
            val density = dm.density
            val reservedHeightPx = (56f + 88f + 48f) * density
            val availableHeightPx = (dm.heightPixels - reservedHeightPx).coerceAtLeast(200f)

            val cols = if (charWidth > 0) (widthPx / charWidth).toInt().coerceIn(20, 200) else 46
            val rows = if (charHeight > 0) (availableHeightPx / charHeight).toInt().coerceIn(10, 100) else 42
            Pair(cols, rows)
        } catch (e: Exception) {
            Pair(46, 42)
        }
    }

    /**
     * Creates a new terminal session and starts the Ubuntu process.
     */
    fun createSession(
        name: String? = null,
        cols: Int? = null,
        rows: Int? = null,
        initialDir: String? = null
    ): TerminalSession {
        val (defCols, defRows) = calculateDefaultDimensions()
        val effectiveCols = cols?.takeIf { it > 0 } ?: defCols
        val effectiveRows = rows?.takeIf { it > 0 } ?: defRows

        val sessionNumber = _sessions.value.size + 1
        val session = TerminalSession.createNamed(name ?: "Session $sessionNumber")

        try {
            val process = runtime.createSessionProcess(
                sessionId = session.id,
                cols = effectiveCols,
                rows = effectiveRows,
                initialWorkingDir = initialDir
            )
            val sessionProcess = SessionProcess(session, process)
            sessionProcess.terminalBuffer.resize(effectiveCols, effectiveRows)
            sessionProcesses[session.id] = sessionProcess
            session.isAlive = true

            // Start background I/O reader for this session immediately
            startSessionReader(sessionProcess)

            Log.d(TAG, "Created session: ${session.id} (${session.name}) in ${initialDir ?: "default"} with size ${effectiveCols}x$effectiveRows")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create session process: ${e.message}", e)
            session.isAlive = false
            session.errorMessage = e.message ?: "Process creation failed"
        }

        val updated = _sessions.value + session
        _sessions.value = updated

        // Always activate newly created session so UI immediately switches to it
        _activeSessionId.value = session.id

        return session
    }

    /**
     * Asynchronously creates a session on Dispatchers.IO to prevent freezing the UI thread / splash screen.
     */
    suspend fun createSessionAsync(
        name: String? = null,
        cols: Int? = null,
        rows: Int? = null,
        initialDir: String? = null
    ): TerminalSession =
        withContext(Dispatchers.IO) {
            createSession(name, cols, rows, initialDir)
        }

    private fun startSessionReader(sessionProcess: SessionProcess) {
        // Wire terminal emulator response (e.g. cursor position report) to session stdin
        sessionProcess.terminalBuffer.onResponse = { data ->
            sendInput(sessionProcess.session.id, data)
        }

        sessionProcess.terminalBuffer.onOutputReceived = { hasPrompt ->
            if (hasPrompt) {
                _sessionReadyFlow.tryEmit(sessionProcess.session.id)
            }
        }

        sessionProcess.readerJob = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(4096)
            val inputStream = sessionProcess.inputStream
            try {
                while (isActive) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break
                    if (bytesRead > 0) {
                        val data = buffer.copyOf(bytesRead)
                        sessionProcess.terminalBuffer.processOutput(data)
                        if (sessionProcess.terminalBuffer.hasPromptOrBanner() || sessionProcess.terminalBuffer.hasAnyVisibleContent()) {
                            _sessionReadyFlow.tryEmit(sessionProcess.session.id)
                        }
                    }
                }
            } catch (e: Exception) {
                // Stream closed
            }

            // Shell process terminated (e.g. exit command or EOF)
            withContext(Dispatchers.Main) {
                sessionProcess.onProcessExited?.invoke()
            }
        }
    }

    /**
     * Returns the [SessionProcess] for a given session ID.
     */
    fun getSessionProcess(sessionId: String): SessionProcess? {
        return sessionProcesses[sessionId]
    }

    /**
     * Switches the active session.
     */
    fun setActiveSession(sessionId: String) {
        if (_sessions.value.any { it.id == sessionId }) {
            _activeSessionId.value = sessionId
            Log.d(TAG, "Active session: $sessionId")
        }
    }

    /**
     * Renames a session.
     */
    fun renameSession(sessionId: String, newName: String) {
        val updated = _sessions.value.map { session ->
            if (session.id == sessionId) session.copy(name = newName) else session
        }
        _sessions.value = updated
    }

    /**
     * Closes and removes a session.
     * @param sessionId The ID of the session to close
     * @param autoCreateFallback If true, creates a new session when none remain. Default is false (allows session count to reach 0).
     * @return Number of remaining sessions.
     */
    fun closeSession(sessionId: String, autoCreateFallback: Boolean = false): Int {
        sessionProcesses[sessionId]?.kill()
        sessionProcesses.remove(sessionId)

        val updated = _sessions.value.filter { it.id != sessionId }
        _sessions.value = updated

        // Switch to another session if the active one was closed
        if (_activeSessionId.value == sessionId) {
            _activeSessionId.value = updated.lastOrNull()?.id
        }

        // When all sessions have closed, clean up stale sockets and locks immediately
        if (updated.isEmpty()) {
            CoroutineScope(Dispatchers.IO).launch {
                runtime.cleanupStaleProotArtifacts()
            }
        }

        // Keep at least one session alive only if explicitly requested
        if (autoCreateFallback && updated.isEmpty()) {
            createSession("Main")
            return _sessions.value.size
        }

        Log.d(TAG, "Closed session: $sessionId, remaining: ${updated.size}")
        return updated.size
    }

    /**
     * Sends input (text/keypress) to the active session.
     */
    fun sendInput(sessionId: String, input: ByteArray) {
        try {
            sessionProcesses[sessionId]?.outputStream?.apply {
                write(input)
                flush()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send input to session $sessionId: ${e.message}")
        }
    }

    /**
     * Sends a special key sequence to the active session.
     */
    fun sendKey(sessionId: String, key: SpecialKey) {
        sendInput(sessionId, key.bytes)
    }

    /**
     * Updates terminal dimensions when screen size changes.
     */
    fun resizeSession(sessionId: String, cols: Int, rows: Int) {
        val sessionProcess = sessionProcesses[sessionId] ?: return
        sessionProcess.terminalBuffer.resize(cols, rows)
        // Send dynamic resize control sequence to PTY bridge (\x00\x1B[9999;rows;colsR\x00)
        val resizeBytes = byteArrayOf(0, 27) + "[9999;${rows};${cols}R".toByteArray(Charsets.ISO_8859_1) + byteArrayOf(0)
        sendInput(sessionId, resizeBytes)
        Log.d(TAG, "Resized session $sessionId to ${cols}x$rows")
    }

    /**
     * Kills all sessions (called on service stop).
     */
    fun killAllSessions() {
        sessionProcesses.values.forEach { it.kill() }
        sessionProcesses.clear()
        _sessions.value = emptyList()
        _activeSessionId.value = null
        CoroutineScope(Dispatchers.IO).launch {
            runtime.cleanupStaleProotArtifacts()
        }
        Log.d(TAG, "All sessions killed and proot artifacts cleaned")
    }

    /**
     * Trims scrollback history across all active and background sessions during memory pressure.
     */
    fun trimAllSessionsMemory(maxLines: Int) {
        sessionProcesses.values.forEach { sp ->
            sp.terminalBuffer.trimHistory(maxLines)
        }
        Log.d(TAG, "Trimmed scrollback across all ${sessionProcesses.size} sessions to $maxLines lines")
    }

    /**
     * Returns count of alive sessions.
     */
    fun getAliveSessionCount(): Int {
        return sessionProcesses.values.count { it.isAlive }
    }

    companion object {
        @Volatile
        private var instance: TerminalManager? = null

        fun getInstance(context: Context): TerminalManager {
            return instance ?: synchronized(this) {
                instance ?: TerminalManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

/**
 * Special terminal key sequences.
 */
enum class SpecialKey(val bytes: ByteArray) {
    CTRL_C(byteArrayOf(3)),
    CTRL_D(byteArrayOf(4)),
    CTRL_L(byteArrayOf(12)),
    CTRL_Z(byteArrayOf(26)),
    TAB(byteArrayOf(9)),
    ESC(byteArrayOf(27)),
    ENTER(byteArrayOf(13)),
    BACKSPACE(byteArrayOf(127)),
    ARROW_UP(byteArrayOf(27, 91, 65)),
    ARROW_DOWN(byteArrayOf(27, 91, 66)),
    ARROW_RIGHT(byteArrayOf(27, 91, 67)),
    ARROW_LEFT(byteArrayOf(27, 91, 68)),
    PAGE_UP(byteArrayOf(27, 91, 53, 126)),
    PAGE_DOWN(byteArrayOf(27, 91, 54, 126)),
    HOME(byteArrayOf(27, 91, 72)),
    END(byteArrayOf(27, 91, 70)),
    DELETE(byteArrayOf(27, 91, 51, 126));

    companion object {
        fun ctrl(char: Char): ByteArray {
            val code = char.code and 0x1F
            return byteArrayOf(code.toByte())
        }
    }
}
