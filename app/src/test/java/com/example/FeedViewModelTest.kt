package com.example

import com.example.data.model.InterfaceVersion
import com.example.data.model.SearchEngineType
import com.example.data.repository.CreatorRepository
import com.example.ui.viewmodel.FeedViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FeedViewModelTest {

    @Before
    fun setUp() {
        com.example.data.service.CreatorDetectionBus.resetForTesting()
    }

    @Test
    fun testInitialFeedState() {
        val viewModel = FeedViewModel()
        val state = viewModel.uiState.value

        assertEquals(5, state.videos.size)
        assertEquals("ju.pasqualotto", state.detectedNickname)
        assertTrue(state.activeSearchQuery.startsWith("ju.pasqualotto"))
        assertEquals(InterfaceVersion.BOTTOM_HORIZONTAL_CARDS, state.activeInterfaceVersion)
    }

    @Test
    fun testSwitchingInterfaceVersions() {
        val viewModel = FeedViewModel()

        viewModel.setInterfaceVersion(InterfaceVersion.SIDE_VERTICAL_CARDS)
        assertEquals(InterfaceVersion.SIDE_VERTICAL_CARDS, viewModel.uiState.value.activeInterfaceVersion)

        viewModel.setInterfaceVersion(InterfaceVersion.BOTTOM_SHEET)
        assertEquals(InterfaceVersion.BOTTOM_SHEET, viewModel.uiState.value.activeInterfaceVersion)

        viewModel.setInterfaceVersion(InterfaceVersion.EXTRACTED_IMAGES_SCROLL)
        assertEquals(InterfaceVersion.EXTRACTED_IMAGES_SCROLL, viewModel.uiState.value.activeInterfaceVersion)

        viewModel.setInterfaceVersion(InterfaceVersion.CAROUSEL_PHOTO_CARDS)
        assertEquals(InterfaceVersion.CAROUSEL_PHOTO_CARDS, viewModel.uiState.value.activeInterfaceVersion)

        viewModel.setInterfaceVersion(InterfaceVersion.BOTTOM_HORIZONTAL_CARDS)
        assertEquals(InterfaceVersion.BOTTOM_HORIZONTAL_CARDS, viewModel.uiState.value.activeInterfaceVersion)
    }

    @Test
    fun testSearchTemplatesManager() {
        val viewModel = FeedViewModel()
        val initialCount = viewModel.uiState.value.searchTemplates.size

        viewModel.addSearchTemplate("beach", "Praia / Beach")
        val updatedState = viewModel.uiState.value
        assertEquals(initialCount + 1, updatedState.searchTemplates.size)

        val added = updatedState.searchTemplates.find { it.keyword == "beach" }
        assertNotNull(added)
        assertEquals("beach", added?.keyword)
    }

    @Test
    fun testLikesAndBookmarks() {
        val viewModel = FeedViewModel()
        val firstId = viewModel.uiState.value.videos.first().id

        viewModel.toggleLike(firstId)
        assertTrue(viewModel.uiState.value.likedVideoIds.contains(firstId))

        viewModel.toggleLike(firstId)
        assertTrue(!viewModel.uiState.value.likedVideoIds.contains(firstId))

        viewModel.toggleBookmark(firstId)
        assertTrue(viewModel.uiState.value.bookmarkedVideoIds.contains(firstId))
    }

    @Test
    fun testVideoPageChangeAndDetection() {
        val viewModel = FeedViewModel()
        viewModel.onVideoPageChanged(1)

        val secondCreator = CreatorRepository.sampleVideos[1]
        assertEquals(1, viewModel.uiState.value.currentVideoIndex)
        assertEquals(secondCreator.nickname, viewModel.uiState.value.detectedNickname)
    }

    @Test
    fun testCreatorDetectionBusDispatch() {
        com.example.data.service.CreatorDetectionBus.setActiveKeyword("look")
        assertEquals("look", com.example.data.service.CreatorDetectionBus.activeSearchKeyword.value)

        com.example.data.service.CreatorDetectionBus.dispatchDetectedCreator("fitwithjoao", "com.instagram.android")
        assertEquals("fitwithjoao", com.example.data.service.CreatorDetectionBus.latestDetectedNickname.value)
        assertEquals("com.instagram.android", com.example.data.service.CreatorDetectionBus.latestPackageName.value)
    }
}

