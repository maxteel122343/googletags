package com.example.data.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Android Accessibility Service for detecting creator profiles & nicknames
 * across TikTok, Instagram Reels, YouTube Shorts, Facebook Reels, and Bluesky.
 *
 * When a creator is detected during scroll or window change, it dispatches the creator
 * to CreatorDetectionBus and immediately triggers FloatingOverlayManager.updateQuery
 * with forceReload = true.
 */
class CreatorDetectorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CreatorDetectorService"
        private val USERNAME_AT_REGEX = Regex("@[a-zA-Z0-9._]{2,32}")
        private val BLUESKY_HANDLE_REGEX = Regex("([a-zA-Z0-9_-]+\\.bsky\\.social)")

        // Ignore common non-username words that might have @ or symbols
        private val IGNORED_WORDS = setOf(
            "gmail.com", "yahoo.com", "hotmail.com", "outlook.com",
            "reply", "share", "follow", "following", "subscribe", "subscribers",
            "home", "search", "explore", "reels", "profile", "audio", "original",
            "curtir", "seguir", "seguindo", "compartilhar", "comentar"
        )
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

        // Throttle events slightly (40ms) for ultra-fast, smooth scroll detection without UI lag
        val now = System.currentTimeMillis()
        if (now - lastEventTime < 40) return
        lastEventTime = now

        // Find the most appropriate node hierarchy
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
                "com.facebook.katana" -> extractFacebookCreator(targetNode)
                "xyz.blueskyweb.app" -> extractBlueskyCreator(targetNode)
                else -> null
            } ?: scanRecursivelyForCreator(targetNode, depth = 0, nodeCount = 0)

            if (!detected.isNullOrBlank()) {
                val cleanNickname = sanitizeNickname(detected)
                if (cleanNickname.isNotBlank() && !IGNORED_WORDS.contains(cleanNickname.lowercase())) {
                    val changed = (cleanNickname != lastDetectedNickname)
                    if (changed) {
                        Log.d(TAG, "⚡ Scroll creator detected: @$cleanNickname (was: @$lastDetectedNickname) in $packageName")
                    }
                    lastDetectedNickname = cleanNickname

                    // 1. Dispatch event via bus
                    CreatorDetectionBus.dispatchDetectedCreator(
                        nickname = cleanNickname,
                        packageName = packageName,
                        context = applicationContext
                    )

                    // 2. Direct immediate trigger on FloatingOverlayManager to guarantee instant reload
                    if (FloatingOverlayManager.isShowing()) {
                        val activeKeyword = CreatorDetectionBus.activeSearchKeyword.value
                        val fullQuery = if (activeKeyword.isNotBlank()) {
                            "$cleanNickname $activeKeyword"
                        } else {
                            cleanNickname
                        }
                        FloatingOverlayManager.updateQuery(fullQuery, forceReload = true)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error inspecting accessibility hierarchy for $packageName", e)
        }
    }

    private fun isTargetPackage(pkg: String): Boolean {
        return pkg in listOf(
            "com.zhiliaoapp.musically",
            "com.ss.android.ugc.trill",
            "com.instagram.android",
            "com.google.android.youtube",
            "com.facebook.katana",
            "xyz.blueskyweb.app"
        )
    }

    private fun extractTikTokCreator(root: AccessibilityNodeInfo): String? {
        val candidateIds = listOf(
            "com.zhiliaoapp.musically:id/author_name",
            "com.zhiliaoapp.musically:id/title",
            "com.zhiliaoapp.musically:id/author_username",
            "com.ss.android.ugc.trill:id/author_name",
            "com.ss.android.ugc.trill:id/title",
            "com.ss.android.ugc.trill:id/author_username"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString()
                    if (!text.isNullOrBlank()) {
                        val clean = sanitizeNickname(text)
                        if (clean.isNotBlank()) return clean
                    }
                }
            }
        }
        return null
    }

    private fun extractInstagramCreator(root: AccessibilityNodeInfo): String? {
        val candidateIds = listOf(
            "com.instagram.android:id/row_feed_photo_profile_name",
            "com.instagram.android:id/row_feed_textview_user_id",
            "com.instagram.android:id/clips_author_name",
            "com.instagram.android:id/reel_viewer_title",
            "com.instagram.android:id/action_bar_title_switcher",
            "com.instagram.android:id/action_bar_title",
            "com.instagram.android:id/secondary_label",
            "com.instagram.android:id/profile_header_username",
            "com.instagram.android:id/user_name",
            "com.instagram.android:id/tag_inner_button"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString() ?: node.contentDescription?.toString()
                    if (!text.isNullOrBlank()) {
                        val clean = sanitizeNickname(text)
                        if (clean.isNotBlank()) return clean
                    }
                }
            }
        }
        return null
    }

    private fun extractYouTubeShortsCreator(root: AccessibilityNodeInfo): String? {
        val candidateIds = listOf(
            "com.google.android.youtube:id/reel_channel_name",
            "com.google.android.youtube:id/channel_name",
            "com.google.android.youtube:id/reel_player_channel_name",
            "com.google.android.youtube:id/author_text",
            "com.google.android.youtube:id/owner_name",
            "com.google.android.youtube:id/shorts_channel_name",
            "com.google.android.youtube:id/sub_channel_name"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString() ?: node.contentDescription?.toString()
                    if (!text.isNullOrBlank()) {
                        val clean = sanitizeNickname(text)
                        if (clean.isNotBlank()) return clean
                    }
                }
            }
        }
        return null
    }

    private fun extractFacebookCreator(root: AccessibilityNodeInfo): String? {
        val candidateIds = listOf(
            "com.facebook.katana:id/fb_reels_author",
            "com.facebook.katana:id/reels_tray_author",
            "com.facebook.katana:id/author_name",
            "com.facebook.katana:id/title_author",
            "com.facebook.katana:id/feed_actor_name",
            "com.facebook.katana:id/video_author",
            "com.facebook.katana:id/header_title"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString() ?: node.contentDescription?.toString()
                    if (!text.isNullOrBlank()) {
                        val clean = sanitizeNickname(text)
                        if (clean.isNotBlank()) return clean
                    }
                }
            }
        }
        return null
    }

    private fun extractBlueskyCreator(root: AccessibilityNodeInfo): String? {
        val candidateIds = listOf(
            "xyz.blueskyweb.app:id/author_handle",
            "xyz.blueskyweb.app:id/post_author",
            "xyz.blueskyweb.app:id/profile_handle",
            "xyz.blueskyweb.app:id/username"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val text = node.text?.toString() ?: node.contentDescription?.toString()
                    if (!text.isNullOrBlank()) {
                        val clean = sanitizeNickname(text)
                        if (clean.isNotBlank()) return clean
                    }
                }
            }
        }
        return null
    }

    /**
     * Enhanced recursive scan up to depth 18 with visit counter to prevent UI blocking.
     * Evaluates text and content descriptions against @username and Bluesky handles.
     */
    private fun scanRecursivelyForCreator(node: AccessibilityNodeInfo?, depth: Int, nodeCount: Int): String? {
        if (node == null || depth > 18 || nodeCount > 180) return null

        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            val atMatch = USERNAME_AT_REGEX.find(text)
            if (atMatch != null) {
                val clean = sanitizeNickname(atMatch.value)
                if (clean.isNotBlank() && !IGNORED_WORDS.contains(clean.lowercase())) {
                    return clean
                }
            }
            val bskyMatch = BLUESKY_HANDLE_REGEX.find(text)
            if (bskyMatch != null) {
                return sanitizeNickname(bskyMatch.value)
            }
        }

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank()) {
            val atMatch = USERNAME_AT_REGEX.find(desc)
            if (atMatch != null) {
                val clean = sanitizeNickname(atMatch.value)
                if (clean.isNotBlank() && !IGNORED_WORDS.contains(clean.lowercase())) {
                    return clean
                }
            }
            val bskyMatch = BLUESKY_HANDLE_REGEX.find(desc)
            if (bskyMatch != null) {
                return sanitizeNickname(bskyMatch.value)
            }
        }

        var currentCount = nodeCount + 1
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            val result = scanRecursivelyForCreator(child, depth + 1, currentCount)
            if (result != null) return result
            currentCount++
        }

        return null
    }

    private fun sanitizeNickname(raw: String): String {
        return raw.trim()
            .removePrefix("@")
            .split(" ", "\n", "\t", "•", "·")[0]
            .trim()
            .removePrefix("@")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }
}
