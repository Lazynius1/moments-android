package com.moments.android.models.cache

import java.util.Arrays
import java.util.Date
import java.util.UUID

/**
 * Port de `Models/Cache/CachedAction.swift`.
 * Acción pendiente offline (SwiftData @Model → data class / persistencia local).
 */
data class CachedAction(
    val id: String = UUID.randomUUID().toString(),
    /** ver [ActionType] */
    val type: String,
    /** ver [ActionStatus]; default `"pending"` */
    val status: String = ActionStatus.PENDING.raw,
    /** JSON de parámetros */
    val payloadData: ByteArray,
    val createdAt: Date = Date(),
    val retryCount: Int = 0,
    val lastError: String? = null,
    val lastAttemptAt: Date? = null,
) {
    enum class ActionType(val raw: String) {
        MOMENT_UPLOAD("moment_upload"),
        STORY_UPLOAD("story_upload"),
        MESSAGE("message"),
        MEDIA_MESSAGE("media_message"),
        REACTION("reaction"),
        COMMENT("comment"),
        DELETE_COMMENT("delete_comment"),
        FOLLOW("follow"),
        SAVE("save"),
        BLOCK("block"),
        UPDATE_PROFILE("update_profile"),
        ACCEPT_FOLLOW_REQUEST("accept_follow_request"),
        REJECT_FOLLOW_REQUEST("reject_follow_request"),
        REPORT_CONTENT("report_content"),
        MARK_AS_READ("mark_as_read"),
        DELETE_MOMENT("delete_moment");

        companion object {
            fun from(raw: String?) = entries.firstOrNull { it.raw == raw }
        }
    }

    enum class ActionStatus(val raw: String) {
        PENDING("pending"),
        EXECUTING("executing"),
        FAILED("failed"),
        /** Suele borrarse al completar; se conserva por auditoría si hace falta. */
        COMPLETED("completed");

        companion object {
            fun from(raw: String?) = entries.firstOrNull { it.raw == raw }
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedAction) return false
        return id == other.id &&
            type == other.type &&
            status == other.status &&
            Arrays.equals(payloadData, other.payloadData) &&
            createdAt == other.createdAt &&
            retryCount == other.retryCount &&
            lastError == other.lastError &&
            lastAttemptAt == other.lastAttemptAt
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + status.hashCode()
        result = 31 * result + Arrays.hashCode(payloadData)
        result = 31 * result + createdAt.hashCode()
        result = 31 * result + retryCount
        result = 31 * result + (lastError?.hashCode() ?: 0)
        result = 31 * result + (lastAttemptAt?.hashCode() ?: 0)
        return result
    }
}
