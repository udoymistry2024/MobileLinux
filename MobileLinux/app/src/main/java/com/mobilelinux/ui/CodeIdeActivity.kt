package com.mobilelinux.ui

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
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
import com.mobilelinux.service.LinuxService
import com.mobilelinux.terminal.SpecialKey
import com.mobilelinux.terminal.TerminalBuffer
import com.mobilelinux.terminal.TerminalManager
import com.mobilelinux.terminal.TerminalView
import com.mobilelinux.util.StorageHelper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Native Mobile Code IDE Activity.
 * Features an Acode-inspired touch UI, interactive multi-level directory picker,
 * and a genuine embedded Linux Terminal (bash in PRoot) like VS Code.
 */
class CodeIdeActivity : AppCompatActivity() {

    private val TAG = "CodeIdeActivity"

    companion object {
        private const val KEY_IDE_WELCOME_SHOWN = "initial_welcome_shown"
        private const val KEY_SAVED_OPEN_TABS = "saved_open_tabs"
        private const val KEY_ACTIVE_TAB_PATH = "saved_active_tab_path"
    }

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var editorWebView: WebView
    private lateinit var tvActiveFilename: TextView
    private lateinit var btnToggleTerminal: ImageView
    private lateinit var btnSaveFile: ImageView
    private lateinit var btnRunCode: MaterialButton
    private lateinit var btnUndo: ImageView
    private lateinit var btnRedo: ImageView
    private lateinit var btnIdeOverflow: ImageView
    private lateinit var btnToggleDrawer: ImageView
    private lateinit var layoutEmptyWelcome: View
    private lateinit var rvIdeTabs: RecyclerView
    private lateinit var btnNewScratchTab: ImageView
    private lateinit var extraKeysContainer: View
    private lateinit var extraKeysView: ExtraKeysView

    private var isEditorCtrlActive = false
    private var isEditorAltActive = false
    private var isEditorShiftActive = false
    private var activeFocusTarget: FocusTarget = FocusTarget.EDITOR

    private enum class FocusTarget {
        EDITOR,
        TERMINAL
    }

    private lateinit var layoutTabsBar: View
    private lateinit var layoutExecutionPanel: View
    private lateinit var layoutConsoleHeader: View
    private lateinit var layoutConsoleBody: View
    private lateinit var tvConsoleStatus: TextView
    private lateinit var ideTerminalView: TerminalView
    private lateinit var btnStopExecution: ImageView
    private lateinit var btnClearConsole: ImageView
    private lateinit var btnCloseConsole: ImageView
    private lateinit var btnToggleConsole: ImageView

    // Dynamic sizing & keyboard state tracking
    private var userConsoleHeight: Int = -1
    private var lastKeyboardVisible: Boolean = false
    private var lastImeBottom: Int = 0
    private var lastStatusBarTop: Int = 0

    // Drawer Project & File Explorer UI
    private lateinit var rvFileTree: RecyclerView
    private lateinit var tvProjectFolderName: TextView
    private lateinit var tvProjectFolderPath: TextView
    private lateinit var btnDrawerOpenFolder: ImageView
    private lateinit var btnDrawerNavigateUp: ImageView
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

    // Real Linux Terminal Service & Multi-Terminal Sessions
    private val terminalManager: TerminalManager by lazy { TerminalManager.getInstance(applicationContext) }
    private var linuxService: LinuxService? = null
    private var isServiceBound = false
    private val ideTabs = mutableListOf<IdeTerminalTabItem>()
    private var activeIdeSessionId: String? = null
    private lateinit var rvTerminalTabs: RecyclerView
    private lateinit var btnNewTerminalTab: ImageView
    private lateinit var terminalTabAdapter: IdeTerminalTabAdapter
    private var isTerminalAttached = false
    private var isDraggingConsole = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            linuxService = (binder as? LinuxService.LinuxBinder)?.getService()
            isServiceBound = true
            attachOrCreateIdeTerminalSession()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            linuxService = null
            isServiceBound = false
            isTerminalAttached = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        setContentView(R.layout.activity_code_ide)

        codeRunner = CodeRunner(this)
        initViews()
        setupEdgeToEdgeInsets()
        setupProjectDirectory()
        setupTabs()
        setupFileExplorer()
        setupExtraKeys()
        setupTerminalPanel()
        setupWebView()

