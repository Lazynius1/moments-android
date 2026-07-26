package com.moments.android.notifications.core

import android.content.Context
import android.net.Uri
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.views.feed.reactions.ReactionType

/** Port de NotificationRowSupport.swift */

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

/** ≡ NotificationProfileLink — deep link `moments://notification-profile/{userId}` */
object NotificationProfileLink {
    private const val SCHEME = "moments"
    private const val HOST = "notification-profile"
    const val ANNOTATION_TAG = "notification-profile"

    fun url(userId: String): Uri? {
        val trimmed = userId.trim()
        if (trimmed.isEmpty()) return null
        return Uri.Builder()
            .scheme(SCHEME)
            .authority(HOST)
            .appendPath(trimmed)
            .build()
    }

    fun userId(from: Uri): String? {
        if (from.scheme != SCHEME || from.host != HOST) return null
        val raw = from.pathSegments.firstOrNull()?.takeIf { it.isNotEmpty() } ?: return null
        return raw
    }

    /** Path string for AnnotatedString annotations (same as [url]). */
    fun path(userId: String): String? = url(userId)?.toString()

    fun userIdFromPath(path: String): String? = runCatching { userId(Uri.parse(path)) }.getOrNull()
}

/** ≡ styledNotificationMessage — nombres en semibold + link de perfil + emoji grande opcional. */
fun styledNotificationMessage(
    plain: String,
    boldNames: List<String>,
    nameToUserId: Map<String, String>,
    baseColor: Color,
    largeEmoji: String? = null,
): AnnotatedString {
    val uniqueBoldNames = boldNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
        .sortedByDescending { it.length }

    return buildAnnotatedString {
        withStyle(SpanStyle(color = baseColor, fontSize = 14.sp)) {
            append(plain)
        }

        for (name in uniqueBoldNames) {
            var start = 0
            while (true) {
                val index = plain.indexOf(name, startIndex = start)
                if (index < 0) break
                val end = index + name.length
                addStyle(
                    SpanStyle(color = baseColor, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
                    index,
                    end,
                )
                nameToUserId[name]?.let { userId ->
                    NotificationProfileLink.path(userId)?.let { link ->
                        addStringAnnotation(NotificationProfileLink.ANNOTATION_TAG, link, index, end)
                    }
                }
                start = end
            }
        }

        if (!largeEmoji.isNullOrEmpty()) {
            val index = plain.indexOf(largeEmoji)
            if (index >= 0) {
                addStyle(SpanStyle(fontSize = 18.sp), index, index + largeEmoji.length)
            }
        }
    }
}

/**
 * ≡ notificationGroupedMessage(twoKey:threePlusKey:multipleKey:…).
 * En Android las claves iOS (`notifications.message.*.two`) son `@StringRes`.
 */
fun notificationGroupedMessage(
    context: Context,
    @StringRes twoRes: Int,
    @StringRes threePlusRes: Int,
    @StringRes multipleRes: Int,
    actors: NotificationGroupedActors,
    nameToUserId: Map<String, String>,
    baseColor: Color,
): AnnotatedString {
    val boldNames = buildList {
        add(actors.primary)
        actors.secondary?.let { add(it) }
    }
    val plain = when {
        actors.hasExactlyTwo && actors.secondary != null ->
            context.getString(twoRes, actors.primary, actors.secondary)
        actors.secondary != null && actors.othersCount > 0 ->
            context.getString(threePlusRes, actors.primary, actors.secondary, actors.othersCount)
        else -> {
            val moreCount = maxOf(actors.othersCount, 1)
            context.getString(multipleRes, actors.primary, moreCount)
        }
    }
    return styledNotificationMessage(plain, boldNames, nameToUserId, baseColor)
}

fun normalizedCommentPreview(notification: MomentsNotification): String? {
    for (raw in listOf(notification.reaction, notification.message)) {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) continue
        // ≡ if ReactionType(rawValue: text) != nil { continue }
        if (ReactionType.fromRaw(text) != null) continue
        if (text.length > 140) return text.take(137) + "…"
        return text
    }
    return null
}
