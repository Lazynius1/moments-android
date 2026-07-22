package com.moments.android.views.messaging.services

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.moments.android.models.ChatMediaPurpose
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MediaMessagePayload
import com.moments.android.models.MessagePayload
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageSyncCursor
import com.moments.android.models.MessageType
import com.moments.android.models.cache.CachedAction
import com.moments.android.models.encode
import com.moments.android.services.firestore.shouldQueueFirestoreOutbox
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Date
import java.util.UUID

import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.ChatCacheStore

/**
 * Port core de ChatService.swift para ingest, offline sync y logout.
 * UI listeners / media pipeline completo siguen en Messaging Views.
 */
object ChatService {

    class NotPortedYet(message: String) : Exception(message)

    const val SEND_ACK_TIMEOUT_MS = 15_000L

    private val db get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val encryptedMediaResolver = ChatEncryptedMediaResolver

    data class LiveLocationStatus(
        val exists: Boolean,
        val senderId: String?,
        val isStopped: Boolean,
        val expiresAt: Date?,
    )

    suspend fun preloadEncryption(conversationId: String) {
        EncryptionService.preloadConversationKeys(listOf(conversationId))
    }

    suspend fun fetchRecentMessages(conversationId: String, limit: Int): Result<List<EnhancedMessage>> = runCatching {
        preloadEncryption(conversationId)
        val snapshot = db.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(limit.toLong())
            .get()
            .await()
        snapshot.documents.mapNotNull { doc ->
            ChatMessageMapper.buildFromSnapshot(doc, conversationId)
        }
    }

