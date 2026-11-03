package com.example.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Base64
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.data.model.InterfaceVersion
import com.example.data.repository.CreatorRepository
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Manages the real system WindowManager floating overlay over TikTok, Instagram,
 * YouTube Shorts, or any third-party app.
 *
 * Supports 7 interface versions:
 * - V1: Bottom Horizontal Cards (Reels style bar)
 * - V2: Sidebar Vertical Cards (Split screen right edge with resize & dock)
 * - V3: Compact Floating Card (Popup card)
 * - V4: Full Split Screen 50/50 (Google Dark Search)
 * - V5: Bottom Sheet (Resizable height via top drag handle)
 * - V6: Bottom Sheet with JavaScript Extracted Google Images Horizontal Scroll
 * - V7: Horizontal Photo Cards Carousel with Extracted Google Images
 *
 * Features:
 * - Instant query reload on TikTok/Instagram feed scroll detection
 * - Top-edge draggable height resize for Bottom Sheet (V5 & V6)
 * - Headless/Live JavaScript image extraction from Google Search DOM
 * - Smooth state preservation across versions and scroll changes
 */
object FloatingOverlayManager {

    private const val TAG = "FeedLensOverlay"

    private const val JS_IMAGE_EXTRACTION_SCRIPT = """
        (function() {
            try {
                var found = [];
                var seen = {};

                function addUrl(u) {
                    if (!u || typeof u !== 'string') return;
                    u = u.trim();
                    if (u.length < 12) return;
                    if (u.indexOf('data:image/svg') === 0) return;
                    if (u.indexOf('googlelogo') !== -1 || u.indexOf('cleardot.gif') !== -1 || u.indexOf('favicon') !== -1) return;
                    if (!seen[u]) {
                        seen[u] = true;
                        found.push(u);
                    }
                }

                var imgs = document.querySelectorAll('img');
                for (var i = 0; i < imgs.length; i++) {
                    var el = imgs[i];
                    addUrl(el.getAttribute('data-src'));
                    addUrl(el.getAttribute('data-iurl'));
                    addUrl(el.src);
                }

                var links = document.querySelectorAll('a[href*="imgurl="]');
                for (var j = 0; j < links.length; j++) {
                    var href = links[j].href;
                    var match = href.match(/imgurl=([^&]+)/);
                    if (match && match[1]) {
                        try {
                            addUrl(decodeURIComponent(match[1]));
                        } catch(e) {}
                    }
                }

                var resultJson = JSON.stringify(found.slice(0, 30));
                if (window.ImageExtractor && window.ImageExtractor.onImagesExtracted) {
                    window.ImageExtractor.onImagesExtracted(resultJson);
                }
                return resultJson;
            } catch(err) {
                return JSON.stringify([]);
            }
        })();
    """

    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private var currentWmParams: WindowManager.LayoutParams? = null
    private var lastContext: Context? = null

    private var mainContainer: LinearLayout? = null
    private var webView: WebView? = null
    private var searchEditText: EditText? = null
    private var progressBar: ProgressBar? = null
    private var loadingContainer: View? = null
    private var chipsRowLayout: LinearLayout? = null
    private var addTagInlineContainer: LinearLayout? = null
    private var newTagEditText: EditText? = null
    private var dockedEdgeTab: LinearLayout? = null

    // For V6 and V7 extracted images
    private var extractedImagesScrollContainer: HorizontalScrollView? = null
    private var extractedImagesRow: LinearLayout? = null
    private var extractedLoadingText: TextView? = null

    private var currentVersion = InterfaceVersion.SIDE_VERTICAL_CARDS
    private var currentQuery = "ju.pasqualotto fotos"
    private var lastLoadedUrl = ""
    private var isSidebarDocked = false
    private var sidebarWidthPx = 0
    private var bottomSheetHeightPx = 0

    // Cached extracted images
    private val extractedImageUrls = mutableListOf<String>()

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
        lastContext = context.applicationContext

