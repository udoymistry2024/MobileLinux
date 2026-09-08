package com.mobilelinux.ui

import android.annotation.SuppressLint
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
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.*
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.PopupMenu
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mobilelinux.R
import java.net.URLEncoder
import java.util.Collections

class DevBrowserActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var etUrl: EditText
    private lateinit var btnClearUrl: ImageButton
    private lateinit var btnClose: ImageButton
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnMenu: ImageButton
    private lateinit var progressBar: ProgressBar
    private lateinit var ivSslIndicator: ImageView
    private lateinit var layoutError: LinearLayout
    private lateinit var tvErrorDesc: TextView
    private lateinit var btnRetry: Button
    private lateinit var topBar: LinearLayout

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var isDesktopMode = false
    private var defaultUserAgent: String = ""
    private val consoleLogs = Collections.synchronizedList(mutableListOf<String>())

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

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dev_browser)

        initViews()
        initWindowInsets()
        initWebView()
        initAddressBar()
        initQuickChips()
        initBackNavigation()

        val initialUrl = intent.getStringExtra(EXTRA_URL) ?: DEFAULT_HOME_URL
        loadTargetUrl(initialUrl)
    }

    private fun initViews() {
        webView = findViewById(R.id.dev_web_view)
        etUrl = findViewById(R.id.et_browser_url)
        btnClearUrl = findViewById(R.id.btn_clear_url)
        btnClose = findViewById(R.id.btn_browser_close)
        btnRefresh = findViewById(R.id.btn_browser_refresh)
        btnMenu = findViewById(R.id.btn_browser_menu)
        progressBar = findViewById(R.id.pb_browser)
        ivSslIndicator = findViewById(R.id.iv_ssl_indicator)
        layoutError = findViewById(R.id.layout_browser_error)
        tvErrorDesc = findViewById(R.id.tv_error_desc)
        btnRetry = findViewById(R.id.btn_retry_load)
        topBar = findViewById(R.id.browser_top_bar)

        btnClose.setOnClickListener {
            finish()
        }

        btnRefresh.setOnClickListener {
            if (progressBar.visibility == View.VISIBLE) {
                webView.stopLoading()
            } else {
                webView.reload()
            }
        }

        btnMenu.setOnClickListener { v ->
            showOverflowMenu(v)
        }

        btnRetry.setOnClickListener {
            layoutError.visibility = View.GONE
            webView.reload()
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

        val rootLayout: View = findViewById(R.id.layout_dev_browser_root)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { _, insets ->
            val navInsets = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            webView.setPadding(0, 0, 0, navInsets.bottom)
            insets
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        val settings = webView.settings
        defaultUserAgent = settings.userAgentString

        // Core modern web capabilities
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true

        // Viewport & Scaling
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        // Developer-friendly relaxed security for local development
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.mediaPlaybackRequiresUserGesture = false
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.setSupportMultipleWindows(true)

        // Web Client
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                progressBar.visibility = View.VISIBLE
                btnRefresh.setImageResource(R.drawable.ic_close)
                layoutError.visibility = View.GONE

                url?.let {
                    if (!etUrl.isFocused) {
                        etUrl.setText(it)
                    }
                    updateSslIndicator(it)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                progressBar.visibility = View.GONE
                btnRefresh.setImageResource(R.drawable.ic_refresh)

                url?.let {
                    if (!etUrl.isFocused) {
                        etUrl.setText(it)
                    }
                    updateSslIndicator(it)
                }
            }

            @SuppressLint("WebViewClientOnReceivedSslError")
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                val url = error?.url ?: ""
                // Auto-permit self-signed SSL for local development (localhost, 127.0.0.1, internal IP)
                if (url.contains("localhost") || url.contains("127.0.0.1") || url.contains("0.0.0.0") ||
                    url.startsWith("https://192.168.") || url.startsWith("https://10.")
                ) {
                    handler?.proceed()
                } else {
                    // For public websites with SSL issues, ask user
                    MaterialAlertDialogBuilder(this@DevBrowserActivity)
                        .setTitle("SSL Certificate Warning")
                        .setMessage("The certificate for '${url.take(50)}' is not trusted.\n\nDo you want to proceed anyway?")
                        .setPositiveButton("Proceed (Unsafe)") { _, _ -> handler?.proceed() }
                        .setNegativeButton("Cancel") { _, _ -> handler?.cancel() }
                        .show()
                }
            }

            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                super.onReceivedError(view, request, error)
                if (request?.isForMainFrame == true) {
                    val failingUrl = request.url.toString()
                    progressBar.visibility = View.GONE
                    btnRefresh.setImageResource(R.drawable.ic_refresh)

                    // Show friendly dev error overlay
                    layoutError.visibility = View.VISIBLE
                    tvErrorDesc.text = "Could not connect to:\n$failingUrl\n\nEnsure your local server (e.g. Jupyter, Node.js, Flask) is actively running in the terminal."
                }
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false

                if (scheme == "http" || scheme == "https") {
                    return false // Load in WebView
                }

                // Handle external app schemes if applicable
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
                progressBar.progress = newProgress
                if (newProgress >= 100) {
                    progressBar.visibility = View.GONE
                    btnRefresh.setImageResource(R.drawable.ic_refresh)
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                if (!etUrl.isFocused && !title.isNullOrEmpty() && !title.startsWith("http")) {
                    etUrl.hint = title
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

            // File chooser for uploads (Jupyter file uploads, file pickers)
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

        // File download handling (Exported notebooks, code, datasets)
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

    private fun initBackNavigation() {
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) {
                    webView.goBack()
                } else {
                    finish()
                }
            }
        })
    }

    private fun loadTargetUrl(input: String) {
        val trimmed = input.trim()
        val finalUrl = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("localhost") || trimmed.startsWith("127.0.0.1") || trimmed.startsWith("0.0.0.0") -> "http://$trimmed"
            trimmed.contains(".") && !trimmed.contains(" ") -> "https://$trimmed"
            else -> "https://www.google.com/search?q=" + URLEncoder.encode(trimmed, "UTF-8")
        }

        layoutError.visibility = View.GONE
        etUrl.setText(finalUrl)
        etUrl.clearFocus()
        webView.loadUrl(finalUrl)
    }

    private fun updateSslIndicator(url: String) {
        if (url.startsWith("https://")) {
            ivSslIndicator.setImageResource(R.drawable.ic_shield_check)
            ivSslIndicator.imageTintList = android.content.res.ColorStateList.valueOf(
                androidx.core.content.ContextCompat.getColor(this, R.color.accent_green)
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
        val popup = PopupMenu(this, anchor)
        popup.menuInflater.inflate(R.menu.menu_dev_browser, popup.menu)

        popup.menu.findItem(R.id.menu_desktop_mode)?.isChecked = isDesktopMode

        popup.setOnMenuItemClickListener { item: MenuItem ->
            when (item.itemId) {
                R.id.menu_desktop_mode -> {
                    isDesktopMode = !isDesktopMode
                    item.isChecked = isDesktopMode
                    val settings = webView.settings
                    if (isDesktopMode) {
                        settings.userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"
                    } else {
                        settings.userAgentString = defaultUserAgent
                    }
                    webView.reload()
                    true
                }
                R.id.menu_view_console -> {
                    showConsoleLogsDialog()
                    true
                }
                R.id.menu_copy_url -> {
                    val currentUrl = webView.url ?: etUrl.text.toString()
                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("URL", currentUrl))
                    Toast.makeText(this, "URL copied to clipboard", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_open_external -> {
                    val currentUrl = webView.url ?: etUrl.text.toString()
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                        startActivity(intent)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Could not open external browser: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                    true
                }
                R.id.menu_clear_cache -> {
                    webView.clearCache(true)
                    webView.clearHistory()
                    WebStorage.getInstance().deleteAllData()
                    CookieManager.getInstance().removeAllCookies(null)
                    Toast.makeText(this, "Cache and cookies cleared", Toast.LENGTH_SHORT).show()
                    webView.reload()
                    true
                }
                else -> false
            }
        }
        popup.show()
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
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Browser Console Logs")
            .setView(sv)
            .setPositiveButton("Close", null)
            .setNegativeButton("Clear") { _, _ ->
                consoleLogs.clear()
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
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "extra_url"
        const val DEFAULT_HOME_URL = "https://www.google.com"

        fun openUrl(context: Context, url: String) {
            val intent = Intent(context, DevBrowserActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            context.startActivity(intent)
        }
    }
}
