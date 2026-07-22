package com.moments.android.views.shared

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val MomentsColors = lightColorScheme(
    primary = Accent,
    onPrimary = Surface,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Ink,
    background = Surface,
    onBackground = Ink,
    surface = Surface,
    onSurface = Ink,
    surfaceVariant = SurfaceMuted,
    outlineVariant = Outline,
)

@Composable
fun MomentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MomentsColors, typography = interTypography()) {
        ProvideTextStyle(value = LocalTextStyle.current.copy(fontFamily = InterFamily)) {
            content()
        }
    }
}
