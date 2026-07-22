package com.moments.android.services.firestore

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.moments.android.models.MediaItem
import com.moments.android.services.network.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

/** Resumen ligero de historias activas de un autor (FirestoreCore.swift). */
data class StoryAuthorSummary(
    val activeStoryCount: Int,
    val latestStoryAt: Date?,
    val latestExpirationAt: Date?,
    val audiencesSummary: Map<String, Int>,
    val updatedAt: Date?,
) {
    fun shouldSkipDetailedFetch(maxStalenessSeconds: Long = 300): Boolean {
        latestExpirationAt?.let { if (!it.after(Date())) return true }
        if (activeStoryCount > 0) return false
        val updated = updatedAt ?: return false
        return (Date().time - updated.time) / 1000 <= maxStalenessSeconds
    }
}

enum class PublicProfileAvailability {
    AVAILABLE, UNAVAILABLE;

    companion object {
        fun fromUserData(data: Map<String, Any?>): PublicProfileAvailability {
            val isActive = data["isActive"] as? Boolean ?: true
            if (!isActive) return UNAVAILABLE
            val isSuspended = data["isSuspended"] as? Boolean ?: false
            if (!isSuspended) return AVAILABLE
            val suspendedUntil = (data["suspendedUntil"] as? Timestamp)?.toDate()
            return if (suspendedUntil != null && Date().after(suspendedUntil)) AVAILABLE else UNAVAILABLE
        }
    }
}

/** Encola acción en outbox cuando no hay red (paridad iOS: solo isConnected, no slow-cellular). */
internal fun shouldQueueFirestoreOutbox(): Boolean = !NetworkMonitor.isConnected

/** Helpers compartidos de FirestoreCore.swift. */
suspend fun FirestoreService.updateUserActivityMetadata(userId: String, fields: Map<String, Any>) {
    if (fields.isEmpty()) return
    db.collection("users").document(userId).update(fields).await()
}

fun FirestoreService.calculateStoryExpirationDate(
    isChain: Boolean = false,
    chainId: String? = null,
    expirationHours: Int? = null,
): Date {
    if (isChain && chainId != null) return calculateChainExpirationDate(chainId)
    val resolvedHours = if (expirationHours == 48) 48 else 24
    return Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, resolvedHours) }.time
}

fun FirestoreService.calculateChainExpirationDate(chainId: String): Date {
    val currentExpiration = Calendar.getInstance().apply { add(Calendar.HOUR_OF_DAY, 48) }.time
    firestoreScope.launch(Dispatchers.IO) {
        runCatching { updateChainExpirationInBackground(chainId) }
    }
    return currentExpiration
}

suspend fun FirestoreService.updateChainExpirationInBackground(chainId: String) {
    val chainDoc = db.collection("storyChains").document(chainId).get().await()
    @Suppress("UNCHECKED_CAST")
    val data = chainDoc.data as? Map<String, Any?> ?: return
    val createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: return
    val correctExpiration = Calendar.getInstance().apply {
        time = createdAt
        add(Calendar.HOUR_OF_DAY, 48)
    }.time

    val storiesSnapshot = db.collectionGroup("stories")
        .whereEqualTo("chainId", chainId)
        .get()
        .await()

    val batch = db.batch()
    val affectedUserIds = mutableSetOf<String>()
    for (doc in storiesSnapshot.documents) {
        batch.update(doc.reference, "expirationDate", Timestamp(correctExpiration))
        doc.reference.parent.parent?.id?.takeIf { it.isNotEmpty() }?.let { affectedUserIds.add(it) }
    }
    batch.commit().await()
    for (userId in affectedUserIds) {
        runCatching { rebuildStorySummary(userId) }
    }
}

