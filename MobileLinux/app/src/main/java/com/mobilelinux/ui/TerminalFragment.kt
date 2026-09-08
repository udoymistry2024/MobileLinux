package com.mobilelinux.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.mobilelinux.R
import com.mobilelinux.terminal.SpecialKey
import com.mobilelinux.terminal.TerminalView
import com.mobilelinux.ui.ExtraKeysView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.mobilelinux.service.LinuxService

/**
 * Fragment hosting the TerminalView for a specific session.
 * Attaches directly to the persistent SessionProcess and TerminalBuffer.
 */
class TerminalFragment : Fragment() {

    companion object {
        private const val ARG_SESSION_ID = "session_id"

        fun newInstance(sessionId: String): TerminalFragment {
            return TerminalFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_SESSION_ID, sessionId)
                }
            }
        }
    }

    private val viewModel: MainViewModel by activityViewModels {
        MainViewModel.Factory(requireContext())
    }

    private lateinit var terminalView: TerminalView
    private lateinit var extraKeysView: ExtraKeysView
    private var sessionId: String? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_terminal, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sessionId = arguments?.getString(ARG_SESSION_ID)
        terminalView = view.findViewById(R.id.terminal_view)
        extraKeysView = view.findViewById(R.id.extra_keys_view)
        val extraKeysContainer = view.findViewById<View>(R.id.extra_keys_container)

        // Protect extra keys from Android navigation bar (Back/Home) and Soft Keyboard (IME)
        ViewCompat.setOnApplyWindowInsetsListener(view) { _, windowInsets ->
            val navAndImeInsets = windowInsets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.ime() or
                WindowInsetsCompat.Type.displayCutout()
            )
            extraKeysContainer.updatePadding(bottom = navAndImeInsets.bottom)
            view.updatePadding(
                left = navAndImeInsets.left,
                right = navAndImeInsets.right
            )
            windowInsets
        }

        setupTerminalInput()
        setupExtraKeys()

        // Attach persistent terminal buffer for this session
        sessionId?.let { attachToSession(it) }

        // Apply user preferences (theme, font size, bell)
        terminalView.applyPreferences()

        // Focus terminal
        terminalView.requestFocus()
    }

    /**
     * Dynamically switches the view to another session without destroying the fragment or views.
     */
    fun switchToSession(newSessionId: String) {
        sessionId = newSessionId
        arguments?.putString(ARG_SESSION_ID, newSessionId)
        attachToSession(newSessionId)
    }

    private fun attachToSession(targetSessionId: String) {
        val sessionProcess = viewModel.getSessionProcess(targetSessionId)
        if (sessionProcess != null) {
            terminalView.attachBuffer(sessionProcess.terminalBuffer)
            sessionProcess.onProcessExited = {
                handleProcessExited(targetSessionId)
            }
        } else {
            val err = viewModel.sessions.value.find { it.id == targetSessionId }?.errorMessage
                ?: "Terminal process not available"
            terminalView.processOutput("\r\n\u001B[1;31m[ERROR: $err]\u001B[0m\r\n".toByteArray())
        }
        val cols = terminalView.getTerminalCols()
        val rows = terminalView.getTerminalRows()
        if (cols > 0 && rows > 0 && sessionProcess != null) {
            val buf = sessionProcess.terminalBuffer
            if (buf.cols != cols || buf.rows != rows) {
                viewModel.resizeSession(targetSessionId, cols, rows)
            }
        }
        terminalView.requestFocus()
        terminalView.invalidate()
    }

    private fun handleProcessExited(exitedSessionId: String) {
        val remaining = viewModel.onSessionProcessExited(exitedSessionId)
        if (remaining <= 0) {
            activity?.let { act ->
                LinuxService.stop(act)
                act.finishAffinity()
            }
        }
    }

    private fun setupTerminalInput() {
        terminalView.onInputListener = { data ->
            sessionId?.let { viewModel.sendInput(it, data) }
        }
        terminalView.onTerminalResize = { cols, rows ->
            sessionId?.let { viewModel.resizeSession(it, cols, rows) }
        }
    }

    private fun setupExtraKeys() {
        terminalView.onModifierChanged = { key, active ->
            extraKeysView.setModifierActive(key, active)
        }

        extraKeysView.onKeyListener = { action ->
            terminalView.requestFocus()
            when (action) {
                is ExtraKeysView.KeyAction.ToggleCtrl -> {
                    terminalView.toggleCtrl()
                }
                is ExtraKeysView.KeyAction.ToggleAlt -> {
                    terminalView.toggleAlt()
                }
                is ExtraKeysView.KeyAction.ToggleShift -> {
                    terminalView.toggleShift()
                }
                is ExtraKeysView.KeyAction.SpecialKeyAction -> {
                    terminalView.sendSpecialKey(action.key)
                }
                is ExtraKeysView.KeyAction.TextAction -> {
                    for (ch in action.text) {
                        terminalView.handleCharacterInput(ch)
                    }
                }
                is ExtraKeysView.KeyAction.CtrlAction -> {
                    terminalView.sendCtrl(action.char)
                }
                is ExtraKeysView.KeyAction.PasteAction -> {
                    terminalView.pasteClipboard()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        terminalView.applyPreferences()
        sessionId?.let { attachToSession(it) }
        terminalView.requestFocus()
        terminalView.showSoftKeyboard()
    }
}
