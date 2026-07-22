package com.moments.android.services.firestore

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Query
import com.moments.android.models.Comment
import com.moments.android.models.CommentMentionEntity
import com.moments.android.models.CommentPayload
import com.moments.android.models.DeleteCommentPayload
import com.moments.android.models.NotificationType
import com.moments.android.models.cache.CachedAction
import com.moments.android.models.encode
import com.moments.android.models.toMap
import com.moments.android.services.notifications.NotificationService
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.privacy.ContentVisibilityService
import com.moments.android.services.privacy.ContentVisibilityType
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

data class CommentsPage(val comments: List<Comment>, val lastDocument: DocumentSnapshot?)

/** Port de FirestoreCommentsRepository.swift. Menciones/push vía NotificationService stub. */
suspend fun FirestoreService.fetchComments(
    momentId: String,
    userId: String,
    limit: Int = 10,
    lastDocument: DocumentSnapshot? = null,
): CommentsPage {
    var query: Query = db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("comments")
        .orderBy("timestamp", Query.Direction.ASCENDING)
        .limit(limit.toLong())
    if (lastDocument != null) query = query.startAfter(lastDocument)
    val snap = query.get().await()
    val comments = snap.documents.mapNotNull { doc ->
        @Suppress("UNCHECKED_CAST")
        runCatching { Comment.from(doc.id, doc.data as Map<String, Any?>) }.getOrNull()
    }.filter { it.id != null }
    return CommentsPage(comments, snap.documents.lastOrNull())
}

suspend fun FirestoreService.addComment(
    momentId: String,
    userId: String,
    authorId: String,
    content: String,
    parentCommentId: String? = null,
    commentId: String? = null,
    mentions: List<CommentMentionEntity>? = null,
) {
    val resolvedCommentId = commentId ?: UUID.randomUUID().toString()
    val sanitizedMentions = sanitizeCommentMentions(mentions ?: emptyList(), content)

    if (shouldQueueFirestoreOutbox()) {
        val payload = CommentPayload(
            momentId = momentId,
            authorId = userId,
            senderId = authorId,
            content = content,
            parentCommentId = parentCommentId,
            commentId = resolvedCommentId,
            mentions = sanitizedMentions,
        )
        LocalPersistenceService.updateCommentCountLocally(momentId, increment = 1)
        LocalPersistenceService.saveAction(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.COMMENT.raw,
                payloadData = payload.encode(),
            ),
        )
        return
    }

    val user = fetchUser(authorId)
    val now = Date()
    val commentData = buildMap<String, Any?> {
        put("authorId", authorId)
        put("username", user.username)
        put("content", content)
        put("text", content)
        put("timestamp", Timestamp(now))
        put("profileImagePath", user.profileImagePath)
        put("updatedAt", null)
        put("reactions", emptyMap<String, List<String>>())
        put("isEdited", false)
        put("editedTimestamp", null)
        put("mentions", sanitizedMentions.map { it.toMap() })
        put("parentCommentId", parentCommentId)
    }
    db.runBatch { batch ->
        val commentRef = db.collection("users").document(userId).collection("moments")
            .document(momentId).collection("comments").document(resolvedCommentId)
        batch.set(commentRef, commentData)
        batch.update(
            db.collection("users").document(userId).collection("moments").document(momentId),
            "commentCount", FieldValue.increment(1),
        )
    }.await()
    LocalPersistenceService.updateCommentCountLocally(momentId, increment = 1)
    // Menciones / reply notifications: NotificationService stub (servidor crea en producción).
}

suspend fun FirestoreService.updateComment(
    momentId: String,
    userId: String,
    commentId: String,
    content: String,
    mentions: List<CommentMentionEntity>? = null,
) {
    val sanitizedMentions = sanitizeCommentMentions(mentions ?: emptyList(), content)
    db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("comments").document(commentId)
        .update(
            mapOf(
                "content" to content,
                "isEdited" to true,
                "editedTimestamp" to Timestamp(Date()),
                "mentions" to sanitizedMentions.map { it.toMap() },
            ),
        ).await()
}

