package com.example.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.settings.AiModelOption
import com.example.settings.ApiKeyProvider
import com.example.settings.AppSettings
import com.example.settings.AppSettingsRepository
import com.example.settings.AppThemePreference
import com.example.ui.theme.AccentPink
import com.example.ui.theme.BadgeGreenBg
import com.example.ui.theme.BadgeGreenBorder
import com.example.ui.theme.BadgeGreenText
import com.example.ui.theme.BgDark
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceInput
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun SettingsScreen(
    settings: AppSettings,
    ttsVoices: List<TtsVoiceOption>,
    onThemeSelected: (AppThemePreference) -> Unit,
    onTttProviderSelected: (ApiKeyProvider) -> Unit = {},
    onTttModelSelected: (String) -> Unit = {},
    onSttModelSelected: (String) -> Unit = {},
    onTokenStreamingSelected: (Boolean) -> Unit,
    onMaxFontSizeSelected: (Int) -> Unit,
    onTtsVoiceSelected: (String?) -> Unit,
    onTtsRateSelected: (Float) -> Unit,
    onTestTts: () -> Unit,
    onAddApiKey: (ApiKeyProvider, String) -> Boolean,
    onRemoveApiKey: (ApiKeyProvider, Int) -> Unit,
    onClearApiKeys: (ApiKeyProvider) -> Unit,
    reviewItemCount: Int = 0,
    subjectCount: Int = 0,
    backupState: BackupUiState = BackupUiState.Idle,
    onExportFlashData: (Uri) -> Unit = {},
    onImportFlashData: (Uri) -> Unit = {}
) {
    var hormouzKeyInput by rememberSaveable { mutableStateOf("") }
    var onexaiKeyInput by rememberSaveable { mutableStateOf("") }
    var houshyarKeyInput by rememberSaveable { mutableStateOf("") }

    var activeKeyFieldProvider by remember { mutableStateOf<ApiKeyProvider?>(null) }
    var hormouzSelection by remember { mutableIntStateOf(0) }
    var onexaiSelection by remember { mutableIntStateOf(0) }
    var houshyarSelection by remember { mutableIntStateOf(0) }

    val scrollState = rememberScrollState()
    val isKeyboardVisible = activeKeyFieldProvider != null

    LaunchedEffect(activeKeyFieldProvider) {
        if (activeKeyFieldProvider != null) {
            scrollState.animateScrollTo((scrollState.value + 220).coerceAtMost(scrollState.maxValue))
        }
    }

    BackHandler(enabled = isKeyboardVisible) {
        activeKeyFieldProvider = null
    }

    KeyboardDismissSurface(
        isKeyboardVisible = isKeyboardVisible,
        onDismissKeyboard = { activeKeyFieldProvider = null },
        modifier = Modifier.fillMaxSize()
    ) {
        Scaffold(
            containerColor = BgDark,
            bottomBar = {
                val activeProvider = activeKeyFieldProvider
                if (activeProvider != null) {
                    val currentText = when (activeProvider) {
                        ApiKeyProvider.HORMOUZ -> hormouzKeyInput
                        ApiKeyProvider.ONEXAI -> onexaiKeyInput
                        ApiKeyProvider.HOUSHYAR24 -> houshyarKeyInput
                    }
                    val currentSelection = when (activeProvider) {
                        ApiKeyProvider.HORMOUZ -> hormouzSelection
                        ApiKeyProvider.ONEXAI -> onexaiSelection
                        ApiKeyProvider.HOUSHYAR24 -> houshyarSelection
                    }
                    CreationVirtualKeyboard(
                        text = TextFieldValue(
                            currentText,
                            TextRange(currentSelection.coerceIn(0, currentText.length))
                        ),
                        onTextChange = { updated ->
                            when (activeProvider) {
                                ApiKeyProvider.HORMOUZ -> {
                                    hormouzKeyInput = updated.text
                                    hormouzSelection = updated.selection.end
                                }
                                ApiKeyProvider.ONEXAI -> {
                                    onexaiKeyInput = updated.text
                                    onexaiSelection = updated.selection.end
                                }
                                ApiKeyProvider.HOUSHYAR24 -> {
                                    houshyarKeyInput = updated.text
                                    houshyarSelection = updated.selection.end
                                }
                            }
                        },
                        onSend = { activeKeyFieldProvider = null },
                        showInputField = false,
                        sendLabel = "Done"
                    )
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                        .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ApiProvidersOpeningSection(
                        settings = settings,
                        hormouzKeyInput = hormouzKeyInput,
                        onHormouzKeyInputChange = { hormouzKeyInput = it },
                        hormouzSelection = hormouzSelection,
                        onHormouzSelectionChange = { hormouzSelection = it },
                        houshyarKeyInput = houshyarKeyInput,
                        onHoushyarKeyInputChange = { houshyarKeyInput = it },
                        houshyarSelection = houshyarSelection,
                        onHoushyarSelectionChange = { houshyarSelection = it },
                        onexaiKeyInput = onexaiKeyInput,
                        onOnexaiKeyInputChange = { onexaiKeyInput = it },
                        onexaiSelection = onexaiSelection,
                        onOnexaiSelectionChange = { onexaiSelection = it },
                        activeFieldProvider = activeKeyFieldProvider,
                        onFieldActivate = { provider -> activeKeyFieldProvider = provider },
                        onAddApiKey = onAddApiKey,
                        onRemoveApiKey = onRemoveApiKey,
                        onClearApiKeys = onClearApiKeys
                    )

                    ModelsOpeningSection(
                        settings = settings,
                        onTttProviderSelected = onTttProviderSelected,
                        onTttModelSelected = onTttModelSelected,
                        onSttModelSelected = onSttModelSelected
                    )

                    ThemeSection(settings = settings, onThemeSelected = onThemeSelected)

                    ChatSection(
                        settings = settings,
                        onTokenStreamingSelected = onTokenStreamingSelected,
                        onMaxFontSizeSelected = onMaxFontSizeSelected
                    )

                    TtsSection(
                        settings = settings,
                        ttsVoices = ttsVoices,
                        onTtsVoiceSelected = onTtsVoiceSelected,
                        onTtsRateSelected = onTtsRateSelected,
                        onTestTts = onTestTts
                    )

                    DataBackupSection(
                        storedApiKeyCount = settings.totalStoredApiKeyCount,
                        reviewItemCount = reviewItemCount,
                        subjectCount = subjectCount,
                        backupState = backupState,
                        onExportFlashData = onExportFlashData,
                        onImportFlashData = onImportFlashData
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ApiProvidersOpeningSection(
    settings: AppSettings,
    hormouzKeyInput: String,
    onHormouzKeyInputChange: (String) -> Unit,
    hormouzSelection: Int,
    onHormouzSelectionChange: (Int) -> Unit,
    houshyarKeyInput: String,
    onHoushyarKeyInputChange: (String) -> Unit,
    houshyarSelection: Int,
    onHoushyarSelectionChange: (Int) -> Unit,
    onexaiKeyInput: String,
    onOnexaiKeyInputChange: (String) -> Unit,
    onexaiSelection: Int,
    onOnexaiSelectionChange: (Int) -> Unit,
    activeFieldProvider: ApiKeyProvider?,
    onFieldActivate: (ApiKeyProvider?) -> Unit,
    onAddApiKey: (ApiKeyProvider, String) -> Boolean,
    onRemoveApiKey: (ApiKeyProvider, Int) -> Unit,
    onClearApiKeys: (ApiKeyProvider) -> Unit
) {
    val readyProvidersCount = listOf(
        settings.hasHormouzApiKey,
        settings.hasHoushyarApiKey,
        settings.hasOnexaiApiKey
    ).count { it }

    ExpandableSettingsCard(
        title = "API Providers",
        subtitle = "Manage encrypted API key pools across Hormouz, Houshyar24, and 1xAI.",
        summaryBadge = "$readyProvidersCount/3 ready",
        icon = Icons.Default.Key,
        initiallyExpanded = readyProvidersCount == 0,
        testTag = "opening_api_providers_section"
    ) {
        // 1. Hormouz (NEW)
        ExpandableSubsectionCard(
            title = "Hormouz (api.hormouz.net/v1)",
            subtitle = "Powers GLM 5.3 Flash (Low), GPT-6 Luna & Microsoft MAI-Transcribe 2",
            badgeText = providerStatusLabel(settings.hormouzKeyPreviews.size, settings.hasHormouzApiKey),
            badgeReady = settings.hasHormouzApiKey,
            initiallyExpanded = !settings.hasHormouzApiKey || activeFieldProvider == ApiKeyProvider.HORMOUZ,
            testTag = "opening_provider_hormouz"
        ) {
            ProviderKeyPoolBody(
                provider = ApiKeyProvider.HORMOUZ,
                previews = settings.hormouzKeyPreviews,
                activeIndex = settings.activeHormouzKeyIndex,
                hasKey = settings.hasHormouzApiKey,
                missingKeyHint = "Add a Hormouz API key (ng-...) or set HORMOUZ_API_KEY in AI Studio Secrets for GLM 5.3 Flash, GPT-6 Luna, and Microsoft MAI-Transcribe 2.",
                keyInput = hormouzKeyInput,
                onKeyInputChange = onHormouzKeyInputChange,
                selection = hormouzSelection,
                onSelectionChange = onHormouzSelectionChange,
                isFieldActive = activeFieldProvider == ApiKeyProvider.HORMOUZ,
                onFieldActivate = { onFieldActivate(ApiKeyProvider.HORMOUZ) },
                onAddApiKey = { key -> onAddApiKey(ApiKeyProvider.HORMOUZ, key) },
                onRemoveApiKey = { idx -> onRemoveApiKey(ApiKeyProvider.HORMOUZ, idx) },
                onClearApiKeys = { onClearApiKeys(ApiKeyProvider.HORMOUZ) },
                tagPrefix = "hormouz"
            )
        }

        // 2. Houshyar24
        ExpandableSubsectionCard(
            title = "Houshyar24 (houshyar24.ir/api/openai/v1)",
            subtitle = "Powers GPT 6 Luna for tutoring & evaluations",
            badgeText = providerStatusLabel(settings.houshyarKeyPreviews.size, settings.hasHoushyarApiKey),
            badgeReady = settings.hasHoushyarApiKey,
            initiallyExpanded = activeFieldProvider == ApiKeyProvider.HOUSHYAR24,
            testTag = "opening_provider_houshyar24"
        ) {
            ProviderKeyPoolBody(
                provider = ApiKeyProvider.HOUSHYAR24,
                previews = settings.houshyarKeyPreviews,
                activeIndex = settings.activeHoushyarKeyIndex,
                hasKey = settings.hasHoushyarApiKey,
                missingKeyHint = "Add a Houshyar24 API key (sk-hy24-...) or set HOUSHYAR24_API_KEY in AI Studio Secrets for GPT 6 Luna.",
                keyInput = houshyarKeyInput,
                onKeyInputChange = onHoushyarKeyInputChange,
                selection = houshyarSelection,
                onSelectionChange = onHoushyarSelectionChange,
                isFieldActive = activeFieldProvider == ApiKeyProvider.HOUSHYAR24,
                onFieldActivate = { onFieldActivate(ApiKeyProvider.HOUSHYAR24) },
                onAddApiKey = { key -> onAddApiKey(ApiKeyProvider.HOUSHYAR24, key) },
                onRemoveApiKey = { idx -> onRemoveApiKey(ApiKeyProvider.HOUSHYAR24, idx) },
                onClearApiKeys = { onClearApiKeys(ApiKeyProvider.HOUSHYAR24) },
                tagPrefix = "houshyar"
            )
        }

        // 3. 1xAI
        ExpandableSubsectionCard(
            title = "1xAI (1xai.ir/v1)",
            subtitle = "Powers Gemini 3.1 Flash-Lite speech-to-text & analysis",
            badgeText = providerStatusLabel(settings.onexaiKeyPreviews.size, settings.hasOnexaiApiKey),
            badgeReady = settings.hasOnexaiApiKey,
            initiallyExpanded = activeFieldProvider == ApiKeyProvider.ONEXAI,
            testTag = "opening_provider_onexai"
        ) {
            ProviderKeyPoolBody(
                provider = ApiKeyProvider.ONEXAI,
                previews = settings.onexaiKeyPreviews,
                activeIndex = settings.activeOnexaiKeyIndex,
                hasKey = settings.hasOnexaiApiKey,
                missingKeyHint = "Add a 1xAI API key (sk-...) or set ONEXAI_API_KEY in AI Studio Secrets for Gemini 3.1 Flash-Lite.",
                keyInput = onexaiKeyInput,
                onKeyInputChange = onOnexaiKeyInputChange,
                selection = onexaiSelection,
                onSelectionChange = onOnexaiSelectionChange,
                isFieldActive = activeFieldProvider == ApiKeyProvider.ONEXAI,
                onFieldActivate = { onFieldActivate(ApiKeyProvider.ONEXAI) },
                onAddApiKey = { key -> onAddApiKey(ApiKeyProvider.ONEXAI, key) },
                onRemoveApiKey = { idx -> onRemoveApiKey(ApiKeyProvider.ONEXAI, idx) },
                onClearApiKeys = { onClearApiKeys(ApiKeyProvider.ONEXAI) },
                tagPrefix = "onexai"
            )
        }

        settings.errorMessage?.let { error ->
            Text(error, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
        }

        Text(
            text = "All API keys are AES-GCM encrypted locally in Android Keystore and rotate automatically if a key hits quota limits.",
            color = TextMuted,
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun ModelsOpeningSection(
    settings: AppSettings,
    onTttProviderSelected: (ApiKeyProvider) -> Unit,
    onTttModelSelected: (String) -> Unit,
    onSttModelSelected: (String) -> Unit
) {
    val activeTtt = settings.activeTttOption
    val activeStt = settings.activeSttOption

    ExpandableSettingsCard(
        title = "Models",
        subtitle = "TTT: ${activeTtt.label} · STT: ${activeStt.label}",
        summaryBadge = "TTT + STT",
        icon = Icons.Default.AutoAwesome,
        initiallyExpanded = false,
        testTag = "opening_models_section"
    ) {
        // Nested Opening 1: TTT (Text-to-Text)
        ExpandableSubsectionCard(
            title = "TTT · Text & Tutoring Models",
            subtitle = "Active: ${activeTtt.label}",
            badgeText = activeTtt.provider.displayName,
            badgeReady = settings.hasKeyForProvider(activeTtt.provider),
            icon = Icons.Default.Psychology,
            initiallyExpanded = true,
            testTag = "opening_models_ttt"
        ) {
            AppSettingsRepository.TTT_MODEL_OPTIONS.forEach { option ->
                val isSelected = activeTtt.id.equals(option.id, ignoreCase = true)
                val hasKey = settings.hasKeyForProvider(option.provider)
                SelectableModelOptionCard(
                    option = option,
                    isSelected = isSelected,
                    hasProviderKey = hasKey,
                    onClick = {
                        onTttModelSelected(option.id)
                        onTttProviderSelected(option.provider)
                    },
                    testTag = "ttt_model_option_${option.id.replace('/', '_')}"
                )
            }
        }

        // Nested Opening 2: STT (Speech-to-Text)
        ExpandableSubsectionCard(
            title = "STT · Speech-to-Text Models",
            subtitle = "Active: ${activeStt.label}",
            badgeText = activeStt.provider.displayName,
            badgeReady = settings.hasKeyForProvider(activeStt.provider),
            icon = Icons.Default.GraphicEq,
            initiallyExpanded = true,
            testTag = "opening_models_stt"
        ) {
            AppSettingsRepository.STT_MODEL_OPTIONS.forEach { option ->
                val isSelected = activeStt.id.equals(option.id, ignoreCase = true)
                val hasKey = settings.hasKeyForProvider(option.provider)
                SelectableModelOptionCard(
                    option = option,
                    isSelected = isSelected,
                    hasProviderKey = hasKey,
                    onClick = { onSttModelSelected(option.id) },
                    testTag = "stt_model_option_${option.id.replace('/', '_')}"
                )
            }
            if (activeStt.isDedicatedSttOnly) {
                Text(
                    text = "Microsoft MAI-Transcribe 2 handles verbatim speech-to-text, while post-transcription translation & spoken grammar hints run automatically on GLM 5.3 Flash (Low).",
                    color = TextMuted,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun SelectableModelOptionCard(
    option: AiModelOption,
    isSelected: Boolean,
    hasProviderKey: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val borderColor = if (isSelected) PrimaryPurple else SurfaceCardBorder
    val containerColor = if (isSelected) SurfaceDark else SurfaceInput

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(containerColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = option.label,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StatusPill(
                    text = if (hasProviderKey) "${option.provider.displayName} · Ready" else "${option.provider.displayName} · Key needed",
                    isReady = hasProviderKey
                )
            }
            Text(
                text = option.endpointLabel,
                color = TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = option.note,
                color = TextMuted,
                fontSize = 11.sp
            )
        }
        if (isSelected) {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = "Selected model",
                tint = PrimaryPurple,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ThemeSection(
    settings: AppSettings,
    onThemeSelected: (AppThemePreference) -> Unit
) {
    ExpandableSettingsCard(
        title = "Theme",
        subtitle = "How FlashRecall looks.",
        summaryBadge = settings.theme.title,
        icon = Icons.Default.Palette,
        initiallyExpanded = false,
        testTag = "opening_theme_section"
    ) {
        AppThemePreference.entries.forEach { theme ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onThemeSelected(theme) }
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = settings.theme == theme,
                    onClick = { onThemeSelected(theme) }
                )
                Text(theme.title, color = TextPrimary)
            }
        }
    }
}

@Composable
private fun ChatSection(
    settings: AppSettings,
    onTokenStreamingSelected: (Boolean) -> Unit,
    onMaxFontSizeSelected: (Int) -> Unit
) {
    var fontDraft by remember(settings.maxFontSizeSp) {
        mutableFloatStateOf(settings.maxFontSizeSp.toFloat())
    }
    val streamSummary = if (settings.tokenStreaming) "Streaming" else "Batch"

    ExpandableSettingsCard(
        title = "Chat",
        subtitle = "How AI responses are shown.",
        summaryBadge = "$streamSummary · ${settings.maxFontSizeSp}sp",
        icon = Icons.AutoMirrored.Filled.Chat,
        initiallyExpanded = false,
        testTag = "opening_chat_section"
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Token streaming", color = TextPrimary)
                Text("Show responses as they are generated.", color = TextSecondary, fontSize = 12.sp)
            }
            Switch(
                checked = settings.tokenStreaming,
                onCheckedChange = onTokenStreamingSelected
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Font size", color = TextPrimary)
            Text("${fontDraft.roundToInt()}sp", color = TextSecondary, fontSize = 13.sp)
        }
        Slider(
            value = fontDraft,
            onValueChange = { fontDraft = it },
            onValueChangeFinished = { onMaxFontSizeSelected(fontDraft.roundToInt()) },
            valueRange = AppSettingsRepository.MIN_MAX_FONT_SIZE.toFloat()..
                AppSettingsRepository.MAX_MAX_FONT_SIZE.toFloat(),
            steps = 14
        )
    }
}

@Composable
private fun TtsSection(
    settings: AppSettings,
    ttsVoices: List<TtsVoiceOption>,
    onTtsVoiceSelected: (String?) -> Unit,
    onTtsRateSelected: (Float) -> Unit,
    onTestTts: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var rateDraft by remember(settings.ttsRate) { mutableFloatStateOf(settings.ttsRate) }
    val selectedVoice = ttsVoices.firstOrNull { it.name == settings.ttsVoiceName }
    val speedLabel = String.format(Locale.getDefault(), "%.1f×", settings.ttsRate)

    ExpandableSettingsCard(
        title = "Text to Speech",
        subtitle = "The voice used to read cards aloud.",
        summaryBadge = speedLabel,
        icon = Icons.AutoMirrored.Filled.VolumeUp,
        initiallyExpanded = false,
        testTag = "opening_tts_section"
    ) {
        Box {
            OutlinedButton(
                onClick = { menuExpanded = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
            ) {
                Text(
                    text = selectedVoice?.label ?: "System default voice",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                DropdownMenuItem(
                    text = { Text("System default voice") },
                    onClick = {
                        onTtsVoiceSelected(null)
                        menuExpanded = false
                    }
                )
                ttsVoices.forEach { voice ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = voice.label,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        onClick = {
                            onTtsVoiceSelected(voice.name)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
        if (ttsVoices.isEmpty()) {
            Text(
                text = "No extra voices reported by the system engine. The default voice will be used.",
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Speed", color = TextPrimary)
            Text(
                text = String.format(Locale.getDefault(), "%.1f×", rateDraft),
                color = TextSecondary,
                fontSize = 13.sp
            )
        }
        Slider(
            value = rateDraft,
            onValueChange = { rateDraft = it },
            onValueChangeFinished = { onTtsRateSelected(rateDraft) },
            valueRange = AppSettingsRepository.MIN_TTS_RATE..AppSettingsRepository.MAX_TTS_RATE,
            steps = 14
        )

        OutlinedButton(
            onClick = onTestTts,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
        ) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Test voice",
                modifier = Modifier.width(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text("Test voice")
        }
    }
}

@Composable
private fun ProviderKeyPoolBody(
    provider: ApiKeyProvider,
    previews: List<String>,
    activeIndex: Int,
    hasKey: Boolean,
    missingKeyHint: String,
    keyInput: String,
    onKeyInputChange: (String) -> Unit,
    selection: Int,
    onSelectionChange: (Int) -> Unit,
    isFieldActive: Boolean,
    onFieldActivate: () -> Unit,
    onAddApiKey: (String) -> Boolean,
    onRemoveApiKey: (Int) -> Unit,
    onClearApiKeys: () -> Unit,
    tagPrefix: String
) {
    val optionLabel = provider.displayName
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        previews.forEachIndexed { index, preview ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(start = 12.dp, end = 4.dp, top = 2.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("${index + 1}", color = TextMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = preview,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
                if (index == activeIndex) {
                    Spacer(modifier = Modifier.width(8.dp))
                    StatusPill(text = "In use", isReady = true)
                }
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = { onRemoveApiKey(index) },
                    modifier = Modifier.testTag("${tagPrefix}_remove_key_$index")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Remove $optionLabel key ${index + 1}",
                        tint = AccentPink.copy(alpha = 0.85f)
                    )
                }
            }
        }

        if (previews.isEmpty()) {
            Text(
                text = if (hasKey) {
                    "A key from AI Studio Secrets is active."
                } else {
                    missingKeyHint
                },
                color = TextMuted,
                fontSize = 12.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark)
                .border(
                    width = 1.dp,
                    color = if (isFieldActive) PrimaryPurple else SurfaceCardBorder,
                    shape = RoundedCornerShape(14.dp)
                )
                .virtualFieldContainer {
                    onSelectionChange(keyInput.length)
                    onFieldActivate()
                }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("${tagPrefix}_key_field")
        ) {
            VirtualTextField(
                text = keyInput,
                selection = selection,
                isActive = isFieldActive,
                onActivate = { offset ->
                    onSelectionChange(offset)
                    onFieldActivate()
                },
                placeholder = provider.placeholder,
                color = TextPrimary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 1,
                onPaste = { clip ->
                    onKeyInputChange(clip.trim())
                    onSelectionChange(clip.trim().length)
                    onFieldActivate()
                }
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (onAddApiKey(keyInput)) {
                        onKeyInputChange("")
                        onSelectionChange(0)
                    }
                },
                enabled = keyInput.isNotBlank(),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("${tagPrefix}_add_key_button")
            ) {
                Text("Add key")
            }
            if (previews.isNotEmpty()) {
                TextButton(
                    onClick = onClearApiKeys,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("${tagPrefix}_clear_keys_button")
                ) {
                    Text("Remove all", color = PrimaryPurple)
                }
            }
        }
    }
}

@Composable
internal fun ExpandableSettingsCard(
    title: String,
    subtitle: String? = null,
    summaryBadge: String? = null,
    icon: ImageVector? = null,
    initiallyExpanded: Boolean = false,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(),
        label = "chevron_rotation"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .border(
                width = 1.dp,
                color = if (expanded) PrimaryPurple.copy(alpha = 0.55f) else SurfaceCardBorder,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .let { mod -> if (testTag != null) mod.testTag(testTag) else mod },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (icon != null) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(PrimaryPurple.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = PrimaryPurple,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = TextSecondary,
                        fontSize = 12.sp,
                        maxLines = if (expanded) 3 else 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            summaryBadge?.let { badge ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(SurfaceInput)
                        .border(1.dp, SurfaceCardBorder, RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = badge,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = TextSecondary,
                modifier = Modifier
                    .size(22.dp)
                    .rotate(chevronRotation)
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
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.7f))
                content()
            }
        }
    }
}

@Composable
private fun ExpandableSubsectionCard(
    title: String,
    subtitle: String? = null,
    badgeText: String? = null,
    badgeReady: Boolean = false,
    icon: ImageVector? = null,
    initiallyExpanded: Boolean = false,
    testTag: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = spring(),
        label = "subsection_chevron"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceInput)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(16.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable { expanded = !expanded }
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .let { mod -> if (testTag != null) mod.testTag(testTag) else mod },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = PrimaryPurple,
                    modifier = Modifier.size(18.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                subtitle?.let {
                    Text(
                        text = it,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            badgeText?.let { text ->
                StatusPill(text = text, isReady = badgeReady)
            }
            Icon(
                imageVector = Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse $title" else "Expand $title",
                tint = TextSecondary,
                modifier = Modifier
                    .size(20.dp)
                    .rotate(chevronRotation)
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
                    .padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = SurfaceCardBorder.copy(alpha = 0.6f))
                content()
            }
        }
    }
}

@Composable
internal fun SettingsCard(
    title: String,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceDark)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
            subtitle?.let {
                Text(it, color = TextSecondary, fontSize = 13.sp)
            }
        }
        content()
    }
}

@Composable
private fun StatusPill(
    text: String,
    isReady: Boolean
) {
    val bgColor = if (isReady) BadgeGreenBg else SurfaceDark
    val borderColor = if (isReady) BadgeGreenBorder else SurfaceCardBorder
    val textColor = if (isReady) BadgeGreenText else TextMuted

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = textColor, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun providerStatusLabel(storedCount: Int, hasKey: Boolean): String = when {
    storedCount > 0 -> "$storedCount ${if (storedCount == 1) "key" else "keys"}"
    hasKey -> "Secrets active"
    else -> "No key"
}
