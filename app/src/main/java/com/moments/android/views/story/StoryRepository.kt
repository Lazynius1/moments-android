package com.moments.android.views.story

import com.google.firebase.firestore.Query
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.permanentlyDeleteRecentlyDeleted
import kotlinx.coroutines.tasks.await

/** Port de `StoryReplyData` en `Views/story/StoryRepository.swift`. */
data class StoryReplyData(
    val storyId: String,
    val mediaUrl: String,
    val mediaType: String,
    val authorId: String,
    val expirationTimestamp: Double,
    val previewUrl: String,
) {
    val payload: Map<String, String>
        get() = mapOf(
            "storyId" to storyId,
            "storyMediaUrl" to mediaUrl,
            "storyMediaType" to mediaType,
            "storyAuthorId" to authorId,
            "storyExpiration" to expirationTimestamp.toString(),
            "storyPreviewUrl" to previewUrl,
        )

    companion object {
        fun from(story: Story): StoryReplyData? {
            val storyId = story.id ?: return null
            val preview = story.backgroundFrameURL?.trim()?.takeIf { it.isNotEmpty() }
                ?: story.backgroundBlurredFrameURL?.trim()?.takeIf { it.isNotEmpty() }
                ?: story.mediaItem.url
            return StoryReplyData(
                storyId = storyId,
                mediaUrl = story.mediaItem.url,
                mediaType = if (story.mediaItem.type == MediaItem.MediaType.VIDEO) "video" else "image",
                authorId = story.authorId,
                expirationTimestamp = story.expirationDate.time / 1000.0,
                previewUrl = preview,
            )
        }
    }
}

/** Port progresivo de `StoryRepository`: lecturas principales y payload de reply. */
class StoryRepository(
    private val firestoreService: FirestoreService = FirestoreService(),
) {
    // Como en iOS: sin `orderBy` en servidor (evita el índice compuesto), se ordena en cliente.
    suspend fun fetchActiveStories(userId: String): List<Story> =
        firestoreService.db.collection("users").document(userId).collection("stories")
            .whereGreaterThan("expirationDate", java.util.Date())
            .get().await().documents
            .mapNotNull { Story.from(it.id, it.data ?: emptyMap()) }
            .sortedBy { it.timestamp }

    suspend fun hasActiveStories(userId: String): Boolean =
        firestoreService.db.collection("users").document(userId).collection("stories")
            .whereGreaterThan("expirationDate", java.util.Date())
            .limit(1)
            .get().await().isEmpty.not()

    suspend fun fetchStory(userId: String, storyId: String): Story =
        firestoreService.db.collection("users").document(userId).collection("stories").document(storyId)
            .get().await().let { document ->
                Story.from(document.id, document.data ?: emptyMap()) ?: error("Story not found")
            }

    suspend fun fetchStoryReplyData(userId: String, storyId: String): StoryReplyData? =
        runCatching { fetchStory(userId, storyId) }.getOrNull()?.let(StoryReplyData::from)

    fun observeReactions(userId: String, storyId: String, onChange: (List<StoryReaction>) -> Unit): ListenerRegistration =
        firestoreService.db.collection("users").document(userId).collection("stories").document(storyId)
            .collection("reactions").orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val reactions = snapshot?.documents.orEmpty().mapNotNull { document ->
                    val data = document.data ?: return@mapNotNull null
                    val reactionUserId = data["userId"] as? String ?: return@mapNotNull null
                    val reaction = data["reaction"] as? String ?: return@mapNotNull null
                    val timestamp = (data["timestamp"] as? Timestamp)?.toDate() ?: return@mapNotNull null
                    StoryReaction(document.id, reactionUserId, reaction, timestamp)
                }
                onChange(reactions.latestPerUser())
            }

    suspend fun fetchViewers(userId: String, storyId: String): List<StoryViewer> =
        firestoreService.db.collection("users").document(userId).collection("stories").document(storyId)
            .collection("viewers").orderBy("timestamp", Query.Direction.DESCENDING)
            .get().await().documents.mapNotNull { StoryViewer.from(it.id, it.data ?: emptyMap()) }

    suspend fun markStoryAsViewed(authorId: String, storyId: String, viewer: AppUser) {
        val reference = firestoreService.db.collection("users").document(authorId).collection("stories").document(storyId)
            .collection("viewers").document(viewer.id)
        firestoreService.db.runTransaction { transaction ->
            val snapshot = transaction.get(reference)
            val now = Timestamp.now()
            val existing = snapshot.data ?: emptyMap()
            val count = (existing["viewCount"] as? Number)?.toInt() ?: 1
            val firstViewedAt = existing["firstViewedAt"] as? Timestamp
                ?: existing["timestamp"] as? Timestamp
                ?: now
            transaction.set(reference, mapOf(
                "userId" to viewer.id,
                "username" to viewer.username,
                "profileImagePath" to (viewer.profileImagePath ?: ""),
                "timestamp" to now,
                "firstViewedAt" to firstViewedAt,
                "lastViewedAt" to now,
                "viewCount" to if (snapshot.exists()) count + 1 else 1,
            ), com.google.firebase.firestore.SetOptions.merge())
            null
        }.await()
    }

    suspend fun addReaction(userId: String, storyId: String, currentUserId: String, reaction: String) {
        val collection = firestoreService.db.collection("users").document(userId).collection("stories").document(storyId)
            .collection("reactions")
        val existing = collection.whereEqualTo("userId", currentUserId).get().await()
        val batch = firestoreService.db.batch()
        batch.set(collection.document(currentUserId), mapOf(
            "userId" to currentUserId,
            "reaction" to reaction,
            "timestamp" to FieldValue.serverTimestamp(),
        ), com.google.firebase.firestore.SetOptions.merge())
        existing.documents.filter { it.id != currentUserId }.forEach { batch.delete(it.reference) }
        batch.commit().await()
    }

    suspend fun softDeleteStory(userId: String, storyId: String) {
        val story = firestoreService.db.collection("users").document(userId).collection("stories").document(storyId)
        val recentlyDeleted = firestoreService.db.collection("users").document(userId).collection("recentlyDeleted").document(storyId)
        val data = (story.get().await().data ?: error("Story not found")).toMutableMap()
        data["deletedAt"] = FieldValue.serverTimestamp()
        data["type"] = "story"
        recentlyDeleted.set(data).await()
        story.delete().await()
    }

    suspend fun permanentlyDeleteStory(userId: String, storyId: String) {
        require(FirebaseAuth.getInstance().currentUser?.uid == userId) { "Not authenticated" }
        firestoreService.permanentlyDeleteRecentlyDeleted(listOf(storyId))
    }

    suspend fun restoreStory(userId: String, storyId: String) {
        val story = firestoreService.db.collection("users").document(userId).collection("stories").document(storyId)
        val recentlyDeleted = firestoreService.db.collection("users").document(userId).collection("recentlyDeleted").document(storyId)
        val data = (recentlyDeleted.get().await().data ?: error("Document not found")).toMutableMap()
        data.remove("deletedAt")
        data.remove("type")
        story.set(data).await()
        recentlyDeleted.delete().await()
    }
}
