package com.moments.android.views.feed.maps

import java.util.Date

/** Port de `MapDiscoverSupport.swift`. */
enum class MapServiceError {
    Unauthenticated,
    InvalidConfiguration,
    Network,
    InvalidResponse,
    Decoding,
}

enum class MapDiscoverContentFilter(val titleKeyRes: Int) {
    All(com.moments.android.R.string.maps_filter_all),
    Friends(com.moments.android.R.string.maps_filter_friends),
    Places(com.moments.android.R.string.maps_filter_places),
}

enum class MapDiscoverTimeFilter(val titleKeyRes: Int) {
    Today(com.moments.android.R.string.maps_time_filter_today),
    Week(com.moments.android.R.string.maps_time_filter_week),
    All(com.moments.android.R.string.maps_time_filter_all),
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
)

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
)

data class MapDiscoverPayload(
    val momentIds: List<String> = emptyList(),
    val stories: List<MapStoryPreview> = emptyList(),
    val source: String = "",
    val momentsError: MapServiceError? = null,
    val storiesError: MapServiceError? = null,
) {
    val hasContent: Boolean get() = momentIds.isNotEmpty() || stories.isNotEmpty()
    val isCompleteFailure: Boolean get() = !hasContent && momentsError != null && storiesError != null
    val hasPartialFailure: Boolean get() = hasContent && (momentsError != null || storiesError != null)
}

data class MapMomentDetailRoute(
    val momentIds: List<String>,
    val initialIndex: Int,
    val locationName: String,
)

object MapSheetPresentationDelay {
    const val DISMISS_BEFORE_NEXT_PRESENTATION_MS = 450L
    const val REOPEN_BOTTOM_SHEET_AFTER_DETAIL_MS = 350L
}

object MapDiscoverSupport {
    fun filterVisiblePlaces(places: List<MapPlaceAnnotation>, query: String): List<MapPlaceAnnotation> {
        if (query.isBlank()) return places
        return places.filter { it.name.contains(query, ignoreCase = true) }
    }
}
