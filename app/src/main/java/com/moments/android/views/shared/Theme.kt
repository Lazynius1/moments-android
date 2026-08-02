package com.moments.android.views.shared

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Light — canvas Moments paper (`FAF9F6`) + accent marca. */
private val MomentsLightColors = lightColorScheme(
    primary = Accent,
    onPrimary = Surface,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Ink,
    background = Surface,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceMuted,
    onSurfaceVariant = Ink.copy(alpha = 0.7f),
    outlineVariant = Outline,
)

/**
 * Dark — canvas AdaptiveColors ink (`0B1215`), no inventar grises.
 * ≡ `AdaptiveColors.surfaceBackground` dark.
 */
private val MomentsDarkColors = darkColorScheme(
    primary = AccentSoft,
    onPrimary = Ink,
    primaryContainer = Accent.copy(alpha = 0.35f),
    onPrimaryContainer = Surface,
    background = Ink,
    onBackground = Surface,
    surface = Ink,
    onSurface = Surface,
    surfaceVariant = ControlDark,
    onSurfaceVariant = Surface.copy(alpha = 0.7f),
    outlineVariant = Color.White.copy(alpha = 0.12f),
)

@Composable
fun MomentsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) MomentsDarkColors else MomentsLightColors
    // MotionScheme aún internal en el BOM; specs M3 viven en [MomentsMotion].
    MaterialTheme(
        colorScheme = colors,
        typography = interTypography(),
    ) {
        ProvideTextStyle(value = LocalTextStyle.current.copy(fontFamily = InterFamily)) {
            content()
        }
    }
}
