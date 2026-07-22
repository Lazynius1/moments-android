package com.moments.android.services.firestore

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.moments.android.models.MapVisibilityPolicy
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.models.MomentGridPreviewSettings
import com.moments.android.models.SavePayload
import com.moments.android.models.cache.CachedAction
import com.moments.android.models.encode
import com.moments.android.models.toMap
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.privacy.ContentVisibilityService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Date
import java.util.UUID

/** Port de FirestoreMomentsRepository.swift. */
suspend fun FirestoreService.updateMoment(userId: String, momentId: String, content: String) {
    db.collection("users").document(userId).collection("moments").document(momentId)
        .update(mapOf("content" to content, "updatedAt" to FieldValue.serverTimestamp())).await()
}

suspend fun FirestoreService.deleteMoment(userId: String, momentId: String) {
    val momentRef = db.collection("users").document(userId).collection("moments").document(momentId)
    val recentlyDeletedRef = db.collection("users").document(userId)
        .collection("recentlyDeleted").document(momentId)
    val snap = momentRef.get().await()
    @Suppress("UNCHECKED_CAST")
    val data = (snap.data as? Map<String, Any?>)?.toMutableMap() ?: error("Moment not found")
    data["deletedAt"] = FieldValue.serverTimestamp()
    data["type"] = "moment"
    recentlyDeletedRef.set(data).await()
    momentRef.delete().await()
}

suspend fun FirestoreService.permanentlyDeleteRecentlyDeleted(ids: List<String>) {
    val cleanIds = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    if (cleanIds.isEmpty()) return
    val currentUser = FirebaseAuth.getInstance().currentUser ?: error("Not authenticated")
    val projectId = FirebaseApp.getInstance().options.projectId ?: error("Missing project ID")
    val token = currentUser.getIdToken(false).await().token ?: error("No token")
    val url = URL("https://europe-southwest1-$projectId.cloudfunctions.net/permanentlyDeleteRecentlyDeletedBatch")
    val connection = (url.openConnection() as HttpURLConnection).apply {
        requestMethod = "POST"
        connectTimeout = 60_000
        readTimeout = 60_000
        setRequestProperty("Content-Type", "application/json")
        setRequestProperty("Authorization", "Bearer $token")
        doOutput = true
    }
    try {
        connection.outputStream.use {
            it.write(JSONObject().put("ids", cleanIds).toString().toByteArray())
        }
        if (connection.responseCode !in 200..299) {
            error("Backend error ${connection.responseCode}")
        }
    } finally {
        connection.disconnect()
    }
}

suspend fun FirestoreService.permanentlyDeleteMoment(momentId: String, userId: String) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
    require(currentUserId == userId) { "Not authenticated" }
    permanentlyDeleteRecentlyDeleted(listOf(momentId))
}

suspend fun FirestoreService.restoreMoment(userId: String, momentId: String) {
    val momentRef = db.collection("users").document(userId).collection("moments").document(momentId)
    val recentlyDeletedRef = db.collection("users").document(userId)
        .collection("recentlyDeleted").document(momentId)
    val snap = recentlyDeletedRef.get().await()
    @Suppress("UNCHECKED_CAST")
    val data = (snap.data as? Map<String, Any?>)?.toMutableMap() ?: error("Document not found")
    data.remove("deletedAt")
    data.remove("type")
    momentRef.set(data).await()
    recentlyDeletedRef.delete().await()
}

suspend fun FirestoreService.archiveMoment(userId: String, momentId: String) {
    db.collection("users").document(userId).collection("moments").document(momentId)
        .update(mapOf("isArchived" to true, "archivedAt" to FieldValue.serverTimestamp())).await()
}

suspend fun FirestoreService.unarchiveMoment(userId: String, momentId: String) {
    db.collection("users").document(userId).collection("moments").document(momentId)
        .update(mapOf("isArchived" to FieldValue.delete(), "archivedAt" to FieldValue.delete())).await()
}

suspend fun FirestoreService.pinMoment(userId: String, momentId: String) {
    db.collection("users").document(userId).collection("moments").document(momentId)
        .update(mapOf("isPinned" to true, "pinnedAt" to FieldValue.serverTimestamp())).await()
}

suspend fun FirestoreService.unpinMoment(userId: String, momentId: String) {
    db.collection("users").document(userId).collection("moments").document(momentId)
        .update(mapOf("isPinned" to FieldValue.delete(), "pinnedAt" to FieldValue.delete())).await()
}

