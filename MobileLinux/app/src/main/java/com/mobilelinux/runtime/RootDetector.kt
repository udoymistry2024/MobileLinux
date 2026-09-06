package com.mobilelinux.runtime

import android.content.Context
import android.os.Process
import android.util.Log
import java.io.File

/**
 * Detects whether the device is rooted and which root management app is available.
 */
object RootDetector {

    private const val TAG = "RootDetector"

    private val ROOT_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/su",
        "/system/bin/.ext/.su",
        "/system/usr/we-need-root/su-backup",
        "/system/xbin/mu"
    )

    private val ROOT_MANAGEMENT_APPS = arrayOf(
        "com.topjohnwu.magisk",
        "eu.chainfire.supersu",
        "com.noshufou.android.su",
        "com.koushikdutta.superuser",
        "com.zachspong.temprootremovejb",
        "com.ramdroid.appquarantine"
    )

    /**
     * Returns true if the device appears to be rooted.
     */
    fun isRooted(): Boolean {
        return checkSuBinary() || checkRootManagementApp() || checkRootCloaking()
    }

    /**
     * Returns true if we can actually execute commands as root.
     */
    fun canExecuteAsRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val result = process.inputStream.bufferedReader().readLine()
            process.waitFor()
            result?.contains("uid=0") == true
        } catch (e: Exception) {
            Log.d(TAG, "Root execution test failed: ${e.message}")
            false
        }
    }

    private fun checkSuBinary(): Boolean {
        for (path in ROOT_PATHS) {
            if (File(path).exists()) {
                Log.d(TAG, "Found su binary at: $path")
                return true
            }
        }
        return false
    }

    private fun checkRootManagementApp(): Boolean {
        // Check via package manager (requires Context — done at app level)
        return false
    }

    private fun checkRootCloaking(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("which su")
            val result = process.inputStream.bufferedReader().readLine()
            process.waitFor()
            result?.isNotEmpty() == true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if root management apps are installed.
     */
    fun checkRootManagementApp(context: Context): Boolean {
        val pm = context.packageManager
        for (pkg in ROOT_MANAGEMENT_APPS) {
            try {
                pm.getPackageInfo(pkg, 0)
                Log.d(TAG, "Root management app found: $pkg")
                return true
            } catch (e: Exception) {
                // not installed
            }
        }
        return false
    }

    /**
     * Returns device ABI to select correct proot binary.
     */
    fun getDeviceAbi(): String {
        val supportedAbis = android.os.Build.SUPPORTED_ABIS
        return when {
            supportedAbis.contains("arm64-v8a") -> "aarch64"
            supportedAbis.contains("x86_64") -> "x86_64"
            supportedAbis.contains("armeabi-v7a") -> "armv7"
            else -> "aarch64" // fallback
        }
    }
}
