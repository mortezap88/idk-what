package com.example.notepad.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.notepad.data.model.MediaItem
import com.example.ui.VirtualTextField
import com.example.notepad.data.model.MIN_NOTEPAD_LINE_COUNT
import com.example.notepad.data.model.NoteLine
import com.example.notepad.data.model.NotepadBlock
import com.example.notepad.ui.theme.CheckboxDeleteTint
import com.example.notepad.ui.theme.CheckboxPurpleBorder
import com.example.notepad.ui.theme.NotepadCardBg
import com.example.notepad.ui.theme.NotepadCardBorder
import com.example.notepad.ui.theme.TextMuted
import com.example.notepad.ui.theme.TextOffWhite
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val MEDIA_ITEMS_PER_ROW = 6

@Composable
fun NotepadCard(
    block: NotepadBlock,
    currentlyPlayingId: String?,
    onUpdateLine: (String, String) -> Unit,
    onLineTypingChanged: (String, Boolean) -> Unit,
    onInsertLineAfter: (String, String) -> Unit,
    onRemoveLine: (String) -> Unit,
    onLineActivated: (blockId: String, lineId: String, offset: Int) -> Unit,
    activeLineId: String?,
    activeSelection: Int,
    onTapMediaItem: (MediaItem) -> Unit,
    onLongPressMediaItem: (MediaItem) -> Unit,
    onTapAddMedia: () -> Unit,
    onStartVoiceRecording: () -> Unit,
    onStopVoiceRecording: () -> Unit,
    onCancelVoiceRecording: () -> Unit,
    modifier: Modifier = Modifier
) {
    val deletingLineIds = remember { mutableStateMapOf<String, Boolean>() }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = NotepadCardBg,
        border = BorderStroke(1.5.dp, NotepadCardBorder),
        modifier = modifier
            .fillMaxWidth()
            .testTag("notepad_block_card_${block.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp)
        ) {
            if (block.lines.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    block.lines.forEach { line ->
                        key(line.id) {
                            val isDeleting = deletingLineIds[line.id] == true

                            AnimatedVisibility(
                                visible = !isDeleting,
                                exit = shrinkVertically(
                                    animationSpec = spring(stiffness = 400f)
                                ) + fadeOut(
                                    animationSpec = tween(220)
                                )
                            ) {
                                NotepadLineRow(
                                    line = line,
                                    canRemove = block.lines.size > MIN_NOTEPAD_LINE_COUNT,
                                    isActive = activeLineId == line.id,
                                    selection = if (activeLineId == line.id) {
                                        activeSelection
                                    } else {
                                        line.text.length
                                    },
                                    onActiveChanged = { isTyping ->
                                        onLineTypingChanged(line.id, isTyping)
                                    },
                                    onActivate = { offset ->
                                        onLineActivated(block.id, line.id, offset)
                                    },
                                    onTextInserted = { newText ->
                                        onUpdateLine(line.id, newText)
                                    },
                                    onClearText = { onUpdateLine(line.id, "") },
                                    onCheckboxClicked = {
                                        deletingLineIds[line.id] = true
                                    }
                                )
                            }

                            LaunchedEffect(isDeleting) {
                                if (isDeleting) {
                                    delay(240)
                                    onRemoveLine(line.id)
                                    deletingLineIds.remove(line.id)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
            }

            // Fixed 6-per-row media grid filling the card width evenly
            val totalSlots = block.mediaItems.size + 1
            val rowCount = (totalSlots + MEDIA_ITEMS_PER_ROW - 1) / MEDIA_ITEMS_PER_ROW
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                for (rowIndex in 0 until rowCount) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        for (colIndex in 0 until MEDIA_ITEMS_PER_ROW) {
                            val slotIndex = rowIndex * MEDIA_ITEMS_PER_ROW + colIndex
                            when {
                                slotIndex < block.mediaItems.size -> {
                                    val item = block.mediaItems[slotIndex]
                                    key(item.id) {
                                        MediaItemSquircle(
                                            item = item,
                                            isPlaying = currentlyPlayingId == item.id,
                                            onTap = { onTapMediaItem(item) },
                                            onLongPress = { onLongPressMediaItem(item) },
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                slotIndex == block.mediaItems.size -> {
                                    key("add_media_squircle") {
                                        AddMediaSquircle(
                                            onTap = onTapAddMedia,
                                            onStartRecording = onStartVoiceRecording,
                                            onStopRecording = onStopVoiceRecording,
                                            onCancelRecording = onCancelVoiceRecording,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                else -> {
                                    Spacer(modifier = Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun NotepadLineRow(
    line: NoteLine,
    canRemove: Boolean,
    isActive: Boolean,
    selection: Int,
    onActiveChanged: (Boolean) -> Unit,
    onActivate: (Int) -> Unit,
    onTextInserted: (String) -> Unit,
    onClearText: () -> Unit,
    onCheckboxClicked: () -> Unit
) {
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    var isChecked by remember { mutableStateOf(false) }
    val popScale by animateFloatAsState(
        targetValue = if (isChecked) 0.82f else 1f,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 600f)
    )

    LaunchedEffect(isActive) {
        onActiveChanged(isActive)
        if (isActive) {
            delay(40)
            runCatching { bringIntoViewRequester.bringIntoView() }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bringIntoViewRequester(bringIntoViewRequester)
            .scale(popScale)
            .padding(vertical = 1.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        VirtualTextField(
            text = line.text,
            selection = selection,
            isActive = isActive,
            onActivate = onActivate,
            color = TextOffWhite,
            fontSize = 17.sp,
            lineHeight = 22.sp,
            caretColor = TextOffWhite,
            onPaste = { clip ->
                onTextInserted(clip)
                onActivate(clip.length)
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .testTag("line_input_${line.id}")
        )

        // Designated Line Remover Checkbox
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(22.dp)
                .background(
                    if (isChecked) CheckboxDeleteTint.copy(alpha = 0.35f) else Color.Transparent,
                    RoundedCornerShape(7.dp)
                )
                .border(
                    width = 1.5.dp,
                    color = if (isChecked) CheckboxDeleteTint else CheckboxPurpleBorder,
                    shape = RoundedCornerShape(7.dp)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    isChecked = true
                    if (canRemove) {
                        onCheckboxClicked()
                    } else {
                        onClearText()
                    }
                }
                .testTag("checkbox_remove_${line.id}")
        ) {
            if (isChecked) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = if (canRemove) "Remove line" else "Clear line",
                    tint = CheckboxDeleteTint,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    LaunchedEffect(isChecked) {
        if (isChecked) {
            delay(300)
            isChecked = false
        }
    }
}
