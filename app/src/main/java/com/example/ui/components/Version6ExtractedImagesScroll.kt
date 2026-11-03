package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.model.CreatorVideo
import com.example.data.model.ImageSearchResult
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GoogleBlue

/**
 * Version 6: Bottom Sheet layout with extracted Google Images in a smooth horizontal scroll row.
 */
@Composable
fun Version6ExtractedImagesScroll(
    video: CreatorVideo,
    searchQuery: String,
    results: List<ImageSearchResult>,
    onImageClick: (ImageSearchResult) -> Unit,
    onSeeAllClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AnimatedVisibility(
        visible = true,
        enter = slideInVertically(initialOffsetY = { it / 2 }) + fadeIn(),
        modifier = modifier
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(elevation = 16.dp, shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                .testTag("version_6_bottom_sheet"),
            color = DarkSurface.copy(alpha = 0.96f),
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF333A4D))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 14.dp)
            ) {
                // Top drag handle indicator
                Box(
                    modifier = Modifier
                        .size(width = 46.dp, height = 4.dp)
                        .clip(CircleShape)
                        .background(GoogleBlue.copy(alpha = 0.7f))
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Header with badge and search bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(GoogleBlue),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Collections,
                                contentDescription = "Extracted Photos",
                                tint = Color.White,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "V6: Fotos Extraídas (Google)",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "q=$searchQuery",
                                color = Color(0xFF8AB4F8),
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    // See all button
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF262D3D))
                            .clickable(onClick = onSeeAllClick)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Ver no Google",
                            color = Color(0xFF8AB4F8),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.OpenInNew,
                            contentDescription = "Open in Google",
                            tint = Color(0xFF8AB4F8),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Horizontal scroll of extracted photos
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(results) { index, item ->
                        Card(
                            modifier = Modifier
                                .width(115.dp)
                                .height(145.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF333A4B), RoundedCornerShape(12.dp))
                                .clickable { onImageClick(item) }
                                .testTag("v6_image_card_$index"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1F2A))
                        ) {
                            Box(modifier = Modifier.fillMaxWidth()) {
                                val model = item.assetFileName?.let { "file:///android_asset/$it" } ?: item.webImageUrl
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(model)
                                        .crossfade(true)
                                        .build(),
                                    contentDescription = item.title,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(145.dp)
                                )

                                // Photo index badge
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(6.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color.Black.copy(alpha = 0.65f))
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        text = "#${index + 1}",
                                        color = Color.White,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
