package com.example.notepad.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The notepad's own palette, in a dark and a light flavour.
 *
 * [LocalNotepadColors] is provided by [com.example.notepad.NotepadPage] based on the app
 * theme, so the notes page follows the Theme setting instead of always being dark.
 */
data class NotepadColors(
    val background: Color,
    val card: Color,
    val cardBorder: Color,
    val squircle: Color,
    val squircleBorder: Color,
    /** Tile colour used to mark a media item that has been transcribed (inverted). */
    val invertedSquircle: Color,
    val invertedInk: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val accent: Color,
    val onAccent: Color,
    val recordAccent: Color,
    val deleteAccent: Color,
    val dot: Color,
    val dotGlow: Color,
    val checkboxBorder: Color,
    val deleteButtonBg: Color,
    val deleteButtonBorder: Color,
    val deleteButtonText: Color
)

val DarkNotepadColors = NotepadColors(
    background = Color(0xFF090A0C),
    card = Color(0xFF212121),
    cardBorder = Color(0xFF393939),
    squircle = Color(0xFF262626),
    squircleBorder = Color(0xFF484848),
    invertedSquircle = Color(0xFFEDEDED),
    invertedInk = Color(0xFF171717),
    textPrimary = Color(0xFFF2F2F2),
    textMuted = Color(0xFFAAAAAA),
    accent = Color(0xFFF2F2F2),
    onAccent = Color(0xFF171717),
    recordAccent = Color(0xFFFF4081),
    deleteAccent = Color(0xFFFF5252),
    dot = Color(0xFF2EE59D),
    dotGlow = Color(0x662EE59D),
    checkboxBorder = Color(0xFF777777),
    deleteButtonBg = Color(0xFF2B2B2B),
    deleteButtonBorder = Color(0xFF777777),
    deleteButtonText = Color(0xFFF2F2F2)
)

val LightNotepadColors = NotepadColors(
    background = Color(0xFFF4F4F4),
    card = Color(0xFFFFFFFF),
    cardBorder = Color(0xFFD6D6D6),
    squircle = Color(0xFFEDEDED),
    squircleBorder = Color(0xFFC6C6C6),
    invertedSquircle = Color(0xFF262626),
    invertedInk = Color(0xFFF2F2F2),
    textPrimary = Color(0xFF171717),
    textMuted = Color(0xFF5C5C5C),
    accent = Color(0xFF171717),
    onAccent = Color(0xFFFFFFFF),
    recordAccent = Color(0xFFD81B60),
    deleteAccent = Color(0xFFD32F2F),
    dot = Color(0xFF12B886),
    dotGlow = Color(0x6612B886),
    checkboxBorder = Color(0xFF8A8A8A),
    deleteButtonBg = Color(0xFFEDEDED),
    deleteButtonBorder = Color(0xFFBDBDBD),
    deleteButtonText = Color(0xFF171717)
)

val LocalNotepadColors = staticCompositionLocalOf { DarkNotepadColors }

val DeepObsidianBg: Color
    @Composable get() = LocalNotepadColors.current.background
val NotepadCardBg: Color
    @Composable get() = LocalNotepadColors.current.card
val NotepadCardBorder: Color
    @Composable get() = LocalNotepadColors.current.cardBorder

val SquircleBg: Color
    @Composable get() = LocalNotepadColors.current.squircle
val SquircleBorder: Color
    @Composable get() = LocalNotepadColors.current.squircleBorder

/** Inverted tile used for media that already has a transcription. */
val TranscribedSquircle: Color
    @Composable get() = LocalNotepadColors.current.invertedSquircle
val TranscribedSquircleInk: Color
    @Composable get() = LocalNotepadColors.current.invertedInk

val MintGreenDot: Color
    @Composable get() = LocalNotepadColors.current.dot
val MintGreenGlow: Color
    @Composable get() = LocalNotepadColors.current.dotGlow

/** The high-contrast ink colour used for icons, buttons and selected pills. */
val CyanAccent: Color
    @Composable get() = LocalNotepadColors.current.accent
val OnCyanAccent: Color
    @Composable get() = LocalNotepadColors.current.onAccent

val TextOffWhite: Color
    @Composable get() = LocalNotepadColors.current.textPrimary
val TextMuted: Color
    @Composable get() = LocalNotepadColors.current.textMuted

val CheckboxPurpleBorder: Color
    @Composable get() = LocalNotepadColors.current.checkboxBorder
val CheckboxDeleteTint: Color
    @Composable get() = LocalNotepadColors.current.deleteAccent

/** Red used for destructive actions. */
val DeleteAccent: Color
    @Composable get() = LocalNotepadColors.current.deleteAccent

val DeleteButtonBg: Color
    @Composable get() = LocalNotepadColors.current.deleteButtonBg
val DeleteButtonBorder: Color
    @Composable get() = LocalNotepadColors.current.deleteButtonBorder
val DeleteButtonText: Color
    @Composable get() = LocalNotepadColors.current.deleteButtonText

/** Accent used by recording controls; readable on both backgrounds. */
val RecordAccent: Color
    @Composable get() = LocalNotepadColors.current.recordAccent
