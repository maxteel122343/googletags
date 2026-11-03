package com.example.data.service

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
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
import android.widget.ScrollView
import android.widget.TextView
import coil.ImageLoader
import coil.request.ImageRequest
import com.example.data.model.InterfaceVersion
import com.example.data.repository.CreatorRepository
import org.json.JSONArray
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * High-performance, zero-lag WindowManager floating overlay over TikTok, Instagram,
 * YouTube Shorts, Facebook Reels, and Bluesky.
 *
 * Supported 5 interface versions:
 * - V1: Bottom Horizontal Cards (Reels style bottom search bar)
 * - V2: Sidebar Vertical Cards (Split screen right edge with resize & dock)
 * - V5: Bottom Sheet (Resizable height via top drag handle)
 * - V6: Fotos na Vertical (Clean vertical stream of high-resolution photos only, infinite scroll)
 * - V7: Fotos na Horizontal (Clean horizontal swipe carousel of high-res photos, infinite scroll)
 *
 * Performance and Stability Features:
 * 1. Persistent Root View & WebView: Does NOT destroy or re-instantiate WebView or WindowManager views
 *    on version switch. Layout updates take ~1ms via updateViewLayout, eliminating black screens and lag.
 * 2. High-Resolution Photo Extraction: Extracts original imgurl source URLs and high-res image metadata from Google.
 * 3. Infinite Scrolling: Automatically loads next batch of photos when user reaches end of V6/V7 scroll.
 * 4. Solid `#161922` WebView background prevents WindowManager alpha composition black glitches.
 * 5. Smart query caching: Never reloads if query hasn't changed.
 */
object FloatingOverlayManager {

    private const val TAG = "FeedLensOverlay"

