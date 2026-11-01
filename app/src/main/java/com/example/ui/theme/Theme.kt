package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import com.example.settings.AppThemePreference

@Composable
fun MyApplicationTheme(
    themePreference: AppThemePreference = AppThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        AppThemePreference.SYSTEM -> isSystemInDarkTheme()
        AppThemePreference.DARK -> true
        AppThemePreference.LIGHT -> false
    }
    val appColors = if (useDarkTheme) DarkAppColors else LightAppColors
    val colorScheme = if (useDarkTheme) {
        darkColorScheme(
            primary = appColors.primary,
            onPrimary = Color.Black,
            primaryContainer = Color(0xFF303134),
            onPrimaryContainer = Color(0xFFF0F0F0),
            secondary = appColors.primaryVariant,
            onSecondary = Color.Black,
            tertiary = appColors.textSecondary,
            error = appColors.primaryVariant,
            onError = Color.Black,
            errorContainer = appColors.card,
            onErrorContainer = appColors.textPrimary,
            inversePrimary = appColors.primaryVariant,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.card,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.cardBorder,
            outlineVariant = Color(0xFF3A3B3E),
            surfaceTint = appColors.primary,
            // Keep Material components (sliders, menus, sheets) on the grayscale palette
            // instead of falling back to the stock purple containers.
            secondaryContainer = appColors.card,
            onSecondaryContainer = appColors.textPrimary,
            tertiaryContainer = appColors.card,
            onTertiaryContainer = appColors.textPrimary,
            surfaceContainerLowest = appColors.background,
            surfaceContainerLow = appColors.card,
            surfaceContainer = appColors.card,
            surfaceContainerHigh = appColors.card,
            surfaceContainerHighest = appColors.card,
            inverseSurface = appColors.textPrimary,
            inverseOnSurface = appColors.background,
            scrim = Color.Black
        )
    } else {
        lightColorScheme(
            primary = appColors.primary,
            onPrimary = Color.White,
            primaryContainer = Color(0xFFE2E2E2),
            onPrimaryContainer = Color(0xFF202020),
            secondary = appColors.primaryVariant,
            onSecondary = Color.White,
            tertiary = appColors.textSecondary,
            error = appColors.primaryVariant,
            onError = Color.White,
            errorContainer = appColors.card,
            onErrorContainer = appColors.textPrimary,
            inversePrimary = appColors.primaryVariant,
            background = appColors.background,
            onBackground = appColors.textPrimary,
            surface = appColors.surface,
            onSurface = appColors.textPrimary,
            surfaceVariant = appColors.card,
            onSurfaceVariant = appColors.textSecondary,
            outline = appColors.cardBorder,
            surfaceTint = appColors.primary,
            secondaryContainer = appColors.card,
            onSecondaryContainer = appColors.textPrimary,
            tertiaryContainer = appColors.card,
            onTertiaryContainer = appColors.textPrimary,
            surfaceContainerLowest = appColors.background,
            surfaceContainerLow = appColors.background,
            surfaceContainer = appColors.surface,
            surfaceContainerHigh = appColors.surface,
            surfaceContainerHighest = appColors.card,
            inverseSurface = appColors.textPrimary,
            inverseOnSurface = appColors.background,
            scrim = Color.Black
        )
    }

    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalIsDarkTheme provides useDarkTheme
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(10.dp),
                small = RoundedCornerShape(14.dp),
                medium = RoundedCornerShape(18.dp),
                large = RoundedCornerShape(22.dp),
                extraLarge = RoundedCornerShape(28.dp)
            ),
            typography = Typography,
            content = content
        )
    }
}
