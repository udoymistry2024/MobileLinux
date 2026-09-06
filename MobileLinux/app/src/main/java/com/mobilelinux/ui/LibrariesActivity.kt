package com.mobilelinux.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_libraries)

        runtime = UbuntuRuntime.getInstance(this)

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
            onCopyClick = { pkg -> copyPackageCommand(pkg) }
        )
        rvPackages.layoutManager = LinearLayoutManager(this)
        rvPackages.adapter = adapter
    }

    private fun loadPackages() {
        allPackages.clear()
        allPackages.addAll(PackageRepository.getCuratedPackages())
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
                // Ensure pip.conf disables PEP 668 externally-managed errors globally
                runtime.runCommand("mkdir -p /etc && printf '[global]\\nbreak-system-packages = true\\n' > /etc/pip.conf 2>/dev/null || true")

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
                val isCondaActivated = realCondaInstalled && runtime.runCommand(
                    "grep -q 'conda initialize' /home/ubuntu/.bashrc 2>/dev/null && [ -f /home/ubuntu/.condarc ]"
                ).first == 0

                allPackages.forEach { pkg ->
                    // Preserve status for packages currently installing or in queue
                    if (pkg.isInstalling) return@forEach

                    if (pkg.id == "miniconda") {
                        pkg.isInstalled = realCondaInstalled
                        pkg.isActivated = isCondaActivated
                        pkg.statusText = when {
                            !realCondaInstalled -> "Ready to install"
                            isCondaActivated -> "Active & Ready (base)"
                            else -> "Installed. Click Activate to enable."
                        }
                    } else if (installedIds.contains(pkg.id)) {
                        pkg.isInstalled = true
                        pkg.statusText = "Installed and ready"
                    }
                }
            } catch (ignored: Exception) {
            } finally {
                withContext(Dispatchers.Main) {
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

            try {
                // Safety 1: Clean any broken dpkg state or leftover locks before starting
                runtime.runCommand("sudo dpkg --configure -a 2>/dev/null || true")

                // Safety 2: Ensure pip.conf is present before running install
                runtime.runCommand("mkdir -p /etc && printf '[global]\\nbreak-system-packages = true\\n' > /etc/pip.conf 2>/dev/null || true")

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
                                adapter.updateItem(pkg.id)
                                Toast.makeText(
                                    this@LibrariesActivity,
                                    "Conda installation finished, but binary not found.",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        } else {
                            pkg.isInstalled = true
                            pkg.progressPercent = 100
                            pkg.statusText = "Installed and ready"
                            adapter.updateItem(pkg.id)
                            Toast.makeText(
                                this@LibrariesActivity,
                                "${pkg.name} installed successfully.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    } else {
                        android.util.Log.e("LibrariesActivity", "Install error for ${pkg.id} (code ${result.first}): ${result.second}")
                        pkg.isInstalled = false
                        pkg.progressPercent = -1
                        pkg.statusText = "Install failed (Exit code: ${result.first})"
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
