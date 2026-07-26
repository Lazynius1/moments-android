package com.moments.android.models.cache

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.ConversationLastMessageReaction
import org.json.JSONObject
import java.util.Arrays
import java.util.Date
import java.util.UUID

/**
 * Port de `Models/Cache/CachedConversation.swift`.
 * Blobs ≡ mapas Codable (`[String:Bool]`, `[String:Date]`, reaction).
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
) {
    /** ≡ iOS `toConversation()`. */
    fun toConversation(): Conversation {
        val currentUid = FirebaseAuth.getInstance().currentUser?.uid
        val conversation = Conversation(
            id = id,
            participants = participants,
            lastMessage = lastMessage,
            timestamp = timestamp,
            readStatus = decodeStringBoolMap(readStatusData),
            otherParticipantId = otherParticipantId,
            otherParticipantUsername = otherParticipantUsername,
            otherParticipantProfileImagePath = otherParticipantProfileImagePath,
            isPinned = isPinned,
            isMuted = isMuted,
            archivedByUserIds = if (isArchived) {
                listOfNotNull(currentUid?.takeIf { it.isNotEmpty() }).takeIf { it.isNotEmpty() }
            } else {
                null
            },
        )
        conversation.readReceiptPreferences = decodeStringBoolMapOptional(readReceiptPreferencesData)
        conversation.forwardingPreferences = decodeStringBoolMapOptional(forwardingPreferencesData)
        conversation.lastDeletedAt = decodeStringDateMap(lastDeletedAtData)
        conversation.lastReadAt = decodeStringDateMap(lastReadAtData)
        conversation.lastMessageSenderId = lastMessageSenderId
        conversation.lastMessageSeenAt = decodeStringDateMap(lastMessageSeenAtData)
        conversation.lastMessageReaction = decodeReaction(lastMessageReactionData)
        conversation.vanishModeActive = vanishModeActive
        return conversation
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CachedConversation) return false
        return id == other.id &&
            participants == other.participants &&
            lastMessage == other.lastMessage &&
            timestamp == other.timestamp &&
            Arrays.equals(readStatusData, other.readStatusData) &&
            otherParticipantId == other.otherParticipantId &&
            otherParticipantUsername == other.otherParticipantUsername &&
            otherParticipantProfileImagePath == other.otherParticipantProfileImagePath &&
            isPinned == other.isPinned &&
            isMuted == other.isMuted &&
            isArchived == other.isArchived &&
            Arrays.equals(readReceiptPreferencesData, other.readReceiptPreferencesData) &&
            Arrays.equals(forwardingPreferencesData, other.forwardingPreferencesData) &&
            Arrays.equals(lastDeletedAtData, other.lastDeletedAtData) &&
            Arrays.equals(lastReadAtData, other.lastReadAtData) &&
            lastMessageSenderId == other.lastMessageSenderId &&
            Arrays.equals(lastMessageSeenAtData, other.lastMessageSeenAtData) &&
            Arrays.equals(lastMessageReactionData, other.lastMessageReactionData) &&
            lastSyncedAt == other.lastSyncedAt &&
            vanishModeActive == other.vanishModeActive
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + participants.hashCode()
        result = 31 * result + (lastMessage?.hashCode() ?: 0)
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + (readStatusData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + otherParticipantId.hashCode()
        result = 31 * result + (otherParticipantUsername?.hashCode() ?: 0)
        result = 31 * result + (otherParticipantProfileImagePath?.hashCode() ?: 0)
        result = 31 * result + isPinned.hashCode()
        result = 31 * result + isMuted.hashCode()
        result = 31 * result + isArchived.hashCode()
        result = 31 * result + (readReceiptPreferencesData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (forwardingPreferencesData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (lastDeletedAtData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (lastReadAtData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (lastMessageSenderId?.hashCode() ?: 0)
        result = 31 * result + (lastMessageSeenAtData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + (lastMessageReactionData?.let { Arrays.hashCode(it) } ?: 0)
        result = 31 * result + lastSyncedAt.hashCode()
        result = 31 * result + vanishModeActive.hashCode()
        return result
    }

    companion object {
        /** ≡ iOS `CachedConversation.from(_:)`. */
        fun from(conversation: Conversation): CachedConversation {
            val currentUid = FirebaseAuth.getInstance().currentUser?.uid
            return CachedConversation(
                id = conversation.id ?: UUID.randomUUID().toString(),
                participants = conversation.participants,
                lastMessage = conversation.lastMessage,
                timestamp = conversation.timestamp,
                readStatusData = encodeStringBoolMap(conversation.readStatus),
                otherParticipantId = conversation.otherParticipantId,
                otherParticipantUsername = conversation.otherParticipantUsername,
                otherParticipantProfileImagePath = conversation.otherParticipantProfileImagePath,
                isPinned = conversation.isPinned ?: false,
                isMuted = conversation.isMuted ?: false,
                isArchived = conversation.isArchived(currentUid),
                readReceiptPreferencesData = conversation.readReceiptPreferences?.let(::encodeStringBoolMap),
                forwardingPreferencesData = conversation.forwardingPreferences?.let(::encodeStringBoolMap),
                lastDeletedAtData = conversation.lastDeletedAt?.let(::encodeStringDateMap),
                lastReadAtData = conversation.lastReadAt?.let(::encodeStringDateMap),
                lastMessageSenderId = conversation.lastMessageSenderId,
                lastMessageSeenAtData = conversation.lastMessageSeenAt?.let(::encodeStringDateMap),
                lastMessageReactionData = conversation.lastMessageReaction?.let(::encodeReaction),
                lastSyncedAt = Date(),
                vanishModeActive = conversation.vanishModeActive ?: false,
            )
        }

        fun encodeStringBoolMap(map: Map<String, Boolean>): ByteArray =
            JSONObject().apply { map.forEach { (k, v) -> put(k, v) } }.toString().toByteArray()

        fun decodeStringBoolMap(data: ByteArray?): Map<String, Boolean> {
            if (data == null) return emptyMap()
            return runCatching {
                val obj = JSONObject(String(data))
                obj.keys().asSequence().associateWith { obj.getBoolean(it) }
            }.getOrDefault(emptyMap())
        }

        private fun encodeStringDateMap(map: Map<String, Date>): ByteArray =
            JSONObject().apply { map.forEach { (k, v) -> put(k, v.time) } }.toString().toByteArray()

        private fun encodeReaction(reaction: ConversationLastMessageReaction): ByteArray =
            JSONObject().apply {
                put("messageId", reaction.messageId)
                put("emoji", reaction.emoji)
                put("byUserId", reaction.byUserId)
            }.toString().toByteArray()

        /** iOS: sin data → `[:]`; decode fallido → nil. */
        private fun decodeStringBoolMapOptional(data: ByteArray?): Map<String, Boolean>? {
            if (data == null) return emptyMap()
            return runCatching {
                val obj = JSONObject(String(data))
                obj.keys().asSequence().associateWith { obj.getBoolean(it) }
            }.getOrNull()
        }

        private fun decodeStringDateMap(data: ByteArray?): Map<String, Date>? {
            if (data == null) return null
            return runCatching {
                val obj = JSONObject(String(data))
                obj.keys().asSequence().associateWith { Date(obj.getLong(it)) }
            }.getOrNull()
        }

        private fun decodeReaction(data: ByteArray?): ConversationLastMessageReaction? {
            if (data == null) return null
            return runCatching {
                val obj = JSONObject(String(data))
                ConversationLastMessageReaction(
                    messageId = obj.getString("messageId"),
                    emoji = obj.getString("emoji"),
                    byUserId = obj.getString("byUserId"),
                )
            }.getOrNull()
        }
    }
}
