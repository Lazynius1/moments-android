package com.moments.android.views.story

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.views.components.VerifiedBadge
import kotlinx.coroutines.tasks.await
import java.util.Date
import kotlin.math.max

/** Port de `StoryReaction` (`Views/story/StoryModels.swift`). */
data class StoryReaction(
    val id: String,
    val userId: String,
    val reaction: String,
    val timestamp: Date,
)

/**
 * Una reacción por persona: la más reciente sobrescribe las anteriores.
 * ≡ `Array where Element == StoryReaction.latestPerUser()`.
 */
fun List<StoryReaction>.latestPerUser(): List<StoryReaction> {
    val latestByUser = linkedMapOf<String, StoryReaction>()
    for (reaction in this) {
        val existing = latestByUser[reaction.userId]
        if (existing == null || reaction.timestamp.after(existing.timestamp)) {
            latestByUser[reaction.userId] = reaction
        }
    }
    return latestByUser.values.sortedByDescending { it.timestamp.time }
}

/** Port de `StoryViewer`. */
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
    val rewatchBadgeText: String?
        get() {
            val count = max(viewCount, 1)
            return if (count > 1) "x$count" else null
        }

    companion object {
        fun from(documentId: String, data: Map<String, Any?>): StoryViewer? {
            val userId = data["userId"] as? String ?: return null

            fun timestampDate(key: String): Date? =
                (data[key] as? Timestamp)?.toDate()

            val lastViewedAt = timestampDate("lastViewedAt")
            val timestamp = timestampDate("timestamp")
                ?: lastViewedAt
                ?: timestampDate("firstViewedAt")
                ?: return null

            val firstViewedAt = timestampDate("firstViewedAt")
            val rawViewCount = (data["viewCount"] as? Number)?.toInt() ?: 1

            return StoryViewer(
                id = documentId,
                userId = userId,
                username = data["username"] as? String,
                profileImagePath = data["profileImagePath"] as? String,
                timestamp = timestamp,
                viewCount = max(rawViewCount, 1),
                firstViewedAt = firstViewedAt,
                lastViewedAt = lastViewedAt ?: timestamp,
            )
        }
    }
}

/** Port de `StoryRing` (anillo simple pink/orange/yellow). */
@Composable
fun StoryRing(
    hasStory: Boolean,
    hasUnseenStory: Boolean,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val gradientColors = when {
        hasUnseenStory -> listOf(
            Color(0xFFFF2D55), // .pink
            Color(0xFFFF9500), // .orange
            Color(0xFFFFCC00), // .yellow
        )
        hasStory -> listOf(
            Color.Gray.copy(alpha = 0.5f),
            Color.Gray.copy(alpha = 0.7f),
        )
        else -> listOf(Color.Transparent)
    }

    Box(
        modifier
            .size(size)
            .graphicsLayer { alpha = if (hasStory) 1f else 0.3f }
            .border(
                width = if (hasStory) 2.5.dp else 0.dp,
                brush = Brush.linearGradient(gradientColors),
                shape = CircleShape,
            ),
    )
}

/**
 * Port de `VerifiedBadgeView` (StoryModels.swift).
 * Carga `users/{userId}.isVerified` vía Firestore (como iOS).
 */
@Composable
fun VerifiedBadgeView(
    userId: String,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var isVerified by remember(userId) { mutableStateOf(false) }
    var isLoading by remember(userId) { mutableStateOf(true) }

    LaunchedEffect(userId) {
        isLoading = true
        isVerified = false
        if (userId.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val snap = FirebaseFirestore.getInstance()
                .collection("users")
                .document(userId)
                .get()
                .await()
            isVerified = snap.getBoolean("isVerified") == true
        } catch (_: Exception) {
            isVerified = false
        }
        isLoading = false
    }

    when {
        isLoading -> Box(modifier.size(size)) // Color.clear placeholder
        isVerified -> VerifiedBadge(size = size, modifier = modifier)
        else -> Box(modifier.size(size))
    }
}

/** Port de `CurrentUserVerifiedBadge` (StoryModels.swift). */
@Composable
fun CurrentUserVerifiedBadge(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    var isVerified by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId.isNullOrEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        try {
            val snap = FirebaseFirestore.getInstance()
                .collection("users")
                .document(currentUserId)
                .get()
                .await()
            isVerified = snap.getBoolean("isVerified") == true
        } catch (_: Exception) {
            isVerified = false
        }
        isLoading = false
    }

    when {
        isLoading -> Box(modifier.size(size))
        isVerified -> VerifiedBadge(size = size, modifier = modifier)
        else -> Box(modifier.size(size))
    }
}
