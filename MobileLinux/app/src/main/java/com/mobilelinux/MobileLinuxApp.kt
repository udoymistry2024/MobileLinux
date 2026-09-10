package com.mobilelinux

import android.app.ActivityManager
import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import android.util.Log
import com.mobilelinux.terminal.TerminalManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Global Application class for MobileLinux.
 *
 * Implements hardware-aware memory tuning and lifecycle optimizations:
 * - Dynamic device RAM detection (adjusts scrollback and cache limits for 6GB, 8GB, 12GB+ devices)
 * - Proactive ComponentCallbacks2 onTrimMemory / onLowMemory handling
 * - Background singleton pre-warming to minimize cold-start latency
 */
class MobileLinuxApp : Application() {

    companion object {
        private const val TAG = "MobileLinuxApp"

        @Volatile
        lateinit var instance: MobileLinuxApp
            private set

        var totalRamMb: Long = 4096L
            private set

        var isLowRamDevice: Boolean = false
            private set

        private var currentActivityRef: java.lang.ref.WeakReference<android.app.Activity>? = null

        fun updateWindowKeepScreenOn(activity: android.app.Activity?) {
            if (activity == null || activity.isFinishing || activity.isDestroyed) return
            try {
                val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(activity)
                val keepScreenOnPref = prefs.getBoolean("pref_keep_screen_on", false)
                val isInstalling = try {
                    com.mobilelinux.service.PackageInstallationManager.getInstance(activity).isAnyInstallInProgress()
                } catch (e: Exception) {
                    false
                }

                if (keepScreenOnPref || isInstalling) {
                    activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                } else {
                    activity.window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                }
            } catch (e: Exception) {
                Log.w(TAG, "updateWindowKeepScreenOn error: ${e.message}")
            }
        }

        fun notifyScreenKeepOnChanged() {
            currentActivityRef?.get()?.let { act ->
                act.runOnUiThread {
                    updateWindowKeepScreenOn(act)
                }
            }
        }

        /**
         * Suggested scrollback capacity tailored to device RAM tier:
         * - Devices <= 6GB RAM: 2,000 lines
         * - Devices 8GB - 12GB RAM: 4,000 lines
         * - Devices > 12GB RAM: 8,000 lines
         */
        val optimalScrollbackLimit: Int
            get() = when {
                totalRamMb <= 6144L -> 2000
                totalRamMb <= 12288L -> 4000
                else -> 8000
            }
    }

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Global Keep-Screen-On lifecycle listener & SharedPreferences observer
        try {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            prefs.registerOnSharedPreferenceChangeListener { _, key ->
                if (key == "pref_keep_screen_on") {
                    notifyScreenKeepOnChanged()
                }
            }

            registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(activity: android.app.Activity, savedInstanceState: android.os.Bundle?) {
                    updateWindowKeepScreenOn(activity)
                }

                override fun onActivityStarted(activity: android.app.Activity) {
                    updateWindowKeepScreenOn(activity)
                }

                override fun onActivityResumed(activity: android.app.Activity) {
                    currentActivityRef = java.lang.ref.WeakReference(activity)
                    updateWindowKeepScreenOn(activity)
                }

                override fun onActivityPaused(activity: android.app.Activity) {
                    if (currentActivityRef?.get() === activity) {
                        currentActivityRef = null
                    }
                }

                override fun onActivityStopped(activity: android.app.Activity) {}
                override fun onActivitySaveInstanceState(activity: android.app.Activity, outState: android.os.Bundle) {}
                override fun onActivityDestroyed(activity: android.app.Activity) {}
            })
        } catch (e: Exception) {
            Log.w(TAG, "ActivityLifecycleCallbacks error: ${e.message}")
        }

        // Early sanitation: ensure dead sockets and stale locks from any previous crash / force-stop are purged
        try {
            val prootTmp = java.io.File(cacheDir, "proot_tmp")
            if (prootTmp.exists()) {
                prootTmp.deleteRecursively()
            }
            prootTmp.mkdirs()
        } catch (ignored: Exception) {}

        detectDeviceMemory()

        // Background singleton & runtime pre-warming to keep main thread completely unblocked
        appScope.launch {
            try {
                Log.d(TAG, "Pre-warming runtime components for ${totalRamMb}MB RAM device...")
                val runtime = com.mobilelinux.runtime.UbuntuRuntime.getInstance(this@MobileLinuxApp)
                runtime.cleanupStaleProotArtifacts()
                com.mobilelinux.model.PackageRepository.getCuratedPackages()
                Log.d(TAG, "Pre-warming completed.")
            } catch (e: Exception) {
                Log.w(TAG, "Background pre-warming notice: ${e.message}")
            }
        }
    }

    private fun detectDeviceMemory() {
        try {
            val actManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            if (actManager != null) {
                val memInfo = ActivityManager.MemoryInfo()
                actManager.getMemoryInfo(memInfo)
                totalRamMb = memInfo.totalMem / (1024 * 1024)
                isLowRamDevice = memInfo.lowMemory || actManager.isLowRamDevice || totalRamMb <= 4096L
                Log.i(TAG, "Detected total RAM: ${totalRamMb} MB (isLowRam: $isLowRamDevice)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not query memory info: ${e.message}")
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        Log.d(TAG, "onTrimMemory called with level: $level")

        when (level) {
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL,
            ComponentCallbacks2.TRIM_MEMORY_COMPLETE -> {
                // Severe memory pressure — aggressively trim inactive buffers
                TerminalManager.getInstance(this).trimAllSessionsMemory(maxLines = 800)
                System.gc()
            }
            ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW,
            ComponentCallbacks2.TRIM_MEMORY_MODERATE -> {
                // Moderate memory pressure — trim down to 1,500 lines
                TerminalManager.getInstance(this).trimAllSessionsMemory(maxLines = 1500)
            }
            ComponentCallbacks2.TRIM_MEMORY_BACKGROUND,
            ComponentCallbacks2.TRIM_MEMORY_UI_HIDDEN -> {
                // App moved to background — light maintenance trim
                TerminalManager.getInstance(this).trimAllSessionsMemory(maxLines = 2500)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        Log.w(TAG, "onLowMemory received from Android OS!")
        TerminalManager.getInstance(this).trimAllSessionsMemory(maxLines = 500)
        System.gc()
    }
}
