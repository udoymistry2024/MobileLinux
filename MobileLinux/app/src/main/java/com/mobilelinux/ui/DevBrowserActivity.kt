package com.mobilelinux.ui

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Environment
import android.os.Message
import android.util.Log
import android.text.Editable
import android.text.TextWatcher
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mobilelinux.R
import com.mobilelinux.data.BrowserHistoryDbHelper
import com.mobilelinux.data.BrowserHistoryItem
import java.net.URLEncoder
import java.util.Collections
import java.util.UUID
import kotlin.math.max

data class BrowserTab(
    val id: String = UUID.randomUUID().toString(),
    var title: String = "Google",
    var url: String = DevBrowserActivity.DEFAULT_HOME_URL,
    val webView: WebView,
    var isDesktopMode: Boolean = false
)

class DevBrowserActivity : AppCompatActivity() {

    private lateinit var webviewContainer: FrameLayout
    private lateinit var etUrl: EditText
    private lateinit var btnClearUrl: ImageButton
    private lateinit var btnHome: ImageButton
    private lateinit var btnTabSwitcher: FrameLayout
    private lateinit var tvTabCount: TextView
    private lateinit var btnMenu: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var ivSslIndicator: ImageView
    private lateinit var layoutError: LinearLayout
    private lateinit var tvErrorDesc: TextView
    private lateinit var btnRetry: Button
    private lateinit var topBar: LinearLayout

    // Tab Switcher Overlay Views
    private lateinit var layoutTabSwitcher: LinearLayout
    private lateinit var tabSwitcherTopBar: LinearLayout
    private lateinit var tvTabSwitcherTitle: TextView
    private lateinit var btnNewTab: MaterialButton
    private lateinit var btnCloseAllTabs: MaterialButton
    private lateinit var btnCloseTabSwitcher: MaterialButton
    private lateinit var rvTabGrid: RecyclerView
    private lateinit var tabGridAdapter: TabGridAdapter

    // Intent debouncing
    private var lastIntentUrl: String? = null
    private var lastIntentTime: Long = 0L

    // Tabs state
    private val tabs = mutableListOf<BrowserTab>()
    private var activeTabIndex = 0
    private val currentTab: BrowserTab?
        get() = if (tabs.isNotEmpty() && activeTabIndex in tabs.indices) tabs[activeTabIndex] else null

    private var defaultUserAgent: String = ""
    private val consoleLogs = Collections.synchronizedList(mutableListOf<String>())
    private lateinit var historyDb: BrowserHistoryDbHelper

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var jupyterPatchScript: String? = null

    private fun getJupyterPatchScript(): String {
        if (jupyterPatchScript == null) {
            jupyterPatchScript = try {
                assets.open("scripts/jupyter-mobile-patch.js").bufferedReader().use { it.readText() }
            } catch (e: Exception) {
                Log.e("DevBrowser", "Failed to load jupyter-mobile-patch.js", e)
                ""
            }
        }
        return jupyterPatchScript ?: ""
    }

    private fun injectJupyterMobilePatchIfNeeded(view: WebView?, url: String?) {
        if (view == null || url == null) return
        val lower = url.lowercase()
        if (lower.contains(":8888") || lower.contains("/tree") || lower.contains("/notebooks/") || lower.contains("/lab") || lower.contains("jupyter")) {
            val script = getJupyterPatchScript()
            if (script.isNotEmpty()) {
                view.evaluateJavascript(script, null)
            }
        }
    }