    /**
     * Enhanced JavaScript extractor for Google Images:
     * 1. Prioritizes full-resolution source image URLs from `imgurl=` links and script data.
     * 2. Extracts `data-iurl`, `data-src`, and image sources.
     * 3. Filters out Google icons, tracking pixels, and low-res SVGs.
     */
    private const val JS_IMAGE_EXTRACTION_SCRIPT = """
        (function() {
            try {
                var found = [];
                var seen = {};

                function addUrl(u, priority) {
                    if (!u || typeof u !== 'string') return;
                    u = u.trim();
                    if (u.length < 15) return;
                    if (u.indexOf('data:image/svg') === 0) return;
                    if (u.indexOf('googlelogo') !== -1 || u.indexOf('cleardot.gif') !== -1 || u.indexOf('favicon') !== -1 || u.indexOf('transparent.png') !== -1) return;
                    if (!seen[u]) {
                        seen[u] = true;
                        found.push({ url: u, prio: priority || 0 });
                    }
                }

                // 1. High-priority: Original source images via imgurl in href
                var links = document.querySelectorAll('a[href*="imgurl="]');
                for (var i = 0; i < links.length; i++) {
                    var href = links[i].href;
                    var m = href.match(/imgurl=([^&]+)/);
                    if (m && m[1]) {
                        try {
                            var decoded = decodeURIComponent(m[1]);
                            if (decoded.indexOf('http') === 0) {
                                addUrl(decoded, 10);
                            }
                        } catch(e) {}
                    }
                }

                // 2. Script data tags containing direct media URLs
                var scriptTags = document.querySelectorAll('script');
                for (var s = 0; s < scriptTags.length; s++) {
                    var txt = scriptTags[s].textContent || '';
                    if (txt.indexOf('http') !== -1) {
                        var matches = txt.match(/https?:\/\/[^"'\s\\]+\.(?:jpg|jpeg|png|webp)/gi);
                        if (matches) {
                            for (var k = 0; k < Math.min(matches.length, 20); k++) {
                                var cleanU = matches[k].replace(/\\u0026/g, '&').replace(/\\u003d/g, '=');
                                if (cleanU.indexOf('gstatic.com') === -1 && cleanU.indexOf('google.com') === -1) {
                                    addUrl(cleanU, 8);
                                }
                            }
                        }
                    }
                }

                // 3. Fallback: Image element data attributes
                var imgs = document.querySelectorAll('img');
                for (var j = 0; j < imgs.length; j++) {
                    var el = imgs[j];
                    var src = el.getAttribute('data-iurl') || el.getAttribute('data-src') || el.src;
                    if (src && src.indexOf('http') === 0) {
                        var w = el.naturalWidth || el.width || 0;
                        addUrl(src, (w > 200) ? 6 : 2);
                    }
                }

                found.sort(function(a, b) { return b.prio - a.prio; });
                var urls = found.map(function(item) { return item.url; }).slice(0, 60);

                var resultJson = JSON.stringify(urls);
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
    private var overlayRootView: FrameLayout? = null
    private var currentWmParams: WindowManager.LayoutParams? = null
    private var lastContext: Context? = null

    // Main Google search container (V1, V2, V5)
    private var mainContainer: LinearLayout? = null
    private var leftResizeHandle: View? = null
    private var dragHandleContainer: FrameLayout? = null
    private var webContainer: FrameLayout? = null
    private var webView: WebView? = null
    private var searchEditText: EditText? = null
    private var progressBar: ProgressBar? = null
    private var chipsRowLayout: LinearLayout? = null
    private var addTagInlineContainer: LinearLayout? = null
    private var newTagEditText: EditText? = null
    private var dockedEdgeTab: LinearLayout? = null
    private val versionPills = mutableListOf<Pair<InterfaceVersion, TextView>>()

    // Clean Photo containers (V6, V7)
    private var cleanPhotosContainer: FrameLayout? = null
    private var vScroll: ScrollView? = null
    private var hScroll: HorizontalScrollView? = null
    private var verticalPhotosContainer: LinearLayout? = null
    private var horizontalPhotosContainer: LinearLayout? = null
    private var cleanTitleBadge: TextView? = null
    private var photosLoadingBadge: TextView? = null
    private val cleanVersionPills = mutableListOf<Pair<InterfaceVersion, TextView>>()

    private var currentVersion = InterfaceVersion.BOTTOM_HORIZONTAL_CARDS
    private var currentQuery = "ju.pasqualotto fotos"
    private var lastLoadedUrl = ""
    private var isSidebarDocked = false
    private var sidebarWidthPx = 0
    private var bottomSheetHeightPx = 0

    // Infinite scroll state & image cache
    private val extractedImageUrls = mutableListOf<String>()
    private var isLoadingMorePhotos = false

    fun isShowing(): Boolean = overlayRootView != null

    fun resetForTesting() {
        overlayRootView = null
        currentVersion = InterfaceVersion.BOTTOM_HORIZONTAL_CARDS
        extractedImageUrls.clear()
        lastLoadedUrl = ""
        isSidebarDocked = false
    }

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
            updateQuery(initialQuery, forceReload = false)
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

        buildMasterOverlayHierarchy(appContext, root, wmParams)

        try {
            Log.d(TAG, "showOverlay: adding master root to WindowManager for version $version")
            windowManager?.addView(root, wmParams)
            CreatorDetectionBus.setOverlayShowing(true)
            applyVersionDisplayState(appContext, version)
            loadSearchQuery(currentQuery, force = true)
        } catch (e: Exception) {
            Log.e(TAG, "showOverlay failed to add view", e)
            hideOverlay()
        }
    }

    /**
     * Instant zero-lag version switching:
     * Does NOT tear down the WindowManager view or destroy the WebView.
     * Updates WindowManager layout parameters and toggles container visibility in ~1ms!
     */
    fun switchInterfaceVersion(newVersion: InterfaceVersion) {
        runOnMainThread {
            try {
                val context = overlayRootView?.context ?: lastContext ?: return@runOnMainThread
                val wm = windowManager ?: return@runOnMainThread
                val root = overlayRootView ?: return@runOnMainThread
                val wmParams = currentWmParams ?: return@runOnMainThread

                if (newVersion == currentVersion && overlayRootView != null && !isSidebarDocked) {
                    return@runOnMainThread
                }

                Log.d(TAG, "⚡ Instant version switch: $currentVersion ➔ $newVersion")
                currentVersion = newVersion
                isSidebarDocked = false
                dockedEdgeTab?.visibility = View.GONE

                // 1. Update WindowManager geometry immediately
                applyVersionGeometry(context, wmParams, newVersion)
                wm.updateViewLayout(root, wmParams)

                // 2. Adjust visibility and internal views
                applyVersionDisplayState(context, newVersion)

                // 3. If switching to clean photos and images are available, populate immediately
                if (newVersion == InterfaceVersion.EXTRACTED_IMAGES_SCROLL || newVersion == InterfaceVersion.CAROUSEL_PHOTO_CARDS) {
                    populateCleanPhotosUi(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "switchInterfaceVersion failed", e)
            }
        }
    }

    private fun applyVersionDisplayState(context: Context, version: InterfaceVersion) {
        val wmParams = currentWmParams ?: return

        // Update active indicators on all version pills
        updateAllVersionPillStyles(version)

        when (version) {
            InterfaceVersion.BOTTOM_HORIZONTAL_CARDS,
            InterfaceVersion.SIDE_VERTICAL_CARDS,
            InterfaceVersion.BOTTOM_SHEET -> {
                cleanPhotosContainer?.visibility = View.GONE
                mainContainer?.visibility = View.VISIBLE

                val isSidebar = (version == InterfaceVersion.SIDE_VERTICAL_CARDS)
                val isBottomSheet = (version == InterfaceVersion.BOTTOM_SHEET)

                mainContainer?.orientation = if (isSidebar) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
                leftResizeHandle?.visibility = if (isSidebar) View.VISIBLE else View.GONE

                // Configure drag/resize handle on top
                dragHandleContainer?.let { container ->
                    if (isBottomSheet) {
                        setupBottomSheetHeightResizeListener(context, container, wmParams)
                    } else {
                        setupDraggableTouchListener(context, container, wmParams)
                    }
                }
            }

            InterfaceVersion.EXTRACTED_IMAGES_SCROLL -> {
                mainContainer?.visibility = View.GONE
                cleanPhotosContainer?.visibility = View.VISIBLE
                vScroll?.visibility = View.VISIBLE
                hScroll?.visibility = View.GONE
                cleanTitleBadge?.text = "📸 V6: Fotos Vertical"
                populateCleanPhotosUi(context)
            }

            InterfaceVersion.CAROUSEL_PHOTO_CARDS -> {
                mainContainer?.visibility = View.GONE
                cleanPhotosContainer?.visibility = View.VISIBLE
                vScroll?.visibility = View.GONE
                hScroll?.visibility = View.VISIBLE
                cleanTitleBadge?.text = "🖼️ V7: Fotos Horizontal"
                populateCleanPhotosUi(context)
            }
        }
    }

    private fun updateAllVersionPillStyles(activeVersion: InterfaceVersion) {
        val allPills = versionPills + cleanVersionPills
        for ((ver, textView) in allPills) {
            val isActive = (ver == activeVersion)
            textView.setTextColor(if (isActive) Color.WHITE else Color.parseColor("#999999"))
            val bg = GradientDrawable().apply {
                setColor(if (isActive) Color.parseColor("#4285F4") else Color.parseColor("#262A36"))
                cornerRadius = dpToPx(textView.context, 6).toFloat()
            }
            textView.background = bg
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
            // V1: Bottom Horizontal Cards
            InterfaceVersion.BOTTOM_HORIZONTAL_CARDS -> {
                wmParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wmParams.x = 0
                wmParams.y = dpToPx(context, 12)
                wmParams.width = (screenW * 0.94f).toInt()
                wmParams.height = dpToPx(context, 320)
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

            // V5: Bottom Sheet with height resize
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

            // V6: Clean Vertical Photos (Infinite scroll, edge-to-edge)
            InterfaceVersion.EXTRACTED_IMAGES_SCROLL -> {
                wmParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wmParams.x = 0
                wmParams.y = 0
                wmParams.width = screenW
                if (bottomSheetHeightPx <= 0) {
                    bottomSheetHeightPx = (screenH * 0.58f).toInt()
                }
                wmParams.height = bottomSheetHeightPx
            }

            // V7: Clean Horizontal Photos (Swipe sideways, edge-to-edge)
            InterfaceVersion.CAROUSEL_PHOTO_CARDS -> {
                wmParams.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                wmParams.x = 0
                wmParams.y = 0
                wmParams.width = screenW
                if (bottomSheetHeightPx <= 0) {
                    bottomSheetHeightPx = (screenH * 0.58f).toInt()
                }
                wmParams.height = bottomSheetHeightPx
            }
        }
    }

    /**
     * Builds master view hierarchy containing:
     * - Edge tab (docked)
     * - Main Google container (V1, V2, V5) with single persistent WebView
     * - Clean photos container (V6, V7)
     */
    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun buildMasterOverlayHierarchy(
        context: Context,
        root: FrameLayout,
        wmParams: WindowManager.LayoutParams
    ) {
        root.removeAllViews()
        versionPills.clear()
        cleanVersionPills.clear()

        // 1. DOCKED EDGE TAB
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
            setOnClickListener { restoreFromEdgeDock(context) }
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
        edgeTab.addView(edgeG, LinearLayout.LayoutParams(dpToPx(context, 22), dpToPx(context, 22)).apply {
            setMargins(0, 0, dpToPx(context, 6), 0)
        })

        val edgeText = TextView(context).apply {
            text = "🔍 FeedLens"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        edgeTab.addView(edgeText)
        root.addView(edgeTab, edgeTabLp)

        // 2. MAIN CONTAINER (V1, V2, V5)
        buildMainGoogleContainer(context, root, wmParams)

        // 3. CLEAN PHOTOS CONTAINER (V6, V7)
        buildCleanPhotosContainer(context, root, wmParams)
    }

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    private fun buildMainGoogleContainer(
        context: Context,
        root: FrameLayout,
        wmParams: WindowManager.LayoutParams
    ) {
        val isHorizontal = currentVersion == InterfaceVersion.SIDE_VERTICAL_CARDS
        val isBottomSheetType = currentVersion == InterfaceVersion.BOTTOM_SHEET

        val mainLayout = LinearLayout(context).apply {
            orientation = if (isHorizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#FA12141A"))
                cornerRadius = if (isBottomSheetType) dpToPx(context, 18).toFloat() else dpToPx(context, 16).toFloat()
                setStroke(dpToPx(context, 1), Color.parseColor("#383E4E"))
            }
            background = bg
            elevation = dpToPx(context, 16).toFloat()
            visibility = if (isSidebarDocked) View.GONE else View.VISIBLE
        }
        mainContainer = mainLayout

        // Left resize handle for V2 sidebar
        val resizeHandle = View(context).apply {
            val handleBg = GradientDrawable().apply {
                setColor(Color.parseColor("#334285F4"))
                cornerRadius = dpToPx(context, 3).toFloat()
            }
            background = handleBg
            visibility = if (isHorizontal) View.VISIBLE else View.GONE
        }
        leftResizeHandle = resizeHandle
        setupResizeTouchListener(context, resizeHandle, wmParams)
        mainLayout.addView(resizeHandle, LinearLayout.LayoutParams(dpToPx(context, 12), ViewGroup.LayoutParams.MATCH_PARENT))

        val contentColumn = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(context, 10), dpToPx(context, 8), dpToPx(context, 10), dpToPx(context, 8))
        }
        val contentLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)

        // Top drag handle container
        val dragHandleBox = FrameLayout(context).apply {
            setPadding(0, dpToPx(context, 4), 0, dpToPx(context, 6))
        }
        dragHandleContainer = dragHandleBox

        val dragPill = View(context).apply {
            val handleDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#668AB4F8"))
                cornerRadius = dpToPx(context, 3).toFloat()
            }
            background = handleDrawable
        }
        dragHandleBox.addView(dragPill, FrameLayout.LayoutParams(dpToPx(context, 42), dpToPx(context, 4)).apply {
            gravity = Gravity.CENTER
        })
        contentColumn.addView(dragHandleBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // Header Row: Version Pills + Browser + Dock + Close
        val headerRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerRowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, 0, dpToPx(context, 6))
        }

        val modeScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val modeRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }

        InterfaceVersion.entries.forEach { ver ->
            val isCurrent = ver == currentVersion
            val verLabel = when (ver) {
                InterfaceVersion.BOTTOM_HORIZONTAL_CARDS -> "V1"
                InterfaceVersion.SIDE_VERTICAL_CARDS -> "V2"
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
                setOnClickListener { CreatorDetectionBus.setInterfaceVersion(ver) }
            }
            versionPills.add(ver to pill)
            modeRow.addView(pill, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dpToPx(context, 4), 0)
            })
        }
        modeScroll.addView(modeRow)
        headerRow.addView(modeScroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        val browserBtn = TextView(context).apply {
            text = "↗"
            setTextColor(Color.parseColor("#8AB4F8"))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(context, 6), 0, dpToPx(context, 6), 0)
            setOnClickListener { openExternalSearch(context, currentQuery) }
        }
        headerRow.addView(browserBtn)

        val dockBtn = TextView(context).apply {
            text = "◀"
            setTextColor(Color.parseColor("#81C995"))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(context, 6), 0, dpToPx(context, 6), 0)
            setOnClickListener { dockToEdge(context) }
        }
        headerRow.addView(dockBtn)

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#E06060"))
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(context, 6), 0, dpToPx(context, 6), 0)
            setOnClickListener { hideOverlay() }
        }
        headerRow.addView(closeBtn)

        contentColumn.addView(headerRow, headerRowLp)

        // Search Bar
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
        val searchBarLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
        searchBarLayout.addView(editSearch, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

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

        // Chips Row
        val chipsScroll = HorizontalScrollView(context).apply { isHorizontalScrollBarEnabled = false }
        val chipsScrollLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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

        // Inline Add-Tag Row
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
        val addTagRowLp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
        addTagRow.addView(tagEdit, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

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
            setOnClickListener { confirmNewTag(context) }
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

        // Progress bar (sleek 2.5dp indicator line)
        val pBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = false
            max = 100
            visibility = View.GONE
        }
        progressBar = pBar
        contentColumn.addView(pBar, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(context, 2.5f)))

        // Webview Container (with solid dark background, NEVER transparent to avoid black holes!)
        val webBox = FrameLayout(context).apply {
            val wvDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#161922"))
                cornerRadius = dpToPx(context, 10).toFloat()
            }
            background = wvDrawable
            clipToOutline = true
        }
        webContainer = webBox

        val wv = WebView(context).apply {
            setBackgroundColor(Color.parseColor("#161922"))
            isVerticalScrollBarEnabled = true
            isHorizontalScrollBarEnabled = false

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                loadsImagesAutomatically = true
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
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    progressBar?.visibility = View.GONE
                    // Trigger high-resolution image extraction
                    view?.evaluateJavascript(JS_IMAGE_EXTRACTION_SCRIPT, null)
                    Handler(Looper.getMainLooper()).postDelayed({
                        view?.evaluateJavascript(JS_IMAGE_EXTRACTION_SCRIPT, null)
                    }, 600)
                }

                override fun onReceivedError(view: WebView?, errorCode: Int, description: String?, failingUrl: String?) {
                    Log.e(TAG, "WebView error: $errorCode $description on $failingUrl")
                    progressBar?.visibility = View.GONE
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }
        }
        webBox.addView(wv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        webView = wv

        contentColumn.addView(webBox, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        mainLayout.addView(contentColumn, contentLp)
        root.addView(mainLayout, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /**
     * Builds clean, edge-to-edge photo viewer for V6 and V7.
     * ZERO Google headers, ZERO Google search web chrome, ZERO bulky borders.
     * Equipped with Infinite Scroll listener.
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun buildCleanPhotosContainer(
        context: Context,
        root: FrameLayout,
        wmParams: WindowManager.LayoutParams
    ) {
        val container = FrameLayout(context).apply {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#E60D0F14"))
                cornerRadius = dpToPx(context, 20).toFloat()
                setStroke(dpToPx(context, 1), Color.parseColor("#2C3140"))
            }
            background = bg
            clipToOutline = true
            visibility = View.GONE
        }
        cleanPhotosContainer = container

        // 1. V6: Vertical photos stream with infinite scroll
        val verticalScroll = ScrollView(context).apply {
            isVerticalScrollBarEnabled = false
            viewTreeObserver.addOnScrollChangedListener {
                val vCol = verticalPhotosContainer ?: return@addOnScrollChangedListener
                if (currentVersion == InterfaceVersion.EXTRACTED_IMAGES_SCROLL) {
                    val diff = (vCol.bottom - (height + scrollY))
                    if (diff <= dpToPx(context, 350) && !isLoadingMorePhotos) {
                        requestMorePhotosFromWeb()
                    }
                }
            }
        }
        vScroll = verticalScroll
        val vCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(context, 10), dpToPx(context, 50), dpToPx(context, 10), dpToPx(context, 24))
        }
        verticalPhotosContainer = vCol
        verticalScroll.addView(vCol)
        container.addView(verticalScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 2. V7: Horizontal photos carousel with infinite scroll
        val horizontalScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            viewTreeObserver.addOnScrollChangedListener {
                val hRow = horizontalPhotosContainer ?: return@addOnScrollChangedListener
                if (currentVersion == InterfaceVersion.CAROUSEL_PHOTO_CARDS) {
                    val diff = (hRow.right - (width + scrollX))
                    if (diff <= dpToPx(context, 350) && !isLoadingMorePhotos) {
                        requestMorePhotosFromWeb()
                    }
                }
            }
        }
        hScroll = horizontalScroll
        val hRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dpToPx(context, 12), dpToPx(context, 50), dpToPx(context, 12), dpToPx(context, 16))
        }
        horizontalPhotosContainer = hRow
        horizontalScroll.addView(hRow)
        container.addView(horizontalScroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // 3. TOP FLOATING CONTROL BAR (Translucent, subtle floating pill over the photos)
        val floatingControlPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            val pillBg = GradientDrawable().apply {
                setColor(Color.parseColor("#CC181C26"))
                cornerRadius = dpToPx(context, 16).toFloat()
                setStroke(dpToPx(context, 1), Color.parseColor("#444F68"))
            }
            background = pillBg
            setPadding(dpToPx(context, 10), dpToPx(context, 4), dpToPx(context, 10), dpToPx(context, 4))
        }
        val pillLp = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setMargins(dpToPx(context, 12), dpToPx(context, 8), dpToPx(context, 12), 0)
        }

        val titleBadge = TextView(context).apply {
            text = "📸 Fotos"
            setTextColor(Color.WHITE)
            textSize = 11.5f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
        }
        cleanTitleBadge = titleBadge
        floatingControlPill.addView(titleBadge)

        val statusBadge = TextView(context).apply {
            text = " • Fotos"
            setTextColor(Color.parseColor("#8AB4F8"))
            textSize = 10.5f
        }
        photosLoadingBadge = statusBadge
        floatingControlPill.addView(statusBadge)

        val spacer = View(context)
        floatingControlPill.addView(spacer, LinearLayout.LayoutParams(0, 0, 1f))

        // Version switch pills in clean container
        InterfaceVersion.entries.forEach { ver ->
            val isCurrent = ver == currentVersion
            val verLabel = when (ver) {
                InterfaceVersion.BOTTOM_HORIZONTAL_CARDS -> "V1"
                InterfaceVersion.SIDE_VERTICAL_CARDS -> "V2"
                InterfaceVersion.BOTTOM_SHEET -> "V5"
                InterfaceVersion.EXTRACTED_IMAGES_SCROLL -> "V6"
                InterfaceVersion.CAROUSEL_PHOTO_CARDS -> "V7"
            }
            val p = TextView(context).apply {
                text = verLabel
                setTextColor(if (isCurrent) Color.WHITE else Color.parseColor("#999999"))
                textSize = 10f
                typeface = android.graphics.Typeface.DEFAULT_BOLD
                val pBg = GradientDrawable().apply {
                    setColor(if (isCurrent) Color.parseColor("#4285F4") else Color.parseColor("#262A36"))
                    cornerRadius = dpToPx(context, 6).toFloat()
                }
                background = pBg
                setPadding(dpToPx(context, 6), dpToPx(context, 2), dpToPx(context, 6), dpToPx(context, 2))
                setOnClickListener { CreatorDetectionBus.setInterfaceVersion(ver) }
            }
            cleanVersionPills.add(ver to p)
            floatingControlPill.addView(p, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dpToPx(context, 4), 0)
            })
        }

        val dockBtn = TextView(context).apply {
            text = "◀"
            setTextColor(Color.parseColor("#81C995"))
            textSize = 12f
            setPadding(dpToPx(context, 4), 0, dpToPx(context, 4), 0)
            setOnClickListener { dockToEdge(context) }
        }
        floatingControlPill.addView(dockBtn)

        val closeBtn = TextView(context).apply {
            text = "✕"
            setTextColor(Color.parseColor("#E06060"))
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(context, 6), 0, dpToPx(context, 2), 0)
            setOnClickListener { hideOverlay() }
        }
        floatingControlPill.addView(closeBtn)

        container.addView(floatingControlPill, pillLp)
        setupBottomSheetHeightResizeListener(context, floatingControlPill, wmParams)
        root.addView(container, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
    }

    /**
     * Infinite scroll trigger: Scrolls Google search DOM by 2500px to trigger dynamic loading of next photos.
     */
    private fun requestMorePhotosFromWeb() {
        isLoadingMorePhotos = true
        Log.d(TAG, "🔄 Infinite scroll: requesting more photos from web")
        photosLoadingBadge?.text = " • Carregando mais..."

        runOnMainThread {
            webView?.evaluateJavascript("window.scrollBy(0, 2500);", null)
            Handler(Looper.getMainLooper()).postDelayed({
                webView?.evaluateJavascript(JS_IMAGE_EXTRACTION_SCRIPT, null)
                isLoadingMorePhotos = false
            }, 600)
        }
    }

    private fun handleExtractedImages(context: Context, urls: List<String>) {
        runOnMainThread {
            if (urls.isNotEmpty()) {
                val previousCount = extractedImageUrls.size
                var newlyAdded = 0
                for (u in urls) {
                    if (!extractedImageUrls.contains(u)) {
                        extractedImageUrls.add(u)
                        newlyAdded++
                    }
                }
                Log.d(TAG, "📸 Extracted ${urls.size} photos (new: $newlyAdded, total: ${extractedImageUrls.size})")

                if (newlyAdded > 0 || previousCount == 0) {
                    populateCleanPhotosUi(context)
                }
            }
        }
    }

    private fun populateCleanPhotosUi(context: Context) {
        val nick = CreatorDetectionBus.latestDetectedNickname.value
            ?: currentQuery.split(" ").firstOrNull()
            ?: "criador"
        val activeKw = CreatorDetectionBus.activeSearchKeyword.value

        val imagesToDisplay: List<String> = if (extractedImageUrls.isNotEmpty()) {
            extractedImageUrls
        } else {
            val sample = CreatorRepository.sampleVideos.find { it.nickname.contains(nick, ignoreCase = true) }
                ?: CreatorRepository.sampleVideos.first()
            sample.imageResults.map { it.assetFileName ?: it.title }
        }

        photosLoadingBadge?.text = " • ${imagesToDisplay.size} fotos"

        if (currentVersion == InterfaceVersion.EXTRACTED_IMAGES_SCROLL) {
            // V6: Vertical photos list (large, full width, stacked)
            val vCol = verticalPhotosContainer ?: return
            vCol.removeAllViews()

            imagesToDisplay.forEachIndexed { index, imgSource ->
                val card = FrameLayout(context).apply {
                    val cBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#171A24"))
                        cornerRadius = dpToPx(context, 16).toFloat()
                    }
                    background = cBg
                    clipToOutline = true
                    setOnClickListener { openExternalSearch(context, "$nick $activeKw") }
                }
                val cardLp = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dpToPx(context, 340)
                ).apply {
                    setMargins(0, 0, 0, dpToPx(context, 12))
                }

                val iv = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                loadImageItem(context, imgSource, iv)
                card.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

                // Corner tag
                val badge = TextView(context).apply {
                    text = "@$nick • #${index + 1}"
                    setTextColor(Color.WHITE)
                    textSize = 10f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    val bBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#B3000000"))
                        cornerRadius = dpToPx(context, 6).toFloat()
                    }
                    background = bBg
                    setPadding(dpToPx(context, 8), dpToPx(context, 3), dpToPx(context, 8), dpToPx(context, 3))
                }
                card.addView(badge, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    setMargins(dpToPx(context, 10), 0, 0, dpToPx(context, 10))
                })

                vCol.addView(card, cardLp)
            }
        } else if (currentVersion == InterfaceVersion.CAROUSEL_PHOTO_CARDS) {
            // V7: Horizontal photos carousel (large, edge-to-edge swipe sideways)
            val hRow = horizontalPhotosContainer ?: return
            hRow.removeAllViews()

            val dm = context.resources.displayMetrics
            val cardW = (dm.widthPixels * 0.88f).toInt()

            imagesToDisplay.forEachIndexed { index, imgSource ->
                val card = FrameLayout(context).apply {
                    val cBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#171A24"))
                        cornerRadius = dpToPx(context, 18).toFloat()
                    }
                    background = cBg
                    clipToOutline = true
                    setOnClickListener { openExternalSearch(context, "$nick $activeKw") }
                }
                val cardLp = LinearLayout.LayoutParams(
                    cardW,
                    ViewGroup.LayoutParams.MATCH_PARENT
                ).apply {
                    setMargins(0, 0, dpToPx(context, 12), 0)
                }

                val iv = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.CENTER_CROP
                }
                loadImageItem(context, imgSource, iv)
                card.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

                // Corner tag
                val badge = TextView(context).apply {
                    text = "@$nick • #$activeKw (${index + 1}/${imagesToDisplay.size})"
                    setTextColor(Color.WHITE)
                    textSize = 10.5f
                    typeface = android.graphics.Typeface.DEFAULT_BOLD
                    val bBg = GradientDrawable().apply {
                        setColor(Color.parseColor("#B3000000"))
                        cornerRadius = dpToPx(context, 6).toFloat()
                    }
                    background = bBg
                    setPadding(dpToPx(context, 8), dpToPx(context, 4), dpToPx(context, 8), dpToPx(context, 4))
                }
                card.addView(badge, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM or Gravity.START
                ).apply {
                    setMargins(dpToPx(context, 12), 0, 0, dpToPx(context, 12))
                })

                hRow.addView(card, cardLp)
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
            row.addView(chip, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(0, 0, dpToPx(context, 6), 0)
            })
        }

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
        row.addView(addBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, 0, dpToPx(context, 4), 0)
        })
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

    fun updateQuery(newQuery: String, forceReload: Boolean = false) {
        if (newQuery.isBlank()) return
        val queryChanged = (currentQuery != newQuery)
        currentQuery = newQuery

        runOnMainThread {
            searchEditText?.setText(newQuery)
            chipsRowLayout?.let { row ->
                lastContext?.let { ctx -> renderTagChips(ctx, row) }
            }
            if (queryChanged) {
                extractedImageUrls.clear()
            }
            if (currentVersion == InterfaceVersion.EXTRACTED_IMAGES_SCROLL || currentVersion == InterfaceVersion.CAROUSEL_PHOTO_CARDS) {
                lastContext?.let { populateCleanPhotosUi(it) }
            }
            loadSearchQuery(newQuery, force = forceReload || queryChanged)
        }
    }

    private fun loadSearchQuery(query: String, force: Boolean = false) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val searchUrl = "https://www.google.com/search?tbm=isch&q=$encoded"

        if (!force && searchUrl == lastLoadedUrl) {
            return
        }
        lastLoadedUrl = searchUrl

        Log.d(TAG, "⚡ loadSearchQuery: loading $searchUrl (force=$force)")
        progressBar?.visibility = View.VISIBLE
        photosLoadingBadge?.text = " • Buscando..."

        webView?.let { wv ->
            wv.stopLoading()
            wv.loadUrl(searchUrl)
        }
    }

    private fun dockToEdge(context: Context) {
        isSidebarDocked = true
        mainContainer?.visibility = View.GONE
        cleanPhotosContainer?.visibility = View.GONE
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

        val wm = windowManager ?: return
        val root = overlayRootView ?: return
        val wmParams = currentWmParams ?: return

        applyVersionGeometry(context, wmParams, currentVersion)
        try {
            wm.updateViewLayout(root, wmParams)
        } catch (_: Exception) {}

        applyVersionDisplayState(context, currentVersion)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupBottomSheetHeightResizeListener(
        context: Context,
        handle: View,
        wmParams: WindowManager.LayoutParams
    ) {
        var startY = 0f
        var startHeight = 0
        val dm = context.resources.displayMetrics
        val minHeight = dpToPx(context, 180)
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

    /**
     * 100% natural, intuitive 1:1 drag listener:
     * - Drag left: moves left
     * - Drag right: moves right
     * - Drag up: moves up
     * - Drag down: moves down
     */
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
                    val loc = IntArray(2)
                    overlayRootView?.getLocationOnScreen(loc)
                    initialX = loc[0]
                    initialY = loc[1]
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isClick = true

                    wmParams.gravity = Gravity.TOP or Gravity.START
                    wmParams.x = initialX
                    wmParams.y = initialY
                    try {
                        windowManager?.updateViewLayout(overlayRootView, wmParams)
                    } catch (_: Exception) {}
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    if (Math.abs(dx) > dpToPx(context, 4) || Math.abs(dy) > dpToPx(context, 4)) {
                        isClick = false

                        val dm = context.resources.displayMetrics
                        val maxX = (dm.widthPixels - dpToPx(context, 40)).coerceAtLeast(0)
                        val maxY = (dm.heightPixels - dpToPx(context, 40)).coerceAtLeast(0)

                        wmParams.x = (initialX + dx).coerceIn(0, maxX)
                        wmParams.y = (initialY + dy).coerceIn(0, maxY)

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

    private fun destroyCurrentOverlayViews() {
        Log.d(TAG, "destroyCurrentOverlayViews: releasing views and WebView")

        webView?.let { wv ->
            try {
                wv.stopLoading()
                wv.loadUrl("about:blank")
                wv.clearHistory()
                wv.onPause()
                (wv.parent as? ViewGroup)?.removeView(wv)
                wv.destroy()
            } catch (e: Exception) {
                Log.e(TAG, "Error destroying WebView", e)
            }
        }
        webView = null

        overlayRootView?.let { root ->
            try {
                if (root.isAttachedToWindow) {
                    windowManager?.removeViewImmediate(root)
                } else {
                    windowManager?.removeView(root)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error removing root from WindowManager", e)
            }
        }
        overlayRootView = null

        mainContainer = null
        cleanPhotosContainer = null
        dockedEdgeTab = null
        searchEditText = null
        newTagEditText = null
        addTagInlineContainer = null
        chipsRowLayout = null
        progressBar = null
        verticalPhotosContainer = null
        horizontalPhotosContainer = null
        cleanTitleBadge = null
        photosLoadingBadge = null
        vScroll = null
        hScroll = null
        versionPills.clear()
        cleanVersionPills.clear()
        currentWmParams = null
        lastLoadedUrl = ""
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
     * JavaScript Bridge receiving high-res extracted image URLs from Google Search DOM
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
