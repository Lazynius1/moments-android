package com.moments.android.views.feed.maps

import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import org.junit.Assert.*
import org.junit.Test

class MapOriginMomentTest {
    @Test fun tappedPostRetainsItsIdentityLocationAndVisibleMedia() {
        val coordinate = Moment.LocationCoordinate(41.38, 2.19)
        val original = FeedMoment(
            id = "origin", authorId = "author", username = "user", content = "post",
            timestamp = 1_750_000_000_000, profileImagePath = null, location = "Barcelona",
            mediaItems = listOf(
                FeedMediaItem("video", "video", "video.mp4", "cover.jpg", "9:16", videoDuration = 10.0),
                FeedMediaItem("hidden", "image", "hidden.jpg", null, "1:1", isHiddenByModeration = true),
            ),
            aspectRatio = "9:16", commentCount = 2, reactionCount = 3,
            hideLikeCounts = false, disableComments = false,
            audience = "mutuals", locationCoordinate = coordinate,
        )
        val mapped = original.mapOriginMoment()
        assertEquals(original.id, mapped.id)
        assertEquals(coordinate, mapped.locationCoordinate)
        assertEquals(original.timestamp, mapped.timestamp.time)
        assertEquals("mutuals", mapped.audience)
        assertEquals(1, mapped.mediaItems?.size)
        assertEquals(MediaItem.MediaType.VIDEO, mapped.mediaItems?.first()?.type)
        assertEquals("cover.jpg", mapped.mapPreferredVideoThumbnailUrl)
    }
}
