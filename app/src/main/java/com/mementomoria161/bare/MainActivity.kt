package com.mementomoria161.bare

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import java.net.URI
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {

    // Tab data structure
    data class Tab(
        val webView: WebView,
        var title: String = "New Tab",
        var url: String = "about:blank",
        var isLoading: Boolean = false,
        var thumbnail: Bitmap? = null
    )

    private val tabList = mutableListOf<Tab>()
    private var activeTabIndex = -1

    // View references
    private lateinit var webViewContainer: FrameLayout
    private lateinit var addressInput: EditText
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnTabOverview: FrameLayout
    private lateinit var tvTabCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout

    // SharedPreferences for settings
    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        // Enable modern Edge-to-Edge rendering
        enableEdgeToEdge()
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("BarePrefs", Context.MODE_PRIVATE)

        // Initialize view references
        webViewContainer = findViewById(R.id.webViewContainer)
        addressInput = findViewById(R.id.addressInput)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSettings = findViewById(R.id.btnSettings)
        btnTabOverview = findViewById(R.id.btnTabOverview)
        tvTabCount = findViewById(R.id.tvTabCount)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)

        setupEdgeToEdgeInsets()
        setupAddressBarBehavior()
        setupActionButtons()
        setupBackNavigation()

        // Create the initial blank tab
        addNewTab("about:blank")
    }

    private fun setupEdgeToEdgeInsets() {
        val bottomBarCard: View = findViewById(R.id.bottomBarCard)
        
        // Dynamically apply window insets to offset the floating bottom bar and WebView padding
        ViewCompat.setOnApplyWindowInsetsListener(bottomBarCard) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            
            // Adjust floating bottom bar margins dynamically so it hovers above the gesture bar
            val params = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.bottomMargin = systemBars.bottom + dpToPx(this, 8)
            params.leftMargin = systemBars.left + dpToPx(this, 12)
            params.rightMargin = systemBars.right + dpToPx(this, 12)
            view.layoutParams = params
            
            // Push WebView content up to clear the status bar and bottom floating bar
            val totalBottomPadding = dpToPx(this, 56 + 8) + systemBars.bottom
            webViewContainer.setPadding(0, systemBars.top, 0, totalBottomPadding)
            
            insets
        }
    }

    private fun setupAddressBarBehavior() {
        // Format simplified URL when address bar focus shifts
        addressInput.setOnFocusChangeListener { _, hasFocus ->
            if (activeTabIndex in tabList.indices) {
                val activeTab = tabList[activeTabIndex]
                if (hasFocus) {
                    // Show full URL and select all text for editing (or empty if start page)
                    if (activeTab.url == "about:blank") {
                        addressInput.setText("")
                    } else {
                        addressInput.setText(activeTab.webView.url ?: "")
                        addressInput.selectAll()
                    }
                    btnRefresh.setImageResource(R.drawable.ic_close) // Clear text
                } else {
                    // Show truncated URL when out of focus
                    if (activeTab.url == "about:blank") {
                        addressInput.setText("")
                    } else {
                        addressInput.setText(getSimplifiedUrl(activeTab.webView.url ?: ""))
                    }
                    updateRefreshIconState()
                }
            }
        }

        // Action when pressing "Go" in soft keyboard
        addressInput.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadEnteredAddress()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun setupActionButtons() {
        // Swipe to Refresh
        swipeRefresh.setOnRefreshListener {
            if (activeTabIndex in tabList.indices) {
                tabList[activeTabIndex].webView.reload()
            } else {
                swipeRefresh.isRefreshing = false
            }
        }

        // Address Bar button: refreshes or stops loading, or clears text when typing
        btnRefresh.setOnClickListener {
            if (addressInput.isFocused) {
                addressInput.setText("")
            } else if (activeTabIndex in tabList.indices) {
                val activeTab = tabList[activeTabIndex]
                if (activeTab.isLoading) {
                    activeTab.webView.stopLoading()
                } else {
                    activeTab.webView.reload()
                }
            }
        }

        // Tab Overview Fullscreen Dialog
        btnTabOverview.setOnClickListener {
            showTabsOverviewDialog()
        }

        // Settings Sheet
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    private fun setupBackNavigation() {
        // Modern back button navigation using OnBackPressedDispatcher
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (activeTabIndex in tabList.indices) {
                    val activeWebView = tabList[activeTabIndex].webView
                    if (activeWebView.canGoBack()) {
                        activeWebView.goBack()
                    } else {
                        // If we can't go back, exit the app
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                }
            }
        })
    }

    private fun loadEnteredAddress() {
        if (activeTabIndex !in tabList.indices) return
        val activeWebView = tabList[activeTabIndex].webView

        val input = addressInput.text.toString().trim()
        if (input.isEmpty()) {
            loadStartPage(activeTab = tabList[activeTabIndex])
            hideKeyboard()
            addressInput.clearFocus()
            return
        }

        val hasSpaces = input.contains(" ")
        val isUrl = !hasSpaces && (android.util.Patterns.WEB_URL.matcher(input).matches() || input.contains("."))

        val destinationUrl = if (isUrl) {
            if (!input.startsWith("http://") && !input.startsWith("https://") && !input.startsWith("file://") && !input.startsWith("about:")) {
                "https://$input"
            } else {
                input
            }
        } else {
            val encodedQuery = URLEncoder.encode(input, "UTF-8")
            getSearchEnginePrefix() + encodedQuery
        }

        activeWebView.loadUrl(destinationUrl)
        hideKeyboard()
        addressInput.clearFocus()
    }

    private fun loadStartPage(activeTab: Tab) {
        activeTab.url = "about:blank"
        activeTab.title = "New Tab"
        activeTab.webView.loadDataWithBaseURL("file:///android_asset/", getStartPageHtml(), "text/html", "UTF-8", null)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createNewWebView(): WebView {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        // Web settings configuration (JavaScript enabled by default, setting removed)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        applyDesktopModeSetting(webView, sharedPreferences.getBoolean("desktop_mode", false))

        // WebView Client for URL overriding and load listener states
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                // Handle external apps mapping (e.g. mailto, tel, intents)
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    startActivity(intent)
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_error_app_link), Toast.LENGTH_SHORT).show()
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                val tabIndex = findTabIndexForWebView(view)
                if (tabIndex != -1) {
                    val tab = tabList[tabIndex]
                    tab.isLoading = true
                    tab.url = url ?: ""
                    
                    if (tabIndex == activeTabIndex) {
                        progressBar.visibility = View.VISIBLE
                        progressBar.progress = 0
                        
                        if (!addressInput.isFocused) {
                            if (tab.url == "about:blank") {
                                addressInput.setText("")
                            } else {
                                addressInput.setText(url ?: "")
                            }
                        }
                        updateRefreshIconState()
                    }
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
                val tabIndex = findTabIndexForWebView(view)
                if (tabIndex != -1) {
                    val tab = tabList[tabIndex]
                    tab.isLoading = false
                    tab.url = url ?: ""
                    
                    // Do not show local start page titles as "about:blank"
                    tab.title = if (tab.url == "about:blank") "New Tab" else (view?.title ?: "New Tab")

                    if (tabIndex == activeTabIndex) {
                        progressBar.visibility = View.GONE
                        swipeRefresh.isRefreshing = false
                        if (!addressInput.isFocused) {
                            if (tab.url == "about:blank") {
                                addressInput.setText("")
                            } else {
                                addressInput.setText(getSimplifiedUrl(url ?: ""))
                            }
                        }
                        updateRefreshIconState()
                    }

                    // Dynamically capture thumbnail of the webpage with a short delay for rendering
                    view?.postDelayed({
                        val idx = findTabIndexForWebView(view)
                        if (idx != -1) {
                            captureTabThumbnail(tabList[idx])
                        }
                    }, 500)
                }
            }
        }

        // WebChromeClient to track progress and document titles
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                super.onProgressChanged(view, newProgress)
                val tabIndex = findTabIndexForWebView(view)
                if (tabIndex == activeTabIndex) {
                    progressBar.progress = newProgress
                    if (newProgress >= 100) {
                        progressBar.visibility = View.GONE
                        swipeRefresh.isRefreshing = false
                    }
                }
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                val tabIndex = findTabIndexForWebView(view)
                if (tabIndex != -1) {
                    val tab = tabList[tabIndex]
                    tab.title = if (tab.url == "about:blank") "New Tab" else (title ?: "New Tab")
                }
            }
        }

        return webView
    }

    private fun findTabIndexForWebView(view: WebView?): Int {
        if (view == null) return -1
        return tabList.indexOfFirst { it.webView == view }
    }

    // Capture low-resource 20%-scaled screenshot of a WebView to show as tab thumbnail
    private fun captureTabThumbnail(tab: Tab) {
        val webView = tab.webView
        if (webView.width <= 0 || webView.height <= 0) return
        try {
            val scale = 0.2f
            val w = (webView.width * scale).toInt()
            val h = (webView.height * scale).toInt()
            if (w <= 0 || h <= 0) return
            
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            canvas.scale(scale, scale)
            webView.draw(canvas)
            tab.thumbnail = bitmap
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Tab Management
    private fun addNewTab(url: String = "about:blank") {
        val webView = createNewWebView()
        val tab = Tab(webView = webView, url = url)
        tabList.add(tab)
        webViewContainer.addView(webView)
        
        if (url == "about:blank") {
            loadStartPage(tab)
        } else {
            webView.loadUrl(url)
        }
        selectTab(tabList.lastIndex)
    }

    private fun selectTab(index: Int) {
        if (index !in tabList.indices) return

        // Capture thumbnail of deactivated tab
        if (activeTabIndex in tabList.indices) {
            captureTabThumbnail(tabList[activeTabIndex])
            tabList[activeTabIndex].webView.visibility = View.GONE
        }

        activeTabIndex = index
        val activeTab = tabList[activeTabIndex]
        
        // Show selected tab
        activeTab.webView.visibility = View.VISIBLE
        activeTab.webView.requestFocus()

        // Update address bar text and page loading progress bar
        if (!addressInput.isFocused) {
            if (activeTab.url == "about:blank") {
                addressInput.setText("")
            } else {
                addressInput.setText(
                    if (activeTab.isLoading) activeTab.webView.url ?: "" 
                    else getSimplifiedUrl(activeTab.webView.url ?: "")
                )
            }
        }
        progressBar.visibility = if (activeTab.isLoading) View.VISIBLE else View.GONE
        updateRefreshIconState()
        updateTabButtonCount()
    }

    private fun closeTab(index: Int) {
        if (index !in tabList.indices) return

        val tabToRemove = tabList[index]
        webViewContainer.removeView(tabToRemove.webView)
        tabToRemove.webView.destroy()
        tabList.removeAt(index)

        if (tabList.isEmpty()) {
            // Re-create a clean start page if all tabs were closed
            addNewTab("about:blank")
        } else {
            // Fix active index reference
            if (activeTabIndex >= tabList.size) {
                activeTabIndex = tabList.size - 1
            }
            if (index == activeTabIndex || activeTabIndex == -1) {
                selectTab(activeTabIndex)
            } else {
                // If closed background tab, simply update selector offset
                if (index < activeTabIndex) {
                    activeTabIndex--
                }
                updateTabButtonCount()
            }
        }
    }

    private fun updateTabButtonCount() {
        tvTabCount.text = tabList.size.toString()
    }

    private fun updateRefreshIconState() {
        if (activeTabIndex in tabList.indices) {
            val activeTab = tabList[activeTabIndex]
            if (activeTab.isLoading) {
                btnRefresh.setImageResource(R.drawable.ic_close)
            } else {
                btnRefresh.setImageResource(R.drawable.ic_refresh)
            }
        }
    }

    private fun applyDesktopModeSetting(webView: WebView, enable: Boolean) {
        if (enable) {
            val desktopUA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            webView.settings.userAgentString = desktopUA
        } else {
            webView.settings.userAgentString = null
        }
    }

    private fun getSearchEnginePrefix(): String {
        return when (sharedPreferences.getString("search_engine", "google")) {
            "google" -> getString(R.string.search_prefix_google)
            "duckduckgo" -> getString(R.string.search_prefix_duckduckgo)
            "bing" -> getString(R.string.search_prefix_bing)
            "ecosia" -> getString(R.string.search_prefix_ecosia)
            else -> getString(R.string.search_prefix_google)
        }
    }

    // Fullscreen dialog for Tab Overview
    private fun showTabsOverviewDialog() {
        // Capture active tab screenshot before opening overview
        if (activeTabIndex in tabList.indices) {
            captureTabThumbnail(tabList[activeTabIndex])
        }

        val dialog = Dialog(this, R.style.Theme_Bare_FullscreenDialog)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_tabs, null)
        dialog.setContentView(view)

        // Set layout constraints to match parent width/height
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val dialogRoot = view.findViewById<View>(R.id.dialogRoot)
        val btnCloseDialog = view.findViewById<ImageButton>(R.id.btnCloseDialog)
        val btnAddTab = view.findViewById<ImageButton>(R.id.btnAddTab)
        val rvTabs = view.findViewById<RecyclerView>(R.id.rvTabs)

        // Apply edge-to-edge window padding so dialogue content respects status bar/notch
        ViewCompat.setOnApplyWindowInsetsListener(dialogRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        rvTabs.layoutManager = LinearLayoutManager(this)
        
        val adapter = TabAdapter(
            tabs = tabList,
            activeTabIndex = activeTabIndex,
            onTabSelected = { selectedIndex ->
                selectTab(selectedIndex)
                dialog.dismiss()
            },
            onTabClosed = { closedIndex ->
                closeTab(closedIndex)
                rvTabs.adapter?.notifyDataSetChanged()
                if (tabList.size == 1 && closedIndex == 0) {
                    dialog.dismiss()
                }
            }
        )
        rvTabs.adapter = adapter

        btnCloseDialog.setOnClickListener {
            dialog.dismiss()
        }

        btnAddTab.setOnClickListener {
            addNewTab("about:blank")
            dialog.dismiss()
        }

        dialog.show()
    }

    // Settings Bottom Sheet dialog (JavaScript toggle removed, Search Engine radio group added)
    private fun showSettingsDialog() {
        val bottomSheet = BottomSheetDialog(this)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_settings, null)
        bottomSheet.setContentView(view)

        val switchDesktop = view.findViewById<MaterialSwitch>(R.id.switchDesktopMode)
        val rgSearchEngine = view.findViewById<RadioGroup>(R.id.rgSearchEngine)
        val btnClearData = view.findViewById<Button>(R.id.btnClearData)
        val btnAbout = view.findViewById<Button>(R.id.btnAbout)

        // Bind switch state
        switchDesktop.isChecked = sharedPreferences.getBoolean("desktop_mode", false)

        // Bind active search engine check
        when (sharedPreferences.getString("search_engine", "google")) {
            "google" -> rgSearchEngine.check(R.id.rbGoogle)
            "duckduckgo" -> rgSearchEngine.check(R.id.rbDuckDuckGo)
            "bing" -> rgSearchEngine.check(R.id.rbBing)
            "ecosia" -> rgSearchEngine.check(R.id.rbEcosia)
        }

        // Save switch settings
        switchDesktop.setOnCheckedChangeListener { _, isChecked ->
            sharedPreferences.edit().putBoolean("desktop_mode", isChecked).apply()
            // Apply immediately to all webviews
            for (tab in tabList) {
                applyDesktopModeSetting(tab.webView, isChecked)
                tab.webView.reload()
            }
        }

        // Save Search Engine selection
        rgSearchEngine.setOnCheckedChangeListener { _, checkedId ->
            val engine = when (checkedId) {
                R.id.rbGoogle -> "google"
                R.id.rbDuckDuckGo -> "duckduckgo"
                R.id.rbBing -> "bing"
                R.id.rbEcosia -> "ecosia"
                else -> "google"
            }
            sharedPreferences.edit().putString("search_engine", engine).apply()
        }

        btnClearData.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.clear_data_confirm_title))
                .setMessage(getString(R.string.clear_data_confirm_message))
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .setPositiveButton(getString(R.string.btn_clear)) { _, _ ->
                    // Clear cache
                    if (activeTabIndex in tabList.indices) {
                        tabList[activeTabIndex].webView.clearCache(true)
                    }
                    // Clear history
                    if (activeTabIndex in tabList.indices) {
                        tabList[activeTabIndex].webView.clearHistory()
                    }
                    // Clear cookies
                    CookieManager.getInstance().removeAllCookies(null)
                    CookieManager.getInstance().flush()
                    // Clear storage
                    WebStorage.getInstance().deleteAllData()
                    
                    Toast.makeText(this, getString(R.string.clear_data_success), Toast.LENGTH_SHORT).show()
                    bottomSheet.dismiss()
                }
                .show()
        }

        btnAbout.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(getString(R.string.about_bare))
                .setMessage(getString(R.string.about_message))
                .setPositiveButton(getString(R.string.btn_ok), null)
                .show()
        }

        bottomSheet.show()
    }

    // Helper functions
    private fun getSimplifiedUrl(urlString: String): String {
        if (urlString.isEmpty() || urlString == "about:blank") return ""
        try {
            val uri = URI(urlString)
            var host = uri.host ?: return urlString
            if (host.startsWith("www.")) {
                host = host.substring(4)
            }
            return host
        } catch (e: Exception) {
            var result = urlString
            if (result.contains("://")) {
                result = result.substring(result.indexOf("://") + 3)
            }
            if (result.startsWith("www.")) {
                result = result.substring(4)
            }
            val slashIndex = result.indexOf('/')
            if (slashIndex != -1) {
                result = result.substring(0, slashIndex)
            }
            return result
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        val density = context.resources.displayMetrics.density
        return (dp * density).toInt()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(addressInput.windowToken, 0)
    }

    // Dynamically generate minimalist offline HTML start page
    private fun getStartPageHtml(): String {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        val bgColor = if (isDark) "#0B0F19" else "#F9FAFB"
        val textColor = if (isDark) "#F9FAFB" else "#111827"
        val primaryColor = if (isDark) "#6366F1" else "#4F46E5"
        val secondaryColor = if (isDark) "#9CA3AF" else "#6B7280"

        return """
            <!DOCTYPE html>
            <html>
            <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
            <style>
              body {
                background-color: $bgColor;
                color: $textColor;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: center;
                height: 100vh;
                margin: 0;
                user-select: none;
              }
              .logo {
                font-size: 56px;
                font-weight: 800;
                letter-spacing: -2px;
                color: $primaryColor;
                margin-bottom: 8px;
                animation: fadeIn 0.8s ease-out;
              }
              .subtitle {
                font-size: 14px;
                color: $secondaryColor;
                font-weight: 400;
                animation: fadeIn 1s ease-out;
              }
              @keyframes fadeIn {
                from { opacity: 0; transform: translateY(10px); }
                to { opacity: 1; transform: translateY(0); }
              }
            </style>
            </head>
            <body>
              <div class="logo">Bare</div>
              <div class="subtitle">Search or enter URL below</div>
            </body>
            </html>
        """.trimIndent()
    }
}
