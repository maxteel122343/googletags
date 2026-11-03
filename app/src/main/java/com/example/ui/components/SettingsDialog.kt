package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.model.InterfaceVersion
import com.example.data.model.SearchEngineType
import com.example.data.model.SearchTemplate
import com.example.ui.theme.DarkElevatedCard
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.GoogleBlue

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsDialog(
    currentVersion: InterfaceVersion,
    defaultVersion: InterfaceVersion,
    onSetDefaultVersion: (InterfaceVersion) -> Unit,
    searchEngineType: SearchEngineType,
    onSetSearchEngineType: (SearchEngineType) -> Unit,
    autoSearchEnabled: Boolean,
    onToggleAutoSearch: (Boolean) -> Unit,
    searchTemplates: List<SearchTemplate>,
    activeTemplateId: String,
    onSelectTemplate: (String) -> Unit,
    onAddTemplate: (String, String) -> Unit,
    onRemoveTemplate: (String) -> Unit,
    onOpenAccessibilityGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    var newKeywordText by remember { mutableStateOf("") }
    val scrollState = rememberScrollState()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .heightIn(max = 680.dp)
                .clip(RoundedCornerShape(24.dp))
                .testTag("settings_dialog"),
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
                    Column {
                        Text(
                            text = "Configurações do Protótipo",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "FeedLens • Detecção & Busca no Google",
                            color = Color(0xFFAAAAAA),
                            fontSize = 12.sp
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("close_settings_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 1: VERSÃO DE INTERFACE PADRÃO (1 to 5)
                Text(
                    text = "1. ESCOLHA A VERSÃO DE INTERFACE PADRÃO",
                    color = GoogleBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                InterfaceVersion.entries.forEach { version ->
                    val isDefault = version == defaultVersion
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onSetDefaultVersion(version) }
                            .testTag("version_option_${version.id}"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isDefault) Color(0xFF1B2D4F) else DarkElevatedCard
                        ),
                        border = if (isDefault) androidx.compose.foundation.BorderStroke(1.5.dp, GoogleBlue) else null
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isDefault,
                                onClick = { onSetDefaultVersion(version) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = GoogleBlue,
                                    unselectedColor = Color(0xFF757575)
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = version.title,
                                    color = Color.White,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = version.subtitle,
                                    color = Color(0xFF8AB4F8),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = version.description,
                                    color = Color(0xFFB0B0B0),
                                    fontSize = 10.5.sp,
                                    lineHeight = 13.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 2: TIPO DE BUSCA (Imagens vs Search normal)
                Text(
                    text = "2. TIPO DE BUSCA NO GOOGLE",
                    color = GoogleBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SearchEngineType.entries.forEach { type ->
                        val isSelected = type == searchEngineType
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onSetSearchEngineType(type) }
                                .testTag("search_type_${type.name}"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) Color(0xFF1B2D4F) else DarkElevatedCard
                            ),
                            border = if (isSelected) androidx.compose.foundation.BorderStroke(1.5.dp, GoogleBlue) else null
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                GoogleIconBadge(size = 20.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = type.displayName,
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = type.badge,
                                    color = Color(0xFF8AB4F8),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 3: BUSCA AUTOMÁTICA AO SCROLLAR
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkElevatedCard)
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Busca automática ao rolar feed",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Ao trocar de vídeo, detecta o criador e dispara a busca",
                            color = Color(0xFFAAAAAA),
                            fontSize = 11.sp
                        )
                    }

                    Switch(
                        checked = autoSearchEnabled,
                        onCheckedChange = onToggleAutoSearch,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = GoogleBlue
                        ),
                        modifier = Modifier.testTag("auto_search_switch")
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 4: TEMPLATES PRÉ-PROGRAMADOS (nickname + keyword)
                Text(
                    text = "4. TEMPLATES DE BUSCA (nickname + palavra-chave)",
                    color = GoogleBlue,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = "Selecione o formato da busca automática executada ao trocar de vídeo:",
                    color = Color(0xFFAAAAAA),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(8.dp))

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    searchTemplates.forEach { template ->
                        val isActive = template.id == activeTemplateId
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isActive) GoogleBlue else DarkElevatedCard)
                                .clickable { onSelectTemplate(template.id) }
                                .padding(horizontal = 12.dp, vertical = 7.dp)
                                .testTag("template_chip_${template.id}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isActive) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                }
                                Text(
                                    text = template.label,
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                )
                                if (!template.isSystem) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Delete",
                                        tint = Color(0xFFFFAAAA),
                                        modifier = Modifier
                                            .size(13.dp)
                                            .clickable { onRemoveTemplate(template.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Add new template keyword
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newKeywordText,
                        onValueChange = { newKeywordText = it },
                        placeholder = { Text("Ex: look, beach, podcast...", fontSize = 12.sp, color = Color(0xFF757575)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = GoogleBlue,
                            unfocusedBorderColor = Color(0xFF3C4043),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("add_template_input")
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = {
                            if (newKeywordText.isNotBlank()) {
                                onAddTemplate(newKeywordText, newKeywordText)
                                newKeywordText = ""
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue),
                        modifier = Modifier.testTag("add_template_button")
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Criar", fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFF2E323D))
                Spacer(modifier = Modifier.height(16.dp))

                // SECTION 5: GUIA DO ACCESSIBILITY SERVICE
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenAccessibilityGuide)
                        .testTag("open_accessibility_guide_card"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E2638))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = Color(0xFF8AB4F8),
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Como funciona no Android Real?",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "AccessibilityService (TikTok, Reels, Shorts) e WindowManager Overlay",
                                color = Color(0xFFB0B0B0),
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Apply button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .testTag("apply_settings_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = GoogleBlue)
                ) {
                    Text("Concluir e Salvar", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
