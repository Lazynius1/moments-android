package com.moments.android.services.social

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.moments.android.R
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/** Port de StoryChainLimits (constantes) en StoryChainLimitsService.swift. */
object StoryChainLimits {
    const val MAX_PARTS = 10
    const val EXPIRATION_HOURS = 48
    val MIN_TIME_BETWEEN_PARTS_MS: Long = TimeUnit.MINUTES.toMillis(5)
    const val MAX_CHAIN_TITLE_LENGTH = 50
}

sealed class StoryChainLimitError(message: String) : Exception(message) {
    data object MaxPartsReached : StoryChainLimitError("storyChains.error.maxPartsReached")
    data object ChainExpired : StoryChainLimitError("storyChains.error.chainExpired")
    data object TooSoonBetweenParts : StoryChainLimitError("storyChains.error.tooSoonBetweenParts")
    data object ChainNotFound : StoryChainLimitError("storyChains.error.chainNotFound")
    data object UserNotAuthorized : StoryChainLimitError("storyChains.error.userNotAuthorized")
    data object InvalidChainData : StoryChainLimitError("storyChains.error.invalidChainData")
}

data class ChainStats(
    val partCount: Int,
    val remainingTimeSeconds: Double,
    val isExpired: Boolean,
)

/** Port de StoryChainLimitsService.swift. */
object StoryChainLimitsService {
    private val firestore: FirebaseFirestore get() = FirebaseFirestore.getInstance()

    suspend fun canContinueChain(chainId: String, userId: String): Boolean {
        // Verify the lifetime against part 1, not potentially stale metadata.
        if (getRemainingTimeSeconds(chainId) <= 0.0) throw StoryChainLimitError.ChainExpired

        val storiesSnapshot = firestore.collectionGroup("stories")
            .whereEqualTo("chainId", chainId)
            .whereEqualTo("audience", "everyone")
            .orderBy("chainPosition", Query.Direction.ASCENDING)
            .limit(StoryChainLimits.MAX_PARTS.toLong())
            .get()
            .await()
        if (storiesSnapshot.size() >= StoryChainLimits.MAX_PARTS) {
            throw StoryChainLimitError.MaxPartsReached
        }

        val userStories = firestore.collectionGroup("stories")
            .whereEqualTo("chainId", chainId)
            .whereEqualTo("audience", "everyone")
            .whereEqualTo("authorId", userId)
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()

        val lastTs = userStories.documents.firstOrNull()?.getTimestamp("timestamp")?.toDate()
        if (lastTs != null) {
            val elapsed = Date().time - lastTs.time
            if (elapsed < StoryChainLimits.MIN_TIME_BETWEEN_PARTS_MS) {
                throw StoryChainLimitError.TooSoonBetweenParts
            }
        }
        return true
    }

    suspend fun getNextChainPosition(chainId: String): Int {
        val storiesSnapshot = firestore.collectionGroup("stories")
            .whereEqualTo("chainId", chainId)
            .whereEqualTo("audience", "everyone")
            .orderBy("chainPosition", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .await()
        val lastPosition = storiesSnapshot.documents.firstOrNull()?.getLong("chainPosition")?.toInt()
        return if (lastPosition != null) lastPosition + 1 else 1
    }

    fun validateChainTitle(title: String) {
        val trimmed = title.trim()
        if (trimmed.isEmpty() || trimmed.length > StoryChainLimits.MAX_CHAIN_TITLE_LENGTH) {
            throw StoryChainLimitError.InvalidChainData
        }
    }

    suspend fun getRemainingTimeSeconds(chainId: String): Double {
        // The first part is authoritative. Global metadata remains a fallback
        // for legacy chains that predate public story-chain reads.
        val firstPart = firestore.collectionGroup("stories")
            .whereEqualTo("chainId", chainId)
            .whereEqualTo("audience", "everyone")
            .orderBy("chainPosition", Query.Direction.ASCENDING)
            .limit(1)
            .get().await()
        val createdAt = firstPart.documents.firstOrNull()?.getTimestamp("timestamp")?.toDate()
            ?: run {
                val chainDoc = firestore.collection("storyChains").document(chainId).get().await()
                if (!chainDoc.exists()) throw StoryChainLimitError.ChainNotFound
                chainDoc.getTimestamp("createdAt")?.toDate()
                    ?: throw StoryChainLimitError.ChainNotFound
            }
        val expiration = Calendar.getInstance().apply {
            time = createdAt
            add(Calendar.HOUR_OF_DAY, StoryChainLimits.EXPIRATION_HOURS)
        }.time
        return maxOf(0.0, (expiration.time - Date().time) / 1000.0)
    }

    suspend fun getChainStats(chainId: String): ChainStats {
        val partCount = firestore.collectionGroup("stories")
            .whereEqualTo("chainId", chainId)
            .whereEqualTo("audience", "everyone")
            .orderBy("chainPosition", Query.Direction.ASCENDING)
            .limit(StoryChainLimits.MAX_PARTS.toLong())
            .get()
            .await()
            .size()
        val remaining = getRemainingTimeSeconds(chainId)
        return ChainStats(partCount, remaining, remaining <= 0)
    }

    suspend fun cleanupExpiredChains() {
        val expiredDate = Calendar.getInstance().apply {
            add(Calendar.HOUR_OF_DAY, -StoryChainLimits.EXPIRATION_HOURS)
        }.time
        val expired = firestore.collection("storyChains")
            .whereLessThan("createdAt", Timestamp(expiredDate))
            .get()
            .await()
        val batch = firestore.batch()
        for (doc in expired.documents) {
            batch.update(
                doc.reference,
                mapOf(
                    "isExpired" to true,
                    "expiredAt" to Timestamp.now(),
                ),
            )
        }
        batch.commit().await()
    }
}

fun Double.formattedRemainingTime(context: Context): String {
    val hours = toInt() / 3600
    val minutes = (toInt() % 3600) / 60
    return when {
        hours > 0 -> context.getString(R.string.story_chains_time_remaining_hm, hours, minutes)
        minutes > 0 -> context.getString(R.string.story_chains_time_remaining_m, minutes)
        else -> context.getString(R.string.story_chains_time_expired)
    }
}

/** ≡ `StoryChainLimitError.errorDescription` en Swift. */
fun StoryChainLimitError.localizedMessage(context: Context): String = when (this) {
    StoryChainLimitError.MaxPartsReached ->
        context.getString(R.string.story_chains_error_max_parts, StoryChainLimits.MAX_PARTS)
    StoryChainLimitError.ChainExpired ->
        context.getString(R.string.story_chains_error_expired)
    StoryChainLimitError.TooSoonBetweenParts ->
        context.getString(
            R.string.story_chains_error_too_soon,
            (StoryChainLimits.MIN_TIME_BETWEEN_PARTS_MS / 60_000L).toInt(),
        )
    StoryChainLimitError.ChainNotFound ->
        context.getString(R.string.story_chains_error_not_found)
    StoryChainLimitError.UserNotAuthorized ->
        context.getString(R.string.story_chains_error_user_not_authorized)
    StoryChainLimitError.InvalidChainData ->
        context.getString(R.string.story_chains_error_invalid_data)
}
