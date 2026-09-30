package com.moments.android.views.feed.maps

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.mapbox.geojson.Point
import com.moments.android.MomentsApplication
import com.moments.android.extensions.optStringOrNull
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.services.network.CloudFunctionsClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import org.json.JSONObject
import java.util.Date
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Port de `MapLocationServices.swift`.
 * Contiene los mismos tipos que iOS en ese archivo:
 * - LocationUtilities
 * - MapRegionStore
 * - LocationSearchService
 * - MapLocationDisplayFormatter
 */
object MapLocationServices {
    // Entry legacy; LocationUtilities es la API iOS.
    fun isLocationEnabled(context: Context): Boolean =
        LocationUtilities.hasForegroundPermission(context)

    /** ≡ iOS `requestCurrentLocation()` — (latitude, longitude) o null si no hay permiso/fix. */
    suspend fun requestCurrentLocation(): Pair<Double, Double>? {
        val context = MomentsApplication.instance ?: return null
        if (!LocationUtilities.hasForegroundPermission(context)) return null
        return suspendCancellableCoroutine { cont ->
            LocationUtilities.getCurrentLocation(context) { point ->
                if (cont.isActive) {
                    cont.resume(point?.let { it.latitude() to it.longitude() })
                }
            }
        }
    }
}

/** ≡ iOS `LocationUtilities` (MapLocationServices.swift). */
object LocationUtilities {
    fun hasForegroundPermission(context: Context): Boolean {
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(context: Context, completion: (Point?) -> Unit) {
        if (!hasForegroundPermission(context)) {
            completion(null)
            return
        }
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        if (lm == null) {
            completion(null)
            return
        }

        fun usable(location: Location): Boolean = location.hasAccuracy() && location.accuracy >= 0 && location.accuracy <= 5_000 &&
            kotlin.math.abs(System.currentTimeMillis() - location.time) <= 60_000
        val last = listOfNotNull(
            runCatching { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull(),
            runCatching { lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) }.getOrNull(),
        ).filter(::usable).maxByOrNull { it.time }

        if (last != null) {
            completion(Point.fromLngLat(last.longitude, last.latitude))
            return
        }

        val main = Handler(Looper.getMainLooper())
        var finished = false
        fun finish(location: Location?) {
            if (finished) return
            finished = true
            main.removeCallbacksAndMessages(null)
            completion(location?.let { Point.fromLngLat(it.longitude, it.latitude) })
        }

        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!usable(location)) return
                runCatching { lm.removeUpdates(this) }
                finish(location)
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit

            override fun onProviderEnabled(provider: String) = Unit
            override fun onProviderDisabled(provider: String) = Unit
        }

        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        val requested = providers.map { provider ->
            runCatching { lm.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper()) }.isSuccess
        }.any { it }
        if (!requested) {
            finish(null)
            return
        }

        main.postDelayed({
            runCatching { lm.removeUpdates(listener) }
            finish(last)
        }, 8_000)
    }
}

/** ≡ iOS `MapRegionStore` (MapLocationServices.swift). */
object MapRegionStore {
    private const val PREFS = "discoverMap"
    private const val LAST_REGION_KEY = "discoverMap.lastRegion"
    private const val DEFAULT_LAT_DELTA = 12.0
    private const val DEFAULT_LON_DELTA = 12.0

    /** Centro España ≈ iOS `spainCenter`. */
    val spainCenter: Point = Point.fromLngLat(-4.0, 40.0)

    data class Region(
        val centerLat: Double,
        val centerLon: Double,
        val latitudeDelta: Double,
        val longitudeDelta: Double,
    ) {
        val center: Point get() = Point.fromLngLat(centerLon, centerLat)
        val zoom: Double get() = zoomFromLongitudeDelta(longitudeDelta)
    }

    fun initialRegion(context: Context): Region =
        loadSavedRegion(context) ?: defaultRegion()

    fun defaultRegion(): Region = Region(
        centerLat = spainCenter.latitude(),
        centerLon = spainCenter.longitude(),
        latitudeDelta = DEFAULT_LAT_DELTA,
        longitudeDelta = DEFAULT_LON_DELTA,
    )

