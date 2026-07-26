package com.moments.android.models

import com.moments.android.views.messaging.core.EnhancedMessage
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Port de `Models/OutboxPayloads.swift`.
 * Payloads de la cola offline; `encode()` ≡ JSONEncoder Codable en iOS.
 */

data class ReactionPayload(
    val momentId: String,
    val reaction: String,
    val authorId: String,
    val userId: String,
)

data class SavePayload(
    val userId: String,
    val momentId: String,
)

data class CommentPayload(
    val momentId: String,
    val authorId: String,
    val senderId: String,
    val content: String,
    val parentCommentId: String? = null,
    val commentId: String? = null,
    val mentions: List<CommentMentionEntity>? = null,
)

data class DeleteCommentPayload(
    val momentId: String,
    val commentId: String,
    val userId: String,
    val authorId: String,
)

data class MessagePayload(
    val message: EnhancedMessage,
    val useServerTimestamp: Boolean,
)

/** Bytes en caché descifrada; ruta re-derivada con ChatCacheStore. */
data class MediaMessagePayload(
    val conversationId: String,
    val senderId: String,
    val messageId: String,
    val typeRaw: String,
    val fileExtension: String,
    val fileName: String? = null,
    val duration: Double? = null,
    val audioWaveform: List<Float>? = null,
    val mediaBatchId: String? = null,
    val isVanishModeMessage: Boolean,
    val vanishExpiresAt: Date? = null,
    val replyTo: String? = null,
)

data class FollowActionPayload(
    val followerId: String,
    val followedId: String,
    val followedUsername: String,
    val isFollow: Boolean,
)

data class BlockActionPayload(
    val currentUserId: String,
    val targetUserId: String,
    val isBlock: Boolean,
)

data class FollowRequestActionPayload(
    val notificationId: String,
    val senderId: String,
    val recipientId: String,
    val isAccept: Boolean,
)

data class ReportActionPayload(
    val reporterId: String,
    val reportedUserId: String,
    val reportedContentType: String,
    val reportedContentId: String,
    val category: String,
    val description: String,
    val priority: String,
)

data class MarkAsReadPayload(
    val notificationId: String,
    val userId: String,
)

data class DeleteMomentPayload(
    val momentId: String,
    val userId: String,
    val imagePath: String? = null,
    val videoUrl: String? = null,
)

data class ProfileUpdatePayload(
    val userId: String,
    val bio: String? = null,
    val oldBio: String? = null,
    val websiteUrl: String? = null,
    val oldWebsiteUrl: String? = null,
    val interests: List<String>? = null,
    val profileImageLocalPath: String? = null,
    val isImageUpdate: Boolean,
)

// MARK: - encode (≡ JSONEncoder)

fun ReactionPayload.encode(): ByteArray = JSONObject().apply {
    put("momentId", momentId)
    put("reaction", reaction)
    put("authorId", authorId)
    put("userId", userId)
}.toString().toByteArray()

fun SavePayload.encode(): ByteArray = JSONObject().apply {
    put("userId", userId)
    put("momentId", momentId)
}.toString().toByteArray()

fun CommentPayload.encode(): ByteArray = JSONObject().apply {
    put("momentId", momentId)
    put("authorId", authorId)
    put("senderId", senderId)
    put("content", content)
    parentCommentId?.let { put("parentCommentId", it) }
    commentId?.let { put("commentId", it) }
    mentions?.let { list ->
        put("mentions", JSONArray().apply {
            list.forEach { put(JSONObject(it.toMap())) }
        })
    }
}.toString().toByteArray()

fun DeleteCommentPayload.encode(): ByteArray = JSONObject().apply {
    put("momentId", momentId)
    put("commentId", commentId)
    put("userId", userId)
    put("authorId", authorId)
}.toString().toByteArray()

fun MessagePayload.encode(): ByteArray = JSONObject().apply {
    put("message", message.toJson())
    put("useServerTimestamp", useServerTimestamp)
}.toString().toByteArray()

fun MediaMessagePayload.encode(): ByteArray = JSONObject().apply {
    put("conversationId", conversationId)
    put("senderId", senderId)
    put("messageId", messageId)
    put("typeRaw", typeRaw)
    put("fileExtension", fileExtension)
    fileName?.let { put("fileName", it) }
    duration?.let { put("duration", it) }
    audioWaveform?.let { list ->
        put("audioWaveform", JSONArray().apply { list.forEach { put(it.toDouble()) } })
    }
    mediaBatchId?.let { put("mediaBatchId", it) }
    put("isVanishModeMessage", isVanishModeMessage)
    vanishExpiresAt?.let { put("vanishExpiresAt", it.time) }
    replyTo?.let { put("replyTo", it) }
}.toString().toByteArray()

fun FollowActionPayload.encode(): ByteArray = JSONObject().apply {
    put("followerId", followerId)
    put("followedId", followedId)
    put("followedUsername", followedUsername)
    put("isFollow", isFollow)
}.toString().toByteArray()

fun BlockActionPayload.encode(): ByteArray = JSONObject().apply {
    put("currentUserId", currentUserId)
    put("targetUserId", targetUserId)
    put("isBlock", isBlock)
}.toString().toByteArray()

fun FollowRequestActionPayload.encode(): ByteArray = JSONObject().apply {
    put("notificationId", notificationId)
    put("senderId", senderId)
    put("recipientId", recipientId)
    put("isAccept", isAccept)
}.toString().toByteArray()

fun ReportActionPayload.encode(): ByteArray = JSONObject().apply {
    put("reporterId", reporterId)
    put("reportedUserId", reportedUserId)
    put("reportedContentType", reportedContentType)
    put("reportedContentId", reportedContentId)
    put("category", category)
    put("description", description)
    put("priority", priority)
}.toString().toByteArray()

fun MarkAsReadPayload.encode(): ByteArray = JSONObject().apply {
    put("notificationId", notificationId)
    put("userId", userId)
}.toString().toByteArray()

fun DeleteMomentPayload.encode(): ByteArray = JSONObject().apply {
    put("momentId", momentId)
    put("userId", userId)
    imagePath?.let { put("imagePath", it) }
    videoUrl?.let { put("videoUrl", it) }
}.toString().toByteArray()

fun ProfileUpdatePayload.encode(): ByteArray = JSONObject().apply {
    put("userId", userId)
    bio?.let { put("bio", it) }
    oldBio?.let { put("oldBio", it) }
    websiteUrl?.let { put("websiteUrl", it) }
    oldWebsiteUrl?.let { put("oldWebsiteUrl", it) }
    interests?.let { put("interests", JSONArray(it)) }
    profileImageLocalPath?.let { put("profileImageLocalPath", it) }
    put("isImageUpdate", isImageUpdate)
}.toString().toByteArray()
