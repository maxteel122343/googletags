package com.example.data.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * High-performance, zero-lag Android Accessibility Service for detecting creator profiles & nicknames
 * across TikTok, Instagram Reels, YouTube Shorts, Facebook Reels, and Bluesky.
 *
 * Key features:
 * - Dynamic app switching (TikTok ↔ Instagram) with immediate screen evaluation
 * - Instagram-specific heuristics (detecting usernames without '@', profile picture descriptions, and Follow button siblings)
 * - Anti-lag throttling and single-dispatch architecture
 */
class CreatorDetectorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "CreatorDetectorService"
        private val USERNAME_AT_REGEX = Regex("@[a-zA-Z0-9._]{2,32}")
        private val BLUESKY_HANDLE_REGEX = Regex("([a-zA-Z0-9_-]+\\.bsky\\.social)")

        // Instagram avatar image content descriptions (multilingual: PT, EN, ES)
        private val INSTAGRAM_AVATAR_REGEX = Regex(
            """(?:Foto\s+d[oe]\s+perfil\s+de|Profile\s+picture\s+of|Profile\s+photo\s+of|Avatar\s+de)\s+([a-zA-Z0-9._]{2,30})""",
            RegexOption.IGNORE_CASE
        )
        private val INSTAGRAM_POSSESSIVE_REGEX = Regex(
            """([a-zA-Z0-9._]{2,30})(?:'s\s+profile\s+picture|'s\s+avatar)""",
            RegexOption.IGNORE_CASE
        )

        // General username pattern without '@'
        private val USERNAME_PLAIN_REGEX = Regex("^[a-zA-Z0-9._]{3,30}$")

        // Ignore common non-username words and UI buttons
        private val IGNORED_WORDS = setOf(
            "gmail.com", "yahoo.com", "hotmail.com", "outlook.com",
            "reply", "share", "follow", "following", "subscribe", "subscribers",
            "home", "search", "explore", "reels", "profile", "audio", "original",
            "curtir", "seguir", "seguindo", "compartilhar", "comentar", "salvar",
            "enviar", "mensagens", "stories", "direct", "publicações", "posts",
            "instagram", "tiktok", "youtube", "shorts", "facebook", "bluesky",
            "feedlens", "more", "options", "configurações", "settings", "notifications"
        )
    }

    private var lastPackageName: String? = null
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

        val isAppSwitched = (packageName != lastPackageName)
        if (isAppSwitched) {
            Log.d(TAG, "🔄 Dynamic app switch detected: $lastPackageName ➔ $packageName")
            lastPackageName = packageName
            lastDetectedNickname = null // Reset so new app's creator is immediately recognized
        }

        val eventType = event.eventType
        val isWindowStateChange = (eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)

        // Throttle rapid scroll events (40ms), but NEVER throttle app switches or window state changes
        val now = System.currentTimeMillis()
        if (!isAppSwitched && !isWindowStateChange && (now - lastEventTime < 40)) {
            return
        }
        lastEventTime = now

        // Find the active node hierarchy
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
            } ?: scanRecursivelyForCreator(targetNode, packageName, depth = 0, nodeCount = 0)

            if (!detected.isNullOrBlank()) {
                val cleanNickname = sanitizeNickname(detected)
                if (cleanNickname.length in 2..32 && !IGNORED_WORDS.contains(cleanNickname.lowercase())) {
                    val changed = (cleanNickname != lastDetectedNickname)
                    if (changed || isAppSwitched) {
                        Log.d(TAG, "⚡ Creator detected: @$cleanNickname in $packageName (changed=$changed, switched=$isAppSwitched)")
                        lastDetectedNickname = cleanNickname

                        // Dispatch to central bus - this automatically updates FloatingOverlayManager
                        // without redundant double-reloads
                        CreatorDetectionBus.dispatchDetectedCreator(
                            nickname = cleanNickname,
                            packageName = packageName,
                            context = applicationContext
                        )
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
                    val text = node.text?.toString() ?: node.contentDescription?.toString()
                    if (!text.isNullOrBlank()) {
                        val clean = sanitizeNickname(text)
                        if (clean.isNotBlank() && !IGNORED_WORDS.contains(clean.lowercase())) return clean
                    }
                }
            }
        }
        return null
    }

    /**
     * Enhanced Instagram creator detection:
     * 1. Checks specific Instagram Reel/Feed view IDs.
     * 2. Checks profile picture content descriptions (e.g. "Foto do perfil de ju.pasqualotto").
     * 3. Checks nodes immediately preceding or sibling to "Follow" / "Seguir" buttons.
     */
    private fun extractInstagramCreator(root: AccessibilityNodeInfo): String? {
        // 1. Direct Instagram View IDs
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
            "com.instagram.android:id/media_author",
            "com.instagram.android:id/post_author",
            "com.instagram.android:id/carousel_author_name",
            "com.instagram.android:id/clips_author_container",
            "com.instagram.android:id/toolbar_title",
            "com.instagram.android:id/tag_inner_button"
        )
        for (id in candidateIds) {
            val nodes = root.findAccessibilityNodeInfosByViewId(id)
            if (!nodes.isNullOrEmpty()) {
                for (node in nodes) {
                    val raw = node.text?.toString() ?: node.contentDescription?.toString()
                    if (!raw.isNullOrBlank()) {
                        val clean = sanitizeNickname(raw)
                        if (isValidUsername(clean)) return clean
                    }
                }
            }
        }

        // 2. Scan for profile avatar image content descriptions (high reliability across all Instagram versions)
        val avatarMatch = findInstagramAvatarInNode(root, depth = 0)
        if (avatarMatch != null) {
            return avatarMatch
        }

        // 3. Scan for "Follow" / "Seguir" buttons and inspect preceding sibling/parent text
        val followButtons = root.findAccessibilityNodeInfosByText("Follow")
            ?: root.findAccessibilityNodeInfosByText("Seguir")
        if (!followButtons.isNullOrEmpty()) {
            for (btn in followButtons) {
                val parent = btn.parent
                if (parent != null) {
                    for (i in 0 until parent.childCount) {
                        val child = parent.getChild(i) ?: continue
                        if (child != btn) {
                            val txt = child.text?.toString()
                            if (!txt.isNullOrBlank()) {
                                val clean = sanitizeNickname(txt)
                                if (isValidUsername(clean)) return clean
                            }
                        }
                    }
                }
            }
        }

        return null
    }

    private fun findInstagramAvatarInNode(node: AccessibilityNodeInfo?, depth: Int): String? {
        if (node == null || depth > 10) return null

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank()) {
            val match1 = INSTAGRAM_AVATAR_REGEX.find(desc)
            if (match1 != null && match1.groupValues.size > 1) {
                val candidate = sanitizeNickname(match1.groupValues[1])
                if (isValidUsername(candidate)) return candidate
            }

            val match2 = INSTAGRAM_POSSESSIVE_REGEX.find(desc)
            if (match2 != null && match2.groupValues.size > 1) {
                val candidate = sanitizeNickname(match2.groupValues[1])
                if (isValidUsername(candidate)) return candidate
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            val res = findInstagramAvatarInNode(child, depth + 1)
            if (res != null) return res
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
                        if (isValidUsername(clean)) return clean
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
                        if (isValidUsername(clean)) return clean
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
                        if (isValidUsername(clean)) return clean
                    }
                }
            }
        }
        return null
    }

    /**
     * Enhanced recursive scan up to depth 16 with visit counter to prevent UI blocking.
     */
    private fun scanRecursivelyForCreator(
        node: AccessibilityNodeInfo?,
        packageName: String,
        depth: Int,
        nodeCount: Int
    ): String? {
        if (node == null || depth > 16 || nodeCount > 150) return null

        val text = node.text?.toString()
        if (!text.isNullOrBlank()) {
            val atMatch = USERNAME_AT_REGEX.find(text)
            if (atMatch != null) {
                val clean = sanitizeNickname(atMatch.value)
                if (isValidUsername(clean)) return clean
            }
            val bskyMatch = BLUESKY_HANDLE_REGEX.find(text)
            if (bskyMatch != null) {
                return sanitizeNickname(bskyMatch.value)
            }
            if (packageName == "com.instagram.android") {
                val clean = sanitizeNickname(text)
                if (USERNAME_PLAIN_REGEX.matches(clean) && isValidUsername(clean)) {
                    // Check if node is clickable or has action
                    if (node.isClickable || depth in 3..10) {
                        return clean
                    }
                }
            }
        }

        val desc = node.contentDescription?.toString()
        if (!desc.isNullOrBlank()) {
            val atMatch = USERNAME_AT_REGEX.find(desc)
            if (atMatch != null) {
                val clean = sanitizeNickname(atMatch.value)
                if (isValidUsername(clean)) return clean
            }
            val bskyMatch = BLUESKY_HANDLE_REGEX.find(desc)
            if (bskyMatch != null) {
                return sanitizeNickname(bskyMatch.value)
            }
            if (packageName == "com.instagram.android") {
                val av1 = INSTAGRAM_AVATAR_REGEX.find(desc)
                if (av1 != null && av1.groupValues.size > 1) {
                    val clean = sanitizeNickname(av1.groupValues[1])
                    if (isValidUsername(clean)) return clean
                }
            }
        }

        var currentCount = nodeCount + 1
        val childCount = node.childCount
        for (i in 0 until childCount) {
            val child = node.getChild(i)
            val result = scanRecursivelyForCreator(child, packageName, depth + 1, currentCount)
            if (result != null) return result
            currentCount++
        }

        return null
    }

    private fun isValidUsername(clean: String): Boolean {
        return clean.length in 2..32 &&
                !IGNORED_WORDS.contains(clean.lowercase()) &&
                !clean.contains(" ") &&
                !clean.contains("\n")
    }

    private fun sanitizeNickname(raw: String): String {
        return raw.trim()
            .removePrefix("@")
            .split(" ", "\n", "\t", "•", "·", "|", "-")[0]
            .trim()
            .removePrefix("@")
    }

    override fun onInterrupt() {
        Log.d(TAG, "Accessibility Service Interrupted")
    }
}