suspend fun FirestoreService.updateMomentGridPreview(
    userId: String,
    momentId: String,
    settings: MomentGridPreviewSettings,
) {
    val updateData = if (settings.isDefault) {
        mapOf(
            "gridPreviewScale" to FieldValue.delete(),
            "gridPreviewOffsetX" to FieldValue.delete(),
            "gridPreviewOffsetY" to FieldValue.delete(),
            "gridPreviewFitMode" to FieldValue.delete(),
            "gridPreviewBackground" to FieldValue.delete(),
        )
    } else {
        mapOf(
            "gridPreviewScale" to settings.scale,
            "gridPreviewOffsetX" to settings.offsetX,
            "gridPreviewOffsetY" to settings.offsetY,
            "gridPreviewFitMode" to settings.fitMode.raw,
            "gridPreviewBackground" to settings.background.raw,
        )
    }
    db.collection("users").document(userId).collection("moments").document(momentId)
        .update(updateData).await()
}

suspend fun FirestoreService.pinMomentReplacingOldestIfNeeded(
    userId: String,
    momentId: String,
    pinnedMoments: List<Moment>,
) {
    val currentlyPinned = pinnedMoments.filter { it.isPinned == true && it.id != momentId }
    if (currentlyPinned.size < 3) {
        pinMoment(userId, momentId)
        return
    }
    val oldest = currentlyPinned.minByOrNull {
        (it.pinnedAt ?: it.timestamp).time
    } ?: error("Unable to resolve oldest pinned moment")
    unpinMoment(userId, oldest.id ?: error("Missing moment id"))
    pinMoment(userId, momentId)
}

suspend fun FirestoreService.fetchArchivedMoments(userId: String): List<Moment> {
    val snap = db.collection("users").document(userId).collection("moments")
        .whereEqualTo("isArchived", true)
        .orderBy("archivedAt", Query.Direction.DESCENDING)
        .limit(100)
        .get().await()
    return snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { Moment.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
    }
}

suspend fun FirestoreService.deleteMomentComments(userId: String, momentId: String) {
    val commentsRef = db.collection("users").document(userId)
        .collection("moments").document(momentId).collection("comments")
    val snap = commentsRef.get().await()
    if (snap.isEmpty) return
    db.runBatch { batch ->
        snap.documents.forEach { batch.delete(it.reference) }
    }.await()
}

suspend fun FirestoreService.deleteMomentReactions(userId: String, momentId: String) {
    val reactionsRef = db.collection("users").document(userId)
        .collection("moments").document(momentId).collection("reactions")
    val snap = reactionsRef.get().await()
    if (snap.isEmpty) return
    db.runBatch { batch ->
        snap.documents.forEach { batch.delete(it.reference) }
    }.await()
}

suspend fun FirestoreService.loadSavedMoments(userId: String) {
    val snap = runCatching {
        db.collection("users").document(userId).collection("savedMoments").get().await()
    }.getOrNull()
    val momentIds = snap?.documents?.map { it.id } ?: emptyList()
    _savedMomentIds.value = momentIds
    _savedMomentsLoadedForUserId.value = userId
}

fun FirestoreService.hasLoadedSavedMoments(userId: String): Boolean =
    _savedMomentsLoadedForUserId.value == userId

suspend fun FirestoreService.checkIfSaved(userId: String, momentId: String): Boolean {
    val snap = db.collection("users").document(userId)
        .collection("savedMoments").document(momentId).get().await()
    return snap.exists()
}

suspend fun FirestoreService.toggleSaveMoment(userId: String, momentId: String) {
    if (shouldQueueFirestoreOutbox()) {
        val payload = SavePayload(userId, momentId)
        LocalPersistenceService.saveAction(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.SAVE.raw,
                payloadData = payload.encode(),
            ),
        )
        return
    }
    val savedMomentRef = db.collection("users").document(userId)
        .collection("savedMoments").document(momentId)
    db.runTransaction { transaction ->
        val snapshot = transaction.get(savedMomentRef)
        if (snapshot.exists()) {
            transaction.delete(savedMomentRef)
            _savedMomentIds.update { it.filterNot { id -> id == momentId } }
        } else {
            transaction.set(savedMomentRef, mapOf("momentId" to momentId, "timestamp" to Timestamp(Date())))
            _savedMomentIds.update { it + momentId }
        }
        null
    }.await()
}

