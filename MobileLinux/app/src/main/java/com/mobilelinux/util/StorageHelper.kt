package com.mobilelinux.util

import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import java.io.File

/**
 * Manages shared storage folders between Android and the Ubuntu Linux environment.
 * Ensures the "MobileLinux" folder is visible in phone's File Manager app.
 */
object StorageHelper {

    private const val TAG = "StorageHelper"
    const val FOLDER_NAME = "MobileLinux"

    /**
     * Creates the MobileLinux folder in multiple accessible locations so it is
     * guaranteed to appear in the phone's File Manager:
     * 1. /sdcard/Download/MobileLinux (Always visible in Downloads category on Android 11-16)
     * 2. /sdcard/MobileLinux (Visible in root storage if MANAGE_EXTERNAL_STORAGE is granted)
     * 3. /sdcard/Android/data/<pkg>/files/MobileLinux (App-specific fallback)
     */
    fun setupSharedStorage(context: Context): List<File> {
        val createdDirs = mutableListOf<File>()

        // 1. Public Downloads directory: /sdcard/Download/MobileLinux
        // This is 100% permitted without special permissions on Android 11, 12, 13, 14, 15, 16.
        try {
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (downloadsDir != null) {
                val mlDownloadDir = File(downloadsDir, FOLDER_NAME)
                if (mlDownloadDir.exists() || mlDownloadDir.mkdirs()) {
                    createdDirs.add(mlDownloadDir)
                    writeReadmeFile(mlDownloadDir)
                    Log.i(TAG, "Created shared folder in Downloads: ${mlDownloadDir.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not create Download/$FOLDER_NAME: ${e.message}")
        }

        // 2. Root of External Storage: /sdcard/MobileLinux
        // Works if MANAGE_EXTERNAL_STORAGE is granted or on Android 10 and below
        try {
            val rootSd = Environment.getExternalStorageDirectory()
            if (rootSd != null && (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager())) {
                val mlRootDir = File(rootSd, FOLDER_NAME)
                if (mlRootDir.exists() || mlRootDir.mkdirs()) {
                    createdDirs.add(mlRootDir)
                    writeReadmeFile(mlRootDir)
                    Log.i(TAG, "Created shared folder in SDCard root: ${mlRootDir.absolutePath}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not create /sdcard/$FOLDER_NAME: ${e.message}")
        }

        // 3. App-specific external storage: /sdcard/Android/data/.../files/MobileLinux
        try {
            val appExtDir = context.getExternalFilesDir(FOLDER_NAME)
            if (appExtDir != null && (appExtDir.exists() || appExtDir.mkdirs())) {
                createdDirs.add(appExtDir)
                writeReadmeFile(appExtDir)
                Log.i(TAG, "Created app external folder: ${appExtDir.absolutePath}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not create app external files: ${e.message}")
        }

        // Scan all created directories and README files so Android File Manager sees them immediately
        try {
            val pathsToScan = mutableListOf<String>()
            for (dir in createdDirs) {
                pathsToScan.add(dir.absolutePath)
                val readme = File(dir, "README.txt")
                if (readme.exists()) {
                    pathsToScan.add(readme.absolutePath)
                }
            }
            if (pathsToScan.isNotEmpty()) {
                MediaScannerConnection.scanFile(
                    context.applicationContext,
                    pathsToScan.toTypedArray(),
                    null
                ) { path, uri ->
                    Log.d(TAG, "MediaScanner indexed: $path -> $uri")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "MediaScanner error: ${e.message}")
        }

        return createdDirs
    }

    /**
     * Returns the best directory to bind mount into Ubuntu as ~/MobileLinux
     */
    fun getPreferredSharedDir(context: Context): File {
        // First check root /sdcard/MobileLinux if it exists and is writable
        val rootSd = File(Environment.getExternalStorageDirectory(), FOLDER_NAME)
        if (rootSd.exists() && rootSd.canWrite()) {
            return rootSd
        }

        // Otherwise use Download/MobileLinux (universally accessible)
        val downloadDir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), FOLDER_NAME)
        if (downloadDir.exists()) {
            return downloadDir
        }

        // Fallback to app external files dir
        val appExtDir = context.getExternalFilesDir(FOLDER_NAME)
        if (appExtDir != null && appExtDir.exists()) {
            return appExtDir
        }

        // Final fallback
        return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    }

    private fun writeReadmeFile(dir: File) {
        try {
            val readme = File(dir, "README.txt")
            if (!readme.exists()) {
                readme.writeText(
                    """
                    ===================================================================
                      MobileLinux Shared Storage
                    ===================================================================
                    Files placed inside this folder can be accessed directly inside
                    your Ubuntu 24.04 Linux container at:

                        ~/MobileLinux
                      or:
                        /sdcard/MobileLinux
                      or:
                        /sdcard/Download/MobileLinux

                    Examples from inside Ubuntu:
                        ls ~/MobileLinux
                        cp my_script.py ~/MobileLinux/
                        python3 ~/MobileLinux/my_script.py

                    Enjoy running full Linux on your Android phone!
                    ===================================================================
                    """.trimIndent() + "\n"
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write README in ${dir.name}: ${e.message}")
        }
    }

    /**
     * Checks if All Files Access is granted on Android 11+ (API 30+)
     */
    fun hasAllFilesAccess(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    /**
     * Prompts user to grant All Files Access in system settings if needed
     */
    fun requestAllFilesAccess(activity: AppCompatActivity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:${activity.packageName}")
                    }
                    activity.startActivity(intent)
                } catch (e: Exception) {
                    try {
                        val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                        activity.startActivity(intent)
                    } catch (ignored: Exception) {}
                }
            }
        }
    }
}
