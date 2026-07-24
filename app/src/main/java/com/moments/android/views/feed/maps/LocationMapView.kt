package com.moments.android.views.feed.maps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.feed.maps.mapssections.MapCanvasSection
import com.moments.android.views.feed.maps.mapssections.MapHeaderCloseStyle
import com.moments.android.views.feed.maps.mapssections.MapHeaderSection

/** Port de `LocationMapView` en `Maps.swift`. */
@Composable
fun LocationMapView(
    locationName: String,
    latitude: Double? = null,
    longitude: Double? = null,
    echoHistoryUserId: String? = null,
    echoHistoryOnly: Boolean = false,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    momentCount: Int? = null,
) {
    val effectiveName = locationName.ifBlank { stringResource(R.string.feed_location_default) }
    val subtitle = when {
        momentCount != null && momentCount > 0 ->
            stringResource(R.string.maps_location_moments, momentCount)
        else -> stringResource(R.string.maps_discover_subtitle)
    }
    val location = MapLocationData.from(
        name = effectiveName,
        latitude = latitude,
        longitude = longitude,
    )

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E)),
    ) {
        MapCanvasSection(
            location = location,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            MapHeaderSection(
                title = effectiveName,
                subtitle = subtitle,
                closeStyle = MapHeaderCloseStyle.Location,
                onClose = onDismiss,
            )
        }
    }
}