fun FirestoreService.serializedMediaItems(mediaItems: List<MediaItem>): List<Map<String, Any?>> =
    mediaItems.map { item ->
        buildMap<String, Any?> {
            put("id", item.id)
            put("type", item.type.raw)
            put("url", item.url)
            item.aspectRatio?.let { put("aspectRatio", it) }
            item.thumbnailUrl?.let { put("thumbnailUrl", it) }
            item.videoDuration?.let { put("videoDuration", it) }
            item.videoFileSize?.let { put("videoFileSize", it) }
            item.videoResolution?.let { put("videoResolution", it) }
            item.videoProcessingStatus?.raw?.let { put("videoProcessingStatus", it) }
            item.originalVideoUrl?.let { put("originalVideoUrl", it) }
            item.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
                put("tags", tags.map { tag ->
                    mapOf(
                        "id" to tag.id,
                        "userId" to tag.userId,
                        "username" to tag.username,
                        "x" to tag.x,
                        "y" to tag.y,
                    )
                })
            }
            item.moderationState?.raw?.let { put("moderationState", it) }
            item.moderationReason?.let { put("moderationReason", it) }
            item.moderationCategory?.let { put("moderationCategory", it) }
            item.moderationConfidence?.let { put("moderationConfidence", it) }
            item.moderatedAt?.let { put("moderatedAt", Timestamp(it)) }
        }
    }

internal fun legacyStorySummaryCleanupPayload(audienceKeys: List<String>): Map<String, Any> {
    val defaultAudienceKeys = listOf("everyone", "mutuals", "bestFriends", "custom", "customList", "onlyMe")
    val keys = (defaultAudienceKeys + audienceKeys).distinct()
    val payload = mutableMapOf<String, Any>(
        "storySummary.activeStoryCount" to FieldValue.delete(),
        "storySummary.latestStoryAt" to FieldValue.delete(),
        "storySummary.latestExpirationAt" to FieldValue.delete(),
        "storySummary.updatedAt" to FieldValue.delete(),
        "storySummary.audiencesSummary" to FieldValue.delete(),
    )
    for (key in keys) {
        payload["storySummary.audiencesSummary.$key"] = FieldValue.delete()
    }
    return payload
}

internal fun parseStorySummary(userData: Map<String, Any?>): StoryAuthorSummary? {
    @Suppress("UNCHECKED_CAST")
    val summary = userData["storySummary"] as? Map<String, Any?> ?: return null
    val activeCount = (summary["activeStoryCount"] as? Number)?.toInt() ?: 0
    val latestStoryAt = (summary["latestStoryAt"] as? Timestamp)?.toDate()
    val latestExpirationAt = (summary["latestExpirationAt"] as? Timestamp)?.toDate()
    val updatedAt = (summary["updatedAt"] as? Timestamp)?.toDate()
    @Suppress("UNCHECKED_CAST")
    val rawAudiences = summary["audiencesSummary"] as? Map<String, Any?>
    val audiencesSummary = rawAudiences?.mapNotNull { (key, value) ->
        (value as? Number)?.toInt()?.let { key to it }
    }?.toMap() ?: emptyMap()
    return StoryAuthorSummary(activeCount, latestStoryAt, latestExpirationAt, audiencesSummary, updatedAt)
}

internal fun normalizedStorySummary(summary: StoryAuthorSummary): StoryAuthorSummary {
    val latestExpirationAt = summary.latestExpirationAt
    if (latestExpirationAt == null || latestExpirationAt.after(Date())) {
        if (summary.latestExpirationAt == null &&
            summary.activeStoryCount > 0 &&
            summary.latestStoryAt != null &&
            summary.latestStoryAt.time <= System.currentTimeMillis() - 52 * 60 * 60 * 1000
        ) {
            return summary.copy(
                activeStoryCount = 0,
                latestStoryAt = null,
                latestExpirationAt = summary.latestStoryAt,
                audiencesSummary = emptyMap(),
            )
        }
        return summary
    }
    return summary.copy(
        activeStoryCount = 0,
        latestStoryAt = null,
        audiencesSummary = emptyMap(),
    )
}
