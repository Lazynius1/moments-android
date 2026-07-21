package com.moments.android.ui.feed

import androidx.compose.ui.graphics.Color

// Paleta del feed (equivalente a AdaptiveColors + hex de iOS). Tema claro por ahora.
internal val FeedCanvas = Color(0xFFFAF9F6)
internal val FeedInk = Color(0xFF0B1215)
internal val FeedTeal = Color(0xFF00A896)
internal val FeedPurple = Color(0xFFAF52DE)

// Anillo de historia no vista: azul → morado → rosa (.blue/.purple/.pink de iOS).
internal val StoryRingColors = listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55))
internal val StoryRingViewed = listOf(Color(0xFFC2C2C2), Color(0xFFF0F0F0))
