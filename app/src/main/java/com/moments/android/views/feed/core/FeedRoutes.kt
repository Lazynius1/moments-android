package com.moments.android.views.feed.core

/**
 * Port 1:1 de `FeedRoutes.swift`.
 *
 * iOS `FeedProfileSheetRoute.zoomSourceID` usa
 * `UserProfileZoomNavigation.sourceID(userId:)` → `"user-profile-\(userId)"`.
 * Hasta portar Profile/Core, replicamos esa misma fórmula aquí.
 */
private fun userProfileZoomSourceId(userId: String): String = "user-profile-$userId"

/** Port 1:1 de `FeedProfileSheetRoute`. */
data class FeedProfileSheetRoute(val userId: String) {
    val id: String get() = userId

    val zoomSourceID: String
        get() = userProfileZoomSourceId(userId)
}

/** Port 1:1 de `FeedEchoInvitationRoute`. */
data class FeedEchoInvitationRoute(val echoId: String) {
    val id: String get() = echoId
}

/** Port 1:1 de `StoryUserPresentationRoute`. */
data class StoryUserPresentationRoute(val userId: String) {
    val id: String get() = userId
}
