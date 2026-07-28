package com.moments.android.views.feed.maps

import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.utilities.MomentsFormat
import java.util.Date
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Port de `MapPlaceClusterEngine.swift` + `MapPlaceCluster` / `MapPlaceLayout`.
 */
data class MapPlaceCluster(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val moments: List<Moment> = emptyList(),
    val stories: List<MapStoryPreview> = emptyList(),
    val friends: List<MapFriendActivityPin> = emptyList(),
) {
    val momentCount: Int get() = moments.size
    val storyCount: Int get() = stories.size
    val totalCount: Int get() = momentCount + storyCount

    val primaryStory: MapStoryPreview?
        get() = stories.maxByOrNull { it.timestamp.time }

    val primaryMoment: Moment?
        get() = moments.maxByOrNull { it.timestamp.time }

    val latestTimestamp: Date
        get() {
            // iOS: max(…, .distantPast) cuando vacío
            val momentDate = moments.maxOfOrNull { it.timestamp.time } ?: Long.MIN_VALUE
            val storyDate = stories.maxOfOrNull { it.timestamp.time } ?: Long.MIN_VALUE
            val friendDate = friends.maxOfOrNull { it.latestTimestamp.time } ?: Long.MIN_VALUE
            return Date(maxOf(momentDate, storyDate, friendDate))
        }

    val hasFreshStory: Boolean
        get() {
            val story = primaryStory ?: return false
            return System.currentTimeMillis() - story.timestamp.time < 3_600_000
        }

    val isStale: Boolean
        get() = System.currentTimeMillis() - latestTimestamp.time > 7L * 24 * 3_600_000

    val isAggregate: Boolean get() = id == MapPlaceClusterEngine.AGGREGATE_CLUSTER_ID

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is MapPlaceCluster) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}

data class MapPlaceLayout(
    val placeClusters: List<MapPlaceCluster>,
    val standaloneFriends: List<MapFriendActivityPin>,
) {
    companion object {
        /** ≡ iOS `MapPlaceLayout.empty`. */
        val Empty = MapPlaceLayout(placeClusters = emptyList(), standaloneFriends = emptyList())
    }
}

object MapPlaceClusterEngine {
    /** ≡ iOS `aggregateClusterId`. */
    const val AGGREGATE_CLUSTER_ID = "region-aggregate"

    private data class MutableCluster(
        var id: String,
        var latitude: Double,
        var longitude: Double,
        var displayName: String,
        var moments: MutableList<Moment> = mutableListOf(),
        var stories: MutableList<MapStoryPreview> = mutableListOf(),
        var friends: MutableList<MapFriendActivityPin> = mutableListOf(),
        // iOS: .distantPast
        var latestTimestamp: Long = Long.MIN_VALUE,
    )

