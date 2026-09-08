package com.mobilelinux.util

import android.content.Context
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mobilelinux.ui.DevBrowserActivity
import java.io.File

/**
 * FileObserver IPC trigger for terminal-to-browser redirection.
 * Watches /dev/shm and /tmp for a trigger file (.open_url) written by xdg-open or CLI tools.
 * When written, immediately launches DevBrowserActivity with that URL.
 */
class BrowserUrlTriggerObserver(
    private val context: Context,
    private val watchDirs: List<File>
) {

    private val TAG = "BrowserUrlObserver"
    private val mainHandler = Handler(Looper.getMainLooper())
    private val observers = mutableListOf<FileObserver>()
    @Volatile private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true

        for (dir in watchDirs) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val observer = @Suppress("DEPRECATION") object : FileObserver(dir.absolutePath, CLOSE_WRITE or MOVED_TO) {
                    override fun onEvent(event: Int, path: String?) {
                        if (path == ".open_url" || path == "open_url") {
                            val triggerFile = File(dir, path)
                            handleTriggerFile(triggerFile)
                        }
                    }
                }
                observer.startWatching()
                observers.add(observer)
                Log.d(TAG, "Watching for browser open triggers in: ${dir.absolutePath}")
            } catch (e: Exception) {
                Log.w(TAG, "Could not start FileObserver for ${dir.absolutePath}: ${e.message}")
            }
        }
    }

    private fun handleTriggerFile(file: File) {
        try {
            if (!file.exists()) return
            val url = file.readText().trim()
            file.delete()

            if (url.isNotEmpty()) {
                Log.i(TAG, "Detected URL trigger from terminal: $url")
                mainHandler.post {
                    try {
                        DevBrowserActivity.openUrl(context, url)
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to launch browser for $url", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling trigger file: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        for (observer in observers) {
            try {
                observer.stopWatching()
            } catch (ignored: Exception) {}
        }
        observers.clear()
    }
}
