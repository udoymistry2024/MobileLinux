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

    // Sequential Installation Queue & Concurrency Safety
    private val installQueue = ArrayDeque<LinuxPackage>()
    private var isQueueProcessing = false
    @Volatile private var isUninstallRunning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_libraries)

        runtime = UbuntuRuntime.getInstance(this)

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

        curated.forEach { pkg ->
            if (pkg.id == "miniconda") {
                if (savedInstalled.contains("miniconda")) {
                    pkg.isInstalled = true
                    pkg.isActivated = true
                    pkg.statusText = "Active & Ready (base)"
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

                // Real verification of Conda binary existence
                val realCondaInstalled = runtime.runCommand(
                    "[ -x /home/ubuntu/miniforge3/bin/conda ] || [ -x /root/miniconda3/bin/conda ] || [ -x /opt/conda/bin/conda ]"
                ).first == 0

                // Check whether Conda is already activated in .bashrc and .condarc
                var isCondaActivated = realCondaInstalled && runtime.runCommand(
                    "grep -q 'conda initialize' /home/ubuntu/.bashrc 2>/dev/null && [ -f /home/ubuntu/.condarc ]"
                ).first == 0

                // Auto-activate & auto-repair Conda if binary exists so user never has to manually init
                if (realCondaInstalled && !isCondaActivated) {
                    runtime.configureCondaEnvironment()
                    isCondaActivated = true
                }

                val finalInstalledIds = installedIds.toMutableSet()
                if (realCondaInstalled) {
                    finalInstalledIds.add("miniconda")
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
     * Entry point for package installation with FIFO Queue & Concurrency Protection
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

        if (!isQueueProcessing) {
            isQueueProcessing = true
            executeInstall(pkg)
        } else {
            installQueue.addLast(pkg)
            pkg.isInstalling = true
            pkg.progressPercent = -1
            val queuePos = installQueue.size
            pkg.statusText = "Queued (Pending #$queuePos in line)"
            adapter.updateItem(pkg.id)
            Toast.makeText(this, "${pkg.name} added to queue (Position #$queuePos)", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Executes the actual installation process for a package
     */
    private fun executeInstall(pkg: LinuxPackage) {
        pkg.isInstalling = true
        pkg.progressPercent = 5
        pkg.statusText = "Starting installation..."
        adapter.updateItem(pkg.id)

        Toast.makeText(this, "Starting installation of ${pkg.name}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val parser = PackageProgressParser(pkg.name)
            var lastUpdateMs = 0L

            // Smooth progress ticker for long silent phases (e.g., extracting 15,000 wheels in PRoot)
            val tickerJob = lifecycleScope.launch(Dispatchers.Main) {
                while (pkg.isInstalling) {
                    kotlinx.coroutines.delay(2000)
                    if (pkg.isInstalling && pkg.progressPercent in 65..91) {
                        val next = pkg.progressPercent + 1
                        pkg.progressPercent = next
                        if (pkg.statusText.contains("unpacking", ignoreCase = true) ||
                            pkg.statusText.contains("installing", ignoreCase = true) ||
                            !pkg.statusText.contains("%")) {
                            pkg.statusText = "Unpacking & configuring files ($next%)..."
                        }
                        adapter.updateItem(pkg.id)
                    }
                }
            }

            try {
                // Safety 1: Wait if background essential tools are installing (APT lock contention)
                if (runtime.isInstallingTools) {
                    withContext(Dispatchers.Main) {
                        pkg.statusText = "Waiting for system setup..."
                        adapter.updateItem(pkg.id)
                    }
                    // Wait up to 90 seconds for background install to finish
                    var waited = 0
                    while (runtime.isInstallingTools && waited < 90) {
                        kotlinx.coroutines.delay(1000)
                        waited++
                    }
                }

                // Safety 2: Clean locks + fix dpkg in one atomic guest command (prevents lock recreation race)
                runtime.cleanupAptLocks()
                runtime.runCommand(
                    "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* /var/lib/dpkg/updates/* /var/cache/debconf/*.lock /var/cache/debconf/*-lock 2>/dev/null; " +
                    "sudo dpkg --configure -a 2>/dev/null || true"
                )

                // Safety 3: Ensure pip.conf is present before running install
                runtime.runCommand("sudo mkdir -p /etc && printf '[global]\\nbreak-system-packages = true\\n' | sudo tee /etc/pip.conf >/dev/null 2>&1 || true")

                val result = runtime.runCommand(pkg.installCommand) { line ->
                    val update = parser.parseLine(line)
                    val now = System.currentTimeMillis()
                    if (update.percent != pkg.progressPercent || now - lastUpdateMs > 200) {
                        lastUpdateMs = now
                        lifecycleScope.launch(Dispatchers.Main) {
                            pkg.progressPercent = update.percent
                            pkg.statusText = if (update.percent > 0) {
                                "${update.stage} (${update.percent}%)"
                            } else {
                                update.stage
                            }
                            adapter.updateItem(pkg.id)
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    pkg.isInstalling = false
                    if (result.first == 0) {
                        val prefs = getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)

                        if (pkg.id == "miniconda") {
                            val checkConda = withContext(Dispatchers.IO) {
                                runtime.runCommand(
                                    "[ -x /home/ubuntu/miniforge3/bin/conda ] || [ -x /root/miniconda3/bin/conda ] || [ -x /opt/conda/bin/conda ]"
                                )
                            }
                            if (checkConda.first == 0) {
                                pkg.isInstalled = true
                                pkg.isActivated = true
                                pkg.progressPercent = 100
                                pkg.statusText = "Active & Ready (base)"
                                val currentSet = getCachedInstalledIds(prefs)
                                currentSet.add(pkg.id)
                                prefs.edit().putStringSet("installed_ids", currentSet).putBoolean("conda_active", true).apply()
                                withContext(Dispatchers.IO) {
                                    runtime.configureCondaEnvironment()
                                }
                                adapter.updateItem(pkg.id)
                                Toast.makeText(
                                    this@LibrariesActivity,
                                    "Miniconda3 / Conda installed and activated successfully! (base) is active.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                pkg.isInstalled = false
                                pkg.isActivated = false
                                pkg.progressPercent = -1
                                pkg.statusText = "Install completed but binary missing"
                                val currentSet = getCachedInstalledIds(prefs)
                                currentSet.remove(pkg.id)
                                prefs.edit().putStringSet("installed_ids", currentSet).apply()
                                adapter.updateItem(pkg.id)
                                Toast.makeText(
                                    this@LibrariesActivity,
                                    "Conda installation finished, but binary not found.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            val checkResult = withContext(Dispatchers.IO) {
                                runtime.runCommand(pkg.checkInstalledCommand)
                            }
                            if (checkResult.first == 0) {
                                pkg.isInstalled = true
                                pkg.progressPercent = 100
                                pkg.statusText = "Installed and ready"
                                val currentSet = getCachedInstalledIds(prefs)
                                currentSet.add(pkg.id)
                                prefs.edit().putStringSet("installed_ids", currentSet).apply()
                                if (pkg.id == "jupyterlab") {
                                    withContext(Dispatchers.IO) {
                                        try {
                                            runtime.patchJupyterTemplatesForMobile()
                                        } catch (ignored: Exception) {}
                                    }
                                }
                                adapter.updateItem(pkg.id)
                                Toast.makeText(
                                    this@LibrariesActivity,
                                    "${pkg.name} installed successfully.",
                                    Toast.LENGTH_LONG
                                ).show()
                            } else {
                                pkg.isInstalled = false
                                pkg.progressPercent = -1
                                pkg.statusText = "Install completed, check failed"
                                val currentSet = getCachedInstalledIds(prefs)
                                currentSet.remove(pkg.id)
                                prefs.edit().putStringSet("installed_ids", currentSet).apply()
                                adapter.updateItem(pkg.id)
                                Toast.makeText(
                                    this@LibrariesActivity,
                                    "${pkg.name} install process completed, but package check failed.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    } else {
                        android.util.Log.e("LibrariesActivity", "Install error for ${pkg.id} (code ${result.first}): ${result.second}")
                        pkg.isInstalled = false
                        pkg.progressPercent = -1
                        val errorSnippet = result.second.lines()
                            .map { it.replace(Regex("\u001B\\[[;?0-9]*[a-zA-Z]"), "").trim() }
                            .filter { it.isNotBlank() && (it.startsWith("E:") || it.startsWith("npm error") || it.contains("error:", ignoreCase = true) || it.contains("failed", ignoreCase = true) || it.contains("not found", ignoreCase = true)) }
                            .lastOrNull()?.take(70)
                            ?: result.second.lines().map { it.trim() }.filter { it.isNotBlank() }.lastOrNull()?.take(70)

                        pkg.statusText = if (!errorSnippet.isNullOrBlank()) {
                            "Failed (${result.first}): $errorSnippet"
                        } else {
                            "Install failed (Exit code: ${result.first})"
                        }
                        adapter.updateItem(pkg.id)
                        Toast.makeText(
                            this@LibrariesActivity,
                            "Failed to install ${pkg.name}. Exit code: ${result.first}",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pkg.isInstalling = false
                    pkg.progressPercent = -1
                    pkg.statusText = "Error: ${e.message}"
                    adapter.updateItem(pkg.id)
                    Toast.makeText(
                        this@LibrariesActivity,
                        "Error installing ${pkg.name}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                tickerJob.cancel()
                withContext(Dispatchers.Main) {
                    processNextInQueue()
                }
            }
        }
    }

    private fun processNextInQueue() {
        if (installQueue.isNotEmpty()) {
            val nextPkg = installQueue.removeFirst()
            // Update queue position numbers for remaining packages
            installQueue.forEachIndexed { index, queuedPkg ->
                queuedPkg.statusText = "Queued (Pending #${index + 1} in line)"
                adapter.updateItem(queuedPkg.id)
            }
            executeInstall(nextPkg)
        } else {
            isQueueProcessing = false
        }
    }

    private fun launchPackage(pkg: LinuxPackage) {
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
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Could not open browser: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * Prompts user with a confirmation dialog to permanently uninstall/clean the package.
     */
    private fun confirmAndUninstallPackage(pkg: LinuxPackage) {
        if (pkg.isInstalling || pkg.isUninstalling || isQueueProcessing || isUninstallRunning) {
            Toast.makeText(this, "Please wait until active operations finish...", Toast.LENGTH_SHORT).show()
            return
        }

        com.google.android.material.dialog.MaterialAlertDialogBuilder(this)
            .setTitle("Uninstall ${pkg.name}?")
            .setMessage("Are you sure you want to permanently remove and clean '${pkg.name}' from your Linux system?\n\nThis will remove binaries, libraries, configurations, and free up storage space.")
            .setIcon(R.drawable.ic_trash)
            .setPositiveButton("Uninstall & Clean") { _, _ ->
                executeUninstall(pkg)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    /**
     * Executes clean removal and purge of a package, updating state and disk cache.
     */
    private fun executeUninstall(pkg: LinuxPackage) {
        pkg.isUninstalling = true
        isUninstallRunning = true
        pkg.progressPercent = 10
        pkg.statusText = "Starting uninstallation..."
        adapter.updateItem(pkg.id)

        Toast.makeText(this, "Uninstalling ${pkg.name}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            val parser = PackageProgressParser(pkg.name)
            var lastUpdateMs = 0L

            try {
                // Safety: Clean leftover locks + fix dpkg in single atomic command
                runtime.cleanupAptLocks()
                runtime.runCommand(
                    "sudo rm -f /var/lib/apt/lists/lock /var/cache/apt/archives/lock /var/lib/dpkg/lock* /var/lib/dpkg/updates/* /var/cache/debconf/*.lock /var/cache/debconf/*-lock 2>/dev/null; " +
                    "sudo dpkg --configure -a 2>/dev/null || true"
                )

                val uninstallCmd = PackageRepository.getUninstallCommand(pkg)
                android.util.Log.d("LibrariesActivity", "Executing uninstall: $uninstallCmd")
                val result = runtime.runCommand(uninstallCmd) { line ->
                    val update = parser.parseUninstallLine(line)
                    val now = System.currentTimeMillis()
                    if (update.percent != pkg.progressPercent || now - lastUpdateMs > 150) {
                        lastUpdateMs = now
                        lifecycleScope.launch(Dispatchers.Main) {
                            pkg.progressPercent = update.percent
                            pkg.statusText = if (update.percent > 0) {
                                "${update.stage} (${update.percent}%)"
                            } else {
                                update.stage
                            }
                            adapter.updateItem(pkg.id)
                        }
                    }
                }
                android.util.Log.d("LibrariesActivity", "Uninstall result code: ${result.first}")

                // BUG FIX: ALWAYS verify via checkInstalledCommand after uninstall,
                // regardless of exit code. This catches cases where the uninstall command
                // reports success but the binary/module is still present.
                val verifyResult = withContext(Dispatchers.IO) {
                    runtime.runCommand(pkg.checkInstalledCommand)
                }
                val isStillInstalled = verifyResult.first == 0

                withContext(Dispatchers.Main) {
                    pkg.isUninstalling = false
                    if (!isStillInstalled) {
                        pkg.isInstalled = false
                        pkg.isActivated = false
                        pkg.progressPercent = -1
                        pkg.statusText = "Ready to install"

                        // Remove from persistent disk cache
                        val prefs = getSharedPreferences("packages_state_cache", Context.MODE_PRIVATE)
                        val currentSet = getCachedInstalledIds(prefs)
                        currentSet.remove(pkg.id)
                        val editor = prefs.edit().putStringSet("installed_ids", currentSet)
                        if (pkg.id == "miniconda") {
                            editor.putBoolean("conda_active", false)
                        }
                        editor.apply()

                        adapter.updateItem(pkg.id)
                        Toast.makeText(
                            this@LibrariesActivity,
                            "${pkg.name} permanently uninstalled and cleaned.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        pkg.isInstalled = true
                        pkg.progressPercent = -1
                        pkg.statusText = "Uninstall incomplete (still detected)"
                        adapter.updateItem(pkg.id)
                        Toast.makeText(
                            this@LibrariesActivity,
                            "Warning: ${pkg.name} could not be completely uninstalled.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    pkg.isUninstalling = false
                    pkg.progressPercent = -1
                    pkg.statusText = "Uninstall error: ${e.message}"
                    adapter.updateItem(pkg.id)
                    Toast.makeText(
                        this@LibrariesActivity,
                        "Error uninstalling ${pkg.name}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } finally {
                isUninstallRunning = false
            }
        }
    }

    /**
     * Activates Conda base environment, runs conda init and hooks into .bashrc & .condarc
     */
    private fun activateConda(pkg: LinuxPackage) {
        if (pkg.isActivating) return
        if (isQueueProcessing) {
            Toast.makeText(this, "Please wait for current installation to finish before activating Conda.", Toast.LENGTH_SHORT).show()
            return
        }

        pkg.isActivating = true
        pkg.statusText = "Verifying Conda installation..."
        adapter.updateItem(pkg.id)

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Verify real conda binary exists
                val checkConda = runtime.runCommand(
                    "[ -x /home/ubuntu/miniforge3/bin/conda ] || [ -x /root/miniconda3/bin/conda ] || [ -x /opt/conda/bin/conda ]"
                )
                if (checkConda.first != 0) {
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

                // Ensure miniforge3 permissions and binaries are executable
                runtime.runCommand("chmod -R u+rx /home/ubuntu/miniforge3/bin 2>/dev/null || true")

                // Run conda init and configure settings inside container
                val initCmd = listOf(
                    "if [ -x /home/ubuntu/miniforge3/bin/conda ]; then",
                    "    /home/ubuntu/miniforge3/bin/conda init bash",
                    "    /home/ubuntu/miniforge3/bin/conda config --set always_copy true",
                    "    /home/ubuntu/miniforge3/bin/conda config --set auto_activate_base true",
                    "    /usr/local/bin/conda-sync-packages 2>/dev/null || true",
                    "fi"
                ).joinToString("\n")
                runtime.runCommand(initCmd)

                // Inject and verify .bashrc & .condarc & command wrappers
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
