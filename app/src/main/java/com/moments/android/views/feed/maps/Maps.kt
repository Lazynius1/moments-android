package com.moments.android.views.feed.maps

import com.moments.android.BuildConfig

/** Port de `Maps.swift` — entry / flags del mapa del feed (Mapbox). */
object FeedMaps {
    private const val MAPBOX_PLACEHOLDER = "YOUR_MAPBOX_ACCESS_TOKEN"
    const val PLACEHOLDER_API_KEY = "REPLACE_WHEN_YOU_HAVE_GOOGLE_KEY"

    fun hasMapboxToken(): Boolean {
        val token = BuildConfig.MAPBOX_ACCESS_TOKEN.trim()
        return token.isNotEmpty() &&
            token != MAPBOX_PLACEHOLDER &&
            token.startsWith("pk.")
    }

    /** Google Places / pickers legacy (LocationPicker archive, etc.). Chat location preview = Mapbox. */
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
