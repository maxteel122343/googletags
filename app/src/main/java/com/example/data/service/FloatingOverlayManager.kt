package com.example.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import com.example.data.model.InterfaceVersion
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Manages the real system WindowManager floating overlay over TikTok, Instagram,
 * YouTube Shorts, or any third-party app.
 *
 * Supports:
 * - 5 layout paradigms: V1 (Bottom Bar), V2 (Sidebar), V3 (Popup Card), V4 (50% Split), V5 (Bottom Sheet)
 * - Sidebar drag-to-edge hide, edge resize handle, and edge tab restore
 * - Real EditText search bar with dynamic WindowManager focus & soft keyboard management
 * - Dynamic '+' tag adder with inline tag creation
 * - Live Google Search / Images WebView with automatic query reloading
 */
object FloatingOverlayManager {

    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private var currentWmParams: WindowManager.LayoutParams? = null

    private var mainContainer: LinearLayout? = null
    private var webView: WebView? = null
    private var searchEditText: EditText? = null
    private var progressBar: ProgressBar? = null
    private var chipsRowLayout: LinearLayout? = null
    private var addTagInlineContainer: LinearLayout? = null
    private var newTagEditText: EditText? = null
    private var dockedEdgeTab: LinearLayout? = null

    private var currentVersion = InterfaceVersion.SIDE_VERTICAL_CARDS
    private var currentQuery = "ju.pasqualotto fotos"
    private var lastLoadedUrl = ""
    private var isSidebarDocked = false
    private var sidebarWidthPx = 0

    fun isShowing(): Boolean = overlayRootView != null