        if (overlayRootView != null) {
            switchInterfaceVersion(version)
            updateQuery(initialQuery, forceReload = true)
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
            Log.w(TAG, "showOverlay: SYSTEM_ALERT_WINDOW not granted, requesting permission")
            CreatorDetectionBus.requestOverlayPermission(context)
            return
        }

        val appContext = context.applicationContext
        windowManager = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        currentQuery = initialQuery
        currentVersion = version
        lastLoadedUrl = ""

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
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT
        ).apply {
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        currentWmParams = wmParams

        applyVersionGeometry(appContext, wmParams, version)

        val root = FrameLayout(appContext)
        overlayRootView = root

        buildOverlayViewHierarchy(appContext, root, wmParams)

        try {
            Log.d(TAG, "showOverlay: adding view to WindowManager for version $version")
            windowManager?.addView(root, wmParams)
            CreatorDetectionBus.setOverlayShowing(true)
            loadSearchQuery(currentQuery, force = true)
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay failed to add view", e)
            hideOverlay()
        }
    }

    /**
     * Completely destroys the current overlay view hierarchy and WebView,
     * removes from WindowManager, and recreates cleanly for the new InterfaceVersion.
     * Preserves search query and extracted photos seamlessly.
     */
    fun switchInterfaceVersion(newVersion: InterfaceVersion) {
        runOnMainThread {
            try {
                Log.d(TAG, "switchInterfaceVersion: transitioning from $currentVersion to $newVersion")
                val context = overlayRootView?.context ?: lastContext
                if (context == null) {
                    Log.e(TAG, "switchInterfaceVersion: context is null")
                    return@runOnMainThread
                }

                val queryToRetain = currentQuery

                // 1. Destroy old views, WebView, and WindowManager layout completely
                destroyCurrentOverlayViews()

                // 2. Reset state for clean rebuild
                lastLoadedUrl = ""
                currentVersion = newVersion
                isSidebarDocked = false

                // 3. Re-create WindowManager layout params with new version geometry
                val wm = windowManager ?: (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).also {
                    windowManager = it
                }

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
                            WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                            WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                }
                currentWmParams = wmParams

                applyVersionGeometry(context, wmParams, newVersion)

                // 4. Build fresh view hierarchy
                val newRoot = FrameLayout(context)
                overlayRootView = newRoot

                buildOverlayViewHierarchy(context, newRoot, wmParams)

                // 5. Add cleanly to WindowManager
                wm.addView(newRoot, wmParams)
                CreatorDetectionBus.setOverlayShowing(true)
                Log.d(TAG, "switchInterfaceVersion: successfully added new overlay for $newVersion")

                // 6. Force reload search query
                loadSearchQuery(queryToRetain, force = true)
            } catch (e: Exception) {
                Log.e(TAG, "switchInterfaceVersion failed", e)
            }
        }
    }

    private fun destroyCurrentOverlayViews() {
        Log.d(TAG, "destroyCurrentOverlayViews: releasing existing views and WebView")

        // Destroy WebView cleanly
        webView?.let { wv ->
            try {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                wv.clearCache(true)
                wv.onPause()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
                Log.d(TAG, "WebView destroyed")
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying WebView", e)
            }
        }
        webView = null

        // Remove from WindowManager
        overlayRootView?.let { root ->
            try {
                if (root.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(root)
                } else {
                    windowManager?.removeView(root)
                }
                Log.d(TAG, "Root view removed from WindowManager")
            } catch (e: Exception) {
                Log.e(TAG, "Error removing root from WindowManager", e)
            }
        }
        overlayRootView = null

        mainContainer = null
        dockedEdgeTab = null
        searchEditText = null
        newTagEditText = null
        addTagInlineContainer = null
        chipsRowLayout = null
        progressBar = null
        loadingContainer = null
        extractedImagesScrollContainer = null
        extractedImagesRow = null
        extractedLoadingText = null
        currentWmParams = null
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
            // V1: Bottom Horizontal Cards (Reels style bar anchored to bottom)
            InterfaceVersion.BOTTOM_HORIZONTAL_CARDS -> {
                wmParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wmParams.x = 0
                wmParams.y = dpToPx(context, 12)
                wmParams.width = (screenW * 0.94f).toInt()
                wmParams.height = dpToPx(context, 300)
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

            // V5: Bottom Sheet (Google Search / Imagens)
            InterfaceVersion.BOTTOM_SHEET -> {
                wmParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wmParams.x = 0
                wmParams.y = 0
                wmParams.width = screenW
                if (bottomSheetHeightPx <= 0) {
                    bottomSheetHeightPx = (screenH * 0.52f).toInt()
                }
                wmParams.height = bottomSheetHeightPx
            }

            // V6: Bottom Sheet with JavaScript Extracted Google Images Horizontal Scroll
            InterfaceVersion.EXTRACTED_IMAGES_SCROLL -> {
                wmParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wmParams.x = 0
                wmParams.y = 0
                wmParams.width = screenW
                if (bottomSheetHeightPx <= 0) {
                    bottomSheetHeightPx = (screenH * 0.46f).toInt()
                }
                wmParams.height = bottomSheetHeightPx
            }

            // V7: Horizontal Photo Cards Carousel with Extracted Google Images
            InterfaceVersion.CAROUSEL_PHOTO_CARDS -> {
                wmParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wmParams.x = 0
                wmParams.y = dpToPx(context, 8)
                wmParams.width = (screenW * 0.96f).toInt()
                wmParams.height = dpToPx(context, 320)
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

        // 2. MAIN CONTAINER
        val isHorizontal = currentVersion == InterfaceVersion.SIDE_VERTICAL_CARDS || currentVersion == InterfaceVersion.FULL_SPLIT_SCREEN
        val isBottomSheetType = currentVersion == InterfaceVersion.BOTTOM_SHEET || currentVersion == InterfaceVersion.EXTRACTED_IMAGES_SCROLL
        val mainLayout = LinearLayout(context).apply {
            orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FA12141A"))
                cornerRadius = if (isBottomSheetType) {
                    dpToPx(context, 18).toFloat()
                } else if (currentVersion == InterfaceVersion.FULL_SPLIT_SCREEN) {
                    dpToPx(context, 8).toFloat()
                } else {
                    dpToPx(context, 18).toFloat()
                }
                setStroke(dpToPx(context, 1), Color.parseColor("#383E4E"))
            }
            background = bg
            elevation = dpToPx(context, 16).toFloat()
            visibility = if (isSidebarDocked) View.GONE else View.VISIBLE
        }
        mainContainer = mainLayout

        // RESIZE HANDLE (Left vertical strip for sidebar width resize)
        if (isHorizontal) {
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
        val contentLp = if (isHorizontal) {
            LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.MATCH_PARENT,
                1f
            )
        } else {
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        // TOP BAR: Drag Handle Pill
        // For V5 and V6, this handle resizes the bottom sheet height dynamically!
        val dragHandleContainer = FrameLayout(context).apply {
            setPadding(0, dpToPx(context, 4), 0, dpToPx(context, 6))
        }
        val dragHandle = View(context).apply {
            val handleDrawable = GradientDrawable().apply {
                setColor(if (isBottomSheetType) Color.parseColor("#8AB4F8") else Color.parseColor("#668AB4F8"))
                cornerRadius = dpToPx(context, 3).toFloat()
            }
            background = handleDrawable
        }
        val dragHandleLp = FrameLayout.LayoutParams(
            dpToPx(context, if (isBottomSheetType) 48 else 38),
            dpToPx(context, 4)
        ).apply {
            gravity = Gravity.CENTER
        }
        dragHandleContainer.addView(dragHandle, dragHandleLp)

        val dragContainerLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        contentColumn.addView(dragHandleContainer, dragContainerLp)

        // Setup top drag handle listener
        if (isBottomSheetType) {
            setupBottomSheetHeightResizeListener(context, dragHandleContainer, wmParams)
        } else {
            setupDraggableTouchListener(context, dragHandleContainer, wmParams)
        }

        // HEADER: Mode Pills (V1..V7) + Browser Icon + Dock Icon + Close Icon
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

        // Mode switch pills
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
                InterfaceVersion.EXTRACTED_IMAGES_SCROLL -> "V6"
                InterfaceVersion.CAROUSEL_PHOTO_CARDS -> "V7"
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
                    CreatorDetectionBus.setInterfaceVersion(ver)
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

        // SEARCH BAR (Interactive EditText with keyboard support)
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
                        updateQuery(newQ, forceReload = true)
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
                    updateQuery(q, forceReload = true)
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
            isIndeterminate = false
            max = 100
            visibility = View.GONE
        }
        val pBarLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(context, 2.5f)
        )
        progressBar = pBar
        contentColumn.addView(pBar, pBarLp)

        // CONTENT DISPLAY (WebView vs V6/V7 extracted images)
        if (currentVersion == InterfaceVersion.EXTRACTED_IMAGES_SCROLL || currentVersion == InterfaceVersion.CAROUSEL_PHOTO_CARDS) {
            // V6 & V7: Render Horizontal Images Display
            buildExtractedImagesDisplay(context, contentColumn)
            // Headless/minimal WebView in background to extract images
            buildHeadlessWebView(context, contentColumn)
        } else {
            // V1..V5: Direct Interactive Google WebView
            buildInteractiveWebContainer(context, contentColumn)
        }

        mainLayout.addView(contentColumn, contentLp)

        val mainLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        root.addView(mainLayout, mainLp)

        // Setup drag listener for header if not bottom sheet
        if (!isBottomSheetType) {
            setupDraggableTouchListener(context, headerRow, wmParams)
        }
    }

    private fun buildInteractiveWebContainer(context: Context, parent: LinearLayout) {
        val webContainer = FrameLayout(context).apply {
            val wvDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#12141A"))
                cornerRadius = dpToPx(context, 10).toFloat()
            }
            background = wvDrawable
            clipToOutline = true
        }

        val wv = WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress in 1..99) {
                        progressBar?.visibility = View.VISIBLE
                        progressBar?.progress = newProgress
                        if (newProgress > 65) {
                            loadingContainer?.visibility = View.GONE
                        }
                    } else {
                        progressBar?.visibility = View.GONE
                        loadingContainer?.visibility = View.GONE
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    Log.d(TAG, "WebView onPageStarted: $url")
                    progressBar?.visibility = View.VISIBLE
                    loadingContainer?.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    Log.d(TAG, "WebView onPageFinished: $url")
                    progressBar?.visibility = View.GONE
                    loadingContainer?.visibility = View.GONE
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e(TAG, "WebView error: $errorCode $description on $failingUrl")
                    progressBar?.visibility = View.GONE
                    loadingContainer?.visibility = View.GONE
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
        }
        val wvLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        webContainer.addView(wv, wvLp)
        webView = wv

        val loadingLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            visibility = View.GONE
        }
        loadingContainer = loadingLayout

        val spinner = ProgressBar(context).apply {
            indeterminateTintList = ColorStateList.valueOf(Color.parseColor("#4285F4"))
        }
        val spLp = LinearLayout.LayoutParams(dpToPx(context, 30), dpToPx(context, 30))
        loadingLayout.addView(spinner, spLp)

        val loadingText = TextView(context).apply {
            text = "Buscando no Google..."
            setTextColor(Color.parseColor("#8AB4F8"))
            textSize = 12f
            setPadding(0, dpToPx(context, 8), 0, 0)
        }
        loadingLayout.addView(loadingText)

        val loadLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.CENTER
        )
        webContainer.addView(loadingLayout, loadLp)

        val webContainerLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        parent.addView(webContainer, webContainerLp)
    }

    private fun buildHeadlessWebView(context: Context, parent: LinearLayout) {
        val wv = WebView(context).apply {
            setBackgroundColor(Color.TRANSPARENT)
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
            }

            addJavascriptInterface(ImageExtractorBridge { urls ->
                handleExtractedImages(context, urls)
            }, "ImageExtractor")

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    if (newProgress in 1..99) {
                        progressBar?.visibility = View.VISIBLE
                        progressBar?.progress = newProgress
                    } else {
                        progressBar?.visibility = View.GONE
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    progressBar?.visibility = View.VISIBLE
                    extractedLoadingText?.visibility = View.VISIBLE
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar?.visibility = View.GONE
                    // Trigger extraction immediately and again after 800ms for lazy images
                    view?.evaluateJavascript(JS_IMAGE_EXTRACTION_SCRIPT, null)
                    Handler(Looper.getMainLooper()).postDelayed({
                        view?.evaluateJavascript(JS_IMAGE_EXTRACTION_SCRIPT, null)
                    }, 800)
                }
            }
        }
        // Minimal 1dp view so WebKit processes layout and loads DOM images
        val wvLp = LinearLayout.LayoutParams(1, 1)
        parent.addView(wv, wvLp)
        webView = wv
    }

    private fun buildExtractedImagesDisplay(context: Context, parent: LinearLayout) {
        val isCarousel = currentVersion == InterfaceVersion.CAROUSEL_PHOTO_CARDS

        // Subtitle badge
        val infoRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 0, 0, dpToPx(context, 6))
        }
        val infoBadge = TextView(context).apply {
            text = if (isCarousel) "📸 V7: CARROSSEL DE FOTOS (GOOGLE)" else "🖼️ V6: FOTOS EXTRAÍDAS (GOOGLE)"
            setTextColor(Color.parseColor("#8AB4F8"))
            textSize = 10.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        infoRow.addView(infoBadge)

        val loadStatus = TextView(context).apply {
            text = " • Carregando..."
            setTextColor(Color.parseColor("#9AA0A6"))
            textSize = 10.5f
        }
        extractedLoadingText = loadStatus
        infoRow.addView(loadStatus)

        parent.addView(infoRow)

        // Horizontal scroll container
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = true
            isFillViewport = true
        }
        extractedImagesScrollContainer = scroll

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        extractedImagesRow = row

        scroll.addView(row)

        val scrollLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            0,
            1f
        )
        parent.addView(scroll, scrollLp)

        // Populate with already cached images or initial fallback items
        populateExtractedImagesUi(context)
    }

    private fun handleExtractedImages(context: Context, urls: List<String>) {
        runOnMainThread {
            if (urls.isNotEmpty()) {
                extractedImageUrls.clear()
                extractedImageUrls.addAll(urls)
                extractedLoadingText?.text = " • ${urls.size} fotos encontradas"
                populateExtractedImagesUi(context)
            }
        }
    }

    private fun populateExtractedImagesUi(context: Context) {
        val row = extractedImagesRow ?: return
        row.removeAllViews()

        val isCarousel = currentVersion == InterfaceVersion.CAROUSEL_PHOTO_CARDS
        val nick = CreatorDetectionBus.latestDetectedNickname.value ?: currentQuery.split(" ").firstOrNull() ?: "criador"
        val activeKw = CreatorDetectionBus.activeSearchKeyword.value

        // If no images extracted yet, provide sample video images from CreatorRepository as instant preview
        val imagesToDisplay: List<String> = if (extractedImageUrls.isNotEmpty()) {
            extractedImageUrls
        } else {
            val sample = CreatorRepository.sampleVideos.find { it.nickname.contains(nick, ignoreCase = true) }
                ?: CreatorRepository.sampleVideos.first()
            sample.imageResults.map { it.assetFileName ?: it.title }
        }

        imagesToDisplay.forEachIndexed { index, imgItem ->
            if (isCarousel) {
                // V7: Styled Carousel Card
                val card = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    val cardBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#1C202C"))
                        cornerRadius = dpToPx(context, 12).toFloat()
                        setStroke(dpToPx(context, 1), Color.parseColor("#343B4D"))
                    }
                    background = cardBg
                    elevation = dpToPx(context, 6).toFloat()
                    clipToOutline = true
                    setOnClickListener {
                        openExternalSearch(context, "$nick $activeKw")
                    }
                }
                val cardLp = LinearLayout.LayoutParams(
                    dpToPx(context, 140),
                    dpToPx(context, 185)
                ).apply {
                    setMargins(0, 0, dpToPx(context, 10), 0)
                }

                // Image inside Card
                val iv = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                loadImageItem(context, imgItem, iv)

                val ivLp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(context, 120)
                )
                card.addView(iv, ivLp)

                // Info footer
                val footer = LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dpToPx(context, 8), dpToPx(context, 6), dpToPx(context, 8), dpToPx(context, 6))
                }
                val userTag = TextView(context).apply {
                    text = "@$nick"
                    setTextColor(Color.WHITE)
                    textSize = 10.5f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    maxLines = 1
                }
                footer.addView(userTag)

                val tagRow = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                }
                val badge = TextView(context).apply {
                    text = "#$activeKw"
                    setTextColor(Color.parseColor("#8AB4F8"))
                    textSize = 9.5f
                }
                tagRow.addView(badge)

                val openIco = TextView(context).apply {
                    text = "  ↗"
                    setTextColor(Color.parseColor("#81C995"))
                    textSize = 10f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                }
                tagRow.addView(openIco)

                footer.addView(tagRow)
                card.addView(footer)
                row.addView(card, cardLp)

            } else {
                // V6: Streamlined Horizontal Scroll Image Thumbnails
                val imgFrame = FrameLayout(context).apply {
                    val frameBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#1B1F2A"))
                        cornerRadius = dpToPx(context, 10).toFloat()
                        setStroke(dpToPx(context, 1), Color.parseColor("#333A4B"))
                    }
                    background = frameBg
                    clipToOutline = true
                    setOnClickListener {
                        openExternalSearch(context, "$nick $activeKw")
                    }
                }
                val frameLp = LinearLayout.LayoutParams(
                    dpToPx(context, 110),
                    dpToPx(context, 140)
                ).apply {
                    setMargins(0, 0, dpToPx(context, 8), 0)
                }

                val iv = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                loadImageItem(context, imgItem, iv)

                val ivLp = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                imgFrame.addView(iv, ivLp)

                val numBadge = TextView(context).apply {
                    text = "${index + 1}"
                    setTextColor(Color.WHITE)
                    textSize = 9f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    val nBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#CC000000"))
                        cornerRadius = dpToPx(context, 4).toFloat()
                    }
                    background = nBg
                    setPadding(dpToPx(context, 4), dpToPx(context, 1), dpToPx(context, 4), dpToPx(context, 1))
                }
                val numLp = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.TOP or Gravity.START
                ).apply {
                    setMargins(dpToPx(context, 4), dpToPx(context, 4), 0, 0)
                }
                imgFrame.addView(numBadge, numLp)

                row.addView(imgFrame, frameLp)
            }
        }
    }

    private fun loadImageItem(context: Context, source: String, imageView: ImageView) {
        if (source.startsWith("data:image/")) {
            try {
                val base64Data = source.substringAfter("base64,")
                val decodedBytes = Base64.decode(base64Data, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap)
                    return
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed base64 decode", e)
            }
        }

        if (source.startsWith("http://") || source.startsWith("https://")) {
            try {
                val imageLoader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(source)
                    .target(imageView)
                    .crossfade(true)
                    .build()
                imageLoader.enqueue(request)
                return
            } catch (e: Exception) {
                Log.e(TAG, "Failed Coil image load", e)
            }
        }

        // Fallback to local asset
        val assetName = if (source.endsWith(".png") || source.endsWith(".jpg")) {
            source
        } else {
            "image_25e5e779.png"
        }
        try {
            context.assets.open(assetName).use { input ->
                val bm = BitmapFactory.decodeStream(input)
                imageView.setImageBitmap(bm)
            }
        } catch (_: Exception) {
            imageView.setBackgroundColor(Color.parseColor("#2A3142"))
        }
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
                    updateQuery("$baseNick $kw", forceReload = true)
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

        // '+' Button to add new tag
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
            updateQuery("$baseNick $tagText", forceReload = true)
            chipsRowLayout?.let { renderTagChips(context, it) }
        }
        newTagEditText?.setText("")
        addTagInlineContainer?.visibility = View.GONE
        newTagEditText?.let { disableInputFocus(it) }
    }

    private fun enableInputFocus(editText: EditText) {
        val wm = windowManager ?: return
        val root = overlayRootView ?: return
        val wmParams = currentWmParams ?: return

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

        wmParams.flags = wmParams.flags or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
        try {
            wm.updateViewLayout(root, wmParams)
        } catch (_: Exception) {}
    }

    /**
     * Updates the active query and forces immediate reload in the Google search engine.
     */
    fun updateQuery(newQuery: String, forceReload: Boolean = false) {
        if (newQuery.isBlank()) return
        val queryChanged = (currentQuery != newQuery)
        currentQuery = newQuery

        runOnMainThread {
            searchEditText?.setText(newQuery)
            chipsRowLayout?.let { row ->
                lastContext?.let { ctx -> renderTagChips(ctx, row) }
            }
            loadSearchQuery(newQuery, force = forceReload || queryChanged)
        }
    }

    private fun loadSearchQuery(query: String, force: Boolean = false) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val searchUrl = "https://www.google.com/search?tbm=isch&q=$encoded"

        if (!force && searchUrl == lastLoadedUrl) {
            Log.d(TAG, "loadSearchQuery: URL already loaded ($searchUrl), skipping")
            return
        }
        lastLoadedUrl = searchUrl

        Log.d(TAG, "loadSearchQuery: loading $searchUrl (force=$force)")
        progressBar?.visibility = View.VISIBLE
        loadingContainer?.visibility = View.VISIBLE
        extractedLoadingText?.text = " • Buscando no Google..."

        webView?.let { wv ->
            wv.stopLoading()
            wv.loadUrl(searchUrl)
        }
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

    /**
     * Resizes the bottom sheet height dynamically by dragging the top edge handle.
     * Dragging up increases height, dragging down decreases height.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun setupBottomSheetHeightResizeListener(
        context: Context,
        handle: View,
        wmParams: WindowManager.LayoutParams
    ) {
        var startY = 0f
        var startHeight = 0
        val dm = context.resources.displayMetrics
        val minHeight = dpToPx(context, 170)
        val maxHeight = (dm.heightPixels * 0.90f).toInt()

        handle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startY = event.rawY
                    startHeight = wmParams.height
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = (startY - event.rawY).toInt()
                    val newHeight = (startHeight + dy).coerceIn(minHeight, maxHeight)
                    if (newHeight != wmParams.height) {
                        wmParams.height = newHeight
                        bottomSheetHeightPx = newHeight
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
            destroyCurrentOverlayViews()
            CreatorDetectionBus.setOverlayShowing(false)
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

    /**
     * Bridge class receiving extracted image URLs from JavaScript
     */
    class ImageExtractorBridge(private val onExtracted: (List<String>) -> Unit) {
        @JavascriptInterface
        fun onImagesExtracted(jsonString: String) {
            try {
                val array = JSONArray(jsonString)
                val list = mutableListOf<String>()
                for (i in 0 until array.length()) {
                    val url = array.optString(i)
                    if (url.isNotBlank()) {
                        list.add(url)
                    }
                }
                onExtracted(list)
            } catch (e: Exception) {
                Log.e(TAG, "ImageExtractorBridge json parse error", e)
            }
        }
    }
}
