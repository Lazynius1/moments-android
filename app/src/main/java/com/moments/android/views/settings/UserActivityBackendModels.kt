package com.moments.android.views.settings

import com.moments.android.models.Moment
import com.moments.android.services.content.BackendTagsCursor
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date

/**
 * Port de `UserActivityBackendModels.swift`.
 *
 * En iOS son `Codable` sobre la respuesta JSON de las Cloud Functions. Aquí se mantiene el
 * mismo contrato pero parseando con `org.json`, que es el idioma ya establecido en el port
 * (ver `BackendFeedService.kt`). `BackendTagsCursor` no se redeclara: ya existe allí.
 */

data class BackendReactionsCursor(val timestamp: Double) {
    fun toMap(): Map<String, Any> = mapOf("timestamp" to timestamp)

    companion object {
        fun from(json: JSONObject?): BackendReactionsCursor? =
            json?.optDoubleOrNull("timestamp")?.let { BackendReactionsCursor(it) }
    }
}

data class BackendReactionsItem(
    val moment: Moment?,
    val reactionType: String,
    val reactedAt: Double?,
    val authorId: String?,
    val momentId: String?,
    val canView: Boolean?,
) {
    companion object {
        fun from(json: JSONObject): BackendReactionsItem = BackendReactionsItem(
            moment = json.optJSONObject("moment")?.toActivityMoment(),
            reactionType = json.optString("reactionType"),
            reactedAt = json.optDoubleOrNull("reactedAt"),
            authorId = json.optStringOrNull("authorId"),
            momentId = json.optStringOrNull("momentId"),
            canView = json.optBooleanOrNull("canView"),
        )
    }
}

data class BackendReactionsResponse(
    val items: List<BackendReactionsItem>,
    val nextCursor: BackendReactionsCursor?,
    val source: String,
    val totalCandidates: Int,
) {
    companion object {
        fun from(json: JSONObject): BackendReactionsResponse = BackendReactionsResponse(
            items = json.optJSONArray("items").mapObjects(BackendReactionsItem::from),
            nextCursor = BackendReactionsCursor.from(json.optJSONObject("nextCursor")),
            source = json.optString("source", "unknown"),
            totalCandidates = json.optInt("totalCandidates", 0),
        )
    }
}

data class BackendCommentsCursor(val timestamp: Double) {
    fun toMap(): Map<String, Any> = mapOf("timestamp" to timestamp)

    companion object {
        fun from(json: JSONObject?): BackendCommentsCursor? =
            json?.optDoubleOrNull("timestamp")?.let { BackendCommentsCursor(it) }
    }
}

data class BackendCommentPayload(
    val id: String?,
    val content: String?,
    val timestamp: Double?,
    val parentCommentId: String?,
) {
    companion object {
        fun from(json: JSONObject): BackendCommentPayload = BackendCommentPayload(
            id = json.optStringOrNull("id"),
            content = json.optStringOrNull("content"),
            timestamp = json.optDoubleOrNull("timestamp"),
            parentCommentId = json.optStringOrNull("parentCommentId"),
        )
    }
}

data class BackendCommentedItem(
    val moment: Moment?,
    val comment: BackendCommentPayload?,
    val commentedAt: Double?,
    val authorId: String?,
    val momentId: String?,
    val commentId: String?,
    val canView: Boolean?,
) {
    companion object {
        fun from(json: JSONObject): BackendCommentedItem = BackendCommentedItem(
            moment = json.optJSONObject("moment")?.toActivityMoment(),
            comment = json.optJSONObject("comment")?.let(BackendCommentPayload::from),
            commentedAt = json.optDoubleOrNull("commentedAt"),
            authorId = json.optStringOrNull("authorId"),
            momentId = json.optStringOrNull("momentId"),
            commentId = json.optStringOrNull("commentId"),
            canView = json.optBooleanOrNull("canView"),
        )
    }
}

data class BackendCommentsResponse(
    val items: List<BackendCommentedItem>,
    val nextCursor: BackendCommentsCursor?,
    val source: String,
    val totalCandidates: Int,
) {
    companion object {
        fun from(json: JSONObject): BackendCommentsResponse = BackendCommentsResponse(
            items = json.optJSONArray("items").mapObjects(BackendCommentedItem::from),
            nextCursor = BackendCommentsCursor.from(json.optJSONObject("nextCursor")),
            source = json.optString("source", "unknown"),
            totalCandidates = json.optInt("totalCandidates", 0),
        )
    }
}

data class BackendTaggedItem(
    val moment: Moment?,
    val taggedAt: Double?,
    val authorId: String?,
    val momentId: String?,
    val canView: Boolean?,
) {
    companion object {
        fun from(json: JSONObject): BackendTaggedItem = BackendTaggedItem(
            moment = json.optJSONObject("moment")?.toActivityMoment(),
            taggedAt = json.optDoubleOrNull("taggedAt"),
            authorId = json.optStringOrNull("authorId"),
            momentId = json.optStringOrNull("momentId"),
            canView = json.optBooleanOrNull("canView"),
        )
    }
}

