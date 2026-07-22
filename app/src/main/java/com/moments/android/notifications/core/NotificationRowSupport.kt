package com.moments.android.notifications.core

import com.moments.android.models.NotificationType

fun isPerActorSocialNotification(type: NotificationType): Boolean = when (type) {
    NotificationType.NEW_FOLLOWER,
    NotificationType.MUTUAL_CONNECTION,
    NotificationType.FOLLOW_REQUEST,
    NotificationType.REQUEST_ACCEPTED,
    -> true
    else -> false
}

object NotificationRowMetrics {
    const val AVATAR_SIZE_DP = 48f
    const val STACKED_AVATAR_SIZE_DP = 46f
    const val STACKED_OVERLAP_RATIO = 0.34f
    val stackedOverlapDp get() = STACKED_AVATAR_SIZE_DP * STACKED_OVERLAP_RATIO
    val stackedRowWidthDp get() = STACKED_AVATAR_SIZE_DP * 2 - stackedOverlapDp
    const val STORY_THUMB_WIDTH_DP = 44f
    const val STORY_THUMB_HEIGHT_DP = 58f
    const val STORY_THUMB_CORNER_RADIUS_DP = 8f
}

data class NotificationGroupedActors(
    val primary: String,
    val secondary: String?,
    val othersCount: Int,
) {
    val hasExactlyTwo: Boolean get() = secondary != null && othersCount == 0
}

fun uniqueSenderIds(group: NotificationGroup): List<String> {
    val seen = mutableSetOf<String>()
    return group.notifications.mapNotNull { notification ->
        val id = notification.senderId.trim()
        if (id.isEmpty() || !seen.add(id)) null else id
    }
}

object NotificationProfileLink {
    private const val HOST = "notification-profile"

    fun path(userId: String): String? {
        val trimmed = userId.trim()
        if (trimmed.isEmpty()) return null
        return "moments://$HOST/${java.net.URLEncoder.encode(trimmed, Charsets.UTF_8.name())}"
    }

    fun userIdFromPath(path: String): String? {
        val prefix = "moments://$HOST/"
        if (!path.startsWith(prefix)) return null
        val raw = path.removePrefix(prefix).trim('/')
        if (raw.isEmpty()) return null
        return java.net.URLDecoder.decode(raw, Charsets.UTF_8.name())
    }
}

fun normalizedCommentPreview(notification: com.moments.android.models.MomentsNotification): String? {
    for (raw in listOf(notification.reaction, notification.message)) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) continue
        if (text.length > 140) return text.take(137) + "…"
        return text
    }
    return null
}
