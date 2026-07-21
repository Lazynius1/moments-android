package com.moments.android.models.cache

import java.util.Date

// Entidades de caché local (SwiftData @Model en iOS → Room en Android).
// Por ahora son los "shapes"; las anotaciones @Entity/@Dao y las conversiones
// from/to se añadirán al montar la capa de caché (algunas dependen de modelos
// aún no portados: Conversation, EnhancedMessage).

// MARK: - CachedConnection
data class CachedConnection(
    val userId: String,
    val targetId: String,
    val type: String, // "follower" | "following"
    val timestamp: Date = Date(),
) {
    val id: String get() = "${userId}_${targetId}_$type"
}

// MARK: - CachedSearch
data class CachedSearch(
    val query: String,
    val type: String, // "user" | "hashtag" | "text"
    val targetId: String? = null,
    val timestamp: Date = Date(),
) {
    val id: String get() = "${query}_${type}_${targetId ?: "none"}"
}

// MARK: - CachedAction (acción pendiente offline)
data class CachedAction(
    val id: String,
    val type: String,       // ver ActionType
    val status: String = "pending",
    val payloadData: ByteArray, // JSON de parámetros
    val createdAt: Date = Date(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    val lastAttemptAt: Date? = null,
) {
    enum class ActionType(val raw: String) {
        MOMENT_UPLOAD("moment_upload"), STORY_UPLOAD("story_upload"), MESSAGE("message"),
        MEDIA_MESSAGE("media_message"), REACTION("reaction"), COMMENT("comment"),
        DELETE_COMMENT("delete_comment"), FOLLOW("follow"), SAVE("save"), BLOCK("block"),
        UPDATE_PROFILE("update_profile"), ACCEPT_FOLLOW_REQUEST("accept_follow_request"),
        REJECT_FOLLOW_REQUEST("reject_follow_request"), REPORT_CONTENT("report_content"),
        MARK_AS_READ("mark_as_read"), DELETE_MOMENT("delete_moment");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
    }

    enum class ActionStatus(val raw: String) {
        PENDING("pending"), EXECUTING("executing"), FAILED("failed"), COMPLETED("completed");
        companion object { fun from(raw: String?) = entries.firstOrNull { it.raw == raw } }
    }
}
