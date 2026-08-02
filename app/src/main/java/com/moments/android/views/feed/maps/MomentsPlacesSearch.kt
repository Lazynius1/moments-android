package com.moments.android.views.feed.maps

import android.content.Context
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.CircularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.android.libraries.places.api.net.SearchByTextRequest
import com.google.android.libraries.places.api.net.SearchNearbyRequest
import com.moments.android.BuildConfig
import kotlinx.coroutines.tasks.await

/**
 * Google Places (New) — ≡ MapKit `MKLocalSearch` / `MKLocalPointsOfInterestRequest` en iOS.
 * Token: `GOOGLE_MAPS_API_KEY` en local.properties → BuildConfig.
 */
object MomentsPlacesSearch {
    private const val NEARBY_RADIUS_METERS = 1_000.0
    private const val SEARCH_RADIUS_METERS = 10_000.0

    data class Hit(
        val id: String,
        val name: String,
        val address: String?,
        val latitude: Double,
        val longitude: Double,
        val distanceMeters: Double? = null,
        val category: String = "place",
    )

    fun isConfigured(): Boolean = FeedMaps.hasGoogleMapsKey()

    fun clientOrNull(context: Context): PlacesClient? {
        val key = BuildConfig.GOOGLE_MAPS_API_KEY
        if (!FeedMaps.hasGoogleMapsKey()) return null
        if (!Places.isInitialized()) {
            Places.initializeWithNewPlacesApiEnabled(context.applicationContext, key)
        }
        return Places.createClient(context.applicationContext)
    }

    private fun placeFields() = listOf(
        Place.Field.ID,
        Place.Field.DISPLAY_NAME,
        Place.Field.FORMATTED_ADDRESS,
        Place.Field.LOCATION,
        Place.Field.PRIMARY_TYPE,
    )

    /** Nearby ≡ iOS POI around user (Places SearchNearby). */
    suspend fun searchNearby(
        context: Context,
        latitude: Double,
        longitude: Double,
        maxResults: Int = 20,
    ): List<Hit> {
        val client = clientOrNull(context) ?: return emptyList()
        val request = SearchNearbyRequest.builder(
            CircularBounds.newInstance(LatLng(latitude, longitude), NEARBY_RADIUS_METERS),
            placeFields(),
        )
            .setMaxResultCount(maxResults.coerceIn(1, 20))
            .build()
        return client.searchNearby(request).await().places.mapNotNull { place ->
            toHit(place, latitude, longitude)
        }
    }

    /** Text search ≡ iOS `MKLocalSearch` naturalLanguageQuery. */
    suspend fun searchByText(
        context: Context,
        query: String,
        latitude: Double,
        longitude: Double,
        maxResults: Int = 25,
    ): List<Hit> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        val client = clientOrNull(context) ?: return emptyList()
        val request = SearchByTextRequest.builder(trimmed, placeFields())
            .setMaxResultCount(maxResults.coerceIn(1, 20))
            .setLocationBias(CircularBounds.newInstance(LatLng(latitude, longitude), SEARCH_RADIUS_METERS))
            .build()
        return client.searchByText(request).await().places.mapNotNull { place ->
            toHit(place, latitude, longitude)
        }
    }

    /**
     * Nearby por categorías localizadas (≡ iOS LocationPicker / sticker: varias
     * `MKLocalSearch` + dedupe). Usa SearchByText con bias de ubicación.
     */
    suspend fun searchNearbyByQueries(
        context: Context,
        latitude: Double,
        longitude: Double,
        queries: List<Pair<String, String>>,
        perQueryLimit: Int,
        totalLimit: Int,
    ): List<Hit> {
        if (clientOrNull(context) == null) return emptyList()
        val all = mutableListOf<Hit>()
        for ((query, category) in queries) {
            val batch = runCatching {
                searchByText(context, query, latitude, longitude, maxResults = perQueryLimit.coerceIn(1, 20))
            }.getOrDefault(emptyList())
            all += batch.map { it.copy(category = category) }.take(perQueryLimit)
        }
        val seen = linkedSetOf<String>()
        return all.filter { hit ->
            seen.add("%.5f,%.5f".format(hit.latitude, hit.longitude))
        }.sortedBy { it.distanceMeters ?: Double.MAX_VALUE }.take(totalLimit)
    }

    private fun toHit(place: Place, originLat: Double, originLng: Double): Hit? {
        val coordinate = place.location ?: return null
        val name = place.displayName?.takeIf { it.isNotBlank() } ?: return null
        val address = place.formattedAddress?.takeIf { it.isNotBlank() }?.let { formatted ->
            formatted.split(",").take(2).joinToString(",").trim().ifBlank { formatted }
        }
        val distance = FloatArray(1).also {
            Location.distanceBetween(originLat, originLng, coordinate.latitude, coordinate.longitude, it)
        }[0].toDouble()
        return Hit(
            id = place.id ?: "${coordinate.latitude}:${coordinate.longitude}:$name",
            name = name,
            address = address,
            latitude = coordinate.latitude,
            longitude = coordinate.longitude,
            distanceMeters = distance,
            category = place.primaryType ?: "place",
        )
    }
}
