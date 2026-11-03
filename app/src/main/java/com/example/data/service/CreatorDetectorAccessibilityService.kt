package com.example.data.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Real Android Accessibility Service for detecting creator profiles & nicknames
 * across TikTok, Instagram Reels, and YouTube Shorts.
 *
 * When a creator is detected, it dispatches the creator to CreatorDetectionBus
 * and automatically triggers the real FloatingOverlayManager WebView search!
 */
class CreatorDetectorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CreatorDetectorService"
        private val USERNAME_REGEX = Regex("@[a-zA-Z0-9._]{2,30}")
    }

    private var lastDetectedNickname: String? = null
    private var lastEventTime: Long = 0

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "Accessibility Service Connected")
        CreatorDetectionBus.setAccessibilityConnected(true)
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.d(TAG, "Accessibility Service Unbound")
        CreatorDetectionBus.setAccessibilityConnected(false)
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Accessibility Service Destroyed")
        CreatorDetectionBus.setAccessibilityConnected(false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        val packageName = event.packageName?.toString() ?: return
        if (!isTargetPackage(packageName)) return

        // Throttle events slightly (60ms) for ultra-fast and responsive scroll detection
        val now = System.currentTimeMillis()
        if (now - lastEventTime < 60) return
        lastEventTime = now

        // When a system overlay is visible, rootInActiveWindow might point to the overlay (com.example)
        // or be null. Prioritize the event source corresponding to the target package.
        val src = event.source
        val activeRoot = rootInActiveWindow
        val targetNode: AccessibilityNodeInfo = (if (src != null && src.packageName?.toString() == packageName) src else null)
            ?: (if (activeRoot != null && activeRoot.packageName?.toString() == packageName) activeRoot else null)
            ?: src
            ?: activeRoot
            ?: return

        try {
            val detected = when (packageName) {
                "com.zhiliaoapp.musically", "com.ss.android.ugc.trill" -> extractTikTokCreator(targetNode)
                "com.instagram.android" -> extractInstagramCreator(targetNode)
                "com.google.android.youtube" -> extractYouTubeShortsCreator(targetNode)
                else -> null
            } ?: scanRecursivelyForCreator(targetNode, 0)

            if (!detected.isNullOrBlank()) {
                val cleanNickname = detected.trim().removePrefix("@")
                if (cleanNickname.isNotBlank()) {
                    if (cleanNickname != lastDetectedNickname) {
                        Log.d(TAG, "Scroll/Creator changed: @$cleanNickname (previous: @$lastDetectedNickname) from $packageName")
                    }
                    lastDetectedNickname = cleanNickname

                    CreatorDetectionBus.dispatchDetectedCreator(
                        nickname = cleanNickname,
                        packageName = packageName,
                        context = applicationContext
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting accessibility hierarchy", e)
        }
    }

    private fun isTargetPackage(pkg: String): Boolean {
        return pkg in listOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.instagram.android",
            "com.google.android.youtube"
        )
    }

    private fun extractTikTokCreator(root: AccessibilityNodeInfo): String? {
        val candidateIds = listOf(
            "com.zhiliaoapp.musically:id/author_name",
            "com.zhiliaoapp.musically:id/title",
            "com.ss.android.ugc.trill:id/author_name",
            "com.ss.android.ugc.trill:id/title"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrBlank()) {
                    return sanitizeNickname(text)
                }
            }
        }
        return null
    }

    private fun extractInstagramCreator(root: AccessibilityNodeInfo): String? {
        val candidateIds = listOf(
            "com.instagram.android:id/row_feed_photo_profile_name",
            "com.instagram.android:id/clips_author_name",
            "com.instagram.android:id/action_bar_title",
            "com.instagram.android:id/reel_viewer_title"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrBlank()) {
                    return sanitizeNickname(text)
                }
            }
        }
        return null
    }

    private fun extractYouTubeShortsCreator(root: AccessibilityNodeInfo): String? {
        val candidateIds = listOf(
            "com.google.android.youtube:id/channel_name",
            "com.google.android.youtube:id/owner_name",
            "com.google.android.youtube:id/reel_channel_name"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                val text = nodes[0].text?.toString()
                if (!text.isNullOrBlank()) {
                    return sanitizeNickname(text)
                }
            }
        }
        return null
    }

    private fun scanRecursivelyForCreator(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 8) return null

        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            val match = USERNAME_REGEX.find(text)
            if (match != null) {
                return sanitizeNickname(match.value)
            }
        }

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank()) {
            val match = USERNAME_REGEX.find(desc)
            if (match != null) {
                return sanitizeNickname(match.value)
            }
        }

        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            val result = scanRecursivelyForCreator(child, depth + 1)
            if (result != null) return result
        }

        return null
    }

    private fun sanitizeNickname(raw: String): String {
        return raw.trim().removePrefix("@").split(" ", "\n", "\t")[0]
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }
}
