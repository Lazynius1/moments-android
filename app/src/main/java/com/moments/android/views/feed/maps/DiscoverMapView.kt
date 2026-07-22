package com.moments.android.views.feed.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.feed.maps.mapsections.MapCanvasSection
import com.moments.android.views.feed.maps.mapsections.MapFilterChipsSection
import com.moments.android.views.feed.maps.mapsections.MapHeaderCloseStyle
import com.moments.android.views.feed.maps.mapsections.MapHeaderSection

/** Port de `DiscoverMapView.swift` — scaffold hasta tener Google Maps API key. */
@Composable
fun DiscoverMapView(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    zoneName: String? = null,
) {
    var contentFilter by remember { mutableStateOf(MapDiscoverContentFilter.All) }
    val title = zoneName?.takeIf { it.isNotBlank() }
        ?: stringResource(R.string.maps_discover_title)
    val subtitle = stringResource(R.string.maps_discover_subtitle)

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
    ) {
        MapCanvasSection(
            location = null,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MapHeaderSection(
                title = title,
                subtitle = subtitle,
                closeStyle = MapHeaderCloseStyle.Discover,
                onClose = onDismiss,
            )
            Spacer(modifier = Modifier.height(12.dp))
            MapFilterChipsSection(
                selected = contentFilter,
                onSelect = { contentFilter = it },
            )
        }
    }
}
