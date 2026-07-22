package com.moments.android.notifications.services

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Helpers globales portados de NotificationExtensions.swift (NotificationCenter / cache).
 * En Android usamos SharedFlow en lugar de NotificationCenter.
 */
object AppEventBus {
    private val _profileUpdated = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val profileUpdated: SharedFlow<String> = _profileUpdated.asSharedFlow()

    private val _profileImageUpdated = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val profileImageUpdated: SharedFlow<String> = _profileImageUpdated.asSharedFlow()

    private val _moderationStatusChanged = MutableSharedFlow<ModerationStatusEvent>(extraBufferCapacity = 8)
    val moderationStatusChanged: SharedFlow<ModerationStatusEvent> = _moderationStatusChanged.asSharedFlow()

    data class ModerationStatusEvent(
        val status: String,
        val contentType: String,
        val contentId: String,
        val authorId: String? = null,
    )

    fun postProfileUpdate(userId: String) {
        _profileUpdated.tryEmit(userId)
        _profileImageUpdated.tryEmit(userId)
    }

    fun postModerationUpdate(
        contentType: String,
        contentId: String,
        status: String,
        authorId: String? = null,
    ) {
        _moderationStatusChanged.tryEmit(
            ModerationStatusEvent(status, contentType, contentId, authorId),
        )
    }
}

/** Stub de ImageCacheManager de NotificationExtensions.swift */
object ImageCacheManager {
    fun clearCacheForUser(userId: String) = Unit
    fun clearAllCache() = Unit
    fun postProfileUpdate(userId: String) = AppEventBus.postProfileUpdate(userId)
    fun postModerationUpdate(contentType: String, contentId: String, status: String, authorId: String? = null) {
        AppEventBus.postModerationUpdate(contentType, contentId, status, authorId)
    }
}
