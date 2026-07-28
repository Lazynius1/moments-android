package com.moments.android.views.feed.maps

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.models.StickerData
import com.moments.android.utilities.MomentsFormat
import java.util.Calendar
import java.util.Date
import java.util.UUID
import java.util.concurrent.Executors

/** Port de `MapDiscoverSupport.swift`. */
enum class MapServiceError {
    Unauthenticated,
    InvalidConfiguration,
    Network,
    InvalidResponse,
    Decoding,
}

enum class MapDiscoverContentFilter(val titleKeyRes: Int) {
    All(R.string.maps_filter_all),
    Friends(R.string.maps_filter_friends),
    Places(R.string.maps_filter_places),
}

/** ≡ iOS `BackendMapStory`. */
data class BackendMapStory(
    val id: String,
    val authorId: String,
    val username: String,
    val profileImagePath: String? = null,
    val timestamp: Double? = null,
    val expirationDate: Double? = null,
    val audience: String? = null,
    val locationName: String? = null,
    val locationCoordinate: Moment.LocationCoordinate? = null,
    val locationFuzzed: Boolean? = null,
    val previewUrl: String? = null,
    val contentType: String? = null,
) {
    fun toStoryPreview(): MapStoryPreview {
        val lat = locationCoordinate?.latitude
        val lon = locationCoordinate?.longitude
        return MapStoryPreview(
            id = id,
            authorId = authorId,
            username = username,
            profileImagePath = profileImagePath,
            // iOS: Date(timeIntervalSince1970: $0 / 1000) — backend envía ms
            timestamp = timestamp?.let { Date(it.toLong()) } ?: Date(),
            locationName = locationName,
            latitude = lat,
            longitude = lon,
            previewUrl = previewUrl,
            locationFuzzed = locationFuzzed == true,
        )
    }
}

data class MapStoryPreview(
    val id: String,
    val authorId: String,
    val username: String,
    val profileImagePath: String? = null,
    val timestamp: Date = Date(),
    val locationName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val previewUrl: String? = null,
    val locationFuzzed: Boolean = false,
) {
    val coordinate: Pair<Double, Double>?
        get() {
            val lat = latitude ?: return null
            val lon = longitude ?: return null
            return lat to lon
        }

    /** ≡ iOS Hashable/Equatable: solo `id` + `authorId`. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MapStoryPreview) return false
        return id == other.id && authorId == other.authorId
    }

    override fun hashCode(): Int = 31 * id.hashCode() + authorId.hashCode()
}

data class MapFriendActivityPin(
    val id: String,
    val authorId: String,
    val username: String,
    val profileImagePath: String? = null,
    val latitude: Double,
    val longitude: Double,
    val latestTimestamp: Date = Date(),
    val momentCount: Int = 0,
    val storyCount: Int = 0,
) {
    /** ≡ iOS Hashable/Equatable: solo `id`. */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MapFriendActivityPin) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

/**
 * ≡ iOS `MapVisibilityPolicy` (MapDiscoverSupport.swift).
 * Si hay ubicación en el post, aparece en el mapa para quien pueda ver el contenido.
 * `onlyMe` nunca sale en mapa ajeno; el resto lo decide `canViewerSeeMoment` / `canViewerSeeStory`.
 */
object MapVisibilityPolicy {
    fun resolvedVisibility(hasLocation: Boolean, audience: String?): String {
        if (!hasLocation) return "hidden"
        return if (audience == "onlyMe") "hidden" else "public"
    }

    data class StoryMapLocation(
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )

    fun storyMapLocation(stickers: List<StickerData>?): StoryMapLocation? {
        if (stickers == null) return null
        for (sticker in stickers) {
            if (sticker.type != "location") continue
            val latitude = sticker.latitude ?: continue
            val longitude = sticker.longitude ?: continue
            val name = sticker.location?.trim().orEmpty()
            return StoryMapLocation(name, latitude, longitude)
        }
        return null
    }
}

data class MapDiscoverPayload(
    val moments: List<Moment>,
    val stories: List<MapStoryPreview>,
    val source: String,
    val momentsError: MapServiceError?,
    val storiesError: MapServiceError?,
) {
    val hasContent: Boolean get() = moments.isNotEmpty() || stories.isNotEmpty()
    val isCompleteFailure: Boolean get() = !hasContent && momentsError != null && storiesError != null
    val hasPartialFailure: Boolean get() = hasContent && (momentsError != null || storiesError != null)
}

