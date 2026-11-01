package com.example.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.BgDark
import com.example.ui.theme.LocalIsDarkTheme
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.SurfaceCard
import com.example.ui.theme.SurfaceCardBorder
import com.example.ui.theme.SurfaceDark
import com.example.ui.theme.SurfaceInput
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import kotlin.math.abs

enum class KeyboardLanguage { EN, FA }

private const val ENGLISH_ROW_TOP = "qwertyuiop"
private const val ENGLISH_ROW_MIDDLE = "asdfghjkl"
private const val ENGLISH_ROW_BOTTOM = "zxcvbnm"

private const val PERSIAN_ROW_TOP = "ضصثقفغعهخحجچ"
private const val PERSIAN_ROW_MIDDLE = "شسیبلاتنمکگ"
private const val PERSIAN_ROW_BOTTOM = "ظطزرذدپوژ"

/** Zero-width non-joiner, the Persian "نیم‌فاصله". */
private const val ZWNJ = "\u200C"

/**
 * The app's own keyboard, used instead of the system IME.
 *
 * Spans the full width of the screen, supports English and Persian (swipe the space key to
 * switch), and sends with the key in the bottom-right corner.
 */
@Composable
fun CreationVirtualKeyboard(
    visible: Boolean = true,
    text: TextFieldValue,
    onTextChange: (TextFieldValue) -> Unit,
    onSend: (String) -> Unit,
    modifier: Modifier = Modifier,
    sendLabel: String = "Send",
    useSpecialKeyStyleForSend: Boolean = false,
    /** Chat shows the typed text above the keys; screens with their own field hide it. */
    showInputField: Boolean = true
) {
    var isShifted by remember { mutableStateOf(false) }
    var isNumbersMode by remember { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf(KeyboardLanguage.EN) }

    val isPersian = language == KeyboardLanguage.FA
    val isDarkTheme = LocalIsDarkTheme.current
    val trayBackground = if (isDarkTheme) SurfaceDark else BgDark
    val dismissController = LocalKeyboardDismissController.current
    val keyboardInstanceKey = remember { Any() }

    fun insert(value: String) = onTextChange(insertText(text, value))

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier.fillMaxWidth()
    ) {
        DisposableEffect(dismissController, keyboardInstanceKey) {
            onDispose {
                dismissController?.updateKeyboardBounds(keyboardInstanceKey, null)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    dismissController?.updateKeyboardBounds(
                        keyboardInstanceKey,
                        if (coords.isAttached) coords.boundsInRoot() else null
                    )
                }
                .background(trayBackground)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {}
        ) {
            HorizontalDivider(thickness = 1.dp, color = SurfaceCardBorder)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(top = 8.dp, bottom = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
            // Chat shows the typed text above the keys; other screens already have a field.
            if (showInputField) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceInput)
                        .heightIn(min = 46.dp)
                        .padding(horizontal = 14.dp, vertical = 12.dp)
                ) {
                    VirtualTextField(
                        text = text.text,
                        selection = text.selection.end,
                        isActive = true,
                        onActivate = { offset ->
                            onTextChange(text.copy(selection = TextRange(offset)))
                        },
                        placeholder = if (isPersian) "متن خود را بنویسید…" else "Type your message…"
                    )
                }
            }

            // Keys.
            if (isNumbersMode) {
                KeyRow(listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")) { insert(it) }
                KeyRow(listOf("@", "#", "$", "%", "&", "-", "+", "(", ")", "/")) { insert(it) }
                KeyRow(listOf("*", "\"", "'", ":", ";", "!", "?"), trailingAction = {
                    BackspaceKey(Modifier.weight(1.4f)) { onTextChange(deleteBeforeCursor(text)) }
                }) { insert(it) }
            } else {
                if (isPersian) {
                    KeyRow(PERSIAN_ROW_TOP.toCharArray().map { it.toString() }) { insert(it) }
                    KeyRow(PERSIAN_ROW_MIDDLE.toCharArray().map { it.toString() }) { insert(it) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        SpecialKey(
                            label = "نیم‌فاصله",
                            modifier = Modifier.weight(1.5f),
                            fontSize = 9.sp,
                            onClick = { insert(ZWNJ) }
                        )
                        PERSIAN_ROW_BOTTOM.forEach { letter ->
                            KeyButton(letter.toString(), Modifier.weight(1f)) {
                                insert(letter.toString())
                            }
                        }
                        BackspaceKey(Modifier.weight(1.5f)) { onTextChange(deleteBeforeCursor(text)) }
                    }
                } else {
                    KeyRow(ENGLISH_ROW_TOP.toCharArray().map { it.toString() }) {
                        insert(if (isShifted) it.uppercase() else it)
                    }
                    KeyRow(
                        ENGLISH_ROW_MIDDLE.toCharArray().map { it.toString() },
                        horizontalPadding = 12.dp
                    ) {
                        insert(if (isShifted) it.uppercase() else it)
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        SpecialKey(
                            label = "Shift",
                            icon = Icons.Default.ArrowUpward,
                            modifier = Modifier.weight(1.3f),
                            selected = isShifted,
                            onClick = { isShifted = !isShifted }
                        )
                        ENGLISH_ROW_BOTTOM.forEach { letter ->
                            KeyButton(if (isShifted) letter.uppercase() else letter.toString(), Modifier.weight(1f)) {
                                insert(if (isShifted) letter.uppercase() else letter.toString())
                                isShifted = false
                            }
                        }
                        BackspaceKey(Modifier.weight(1.3f)) { onTextChange(deleteBeforeCursor(text)) }
                    }
                }
            }

            // Bottom row: symbols, comma, space (swipe to switch language), period, send.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SpecialKey(
                    label = if (isNumbersMode) "ABC" else "?123",
                    modifier = Modifier.weight(1.3f),
                    onClick = { isNumbersMode = !isNumbersMode }
                )
                KeyButton(if (isPersian) "،" else ",", Modifier.weight(0.9f)) {
                    insert(if (isPersian) "،" else ",")
                }
                SpaceKey(
                    language = language,
                    modifier = Modifier.weight(3.4f),
                    onSpace = { insert(" ") },
                    onSwitchLanguage = {
                        language = if (isPersian) KeyboardLanguage.EN else KeyboardLanguage.FA
                        isNumbersMode = false
                    }
                )
                KeyButton(".", Modifier.weight(0.9f)) { insert(".") }
                SendKey(
                    label = sendLabel,
                    enabled = if (showInputField) text.text.isNotBlank() else true,
                    useSpecialKeyStyle = useSpecialKeyStyleForSend,
                    modifier = Modifier.weight(1.5f),
                    onClick = { onSend(text.text.trim()) }
                )
            }
            }
        }
    }
}