    fun save(context: Context, region: Region) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(
                LAST_REGION_KEY,
                "${region.centerLat},${region.centerLon},${region.latitudeDelta},${region.longitudeDelta}",
            )
            .apply()
    }

    fun saveCamera(context: Context, center: Point, zoom: Double) {
        val lonDelta = longitudeDeltaFromZoom(zoom)
        save(
            context,
            Region(
                centerLat = center.latitude(),
                centerLon = center.longitude(),
                latitudeDelta = lonDelta,
                longitudeDelta = lonDelta,
            ),
        )
    }

    fun resolveFallbackRegion(context: Context, completion: (Region) -> Unit) {
        completion(loadSavedRegion(context) ?: defaultRegion())
    }

    private fun loadSavedRegion(context: Context): Region? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(LAST_REGION_KEY, null)
            ?: return null
        val parts = raw.split(",")
        if (parts.size != 4) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        val latDelta = parts[2].toDoubleOrNull() ?: return null
        val lonDelta = parts[3].toDoubleOrNull() ?: return null
        if (!lat.isFinite() || !lon.isFinite() || lat !in -90.0..90.0 || lon !in -180.0..180.0 ||
            !latDelta.isFinite() || !lonDelta.isFinite() || latDelta <= 0 || lonDelta <= 0 ||
            (kotlin.math.abs(lat - 40) < 0.0001 && kotlin.math.abs(lon + 4) < 0.0001)) return null
        return Region(lat, lon, latDelta, lonDelta)
    }

    fun zoomFromLongitudeDelta(longitudeDelta: Double): Double {
        val delta = max(longitudeDelta, 1e-6)
        return (ln(360.0 / delta) / ln(2.0)).coerceIn(1.0, 20.0)
    }

    fun longitudeDeltaFromZoom(zoom: Double): Double {
        val z = min(max(zoom, 1.0), 20.0)
        return 360.0 / Math.pow(2.0, z)
    }
}

/** ≡ iOS `LocationSearchService` (MapLocationServices.swift). */
object LocationSearchService {
    private sealed class MapQueryMode {
        data class Location(val locationName: String) : MapQueryMode()
        data class Region(val region: MapRegionStore.Region) : MapQueryMode()
    }

    private class MapServiceException(val error: MapServiceError) : Exception(error.name)

