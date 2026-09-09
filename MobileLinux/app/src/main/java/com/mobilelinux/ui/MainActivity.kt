package com.mobilelinux.ui

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.view.MenuItem
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.lifecycle.lifecycleScope
import com.mobilelinux.R
import com.mobilelinux.service.LinuxService
import com.mobilelinux.terminal.TerminalSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Main activity — DrawerLayout with session sidebar + terminal.
 * Android 14/15/16 compatible:
 *  - POST_NOTIFICATIONS permission requested at runtime (Android 13+)
 *  - Battery optimization exemption request
 *  - moveTaskToBack() instead of finish() to keep sessions alive
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels { MainViewModel.Factory(this) }

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var toolbar: Toolbar
    private lateinit var drawerToggle: ActionBarDrawerToggle

    private var linuxService: LinuxService? = null
    private var serviceConnected = false
    private var hasCreatedInitialSession = false
    private var browserUrlObserver: com.mobilelinux.util.BrowserUrlTriggerObserver? = null

    // Runtime permissions request for storage, notifications, camera & microphone
    private val appPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            // After standard permissions dialogs, check and request All Files Access on Android 11+ if not granted
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !com.mobilelinux.util.StorageHelper.hasAllFilesAccess()) {
                com.mobilelinux.util.StorageHelper.requestAllFilesAccess(this)
            }
            // Proceed to start Linux background service regardless of individual permissions
            startLinuxService()
        }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            linuxService = (binder as? LinuxService.LinuxBinder)?.getService()
            serviceConnected = true
        }
        override fun onServiceDisconnected(name: ComponentName?) {
            linuxService = null
            serviceConnected = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Check if first-run setup is needed
        if (!isSetupComplete()) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        setupToolbar()
        setupDrawer()
        setupSidebar()
        observeSessionChanges()

        // Create initial session asynchronously off the main thread to ensure splash screen dismisses instantly (<50ms)
        if (viewModel.sessions.value.isEmpty()) {
            toolbar.subtitle = "Starting..."
            lifecycleScope.launch {
                try {
                    // Maximum 10s timeout protection against PRoot stalls
                    kotlinx.coroutines.withTimeoutOrNull(10000L) {
                        viewModel.createSessionAsync("Main")
                    } ?: run {
                        android.util.Log.e("MainActivity", "Initial session creation timed out, attempting retry")
                        if (viewModel.sessions.value.isEmpty()) {
                            viewModel.createSessionAsync("Main")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Failed to create initial session: ${e.message}", e)
                } finally {
                    hasCreatedInitialSession = true
                }
            }
        } else {
            hasCreatedInitialSession = true
            viewModel.activeSessionId.value?.let { sid ->
                val session = viewModel.sessions.value.find { it.id == sid }
                session?.let {
                    showTerminalFragment(it)
                    toolbar.subtitle = it.name
                }
            }
        }

        // Request initial development permissions (Notifications, Camera, Microphone)
        requestInitialPermissionsAndStart()

        // Request battery optimization exemption for background persistence
        requestBatteryOptimizationExemption()

        // Asynchronously setup storage, command wrappers, and browser triggers off the UI thread
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.mobilelinux.util.StorageHelper.setupSharedStorage(this@MainActivity)
                val runtime = com.mobilelinux.runtime.UbuntuRuntime.getInstance(this@MainActivity)
                runtime.installCommandWrappers()
                val shmDir = runtime.ensureSharedMemoryReady()
                val tmpDir = java.io.File(runtime.rootfsDir, "tmp")
                val ubuntuHome = java.io.File(runtime.rootfsDir, "home/ubuntu")
                withContext(Dispatchers.Main) {
                    browserUrlObserver = com.mobilelinux.util.BrowserUrlTriggerObserver(this@MainActivity, listOf(shmDir, tmpDir, ubuntuHome)).apply {
                        start()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MainActivity", "Background startup I/O: ${e.message}")
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Refresh shared storage asynchronously
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                com.mobilelinux.util.StorageHelper.setupSharedStorage(this@MainActivity)
            } catch (ignored: Exception) {}
        }

        // Apply keep screen on preference
        try {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(this)
            val keepOn = prefs.getBoolean("pref_keep_screen_on", false)
            if (keepOn) {
                window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        } catch (ignored: Exception) {}
    }

    private fun requestInitialPermissionsAndStart() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CAMERA)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.RECORD_AUDIO)
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            appPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !com.mobilelinux.util.StorageHelper.hasAllFilesAccess()) {
                com.mobilelinux.util.StorageHelper.requestAllFilesAccess(this)
            }
            startLinuxService()
        }
    }

    private fun startLinuxService() {
        LinuxService.start(this)
        bindService(
            Intent(this, LinuxService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    /**
     * Requests battery optimization exemption.
     * Without this, Android may kill the background service on modern devices.
     * On Android 13+ this requires user to manually allow in settings.
     */
    private fun requestBatteryOptimizationExemption() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(packageName)) {
            try {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: Exception) {
                // Device doesn't support this intent — ignore
            }
        }
    }

    private fun isSetupComplete(): Boolean {
        return getSharedPreferences("mobilelinux_prefs", Context.MODE_PRIVATE)
            .getBoolean("setup_complete", false)
    }

    private fun setupToolbar() {
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            title = "MobileLinux"
            setDisplayHomeAsUpEnabled(true)
        }

        // Avoid camera notch and status bar overlapping toolbar content
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = insets.top,
                left = insets.left,
                right = insets.right
            )
            windowInsets
        }
    }

    private fun setupDrawer() {
        drawerLayout = findViewById(R.id.drawer_layout)
        drawerToggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar,
            R.string.drawer_open, R.string.drawer_close
        )
        drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()
    }

    private fun setupSidebar() {
        if (supportFragmentManager.findFragmentById(R.id.fragment_sidebar) == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.fragment_sidebar, SessionSidebarFragment())
                .commit()
        }
    }

    private fun observeSessionChanges() {
        lifecycleScope.launch {
            viewModel.activeSessionId.collectLatest { sessionId ->
                if (sessionId != null) {
                    val session = viewModel.sessions.value.find { it.id == sessionId }
                    session?.let {
                        showTerminalFragment(it)
                        toolbar.subtitle = it.name
                    }
                } else if (hasCreatedInitialSession && viewModel.sessions.value.isEmpty()) {
                    // Fallback: If sessions list becomes empty while activity remains active, spawn a fresh session
                    viewModel.createSessionAsync("Main")
                }
            }
        }
    }

    internal fun showTerminalFragment(session: TerminalSession) {
        toolbar.subtitle = session.name
        val current = supportFragmentManager.findFragmentById(R.id.fragment_terminal)
        if (current is TerminalFragment) {
            current.switchToSession(session.id)
            return
        }

        val fragment = TerminalFragment.newInstance(session.id)
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_terminal, fragment, "terminal_${session.id}")
            .commitAllowingStateLoss()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (drawerToggle.onOptionsItemSelected(item)) return true
        return when (item.itemId) {
            R.id.action_open_browser -> {
                openDevBrowser()
                true
            }
            R.id.action_new_session -> {
                lifecycleScope.launch {
                    val session = viewModel.createSessionAsync()
                    showTerminalFragment(session)
                    closeDrawer()
                }
                true
            }
            R.id.action_desktop_mode -> {
                launchDesktopMode()
                true
            }
            R.id.action_libraries -> {
                openLibraries(); true
            }
            R.id.action_settings -> {
                openSettings(); true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onCreateOptionsMenu(menu: android.view.Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            // Move to background — sessions keep running
            moveTaskToBack(true)
        }
    }

    fun closeDrawer() = drawerLayout.closeDrawer(GravityCompat.START)
    fun openLibraries() = startActivity(Intent(this, LibrariesActivity::class.java))
    fun openSettings() = startActivity(Intent(this, SettingsActivity::class.java))
    fun openDevBrowser(url: String = DevBrowserActivity.DEFAULT_HOME_URL) {
        DevBrowserActivity.openUrl(this, url)
    }

    private fun launchDesktopMode() {
        val runtime = com.mobilelinux.runtime.UbuntuRuntime.getInstance(this)
        if (runtime.isDesktopInstalled()) {
            startActivity(Intent(this, DesktopActivity::class.java))
        } else {
            showInstallDesktopPrompt()
        }
    }

    private fun showInstallDesktopPrompt() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reinstall Desktop Mode?")
            .setMessage("Desktop Mode is currently not installed. Would you like to reinstall and launch the XFCE4 desktop environment now?")
            .setPositiveButton("Reinstall & Launch") { _, _ ->
                startDesktopInstallation()
            }
            .setNeutralButton("Libraries & Packages") { _, _ ->
                openLibraries()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startDesktopInstallation() {
        val runtime = com.mobilelinux.runtime.UbuntuRuntime.getInstance(this)

        val progressLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
        }

        val progressBar = ProgressBar(this).apply {
            isIndeterminate = true
        }
        val statusTv = TextView(this).apply {
            text = "Starting package installation (takes 1-3 mins)..."
            setTextColor(android.graphics.Color.parseColor("#C9D1D9"))
            setPadding(0, (14 * resources.displayMetrics.density).toInt(), 0, 0)
        }

        progressLayout.addView(progressBar)
        progressLayout.addView(statusTv)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Installing XFCE4 & TigerVNC")
            .setView(progressLayout)
            .setCancelable(false)
            .create()

        dialog.show()

        lifecycleScope.launch(Dispatchers.IO) {
            val cmd = "export DEBIAN_FRONTEND=noninteractive; sudo apt-get update && sudo apt-get install -y --no-install-recommends xfce4 xfce4-terminal tigervnc-standalone-server tigervnc-common dbus-x11"
            val result = runtime.runCommand(cmd, timeoutSeconds = 600L) { line ->
                val trimmed = line.trim()
                if (trimmed.isNotEmpty()) {
                    lifecycleScope.launch(Dispatchers.Main) {
                        statusTv.text = trimmed
                    }
                }
            }

            // Re-install command wrappers to ensure desktop-start is generated
            runtime.installCommandWrappers()

            withContext(Dispatchers.Main) {
                dialog.dismiss()
                if (runtime.isDesktopInstalled()) {
                    try {
                        val prefs = getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)
                        val set = (prefs.getStringSet("installed_ids", emptySet()) ?: emptySet()).toMutableSet()
                        set.add("xfce4-desktop")
                        prefs.edit().putStringSet("installed_ids", set).apply()
                    } catch (ignored: Exception) {}
                    Toast.makeText(this@MainActivity, "Desktop Installed Successfully!", Toast.LENGTH_SHORT).show()
                    startActivity(Intent(this@MainActivity, DesktopActivity::class.java))
                } else {
                    MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle("Installation Incomplete")
                        .setMessage("Installation finished with exit code ${result.first}. Please check your internet connection or try installing via 'Libraries & Packages'.")
                        .setPositiveButton("OK", null)
                        .setNeutralButton("Open Store") { _, _ -> openLibraries() }
                        .show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        browserUrlObserver?.stop()
        if (serviceConnected) {
            try { unbindService(serviceConnection) } catch (e: Exception) { /* ignore */ }
        }
        // DO NOT stop LinuxService — Ubuntu sessions keep running
    }
}
