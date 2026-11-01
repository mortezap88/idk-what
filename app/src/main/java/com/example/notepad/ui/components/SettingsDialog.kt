package com.example.notepad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notepad.ui.theme.SquircleBorder
import com.example.notepad.ui.theme.TextMuted
import com.example.notepad.ui.theme.TextOffWhite
import com.example.settings.AiModelOption
import com.example.settings.AppSettingsRepository

@Composable
fun SettingsSidePanel(
    initialAvalaiKey: String,
    onSaveAiSettings: (avalaiKey: String) -> Unit,
    onResetTemplateData: () -> Unit,
    onDismiss: () -> Unit
) {
    var avalaiKeyInput by remember(initialAvalaiKey) { mutableStateOf(initialAvalaiKey) }
    var tttExpanded by rememberSaveable { mutableStateOf(false) }
    var sttExpanded by rememberSaveable { mutableStateOf(true) }
    var keyExpanded by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    Surface(
        shape = RoundedCornerShape(topStart = 24.dp, bottomStart = 24.dp),
        color = Color(0xFF212121),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_dialog_card")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { focusManager.clearFocus() }
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = Color(0xFF1C1C1C),
                border = BorderStroke(1.dp, Color(0xFF353535)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Model Controls",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AI PROVIDERS & MODELS",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }

                    // STT Opening
                    NotepadAccordionGroup(
                        title = "STT · Speech-to-Text",
                        expanded = sttExpanded,
                        onToggle = { sttExpanded = !sttExpanded },
                        testTag = "notepad_opening_stt"
                    ) {
                        AppSettingsRepository.STT_MODEL_OPTIONS.forEach { option ->
                            NotepadModelCard(option = option)
                        }
                    }

                    // TTT Opening
                    NotepadAccordionGroup(
                        title = "TTT · Text & Tutoring",
                        expanded = tttExpanded,
                        onToggle = { tttExpanded = !tttExpanded },
                        testTag = "notepad_opening_ttt"
                    ) {
                        AppSettingsRepository.TTT_MODEL_OPTIONS.forEach { option ->
                            NotepadModelCard(option = option)
                        }
                    }

                    // Quick Key Opening
                    NotepadAccordionGroup(
                        title = "Quick API Key Add (Hormouz / 1xAI / Houshyar24)",
                        expanded = keyExpanded,
                        onToggle = { keyExpanded = !keyExpanded },
                        testTag = "notepad_opening_api_key"
                    ) {
                        OutlinedTextField(
                            value = avalaiKeyInput,
                            onValueChange = { avalaiKeyInput = it },
                            singleLine = true,
                            placeholder = {
                                Text(
                                    "ng-... (Hormouz), sk-hy24-... or sk-...",
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "API Key",
                                    tint = TextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            textStyle = TextStyle(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextOffWhite,
                                unfocusedTextColor = TextOffWhite,
                                focusedBorderColor = Color.White,
                                unfocusedBorderColor = SquircleBorder
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("houshyar_api_key_input")
                        )

                        Button(
                            onClick = {
                                onSaveAiSettings(avalaiKeyInput.trim())
                                onDismiss()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF171717)
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp)
                                .testTag("save_ai_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Save,
                                contentDescription = "Save Settings",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save API Key", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Button(
                onClick = {
                    onResetTemplateData()
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF303030),
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color(0xFF484848)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("reset_template_data_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset Notepads",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Reset to Original Template Notepads",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
private fun NotepadAccordionGroup(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    testTag: String,
    content: @Composable () -> Unit
) {
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(),
        label = "notepad_accordion_chevron"
    )
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF242424),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clickable(onClick = onToggle)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .testTag(testTag),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    color = TextOffWhite,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                    tint = TextMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .rotate(rotation)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(animationSpec = spring()) + fadeIn(),
                exit = shrinkVertically(animationSpec = spring()) + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun NotepadModelCard(option: AiModelOption) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = Color(0xFF1D1D1D),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("notepad_model_option_${option.id.replace('/', '_')}")
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = option.label,
                color = TextOffWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = option.endpointLabel,
                color = TextMuted,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = option.note,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}
