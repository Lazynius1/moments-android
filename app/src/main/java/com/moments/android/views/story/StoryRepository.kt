package com.moments.android.views.story

import android.util.Base64
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.storage.FirebaseStorage
import com.moments.android.models.AppUser
import com.moments.android.models.MediaItem
import com.moments.android.models.Point
import com.moments.android.models.Story
import com.moments.android.services.content.BackendStoryDocument
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.permanentlyDeleteRecentlyDeleted
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

/** Port de `StoryReplyData` (`Views/story/StoryRepository.swift`). */
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

/** Port 1:1 de `StoryRepository` (`Views/story/StoryRepository.swift`). */
class StoryRepository(
    private val firestoreService: FirestoreService = FirestoreService(),
) {
    /**
     * iOS: `where expirationDate > now` + `orderBy timestamp asc`.
     * Si falta el índice compuesto, fallback ordenando en cliente (mismo resultado).
     */
    suspend fun fetchActiveStories(userId: String): List<Story> {
        val collection = firestoreService.db.collection("users").document(userId).collection("stories")
        val now = Date()
        return try {
            collection
                .whereGreaterThan("expirationDate", now)
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .get().await().documents
                .mapNotNull { decodeStory(it.id, it.data) }
        } catch (_: Exception) {
            collection
                .whereGreaterThan("expirationDate", now)
                .get().await().documents
                .mapNotNull { decodeStory(it.id, it.data) }
                .sortedBy { it.timestamp }
        }
    }

    suspend fun hasActiveStories(userId: String): Boolean =
        try {
            firestoreService.db.collection("users").document(userId).collection("stories")
                .whereGreaterThan("expirationDate", Date())
                .get().await().isEmpty.not()
        } catch (_: Exception) {
            false
        }

    suspend fun fetchStory(userId: String, storyId: String): Story {
        val snapshot = firestoreService.db.collection("users").document(userId)
            .collection("stories").document(storyId)
            .get().await()
        if (!snapshot.exists()) error("Story not found")
        return decodeStory(snapshot.id, snapshot.data) ?: error("Story not found")
    }

    suspend fun fetchStoryReplyData(userId: String, storyId: String): StoryReplyData? =
        runCatching { fetchStory(userId, storyId) }.getOrNull()?.let(StoryReplyData::from)

    fun observeReactions(
        userId: String,
        storyId: String,
        onChange: (List<StoryReaction>) -> Unit,
    ): ListenerRegistration =
        firestoreService.db.collection("users").document(userId).collection("stories").document(storyId)
            .collection("reactions")
            .orderBy("timestamp", Query.Direction.DESCENDING)
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
            .collection("viewers")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get().await().documents
            .mapNotNull { StoryViewer.from(it.id, it.data ?: emptyMap()) }

    suspend fun markStoryAsViewed(authorId: String, storyId: String, viewer: AppUser) {
        val viewerRef = firestoreService.db.collection("users").document(authorId)
            .collection("stories").document(storyId)
            .collection("viewers").document(viewer.id)
        firestoreService.db.runTransaction { transaction ->
            val snapshot = transaction.get(viewerRef)
            val now = Timestamp.now()
            val existing = snapshot.data ?: emptyMap()
            val existingCount = (existing["viewCount"] as? Number)?.toInt() ?: 1
            val firstViewedAt = existing["firstViewedAt"] as? Timestamp
                ?: existing["timestamp"] as? Timestamp
                ?: now
            transaction.set(
                viewerRef,
                mapOf(
                    "userId" to viewer.id,
                    "username" to viewer.username,
                    "profileImagePath" to (viewer.profileImagePath ?: ""),
                    "timestamp" to now,
                    "firstViewedAt" to firstViewedAt,
                    "lastViewedAt" to now,
                    "viewCount" to if (snapshot.exists()) existingCount + 1 else 1,
                ),
                SetOptions.merge(),
            )
            null
        }.await()
    }

    suspend fun addReaction(userId: String, storyId: String, currentUserId: String, reaction: String) {
        val db = firestoreService.db
        val reactionsCollection = db.collection("users").document(userId)
            .collection("stories").document(storyId)
            .collection("reactions")
        val canonicalRef = reactionsCollection.document(currentUserId)
        // iOS: Timestamp() (cliente), no serverTimestamp
        val reactionData = mapOf(
            "userId" to currentUserId,
            "reaction" to reaction,
            "timestamp" to Timestamp.now(),
        )
        val snapshot = reactionsCollection.whereEqualTo("userId", currentUserId).get().await()
        val batch = db.batch()
        batch.set(canonicalRef, reactionData, SetOptions.merge())
        for (doc in snapshot.documents) {
            if (doc.id != currentUserId) batch.delete(doc.reference)
        }
        batch.commit().await()
    }

    suspend fun softDeleteStory(userId: String, storyId: String) {
        val storyRef = firestoreService.db.collection("users").document(userId)
            .collection("stories").document(storyId)
        val recentlyDeletedRef = firestoreService.db.collection("users").document(userId)
            .collection("recentlyDeleted").document(storyId)
        val data = (storyRef.get().await().data ?: error("Story not found")).toMutableMap()
        data["deletedAt"] = FieldValue.serverTimestamp()
        data["type"] = "story"
        recentlyDeletedRef.set(data).await()
        storyRef.delete().await()
    }

    suspend fun permanentlyDeleteStory(userId: String, storyId: String) {
        require(FirebaseAuth.getInstance().currentUser?.uid == userId) { "Not authenticated" }
        firestoreService.permanentlyDeleteRecentlyDeleted(listOf(storyId))
    }

    suspend fun restoreStory(userId: String, storyId: String) {
        val storyRef = firestoreService.db.collection("users").document(userId)
            .collection("stories").document(storyId)
        val recentlyDeletedRef = firestoreService.db.collection("users").document(userId)
            .collection("recentlyDeleted").document(storyId)
        val data = (recentlyDeletedRef.get().await().data ?: error("Document not found")).toMutableMap()
        data.remove("deletedAt")
        data.remove("type")
        storyRef.set(data).await()
        recentlyDeletedRef.delete().await()
    }

    /**
     * ≡ iOS `deleteMediaFromStorage` (privado; no llamado hoy en Swift, portado por paridad).
     */
    @Suppress("unused")
    private suspend fun deleteMediaFromStorage(mediaUrl: String) {
        val url = runCatching { java.net.URI(mediaUrl).toURL() }.getOrNull()
            ?: error("Invalid media URL")
        val storagePath = extractStoragePath(url.toString(), url.path)
        FirebaseStorage.getInstance().reference.child(storagePath).delete().await()
    }

    /** ≡ iOS `extractStoragePath(from:)`. */
    private fun extractStoragePath(fullUrl: String, path: String): String {
        if (path.contains("/o/")) {
            val components = path.split("/o/")
            if (components.size > 1) {
                val encodedPath = components[1].substringBefore("?")
                return java.net.URLDecoder.decode(encodedPath, Charsets.UTF_8.name())
            }
        }
        return fullUrl.substringAfterLast('/').substringBefore('?')
    }

    companion object {
        private const val TAG = "Story"

        /**
         * ≡ `StoryRepository.decodeBackendStory` — timestamps backend en epoch millis.
         */
        fun decodeBackendStory(document: BackendStoryDocument): Story? {
            val backendMedia = document.mediaItem
            val mediaItem: MediaItem = when {
                backendMedia != null &&
                    backendMedia.url.isNotEmpty() &&
                    MediaItem.MediaType.entries.any { it.raw == backendMedia.type } -> {
                    val type = MediaItem.MediaType.entries.first { it.raw == backendMedia.type }
                    MediaItem(type = type, url = backendMedia.url)
                }
                !document.imagePath.isNullOrEmpty() ->
                    MediaItem(type = MediaItem.MediaType.IMAGE, url = document.imagePath)
                !document.videoUrl.isNullOrEmpty() ->
                    MediaItem(type = MediaItem.MediaType.VIDEO, url = document.videoUrl)
                else -> {
                    Log.d("Moments", "[$TAG] ❌ StoryRepository: backend story ${document.id} missing media")
                    return null
                }
            }

            val nowMillis = System.currentTimeMillis().toDouble()
            val timestampMillis = document.timestamp ?: nowMillis
            val timestamp = Date(timestampMillis.toLong())

            val resolvedExpirationHours = document.expirationHours
                ?: if (document.chainId != null) 48 else 24
            val fallbackExpirationMillis =
                timestamp.time + TimeUnit.HOURS.toMillis(resolvedExpirationHours.toLong())
            val expirationDate = Date(
                (document.expirationDate ?: fallbackExpirationMillis.toDouble()).toLong(),
            )

            val textPosition = if (document.textPositionX != null && document.textPositionY != null) {
                Point(document.textPositionX, document.textPositionY)
            } else {
                null
            }

            val drawingData: ByteArray? = document.drawingData?.let { encoded ->
                runCatching { Base64.decode(encoded, Base64.DEFAULT) }.getOrNull()
            }

            return Story(
                id = document.id,
                authorId = document.authorId,
                username = document.username ?: "",
                mediaItem = mediaItem,
                duration = document.duration ?: 5.0,
                timestamp = timestamp,
                expirationHours = resolvedExpirationHours,
                expirationDate = expirationDate,
                profileImagePath = document.profileImagePath,
                audience = document.audience,
                customListId = document.customListId,
                text = document.text,
                textPosition = textPosition,
                textStyle = document.textStyle,
                textPositionNormX = document.textPositionNormX,
                textPositionNormY = document.textPositionNormY,
                textColorHex = document.textColorHex,
                textFontSize = document.textFontSize,
                textAlignment = document.textAlignment,
                textBackgroundFill = document.textBackgroundFill,
                textStroke = document.textStroke,
                textVisualEffect = document.textVisualEffect,
                textMotion = document.textMotion,
                forcesAllCaps = document.forcesAllCaps,
                textLayerOrder = document.textLayerOrder,
                textOverlayLive = document.textOverlayLive,
                textOverlays = document.textOverlays,
                stickers = document.stickers,
                drawingData = drawingData,
                aspectRatio = document.aspectRatio,
                backgroundFrameURL = document.backgroundFrameURL,
                backgroundBlurredFrameURL = document.backgroundBlurredFrameURL,
                chainId = document.chainId,
                chainPosition = document.chainPosition,
                chainTitle = document.chainTitle,
            )
        }

        /** ≡ `decodeStory` / `decodeStoryData` vía `Story.from` (Firestore decoder iOS). */
        private fun decodeStory(documentId: String, data: Map<String, Any?>?): Story? {
            if (data == null) return null
            return Story.from(documentId, data)
        }
    }

    private fun decodeStory(documentId: String, data: Map<String, Any?>?): Story? =
        Companion.decodeStory(documentId, data)
}
