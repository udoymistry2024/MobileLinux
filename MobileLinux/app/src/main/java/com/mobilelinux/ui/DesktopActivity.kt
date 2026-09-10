package com.mobilelinux.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mobilelinux.R
import com.mobilelinux.runtime.UbuntuRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Socket

/**
 * Fullscreen Landscape Activity for Desktop Mode.
 * Hosts VncCanvasView, Laptop-style Trackpad simulation, Floating Control Dock,
 * OTG Hardware Keyboard/Mouse integration, and automatic memory cleanup on exit.
 */
class DesktopActivity : AppCompatActivity(),
    FloatingDesktopControlsView.Listener,
    VncCanvasView.ConnectionListener {

    companion object {
        private const val TAG = "DesktopActivity"
    }

    private lateinit var runtime: UbuntuRuntime
    private lateinit var vncCanvas: VncCanvasView
    private lateinit var floatingControls: FloatingDesktopControlsView
    private lateinit var layoutLoading: View
    private lateinit var tvLoadingStatus: TextView
    private lateinit var tvLoadingSub: TextView
    private lateinit var progressLoading: ProgressBar
    private lateinit var layoutErrorActions: View
    private lateinit var btnRetryError: View
    private lateinit var btnExitError: View
    private lateinit var scrollModifierBar: View
    private lateinit var dummyKeyInput: EditText

    // Modifier keys state
    private var isCtrlActive = false
    private var isAltActive = false
    private var isSuperActive = false

    private var isExiting = false

    @SuppressLint("SourceLockedOrientationActivity")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Lock to Sensor Landscape orientation
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        // Enable Fullscreen Immersive Mode (hide navigation and status bars)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val insetsController = WindowInsetsControllerCompat(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
        insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

        setContentView(R.layout.activity_desktop)

        runtime = UbuntuRuntime.getInstance(applicationContext)

        vncCanvas = findViewById(R.id.vnc_canvas)
        floatingControls = findViewById(R.id.floating_controls)
        layoutLoading = findViewById(R.id.layout_loading)
        tvLoadingStatus = findViewById(R.id.tv_loading_status)
        tvLoadingSub = findViewById(R.id.tv_loading_sub)
        progressLoading = findViewById(R.id.progress_loading)
        layoutErrorActions = findViewById(R.id.layout_error_actions)
        btnRetryError = findViewById(R.id.btn_retry_error)
        btnExitError = findViewById(R.id.btn_exit_error)
        scrollModifierBar = findViewById(R.id.scroll_modifier_bar)
        dummyKeyInput = findViewById(R.id.dummy_key_input)

        btnRetryError.setOnClickListener {
            vncCanvas.disconnect()
            runtime.stopDesktopProcess()
            startDesktopEnvironment()
        }
        btnExitError.setOnClickListener {
            shutdownAndExit()
        }

        vncCanvas.connectionListener = this
        floatingControls.listener = this

        setupModifierBar()
        setupDummyInput()

        // Start Desktop Environment & Connect
        startDesktopEnvironment()
    }

    private fun setupModifierBar() {
        findViewById<TextView>(R.id.key_esc).setOnClickListener {
            vncCanvas.sendKey(true, 0xFF1B)
            vncCanvas.sendKey(false, 0xFF1B)
        }
        findViewById<TextView>(R.id.key_tab).setOnClickListener {
            vncCanvas.sendKey(true, 0xFF09)
            vncCanvas.sendKey(false, 0xFF09)
        }
        val btnCtrl = findViewById<TextView>(R.id.key_ctrl)
        btnCtrl.setOnClickListener {
            isCtrlActive = !isCtrlActive
            btnCtrl.isSelected = isCtrlActive
            vncCanvas.sendKey(isCtrlActive, 0xFFE3)
        }
        val btnAlt = findViewById<TextView>(R.id.key_alt)
        btnAlt.setOnClickListener {
            isAltActive = !isAltActive
            btnAlt.isSelected = isAltActive
            vncCanvas.sendKey(isAltActive, 0xFFE9)
        }
        val btnSuper = findViewById<TextView>(R.id.key_super)
        btnSuper.setOnClickListener {
            isSuperActive = !isSuperActive
            btnSuper.isSelected = isSuperActive
            vncCanvas.sendKey(isSuperActive, 0xFFEB)
        }
        findViewById<TextView>(R.id.key_arrow_up).setOnClickListener {
            vncCanvas.sendKey(true, 0xFF52); vncCanvas.sendKey(false, 0xFF52)
        }
        findViewById<TextView>(R.id.key_arrow_down).setOnClickListener {
            vncCanvas.sendKey(true, 0xFF54); vncCanvas.sendKey(false, 0xFF54)
        }
        findViewById<TextView>(R.id.key_arrow_left).setOnClickListener {
            vncCanvas.sendKey(true, 0xFF51); vncCanvas.sendKey(false, 0xFF51)
        }
        findViewById<TextView>(R.id.key_arrow_right).setOnClickListener {
            vncCanvas.sendKey(true, 0xFF53); vncCanvas.sendKey(false, 0xFF53)
        }
        findViewById<TextView>(R.id.key_hide_kbd).setOnClickListener {
            hideKeyboard()
        }
    }

    private fun setupDummyInput() {
        dummyKeyInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (!count.equals(0) && s != null && s.isNotEmpty()) {
                    for (i in start until (start + count)) {
                        val ch = s[i]
                        val keysym = when (ch) {
                            '\n' -> 0xFF0D
                            '\t' -> 0xFF09
                            else -> ch.code
                        }
                        vncCanvas.sendKey(true, keysym)
                        vncCanvas.sendKey(false, keysym)
                    }
                    dummyKeyInput.setText("")
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        dummyKeyInput.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DEL -> {
                        vncCanvas.sendKey(true, 0xFF08)
                        vncCanvas.sendKey(false, 0xFF08)
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_ENTER -> {
                        vncCanvas.sendKey(true, 0xFF0D)
                        vncCanvas.sendKey(false, 0xFF0D)
                        return@setOnKeyListener true
                    }
                }
            }
            false
        }
    }

    private fun startDesktopEnvironment() {
        progressLoading.visibility = View.VISIBLE
        layoutErrorActions.visibility = View.GONE
        tvLoadingStatus.text = "Initializing XFCE4 Desktop & TigerVNC..."
        tvLoadingSub.text = "Starting local display server :1..."
        layoutLoading.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            // Query real hardware screen bounds to fill 100% of the mobile screen without black sidebars
            val bounds = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds
            } else {
                val realDm = android.util.DisplayMetrics()
                @Suppress("DEPRECATION")
                windowManager.defaultDisplay.getRealMetrics(realDm)
                android.graphics.Rect(0, 0, realDm.widthPixels, realDm.heightPixels)
            }
            val screenW = maxOf(bounds.width(), bounds.height())
            val screenH = minOf(bounds.width(), bounds.height())

            // Maintain exact phone aspect ratio while keeping height at max 1080p for optimal 60fps performance
            val targetH = minOf(screenH, 1080)
            val targetW = (screenW * targetH) / screenH
            val finalW = if (targetW % 2 != 0) targetW + 1 else targetW
            val finalH = if (targetH % 2 != 0) targetH + 1 else targetH
            val resolution = "${finalW}x${finalH}"

            // Start desktop PRoot process that stays alive
            val proc = runtime.startDesktopProcess(resolution)

            // Stream log lines in background for real-time debugging
            launch(Dispatchers.IO) {
                try {
                    proc.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            Log.d(TAG, "desktop-proc: $line")
                        }
                    }
                } catch (ignored: Exception) {}
            }

            // Direct TCP socket polling from Android to verify port 5901 readiness
            var isReady = false
            for (i in 0 until 40) { // Poll for up to 8 seconds (40 x 200ms)
                if (!proc.isAlive) {
                    Log.e(TAG, "Desktop process died prematurely with exit code: ${try { proc.exitValue() } catch(e: Exception) { -1 }}")
                    break
                }
                try {
                    val testSocket = Socket("127.0.0.1", VncCanvasView.DEFAULT_VNC_PORT)
                    testSocket.close()
                    isReady = true
                    break
                } catch (e: Exception) {
                    delay(200)
                }
            }

            withContext(Dispatchers.Main) {
                if (isReady) {
                    tvLoadingStatus.text = "Connecting to desktop session..."
                    tvLoadingSub.text = "Establishing high-speed VNC connection..."
                    vncCanvas.connect("127.0.0.1", VncCanvasView.DEFAULT_VNC_PORT)
                } else {
                    val exitCode = try { proc.exitValue() } catch (e: Exception) { -1 }
                    val errMsg = if (!proc.isAlive) {
                        "Display server process ended unexpectedly (code $exitCode)."
                    } else {
                        "Display server timed out while binding to port 5901."
                    }
                    tvLoadingStatus.text = "Desktop Startup Failed"
                    tvLoadingSub.text = errMsg
                    progressLoading.visibility = View.GONE
                    layoutErrorActions.visibility = View.VISIBLE
                }
            }
        }
    }

    // --- VncCanvasView.ConnectionListener ---

    override fun onConnected(width: Int, height: Int) {
        layoutLoading.visibility = View.GONE
        progressLoading.visibility = View.VISIBLE
        layoutErrorActions.visibility = View.GONE
        Toast.makeText(this, "Desktop Connected (${width}x${height})", Toast.LENGTH_SHORT).show()
    }

    override fun onDisconnected(reason: String?) {
        if (!isExiting) {
            layoutLoading.visibility = View.VISIBLE
            progressLoading.visibility = View.GONE
            layoutErrorActions.visibility = View.VISIBLE
            tvLoadingStatus.text = "Connection Disconnected"
            tvLoadingSub.text = reason ?: "Desktop session ended."
        }
    }

    override fun onError(error: String) {
        if (!isExiting) {
            layoutLoading.visibility = View.VISIBLE
            progressLoading.visibility = View.GONE
            layoutErrorActions.visibility = View.VISIBLE
            tvLoadingStatus.text = "Connection Error"
            tvLoadingSub.text = error
        }
    }

    // --- FloatingDesktopControlsView.Listener ---

    override fun onLeftClick() {
        vncCanvas.sendLeftClick()
    }

    override fun onLeftClickHold(isHeld: Boolean) {
        vncCanvas.setLeftClickHold(isHeld)
        if (isHeld) {
            Toast.makeText(this, "Left-Click Held (Drag with other finger)", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onRightClick() {
        vncCanvas.sendRightClick()
    }

    override fun onScrollUp() {
        vncCanvas.sendScrollUp()
    }

    override fun onScrollDown() {
        vncCanvas.sendScrollDown()
    }

    override fun onToggleKeyboard() {
        if (scrollModifierBar.visibility == View.VISIBLE) {
            hideKeyboard()
        } else {
            showKeyboard()
        }
    }

    override fun onExitDesktop() {
        confirmExit()
    }

    private fun showKeyboard() {
        scrollModifierBar.visibility = View.VISIBLE
        dummyKeyInput.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(dummyKeyInput, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun hideKeyboard() {
        scrollModifierBar.visibility = View.GONE
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(dummyKeyInput.windowToken, 0)
    }

    /**
     * Physical OTG Keyboard Event Dispatcher
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Intercept back button for confirmation
        if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
            confirmExit()
            return true
        }

        // Forward physical OTG keys to VNC Canvas
        if (event.keyCode != KeyEvent.KEYCODE_BACK && event.keyCode != KeyEvent.KEYCODE_VOLUME_UP && event.keyCode != KeyEvent.KEYCODE_VOLUME_DOWN) {
            val isDown = event.action == KeyEvent.ACTION_DOWN
            val keysym = mapKeyCodeToX11Keysym(event)
            if (keysym != 0) {
                vncCanvas.sendKey(isDown, keysym)
                return true
            }
        }

        return super.dispatchKeyEvent(event)
    }

    private fun mapKeyCodeToX11Keysym(event: KeyEvent): Int {
        val keyCode = event.keyCode
        val unicodeChar = event.unicodeChar

        return when (keyCode) {
            KeyEvent.KEYCODE_DEL -> 0xFF08        // Backspace
            KeyEvent.KEYCODE_FORWARD_DEL -> 0xFFFF // Delete
            KeyEvent.KEYCODE_TAB -> 0xFF09        // Tab
            KeyEvent.KEYCODE_ENTER -> 0xFF0D      // Return
            KeyEvent.KEYCODE_ESCAPE -> 0xFF1B     // Escape
            KeyEvent.KEYCODE_DPAD_UP -> 0xFF52    // Up
            KeyEvent.KEYCODE_DPAD_DOWN -> 0xFF54  // Down
            KeyEvent.KEYCODE_DPAD_LEFT -> 0xFF51  // Left
            KeyEvent.KEYCODE_DPAD_RIGHT -> 0xFF53 // Right
            KeyEvent.KEYCODE_PAGE_UP -> 0xFF55
            KeyEvent.KEYCODE_PAGE_DOWN -> 0xFF56
            KeyEvent.KEYCODE_MOVE_HOME -> 0xFF50
            KeyEvent.KEYCODE_MOVE_END -> 0xFF57
            KeyEvent.KEYCODE_INSERT -> 0xFF63
            KeyEvent.KEYCODE_F1 -> 0xFFBE
            KeyEvent.KEYCODE_F2 -> 0xFFBF
            KeyEvent.KEYCODE_F3 -> 0xFFC0
            KeyEvent.KEYCODE_F4 -> 0xFFC1
            KeyEvent.KEYCODE_F5 -> 0xFFC2
            KeyEvent.KEYCODE_F6 -> 0xFFC3
            KeyEvent.KEYCODE_F7 -> 0xFFC4
            KeyEvent.KEYCODE_F8 -> 0xFFC5
            KeyEvent.KEYCODE_F9 -> 0xFFC6
            KeyEvent.KEYCODE_F10 -> 0xFFC7
            KeyEvent.KEYCODE_F11 -> 0xFFC8
            KeyEvent.KEYCODE_F12 -> 0xFFC9
            KeyEvent.KEYCODE_SHIFT_LEFT -> 0xFFE1
            KeyEvent.KEYCODE_SHIFT_RIGHT -> 0xFFE2
            KeyEvent.KEYCODE_CTRL_LEFT -> 0xFFE3
            KeyEvent.KEYCODE_CTRL_RIGHT -> 0xFFE4
            KeyEvent.KEYCODE_ALT_LEFT -> 0xFFE9
            KeyEvent.KEYCODE_ALT_RIGHT -> 0xFFEA
            KeyEvent.KEYCODE_META_LEFT -> 0xFFEB
            KeyEvent.KEYCODE_META_RIGHT -> 0xFFEC
            else -> {
                if (unicodeChar != 0) unicodeChar else 0
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        confirmExit()
    }

    private fun confirmExit() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Exit Desktop Mode?")
            .setMessage("All running graphical desktop applications will be cleanly closed, and background memory will be released.")
            .setPositiveButton("Exit & Free RAM") { _, _ ->
                shutdownAndExit()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shutdownAndExit() {
        if (isExiting) return
        isExiting = true
        vncCanvas.disconnect()
        runtime.stopDesktopProcess()
        finish()

        // Asynchronously stop VNC and clean processes in background without blocking UI
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rt = UbuntuRuntime.getInstance(app)
                rt.runCommand("/usr/local/bin/desktop-stop")
            } catch (ignored: Exception) {}
        }
    }

    override fun onResume() {
        super.onResume()
        com.mobilelinux.MobileLinuxApp.updateWindowKeepScreenOn(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        vncCanvas.disconnect()
        runtime.stopDesktopProcess()
        val app = applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val rt = UbuntuRuntime.getInstance(app)
                rt.runCommand("/usr/local/bin/desktop-stop")
            } catch (ignored: Exception) {}
            withContext(Dispatchers.Main) {
                System.gc()
            }
        }
    }
}
