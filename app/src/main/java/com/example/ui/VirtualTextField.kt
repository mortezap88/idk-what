package com.example.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.PrimaryPurple
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import kotlin.math.roundToInt

/**
 * Text drawn as text — not as a text field — with a caret we control.
 *
 * Used wherever the app supplies its own keyboard: a real text field would either summon
 * the system IME or (when read-only) hide the caret and ignore taps.
 *
 * Tapping moves the caret to the touched character. Holding opens a small menu with
 * Paste and Select all, because a native field's selection bubble does not exist here.
 */
@Composable
fun VirtualTextField(
    text: String,
    selection: Int,
    isActive: Boolean,
    onActivate: (Int) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    color: Color = TextPrimary,
    fontSize: TextUnit = 15.sp,
    lineHeight: TextUnit = 20.sp,
    caretColor: Color = PrimaryPurple,
    maxLines: Int = Int.MAX_VALUE,
    /** Receives the whole clipboard text; the field replaces its content with it. */
    onPaste: ((clip: String) -> Unit)? = null
) {
    val directionSource = if (text.isNotEmpty()) text else placeholder.orEmpty()
    val direction =
        if (directionSource.isRtlText()) LayoutDirection.Rtl else LayoutDirection.Ltr
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var menuOffset by remember { mutableStateOf<Offset?>(null) }
    var selectAll by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val density = LocalDensity.current
    val dismissController = LocalKeyboardDismissController.current

    val caretAlpha by rememberInfiniteTransition(label = "caret").animateFloat(
        initialValue = 1f,
        targetValue = 0.15f,
        animationSpec = infiniteRepeatable(tween(520), RepeatMode.Reverse),
        label = "caret-alpha"
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .pointerInput(text.length, onPaste, dismissController) {
                detectTapGestures(
                    onPress = {
                        dismissController?.markFieldInteraction()
                    },
                    onTap = { position ->
                        dismissController?.markFieldInteraction()
                        selectAll = false
                        val result = layout ?: return@detectTapGestures
                        onActivate(result.getOffsetForPosition(position))
                    },
                    onLongPress = { position ->
                        dismissController?.markFieldInteraction()
                        onActivate(layout?.getOffsetForPosition(position) ?: selection)
                        menuOffset = position
                    }
                )
            }
    ) {
        if (text.isEmpty() && placeholder != null) {
            CompositionLocalProvider(LocalLayoutDirection provides direction) {
                Text(
                    text = placeholder,
                    color = TextMuted,
                    fontSize = fontSize,
                    lineHeight = lineHeight
                )
            }
        }

        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                lineHeight = lineHeight,
                maxLines = maxLines,
                style = TextStyle(textAlign = TextAlign.Start),
                onTextLayout = { layout = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (selectAll) {
                            Modifier.background(PrimaryPurple.copy(alpha = 0.18f))
                        } else {
                            Modifier
                        }
                    )
            )
        }

        if (isActive) {
            layout?.let { result ->
                // The layout can lag one frame behind the text; never index past what it measured.
                val measuredLength = result.layoutInput.text.length
                val caretOffset = selection.coerceIn(0, measuredLength)
                val caret = result.getCursorRect(caretOffset)
                Box(
                    modifier = Modifier
                        .offset { IntOffset(caret.left.roundToInt(), caret.top.roundToInt()) }
                        .width(2.dp)
                        .height(with(density) { caret.height.toDp() })
                        .background(caretColor.copy(alpha = caretAlpha))
                )
            }
        }

        val anchor = menuOffset
        if (anchor != null) {
            val clipboardText = context.readClipboardText()
            DropdownMenu(
                expanded = true,
                onDismissRequest = { menuOffset = null },
                offset = with(density) { DpOffset(anchor.x.toDp(), anchor.y.toDp()) }
            ) {
                DropdownMenuItem(
                    text = { Text("Paste") },
                    enabled = clipboardText != null && onPaste != null,
                    onClick = {
                        menuOffset = null
                        if (clipboardText != null) {
                            selectAll = false
                            onPaste?.invoke(clipboardText)
                        }
                    }
                )
                DropdownMenuItem(
                    text = { Text("Select all") },
                    enabled = text.isNotEmpty(),
                    onClick = {
                        menuOffset = null
                        selectAll = true
                    }
                )
            }
        }
    }
}

/** Reads plain text from the clipboard, if there is any. */
private fun Context.readClipboardText(): String? {
    val manager = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    return manager.primaryClip
        ?.takeIf { it.itemCount > 0 }
        ?.getItemAt(0)
        ?.coerceToText(this)
        ?.toString()
        ?.takeIf { it.isNotBlank() }
}
