package com.mobilelinux.ui

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
            onInstallClick = { pkg -> installPackage(pkg) },
            onLaunchClick = { pkg -> launchPackage(pkg) }
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
                val batchScript = PackageRepository.getFastBatchCheckScript()
                val result = runtime.runCommand(batchScript)
                val installedIds = result.second
                    .lines()
                    .filter { it.startsWith("INSTALLED:") }
                    .map { it.substringAfter("INSTALLED:").trim() }
                    .toSet()

                allPackages.forEach { pkg ->
                    if (installedIds.contains(pkg.id)) {
                        pkg.isInstalled = true
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
     * Performs background installation of the selected package
     */
    private fun installPackage(pkg: LinuxPackage) {
        if (pkg.isInstalling) return

        pkg.isInstalling = true
        pkg.statusText = "Installing package in background..."
        adapter.updateItem(pkg.id)

        Toast.makeText(this, "Starting installation of ${pkg.name}...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val result = runtime.runCommand(pkg.installCommand)
                withContext(Dispatchers.Main) {
                    pkg.isInstalling = false
                    if (result.first == 0) {
                        pkg.isInstalled = true
                        pkg.statusText = "Installation complete"
                        adapter.updateItem(pkg.id)
                        Toast.makeText(
                            this@LibrariesActivity,
                            "${pkg.name} installed successfully.",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        pkg.isInstalled = false
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
                    pkg.statusText = "Error: ${e.message}"
                    adapter.updateItem(pkg.id)
                    Toast.makeText(
                        this@LibrariesActivity,
                        "Error installing ${pkg.name}: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
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

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }
}
