package com.mobilelinux.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.preference.PreferenceManager

/**
 * Receives BOOT_COMPLETED to restart sessions if the user enabled auto-start.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != "android.intent.action.QUICKBOOT_POWERON") return

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val autoStart = prefs.getBoolean("pref_auto_start_on_boot", false)

        if (autoStart) {
            LinuxService.start(context)
        }
    }
}
