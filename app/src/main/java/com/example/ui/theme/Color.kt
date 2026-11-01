package com.example.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppColors(
    val background: Color,
    val surface: Color,
    val card: Color,
    val cardBorder: Color,
    val input: Color,
    val primary: Color,
    val primaryVariant: Color,
    val primaryLight: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textMuted: Color
)

val DarkAppColors = AppColors(
    background = Color(0xFF090A0C),
    surface = Color(0xFF141517),
    card = Color(0xFF1B1C1F),
    cardBorder = Color(0xFF2B2C30),
    input = Color(0xFF101113),
    primary = Color(0xFFD0D0D0),
    primaryVariant = Color(0xFFA8A8A8),
    primaryLight = Color(0xFFF0F0F0),
    textPrimary = Color(0xFFF2F2F2),
    textSecondary = Color(0xFFAAAAAA),
    textMuted = Color(0xFF747474)
)

val LightAppColors = AppColors(
    background = Color(0xFFF4F4F4),
    surface = Color(0xFFFFFFFF),
    card = Color(0xFFEAEAEA),
    cardBorder = Color(0xFFD2D2D2),
    input = Color(0xFFFFFFFF),
    primary = Color(0xFF343434),
    primaryVariant = Color(0xFF555555),
    primaryLight = Color(0xFF202020),
    textPrimary = Color(0xFF171717),
    textSecondary = Color(0xFF5C5C5C),
    textMuted = Color(0xFF858585)
)

val LocalAppColors = staticCompositionLocalOf { DarkAppColors }

/** Whether the current theme resolved to dark; used by embedded screens with their own palette. */
val LocalIsDarkTheme = staticCompositionLocalOf { true }

val BgDark: Color
    @Composable get() = LocalAppColors.current.background
val SurfaceDark: Color
    @Composable get() = LocalAppColors.current.surface
val SurfaceCard: Color
    @Composable get() = LocalAppColors.current.card
val SurfaceCardBorder: Color
    @Composable get() = LocalAppColors.current.cardBorder
val SurfaceInput: Color
    @Composable get() = LocalAppColors.current.input

val PrimaryPurple: Color
    @Composable get() = LocalAppColors.current.primary
val PrimaryPurpleVariant: Color
    @Composable get() = LocalAppColors.current.primaryVariant
val PrimaryPurpleLight: Color
    @Composable get() = LocalAppColors.current.primaryLight

val AccentPink = Color(0xFFB8B8B8)
val AccentOrange = Color(0xFFAFAFAF)
val AccentGold = Color(0xFFC4C4C4)
val AccentTeal = Color(0xFFB0B0B0)
val AccentCyan = Color(0xFFBDBDBD)
val AccentBlue = Color(0xFFA6A6A6)
val AccentIndigo = Color(0xFFB5B5B5)
val AccentViolet = Color(0xFFC0C0C0)

val BadgeGreenBorder = Color(0xFF676767)
val BadgeGreenBg = Color(0xFF252629)
val BadgeGreenText = Color(0xFFD0D0D0)

val TextPrimary: Color
    @Composable get() = LocalAppColors.current.textPrimary
val TextSecondary: Color
    @Composable get() = LocalAppColors.current.textSecondary
val TextMuted: Color
    @Composable get() = LocalAppColors.current.textMuted

val CorrectionRedText: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFF6B6B) else Color(0xFFC62828)

val CorrectionRedBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x2EFF5252) else Color(0x22D32F2F)

val ImprovementYellowText: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFFD54F) else Color(0xFF9E6700)

val ImprovementYellowBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x2EFFC107) else Color(0x26FFB300)

val EvalGreenDot: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF34D399) else Color(0xFF059669)
val EvalGreenBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x1C10B981) else Color(0x14059669)
val EvalGreenBorder: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x4710B981) else Color(0x38059669)
val EvalGreenHighlightBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x3610B981) else Color(0x24059669)
val EvalGreenHighlightText: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF6EE7B7) else Color(0xFF047857)

val EvalRedDot: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFF87171) else Color(0xFFDC2626)
val EvalRedBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x1CEF4444) else Color(0x14DC2626)
val EvalRedBorder: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x47EF4444) else Color(0x38DC2626)
val EvalRedHighlightBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x36EF4444) else Color(0x24DC2626)
val EvalRedHighlightText: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFCA5A5) else Color(0xFFB91C1C)

val EvalBlueDot: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF60A5FA) else Color(0xFF2563EB)
val EvalBlueBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x1C3B82F6) else Color(0x142563EB)
val EvalBlueBorder: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x473B82F6) else Color(0x382563EB)
val EvalBlueHighlightBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x363B82F6) else Color(0x242563EB)
val EvalBlueHighlightText: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF93C5FD) else Color(0xFF1D4ED8)

val InlineHighlightBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0x33FBBF24) else Color(0x29D97706)
val InlineHighlightText: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFFDE68A) else Color(0xFF92400E)

val InlineCodeBg: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFF25272C) else Color(0xFFE2E4E8)
val InlineCodeText: Color
    @Composable get() = if (LocalIsDarkTheme.current) Color(0xFFE5E7EB) else Color(0xFF1F2937)