suspend fun FirestoreService.createMoment(
    userId: String,
    content: String,
    mediaItems: List<MediaItem>,
    taggedUsers: List<String>? = null,
    mentionedUsers: List<String>? = null,
    location: String? = null,
    locationCoordinate: Moment.LocationCoordinate? = null,
    audience: String? = null,
    aspectRatio: String? = null,
    disableComments: Boolean = false,
    hideLikeCounts: Boolean = false,
    allowSharing: Boolean = true,
    scheduledDate: Date? = null,
) {
    createMomentDocument(
        userId, content, mediaItems, taggedUsers, mentionedUsers, location,
        locationCoordinate, audience, null, null, aspectRatio,
        disableComments, hideLikeCounts, allowSharing, scheduledDate, null,
    )
}

suspend fun FirestoreService.createMomentWithVisibility(
    userId: String,
    content: String,
    mediaItems: List<MediaItem>,
    audience: ContentAudience,
    customViewers: List<String>? = null,
    taggedUsers: List<String>? = null,
    mentionedUsers: List<String>? = null,
    location: String? = null,
    locationCoordinate: Moment.LocationCoordinate? = null,
    selectedListId: String? = null,
    aspectRatio: String? = null,
    disableComments: Boolean = false,
    hideLikeCounts: Boolean = false,
    allowSharing: Boolean = true,
    scheduledDate: Date? = null,
    momentId: String? = null,
): String {
    val resolvedMomentId = momentId ?: UUID.randomUUID().toString()
    if (audience == ContentAudience.CUSTOM && !customViewers.isNullOrEmpty()) {
        saveCustomAudienceForContent("moment", resolvedMomentId, userId, customViewers)
    }
    return createMomentDocument(
        userId, content, mediaItems, taggedUsers, mentionedUsers, location,
        locationCoordinate, audience.raw, selectedListId, customViewers, aspectRatio,
        disableComments, hideLikeCounts, allowSharing, scheduledDate, resolvedMomentId,
    )
}

suspend fun FirestoreService.createMomentWithCustomList(
    userId: String,
    content: String,
    mediaItems: List<MediaItem>,
    customListId: String,
    taggedUsers: List<String>? = null,
    mentionedUsers: List<String>? = null,
    location: String? = null,
    locationCoordinate: Moment.LocationCoordinate? = null,
    aspectRatio: String? = null,
    disableComments: Boolean = false,
    hideLikeCounts: Boolean = false,
    allowSharing: Boolean = true,
    scheduledDate: Date? = null,
    momentId: String? = null,
): String = createMomentDocument(
    userId, content, mediaItems, taggedUsers, mentionedUsers, location,
    locationCoordinate, ContentAudience.CUSTOM_LIST.raw, customListId, null, aspectRatio,
    disableComments, hideLikeCounts, allowSharing, scheduledDate, momentId,
)

private suspend fun FirestoreService.createMomentDocument(
    userId: String,
    content: String,
    mediaItems: List<MediaItem>,
    taggedUsers: List<String>?,
    mentionedUsers: List<String>?,
    location: String?,
    locationCoordinate: Moment.LocationCoordinate?,
    audience: String?,
    customListId: String?,
    customViewers: List<String>?,
    aspectRatio: String?,
    disableComments: Boolean,
    hideLikeCounts: Boolean,
    allowSharing: Boolean,
    scheduledDate: Date?,
    momentId: String?,
): String {
    val user = fetchUser(userId)
    val imagePath = mediaItems.firstOrNull { it.type == MediaItem.MediaType.IMAGE }?.url
    val videoItem = mediaItems.firstOrNull { it.type == MediaItem.MediaType.VIDEO }
    val moment = Moment(
        authorId = userId,
        username = user.username,
        content = content,
        imagePath = imagePath,
        videoUrl = videoItem?.url,
        timestamp = Date(),
        profileImagePath = user.profileImagePath,
        taggedUsers = taggedUsers,
        mentionedUsers = mentionedUsers,
        location = location,
        locationCoordinate = locationCoordinate,
        audience = audience,
        mediaItems = mediaItems,
        aspectRatio = aspectRatio ?: "1:1",
        customListId = customListId,
        thumbnailUrl = videoItem?.thumbnailUrl,
        videoDuration = videoItem?.videoDuration,
        videoFileSize = videoItem?.videoFileSize,
        videoResolution = videoItem?.videoResolution,
        disableComments = disableComments,
        hideLikeCounts = hideLikeCounts,
        allowSharing = allowSharing,
        scheduledDate = scheduledDate,
    )
    val momentData = moment.toMap().toMutableMap()
    momentData["mediaItems"] = serializedMediaItems(mediaItems)
    momentData["hasHiddenLayers"] = false
    momentData["hiddenLayerCount"] = 0
    momentData["mapVisibility"] = MapVisibilityPolicy.resolvedVisibility(
        hasLocation = location != null || locationCoordinate != null,
        audience = audience,
    )
    val resolvedMomentId = momentId ?: UUID.randomUUID().toString()
    db.collection("users").document(userId).collection("moments")
        .document(resolvedMomentId).set(momentData).await()
    updateLastMomentCreatedAt(userId)
    return resolvedMomentId
}

