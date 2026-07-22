package com.moments.android.views.feed.maps

import java.util.UUID

/** Port de `MapAnnotationModels.swift`. */
data class MapsLocationAnnotation(
    val id: UUID = UUID.randomUUID(),
    val latitude: Double,
    val longitude: Double,
    val title: String,
)

data class MapPlaceAnnotation(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val momentCount: Int = 0,
    val hasStories: Boolean = false,
)

data class MapCluster(
    val centerLat: Double,
    val centerLng: Double,
    val places: List<MapPlaceAnnotation>,
)

data class CombinedMapAnnotation(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val locationTitle: String?,
    val momentIds: List<String> = emptyList(),
) {
    val count: Int get() = momentIds.size
}
