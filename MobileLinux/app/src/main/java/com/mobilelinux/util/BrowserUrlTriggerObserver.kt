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

    private val pollRunnable = object : Runnable {
        override fun run() {
            if (!isRunning) return
            for (dir in watchDirs) {
                try {
                    val trigger1 = File(dir, ".open_url")
                    if (trigger1.exists() && trigger1.length() > 0) {
                        handleTriggerFile(trigger1)
                    }
                    val trigger2 = File(dir, "open_url")
                    if (trigger2.exists() && trigger2.length() > 0) {
                        handleTriggerFile(trigger2)
                    }
                } catch (ignored: Exception) {}
            }
            if (isRunning) {
                mainHandler.postDelayed(this, 1200L)
            }
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        for (dir in watchDirs) {
            try {
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                val mask = @Suppress("DEPRECATION") (
                    FileObserver.CLOSE_WRITE or
                    FileObserver.MOVED_TO or
                    FileObserver.CREATE or
                    FileObserver.MODIFY
                )
                val observer = @Suppress("DEPRECATION") object : FileObserver(dir.absolutePath, mask) {
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

        // Start fallback polling check every 1.2s
        mainHandler.postDelayed(pollRunnable, 1200L)
    }

    @Volatile private var lastTriggeredUrl: String = ""
    @Volatile private var lastTriggeredTime: Long = 0L

    private fun cleanupAllTriggers() {
        for (dir in watchDirs) {
            try {
                File(dir, ".open_url").delete()
                File(dir, "open_url").delete()
                File(dir, ".open_url.tmp").delete()
            } catch (ignored: Exception) {}
        }
    }

    private fun handleTriggerFile(file: File) {
        try {
            if (!file.exists()) return
            var url = file.readText().trim()
            if (url.isEmpty()) {
                // File may have just been created by shell; wait a brief moment for write to finish
                Thread.sleep(60)
                if (file.exists()) {
                    url = file.readText().trim()
                }
            }
            if (url.isEmpty()) {
                // Still empty, do NOT delete yet; let CLOSE_WRITE, MOVED_TO or poll handle it when bytes arrive
                return
            }

            // Immediately wipe all triggers in all watch dirs so other observers/polls don't double fire
            cleanupAllTriggers()

            // Debounce identical trigger within 2.5s
            val now = System.currentTimeMillis()
            if (url == lastTriggeredUrl && (now - lastTriggeredTime) < 2500L) {
                Log.d(TAG, "Ignoring duplicate URL trigger within 2.5s: $url")
                return
            }
            lastTriggeredUrl = url
            lastTriggeredTime = now

            Log.i(TAG, "Detected URL trigger from terminal: $url")
            mainHandler.post {
                try {
                    DevBrowserActivity.openUrl(context, url)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to launch browser for $url", e)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error handling trigger file: ${e.message}")
        }
    }

    fun stop() {
        isRunning = false
        mainHandler.removeCallbacks(pollRunnable)
        for (observer in observers) {
            try {
                observer.stopWatching()
            } catch (ignored: Exception) {}
        }
        observers.clear()
    }
}