    fun searchDiscoverContentInRegion(
        region: MapRegionStore.Region,
        completion: (MapDiscoverPayload) -> Unit,
    ) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            completion(
                MapDiscoverPayload(
                    moments = emptyList(),
                    stories = emptyList(),
                    source = "unauthenticated",
                    momentsError = MapServiceError.Unauthenticated,
                    storiesError = MapServiceError.Unauthenticated,
                ),
            )
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            val momentsDeferred = async(Dispatchers.IO) {
                runCatching { fetchMapMoments(MapQueryMode.Region(region), limit = 120) }
            }
            val storiesDeferred = async(Dispatchers.IO) {
                runCatching { fetchMapStories(MapQueryMode.Region(region), limit = 120) }
            }
            val momentsResult = momentsDeferred.await()
            val storiesResult = storiesDeferred.await()
            completion(
                MapDiscoverPayload(
                    moments = momentsResult.getOrDefault(emptyList()),
                    stories = storiesResult.getOrDefault(emptyList()),
                    source = "backend",
                    momentsError = momentsResult.exceptionOrNull()?.toMapServiceError(),
                    storiesError = storiesResult.exceptionOrNull()?.toMapServiceError(),
                ),
            )
        }
    }

    /** A frozen viewport/location and independent encrypted cursors for both content types. */
    fun searchContentPage(
        region: MapRegionStore.Region? = null,
        locationName: String? = null,
        followingOnly: Boolean = false,
        momentsCursor: String? = null,
        storiesCursor: String? = null,
        loadMoments: Boolean = true,
        loadStories: Boolean = true,
        completion: (MapDiscoverPayload) -> Unit,
    ) {
        val mode = if (locationName != null) MapQueryMode.Location(locationName)
            else MapQueryMode.Region(requireNotNull(region))
        CoroutineScope(Dispatchers.Main).launch {
            val posts = async(Dispatchers.IO) {
                runCatching {
                    if (loadMoments) postMapEndpoint("getMapMomentsPage", mode, 60, followingOnly, true, momentsCursor) else null
                }
            }
            val chains = async(Dispatchers.IO) {
                runCatching {
                    if (loadStories) postMapEndpoint("getMapStoriesPage", mode, 60, followingOnly, true, storiesCursor) else null
                }
            }
            val postResult = posts.await()
            val storyResult = chains.await()
            val postJson = postResult.getOrNull()
            val storyJson = storyResult.getOrNull()
            completion(MapDiscoverPayload(
                moments = postJson?.optJSONArray("moments")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toMapMoment() }
                        .filter { it.isArchived != true && it.mapHasRenderableMedia }
                }.orEmpty(),
                stories = storyJson?.optJSONArray("stories")?.let { arr ->
                    (0 until arr.length()).mapNotNull { arr.optJSONObject(it)?.toBackendMapStory()?.toStoryPreview() }
                }.orEmpty(),
                source = "backend",
                momentsError = postResult.exceptionOrNull()?.toMapServiceError(),
                storiesError = storyResult.exceptionOrNull()?.toMapServiceError(),
                momentsCursor = postJson?.optStringOrNull("nextCursor"),
                storiesCursor = storyJson?.optStringOrNull("nextCursor"),
            ))
        }
    }

    /** ≡ iOS `searchMomentsByLocation`. */
    fun searchMomentsByLocation(
        locationName: String,
        currentUserId: String?,
        completion: (Result<List<Moment>>) -> Unit,
    ) {
        if (currentUserId == null) {
            completion(Result.failure(MapServiceException(MapServiceError.Unauthenticated)))
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchMapMoments(MapQueryMode.Location(locationName), limit = 400) }
            }
            completion(result.mapFailure { it.toMapServiceError().let { e -> MapServiceException(e) } })
        }
    }

    /** ≡ iOS `searchMomentsInRegion`. */
    fun searchMomentsInRegion(
        region: MapRegionStore.Region,
        currentUserId: String?,
        completion: (Result<List<Moment>>) -> Unit,
    ) {
        if (currentUserId == null) {
            completion(Result.failure(MapServiceException(MapServiceError.Unauthenticated)))
            return
        }
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchMapMoments(MapQueryMode.Region(region), limit = 400) }
            }
            completion(result.mapFailure { it.toMapServiceError().let { e -> MapServiceException(e) } })
        }
    }

    /** ≡ iOS `searchStoriesByLocation`. */
    fun searchStoriesByLocation(
        locationName: String,
        completion: (Result<List<MapStoryPreview>>) -> Unit,
    ) {
        CoroutineScope(Dispatchers.Main).launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchMapStories(MapQueryMode.Location(locationName), limit = 120) }
            }
            completion(result.mapFailure { it.toMapServiceError().let { e -> MapServiceException(e) } })
        }
    }

    private fun <T> Result<T>.mapFailure(transform: (Throwable) -> Throwable): Result<T> =
        fold(onSuccess = { Result.success(it) }, onFailure = { Result.failure(transform(it)) })

    fun buildFriendActivityPins(
        moments: List<Moment>,
        stories: List<MapStoryPreview>,
        followingIds: Set<String>,
        withinMs: Long = 48L * 3_600_000L,
    ): List<MapFriendActivityPin> {
        val cutoff = System.currentTimeMillis() - withinMs
        data class Entry(
            var latitude: Double,
            var longitude: Double,
            var latest: Long,
            var moments: Int,
            var stories: Int,
            var username: String,
            var profile: String?,
        )
        val grouped = linkedMapOf<String, Entry>()

        for (moment in moments) {
            if (moment.authorId !in followingIds) continue
            if (moment.timestamp.time < cutoff) continue
            val coord = moment.locationCoordinate ?: continue
            val key = moment.authorId
            val existing = grouped[key]
            if (existing != null) {
                existing.moments += 1
                if (moment.timestamp.time > existing.latest) {
                    existing.latest = moment.timestamp.time
                    existing.latitude = coord.latitude
                    existing.longitude = coord.longitude
                }
            } else {
                grouped[key] = Entry(
                    latitude = coord.latitude,
                    longitude = coord.longitude,
                    latest = moment.timestamp.time,
                    moments = 1,
                    stories = 0,
                    username = moment.username,
                    profile = moment.profileImagePath,
                )
            }
        }

        for (story in stories) {
            if (story.authorId !in followingIds) continue
            if (story.timestamp.time < cutoff) continue
            val coord = story.coordinate ?: continue
            val key = story.authorId
            val existing = grouped[key]
            if (existing != null) {
                existing.stories += 1
                if (story.timestamp.time > existing.latest) {
                    existing.latest = story.timestamp.time
                    existing.latitude = coord.first
                    existing.longitude = coord.second
                }
            } else {
                grouped[key] = Entry(
                    latitude = coord.first,
                    longitude = coord.second,
                    latest = story.timestamp.time,
                    moments = 0,
                    stories = 1,
                    username = story.username,
                    profile = story.profileImagePath,
                )
            }
        }

        return grouped.map { (authorId, value) ->
            MapFriendActivityPin(
                id = authorId,
                authorId = authorId,
                username = value.username,
                profileImagePath = value.profile,
                latitude = value.latitude,
                longitude = value.longitude,
                latestTimestamp = Date(value.latest),
                momentCount = value.moments,
                storyCount = value.stories,
            )
        }.sortedByDescending { it.latestTimestamp.time }
    }

    private suspend fun fetchMapMoments(mode: MapQueryMode, limit: Int): List<Moment> {
        val json = postMapEndpoint("getMapMomentsPage", mode, limit)
        val arr = json.optJSONArray("moments") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.toMapMoment()
        }.filter { it.isArchived != true && it.mapHasRenderableMedia }
            .sortedByDescending { it.timestamp.time }
    }

    private suspend fun fetchMapStories(mode: MapQueryMode, limit: Int): List<MapStoryPreview> {
        val json = postMapEndpoint("getMapStoriesPage", mode, limit)
        val arr = json.optJSONArray("stories") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.toBackendMapStory()?.toStoryPreview()
        }
    }

    private suspend fun postMapEndpoint(
        functionName: String, mode: MapQueryMode, limit: Int,
        followingOnly: Boolean = false, paginate: Boolean = false, cursor: String? = null,
    ): JSONObject {
        val body = JSONObject().put("limit", limit)
        if (followingOnly) body.put("scope", "following")
        if (paginate) body.put("paginate", true)
        if (cursor != null) body.put("cursor", cursor)
        when (mode) {
            is MapQueryMode.Location -> {
                body.put("mode", "location")
                body.put("locationName", mode.locationName)
            }
            is MapQueryMode.Region -> {
                body.put("mode", "region")
                body.put("centerLatitude", mode.region.centerLat)
                body.put("centerLongitude", mode.region.centerLon)
                body.put("latitudeDelta", mode.region.latitudeDelta)
                body.put("longitudeDelta", mode.region.longitudeDelta)
            }
        }
        return try {
            CloudFunctionsClient.postJson(
                function = functionName,
                payload = body,
                timeoutMs = 15_000,
            )
        } catch (_: CloudFunctionsClient.NotAuthenticatedException) {
            throw MapServiceException(MapServiceError.Unauthenticated)
        } catch (_: CloudFunctionsClient.BackendException) {
            throw MapServiceException(MapServiceError.InvalidResponse)
        } catch (_: Exception) {
            throw MapServiceException(MapServiceError.Network)
        }
    }

    private fun Throwable.toMapServiceError(): MapServiceError =
        (this as? MapServiceException)?.error ?: MapServiceError.Network

    private fun JSONObject.toMapMoment(): Moment? {
        val id = optStringOrNull("id") ?: return null
        val authorId = optStringOrNull("authorId") ?: return null
        val mediaItems = optJSONArray("mediaItems")?.let { arr ->
            (0 until arr.length()).mapNotNull { i ->
                arr.optJSONObject(i)?.let { item ->
                    val typeRaw = item.optString("type", "image")
                    val type = MediaItem.MediaType.entries.firstOrNull { it.raw == typeRaw }
                        ?: MediaItem.MediaType.IMAGE
                    MediaItem(
                        id = item.optString("id"),
                        type = type,
                        url = item.optString("url"),
                        aspectRatio = item.optStringOrNull("aspectRatio"),
                        thumbnailUrl = item.optStringOrNull("thumbnailUrl"),
                        videoDuration = if (item.has("videoDuration")) item.optDouble("videoDuration") else null,
                    )
                }
            }.takeIf { it.isNotEmpty() }
        }
        val locationCoordinate = optJSONObject("locationCoordinate")?.let { coord ->
            if (coord.has("latitude") && coord.has("longitude")) {
                Moment.LocationCoordinate(coord.optDouble("latitude"), coord.optDouble("longitude"))
            } else null
        }
        return Moment(
            id = id,
            authorId = authorId,
            username = optString("username", "moments"),
            content = optString("content"),
            imagePath = optStringOrNull("imageUrl")
                ?: optStringOrNull("imagePath"),
            videoUrl = optStringOrNull("videoUrl"),
            timestamp = Date(optLong("timestamp")),
            commentCount = optInt("commentCount"),
            profileImagePath = optStringOrNull("profileImagePath"),
            location = optStringOrNull("location"),
            locationCoordinate = locationCoordinate,
            audience = optStringOrNull("audience"),
            mediaItems = mediaItems,
            aspectRatio = optStringOrNull("aspectRatio"),
            customListId = optStringOrNull("customListId"),
            thumbnailUrl = optStringOrNull("thumbnailUrl"),
            isArchived = if (has("isArchived")) optBoolean("isArchived") else null,
            hasHiddenLayers = optBoolean("hasHiddenLayers"),
            hiddenLayerCount = optInt("hiddenLayerCount"),
            disableComments = optBoolean("disableComments"),
            hideLikeCounts = optBoolean("hideLikeCounts"),
            allowSharing = optBoolean("allowSharing", true),
        )
    }

    /** ≡ iOS decode `BackendMapStory` → `toStoryPreview()`. */
    private fun JSONObject.toBackendMapStory(): BackendMapStory? {
        val id = optStringOrNull("id") ?: return null
        val authorId = optStringOrNull("authorId") ?: return null
        val locationCoordinate = optJSONObject("locationCoordinate")?.let { coord ->
            if (coord.has("latitude") && coord.has("longitude")) {
                Moment.LocationCoordinate(coord.optDouble("latitude"), coord.optDouble("longitude"))
            } else {
                null
            }
        }
        return BackendMapStory(
            id = id,
            authorId = authorId,
            username = optString("username", "moments"),
            profileImagePath = optStringOrNull("profileImagePath"),
            timestamp = if (has("timestamp")) optDouble("timestamp") else null,
            expirationDate = if (has("expirationDate")) optDouble("expirationDate") else null,
            audience = optStringOrNull("audience"),
            locationName = optStringOrNull("locationName"),
            locationCoordinate = locationCoordinate,
            locationFuzzed = if (has("locationFuzzed")) optBoolean("locationFuzzed") else null,
            previewUrl = optStringOrNull("previewUrl"),
            contentType = optStringOrNull("contentType"),
        )
    }
}

