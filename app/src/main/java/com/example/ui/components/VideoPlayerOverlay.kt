package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.CreatorVideo
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.OverlayTranslucent
import com.example.ui.theme.OverlayWhite
import com.example.ui.theme.OverlayWhiteSubtle
import com.example.ui.theme.TikTokRed

@Composable
fun VideoPlayerOverlay(
    video: CreatorVideo,
    isLiked: Boolean,
    isBookmarked: Boolean,
    isFollowing: Boolean,
    onLikeClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onFollowClick: () -> Unit,
    onShareClick: () -> Unit,
    onCommentClick: () -> Unit,
    modifier: Modifier = Modifier,
    showBottomSpacing: Boolean = false,
    contentPaddingBottom: Int = 0
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .testTag("video_item_${video.id}")
    ) {
        // Video background frame
        AssetOrCoilImage(
            assetFileName = video.backgroundAsset,
            contentDescription = "Video by ${video.nickname}",
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Gradient overlay for contrast
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.55f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.75f)
                        ),
                        startY = 0f,
                        endY = Float.POSITIVE_INFINITY
                    )
                )
        )

        // Top Header: Following / For You tabs + Detection status
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 44.dp, start = 16.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Seguindo",
                    color = OverlayWhiteSubtle,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
                Text(
                    text = "Para Você",
                    color = OverlayWhite,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Detection badge pill
            Row(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xCC000000))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GoogleIconBadge(size = 14.dp)
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Criador detectado: @${video.nickname}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Right Action Bar (Avatar + Follow, Likes, Comments, Bookmark, Share, Sound Disc)
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 12.dp, bottom = (contentPaddingBottom + 70).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Avatar + Follow Button
            Box(
                contentAlignment = Alignment.BottomCenter,
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                AssetOrCoilImage(
                    assetFileName = video.avatarAsset,
                    contentDescription = "Avatar ${video.nickname}",
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                )
                // Follow Plus Button
                Box(
                    modifier = Modifier
                        .padding(top = 34.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(if (isFollowing) GoogleBlue else TikTokRed)
                        .clickable(onClick = onFollowClick)
                        .testTag("follow_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isFollowing) Icons.Default.Check else Icons.Default.Add,
                        contentDescription = "Follow",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            // Like Action
            ActionItem(
                icon = if (isLiked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                tint = if (isLiked) TikTokRed else OverlayWhite,
                count = if (isLiked) "${video.likesCount}+" else video.likesCount,
                contentDescription = "Like video",
                onClick = onLikeClick,
                testTag = "like_action_button"
            )

            // Comment Action
            ActionItem(
                icon = Icons.Outlined.ChatBubbleOutline,
                tint = OverlayWhite,
                count = video.commentsCount,
                contentDescription = "Comments",
                onClick = onCommentClick,
                testTag = "comment_action_button"
            )

            // Bookmark Action
            ActionItem(
                icon = if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                tint = if (isBookmarked) Color(0xFFFFD700) else OverlayWhite,
                count = video.bookmarksCount,
                contentDescription = "Bookmark",
                onClick = onBookmarkClick,
                testTag = "bookmark_action_button"
            )

            // Share Action
            ActionItem(
                icon = Icons.Default.Share,
                tint = OverlayWhite,
                count = video.sharesCount,
                contentDescription = "Share",
                onClick = onShareClick,
                testTag = "share_action_button"
            )

            // Rotating Audio Disc
            AudioDisc(audioTitle = video.audioTitle)
        }

        // Bottom Left Info: Nickname + Follow, Caption, Hashtags, Audio
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth(0.78f)
                .padding(start = 16.dp, end = 8.dp, bottom = (contentPaddingBottom + 64).dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "@${video.nickname}",
                    color = OverlayWhite,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag("creator_nickname_text")
                )
                if (video.isVerified) {
                    Spacer(modifier = Modifier.width(6.dp))
                    VerifiedBadge(size = 14.dp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                // Small Follow tag
                if (!isFollowing) {
                    Surface(
                        shape = RoundedCornerShape(4.dp),
                        color = Color.Transparent,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.7f)),
                        modifier = Modifier.clickable(onClick = onFollowClick)
                    ) {
                        Text(
                            text = "Seguir",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = video.caption,
                color = OverlayWhite,
                fontSize = 13.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Audio track pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0x66000000))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = video.audioTitle,
                    color = OverlayWhite,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Bottom progress bar (Reels / TikTok style red/white bar)
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (contentPaddingBottom + 52).dp)
                .fillMaxWidth()
                .height(2.dp)
                .background(Color.White.copy(alpha = 0.25f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.45f)
                    .height(2.dp)
                    .background(Color.White)
            )
        }
    }
}

@Composable
private fun ActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    count: String,
    contentDescription: String,
    onClick: () -> Unit,
    testTag: String
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(onClick = onClick)
            .testTag(testTag)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = count,
            color = OverlayWhite,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun AudioDisc(audioTitle: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "disc_spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "disc_angle"
    )

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(Color(0xFF262626)),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(Color(0xFF4285F4))
        )
        Icon(
            imageVector = Icons.Default.GraphicEq,
            contentDescription = audioTitle,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
    }
}
