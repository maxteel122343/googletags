package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CreatorVideo
import com.example.data.model.WebSearchResult
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleSearchDarkBg
import com.example.ui.theme.GoogleSearchLinkBlue
import com.example.ui.theme.GoogleSearchTextPrimary
import com.example.ui.theme.GoogleSearchTextSecondary

private val GoogleSearchBg = Color(0xFF1F1F1F)
private val GoogleSearchBarDark = Color(0xFF303134)

@Composable
fun Version4FullSplitScreen(
    video: CreatorVideo,
    searchQuery: String,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onOpenUrl: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val tabs = listOf("All", "Videos", "Images", "News", "More")

    AnimatedVisibility(
        visible = true,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .background(GoogleSearchDarkBg)
                .padding(top = 36.dp, start = 8.dp, end = 8.dp, bottom = 8.dp)
                .testTag("version_4_full_split_screen")
        ) {
            // Google colorful logo centered at top
            GoogleTextBrand(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(vertical = 4.dp),
                fontSize = 22
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Dark Search Bar: [Search Icon] [Query Text] [Clear X] [Mic] [Camera Lens]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(GoogleSearchBarDark)
                    .padding(horizontal = 10.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = Color(0xFF9AA0A6),
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Text(
                    text = "@$searchQuery",
                    color = GoogleSearchTextPrimary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Clear",
                    tint = Color(0xFF9AA0A6),
                    modifier = Modifier.size(14.dp)
                )

                Spacer(modifier = Modifier.width(8.dp))

                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Voice",
                    tint = GoogleBlue,
                    modifier = Modifier.size(15.dp)
                )

                Spacer(modifier = Modifier.width(6.dp))

                Icon(
                    imageVector = Icons.Default.CameraAlt,
                    contentDescription = "Lens",
                    tint = GoogleRed,
                    modifier = Modifier.size(15.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Tabs: All, Videos, Images, News, More
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                tabs.forEach { tab ->
                    val isSelected = tab.equals(selectedTab, ignoreCase = true)
                    Column(
                        modifier = Modifier
                            .clickable { onTabSelected(tab) }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = tab,
                            color = if (isSelected) GoogleSearchLinkBlue else GoogleSearchTextSecondary,
                            fontSize = 11.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Box(
                            modifier = Modifier
                                .width(22.dp)
                                .height(2.dp)
                                .background(if (isSelected) GoogleSearchLinkBlue else Color.Transparent)
                        )
                    }
                }
            }

            HorizontalDivider(color = Color(0xFF3C4043), thickness = 0.5.dp)

            // Scrollable Search Results Area
            LazyColumn(
                contentPadding = PaddingValues(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Results count line
                item {
                    Text(
                        text = "About ${video.totalResultsCount} results (0.36 seconds)",
                        color = Color(0xFF9AA0A6),
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(horizontal = 2.dp)
                    )
                }

                // Knowledge Graph / Creator Panel Card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF303134))
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                AssetOrCoilImage(
                                    assetFileName = video.avatarAsset,
                                    contentDescription = "Avatar",
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "@${video.nickname}",
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        VerifiedBadge(size = 12.dp)
                                    }
                                    Text(
                                        text = video.displayName,
                                        color = Color(0xFFBDC1C6),
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = video.bioSnippet,
                                color = Color(0xFFE8EAED),
                                fontSize = 10.5.sp,
                                lineHeight = 14.sp
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            Text(
                                text = "tiktok.com/@${video.nickname}",
                                color = GoogleSearchLinkBlue,
                                fontSize = 10.5.sp,
                                modifier = Modifier.clickable {
                                    onOpenUrl("https://tiktok.com/@${video.nickname}")
                                }
                            )
                        }
                    }
                }

                // "Top TikTok videos" thumbnail carousel
                if (video.topVideos.isNotEmpty()) {
                    item {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                text = "Top TikTok videos",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )

                            LazyRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                items(video.topVideos, key = { it.id }) { vid ->
                                    Card(
                                        modifier = Modifier
                                            .width(80.dp)
                                            .clickable {
                                                onOpenUrl("https://tiktok.com/@${video.nickname}")
                                            },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF282A2E))
                                    ) {
                                        Column {
                                            AssetOrCoilImage(
                                                assetFileName = vid.assetFileName ?: video.backgroundAsset,
                                                contentDescription = vid.title,
                                                contentScale = ContentScale.Crop,
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(70.dp)
                                            )
                                            Text(
                                                text = vid.views,
                                                color = Color(0xFFBDC1C6),
                                                fontSize = 9.sp,
                                                modifier = Modifier.padding(4.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "See more videos on TikTok",
                                color = GoogleSearchLinkBlue,
                                fontSize = 10.5.sp,
                                modifier = Modifier.clickable {
                                    onOpenUrl("https://tiktok.com/@${video.nickname}")
                                }
                            )
                        }
                    }
                }

                // Organic Web Search Results
                items(video.webResults, key = { it.id }) { result ->
                    OrganicSearchCard(
                        result = result,
                        onClick = { onOpenUrl(result.url) }
                    )
                }

                // Related Searches Accordion
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onOpenUrl("https://www.google.com/search?q=${video.nickname}+related")
                            },
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF202124))
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Related searches",
                                color = Color(0xFFE8EAED),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = "Expand",
                                tint = Color(0xFF9AA0A6),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganicSearchCard(
    result: WebSearchResult,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SourceDomainIcon(iconType = result.iconType, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = result.displayUrl,
                color = Color(0xFFBDC1C6),
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = result.title,
            color = GoogleSearchLinkBlue,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = result.snippet,
            color = Color(0xFFBDC1C6),
            fontSize = 10.sp,
            lineHeight = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(2.dp))

        Text(
            text = "About this result ▾",
            color = Color(0xFF9AA0A6),
            fontSize = 9.sp
        )
    }
}