suspend fun FirestoreService.deleteComment(momentId: String, commentId: String, userId: String, authorId: String) {
    LocalPersistenceService.updateCommentCountLocally(momentId, increment = -1)
    if (shouldQueueFirestoreOutbox()) {
        val payload = DeleteCommentPayload(momentId, commentId, userId, authorId)
        LocalPersistenceService.saveAction(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.DELETE_COMMENT.raw,
                payloadData = payload.encode(),
            ),
        )
        return
    }
    val commentRef = db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("comments").document(commentId)
    if (!commentRef.get().await().exists()) error("Comment not found")
    val nested = db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("comments")
        .whereEqualTo("parentCommentId", commentId)
        .get()
        .await()
    db.runBatch { batch ->
        batch.delete(commentRef)
        batch.update(
            db.collection("users").document(userId).collection("moments").document(momentId),
            "commentCount", FieldValue.increment(-1),
        )
        for (nestedDoc in nested.documents) {
            batch.delete(nestedDoc.reference)
            batch.update(
                db.collection("users").document(userId).collection("moments").document(momentId),
                "commentCount", FieldValue.increment(-1),
            )
            (nestedDoc.data?.get("authorId") as? String)?.let { replyAuthorId ->
                NotificationService.removeNotification(
                    NotificationType.COMMENT, replyAuthorId, authorId,
                    momentId = momentId, commentId = nestedDoc.id,
                )
            }
        }
    }.await()
    NotificationService.removeNotification(
        NotificationType.COMMENT, authorId, userId, momentId = momentId, commentId = commentId,
    )
}

suspend fun FirestoreService.addCommentReaction(
    momentId: String,
    commentId: String,
    reaction: String,
    userId: String,
    authorId: String,
) {
    val commentRef = db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("comments").document(commentId)
    val snap = commentRef.get().await()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    @Suppress("UNCHECKED_CAST")
    val existingReactions = snap.data?.get("reactions") as? Map<String, List<String>>
    if (existingReactions == null) {
        val initialReactions = mapOf(reaction to listOf(currentUserId))
        commentRef.update("reactions", initialReactions).await()
        if (authorId != currentUserId) {
            sendCommentReactionNotification(authorId, currentUserId, momentId, commentId, reaction)
        }
        return
    }
    val reactions = existingReactions.toMutableMap()
    val reactionUsers = reactions[reaction]?.toMutableList() ?: mutableListOf()
    val wasLiked = reactionUsers.contains(currentUserId)
    if (wasLiked) {
        reactionUsers.removeAll { it == currentUserId }
        if (authorId != currentUserId) {
            NotificationService.removeNotification(
                type = NotificationType.LIKE,
                senderId = currentUserId,
                recipientId = authorId,
                momentId = momentId,
                commentId = commentId,
                reaction = reaction,
            )
        }
    } else {
        reactionUsers.add(currentUserId)
        if (authorId != currentUserId) {
            sendCommentReactionNotification(authorId, currentUserId, momentId, commentId, reaction)
        }
    }
    reactions[reaction] = reactionUsers
    commentRef.update(
        mapOf(
            "reactions" to reactions,
            "metadata.lastReactionTimestamp" to Timestamp(Date()),
            "metadata.totalReactions" to reactions.values.sumOf { it.size },
        ),
    ).await()
}

private suspend fun FirestoreService.sendCommentReactionNotification(
    recipientId: String,
    senderId: String,
    momentId: String,
    commentId: String,
    reaction: String,
) {
    if (recipientId == senderId) return
    val sender = runCatching { fetchUser(senderId) }.getOrNull() ?: return
    NotificationService.sendInteractionNotification(
        type = NotificationType.LIKE,
        senderId = senderId,
        recipientId = recipientId,
        momentId = momentId,
        commentId = commentId,
        content = reaction,
    )
}

