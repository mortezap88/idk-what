package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.CorrectionType
import com.example.network.LinguisticCorrection
import com.example.ui.theme.CorrectionRedBg
import com.example.ui.theme.CorrectionRedText
import com.example.ui.theme.ImprovementYellowBg
import com.example.ui.theme.ImprovementYellowText
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary

@Composable
private fun InteractiveCorrectedText(
    text: String,
    corrections: List<LinguisticCorrection>,
    fontSizeSp: Int,
    onCorrectionClick: (LinguisticCorrection) -> Unit,
    textColor: Color = TextPrimary,
    hintColor: Color = TextMuted,
    showTapHint: Boolean = true,
    modifier: Modifier = Modifier
) {
    if (corrections.isEmpty()) {
        BidiText(
            text = text,
            color = textColor,
            fontSize = fontSizeSp.sp,
            lineHeight = (fontSizeSp * 1.45f).sp,
            fontWeight = FontWeight.Normal,
            modifier = modifier
        )
        return
    }

    val grammarFg = CorrectionRedText
    val grammarBg = CorrectionRedBg
    val improvementFg = ImprovementYellowText
    val improvementBg = ImprovementYellowBg

    var hasGrammarHighlight = false
    var hasImprovementHighlight = false

    val annotatedString = buildAnnotatedString {
        append(text)
        val nextSearchIndex = mutableMapOf<String, Int>()
        corrections.forEach { correction ->
            val key = correction.original.lowercase()
            val searchFrom = nextSearchIndex[key] ?: 0
            val start = text.indexOf(correction.original, startIndex = searchFrom)
                .takeIf { it >= 0 }
                ?: text.indexOf(correction.original, startIndex = searchFrom, ignoreCase = true)
            if (start >= 0) {
                val end = start + correction.original.length
                nextSearchIndex[key] = end
                val isGrammar = correction.type == CorrectionType.GRAMMAR
                if (isGrammar) {
                    hasGrammarHighlight = true
                } else {
                    hasImprovementHighlight = true
                }
                addStyle(
                    style = SpanStyle(
                        color = if (isGrammar) grammarFg else improvementFg,
                        background = if (isGrammar) grammarBg else improvementBg,
                        textDecoration = TextDecoration.Underline,
                        fontWeight = FontWeight.SemiBold
                    ),
                    start = start,
                    end = end
                )
                addStringAnnotation(
                    tag = "CORRECTION",
                    annotation = correction.id,
                    start = start,
                    end = end
                )
            }
        }
    }

    var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
    val currentOnCorrectionClick by rememberUpdatedState(onCorrectionClick)
    val direction = if (text.isRtlText()) LayoutDirection.Rtl else LayoutDirection.Ltr
    val hasAnyHighlight = hasGrammarHighlight || hasImprovementHighlight

    Column(modifier = modifier) {
        CompositionLocalProvider(LocalLayoutDirection provides direction) {
            Text(
                text = annotatedString,
                style = TextStyle(
                    color = textColor,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp * 1.45f).sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Start
                ),
                onTextLayout = { textLayoutResult = it },
                modifier = Modifier
                    .testTag("interactive_corrected_text")
                    .pointerInput(annotatedString, corrections) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val layout = textLayoutResult ?: return@awaitEachGesture
                            val offset = layout.getOffsetForPosition(down.position)
                            val annotation = annotatedString.getStringAnnotations(
                                tag = "CORRECTION",
                                start = offset,
                                end = offset
                            ).firstOrNull() ?: return@awaitEachGesture

                            val up = waitForUpOrCancellation()
                            if (up != null) {
                                up.consume()
                                corrections.firstOrNull { it.id == annotation.item }
                                    ?.let(currentOnCorrectionClick)
                            }
                        }
                    }
            )
        }

        if (showTapHint && hasAnyHighlight) {
            Spacer(modifier = Modifier.height(6.dp))
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    modifier = Modifier.testTag("correction_tap_hint")
                ) {
                    if (hasGrammarHighlight) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(grammarFg, CircleShape)
                        )
                    }
                    if (hasImprovementHighlight) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(improvementFg, CircleShape)
                        )
                    }
                    Text(
                        text = "Tap highlighted text for fixes & reasons",
                        color = hintColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ConversationCorrectedText(
    text: String,
    corrections: List<LinguisticCorrection>,
    fontSizeSp: Int,
    onCorrectionClick: (LinguisticCorrection) -> Unit,
    textColor: Color = TextPrimary,
    hintColor: Color = TextMuted,
    showTapHint: Boolean = true,
    modifier: Modifier = Modifier
) = InteractiveCorrectedText(
    text = text,
    corrections = corrections,
    fontSizeSp = fontSizeSp,
    onCorrectionClick = onCorrectionClick,
    textColor = textColor,
    hintColor = hintColor,
    showTapHint = showTapHint,
    modifier = modifier
)
