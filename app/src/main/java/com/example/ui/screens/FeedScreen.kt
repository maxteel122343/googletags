package com.example.ui.screens

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PersonOutline
import androidx.compose.material.icons.filled.PeopleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InterfaceVersion
import com.example.ui.components.AccessibilityGuideDialog
import com.example.ui.components.GoogleIconBadge
import com.example.ui.components.QuickModeSwitcherBar
import com.example.ui.components.SettingsDialog
import com.example.ui.components.Version1BottomHorizontalCards
import com.example.ui.components.Version2SideVerticalCards
import com.example.ui.components.Version3CompactFloatingCard
import com.example.ui.components.Version4FullSplitScreen
import com.example.ui.components.Version5BottomSheet
import com.example.ui.components.VideoPlayerOverlay
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.TikTokRed
import com.example.ui.viewmodel.FeedViewModel
import kotlinx.coroutines.launch

@Composable
fun FeedScreen(
    viewModel: FeedViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { uiState.videos.size }
    )

    // Detect scroll change and notify ViewModel
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.onVideoPageChanged(page)
        }
    }

    // Handle back button if bottom sheet or dialog is open
    BackHandler(enabled = uiState.showSettingsDialog || uiState.showAccessibilityDialog || uiState.isBottomSheetExpanded) {
        if (uiState.showSettingsDialog) viewModel.openSettingsDialog(false)
        else if (uiState.showAccessibilityDialog) viewModel.openAccessibilityDialog(false)
        else if (uiState.isBottomSheetExpanded) viewModel.setBottomSheetExpanded(false)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("feed_screen_root")
    ) {
        val currentVideo = uiState.currentVideo
        val isLiked = uiState.likedVideoIds.contains(currentVideo.id)
        val isBookmarked = uiState.bookmarkedVideoIds.contains(currentVideo.id)
        val isFollowing = uiState.followedNicknames.contains(currentVideo.nickname)

        // RENDER LAYOUT BASED ON ACTIVE INTERFACE VERSION
        when (uiState.activeInterfaceVersion) {
            // VERSION 1: BOTTOM HORIZONTAL CARDS (Reels Style)
            InterfaceVersion.BOTTOM_HORIZONTAL_CARDS -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val video = uiState.videos[page]
                        VideoPlayerOverlay(
                            video = video,
                            isLiked = uiState.likedVideoIds.contains(video.id),
                            isBookmarked = uiState.bookmarkedVideoIds.contains(video.id),
                            isFollowing = uiState.followedNicknames.contains(video.nickname),
                            onLikeClick = { viewModel.toggleLike(video.id) },
                            onBookmarkClick = { viewModel.toggleBookmark(video.id) },
                            onFollowClick = { viewModel.toggleFollow(video.nickname) },
                            onShareClick = {
                                viewModel.openInBrowser(context, "https://www.google.com/search?q=${video.nickname}")
                            },
                            onCommentClick = {
                                Toast.makeText(context, "${video.commentsCount} comentários", Toast.LENGTH_SHORT).show()
                            },
                            contentPaddingBottom = 160
                        )
                    }

                    // Version 1 Horizontal Cards anchored to bottom above navigation bar
                    Version1BottomHorizontalCards(
                        searchQuery = uiState.activeSearchQuery,
                        results = uiState.activeImageResults,
                        onCardClick = { item ->
                            viewModel.openInBrowser(context, "${item.title} ${uiState.activeSearchQuery}", asImages = true)
                        },
                        onSeeAllClick = {
                            viewModel.openInBrowser(context, uiState.activeSearchQuery, asImages = true)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 54.dp)
                    )
                }
            }

            // VERSION 2: SIDE VERTICAL CARDS (Split Screen - Video Left, Image Cards Right)
            InterfaceVersion.SIDE_VERTICAL_CARDS -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left: Short video (58%)
                    Box(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                    ) {
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val video = uiState.videos[page]
                            VideoPlayerOverlay(
                                video = video,
                                isLiked = uiState.likedVideoIds.contains(video.id),
                                isBookmarked = uiState.bookmarkedVideoIds.contains(video.id),
                                isFollowing = uiState.followedNicknames.contains(video.nickname),
                                onLikeClick = { viewModel.toggleLike(video.id) },
                                onBookmarkClick = { viewModel.toggleBookmark(video.id) },
                                onFollowClick = { viewModel.toggleFollow(video.nickname) },
                                onShareClick = {
                                    viewModel.openInBrowser(context, "https://www.google.com/search?q=${video.nickname}")
                                },
                                onCommentClick = {
                                    Toast.makeText(context, "${video.commentsCount} comentários", Toast.LENGTH_SHORT).show()
                                },
                                contentPaddingBottom = 54
                            )
                        }
                    }

                    // Right: Vertical Image Cards (42%)
                    Box(
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                            .padding(bottom = 54.dp)
                    ) {
                        Version2SideVerticalCards(
                            searchQuery = uiState.activeSearchQuery,
                            results = uiState.activeImageResults,
                            onCardClick = { item ->
                                viewModel.openInBrowser(context, "${item.title} ${uiState.activeSearchQuery}", asImages = true)
                            },
                            onOpenSearch = {
                                viewModel.openInBrowser(context, uiState.activeSearchQuery, asImages = true)
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // VERSION 3: COMPACT FLOATING CARD (Popup overlay on bottom right)
            InterfaceVersion.COMPACT_FLOATING_CARD -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val video = uiState.videos[page]
                        VideoPlayerOverlay(
                            video = video,
                            isLiked = uiState.likedVideoIds.contains(video.id),
                            isBookmarked = uiState.bookmarkedVideoIds.contains(video.id),
                            isFollowing = uiState.followedNicknames.contains(video.nickname),
                            onLikeClick = { viewModel.toggleLike(video.id) },
                            onBookmarkClick = { viewModel.toggleBookmark(video.id) },
                            onFollowClick = { viewModel.toggleFollow(video.nickname) },
                            onShareClick = {
                                viewModel.openInBrowser(context, "https://www.google.com/search?q=${video.nickname}")
                            },
                            onCommentClick = {
                                Toast.makeText(context, "${video.commentsCount} comentários", Toast.LENGTH_SHORT).show()
                            },
                            contentPaddingBottom = 54
                        )
                    }

                    // Compact floating card on bottom right
                    if (uiState.isFloatingCardVisible) {
                        Version3CompactFloatingCard(
                            video = currentVideo,
                            searchQuery = uiState.activeSearchQuery,
                            isVisible = uiState.isFloatingCardVisible,
                            onDismiss = { viewModel.dismissFloatingCard() },
                            onSeeMoreResults = {
                                viewModel.openInBrowser(context, uiState.activeSearchQuery)
                            },
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 12.dp, bottom = 66.dp)
                        )
                    } else {
                        // Restore icon pill if dismissed
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 16.dp, bottom = 70.dp)
                                .clip(RoundedCornerShape(20.dp))
                                .background(Color(0xCC000000))
                                .clickable { viewModel.showFloatingCard() }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("restore_floating_card_button")
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GoogleIconBadge(size = 14.dp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Ver busca",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // VERSION 4: FULL SPLIT SCREEN 50/50 (Google Dark Search on Right)
            InterfaceVersion.FULL_SPLIT_SCREEN -> {
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left 50%: Short video player
                    Box(
                        modifier = Modifier
                            .weight(0.50f)
                            .fillMaxHeight()
                    ) {
                        VerticalPager(
                            state = pagerState,
                            modifier = Modifier.fillMaxSize()
                        ) { page ->
                            val video = uiState.videos[page]
                            VideoPlayerOverlay(
                                video = video,
                                isLiked = uiState.likedVideoIds.contains(video.id),
                                isBookmarked = uiState.bookmarkedVideoIds.contains(video.id),
                                isFollowing = uiState.followedNicknames.contains(video.nickname),
                                onLikeClick = { viewModel.toggleLike(video.id) },
                                onBookmarkClick = { viewModel.toggleBookmark(video.id) },
                                onFollowClick = { viewModel.toggleFollow(video.nickname) },
                                onShareClick = {
                                    viewModel.openInBrowser(context, "https://www.google.com/search?q=${video.nickname}")
                                },
                                onCommentClick = {
                                    Toast.makeText(context, "${video.commentsCount} comentários", Toast.LENGTH_SHORT).show()
                                },
                                contentPaddingBottom = 54
                            )
                        }
                    }

                    // Right 50%: Google Search dark mode
                    Box(
                        modifier = Modifier
                            .weight(0.50f)
                            .fillMaxHeight()
                            .padding(bottom = 54.dp)
                    ) {
                        Version4FullSplitScreen(
                            video = currentVideo,
                            searchQuery = uiState.activeSearchQuery,
                            selectedTab = uiState.selectedSearchTab,
                            onTabSelected = { viewModel.setSelectedSearchTab(it) },
                            onOpenUrl = { viewModel.openInBrowser(context, it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            // VERSION 5: BOTTOM SHEET SEARCH RESULTS
            InterfaceVersion.BOTTOM_SHEET -> {
                Box(modifier = Modifier.fillMaxSize()) {
                    VerticalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize()
                    ) { page ->
                        val video = uiState.videos[page]
                        VideoPlayerOverlay(
                            video = video,
                            isLiked = uiState.likedVideoIds.contains(video.id),
                            isBookmarked = uiState.bookmarkedVideoIds.contains(video.id),
                            isFollowing = uiState.followedNicknames.contains(video.nickname),
                            onLikeClick = { viewModel.toggleLike(video.id) },
                            onBookmarkClick = { viewModel.toggleBookmark(video.id) },
                            onFollowClick = { viewModel.toggleFollow(video.nickname) },
                            onShareClick = {
                                viewModel.openInBrowser(context, "https://www.google.com/search?q=${video.nickname}")
                            },
                            onCommentClick = {
                                Toast.makeText(context, "${video.commentsCount} comentários", Toast.LENGTH_SHORT).show()
                            },
                            contentPaddingBottom = if (uiState.isBottomSheetExpanded) 360 else 180
                        )
                    }

                    // Version 5 Bottom Sheet sitting at bottom above nav bar
                    Version5BottomSheet(
                        video = currentVideo,
                        searchQuery = uiState.activeSearchQuery,
                        isExpanded = uiState.isBottomSheetExpanded,
                        onToggleExpand = {
                            viewModel.setBottomSheetExpanded(!uiState.isBottomSheetExpanded)
                        },
                        onOpenUrl = { url ->
                            viewModel.openInBrowser(context, url)
                        },
                        onFilterChipClick = { chip ->
                            viewModel.openInBrowser(context, "${uiState.activeSearchQuery} $chip")
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 54.dp)
                    )
                }
            }
        }

        // QUICK MODE SWITCHER BAR (Floating top navigation)
        QuickModeSwitcherBar(
            currentVersion = uiState.activeInterfaceVersion,
            onVersionSelected = { viewModel.setInterfaceVersion(it) },
            activeTemplateName = uiState.activeTemplate?.label ?: "Template",
            onTemplateClick = { viewModel.openSettingsDialog(true) },
            onSettingsClick = { viewModel.openSettingsDialog(true) },
            onAccessibilityGuideClick = { viewModel.openAccessibilityDialog(true) },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(top = 8.dp)
        )

        // FLOATING NOTIFICATION POPUP (when query updates automatically on scroll)
        AnimatedVisibility(
            visible = uiState.searchNotificationMessage != null,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 96.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = Color(0xF0181A20),
                border = androidx.compose.foundation.BorderStroke(1.dp, GoogleBlue),
                modifier = Modifier.shadow(8.dp, RoundedCornerShape(20.dp))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoogleIconBadge(size = 14.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = uiState.searchNotificationMessage ?: "",
                        color = Color.White,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // BOTTOM APP BAR (TikTok / Instagram style navigation bar)
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(54.dp)
                .windowInsetsPadding(WindowInsets.navigationBars),
            color = Color.Black
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                BottomNavItem(icon = Icons.Default.Home, label = "Início", isSelected = true)
                BottomNavItem(icon = Icons.Default.PeopleOutline, label = "Amigos", isSelected = false)

                // Plus button
                Box(
                    modifier = Modifier
                        .width(38.dp)
                        .height(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White)
                        .clickable { viewModel.openSettingsDialog(true) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "New",
                        tint = Color.Black,
                        modifier = Modifier.size(20.dp)
                    )
                }

                BottomNavItem(icon = Icons.Default.ChatBubbleOutline, label = "Caixa de entrada", isSelected = false)
                BottomNavItem(icon = Icons.Default.PersonOutline, label = "Perfil", isSelected = false)
            }
        }

        // SETTINGS DIALOG
        if (uiState.showSettingsDialog) {
            SettingsDialog(
                currentVersion = uiState.activeInterfaceVersion,
                defaultVersion = uiState.defaultInterfaceVersion,
                onSetDefaultVersion = { viewModel.setDefaultInterfaceVersion(it) },
                searchEngineType = uiState.searchEngineType,
                onSetSearchEngineType = { viewModel.setSearchEngineType(it) },
                autoSearchEnabled = uiState.autoSearchEnabled,
                onToggleAutoSearch = { viewModel.toggleAutoSearch(it) },
                searchTemplates = uiState.searchTemplates,
                activeTemplateId = uiState.activeTemplateId,
                onSelectTemplate = { viewModel.setActiveTemplate(it) },
                onAddTemplate = { kw, lbl -> viewModel.addSearchTemplate(kw, lbl) },
                onRemoveTemplate = { viewModel.removeSearchTemplate(it) },
                onOpenAccessibilityGuide = {
                    viewModel.openSettingsDialog(false)
                    viewModel.openAccessibilityDialog(true)
                },
                onDismiss = { viewModel.openSettingsDialog(false) }
            )
        }

        // ACCESSIBILITY SERVICE GUIDE & SIMULATOR DIALOG
        if (uiState.showAccessibilityDialog) {
            AccessibilityGuideDialog(
                onSimulateEvent = { appName ->
                    val nextIndex = (uiState.currentVideoIndex + 1) % uiState.videos.size
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(nextIndex)
                    }
                },
                onDismiss = { viewModel.openAccessibilityDialog(false) }
            )
        }
    }
}

@Composable
private fun BottomNavItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = if (isSelected) Color.White else Color(0xFF8E8E93),
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = label,
            color = if (isSelected) Color.White else Color(0xFF8E8E93),
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
