package com.example.notepad.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.network.LinguisticCorrection
import com.example.notepad.data.model.MediaItem
import com.example.notepad.ui.theme.CyanAccent
import com.example.notepad.ui.theme.DeleteAccent
import com.example.notepad.ui.theme.NotepadCardBg
import com.example.notepad.ui.theme.NotepadCardBorder
import com.example.notepad.ui.theme.OnCyanAccent
import com.example.notepad.ui.theme.SquircleBg
import com.example.notepad.ui.theme.SquircleBorder
import com.example.notepad.ui.theme.TextMuted
import com.example.notepad.ui.theme.TextOffWhite
import com.example.ui.BidiText
import com.example.ui.ConversationCorrectedText
import com.example.ui.ConversationCorrectionDialog
import java.util.Locale

@Composable
fun AudioDetailsDialog(
    item: MediaItem,
    isPlaying: Boolean,
    currentPositionMs: Int,
    durationMs: Int,
    onTogglePlay: () -> Unit,
    onDelete: () -> Unit,
    onRetry: (() -> Unit)? = null,
    onApplyCorrection: ((String) -> Unit)? = null,
    onDismiss: () -> Unit
) {
    var activeCorrection by remember(item.id) { mutableStateOf<LinguisticCorrection?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.75f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() }
                .padding(horizontal = 24.dp, vertical = 28.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = NotepadCardBg,
                border = BorderStroke(1.5.dp, NotepadCardBorder),
                shadowElevation = 16.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 560.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { /* prevent backdrop tap */ }
                    .testTag("voice_note_review_modal")
            ) {
                Column(
                    modifier = Modifier.padding(22.dp)
                ) {
                    // Header: [Sound Wave icon] Voice Note   [Delete Trash] [Close X]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.GraphicEq,
                                contentDescription = null,
                                tint = CyanAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Voice Note",
                                color = TextOffWhite,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onDelete,
                                modifier = Modifier.size(48.dp).testTag("delete_voice_note_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete",
                                    tint = DeleteAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(48.dp).testTag("close_voice_note_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close",
                                    tint = TextMuted,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Player Section: Big circular button + Progress track with playhead
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(48.dp)
                                .background(CyanAccent, CircleShape)
                                .clickable { onTogglePlay() }
                                .testTag("voice_note_play_button")
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = OnCyanAccent,
                                modifier = Modifier.size(26.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        val effectiveDuration = durationMs.coerceAtLeast(1000)
                        val progressFraction = (currentPositionMs.toFloat() / effectiveDuration).coerceIn(0f, 1f)

                        Column(modifier = Modifier.weight(1f)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(26.dp),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .background(SquircleBg, RoundedCornerShape(4.dp))
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                        .height(8.dp)
                                        .background(CyanAccent, RoundedCornerShape(4.dp))
                                )

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .size(5.dp)
                                        .background(CyanAccent, CircleShape)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(progressFraction)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(3.dp)
                                            .height(24.dp)
                                            .background(CyanAccent, RoundedCornerShape(1.5.dp))
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatTime(currentPositionMs),
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = formatTime(effectiveDuration),
                                    color = TextMuted,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Scrollable Transcription + conditional Persian-to-English Translation boxes
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        // Transcription Box (with Red correction & Yellow improvement highlights)
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = SquircleBg,
                            border = BorderStroke(1.dp, SquircleBorder),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("voice_note_transcription_box")
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Transcription",
                                        color = CyanAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (item.isProcessing) {
                                        val hasVerbatim = !item.transcription.isNullOrBlank()
                                        val statusLabel = when {
                                            !hasVerbatim -> "Transcribing…"
                                            com.example.notepad.data.gemini.GeminiService.containsPersianScript(item.transcription.orEmpty()) ->
                                                "Translating…"
                                            else -> "Analyzing…"
                                        }
                                        Text(
                                            text = statusLabel,
                                            color = TextMuted,
                                            fontSize = 11.sp,
                                            modifier = Modifier.testTag("voice_note_processing_badge")
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))

                                val transcriptionText = item.transcription?.takeIf { it.isNotBlank() }
                                if (transcriptionText != null) {
                                    ConversationCorrectedText(
                                        text = transcriptionText,
                                        corrections = item.corrections,
                                        fontSizeSp = 15,
                                        textColor = TextOffWhite,
                                        hintColor = TextMuted,
                                        onCorrectionClick = { correction ->
                                            activeCorrection = correction
                                        }
                                    )
                                } else {
                                    Text(
                                        text = when {
                                            item.isProcessing -> "Transcription is in progress…"
                                            !item.transcriptionError.isNullOrBlank() -> "Transcription incomplete."
                                            else -> "No transcription is available."
                                        },
                                        color = TextOffWhite,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }

                                val errorBanner = item.transcriptionError?.takeIf { it.isNotBlank() }
                                if (!item.isProcessing && errorBanner != null) {
                                    Spacer(modifier = Modifier.height(10.dp))
                                    Text(
                                        text = errorBanner,
                                        color = DeleteAccent,
                                        fontSize = 13.sp,
                                        lineHeight = 18.sp,
                                        modifier = Modifier.testTag("voice_note_error_message")
                                    )
                                }

                                val showRetry = onRetry != null && !item.isProcessing && (
                                    !item.isTranscribed ||
                                        !item.transcriptionError.isNullOrBlank() ||
                                        item.transcription.isNullOrBlank()
                                    )
                                if (showRetry) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TextButton(
                                        onClick = { onRetry?.invoke() },
                                        modifier = Modifier
                                            .align(Alignment.End)
                                            .testTag("retry_voice_note_button")
                                    ) {
                                        Text(
                                            text = "Retry",
                                            color = CyanAccent,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // Translation Box: shown ONLY when Persian was spoken (Persian -> English)
                        val translationText = item.englishTranslation?.takeIf { it.isNotBlank() }
                        if (translationText != null) {
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = SquircleBg,
                                border = BorderStroke(1.dp, SquircleBorder),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("voice_note_translation_box")
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Translation",
                                        color = CyanAccent,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    BidiText(
                                        text = translationText,
                                        color = TextOffWhite,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        activeCorrection?.let { correction ->
            ConversationCorrectionDialog(
                correction = correction,
                onApply = {
                    onApplyCorrection?.invoke(correction.id)
                    activeCorrection = null
                },
                onDismiss = { activeCorrection = null }
            )
        }
    }
}

private fun formatTime(millis: Int): String {
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
