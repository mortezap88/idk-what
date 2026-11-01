package com.example.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.network.TutorMessageFormatter
import com.example.network.TutorMessageFormatter.FeedbackTone
import com.example.network.TutorMessageFormatter.InlineSpan
import com.example.network.TutorMessageFormatter.InlineStyle
import com.example.network.TutorMessageFormatter.MessageSegment
import com.example.network.TutorMessageFormatter.ReplyOrientation
import com.example.network.TutorMessageFormatter.RichBlock
import com.example.ui.theme.EvalBlueDot
import com.example.ui.theme.EvalBlueHighlightBg
import com.example.ui.theme.EvalBlueHighlightText
import com.example.ui.theme.EvalGreenDot
import com.example.ui.theme.EvalGreenHighlightBg
import com.example.ui.theme.EvalGreenHighlightText
import com.example.ui.theme.EvalRedDot
import com.example.ui.theme.EvalRedHighlightBg
import com.example.ui.theme.EvalRedHighlightText
import com.example.ui.theme.InlineCodeBg
import com.example.ui.theme.InlineCodeText
import com.example.ui.theme.InlineHighlightBg
import com.example.ui.theme.InlineHighlightText
import com.example.ui.theme.TextPrimary

/**
 * Renders AI tutor responses with:
 * - Clean, full-width vertical accent lines (Green, Red, Blue) with NO surrounding boxes or dots
 * - Language-aware line placement determined at the start of the reply (`[lang:fa]` -> line on the
 *   right & RTL text; `[lang:en]` -> line on the left & LTR text)
 * - Rich inline emphasis (`**bold**`, `==highlight==`, `*italic*`, `` `code` ``) with zero raw tags.
 */
@Composable
fun StructuredTutorMessage(
    rawText: String,
    fontSizeSp: Int,
    modifier: Modifier = Modifier
) {
    val parsed = remember(rawText) {
        TutorMessageFormatter.parseMessage(rawText)
    }
    if (parsed.segments.isEmpty()) return

    val replyDirection = when (parsed.orientation) {
        ReplyOrientation.PERSIAN_RTL -> LayoutDirection.Rtl
        ReplyOrientation.ENGLISH_LTR -> LayoutDirection.Ltr
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("structured_tutor_message"),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        parsed.segments.forEach { segment ->
            when (segment) {
                is MessageSegment.EvaluationBlock -> {
                    EvaluationAccentSection(
                        tone = segment.tone,
                        blocks = segment.blocks,
                        orientation = parsed.orientation,
                        replyDirection = replyDirection,
                        fontSizeSp = fontSizeSp
                    )
                }
                is MessageSegment.FreestyleBlock -> {
                    RichBlockView(
                        block = segment.block,
                        fontSizeSp = fontSizeSp,
                        defaultDirection = replyDirection,
                        highlightBg = InlineHighlightBg,
                        highlightText = InlineHighlightText,
                        bulletDotColor = TextPrimary.copy(alpha = 0.72f)
                    )
                }
            }
        }
    }
}

@Composable
private fun EvaluationAccentSection(
    tone: FeedbackTone,
    blocks: List<RichBlock>,
    orientation: ReplyOrientation,
    replyDirection: LayoutDirection,
    fontSizeSp: Int
) {
    val lineColor: Color
    val highlightBg: Color
    val highlightText: Color
    val tag: String

    when (tone) {
        FeedbackTone.GREEN -> {
            lineColor = EvalGreenDot
            highlightBg = EvalGreenHighlightBg
            highlightText = EvalGreenHighlightText
            tag = "eval_line_green"
        }
        FeedbackTone.RED -> {
            lineColor = EvalRedDot
            highlightBg = EvalRedHighlightBg
            highlightText = EvalRedHighlightText
            tag = "eval_line_red"
        }
        FeedbackTone.BLUE -> {
            lineColor = EvalBlueDot
            highlightBg = EvalBlueHighlightBg
            highlightText = EvalBlueHighlightText
            tag = "eval_line_blue"
        }
    }

    val isPersian = orientation == ReplyOrientation.PERSIAN_RTL
    val barShape = RoundedCornerShape(999.dp)

    // Pin Row layout to physical screen Ltr so right vs left bar placement is 100% deterministic:
    // - Persian-oriented (`[lang:fa]`) -> accent line on the RIGHT edge, text right-aligned (RTL)
    // - English-oriented (`[lang:en]`) -> accent line on the LEFT edge, text left-aligned (LTR)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(IntrinsicSize.Min)
                .testTag(tag),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (!isPersian) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .fillMaxHeight()
                        .clip(barShape)
                        .background(lineColor)
                        .testTag("${tag}_left_bar")
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 1.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                blocks.forEach { block ->
                    RichBlockView(
                        block = block,
                        fontSizeSp = fontSizeSp,
                        defaultDirection = replyDirection,
                        highlightBg = highlightBg,
                        highlightText = highlightText,
                        bulletDotColor = lineColor
                    )
                }
            }

            if (isPersian) {
                Box(
                    modifier = Modifier
                        .width(3.5.dp)
                        .fillMaxHeight()
                        .clip(barShape)
                        .background(lineColor)
                        .testTag("${tag}_right_bar")
                )
            }
        }
    }
}

