package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.CreatorVideo
import com.example.data.model.ImageSearchResult
import com.example.data.model.InterfaceVersion
import com.example.data.model.SearchEngineType
import com.example.data.model.SearchTemplate
import com.example.data.model.WebSearchResult
import com.example.data.repository.CreatorRepository
import com.example.data.service.CreatorDetectionBus
import com.example.data.service.FloatingOverlayManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class FeedUiState(
    val videos: List<CreatorVideo> = CreatorRepository.sampleVideos,
    val currentVideoIndex: Int = 0,
    val detectedNickname: String = "",
    val activeInterfaceVersion: InterfaceVersion = InterfaceVersion.BOTTOM_HORIZONTAL_CARDS,
    val defaultInterfaceVersion: InterfaceVersion = InterfaceVersion.BOTTOM_HORIZONTAL_CARDS,
    val searchEngineType: SearchEngineType = SearchEngineType.GOOGLE_IMAGES,
    val searchTemplates: List<SearchTemplate> = CreatorRepository.defaultTemplates,
    val activeTemplateId: String = "t2", // "fotos" by default
    val autoSearchEnabled: Boolean = true,
    val isSearching: Boolean = false,
    val activeSearchQuery: String = "",
    val activeImageResults: List<ImageSearchResult> = emptyList(),
    val activeWebResults: List<WebSearchResult> = emptyList(),
    val likedVideoIds: Set<String> = emptySet(),
    val bookmarkedVideoIds: Set<String> = emptySet(),
    val followedNicknames: Set<String> = emptySet(),
    val isFloatingCardVisible: Boolean = true,
    val isBottomSheetExpanded: Boolean = false,
    val selectedSearchTab: String = "All",
    val showSettingsDialog: Boolean = false,
    val showAccessibilityDialog: Boolean = false,
    val searchNotificationMessage: String? = null,
    val isRealOverlayShowing: Boolean = false,
    val isAccessibilityConnected: Boolean = false,
    val isAccessibilityEnabledInSystem: Boolean = false,
    val hasOverlayPermission: Boolean = false
) {
    val currentVideo: CreatorVideo
        get() = videos.getOrElse(currentVideoIndex) { videos.first() }

    val activeTemplate: SearchTemplate?
        get() = searchTemplates.find { it.id == activeTemplateId }
}

class FeedViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    init {
        val initialVideo = _uiState.value.videos.first()
        val detected = initialVideo.nickname
        val query = buildSearchQuery(detected, _uiState.value.activeTemplate?.keyword)
        _uiState.update {
            it.copy(
                detectedNickname = detected,
                activeSearchQuery = query,
                activeImageResults = initialVideo.imageResults,
                activeWebResults = initialVideo.webResults
            )
        }

        // Listen for live creator detection events from Accessibility Service
        viewModelScope.launch {
            CreatorDetectionBus.latestDetectedNickname.collect { liveNick ->
                if (!liveNick.isNullOrBlank() && liveNick != _uiState.value.detectedNickname) {
                    onCreatorDetectedExternally(liveNick)
                }
            }
        }

        // Listen for accessibility connection status
        viewModelScope.launch {
            CreatorDetectionBus.isAccessibilityConnected.collect { connected ->
                _uiState.update { it.copy(isAccessibilityConnected = connected) }
            }
        }

        // Listen for overlay display status
        viewModelScope.launch {
            CreatorDetectionBus.isOverlayShowing.collect { showing ->
                _uiState.update { it.copy(isRealOverlayShowing = showing) }
            }
        }

        // Listen for version switches triggered directly from inside the overlay
        viewModelScope.launch {
            CreatorDetectionBus.activeInterfaceVersion.drop(1).collect { ver ->
                if (ver != _uiState.value.activeInterfaceVersion) {
                    _uiState.update { it.copy(activeInterfaceVersion = ver) }
                }
            }
        }

