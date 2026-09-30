package com.moments.android.views.feed.maps

import com.moments.android.models.Moment
import com.moments.android.models.MediaItem
import com.moments.android.services.content.FeedMoment
import java.util.Date

/** Preserve the tapped post and its media without refetching the entire place. */
fun FeedMoment.mapOriginMoment(): Moment = Moment(
    id = id, authorId = authorId, username = username, content = content,
    timestamp = Date(timestamp), audience = audience, customListId = customListId,
    location = location, locationCoordinate = locationCoordinate,
    profileImagePath = profileImagePath, imagePath = imagePath, thumbnailUrl = thumbnailUrl,
    aspectRatio = aspectRatio, isArchived = isArchived, allowSharing = allowSharing,
    commentCount = commentCount, hideLikeCounts = hideLikeCounts, disableComments = disableComments,
    mediaItems = visibleMediaItems.map { item ->
        MediaItem(id = item.id, type = MediaItem.MediaType.entries.firstOrNull { it.raw.equals(item.type, ignoreCase = true) } ?: MediaItem.MediaType.IMAGE,
            url = item.url, thumbnailUrl = item.thumbnailUrl, aspectRatio = item.aspectRatio, videoDuration = item.videoDuration)
    },
)