    /**
     * ≡ iOS `build(… region:)` — solo usa span (deltas); center se pasa por paridad de API.
     */
    fun build(
        moments: List<Moment>,
        stories: List<MapStoryPreview>,
        friendPins: List<MapFriendActivityPin>,
        filter: MapDiscoverContentFilter,
        @Suppress("UNUSED_PARAMETER") centerLat: Double,
        @Suppress("UNUSED_PARAMETER") centerLon: Double,
        latitudeDelta: Double,
        longitudeDelta: Double,
    ): MapPlaceLayout {
        if (filter == MapDiscoverContentFilter.Friends) {
            return MapPlaceLayout(placeClusters = emptyList(), standaloneFriends = friendPins)
        }

        val mergeRadius = mergeRadiusMeters(latitudeDelta, longitudeDelta)
        val precision = coordinatePrecision(latitudeDelta, longitudeDelta)
        val clusters = mutableListOf<MutableCluster>()

        fun upsertMoment(moment: Moment, lat: Double, lon: Double, name: String) {
            val key = clusterKey(name, lat, lon, precision)
            val index = clusters.indexOfFirst { existing ->
                existing.id == key || shouldMerge(
                    existing.latitude, existing.longitude, lat, lon, mergeRadius,
                    existing.displayName, name,
                )
            }
            if (index >= 0) {
                clusters[index].moments.add(moment)
                if (moment.timestamp.time > clusters[index].latestTimestamp) {
                    clusters[index].latestTimestamp = moment.timestamp.time
                }
                if (clusters[index].displayName.isEmpty() && name.isNotEmpty()) {
                    clusters[index].displayName = name
                }
            } else {
                clusters.add(
                    MutableCluster(
                        id = key,
                        latitude = lat,
                        longitude = lon,
                        displayName = name,
                        moments = mutableListOf(moment),
                        latestTimestamp = moment.timestamp.time,
                    ),
                )
            }
        }

        fun upsertStory(story: MapStoryPreview, lat: Double, lon: Double, name: String) {
            val key = clusterKey(name, lat, lon, precision)
            val index = clusters.indexOfFirst { existing ->
                existing.id == key || shouldMerge(
                    existing.latitude, existing.longitude, lat, lon, mergeRadius,
                    existing.displayName, name,
                )
            }
            if (index >= 0) {
                clusters[index].stories.add(story)
                if (story.timestamp.time > clusters[index].latestTimestamp) {
                    clusters[index].latestTimestamp = story.timestamp.time
                }
                if (clusters[index].displayName.isEmpty() && name.isNotEmpty()) {
                    clusters[index].displayName = name
                }
            } else {
                clusters.add(
                    MutableCluster(
                        id = key,
                        latitude = lat,
                        longitude = lon,
                        displayName = name,
                        stories = mutableListOf(story),
                        latestTimestamp = story.timestamp.time,
                    ),
                )
            }
        }

        for (moment in moments) {
            val coord = moment.locationCoordinate ?: continue
            if (!isValidCoordinate(coord.latitude, coord.longitude)) continue
            upsertMoment(moment, coord.latitude, coord.longitude, normalizedLocationName(moment.location))
        }

        val storiesForMap = if (filter == MapDiscoverContentFilter.Places) emptyList() else stories
        for (story in storiesForMap) {
            val coord = story.coordinate ?: continue
            if (!isValidCoordinate(coord.first, coord.second)) continue
            upsertStory(story, coord.first, coord.second, normalizedLocationName(story.locationName))
        }

        val absorbedFriendIds = mutableSetOf<String>()
        if (filter == MapDiscoverContentFilter.All) {
            for (pin in friendPins) {
                val index = clusters.indexOfFirst { cluster ->
                    shouldMerge(
                        cluster.latitude, cluster.longitude, pin.latitude, pin.longitude, mergeRadius,
                        cluster.displayName, "",
                    ) || cluster.moments.any { it.authorId == pin.authorId }
                        || cluster.stories.any { it.authorId == pin.authorId }
                }
                if (index < 0) continue
                clusters[index].friends.add(pin)
                absorbedFriendIds.add(pin.authorId)
                if (pin.latestTimestamp.time > clusters[index].latestTimestamp) {
                    clusters[index].latestTimestamp = pin.latestTimestamp.time
                }
            }
        }

        val standaloneFriends = if (filter == MapDiscoverContentFilter.Places) {
            emptyList()
        } else {
            friendPins.filter { it.authorId !in absorbedFriendIds }
        }

        val merged = mutableListOf<MutableCluster>()
        for (cluster in clusters) {
            val index = merged.indexOfFirst { existing ->
                shouldMerge(
                    existing.latitude, existing.longitude, cluster.latitude, cluster.longitude, mergeRadius,
                    existing.displayName, cluster.displayName,
                )
            }
            if (index >= 0) {
                merged[index].moments.addAll(cluster.moments)
                merged[index].stories.addAll(cluster.stories)
                merged[index].friends.addAll(cluster.friends)
                if (cluster.latestTimestamp > merged[index].latestTimestamp) {
                    merged[index].latestTimestamp = cluster.latestTimestamp
                }
                if (merged[index].displayName.isEmpty() && cluster.displayName.isNotEmpty()) {
                    merged[index].displayName = cluster.displayName
                }
            } else {
                merged.add(cluster)
            }
        }

        val placeClusters = merged
            .filter { it.moments.isNotEmpty() || it.stories.isNotEmpty() }
            .map { mutable ->
                MapPlaceCluster(
                    id = mutable.id,
                    latitude = mutable.latitude,
                    longitude = mutable.longitude,
                    displayName = resolvedDisplayName(mutable.displayName, mutable.moments, mutable.stories),
                    moments = mutable.moments.sortedByDescending { it.timestamp.time },
                    stories = mutable.stories.sortedByDescending { it.timestamp.time },
                    friends = mutable.friends,
                )
            }
            .sortedByDescending { it.latestTimestamp.time }

        return MapPlaceLayout(placeClusters = placeClusters, standaloneFriends = standaloneFriends)
    }