data class BackendTagsResponse(
    val items: List<BackendTaggedItem>,
    val nextCursor: BackendTagsCursor?,
    val source: String,
    val totalCandidates: Int,
) {
    companion object {
        fun from(json: JSONObject): BackendTagsResponse = BackendTagsResponse(
            items = json.optJSONArray("items").mapObjects(BackendTaggedItem::from),
            nextCursor = json.optJSONObject("nextCursor")
                ?.optDoubleOrNull("timestamp")
                ?.let { BackendTagsCursor(it) },
            source = json.optString("source", "unknown"),
            totalCandidates = json.optInt("totalCandidates", 0),
        )
    }
}

data class BackendStickerRepliesCursor(val timestamp: Double) {
    fun toMap(): Map<String, Any> = mapOf("timestamp" to timestamp)

    companion object {
        fun from(json: JSONObject?): BackendStickerRepliesCursor? =
            json?.optDoubleOrNull("timestamp")?.let { BackendStickerRepliesCursor(it) }
    }
}

data class BackendStickerReplyItem(
    val id: String,
    val sourceId: String?,
    val kind: String,
    val authorId: String?,
    val storyId: String?,
    val targetUsername: String?,
    val actorId: String?,
    val actorUsername: String?,
    val actorProfileImagePath: String?,
    val timestamp: Double?,
    val questionText: String?,
    val responseText: String?,
    val pollOption: Int?,
    val pollOptionText: String?,
) {
    companion object {
        fun from(json: JSONObject): BackendStickerReplyItem = BackendStickerReplyItem(
            id = json.optString("id"),
            sourceId = json.optStringOrNull("sourceId"),
            kind = json.optString("kind"),
            authorId = json.optStringOrNull("authorId"),
            storyId = json.optStringOrNull("storyId"),
            targetUsername = json.optStringOrNull("targetUsername"),
            actorId = json.optStringOrNull("actorId"),
            actorUsername = json.optStringOrNull("actorUsername"),
            actorProfileImagePath = json.optStringOrNull("actorProfileImagePath"),
            timestamp = json.optDoubleOrNull("timestamp"),
            questionText = json.optStringOrNull("questionText"),
            responseText = json.optStringOrNull("responseText"),
            pollOption = if (json.has("pollOption") && !json.isNull("pollOption")) {
                json.optInt("pollOption")
            } else {
                null
            },
            pollOptionText = json.optStringOrNull("pollOptionText"),
        )
    }
}

data class BackendStickerRepliesResponse(
    val items: List<BackendStickerReplyItem>,
    val nextCursor: BackendStickerRepliesCursor?,
    val source: String,
    val totalCandidates: Int,
) {
    companion object {
        fun from(json: JSONObject): BackendStickerRepliesResponse = BackendStickerRepliesResponse(
            items = json.optJSONArray("items").mapObjects(BackendStickerReplyItem::from),
            nextCursor = BackendStickerRepliesCursor.from(json.optJSONObject("nextCursor")),
            source = json.optString("source", "unknown"),
            totalCandidates = json.optInt("totalCandidates", 0),
        )
    }
}

data class DeleteCommentsTarget(
    val authorId: String,
    val momentId: String,
    val commentId: String,
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("authorId", authorId)
        put("momentId", momentId)
        put("commentId", commentId)
    }
}

data class DeleteCommentsBatchResponse(
    val deleted: Int,
    val skipped: Int,
    val cascadedReplies: Int?,
) {
    companion object {
        fun from(json: JSONObject): DeleteCommentsBatchResponse = DeleteCommentsBatchResponse(
            deleted = json.optInt("deleted", 0),
            skipped = json.optInt("skipped", 0),
            cascadedReplies = if (json.has("cascadedReplies") && !json.isNull("cascadedReplies")) {
                json.optInt("cascadedReplies")
            } else {
                null
            },
        )
    }
}

data class NotificationRecord(
    val id: String,
    val type: String,
    val senderUsername: String?,
    val reaction: String?,
    val timestamp: Date,
)

// MARK: - Helpers de parseo

internal fun JSONObject.optStringOrNull(key: String): String? =
    if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null

internal fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (has(key) && !isNull(key)) optDouble(key).takeIf { !it.isNaN() } else null

internal fun JSONObject.optBooleanOrNull(key: String): Boolean? =
    if (has(key) && !isNull(key)) optBoolean(key) else null

internal fun <T> JSONArray?.mapObjects(transform: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return (0 until length()).mapNotNull { index -> optJSONObject(index)?.let(transform) }
}

internal fun JSONObject.toActivityMoment(): Moment =
    Moment.from(optString("id").ifBlank { null }, toPlainMap())

internal fun JSONObject.toPlainMap(): Map<String, Any?> =
    keys().asSequence().associateWith { key ->
        when (val value = opt(key)) {
            is JSONObject -> value.toPlainMap()
            is JSONArray -> (0 until value.length()).map { index ->
                value.opt(index).let { if (it is JSONObject) it.toPlainMap() else it }
            }
            JSONObject.NULL -> null
            else -> value
        }
    }
