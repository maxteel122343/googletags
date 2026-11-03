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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
    isAccessibilityConnected: Boolean,
    isAccessibilityEnabledInSystem: Boolean,
    hasOverlayPermission: Boolean,
    isRealOverlayShowing: Boolean,
    onOpenAccessibilitySettings: () -> Unit,
    onRequestOverlayPermission: () -> Unit,
    onToggleRealOverlay: () -> Unit,
    onSimulateEvent: (nickname: String, appName: String) -> Unit,
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
                .heightIn(max = 700.dp)
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
                                text = "Accessibility & Overlay Real",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Detecção de Criadores + WindowManager",
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

                // SECTION 1: STATUS E PERMISSÕES DO SISTEMA
                Text(
                    text = "1. STATUS DAS PERMISSÕES NO ANDROID",
                    color = GoogleBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Card: Accessibility Service Status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkElevatedCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isAccessibilityConnected || isAccessibilityEnabledInSystem)
                                        Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isAccessibilityConnected || isAccessibilityEnabledInSystem)
                                        Color(0xFF81C995) else Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Serviço de Acessibilidade",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (isAccessibilityConnected)
                                            "Ativo e Conectado ao Sistema"
                                        else if (isAccessibilityEnabledInSystem)
                                            "Habilitado nas configurações"
                                        else
                                            "Desativado nas configurações",
                                        color = if (isAccessibilityConnected || isAccessibilityEnabledInSystem)
                                            Color(0xFF81C995) else Color(0xFFFFB74D),
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            Button(
                                onClick = onOpenAccessibilitySettings,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26324D)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF8AB4F8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Abrir", fontSize = 10.5.sp, color = Color(0xFF8AB4F8))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Card: SYSTEM_ALERT_WINDOW Status
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkElevatedCard)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (hasOverlayPermission) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (hasOverlayPermission) Color(0xFF81C995) else Color(0xFFFFB74D),
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Sobrepor a outros apps",
                                        color = Color.White,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = if (hasOverlayPermission) "Permissão Concedida" else "Permissão Necessária",
                                        color = if (hasOverlayPermission) Color(0xFF81C995) else Color(0xFFFFB74D),
                                        fontSize = 10.5.sp
                                    )
                                }
                            }

                            Button(
                                onClick = onRequestOverlayPermission,
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26324D)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF8AB4F8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (hasOverlayPermission) "Gerenciar" else "Permitir", fontSize = 10.5.sp, color = Color(0xFF8AB4F8))
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 2: OVERLAY FLUTUANTE COM WEBVIEW
                Text(
                    text = "2. OVERLAY FLUTUANTE REAL (WINDOW MANAGER)",
                    color = GoogleBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "O overlay flutuante renderiza uma janela flutuante com um WebView real do Google. Quando você rola e detecta um nickname no TikTok, Instagram ou Shorts, a busca em imagem atualiza em tempo real!",
                    color = Color(0xFFB0B0B0),
                    fontSize = 11.sp,
                    lineHeight = 15.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = onToggleRealOverlay,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRealOverlayShowing) Color(0xFFD93025) else GoogleBlue
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(42.dp)
                        .testTag("toggle_real_overlay_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Layers,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isRealOverlayShowing) "Fechar Overlay Flutuante da Tela" else "Lançar Overlay Flutuante com WebView",
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.5.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 3: SIMULADOR DE EVENTOS DE DETECÇÃO
                Text(
                    text = "3. SIMULAR DETECÇÃO DE CRIADOR (TESTE RÁPIDO)",
                    color = GoogleBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Clique em um app para simular que o usuário rolou e o AccessibilityService capturou o nickname. Veja o WebView atualizar na hora:",
                    color = Color(0xFF9E9E9E),
                    fontSize = 10.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                val sampleSimulations = listOf(
                    Triple("TikTok", "ju.pasqualotto", "com.zhiliaoapp.musically"),
                    Triple("Instagram Reels", "fitwithjoao", "com.instagram.android"),
                    Triple("YouTube Shorts", "dancequeen_official", "com.google.android.youtube"),
                    Triple("Instagram", "lifestyle.ana", "com.instagram.android")
                )

                sampleSimulations.forEach { (appName, nick, pkg) ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = DarkElevatedCard)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = appName,
                                    color = Color.White,
                                    fontSize = 12.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "@$nick • $pkg",
                                    color = Color(0xFF8AB4F8),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }

                            Button(
                                onClick = {
                                    simulatedAppFeedback = "Detectado: @$nick no $appName"
                                    onSimulateEvent(nick, appName)
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF26324D)),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF8AB4F8))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Simular", fontSize = 11.sp, color = Color(0xFF8AB4F8))
                            }
                        }
                    }
                }

                if (simulatedAppFeedback != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "✓ $simulatedAppFeedback (WebView atualizado!)",
                        color = Color(0xFF81C995),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(14.dp))

                // SECTION 4: INFORMAÇÕES DE IMPLEMENTAÇÃO
                Text(
                    text = "4. ARQUIVOS IMPLEMENTADOS NO PROJETO",
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
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text(
                            text = "• CreatorDetectorAccessibilityService.kt\n" +
                                    "  Service registrado com permissão BIND_ACCESSIBILITY_SERVICE\n\n" +
                                    "• res/xml/accessibility_service_config.xml\n" +
                                    "  Configura escuta de TikTok, Instagram e YouTube Shorts\n\n" +
                                    "• FloatingOverlayManager.kt\n" +
                                    "  Gerencia WindowManager TYPE_APPLICATION_OVERLAY com WebView\n\n" +
                                    "• CreatorDetectionBus.kt\n" +
                                    "  Barramento reativo ligando Service -> Overlay -> FeedScreen",
                            color = Color(0xFFDCDCDC),
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            lineHeight = 14.sp
                        )
                    }
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
                    Text("Fechar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