/**
 * ≡ iOS `MapLocationDisplayFormatter` (MapLocationServices.swift).
 * Título de lugar con ciudad: «Ciutat Vella, Barcelona».
 */
object MapLocationDisplayFormatter {
    private val cityCache = mutableMapOf<String, String>()

    fun formattedTitle(place: String, city: String?): String {
        val trimmed = place.trim()
        if (trimmed.isEmpty()) return city.orEmpty()
        if (trimmed.contains(",")) return trimmed
        val c = city?.trim().orEmpty()
        if (c.isEmpty()) return trimmed
        if (trimmed.contains(c, ignoreCase = true)) return trimmed
        return "$trimmed, $c"
    }

    fun cacheKey(lat: Double, lon: Double): String =
        String.format(java.util.Locale.US, "%.2f|%.2f", lat, lon)

    fun resolveTitle(
        context: Context,
        place: String,
        latitude: Double?,
        longitude: Double?,
    ): String {
        val trimmed = place.trim()
        if (trimmed.contains(",")) return trimmed
        if (latitude == null || longitude == null) return trimmed
        val key = cacheKey(latitude, longitude)
        cityCache[key]?.let { return formattedTitle(trimmed, it) }
        return try {
            @Suppress("DEPRECATION")
            val geocoder = android.location.Geocoder(context)
            @Suppress("DEPRECATION")
            val results = geocoder.getFromLocation(latitude, longitude, 1)
            val city = results?.firstOrNull()?.let { addr ->
                listOfNotNull(addr.locality, addr.adminArea).firstOrNull { it.isNotBlank() }
            }
            if (!city.isNullOrBlank()) cityCache[key] = city
            formattedTitle(trimmed, city)
        } catch (_: Exception) {
            trimmed
        }
    }
}
