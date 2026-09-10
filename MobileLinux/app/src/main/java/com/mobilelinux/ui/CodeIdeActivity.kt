package com.mobilelinux.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mobilelinux.R
import com.mobilelinux.ide.*
import com.mobilelinux.runtime.UbuntuRuntime
import com.mobilelinux.util.StorageHelper
import org.json.JSONObject
import java.io.File

/**
 * Native Mobile Code IDE Activity.
 * Provides touch-optimized multi-tab coding inspired by Acode, integrated with the
 * full Ubuntu PRoot Linux engine for running scripts and compiling code natively.
 */
class CodeIdeActivity : AppCompatActivity() {

    private val TAG = "CodeIdeActivity"

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var editorWebView: WebView
    private lateinit var tvActiveFilename: TextView
    private lateinit var btnSaveFile: ImageView
    private lateinit var btnRunCode: MaterialButton
    private lateinit var btnUndo: ImageView
    private lateinit var btnRedo: ImageView
    private lateinit var btnIdeOverflow: ImageView
    private lateinit var btnToggleDrawer: ImageView
    private lateinit var layoutEmptyWelcome: View
    private lateinit var rvIdeTabs: RecyclerView
    private lateinit var btnNewScratchTab: ImageView
    private lateinit var layoutAccessoryKeys: LinearLayout

    // Console Panel UI
    private lateinit var layoutExecutionPanel: View
    private lateinit var layoutConsoleBody: View
    private lateinit var tvConsoleStatus: TextView
    private lateinit var tvConsoleOutput: TextView
    private lateinit var scrollConsoleOutput: ScrollView
    private lateinit var btnStopExecution: ImageView
    private lateinit var btnClearConsole: ImageView
    private lateinit var btnCloseConsole: ImageView
    private lateinit var btnToggleConsole: ImageView
    private lateinit var etStdinInput: EditText
    private lateinit var btnSendStdin: MaterialButton

    // Drawer File Explorer UI
    private lateinit var rvFileTree: RecyclerView
    private lateinit var tvCurrentRootLabel: TextView
    private lateinit var btnSwitchRoot: TextView
    private lateinit var btnDrawerNewFile: ImageView
    private lateinit var btnDrawerNewFolder: ImageView
    private lateinit var btnDrawerRefresh: ImageView

    // Data & Adapters
    private val openTabs = mutableListOf<IdeTab>()
    private var activeTabIndex = -1
    private lateinit var tabsAdapter: IdeTabsAdapter
    private lateinit var fileTreeAdapter: FileTreeAdapter
    private lateinit var codeRunner: CodeRunner