/** ≡ iOS `MapMomentDetailRoute`. */
data class MapMomentDetailRoute(
    val moments: List<Moment>,
    val initialIndex: Int,
    val locationName: String,
    val id: String = UUID.randomUUID().toString(),
)

object MapSheetPresentationDelay {
    const val DISMISS_BEFORE_NEXT_PRESENTATION_MS = 450L
    const val REOPEN_BOTTOM_SHEET_AFTER_DETAIL_MS = 350L
}

enum class MapDiscoverTimeFilter(val titleKeyRes: Int) {
    Today(R.string.maps_time_filter_today),
    Week(R.string.maps_time_filter_week),
    All(R.string.maps_time_filter_all),
    ;

    /** ≡ iOS `cutoffDate`. */
    val cutoffDate: Date?
        get() {
            val cal = Calendar.getInstance()
            return when (this) {
                Today -> {
                    cal.add(Calendar.HOUR_OF_DAY, -24)
                    cal.time
                }
                Week -> {
                    cal.add(Calendar.DAY_OF_YEAR, -7)
                    cal.time
                }
                All -> null
            }
        }
}

/** ≡ iOS `MapDistanceFormatter`. */
object MapDistanceFormatter {
    fun string(
        context: Context,
        fromLat: Double?,
        fromLon: Double?,
        toLat: Double,
        toLon: Double,
    ): String? {
        // iOS: guard let origin, CLLocationCoordinate2DIsValid(origin)
        if (fromLat == null || fromLon == null) return null
        if (!isValidCoordinate(fromLat, fromLon)) return null
        val meters = haversineMeters(fromLat, fromLon, toLat, toLon)
        return when {
            meters < 1000 -> context.getString(R.string.maps_distance_meters, meters.toInt())
            meters / 1000 < 10 ->
                context.getString(R.string.maps_distance_kilometers_decimal, meters / 1000.0)
            else -> context.getString(R.string.maps_distance_kilometers, (meters / 1000).toInt())
        }
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2) * kotlin.math.sin(dLat / 2) +
            kotlin.math.cos(Math.toRadians(lat1)) * kotlin.math.cos(Math.toRadians(lat2)) *
            kotlin.math.sin(dLon / 2) * kotlin.math.sin(dLon / 2)
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }
}

/** ≡ iOS `MapRelativeTimeFormatter`. */
object MapRelativeTimeFormatter {
    fun string(from: Date): String = MomentsFormat.relativeTime(from = from)
}

/**
 * ≡ iOS `MapZoneContextService` — reverse geocode del centro con cache por celda (~1 km).
 */
object MapZoneContextService {
    private val cache = mutableMapOf<String, String>()
    @Volatile private var lastRequestedKey: String? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private val geocodeExecutor = Executors.newSingleThreadExecutor()

    fun zoneName(context: Context, latitude: Double, longitude: Double, completion: (String?) -> Unit) {
        val key = cacheKey(latitude, longitude)
        cache[key]?.let {
            completion(it)
            return
        }
        lastRequestedKey = key
        // iOS: geocoder.cancelGeocode() — Geocoder Android no cancela; lastRequestedKey descarta stale
        val appContext = context.applicationContext
        geocodeExecutor.execute {
            try {
                @Suppress("DEPRECATION")
                val geocoder = android.location.Geocoder(appContext)
                @Suppress("DEPRECATION")
                val results = geocoder.getFromLocation(latitude, longitude, 1)
                if (lastRequestedKey != key) return@execute
                val name = results?.firstOrNull()?.let { addr ->
                    listOfNotNull(addr.subLocality, addr.locality, addr.adminArea)
                        .firstOrNull { it.isNotBlank() }
                }
                // iOS: DispatchQueue.main.async
                mainHandler.post {
                    if (lastRequestedKey != key) return@post
                    if (!name.isNullOrBlank()) {
                        cache[key] = name
                        completion(name)
                    } else {
                        completion(null)
                    }
                }
            } catch (_: Exception) {
                mainHandler.post {
                    if (lastRequestedKey == key) completion(null)
                }
            }
        }
    }

    private fun cacheKey(latitude: Double, longitude: Double): String =
        String.format(java.util.Locale.US, "%.2f|%.2f", latitude, longitude)
}