@Composable
private fun RichBlockView(
    block: RichBlock,
    fontSizeSp: Int,
    defaultDirection: LayoutDirection,
    highlightBg: Color,
    highlightText: Color,
    bulletDotColor: Color
) {
    val textColor = TextPrimary
    val codeBg = InlineCodeBg
    val codeText = InlineCodeText

    when (block) {
        is RichBlock.Paragraph -> {
            val annotated = buildRichAnnotatedString(
                spans = block.spans,
                baseFontSize = fontSizeSp.sp,
                textColor = textColor,
                highlightBg = highlightBg,
                highlightText = highlightText,
                codeBg = codeBg,
                codeText = codeText
            )
            CompositionLocalProvider(LocalLayoutDirection provides defaultDirection) {
                Text(
                    text = annotated,
                    style = TextStyle(
                        color = textColor,
                        fontSize = fontSizeSp.sp,
                        lineHeight = (fontSizeSp * 1.48f).sp,
                        textAlign = TextAlign.Start
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        is RichBlock.Heading -> {
            val scale = when (block.level) {
                1 -> 1.16f
                2 -> 1.10f
                else -> 1.05f
            }
            val headingSize = (fontSizeSp * scale).sp
            val annotated = buildRichAnnotatedString(
                spans = block.spans,
                baseFontSize = headingSize,
                textColor = textColor,
                highlightBg = highlightBg,
                highlightText = highlightText,
                codeBg = codeBg,
                codeText = codeText,
                defaultWeight = FontWeight.Bold
            )
            CompositionLocalProvider(LocalLayoutDirection provides defaultDirection) {
                Text(
                    text = annotated,
                    style = TextStyle(
                        color = textColor,
                        fontSize = headingSize,
                        lineHeight = (fontSizeSp * scale * 1.38f).sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Start
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp)
                )
            }
        }

        is RichBlock.BulletItem -> {
            val annotated = buildRichAnnotatedString(
                spans = block.spans,
                baseFontSize = fontSizeSp.sp,
                textColor = textColor,
                highlightBg = highlightBg,
                highlightText = highlightText,
                codeBg = codeBg,
                codeText = codeText
            )
            CompositionLocalProvider(LocalLayoutDirection provides defaultDirection) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    if (block.marker == "•") {
                        Box(
                            modifier = Modifier
                                .padding(top = (fontSizeSp * 0.52f).coerceIn(7f, 13f).dp)
                                .size(5.dp)
                                .background(bulletDotColor, CircleShape)
                        )
                    } else {
                        Text(
                            text = block.marker,
                            color = bulletDotColor,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.48f).sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Text(
                        text = annotated,
                        style = TextStyle(
                            color = textColor,
                            fontSize = fontSizeSp.sp,
                            lineHeight = (fontSizeSp * 1.48f).sp,
                            textAlign = TextAlign.Start
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

private fun buildRichAnnotatedString(
    spans: List<InlineSpan>,
    baseFontSize: TextUnit,
    textColor: Color,
    highlightBg: Color,
    highlightText: Color,
    codeBg: Color,
    codeText: Color,
    defaultWeight: FontWeight = FontWeight.Normal
): AnnotatedString = buildAnnotatedString {
    val codeFontSize = (baseFontSize.value * 0.92f).sp
    for (span in spans) {
        when (span.style) {
            InlineStyle.PLAIN -> {
                if (defaultWeight != FontWeight.Normal) {
                    withStyle(SpanStyle(color = textColor, fontWeight = defaultWeight)) {
                        append(span.text)
                    }
                } else {
                    append(span.text)
                }
            }
            InlineStyle.BOLD -> {
                withStyle(
                    SpanStyle(
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                ) {
                    append(span.text)
                }
            }
            InlineStyle.HIGHLIGHT -> {
                withStyle(
                    SpanStyle(
                        color = highlightText,
                        background = highlightBg,
                        fontWeight = FontWeight.SemiBold
                    )
                ) {
                    append("\u00A0${span.text}\u00A0")
                }
            }
            InlineStyle.ITALIC -> {
                withStyle(
                    SpanStyle(
                        color = textColor.copy(alpha = 0.92f),
                        fontStyle = FontStyle.Italic
                    )
                ) {
                    append(span.text)
                }
            }
            InlineStyle.CODE -> {
                withStyle(
                    SpanStyle(
                        color = codeText,
                        background = codeBg,
                        fontFamily = FontFamily.Monospace,
                        fontSize = codeFontSize,
                        fontWeight = FontWeight.Medium
                    )
                ) {
                    append("\u00A0${span.text}\u00A0")
                }
            }
        }
    }
}
