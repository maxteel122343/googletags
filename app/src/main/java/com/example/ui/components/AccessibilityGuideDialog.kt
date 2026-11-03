package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.service.AccessibilityGuideData
import com.example.ui.theme.DarkElevatedCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GoogleBlue

@Composable
fun AccessibilityGuideDialog(
    onSimulateEvent: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val scrollState = rememberScrollState()
    var simulatedAppFeedback by remember { mutableStateOf<String?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(24.dp))
                .testTag("accessibility_guide_dialog"),
            color = DarkSurface,
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF1B2D4F)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Security,
                                contentDescription = null,
                                tint = GoogleBlue,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "Guia Android Real",
                                color = Color.White,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "AccessibilityService & Window Overlay",
                                color = Color(0xFF8AB4F8),
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Intro explanation
                Text(
                    text = "Como o aplicativo detecta o @ do criador ao rolar no TikTok, Reels ou Shorts em um dispositivo Android real?",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "No Android, apps de terceiros rodam em sandbox isolada. Para ler a tela de outros apps sem root ou gravação de tela contínua, a arquitetura oficial utiliza o Android AccessibilityService.",
                    color = Color(0xFFB0B0B0),
                    fontSize = 11.5.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(14.dp))

                // APPS SUPORTADOS & PACOTES
                Text(
                    text = "1. PACOTES MONITORADOS PELO SISTEMA",
                    color = GoogleBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                AccessibilityGuideData.supportedApps.forEach { app ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkElevatedCard)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = app.name,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = {
                                        simulatedAppFeedback = "Evento disparado para: ${app.packageName}"
                                        onSimulateEvent(app.name)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26324D)),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF8AB4F8))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Simular", fontSize = 10.5.sp, color = Color(0xFF8AB4F8))
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Package: ${app.packageName}",
                                color = Color(0xFF8AB4F8),
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            if (app.altPackage != null) {
                                Text(
                                    text = "Alt: ${app.altPackage}",
                                    color = Color(0xFF9E9E9E),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                            Text(
                                text = "View ID: ${app.idPattern}",
                                color = Color(0xFF81C995),
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                if (simulatedAppFeedback != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "✓ $simulatedAppFeedback",
                        color = Color(0xFF81C995),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(14.dp))

                // FLUXO DO OVERLAY
                Text(
                    text = "2. DESENHO DO OVERLAY FLUTUANTE",
                    color = GoogleBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Com a permissão android.permission.SYSTEM_ALERT_WINDOW concedida pelo usuário:\n" +
                            "• O serviço obtém o WindowManager do sistema.\n" +
                            "• Define LayoutParams.TYPE_APPLICATION_OVERLAY.\n" +
                            "• Renderiza a ComposeView contendo qualquer uma das 5 interfaces (Cards Horizontais, Split Lateral, Popup Flutuante, etc.) flutuando sobre o TikTok ou Instagram aberto.",
                    color = Color(0xFFB0B0B0),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(14.dp))

                // CÓDIGO FONTE DE EXEMPLO
                Text(
                    text = "3. CÓDIGO KOTLIN DO SERVIÇO",
                    color = GoogleBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF13151A))
                ) {
                    Text(
                        text = AccessibilityGuideData.CODE_SAMPLE_SERVICE.trim(),
                        color = Color(0xFFDCDCDC),
                        fontSize = 9.5.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(10.dp),
                        lineHeight = 13.sp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
                ) {
                    Text("Entendi", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
