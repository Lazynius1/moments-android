package com.moments.android.views.feed.core

import com.moments.android.views.profile.core.sections.UserProfileZoomNavigation

/**
 * Port 1:1 de `FeedRoutes.swift`.
 *
 * `zoomSourceID` ≡ `UserProfileZoomNavigation.sourceID` (Compose SharedTransition).
 */

/** Port 1:1 de `FeedProfileSheetRoute`. */
data class FeedProfileSheetRoute(val userId: String) {
    val id: String get() = userId

    val zoomSourceID: String
        get() = UserProfileZoomNavigation.sourceID(userId)
}

/** Port 1:1 de `FeedEchoInvitationRoute`. */
data class FeedEchoInvitationRoute(val echoId: String) {
    val id: String get() = echoId
}

/** Port 1:1 de `StoryUserPresentationRoute`. */
data class StoryUserPresentationRoute(
    val userId: String,
    val startStoryId: String? = null,
    val startElapsed: Double = 0.0,
) {
    val id: String get() = userId
}
