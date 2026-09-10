package com.mobilelinux.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mobilelinux.R
import com.mobilelinux.model.LinuxPackage
import com.mobilelinux.model.PackageCategory
import com.mobilelinux.model.PackageRepository
import com.mobilelinux.runtime.UbuntuRuntime
import com.mobilelinux.service.PackageInstallationManager
import com.mobilelinux.util.PackageProgressParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LibrariesActivity : AppCompatActivity() {

    private lateinit var runtime: UbuntuRuntime
    private lateinit var adapter: PackagesAdapter

    private val allPackages = mutableListOf<LinuxPackage>()
    private var selectedCategory: PackageCategory = PackageCategory.ALL
    private var searchQuery: String = ""

    private lateinit var rvPackages: RecyclerView
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var layoutCategories: LinearLayout
    private lateinit var tvPackageCount: TextView
    private lateinit var pbScanning: ProgressBar
    private lateinit var tvScanningLabel: TextView
    private lateinit var layoutEmpty: LinearLayout

    private lateinit var installerManager: PackageInstallationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_libraries)

        runtime = UbuntuRuntime.getInstance(this)
        installerManager = PackageInstallationManager.getInstance(this)

        // Listen to persistent background install and uninstall events
        lifecycleScope.launch {
            installerManager.progressEvents.collect { update ->
                val pkg = allPackages.firstOrNull { it.id == update.packageId }
                if (pkg != null) {
                    pkg.isInstalling = update.isInstalling
                    pkg.isInstalled = update.isInstalled
                    pkg.isUninstalling = update.isUninstalling
                    pkg.progressPercent = update.percent
                    pkg.statusText = update.stage
                    pkg.isActivated = update.isActivated
                    adapter.updateItem(pkg.id)
                }
            }
        }

        // Ensure container wrappers are fresh
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                runtime.installCommandWrappers()
                runtime.patchJupyterTemplatesForMobile()
            } catch (ignored: Exception) {}
        }

        initViews()
        initToolbar()
        initWindowInsets()
        initCategories()
        initSearch()
        initRecyclerView()

        loadPackages()
        scanInstalledPackages()
    }

    override fun onResume() {
        super.onResume()

        // Sync real-time progress from persistent background installer
        try {
            installerManager.activeStates.forEach { (pkgId, update) ->
                val pkg = allPackages.firstOrNull { it.id == pkgId }
                if (pkg != null) {
                    pkg.isInstalling = update.isInstalling
                    pkg.isInstalled = update.isInstalled
                    pkg.isUninstalling = update.isUninstalling
                    pkg.progressPercent = update.percent
                    pkg.statusText = update.stage
                    pkg.isActivated = update.isActivated
                    adapter.updateItem(pkg.id)
                }
            }
        } catch (ignored: Exception) {}

        // Synchronize package states with real disk filesystem when returning to screen
        val isCondaPresent = runtime.isCondaInstalled()
        val isCondaActive = runtime.isCondaActive()
        val isDesktopPresent = runtime.isDesktopInstalled()
        var stateChanged = false

        allPackages.forEach { pkg ->
            if (pkg.id == "miniconda" && !pkg.isInstalling && !pkg.isUninstalling && !pkg.isActivating) {
                if (isCondaPresent && !pkg.isInstalled) {
                    pkg.isInstalled = true
                    pkg.isActivated = isCondaActive
                    pkg.statusText = if (isCondaActive) "Active & Ready (base)" else "Installed. Click Activate to enable."
                    stateChanged = true
                    val prefs = getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)
                    val currentSet = getCachedInstalledIds(prefs)
                    currentSet.add("miniconda")
                    prefs.edit().putStringSet("installed_ids", currentSet).putBoolean("conda_active", isCondaActive).apply()
                } else if (!isCondaPresent && pkg.isInstalled) {
                    pkg.isInstalled = false
                    pkg.isActivated = false
                    pkg.statusText = "Ready to install"
                    stateChanged = true
                    val prefs = getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)
                    val currentSet = getCachedInstalledIds(prefs)
                    currentSet.remove("miniconda")
                    prefs.edit().putStringSet("installed_ids", currentSet).putBoolean("conda_active", false).apply()
                }

                // If Conda is physically present but not active in .bashrc / .condarc, auto-activate it in the background
                if (isCondaPresent && !isCondaActive) {
                    lifecycleScope.launch(Dispatchers.IO) {
                        try {
                            runtime.configureCondaEnvironment()
                            withContext(Dispatchers.Main) {
                                pkg.isActivated = true
                                pkg.statusText = "Active & Ready (base)"
                                val prefs = getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)
                                prefs.edit().putBoolean("conda_active", true).apply()
                                adapter.updateItem(pkg.id)
                            }
                        } catch (ignored: Exception) {}
                    }
                }
            } else if (pkg.id == "xfce4-desktop" && !pkg.isInstalling && !pkg.isUninstalling) {
                if (isDesktopPresent && !pkg.isInstalled) {
                    pkg.isInstalled = true
                    pkg.statusText = "Installed and ready"
                    stateChanged = true
                } else if (!isDesktopPresent && pkg.isInstalled) {
                    pkg.isInstalled = false
                    pkg.statusText = "Ready to install"
                    stateChanged = true
                }
            }
        }
        if (stateChanged) {
            applyFilters()
        }
    }

    private fun initViews() {
        rvPackages = findViewById(R.id.rv_packages)
        etSearch = findViewById(R.id.et_search)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        layoutCategories = findViewById(R.id.layout_categories)
        tvPackageCount = findViewById(R.id.tv_package_count)
        pbScanning = findViewById(R.id.pb_scanning)
        tvScanningLabel = findViewById(R.id.tv_scanning_label)
        layoutEmpty = findViewById(R.id.layout_empty)
    }

    private fun initToolbar() {
        val toolbar: Toolbar = findViewById(R.id.libraries_toolbar)
        setSupportActionBar(toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Libraries & Packages"
        }
    }

    private fun initWindowInsets() {
        val toolbar: Toolbar = findViewById(R.id.libraries_toolbar)

        // Top insets for status bar & display cutout / camera notch
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

        // Bottom insets for system navigation bar (Back, Home, Recents buttons or gesture bar)
        ViewCompat.setOnApplyWindowInsetsListener(rvPackages) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(
                left = navInsets.left,
                right = navInsets.right,
                bottom = navInsets.bottom + dpToPx(16)
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(layoutEmpty) { v, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(
                left = navInsets.left,
                right = navInsets.right,
                bottom = navInsets.bottom + dpToPx(24)
            )
            insets
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return if (item.itemId == android.R.id.home) {
            onBackPressedDispatcher.onBackPressed()
            true
        } else super.onOptionsItemSelected(item)
    }

    private fun initCategories() {
        layoutCategories.removeAllViews()
        PackageCategory.values().forEach { category ->
            val chip = TextView(this).apply {
                text = category.displayName
                textSize = 13f
                setPadding(dpToPx(14), dpToPx(7), dpToPx(14), dpToPx(7))
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(8)
                }
                layoutParams = params

                updateChipStyle(this, category == selectedCategory)

                setOnClickListener {
                    if (selectedCategory != category) {
                        selectedCategory = category
                        refreshCategoryChips()
                        applyFilters()
                    }
                }
            }
            layoutCategories.addView(chip)
        }
    }

    private fun refreshCategoryChips() {
        for (i in 0 until layoutCategories.childCount) {
            val chip = layoutCategories.getChildAt(i) as? TextView
            val category = PackageCategory.values().getOrNull(i)
            if (chip != null && category != null) {
                updateChipStyle(chip, category == selectedCategory)
            }
        }
    }

    private fun updateChipStyle(chip: TextView, isSelected: Boolean) {
        if (isSelected) {
            chip.setBackgroundResource(R.drawable.bg_chip_selected)
            chip.setTextColor(ContextCompat.getColor(this, R.color.colorOnPrimary))
            chip.setTypeface(null, android.graphics.Typeface.BOLD)
        } else {
            chip.setBackgroundResource(R.drawable.bg_chip_unselected)
            chip.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
            chip.setTypeface(null, android.graphics.Typeface.NORMAL)
        }
    }

    private fun initSearch() {
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                searchQuery = s?.toString()?.trim() ?: ""
                btnClearSearch.visibility = if (searchQuery.isNotEmpty()) View.VISIBLE else View.GONE
                applyFilters()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.text?.clear()
        }
    }

    private fun initRecyclerView() {
        adapter = PackagesAdapter(
            onInstallClick = { pkg -> queueOrInstallPackage(pkg) },
            onLaunchClick = { pkg -> launchPackage(pkg) },
            onActivateClick = { pkg -> activateConda(pkg) },
            onCopyClick = { pkg -> copyPackageCommand(pkg) },
            onUninstallClick = { pkg -> confirmAndUninstallPackage(pkg) }
        )
        rvPackages.layoutManager = LinearLayoutManager(this)
        rvPackages.adapter = adapter
    }

    private fun getCachedInstalledIds(prefs: SharedPreferences): MutableSet<String> {
        return (prefs.getStringSet("installed_ids", emptySet()) ?: emptySet()).toMutableSet()
    }

    private fun loadPackages() {
        allPackages.clear()
        val curated = PackageRepository.getCuratedPackages()

        // Instant Cache from SharedPreferences: shows installed status in 0ms on startup
        val prefs = getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)
        val savedInstalled = getCachedInstalledIds(prefs)
        val hasExplicitCache = prefs.contains("installed_ids")
        val isCondaBinaryPresent = runtime.isCondaInstalled()
        val isCondaActivePresent = runtime.isCondaActive()
        val isDesktopBinaryPresent = runtime.isDesktopInstalled()

        curated.forEach { pkg ->
            if (pkg.id == "miniconda") {
                if (isCondaBinaryPresent) {
                    pkg.isInstalled = true
                    pkg.isActivated = isCondaActivePresent
                    pkg.statusText = if (isCondaActivePresent) "Active & Ready (base)" else "Installed. Click Activate to enable."
                    if (!savedInstalled.contains("miniconda")) {
                        savedInstalled.add("miniconda")
                        prefs.edit().putStringSet("installed_ids", savedInstalled).putBoolean("conda_active", isCondaActivePresent).apply()
                    }
                } else {
                    pkg.isInstalled = false
                    pkg.isActivated = false
                    pkg.statusText = "Ready to install"
                    if (savedInstalled.contains("miniconda")) {
                        savedInstalled.remove("miniconda")
                        prefs.edit().putStringSet("installed_ids", savedInstalled).putBoolean("conda_active", false).apply()
                    }
                }
            } else if (pkg.id == "xfce4-desktop") {
                if (savedInstalled.contains("xfce4-desktop") || (!hasExplicitCache && isDesktopBinaryPresent) || isDesktopBinaryPresent) {
                    pkg.isInstalled = true
                    pkg.statusText = "Installed and ready"
                }
            } else if (savedInstalled.contains(pkg.id)) {
                pkg.isInstalled = true
                pkg.statusText = "Installed and ready"
            }
        }

        allPackages.addAll(curated)
        applyFilters()
    }

    private fun applyFilters() {
        val filtered = allPackages.filter { pkg ->
            val matchesCategory = (selectedCategory == PackageCategory.ALL || pkg.category == selectedCategory)
            val matchesSearch = if (searchQuery.isEmpty()) {
                true
            } else {
                pkg.name.contains(searchQuery, ignoreCase = true) ||
                pkg.description.contains(searchQuery, ignoreCase = true) ||
                pkg.version.contains(searchQuery, ignoreCase = true) ||
                pkg.id.contains(searchQuery, ignoreCase = true)
            }
            matchesCategory && matchesSearch
        }

        adapter.submitList(filtered)
        tvPackageCount.text = "Showing ${filtered.size} packages"

        if (filtered.isEmpty()) {
            layoutEmpty.visibility = View.VISIBLE
            rvPackages.visibility = View.GONE
        } else {
            layoutEmpty.visibility = View.GONE
            rvPackages.visibility = View.VISIBLE
        }
    }

    /**
     * Checks existing installations inside Ubuntu in the background using ultra-fast batch script
     */
    private fun scanInstalledPackages() {
        pbScanning.visibility = View.VISIBLE
        tvScanningLabel.visibility = View.VISIBLE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val batchScript = PackageRepository.getFastBatchCheckScript()
                val result = runtime.runCommand(batchScript)
                val installedIds = result.second
                    .lines()
                    .filter { it.startsWith("INSTALLED:") }
                    .map { it.substringAfter("INSTALLED:").trim() }
                    .toSet()

                // Real verification of Conda binary existence (0ms host filesystem check OR command)
                val realCondaInstalled = runtime.isCondaInstalled() || (
                    runtime.runCommand(
                        "[ -x /home/ubuntu/miniforge3/bin/conda ] || [ -x /home/ubuntu/miniconda3/bin/conda ] || [ -x /home/ubuntu/anaconda3/bin/conda ] || [ -x /root/miniconda3/bin/conda ] || [ -x /root/miniforge3/bin/conda ] || [ -x /root/anaconda3/bin/conda ] || [ -x /opt/conda/bin/conda ]"
                    ).first == 0
                )

                // Check whether Conda is already activated in .bashrc and .condarc
                var isCondaActivated = realCondaInstalled && (
                    runtime.isCondaActive() || runtime.runCommand(
                        "grep -q 'conda initialize' /home/ubuntu/.bashrc 2>/dev/null && [ -f /home/ubuntu/.condarc ]"
                    ).first == 0
                )

                // Auto-activate & auto-repair Conda if binary exists so user never has to manually init
                if (realCondaInstalled && !isCondaActivated) {
                    runtime.configureCondaEnvironment()
                    isCondaActivated = true
                }

                val finalInstalledIds = installedIds.toMutableSet()
                if (realCondaInstalled) {
                    finalInstalledIds.add("miniconda")
                } else {
                    finalInstalledIds.remove("miniconda")
                }

                // Update persistent disk cache with verified scan results
                val prefs = getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)
                prefs.edit()
                    .putStringSet("installed_ids", finalInstalledIds)
                    .putBoolean("conda_active", isCondaActivated)
                    .apply()

                allPackages.forEach { pkg ->
                    // Preserve status for packages currently installing, uninstalling, or activating
                    if (pkg.isInstalling || pkg.isUninstalling || pkg.isActivating) return@forEach

                    if (pkg.id == "miniconda") {
                        pkg.isInstalled = realCondaInstalled
                        pkg.isActivated = isCondaActivated
                        pkg.statusText = when {
                            !realCondaInstalled -> "Ready to install"
                            isCondaActivated -> "Active & Ready (base)"
                            else -> "Active & Ready (base)"
                        }
                    } else {
                        val isInst = finalInstalledIds.contains(pkg.id)
                        pkg.isInstalled = isInst
                        pkg.statusText = if (isInst) "Installed and ready" else "Ready to install"
                    }
                }
            } catch (ignored: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
                    allPackages.forEach { pkg ->
                        if (pkg.isInstalling) {
                            if (pkg.statusText.equals("Installed and ready", ignoreCase = true) ||
                                pkg.statusText.equals("Ready to install", ignoreCase = true)) {
                                pkg.statusText = "Installing ${pkg.name}..."
                            }
                        }
                    }
                    applyFilters()
                    pbScanning.visibility = View.GONE
                    tvScanningLabel.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Entry point for package installation with persistent background manager
     */
    private fun queueOrInstallPackage(pkg: LinuxPackage) {
        if (pkg.isInstalled) {
            Toast.makeText(this, "${pkg.name} is already installed.", Toast.LENGTH_SHORT).show()
            return
        }
        if (pkg.isInstalling) {
            Toast.makeText(this, "${pkg.name} is already in the installation queue.", Toast.LENGTH_SHORT).show()
            return
        }

        installerManager.enqueueInstall(pkg) { queuePos ->
            Toast.makeText(this, "${pkg.name} added to queue (Position #$queuePos)", Toast.LENGTH_SHORT).show()
        }
        Toast.makeText(this, "Starting installation of ${pkg.name}...", Toast.LENGTH_SHORT).show()
    }

    private fun launchPackage(pkg: LinuxPackage) {
        if (pkg.category == PackageCategory.DESKTOP_APPS || pkg.id == "xfce4-desktop") {
            startActivity(Intent(this, DesktopActivity::class.java))
            return
        }
        if (pkg.id == "jupyterlab") {
            lifecycleScope.launch(Dispatchers.IO) {
                try {
                    runtime.patchJupyterTemplatesForMobile()
                } catch (ignored: Exception) {}
            }
        }
        val url = pkg.launchUrl
        if (!url.isNullOrEmpty()) {
            try {
                DevBrowserActivity.openUrl(this, url)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Prompts user with a confirmation dialog to permanently uninstall/clean the package.
     */
    private fun confirmAndUninstallPackage(pkg: LinuxPackage) {
        if (pkg.isInstalling || pkg.isUninstalling || installerManager.isAnyInstallInProgress()) {
            Toast.makeText(this, "Please wait until active operations finish...", Toast.LENGTH_SHORT).show()
            return
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Uninstall ${pkg.name}?")
            .setMessage("Are you sure you want to permanently remove and clean '${pkg.name}' from your Linux system?\n\nThis will remove binaries, libraries, configurations, and free up storage space.")
            .setIcon(R.drawable.ic_trash)
            .setPositiveButton("Uninstall & Clean") { _, _ ->
                installerManager.enqueueUninstall(pkg)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Activates Conda base environment, runs conda init and hooks into .bashrc & .condarc
     */
    private fun activateConda(pkg: LinuxPackage) {
        if (pkg.isActivating) return
        if (installerManager.isAnyInstallInProgress()) {
            Toast.makeText(this, "Please wait for current installation to finish before activating Conda.", Toast.LENGTH_SHORT).show()
            return
        }

        pkg.isActivating = true
        pkg.statusText = "Verifying Conda installation..."
        adapter.updateItem(pkg.id)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Verify real conda binary exists using host check + guest command
                val isPresent = runtime.isCondaInstalled() || runtime.runCommand(
                    "[ -x /home/ubuntu/miniforge3/bin/conda ] || [ -x /home/ubuntu/miniconda3/bin/conda ] || [ -x /home/ubuntu/anaconda3/bin/conda ] || [ -x /root/miniconda3/bin/conda ] || [ -x /root/miniforge3/bin/conda ] || [ -x /root/anaconda3/bin/conda ] || [ -x /opt/conda/bin/conda ]"
                ).first == 0

                if (!isPresent) {
                    withContext(Dispatchers.Main) {
                        pkg.isActivating = false
                        pkg.isInstalled = false
                        pkg.isActivated = false
                        pkg.statusText = "Ready to install"
                        adapter.updateItem(pkg.id)
                        Toast.makeText(
                            this@LibrariesActivity,
                            "Conda binary not found. Please tap 'Install' to install Conda first.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    pkg.statusText = "Activating Conda base environment..."
                    adapter.updateItem(pkg.id)
                    Toast.makeText(this@LibrariesActivity, "Activating Conda base environment...", Toast.LENGTH_SHORT).show()
                }

                // Inject and verify .bashrc & .condarc & command wrappers via UbuntuRuntime
                runtime.configureCondaEnvironment()

                withContext(Dispatchers.Main) {
                    pkg.isActivating = false
                    pkg.isInstalled = true
                    pkg.isActivated = true
                    pkg.statusText = "Active & Ready (base)"

                    val prefs = getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)
                    val currentSet = getCachedInstalledIds(prefs)
                    currentSet.add("miniconda")
                    prefs.edit()
                        .putStringSet("installed_ids", currentSet)
                        .putBoolean("conda_active", true)
                        .apply()

                    adapter.updateItem(pkg.id)
                    Toast.makeText(
                        this@LibrariesActivity,
                        "Conda activated successfully! (base) environment is active.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pkg.isActivating = false
                    pkg.statusText = "Activation error: ${e.message}"
                    adapter.updateItem(pkg.id)
                    Toast.makeText(
                        this@LibrariesActivity,
                        "Failed to activate Conda: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    /**
     * Copies the package install command directly to system clipboard
     */
    private fun copyPackageCommand(pkg: LinuxPackage) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Install Command", pkg.installCommand)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(this, "Copied install command for ${pkg.name}", Toast.LENGTH_SHORT).show()
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