    private fun runOnMainThread(action: () -> Unit) {
        try {
            val looper = Looper.getMainLooper()
            if (looper != null && Looper.myLooper() == looper) {
                action()
            } else if (looper != null) {
                Handler(looper).post(action)
            } else {
                action()
            }
        } catch (_: Exception) {
            action()
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    fun showOverlay(
        context: Context,
        initialQuery: String,
        version: InterfaceVersion = CreatorDetectionBus.activeInterfaceVersion.value
    ) {
        if (overlayRootView != null) {
            switchInterfaceVersion(version)
            updateQuery(initialQuery)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            CreatorDetectionBus.requestOverlayPermission(context)
            return
        }

        val appContext = context.applicationContext
        windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        currentQuery = initialQuery
        currentVersion = version

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val wmParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        currentWmParams = wmParams

        // Configure layout geometry based on active InterfaceVersion
        applyVersionGeometry(appContext, wmParams, version)

        // Root container holds both the main overlay window and the docked edge tab
        val root = FrameLayout(appContext)
        overlayRootView = root

        buildOverlayViewHierarchy(appContext, root, wmParams)

        try {
            windowManager?.addView(root, wmParams)
            CreatorDetectionBus.setOverlayShowing(true)
            loadSearchQuery(currentQuery)
        } catch (_: Exception) {
            hideOverlay()
        }
    }

    private fun applyVersionGeometry(
        context: Context,
        wmParams: WindowManager.LayoutParams,
        version: InterfaceVersion
    ) {
        val dm = context.resources.displayMetrics
        val screenW = dm.widthPixels
        val screenH = dm.heightPixels

        when (version) {
            // V1: Bottom Horizontal Cards (Reels style bar)
            InterfaceVersion.BOTTOM_HORIZONTAL_CARDS -> {
                wmParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wmParams.x = 0
                wmParams.y = dpToPx(context, 12)
                wmParams.width = (screenW * 0.94f).toInt()
                wmParams.height = dpToPx(context, 260)
            }

            // V2: Side Vertical Cards (Sidebar docked to right edge)
            InterfaceVersion.SIDE_VERTICAL_CARDS -> {
                wmParams.gravity = Gravity.TOP or Gravity.END
                wmParams.x = 0
                wmParams.y = dpToPx(context, 40)
                if (sidebarWidthPx <= 0) {
                    sidebarWidthPx = (screenW * 0.44f).toInt().coerceIn(dpToPx(context, 220), dpToPx(context, 380))
                }
                wmParams.width = sidebarWidthPx
                wmParams.height = screenH - dpToPx(context, 70)
            }

            // V3: Compact Floating Card (Popup Window)
            InterfaceVersion.COMPACT_FLOATING_CARD -> {
                wmParams.gravity = Gravity.TOP or Gravity.START
                wmParams.x = dpToPx(context, 16)
                wmParams.y = dpToPx(context, 80)
                wmParams.width = dpToPx(context, 340).coerceAtMost((screenW * 0.92f).toInt())
                wmParams.height = dpToPx(context, 440)
            }

            // V4: Full Split Screen 50%
            InterfaceVersion.FULL_SPLIT_SCREEN -> {
                wmParams.gravity = Gravity.TOP or Gravity.END
                wmParams.x = 0
                wmParams.y = 0
                wmParams.width = (screenW * 0.50f).toInt()
                wmParams.height = screenH
            }

            // V5: Bottom Sheet
            InterfaceVersion.BOTTOM_SHEET -> {
                wmParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wmParams.x = 0
                wmParams.y = 0
                wmParams.width = screenW
                wmParams.height = (screenH * 0.48f).toInt()
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun buildOverlayViewHierarchy(
        context: Context,
        root: FrameLayout,
        wmParams: WindowManager.LayoutParams
    ) {
        root.removeAllViews()

        // 1. DOCKED EDGE TAB (Visible when sidebar/window is minimized to edge)
        val edgeTab = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = if (isSidebarDocked) View.VISIBLE else View.GONE
            val tabBg = GradientDrawable().apply {
                setColor(Color.parseColor("#F21E2330"))
                cornerRadius = dpToPx(context, 20).toFloat()
                setStroke(dpToPx(context, 1.5f), Color.parseColor("#4285F4"))
            }
            background = tabBg
            elevation = dpToPx(context, 12).toFloat()
            setPadding(dpToPx(context, 8), dpToPx(context, 6), dpToPx(context, 12), dpToPx(context, 6))
            setOnClickListener {
                restoreFromEdgeDock(context)
            }
        }
        val edgeTabLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dpToPx(context, 42)
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            topMargin = dpToPx(context, 140)
        }
        dockedEdgeTab = edgeTab

        val edgeG = TextView(context).apply {
            text = "G"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val gCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4285F4"))
            }
            background = gCircle
            gravity = Gravity.CENTER
        }
        val edgeGLp = LinearLayout.LayoutParams(dpToPx(context, 22), dpToPx(context, 22)).apply {
            setMargins(0, 0, dpToPx(context, 6), 0)
        }
        edgeTab.addView(edgeG, edgeGLp)

        val edgeText = TextView(context).apply {
            text = "🔍 FeedLens"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        edgeTab.addView(edgeText)

        root.addView(edgeTab, edgeTabLp)

        // 2. MAIN CONTAINER (The full overlay layout)
        val mainLayout = LinearLayout(context).apply {
            orientation = if (currentVersion == InterfaceVersion.SIDE_VERTICAL_CARDS) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FA12141A"))
                cornerRadius = if (currentVersion == InterfaceVersion.BOTTOM_SHEET || currentVersion == InterfaceVersion.FULL_SPLIT_SCREEN)
                    dpToPx(context, 12).toFloat()
                else
                    dpToPx(context, 18).toFloat()
                setStroke(dpToPx(context, 1), Color.parseColor("#383E4E"))
            }
            background = bg
            elevation = dpToPx(context, 16).toFloat()
            visibility = if (isSidebarDocked) View.GONE else View.VISIBLE
        }
        mainContainer = mainLayout

        // V2 RESIZE HANDLE (Left vertical strip for sidebar width resize)
        if (currentVersion == InterfaceVersion.SIDE_VERTICAL_CARDS || currentVersion == InterfaceVersion.FULL_SPLIT_SCREEN) {
            val leftResizeHandle = View(context).apply {
                val handleBg = GradientDrawable().apply {
                    setColor(Color.parseColor("#334285F4"))
                    cornerRadius = dpToPx(context, 3).toFloat()
                }
                background = handleBg
            }
            val resizeLp = LinearLayout.LayoutParams(
                dpToPx(context, 12),
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setupResizeTouchListener(context, leftResizeHandle, wmParams)
            mainLayout.addView(leftResizeHandle, resizeLp)
        }

        // Inner vertical column for content
        val contentColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(
                dpToPx(context, 10),
                dpToPx(context, 8),
                dpToPx(context, 10),
                dpToPx(context, 8)
            )
        }
        val contentLp = LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.MATCH_PARENT,
            1f
        )

        // TOP BAR: Drag Handle Pill
        val dragHandle = View(context).apply {
            val handleDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#668AB4F8"))
                cornerRadius = dpToPx(context, 3).toFloat()
            }
            background = handleDrawable
        }
        val dragHandleLp = LinearLayout.LayoutParams(
            dpToPx(context, 38),
            dpToPx(context, 4)
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, dpToPx(context, 6))
        }
        contentColumn.addView(dragHandle, dragHandleLp)

