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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

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

    // Android 13+ notification permission request
    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                // Notification permission granted — service notifications will show
            }
            // Whether granted or not, continue starting the service
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

        // Setup shared MobileLinux folder in File Manager
        com.mobilelinux.util.StorageHelper.setupSharedStorage(this)
        com.mobilelinux.util.StorageHelper.requestAllFilesAccess(this)

        // Request notification permission (Android 13+), then start service
        requestNotificationPermissionAndStart()

        // Request battery optimization exemption for background persistence
        requestBatteryOptimizationExemption()

        // Create initial session if none exists
        if (viewModel.sessions.value.isEmpty()) {
            viewModel.createSession("Main")
        }
        hasCreatedInitialSession = true
    }

    override fun onResume() {
        super.onResume()
        // Refresh shared storage in case user just granted permission in Settings
        com.mobilelinux.util.StorageHelper.setupSharedStorage(this)

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

    private fun requestNotificationPermissionAndStart() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+ — must request POST_NOTIFICATIONS at runtime
            when {
                ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED -> {
                    startLinuxService()
                }
                shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> {
                    // Show rationale then request
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                else -> {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        } else {
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
                    android.util.Log.d("MainActivity", "All sessions closed — terminating application")
                    LinuxService.stop(this@MainActivity)
                    finishAffinity()
                }
            }
        }
    }

    private fun showTerminalFragment(session: TerminalSession) {
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
            R.id.action_new_session -> {
                val session = viewModel.createSession()
                showTerminalFragment(session)
                closeDrawer()
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

    override fun onDestroy() {
        super.onDestroy()
        if (serviceConnected) {
            try { unbindService(serviceConnection) } catch (e: Exception) { /* ignore */ }
        }
        // DO NOT stop LinuxService — Ubuntu sessions keep running
    }
}
