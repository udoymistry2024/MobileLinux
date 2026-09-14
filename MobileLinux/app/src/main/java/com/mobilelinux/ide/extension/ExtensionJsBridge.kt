package com.mobilelinux.ide.extension

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.widget.Toast
import com.mobilelinux.runtime.UbuntuRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * JavaScript Interface bridge injected into the Code IDE WebView as `window.ExtensionBridge`.
 * Provides runtime services to extensions, including executing silent commands inside Ubuntu PRoot.
 */
class ExtensionJsBridge(
    private val context: Context,
    private val webView: WebView,
    private val onSendToTerminal: (String) -> Unit
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs by lazy {
        context.getSharedPreferences("ide_extension_settings", Context.MODE_PRIVATE)
    }

    @JavascriptInterface
    fun showToast(message: String?) {
        if (message.isNullOrBlank()) return
        mainHandler.post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun executeLinuxCommand(command: String?, callbackId: String?) {
        if (command.isNullOrBlank() || callbackId.isNullOrBlank()) return

        scope.launch {
            try {
                val runtime = UbuntuRuntime.getInstance(context)
                val stdoutBuilder = StringBuilder()

                val result = runtime.runCommand(command, timeoutSeconds = 60L) { line ->
                    stdoutBuilder.append(line).append("\n")
                }

                val exitCode = result.first
                val stdout = stdoutBuilder.toString().trimEnd()
                val stderr = if (exitCode != 0 && stdout.isEmpty()) "Process exited with code $exitCode" else ""

                deliverCallback(callbackId, exitCode, stdout, stderr)
            } catch (e: Exception) {
                deliverCallback(callbackId, -1, "", e.message ?: "Unknown execution error")
            }
        }
    }

    @JavascriptInterface
    fun sendToTerminal(command: String?) {
        if (command.isNullOrBlank()) return
        mainHandler.post {
            onSendToTerminal(command)
        }
    }

    @JavascriptInterface
    fun saveSetting(extId: String, key: String, value: String) {
        prefs.edit().putString("${extId}_$key", value).apply()
    }

    @JavascriptInterface
    fun getSetting(extId: String, key: String): String {
        return prefs.getString("${extId}_$key", "") ?: ""
    }

    @JavascriptInterface
    fun startKernelSession(sessionId: String?, callbackId: String?) {
        if (sessionId.isNullOrBlank() || callbackId.isNullOrBlank()) return
        val kernelManager = KernelSessionManager.getInstance(context)
        kernelManager.startSession(sessionId) { ok, msg ->
            mainHandler.post {
                val safeMsg = JSONObject.quote(msg)
                val js = "window.__extensionKernelCallback && window.__extensionKernelCallback('$callbackId', { ok: $ok, message: $safeMsg });"
                webView.evaluateJavascript(js, null)
            }
        }
    }

    @JavascriptInterface
    fun executeInKernel(sessionId: String?, code: String?, reqId: String?, callbackId: String?) {
        if (sessionId.isNullOrBlank() || reqId.isNullOrBlank() || callbackId.isNullOrBlank()) return
        val kernelManager = KernelSessionManager.getInstance(context)
        kernelManager.execute(sessionId, code ?: "", reqId) { result ->
            mainHandler.post {
                val js = "window.__extensionKernelCallback && window.__extensionKernelCallback('$callbackId', ${result.toString()});"
                webView.evaluateJavascript(js, null)
            }
        }
    }

    @JavascriptInterface
    fun restartKernelSession(sessionId: String?, callbackId: String?) {
        if (sessionId.isNullOrBlank() || callbackId.isNullOrBlank()) return
        val kernelManager = KernelSessionManager.getInstance(context)
        kernelManager.restart(sessionId, "restart_${System.currentTimeMillis()}") { result ->
            mainHandler.post {
                val js = "window.__extensionKernelCallback && window.__extensionKernelCallback('$callbackId', ${result.toString()});"
                webView.evaluateJavascript(js, null)
            }
        }
    }

    @JavascriptInterface
    fun stopKernelSession(sessionId: String?) {
        if (sessionId.isNullOrBlank()) return
        KernelSessionManager.getInstance(context).stop(sessionId)
    }

    // =========================================================================
    // Universal Process & Daemon Subsystem (Supports any language/script)
    // =========================================================================

    @JavascriptInterface
    fun spawnProcess(processId: String?, command: String?, workingDir: String?) {
        if (processId.isNullOrBlank() || command.isNullOrBlank()) return
        val manager = KernelSessionManager.getInstance(context)
        manager.spawnProcess(processId, command, workingDir) { id, event, data ->
            mainHandler.post {
                val safeData = JSONObject.quote(data)
                val js = "window.__extensionProcessCallback && window.__extensionProcessCallback('$id', '$event', $safeData);"
                webView.evaluateJavascript(js, null)
            }
        }
    }

    @JavascriptInterface
    fun writeProcessStdin(processId: String?, data: String?): Boolean {
        if (processId.isNullOrBlank() || data == null) return false
        return KernelSessionManager.getInstance(context).writeProcessStdin(processId, data)
    }

    @JavascriptInterface
    fun killProcess(processId: String?): Boolean {
        if (processId.isNullOrBlank()) return false
        return KernelSessionManager.getInstance(context).killProcess(processId)
    }

    // =========================================================================
    // Extension Workspace Filesystem API
    // =========================================================================

    @JavascriptInterface
    fun readFile(path: String?): String {
        if (path.isNullOrBlank()) return ""
        return try {
            java.io.File(path).readText(Charsets.UTF_8)
        } catch (e: Exception) {
            ""
        }
    }

    @JavascriptInterface
    fun writeFile(path: String?, content: String?): Boolean {
        if (path.isNullOrBlank() || content == null) return false
        return try {
            val f = java.io.File(path)
            f.parentFile?.mkdirs()
            f.writeText(content, Charsets.UTF_8)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun deliverCallback(callbackId: String, exitCode: Int, stdout: String, stderr: String) {
        mainHandler.post {
            val safeStdout = JSONObject.quote(stdout)
            val safeStderr = JSONObject.quote(stderr)
            val js = "window.__extensionCallback && window.__extensionCallback('$callbackId', $exitCode, $safeStdout, $safeStderr);"
            webView.evaluateJavascript(js, null)
        }
    }
}

