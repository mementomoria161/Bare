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
    private lateinit var btnSettingSearchEngine: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingCustomSearch: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingDesktopSite: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingAutoClose: com.google.android.material.button.MaterialButton
    private lateinit var btnSettingClearData: com.google.android.material.button.MaterialButton

    private var isSettingsOpen = false
    private var isAnimatingSettings = false
    private var currentStatusColor = android.graphics.Color.TRANSPARENT

    // Unprocess tabs views
    private lateinit var tabsPanel: LinearLayout
    private lateinit var clearAllContainer: FrameLayout
    private var isTabsOpen = false
    private var isAnimatingTabs = false

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
        
        // Enforce transparent navigation and status bar at all times
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        
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
        btnSettingAutoClose = findViewById(R.id.btnSettingAutoClose)
        btnSettingClearData = findViewById(R.id.btnSettingClearData)
        tabsPanel = findViewById(R.id.tabsPanel)
        clearAllContainer = findViewById(R.id.clearAllContainer)

        setupEdgeToEdgeInsets()
        setupAddressBarBehavior()
        setupActionButtons()
        setupBackNavigation()

        // Restore tabs state or create new one
        restoreTabsState()
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
                    addressInput.selectAll()
                }
                
                updateAddressBarButtonsState()
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

        // Tab Overview Inline toggle
        btnTabOverview.setOnClickListener {
            toggleTabsOverview()
        }

        // Unprocess settings pop-up triggers
        btnSettings.setOnClickListener {
            toggleSettingsMenu()
        }

        settingsDimOverlay.setOnClickListener {
            if (isSettingsOpen) {
                toggleSettingsMenu()
            } else if (isTabsOpen) {
                toggleTabsOverview()
            }
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

        btnSettingSearchEngine.setOnClickListener {
            cycleSearchEngineSetting()
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
            btnSettingSearchEngine.parent as View, // Grouped Search Engine Row container
            btnSettingDesktopSite,
            btnSettingAutoClose,
            btnSettingClearData
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
            closeActiveTabIfBlank()
            
            if (tabList.size == 1) {
                val activeTab = tabList[0]
                val isBlank = activeTab.url == "about:blank" || activeTab.url.startsWith("file:///android_asset/") || activeTab.webView.url == null || activeTab.webView.url == "about:blank" || activeTab.webView.url!!.startsWith("file:///android_asset/")
                if (isBlank) {
                    val tabToRemove = tabList[0]
                    webViewContainer.removeView(tabToRemove.webView)
                    tabToRemove.webView.destroy()
                    tabList.clear()
                    activeTabIndex = -1
                }
            }

            btnClearAllInline.visibility = if (tabList.isNotEmpty()) View.VISIBLE else View.GONE
            clearAllContainer.visibility = if (tabList.isNotEmpty()) View.VISIBLE else View.GONE
            setupInlineTabAdapter(rvTabsInline, btnClearAllInline)

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
                    .setInterpolator(android.view.animation.OvershootInterpolator(1.5f))
                    .start()
            }

            // Animate visible RecyclerView tab cards in a staggered pop-up overshoot sequence
            rvTabsInline.post {
                for (i in 0 until rvTabsInline.childCount) {
                    val child = rvTabsInline.getChildAt(i)
                    child.animate().cancel()
                    child.alpha = 0f
                    child.scaleX = 0.3f
                    child.scaleY = 0.3f
                    child.translationY = dpToPx(this@MainActivity, 100).toFloat()
                }

                // RecyclerView layout pass complete; show recycler view now that all children are pre-hidden/scaled
                rvTabsInline.alpha = 1f

                for (i in 0 until rvTabsInline.childCount) {
                    val child = rvTabsInline.getChildAt(i)
                    child.animate()
                        .alpha(1f)
                        .scaleX(1f)
                        .scaleY(1f)
                        .translationY(0f)
                        .setDuration(280)
                        .setStartDelay(120L + i * 60L)
                        .setInterpolator(android.view.animation.OvershootInterpolator(1.1f))
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
                        onFinished?.invoke()
                        if (tabList.isEmpty()) {
                            addNewTab("about:blank")
                        }
                    }
                })
                .start()
        }
    }

    private fun setupInlineTabAdapter(rvTabs: androidx.recyclerview.widget.RecyclerView, btnClearAll: Button) {
        rvTabs.layoutManager = androidx.recyclerview.widget.LinearLayoutManager(this)
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
                toggleTabsOverview()
            },
            onTabClosed = { closedIndex ->
                closeTab(closedIndex, autoCreate = false)
                rvTabs.adapter?.notifyDataSetChanged()
                
                btnClearAll.visibility = if (tabList.isNotEmpty()) View.VISIBLE else View.GONE
                clearAllContainer.visibility = if (tabList.isNotEmpty()) View.VISIBLE else View.GONE
                if (tabList.isEmpty()) {
                    toggleTabsOverview()
                }
            }
        )
        rvTabs.adapter = adapter
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

        // 3. Auto-Close Tabs label formatting
        val autoClose = sharedPreferences.getString("auto_close_tabs", "never") ?: "never"
        val autoCloseLabel = when (autoClose) {
            "never" -> "Never"
            "day" -> "1 Day"
            "week" -> "1 Week"
            "month" -> "1 Month"
            else -> "Never"
        }
        btnSettingAutoClose.text = "Auto-Close: $autoCloseLabel"
        
        if (autoClose != "never") {
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimaryContainer, typedValue, true)
            btnSettingAutoClose.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnPrimaryContainer, typedValue, true)
            btnSettingAutoClose.setTextColor(typedValue.data)
            btnSettingAutoClose.iconTint = android.content.res.ColorStateList.valueOf(typedValue.data)
        } else {
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurfaceVariant, typedValue, true)
            btnSettingAutoClose.backgroundTintList = android.content.res.ColorStateList.valueOf(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorOnSurface, typedValue, true)
            btnSettingAutoClose.setTextColor(typedValue.data)
            btnSettingAutoClose.iconTint = android.content.res.ColorStateList.valueOf(typedValue.data)
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
              .logo {
                font-size: 56px;
                font-weight: 800;
                letter-spacing: -2px;
                color: $primaryColor;
                margin-top: 33vh;
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
        closeActiveTabIfBlank()
        if (isTabsOpen) {
            toggleTabsOverview()
        }
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
        if (index !in tabList.indices) return
        val targetTab = tabList[index]
        
        cleanBlankTabs(index)
        
        val finalIndex = tabList.indexOf(targetTab)
        if (finalIndex !in tabList.indices) return

        // Capture thumbnail of deactivated tab
        if (activeTabIndex in tabList.indices && activeTabIndex != finalIndex) {
            captureTabThumbnail(tabList[activeTabIndex])
            tabList[activeTabIndex].webView.visibility = View.GONE
        }

        activeTabIndex = finalIndex
        val activeTab = tabList[activeTabIndex]
        activeTab.lastActiveTime = System.currentTimeMillis()
        
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
            // Continuously update colors on scroll
            updateDynamicColors(webView)

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

            swipeRefresh.setColorSchemeColors(pixelColor)
            val typedValue = android.util.TypedValue()
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
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
            val isDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
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
            theme.resolveAttribute(com.google.android.material.R.attr.colorPrimary, typedValue, true)
            swipeRefresh.setColorSchemeColors(typedValue.data)
            theme.resolveAttribute(com.google.android.material.R.attr.colorSurface, typedValue, true)
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
                        
                        val webView = createNewWebView()
                        val tab = Tab(
                            webView = webView,
                            url = url,
                            title = title,
                            lastActiveTime = lastActiveTime
                        )
                        tabList.add(tab)
                        webViewContainer.addView(webView)
                        
                        if (url == "about:blank") {
                            loadStartPage(tab)
                        } else {
                            webView.loadUrl(url)
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
}