        // HEADER: Mode Pills + Browser Icon + Dock/Hide Icon + Close Icon
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerRowLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dpToPx(context, 6))
        }

        // Mode switch pills (V1, V2, V3, V4, V5)
        val modeScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val modeScrollLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)

        val modeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        InterfaceVersion.entries.forEach { ver ->
            val isCurrent = ver == currentVersion
            val verLabel = when (ver) {
                InterfaceVersion.BOTTOM_HORIZONTAL_CARDS -> "V1"
                InterfaceVersion.SIDE_VERTICAL_CARDS -> "V2"
                InterfaceVersion.COMPACT_FLOATING_CARD -> "V3"
                InterfaceVersion.FULL_SPLIT_SCREEN -> "V4"
                InterfaceVersion.BOTTOM_SHEET -> "V5"
            }
            val pill = TextView(context).apply {
                text = verLabel
                setTextColor(if (isCurrent) Color.WHITE else Color.parseColor("#AAAAAA"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val pBg = GradientDrawable().apply {
                    setColor(if (isCurrent) Color.parseColor("#4285F4") else Color.parseColor("#262A36"))
                    cornerRadius = dpToPx(context, 6).toFloat()
                }
                background = pBg
                setPadding(dpToPx(context, 6), dpToPx(context, 2), dpToPx(context, 6), dpToPx(context, 2))
                setOnClickListener {
                    switchInterfaceVersion(ver)
                }
            }
            val pillLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(context, 4), 0)
            }
            modeRow.addView(pill, pillLp)
        }
        modeScroll.addView(modeRow)
        headerRow.addView(modeScroll, modeScrollLp)

        // External browser button
        val browserBtn = TextView(context).apply {
            text = "↗"
            setTextColor(Color.parseColor("#8AB4F8"))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(context, 6), 0, dpToPx(context, 6), 0)
            setOnClickListener {
                openExternalSearch(context, currentQuery)
            }
        }
        headerRow.addView(browserBtn)

        // Hide/Dock to edge button
        val dockBtn = TextView(context).apply {
            text = "◀"
            setTextColor(Color.parseColor("#81C995"))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(context, 6), 0, dpToPx(context, 6), 0)
            setOnClickListener {
                dockToEdge(context)
            }
        }
        headerRow.addView(dockBtn)

        // Close button
        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#E06060"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(context, 6), 0, dpToPx(context, 6), 0)
            setOnClickListener {
                hideOverlay()
            }
        }
        headerRow.addView(closeBtn)

        contentColumn.addView(headerRow, headerRowLp)

        // SEARCH BAR (Interactive EditText with keyboard support!)
        val searchBarLayout = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val sbBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1E212B"))
                cornerRadius = dpToPx(context, 10).toFloat()
                setStroke(dpToPx(context, 1), Color.parseColor("#353B4B"))
            }
            background = sbBg
            setPadding(dpToPx(context, 8), dpToPx(context, 4), dpToPx(context, 8), dpToPx(context, 4))
        }
        val searchBarLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dpToPx(context, 6))
        }

        val searchG = TextView(context).apply {
            text = "G"
            setTextColor(Color.parseColor("#4285F4"))
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(0, 0, dpToPx(context, 6), 0)
        }
        searchBarLayout.addView(searchG)

        val editSearch = EditText(context).apply {
            setText(currentQuery)
            setTextColor(Color.WHITE)
            textSize = 12f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH

            // When user taps EditText, temporarily enable focus so soft keyboard opens
            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    enableInputFocus(this)
                }
                false
            }

            setOnEditorActionListener { _, actionId, event ->
                if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                    actionId == EditorInfo.IME_ACTION_DONE ||
                    (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER)
                ) {
                    val newQ = text.toString().trim()
                    if (newQ.isNotBlank()) {
                        updateQuery(newQ)
                    }
                    disableInputFocus(this)
                    true
                } else {
                    false
                }
            }
        }
        searchEditText = editSearch
        val editSearchLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        searchBarLayout.addView(editSearch, editSearchLp)

        val goSearchBtn = TextView(context).apply {
            text = "🔍"
            textSize = 13f
            setPadding(dpToPx(context, 4), 0, dpToPx(context, 4), 0)
            setOnClickListener {
                val q = editSearch.text.toString().trim()
                if (q.isNotBlank()) {
                    updateQuery(q)
                }
                disableInputFocus(editSearch)
            }
        }
        searchBarLayout.addView(goSearchBtn)

        contentColumn.addView(searchBarLayout, searchBarLp)

        // CHIPS ROW: Horizontal keywords + "+" Button
        val chipsScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
        }
        val chipsScrollLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dpToPx(context, 6))
        }

        val chipsRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        chipsRowLayout = chipsRow

        renderTagChips(context, chipsRow)

        chipsScroll.addView(chipsRow)
        contentColumn.addView(chipsScroll, chipsScrollLp)

        // INLINE ADD-TAG BAR (Expands when clicking '+')
        val addTagRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val atBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1B202D"))
                cornerRadius = dpToPx(context, 8).toFloat()
            }
            background = atBg
            setPadding(dpToPx(context, 8), dpToPx(context, 4), dpToPx(context, 8), dpToPx(context, 4))
        }
        val addTagRowLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dpToPx(context, 6))
        }
        addTagInlineContainer = addTagRow

        val tagEdit = EditText(context).apply {
            hint = "Nova tag (ex: fitness, vlog)"
            setHintTextColor(Color.parseColor("#888888"))
            setTextColor(Color.WHITE)
            textSize = 11.5f
            background = null
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_DONE

            setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    enableInputFocus(this)
                }
                false
            }
            setOnEditorActionListener { _, _, _ ->
                confirmNewTag(context)
                true
            }
        }
        newTagEditText = tagEdit
        val tagEditLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        addTagRow.addView(tagEdit, tagEditLp)

        val confirmTagBtn = TextView(context).apply {
            text = "✓ Adicionar"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val cBg = GradientDrawable().apply {
                setColor(Color.parseColor("#4285F4"))
                cornerRadius = dpToPx(context, 6).toFloat()
            }
            background = cBg
            setPadding(dpToPx(context, 8), dpToPx(context, 4), dpToPx(context, 8), dpToPx(context, 4))
            setOnClickListener {
                confirmNewTag(context)
            }
        }
        addTagRow.addView(confirmTagBtn)

        val cancelTagBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#AAAAAA"))
            textSize = 12f
            setPadding(dpToPx(context, 8), 0, dpToPx(context, 4), 0)
            setOnClickListener {
                addTagInlineContainer?.visibility = View.GONE
                disableInputFocus(tagEdit)
            }
        }
        addTagRow.addView(cancelTagBtn)

        contentColumn.addView(addTagRow, addTagRowLp)

        // PROGRESS BAR
        val pBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        val pBarLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(context, 2.5f)
        )
        progressBar = pBar
        contentColumn.addView(pBar, pBarLp)

        // REAL INTERACTIVE WEBVIEW
        val wv = WebView(context).apply {
            val wvDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#121212"))
                cornerRadius = dpToPx(context, 10).toFloat()
            }
            background = wvDrawable
            clipToOutline = true

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    progressBar?.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar?.visibility = View.GONE
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
        }
        val wvLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        webView = wv
        contentColumn.addView(wv, wvLp)

        mainLayout.addView(contentColumn, contentLp)

        val mainLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(mainLayout, mainLp)

        // Setup touch dragging
        setupDraggableTouchListener(context, dragHandle, wmParams)
        setupDraggableTouchListener(context, headerRow, wmParams)
    }

    private fun renderTagChips(context: Context, row: LinearLayout) {
        row.removeAllViews()
        val currentKw = CreatorDetectionBus.activeSearchKeyword.value
        val keywords = CreatorDetectionBus.availableKeywords.value

        keywords.forEach { kw ->
            val isActive = kw.equals(currentKw, ignoreCase = true)
            val chip = TextView(context).apply {
                text = kw
                setTextColor(if (isActive) Color.WHITE else Color.parseColor("#D0D0D0"))
                textSize = 11f
                typeface = if (isActive) android.graphics.Typeface.DEFAULT_BOLD else android.graphics.Typeface.DEFAULT
                val chipDrawable = GradientDrawable().apply {
                    setColor(if (isActive) Color.parseColor("#4285F4") else Color.parseColor("#252A36"))
                    cornerRadius = dpToPx(context, 8).toFloat()
                    setStroke(dpToPx(context, 1), if (isActive) Color.parseColor("#8AB4F8") else Color.parseColor("#383E4E"))
                }
                background = chipDrawable
                setPadding(dpToPx(context, 8), dpToPx(context, 3), dpToPx(context, 8), dpToPx(context, 3))
                setOnClickListener {
                    val baseNick = CreatorDetectionBus.latestDetectedNickname.value ?: "ju.pasqualotto"
                    CreatorDetectionBus.setActiveKeyword(kw)
                    updateQuery("$baseNick $kw")
                    renderTagChips(context, row)
                }
            }
            val chipLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(context, 6), 0)
            }
            row.addView(chip, chipLp)
        }

        // '+' Button to add new tag!
        val addBtn = TextView(context).apply {
            text = "+ Tag"
            setTextColor(Color.WHITE)
            textSize = 11f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val addBg = GradientDrawable().apply {
                setColor(Color.parseColor("#1B2D4F"))
                cornerRadius = dpToPx(context, 8).toFloat()
                setStroke(dpToPx(context, 1), Color.parseColor("#4285F4"))
            }
            background = addBg
            setPadding(dpToPx(context, 8), dpToPx(context, 3), dpToPx(context, 8), dpToPx(context, 3))
            setOnClickListener {
                addTagInlineContainer?.visibility = View.VISIBLE
                newTagEditText?.let { enableInputFocus(it) }
            }
        }
        val addBtnLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, dpToPx(context, 4), 0)
        }
        row.addView(addBtn, addBtnLp)
    }

    private fun confirmNewTag(context: Context) {
        val tagText = newTagEditText?.text?.toString()?.trim() ?: ""
        if (tagText.isNotBlank()) {
            CreatorDetectionBus.addKeyword(tagText)
            val baseNick = CreatorDetectionBus.latestDetectedNickname.value ?: "ju.pasqualotto"
            updateQuery("$baseNick $tagText")
            chipsRowLayout?.let { renderTagChips(context, it) }
        }
        newTagEditText?.setText("")
        addTagInlineContainer?.visibility = View.GONE
        newTagEditText?.let { disableInputFocus(it) }
    }

    // Dynamic keyboard / focus management for WindowManager overlay
    private fun enableInputFocus(editText: EditText) {
        val wm = windowManager ?: return
        val root = overlayRootView ?: return
        val wmParams = currentWmParams ?: return

        // Remove FLAG_NOT_FOCUSABLE so the window can accept text input
        wmParams.flags = wmParams.flags and WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE.inv()
        try {
            wm.updateViewLayout(root, wmParams)
        } catch (_: Exception) {}

        editText.requestFocus()
        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.showSoftInput(editText, InputMethodManager.SHOW_IMPLICIT)
    }

    private fun disableInputFocus(editText: EditText) {
        val wm = windowManager ?: return
        val root = overlayRootView ?: return
        val wmParams = currentWmParams ?: return

        val imm = editText.context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(editText.windowToken, 0)
        editText.clearFocus()

        // Restore FLAG_NOT_FOCUSABLE so touches outside pass through
        wmParams.flags = wmParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try {
            wm.updateViewLayout(root, wmParams)
        } catch (_: Exception) {}
    }

    fun switchInterfaceVersion(newVersion: InterfaceVersion) {
        currentVersion = newVersion
        val root = overlayRootView ?: return
        val wm = windowManager ?: return
        val wmParams = currentWmParams ?: return
        val context = root.context

        isSidebarDocked = false
        applyVersionGeometry(context, wmParams, newVersion)
        buildOverlayViewHierarchy(context, root as FrameLayout, wmParams)

        try {
            wm.updateViewLayout(root, wmParams)
            loadSearchQuery(currentQuery)
        } catch (_: Exception) {}
    }

    fun updateQuery(newQuery: String) {
        if (newQuery.isBlank()) return
        currentQuery = newQuery

        runOnMainThread {
            searchEditText?.setText(newQuery)
            loadSearchQuery(newQuery)
        }
    }

    private fun loadSearchQuery(query: String) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val searchUrl = "https://www.google.com/search?tbm=isch&q=$encoded"

        if (searchUrl == lastLoadedUrl) return
        lastLoadedUrl = searchUrl

        webView?.loadUrl(searchUrl)
    }

    private fun dockToEdge(context: Context) {
        isSidebarDocked = true
        mainContainer?.visibility = View.GONE
        dockedEdgeTab?.visibility = View.VISIBLE

        val wm = windowManager ?: return
        val root = overlayRootView ?: return
        val wmParams = currentWmParams ?: return

        wmParams.width = WindowManager.LayoutParams.WRAP_CONTENT
        wmParams.height = WindowManager.LayoutParams.WRAP_CONTENT
        wmParams.gravity = Gravity.TOP or Gravity.END
        wmParams.x = 0
        wmParams.y = dpToPx(context, 140)

        try {
            wm.updateViewLayout(root, wmParams)
        } catch (_: Exception) {}
    }

    private fun restoreFromEdgeDock(context: Context) {
        isSidebarDocked = false
        dockedEdgeTab?.visibility = View.GONE
        mainContainer?.visibility = View.VISIBLE

        val wm = windowManager ?: return
        val root = overlayRootView ?: return
        val wmParams = currentWmParams ?: return

        applyVersionGeometry(context, wmParams, currentVersion)
        try {
            wm.updateViewLayout(root, wmParams)
        } catch (_: Exception) {}
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupResizeTouchListener(
        context: Context,
        handle: View,
        wmParams: WindowManager.LayoutParams
    ) {
        var startX = 0f
        var startWidth = 0
        val dm = context.resources.displayMetrics
        val minWidth = dpToPx(context, 180)
        val maxWidth = dm.widthPixels - dpToPx(context, 30)

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = event.rawX
                    startWidth = wmParams.width
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (startX - event.rawX).toInt()
                    val newWidth = (startWidth + dx).coerceIn(minWidth, maxWidth)
                    if (newWidth != wmParams.width) {
                        wmParams.width = newWidth
                        sidebarWidthPx = newWidth
                        try {
                            windowManager?.updateViewLayout(overlayRootView, wmParams)
                        } catch (_: Exception) {}
                    }
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDraggableTouchListener(
        context: Context,
        view: View,
        wmParams: WindowManager.LayoutParams
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isClick = false

        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = wmParams.x
                    initialY = wmParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > dpToPx(context, 4) || Math.abs(dy) > dpToPx(context, 4)) {
                        isClick = false

                        // If user drags towards the right edge in sidebar mode, dock it!
                        if (currentVersion == InterfaceVersion.SIDE_VERTICAL_CARDS && dx > dpToPx(context, 90)) {
                            dockToEdge(context)
                            return@setOnTouchListener true
                        }

                        if (wmParams.gravity and Gravity.END != 0) {
                            wmParams.x = (initialX - dx).coerceAtLeast(0)
                        } else {
                            wmParams.x = (initialX + dx).coerceAtLeast(0)
                        }
                        wmParams.y = (initialY + dy).coerceAtLeast(0)

                        try {
                            windowManager?.updateViewLayout(overlayRootView, wmParams)
                        } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isClick) {
                        v.performClick()
                    }
                    true
                }
                else -> false
            }
        }
    }

    private fun openExternalSearch(context: Context, query: String) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "https://www.google.com/search?tbm=isch&q=$encoded"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {}
    }

    fun hideOverlay() {
        runOnMainThread {
            try {
                if (overlayRootView != null) {
                    webView?.destroy()
                    webView = null
                    windowManager?.removeView(overlayRootView)
                }
            } catch (_: Exception) {
            } finally {
                overlayRootView = null
                mainContainer = null
                dockedEdgeTab = null
                searchEditText = null
                currentWmParams = null
                CreatorDetectionBus.setOverlayShowing(false)
            }
        }
    }

    private fun dpToPx(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            dp,
            context.resources.displayMetrics
        ).toInt()
    }

    private fun dpToPx(context: Context, dp: Int): Int = dpToPx(context, dp.toFloat())
}
