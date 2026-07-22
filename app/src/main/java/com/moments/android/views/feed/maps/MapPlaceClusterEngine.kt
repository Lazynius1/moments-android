package com.moments.android.views.feed.maps

import java.util.Date

/** Port de `MapPlaceClusterEngine.swift`. */
data class MapPlaceCluster(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val momentIds: List<String> = emptyList(),
    val stories: List<MapStoryPreview> = emptyList(),
    val friends: List<MapFriendActivityPin> = emptyList(),
) {
    val momentCount: Int get() = momentIds.size
    val storyCount: Int get() = stories.size
    val totalCount: Int get() = momentCount + storyCount

    val primaryStory: MapStoryPreview?
        get() = stories.maxByOrNull { it.timestamp.time }

    val latestTimestamp: Date
        get() {
            val momentDates = momentIds.map { Date(0) }
            val storyDate = stories.maxOfOrNull { it.timestamp } ?: Date(0)
            val friendDate = friends.maxOfOrNull { it.latestTimestamp } ?: Date(0)
            return listOfNotNull(storyDate, friendDate, momentDates.maxOrNull()).maxOrNull() ?: Date(0)
        }

    val hasFreshStory: Boolean
        get() {
            val story = primaryStory ?: return false
            return System.currentTimeMillis() - story.timestamp.time < 3_600_000
        }

    val isAggregate: Boolean get() = id == MapPlaceClusterEngine.AGGREGATE_CLUSTER_ID
}

data class MapPlaceLayout(
    val placeClusters: List<MapPlaceCluster> = emptyList(),
    val standaloneFriends: List<MapFriendActivityPin> = emptyList(),
) {
    companion object {
        val Empty = MapPlaceLayout()
    }
}

object MapPlaceClusterEngine {
    const val AGGREGATE_CLUSTER_ID = "region-aggregate"

    fun cluster(places: List<MapPlaceAnnotation>, zoom: Float): List<MapCluster> {
        if (places.isEmpty()) return emptyList()
        if (places.size == 1) {
            val place = places.first()
            return listOf(MapCluster(place.latitude, place.longitude, listOf(place)))
        }
        val avgLat = places.map { it.latitude }.average()
        val avgLng = places.map { it.longitude }.average()
        return listOf(MapCluster(avgLat, avgLng, places))
    }

    fun aggregateRegionCluster(
        title: String,
        momentIds: List<String>,
        stories: List<MapStoryPreview>,
        latitude: Double,
        longitude: Double,
    ): MapPlaceCluster = MapPlaceCluster(
        id = AGGREGATE_CLUSTER_ID,
        latitude = latitude,
        longitude = longitude,
        displayName = title,
        momentIds = momentIds,
        stories = stories,
    )

    fun build(
        momentIds: List<String>,
        stories: List<MapStoryPreview>,
        friendPins: List<MapFriendActivityPin>,
        filter: MapDiscoverContentFilter,
        latitude: Double,
        longitude: Double,
    ): MapPlaceLayout {
        if (filter == MapDiscoverContentFilter.Friends) {
            return MapPlaceLayout(standaloneFriends = friendPins)
        }
        if (momentIds.isEmpty() && stories.isEmpty()) return MapPlaceLayout.Empty
        return MapPlaceLayout(
            placeClusters = listOf(
                aggregateRegionCluster(
                    title = "",
                    momentIds = momentIds,
                    stories = if (filter == MapDiscoverContentFilter.Places) emptyList() else stories,
                    latitude = latitude,
                    longitude = longitude,
                ),
            ),
        )
    }
}
