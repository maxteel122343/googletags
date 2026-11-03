package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.GoogleBlue
import com.example.ui.theme.GoogleGreen
import com.example.ui.theme.GoogleRed
import com.example.ui.theme.GoogleYellow
import com.example.ui.theme.VerifiedBadgeBlue

@Composable
fun GoogleIconBadge(modifier: Modifier = Modifier, size: Dp = 18.dp) {
    // Sharp Google "G" logo drawn with precise arcs in Google's official colors
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White)
            .padding(1.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = this.size.width
            val h = this.size.height
            val center = Offset(w / 2f, h / 2f)
            val radius = (w / 2f) * 0.78f
            val strokeWidth = w * 0.22f

            // Blue arc (top-right & right crossbar)
            drawArc(
                color = GoogleBlue,
                startAngle = -45f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Green arc (bottom-right)
            drawArc(
                color = GoogleGreen,
                startAngle = 45f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Yellow arc (bottom-left)
            drawArc(
                color = GoogleYellow,
                startAngle = 135f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Red arc (top-left)
            drawArc(
                color = GoogleRed,
                startAngle = 225f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
            )
            // Center horizontal crossbar
            drawLine(
                color = GoogleBlue,
                start = Offset(center.x - radius * 0.05f, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Square
            )
        }
    }
}

@Composable
fun GoogleTextBrand(modifier: Modifier = Modifier, fontSize: Int = 22) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text("G", color = GoogleBlue, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        Text("o", color = GoogleRed, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        Text("o", color = GoogleYellow, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        Text("g", color = GoogleBlue, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        Text("l", color = GoogleGreen, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
        Text("e", color = GoogleRed, fontSize = fontSize.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier, size: Dp = 14.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(VerifiedBadgeBlue),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Verified badge",
            tint = Color.White,
            modifier = Modifier.size(size * 0.75f)
        )
    }
}

@Composable
fun SourceDomainIcon(iconType: String, modifier: Modifier = Modifier) {
    when (iconType.lowercase()) {
        "instagram" -> {
            Box(
                modifier = modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF833AB4), Color(0xFFFD1D1D), Color(0xFFFCB045))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("📷", fontSize = 13.sp)
            }
        }
        "youtube" -> {
            Box(
                modifier = modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFFF0000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "YouTube",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        "tiktok" -> {
            Box(
                modifier = modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text("🎵", fontSize = 13.sp)
            }
        }
        else -> {
            Box(
                modifier = modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1A73E8)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Language,
                    contentDescription = "Web",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun AssetOrCoilImage(
    assetFileName: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val context = LocalContext.current
    val imagePath = remember(assetFileName) {
        if (!assetFileName.isNullOrBlank()) {
            if (assetFileName.startsWith("http")) assetFileName
            else "file:///android_asset/$assetFileName"
        } else {
            "file:///android_asset/image_87976832.png"
        }
    }

    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(imagePath)
            .crossfade(true)
            .build(),
        contentDescription = contentDescription,
        contentScale = contentScale,
        modifier = modifier
    )
}
