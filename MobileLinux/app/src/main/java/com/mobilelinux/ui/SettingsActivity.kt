package com.mobilelinux.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SeekBarPreference
import androidx.preference.SwitchPreferenceCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mobilelinux.BuildConfig
import com.mobilelinux.R
import com.mobilelinux.runtime.RootDetector
import com.mobilelinux.service.BootReceiver
import com.mobilelinux.service.LinuxService
import com.mobilelinux.terminal.TerminalManager
import com.mobilelinux.util.StorageHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val toolbar = findViewById<Toolbar>(R.id.settings_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
        }

        val container = findViewById<View>(R.id.settings_container)

        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, insets ->
            val sysInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = sysInsets.top,
                left = sysInsets.left,
                right = sysInsets.right
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(container) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = navInsets.bottom)
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settings_container, SettingsFragment())
                .commit()
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed(); true
        } else super.onOptionsItemSelected(item)
    }

    class SettingsFragment : PreferenceFragmentCompat() {

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(R.xml.preferences, rootKey)
            setupTerminalCategory()
            setupSystemCategory()
            setupAboutCategory()
        }

        override fun onResume() {
            super.onResume()
            updateDynamicSummaries()
        }

        private fun setupTerminalCategory() {
            // Font size
            findPreference<SeekBarPreference>("pref_font_size")?.apply {
                summary = "${value} sp"
                setOnPreferenceChangeListener { _, newValue ->
                    summary = "$newValue sp"
                    true
                }
            }

            // Color scheme
            findPreference<ListPreference>("pref_color_scheme")?.apply {
                summary = entry ?: "GitHub Dark (Default)"
                setOnPreferenceChangeListener { _, newValue ->
                    val index = findIndexOfValue(newValue.toString())
                    summary = if (index >= 0) entries[index] else newValue.toString()
                    true
                }
            }
        }

        private fun setupSystemCategory() {
            val ctx = requireContext()

            // 1. Shared Storage (File Manager access)
            findPreference<Preference>("pref_shared_storage")?.apply {
                setOnPreferenceClickListener {
                    val act = activity as? AppCompatActivity
                    if (act != null) {
                        StorageHelper.requestAllFilesAccess(act)
                    }
                    true
                }
            }

            // 2. Root mode
            findPreference<SwitchPreferenceCompat>("pref_root_mode")?.apply {
                val isRooted = RootDetector.isRooted() && RootDetector.canExecuteAsRoot()
                if (!isRooted) {
                    isEnabled = false
                    isChecked = false
                    summary = "Not available (Device unrooted — secure rootless PRoot mode active)"
                } else {
                    summary = "Device rooted — Toggle to run chroot instead of PRoot"
                }
            }

            // 3. Auto start on boot
            findPreference<SwitchPreferenceCompat>("pref_auto_start_on_boot")?.apply {
                setOnPreferenceChangeListener { _, newValue ->
                    val enable = newValue as? Boolean ?: false
                    try {
                        val component = ComponentName(ctx, BootReceiver::class.java)
                        ctx.packageManager.setComponentEnabledSetting(
                            component,
                            if (enable) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                            else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                        Toast.makeText(ctx, if (enable) "Auto-start enabled" else "Auto-start disabled", Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, "Failed to update boot receiver: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
            }

            // 4. Battery Optimization
            findPreference<Preference>("pref_battery_optimization")?.apply {
                setOnPreferenceClickListener {
                    try {
                        val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                            data = Uri.parse("package:${ctx.packageName}")
                        }
                        startActivity(intent)
                    } catch (e: Exception) {
                        try {
                            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                        } catch (e2: Exception) {
                            Toast.makeText(ctx, "Cannot open battery settings", Toast.LENGTH_SHORT).show()
                        }
                    }
                    true
                }
            }

            // 5. Reinstall Ubuntu
            findPreference<Preference>("pref_reinstall")?.apply {
                setOnPreferenceClickListener {
                    showReinstallConfirmationDialog()
                    true
                }
            }
        }

        private fun setupAboutCategory() {
            findPreference<Preference>("pref_version")?.apply {
                summary = "v${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})"
            }

            findPreference<Preference>("pref_ubuntu_version")?.apply {
                summary = "Ubuntu 24.04 LTS (Noble Numbat) ARM64"
            }
        }

        private fun updateDynamicSummaries() {
            val ctx = context ?: return

            // Update Storage Permission summary
            findPreference<Preference>("pref_shared_storage")?.apply {
                val hasAccess = StorageHelper.hasAllFilesAccess()
                summary = if (hasAccess) {
                    "✓ Granted — Full access to /sdcard/MobileLinux active"
                } else {
                    "Tap to allow full storage access (recommended for File Manager)"
                }
            }

            // Update Battery Optimization summary
            findPreference<Preference>("pref_battery_optimization")?.apply {
                val pm = ctx.getSystemService(Context.POWER_SERVICE) as? PowerManager
                val isIgnoring = pm?.isIgnoringBatteryOptimizations(ctx.packageName) == true
                summary = if (isIgnoring) {
                    "✓ Disabled (Exempted — Background sessions will not be killed)"
                } else {
                    "Active (Optimized — Tap to exempt for continuous background use)"
                }
            }
        }

        private fun showReinstallConfirmationDialog() {
            val ctx = requireContext()
            MaterialAlertDialogBuilder(ctx)
                .setTitle("Reinstall Ubuntu 24.04?")
                .setMessage("This will reset the Ubuntu environment to its initial state. Your shared files in ~/MobileLinux will not be deleted.")
                .setPositiveButton("Reinstall") { _, _ ->
                    performReinstall()
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        private fun performReinstall() {
            val ctx = requireContext()
            Toast.makeText(ctx, "Resetting Ubuntu environment...", Toast.LENGTH_SHORT).show()

            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    // Stop service & terminate sessions
                    TerminalManager.getInstance(ctx).killAllSessions()
                    LinuxService.stop(ctx)

                    // Clear setup flags and rootfs
                    File(ctx.filesDir, ".setup_complete").delete()
                    File(ctx.filesDir, "ubuntu-rootfs/.rootfs_extracted").delete()
                    ctx.getSharedPreferences("mobilelinux_prefs", Context.MODE_PRIVATE)
                        .edit().putBoolean("setup_complete", false).apply()

                    withContext(Dispatchers.Main) {
                        val intent = Intent(ctx, SetupActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(intent)
                        activity?.finish()
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(ctx, "Reinstall failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }
}
