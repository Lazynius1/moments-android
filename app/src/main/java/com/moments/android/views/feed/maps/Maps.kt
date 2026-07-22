package com.moments.android.views.feed.maps

import com.moments.android.BuildConfig

/** Port de `Maps.swift` — entry point del mapa del feed. */
object FeedMaps {
    const val PLACEHOLDER_API_KEY = "REPLACE_WHEN_YOU_HAVE_GOOGLE_KEY"

    fun hasGoogleMapsKey(): Boolean =
        BuildConfig.GOOGLE_MAPS_API_KEY.isNotBlank() &&
            BuildConfig.GOOGLE_MAPS_API_KEY != PLACEHOLDER_API_KEY
}

data class MapLocationCoordinate(
    val latitude: Double,
    val longitude: Double,
)

data class MapLocationData(
    val name: String,
    val latitude: Double? = null,
    val longitude: Double? = null,
) {
    val coordinate: MapLocationCoordinate?
        get() = if (latitude != null && longitude != null) {
            MapLocationCoordinate(latitude, longitude)
        } else {
            null
        }

    companion object {
        fun from(name: String, latitude: Double?, longitude: Double?): MapLocationData =
            MapLocationData(name = name, latitude = latitude, longitude = longitude)
    }
}
