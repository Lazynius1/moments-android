package com.moments.android.views.messaging.services

import android.net.Uri
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.moments.android.MomentsApplication
import com.moments.android.views.messaging.core.ChatMediaPurpose
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.ConversationLastMessageReaction
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.models.MediaMessagePayload
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageSyncCursor
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.models.WrappedConversationKey
import com.moments.android.models.cache.CachedAction
import com.moments.android.models.encode
import com.moments.android.views.messaging.core.sanitizedConversationPreview
import com.moments.android.models.toMap
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserProfile
import com.moments.android.services.firestore.shouldQueueFirestoreOutbox
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.DirectMessageRoute
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.services.messaging.LocalFirstMessagingSettings
import com.moments.android.services.messaging.VanishMessageTimer
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.views.messaging.models.ChatLocationPayload
import com.moments.android.views.messaging.models.LiveLocationDuration
import com.moments.android.views.shared.ChatPreviewPrivacy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume

/**
 * Port core de `ChatService.swift` (por trozos).
 * Trozo 1: Properties + Listeners Management.
 */
object ChatService {

    class NotPortedYet(message: String) : Exception(message)

    /** ≡ `sendAckTimeoutNanos` (15s). */
    const val SEND_ACK_TIMEOUT_MS = 15_000L

    /** ≡ `typingTimeout` (3s) — se usa al portar start/stop typing. */
    const val TYPING_TIMEOUT_MS = 3_000L

    private val db get() = FirebaseFirestore.getInstance()
    internal val firestore get() = db
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val encryptedMediaResolver = ChatEncryptedMediaResolver

    /**
     * ≡ `Notification.Name("MessageStatusUpdated")` — [ChatViewModel] escucha y actualiza la UI.
     */
    data class MessageStatusUpdate(
        val conversationId: String,
        val messageId: String,
        val status: MessageStatus,
    )

    private val _messageStatusUpdates =
        MutableSharedFlow<MessageStatusUpdate>(extraBufferCapacity = 64)
    val messageStatusUpdates: SharedFlow<MessageStatusUpdate> =
        _messageStatusUpdates.asSharedFlow()

    /**
     * ≡ `activeListeners` iOS — claves:
     * - `{conversationId}` mensajes
     * - `typing_{id}` · `conversation_prefs_{id}` · `reactions_{id}` · `buzz_{id}` · `conversations_{uid}`
     * Reacciones/buzz siguen en sus archivos `+*` pero `removeListener` los limpia igual.
     */
    private val activeListeners =
        mutableMapOf<String, com.google.firebase.firestore.ListenerRegistration>()
    private val listenerGenerations = mutableMapOf<String, Int>()

    private val _typingUsers = MutableStateFlow<Map<String, Set<String>>>(emptyMap())
    val typingUsers: StateFlow<Map<String, Set<String>>> = _typingUsers.asStateFlow()

    /** ≡ `conversationCutoffs` — poblado en `fetchConversations`. */
    private val conversationCutoffs = ConcurrentHashMap<String, Date>()

    /** ≡ `archivedConversationIds` — poblado en `fetchConversations`. */
    @Volatile
    private var archivedConversationIds: Set<String> = emptySet()

    /** Registro legacy + clave en `activeListeners` (`conversations_{uid}`). */
    private var conversationsListener: com.google.firebase.firestore.ListenerRegistration? = null

    /** ≡ `ChatService.MessageHistoryPage`. */
    data class MessageHistoryPage(
        val messages: List<EnhancedMessage>,
        /** Cursor del último doc examinado (aunque se filtre en UI). */
        val nextCursor: MessageSyncCursor?,
        /** Con docs Firestore, no con mensajes visibles tras filtrar. */
        val hasMore: Boolean,
    )

    data class LiveLocationStatus(
        val exists: Boolean,
        val senderId: String?,
        val isStopped: Boolean,
        val expiresAt: Date?,
    )

    // MARK: - Listener generations (≡ iOS)

    fun beginListenerGeneration(key: String): Int {
        bumpListenerGeneration(key)
        return listenerGeneration(key)
    }

    fun isCurrentListenerGeneration(generation: Int, key: String): Boolean =
        listenerGeneration(key) == generation

    private fun bumpListenerGeneration(key: String) {
        listenerGenerations[key] = (listenerGenerations[key] ?: 0) + 1
    }

    private fun listenerGeneration(key: String): Int = listenerGenerations[key] ?: 0

    private fun typingListenerKey(conversationId: String) = "typing_$conversationId"
    private fun prefsListenerKey(conversationId: String) = "conversation_prefs_$conversationId"

    suspend fun preloadEncryption(conversationId: String) {
        EncryptionService.preloadConversationKeys(listOf(conversationId))
    }

    suspend fun fetchRecentMessages(
        conversationId: String,
        limit: Int = 300,
        cutoffDate: Date? = null,
    ): Result<List<EnhancedMessage>> = runCatching {
        preloadEncryption(conversationId)
        val snapshot = db.collection("conversations").document(conversationId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(limit.toLong())
            .get()
            .await()
        handleMessagesSnapshot(
            documents = snapshot.documents,
            error = null,
            conversationId = conversationId,
            cutoffDate = cutoffDate,
        ).getOrThrow()
    }

    suspend fun fetchMessagesAfter(
        conversationId: String,
        after: MessageSyncCursor,
        limit: Int = 50,
        cutoffDate: Date? = null,
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
        handleMessagesSnapshot(
            documents = snapshot.documents,
            error = null,
            conversationId = conversationId,
            cutoffDate = cutoffDate,
        ).getOrThrow()
            .filter { MessageSyncCursor(it.timestamp, it.id).isAfter(after) }
    }

    /** Página DESC con cursor del último doc. */
    suspend fun fetchOlderMessages(
        conversationId: String,
        before: MessageSyncCursor,
        cutoffDate: Date? = null,
        limit: Int = 25,
    ): Result<MessageHistoryPage> = runCatching {
        preloadEncryption(conversationId)
        val collection = db.collection("conversations").document(conversationId).collection("messages")
        val beforeTs = Timestamp(before.timestamp)
        val snapshot = if (before.messageId.isEmpty()) {
            collection
                .whereLessThan("timestamp", beforeTs)
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(limit.toLong())
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
        } else {
            collection
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .orderBy(FieldPath.documentId(), Query.Direction.DESCENDING)
                .startAfter(beforeTs, before.messageId)
                .limit(limit.toLong())
                .get(com.google.firebase.firestore.Source.SERVER)
                .await()
        }
        val nextCursor = snapshot.documents.lastOrNull()?.let { doc ->
            val ts = doc.getTimestamp("timestamp")?.toDate() ?: return@let null
            MessageSyncCursor(ts, doc.id)
        }
        val hasMore = snapshot.documents.size >= limit
        val messages = handleMessagesSnapshot(
            documents = snapshot.documents,
            error = null,
            conversationId = conversationId,
            cutoffDate = cutoffDate,
            hydrateReactions = false,
        ).getOrThrow()
        MessageHistoryPage(messages = messages, nextCursor = nextCursor, hasMore = hasMore)
    }

    /** Compat: mensajes anteriores (asc) — usa `fetchOlderMessages` y ordena. */
    suspend fun fetchMessagesBefore(
        conversationId: String,
        before: MessageSyncCursor,
        limit: Int,
        cutoffDate: Date? = null,
    ): Result<List<EnhancedMessage>> = fetchOlderMessages(conversationId, before, cutoffDate, limit)
        .map { page -> page.messages.sortedBy { it.timestamp } }

    suspend fun fetchMessage(
        conversationId: String,
        messageId: String,
    ): Result<EnhancedMessage?> = runCatching {
        preloadEncryption(conversationId)
        val document = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId).get().await()
        if (!document.exists()) return@runCatching null
        val data = document.data ?: return@runCatching null
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        @Suppress("UNCHECKED_CAST")
        val deletedFor = data["deletedFor"] as? List<*>
        if (currentUserId != null && deletedFor?.filterIsInstance<String>()?.contains(currentUserId) == true) {
            return@runCatching null
        }
        @Suppress("UNCHECKED_CAST")
        val vanishedFor = data["vanishedFor"] as? List<*>
        if (currentUserId != null && vanishedFor?.filterIsInstance<String>()?.contains(currentUserId) == true) {
            return@runCatching null
        }
        var message = buildEnhancedMessage(data, document.id, conversationId)
        val reactions = fetchReactionMap(conversationId, listOf(message.id))
        message = message.copy(
            reactions = mergeLegacyAndLiveReactions(message.reactions, reactions[message.id]),
        )
        message
    }

