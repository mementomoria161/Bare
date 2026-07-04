package com.mementomoria161.bare

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
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
import android.widget.LinearLayout
import android.widget.ProgressBar
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
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.DynamicColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
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
    private lateinit var addressBarWrapper: FrameLayout
    private lateinit var addressInput: EditText
    private lateinit var btnRefresh: ImageButton
    private lateinit var btnSettings: ImageButton
    private lateinit var btnTabOverview: FrameLayout
    private lateinit var tvTabCount: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var bottomBarCard: View
    private lateinit var btnNewTabMain: ImageButton

    // Unprocess settings views
    private lateinit var settingsPanel: LinearLayout
    private lateinit var settingsDimOverlay: View
    private lateinit var btnSettingSearchEngine: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingCustomSearch: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingDesktopSite: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingClearData: com.google.android.material.button.MaterialButton

    private var isSettingsOpen = false
    private var isAnimatingSettings = false
    private var currentStatusColor = android.graphics.Color.TRANSPARENT

    // SharedPreferences for settings
    private lateinit var sharedPreferences: SharedPreferences

    // Toolbar hiding state
    private var isBottomBarHidden = false

    override fun onCreate(savedInstanceState: Bundle?) {
        // Apply Material You dynamic color theme to activity if supported by user device
        DynamicColors.applyToActivityIfAvailable(this)
        
        // Enable modern Edge-to-Edge rendering
        enableEdgeToEdge()
        
        // Disable Android Q+ system navigation bar contrast protections to allow 100% transparent navigation chins
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        
        // Enforce transparent navigation bar at all times
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        sharedPreferences = getSharedPreferences("BarePrefs", Context.MODE_PRIVATE)

        // Initialize view references
        webViewContainer = findViewById(R.id.webViewContainer)
        addressBarWrapper = findViewById(R.id.addressBarWrapper)
        addressInput = findViewById(R.id.addressInput)
        btnRefresh = findViewById(R.id.btnRefresh)
        btnSettings = findViewById(R.id.btnSettings)
        btnTabOverview = findViewById(R.id.btnTabOverview)
        tvTabCount = findViewById(R.id.tvTabCount)
        progressBar = findViewById(R.id.progressBar)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        bottomBarCard = findViewById(R.id.bottomBarCard)
        btnNewTabMain = findViewById(R.id.btnNewTabMain)

        // Initialize settings overlay views
        settingsPanel = findViewById(R.id.settingsPanel)
        settingsDimOverlay = findViewById(R.id.settingsDimOverlay)
        btnSettingSearchEngine = findViewById(R.id.btnSettingSearchEngine)
        btnSettingCustomSearch = findViewById(R.id.btnSettingCustomSearch)
        btnSettingDesktopSite = findViewById(R.id.btnSettingDesktopSite)
        btnSettingClearData = findViewById(R.id.btnSettingClearData)

        setupEdgeToEdgeInsets()
        setupAddressBarBehavior()
        setupActionButtons()
        setupBackNavigation()

        // Create the initial blank tab
        addNewTab("about:blank")
    }

    private fun setupEdgeToEdgeInsets() {
        // Dynamically apply window insets to offset the floating bottom bar and WebView padding
        ViewCompat.setOnApplyWindowInsetsListener(bottomBarCard) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            
            // Adjust to whichever inset is larger (IME for keyboard, systemBars for gesture nav)
            val bottomInset = java.lang.Math.max(systemBars.bottom, ime.bottom)
            
            // Set margins to prevent clipping on curved screen corners
            val params = view.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
            params.bottomMargin = bottomInset + dpToPx(this, 24)
            params.leftMargin = systemBars.left + dpToPx(this, 16)
            params.rightMargin = systemBars.right + dpToPx(this, 16)
            view.layoutParams = params
            
            // Push WebView content up to clear status bar ONLY (bottom padding is 0 for fullscreen scrolling)
            webViewContainer.setPadding(0, systemBars.top, 0, 0)
            
            insets
        }
    }

    private fun setupAddressBarBehavior() {
        // Format simplified URL when address bar focus shifts
        addressInput.setOnFocusChangeListener { _, hasFocus ->
            if (activeTabIndex in tabList.indices) {
                val activeTab = tabList[activeTabIndex]
                val isBlank = activeTab.url == "about:blank" || activeTab.url.startsWith("file:///android_asset/")
                val currentText = if (hasFocus) {
                    if (isBlank) "" else activeTab.webView.url ?: ""
                } else {
                    if (isBlank) "" else getSimplifiedUrl(activeTab.webView.url ?: "")
                }
                
                addressInput.setText(currentText)
                if (hasFocus && currentText.isNotEmpty()) {
                    addressInput.selectAll()
                }
                
                // Clear button icon
                if (hasFocus) {
                    btnRefresh.setImageResource(R.drawable.ic_close)
                } else {
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
        // Swipe to Refresh (disabled by default, enabled dynamically only at scroll top)
        swipeRefresh.isEnabled = true
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

        // Dynamic New Tab button inside bottom capsule bar
        btnNewTabMain.setOnClickListener {
            addNewTab("about:blank")
        }

        // Tab Overview Fullscreen Dialog
        btnTabOverview.setOnClickListener {
            if (isSettingsOpen) toggleSettingsMenu()
            showTabsOverviewDialog()
        }

        // Unprocess settings pop-up triggers
        btnSettings.setOnClickListener {
            toggleSettingsMenu()
        }

        settingsDimOverlay.setOnClickListener {
            if (isSettingsOpen) toggleSettingsMenu()
        }

        btnSettingSearchEngine.setOnClickListener {
            cycleSearchEngineSetting()
        }

        btnSettingCustomSearch.setOnClickListener {
            showCustomSearchEngineDialog()
        }

        btnSettingDesktopSite.setOnClickListener {
            toggleDesktopSiteSetting()
        }

        btnSettingClearData.setOnClickListener {
            handleClearDataSetting()
        }
    }

    private fun setupBackNavigation() {
        // Modern back button navigation using OnBackPressedCallback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isSettingsOpen) {
                    toggleSettingsMenu()
                    return
                }
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

    // Scroll-to-Hide / Scroll-to-Show bottom toolbar animations
    private fun showBottomBar() {
        if (!isBottomBarHidden) return
        isBottomBarHidden = false
        bottomBarCard.animate()
            .translationY(0f)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .setDuration(250)
            .start()
    }

    private fun hideBottomBar() {
        if (isBottomBarHidden || isSettingsOpen) return // Never hide if settings pop-up is active
        isBottomBarHidden = true
        val params = bottomBarCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        val totalMargin = params.bottomMargin + bottomBarCard.height.toFloat() + dpToPx(this, 16).toFloat()
        bottomBarCard.animate()
            .translationY(totalMargin)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .setDuration(200)
            .start()
    }

    // Unprocess settings menu toggle & bottom-to-top pop-up overshoot animations
    private fun toggleSettingsMenu() {
        if (isAnimatingSettings) return
        isAnimatingSettings = true
        isSettingsOpen = !isSettingsOpen

        val toggles = listOf(
            btnSettingSearchEngine.parent as View, // Grouped Search Engine Row container
            btnSettingDesktopSite,
            btnSettingClearData
        )

        if (isSettingsOpen) {
            updateSettingsButtonsUI()

            // Dim the top status bar in sync with settings dim overlay
            window.statusBarColor = getDimmedColor(currentStatusColor)

            settingsDimOverlay.animate().cancel()
            toggles.forEach { it.animate().cancel() }

            settingsDimOverlay.visibility = View.VISIBLE
            settingsDimOverlay.alpha = 0f
            settingsDimOverlay.animate()
                .alpha(1f)
                .setDuration(250)
                .setListener(null)
                .start()

            settingsPanel.visibility = View.VISIBLE

            // Animate vertically from bottom to top (Y-axis instead of X-axis)
            val startTranslationY = dpToPx(this, 100).toFloat()
            val count = toggles.size
            toggles.forEachIndexed { index, button ->
                button.alpha = 0f
                button.scaleX = 0.3f
                button.scaleY = 0.3f
                button.translationX = 0f
                button.translationY = startTranslationY

                // Stagger delay from bottom to top: bottom item animates first
                button.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationX(0f)
                    .translationY(0f)
                    .setDuration(280)
                    .setStartDelay((count - 1 - index) * 60L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                    .setListener(if (index == 0) { // top item completes animation last when opening
                        object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                isAnimatingSettings = false
                            }
                        }
                    } else null)
                    .start()
            }
        } else {
            // Restore normal status bar color when settings dim overlay closes
            window.statusBarColor = if (currentStatusColor == android.graphics.Color.TRANSPARENT || currentStatusColor == (if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES) android.graphics.Color.parseColor("#1A1A1A") else android.graphics.Color.parseColor("#F9FAFB"))) android.graphics.Color.TRANSPARENT else currentStatusColor

            settingsDimOverlay.animate().cancel()
            toggles.forEach { it.animate().cancel() }

            settingsDimOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .setListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        settingsDimOverlay.visibility = View.GONE
                    }
                })
                .start()

            // Animate vertically collapsing downwards on close
            val targetTranslationY = dpToPx(this, 100).toFloat()
            toggles.forEachIndexed { index, button ->
                button.animate()
                    .alpha(0f)
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .translationY(targetTranslationY)
                    .setDuration(200)
                    .setStartDelay(index * 40L)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .setListener(if (index == toggles.lastIndex) { // bottom-most item completes animation last when closing
                        object : android.animation.AnimatorListenerAdapter() {
                            override fun onAnimationEnd(animation: android.animation.Animator) {
                                settingsPanel.visibility = View.GONE
                                isAnimatingSettings = false
                            }
                        }
                    } else null)
                    .start()
            }
        }
    }

    private fun updateSettingsButtonsUI() {
        // 1. Search Engine label and logo icon (formatted without a colon)
        val engine = sharedPreferences.getString("search_engine", "google") ?: "google"
        val engineLabel = if (engine == "custom") {
            sharedPreferences.getString("custom_search_name", "Custom") ?: "Custom"
        } else {
            engine.replaceFirstChar { it.uppercase() }
        }
        btnSettingSearchEngine.text = "Search Engine $engineLabel"
        
        val iconRes = when (engine) {
            "google" -> R.drawable.ic_logo_google
            "duckduckgo" -> R.drawable.ic_logo_duckduckgo
            "bing" -> R.drawable.ic_logo_bing
            "ecosia" -> R.drawable.ic_logo_ecosia
            else -> R.drawable.ic_add
        }
        btnSettingSearchEngine.setIconResource(iconRes)

        // 2. Desktop Mode color indicator formatting (matching Unprocess style)
        val isDesktop = sharedPreferences.getBoolean("desktop_mode", false)
        btnSettingDesktopSite.text = "Desktop View"
        
        val typedValue = TypedValue()
        if (isDesktop) {
            // Active state: filled with dynamic primary container colors
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
            btnSettingDesktopSite.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
            btnSettingDesktopSite.setTextColor(typedValue.data)
            btnSettingDesktopSite.iconTint = android.content.res.ColorStateList.valueOf(typedValue.data)
        } else {
            // Inactive state: surface variant tones
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
            btnSettingDesktopSite.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            btnSettingDesktopSite.setTextColor(typedValue.data)
            btnSettingDesktopSite.iconTint = android.content.res.ColorStateList.valueOf(typedValue.data)
        }
    }

    private fun cycleSearchEngineSetting() {
        val engines = listOf("google", "duckduckgo", "bing", "ecosia")
        val current = sharedPreferences.getString("search_engine", "google") ?: "google"
        
        // Only cycle default search engines. Custom search is toggled via the "+" button.
        val idx = engines.indexOf(current)
        val nextIdx = if (idx == -1) 0 else (idx + 1) % engines.size
        val nextEngine = engines[nextIdx]

        sharedPreferences.edit().putString("search_engine", nextEngine).apply()
        updateSettingsButtonsUI()
    }

    private fun toggleDesktopSiteSetting() {
        val isDesktop = sharedPreferences.getBoolean("desktop_mode", false)
        val nextState = !isDesktop
        sharedPreferences.edit().putBoolean("desktop_mode", nextState).apply()
        for (tab in tabList) {
            applyDesktopModeSetting(tab.webView, nextState)
            tab.webView.reload()
        }
        updateSettingsButtonsUI()
    }

    private fun handleClearDataSetting() {
        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.clear_data_confirm_title))
            .setMessage(getString(R.string.clear_data_confirm_message))
            .setNegativeButton(getString(R.string.btn_cancel), null)
            .setPositiveButton(getString(R.string.btn_clear)) { _, _ ->
                if (activeTabIndex in tabList.indices) {
                    tabList[activeTabIndex].webView.clearCache(true)
                    tabList[activeTabIndex].webView.clearHistory()
                }
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
                WebStorage.getInstance().deleteAllData()
                
                Toast.makeText(this, getString(R.string.clear_data_success), Toast.LENGTH_SHORT).show()
                if (isSettingsOpen) toggleSettingsMenu()
            }
            .show()
    }

    private fun updateNewTabButtonVisibility() {
        if (activeTabIndex in tabList.indices) {
            val activeTab = tabList[activeTabIndex]
            val isBlank = activeTab.url == "about:blank" || activeTab.url.startsWith("file:///android_asset/")
            btnNewTabMain.visibility = if (isBlank) View.GONE else View.VISIBLE
        } else {
            btnNewTabMain.visibility = View.GONE
        }
    }

    // Configure custom search engine query string dialog
    private fun showCustomSearchEngineDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val paddingVal = dpToPx(this@MainActivity, 16)
            setPadding(paddingVal, paddingVal, paddingVal, paddingVal)
        }
        
        val existingName = sharedPreferences.getString("custom_search_name", "")
        val existingPrefix = sharedPreferences.getString("custom_search_prefix", "")

        val etName = EditText(this).apply {
            hint = getString(R.string.custom_engine_name_hint)
            setText(existingName)
            setSingleLine(true)
        }
        val etUrl = EditText(this).apply {
            hint = getString(R.string.custom_engine_url_hint)
            setText(existingPrefix)
            setSingleLine(true)
            inputType = InputType.TYPE_TEXT_VARIATION_URI
        }
        
        layout.addView(etName)
        layout.addView(etUrl)
        
        val urlParams = etUrl.layoutParams as LinearLayout.LayoutParams
        urlParams.topMargin = dpToPx(this, 12)
        etUrl.layoutParams = urlParams

        MaterialAlertDialogBuilder(this)
            .setTitle(getString(R.string.custom_engine_dialog_title))
            .setView(layout)
            .setNegativeButton(getString(R.string.btn_cancel)) { _, _ ->
                if (!existingPrefix.isNullOrEmpty()) {
                    sharedPreferences.edit().putString("search_engine", "custom").apply()
                } else {
                    sharedPreferences.edit().putString("search_engine", "google").apply()
                }
                updateSettingsButtonsUI()
            }
            .setPositiveButton(getString(R.string.btn_save)) { _, _ ->
                val name = etName.text.toString().trim()
                val url = etUrl.text.toString().trim()
                if (name.isNotEmpty() && url.isNotEmpty() && (url.startsWith("http://") || url.startsWith("https://"))) {
                    sharedPreferences.edit().apply {
                        putString("search_engine", "custom")
                        putString("custom_search_name", name)
                        putString("custom_search_prefix", url)
                        apply()
                    }
                } else {
                    Toast.makeText(this, getString(R.string.custom_engine_invalid), Toast.LENGTH_SHORT).show()
                    sharedPreferences.edit().putString("search_engine", "google").apply()
                }
                updateSettingsButtonsUI()
            }
            .show()
    }

    private fun getHexColor(attrId: Int, defaultHex: String): String {
        try {
            val typedValue = TypedValue()
            if (theme.resolveAttribute(attrId, typedValue, true)) {
                return String.format("#%06X", 0xFFFFFF and typedValue.data)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return defaultHex
    }

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

    // Generate dynamic start page HTML matching Material You theme colors (dark background matches Unprocess #1A1A1A)
    private fun getStartPageHtml(): String {
        val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
        
        // Dynamically resolve wallpaper palette hex values (falls back to Unprocess charcoal #1A1A1A)
        val bgColor = getHexColor(android.R.attr.windowBackground, if (isDark) "#1A1A1A" else "#F9FAFB")
        val textColor = getHexColor(com.google.android.material.R.attr.colorOnSurface, if (isDark) "#F9FAFB" else "#111827")
        val primaryColor = getHexColor(com.google.android.material.R.attr.colorPrimary, if (isDark) "#D0BCFF" else "#4F46E5")

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
            </style>
            </head>
            <body>
              <div class="logo">Bare</div>
            </body>
            </html>
        """.trimIndent()
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

        // Force dynamic URL empty strings for blank/asset pages (no "about:blank" showing)
        val isBlank = activeTab.url == "about:blank" || activeTab.url.startsWith("file:///android_asset/")
        val displayText = if (!addressInput.isFocused) {
            if (isBlank) "" 
            else if (activeTab.isLoading) activeTab.webView.url ?: "" 
            else getSimplifiedUrl(activeTab.webView.url ?: "")
        } else {
            addressInput.text.toString()
        }
        
        if (!addressInput.isFocused) {
            addressInput.setText(displayText)
        }
        
        progressBar.visibility = if (activeTab.isLoading) View.VISIBLE else View.GONE
        updateRefreshIconState()
        updateTabButtonCount()
        updateNewTabButtonVisibility()
        
        // Apply webpage colors dynamically
        if (!isBlank) {
            updateDynamicColors(activeTab.webView)
        } else {
            resetUiColors()
        }
        
        showBottomBar() // Ensure bottom bar is visible when tab changes
        
        // Set SwipeRefresh eligibility
        swipeRefresh.isEnabled = !activeTab.webView.canScrollVertically(-1)
    }

    private fun closeTab(index: Int) {
        if (index !in tabList.indices) return

        val tabToRemove = tabList[index]
        webViewContainer.removeView(tabToRemove.webView)
        tabToRemove.webView.destroy()
        tabList.removeAt(index)

        if (tabList.isEmpty()) {
            addNewTab("about:blank")
        } else {
            // Fix active index reference
            if (activeTabIndex >= tabList.size) {
                activeTabIndex = tabList.size - 1
            }
            if (index == activeTabIndex || activeTabIndex == -1) {
                selectTab(activeTabIndex)
            } else {
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
        swipeRefresh.isEnabled = true // Empty page is always at top
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createNewWebView(): WebView {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )

        // Web settings configuration
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
        }

        // Apply desktop mode layout preferences if configured
        val isDesktop = sharedPreferences.getBoolean("desktop_mode", false)
        applyDesktopModeSetting(webView, isDesktop)

        // Listen to scrolls on the WebView to resolve pull-to-refresh conflicts and scroll-to-hide
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            // Continuously update colors on scroll
            updateDynamicColors(webView)

            val dy = scrollY - oldScrollY
            if (dy > 10) {
                hideBottomBar()
            } else if (dy < -10) {
                showBottomBar()
            }

            // Always display toolbar at the very top of page
            val isAtTop = !webView.canScrollVertically(-1)
            if (isAtTop) {
                showBottomBar()
            }
            
            if (findTabIndexForWebView(webView) == activeTabIndex) {
                swipeRefresh.isEnabled = isAtTop
            }
        }

        // WebView Client for URL overriding and load listener states
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: android.webkit.WebResourceRequest): Boolean {
                val url = request.url.toString()
                if (url.startsWith("http://") || url.startsWith("https://")) {
                    return false
                }
                // Handle external apps mapping
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
                        
                        val isBlank = tab.url == "about:blank" || tab.url.startsWith("file:///android_asset/")
                        val displayText = if (isBlank) "" else (url ?: "")
                        if (!addressInput.isFocused) {
                            addressInput.setText(displayText)
                        }
                        updateRefreshIconState()
                        updateNewTabButtonVisibility()
                        resetUiColors()
                        showBottomBar() // Display bottom bar on page reload
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
                    tab.title = if (tab.url == "about:blank") "New Tab" else (view?.title ?: "New Tab")

                    if (tabIndex == activeTabIndex) {
                        progressBar.visibility = View.GONE
                        swipeRefresh.isRefreshing = false
                        
                        val isBlank = tab.url == "about:blank" || tab.url.startsWith("file:///android_asset/")
                        val displayText = if (isBlank) "" else getSimplifiedUrl(url ?: "")
                        if (!addressInput.isFocused) {
                            addressInput.setText(displayText)
                        }
                        updateRefreshIconState()
                        updateNewTabButtonVisibility()
                        // Update scroll refresh eligibility
                        swipeRefresh.isEnabled = !webView.canScrollVertically(-1)
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

    // Capture low-resource 20%-scaled screenshot of a WebView to show as circular tab thumbnail
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

            // Dynamically adapt UI colors to match the webpage's top-left pixel color
            if (tabList.indexOf(tab) == activeTabIndex) {
                updateDynamicColors(webView)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    // Sample top-left pixel color of webpage and update parent layouts, status bar, and capsule card background
    private fun updateDynamicColors(webView: WebView) {
        if (webView.width <= 0 || webView.height <= 0) return
        try {
            // Create a 1x1 bitmap to sample the top-left color of the webpage
            val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(bitmap)
            webView.draw(canvas)
            val pixelColor = bitmap.getPixel(0, 0)

            // Layout backgrounds
            swipeRefresh.setBackgroundColor(pixelColor)
            webViewContainer.setBackgroundColor(pixelColor)

            currentStatusColor = pixelColor
            window.statusBarColor = if (isSettingsOpen) getDimmedColor(pixelColor) else pixelColor
            // Keep dynamic system navigation bar 100% transparent
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            val isDark = isColorDark(pixelColor)
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = !isDark
            windowInsetsController.isAppearanceLightNavigationBars = !isDark

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetUiColors() {
        try {
            val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
            // Dynamic window background aligns to Unprocess charcoal (#1A1A1A) in dark mode
            val systemColor = if (isDark) android.graphics.Color.parseColor("#1A1A1A") else android.graphics.Color.parseColor("#F9FAFB")
            
            swipeRefresh.setBackgroundColor(systemColor)
            webViewContainer.setBackgroundColor(systemColor)
            
            currentStatusColor = systemColor
            window.statusBarColor = if (isSettingsOpen) getDimmedColor(systemColor) else android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = !isDark
            windowInsetsController.isAppearanceLightNavigationBars = !isDark
            
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getDimmedColor(color: Int): Int {
        val alpha = 0.5f
        val r = (android.graphics.Color.red(color) * (1 - alpha)).toInt()
        val g = (android.graphics.Color.green(color) * (1 - alpha)).toInt()
        val b = (android.graphics.Color.blue(color) * (1 - alpha)).toInt()
        return android.graphics.Color.rgb(r, g, b)
    }

    private fun isColorDark(color: Int): Boolean {
        val r = android.graphics.Color.red(color)
        val g = android.graphics.Color.green(color)
        val b = android.graphics.Color.blue(color)
        val luma = 0.299 * r + 0.587 * g + 0.114 * b
        return luma < 128
    }

    private fun getSearchEnginePrefix(): String {
        return when (sharedPreferences.getString("search_engine", "google")) {
            "google" -> getString(R.string.search_prefix_google)
            "duckduckgo" -> getString(R.string.search_prefix_duckduckgo)
            "bing" -> getString(R.string.search_prefix_bing)
            "ecosia" -> getString(R.string.search_prefix_ecosia)
            "custom" -> sharedPreferences.getString("custom_search_prefix", getString(R.string.search_prefix_google)) ?: getString(R.string.search_prefix_google)
            else -> getString(R.string.search_prefix_google)
        }
    }

    // Fullscreen tab overview dialog with bottom-rearranged buttons (Back, Clear All, Add FAB)
    private fun showTabsOverviewDialog() {
        if (activeTabIndex in tabList.indices) {
            captureTabThumbnail(tabList[activeTabIndex])
        }

        val dialog = Dialog(this, R.style.Theme_Bare_FullscreenDialog)
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_tabs, null)
        dialog.setContentView(view)

        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

        val dialogRoot = view.findViewById<View>(R.id.dialogRoot)
        val btnBack = view.findViewById<ImageButton>(R.id.btnBack)
        val btnClearAll = view.findViewById<Button>(R.id.btnClearAll)
        val btnGap = view.findViewById<View>(R.id.btnGap)
        val btnAddTab = view.findViewById<FloatingActionButton>(R.id.btnAddTab)
        val rvTabs = view.findViewById<RecyclerView>(R.id.rvTabs)

        // Apply edge-to-edge window padding (pad top only to avoid status bar overlaps)
        ViewCompat.setOnApplyWindowInsetsListener(dialogRoot) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(v.paddingLeft, systemBars.top, v.paddingRight, 0)
            insets
        }

        rvTabs.layoutManager = LinearLayoutManager(this)
        
        // Dynamically extract Material You color values from the active system theme
        val typedValue = TypedValue()
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
        val colorPrimary = typedValue.data
        
        theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
        val colorPrimaryContainer = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorOutline, typedValue, true)
        val colorOutline = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
        val colorSurface = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
        val colorSurfaceVariant = typedValue.data

        theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
        val colorOnSurfaceVariant = typedValue.data

        val adapter = TabAdapter(
            tabs = tabList,
            activeTabIndex = activeTabIndex,
            colorPrimary = colorPrimary,
            colorPrimaryContainer = colorPrimaryContainer,
            colorOutline = colorOutline,
            colorSurface = colorSurface,
            colorSurfaceVariant = colorSurfaceVariant,
            colorOnSurfaceVariant = colorOnSurfaceVariant,
            onTabSelected = { selectedIndex ->
                selectTab(selectedIndex)
                dialog.dismiss()
            },
            onTabClosed = { closedIndex ->
                closeTab(closedIndex)
                rvTabs.adapter?.notifyDataSetChanged()
                
                // Show Clear All button and its gap spacer with fade-in animation when a tab is closed
                if (btnClearAll.visibility != View.VISIBLE) {
                    btnGap.visibility = View.VISIBLE
                    btnClearAll.visibility = View.VISIBLE
                    btnClearAll.alpha = 0f
                    btnClearAll.animate()
                        .alpha(1f)
                        .setDuration(250)
                        .start()
                }

                if (tabList.size == 1 && closedIndex == 0) {
                    dialog.dismiss()
                }
            }
        )
        rvTabs.adapter = adapter

        // Bind thin back button in the bottom toolbar
        btnBack.setOnClickListener {
            dialog.dismiss()
        }

        // Bind small bottom Clear All button
        btnClearAll.setOnClickListener {
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle("Close all tabs?")
                .setMessage("This will close all your open tabs.")
                .setNegativeButton(getString(R.string.btn_cancel), null)
                .setPositiveButton("Close All") { _, _ ->
                    while (tabList.isNotEmpty()) {
                        val tab = tabList.removeAt(0)
                        webViewContainer.removeView(tab.webView)
                        tab.webView.destroy()
                    }
                    addNewTab("about:blank")
                    dialog.dismiss()
                }
                .show()
        }

        // Bind large bottom thin add FAB
        btnAddTab.setOnClickListener {
            addNewTab("about:blank")
            dialog.dismiss()
        }

        dialog.show()
    }
}
