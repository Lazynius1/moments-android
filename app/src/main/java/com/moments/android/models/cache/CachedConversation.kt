package com.moments.android.models.cache

import java.util.Date

/**
 * Conversación cacheada localmente. Room + conversiones ↔ Conversation al portar Messaging
 * (los blobs Data guardan mapas [String:Bool]/[String:Date] serializados).
 */
data class CachedConversation(
    val id: String,
    val participants: List<String>,
    val lastMessage: String? = null,
    val timestamp: Date,
    val readStatusData: ByteArray? = null,
    val otherParticipantId: String,
    val otherParticipantUsername: String? = null,
    val otherParticipantProfileImagePath: String? = null,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
    val readReceiptPreferencesData: ByteArray? = null,
    val forwardingPreferencesData: ByteArray? = null,
    val lastDeletedAtData: ByteArray? = null,
    val lastReadAtData: ByteArray? = null,
    val lastMessageSenderId: String? = null,
    val lastMessageSeenAtData: ByteArray? = null,
    val lastMessageReactionData: ByteArray? = null,
    val lastSyncedAt: Date = Date(),
    val vanishModeActive: Boolean = false,
)
