package com.mobilelinux.terminal

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

/**
 * Represents a single terminal session (one proot/chroot process).
 */
@Parcelize
data class TerminalSession(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Terminal",
    var isAlive: Boolean = false,
    var errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis()
) : Parcelable {

    companion object {
        fun createNamed(name: String) = TerminalSession(
            id = UUID.randomUUID().toString(),
            name = name
        )
    }
}

/**
 * Holds the running state of a session: process + I/O streams.
 */
class SessionProcess(
    val session: TerminalSession,
    val process: Process
) {
    val terminalBuffer = TerminalBuffer()
    var readerJob: kotlinx.coroutines.Job? = null
    var onProcessExited: (() -> Unit)? = null

    val outputStream get() = process.outputStream  // write to stdin
    val inputStream get() = process.inputStream    // read from stdout
    val errorStream get() = process.errorStream    // read from stderr

    val isAlive: Boolean get() = try {
        process.exitValue()
        false
    } catch (e: IllegalThreadStateException) {
        true
    }

    fun kill() {
        readerJob?.cancel()
        try {
            outputStream.close()
        } catch (ignored: Exception) {}
        try {
            inputStream.close()
        } catch (ignored: Exception) {}
        try {
            errorStream.close()
        } catch (ignored: Exception) {}
        try {
            process.destroyForcibly()
        } catch (e: Exception) {
            try { process.destroy() } catch (ignored: Exception) {}
        }
    }
}
