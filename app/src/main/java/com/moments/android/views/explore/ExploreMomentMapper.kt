package com.moments.android.views.explore

import com.moments.android.models.Moment
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment

fun Moment.toExploreFeedMoment(): FeedMoment {
    val media = (mediaItems ?: emptyList()).mapIndexed { index, item ->
        FeedMediaItem(
            id = item.id.ifBlank { "$index" },
            type = item.type.raw,
            url = item.url,
            thumbnailUrl = item.thumbnailUrl,
            aspectRatio = item.aspectRatio,
            isHiddenByModeration = item.isHiddenByModeration,
            tags = item.tags,
            videoDuration = item.videoDuration,
        )
    }.ifEmpty {
        buildList {
            imagePath?.takeIf { it.isNotBlank() }?.let {
                add(
                    FeedMediaItem(
                        id = "img",
                        type = "image",
                        url = it,
                        thumbnailUrl = null,
                        aspectRatio = aspectRatio,
                    ),
                )
            }
            videoUrl?.takeIf { it.isNotBlank() }?.let {
                add(
                    FeedMediaItem(
                        id = "vid",
                        type = "video",
                        url = it,
                        thumbnailUrl = thumbnailUrl,
                        aspectRatio = aspectRatio,
                    ),
                )
            }
        }
    }
    val reactionTotal = reactions.values.sumOf { it.size }
    return FeedMoment(
        id = id.orEmpty(),
        authorId = authorId,
        username = username,
        content = content,
        timestamp = timestamp.time,
        profileImagePath = profileImagePath,
        location = location,
        mediaItems = media,
        aspectRatio = aspectRatio,
        commentCount = commentCount,
        reactionCount = reactionTotal,
        hideLikeCounts = hideLikeCounts,
        disableComments = disableComments,
        hasHiddenLayers = hasHiddenLayers,
        hiddenLayerCount = hiddenLayerCount,
        audience = audience,
        customListId = customListId,
        isArchived = isArchived,
        locationCoordinate = locationCoordinate,
    )
}