    private var currentRootDir: File? = null
    private var isEditorReady = false
    private var pendingFileToOpen: File? = null
    private var currentFontSize = 14
    private var isWordWrap = false

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_code_ide)

        codeRunner = CodeRunner(this)
        initViews()
        setupEdgeToEdgeInsets()
        setupRootDirectory()
        setupTabs()
        setupFileExplorer()
        setupAccessoryKeys()
        setupExecutionConsole()
        setupWebView()

        // Open default home file or create example script if directory is empty
        ensureInitialFile()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        editorWebView = findViewById(R.id.editor_webview)
        tvActiveFilename = findViewById(R.id.tv_active_filename)
        btnSaveFile = findViewById(R.id.btn_save_file)
        btnRunCode = findViewById(R.id.btn_run_code)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        btnIdeOverflow = findViewById(R.id.btn_ide_overflow)
        btnToggleDrawer = findViewById(R.id.btn_toggle_drawer)
        layoutEmptyWelcome = findViewById(R.id.layout_empty_welcome)
        rvIdeTabs = findViewById(R.id.rv_ide_tabs)
        btnNewScratchTab = findViewById(R.id.btn_new_scratch_tab)
        layoutAccessoryKeys = findViewById(R.id.layout_accessory_keys)

        layoutExecutionPanel = findViewById(R.id.layout_execution_panel)
        layoutConsoleBody = findViewById(R.id.layout_console_body)
        tvConsoleStatus = findViewById(R.id.tv_console_status)
        tvConsoleOutput = findViewById(R.id.tv_console_output)
        scrollConsoleOutput = findViewById(R.id.scroll_console_output)
        btnStopExecution = findViewById(R.id.btn_stop_execution)
        btnClearConsole = findViewById(R.id.btn_clear_console)
        btnCloseConsole = findViewById(R.id.btn_close_console)
        btnToggleConsole = findViewById(R.id.btn_toggle_console)
        etStdinInput = findViewById(R.id.et_stdin_input)
        btnSendStdin = findViewById(R.id.btn_send_stdin)

        rvFileTree = findViewById(R.id.rv_file_tree)
        tvCurrentRootLabel = findViewById(R.id.tv_current_root_label)
        btnSwitchRoot = findViewById(R.id.btn_switch_root)
        btnDrawerNewFile = findViewById(R.id.btn_drawer_new_file)
        btnDrawerNewFolder = findViewById(R.id.btn_drawer_new_folder)
        btnDrawerRefresh = findViewById(R.id.btn_drawer_refresh)

        btnToggleDrawer.setOnClickListener {
            if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
                drawerLayout.closeDrawer(GravityCompat.START)
            } else {
                drawerLayout.openDrawer(GravityCompat.START)
            }
        }

        btnSaveFile.setOnClickListener { saveCurrentFile() }
        btnRunCode.setOnClickListener { runActiveScript() }
        btnUndo.setOnClickListener { editorWebView.evaluateJavascript("window.editorUndo();", null) }
        btnRedo.setOnClickListener { editorWebView.evaluateJavascript("window.editorRedo();", null) }
        btnIdeOverflow.setOnClickListener { showOverflowMenu(it) }

        findViewById<View>(R.id.btn_empty_open_files).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        btnNewScratchTab.setOnClickListener {
            showNewFileDialog(currentRootDir ?: getDefaultLinuxHome())
        }
    }

    private fun setupEdgeToEdgeInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_layout)) { _, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())

            findViewById<View>(R.id.ide_toolbar).updatePadding(top = statusBars.top)
            findViewById<View>(R.id.file_explorer_drawer).updatePadding(top = statusBars.top)

            // Lift accessory bar / bottom console above virtual keyboard
            val bottomPadding = if (ime.bottom > 0) ime.bottom else navBars.bottom
            layoutExecutionPanel.updatePadding(bottom = bottomPadding)

            insets
        }
    }

    private fun setupRootDirectory() {
        currentRootDir = getDefaultLinuxHome()
        updateRootLabel()

        btnSwitchRoot.setOnClickListener {
            val popup = PopupMenu(this, btnSwitchRoot)
            popup.menu.add(0, 1, 0, "Linux Home (~/home/ubuntu)")
            popup.menu.add(0, 2, 1, "Shared Storage (/sdcard/MobileLinux)")
            popup.menu.add(0, 3, 2, "SDCard Root (/sdcard)")

            popup.setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    1 -> {
                        currentRootDir = getDefaultLinuxHome()
                        updateRootLabel()
                        fileTreeAdapter.setRootDir(currentRootDir!!)
                    }
                    2 -> {
                        currentRootDir = StorageHelper.getPreferredSharedDir(this)
                        updateRootLabel()
                        fileTreeAdapter.setRootDir(currentRootDir!!)
                    }
                    3 -> {
                        val sd = android.os.Environment.getExternalStorageDirectory()
                        if (sd != null && sd.exists()) {
                            currentRootDir = sd
                            updateRootLabel()
                            fileTreeAdapter.setRootDir(currentRootDir!!)
                        }
                    }
                }
                true
            }
            popup.show()
        }
    }

    private fun getDefaultLinuxHome(): File {
        val rootfs = UbuntuRuntime.getInstance(this).rootfsDir
        val home = File(rootfs, "home/ubuntu")
        if (!home.exists()) home.mkdirs()
        return home
    }

    private fun updateRootLabel() {
        val dir = currentRootDir ?: return
        val rootfs = UbuntuRuntime.getInstance(this).rootfsDir
        if (dir.absolutePath.startsWith(File(rootfs, "home/ubuntu").absolutePath)) {
            tvCurrentRootLabel.text = "Linux Home (~)"
        } else if (dir.absolutePath.contains("MobileLinux")) {
            tvCurrentRootLabel.text = "Shared (/sdcard/MobileLinux)"
        } else {
            tvCurrentRootLabel.text = dir.name
        }
    }

    private fun setupTabs() {
        tabsAdapter = IdeTabsAdapter(
            tabs = openTabs,
            selectedIndex = activeTabIndex,
            onTabClick = { index -> switchTab(index) },
            onTabClose = { index -> closeTab(index) }
        )
        rvIdeTabs.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvIdeTabs.adapter = tabsAdapter
    }

    private fun setupFileExplorer() {
        val root = currentRootDir ?: getDefaultLinuxHome()
        fileTreeAdapter = FileTreeAdapter(
            rootDir = root,
            onFileClick = { file ->
                openFileInEditor(file)
                drawerLayout.closeDrawer(GravityCompat.START)
            },
            onNewFile = { parentDir -> showNewFileDialog(parentDir) },
            onNewFolder = { parentDir -> showNewFolderDialog(parentDir) },
            onRename = { target -> showRenameDialog(target) },
            onDelete = { target -> confirmDelete(target) },
            onCopyPath = { target ->
                val linuxPath = codeRunner.toLinuxPath(target)
                val cb = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cb.setPrimaryClip(ClipData.newPlainText("Path", linuxPath))
                Toast.makeText(this, "Copied: $linuxPath", Toast.LENGTH_SHORT).show()
            }
        )
        rvFileTree.layoutManager = LinearLayoutManager(this)
        rvFileTree.adapter = fileTreeAdapter

        btnDrawerNewFile.setOnClickListener {
            showNewFileDialog(currentRootDir ?: getDefaultLinuxHome())
        }
        btnDrawerNewFolder.setOnClickListener {
            showNewFolderDialog(currentRootDir ?: getDefaultLinuxHome())
        }
        btnDrawerRefresh.setOnClickListener {
            fileTreeAdapter.reload()
            Toast.makeText(this, "Refreshed", Toast.LENGTH_SHORT).show()
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setupWebView() {
        val settings = editorWebView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.cacheMode = WebSettings.LOAD_NO_CACHE

        val bridge = IdeBridge(
            onReady = {
                runOnUiThread {
                    isEditorReady = true
                    pendingFileToOpen?.let { file ->
                        loadFileIntoEditor(file)
                        pendingFileToOpen = null
                    }
                }
            },
            onDirtyChanged = { isDirty ->
                runOnUiThread {
                    if (activeTabIndex in openTabs.indices) {
                        openTabs[activeTabIndex].isDirty = isDirty
                        tabsAdapter.notifyItemChanged(activeTabIndex)
                        btnSaveFile.setColorFilter(
                            getColor(if (isDirty) R.color.accent_blue else R.color.text_secondary)
                        )
                    }
                }
            },
            onCursor = { row, col ->
                runOnUiThread {
                    if (activeTabIndex in openTabs.indices) {
                        openTabs[activeTabIndex].cursorRow = row
                        openTabs[activeTabIndex].cursorCol = col
                    }
                }
            },
            onSave = {
                runOnUiThread { saveCurrentFile() }
            }
        )

        editorWebView.addJavascriptInterface(bridge, "IdeBridge")
        editorWebView.loadUrl("file:///android_asset/editor/index.html")
    }

    private fun setupAccessoryKeys() {
        val keys = listOf(
            "TAB", "{", "}", "(", ")", "[", "]", "\"", "'", ":", ";",
            "=", "<", ">", "+", "-", "*", "/", "\\", "_", "$", "&", "|", "!", "?", "#"
        )

        for (key in keys) {
            val btn = Button(this).apply {
                text = key
                textSize = 12f
                setTextColor(getColor(R.color.text_primary))
                setBackgroundResource(R.drawable.bg_accessory_key)
                minWidth = 0
                minimumWidth = 0
                setPadding(
                    (12 * resources.displayMetrics.density).toInt(),
                    0,
                    (12 * resources.displayMetrics.density).toInt(),
                    0
                )
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    (32 * resources.displayMetrics.density).toInt()
                ).apply {
                    setMargins((3 * resources.displayMetrics.density).toInt(), 0, (3 * resources.displayMetrics.density).toInt(), 0)
                }
                layoutParams = params

                setOnClickListener {
                    if (key == "TAB") {
                        editorWebView.evaluateJavascript("window.editorInsert('    ');", null)
                    } else {
                        val escaped = JSONObject.quote(key)
                        editorWebView.evaluateJavascript("window.editorInsert($escaped);", null)
                    }
                }
            }
            layoutAccessoryKeys.addView(btn)
        }
    }

    private fun setupExecutionConsole() {
        btnToggleConsole.setOnClickListener {
            val isVisible = layoutConsoleBody.visibility == View.VISIBLE
            layoutConsoleBody.visibility = if (isVisible) View.GONE else View.VISIBLE
        }

        btnCloseConsole.setOnClickListener {
            layoutConsoleBody.visibility = View.GONE
        }

        btnClearConsole.setOnClickListener {
            tvConsoleOutput.text = ""
            tvConsoleStatus.text = "Console Cleared"
        }

        btnStopExecution.setOnClickListener {
            codeRunner.stop()
            tvConsoleStatus.text = "Stopped by user"
            btnStopExecution.visibility = View.GONE
        }

        btnSendStdin.setOnClickListener { sendStdinInput() }
        etStdinInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                sendStdinInput()
                true
            } else false
        }
    }

    private fun sendStdinInput() {
        val input = etStdinInput.text.toString()
        if (input.isNotEmpty()) {
            codeRunner.sendInput(input)
            tvConsoleOutput.append("> $input\n")
            scrollConsoleOutput.post { scrollConsoleOutput.fullScroll(View.FOCUS_DOWN) }
            etStdinInput.setText("")
        }
    }

    // =========================================================================
    // File & Tab Operations
    // =========================================================================

    fun openFileInEditor(file: File) {
        // Check if already open
        val existingIndex = openTabs.indexOfFirst { it.file.absolutePath == file.absolutePath }
        if (existingIndex != -1) {
            switchTab(existingIndex)
            return
        }

        // Add new tab
        val tab = IdeTab(file = file)
        openTabs.add(tab)
        val newIndex = openTabs.size - 1
        tabsAdapter.notifyItemInserted(newIndex)
        switchTab(newIndex)
    }

    private fun switchTab(index: Int) {
        if (index !in openTabs.indices) return

        // If switching from another tab, save its state
        if (activeTabIndex != index && activeTabIndex in openTabs.indices) {
            // Can store scroll/cursor state if needed
        }

        activeTabIndex = index
        tabsAdapter.setSelectedIndex(index)
        rvIdeTabs.smoothScrollToPosition(index)

        val activeTab = openTabs[index]
        tvActiveFilename.text = activeTab.title
        layoutEmptyWelcome.visibility = View.GONE
        editorWebView.visibility = View.VISIBLE

        if (isEditorReady) {
            loadFileIntoEditor(activeTab.file)
        } else {
            pendingFileToOpen = activeTab.file
        }
    }

    private fun loadFileIntoEditor(file: File) {
        try {
            val content = if (file.exists()) file.readText() else ""
            val mode = IdeTab.detectMode(file.name)
            val jsonPath = JSONObject.quote(file.absolutePath)
            val jsonContent = JSONObject.quote(content)
            val jsonMode = JSONObject.quote(mode)

            editorWebView.evaluateJavascript("window.editorSetFile($jsonPath, $jsonContent, $jsonMode);", null)
            btnSaveFile.setColorFilter(getColor(R.color.text_secondary))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load file: ${e.message}", e)
            Toast.makeText(this, "Error reading file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCurrentFile() {
        if (activeTabIndex !in openTabs.indices) return
        val activeTab = openTabs[activeTabIndex]

        editorWebView.evaluateJavascript("window.editorGetContent();") { result ->
            try {
                // Result from evaluateJavascript is JSON encoded string
                val content = if (result.startsWith("\"") && result.endsWith("\"")) {
                    org.json.JSONTokener(result).nextValue().toString()
                } else {
                    result
                }

                activeTab.file.parentFile?.mkdirs()
                activeTab.file.writeText(content)

                editorWebView.evaluateJavascript("window.editorMarkClean();", null)
                activeTab.isDirty = false
                tabsAdapter.notifyItemChanged(activeTabIndex)
                btnSaveFile.setColorFilter(getColor(R.color.text_secondary))

                Toast.makeText(this, "Saved ${activeTab.title}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Log.e(TAG, "Save error: ${e.message}", e)
                Toast.makeText(this, "Failed to save: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun closeTab(index: Int) {
        if (index !in openTabs.indices) return
        val tab = openTabs[index]

        if (tab.isDirty) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Unsaved Changes")
                .setMessage("Save changes to \"${tab.title}\" before closing?")
                .setPositiveButton("Save & Close") { _, _ ->
                    saveCurrentFile()
                    performCloseTab(index)
                }
                .setNegativeButton("Discard") { _, _ ->
                    performCloseTab(index)
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            performCloseTab(index)
        }
    }

    private fun performCloseTab(index: Int) {
        openTabs.removeAt(index)
        tabsAdapter.notifyItemRemoved(index)

        if (openTabs.isEmpty()) {
            activeTabIndex = -1
            tvActiveFilename.text = "MobileLinux IDE"
            editorWebView.visibility = View.INVISIBLE
            layoutEmptyWelcome.visibility = View.VISIBLE
        } else {
            val nextIndex = if (index >= openTabs.size) openTabs.size - 1 else index
            switchTab(nextIndex)
        }
    }

    // =========================================================================
    // Execution Runner
    // =========================================================================

    private fun runActiveScript() {
        if (activeTabIndex !in openTabs.indices) {
            Toast.makeText(this, "No file open to run", Toast.LENGTH_SHORT).show()
            return
        }

        val activeTab = openTabs[activeTabIndex]
        saveCurrentFile()

        val file = activeTab.file
        val ext = file.extension.lowercase()

        // HTML files open directly in Dev Browser
        if (ext == "html" || ext == "htm") {
            DevBrowserActivity.openUrl(this, "file://${file.absolutePath}")
            return
        }

        // Expand console panel
        layoutConsoleBody.visibility = View.VISIBLE
        btnStopExecution.visibility = View.VISIBLE
        tvConsoleOutput.text = ""

        codeRunner.runFile(
            file = file,
            onStart = { cmd ->
                tvConsoleStatus.text = "Running: $cmd"
                tvConsoleOutput.append("[Running: $cmd]\n----------------------------------------\n")
            },
            onOutput = { text ->
                tvConsoleOutput.append(text)
                scrollConsoleOutput.post { scrollConsoleOutput.fullScroll(View.FOCUS_DOWN) }
            },
            onFinished = { exitCode, durationMs ->
                btnStopExecution.visibility = View.GONE
                val seconds = durationMs / 1000.0
                val statusMsg = if (exitCode == 0) {
                    "✓ Exited (code 0) [${String.format("%.2f", seconds)}s]"
                } else {
                    "✗ Exited with code $exitCode [${String.format("%.2f", seconds)}s]"
                }
                tvConsoleStatus.text = statusMsg
                tvConsoleOutput.append("\n----------------------------------------\n[$statusMsg]\n")
                scrollConsoleOutput.post { scrollConsoleOutput.fullScroll(View.FOCUS_DOWN) }
            }
        )
    }

    // =========================================================================
    // Dialogs & Actions
    // =========================================================================

    private fun showNewFileDialog(parentDir: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_file_action, null)
        val promptText = dialogView.findViewById<TextView>(R.id.tv_dialog_prompt)
        val etName = dialogView.findViewById<EditText>(R.id.et_file_name)

        promptText.text = "New file in ${parentDir.name}:"
        etName.hint = "e.g. main.py, test.c, app.js"

        MaterialAlertDialogBuilder(this)
            .setTitle("New File")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newFile = File(parentDir, name)
                    if (!newFile.exists()) {
                        newFile.createNewFile()
                        fileTreeAdapter.reload()
                        openFileInEditor(newFile)
                        Toast.makeText(this, "Created $name", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "File already exists", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewFolderDialog(parentDir: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_file_action, null)
        val promptText = dialogView.findViewById<TextView>(R.id.tv_dialog_prompt)
        val etName = dialogView.findViewById<EditText>(R.id.et_file_name)

        promptText.text = "New folder in ${parentDir.name}:"
        etName.hint = "Folder name"

        MaterialAlertDialogBuilder(this)
            .setTitle("New Folder")
            .setView(dialogView)
            .setPositiveButton("Create") { _, _ ->
                val name = etName.text.toString().trim()
                if (name.isNotEmpty()) {
                    val newFolder = File(parentDir, name)
                    if (!newFolder.exists() && newFolder.mkdirs()) {
                        fileTreeAdapter.reload()
                        Toast.makeText(this, "Created folder $name", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showRenameDialog(target: File) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_file_action, null)
        val promptText = dialogView.findViewById<TextView>(R.id.tv_dialog_prompt)
        val etName = dialogView.findViewById<EditText>(R.id.et_file_name)

        promptText.text = "Rename \"${target.name}\" to:"
        etName.setText(target.name)

        MaterialAlertDialogBuilder(this)
            .setTitle("Rename")
            .setView(dialogView)
            .setPositiveButton("Rename") { _, _ ->
                val newName = etName.text.toString().trim()
                if (newName.isNotEmpty() && newName != target.name) {
                    val dest = File(target.parentFile, newName)
                    if (target.renameTo(dest)) {
                        // Update any open tab
                        openTabs.find { it.file.absolutePath == target.absolutePath }?.let { tab ->
                            val idx = openTabs.indexOf(tab)
                            openTabs[idx] = tab.copy(file = dest, title = dest.name)
                            tabsAdapter.notifyItemChanged(idx)
                            if (activeTabIndex == idx) tvActiveFilename.text = dest.name
                        }
                        fileTreeAdapter.reload()
                        Toast.makeText(this, "Renamed to $newName", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(target: File) {
        val type = if (target.isDirectory) "folder" else "file"
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete $type?")
            .setMessage("Are you sure you want to permanently delete \"${target.name}\"?")
            .setPositiveButton("Delete") { _, _ ->
                val openIdx = openTabs.indexOfFirst { it.file.absolutePath == target.absolutePath }
                if (openIdx != -1) {
                    performCloseTab(openIdx)
                }
                if (target.isDirectory) {
                    target.deleteRecursively()
                } else {
                    target.delete()
                }
                fileTreeAdapter.reload()
                Toast.makeText(this, "Deleted ${target.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showOverflowMenu(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 1, 0, "Find / Replace")
        popup.menu.add(0, 2, 1, if (isWordWrap) "Disable Word Wrap" else "Enable Word Wrap")
        popup.menu.add(0, 3, 2, "Increase Font (${currentFontSize + 2}px)")
        popup.menu.add(0, 4, 3, "Decrease Font (${currentFontSize - 2}px)")
        popup.menu.add(0, 5, 4, "Theme: One Dark")
        popup.menu.add(0, 6, 5, "Theme: Monokai")
        popup.menu.add(0, 7, 6, "Theme: Dracula")
        popup.menu.add(0, 8, 7, "Open Terminal Session")
        popup.menu.add(0, 9, 8, "Close All Tabs")

        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> editorWebView.evaluateJavascript("window.editorFind();", null)
                2 -> {
                    isWordWrap = !isWordWrap
                    editorWebView.evaluateJavascript("window.editorSetWordWrap($isWordWrap);", null)
                }
                3 -> {
                    currentFontSize += 2
                    editorWebView.evaluateJavascript("window.editorSetFontSize($currentFontSize);", null)
                }
                4 -> {
                    if (currentFontSize > 10) currentFontSize -= 2
                    editorWebView.evaluateJavascript("window.editorSetFontSize($currentFontSize);", null)
                }
                5 -> editorWebView.evaluateJavascript("window.editorSetTheme('one_dark');", null)
                6 -> editorWebView.evaluateJavascript("window.editorSetTheme('monokai');", null)
                7 -> editorWebView.evaluateJavascript("window.editorSetTheme('dracula');", null)
                8 -> {
                    startActivity(Intent(this, MainActivity::class.java))
                }
                9 -> {
                    while (openTabs.isNotEmpty()) {
                        performCloseTab(0)
                    }
                }
            }
            true
        }
        popup.show()
    }

    private fun ensureInitialFile() {
        val home = getDefaultLinuxHome()
        val exampleFile = File(home, "hello.py")
        if (!exampleFile.exists()) {
            try {
                exampleFile.writeText(
                    "# Welcome to MobileLinux Code IDE!\n" +
                    "# Write, edit and run Python, C, Node.js and Bash scripts natively.\n\n" +
                    "def main():\n" +
                    "    print(\"Hello from MobileLinux Code IDE!\")\n" +
                    "    print(\"Powered by Ubuntu 24.04 PRoot Engine.\")\n\n" +
                    "if __name__ == '__main__':\n" +
                    "    main()\n"
                )
            } catch (ignored: Exception) {}
        }
        openFileInEditor(exampleFile)
    }

    override fun onDestroy() {
        codeRunner.stop()
        super.onDestroy()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (layoutConsoleBody.visibility == View.VISIBLE) {
            layoutConsoleBody.visibility = View.GONE
        } else {
            super.onBackPressed()
        }
    }
}
