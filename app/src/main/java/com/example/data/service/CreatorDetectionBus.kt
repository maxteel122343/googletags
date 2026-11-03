package com.example.data.service

import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared event bus and system state controller for Accessibility detection
 * and real WindowManager floating overlays.
 */
object CreatorDetectionBus {

    private val _isAccessibilityConnected = MutableStateFlow(false)
    val isAccessibilityConnected: StateFlow<Boolean> = _isAccessibilityConnected.asStateFlow()

    private val _latestDetectedNickname = MutableStateFlow<String?>(null)
    val latestDetectedNickname: StateFlow<String?> = _latestDetectedNickname.asStateFlow()

    private val _latestPackageName = MutableStateFlow<String?>(null)
    val latestPackageName: StateFlow<String?> = _latestPackageName.asStateFlow()

    private val _isOverlayShowing = MutableStateFlow(false)
    val isOverlayShowing: StateFlow<Boolean> = _isOverlayShowing.asStateFlow()

    private val _activeSearchKeyword = MutableStateFlow("fotos")
    val activeSearchKeyword: StateFlow<String> = _activeSearchKeyword.asStateFlow()

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    fun setOverlayShowing(showing: Boolean) {
        _isOverlayShowing.value = showing
    }

    fun setActiveKeyword(keyword: String) {
        _activeSearchKeyword.value = keyword
    }

    fun dispatchDetectedCreator(nickname: String, packageName: String, context: Context? = null) {
        val cleanNick = nickname.trim().removePrefix("@")
        if (cleanNick.isBlank()) return

        _latestDetectedNickname.value = cleanNick
        _latestPackageName.value = packageName

        context?.let { ctx ->
            if (FloatingOverlayManager.isShowing()) {
                val query = if (_activeSearchKeyword.value.isNotBlank()) {
                    "$cleanNick ${_activeSearchKeyword.value}"
                } else {
                    cleanNick
                }
                FloatingOverlayManager.updateQuery(query)
            }
        }
    }

    /**
     * Checks if SYSTEM_ALERT_WINDOW is granted.
     */
    fun hasOverlayPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(context)
        } else {
            true
        }
    }

    /**
     * Launches the system settings screen to grant overlay permission.
     */
    fun requestOverlayPermission(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            try {
                context.startActivity(intent)
            } catch (_: Exception) {
                // Fallback to generic overlay settings
                context.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            }
        }
    }

    /**
     * Checks if our CreatorDetectorAccessibilityService is actively enabled in System Accessibility.
     */
    fun isAccessibilityServiceEnabled(context: Context): Boolean {
        val accessibilityManager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
            ?: return false

        val enabledServices = accessibilityManager.getEnabledAccessibilityServiceList(
            AccessibilityServiceInfo.FEEDBACK_GENERIC or AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )

        val serviceName = CreatorDetectorAccessibilityService::class.java.name
        return enabledServices.any {
            it.resolveInfo?.serviceInfo?.name?.contains(serviceName) == true ||
                    it.id?.contains(serviceName) == true
        }
    }

    /**
     * Opens system Accessibility settings so the user can enable the service.
     */
    fun openAccessibilitySettings(context: Context) {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            // Safe fallback
        }
    }

    /**
     * Toggles the real floating WindowManager overlay on or off.
     */
    fun toggleRealOverlay(context: Context, initialNickname: String = "ju.pasqualotto") {
        if (FloatingOverlayManager.isShowing()) {
            FloatingOverlayManager.hideOverlay()
            _isOverlayShowing.value = false
        } else {
            if (!hasOverlayPermission(context)) {
                requestOverlayPermission(context)
                return
            }
            val query = if (_activeSearchKeyword.value.isNotBlank()) {
                "$initialNickname ${_activeSearchKeyword.value}"
            } else {
                initialNickname
            }
            FloatingOverlayManager.showOverlay(context, query)
            _isOverlayShowing.value = true
        }
    }
}