        // Intercept system back button / gestures - only allow exiting via 3-dot menu -> Exit IDE
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                handleBackPressed()
            }
        })

        // Bind LinuxService to power the embedded TerminalView
        bindLinuxService()

        // Restore last session tabs or show welcome/clean workspace
        restoreOrInitIdeSession()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawer_layout)
        editorWebView = findViewById(R.id.editor_webview)
        tvActiveFilename = findViewById(R.id.tv_active_filename)
        btnToggleTerminal = findViewById(R.id.btn_toggle_terminal)
        btnSaveFile = findViewById(R.id.btn_save_file)
        btnRunCode = findViewById(R.id.btn_run_code)
        btnUndo = findViewById(R.id.btn_undo)
        btnRedo = findViewById(R.id.btn_redo)
        btnIdeOverflow = findViewById(R.id.btn_ide_overflow)
        btnToggleDrawer = findViewById(R.id.btn_toggle_drawer)
        layoutEmptyWelcome = findViewById(R.id.layout_empty_welcome)
        layoutTabsBar = findViewById(R.id.layout_tabs_bar)
        rvIdeTabs = findViewById(R.id.rv_ide_tabs)
        btnNewScratchTab = findViewById(R.id.btn_new_scratch_tab)
        extraKeysContainer = findViewById(R.id.extra_keys_container)
        extraKeysView = findViewById(R.id.extra_keys_view)

        layoutExecutionPanel = findViewById(R.id.layout_execution_panel)
        layoutConsoleHeader = findViewById(R.id.layout_console_header)
        layoutConsoleBody = findViewById(R.id.layout_console_body)
        tvConsoleStatus = findViewById(R.id.tv_console_status)
        ideTerminalView = findViewById(R.id.ide_terminal_view)
        btnStopExecution = findViewById(R.id.btn_stop_execution)
        btnClearConsole = findViewById(R.id.btn_clear_console)
        btnCloseConsole = findViewById(R.id.btn_close_console)
        btnToggleConsole = findViewById(R.id.btn_toggle_console)
        rvTerminalTabs = findViewById(R.id.rv_terminal_tabs)
        btnNewTerminalTab = findViewById(R.id.btn_new_terminal_tab)

        terminalTabAdapter = IdeTerminalTabAdapter(
            onTabClick = { item ->
                switchToIdeSession(item.sessionId)
            },
            onTabClose = { item ->
                closeIdeSession(item)
            },
            onTabLongClick = { item ->
                showRenameTerminalDialog(item)
            }
        )
        rvTerminalTabs.adapter = terminalTabAdapter

        btnNewTerminalTab.setOnClickListener {
            createNewIdeTerminalSession()
        }

        rvFileTree = findViewById(R.id.rv_file_tree)
        tvProjectFolderName = findViewById(R.id.tv_project_folder_name)
        tvProjectFolderPath = findViewById(R.id.tv_project_folder_path)
        btnDrawerOpenFolder = findViewById(R.id.btn_drawer_open_folder)
        btnDrawerNavigateUp = findViewById(R.id.btn_drawer_navigate_up)
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

        // Quick Terminal Toggle in Toolbar
        btnToggleTerminal.setOnClickListener {
            toggleConsole()
        }

        btnSaveFile.setOnClickListener { saveCurrentFile() }
        btnRunCode.setOnClickListener { runActiveScript() }
        btnRunCode.setOnLongClickListener {
            showCustomRunDialog()
            true
        }
        btnRunCode.tooltipText = "Run Code (Long-press to customize)"
        btnUndo.setOnClickListener { editorWebView.evaluateJavascript("window.editorUndo();", null) }
        btnRedo.setOnClickListener { editorWebView.evaluateJavascript("window.editorRedo();", null) }
        btnIdeOverflow.setOnClickListener { showOverflowMenu(it) }

        findViewById<View>(R.id.btn_empty_open_files).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        btnNewScratchTab.setOnClickListener {
            showNewFileDialog(currentRootDir ?: getDefaultLinuxHome())
        }

        // Open Folder Action -> Launches Interactive Directory Browser
        btnDrawerOpenFolder.setOnClickListener {
            showFolderPickerDialog(currentRootDir)
        }

        btnSwitchRoot.setOnClickListener {
            showFolderPickerDialog(currentRootDir)
        }

        btnDrawerNavigateUp.setOnClickListener {
            navigateUpToParentDirectory()
        }
    }

    private fun setupEdgeToEdgeInsets() {
        // Toolbar padding to prevent status bar / camera notch overlap
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.ide_toolbar)) { v, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(
                top = statusBars.top,
                left = statusBars.left,
                right = statusBars.right
            )
            insets
        }

        // Drawer header padding
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.drawer_header_container)) { v, insets ->
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            v.updatePadding(top = statusBars.top)
            insets
        }

        // Lift extra keys bar & terminal above virtual keyboard and gesture nav
        ViewCompat.setOnApplyWindowInsetsListener(extraKeysContainer) { v, insets ->
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val statusBars = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            val isKeyboardVisible = ime.bottom > 0
            val bottomPadding = if (isKeyboardVisible) ime.bottom else navBars.bottom
            v.updatePadding(bottom = bottomPadding)

            adjustConsoleHeightForKeyboard(isKeyboardVisible, ime.bottom, statusBars.top)

            insets
        }
    }

    private fun calculateMaxAvailableConsoleHeight(imeBottom: Int, statusBarTop: Int): Int {
        val density = resources.displayMetrics.density
        val minTerminalHeight = (80 * density).toInt()
        val rootHeight = drawerLayout.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        val toolbarHeight = findViewById<View>(R.id.ide_toolbar).height.takeIf { it > 0 } ?: (52 * density).toInt()
        val tabsHeight = if (layoutTabsBar.visibility == View.VISIBLE) {
            layoutTabsBar.height.takeIf { it > 0 } ?: (38 * density).toInt()
        } else {
            0
        }
        val consoleHeaderHeight = layoutConsoleHeader.height.takeIf { it > 0 } ?: (42 * density).toInt()
        val extraKeysHeight = extraKeysView.height.takeIf { it > 0 } ?: (78 * density).toInt()
        val dividerHeight = (4 * density).toInt()

        val fixedOverhead = statusBarTop + toolbarHeight + tabsHeight + consoleHeaderHeight + extraKeysHeight + dividerHeight + imeBottom
        // Leave at least 30dp for code editor visibility if possible
        val maxAllowed = rootHeight - fixedOverhead - (30 * density).toInt()
        return maxAllowed.coerceAtLeast(minTerminalHeight)
    }

    private fun adjustConsoleHeightForKeyboard(isKeyboardVisible: Boolean, imeBottom: Int, statusBarTop: Int) {
        lastKeyboardVisible = isKeyboardVisible
        lastImeBottom = imeBottom
        lastStatusBarTop = statusBarTop

        if (layoutConsoleBody.visibility != View.VISIBLE) return
        if (isDraggingConsole) return

        val density = resources.displayMetrics.density
        val minTerminalHeight = (80 * density).toInt()

        if (isKeyboardVisible) {
            val maxAvailableConsoleHeight = calculateMaxAvailableConsoleHeight(imeBottom, statusBarTop)
            val preferredHeight = if (userConsoleHeight > 0) userConsoleHeight else (220 * density).toInt()

            val targetHeight = if (activeFocusTarget == FocusTarget.TERMINAL) {
                minOf(preferredHeight, maxAvailableConsoleHeight)
            } else {
                // If user is editing code in editorWebView, keep console at minimum so editor has space
                minOf(minTerminalHeight, maxAvailableConsoleHeight / 2)
            }

            val params = layoutConsoleBody.layoutParams
            if (params.height != targetHeight) {
                params.height = targetHeight
                layoutConsoleBody.layoutParams = params
            }
            ideTerminalView.post {
                ideTerminalView.scrollToBottom()
            }
        } else {
            // Restore user's preferred height when keyboard closes
            if (userConsoleHeight > 0) {
                val params = layoutConsoleBody.layoutParams
                if (params.height != userConsoleHeight) {
                    params.height = userConsoleHeight
                    layoutConsoleBody.layoutParams = params
                }
            }
        }
    }

    private fun bindLinuxService() {
        LinuxService.start(this)
        bindService(
            Intent(this, LinuxService::class.java),
            serviceConnection,
            Context.BIND_AUTO_CREATE
        )
    }

    private fun attachOrCreateIdeTerminalSession() {
        val tm = terminalManager
        if (isTerminalAttached && activeIdeSessionId != null) {
            val sessionProcess = tm.getSessionProcess(activeIdeSessionId!!)
            if (sessionProcess != null && sessionProcess.isAlive) {
                return
            }
        }

        // Restore any existing IDE terminal sessions from TerminalManager if available
        if (ideTabs.isEmpty()) {
            val existingSessions = tm.sessions.value.filter {
                it.name.startsWith("Terminal") || it.name == "IDE Terminal"
            }
            if (existingSessions.isNotEmpty()) {
                ideTabs.clear()
                for (sess in existingSessions) {
                    ideTabs.add(IdeTerminalTabItem(sess.id, sess.name))
                }
                val targetId = activeIdeSessionId ?: ideTabs.first().sessionId
                switchToIdeSession(targetId)
                return
            }
        } else if (activeIdeSessionId != null) {
            switchToIdeSession(activeIdeSessionId!!)
            return
        }

        // Create first session
        createNewIdeTerminalSession("Terminal 1")
    }

    private fun createNewIdeTerminalSession(customName: String? = null): String {
        val tm = terminalManager
        val initialDir = currentRootDir?.let { codeRunner.toLinuxPath(it) } ?: "/home/ubuntu"
        val sessionName = customName ?: run {
            val nextNum = ideTabs.size + 1
            "Terminal $nextNum"
        }

        val session = tm.createSession(sessionName, initialDir = initialDir)
        val tabItem = IdeTerminalTabItem(session.id, sessionName)
        ideTabs.add(tabItem)

        switchToIdeSession(session.id)

        rvTerminalTabs.post {
            if (ideTabs.isNotEmpty()) {
                rvTerminalTabs.smoothScrollToPosition(ideTabs.size - 1)
            }
        }
        return session.id
    }

    private fun switchToIdeSession(sessionId: String) {
        val tm = terminalManager
        activeIdeSessionId = sessionId
        lastExecutionDir = currentRootDir?.let { codeRunner.toLinuxPath(it) } ?: "/home/ubuntu"

        val sessionProcess = tm.getSessionProcess(sessionId)
        if (sessionProcess != null) {
            ideTerminalView.attachBuffer(sessionProcess.terminalBuffer)
            ideTerminalView.applyPreferences()
            isTerminalAttached = true

            val tabItem = ideTabs.find { it.sessionId == sessionId }
            val tabName = tabItem?.name ?: "bash"
            tvConsoleStatus.text = "Terminal ($tabName) — Ready"

            ideTerminalView.onInputListener = { data ->
                tm.sendInput(sessionId, data)
            }
            ideTerminalView.onTerminalResize = { cols, rows ->
                tm.resizeSession(sessionId, cols, rows)
            }
            ideTerminalView.onModifierChanged = { key, active ->
                if (activeFocusTarget == FocusTarget.TERMINAL) {
                    extraKeysView.setModifierActive(key, active)
                }
            }
        }

        terminalTabAdapter.submitTabs(ideTabs, activeIdeSessionId)
        ideTerminalView.scrollToBottom()
        ideTerminalView.requestFocus()
    }

    private fun closeIdeSession(item: IdeTerminalTabItem) {
        val tm = terminalManager
        try {
            tm.closeSession(item.sessionId)
        } catch (e: Exception) {
            Log.e(TAG, "Error closing session ${item.sessionId}: ${e.message}")
        }

        ideTabs.remove(item)

        if (item.sessionId == activeIdeSessionId) {
            if (ideTabs.isNotEmpty()) {
                switchToIdeSession(ideTabs.last().sessionId)
            } else {
                isTerminalAttached = false
                activeIdeSessionId = null
                ideTerminalView.attachBuffer(TerminalBuffer())
                tvConsoleStatus.text = "Terminal (bash) — Idle"
                terminalTabAdapter.submitTabs(ideTabs, null)
                // Auto-create a fresh Terminal 1 for convenience
                createNewIdeTerminalSession("Terminal 1")
            }
        } else {
            terminalTabAdapter.submitTabs(ideTabs, activeIdeSessionId)
        }
    }

    private fun showRenameTerminalDialog(item: IdeTerminalTabItem) {
        val input = EditText(this).apply {
            setText(item.name)
            setSingleLine(true)
            setSelection(text.length)
        }
        val frame = FrameLayout(this).apply {
            val pad = (20 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad / 2, pad, 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Rename Terminal")
            .setView(frame)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isNotEmpty()) {
                    item.name = newName
                    terminalManager.renameSession(item.sessionId, newName)
                    terminalTabAdapter.notifyDataSetChanged()
                    if (item.sessionId == activeIdeSessionId) {
                        tvConsoleStatus.text = "Terminal ($newName) — Ready"
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun setupProjectDirectory() {
        val prefs = getSharedPreferences("ide_prefs", Context.MODE_PRIVATE)
        val savedPath = prefs.getString("last_project_root", null)
        val savedFile = savedPath?.let { File(it) }

        currentRootDir = if (savedFile != null && savedFile.exists() && savedFile.isDirectory) {
            savedFile
        } else {
            getDefaultLinuxHome()
        }
        updateProjectBanner()
    }

    private fun getDefaultLinuxHome(): File {
        val rootfs = UbuntuRuntime.getInstance(this).rootfsDir
        val home = File(rootfs, "home/ubuntu")
        if (!home.exists()) home.mkdirs()
        return home
    }

    private fun openProjectFolder(folder: File) {
        if (!folder.exists() || !folder.isDirectory) return
        currentRootDir = folder
        getSharedPreferences("ide_prefs", Context.MODE_PRIVATE).edit()
            .putString("last_project_root", folder.absolutePath)
            .apply()

        updateProjectBanner()
        fileTreeAdapter.setRootDir(folder)

        val linuxPath = codeRunner.toLinuxPath(folder)
        lastExecutionDir = linuxPath

        // Sync terminal working directory to newly opened project
        activeIdeSessionId?.let { sessId ->
            val tm = terminalManager
            tm.sendInput(sessId, "cd \"$linuxPath\"\n".toByteArray())
        }

        Toast.makeText(this, "Project opened: ${folder.name}", Toast.LENGTH_SHORT).show()
    }

    private fun updateProjectBanner() {
        val dir = currentRootDir ?: return
        val rootfs = UbuntuRuntime.getInstance(this).rootfsDir
        val home = File(rootfs, "home/ubuntu")

        tvProjectFolderName.text = if (dir.absolutePath == home.absolutePath) "ubuntu (~)" else dir.name
        tvProjectFolderPath.text = codeRunner.toLinuxPath(dir)

        val parent = dir.parentFile
        val canGoUp = parent != null && parent.canRead() && (
            parent.absolutePath.startsWith(rootfs.absolutePath) ||
            parent.absolutePath.contains("MobileLinux") ||
            parent.absolutePath.startsWith(android.os.Environment.getExternalStorageDirectory()?.absolutePath ?: "")
        )
        btnDrawerNavigateUp.alpha = if (canGoUp) 1.0f else 0.35f
        btnDrawerNavigateUp.isEnabled = canGoUp
    }

    private fun navigateUpToParentDirectory() {
        val dir = currentRootDir ?: return
        val parent = dir.parentFile
        if (parent != null && parent.exists() && parent.isDirectory) {
            openProjectFolder(parent)
        }
    }

    /**
     * Interactive Acode-style directory picker modal.
     * Allows navigating into any subfolder, sub-subfolder, creating new folders on the fly,
     * and selecting any folder as the project root.
     */
    private fun showFolderPickerDialog(initialDir: File? = null) {
        val rootfs = UbuntuRuntime.getInstance(this).rootfsDir
        val defaultHome = File(rootfs, "home/ubuntu")
        val sharedDir = StorageHelper.getPreferredSharedDir(this)
        val sdcard = android.os.Environment.getExternalStorageDirectory()

        var currentBrowseDir: File = initialDir?.takeIf { it.exists() && it.isDirectory } ?: (currentRootDir ?: defaultHome)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_folder_picker, null)
        val tvCurrentPath = dialogView.findViewById<TextView>(R.id.tv_picker_current_path)
        val btnUp = dialogView.findViewById<ImageView>(R.id.btn_picker_up)
        val btnNewFolder = dialogView.findViewById<ImageView>(R.id.btn_picker_new_folder)
        val rvFolders = dialogView.findViewById<RecyclerView>(R.id.rv_picker_folders)
        val tvEmpty = dialogView.findViewById<TextView>(R.id.tv_picker_empty)
        val btnSelect = dialogView.findViewById<MaterialButton>(R.id.btn_picker_select_folder)
        val btnCancel = dialogView.findViewById<Button>(R.id.btn_picker_cancel)
        val btnClose = dialogView.findViewById<ImageView>(R.id.btn_picker_close)

        val chipHome = dialogView.findViewById<TextView>(R.id.chip_linux_home)
        val chipShared = dialogView.findViewById<TextView>(R.id.chip_shared_storage)
        val chipDevice = dialogView.findViewById<TextView>(R.id.chip_device_storage)

        rvFolders.layoutManager = LinearLayoutManager(this)

        var dialog: AlertDialog? = null
        lateinit var pickerAdapter: FolderPickerAdapter

        fun loadSubfolders(dir: File) {
            currentBrowseDir = dir
            tvCurrentPath.text = codeRunner.toLinuxPath(dir)

            val subdirs = dir.listFiles()
                ?.filter { it.isDirectory && !it.name.startsWith(".") }
                ?.sortedBy { it.name.lowercase() }
                ?: emptyList()

            tvEmpty.visibility = if (subdirs.isEmpty()) View.VISIBLE else View.GONE
            pickerAdapter.updateFolders(subdirs)

            val parent = dir.parentFile
            val canUp = parent != null && parent.canRead()
            btnUp.alpha = if (canUp) 1.0f else 0.35f
            btnUp.isEnabled = canUp

            btnSelect.text = "Select \"${dir.name}\" as Project"
        }

        pickerAdapter = FolderPickerAdapter(emptyList()) { selectedSubfolder ->
            // Enter inside clicked subfolder!
            loadSubfolders(selectedSubfolder)
        }
        rvFolders.adapter = pickerAdapter

        // Initial directory loading
        loadSubfolders(currentBrowseDir)

        // Location Chips
        chipHome.setOnClickListener { loadSubfolders(defaultHome) }
        chipShared.setOnClickListener { loadSubfolders(sharedDir) }
        chipDevice.setOnClickListener {
            if (sdcard != null && sdcard.exists()) loadSubfolders(sdcard)
        }

        // Navigate up one level
        btnUp.setOnClickListener {
            val parent = currentBrowseDir.parentFile
            if (parent != null && parent.exists() && parent.isDirectory) {
                loadSubfolders(parent)
            }
        }

        // Create new folder right in currentBrowseDir
        btnNewFolder.setOnClickListener {
            showNewFolderDialog(currentBrowseDir) { newlyCreated ->
                loadSubfolders(newlyCreated)
            }
        }

        btnSelect.setOnClickListener {
            openProjectFolder(currentBrowseDir)
            dialog?.dismiss()
        }

        btnCancel.setOnClickListener { dialog?.dismiss() }
        btnClose.setOnClickListener { dialog?.dismiss() }

        dialog = MaterialAlertDialogBuilder(this)
            .setView(dialogView)
            .setCancelable(true)
            .show()
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
            onOpenAsProject = { folder ->
                openProjectFolder(folder)
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
            updateProjectBanner()
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
            },
            onCopyText = { text ->
                runOnUiThread {
                    if (text.isNotEmpty()) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Copied Text", text)
                        clipboard.setPrimaryClip(clip)
                    }
                }
            },
            onCutText = { text ->
                runOnUiThread {
                    if (text.isNotEmpty()) {
                        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("Cut Text", text)
                        clipboard.setPrimaryClip(clip)
                    }
                }
            },
            onPasteReq = {
                runOnUiThread {
                    pasteClipboardToEditor()
                }
            },
            onCtrlReset = {
                runOnUiThread {
                    resetEditorCtrlState()
                }
            }
        )

        editorWebView.addJavascriptInterface(bridge, "IdeBridge")
        editorWebView.loadUrl("file:///android_asset/editor/index.html")
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupExtraKeys() {
        // Track focus target via touch & focus
        editorWebView.setOnTouchListener { _, _ ->
            activeFocusTarget = FocusTarget.EDITOR
            extraKeysView.setModifierActive("Ctrl", isEditorCtrlActive)
            extraKeysView.setModifierActive("Alt", isEditorAltActive)
            extraKeysView.setModifierActive("Shift", isEditorShiftActive)
            if (lastKeyboardVisible) {
                adjustConsoleHeightForKeyboard(true, lastImeBottom, lastStatusBarTop)
            }
            false
        }
        editorWebView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activeFocusTarget = FocusTarget.EDITOR
                extraKeysView.setModifierActive("Ctrl", isEditorCtrlActive)
                extraKeysView.setModifierActive("Alt", isEditorAltActive)
                extraKeysView.setModifierActive("Shift", isEditorShiftActive)
                if (lastKeyboardVisible) {
                    adjustConsoleHeightForKeyboard(true, lastImeBottom, lastStatusBarTop)
                }
            }
        }

        ideTerminalView.setOnTouchListener { _, _ ->
            activeFocusTarget = FocusTarget.TERMINAL
            extraKeysView.setModifierActive("Ctrl", ideTerminalView.isCtrlActive)
            extraKeysView.setModifierActive("Alt", ideTerminalView.isAltActive)
            extraKeysView.setModifierActive("Shift", ideTerminalView.isShiftActive)
            if (lastKeyboardVisible) {
                adjustConsoleHeightForKeyboard(true, lastImeBottom, lastStatusBarTop)
            }
            false
        }
        ideTerminalView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                activeFocusTarget = FocusTarget.TERMINAL
                extraKeysView.setModifierActive("Ctrl", ideTerminalView.isCtrlActive)
                extraKeysView.setModifierActive("Alt", ideTerminalView.isAltActive)
                extraKeysView.setModifierActive("Shift", ideTerminalView.isShiftActive)
                if (lastKeyboardVisible) {
                    adjustConsoleHeightForKeyboard(true, lastImeBottom, lastStatusBarTop)
                }
            }
        }

        extraKeysView.onKeyListener = { action ->
            val isTerminalActive = (activeFocusTarget == FocusTarget.TERMINAL && layoutConsoleBody.visibility == View.VISIBLE)
            if (isTerminalActive) {
                handleTerminalKeyAction(action)
            } else {
                handleEditorKeyAction(action)
            }
        }
    }

    private fun handleTerminalKeyAction(action: ExtraKeysView.KeyAction) {
        ideTerminalView.requestFocus()
        when (action) {
            is ExtraKeysView.KeyAction.ToggleCtrl -> ideTerminalView.toggleCtrl()
            is ExtraKeysView.KeyAction.ToggleAlt -> ideTerminalView.toggleAlt()
            is ExtraKeysView.KeyAction.ToggleShift -> ideTerminalView.toggleShift()
            is ExtraKeysView.KeyAction.SpecialKeyAction -> ideTerminalView.sendSpecialKey(action.key)
            is ExtraKeysView.KeyAction.TextAction -> {
                for (ch in action.text) {
                    ideTerminalView.handleCharacterInput(ch)
                }
            }
            is ExtraKeysView.KeyAction.CtrlAction -> ideTerminalView.sendCtrl(action.char)
            is ExtraKeysView.KeyAction.PasteAction -> ideTerminalView.pasteClipboard()
        }
    }

    private fun handleEditorKeyAction(action: ExtraKeysView.KeyAction) {
        editorWebView.requestFocus()
        when (action) {
            is ExtraKeysView.KeyAction.ToggleCtrl -> {
                isEditorCtrlActive = !isEditorCtrlActive
                extraKeysView.setModifierActive("Ctrl", isEditorCtrlActive)
                editorWebView.evaluateJavascript("window.editorSetCtrlModifier($isEditorCtrlActive);", null)
            }
            is ExtraKeysView.KeyAction.ToggleAlt -> {
                isEditorAltActive = !isEditorAltActive
                extraKeysView.setModifierActive("Alt", isEditorAltActive)
            }
            is ExtraKeysView.KeyAction.ToggleShift -> {
                isEditorShiftActive = !isEditorShiftActive
                extraKeysView.setModifierActive("Shift", isEditorShiftActive)
            }
            is ExtraKeysView.KeyAction.SpecialKeyAction -> {
                when (action.key) {
                    SpecialKey.TAB -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('TAB');", null)
                    SpecialKey.ARROW_UP -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('UP');", null)
                    SpecialKey.ARROW_DOWN -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('DOWN');", null)
                    SpecialKey.ARROW_LEFT -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('LEFT');", null)
                    SpecialKey.ARROW_RIGHT -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('RIGHT');", null)
                    SpecialKey.HOME -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('HOME');", null)
                    SpecialKey.END -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('END');", null)
                    SpecialKey.PAGE_UP -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('PAGE_UP');", null)
                    SpecialKey.PAGE_DOWN -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('PAGE_DOWN');", null)
                    SpecialKey.DELETE -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('DELETE');", null)
                    SpecialKey.BACKSPACE -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('BACKSPACE');", null)
                    SpecialKey.ESC -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('ESC');", null)
                    SpecialKey.ENTER -> editorWebView.evaluateJavascript("window.editorInsert('\\n');", null)
                    else -> {}
                }
            }
            is ExtraKeysView.KeyAction.TextAction -> {
                if (isEditorCtrlActive) {
                    when (action.text.uppercase()) {
                        "A" -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('SELECT_ALL');", null)
                        "Z" -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('UNDO');", null)
                        "C" -> copyEditorSelectionToClipboard()
                        "X" -> cutEditorSelectionToClipboard()
                        "V" -> pasteClipboardToEditor()
                        "S" -> saveCurrentFile()
                        else -> {
                            val escaped = JSONObject.quote(action.text)
                            editorWebView.evaluateJavascript("window.editorInsert($escaped);", null)
                        }
                    }
                    resetEditorCtrlState()
                } else {
                    val escaped = JSONObject.quote(action.text)
                    editorWebView.evaluateJavascript("window.editorInsert($escaped);", null)
                }
            }
            is ExtraKeysView.KeyAction.CtrlAction -> {
                when (action.char.uppercaseChar()) {
                    'A' -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('SELECT_ALL');", null)
                    'Z' -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('UNDO');", null)
                    'C' -> copyEditorSelectionToClipboard()
                    'X' -> cutEditorSelectionToClipboard()
                    'D' -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('DUPLICATE_LINE');", null)
                    'L' -> editorWebView.evaluateJavascript("window.editorInsert('\\n');", null)
                    'E' -> editorWebView.evaluateJavascript("window.editorHandleSpecialKey('END');", null)
                    else -> {}
                }
            }
            is ExtraKeysView.KeyAction.PasteAction -> {
                pasteClipboardToEditor()
            }
        }
    }

    private fun resetEditorCtrlState() {
        if (isEditorCtrlActive) {
            isEditorCtrlActive = false
            extraKeysView.setModifierActive("Ctrl", false)
            editorWebView.evaluateJavascript("window.editorSetCtrlModifier(false);", null)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isEditorCtrlActive && event.action == KeyEvent.ACTION_DOWN && activeFocusTarget == FocusTarget.EDITOR) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_A -> {
                    editorWebView.evaluateJavascript("window.editorHandleSpecialKey('SELECT_ALL');", null)
                    resetEditorCtrlState()
                    return true
                }
                KeyEvent.KEYCODE_C -> {
                    copyEditorSelectionToClipboard()
                    resetEditorCtrlState()
                    return true
                }
                KeyEvent.KEYCODE_X -> {
                    cutEditorSelectionToClipboard()
                    resetEditorCtrlState()
                    return true
                }
                KeyEvent.KEYCODE_V -> {
                    pasteClipboardToEditor()
                    resetEditorCtrlState()
                    return true
                }
                KeyEvent.KEYCODE_Z -> {
                    editorWebView.evaluateJavascript("window.editorHandleSpecialKey('UNDO');", null)
                    resetEditorCtrlState()
                    return true
                }
                KeyEvent.KEYCODE_S -> {
                    saveCurrentFile()
                    resetEditorCtrlState()
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun copyEditorSelectionToClipboard() {
        editorWebView.evaluateJavascript("window.editorGetSelectedText();") { result ->
            val text = try {
                org.json.JSONTokener(result ?: "").nextValue()?.toString() ?: ""
            } catch (e: Exception) {
                result?.trim('"', '\'') ?: ""
            }
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", text)
                clipboard.setPrimaryClip(clip)
            }
        }
    }

    private fun cutEditorSelectionToClipboard() {
        editorWebView.evaluateJavascript("window.editorGetSelectedText();") { result ->
            val text = try {
                org.json.JSONTokener(result ?: "").nextValue()?.toString() ?: ""
            } catch (e: Exception) {
                result?.trim('"', '\'') ?: ""
            }
            if (text.isNotEmpty()) {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Cut Text", text)
                clipboard.setPrimaryClip(clip)
                editorWebView.evaluateJavascript("window.editorDeleteSelectedText();", null)
            }
        }
    }

    private fun pasteClipboardToEditor() {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = clipboard.primaryClip
        if (clip != null && clip.itemCount > 0) {
            val text = clip.getItemAt(0).coerceToText(this).toString()
            val escaped = JSONObject.quote(text)
            editorWebView.evaluateJavascript("window.editorInsert($escaped);", null)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupTerminalPanel() {
        // Toggle when clicking header or icon
        btnToggleConsole.setOnClickListener { toggleConsole() }

        btnCloseConsole.contentDescription = "Minimize Terminal (Long-press to close all)"
        btnCloseConsole.tooltipText = "Minimize Terminal"
        btnCloseConsole.setOnClickListener {
            toggleConsole(show = false)
        }
        btnCloseConsole.setOnLongClickListener {
            showCloseAllTerminalsDialog()
            true
        }

        btnToggleTerminal.contentDescription = "Toggle Terminal"
        btnToggleTerminal.tooltipText = "Toggle Terminal"
        btnToggleConsole.contentDescription = "Minimize Terminal"
        btnToggleConsole.tooltipText = "Minimize Terminal"
        btnStopExecution.contentDescription = "Stop Process (Ctrl+C)"
        btnStopExecution.tooltipText = "Stop Process (Ctrl+C)"
        btnClearConsole.contentDescription = "Clear Terminal"
        btnClearConsole.tooltipText = "Clear Terminal"

        // Clear terminal
        btnClearConsole.setOnClickListener {
            activeIdeSessionId?.let { sessId ->
                terminalManager.sendInput(sessId, "clear\n".toByteArray())
            }
        }

        // Stop / Interrupt running command (Ctrl+C)
        btnStopExecution.setOnClickListener {
            activeIdeSessionId?.let { sessId ->
                terminalManager.sendKey(sessId, SpecialKey.CTRL_C)
            }
        }

        // Touch Drag-to-Resize on Terminal Header
        val defaultHeight = (220 * resources.displayMetrics.density).toInt()
        if (userConsoleHeight <= 0) {
            userConsoleHeight = defaultHeight
        }
        var touchStartY = 0f
        var initialHeight = 0

        layoutConsoleHeader.setOnTouchListener { _, event ->
            val minHeight = (80 * resources.displayMetrics.density).toInt()
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchStartY = event.rawY
                    initialHeight = if (layoutConsoleBody.visibility == View.VISIBLE) {
                        layoutConsoleBody.height.takeIf { it > 0 } ?: defaultHeight
                    } else {
                        0
                    }
                    isDraggingConsole = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val deltaY = touchStartY - event.rawY
                    if (Math.abs(deltaY) > 6 * resources.displayMetrics.density) {
                        isDraggingConsole = true
                    }
                    if (isDraggingConsole) {
                        if (layoutConsoleBody.visibility != View.VISIBLE) {
                            layoutConsoleBody.visibility = View.VISIBLE
                            val activeColor = getColor(R.color.accent_blue)
                            btnToggleTerminal.setColorFilter(activeColor)
                            btnToggleConsole.setColorFilter(activeColor)
                            btnStopExecution.visibility = View.VISIBLE
                            if (!isTerminalAttached) {
                                attachOrCreateIdeTerminalSession()
                            }
                            activeFocusTarget = FocusTarget.TERMINAL
                            ideTerminalView.requestFocus()
                        }
                        val newHeight = (initialHeight + deltaY).toInt()
                        if (newHeight < minHeight - (35 * resources.displayMetrics.density).toInt()) {
                            layoutConsoleBody.visibility = View.GONE
                            val inactiveColor = getColor(R.color.text_secondary)
                            btnToggleTerminal.setColorFilter(inactiveColor)
                            btnToggleConsole.setColorFilter(inactiveColor)
                            btnStopExecution.visibility = View.GONE
                            activeFocusTarget = FocusTarget.EDITOR
                            editorWebView.requestFocus()
                        } else {
                            val maxLimit = if (lastKeyboardVisible) {
                                calculateMaxAvailableConsoleHeight(lastImeBottom, lastStatusBarTop)
                            } else {
                                (resources.displayMetrics.heightPixels * 0.75).toInt()
                            }
                            val clamped = newHeight.coerceIn(minHeight, maxLimit)
                            val params = layoutConsoleBody.layoutParams
                            params.height = clamped
                            layoutConsoleBody.layoutParams = params
                            userConsoleHeight = clamped
                        }
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isDraggingConsole) {
                        if (layoutConsoleBody.visibility == View.VISIBLE) {
                            userConsoleHeight = layoutConsoleBody.height
                            ideTerminalView.post { ideTerminalView.scrollToBottom() }
                        }
                        isDraggingConsole = false
                    } else {
                        toggleConsole()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleConsole(show: Boolean? = null) {
        val willShow = show ?: (layoutConsoleBody.visibility != View.VISIBLE)
        layoutConsoleBody.visibility = if (willShow) View.VISIBLE else View.GONE
        val color = getColor(if (willShow) R.color.accent_blue else R.color.text_secondary)
        btnToggleTerminal.setColorFilter(color)
        btnToggleConsole.setColorFilter(color)
        btnStopExecution.visibility = if (willShow) View.VISIBLE else View.GONE

        if (willShow) {
            if (userConsoleHeight <= 0) {
                userConsoleHeight = (220 * resources.displayMetrics.density).toInt()
            }
            val params = layoutConsoleBody.layoutParams
            params.height = userConsoleHeight
            layoutConsoleBody.layoutParams = params

            if (lastKeyboardVisible) {
                adjustConsoleHeightForKeyboard(true, lastImeBottom, lastStatusBarTop)
            }

            if (!isTerminalAttached || activeIdeSessionId == null) {
                attachOrCreateIdeTerminalSession()
            }
            activeFocusTarget = FocusTarget.TERMINAL
            ideTerminalView.requestFocus()
        } else {
            activeFocusTarget = FocusTarget.EDITOR
            editorWebView.requestFocus()
            extraKeysView.setModifierActive("Ctrl", isEditorCtrlActive)
            extraKeysView.setModifierActive("Alt", isEditorAltActive)
            extraKeysView.setModifierActive("Shift", isEditorShiftActive)
        }
    }

    // =========================================================================
    // File & Tab Operations
    // =========================================================================

    fun openFileInEditor(file: File) {
        val existingIndex = openTabs.indexOfFirst { it.file.absolutePath == file.absolutePath }
        if (existingIndex != -1) {
            switchTab(existingIndex)
            return
        }

        val tab = IdeTab(file = file)
        openTabs.add(tab)
        val newIndex = openTabs.size - 1
        tabsAdapter.notifyItemInserted(newIndex)
        switchTab(newIndex)
        saveTabsState()
    }

    private fun switchTab(index: Int) {
        if (index !in openTabs.indices) return

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
        saveTabsState()
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
        saveTabsState()
    }

    // =========================================================================
    // Execution Runner -> Executes Directly in Integrated Terminal (VS Code Style)
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

        val linuxPath = codeRunner.toLinuxPath(file)
        val cmd = codeRunner.buildCommandForFile(linuxPath, file)
        executeCommandInTerminal(file, cmd)
    }

    private fun showCustomRunDialog() {
        if (activeTabIndex !in openTabs.indices) {
            Toast.makeText(this, "No file open to run", Toast.LENGTH_SHORT).show()
            return
        }

        val activeTab = openTabs[activeTabIndex]
        saveCurrentFile()
        val file = activeTab.file
        val linuxPath = codeRunner.toLinuxPath(file)
        val defaultCmd = codeRunner.buildCommandForFile(linuxPath, file)

        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_file_action, null)
        val promptText = dialogView.findViewById<TextView>(R.id.tv_dialog_prompt)
        val etCommand = dialogView.findViewById<EditText>(R.id.et_file_name)

        promptText.text = "Customize run command or arguments:"
        etCommand.setText(defaultCmd)
        etCommand.setSelection(etCommand.text.length)

        MaterialAlertDialogBuilder(this)
            .setTitle("Run Configuration")
            .setView(dialogView)
            .setPositiveButton("Run") { _, _ ->
                val customCmd = etCommand.text.toString().trim()
                if (customCmd.isNotEmpty()) {
                    executeCommandInTerminal(file, customCmd)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private var lastExecutionDir: String? = null

    private fun executeCommandInTerminal(file: File, command: String) {
        // Expand terminal panel
        toggleConsole(show = true)
        activeFocusTarget = FocusTarget.TERMINAL

        if (activeIdeSessionId == null || ideTabs.isEmpty()) {
            createNewIdeTerminalSession()
        }

        val tm = terminalManager
        val sessId = activeIdeSessionId

        if (sessId != null) {
            val linuxPath = codeRunner.toLinuxPath(file)
            val dir = File(linuxPath).parent ?: "/home/ubuntu"
            
            // Clean execution: avoid redundant cd if already in target directory
            val execCmd = if (dir == lastExecutionDir) {
                "$command\n"
            } else {
                lastExecutionDir = dir
                "cd \"$dir\" && $command\n"
            }

            tm.sendInput(sessId, execCmd.toByteArray())
            ideTerminalView.requestFocus()
        } else {
            Toast.makeText(this, "Terminal connecting, please retry in 1s", Toast.LENGTH_SHORT).show()
        }
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

    private fun showNewFolderDialog(parentDir: File, onCreated: ((File) -> Unit)? = null) {
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
                        onCreated?.invoke(newFolder)
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
                        openTabs.find { it.file.absolutePath == target.absolutePath }?.let { tab ->
                            val idx = openTabs.indexOf(tab)
                            openTabs[idx] = tab.copy(file = dest, title = dest.name)
                            tabsAdapter.notifyItemChanged(idx)
                            if (activeTabIndex == idx) tvActiveFilename.text = dest.name
                        }
                        saveTabsState()
                        fileTreeAdapter.reload()
                        updateProjectBanner()
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
                if (target.isDirectory) {
                    val tabsToClose = openTabs.filter { it.file.absolutePath.startsWith(target.absolutePath) }
                    for (tab in tabsToClose) {
                        val idx = openTabs.indexOf(tab)
                        if (idx != -1) performCloseTab(idx)
                    }
                    target.deleteRecursively()
                } else {
                    val openIdx = openTabs.indexOfFirst { it.file.absolutePath == target.absolutePath }
                    if (openIdx != -1) {
                        performCloseTab(openIdx)
                    }
                    target.delete()
                }
                saveTabsState()
                fileTreeAdapter.reload()
                updateProjectBanner()
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
        popup.menu.add(0, 8, 7, "Minimize to Terminal")
        popup.menu.add(0, 9, 8, "Close All Tabs")
        popup.menu.add(0, 10, 9, "Exit IDE")

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
                    minimizeToTerminal()
                }
                9 -> {
                    while (openTabs.isNotEmpty()) {
                        performCloseTab(0)
                    }
                }
                10 -> {
                    confirmAndExitIde()
                }
            }
            true
        }
        popup.show()
    }

    private fun confirmAndExitIde() {
        val unsavedCount = openTabs.count { it.isDirty }
        if (unsavedCount > 0) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Exit Code IDE")
                .setMessage("You have $unsavedCount unsaved file(s). Do you want to save before exiting?")
                .setPositiveButton("Save & Exit") { _, _ ->
                    saveCurrentFile()
                    saveTabsState()
                    finish()
                }
                .setNegativeButton("Exit Without Saving") { _, _ ->
                    saveTabsState()
                    finish()
                }
                .setNeutralButton("Cancel", null)
                .show()
        } else {
            saveTabsState()
            finish()
        }
    }

    private fun saveTabsState() {
        try {
            val prefs = getSharedPreferences("ide_prefs", Context.MODE_PRIVATE)
            val tabPaths = JSONArray()
            for (tab in openTabs) {
                tabPaths.put(tab.file.absolutePath)
            }
            val activePath = if (activeTabIndex in openTabs.indices) {
                openTabs[activeTabIndex].file.absolutePath
            } else null

            prefs.edit()
                .putString(KEY_SAVED_OPEN_TABS, tabPaths.toString())
                .putString(KEY_ACTIVE_TAB_PATH, activePath)
                .apply()
        } catch (e: Exception) {
            Log.e(TAG, "Error saving tabs state: ${e.message}")
        }
    }

    private fun restoreOrInitIdeSession() {
        val prefs = getSharedPreferences("ide_prefs", Context.MODE_PRIVATE)
        val welcomeShown = prefs.getBoolean(KEY_IDE_WELCOME_SHOWN, false)
        if (!welcomeShown) {
            // First time user launch: create hello.py welcome script
            prefs.edit().putBoolean(KEY_IDE_WELCOME_SHOWN, true).apply()
            val root = currentRootDir ?: getDefaultLinuxHome()
            val exampleFile = File(root, "hello.py")
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
            saveTabsState()
            return
        }

        // Subsequent runs: Restore last opened tabs
        val savedTabsRaw = prefs.getString(KEY_SAVED_OPEN_TABS, null)
        val savedActivePath = prefs.getString(KEY_ACTIVE_TAB_PATH, null)

        if (!savedTabsRaw.isNullOrEmpty()) {
            try {
                val jsonArr = JSONArray(savedTabsRaw)
                val filesToOpen = mutableListOf<File>()
                for (i in 0 until jsonArr.length()) {
                    val path = jsonArr.optString(i)
                    if (!path.isNullOrEmpty()) {
                        val file = File(path)
                        if (file.exists() && file.isFile) {
                            filesToOpen.add(file)
                        }
                    }
                }

                if (filesToOpen.isNotEmpty()) {
                    for (file in filesToOpen) {
                        val tab = IdeTab(file = file)
                        openTabs.add(tab)
                    }
                    tabsAdapter.notifyDataSetChanged()

                    var targetIdx = -1
                    if (!savedActivePath.isNullOrEmpty()) {
                        targetIdx = openTabs.indexOfFirst { it.file.absolutePath == savedActivePath }
                    }
                    if (targetIdx == -1) targetIdx = openTabs.size - 1
                    switchTab(targetIdx)
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to restore tabs: ${e.message}")
            }
        }

        // If no tabs were restored or all were closed by user: keep clean workspace
        openTabs.clear()
        activeTabIndex = -1
        tvActiveFilename.text = "MobileLinux IDE"
        editorWebView.visibility = View.INVISIBLE
        layoutEmptyWelcome.visibility = View.VISIBLE
    }

    private fun minimizeToTerminal() {
        saveTabsState()
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
        Toast.makeText(this, "Code IDE running in background", Toast.LENGTH_SHORT).show()
    }

    private fun showCloseAllTerminalsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Close All Terminals")
            .setMessage("Do you want to terminate all open IDE terminal sessions?")
            .setPositiveButton("Close All") { _, _ ->
                val tm = terminalManager
                for (tab in ideTabs.toList()) {
                    try {
                        tm.closeSession(tab.sessionId)
                    } catch (ignored: Exception) {}
                }
                ideTabs.clear()
                activeIdeSessionId = null
                isTerminalAttached = false
                ideTerminalView.attachBuffer(TerminalBuffer())
                tvConsoleStatus.text = "Terminal (bash) — Idle"
                terminalTabAdapter.submitTabs(ideTabs, null)
                toggleConsole(show = false)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    override fun onDestroy() {
        val tm = terminalManager
        for (tab in ideTabs.toList()) {
            try {
                tm.closeSession(tab.sessionId)
            } catch (ignored: Exception) {}
        }
        ideTabs.clear()
        activeIdeSessionId = null

        if (isServiceBound) {
            try {
                unbindService(serviceConnection)
            } catch (ignored: Exception) {}
            isServiceBound = false
        }
        super.onDestroy()
    }

    override fun onPause() {
        super.onPause()
        saveTabsState()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.path?.let { filePath ->
            val file = File(filePath)
            if (file.exists() && file.isFile) {
                openFileInEditor(file)
            }
        }
    }

    private fun showNavigationDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Code IDE Navigation")
            .setMessage("Do you want to minimize Code IDE (keep running in background) or exit completely?")
            .setPositiveButton("Minimize") { _, _ ->
                minimizeToTerminal()
            }
            .setNegativeButton("Exit IDE") { _, _ ->
                confirmAndExitIde()
            }
            .setNeutralButton("Cancel", null)
            .show()
    }

    private fun handleBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else if (layoutConsoleBody.visibility == View.VISIBLE) {
            toggleConsole(show = false)
        } else {
            showNavigationDialog()
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        handleBackPressed()
    }
}
