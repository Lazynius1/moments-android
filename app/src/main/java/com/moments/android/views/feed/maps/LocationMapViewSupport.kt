package com.moments.android.views.feed.maps

import android.content.Context
import android.location.Geocoder
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.models.Echo
import com.moments.android.models.EchoMomentRef
import com.moments.android.models.EchoParticipantStatus
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.privacy.PrivacyService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.util.Locale
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.abs

/**
 * Lógica de extensión de `LocationMapView` en `Maps.swift` (echo history, geocode helpers,
 * availability, jitter). Separado para no volcar 2k líneas en un solo archivo Compose.
 */
object LocationMapViewSupport {

    fun normalizeLocationQuery(raw: String): String {
        val trimmed = raw.trim()
        val noDiacritics = Normalizer.normalize(trimmed, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
        return noDiacritics.lowercase(Locale.getDefault())
    }

    fun isGenericLocationQuery(context: Context, locationName: String): Boolean {
        val normalizedDefault = normalizeLocationQuery(context.getString(R.string.feed_location_default))
        val query = normalizeLocationQuery(locationName)
        val generic = setOf(
            "",
            normalizedDefault,
            "ubicacion",
            "location",
            "ubicacion actual",
            "current location",
            "ubicacion seleccionada",
            "selected location",
            "ubicacion desconocida",
            "unknown location",
            "location unavailable",
            "ubicacion no disponible",
        )
        return query in generic
    }

    fun momentIdentityKey(moment: Moment): String {
        val mediaKey = moment.videoUrl ?: moment.imagePath.orEmpty()
        return "${moment.id ?: "noid"}|${moment.authorId}|$mediaKey|${moment.timestamp.time / 1000}"
    }

    fun dedupMomentsByIdentity(moments: List<Moment>): List<Moment> {
        val seen = linkedSetOf<String>()
        val result = mutableListOf<Moment>()
        for (moment in moments) {
            val mediaKey = moment.videoUrl ?: moment.imagePath.orEmpty()
            val key = "${moment.id ?: "noid"}|${moment.authorId}|$mediaKey"
            if (seen.add(key)) result.add(moment)
        }
        return result
    }

    fun echoCanExposeHistory(echo: Echo, viewerId: String): Boolean {
        val accepted = echo.participants.filter { it.status == EchoParticipantStatus.ACCEPTED }
        if (accepted.size < 2) return false
        return accepted.any { it.userId == viewerId }
    }

    fun echoMatchesCurrentLocation(
        echo: Echo,
        locationName: String,
        targetLat: Double?,
        targetLon: Double?,
    ): Boolean {
        val targetName = locationName.trim().lowercase(Locale.getDefault())
        val echoName = (echo.locationName ?: "").trim().lowercase(Locale.getDefault())
        if (targetName.isNotEmpty() && echoName.isNotEmpty() && targetName == echoName) return true
        if (targetLat == null || targetLon == null) {
            return targetName.isNotEmpty() && targetName == echoName
        }
        val distance = haversineMeters(targetLat, targetLon, echo.location.latitude, echo.location.longitude)
        return distance <= 1200.0
    }

    fun jitteredCoordinate(lat: Double, lon: Double, seed: String): Pair<Double, Double> {
        val hash = abs(seed.hashCode())
        val angle = (hash % 360) * (Math.PI / 180.0)
        val distanceMeters = 14.0 + (hash % 4) * 5.0
        val latMetersPerDegree = 111_000.0
        val cosLat = max(cos(lat * Math.PI / 180.0), 0.2)
        val lonMetersPerDegree = latMetersPerDegree * cosLat
        val latOffset = (distanceMeters * cos(angle)) / latMetersPerDegree
        val lonOffset = (distanceMeters * sin(angle)) / lonMetersPerDegree
        return (lat + latOffset) to (lon + lonOffset)
    }

    fun regionContains(
        region: MapRegionStore.Region,
        lat: Double,
        lon: Double,
    ): Boolean {
        val latMin = region.centerLat - region.latitudeDelta / 2
        val latMax = region.centerLat + region.latitudeDelta / 2
        val lonMin = region.centerLon - region.longitudeDelta / 2
        val lonMax = region.centerLon + region.longitudeDelta / 2
        return lat in latMin..latMax && lon in lonMin..lonMax
    }

    fun buildEchoHistoryMoment(liveMoment: Moment, echo: Echo, fallbackLocationName: String): Moment {
        val resolved = echo.locationName?.trim()?.takeIf { it.isNotEmpty() } ?: fallbackLocationName
        return liveMoment.copy(
            location = resolved,
            locationCoordinate = Moment.LocationCoordinate(echo.location.latitude, echo.location.longitude),
        )
    }

    suspend fun loadEchoHistoryMoments(
        locationName: String,
        targetLat: Double?,
        targetLon: Double?,
        echoHistoryUserId: String?,
    ): EchoHistoryLoadResult = withContext(Dispatchers.IO) {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid?.trim().orEmpty()
        val userId = (echoHistoryUserId ?: currentUid).trim()
        if (userId.isEmpty() || currentUid.isEmpty() || userId != currentUid) {
            return@withContext EchoHistoryLoadResult()
        }

        val snapshot = FirebaseFirestore.getInstance()
            .collection("echoes")
            .whereArrayContains("participantIds", userId)
            .get()
            .await()

        val echoes = snapshot.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            val data = doc.data as? Map<String, Any?> ?: return@mapNotNull null
            Echo.from(doc.id, data)
        }.filter {
            echoMatchesCurrentLocation(it, locationName, targetLat, targetLon) &&
                echoCanExposeHistory(it, userId)
        }

        loadViewableEchoHistoryMoments(echoes, userId, locationName)
    }