    /**
     * ≡ `handleMessagesSnapshot` — cutoff, local-first, reacciones, delivered.
     */
    private suspend fun handleMessagesSnapshot(
        documents: List<com.google.firebase.firestore.DocumentSnapshot>?,
        error: Throwable?,
        conversationId: String,
        cutoffDate: Date? = null,
        hydrateReactions: Boolean = true,
    ): Result<List<EnhancedMessage>> {
        if (error != null) return Result.failure(error)
        val docs = documents.orEmpty()
        if (docs.isEmpty()) return Result.success(emptyList())

        preloadEncryption(conversationId)

        val cutoffDateToUse = cutoffDate ?: conversationCutoffs[conversationId]

        var messages: List<EnhancedMessage> = if (LocalFirstMessagingSettings.isEnabled) {
            val queryDocs = docs.filterIsInstance<com.google.firebase.firestore.QueryDocumentSnapshot>()
            if (queryDocs.size == docs.size) {
                buildMessagesFromSnapshotUsingLocalCache(queryDocs, conversationId, cutoffDateToUse)
            } else {
                buildMessagesWithoutLocalFirst(docs, conversationId, cutoffDateToUse)
            }
        } else {
            buildMessagesWithoutLocalFirst(docs, conversationId, cutoffDateToUse)
        }

        if (hydrateReactions && messages.isNotEmpty()) {
            val fetchedReactions = fetchReactionMap(conversationId, messages.map { it.id })
            messages = messages.map { message ->
                message.copy(
                    reactions = mergeLegacyAndLiveReactions(
                        message.reactions,
                        fetchedReactions[message.id],
                    ),
                )
            }
        }

        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId != null) {
            markMessagesAsDelivered(messages, conversationId, currentUserId)
        }
        return Result.success(messages)
    }

    private suspend fun buildMessagesWithoutLocalFirst(
        documents: List<com.google.firebase.firestore.DocumentSnapshot>,
        conversationId: String,
        cutoffDate: Date?,
    ): List<EnhancedMessage> {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val messages = mutableListOf<EnhancedMessage>()
        for (doc in documents) {
            val data = doc.data ?: continue
            @Suppress("UNCHECKED_CAST")
            val deletedFor = data["deletedFor"] as? List<*>
            if (currentUserId != null && deletedFor?.filterIsInstance<String>()?.contains(currentUserId) == true) {
                continue
            }
            @Suppress("UNCHECKED_CAST")
            val vanishedFor = data["vanishedFor"] as? List<*>
            if (currentUserId != null && vanishedFor?.filterIsInstance<String>()?.contains(currentUserId) == true) {
                continue
            }
            val msgTimestamp = (data["timestamp"] as? Timestamp)?.toDate()
            if (cutoffDate != null && msgTimestamp != null && !msgTimestamp.after(cutoffDate)) {
                continue
            }
            messages += buildEnhancedMessage(data, doc.id, conversationId)
        }
        return messages
    }

    /** Punto de corte en memoria (≡ `deletedAtCutoff(for:)`). */
    fun deletedAtCutoff(conversationId: String): Date? = conversationCutoffs[conversationId]

    /** ≡ `isConversationArchived` — set en memoria desde `fetchConversations`. */
    fun isConversationArchived(conversationId: String, userId: String = ""): Boolean =
        archivedConversationIds.contains(conversationId)

    /** Materializa un borrador exclusivamente mediante el coordinador autoritativo V2. */
    suspend fun materializeConversation(otherUserId: String, currentUserId: String): Result<String> = runCatching {
        require(otherUserId.isNotBlank() && currentUserId.isNotBlank())
        val coordinator = MessageRequestService()
        when (val route = coordinator.resolveRoute(otherUserId)) {
            is DirectMessageRoute.Conversation -> route.id
            is DirectMessageRoute.ConversationDraft ->
                coordinator.activateConversationDraft(otherUserId, route.threadId)
            is DirectMessageRoute.IncomingRequest ->
                coordinator.acceptIncomingThread(route.threadId).conversationId
            is DirectMessageRoute.OutgoingRequest -> throw MessageRequestRequiredException()
        }
    }

    suspend fun decryptMessageContent(content: String, conversationId: String): String =
        EncryptionService.decryptChatMessage(content, conversationId) ?: content

    // sendBuzz → ChatServiceBuzz.kt (≡ ChatService+Buzz.swift)

    /**
     * ≡ `listenToMessages(conversationId:cutoffDate:limit:replaceExisting:)`.
     */
    fun listenToMessages(
        conversationId: String,
        cutoffDate: Date? = null,
        limit: Int = 50,
        replaceExisting: Boolean = true,
        onUpdate: (Result<List<EnhancedMessage>>) -> Unit,
    ) {
        if (conversationId.isBlank()) return
        if (!replaceExisting && activeListeners[conversationId] != null) return

        val generation = beginListenerGeneration(conversationId)
        activeListeners.remove(conversationId)?.remove()

        fun attachListener() {
            if (!isCurrentListenerGeneration(generation, conversationId)) return
            val listener = db.collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.ASCENDING)
                .limitToLast(limit.toLong())
                .addSnapshotListener { snapshot, error ->
                    if (!isCurrentListenerGeneration(generation, conversationId)) return@addSnapshotListener
                    scope.launch {
                        val result = handleMessagesSnapshot(
                            documents = snapshot?.documents,
                            error = error,
                            conversationId = conversationId,
                            cutoffDate = cutoffDate,
                        )
                        withContext(Dispatchers.Main) { onUpdate(result) }
                    }
                }
            activeListeners[conversationId] = listener
        }

        scope.launch {
            preloadEncryption(conversationId)
            if (!isCurrentListenerGeneration(generation, conversationId)) return@launch
            attachListener()
        }
    }

    fun removeMessagesListener(conversationId: String) {
        bumpListenerGeneration(conversationId)
        activeListeners.remove(conversationId)?.remove()
    }

    fun listenToTypingIndicators(conversationId: String) {
        if (conversationId.isBlank()) return
        val key = typingListenerKey(conversationId)
        if (activeListeners[key] != null) return
        activeListeners[key] = db.collection("conversations").document(conversationId)
            .collection("typing")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                val users = snapshot?.documents.orEmpty().map { it.id }.toSet()
                _typingUsers.value = _typingUsers.value + (conversationId to users)
            }
    }

    fun removeTypingListener(conversationId: String) {
        val key = typingListenerKey(conversationId)
        activeListeners.remove(key)?.remove()
        _typingUsers.value = _typingUsers.value - conversationId
    }

    fun listenToConversationPreferences(
        conversationId: String,
        onUpdate: (
            forwarding: Map<String, Boolean>,
            buzz: Map<String, Boolean>,
            vanishActive: Boolean,
            vanishTimer: VanishMessageTimer,
            enabledNoticeId: String?,
            disabledNoticeId: String?,
        ) -> Unit,
    ) {
        if (conversationId.isBlank()) return
        val key = prefsListenerKey(conversationId)
        if (activeListeners[key] != null) return
        val generation = beginListenerGeneration(key)
        activeListeners.remove(key)?.remove()
        activeListeners[key] = db.collection("conversations").document(conversationId)
            .addSnapshotListener { snapshot, error ->
                if (!isCurrentListenerGeneration(generation, key)) return@addSnapshotListener
                if (error != null) return@addSnapshotListener
                val data = snapshot?.data.orEmpty()
                @Suppress("UNCHECKED_CAST")
                val forwarding = (data["forwardingPreferences"] as? Map<String, Boolean>).orEmpty()
                @Suppress("UNCHECKED_CAST")
                val buzz = (data["buzzPreferences"] as? Map<String, Boolean>).orEmpty()
                val active = data["vanishModeActive"] as? Boolean ?: false
                val timer = VanishMessageTimer.fromStored(data["vanishMessageTimer"] as? String)
                val enabledNoticeId = data["vanishSettingsNoticeMessageId"] as? String
                val disabledNoticeId = data["vanishDisabledNoticeMessageId"] as? String
                scope.launch(Dispatchers.Main) {
                    onUpdate(forwarding, buzz, active, timer, enabledNoticeId, disabledNoticeId)
                }
            }
    }

    fun removeConversationPreferencesListener(conversationId: String) {
        val key = prefsListenerKey(conversationId)
        bumpListenerGeneration(key)
        activeListeners.remove(key)?.remove()
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

    /**
     * ≡ `sendMessage(_:useServerTimestamp:completion:)` —
     * offline → pending; ack timeout → pending + cola; error escritura → failed; éxito → sent + preview.
     */
    suspend fun sendMessage(
        message: EnhancedMessage,
        useServerTimestamp: Boolean = true,
    ): Result<EnhancedMessage> {
        if (shouldQueueFirestoreOutbox()) {
            val pending = message.copy(status = MessageStatus.PENDING)
            queueOfflineMessage(pending, useServerTimestamp)
            return Result.success(pending)
        }
        val conversationId = message.conversationId
        val messageId = message.id
        val messageRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
        val messageData = messageToFirestoreData(message, useServerTimestamp)

        // Escritura no cancelada al timeout (≡ setData callback iOS sigue vivo tras el sleep).
        val writeJob = scope.async {
            runCatching { messageRef.set(messageData).await() }
        }
        val writeResult = withTimeoutOrNull(SEND_ACK_TIMEOUT_MS) { writeJob.await() }

        if (writeResult == null) {
            val pending = message.copy(status = MessageStatus.PENDING)
            queueOfflineMessage(pending, useServerTimestamp)
            LocalPersistenceService.saveMessagesInBackground(
                listOf(pending),
                conversationId,
                sync = false,
            )
            updateLocalMessageStatus(conversationId, messageId, MessageStatus.PENDING)
            // Late ack: si el set acabó bien, retirar cola (mismo doc id, reenvío idempotente).
            scope.launch {
                writeJob.await().onSuccess {
                    LocalPersistenceService.deleteAction(messageId)
                    updateLocalMessageStatus(conversationId, messageId, MessageStatus.SENT)
                }
            }
            return Result.success(pending)
        }

        writeResult.onFailure { error ->
            updateLocalMessageStatus(conversationId, messageId, MessageStatus.FAILED)
            runCatching {
                updateMessageStatus(conversationId, messageId, MessageStatus.FAILED)
            }
            return Result.failure(error)
        }

        LocalPersistenceService.deleteAction(messageId)
        updateConversation(
            conversationId = conversationId,
            lastMessage = neutralConversationPreview(message.type),
            senderId = message.senderId,
            messageType = message.type,
        )
        LocalPersistenceService.upsertConversationPreview(message)
        updateMessageStatus(conversationId, messageId, MessageStatus.SENT)
        return Result.success(message.copy(status = MessageStatus.SENT))
    }

    /**
     * Map EnhancedMessage → Firestore (cuerpo de `sendMessage` en ChatService.swift).
     * No es archivo aparte: en iOS vive inline en sendMessage.
     */
    private fun messageToFirestoreData(
        message: EnhancedMessage,
        useServerTimestamp: Boolean,
    ): Map<String, Any?> {
        val data = mutableMapOf<String, Any?>(
            "id" to message.id,
            "conversationId" to message.conversationId,
            "senderId" to message.senderId,
            "type" to message.type.raw,
            "status" to MessageStatus.SENT.raw,
            "isRead" to message.isRead,
            "isDeleted" to message.isDeleted,
            "isViewed" to message.isViewed,
        )
        message.content?.let { data["content"] = it }
        message.mediaObjectPath?.let { data["mediaObjectPath"] = it }
            ?: message.mediaUrl?.let { data["mediaUrl"] = it }
        message.thumbnailObjectPath?.let { data["thumbnailObjectPath"] = it }
            ?: message.thumbnailUrl?.let { data["thumbnailUrl"] = it }
        message.mediaEncryption?.toJson()?.let { json ->
            data["mediaEncryption"] = json.keys().asSequence().associateWith { json.get(it) }
        }
        message.thumbnailEncryption?.toJson()?.let { json ->
            data["thumbnailEncryption"] = json.keys().asSequence().associateWith { json.get(it) }
        }
        message.duration?.let { data["duration"] = it }
        message.audioWaveform?.takeIf { it.isNotEmpty() }?.let {
            data["audioWaveform"] = it.take(64).map(Float::toDouble)
        }
        message.fileName?.let { data["fileName"] = it }
        message.fileSize?.let { data["fileSize"] = it }
        message.mediaWidth?.let { data["mediaWidth"] = it }
        message.mediaHeight?.let { data["mediaHeight"] = it }
        // Ubicación: coords/name/address solo en `content` cifrado (≡ send path iOS).
        // No escribir latitude/longitude/locationName/locationAddress en claro.
        message.isLiveLocation?.let { data["isLiveLocation"] = it }
        message.liveLocationExpiresAt?.let { data["liveLocationExpiresAt"] = Timestamp(it) }
        message.liveLocationDuration?.let { data["liveLocationDuration"] = it }
        message.liveLocationStoppedAt?.let { data["liveLocationStoppedAt"] = Timestamp(it) }
        message.liveLocationSessionId?.let { data["liveLocationSessionId"] = it }
        message.locationUpdatedAt?.let { data["locationUpdatedAt"] = Timestamp(it) }
        message.replyTo?.let { data["replyTo"] = it }
        message.storyReplyData?.let { data["storyReplyData"] = it }
        message.sharedMomentData?.let { data["sharedMomentData"] = it }
        message.sharedStoryData?.let { data["sharedStoryData"] = it }
        message.sharedProfileData?.let { data["sharedProfileData"] = it }
        message.expirationDate?.let { data["expirationDate"] = Timestamp(it) }
        message.mediaBatchId?.let { data["mediaBatchId"] = it }
        message.textOverlayLive?.let { data["textOverlayLive"] = it }
        message.textOverlays?.let { data["textOverlays"] = it.map(StoryTextOverlayMetadata::toMap) }
        message.stickers?.let { data["stickers"] = it.map(StickerData::toMap) }
        message.drawingData?.let { data["drawingData"] = it }
        message.viewedBy?.let { data["viewedBy"] = it }
        message.allowReplay?.let { data["allowReplay"] = it }
        message.replayedBy?.let { data["replayedBy"] = it }
        if (message.isViewOnce) data["isViewOnce"] = true
        message.readBy?.let { data["readBy"] = it }
        message.readAtBy?.let { values -> data["readAtBy"] = values.mapValues { Timestamp(it.value) } }
        message.starredBy?.takeIf { it.isNotEmpty() }?.let { data["starredBy"] = it }
        if (message.isForwarded == true) data["isForwarded"] = true
        if (message.isVanishModeMessage) data["isVanishModeMessage"] = true
        message.vanishExpiresAt?.let { data["vanishExpiresAt"] = Timestamp(it) }
        data["timestamp"] =
            if (useServerTimestamp) FieldValue.serverTimestamp() else Timestamp(message.timestamp)
        return data
    }

    /** ≡ cola offline de `sendMessage` / media en ChatService.swift. */
    private fun queueOfflineMessage(message: EnhancedMessage, useServerTimestamp: Boolean) {
        val pending = message.copy(status = MessageStatus.PENDING)
        val payloadJson = JSONObject().apply {
            put("message", pending.toJson())
            put("useServerTimestamp", useServerTimestamp)
        }
        LocalPersistenceService.saveAction(
            CachedAction(
                id = message.id,
                type = CachedAction.ActionType.MESSAGE.raw,
                payloadData = payloadJson.toString().toByteArray(),
            ),
        )
    }

    /**
     * ≡ `removeListener(for:)` — mensajes + typing + prefs + reacciones + buzz (con bump de gen).
     */
    fun removeListener(conversationId: String) {
        if (conversationId.isBlank()) return
        bumpListenerGeneration(conversationId)
        activeListeners.remove(conversationId)?.remove()

        val reactionsKey = "reactions_$conversationId"
        bumpListenerGeneration(reactionsKey)
        removeMessageReactionsListener(conversationId)

        val prefsKey = prefsListenerKey(conversationId)
        bumpListenerGeneration(prefsKey)
        activeListeners.remove(prefsKey)?.remove()

        val buzzKey = "buzz_$conversationId"
        bumpListenerGeneration(buzzKey)
        removeBuzzListener(conversationId)

        val typingKey = typingListenerKey(conversationId)
        activeListeners.remove(typingKey)?.remove()
        _typingUsers.value = _typingUsers.value - conversationId
    }

    /** ≡ `removeAllListeners()` — logout / cambio de usuario. */
    fun removeAllListeners() {
        activeListeners.values.forEach { it.remove() }
        activeListeners.clear()
        listenerGenerations.clear()
        _typingUsers.value = emptyMap()
        conversationCutoffs.clear()
        archivedConversationIds = emptySet()
        removeAllMessageReactionsListeners()
        removeAllBuzzListeners()
        stopConversationsListener()
    }

    /** ≡ `removeConversationsListener(for:)`. */
    fun removeConversationsListener(userId: String) {
        val listenerKey = "conversations_$userId"
        activeListeners.remove(listenerKey)?.remove()
        // stopConversationsListener cubre el registro legacy hasta trozo fetchConversations.
        stopConversationsListener()
    }

    /**
     * Port de `updateUserDataInAllConversations`: al cambiar nombre o avatar hay que refrescar la
     * copia desnormalizada que cada conversación guarda del participante.
     */
    suspend fun updateUserDataInAllConversations(userId: String, username: String, profileImagePath: String?): Result<Unit> = runCatching {
        val snapshot = db.collection("conversations").whereArrayContains("participants", userId).get().await()
        if (snapshot.documents.isEmpty()) return@runCatching
        val batch = db.batch()
        snapshot.documents.forEach { doc ->
            batch.update(
                doc.reference,
                mapOf(
                    "participantData.$userId.username" to username,
                    "participantData.$userId.profileImagePath" to profileImagePath.orEmpty(),
                    "participantData.$userId.lastUpdated" to FieldValue.serverTimestamp(),
                ),
            )
        }
        batch.commit().await()
    }

    /**
     * Port de `createBidirectionalConversation`: E2E wrappedKeys + participantData.
     * Sin clave del peer → [EncryptionService.EncryptionError.PeerKeyUnavailable].
     */
    suspend fun createBidirectionalConversation(
        user1Id: String,
        user2Id: String,
        initialMessage: String? = null,
    ): Result<String> = runCatching {
        val participants = listOf(user1Id, user2Id).sorted()
        val conversationRef = db.collection("conversations").document()
        val conversationId = conversationRef.id

        val user1 = fetchUserCached(user1Id)
            ?: error("Failed to load user data: $user1Id")
        val user2 = fetchUserCached(user2Id)
            ?: error("Failed to load user data: $user2Id")

        EncryptionService.ensureChatIdentity()
        val sharedEncryptionKey = EncryptionService.randomConversationKey()
        val wrappedKeys = EncryptionService.buildWrappedConversationKeys(
            participants,
            sharedEncryptionKey,
            wrappedBy = user1Id,
        )
        if (wrappedKeys.size != participants.size) {
            throw EncryptionService.EncryptionError.PeerKeyUnavailable
        }

        val participantData = mapOf(
            user1Id to mapOf(
                "userId" to user1.id,
                "username" to user1.username,
                "profileImagePath" to (user1.profileImagePath ?: ""),
                "lastUpdated" to FieldValue.serverTimestamp(),
            ),
            user2Id to mapOf(
                "userId" to user2.id,
                "username" to user2.username,
                "profileImagePath" to (user2.profileImagePath ?: ""),
                "lastUpdated" to FieldValue.serverTimestamp(),
            ),
        )
        conversationRef.set(
            mapOf(
                "participants" to participants,
                "lastMessage" to "",
                "timestamp" to FieldValue.serverTimestamp(),
                "readStatus" to mapOf(user1Id to true, user2Id to false),
                "participantData" to participantData,
                "wrappedKeys" to wrappedKeys,
                "conversationKeyVersion" to 1,
                "encryptionVersion" to "3.0",
            ),
        ).await()

        EncryptionService.cacheConversationKeyLocally(conversationId, sharedEncryptionKey)

        val trimmed = initialMessage?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            sendTextMessage(conversationId, user1Id, trimmed).getOrThrow()
        }
        conversationId
    }

    private suspend fun fetchUserCached(userId: String): com.moments.android.models.AppUser? =
        suspendCancellableCoroutine { cont ->
            UserCacheService.getUser(userId) { user ->
                if (cont.isActive) cont.resume(user)
            }
        }

    /** ≡ `sendEphemeralMessage` — cifra texto si hay content. */
    suspend fun sendEphemeralMessage(
        conversationId: String,
        senderId: String,
        content: String? = null,
        mediaUrl: String? = null,
        mediaObjectPath: String? = null,
        thumbnailUrl: String? = null,
        thumbnailObjectPath: String? = null,
        mediaEncryption: com.moments.android.views.messaging.core.EncryptedChatMediaMetadata? = null,
        thumbnailEncryption: com.moments.android.views.messaging.core.EncryptedChatMediaMetadata? = null,
        expirationHours: Int = 24,
        storyReplyData: Map<String, String>? = null,
        messageId: String? = null,
    ): Result<EnhancedMessage> = runCatching {
        val encryptedContent = content?.let { EncryptionService.encryptChatMessage(it, conversationId) }
        val expirationDate = Date(System.currentTimeMillis() + expirationHours.coerceAtLeast(1) * 60L * 60L * 1000L)
        val message = EnhancedMessage(
            id = messageId ?: UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.EPHEMERAL,
            content = encryptedContent,
            mediaUrl = mediaUrl,
            thumbnailUrl = thumbnailUrl,
            mediaObjectPath = mediaObjectPath,
            thumbnailObjectPath = thumbnailObjectPath,
            mediaEncryption = mediaEncryption,
            thumbnailEncryption = thumbnailEncryption,
            timestamp = Date(),
            status = MessageStatus.SENDING,
            expirationDate = expirationDate,
            storyReplyData = storyReplyData,
        )
        sendMessage(message, useServerTimestamp = true).getOrThrow()
    }

    /** ≡ `canSendMessage` — solo bloqueos mutuos (no PrivacyService / following). */
    suspend fun canSendMessage(senderId: String, userId: String): Result<Boolean> = runCatching {
        val firestore = FirestoreService()
        val recipient = firestore.fetchUserProfile(userId)
        if (senderId in recipient.blockedUsers) return@runCatching false
        val sender = firestore.fetchUserProfile(senderId)
        if (userId in sender.blockedUsers) return@runCatching false
        true
    }

    /**
     * Port de `sendStoryReplyMessage`: responder a una historia desde el chat. El contenido va
     * cifrado como cualquier mensaje y `storyReplyData` viaja en claro (metadatos de la historia).
     */
    suspend fun sendStoryReplyMessage(
        conversationId: String,
        senderId: String,
        content: String,
        storyReplyData: Map<String, String>,
        isVanishModeMessage: Boolean = false,
    ): Result<EnhancedMessage> = runCatching {
        val encrypted = EncryptionService.encryptChatMessage(content, conversationId)
        val message = EnhancedMessage(
            id = UUID.randomUUID().toString(),
            conversationId = conversationId,
            senderId = senderId,
            type = MessageType.TEXT,
            content = encrypted,
            timestamp = Date(),
            status = MessageStatus.SENDING,
            isVanishModeMessage = isVanishModeMessage,
            storyReplyData = storyReplyData,
        )
        sendMessage(message, useServerTimestamp = true).getOrThrow()
    }

    /**
     * Port de `updateLiveLocationMessage`: refresca las coordenadas de una ubicación en directo.
     * El payload se recifra en cada actualización, como en iOS.
     */
    suspend fun updateLiveLocationMessage(
        conversationId: String,
        messageId: String,
        latitude: Double,
        longitude: Double,
    ): Result<Unit> = runCatching {
        val payload = ChatLocationPayload(lat = latitude, lng = longitude).encodedJSON().orEmpty()
        val encrypted = EncryptionService.encryptChatMessage(payload, conversationId)
        db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
            .update(
                mapOf(
                    "content" to encrypted,
                    "locationUpdatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
    }

    /** Port de `restoreConversation`: deshace el borrado local de la conversación. */
    suspend fun restoreConversation(conversationId: String, userId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId)
            .update("deletedFor", FieldValue.arrayRemove(userId)).await()
    }

    /** Port de `setLastMessageReaction`: reacción mostrada en la vista previa de la bandeja. */
    suspend fun setLastMessageReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
        byUserId: String,
    ): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).update(
            mapOf(
                "lastMessageReaction" to mapOf(
                    "messageId" to messageId,
                    "emoji" to emoji,
                    "byUserId" to byUserId,
                ),
            ),
        ).await()
    }

    /** Port de `clearLastMessageReaction`. */
    suspend fun clearLastMessageReaction(conversationId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId)
            .update("lastMessageReaction", FieldValue.delete()).await()
    }

    /**
     * ≡ `markAllPendingMessagesAsDelivered()` — barre todas las conversaciones del usuario.
     */
    suspend fun markAllPendingMessagesAsDelivered(): Result<Unit> = runCatching {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@runCatching
        val conversations = db.collection("conversations")
            .whereArrayContains("participants", currentUserId)
            .get()
            .await()
            .documents
        for (conversationDoc in conversations) {
            val conversationId = conversationDoc.id
            val messages = db.collection("conversations").document(conversationId)
                .collection("messages")
                .whereNotEqualTo("senderId", currentUserId)
                .whereEqualTo("status", MessageStatus.SENT.raw)
                .get()
                .await()
                .documents
            for (messageDoc in messages) {
                runCatching {
                    updateMessageStatus(conversationId, messageDoc.id, MessageStatus.DELIVERED)
                }
            }
        }
    }

    /** Variante por conversación (call sites que ya pasan cid). */
    suspend fun markAllPendingMessagesAsDelivered(
        conversationId: String,
        currentUserId: String,
    ): Result<Unit> = runCatching {
        val snapshot = db.collection("conversations").document(conversationId)
            .collection("messages")
            .whereEqualTo("status", MessageStatus.SENT.raw)
            .get()
            .await()
        val batch = db.batch()
        var pending = 0
        snapshot.documents.forEach { doc ->
            if ((doc.data?.get("senderId") as? String) == currentUserId) return@forEach
            batch.update(doc.reference, "status", MessageStatus.DELIVERED.raw)
            pending++
        }
        if (pending > 0) batch.commit().await()
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
                update["readAtBy.$readerId"] = FieldValue.serverTimestamp()
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

    /** ≡ `markConversationAsRead` — iOS no corta por incógnito aquí (sí en `markMessagesAsRead`). */
    suspend fun markConversationAsRead(conversationId: String, userId: String) {
        db.collection("conversations").document(conversationId).update(
            mapOf(
                "readStatus.$userId" to true,
                "lastReadAt.$userId" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun markConversationAsUnread(conversationId: String, userId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).update("readStatus.$userId", false).await()
    }

    suspend fun archiveConversation(conversationId: String, userId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).update("archivedByUserIds", FieldValue.arrayUnion(userId)).await()
    }

    suspend fun unarchiveConversation(conversationId: String, userId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).update("archivedByUserIds", FieldValue.arrayRemove(userId)).await()
    }

    // Fijar y silenciar conversaciones: mismo contrato Firestore que iOS (array de ids + mapa de
    // marcas de tiempo por usuario). El modelo, el orden de la bandeja y el menú ya existían;
    // faltaban estas escrituras, así que la acción no se podía completar.

    suspend fun pinConversation(conversationId: String, userId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).update(
            mapOf(
                "pinnedByUserIds" to FieldValue.arrayUnion(userId),
                "pinnedByTimestamps.$userId" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun unpinConversation(conversationId: String, userId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).update(
            mapOf(
                "pinnedByUserIds" to FieldValue.arrayRemove(userId),
                "pinnedByTimestamps.$userId" to FieldValue.delete(),
            ),
        ).await()
    }

    suspend fun muteConversation(conversationId: String, userId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).update(
            mapOf(
                "mutedByUserIds" to FieldValue.arrayUnion(userId),
                "mutedByTimestamps.$userId" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    suspend fun unmuteConversation(conversationId: String, userId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).update(
            mapOf(
                "mutedByUserIds" to FieldValue.arrayRemove(userId),
                "mutedByTimestamps.$userId" to FieldValue.delete(),
            ),
        ).await()
    }

    /** ≡ `updateLocalMessageStatus` → NotificationCenter `MessageStatusUpdated`. */
    fun updateLocalMessageStatus(conversationId: String, messageId: String, status: MessageStatus) {
        _messageStatusUpdates.tryEmit(MessageStatusUpdate(conversationId, messageId, status))
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

    // sendViewOnceMessage → ChatServiceSharingAndViewOnce.kt (≡ ChatService+SharingAndViewOnce.swift)

    suspend fun sendGiphyReferenceMessage(
        conversationId: String,
        senderId: String,
        type: MessageType,
        giphyId: String,
        mediaUrl: String,
        width: Int,
        height: Int,
        messageId: String,
        isVanishModeMessage: Boolean,
        replyTo: String?,
    ): Result<EnhancedMessage> = sendMessage(
        EnhancedMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = senderId,
            type = type,
            mediaUrl = mediaUrl,
            fileName = "giphy_$giphyId",
            mediaWidth = width.takeIf { it > 0 },
            mediaHeight = height.takeIf { it > 0 },
            timestamp = Date(),
            status = MessageStatus.SENDING,
            replyTo = replyTo,
            isVanishModeMessage = isVanishModeMessage,
        ),
        useServerTimestamp = true,
    )

    suspend fun sendStaticLocationMessage(
        conversationId: String,
        senderId: String,
        latitude: Double,
        longitude: Double,
        name: String?,
        address: String?,
        messageId: String,
        isVanishModeMessage: Boolean,
    ): Result<EnhancedMessage> = sendLocationMessage(
        conversationId, senderId, latitude, longitude, name, address, false, null, null, null, messageId, isVanishModeMessage,
    )

    suspend fun sendLiveLocationMessage(
        conversationId: String,
        senderId: String,
        latitude: Double,
        longitude: Double,
        name: String?,
        address: String?,
        duration: LiveLocationDuration,
        sessionId: String,
        expiresAt: Date,
        messageId: String,
        isVanishModeMessage: Boolean,
    ): Result<EnhancedMessage> = sendLocationMessage(
        conversationId, senderId, latitude, longitude, name, address, true, duration.firestoreValue, sessionId, expiresAt, messageId, isVanishModeMessage,
    )

    /**
     * ≡ iOS `sendStaticLocationMessage` / `sendLiveLocationMessage`:
     * coords + lugar solo en `content` cifrado. No rellenar latitude/longitude/name/address
     * en el EnhancedMessage de envío (si no, `messageToFirestoreData` los subiría en claro).
     */
    private suspend fun sendLocationMessage(
        conversationId: String,
        senderId: String,
        latitude: Double,
        longitude: Double,
        name: String?,
        address: String?,
        isLive: Boolean,
        duration: String?,
        sessionId: String?,
        expiresAt: Date?,
        messageId: String,
        isVanishModeMessage: Boolean,
    ): Result<EnhancedMessage> = runCatching {
        val payload = ChatLocationPayload(
            lat = latitude,
            lng = longitude,
            name = name,
            address = address,
        ).encodedJSON().orEmpty()
        val encrypted = EncryptionService.encryptChatMessage(payload, conversationId)
        sendMessage(
            EnhancedMessage(
                id = messageId,
                conversationId = conversationId,
                senderId = senderId,
                type = MessageType.LOCATION,
                content = encrypted,
                isLiveLocation = isLive,
                liveLocationDuration = duration,
                liveLocationSessionId = sessionId,
                liveLocationExpiresAt = expiresAt,
                locationUpdatedAt = if (isLive) Date() else null,
                timestamp = Date(),
                status = MessageStatus.SENDING,
                isVanishModeMessage = isVanishModeMessage,
            ),
            useServerTimestamp = true,
        ).getOrThrow()
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

    private var typingAutoStopJob: kotlinx.coroutines.Job? = null

    /** ≡ `startTyping` — payload userId+timestamp; auto-stop a los 3s. */
    fun startTyping(conversationId: String, userId: String) {
        if (conversationId.isBlank() || userId.isBlank()) return
        scope.launch {
            runCatching {
                db.collection("conversations").document(conversationId)
                    .collection("typing").document(userId)
                    .set(
                        mapOf(
                            "userId" to userId,
                            "timestamp" to FieldValue.serverTimestamp(),
                        ),
                    )
                    .await()
            }
        }
        typingAutoStopJob?.cancel()
        typingAutoStopJob = scope.launch {
            delay(TYPING_TIMEOUT_MS)
            stopTyping(conversationId, userId)
        }
    }

    fun stopTyping(conversationId: String, userId: String) {
        if (conversationId.isBlank() || userId.isBlank()) return
        typingAutoStopJob?.cancel()
        typingAutoStopJob = null
        scope.launch {
            runCatching {
                db.collection("conversations").document(conversationId)
                    .collection("typing").document(userId).delete().await()
            }
        }
    }

    suspend fun editMessage(
        conversationId: String,
        messageId: String,
        newContent: String,
    ): Result<Unit> = runCatching {
        val encrypted = EncryptionService.encryptChatMessage(newContent, conversationId)
        db.collection("conversations").document(conversationId).collection("messages").document(messageId)
            .update(mapOf("content" to encrypted, "editedAt" to FieldValue.serverTimestamp()))
            .await()
    }

    /** ≡ `addReaction` — subcolección `messageReactions/{userId}` con toggle. */
    suspend fun addReaction(
        conversationId: String,
        messageId: String,
        emoji: String,
        userId: String,
    ): Result<Unit> = runCatching {
        LocalPersistenceService.toggleMessageReactionLocally(messageId, emoji, userId)
        val reactionRef = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId)
            .collection("messageReactions").document(userId)
        val snapshot = reactionRef.get().await()
        val existingEmoji = snapshot.data?.get("emoji") as? String
        if (existingEmoji == emoji) {
            reactionRef.delete().await()
            return@runCatching
        }
        if (snapshot.exists()) {
            reactionRef.update(
                mapOf(
                    "emoji" to emoji,
                    "timestamp" to FieldValue.serverTimestamp(),
                ),
            ).await()
        } else {
            reactionRef.set(
                mapOf(
                    "conversationId" to conversationId,
                    "messageId" to messageId,
                    "userId" to userId,
                    "emoji" to emoji,
                    "timestamp" to FieldValue.serverTimestamp(),
                ),
            ).await()
        }
    }

    /**
     * ≡ `deleteMessage(conversationId:messageId:)` — soft-delete (content + mediaUrl).
     * Alias [deleteMessageForEveryone] para call sites Android existentes.
     */
    suspend fun deleteMessage(conversationId: String, messageId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).collection("messages").document(messageId)
            .update(
                mapOf(
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp(),
                    "content" to FieldValue.delete(),
                    "mediaUrl" to FieldValue.delete(),
                ),
            )
            .await()
        LocalPersistenceService.markMessageDeletedForEveryone(conversationId, messageId)
    }

    suspend fun deleteMessageForEveryone(conversationId: String, messageId: String): Result<Unit> =
        deleteMessage(conversationId, messageId)

    /** ≡ `deleteMessageWithCleanup` — borra campos media/overlays y archivos Storage. */
    suspend fun deleteMessageWithCleanup(conversationId: String, messageId: String): Result<Unit> = runCatching {
        val document = db.collection("conversations").document(conversationId)
            .collection("messages").document(messageId).get().await()
        if (!document.exists()) error("Mensaje no encontrado")
        val data = document.data.orEmpty()
        val mediaResources = listOfNotNull(
            data["mediaObjectPath"] as? String,
            data["thumbnailObjectPath"] as? String,
            data["mediaUrl"] as? String,
            data["thumbnailUrl"] as? String,
        ).filter { it.isNotEmpty() }

        document.reference.update(
            mapOf(
                "isDeleted" to true,
                "deletedAt" to FieldValue.serverTimestamp(),
                "content" to FieldValue.delete(),
                "mediaUrl" to FieldValue.delete(),
                "thumbnailUrl" to FieldValue.delete(),
                "mediaObjectPath" to FieldValue.delete(),
                "thumbnailObjectPath" to FieldValue.delete(),
                "mediaEncryption" to FieldValue.delete(),
                "thumbnailEncryption" to FieldValue.delete(),
                "textOverlayLive" to FieldValue.delete(),
                "textOverlays" to FieldValue.delete(),
                "stickers" to FieldValue.delete(),
                "drawingData" to FieldValue.delete(),
            ),
        ).await()
        LocalPersistenceService.markMessageDeletedForEveryone(conversationId, messageId)
        if (mediaResources.isNotEmpty()) {
            deleteMediaFiles(mediaResources)
        }
    }

    suspend fun deleteMessageForMe(conversationId: String, messageId: String, userId: String): Result<Unit> = runCatching {
        db.collection("conversations").document(conversationId).collection("messages").document(messageId)
            .update("deletedFor", FieldValue.arrayUnion(userId))
            .await()
    }

    suspend fun deleteConversationsBetweenUsers(user1Id: String, user2Id: String): Result<Unit> = runCatching {
        val conversations = db.collection("conversations")
            .whereArrayContains("participants", user1Id)
            .get()
            .await()
            .documents
            .filter { document -> (document.get("participants") as? List<*>)?.filterIsInstance<String>()?.contains(user2Id) == true }
        if (conversations.isEmpty()) return@runCatching
        val batch = db.batch()
        conversations.forEach { document ->
            batch.update(
                document.reference,
                mapOf(
                    "deletedFor" to FieldValue.arrayUnion(user1Id),
                    "lastDeletedAt.$user1Id" to FieldValue.serverTimestamp(),
                ),
            )
        }
        batch.commit().await()
    }

    // VanishMode → ChatServiceVanishMode.kt (≡ ChatService+VanishMode.swift)

    /**
     * ≡ `fetchConversations(for:)` — incluye archivadas; auto-restaura `deletedFor`
     * si hay actividad tras `lastDeletedAt`; pobla cutoffs y archived set.
     */
    fun fetchConversations(
        userId: String,
        onUpdate: (Result<List<Conversation>>) -> Unit,
    ) {
        val staleKeys = activeListeners.keys.filter {
            it.startsWith("conversations_") && it != "conversations_$userId"
        }
        for (key in staleKeys) {
            activeListeners.remove(key)?.remove()
        }

        val listenerKey = "conversations_$userId"
        activeListeners.remove(listenerKey)?.remove()
        conversationsListener?.remove()
        conversationsListener = null

        val listener = db.collection("conversations")
            .whereArrayContains("participants", userId)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                scope.launch {
                    if (error != null) {
                        withContext(Dispatchers.Main) {
                            if (FirebaseAuth.getInstance().currentUser == null) {
                                onUpdate(Result.success(emptyList()))
                            } else {
                                onUpdate(Result.failure(error))
                            }
                        }
                        return@launch
                    }
                    val docs = snapshot?.documents.orEmpty()
                    val conversations = mutableListOf<Conversation>()
                    val archivedIds = mutableSetOf<String>()
                    val toRestore = mutableListOf<com.google.firebase.firestore.DocumentReference>()

                    for (doc in docs) {
                        val data = doc.data.orEmpty()
                        @Suppress("UNCHECKED_CAST")
                        val deletedFor = (data["deletedFor"] as? List<*>)?.filterIsInstance<String>().orEmpty()
                        val isDeletedForMe = userId in deletedFor

                        @Suppress("UNCHECKED_CAST")
                        val lastDeletedAtMap = data["lastDeletedAt"] as? Map<String, *>
                        val myDeletedAt = (lastDeletedAtMap?.get(userId) as? Timestamp)?.toDate()

                        if (isDeletedForMe) {
                            val lastMsgTimestamp = (data["timestamp"] as? Timestamp)?.toDate()
                            if (lastMsgTimestamp != null && myDeletedAt != null && lastMsgTimestamp.after(myDeletedAt)) {
                                toRestore += doc.reference
                            } else {
                                continue
                            }
                        }

                        val conversation = parseConversation(doc.id, data, userId) ?: continue
                        @Suppress("UNCHECKED_CAST")
                        val archivedByUserIds = (data["archivedByUserIds"] as? List<*>)
                            ?.filterIsInstance<String>().orEmpty()
                        if (userId in archivedByUserIds) {
                            archivedIds += doc.id
                        }
                        conversations += conversation
                    }

                    for (conversation in conversations) {
                        val convId = conversation.id ?: continue
                        val cutoff = conversation.deletedAtCutoff(userId)
                        if (cutoff != null) {
                            conversationCutoffs[convId] = cutoff
                        } else {
                            conversationCutoffs.remove(convId)
                        }
                    }

                    for (ref in toRestore) {
                        runCatching {
                            ref.update("deletedFor", FieldValue.arrayRemove(userId)).await()
                        }
                    }

                    conversations.sortByDescending { it.timestamp }
                    archivedConversationIds = archivedIds.toSet()

                    val hydrated = hydrateConversationPreviews(conversations)
                    LocalPersistenceService.saveConversations(hydrated, sync = true)
                    withContext(Dispatchers.Main) {
                        onUpdate(Result.success(hydrated))
                    }
                }
            }

        activeListeners[listenerKey] = listener
        conversationsListener = listener
    }

    fun stopConversationsListener() {
        conversationsListener?.remove()
        conversationsListener = null
        val keys = activeListeners.keys.filter { it.startsWith("conversations_") }
        for (key in keys) {
            activeListeners.remove(key)?.remove()
        }
    }

    // MARK: - Inbox preview hydration (≡ hydrateConversationPreviews / resolveLatest…)

    private data class ConversationLatestSnapshot(
        val preview: String,
        val timestamp: Date?,
        val senderId: String?,
        val messageType: MessageType?,
        val viewOncePending: Boolean,
    )

    private suspend fun hydrateConversationPreviews(
        conversations: List<Conversation>,
    ): List<Conversation> {
        if (conversations.isEmpty()) return emptyList()
        return conversations.map { conversation ->
            val snapshot = resolveLatestConversationSnapshot(conversation)
            val resolvedTimestamp = resolvedConversationTimestamp(conversation, snapshot.timestamp)
            val resolvedSenderId = resolvedLastMessageSenderId(
                conversation,
                snapshot.timestamp,
                snapshot.senderId,
            )
            conversation.copy(
                lastMessage = snapshot.preview,
                timestamp = resolvedTimestamp,
                lastMessageSenderId = resolvedSenderId,
                lastMessageType = snapshot.messageType ?: conversation.lastMessageType,
                lastMessageViewOncePending = snapshot.viewOncePending,
            )
        }
    }

    private fun resolvedConversationTimestamp(
        conversation: Conversation,
        latestMessageTimestamp: Date?,
    ): Date {
        var best = conversation.timestamp
        if (latestMessageTimestamp != null && latestMessageTimestamp.after(best)) {
            best = latestMessageTimestamp
        }
        val conversationId = conversation.id
        if (conversationId != null) {
            val localTimestamp = LocalPersistenceService.lastMessageTimestamp(conversationId)
            if (localTimestamp != null && localTimestamp.after(best)) {
                best = localTimestamp
            }
        }
        return best
    }

    private fun resolvedLastMessageSenderId(
        conversation: Conversation,
        latestMessageTimestamp: Date?,
        latestMessageSenderId: String?,
    ): String? {
        if (latestMessageTimestamp != null && latestMessageTimestamp.after(conversation.timestamp)) {
            return latestMessageSenderId ?: conversation.lastMessageSenderId
        }
        return conversation.lastMessageSenderId ?: latestMessageSenderId
    }

    private fun viewOncePendingInSnapshot(
        messageType: MessageType,
        messageSenderId: String?,
        messageData: Map<String, Any?>,
    ): Boolean {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        if (!messageType.isViewOnce) return false
        val senderId = messageSenderId ?: return false
        if (senderId == currentUserId) return false
        @Suppress("UNCHECKED_CAST")
        val viewedBy = (messageData["viewedBy"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        return currentUserId !in viewedBy
    }

    private fun fallbackViewOncePending(conversation: Conversation): Boolean {
        val type = conversation.lastMessageType ?: return false
        if (!type.isViewOnce) return false
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        if (conversation.lastMessageSenderId == currentUserId) return false
        return !(conversation.readStatus[currentUserId] ?: true)
    }

    private fun makeConversationSnapshot(
        preview: String,
        timestamp: Date?,
        senderId: String?,
        messageType: MessageType?,
        messageData: Map<String, Any?>? = null,
        fallbackConversation: Conversation? = null,
    ): ConversationLatestSnapshot {
        val pending = when {
            messageType != null && messageData != null ->
                viewOncePendingInSnapshot(messageType, senderId, messageData)
            fallbackConversation != null -> fallbackViewOncePending(fallbackConversation)
            else -> false
        }
        return ConversationLatestSnapshot(preview, timestamp, senderId, messageType, pending)
    }

    private suspend fun resolveLatestConversationSnapshot(
        conversation: Conversation,
    ): ConversationLatestSnapshot {
        val conversationId = conversation.id
            ?: return makeConversationSnapshot(
                preview = conversation.lastMessage.orEmpty(),
                timestamp = null,
                senderId = null,
                messageType = conversation.lastMessageType,
                fallbackConversation = conversation,
            )

        val ctx = MomentsApplication.instance
        val previewEnabled = if (ctx != null) {
            ChatPreviewPrivacy.isUserPreviewEnabled(ctx, conversationId)
        } else {
            true
        }
        if (!previewEnabled) {
            return makeConversationSnapshot(
                preview = conversation.lastMessage.orEmpty(),
                timestamp = null,
                senderId = null,
                messageType = conversation.lastMessageType,
                fallbackConversation = conversation,
            )
        }

        return try {
            val snapshot = db.collection("conversations").document(conversationId)
                .collection("messages")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(5)
                .get()
                .await()

            for (document in snapshot.documents) {
                val data = document.data ?: continue
                if (data["isDeleted"] == true) continue
                val rawType = data["type"] as? String ?: continue
                val messageType = MessageType.from(rawType)
                val messageTimestamp = (data["timestamp"] as? Timestamp)?.toDate()
                val messageSenderId = data["senderId"] as? String
                val isVanishMessage = ChatPreviewPrivacy.isVanishModeMessage(data)
                if (!ChatPreviewPrivacy.shouldRevealPreview(conversationId, isVanishMessage)) {
                    return makeConversationSnapshot(
                        preview = neutralConversationPreview(messageType),
                        timestamp = messageTimestamp,
                        senderId = messageSenderId,
                        messageType = messageType,
                        messageData = data,
                    )
                }
                when (messageType) {
                    MessageType.TEXT -> {
                        val encryptedContent = data["content"] as? String
                        if (encryptedContent.isNullOrEmpty()) continue
                        val decrypted = decryptMessageContent(encryptedContent, conversationId).trim()
                        if (decrypted.isEmpty()) continue
                        return makeConversationSnapshot(
                            preview = decrypted,
                            timestamp = messageTimestamp,
                            senderId = messageSenderId,
                            messageType = messageType,
                            messageData = data,
                        )
                    }
                    MessageType.CHAT_NOTICE -> {
                        val noticeText = chatNoticePreviewText(data["content"] as? String ?: "")
                        if (noticeText.isEmpty()) continue
                        return makeConversationSnapshot(
                            preview = noticeText,
                            timestamp = messageTimestamp,
                            senderId = messageSenderId,
                            messageType = messageType,
                            messageData = data,
                        )
                    }
                    else -> return makeConversationSnapshot(
                        preview = neutralConversationPreview(messageType),
                        timestamp = messageTimestamp,
                        senderId = messageSenderId,
                        messageType = messageType,
                        messageData = data,
                    )
                }
            }
            makeConversationSnapshot(
                preview = conversation.lastMessage.orEmpty(),
                timestamp = null,
                senderId = null,
                messageType = conversation.lastMessageType,
                fallbackConversation = conversation,
            )
        } catch (_: Exception) {
            makeConversationSnapshot(
                preview = conversation.lastMessage.orEmpty(),
                timestamp = null,
                senderId = null,
                messageType = conversation.lastMessageType,
                fallbackConversation = conversation,
            )
        }
    }

    /** ≡ `EnhancedMessage.chatNoticePreviewText(for:)`. */
    private fun chatNoticePreviewText(token: String): String {
        if (VanishMessageTimer.parseEnabledNotice(token) != null || token == "chat.vanish.enabled") {
            return "Disappearing messages on"
        }
        if (token == VanishMessageTimer.DISABLED_NOTICE_TOKEN || token == "chat.vanish.disabled") {
            return "Disappearing messages off"
        }
        if (token == VanishMessageTimer.SCREENSHOT_NOTICE_TOKEN) return "Screenshot"
        if (token == VanishMessageTimer.SCREEN_RECORDING_NOTICE_TOKEN) return "Screen recording"
        return ""
    }

    private fun parseConversation(
        id: String,
        data: Map<String, Any?>,
        viewerId: String,
    ): Conversation? {
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
        val encryptionVersion = data["encryptionVersion"] as? String
        val appContext = MomentsApplication.instance
        val lastMessage = if (appContext != null) {
            sanitizedConversationPreview(appContext, data["lastMessage"] as? String, encryptionVersion)
        } else {
            sanitizedConversationPreview(
                data["lastMessage"] as? String,
                encryptionVersion,
                neutralTextPreview = "New message",
            )
        }

        @Suppress("UNCHECKED_CAST")
        val participantData = data["participantData"] as? Map<String, Map<String, Any?>>
        val otherData = participantData?.get(otherId)
        val cached = UserCacheService.getCachedUser(otherId)
        val username = (otherData?.get("username") as? String)
            ?: cached?.username
            ?: (data["otherParticipantUsername"] as? String)
            ?: "User"
        val avatar = (otherData?.get("profileImagePath") as? String)
            ?: cached?.profileImagePath
            ?: (data["otherParticipantProfileImagePath"] as? String)

        @Suppress("UNCHECKED_CAST")
        val pinnedByUserIds = (data["pinnedByUserIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val legacyPinnedBy = data["pinnedBy"] as? String
        val legacyIsPinned = data["isPinned"] as? Boolean ?: false
        val isPinned = viewerId in pinnedByUserIds || (legacyIsPinned && legacyPinnedBy == viewerId)

        @Suppress("UNCHECKED_CAST")
        val mutedByUserIds = (data["mutedByUserIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        val legacyMutedBy = data["mutedBy"] as? String
        val legacyIsMuted = data["isMuted"] as? Boolean ?: false
        val isMuted = viewerId in mutedByUserIds || (legacyIsMuted && legacyMutedBy == viewerId)

        @Suppress("UNCHECKED_CAST")
        val archivedByUserIds = (data["archivedByUserIds"] as? List<*>)?.filterIsInstance<String>().orEmpty()

        fun timestampMap(key: String): Map<String, Date>? {
            @Suppress("UNCHECKED_CAST")
            val raw = data[key] as? Map<String, *> ?: return null
            val mapped = raw.mapNotNull { (k, v) ->
                val date = when (v) {
                    is Timestamp -> v.toDate()
                    is Date -> v
                    else -> null
                }
                date?.let { k to it }
            }.toMap()
            return mapped.takeIf { it.isNotEmpty() }
        }

        @Suppress("UNCHECKED_CAST")
        val lastReactionRaw = data["lastMessageReaction"] as? Map<*, *>
        val lastReaction = lastReactionRaw?.let { raw ->
            val messageId = raw["messageId"] as? String ?: return@let null
            val emoji = raw["emoji"] as? String ?: return@let null
            val byUserId = raw["byUserId"] as? String ?: return@let null
            ConversationLastMessageReaction(messageId, emoji, byUserId)
        }

        return Conversation(
            id = id,
            participants = participants,
            lastMessage = lastMessage,
            timestamp = timestamp,
            readStatus = readStatus,
            otherParticipantId = otherId,
            otherParticipantUsername = username,
            otherParticipantProfileImagePath = avatar,
            isPinned = isPinned,
            pinnedByUserIds = pinnedByUserIds,
            pinnedBy = legacyPinnedBy,
            isMuted = isMuted,
            mutedByUserIds = mutedByUserIds,
            mutedBy = legacyMutedBy,
            archivedByUserIds = archivedByUserIds,
            encryptionVersion = encryptionVersion,
            conversationKeyVersion = (data["conversationKeyVersion"] as? Number)?.toInt(),
            wrappedKeys = parseWrappedKeys(data["wrappedKeys"]),
            readReceiptPreferences = (data["readReceiptPreferences"] as? Map<String, Boolean>),
            forwardingPreferences = (data["forwardingPreferences"] as? Map<String, Boolean>),
            buzzPreferences = (data["buzzPreferences"] as? Map<String, Boolean>),
            lastDeletedAt = timestampMap("lastDeletedAt"),
            lastReadAt = timestampMap("lastReadAt"),
            vanishModeActive = data["vanishModeActive"] as? Boolean ?: false,
            vanishModeEnabledBy = data["vanishModeEnabledBy"] as? String,
            vanishModeEnabledAt = (data["vanishModeEnabledAt"] as? Timestamp)?.toDate(),
            vanishMessageTimer = data["vanishMessageTimer"] as? String
                ?: VanishMessageTimer.DEFAULT.raw,
            vanishSettingsNoticeMessageId = data["vanishSettingsNoticeMessageId"] as? String,
            vanishDisabledNoticeMessageId = data["vanishDisabledNoticeMessageId"] as? String,
            lastMessageSenderId = data["lastMessageSenderId"] as? String,
            lastMessageSeenAt = timestampMap("lastMessageSeenAt"),
            lastMessageReaction = lastReaction,
            lastMessageType = (data["lastMessageType"] as? String)?.let(MessageType::from),
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseWrappedKeys(raw: Any?): Map<String, WrappedConversationKey>? {
        val map = raw as? Map<String, Any?> ?: return null
        val parsed = map.mapNotNull { (uid, value) ->
            val keyMap = value as? Map<String, Any?> ?: return@mapNotNull null
            WrappedConversationKey.from(keyMap)?.let { uid to it }
        }.toMap()
        return parsed.takeIf { it.isNotEmpty() }
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