    fun aggregateRegionCluster(
        title: String,
        moments: List<Moment>,
        stories: List<MapStoryPreview>,
        latitude: Double,
        longitude: Double,
    ): MapPlaceCluster = MapPlaceCluster(
        id = AGGREGATE_CLUSTER_ID,
        latitude = latitude,
        longitude = longitude,
        displayName = title,
        moments = moments.sortedByDescending { it.timestamp.time },
        stories = stories.sortedByDescending { it.timestamp.time },
        friends = emptyList(),
    )

    fun cluster(
        friend: MapFriendActivityPin,
        moments: List<Moment>,
        stories: List<MapStoryPreview>,
    ): MapPlaceCluster {
        val authorMoments = moments.filter { it.authorId == friend.authorId }
            .sortedByDescending { it.timestamp.time }
        val authorStories = stories.filter { it.authorId == friend.authorId }
            .sortedByDescending { it.timestamp.time }
        return MapPlaceCluster(
            id = "friend-${friend.authorId}",
            latitude = friend.latitude,
            longitude = friend.longitude,
            displayName = friend.username,
            moments = authorMoments,
            stories = authorStories,
            friends = listOf(friend),
        )
    }

    /**
     * ≡ iOS `jitteredCoordinate`.
     * Hash: `abs(seed.hashCode() + index * 31)` con overflow Int JVM (≡ Swift `&+` / `&*`).
     * El valor de `String.hashCode` ≠ `hashValue` de Swift → offset distinto, misma fórmula.
     */
    fun jitteredCoordinate(baseLat: Double, baseLon: Double, seed: String, index: Int): Pair<Double, Double> {
        val combined = seed.hashCode() + index * 31 // Int overflow wraps
        val hash = if (combined == Int.MIN_VALUE) combined else kotlin.math.abs(combined)
        val angle = (hash % 360) * (Math.PI / 180.0)
        val radius = 0.00018 + (hash % 40) * 0.000004
        return (baseLat + cos(angle) * radius) to (baseLon + sin(angle) * radius)
    }

    private fun normalizedLocationName(raw: String?): String =
        raw?.trim()?.lowercase().orEmpty()

    /** ≡ iOS `String.capitalized` (cada palabra). */
    private fun swiftCapitalized(value: String): String =
        value.lowercase(Locale.getDefault())
            .split(" ")
            .joinToString(" ") { word ->
                word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            }

    private fun resolvedDisplayName(name: String, moments: List<Moment>, stories: List<MapStoryPreview>): String {
        if (name.isNotEmpty()) return swiftCapitalized(name)
        moments.mapNotNull { it.location?.trim() }.firstOrNull { it.isNotEmpty() }?.let { return it }
        stories.mapNotNull { it.locationName?.trim() }.firstOrNull { it.isNotEmpty() }?.let { return it }
        return MomentsFormat.requireContext().getString(R.string.maps_place_unnamed)
    }

    private fun clusterKey(name: String, lat: Double, lon: Double, precision: Int): String {
        val factor = 10.0.pow(precision.toDouble())
        val rLat = round(lat * factor) / factor
        val rLon = round(lon * factor) / factor
        return if (name.isNotEmpty()) "place|$name|$rLat|$rLon" else "coord|$rLat|$rLon"
    }

    private fun coordinatePrecision(latDelta: Double, lonDelta: Double): Int {
        val delta = maxOf(latDelta, lonDelta)
        return when {
            delta > 0.12 -> 3
            delta > 0.04 -> 4
            else -> 5
        }
    }

    private fun mergeRadiusMeters(latDelta: Double, lonDelta: Double): Double {
        val delta = maxOf(latDelta, lonDelta)
        return when {
            delta > 0.12 -> 450.0
            delta > 0.04 -> 180.0
            else -> 90.0
        }
    }

    private fun shouldMerge(
        lat1: Double, lon1: Double, lat2: Double, lon2: Double,
        radius: Double, name: String, otherName: String,
    ): Boolean {
        if (name.isNotEmpty() && otherName.isNotEmpty() && name == otherName) return true
        return haversineMeters(lat1, lon1, lat2, lon2) <= radius
    }

    /** ≡ iOS `CLLocationCoordinate2DIsValid`. */
    private fun isValidCoordinate(lat: Double, lon: Double): Boolean =
        lat.isFinite() && lon.isFinite() && lat in -90.0..90.0 && lon in -180.0..180.0

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
