package com.mobilelinux.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.mobilelinux.R
import com.mobilelinux.terminal.TerminalManager
import com.mobilelinux.ui.MainActivity
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps Ubuntu sessions alive in the background.
 *
 * Features:
 * - Holds a PARTIAL_WAKE_LOCK to prevent CPU sleep
 * - Shows persistent notification with session count
 * - Survives app minimize / task swipe
 * - Configured as START_STICKY (auto-restarts)
 */
class LinuxService : LifecycleService() {

    private val TAG = "LinuxService"

    // Wake lock
    private lateinit var wakeLock: PowerManager.WakeLock

    // Notification
    private val NOTIFICATION_ID = 1001
    private val CHANNEL_ID = "mobilelinux_channel"

    // Terminal manager reference
    private lateinit var terminalManager: TerminalManager

    // Status update job
    private var statusJob: Job? = null

    // Binder for UI components
    private val binder = LinuxBinder()

    inner class LinuxBinder : Binder() {
        fun getService(): LinuxService = this@LinuxService
    }

    override fun onBind(intent: Intent): IBinder {
        super.onBind(intent)
        return binder
    }

    // =========================================================================
    // Lifecycle
    // =========================================================================

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "LinuxService created")

        terminalManager = TerminalManager.getInstance(this)

        // Acquire wake lock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MobileLinux::LinuxWakeLock"
        ).apply {
            setReferenceCounted(false)
        }
        wakeLock.acquire(/* indefinite - released on destroy */)

        // Create notification channel
        createNotificationChannel()

        // Start as foreground service
        startForeground(NOTIFICATION_ID, buildNotification(0))

        // Start periodic notification updates
        startStatusUpdates()

        Log.d(TAG, "LinuxService started with foreground notification")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        when (intent?.action) {
            ACTION_KILL_ALL -> {
                terminalManager.killAllSessions()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_NEW_SESSION -> {
                terminalManager.createSession()
                updateNotification()
            }
        }

        return START_STICKY  // Restart if killed by OS
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "LinuxService destroyed")

        statusJob?.cancel()

        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        terminalManager.killAllSessions()
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        val count = terminalManager.getAliveSessionCount()
        if (count == 0) {
            Log.d(TAG, "No sessions running — stopping service on task removed")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        Log.d(TAG, "App swiped from recents — keeping $count session(s) alive in background")
        if (!wakeLock.isHeld) {
            try { wakeLock.acquire() } catch (e: Exception) { Log.w(TAG, "WakeLock acquire error: ${e.message}") }
        }
        startForeground(NOTIFICATION_ID, buildNotification(count))
    }

    // =========================================================================
    // Notification
    // =========================================================================

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "MobileLinux Runtime",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Keeps Linux sessions running in the background"
            setShowBadge(false)
            enableVibration(false)
            enableLights(false)
            setSound(null, null)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(sessionCount: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val killIntent = PendingIntent.getService(
            this, 1,
            Intent(this, LinuxService::class.java).apply { action = ACTION_KILL_ALL },
            PendingIntent.FLAG_IMMUTABLE
        )

        val newSessionIntent = PendingIntent.getService(
            this, 2,
            Intent(this, LinuxService::class.java).apply { action = ACTION_NEW_SESSION },
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MobileLinux")
            .setContentText(
                if (sessionCount == 0) "Ubuntu environment ready"
                else "$sessionCount session${if (sessionCount > 1) "s" else ""} running"
            )
            .setSmallIcon(R.drawable.ic_terminal)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(R.drawable.ic_add_session, "New Session", newSessionIntent)
            .addAction(R.drawable.ic_stop, "Stop", killIntent)
            .build()
    }

    fun updateNotification() {
        val count = terminalManager.getAliveSessionCount()
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(count))
    }

    private fun startStatusUpdates() {
        statusJob = lifecycleScope.launch {
            while (isActive) {
                delay(5000)
                updateNotification()
            }
        }
    }

    companion object {
        const val ACTION_KILL_ALL = "com.mobilelinux.KILL_ALL"
        const val ACTION_NEW_SESSION = "com.mobilelinux.NEW_SESSION"

        fun start(context: Context) {
            val intent = Intent(context, LinuxService::class.java)
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            val intent = Intent(context, LinuxService::class.java).apply {
                action = ACTION_KILL_ALL
            }
            context.startService(intent)
        }
    }
}
