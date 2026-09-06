package com.mobilelinux.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.mobilelinux.terminal.TerminalManager
import com.mobilelinux.terminal.TerminalSession
import com.mobilelinux.terminal.SpecialKey
import kotlinx.coroutines.flow.StateFlow

/**
 * Shared ViewModel for MainActivity — coordinates session state across fragments.
 */
class MainViewModel(private val terminalManager: TerminalManager) : ViewModel() {

    val sessions: StateFlow<List<TerminalSession>> = terminalManager.sessions
    val activeSessionId: StateFlow<String?> = terminalManager.activeSessionId

    fun createSession(name: String? = null): TerminalSession {
        return terminalManager.createSession(name)
    }

    fun setActiveSession(sessionId: String) {
        terminalManager.setActiveSession(sessionId)
    }

    fun closeSession(sessionId: String, autoCreateFallback: Boolean = false): Int {
        return terminalManager.closeSession(sessionId, autoCreateFallback)
    }

    fun onSessionProcessExited(sessionId: String): Int {
        return terminalManager.closeSession(sessionId, autoCreateFallback = false)
    }

    fun renameSession(sessionId: String, newName: String) {
        terminalManager.renameSession(sessionId, newName)
    }

    fun sendInput(sessionId: String, data: ByteArray) {
        terminalManager.sendInput(sessionId, data)
    }

    fun sendKey(sessionId: String, key: SpecialKey) {
        terminalManager.sendKey(sessionId, key)
    }

    fun resizeSession(sessionId: String, cols: Int, rows: Int) {
        terminalManager.resizeSession(sessionId, cols, rows)
    }

    fun getSessionProcess(sessionId: String) = terminalManager.getSessionProcess(sessionId)

    override fun onCleared() {
        super.onCleared()
        // Don't kill sessions on ViewModel clear — service handles lifecycle
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MainViewModel(TerminalManager.getInstance(context)) as T
        }
    }
}
