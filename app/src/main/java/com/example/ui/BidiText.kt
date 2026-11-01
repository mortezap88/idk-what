package com.example.ui

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit

/**
 * True when the first "strong" character of the string belongs to a right-to-left script
 * (Persian, Arabic, Hebrew). Digits, punctuation and spaces are skipped, so
 * "۲. اسمز چیست؟" is recognised as Persian while "ATP چیست" starts Latin and stays LTR.
 */
fun String.isRtlText(): Boolean {
    for (character in this) {
        when {
            character in '\u0590'..'\u08FF' -> return true   // Hebrew, Arabic, Persian
            character in '\uFB1D'..'\uFDFF' -> return true   // Arabic presentation forms A
            character in '\uFE70'..'\uFEFF' -> return true   // Arabic presentation forms B
            character in 'A'..'Z' || character in 'a'..'Z' -> return false
            else -> Unit
        }
    }
    return false
}

/**
 * A [Text] that flips its own layout direction when the content is Persian/Arabic, so the
 * sentence is laid out right-to-left and aligned to the right inside an English UI.
 *
 * Only the text is flipped; the surrounding layout keeps the app's normal direction.
 */
@Composable
fun BidiText(
    text: String,
    color: Color,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip
) {
    val direction = if (text.isRtlText()) LayoutDirection.Rtl else LayoutDirection.Ltr
    CompositionLocalProvider(LocalLayoutDirection provides direction) {
        Text(
            text = text,
            color = color,
            fontSize = fontSize,
            lineHeight = lineHeight,
            fontWeight = fontWeight,
            textAlign = TextAlign.Start,
            maxLines = maxLines,
            overflow = overflow,
            modifier = modifier
        )
    }
}
