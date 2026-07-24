package com.moments.android.views.creator.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color

/** Port de `StickerDetailPalette.swift`. */
data class StickerDetailPalette(val isDark: Boolean) {
    val primaryText: Color get() = if (isDark) Color.White else Color.Black.copy(.92f)
    val secondaryText: Color get() = if (isDark) Color.White.copy(.58f) else Color.Black.copy(.50f)
    val tertiaryText: Color get() = if (isDark) Color.White.copy(.40f) else Color.Black.copy(.34f)
    val searchIcon: Color get() = if (isDark) Color.White.copy(.54f) else Color.Black.copy(.36f)
    val searchIconActive: Color get() = if (isDark) Color.White.copy(.72f) else Color.Black.copy(.66f)
    val clearIcon: Color get() = if (isDark) Color.White.copy(.56f) else Color.Black.copy(.34f)
    val fieldFill: Color get() = if (isDark) Color.White.copy(.08f) else Color.Black.copy(.05f)
    val fieldStroke: Color get() = if (isDark) Color.White.copy(.16f) else Color.Black.copy(.08f)
    val buttonFill: Color get() = if (isDark) Color.White.copy(.08f) else Color.Black.copy(.05f)
    val divider: Color get() = if (isDark) Color.White.copy(.10f) else Color.Black.copy(.08f)
    val skeletonFill: Color get() = if (isDark) Color.White.copy(.10f) else Color.Black.copy(.08f)
}

@Composable
fun rememberStickerDetailPalette(): StickerDetailPalette {
    val dark = isSystemInDarkTheme()
    return remember(dark) { StickerDetailPalette(dark) }
}
