package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.InterfaceVersion
import com.example.ui.theme.GoogleBlue

@Composable
fun QuickModeSwitcherBar(
    currentVersion: InterfaceVersion,
    onVersionSelected: (InterfaceVersion) -> Unit,
    activeTemplateName: String,
    onTemplateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onAccessibilityGuideClick: () -> Unit,
    isRealOverlayShowing: Boolean = false,
    onToggleRealOverlay: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp)
            .shadow(elevation = 8.dp, shape = RoundedCornerShape(20.dp))
            .testTag("quick_mode_switcher_bar"),
        color = Color(0xE614161B),
        shape = RoundedCornerShape(20.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode buttons scrollable row
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                InterfaceVersion.entries.forEach { version ->
                    val isSelected = version == currentVersion
                    val label = when (version) {
                        InterfaceVersion.BOTTOM_HORIZONTAL_CARDS -> "V1 Reels"
                        InterfaceVersion.SIDE_VERTICAL_CARDS -> "V2 Split"
                        InterfaceVersion.COMPACT_FLOATING_CARD -> "V3 Popup"
                        InterfaceVersion.FULL_SPLIT_SCREEN -> "V4 Google 50%"
                        InterfaceVersion.BOTTOM_SHEET -> "V5 Sheet"
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) GoogleBlue else Color(0x33FFFFFF))
                            .clickable { onVersionSelected(version) }
                            .padding(horizontal = 9.dp, vertical = 5.dp)
                            .testTag("mode_tab_${version.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) Color.White else Color(0xFFDDDDDD),
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                    }
                }

                // Template Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF2A2D36))
                        .clickable(onClick = onTemplateClick)
                        .padding(horizontal = 8.dp, vertical = 5.dp)
                        .testTag("active_template_pill"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "🔎 $activeTemplateName ▾",
                        color = Color(0xFF8AB4F8),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Real WindowManager Overlay Toggle Button
            IconButton(
                onClick = onToggleRealOverlay,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("quick_toggle_overlay_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = "Toggle Real Floating Overlay",
                    tint = if (isRealOverlayShowing) Color(0xFF81C995) else Color(0xFFB0B0B0),
                    modifier = Modifier.size(19.dp)
                )
            }

            // Accessibility Guide button
            IconButton(
                onClick = onAccessibilityGuideClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("accessibility_guide_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Accessibility Guide",
                    tint = Color(0xFF8AB4F8),
                    modifier = Modifier.size(19.dp)
                )
            }

            // Settings button
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(32.dp)
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}