suspend fun FirestoreService.getCommentReactionStats(
    momentId: String,
    userId: String,
    commentId: String,
): Map<String, Int> {
    val snap = db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("comments").document(commentId)
        .get().await()
    @Suppress("UNCHECKED_CAST")
    val reactions = snap.data?.get("reactions") as? Map<String, List<String>> ?: return emptyMap()
    return reactions.mapValues { it.value.size }
}

suspend fun FirestoreService.hasUserReactedToComment(
    momentId: String,
    userId: String,
    commentId: String,
    reaction: String,
): Boolean {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    val snap = db.collection("users").document(userId).collection("moments")
        .document(momentId).collection("comments").document(commentId)
        .get().await()
    @Suppress("UNCHECKED_CAST")
    val reactions = snap.data?.get("reactions") as? Map<String, List<String>> ?: return false
    return reactions[reaction]?.contains(currentUserId) == true
}

private fun sanitizeCommentMentions(mentions: List<CommentMentionEntity>, text: String): List<CommentMentionEntity> {
    val seen = mutableSetOf<String>()
    val sanitized = mutableListOf<CommentMentionEntity>()
    for (mention in mentions) {
        if (mention.userId in seen) continue
        val needle = "@${mention.username}"
        val idx = text.indexOf(needle, ignoreCase = true)
        if (idx < 0) continue
        seen.add(mention.userId)
        sanitized += mention.copy(rangeStart = idx, rangeLength = needle.length)
    }
    return sanitized
}

internal suspend fun FirestoreService.canUserViewMoment(
    momentId: String,
    momentAuthorId: String,
    viewerId: String,
): Boolean {
    if (momentAuthorId == viewerId) return true
    val snap = db.collection("users").document(momentAuthorId)
        .collection("moments").document(momentId).get().await()
    @Suppress("UNCHECKED_CAST")
    val data = snap.data as? Map<String, Any?> ?: return false
    val audience = data["audience"] as? String ?: ContentAudience.EVERYONE.raw
    return when (audience) {
        ContentAudience.ONLY_ME.raw -> false
        ContentAudience.CUSTOM.raw -> {
            val allowed = fetchCustomMomentAudience(momentId, momentAuthorId)
            ContentVisibilityService.canUserSeeContent(
                momentAuthorId, viewerId, ContentVisibilityType.CUSTOM, allowed,
            )
        }
        ContentAudience.CUSTOM_LIST.raw -> {
            val listId = data["customListId"] as? String ?: return false
            val members = fetchCustomListMembers(listId, momentAuthorId)
            ContentVisibilityService.canUserSeeContent(
                momentAuthorId, viewerId, ContentVisibilityType.CUSTOM, members,
            )
        }
        ContentAudience.MUTUALS.raw -> ContentVisibilityService.canUserSeeContent(
            momentAuthorId, viewerId, ContentVisibilityType.MUTUALS,
        )
        ContentAudience.BEST_FRIENDS.raw -> ContentVisibilityService.canUserSeeContent(
            momentAuthorId, viewerId, ContentVisibilityType.BEST_FRIENDS,
        )
        else -> ContentVisibilityService.canUserSeeContent(
            momentAuthorId, viewerId, ContentVisibilityType.EVERYONE,
        )
    }
}

private suspend fun FirestoreService.fetchCustomMomentAudience(momentId: String, authorId: String): List<String> {
    val ref = db.collection("users").document(authorId).collection("customAudiences")
    val specific = ref.document("moment_$momentId").get().await()
    @Suppress("UNCHECKED_CAST")
    val specificUsers = (specific.data?.get("allowedUsers") as? List<*>)?.filterIsInstance<String>()
    if (!specificUsers.isNullOrEmpty()) return specificUsers
    val default = ref.document("default_moment").get().await()
    @Suppress("UNCHECKED_CAST")
    return (default.data?.get("allowedUsers") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}

private suspend fun FirestoreService.fetchCustomListMembers(listId: String, ownerId: String): List<String> {
    val snap = db.collection("users").document(ownerId)
        .collection("customAudienceLists").document(listId).get().await()
    @Suppress("UNCHECKED_CAST")
    return (snap.data?.get("members") as? List<*>)?.filterIsInstance<String>() ?: emptyList()
}
