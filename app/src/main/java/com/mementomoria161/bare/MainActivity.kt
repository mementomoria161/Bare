package com.mementomoria161.bare

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.app.DownloadManager
import android.widget.ImageView
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
import androidx.appcompat.app.AppCompatDelegate
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
        var thumbnail: Bitmap? = null,
        var lastActiveTime: Long = System.currentTimeMillis()
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
    private lateinit var btnSettingManageFavorites: com.google.android.material.card.MaterialCardView
    private lateinit var btnSettingFavorites: com.google.android.material.card.MaterialCardView
    private lateinit var tvSettingSelectedSearchEngine: TextView
    private lateinit var btnSettingSearchEngineGoogle: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingSearchEngineDuckDuckGo: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingSearchEngineBing: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingSearchEngineEcosia: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingSearchEngineQwant: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingSearchEngineCustom: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingCustomSearch: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingDesktopSite: com.google.android.material.card.MaterialCardView
    private lateinit var btnSettingAutoClose: com.google.android.material.card.MaterialCardView
    private lateinit var btnSettingClearData: com.google.android.material.card.MaterialCardView
    private lateinit var ivSettingActiveSearchLogo: ImageView
    private lateinit var ivSettingFavoritesHeart: ImageView
    private lateinit var ivSettingFavoritesHeartCard: com.google.android.material.card.MaterialCardView
    private lateinit var tvSettingDesktopStatus: TextView
    private lateinit var tvSettingAutoCloseStatus: TextView
    private lateinit var ivSettingDesktopLogoCard: com.google.android.material.card.MaterialCardView
    private lateinit var ivSettingDesktopLogo: ImageView
    private lateinit var ivSettingAutoCloseLogoCard: com.google.android.material.card.MaterialCardView
    private lateinit var ivSettingAutoCloseLogo: ImageView

    private var isSettingsOpen = false
    private var isAnimatingSettings = false
    private var currentStatusColor = android.graphics.Color.TRANSPARENT
    private var isCustomSearchDeleteState = false

    // Unprocess tabs views
    private lateinit var tabsPanel: FrameLayout
    private lateinit var clearAllContainer: LinearLayout
    private var isTabsOpen = false
    private var isAnimatingTabs = false

    private var consecutiveTabsClosed = 0
    private var isShowingBookmarks = false

    data class BookmarkItem(
        val title: String,
        val url: String,
        var thumbnail: Bitmap? = null
    )
    private val bookmarkList = mutableListOf<BookmarkItem>()

    // Fullscreen custom view support for HTML5 videos
    private var customView: View? = null
    private var customViewCallback: WebChromeClient.CustomViewCallback? = null
    private lateinit var fullscreenContainer: FrameLayout

    // Download notification views
    private lateinit var downloadNotificationCard: com.google.android.material.card.MaterialCardView
    private lateinit var downloadProgress: ProgressBar
    private lateinit var downloadCompleteIcon: ImageView
    private lateinit var tvDownloadStatus: TextView

    private val downloadReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            // Silence in-app notifications on download complete (only standard system status bar notification is shown)
        }
    }

    // SharedPreferences for settings
    private lateinit var sharedPreferences: SharedPreferences

    // Toolbar hiding state
    private var isBottomBarHidden = false

    override fun onCreate(savedInstanceState: Bundle?) {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        // Apply Material You dynamic color theme to activity if supported by user device
        DynamicColors.applyToActivityIfAvailable(this)
        
        // Enable modern Edge-to-Edge rendering
        enableEdgeToEdge()
        
        // Disable Android Q+ system navigation bar contrast protections to allow 100% transparent navigation chins
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
            window.isStatusBarContrastEnforced = false
        }
        
        // Enforce transparent navigation and status bar at all times
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
        // Set window format to translucent to prevent screen flickering during hardware-accelerated video playback
        window.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        
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
        btnSettingManageFavorites = findViewById(R.id.btnSettingManageFavorites)
        btnSettingFavorites = findViewById(R.id.btnSettingFavorites)
        tvSettingSelectedSearchEngine = findViewById(R.id.tvSettingSelectedSearchEngine)
        btnSettingSearchEngineGoogle = findViewById(R.id.btnSettingSearchEngineGoogle)
        btnSettingSearchEngineDuckDuckGo = findViewById(R.id.btnSettingSearchEngineDuckDuckGo)
        btnSettingSearchEngineBing = findViewById(R.id.btnSettingSearchEngineBing)
        btnSettingSearchEngineEcosia = findViewById(R.id.btnSettingSearchEngineEcosia)
        btnSettingSearchEngineQwant = findViewById(R.id.btnSettingSearchEngineQwant)
        btnSettingSearchEngineCustom = findViewById(R.id.btnSettingSearchEngineCustom)
        btnSettingCustomSearch = findViewById(R.id.btnSettingCustomSearch)
        btnSettingDesktopSite = findViewById(R.id.btnSettingDesktopSite)
        btnSettingAutoClose = findViewById(R.id.btnSettingAutoClose)
        btnSettingClearData = findViewById(R.id.btnSettingClearData)
        ivSettingActiveSearchLogo = findViewById(R.id.ivSettingActiveSearchLogo)
        ivSettingFavoritesHeart = findViewById(R.id.ivSettingFavoritesHeart)
        ivSettingFavoritesHeartCard = findViewById(R.id.ivSettingFavoritesHeartCard)
        tvSettingDesktopStatus = findViewById(R.id.tvSettingDesktopStatus)
        tvSettingAutoCloseStatus = findViewById(R.id.tvSettingAutoCloseStatus)
        ivSettingDesktopLogoCard = findViewById(R.id.ivSettingDesktopLogoCard)
        ivSettingDesktopLogo = findViewById(R.id.ivSettingDesktopLogo)
        ivSettingAutoCloseLogoCard = findViewById(R.id.ivSettingAutoCloseLogoCard)
        ivSettingAutoCloseLogo = findViewById(R.id.ivSettingAutoCloseLogo)
        tabsPanel = findViewById(R.id.tabsPanel)
        clearAllContainer = findViewById(R.id.clearAllContainer)
        fullscreenContainer = findViewById(R.id.fullscreenContainer)

        restoreBookmarks()

        // Initialize download notification views
        downloadNotificationCard = findViewById(R.id.downloadNotificationCard)
        downloadProgress = findViewById(R.id.downloadProgress)
        downloadCompleteIcon = findViewById(R.id.downloadCompleteIcon)
        tvDownloadStatus = findViewById(R.id.tvDownloadStatus)

        // Register download complete broadcast receiver
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE), Context.RECEIVER_EXPORTED)
        } else {
            registerReceiver(downloadReceiver, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        }

        setupEdgeToEdgeInsets()
        setupAddressBarBehavior()
        setupActionButtons()
        setupBackNavigation()

        // Restore tabs state or create new one
        restoreTabsState()

        // Handle external link intents if launched from another app
        handleIntent(intent)
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

        // Apply dynamic top padding to rvTabsInline to clear the status bar but allow scrolling under it
        val rvTabsInline = findViewById<com.mementomoria161.bare.FadingRecyclerView>(R.id.rvTabsInline)
        rvTabsInline.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                updateTabPillsScaleOnScroll(recyclerView)
            }
        })
        rvTabsInline.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            updateTabPillsScaleOnScroll(rvTabsInline)
        }
        ViewCompat.setOnApplyWindowInsetsListener(rvTabsInline) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                view.paddingLeft,
                systemBars.top + dpToPx(this, 24),
                view.paddingRight,
                view.paddingBottom
            )
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
                    addressInput.post {
                        addressInput.selectAll()
                    }
                }
                
                updateAddressBarButtonsState()
            }
        }

        addressInput.setOnClickListener {
            if (addressInput.isFocused) {
                addressInput.selectAll()
            }
        }

        // Show/hide clear button dynamically as user types
        addressInput.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updateAddressBarButtonsState()
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

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
            closeAllOverlays()
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
            closeAllOverlays()
            addNewTab("about:blank")
        }

        // Tab Overview Inline toggle
        btnTabOverview.setOnClickListener {
            closeAllOverlays(excludeTabs = true)
            toggleTabsOverview()
        }

        // Unprocess settings pop-up triggers
        btnSettings.setOnClickListener {
            closeAllOverlays(excludeSettings = true)
            toggleSettingsMenu()
        }

        settingsDimOverlay.setOnClickListener {
            if (isSettingsOpen) {
                toggleSettingsMenu()
            } else if (isTabsOpen) {
                toggleTabsOverview()
            }
        }

        // Close tab overview when clicking on empty space / background
        tabsPanel.setOnClickListener {
            if (isTabsOpen) {
                toggleTabsOverview()
            }
        }
        val tabOverviewGestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapUp(e: android.view.MotionEvent): Boolean {
                val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTabsInline)
                val child = rv.findChildViewUnder(e.x, e.y)
                if (child == null) {
                    if (isTabsOpen) {
                        toggleTabsOverview()
                        return true
                    }
                }
                return false
            }
        })
        findViewById<android.view.View>(R.id.rvTabsInline).setOnTouchListener { _, event ->
            tabOverviewGestureDetector.onTouchEvent(event)
            false
        }

        // Bind inline tab clear all button
        val btnClearAllInline = findViewById<Button>(R.id.btnClearAllInline)
        btnClearAllInline.setOnClickListener {
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
                    toggleTabsOverview()
                }
                .show()
        }

        btnSettingManageFavorites.setOnClickListener {
            showBookmarksOverview()
        }

        btnSettingFavorites.setOnClickListener {
            addCurrentPageToBookmarks()
        }


        btnSettingSearchEngineGoogle.setOnClickListener {
            sharedPreferences.edit().putString("search_engine", "google").apply()
            updateSettingsButtonsUI()
        }

        btnSettingSearchEngineDuckDuckGo.setOnClickListener {
            sharedPreferences.edit().putString("search_engine", "duckduckgo").apply()
            updateSettingsButtonsUI()
        }

        btnSettingSearchEngineBing.setOnClickListener {
            sharedPreferences.edit().putString("search_engine", "bing").apply()
            updateSettingsButtonsUI()
        }

        btnSettingSearchEngineEcosia.setOnClickListener {
            sharedPreferences.edit().putString("search_engine", "ecosia").apply()
            updateSettingsButtonsUI()
        }

        btnSettingSearchEngineQwant.setOnClickListener {
            sharedPreferences.edit().putString("search_engine", "qwant").apply()
            updateSettingsButtonsUI()
        }

        btnSettingSearchEngineCustom.setOnClickListener {
            val engine = sharedPreferences.getString("search_engine", "google") ?: "google"
            if (engine == "custom") {
                if (isCustomSearchDeleteState) {
                    sharedPreferences.edit()
                        .putString("custom_search_prefix", "")
                        .putString("custom_search_name", "")
                        .putString("search_engine", "google")
                        .apply()
                    isCustomSearchDeleteState = false
                    updateSettingsButtonsUI()
                } else {
                    isCustomSearchDeleteState = true
                    updateSettingsButtonsUI()
                }
            } else {
                sharedPreferences.edit().putString("search_engine", "custom").apply()
                isCustomSearchDeleteState = false
                updateSettingsButtonsUI()
            }
        }

        btnSettingCustomSearch.setOnClickListener {
            showCustomSearchEngineDialog()
        }

        btnSettingDesktopSite.setOnClickListener {
            toggleDesktopSiteSetting()
        }

        btnSettingAutoClose.setOnClickListener {
            cycleAutoCloseSetting()
        }

        btnSettingClearData.setOnClickListener {
            handleClearDataSetting()
        }
    }

    private fun setupBackNavigation() {
        // Modern back button navigation using OnBackPressedCallback
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (customView != null) {
                    hideCustomView()
                    return
                }
                if (isSettingsOpen) {
                    toggleSettingsMenu()
                    return
                }
                if (isTabsOpen) {
                    toggleTabsOverview()
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

    // Dismiss keyboard and clear URL bar focus when tapping anywhere outside it
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        if (ev.action == android.view.MotionEvent.ACTION_DOWN && addressInput.isFocused) {
            val rect = android.graphics.Rect()
            addressInput.getGlobalVisibleRect(rect)
            if (!rect.contains(ev.rawX.toInt(), ev.rawY.toInt())) {
                addressInput.clearFocus()
                val imm = getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.hideSoftInputFromWindow(addressInput.windowToken, 0)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    // Scroll-to-Hide / Scroll-to-Show bottom toolbar animations
    private fun showBottomBar() {
        if (!isBottomBarHidden) return
        isBottomBarHidden = false
        swipeRefresh.isEnabled = false // Disable pull-to-refresh while animating to prevent overlap
        bottomBarCard.animate().cancel()
        bottomBarCard.animate()
            .translationY(0f)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .setDuration(250)
            .withEndAction {
                if (activeTabIndex in tabList.indices) {
                    val webView = tabList[activeTabIndex].webView
                    val isAtTop = !webView.canScrollVertically(-1)
                    val url = webView.url ?: ""
                    val isBlank = url == "about:blank" || url.startsWith("file:///android_asset/") || url.isEmpty()
                    swipeRefresh.isEnabled = isAtTop && !isBlank
                }
            }
            .start()
    }

    private fun hideBottomBar() {
        if (isBottomBarHidden || isSettingsOpen) return // Never hide if settings pop-up is active
        isBottomBarHidden = true
        swipeRefresh.isEnabled = false // Disable pull-to-refresh when bottom bar is hidden
        val params = bottomBarCard.layoutParams as androidx.constraintlayout.widget.ConstraintLayout.LayoutParams
        val totalMargin = params.bottomMargin + bottomBarCard.height.toFloat() + dpToPx(this, 16).toFloat()
        bottomBarCard.animate().cancel()
        bottomBarCard.animate()
            .translationY(totalMargin)
            .setInterpolator(android.view.animation.AccelerateInterpolator())
            .setDuration(200)
            .start()
    }

    // Unprocess settings menu toggle & bottom-to-top pop-up overshoot animations
    private fun toggleSettingsMenu(onFinished: (() -> Unit)? = null) {
        if (isAnimatingSettings) return
        if (isTabsOpen) {
            toggleTabsOverview(onFinished = { toggleSettingsMenu(onFinished) })
            return
        }
        isAnimatingSettings = true
        isSettingsOpen = !isSettingsOpen

        val toggles = listOf(
            btnSettingClearData,
            btnSettingDesktopSite,
            btnSettingAutoClose,
            findViewById<View>(R.id.searchEngineSectionPill),
            findViewById<View>(R.id.favoritesSplitSection)
        )

        if (isSettingsOpen) {
            updateSettingsButtonsUI()

            // Dim the top status bar in sync with settings dim overlay
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = false

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

            settingsPanel.post {
                toggles.forEach { button ->
                    button.animate().cancel()
                    button.alpha = 0f
                    button.scaleX = 0.3f
                    button.scaleY = 0.3f
                    button.translationX = 0f
                    button.translationY = startTranslationY
                }

                // Settings is a static LinearLayout (not scrollable), so every item
                // always animates to full alpha and scale — no edge-clipping math needed.
                toggles.forEachIndexed { index, button ->
                    button.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationX(0f)
                        .translationY(0f)
                        .setDuration(280)
                        .setStartDelay(120L + (count - 1 - index) * 60L)
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
            }
        } else {
            isCustomSearchDeleteState = false
            // Restore normal status bar color when settings dim overlay closes
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = !isColorDark(currentStatusColor)

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
                                onFinished?.invoke()
                            }
                        }
                    } else null)
                    .start()
            }
        }
    }

    private fun toggleTabsOverview(onFinished: (() -> Unit)? = null) {
        if (isAnimatingTabs) return
        if (isSettingsOpen) {
            toggleSettingsMenu(onFinished = { toggleTabsOverview(onFinished) })
            return
        }
        isAnimatingTabs = true
        isTabsOpen = !isTabsOpen

        val rvTabsInline = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvTabsInline)
        val btnClearAllInline = findViewById<Button>(R.id.btnClearAllInline)

        if (isTabsOpen) {


            val showClearAll = !isShowingBookmarks && consecutiveTabsClosed >= 2 && tabList.isNotEmpty()
            btnClearAllInline.visibility = if (showClearAll) View.VISIBLE else View.GONE
            clearAllContainer.visibility = if (showClearAll) View.VISIBLE else View.GONE

            // Capture thumbnail of current active tab before showing the overview,
            // so it always has a fresh screenshot even if we never switched away from it.
            if (!isShowingBookmarks && activeTabIndex in tabList.indices) {
                captureTabThumbnail(tabList[activeTabIndex])
            }

            rvTabsInline.alpha = 0f // Hide recycler view so children don't flash before animation setup
            setupInlineTabAdapter(rvTabsInline, btnClearAllInline)

            if (tabList.isNotEmpty()) {
                rvTabsInline.scrollToPosition(tabList.lastIndex)
            }

            // Dim status bar and overlay
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = false

            settingsDimOverlay.animate().cancel()
            tabsPanel.animate().cancel()

            settingsDimOverlay.visibility = View.VISIBLE
            settingsDimOverlay.alpha = 0f
            settingsDimOverlay.animate()
                .alpha(1f)
                .setDuration(250)
                .setListener(null)
                .start()

            // Display panel wrapper bündig so we can animate its items individually
            tabsPanel.visibility = View.VISIBLE
            tabsPanel.alpha = 1f
            tabsPanel.translationY = 0f

            // Animate Clear All button container in from bottom with overshoot ( reinbabbeln)
            if (clearAllContainer.visibility == View.VISIBLE) {
                clearAllContainer.animate().cancel()
                clearAllContainer.alpha = 0f
                clearAllContainer.scaleX = 0.3f
                clearAllContainer.scaleY = 0.3f
                clearAllContainer.translationY = dpToPx(this, 100).toFloat()
                clearAllContainer.animate()
                    .alpha(1f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .translationY(0f)
                    .setDuration(280)
                    .setStartDelay(0L)
                    .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                    .start()
            }

            // Animate visible RecyclerView tab cards in a staggered pop-up overshoot sequence
            rvTabsInline.post {
                val childCount = rvTabsInline.childCount
                for (i in 0 until childCount) {
                    val child = rvTabsInline.getChildAt(i)
                    child.animate().cancel()
                    child.alpha = 0f
                    child.scaleX = 0.3f
                    child.scaleY = 0.3f
                    child.translationY = dpToPx(this@MainActivity, 100).toFloat()
                }

                // RecyclerView layout pass complete; show recycler view now that all children are pre-hidden/scaled
                rvTabsInline.alpha = 1f

                for (i in 0 until childCount) {
                    val child = rvTabsInline.getChildAt(i)
                    val childBottom = child.bottom + rvTabsInline.top
                    val threshold = rvTabsInline.height - rvTabsInline.paddingBottom
                    val targetScale = if (threshold > 0 && childBottom > threshold) {
                        val scrolledPast = childBottom - threshold
                        val range = child.height.toFloat()
                        if (range > 0f) (1.0f - (scrolledPast / range)).coerceIn(0.0f, 1.0f) else 1.0f
                    } else {
                        1.0f
                    }

                    child.animate()
                        .alpha(targetScale)
                        .scaleX(targetScale)
                        .scaleY(targetScale)
                        .translationY(0f)
                        .setDuration(280)
                        .setStartDelay(120L + (childCount - 1 - i) * 60L)
                        .setInterpolator(android.view.animation.OvershootInterpolator(2.2f))
                        .start()
                }
            }

            isAnimatingTabs = false
        } else {
            // Restore status bar
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = !isColorDark(currentStatusColor)

            settingsDimOverlay.animate().cancel()
            tabsPanel.animate().cancel()

            settingsDimOverlay.animate()
                .alpha(0f)
                .setDuration(200)
                .setListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        settingsDimOverlay.visibility = View.GONE
                    }
                })
                .start()

            val targetTranslationY = dpToPx(this, 100).toFloat()
            val childCount = rvTabsInline.childCount

            // Stagger visible tab cards collapsing downward
            for (i in 0 until childCount) {
                val child = rvTabsInline.getChildAt(i)
                child.animate().cancel()
                child.animate()
                    .alpha(0f)
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .translationY(targetTranslationY)
                    .setDuration(200)
                    .setStartDelay(i * 40L)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .start()
            }

            // Animate Clear All button container downward last
            if (clearAllContainer.visibility == View.VISIBLE) {
                clearAllContainer.animate().cancel()
                clearAllContainer.animate()
                    .alpha(0f)
                    .scaleX(0.3f)
                    .scaleY(0.3f)
                    .translationY(targetTranslationY)
                    .setDuration(200)
                    .setStartDelay(childCount * 40L)
                    .setInterpolator(android.view.animation.AccelerateInterpolator())
                    .start()
            }

            // Fade out the panel container
            tabsPanel.animate()
                .alpha(0f)
                .setDuration(250)
                .setStartDelay(childCount * 40L)
                .setListener(object : android.animation.AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: android.animation.Animator) {
                        tabsPanel.visibility = View.GONE
                        isAnimatingTabs = false
                        isShowingBookmarks = false
                        onFinished?.invoke()
                        if (tabList.isEmpty()) {
                            addNewTab("about:blank")
                        } else {
                            if (activeTabIndex in tabList.indices) {
                                val activeTab = tabList[activeTabIndex]
                                activeTab.webView.visibility = View.VISIBLE
                                activeTab.webView.onResume()
                                val isTabBlank = activeTab.url == "about:blank" || activeTab.url.startsWith("file:///android_asset/")
                                if (!isTabBlank) {
                                    updateDynamicColors(activeTab.webView)
                                } else {
                                    resetUiColors()
                                }
                            }
                        }
                    }
                })
                .start()
        }
    }

    private fun closeAllOverlays(excludeTabs: Boolean = false, excludeSettings: Boolean = false) {
        if (!excludeSettings && isSettingsOpen) {
            toggleSettingsMenu()
        }
        if (!excludeTabs && isTabsOpen) {
            toggleTabsOverview()
        }
        if (customView != null) {
            hideCustomView()
        }
        downloadNotificationCard.visibility = View.GONE
        mainHandler.removeCallbacksAndMessages(null)
        isCustomSearchDeleteState = false
    }

    private fun setupInlineTabAdapter(rvTabs: androidx.recyclerview.widget.RecyclerView, btnClearAll: Button) {
        rvTabs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
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

        val items = if (isShowingBookmarks) {
            bookmarkList.map { bookmark ->
                TabOverviewItem(
                    title = bookmark.title,
                    url = bookmark.url,
                    thumbnail = bookmark.thumbnail,
                    lastActiveTime = null,
                    isBookmark = true
                )
            }
        } else {
            tabList.map { tab ->
                TabOverviewItem(
                    title = tab.title,
                    url = tab.url,
                    thumbnail = tab.thumbnail,
                    lastActiveTime = tab.lastActiveTime,
                    isBookmark = false
                )
            }
        }

        val adapter = TabAdapter(
            items = items,
            activeTabIndex = if (isShowingBookmarks) -1 else activeTabIndex,
            colorPrimary = colorPrimary,
            colorPrimaryContainer = colorPrimaryContainer,
            colorOutline = colorOutline,
            colorSurface = colorSurface,
            colorSurfaceVariant = colorSurfaceVariant,
            colorOnSurfaceVariant = colorOnSurfaceVariant,
            autoCloseSetting = sharedPreferences.getString("auto_close_tabs", "never") ?: "never",
            isBookmarkMode = isShowingBookmarks,
            onItemSelected = { selectedIndex ->
                if (isShowingBookmarks) {
                    val bookmark = bookmarkList[selectedIndex]
                    addNewTab(bookmark.url)
                    toggleTabsOverview()
                } else {
                    selectTab(selectedIndex)
                    toggleTabsOverview()
                }
            },
            onItemClosed = { closedIndex ->
                if (isShowingBookmarks) {
                    bookmarkList.removeAt(closedIndex)
                    saveBookmarks()
                    setupInlineTabAdapter(rvTabs, btnClearAll)
                } else {
                    closeTab(closedIndex, autoCreate = false)
                    consecutiveTabsClosed++
                    setupInlineTabAdapter(rvTabs, btnClearAll)
                    
                    val showClearAll = !isShowingBookmarks && consecutiveTabsClosed >= 2 && tabList.isNotEmpty()
                    btnClearAll.visibility = if (showClearAll) View.VISIBLE else View.GONE
                    clearAllContainer.visibility = if (showClearAll) View.VISIBLE else View.GONE
                    if (tabList.isEmpty()) {
                        toggleTabsOverview()
                    }
                }
            }
        )
        val savedScrollState = rvTabs.layoutManager?.onSaveInstanceState()
        rvTabs.adapter = adapter
        rvTabs.layoutManager?.onRestoreInstanceState(savedScrollState)
    }

    private fun updateSettingsButtonsUI() {
        // 1. Search Engine Row highlights
        val engine = sharedPreferences.getString("search_engine", "google") ?: "google"
        
        fun highlightSearchButton(button: com.google.android.material.button.MaterialButton, isActive: Boolean) {
            val typedVal = TypedValue()
            if (isActive) {
                theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedVal, true)
                button.backgroundTintList = android.content.res.ColorStateList.valueOf(typedVal.data)
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedVal, true)
                button.setTextColor(typedVal.data)
                button.iconTint = android.content.res.ColorStateList.valueOf(typedVal.data)
            } else {
                button.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedVal, true)
                button.setTextColor(typedVal.data)
                button.iconTint = android.content.res.ColorStateList.valueOf(typedVal.data)
            }
        }

        highlightSearchButton(btnSettingSearchEngineGoogle, engine == "google")
        highlightSearchButton(btnSettingSearchEngineDuckDuckGo, engine == "duckduckgo")
        highlightSearchButton(btnSettingSearchEngineBing, engine == "bing")
        highlightSearchButton(btnSettingSearchEngineEcosia, engine == "ecosia")
        highlightSearchButton(btnSettingSearchEngineQwant, engine == "qwant")

        if (engine != "custom") {
            isCustomSearchDeleteState = false
        }

        val customPrefix = sharedPreferences.getString("custom_search_prefix", "") ?: ""
        if (customPrefix.isNotEmpty()) {
            btnSettingSearchEngineCustom.visibility = View.VISIBLE
            if (engine == "custom" && isCustomSearchDeleteState) {
                btnSettingSearchEngineCustom.setIconResource(R.drawable.ic_close_thin)
                btnSettingSearchEngineCustom.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.TRANSPARENT)
                btnSettingSearchEngineCustom.iconTint = android.content.res.ColorStateList.valueOf(
                    androidx.core.content.ContextCompat.getColor(this, android.R.color.holo_red_dark)
                )
            } else {
                btnSettingSearchEngineCustom.setIconResource(R.drawable.ic_search)
                highlightSearchButton(btnSettingSearchEngineCustom, engine == "custom")
            }
        } else {
            btnSettingSearchEngineCustom.visibility = View.GONE
            if (engine == "custom") {
                sharedPreferences.edit().putString("search_engine", "google").apply()
                highlightSearchButton(btnSettingSearchEngineGoogle, true)
            }
        }

        val engineLabel = if (engine == "custom") {
            sharedPreferences.getString("custom_search_name", "Custom") ?: "Custom"
        } else {
            engine.replaceFirstChar { it.uppercase() }
        }
        tvSettingSelectedSearchEngine.text = engineLabel

        // 2. Desktop Mode color indicator formatting (matching Unprocess style)
        val isDesktop = sharedPreferences.getBoolean("desktop_mode", false)
        tvSettingDesktopStatus.text = if (isDesktop) "Enabled" else "Disabled"
        
        val typedValue = TypedValue()
        if (isDesktop) {
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
            ivSettingDesktopLogoCard.setCardBackgroundColor(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
            ivSettingDesktopLogo.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
        } else {
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
            ivSettingDesktopLogoCard.setCardBackgroundColor(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            ivSettingDesktopLogo.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
        }

        // 3. Auto-Close Tabs label formatting
        val autoClose = sharedPreferences.getString("auto_close_tabs", "never") ?: "never"
        val autoCloseLabel = when (autoClose) {
            "never" -> "Never"
            "day" -> "1 Day"
            "week" -> "1 Week"
            "month" -> "1 Month"
            else -> "Never"
        }
        tvSettingAutoCloseStatus.text = autoCloseLabel
        
        if (autoClose != "never") {
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
            ivSettingAutoCloseLogoCard.setCardBackgroundColor(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
            ivSettingAutoCloseLogo.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
        } else {
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
            ivSettingAutoCloseLogoCard.setCardBackgroundColor(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            ivSettingAutoCloseLogo.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
        }

        // 4. Favorites Add button filled state and highlighting
        val exists = if (activeTabIndex in tabList.indices) {
            val activeTab = tabList[activeTabIndex]
            val url = activeTab.webView.url ?: activeTab.url
            bookmarkList.any { it.url == url }
        } else {
            false
        }

        if (exists) {
            ivSettingFavoritesHeart.setImageResource(R.drawable.ic_heart_filled)
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
            ivSettingFavoritesHeartCard.setCardBackgroundColor(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
            ivSettingFavoritesHeart.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
        } else {
            ivSettingFavoritesHeart.setImageResource(R.drawable.ic_heart)
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceContainer, typedValue, true)
            ivSettingFavoritesHeartCard.setCardBackgroundColor(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            ivSettingFavoritesHeart.imageTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
        }
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
        val isDark = true
        
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
              html, body {
                background-color: $bgColor;
                color: $textColor;
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                display: flex;
                flex-direction: column;
                align-items: center;
                justify-content: flex-start;
                width: 100%;
                height: 100%;
                margin: 0;
                overflow: hidden;
                touch-action: none;
                user-select: none;
              }
              .logo-svg {
                color: $primaryColor;
                width: 360px;
                height: 360px;
                margin-top: 22vh;
                margin-bottom: 24px;
              }
            </style>
            </head>
            <body>
              <svg class="logo-svg" viewBox="0 0 1000 1000" fill="none" xmlns="http://www.w3.org/2000/svg">
                <path d="M702 465.638C702 468.551 668.745 497.194 648.841 513.214C653.21 520.739 660.735 526.079 674.085 526.807C678.697 527.05 691.562 525.837 696.417 524.866L697.145 532.148C684.766 538.459 666.075 549.625 657.337 549.625C633.306 549.625 614.372 527.05 614.372 500.592C614.372 475.833 619.227 463.211 623.354 459.327C628.451 454.472 668.502 435.539 675.299 435.539C682.824 435.539 702 460.783 702 465.638ZM645.442 504.718C657.579 496.951 670.444 485.057 670.444 473.891C670.444 468.065 664.619 455.686 653.453 455.686C644.472 455.686 643.258 472.92 643.258 483.358C643.258 490.883 643.743 498.165 645.442 504.718Z" fill="currentColor"/>
                <path d="M605.579 435.539C607.035 434.325 623.541 450.346 623.541 452.53C623.541 453.501 602.423 471.706 601.21 470.735C591.015 461.026 591.015 461.026 586.16 465.153V471.464C585.917 496.223 586.16 520.011 587.131 544.77L559.216 546.469L558.245 545.984V477.289C558.245 463.211 550.235 462.482 542.225 462.482V456.414C550.478 446.705 558.245 434.811 559.944 435.539C570.625 439.423 581.548 447.19 584.946 455.2C591.015 449.617 599.268 440.879 605.579 435.539Z" fill="currentColor"/>
                <path d="M544.445 529.963L544.688 534.575C539.348 538.944 527.697 546.712 518.715 549.867C516.725 542.585 510.705 534.818 510.705 528.021C499.054 533.847 481.091 543.799 470.168 549.625C463.614 540.643 456.818 528.992 449.293 521.953C461.672 509.816 491.772 485.3 510.948 470.978V467.337C510.948 461.511 505.365 453.016 491.772 453.016C481.334 453.016 470.411 460.055 459.488 467.094L455.604 459.812C472.838 449.132 495.17 437.966 506.579 435.296C523.085 435.296 537.406 449.86 537.406 465.153C537.406 472.435 535.95 507.389 535.95 514.671C535.95 523.166 539.348 526.807 544.445 529.963ZM479.878 511.758C479.878 519.768 486.917 525.837 496.384 525.837C501.238 525.837 506.336 522.924 510.705 519.283C510.705 509.33 510.948 494.281 510.948 482.63C492.257 493.553 479.878 504.718 479.878 511.758Z" fill="currentColor"/>
                <path d="M383.443 439.18C432.961 428.499 466.702 456.657 451.652 506.418C442.913 519.525 397.036 543.799 382.958 549.867C366.937 546.955 345.819 542.1 333.197 542.1C321.545 542.1 310.622 544.77 304.311 549.139L299.699 544.77C309.894 522.195 310.622 515.156 310.622 504.476L311.35 426.072C311.593 410.537 306.738 411.508 300.185 412.964L298 406.896C320.574 394.274 357.713 370 363.053 370C376.646 377.525 394.852 394.274 394.852 412.479C394.852 426.8 388.055 430.441 383.443 439.18ZM347.275 391.361C336.352 398.643 338.051 422.916 338.294 433.597L338.78 466.852C353.587 458.356 368.393 439.423 368.879 426.8C369.607 407.624 354.315 397.186 347.275 391.361ZM430.291 509.088C439.515 478.746 414.513 456.899 383.2 456.899C363.539 456.899 345.819 470.493 338.294 482.63C336.11 488.698 335.381 505.447 335.381 519.525C355.286 514.671 379.802 524.866 393.638 524.866C408.688 524.866 421.553 518.069 430.291 509.088Z" fill="currentColor"/>
              </svg>
            </body>
            </html>
        """.trimIndent()
    }

    // Tab Management
    private fun closeActiveTabIfBlank() {
        if (activeTabIndex in tabList.indices && tabList.size > 1) {
            val activeTab = tabList[activeTabIndex]
            val isBlank = activeTab.url == "about:blank" || activeTab.url.startsWith("file:///android_asset/") || activeTab.webView.url == null || activeTab.webView.url == "about:blank" || activeTab.webView.url!!.startsWith("file:///android_asset/")
            if (isBlank) {
                val indexToClose = activeTabIndex
                val tabToRemove = tabList[indexToClose]
                webViewContainer.removeView(tabToRemove.webView)
                tabToRemove.webView.destroy()
                tabList.removeAt(indexToClose)
                
                if (activeTabIndex >= tabList.size) {
                    activeTabIndex = tabList.size - 1
                }
                if (activeTabIndex < 0) {
                    activeTabIndex = 0
                }
            }
        }
    }

    private fun cleanBlankTabs(keepIndex: Int = -1) {
        val iterator = tabList.listIterator()
        var index = 0
        while (iterator.hasNext()) {
            val tab = iterator.next()
            val isBlank = tab.url == "about:blank" || tab.url.startsWith("file:///android_asset/") || tab.webView.url == null || tab.webView.url == "about:blank" || tab.webView.url!!.startsWith("file:///android_asset/")
            if (isBlank && index != keepIndex && tabList.size > 1) {
                webViewContainer.removeView(tab.webView)
                tab.webView.destroy()
                iterator.remove()
                if (index < activeTabIndex) {
                    activeTabIndex--
                }
            } else {
                index++
            }
        }
        if (activeTabIndex >= tabList.size) {
            activeTabIndex = tabList.size - 1
        }
        if (activeTabIndex < 0) {
            activeTabIndex = 0
        }
    }

    // Tab Management
    private fun addNewTab(url: String = "about:blank") {
        consecutiveTabsClosed = 0
        closeActiveTabIfBlank()
        closeAllOverlays()
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

        if (url == "about:blank") {
            addressInput.postDelayed({
                addressInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                imm.showSoftInput(addressInput, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }
        saveTabsState()
    }

    private fun selectTab(index: Int) {
        consecutiveTabsClosed = 0
        if (index !in tabList.indices) return
        val targetTab = tabList[index]
        
        cleanBlankTabs(index)
        
        val finalIndex = tabList.indexOf(targetTab)
        if (finalIndex !in tabList.indices) return

        // Capture thumbnail of deactivated tab
        if (activeTabIndex in tabList.indices && activeTabIndex != finalIndex) {
            captureTabThumbnail(tabList[activeTabIndex])
            tabList[activeTabIndex].webView.visibility = View.GONE
            tabList[activeTabIndex].webView.onPause()
        }

        activeTabIndex = finalIndex
        val activeTab = tabList[activeTabIndex]
        activeTab.lastActiveTime = System.currentTimeMillis()
        
        // Show selected tab
        activeTab.webView.visibility = View.VISIBLE
        activeTab.webView.onResume()
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
            activeTab.webView.post {
                updateDynamicColors(activeTab.webView)
                activeTab.webView.postDelayed({
                    updateDynamicColors(activeTab.webView)
                }, 150)
            }
        } else {
            resetUiColors()
        }
        
        showBottomBar() // Ensure bottom bar is visible when tab changes
        
        // Set SwipeRefresh eligibility
        swipeRefresh.isEnabled = !activeTab.webView.canScrollVertically(-1)
        saveTabsState()
    }

    private fun closeTab(index: Int, autoCreate: Boolean = true) {
        if (index !in tabList.indices) return

        val tabToRemove = tabList[index]
        webViewContainer.removeView(tabToRemove.webView)
        tabToRemove.webView.destroy()
        tabList.removeAt(index)

        if (tabList.isEmpty()) {
            if (autoCreate) {
                addNewTab("about:blank")
            }
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
        saveTabsState()
    }

    private fun updateTabButtonCount() {
        tvTabCount.text = tabList.size.toString()
        updateTabOverviewButtonVisibility()
    }

    private fun updateTabOverviewButtonVisibility() {
        val nonBlankCount = tabList.count { tab ->
            val url = tab.webView.url ?: tab.url
            !(url == "about:blank" || url.startsWith("file:///android_asset/"))
        }
        btnTabOverview.visibility = if (nonBlankCount >= 1) View.VISIBLE else View.GONE
    }

    private fun updateRefreshIconState() {
        updateAddressBarButtonsState()
    }

    private fun updateAddressBarButtonsState() {
        val hasFocus = addressInput.isFocused
        val text = addressInput.text.toString()
        
        if (hasFocus) {
            if (text.isNotEmpty()) {
                btnRefresh.visibility = View.VISIBLE
                btnRefresh.setImageResource(R.drawable.ic_close)
            } else {
                btnRefresh.visibility = View.GONE
            }
        } else {
            if (activeTabIndex in tabList.indices) {
                val activeTab = tabList[activeTabIndex]
                val url = activeTab.webView.url ?: activeTab.url
                val isBlank = url == "about:blank" || url.startsWith("file:///android_asset/") || url.isEmpty()
                if (isBlank) {
                    btnRefresh.visibility = View.GONE
                } else {
                    btnRefresh.visibility = View.VISIBLE
                    if (activeTab.isLoading) {
                        btnRefresh.setImageResource(R.drawable.ic_close)
                    } else {
                        btnRefresh.setImageResource(R.drawable.ic_refresh)
                    }
                }
            } else {
                btnRefresh.visibility = View.GONE
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
        swipeRefresh.isEnabled = false // Disable pull-to-refresh on blank page
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createNewWebView(): WebView {
        val webView = WebView(this)
        webView.layoutParams = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        // Set slightly transparent background to prevent inline HTML5 video flickering on GPU rendering
        webView.setBackgroundColor(android.graphics.Color.argb(1, 0, 0, 0))
        // Force hardware layer type to synchronize video surface rendering and avoid screen-wide flickering
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)


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

        // Gesture detector to capture swipe/drag gestures even on non-scrollable pages
        val gestureDetector = android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
            override fun onScroll(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                val url = webView.url ?: ""
                val isBlank = url == "about:blank" || url.startsWith("file:///android_asset/") || url.isEmpty()
                if (!isBlank) {
                    if (distanceY > 10) {
                        hideBottomBar()
                    } else if (distanceY < -10) {
                        showBottomBar()
                    }
                }
                return false
            }
        })

        webView.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            v.performClick()
            false
        }

        // Listen to scrolls on the WebView to resolve pull-to-refresh conflicts and scroll-to-hide
        webView.setOnScrollChangeListener { _, _, scrollY, _, oldScrollY ->

            val url = webView.url ?: ""
            val isBlank = url == "about:blank" || url.startsWith("file:///android_asset/") || url.isEmpty()

            if (!isBlank) {
                val dy = scrollY - oldScrollY
                if (dy > 10) {
                    hideBottomBar()
                } else if (dy < -10) {
                    showBottomBar()
                }
            }

            // Always display toolbar at the very top of page
            val isAtTop = !webView.canScrollVertically(-1)
            if (isAtTop) {
                showBottomBar()
            }
            
            if (findTabIndexForWebView(webView) == activeTabIndex) {
                swipeRefresh.isEnabled = isAtTop && !isBlank
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
                        updateTabOverviewButtonVisibility()
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
                        updateTabOverviewButtonVisibility()
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

            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null) {
                    onHideCustomView()
                    return
                }
                customView = view
                customViewCallback = callback
                
                // Hide system UI using modern insets API
                val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                windowInsetsController.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                windowInsetsController.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

                fullscreenContainer.addView(view)
                fullscreenContainer.visibility = View.VISIBLE
                
                webViewContainer.visibility = View.GONE
                bottomBarCard.visibility = View.GONE
            }

            override fun onHideCustomView() {
                hideCustomView()
            }
        }

        webView.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
                androidx.core.content.ContextCompat.checkSelfPermission(this@MainActivity, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                
                pendingDownloadUrl = url
                pendingDownloadUserAgent = userAgent
                pendingDownloadContentDisposition = contentDisposition
                pendingDownloadMimeType = mimetype
                
                androidx.core.app.ActivityCompat.requestPermissions(
                    this@MainActivity,
                    arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                    1001
                )
            } else {
                startDownload(url, userAgent, contentDisposition, mimetype)
            }
        }

        return webView
    }

    private var pendingDownloadUrl: String? = null
    private var pendingDownloadUserAgent: String? = null
    private var pendingDownloadContentDisposition: String? = null
    private var pendingDownloadMimeType: String? = null

    private fun startDownload(url: String, userAgent: String, contentDisposition: String, mimetype: String) {
        try {
            val filename = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimetype)
            val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
                setMimeType(mimetype)
                val cookies = android.webkit.CookieManager.getInstance().getCookie(url)
                addRequestHeader("cookie", cookies)
                addRequestHeader("User-Agent", userAgent)
                setDescription("Downloading file...")
                setTitle(filename)
                setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, filename)
            }
            val dm = getSystemService(Context.DOWNLOAD_SERVICE) as android.app.DownloadManager
            dm.enqueue(request)
            showDownloadNotification("Download started", isLoading = true)
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "Download failed: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            if (grantResults.isNotEmpty() && grantResults[0] == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                val url = pendingDownloadUrl
                val ua = pendingDownloadUserAgent
                val cd = pendingDownloadContentDisposition
                val mime = pendingDownloadMimeType
                if (url != null && ua != null && cd != null && mime != null) {
                    startDownload(url, ua, cd, mime)
                }
            } else {
                Toast.makeText(this, "Storage permission required to download files", Toast.LENGTH_SHORT).show()
            }
            pendingDownloadUrl = null
            pendingDownloadUserAgent = null
            pendingDownloadContentDisposition = null
            pendingDownloadMimeType = null
        }
    }

    private fun findTabIndexForWebView(view: WebView?): Int {
        if (view == null) return -1
        return tabList.indexOfFirst { it.webView == view }
    }

    private fun isBitmapBlank(bitmap: Bitmap): Boolean {
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return true
        val firstPixel = bitmap.getPixel(0, 0)
        for (x in 0 until w step 5) {
            for (y in 0 until h step 5) {
                if (bitmap.getPixel(x, y) != firstPixel) {
                    return false
                }
            }
        }
        return true
    }

    // Capture low-resource 20%-scaled screenshot of a WebView to show as circular tab thumbnail
    private fun captureTabThumbnail(tab: Tab) {
        // Background/inactive tabs should not be captured since they are not visible and will render as blank/black
        if (tabList.indexOf(tab) != activeTabIndex) return
        
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
            
            // Only update the thumbnail if the tab has no thumbnail yet or the newly captured bitmap contains actual content (i.e. is not blank)
            if (tab.thumbnail == null || !isBitmapBlank(bitmap)) {
                tab.thumbnail = bitmap
            }

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

            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            swipeRefresh.setColorSchemeColors(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
            swipeRefresh.setProgressBackgroundColorSchemeColor(typedValue.data)

            currentStatusColor = pixelColor
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            // Keep dynamic system navigation bar 100% transparent
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            val isDark = isColorDark(pixelColor)
            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = if (isSettingsOpen || isTabsOpen) false else !isDark
            windowInsetsController.isAppearanceLightNavigationBars = !isDark

            (progressBar as? CapsuleProgressView)?.setStrokeColor(pixelColor)

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetUiColors() {
        try {
            val isDark = true
            // Dynamic window background aligns to Unprocess charcoal (#1A1A1A) in dark mode
            val systemColor = if (isDark) android.graphics.Color.parseColor("#1A1A1A") else android.graphics.Color.parseColor("#F9FAFB")
            
            swipeRefresh.setBackgroundColor(systemColor)
            webViewContainer.setBackgroundColor(systemColor)
            
            currentStatusColor = systemColor
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT

            val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
            windowInsetsController.isAppearanceLightStatusBars = if (isSettingsOpen || isTabsOpen) false else !isDark
            windowInsetsController.isAppearanceLightNavigationBars = !isDark

            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurfaceVariant, typedValue, true)
            swipeRefresh.setColorSchemeColors(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
            swipeRefresh.setProgressBackgroundColorSchemeColor(typedValue.data)

            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            (progressBar as? CapsuleProgressView)?.setStrokeColor(typedValue.data)
            
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
            "qwant" -> getString(R.string.search_prefix_qwant)
            "custom" -> sharedPreferences.getString("custom_search_prefix", getString(R.string.search_prefix_google)) ?: getString(R.string.search_prefix_google)
            else -> getString(R.string.search_prefix_google)
        }
    }

    override fun onPause() {
        super.onPause()
        saveTabsState()
    }

    private fun cycleAutoCloseSetting() {
        val options = listOf("never", "day", "week", "month")
        val current = sharedPreferences.getString("auto_close_tabs", "never") ?: "never"
        val idx = options.indexOf(current)
        val nextIdx = if (idx == -1) 0 else (idx + 1) % options.size
        val nextOption = options[nextIdx]
        
        sharedPreferences.edit().putString("auto_close_tabs", nextOption).apply()
        updateSettingsButtonsUI()
        applyAutoCloseTabs()
    }

    private fun applyAutoCloseTabs() {
        val setting = sharedPreferences.getString("auto_close_tabs", "never") ?: "never"
        if (setting == "never") return
        
        val threshold = when (setting) {
            "day" -> 24 * 60 * 60 * 1000L
            "week" -> 7 * 24 * 60 * 60 * 1000L
            "month" -> 30 * 24 * 60 * 60 * 1000L
            else -> return
        }
        
        val currentTime = System.currentTimeMillis()
        val toRemove = mutableListOf<Int>()
        
        tabList.forEachIndexed { index, tab ->
            if (index != activeTabIndex) {
                if (currentTime - tab.lastActiveTime > threshold) {
                    toRemove.add(index)
                }
            }
        }
        
        for (i in toRemove.indices.reversed()) {
            val index = toRemove[i]
            val tab = tabList[index]
            webViewContainer.removeView(tab.webView)
            tab.webView.destroy()
            tabList.removeAt(index)
        }
        
        if (activeTabIndex >= tabList.size) {
            activeTabIndex = tabList.size - 1
        }
        if (tabList.isEmpty()) {
            addNewTab("about:blank")
        } else {
            updateTabButtonCount()
        }
        saveTabsState()
    }

    private fun saveTabsState() {
        try {
            val jsonArray = org.json.JSONArray()
            tabList.forEach { tab ->
                val jsonObj = org.json.JSONObject()
                jsonObj.put("url", tab.webView.url ?: tab.url)
                jsonObj.put("title", tab.title)
                jsonObj.put("lastActiveTime", tab.lastActiveTime)
                tab.thumbnail?.let { bmp ->
                    jsonObj.put("thumbnail", bitmapToBase64(bmp))
                }
                jsonArray.put(jsonObj)
            }
            sharedPreferences.edit()
                .putString("saved_tabs", jsonArray.toString())
                .putInt("active_tab_index", activeTabIndex)
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreTabsState() {
        try {
            val savedTabsStr = sharedPreferences.getString("saved_tabs", null)
            val savedActiveIndex = sharedPreferences.getInt("active_tab_index", -1)
            if (savedTabsStr != null) {
                val jsonArray = org.json.JSONArray(savedTabsStr)
                if (jsonArray.length() > 0) {
                    tabList.clear()
                    webViewContainer.removeAllViews()
                    
                    for (i in 0 until jsonArray.length()) {
                        val jsonObj = jsonArray.getJSONObject(i)
                        val url = jsonObj.getString("url")
                        val title = jsonObj.optString("title", "New Tab")
                        val lastActiveTime = jsonObj.optLong("lastActiveTime", System.currentTimeMillis())
                        val thumbnailBase64 = if (jsonObj.has("thumbnail")) jsonObj.getString("thumbnail") else null
                        val thumbnail = if (!thumbnailBase64.isNullOrEmpty()) base64ToBitmap(thumbnailBase64) else null
                        
                        val webView = createNewWebView()
                        val tab = Tab(
                            webView = webView,
                            url = url,
                            title = title,
                            lastActiveTime = lastActiveTime,
                            thumbnail = thumbnail
                        )
                        tabList.add(tab)
                        webViewContainer.addView(webView)
                        
                        if (url == "about:blank") {
                            loadStartPage(tab)
                        } else {
                            webView.loadUrl(url)
                        }
                        val targetActiveIdx = if (savedActiveIndex in 0 until jsonArray.length()) savedActiveIndex else 0
                        if (i != targetActiveIdx) {
                            webView.visibility = View.GONE
                            webView.onPause()
                        } else {
                            webView.visibility = View.VISIBLE
                        }
                    }
                    
                    activeTabIndex = if (savedActiveIndex in tabList.indices) savedActiveIndex else 0
                    selectTab(activeTabIndex)
                    applyAutoCloseTabs()
                    return
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        addNewTab("about:blank")
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(downloadReceiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private var dismissNotificationRunnable: Runnable? = null
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun showDownloadNotification(
        text: String,
        @Suppress("UNUSED_PARAMETER") isLoading: Boolean = true,
        @Suppress("UNUSED_PARAMETER") isFailed: Boolean = false
    ) {
        mainHandler.removeCallbacksAndMessages(null)
        
        tvDownloadStatus.text = text
        downloadProgress.visibility = View.GONE
        downloadCompleteIcon.visibility = View.VISIBLE
        downloadCompleteIcon.setImageResource(R.drawable.ic_download)
        downloadCompleteIcon.imageTintList = android.content.res.ColorStateList.valueOf(
            getThemeColor(com.google.android.material.R.attr.colorOnSurfaceVariant)
        )
        
        if (downloadNotificationCard.visibility != View.VISIBLE) {
            downloadNotificationCard.visibility = View.VISIBLE
            downloadNotificationCard.alpha = 0f
            downloadNotificationCard.translationY = dpToPx(this, 20).toFloat()
            downloadNotificationCard.animate()
                .alpha(1f)
                .translationY(0f)
                .setDuration(250)
                .setInterpolator(android.view.animation.DecelerateInterpolator())
                .start()
        } else {
            downloadNotificationCard.alpha = 1f
            downloadNotificationCard.translationY = 0f
        }
        
        dismissNotificationRunnable = Runnable {
            downloadNotificationCard.animate()
                .alpha(0f)
                .translationY(dpToPx(this@MainActivity, 20).toFloat())
                .setDuration(200)
                .setInterpolator(android.view.animation.AccelerateInterpolator())
                .withEndAction {
                    downloadNotificationCard.visibility = View.GONE
                }
                .start()
        }
        mainHandler.postDelayed(dismissNotificationRunnable!!, 3000)
    }

    private fun getThemeColor(attrId: Int): Int {
        val typedValue = TypedValue()
        theme.resolveAttribute(attrId, typedValue, true)
        return typedValue.data
    }

    private fun updateTabPillsScaleOnScroll(recyclerView: RecyclerView) {
        val threshold = recyclerView.height - recyclerView.paddingBottom
        if (threshold <= 0) return
        
        val childCount = recyclerView.childCount
        for (i in 0 until childCount) {
            val child = recyclerView.getChildAt(i)
            val childBottom = child.bottom + recyclerView.top
            
            if (childBottom <= threshold) {
                child.scaleX = 1.0f
                child.scaleY = 1.0f
                child.alpha = 1.0f
            } else {
                val scrolledPast = childBottom - threshold
                val range = child.height.toFloat()
                if (range > 0f) {
                    val progress = (scrolledPast / range).coerceIn(0.0f, 1.0f)
                    val scale = 1.0f - progress
                    child.scaleX = scale
                    child.scaleY = scale
                    child.alpha = scale
                }
            }
        }
    }

    private fun showBookmarksOverview() {
        if (isSettingsOpen) {
            toggleSettingsMenu(onFinished = {
                isShowingBookmarks = true
                toggleTabsOverview()
            })
        } else {
            isShowingBookmarks = true
            toggleTabsOverview()
        }
    }

    private fun addCurrentPageToBookmarks() {
        if (activeTabIndex in tabList.indices) {
            val activeTab = tabList[activeTabIndex]
            val url = activeTab.webView.url ?: activeTab.url
            val isBlank = url == "about:blank" || url.startsWith("file:///android_asset/") || url.isEmpty()
            if (isBlank) {
                Toast.makeText(this, "Startseite kann nicht gespeichert werden", Toast.LENGTH_SHORT).show()
            } else {
                val exists = bookmarkList.any { it.url == url }
                if (exists) {
                    bookmarkList.removeAll { it.url == url }
                    saveBookmarks()
                    updateSettingsButtonsUI()
                } else {
                    val title = activeTab.title
                    val thumbnail = activeTab.thumbnail
                    bookmarkList.add(BookmarkItem(title, url, thumbnail))
                    saveBookmarks()
                    updateSettingsButtonsUI()
                }
            }
        } else {
            Toast.makeText(this, "Kein aktiver Tab zum Speichern", Toast.LENGTH_SHORT).show()
        }
    }

    private fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = java.io.ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)
    }

    private fun base64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = android.util.Base64.decode(base64Str, android.util.Base64.NO_WRAP)
            android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }

    private fun saveBookmarks() {
        try {
            val jsonArray = org.json.JSONArray()
            bookmarkList.forEach { bookmark ->
                val jsonObj = org.json.JSONObject()
                jsonObj.put("title", bookmark.title)
                jsonObj.put("url", bookmark.url)
                bookmark.thumbnail?.let { bmp ->
                    jsonObj.put("thumbnail", bitmapToBase64(bmp))
                }
                jsonArray.put(jsonObj)
            }
            sharedPreferences.edit()
                .putString("saved_bookmarks", jsonArray.toString())
                .apply()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun restoreBookmarks() {
        try {
            val savedBookmarksStr = sharedPreferences.getString("saved_bookmarks", null)
            if (savedBookmarksStr != null) {
                val jsonArray = org.json.JSONArray(savedBookmarksStr)
                bookmarkList.clear()
                for (i in 0 until jsonArray.length()) {
                    val jsonObj = jsonArray.getJSONObject(i)
                    val title = jsonObj.getString("title")
                    val url = jsonObj.getString("url")
                    val thumbnailBase64 = if (jsonObj.has("thumbnail")) jsonObj.getString("thumbnail") else null
                    val thumbnail = if (!thumbnailBase64.isNullOrEmpty()) base64ToBitmap(thumbnailBase64) else null
                    bookmarkList.add(BookmarkItem(title, url, thumbnail))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun hideCustomView() {
        if (customView == null) return
        fullscreenContainer.removeView(customView)
        fullscreenContainer.visibility = View.GONE
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null

        // Restore system UI visibility using modern insets API
        val windowInsetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        windowInsetsController.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        windowInsetsController.isAppearanceLightStatusBars = !isColorDark(currentStatusColor)

        // Restore other main layout elements
        webViewContainer.visibility = View.VISIBLE
        bottomBarCard.visibility = View.VISIBLE
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        val action = intent.action
        val data = intent.dataString
        if (Intent.ACTION_VIEW == action && data != null) {
            addNewTab(data)
            if (isTabsOpen) {
                toggleTabsOverview()
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }
}