    suspend fun fetchMessagesAfter(
        conversationId: String,
        after: MessageSyncCursor,
        limit: Int,
    ): Result<List<EnhancedMessage>> = runCatching {
        preloadEncryption(conversationId)
        val collection = db.collection("conversations").document(conversationId).collection("messages")
        val snapshot = if (after.messageId.isEmpty()) {
            collection
                .whereGreaterThan("timestamp", Timestamp(after.timestamp))
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
                .limit(limit.toLong())
                .get()
                .await()
        } else {
            collection
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.ASCENDING)
                .startAfter(Timestamp(after.timestamp), after.messageId)
                .limit(limit.toLong())
                .get()
                .await()
        }
        snapshot.documents.mapNotNull { doc ->
            ChatMessageMapper.buildFromSnapshot(doc, conversationId)
        }.filter { MessageSyncCursor(it.timestamp, it.id) > after }
    }

    suspend fun fetchMessage(conversationId: String, messageId: String): Result<EnhancedMessage> = runCatching {
        preloadEncryption(conversationId)
        val doc = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId).get().await()
        ChatMessageMapper.buildFromSnapshot(doc, conversationId)
            ?: error("Message not found")
    }

    suspend fun sendTextMessage(
        conversationId: String,
        senderId: String,
        content: String,
        replyTo: String? = null,
        messageId: String? = null,
        isVanishModeMessage: Boolean = false,
        vanishExpiresAt: Date? = null,
    ): Result<EnhancedMessage> = runCatching {
        val encrypted = EncryptionService.encryptChatMessage(content, conversationId)
        val message = EnhancedMessage(
            id = messageId ?: UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.TEXT,
            content = encrypted,
            timestamp = Date(),
            status = MessageStatus.SENDING,
            replyTo = replyTo,
            isVanishModeMessage = isVanishModeMessage,
            vanishExpiresAt = vanishExpiresAt,
        )
        sendMessage(message, useServerTimestamp = true).getOrThrow()
    }

    suspend fun sendMessage(
        message: EnhancedMessage,
        useServerTimestamp: Boolean = true,
    ): Result<EnhancedMessage> = runCatching {
        if (shouldQueueFirestoreOutbox()) {
            val pending = message.copy(status = MessageStatus.PENDING)
            ChatMessageMapper.queueOfflineMessage(pending, useServerTimestamp)
            return@runCatching pending
        }
        val messageRef = db.collection("conversations").document(message.conversationId)
            .collection("messages").document(message.id)
        val messageData = ChatMessageMapper.toFirestoreData(message, useServerTimestamp)
        val writeSucceeded = withTimeoutOrNull(SEND_ACK_TIMEOUT_MS) {
            messageRef.set(messageData).await()
            true
        } ?: false
        if (!writeSucceeded) {
            val pending = message.copy(status = MessageStatus.PENDING)
            ChatMessageMapper.queueOfflineMessage(pending, useServerTimestamp)
            LocalPersistenceService.saveMessagesInBackground(listOf(pending), message.conversationId, sync = false)
            updateLocalMessageStatus(message.conversationId, message.id, MessageStatus.PENDING)
            return@runCatching pending
        }
        LocalPersistenceService.deleteAction(message.id)
        updateConversationPreview(message)
        updateMessageStatus(message.conversationId, message.id, MessageStatus.SENT)
        message.copy(status = MessageStatus.SENT)
    }

    suspend fun stopLiveLocationMessage(conversationId: String, messageId: String) {
        db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
            .update(mapOf("liveLocationStoppedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    suspend fun fetchLiveLocationStatus(conversationId: String, messageId: String): LiveLocationStatus? = runCatching {
        val snap = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId).get().await()
        if (!snap.exists()) {
            return@runCatching LiveLocationStatus(exists = false, senderId = null, isStopped = true, expiresAt = null)
        }
        val data = snap.data ?: return@runCatching null
        val senderId = data["senderId"] as? String
        val stoppedAt = data["liveLocationStoppedAt"]
        val expiresAt = (data["liveLocationExpiresAt"] as? Timestamp)?.toDate()
        val isLive = data["isLiveLocation"] as? Boolean ?: false
        val isStopped = stoppedAt != null || !isLive || (expiresAt != null && expiresAt <= Date())
        LiveLocationStatus(exists = true, senderId = senderId, isStopped = isStopped, expiresAt = expiresAt)
    }.getOrNull()

    fun markMessagesAsDelivered(
        messages: List<EnhancedMessage>,
        conversationId: String,
        currentUserId: String,
    ) {
        messages.filter {
            it.senderId != currentUserId &&
                it.status == MessageStatus.SENT &&
                !it.isRead
        }.forEach { message ->
            scope.launch {
                runCatching {
                    updateMessageStatus(conversationId, message.id, MessageStatus.DELIVERED)
                }
            }
        }
    }

    fun markMessageAsDeliveredFromNotification(conversationId: String, messageId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching {
                val snap = db.collection("conversations").document(conversationId)
                    .collection("messages").document(messageId).get().await()
                val data = snap.data ?: return@runCatching
                val senderId = data["senderId"] as? String ?: return@runCatching
                val status = data["status"] as? String ?: return@runCatching
                if (senderId == currentUserId || status != MessageStatus.SENT.raw) return@runCatching
                updateMessageStatus(conversationId, messageId, MessageStatus.DELIVERED)
            }
        }
    }

    /**
     * Mirrors iOS' `markMessagesAsRead`: all reads are recorded in `readBy`,
     * while externally visible read status remains subject to user and chat
     * privacy settings. Incognito must leave no server-side read trace.
     */
    suspend fun markMessagesAsRead(
        conversationId: String,
        messageIds: List<String>,
        readerId: String,
        marksLastMessageSeen: Boolean = false,
    ) {
        if (IncognitoModeService.isActiveSnapshot || messageIds.isEmpty()) return

        val userSettings = db.collection("users").document(readerId).get().await().data
        val globalEnabled = userSettings?.get("showReadReceipts") as? Boolean ?: true
        val conversationRef = db.collection("conversations").document(conversationId)
        val conversation = conversationRef.get().await().data
        @Suppress("UNCHECKED_CAST")
        val preferences = conversation?.get("readReceiptPreferences") as? Map<String, Boolean> ?: emptyMap()
        val finalEnabled = ChatReadReceiptPolicy.isEnabled(globalEnabled, preferences[readerId])

        val batch = db.batch()
        messageIds.distinct().forEach { messageId ->
            val update = mutableMapOf<String, Any>("readBy" to FieldValue.arrayUnion(readerId))
            if (finalEnabled) {
                update["isRead"] = true
                update["status"] = MessageStatus.READ.raw
            }
            batch.update(conversationRef.collection("messages").document(messageId), update)
        }
        val conversationUpdate = mutableMapOf<String, Any>(
            "readStatus.$readerId" to true,
            "lastReadAt.$readerId" to FieldValue.serverTimestamp(),
        )
        if (marksLastMessageSeen && finalEnabled) {
            conversationUpdate["lastMessageSeenAt.$readerId"] = FieldValue.serverTimestamp()
        }
        batch.update(conversationRef, conversationUpdate)
        batch.commit().await()
    }

    suspend fun markConversationAsRead(conversationId: String, userId: String) {
        if (IncognitoModeService.isActiveSnapshot) return
        db.collection("conversations").document(conversationId).update(
            mapOf(
                "readStatus.$userId" to true,
                "lastReadAt.$userId" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    fun updateLocalMessageStatus(conversationId: String, messageId: String, status: MessageStatus) {
        // Event bus cuando ChatViewModel esté portado; persistencia local ya actualizada en sendMessage timeout.
    }

    suspend fun updateMessageStatus(
        conversationId: String,
        messageId: String,
        status: MessageStatus,
    ) {
        db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
            .update(mapOf("status" to status.raw))
            .await()
    }

    private suspend fun updateConversationPreview(message: EnhancedMessage) {
        val preview = when (message.type) {
            MessageType.TEXT -> message.content?.take(120) ?: ""
            else -> message.type.raw
        }
        val update = mutableMapOf<String, Any>(
            "lastMessage" to preview,
            "timestamp" to FieldValue.serverTimestamp(),
            "readStatus.${message.senderId}" to true,
            "lastMessageSenderId" to message.senderId,
            "lastMessageSeenAt" to FieldValue.delete(),
            "lastMessageReaction" to FieldValue.delete(),
        )
        db.collection("conversations").document(message.conversationId).update(update).await()
        LocalPersistenceService.upsertConversationPreview(message)
    }

    suspend fun sendAudioMessage(
        conversationId: String,
        senderId: String,
        audioData: ByteArray,
        duration: Double,
        waveform: List<Float>?,
        messageId: String,
        isVanishModeMessage: Boolean,
    ): Result<EnhancedMessage> = runCatching {
        if (shouldQueueFirestoreOutbox()) {
            return@runCatching queueOfflineMediaMessage(
                conversationId = conversationId,
                senderId = senderId,
                type = MessageType.AUDIO,
                mediaData = audioData,
                messageId = messageId,
                fileName = "audio_$messageId.m4a",
                duration = duration,
                audioWaveform = waveform,
                mediaBatchId = null,
                isVanishModeMessage = isVanishModeMessage,
                vanishExpiresAt = null,
                replyTo = null,
            )
        }
        val uploadResult = ChatServiceMediaPipeline.uploadMedia(
            data = audioData,
            type = MessageType.AUDIO,
            conversationId = conversationId,
            messageId = messageId,
        ).getOrThrow()
        val message = EnhancedMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.AUDIO,
            content = null,
            mediaUrl = uploadResult.mediaUrl,
            thumbnailUrl = uploadResult.thumbnailUrl,
            mediaObjectPath = uploadResult.mediaObjectPath,
            thumbnailObjectPath = uploadResult.thumbnailObjectPath,
            mediaEncryption = uploadResult.mediaEncryption,
            thumbnailEncryption = uploadResult.thumbnailEncryption,
            duration = duration,
            audioWaveform = waveform,
            fileName = "audio_$messageId.m4a",
            fileSize = audioData.size.toLong(),
            timestamp = Date(),
            status = MessageStatus.SENDING,
            isVanishModeMessage = isVanishModeMessage,
        )
        sendMessage(message, useServerTimestamp = true).getOrThrow()
    }

    suspend fun sendMediaMessage(
        conversationId: String,
        senderId: String,
        type: MessageType,
        mediaData: ByteArray,
        fileName: String?,
        messageId: String,
        mediaBatchId: String?,
        isVanishModeMessage: Boolean,
        vanishExpiresAt: Date?,
        replyTo: String?,
    ): Result<EnhancedMessage> = runCatching {
        if (shouldQueueFirestoreOutbox()) {
            return@runCatching queueOfflineMediaMessage(
                conversationId = conversationId,
                senderId = senderId,
                type = type,
                mediaData = mediaData,
                messageId = messageId,
                fileName = fileName,
                duration = null,
                audioWaveform = null,
                mediaBatchId = mediaBatchId,
                isVanishModeMessage = isVanishModeMessage,
                vanishExpiresAt = vanishExpiresAt,
                replyTo = replyTo,
            )
        }
        val uploadResult = ChatServiceMediaPipeline.uploadMedia(
            data = mediaData,
            type = type,
            conversationId = conversationId,
            messageId = messageId,
        ).getOrThrow()
        val message = EnhancedMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            type = type,
            content = null,
            mediaUrl = uploadResult.mediaUrl,
            thumbnailUrl = uploadResult.thumbnailUrl,
            mediaObjectPath = uploadResult.mediaObjectPath,
            thumbnailObjectPath = uploadResult.thumbnailObjectPath,
            mediaEncryption = uploadResult.mediaEncryption,
            thumbnailEncryption = uploadResult.thumbnailEncryption,
            fileName = fileName,
            fileSize = mediaData.size.toLong(),
            timestamp = Date(),
            status = MessageStatus.SENDING,
            mediaBatchId = mediaBatchId,
            isVanishModeMessage = isVanishModeMessage,
            vanishExpiresAt = vanishExpiresAt,
            replyTo = replyTo,
        )
        sendMessage(message, useServerTimestamp = true).getOrThrow()
    }

    private fun queueOfflineMediaMessage(
        conversationId: String,
        senderId: String,
        type: MessageType,
        mediaData: ByteArray,
        messageId: String,
        fileName: String?,
        duration: Double?,
        audioWaveform: List<Float>?,
        mediaBatchId: String?,
        isVanishModeMessage: Boolean,
        vanishExpiresAt: Date?,
        replyTo: String?,
    ): EnhancedMessage {
        val fileExtension = ChatServiceMediaPipeline.fileExtensionFor(type)
        val localFile = ChatCacheStore.writeDecryptedMedia(
            mediaData,
            conversationId,
            messageId,
            ChatMediaPurpose.PRIMARY,
            fileExtension,
        )
        val payload = MediaMessagePayload(
            conversationId = conversationId,
            senderId = senderId,
            messageId = messageId,
            typeRaw = type.raw,
            fileExtension = fileExtension,
            fileName = fileName,
            duration = duration,
            audioWaveform = audioWaveform,
            mediaBatchId = mediaBatchId,
            isVanishModeMessage = isVanishModeMessage,
            vanishExpiresAt = vanishExpiresAt,
            replyTo = replyTo,
        )
        LocalPersistenceService.saveAction(
            CachedAction(
                id = messageId,
                type = CachedAction.ActionType.MEDIA_MESSAGE.raw,
                payloadData = payload.encode(),
            ),
        )
        return EnhancedMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            type = type,
            mediaUrl = Uri.fromFile(localFile).toString(),
            duration = duration,
            audioWaveform = audioWaveform,
            fileName = fileName,
            fileSize = mediaData.size.toLong(),
            timestamp = Date(),
            status = MessageStatus.PENDING,
            mediaBatchId = mediaBatchId,
            isVanishModeMessage = isVanishModeMessage,
            vanishExpiresAt = vanishExpiresAt,
            replyTo = replyTo,
        )
    }

    private var conversationsListener: com.google.firebase.firestore.ListenerRegistration? = null

    /**
     * Port reducido de `fetchConversations(for:)` — listener inbox.
     * Pin/mute/restore avanzado = lote Messaging completo.
     */
    fun fetchConversations(
        userId: String,
        onUpdate: (Result<List<com.moments.android.models.Conversation>>) -> Unit,
    ) {
        conversationsListener?.remove()
        conversationsListener = db.collection("conversations")
            .whereArrayContains("participants", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                scope.launch(Dispatchers.Main) {
                    if (error != null) {
                        if (FirebaseAuth.getInstance().currentUser == null) {
                            onUpdate(Result.success(emptyList()))
                        } else {
                            onUpdate(Result.failure(error))
                        }
                        return@launch
                    }
                    val docs = snapshot?.documents.orEmpty()
                    val conversations = docs.mapNotNull { doc ->
                        parseConversation(doc.id, doc.data.orEmpty(), userId)
                    }
                    LocalPersistenceService.saveConversations(conversations, sync = true)
                    onUpdate(Result.success(conversations))
                }
            }
    }

    fun stopConversationsListener() {
        conversationsListener?.remove()
        conversationsListener = null
    }

    private fun parseConversation(
        id: String,
        data: Map<String, Any?>,
        viewerId: String,
    ): com.moments.android.models.Conversation? {
        @Suppress("UNCHECKED_CAST")
        val deletedFor = (data["deletedFor"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (viewerId in deletedFor) return null
        @Suppress("UNCHECKED_CAST")
        val archivedBy = (data["archivedByUserIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (viewerId in archivedBy) return null
        @Suppress("UNCHECKED_CAST")
        val participants = (data["participants"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (participants.isEmpty()) return null
        val timestamp = when (val ts = data["timestamp"]) {
            is Timestamp -> ts.toDate()
            is Date -> ts
            else -> return null
        }
        @Suppress("UNCHECKED_CAST")
        val readStatus = (data["readStatus"] as? Map<String, Boolean>).orEmpty()
        val otherId = participants.firstOrNull { it != viewerId }.orEmpty()
        @Suppress("UNCHECKED_CAST")
        val participantData = data["participantData"] as? Map<String, Map<String, Any?>>
        val otherData = participantData?.get(otherId)
        val username = (otherData?.get("username") as? String)
            ?: (data["otherParticipantUsername"] as? String)
            ?: "User"
        val avatar = (otherData?.get("profileImagePath") as? String)
            ?: (data["otherParticipantProfileImagePath"] as? String)
        return com.moments.android.models.Conversation(
            id = id,
            participants = participants,
            lastMessage = data["lastMessage"] as? String,
            timestamp = timestamp,
            readStatus = readStatus,
            otherParticipantId = otherId,
            otherParticipantUsername = username,
            otherParticipantProfileImagePath = avatar,
        )
    }
}

/**
 * Kept beside ChatService because it is the small, testable expression of that
 * same iOS service's privacy precedence.
 */
internal object ChatReadReceiptPolicy {
    fun isEnabled(globalEnabled: Boolean, conversationOverride: Boolean?): Boolean =
        conversationOverride ?: globalEnabled
}
