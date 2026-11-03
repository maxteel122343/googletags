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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Manages the real system WindowManager floating overlay over TikTok, Instagram,
 * YouTube Shorts, or any third-party app.
 *
 * Renders an interactive floating window with an integrated WebView showing live
 * Google Search / Google Images for the detected creator.
 */
object FloatingOverlayManager {

    private var windowManager: WindowManager? = null
    private var overlayRootView: View? = null
    private var webView: WebView? = null
    private var titleTextView: TextView? = null
    private var progressBar: ProgressBar? = null
    private var expandedContainer: LinearLayout? = null
    private var minimizedPill: LinearLayout? = null
    private var minimizedTitle: TextView? = null

    private var isExpanded = true
    private var currentQuery = "ju.pasqualotto fotos"
    private var lastLoadedUrl = ""

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

    fun isShowing(): Boolean = overlayRootView != null

    @SuppressLint("ClickableViewAccessibility", "SetJavaScriptEnabled")
    fun showOverlay(context: Context, initialQuery: String) {
        if (overlayRootView != null) {
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
            gravity = Gravity.TOP or Gravity.START
            x = dpToPx(appContext, 16)
            y = dpToPx(appContext, 80)
        }

        // Build the Overlay View hierarchy
        val root = FrameLayout(appContext)
        overlayRootView = root

        // --- EXPANDED CONTAINER ---
        val expanded = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            val backgroundDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#EE16181E"))
                cornerRadius = dpToPx(appContext, 18).toFloat()
                setStroke(dpToPx(appContext, 1), Color.parseColor("#444B5A"))
            }
            background = backgroundDrawable
            elevation = dpToPx(appContext, 16).toFloat()
            setPadding(
                dpToPx(appContext, 12),
                dpToPx(appContext, 10),
                dpToPx(appContext, 12),
                dpToPx(appContext, 10)
            )
        }
        val expandedLp = FrameLayout.LayoutParams(
            dpToPx(appContext, 340),
            dpToPx(appContext, 440)
        )
        expanded.layoutParams = expandedLp
        expandedContainer = expanded

        // Drag bar handle at top
        val dragHandle = View(appContext).apply {
            val handleDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#668AB4F8"))
                cornerRadius = dpToPx(appContext, 3).toFloat()
            }
            background = handleDrawable
        }
        val dragHandleLp = LinearLayout.LayoutParams(
            dpToPx(appContext, 40),
            dpToPx(appContext, 4)
        ).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setMargins(0, 0, 0, dpToPx(appContext, 8))
        }
        expanded.addView(dragHandle, dragHandleLp)

        // Header: Badge + Title + Minimize + Close
        val headerRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val headerRowLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dpToPx(appContext, 8))
        }

        val googlePill = TextView(appContext).apply {
            text = "G Live"
            setTextColor(Color.WHITE)
            textSize = 10f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val pillDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#4285F4"))
                cornerRadius = dpToPx(appContext, 10).toFloat()
            }
            background = pillDrawable
            setPadding(dpToPx(appContext, 8), dpToPx(appContext, 2), dpToPx(appContext, 8), dpToPx(appContext, 2))
        }
        headerRow.addView(googlePill)

        val title = TextView(appContext).apply {
            text = currentQuery
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        val titleLp = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            setMargins(dpToPx(appContext, 8), 0, dpToPx(appContext, 8), 0)
        }
        titleTextView = title
        headerRow.addView(title, titleLp)

        // Browser button
        val browserBtn = TextView(appContext).apply {
            text = "↗"
            setTextColor(Color.parseColor("#8AB4F8"))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(appContext, 6), 0, dpToPx(appContext, 6), 0)
            setOnClickListener {
                openExternalSearch(appContext, currentQuery)
            }
        }
        headerRow.addView(browserBtn)

        // Minimize button
        val minimizeBtn = TextView(appContext).apply {
            text = "—"
            setTextColor(Color.parseColor("#B0B0B0"))
            textSize = 16f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(appContext, 6), 0, dpToPx(appContext, 6), 0)
            setOnClickListener {
                toggleMinimized(appContext, wmParams)
            }
        }
        headerRow.addView(minimizeBtn)

        // Close button
        val closeBtn = TextView(appContext).apply {
            text = "✕"
            setTextColor(Color.parseColor("#E06060"))
            textSize = 15f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            setPadding(dpToPx(appContext, 6), 0, dpToPx(appContext, 6), 0)
            setOnClickListener {
                hideOverlay()
            }
        }
        headerRow.addView(closeBtn)

        expanded.addView(headerRow, headerRowLp)

        // Search Chips Row (fotos, beach, look, routine, instagram)
        val chipsScroll = HorizontalScrollView(appContext).apply {
            isHorizontalScrollBarEnabled = false
        }
        val chipsScrollLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            setMargins(0, 0, 0, dpToPx(appContext, 6))
        }

        val chipsRow = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val templateKeywords = listOf("fotos", "beach", "look", "routine", "instagram")
        templateKeywords.forEach { kw ->
            val chip = TextView(appContext).apply {
                text = kw
                setTextColor(Color.parseColor("#D0D0D0"))
                textSize = 11f
                val chipDrawable = GradientDrawable().apply {
                    setColor(Color.parseColor("#252A36"))
                    cornerRadius = dpToPx(appContext, 8).toFloat()
                    setStroke(dpToPx(appContext, 1), Color.parseColor("#383E4E"))
                }
                background = chipDrawable
                setPadding(dpToPx(appContext, 8), dpToPx(appContext, 3), dpToPx(appContext, 8), dpToPx(appContext, 3))
                setOnClickListener {
                    val baseNick = CreatorDetectionBus.latestDetectedNickname.value ?: "ju.pasqualotto"
                    CreatorDetectionBus.setActiveKeyword(kw)
                    updateQuery("$baseNick $kw")
                }
            }
            val chipLp = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, dpToPx(appContext, 6), 0)
            }
            chipsRow.addView(chip, chipLp)
        }
        chipsScroll.addView(chipsRow)
        expanded.addView(chipsScroll, chipsScrollLp)

        // Progress Bar
        val pBar = ProgressBar(appContext, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            visibility = View.GONE
        }
        val pBarLp = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dpToPx(appContext, 3)
        )
        progressBar = pBar
        expanded.addView(pBar, pBarLp)

        // Real Interactive WebView
        val wv = WebView(appContext).apply {
            val wvDrawable = GradientDrawable().apply {
                setColor(Color.parseColor("#121212"))
                cornerRadius = dpToPx(appContext, 12).toFloat()
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
        expanded.addView(wv, wvLp)

        root.addView(expanded)

        // --- MINIMIZED PILL VIEW ---
        val pill = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            val pillBg = GradientDrawable().apply {
                setColor(Color.parseColor("#EE1B1E28"))
                cornerRadius = dpToPx(appContext, 24).toFloat()
                setStroke(dpToPx(appContext, 1.5f), Color.parseColor("#4285F4"))
            }
            background = pillBg
            elevation = dpToPx(appContext, 12).toFloat()
            setPadding(
                dpToPx(appContext, 12),
                dpToPx(appContext, 6),
                dpToPx(appContext, 14),
                dpToPx(appContext, 6)
            )
            setOnClickListener {
                toggleMinimized(appContext, wmParams)
            }
        }
        val pillLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            dpToPx(appContext, 44)
        )
        pill.layoutParams = pillLp
        minimizedPill = pill

        val pillG = TextView(appContext).apply {
            text = "G"
            setTextColor(Color.WHITE)
            textSize = 13f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val gCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#4285F4"))
            }
            background = gCircle
            gravity = Gravity.CENTER
        }
        val pillGLp = LinearLayout.LayoutParams(dpToPx(appContext, 24), dpToPx(appContext, 24)).apply {
            setMargins(0, 0, dpToPx(appContext, 8), 0)
        }
        pill.addView(pillG, pillGLp)

        val pillText = TextView(appContext).apply {
            text = currentQuery
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            maxLines = 1
        }
        minimizedTitle = pillText
        pill.addView(pillText)

        root.addView(pill)

        // Touch listener for dragging the entire overlay window
        setupDraggableTouchListener(appContext, dragHandle, wmParams)
        setupDraggableTouchListener(appContext, headerRow, wmParams)
        setupDraggableTouchListener(appContext, pill, wmParams)

        // Add to WindowManager
        try {
            windowManager?.addView(root, wmParams)
            CreatorDetectionBus.setOverlayShowing(true)
            loadSearchQuery(currentQuery)
        } catch (_: Exception) {
            hideOverlay()
        }
    }

    private fun toggleMinimized(context: Context, wmParams: WindowManager.LayoutParams) {
        isExpanded = !isExpanded
        if (isExpanded) {
            expandedContainer?.visibility = View.VISIBLE
            minimizedPill?.visibility = View.GONE
        } else {
            expandedContainer?.visibility = View.GONE
            minimizedPill?.visibility = View.VISIBLE
            minimizedTitle?.text = currentQuery
        }
        try {
            windowManager?.updateViewLayout(overlayRootView, wmParams)
        } catch (_: Exception) {
        }
    }

    fun updateQuery(newQuery: String) {
        if (newQuery.isBlank()) return
        currentQuery = newQuery

        runOnMainThread {
            titleTextView?.text = newQuery
            minimizedTitle?.text = newQuery
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

    private fun openExternalSearch(context: Context, query: String) {
        val encoded = URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        val url = "https://www.google.com/search?tbm=isch&q=$encoded"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
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
                        wmParams.x = initialX + dx
                        wmParams.y = initialY + dy
                        try {
                            windowManager?.updateViewLayout(overlayRootView, wmParams)
                        } catch (_: Exception) {
                        }
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
                expandedContainer = null
                minimizedPill = null
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