        // Listen for new keywords added from the overlay '+' button
        viewModelScope.launch {
            CreatorDetectionBus.availableKeywords.collect { keywords ->
                val currentKeywords = _uiState.value.searchTemplates.map { it.keyword }
                val newOnes = keywords.filter { it !in currentKeywords }
                if (newOnes.isNotEmpty()) {
                    val newTemplates = newOnes.map {
                        SearchTemplate("custom_${System.currentTimeMillis()}_$it", it, it)
                    }
                    _uiState.update { it.copy(searchTemplates = it.searchTemplates + newTemplates) }
                }
            }
        }
    }

    fun refreshSystemStatus(context: Context) {
        val hasOverlay = CreatorDetectionBus.hasOverlayPermission(context)
        val isA11yEnabled = CreatorDetectionBus.isAccessibilityServiceEnabled(context)
        val isA11yConnected = CreatorDetectionBus.isAccessibilityConnected.value
        val isOverlayActive = CreatorDetectionBus.isOverlayShowing.value

        _uiState.update {
            it.copy(
                hasOverlayPermission = hasOverlay,
                isAccessibilityEnabledInSystem = isA11yEnabled,
                isAccessibilityConnected = isA11yConnected,
                isRealOverlayShowing = isOverlayActive
            )
        }
    }

    fun toggleRealOverlay(context: Context) {
        CreatorDetectionBus.setInterfaceVersion(_uiState.value.activeInterfaceVersion)
        CreatorDetectionBus.setActiveKeyword(_uiState.value.activeTemplate?.keyword ?: "")
        CreatorDetectionBus.toggleRealOverlay(context, _uiState.value.detectedNickname)
        refreshSystemStatus(context)
    }

    fun requestOverlayPermission(context: Context) {
        CreatorDetectionBus.requestOverlayPermission(context)
    }

    fun openAccessibilitySettings(context: Context) {
        CreatorDetectionBus.openAccessibilitySettings(context)
    }

    private fun onCreatorDetectedExternally(nickname: String) {
        _uiState.update { current ->
            // Check if matches one of the sample videos to show matched preview
            val matchingIndex = current.videos.indexOfFirst {
                it.nickname.equals(nickname, ignoreCase = true)
            }
            if (matchingIndex >= 0) {
                current.copy(
                    currentVideoIndex = matchingIndex,
                    detectedNickname = nickname,
                    isFloatingCardVisible = true
                )
            } else {
                current.copy(
                    detectedNickname = nickname,
                    isFloatingCardVisible = true
                )
            }
        }
        triggerSearch(nickname, _uiState.value.activeTemplate?.keyword)
    }

    fun simulateAccessibilityDetection(nickname: String, appName: String, context: Context) {
        CreatorDetectionBus.dispatchDetectedCreator(nickname, appName, context)
        onCreatorDetectedExternally(nickname)
    }

    fun onVideoPageChanged(index: Int) {
        val videos = _uiState.value.videos
        if (index !in videos.indices) return

        val newVideo = videos[index]
        val newNickname = newVideo.nickname

        _uiState.update { current ->
            current.copy(
                currentVideoIndex = index,
                detectedNickname = newNickname,
                isFloatingCardVisible = true
            )
        }

        if (_uiState.value.autoSearchEnabled) {
            triggerSearch(newNickname, _uiState.value.activeTemplate?.keyword)
        }

        // Update real floating overlay if open
        if (FloatingOverlayManager.isShowing()) {
            val query = buildSearchQuery(newNickname, _uiState.value.activeTemplate?.keyword)
            FloatingOverlayManager.updateQuery(query)
        }
    }

    fun setInterfaceVersion(version: InterfaceVersion) {
        _uiState.update { it.copy(activeInterfaceVersion = version, isFloatingCardVisible = true) }
        CreatorDetectionBus.setInterfaceVersion(version)
    }

    fun setDefaultInterfaceVersion(version: InterfaceVersion) {
        _uiState.update {
            it.copy(
                defaultInterfaceVersion = version,
                activeInterfaceVersion = version
            )
        }
        CreatorDetectionBus.setInterfaceVersion(version)
    }

    fun setSearchEngineType(type: SearchEngineType) {
        _uiState.update { it.copy(searchEngineType = type) }
    }

    fun toggleAutoSearch(enabled: Boolean) {
        _uiState.update { it.copy(autoSearchEnabled = enabled) }
        if (enabled) {
            triggerSearch(_uiState.value.detectedNickname, _uiState.value.activeTemplate?.keyword)
        }
    }

    fun setActiveTemplate(templateId: String) {
        _uiState.update { it.copy(activeTemplateId = templateId) }
        val template = _uiState.value.searchTemplates.find { it.id == templateId }
        val kw = template?.keyword ?: ""
        CreatorDetectionBus.setActiveKeyword(kw)
        triggerSearch(_uiState.value.detectedNickname, kw)

        if (FloatingOverlayManager.isShowing()) {
            val query = buildSearchQuery(_uiState.value.detectedNickname, kw)
            FloatingOverlayManager.updateQuery(query)
        }
    }

    fun addSearchTemplate(keyword: String, label: String) {
        if (keyword.isBlank()) return
        val newTemplate = SearchTemplate(
            id = "custom_${System.currentTimeMillis()}",
            keyword = keyword.trim().lowercase(),
            label = if (label.isNotBlank()) label.trim() else keyword.trim().lowercase()
        )
        _uiState.update {
            it.copy(
                searchTemplates = it.searchTemplates + newTemplate,
                activeTemplateId = newTemplate.id
            )
        }
        CreatorDetectionBus.addKeyword(newTemplate.keyword)
        CreatorDetectionBus.setActiveKeyword(newTemplate.keyword)
        triggerSearch(_uiState.value.detectedNickname, newTemplate.keyword)
    }

    fun removeSearchTemplate(templateId: String) {
        _uiState.update { current ->
            val updatedList = current.searchTemplates.filter { it.id != templateId }
            val nextActiveId = if (current.activeTemplateId == templateId) {
                updatedList.firstOrNull()?.id ?: "t1"
            } else current.activeTemplateId
            current.copy(searchTemplates = updatedList, activeTemplateId = nextActiveId)
        }
        val template = _uiState.value.searchTemplates.find { it.id == _uiState.value.activeTemplateId }
        triggerSearch(_uiState.value.detectedNickname, template?.keyword)
    }

    fun performCustomSearch(query: String) {
        _uiState.update { it.copy(activeSearchQuery = query) }
        triggerSearch(query, "")
        if (FloatingOverlayManager.isShowing()) {
            FloatingOverlayManager.updateQuery(query)
        }
    }

    private fun triggerSearch(nicknameOrQuery: String, keyword: String?) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true) }
            val query = buildSearchQuery(nicknameOrQuery, keyword)

            delay(250)

            val currentVideo = _uiState.value.currentVideo
            _uiState.update {
                it.copy(
                    isSearching = false,
                    activeSearchQuery = query,
                    activeImageResults = currentVideo.imageResults,
                    activeWebResults = currentVideo.webResults,
                    searchNotificationMessage = "Busca atualizada: $query"
                )
            }

            delay(2000)
            _uiState.update { it.copy(searchNotificationMessage = null) }
        }
    }

    private fun buildSearchQuery(nickname: String, keyword: String?): String {
        val cleanNick = nickname.trim().removePrefix("@")
        return if (!keyword.isNullOrBlank()) {
            "$cleanNick $keyword"
        } else {
            cleanNick
        }
    }

    fun toggleLike(videoId: String) {
        _uiState.update { current ->
            val likes = current.likedVideoIds.toMutableSet()
            if (likes.contains(videoId)) likes.remove(videoId) else likes.add(videoId)
            current.copy(likedVideoIds = likes)
        }
    }

    fun toggleBookmark(videoId: String) {
        _uiState.update { current ->
            val bookmarks = current.bookmarkedVideoIds.toMutableSet()
            if (bookmarks.contains(videoId)) bookmarks.remove(videoId) else bookmarks.add(videoId)
            current.copy(bookmarkedVideoIds = bookmarks)
        }
    }

    fun toggleFollow(nickname: String) {
        _uiState.update { current ->
            val followed = current.followedNicknames.toMutableSet()
            if (followed.contains(nickname)) followed.remove(nickname) else followed.add(nickname)
            current.copy(followedNicknames = followed)
        }
    }

    fun dismissFloatingCard() {
        _uiState.update { it.copy(isFloatingCardVisible = false) }
    }

    fun showFloatingCard() {
        _uiState.update { it.copy(isFloatingCardVisible = true) }
    }

    fun setBottomSheetExpanded(expanded: Boolean) {
        _uiState.update { it.copy(isBottomSheetExpanded = expanded) }
    }

    fun setSelectedSearchTab(tab: String) {
        _uiState.update { it.copy(selectedSearchTab = tab) }
    }

    fun openSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun openAccessibilityDialog(show: Boolean) {
        _uiState.update { it.copy(showAccessibilityDialog = show) }
    }

    fun openInBrowser(context: Context, queryOrUrl: String, asImages: Boolean = false) {
        val url = if (queryOrUrl.startsWith("http://") || queryOrUrl.startsWith("https://")) {
            queryOrUrl
        } else {
            val encoded = URLEncoder.encode(queryOrUrl, StandardCharsets.UTF_8.toString())
            if (asImages || _uiState.value.searchEngineType == SearchEngineType.GOOGLE_IMAGES) {
                "https://www.google.com/search?tbm=isch&q=$encoded"
            } else {
                "https://www.google.com/search?q=$encoded"
            }
        }
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Safe fallback
        }
    }
}