data class MomentsPage(val moments: List<Moment>, val lastDocument: DocumentSnapshot?)

suspend fun FirestoreService.fetchMoments(userId: String): List<Moment> = coroutineScope {
    val momentsRef = db.collection("users").document(userId).collection("moments")
    val pinnedDeferred = async {
        momentsRef.whereEqualTo("isPinned", true).limit(50).get().await().documents
    }
    val recentDeferred = async {
        momentsRef.orderBy("timestamp", Query.Direction.DESCENDING).limit(50).get().await().documents
    }
    val pinnedDocuments = pinnedDeferred.await()
    val recentDocuments = recentDeferred.await()
    val seenIds = mutableSetOf<String>()
    val merged = (pinnedDocuments + recentDocuments).filter { seenIds.add(it.id) }
    val moments = merged.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { Moment.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
    }
    val filtered = filterScheduledMomentsForCurrentViewer(moments)
    filtered.sortedWith(compareByDescending<Moment> { it.isPinned == true }
        .thenByDescending { (it.pinnedAt ?: it.timestamp).time }
        .thenByDescending { it.timestamp.time })
}

suspend fun FirestoreService.fetchMomentsWithVisibility(userId: String, viewerId: String): List<Moment> {
    val moments = fetchMoments(userId)
    return filterMomentsForVisibility(moments, viewerId)
}

suspend fun FirestoreService.fetchInitialMoments(userId: String): MomentsPage {
    val following = fetchFollowing(userId)
    if (following.isEmpty()) return MomentsPage(emptyList(), null)
    val allMoments = coroutineScope {
        following.map { user ->
            async {
                db.collection("users").document(user.id).collection("moments")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(10)
                    .get().await()
            }
        }.awaitAll()
    }.flatMap { snap ->
        snap.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            runCatching { Moment.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
        }
    }.sortedByDescending { it.timestamp }
    val now = Date()
    val filtered = allMoments.filter { moment ->
        if (moment.isArchived == true) return@filter false
        moment.scheduledDate?.let { !it.after(now) } ?: true
    }
    return MomentsPage(filtered.take(10), null)
}

suspend fun FirestoreService.fetchMoreMoments(
    userId: String,
    startAfter: DocumentSnapshot,
): MomentsPage {
    val following = fetchFollowing(userId)
    if (following.isEmpty()) return MomentsPage(emptyList(), null)
    val allMoments = coroutineScope {
        following.map { user ->
            async {
                db.collection("users").document(user.id).collection("moments")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .startAfter(startAfter)
                    .limit(10)
                    .get().await()
            }
        }.awaitAll()
    }.flatMap { snap ->
        snap.documents.mapNotNull { doc ->
            @Suppress("UNCHECKED_CAST")
            runCatching { Moment.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
        }
    }.sortedByDescending { it.timestamp }
    val now = Date()
    val filtered = allMoments.filter { moment ->
        if (moment.isArchived == true) return@filter false
        moment.scheduledDate?.let { !it.after(now) } ?: true
    }
    return MomentsPage(filtered.take(10), null)
}

private suspend fun FirestoreService.filterMomentsForVisibility(
    moments: List<Moment>,
    viewerId: String,
): List<Moment> = coroutineScope {
    val visibleIds = moments.map { moment ->
        async {
            val type = when (moment.audience) {
                ContentAudience.MUTUALS.raw -> com.moments.android.services.privacy.ContentVisibilityType.MUTUALS
                ContentAudience.BEST_FRIENDS.raw -> com.moments.android.services.privacy.ContentVisibilityType.BEST_FRIENDS
                ContentAudience.CUSTOM.raw, ContentAudience.CUSTOM_LIST.raw ->
                    com.moments.android.services.privacy.ContentVisibilityType.CUSTOM
                ContentAudience.ONLY_ME.raw -> com.moments.android.services.privacy.ContentVisibilityType.ONLY_ME
                else -> com.moments.android.services.privacy.ContentVisibilityType.EVERYONE
            }
            val canSee = ContentVisibilityService.canUserSeeContent(
                moment.authorId, viewerId, type, moment.customListId?.let { listOf(it) },
            )
            if (canSee) moment.id else null
        }
    }.awaitAll().filterNotNull().toSet()
    moments.filter { it.id != null && it.id in visibleIds }
}
