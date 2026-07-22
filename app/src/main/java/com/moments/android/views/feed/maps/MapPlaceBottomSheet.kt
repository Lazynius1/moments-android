package com.moments.android.views.feed.maps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R

/** Port de `MapPlaceBottomSheet.swift` — placeholder hasta conectar datos reales. */
@Composable
fun MapPlaceBottomSheet(
    cluster: MapPlaceCluster,
    momentAvailability: Map<String, Boolean> = emptyMap(),
    isLoading: Boolean = false,
    onMomentTap: (String) -> Unit = {},
    onPlaceStoriesTap: (MapPlaceCluster) -> Unit = {},
    weather: WeatherData? = null,
    userLatitude: Double? = null,
    userLongitude: Double? = null,
    placeIndex: List<MapPlaceCluster> = emptyList(),
    onPlaceTap: ((MapPlaceCluster) -> Unit)? = null,
    onDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth().padding(16.dp)) {
        Text(cluster.displayName)
        Text(
            stringResource(R.string.maps_bottom_sheet_moments, cluster.momentCount),
        )
    }
}