@Composable
private fun BackspaceKey(modifier: Modifier = Modifier, onClick: () -> Unit) {
    SpecialKey(
        label = "Backspace",
        icon = Icons.AutoMirrored.Filled.Backspace,
        modifier = modifier.testTag("key_backspace"),
        onClick = onClick
    )
}

@Composable
private fun SendKey(
    label: String,
    enabled: Boolean,
    useSpecialKeyStyle: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        useSpecialKeyStyle -> SurfaceCard
        enabled -> PrimaryPurple
        else -> SurfaceCard
    }
    val textColor = when {
        !enabled -> TextMuted
        useSpecialKeyStyle -> TextSecondary
        else -> MaterialTheme.colorScheme.onPrimary
    }

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(backgroundColor)
            .testTag("key_send")
            .clickable(
                enabled = enabled,
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = if (useSpecialKeyStyle) 12.sp else 13.sp,
            fontWeight = if (useSpecialKeyStyle) FontWeight.Medium else FontWeight.Bold
        )
    }
}

/**
 * Space bar that switches language when swiped sideways.
 */
@Composable
private fun SpaceKey(
    language: KeyboardLanguage,
    onSpace: () -> Unit,
    onSwitchLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    // The gesture block outlives recompositions, so it must read the latest callbacks.
    val currentOnSpace by rememberUpdatedState(onSpace)
    val currentOnSwitchLanguage by rememberUpdatedState(onSwitchLanguage)

    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(SurfaceInput)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(7.dp))
            .testTag("key_space")
            .pointerInput(language) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var switched = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!switched && abs(change.position.x - down.position.x) >= threshold) {
                            switched = true
                            currentOnSwitchLanguage()
                        }
                        if (!change.pressed) break
                    }
                    if (!switched) currentOnSpace()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (language == KeyboardLanguage.FA) "فاصله" else "space",
            color = TextPrimary,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun KeyRow(
    keys: List<String>,
    horizontalPadding: androidx.compose.ui.unit.Dp = 6.dp,
    trailingAction: (@Composable RowScope.() -> Unit)? = null,
    onKey: (String) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = horizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        keys.forEach { key ->
            KeyButton(key, Modifier.weight(1f)) { onKey(key) }
        }
        trailingAction?.invoke(this)
    }
}

@Composable
private fun KeyButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(SurfaceInput)
            .border(1.dp, SurfaceCardBorder, RoundedCornerShape(7.dp))
            .testTag("key_$label")
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = TextPrimary, fontSize = 15.sp)
    }
}

@Composable
private fun SpecialKey(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    fontSize: androidx.compose.ui.unit.TextUnit = 12.sp,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(38.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) PrimaryPurple else SurfaceCard)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onClick() },
        contentAlignment = Alignment.Center
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) MaterialTheme.colorScheme.onPrimary else TextSecondary,
                modifier = Modifier.width(18.dp).height(18.dp)
            )
        } else {
            Text(
                label,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else TextSecondary,
                fontSize = fontSize,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun insertText(value: TextFieldValue, insertion: String): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val updatedText = value.text.replaceRange(start, end, insertion)
    val cursor = start + insertion.length
    return TextFieldValue(updatedText, TextRange(cursor))
}

private fun deleteBeforeCursor(value: TextFieldValue): TextFieldValue {
    val start = value.selection.min.coerceIn(0, value.text.length)
    val end = value.selection.max.coerceIn(start, value.text.length)
    val deleteStart = if (start == end && start > 0) start - 1 else start
    val updatedText = value.text.removeRange(deleteStart, end)
    return TextFieldValue(updatedText, TextRange(deleteStart))
}

private fun moveCursor(value: TextFieldValue, offset: Int): TextFieldValue {
    val position = (value.selection.end + offset).coerceIn(0, value.text.length)
    return value.copy(selection = TextRange(position))
}
