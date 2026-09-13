package com.mobilelinux.ui

import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.mobilelinux.R
import com.mobilelinux.ide.extension.ExtensionManager
import com.mobilelinux.ide.extension.ExtensionsAdapter
import com.mobilelinux.ide.extension.InstalledExtension
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Dedicated full-screen Activity for managing Code IDE extensions.
 * Allows importing, filtering, enabling/disabling, and uninstalling custom tools and extensions.
 */
class ExtensionManagerActivity : AppCompatActivity() {

    private val extensionManager by lazy { ExtensionManager.getInstance(this) }

    private lateinit var rvExtensions: RecyclerView
    private lateinit var layoutEmptyState: View
    private lateinit var etSearch: EditText
    private lateinit var btnClearSearch: ImageView
    private lateinit var tvHeaderCount: TextView

    private lateinit var chipAll: MaterialButton
    private lateinit var chipEnabled: MaterialButton
    private lateinit var chipDisabled: MaterialButton

    private lateinit var adapter: ExtensionsAdapter

    private enum class FilterType {
        ALL, ENABLED, DISABLED
    }

    private var currentFilter = FilterType.ALL

    private val importExtensionLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            handleImportExtension(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_extension_manager)

        setupEdgeToEdgeInsets()
        initViews()
        setupListeners()
        loadExtensions()
    }

    private fun setupEdgeToEdgeInsets() {
        val initialPaddingStart = (8 * resources.displayMetrics.density).toInt()
        val initialPaddingEnd = (16 * resources.displayMetrics.density).toInt()

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.toolbar_extension_manager)) { v, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = statusBars.top,
                left = statusBars.left + initialPaddingStart,
                right = statusBars.right + initialPaddingEnd
            )
            insets
        }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.rv_extensions)) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            v.updatePadding(bottom = (24 * resources.displayMetrics.density).toInt() + navBars.bottom)
            insets
        }
    }

    private fun initViews() {
        rvExtensions = findViewById(R.id.rv_extensions)
        layoutEmptyState = findViewById(R.id.layout_empty_state)
        etSearch = findViewById(R.id.et_search_extensions)
        btnClearSearch = findViewById(R.id.btn_clear_search)
        tvHeaderCount = findViewById(R.id.tv_header_count)

        chipAll = findViewById(R.id.chip_filter_all)
        chipEnabled = findViewById(R.id.chip_filter_enabled)
        chipDisabled = findViewById(R.id.chip_filter_disabled)

        rvExtensions.layoutManager = LinearLayoutManager(this)

        adapter = ExtensionsAdapter(
            allExtensions = emptyList(),
            onToggle = { ext, isEnabled ->
                extensionManager.setExtensionEnabled(ext.id, isEnabled)
                updateCountsAndFilter()
            },
            onDelete = { ext ->
                showDeleteConfirmation(ext)
            }
        )
        rvExtensions.adapter = adapter
    }

    private fun setupListeners() {
        findViewById<View>(R.id.btn_back).setOnClickListener {
            finish()
        }

        val importAction = {
            try {
                importExtensionLauncher.launch(arrayOf("*/*", "application/zip", "application/octet-stream"))
            } catch (e: Exception) {
                Toast.makeText(this, "File picker error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.btn_toolbar_import).setOnClickListener { importAction() }
        findViewById<View>(R.id.btn_empty_import).setOnClickListener { importAction() }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearSearch.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
                applyCurrentFilter()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearSearch.setOnClickListener {
            etSearch.text.clear()
        }

        chipAll.setOnClickListener {
            currentFilter = FilterType.ALL
            updateFilterChipStyles()
            applyCurrentFilter()
        }

        chipEnabled.setOnClickListener {
            currentFilter = FilterType.ENABLED
            updateFilterChipStyles()
            applyCurrentFilter()
        }

        chipDisabled.setOnClickListener {
            currentFilter = FilterType.DISABLED
            updateFilterChipStyles()
            applyCurrentFilter()
        }
    }

    private fun loadExtensions() {
        updateCountsAndFilter()
    }

    private fun updateCountsAndFilter() {
        val all = extensionManager.getInstalledExtensions()
        val enabledCount = all.count { it.isEnabled }
        val disabledCount = all.size - enabledCount

        tvHeaderCount.text = "${all.size} installed, $enabledCount active"

        chipAll.text = "All (${all.size})"
        chipEnabled.text = "Enabled ($enabledCount)"
        chipDisabled.text = "Disabled ($disabledCount)"

        applyCurrentFilter()
    }

    private fun applyCurrentFilter() {
        val all = extensionManager.getInstalledExtensions()
        val filteredByTab = when (currentFilter) {
            FilterType.ALL -> all
            FilterType.ENABLED -> all.filter { it.isEnabled }
            FilterType.DISABLED -> all.filter { !it.isEnabled }
        }

        val searchQuery = etSearch.text?.toString()?.trim()
        val finalList = if (searchQuery.isNullOrBlank()) {
            filteredByTab
        } else {
            val q = searchQuery.lowercase()
            filteredByTab.filter {
                it.name.lowercase().contains(q) ||
                it.description.lowercase().contains(q) ||
                it.author.lowercase().contains(q) ||
                it.id.lowercase().contains(q)
            }
        }

        adapter.updateList(finalList)
        layoutEmptyState.visibility = if (all.isEmpty()) View.VISIBLE else View.GONE
        rvExtensions.visibility = if (all.isEmpty()) View.GONE else View.VISIBLE
    }

    private fun updateFilterChipStyles() {
        val activeTextColor = ContextCompat.getColor(this, R.color.accent_blue)
        val defaultTextColor = ContextCompat.getColor(this, R.color.text_secondary)

        fun styleChip(chip: MaterialButton, isActive: Boolean) {
            chip.strokeColor = ContextCompat.getColorStateList(this, if (isActive) R.color.accent_blue else R.color.border_color)
            chip.setTextColor(if (isActive) activeTextColor else defaultTextColor)
        }

        styleChip(chipAll, currentFilter == FilterType.ALL)
        styleChip(chipEnabled, currentFilter == FilterType.ENABLED)
        styleChip(chipDisabled, currentFilter == FilterType.DISABLED)
    }

    private fun handleImportExtension(uri: Uri) {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val installed = extensionManager.importExtensionFromUri(uri)
                withContext(Dispatchers.Main) {
                    updateCountsAndFilter()
                    Toast.makeText(this@ExtensionManagerActivity, "Extension '${installed.name}' installed successfully!", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    CustomDialog.Builder(this@ExtensionManagerActivity)
                        .setIcon(R.drawable.ic_extension, ContextCompat.getColor(this@ExtensionManagerActivity, R.color.accent_red))
                        .setTitle("Import Failed")
                        .setMessage(e.message ?: "Invalid extension package archive.")
                        .setPositiveButton("Dismiss")
                        .show()
                }
            }
        }
    }



    private fun showDeleteConfirmation(ext: InstalledExtension) {
        CustomDialog.Builder(this)
            .setIcon(R.drawable.ic_trash, ContextCompat.getColor(this, R.color.accent_red))
            .setTitle("Uninstall Extension?")
            .setMessage("Are you sure you want to uninstall '${ext.name}' (v${ext.version})? All associated files and settings will be permanently deleted.")
            .setPositiveButton("Uninstall", destructive = true) {
                extensionManager.deleteExtension(ext.id)
                updateCountsAndFilter()
                Toast.makeText(this, "Extension '${ext.name}' uninstalled", Toast.LENGTH_SHORT).show()
            }
            .setNeutralButton("Cancel")
            .show()
    }
}