    private suspend fun loadViewableEchoHistoryMoments(
        echoes: List<Echo>,
        viewerId: String,
        fallbackLocationName: String,
    ): EchoHistoryLoadResult = coroutineScope {
        val firestore = FirestoreService()
        val privacy = PrivacyService
        val jobs = echoes.flatMap { echo ->
            echo.visibleMoments.map { ref ->
                async {
                    fetchViewableEchoHistoryMoment(ref, echo, viewerId, fallbackLocationName, firestore, privacy)
                        ?.let { moment -> moment to (echo.id ?: "") }
                }
            }
        }
        val pairs = jobs.awaitAll().filterNotNull()
        val moments = dedupMomentsByIdentity(pairs.map { it.first }).sortedByDescending { it.timestamp.time }
        val mapping = pairs.associate { (moment, echoId) ->
            momentIdentityKey(moment) to echoId
        }.filterValues { it.isNotEmpty() }
        EchoHistoryLoadResult(
            moments = moments,
            echoIdByMomentIdentity = mapping,
            availability = moments.associate { it.mapAvailabilityKey to true },
        )
    }

    private suspend fun fetchViewableEchoHistoryMoment(
        ref: EchoMomentRef,
        echo: Echo,
        viewerId: String,
        fallbackLocationName: String,
        firestore: FirestoreService,
        privacy: PrivacyService,
    ): Moment? {
        val momentId = ref.momentId.trim()
        val authorId = ref.authorId.trim()
        if (momentId.isEmpty() || authorId.isEmpty()) return null
        val live = runCatching { firestore.fetchMoment(momentId, authorId) }.getOrNull() ?: return null
        if (live.isArchived == true) return null
        if (live.authorId != authorId) return null
        val canView = privacy.canUserViewMomentEnhanced(live, viewerId)
        if (!canView) return null
        return buildEchoHistoryMoment(live, echo, fallbackLocationName)
    }

    suspend fun validateLiveAvailability(
        moment: Moment,
        viewerId: String,
        firestore: FirestoreService = FirestoreService(),
        privacy: PrivacyService = PrivacyService,
    ): Boolean {
        if (moment.authorId == viewerId) return true
        val momentId = moment.id?.takeIf { it.isNotBlank() } ?: return false
        val live = runCatching { firestore.fetchMoment(momentId, moment.authorId) }.getOrNull() ?: return false
        if (live.isArchived == true) return false
        return privacy.canUserViewMomentEnhanced(live, viewerId)
    }

    suspend fun geocodeLocationName(context: Context, locationName: String): GeocodeOutcome =
        withContext(Dispatchers.IO) {
            val query = locationName.trim()
            if (query.isEmpty() || isGenericLocationQuery(context, query)) {
                return@withContext GeocodeOutcome.GenericFallback
            }
            try {
                @Suppress("DEPRECATION")
                val results = Geocoder(context, Locale.getDefault()).getFromLocationName(query, 5).orEmpty()
                val best = results.firstOrNull()
                if (best == null) {
                    GeocodeOutcome.NoResults
                } else {
                    GeocodeOutcome.Success(best.latitude, best.longitude)
                }
            } catch (_: Exception) {
                GeocodeOutcome.Failed
            }
        }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * kotlin.math.atan2(kotlin.math.sqrt(a), kotlin.math.sqrt(1 - a))
    }

    data class EchoHistoryLoadResult(
        val moments: List<Moment> = emptyList(),
        val echoIdByMomentIdentity: Map<String, String> = emptyMap(),
        val availability: Map<String, Boolean> = emptyMap(),
    )

    sealed class GeocodeOutcome {
        data class Success(val latitude: Double, val longitude: Double) : GeocodeOutcome()
        data object GenericFallback : GeocodeOutcome()
        data object NoResults : GeocodeOutcome()
        data object Failed : GeocodeOutcome()
    }
}
