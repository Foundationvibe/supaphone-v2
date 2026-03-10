package com.supaphone.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// ── Extended color scheme to carry our custom palette alongside Material 3 ──
data class SupaPhoneColors(
    val bgBase: Color,
    val bgSurface: Color,
    val bgSurfaceHover: Color,
    val bgElevated: Color,
    val textMain: Color,
    val textMuted: Color,
    val primary: Color,
    val primaryHover: Color,
    val success: Color,
    val danger: Color,
    val warning: Color,
)

val LocalSupaPhoneColors = staticCompositionLocalOf {
    SupaPhoneColors(
        bgBase = BgBaseDark,
        bgSurface = BgSurfaceDark,
        bgSurfaceHover = BgSurfaceHoverDark,
        bgElevated = BgElevatedDark,
        textMain = TextMainDark,
        textMuted = TextMutedDark,
        primary = Primary,
        primaryHover = PrimaryHover,
        success = Success,
        danger = Danger,
        warning = Warning,
    )
}

private val DarkColors = SupaPhoneColors(
    bgBase = BgBaseDark,
    bgSurface = BgSurfaceDark,
    bgSurfaceHover = BgSurfaceHoverDark,
    bgElevated = BgElevatedDark,
    textMain = TextMainDark,
    textMuted = TextMutedDark,
    primary = Primary,
    primaryHover = PrimaryHover,
    success = Success,
    danger = Danger,
    warning = Warning,
)

private val LightColors = SupaPhoneColors(
    bgBase = BgBaseLight,
    bgSurface = BgSurfaceLight,
    bgSurfaceHover = BgSurfaceHoverLight,
    bgElevated = BgElevatedLight,
    textMain = TextMainLight,
    textMuted = TextMutedLight,
    primary = Primary,
    primaryHover = PrimaryHover,
    success = Success,
    danger = Danger,
    warning = Warning,
)

// ── Material 3 color schemes (for M3 components) ──
private val DarkColorScheme = darkColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    surface = BgSurfaceDark,
    onSurface = TextMainDark,
    background = BgBaseDark,
    onBackground = TextMainDark,
    error = Danger,
)

private val LightColorScheme = lightColorScheme(
    primary = Primary,
    onPrimary = Color.White,
    surface = BgSurfaceLight,
    onSurface = TextMainLight,
    background = BgBaseLight,
    onBackground = TextMainLight,
    error = Danger,
)

@Composable
fun SupaPhoneAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val supaColors = if (darkTheme) DarkColors else LightColors

    CompositionLocalProvider(LocalSupaPhoneColors provides supaColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = SupaPhoneTypography,
            content = content
        )
    }
}

// Convenience accessor
object SupaPhoneTheme {
    val colors: SupaPhoneColors
        @Composable
        get() = LocalSupaPhoneColors.current
}
