package com.moments.android.views.feed.maps.mapsections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.feed.maps.FeedMaps
import com.moments.android.views.feed.maps.MapLocationData

/** Port de `MapCanvasSection.swift` — canvas del mapa (placeholder sin SDK). */
@Composable
fun MapCanvasSection(
    location: MapLocationData?,
    modifier: Modifier = Modifier,
    showPlaceholderWhenNoKey: Boolean = true,
) {
    val hasKey = FeedMaps.hasGoogleMapsKey()
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF2D3436)),
        contentAlignment = Alignment.Center,
    ) {
        if (hasKey) {
            Text(
                location?.name ?: stringResource(R.string.feed_location_default),
                color = Color.White,
            )
        } else if (showPlaceholderWhenNoKey) {
            Text(
                stringResource(R.string.feed_map_placeholder),
                color = Color.White.copy(alpha = 0.85f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(32.dp),
            )
        }
    }
}