    private fun injectForceDarkUniversal(view: WebView?) {
        if (view == null) return
        val darkScript = """
            (function() {
                try {
                    var meta = document.querySelector('meta[name="color-scheme"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'color-scheme';
                        (document.head || document.documentElement).appendChild(meta);
                    }
                    meta.content = 'dark';
                    if (document.documentElement) {
                        document.documentElement.style.colorScheme = 'dark';
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        view.evaluateJavascript(darkScript, null)
    }
    private val fileChooserLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uris = if (result.resultCode == RESULT_OK && result.data != null) {
            val data = result.data
            if (data?.clipData != null) {
                val clipData = data.clipData!!
                val list = mutableListOf<Uri>()
                for (i in 0 until clipData.itemCount) {
                    list.add(clipData.getItemAt(i).uri)
                }
                list.toTypedArray()
            } else if (data?.data != null) {
                arrayOf(data.data!!)
            } else null
        } else null

        filePathCallback?.onReceiveValue(uris)
        filePathCallback = null
    }

    private var pendingWebPermissionRequest: PermissionRequest? = null
    private val webPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        pendingWebPermissionRequest?.let { req ->
            val grantedList = mutableListOf<String>()
            for (res in req.resources) {
                when (res) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                        if (results[android.Manifest.permission.CAMERA] == true ||
                            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            grantedList.add(res)
                        }
                    }
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                        if (results[android.Manifest.permission.RECORD_AUDIO] == true ||
                            androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            grantedList.add(res)
                        }
                    }
                    PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID -> {
                        grantedList.add(res)
                    }
                }
            }
            if (grantedList.isNotEmpty()) {
                req.grant(grantedList.toTypedArray())
            } else {
                req.deny()
            }
            pendingWebPermissionRequest = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        delegate.localNightMode = AppCompatDelegate.MODE_NIGHT_YES
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_browser)

        historyDb = BrowserHistoryDbHelper.getInstance(this)

        try {
            WebView.setWebContentsDebuggingEnabled(true)
        } catch (ignored: Exception) {}

        initViews()
        initWindowInsets()
        initAddressBar()
        initQuickChips()
        initTabSwitcherOverlay()
        initBackNavigation()

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_HOME_URL
        createNewTab(initialUrl, select = true)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val url = intent.getStringExtra(EXTRA_URL)
        if (!url.isNullOrEmpty()) {
            val now = System.currentTimeMillis()
            if (url == lastIntentUrl && (now - lastIntentTime) < 2000L) {
                // Ignore rapid duplicate trigger
                return
            }
            lastIntentUrl = url
            lastIntentTime = now

            closeTabSwitcher()

            // If the currently active tab is a clean blank or default home tab with no navigation history, reuse it
            val active = currentTab
            val activeWeb = active?.webView
            val isBlankOrHome = activeWeb != null &&
                    !activeWeb.canGoBack() &&
                    (active.url == "about:blank" || active.url == DEFAULT_HOME_URL || activeWeb.url == "about:blank" || activeWeb.url == null)

            if (isBlankOrHome && active != null) {
                loadTargetUrlInTab(active, url)
            } else {
                createNewTab(url, select = true)
            }
        }
    }

    private fun initViews() {
        webviewContainer = findViewById(R.id.webview_container)
        etUrl = findViewById(R.id.et_browser_url)
        btnClearUrl = findViewById(R.id.btn_clear_url)
        btnHome = findViewById(R.id.btn_browser_home)
        btnTabSwitcher = findViewById(R.id.btn_tab_switcher)
        tvTabCount = findViewById(R.id.tv_tab_count)
        btnMenu = findViewById(R.id.btn_browser_menu)
        progressBar = findViewById(R.id.pb_browser)
        ivSslIndicator = findViewById(R.id.iv_ssl_indicator)
        layoutError = findViewById(R.id.layout_browser_error)
        tvErrorDesc = findViewById(R.id.tv_error_desc)
        btnRetry = findViewById(R.id.btn_retry_load)
        topBar = findViewById(R.id.browser_top_bar)

        btnHome.setOnClickListener {
            loadTargetUrl(DEFAULT_HOME_URL)
        }

        btnTabSwitcher.setOnClickListener {
            openTabSwitcher()
        }

        btnMenu.setOnClickListener { v ->
            showOverflowMenu(v)
        }

        btnRetry.setOnClickListener {
            layoutError.visibility = View.GONE
            currentTab?.webView?.reload()
        }
    }

    private fun initWindowInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(topBar) { v, insets ->
            val sysInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                v.paddingLeft,
                sysInsets.top + dpToPx(4),
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        tabSwitcherTopBar = findViewById(R.id.tab_switcher_top_bar)
        ViewCompat.setOnApplyWindowInsetsListener(tabSwitcherTopBar) { v, insets ->
            val sysInsets = insets.getInsets(
                WindowInsetsCompat.Type.statusBars() or
                WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(
                v.paddingLeft,
                sysInsets.top + dpToPx(4),
                v.paddingRight,
                v.paddingBottom
            )
            insets
        }

        val rootLayout: View = findViewById(R.id.layout_dev_browser_root)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val navAndImeInsets = insets.getInsets(
                WindowInsetsCompat.Type.navigationBars() or
                WindowInsetsCompat.Type.ime()
            )
            val bottomInset = navAndImeInsets.bottom
            webviewContainer.setPadding(0, 0, 0, bottomInset)
            rvTabGrid.setPadding(dpToPx(8), dpToPx(8), dpToPx(8), bottomInset + dpToPx(8))
            insets
        }
    }

    private fun initAddressBar() {
        etUrl.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE) {
                val input = etUrl.text.toString().trim()
                if (input.isNotEmpty()) {
                    loadTargetUrl(input)
                    hideKeyboard()
                }
                true
            } else false
        }

        etUrl.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                btnClearUrl.visibility = if (!s.isNullOrEmpty() && etUrl.isFocused) View.VISIBLE else View.GONE
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        etUrl.setOnFocusChangeListener { _, hasFocus ->
            btnClearUrl.visibility = if (hasFocus && etUrl.text.isNotEmpty()) View.VISIBLE else View.GONE
            if (hasFocus) {
                etUrl.selectAll()
            }
        }

        btnClearUrl.setOnClickListener {
            etUrl.text.clear()
        }
    }

    private fun initQuickChips() {
        findViewById<TextView>(R.id.chip_jupyter).setOnClickListener {
            loadTargetUrl("http://127.0.0.1:8888/lab")
        }
        findViewById<TextView>(R.id.chip_port_3000).setOnClickListener {
            loadTargetUrl("http://127.0.0.1:3000")
        }
        findViewById<TextView>(R.id.chip_port_5000).setOnClickListener {
            loadTargetUrl("http://127.0.0.1:5000")
        }
        findViewById<TextView>(R.id.chip_port_8000).setOnClickListener {
            loadTargetUrl("http://127.0.0.1:8000")
        }
        findViewById<TextView>(R.id.chip_port_8080).setOnClickListener {
            loadTargetUrl("http://127.0.0.1:8080")
        }
        findViewById<TextView>(R.id.chip_google).setOnClickListener {
            loadTargetUrl("https://www.google.com")
        }
        findViewById<TextView>(R.id.chip_github).setOnClickListener {
            loadTargetUrl("https://github.com")
        }
    }

    private fun initTabSwitcherOverlay() {
        layoutTabSwitcher = findViewById(R.id.layout_tab_switcher)
        tvTabSwitcherTitle = findViewById(R.id.tv_tab_switcher_title)
        btnNewTab = findViewById(R.id.btn_new_tab)
        btnCloseAllTabs = findViewById(R.id.btn_close_all_tabs)
        btnCloseTabSwitcher = findViewById(R.id.btn_close_tab_switcher)
        rvTabGrid = findViewById(R.id.rv_tab_grid)

        rvTabGrid.layoutManager = GridLayoutManager(this, 2)
        tabGridAdapter = TabGridAdapter(
            onTabSelected = { index ->
                switchTab(index)
                closeTabSwitcher()
            },
            onTabClosed = { index ->
                closeTab(index)
            }
        )
        rvTabGrid.adapter = tabGridAdapter

        btnNewTab.setOnClickListener {
            closeTabSwitcher()
            createNewTab(DEFAULT_HOME_URL, select = true)
        }

        btnCloseAllTabs.setOnClickListener {
            showCloseAllTabsConfirmationDialog()
        }

        btnCloseTabSwitcher.setOnClickListener {
            closeTabSwitcher()
        }
    }

    private fun showCloseAllTabsConfirmationDialog() {
        val count = tabs.size
        val isSingleCleanTab = count == 1 && (tabs.first().url == DEFAULT_HOME_URL || tabs.first().url == "about:blank")
        if (isSingleCleanTab) {
            Toast.makeText(this, "Only a single home tab is open", Toast.LENGTH_SHORT).show()
            return
        }

        CustomDialog.Builder(this)
            .setIcon(R.drawable.ic_close, ContextCompat.getColor(this, R.color.accent_red))
            .setTitle("Close All Tabs")
            .setMessage("Are you sure you want to close all $count tabs? A clean home tab will be opened.")
            .setNeutralButton("Cancel")
            .setPositiveButton("Close All", destructive = true) {
                closeAllTabs()
            }
            .show()
    }

    private fun closeAllTabs() {
        for (tab in tabs) {
            try {
                webviewContainer.removeView(tab.webView)
                tab.webView.stopLoading()
                tab.webView.loadUrl("about:blank")
                tab.webView.destroy()
            } catch (ignored: Exception) {}
        }
        tabs.clear()

        // Create a single clean new tab with default home URL
        createNewTab(DEFAULT_HOME_URL, select = true)
        tabGridAdapter.notifyDataSetChanged()
        closeTabSwitcher()
        Toast.makeText(this, "All tabs closed", Toast.LENGTH_SHORT).show()
    }

    private fun initBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (layoutTabSwitcher.visibility == View.VISIBLE) {
                    closeTabSwitcher()
                    return
                }

                val activeWeb = currentTab?.webView
                if (activeWeb != null && activeWeb.canGoBack()) {
                    activeWeb.goBack()
                } else if (tabs.size > 1) {
                    closeTab(activeTabIndex)
                } else {
                    showExitConfirmationDialog()
                }
            }
        })
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createNewTab(initialUrl: String = DEFAULT_HOME_URL, select: Boolean = true): BrowserTab {
        val webView = WebView(this)
        val tab = BrowserTab(
            url = initialUrl,
            webView = webView
        )

        val settings = webView.settings
        if (defaultUserAgent.isEmpty()) {
            defaultUserAgent = settings.userAgentString
        }

        // Modern Web Standards
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        @Suppress("DEPRECATION")
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        @Suppress("DEPRECATION")
        settings.allowFileAccessFromFileURLs = true
        @Suppress("DEPRECATION")
        settings.allowUniversalAccessFromFileURLs = true

        // Viewport & Scale
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // Relaxed development security
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)

        // Native System Clipboard Bridge for in-page Javascript
        webView.addJavascriptInterface(BrowserClipboardBridge(this), "MobileLinuxClipboard")

        // Force Dark Mode & Algorithmic Darkening for all websites and localhost servers
        webView.setBackgroundColor(android.graphics.Color.parseColor("#121212"))
        try {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
                WebSettingsCompat.setForceDark(settings, WebSettingsCompat.FORCE_DARK_ON)
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
                WebSettingsCompat.setForceDarkStrategy(
                    settings,
                    WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
                )
            }
            if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
                WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, true)
            }
        } catch (e: Throwable) {
            Log.w("DevBrowser", "AndroidX WebKit Force Dark setup: ${e.message}")
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            try {
                @Suppress("DEPRECATION")
                settings.forceDark = WebSettings.FORCE_DARK_ON
            } catch (ignored: Throwable) {}
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            try {
                settings.isAlgorithmicDarkeningAllowed = true
            } catch (ignored: Throwable) {}
        }

        // Web Client
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                injectForceDarkUniversal(view)
                url?.let {
                    tab.url = it
                    if (isTabActive(tab)) {
                        progressBar.visibility = View.VISIBLE
                        layoutError.visibility = View.GONE
                        if (!etUrl.isFocused) {
                            etUrl.setText(it)
                        }
                        updateSslIndicator(it)
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                injectForceDarkUniversal(view)
                url?.let {
                    tab.url = it
                    val pageTitle = view?.title ?: tab.title
                    tab.title = if (pageTitle.isNotBlank() && !pageTitle.startsWith("http")) pageTitle else it

                    if (isTabActive(tab)) {
                        progressBar.visibility = View.GONE
                        if (!etUrl.isFocused) {
                            etUrl.setText(it)
                        }
                        updateSslIndicator(it)
                    }

                    // Save to local private history
                    historyDb.addHistory(tab.title, it)

                    // Auto-inject touch and mobile enhancements for Jupyter Notebook / JupyterLab
                    injectJupyterMobilePatchIfNeeded(view, it)
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val url = error?.url ?: ""
                // Auto-permit self-signed SSL for local development (localhost, 127.0.0.1, internal IPs)
                if (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("0.0.0.0") ||
                    url.startsWith("https://192.168.") || url.startsWith("https://10.")
                ) {
                    handler?.proceed()
                } else {
                    CustomDialog.Builder(this@DevBrowserActivity)
                        .setIcon(R.drawable.ic_shield_check, ContextCompat.getColor(this@DevBrowserActivity, R.color.accent_yellow))
                        .setTitle("SSL Certificate Warning")
                        .setMessage("The certificate for '${url.take(50)}' is not trusted.\n\nDo you want to proceed anyway?")
                        .setPositiveButton("Proceed (Unsafe)", destructive = true) { handler?.proceed() }
                        .setNeutralButton("Cancel") { handler?.cancel() }
                        .show()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true && isTabActive(tab)) {
                    val failingUrl = request.url.toString()
                    val errCode = error?.errorCode ?: -1
                    val errDesc = error?.description ?: "Unknown error"
                    Log.e("DevBrowser", "onReceivedError ($errCode): $errDesc for $failingUrl")
                    progressBar.visibility = View.GONE
                    layoutError.visibility = View.VISIBLE
                    val hint = if (failingUrl.startsWith("file://", ignoreCase = true)) {
                        "Ensure the local HTML file exists and is accessible."
                    } else {
                        "Ensure your local server (e.g. Jupyter, Node.js, Flask) is actively running in the terminal."
                    }
                    tvErrorDesc.text = "Could not connect to:\n$failingUrl\n\n$hint"
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false

                if (scheme == "http" || scheme == "https" || scheme == "file" || scheme == "about" || scheme == "javascript") {
                    return false
                }

                // Handle custom schemes / external app triggers
                return try {
                    val intent = Intent(Intent.ACTION_VIEW, uri)
                    startActivity(intent)
                    true
                } catch (e: Exception) {
                    false
                }
            }
        }

        // WebChrome Client
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (isTabActive(tab)) {
                    progressBar.progress = newProgress
                    if (newProgress >= 100) {
                        progressBar.visibility = View.GONE
                    }
                }
                if (newProgress >= 65) {
                    injectJupyterMobilePatchIfNeeded(view, view?.url)
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!title.isNullOrEmpty()) {
                    tab.title = title
                    if (isTabActive(tab) && !etUrl.isFocused && !title.startsWith("http")) {
                        etUrl.hint = title
                    }
                }
            }

            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                consoleMessage?.let {
                    val level = it.messageLevel().name
                    val msg = "[${it.sourceId()}:${it.lineNumber()}] [$level] ${it.message()}"
                    if (consoleLogs.size > 200) {
                        consoleLogs.removeAt(0)
                    }
                    consoleLogs.add(msg)
                }
                return true
            }

            // Support target="_blank" links opening in a new tab without collision (requires user gesture)
            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                if (!isUserGesture) {
                    return false
                }
                val newTab = createNewTab("about:blank", select = true)
                val transport = resultMsg?.obj as? WebView.WebViewTransport
                transport?.webView = newTab.webView
                resultMsg?.sendToTarget()
                return true
            }

            // File chooser for uploads (Jupyter, forms)
            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                this@DevBrowserActivity.filePathCallback?.onReceiveValue(null)
                this@DevBrowserActivity.filePathCallback = filePathCallback

                return try {
                    val intent = fileChooserParams?.createIntent() ?: Intent(Intent.ACTION_GET_CONTENT).apply {
                        type = "*/*"
                    }
                    fileChooserLauncher.launch(intent)
                    true
                } catch (e: Exception) {
                    this@DevBrowserActivity.filePathCallback = null
                    false
                }
            }

            // Dynamic on-demand camera & microphone permissions for Jupyter, WebRTC, OpenCV, WebCam
            override fun onPermissionRequest(request: PermissionRequest?) {
                if (request == null) return
                val requestedResources = request.resources
                val neededAndroidPerms = mutableListOf<String>()

                for (res in requestedResources) {
                    if (res == PermissionRequest.RESOURCE_VIDEO_CAPTURE) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                this@DevBrowserActivity,
                                android.Manifest.permission.CAMERA
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            neededAndroidPerms.add(android.Manifest.permission.CAMERA)
                        }
                    }
                    if (res == PermissionRequest.RESOURCE_AUDIO_CAPTURE) {
                        if (androidx.core.content.ContextCompat.checkSelfPermission(
                                this@DevBrowserActivity,
                                android.Manifest.permission.RECORD_AUDIO
                            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
                        ) {
                            neededAndroidPerms.add(android.Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }

                if (neededAndroidPerms.isEmpty()) {
                    request.grant(requestedResources)
                } else {
                    pendingWebPermissionRequest = request
                    webPermissionLauncher.launch(neededAndroidPerms.toTypedArray())
                }
            }

            override fun onPermissionRequestCanceled(request: PermissionRequest?) {
                super.onPermissionRequestCanceled(request)
                if (pendingWebPermissionRequest == request) {
                    pendingWebPermissionRequest = null
                }
            }

            override fun onJsAlert(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                MaterialAlertDialogBuilder(this@DevBrowserActivity)
                    .setTitle("Alert")
                    .setMessage(message ?: "")
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: JsResult?): Boolean {
                MaterialAlertDialogBuilder(this@DevBrowserActivity)
                    .setTitle("Confirm")
                    .setMessage(message ?: "")
                    .setPositiveButton("OK") { _, _ -> result?.confirm() }
                    .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: JsPromptResult?): Boolean {
                val input = EditText(this@DevBrowserActivity).apply {
                    setText(defaultValue ?: "")
                }
                MaterialAlertDialogBuilder(this@DevBrowserActivity)
                    .setTitle("Prompt")
                    .setMessage(message ?: "")
                    .setView(input)
                    .setPositiveButton("OK") { _, _ -> result?.confirm(input.text.toString()) }
                    .setNegativeButton("Cancel") { _, _ -> result?.cancel() }
                    .setOnCancelListener { result?.cancel() }
                    .show()
                return true
            }
        }

        // Native Downloads
        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            try {
                val request = DownloadManager.Request(Uri.parse(url)).apply {
                    setMimeType(mimetype)
                    addRequestHeader("User-Agent", userAgent)
                    setDescription("Downloading file from MobileLinux...")
                    setTitle(URLUtil.guessFileName(url, contentDisposition, mimetype))
                    setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    setDestinationInExternalPublicDir(
                        Environment.DIRECTORY_DOWNLOADS,
                        URLUtil.guessFileName(url, contentDisposition, mimetype)
                    )
                }
                val dm = getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
                dm?.enqueue(request)
                Toast.makeText(this, "Downloading file to Downloads folder...", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this, "Download error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        tabs.add(tab)
        updateTabCountDisplay()

        if (select) {
            switchTab(tabs.size - 1)
        }

        loadTargetUrlInTab(tab, initialUrl)
        return tab
    }

    private fun isTabActive(tab: BrowserTab): Boolean {
        return currentTab?.id == tab.id
    }

    private fun switchTab(index: Int) {
        if (index !in tabs.indices) return
        activeTabIndex = index
        val tab = tabs[index]

        webviewContainer.removeAllViews()
        val parent = tab.webView.parent as? ViewGroup
        parent?.removeView(tab.webView)
        webviewContainer.addView(
            tab.webView,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        etUrl.setText(tab.webView.url ?: tab.url)
        updateSslIndicator(tab.webView.url ?: tab.url)
        layoutError.visibility = View.GONE
        progressBar.visibility = if (tab.webView.progress < 100) View.VISIBLE else View.GONE

        updateTabCountDisplay()
    }

    private fun closeTab(index: Int) {
        if (index !in tabs.indices) return
        val tabToClose = tabs.removeAt(index)

        try {
            webviewContainer.removeView(tabToClose.webView)
            tabToClose.webView.stopLoading()
            tabToClose.webView.destroy()
        } catch (ignored: Exception) {}

        if (tabs.isEmpty()) {
            createNewTab(DEFAULT_HOME_URL, select = true)
        } else {
            if (activeTabIndex >= tabs.size) {
                activeTabIndex = tabs.size - 1
            } else if (activeTabIndex == index) {
                activeTabIndex = max(0, index - 1)
            }
            switchTab(activeTabIndex)
        }

        updateTabCountDisplay()
        tabGridAdapter.notifyDataSetChanged()
    }

    private fun updateTabCountDisplay() {
        tvTabCount.text = "${tabs.size}"
        tvTabSwitcherTitle.text = "Tabs (${tabs.size})"
    }

    private fun openTabSwitcher() {
        hideKeyboard()
        layoutTabSwitcher.visibility = View.VISIBLE
        tabGridAdapter.notifyDataSetChanged()
    }

    private fun closeTabSwitcher() {
        layoutTabSwitcher.visibility = View.GONE
    }

    private fun loadTargetUrl(input: String) {
        val tab = currentTab ?: return
        loadTargetUrlInTab(tab, input)
    }

    private fun loadTargetUrlInTab(tab: BrowserTab, input: String) {
        val trimmed = input.trim()
        val finalUrl = when {
            trimmed.isEmpty() || trimmed == "about:blank" -> "about:blank"
            trimmed.startsWith("file://", ignoreCase = true) -> trimmed
            trimmed.startsWith("/") -> "file://$trimmed"
            trimmed.startsWith("content://", ignoreCase = true) -> trimmed
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            trimmed.startsWith("localhost", ignoreCase = true) || trimmed.startsWith("127.0.0.1") || trimmed.startsWith("0.0.0.0") -> "http://$trimmed"
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
        }

        tab.url = finalUrl
        if (isTabActive(tab)) {
            layoutError.visibility = View.GONE
            etUrl.setText(if (finalUrl == "about:blank") "" else finalUrl)
            etUrl.clearFocus()
        }
        if (finalUrl != "about:blank") {
            tab.webView.loadUrl(finalUrl)
        }
    }

    private fun updateSslIndicator(url: String) {
        if (url.startsWith("https://", ignoreCase = true)) {
            ivSslIndicator.setImageResource(R.drawable.ic_shield_check)
            ivSslIndicator.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.accent_green)
            )
        } else if (url.startsWith("file://", ignoreCase = true) || url.startsWith("/")) {
            ivSslIndicator.setImageResource(R.drawable.ic_code)
            ivSslIndicator.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.accent_blue)
            )
        } else if (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("0.0.0.0")) {
            ivSslIndicator.setImageResource(R.drawable.ic_globe)
            ivSslIndicator.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.accent_yellow)
            )
        } else {
            ivSslIndicator.setImageResource(R.drawable.ic_globe)
            ivSslIndicator.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.accent_blue)
            )
        }
    }

    private fun showOverflowMenu(anchor: View) {
        val tab = currentTab
        val isDesktopMode = tab?.isDesktopMode ?: false

        MenuHelper.show(
            context = this,
            anchor = anchor,
            title = "Browser",
            items = listOf(
                MenuHelper.Item.Action(
                    icon = R.drawable.ic_refresh,
                    title = "Refresh"
                ) { tab?.webView?.reload() },

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_add_session,
                    title = "New Tab"
                ) { createNewTab(DEFAULT_HOME_URL, select = true) },

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_history,
                    title = "History"
                ) { showHistoryBottomSheet() },

                MenuHelper.Item.Divider,

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_globe,
                    title = "Desktop Site",
                    badge = if (isDesktopMode) "✓" else null
                ) {
                    if (tab != null) {
                        tab.isDesktopMode = !tab.isDesktopMode
                        val settings = tab.webView.settings
                        if (tab.isDesktopMode) {
                            settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                        } else {
                            settings.userAgentString = defaultUserAgent
                        }
                        tab.webView.reload()
                    }
                },

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_code,
                    title = "Inspect Element (DevTools)"
                ) { toggleInspectElement(tab) },

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_library,
                    title = "Console Logs"
                ) { showConsoleLogsDialog() },

                MenuHelper.Item.Divider,

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_globe,
                    title = "Copy URL"
                ) {
                    val currentUrl = tab?.webView?.url ?: etUrl.text.toString()
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                    Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                },

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_globe,
                    title = "Open in External Browser"
                ) {
                    val currentUrl = tab?.webView?.url ?: etUrl.text.toString()
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl)))
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open external browser: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                },

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_settings,
                    title = "Clear Cache & Storage"
                ) {
                    tab?.webView?.clearCache(true)
                    tab?.webView?.clearHistory()
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    Toast.makeText(this, "Cache and cookies cleared", Toast.LENGTH_SHORT).show()
                    tab?.webView?.reload()
                },

                MenuHelper.Item.Divider,

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_close,
                    title = "Close Tab",
                    destructive = true
                ) { closeTab(activeTabIndex) },

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_arrow_back,
                    title = "Minimize to Terminal"
                ) { minimizeToTerminal() },

                MenuHelper.Item.Action(
                    icon = R.drawable.ic_close,
                    title = "Exit Browser",
                    destructive = true
                ) { finish() }
            )
        )
    }

    private var erudaScriptCache: String? = null

    private fun toggleInspectElement(tab: BrowserTab?) {
        val wv = tab?.webView ?: return
        try {
            wv.evaluateJavascript("typeof window.eruda !== 'undefined'") { result ->
                if (result == "true") {
                    val toggleJs = """
                        (function() {
                            if (window._erudaActive) {
                                try { eruda.hide(); } catch(e) {}
                                window._erudaActive = false;
                            } else {
                                try { eruda.show(); eruda.show('elements'); } catch(e) {}
                                window._erudaActive = true;
                            }
                        })();
                    """.trimIndent()
                    wv.evaluateJavascript(toggleJs, null)
                    Toast.makeText(this, "DevTools toggled", Toast.LENGTH_SHORT).show()
                } else {
                    if (erudaScriptCache == null) {
                        erudaScriptCache = assets.open("scripts/eruda.min.js").bufferedReader().use { it.readText() }
                    }
                    erudaScriptCache?.let { script ->
                        wv.evaluateJavascript(script) {
                            val initJs = """
                                (function() {
                                    try {
                                        eruda.init();
                                        eruda.show('elements');
                                        window._erudaActive = true;
                                    } catch(e) {
                                        console.error('Eruda init error', e);
                                    }
                                })();
                            """.trimIndent()
                            wv.evaluateJavascript(initJs, null)
                            Toast.makeText(this, "Inspect Element (DevTools) active", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load DevTools: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showHistoryBottomSheet() {
        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_browser_history, null)
        dialog.setContentView(view)

        val rvHistory = view.findViewById<RecyclerView>(R.id.rv_history)
        val tvEmpty = view.findViewById<TextView>(R.id.tv_empty_history)
        val etSearch = view.findViewById<EditText>(R.id.et_search_history)
        val btnClearAll = view.findViewById<MaterialButton>(R.id.btn_clear_all_history)

        rvHistory.layoutManager = LinearLayoutManager(this)
        val historyList = mutableListOf<BrowserHistoryItem>()
        val historyAdapter = HistoryAdapter(
            historyList,
            onItemClicked = { item ->
                loadTargetUrl(item.url)
                dialog.dismiss()
            },
            onItemDeleted = { item ->
                historyDb.deleteItem(item.id)
                historyList.remove(item)
                rvHistory.adapter?.notifyDataSetChanged()
                tvEmpty.visibility = if (historyList.isEmpty()) View.VISIBLE else View.GONE
            }
        )
        rvHistory.adapter = historyAdapter

        fun refreshList(query: String? = null) {
            historyList.clear()
            historyList.addAll(historyDb.getHistory(query))
            historyAdapter.notifyDataSetChanged()
            tvEmpty.visibility = if (historyList.isEmpty()) View.VISIBLE else View.GONE
        }

        refreshList()

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                refreshList(s?.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        btnClearAll.setOnClickListener {
            CustomDialog.Builder(this)
                .setIcon(R.drawable.ic_trash, ContextCompat.getColor(this, R.color.accent_red))
                .setTitle("Clear Browsing History")
                .setMessage("Are you sure you want to clear all browsing history? This cannot be undone.")
                .setPositiveButton("Clear All", destructive = true) {
                    historyDb.clearAllHistory()
                    refreshList()
                    Toast.makeText(this, "Browsing history cleared", Toast.LENGTH_SHORT).show()
                }
                .setNeutralButton("Cancel")
                .show()
        }

        dialog.show()
    }

    private fun showConsoleLogsDialog() {
        val logsText = if (consoleLogs.isEmpty()) {
            "No JavaScript console messages logged yet."
        } else {
            consoleLogs.joinToString("\n\n")
        }

        val tv = TextView(this).apply {
            text = logsText
            textSize = 12f
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(16))
            setTextIsSelectable(true)
            typeface = android.graphics.Typeface.MONOSPACE
            setTextColor(androidx.core.content.ContextCompat.getColor(context, R.color.text_primary))
        }

        val sv = ScrollView(this).apply {
            addView(tv)
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(280))
        }

        CustomDialog.Builder(this)
            .setIcon(R.drawable.ic_terminal_small, ContextCompat.getColor(this, R.color.accent_blue))
            .setTitle("Browser Console Logs")
            .setView(sv)
            .setPositiveButton("Close")
            .setNegativeButton("Clear", destructive = true) {
                consoleLogs.clear()
            }
            .show()
    }

    private fun minimizeToTerminal() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
        Toast.makeText(this, "Browser running in background", Toast.LENGTH_SHORT).show()
    }

    private fun showExitConfirmationDialog() {
        CustomDialog.Builder(this)
            .setIcon(R.drawable.ic_globe, ContextCompat.getColor(this, R.color.accent_blue))
            .setTitle("Browser Navigation")
            .setMessage("Do you want to minimize the browser (keep tabs running in background) or exit completely?")
            .setNeutralButton("Cancel")
            .setNegativeButton("Exit Browser", destructive = true) {
                finish()
            }
            .setPositiveButton("Minimize") {
                minimizeToTerminal()
            }
            .show()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(etUrl.windowToken, 0)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        for (tab in tabs) {
            try {
                tab.webView.stopLoading()
                tab.webView.destroy()
            } catch (ignored: Exception) {}
        }
        tabs.clear()
        super.onDestroy()
    }

    // Tab Switcher Grid Adapter
    inner class TabGridAdapter(
        private val onTabSelected: (Int) -> Unit,
        private val onTabClosed: (Int) -> Unit
    ) : RecyclerView.Adapter<TabGridAdapter.TabViewHolder>() {

        inner class TabViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val container: LinearLayout = itemView.findViewById(R.id.card_tab_container)
            val tvTitle: TextView = itemView.findViewById(R.id.tv_tab_title)
            val tvUrl: TextView = itemView.findViewById(R.id.tv_tab_url)
            val btnCloseTab: ImageButton = itemView.findViewById(R.id.btn_close_tab)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_browser_tab, parent, false)
            return TabViewHolder(v)
        }

        override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
            val tab = tabs[position]
            holder.tvTitle.text = tab.title.ifBlank { "New Tab" }
            holder.tvUrl.text = tab.url

            val isActive = position == activeTabIndex
            holder.container.setBackgroundResource(
                if (isActive) R.drawable.bg_tab_card_active else R.drawable.bg_tab_card_inactive
            )

            holder.container.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTabSelected(pos)
                }
            }

            holder.btnCloseTab.setOnClickListener {
                val pos = holder.adapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onTabClosed(pos)
                }
            }
        }

        override fun getItemCount(): Int = tabs.size
    }

    // History List Adapter
    inner class HistoryAdapter(
        private val items: List<BrowserHistoryItem>,
        private val onItemClicked: (BrowserHistoryItem) -> Unit,
        private val onItemDeleted: (BrowserHistoryItem) -> Unit
    ) : RecyclerView.Adapter<HistoryAdapter.HistoryViewHolder>() {

        inner class HistoryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvTitle: TextView = itemView.findViewById(R.id.tv_history_title)
            val tvUrl: TextView = itemView.findViewById(R.id.tv_history_url)
            val tvTime: TextView = itemView.findViewById(R.id.tv_history_time)
            val btnDelete: ImageButton = itemView.findViewById(R.id.btn_delete_history_item)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_browser_history, parent, false)
            return HistoryViewHolder(v)
        }

        override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
            val item = items[position]
            holder.tvTitle.text = item.title
            holder.tvUrl.text = item.url
            holder.tvTime.text = DateUtils.getRelativeTimeSpanString(
                item.timestamp,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
            )

            holder.itemView.setOnClickListener {
                onItemClicked(item)
            }

            holder.btnDelete.setOnClickListener {
                onItemDeleted(item)
            }
        }

        override fun getItemCount(): Int = items.size
    }

    inner class BrowserClipboardBridge(private val context: Context) {
        private var memoryClipboard: String = ""

        @JavascriptInterface
        fun copyText(text: String?) {
            if (text.isNullOrEmpty()) return
            memoryClipboard = text
            runOnUiThread {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("MobileLinux Code", text)
                    clipboard.setPrimaryClip(clip)
                } catch (e: Exception) {
                    Log.e("DevBrowser", "Failed to copy text to system clipboard", e)
                }
            }
        }

        @JavascriptInterface
        fun pasteText(): String {
            var result = memoryClipboard
            val latch = java.util.concurrent.CountDownLatch(1)
            runOnUiThread {
                try {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = clipboard.primaryClip
                    if (clip != null && clip.itemCount > 0) {
                        val sys = clip.getItemAt(0).coerceToText(context)?.toString() ?: ""
                        if (sys.isNotEmpty()) {
                            result = sys
                            memoryClipboard = sys
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DevBrowser", "Failed to paste text from system clipboard on UI thread", e)
                } finally {
                    latch.countDown()
                }
            }
            try {
                latch.await(600, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (ignored: Exception) {}
            return result
        }
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val DEFAULT_HOME_URL = "https://www.google.com"

        fun openUrl(context: Context, url: String? = null) {
            val intent = Intent(context, DevBrowserActivity::class.java).apply {
                if (!url.isNullOrEmpty()) {
                    putExtra(EXTRA_URL, url)
                }
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or (if (context !is Activity) Intent.FLAG_ACTIVITY_NEW_TASK else 0)
            }
            context.startActivity(intent)
        }
    }
}
