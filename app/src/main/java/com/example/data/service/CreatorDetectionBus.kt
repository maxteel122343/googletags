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

    private val _activeInterfaceVersion = MutableStateFlow(com.example.data.model.InterfaceVersion.BOTTOM_HORIZONTAL_CARDS)
    val activeInterfaceVersion: StateFlow<com.example.data.model.InterfaceVersion> = _activeInterfaceVersion.asStateFlow()

    private val _availableKeywords = MutableStateFlow(listOf("fotos", "beach", "look", "routine", "instagram"))
    val availableKeywords: StateFlow<List<String>> = _availableKeywords.asStateFlow()

    fun resetForTesting() {
        _latestDetectedNickname.value = null
        _latestPackageName.value = null
        _isAccessibilityConnected.value = false
        _isOverlayShowing.value = false
        _activeSearchKeyword.value = "fotos"
        _activeInterfaceVersion.value = com.example.data.model.InterfaceVersion.BOTTOM_HORIZONTAL_CARDS
        _availableKeywords.value = listOf("fotos", "beach", "look", "routine", "instagram")
        FloatingOverlayManager.resetForTesting()
    }

    fun setAccessibilityConnected(connected: Boolean) {
        _isAccessibilityConnected.value = connected
    }

    fun setOverlayShowing(showing: Boolean) {
        _isOverlayShowing.value = showing
    }

    fun setActiveKeyword(keyword: String) {
        _activeSearchKeyword.value = keyword
    }

    fun addKeyword(keyword: String) {
        val clean = keyword.trim().lowercase()
        if (clean.isNotBlank() && !_availableKeywords.value.contains(clean)) {
            _availableKeywords.value = _availableKeywords.value + clean
        }
        _activeSearchKeyword.value = clean
    }

    fun setInterfaceVersion(version: com.example.data.model.InterfaceVersion) {
        _activeInterfaceVersion.value = version
        if (FloatingOverlayManager.isShowing()) {
            FloatingOverlayManager.switchInterfaceVersion(version)
        }
    }

    fun dispatchDetectedCreator(nickname: String, packageName: String, context: Context? = null) {
        val cleanNick = nickname.trim().removePrefix("@")
        if (cleanNick.isBlank()) return

        _latestDetectedNickname.value = cleanNick
        _latestPackageName.value = packageName

        if (FloatingOverlayManager.isShowing()) {
            val query = if (_activeSearchKeyword.value.isNotBlank()) {
                "$cleanNick ${_activeSearchKeyword.value}"
            } else {
                cleanNick
            }
            // Update query smoothly without cancelling current search if query is identical
            FloatingOverlayManager.updateQuery(query, forceReload = false)
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
