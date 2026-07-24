package com.moments.android.views.story

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import com.google.firebase.Timestamp
import java.util.Date

/** Port de `StoryReaction` en `Views/story/StoryModels.swift`. */
data class StoryReaction(
    val id: String,
    val userId: String,
    val reaction: String,
    val timestamp: Date,
)

/** Una reacción por persona; conserva la más reciente como Swift `latestPerUser()`. */
fun List<StoryReaction>.latestPerUser(): List<StoryReaction> =
    groupBy { it.userId }
        .mapNotNull { (_, reactions) -> reactions.maxByOrNull { it.timestamp.time } }
        .sortedByDescending { it.timestamp.time }

/** Port de `StoryViewer`, incluido el decode Firestore tolerante de fechas. */
data class StoryViewer(
    val id: String,
    val userId: String,
    val username: String?,
    val profileImagePath: String?,
    val timestamp: Date,
    val viewCount: Int = 1,
    val firstViewedAt: Date? = null,
    val lastViewedAt: Date? = null,
) {
    val normalizedViewCount: Int = viewCount.coerceAtLeast(1)
    val rewatchBadgeText: String? get() = normalizedViewCount.takeIf { it > 1 }?.let { "x$it" }

    companion object {
        fun from(documentId: String, data: Map<String, Any?>): StoryViewer? {
            val userId = data["userId"] as? String ?: return null
            fun date(key: String) = (data[key] as? Timestamp)?.toDate() ?: data[key] as? Date
            val lastViewedAt = date("lastViewedAt")
            val timestamp = date("timestamp") ?: lastViewedAt ?: date("firstViewedAt") ?: return null
            return StoryViewer(
                id = documentId,
                userId = userId,
                username = data["username"] as? String,
                profileImagePath = data["profileImagePath"] as? String,
                timestamp = timestamp,
                viewCount = ((data["viewCount"] as? Number)?.toInt() ?: 1).coerceAtLeast(1),
                firstViewedAt = date("firstViewedAt"),
                lastViewedAt = lastViewedAt ?: timestamp,
            )
        }
    }
}

/** Equivalente Compose de la vista `StoryRing` de Swift. */
@Composable
fun StoryRing(
    hasStory: Boolean,
    hasUnseenStory: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val colors = when {
        hasUnseenStory -> listOf(Color(0xFFFF2D55), Color(0xFFFF9500), Color(0xFFFFCC00))
        hasStory -> listOf(Color.Gray.copy(alpha = 0.5f), Color.Gray.copy(alpha = 0.7f))
        else -> listOf(Color.Transparent, Color.Transparent)
    }
    Canvas(modifier.size(size)) {
        if (hasStory) {
            drawCircle(
                brush = Brush.linearGradient(colors),
                radius = minOf(this.size.width, this.size.height) / 2f - 1.25f,
                style = Stroke(width = 2.5f),
            )
        }
    }
}
