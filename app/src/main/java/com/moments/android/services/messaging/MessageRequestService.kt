package com.moments.android.services.messaging

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import com.moments.android.MomentsApplication
import com.moments.android.R
import com.moments.android.extensions.optStringOrNull
import com.moments.android.services.storage.MediaUploadService
import com.moments.android.services.storage.StorageUploadTarget
import com.moments.android.views.messaging.core.AcceptMessageRequestResult
import com.moments.android.views.messaging.core.ChatMediaPurpose
import com.moments.android.views.messaging.core.EncryptedChatMediaMetadata
import com.moments.android.views.messaging.core.MessageRequest
import com.moments.android.views.messaging.core.MessageRequestFolder
import com.moments.android.views.messaging.core.MessageRequestMessage
import com.moments.android.views.messaging.core.MessageType
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.util.Date
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

sealed class DirectMessageRoute {
    data class Conversation(val id: String) : DirectMessageRoute()
    data class ConversationDraft(val threadId: String) : DirectMessageRoute()
    data class OutgoingRequest(
        val threadId: String,
        val messageCount: Int,
        val limit: Int,
        val cryptoConfigured: Boolean,
    ) : DirectMessageRoute()
    data class IncomingRequest(val threadId: String, val messageCount: Int) : DirectMessageRoute()
}

data class MessageRequestInteractionContext(
    val kind: Kind = Kind.GENERAL,
    val storyId: String? = null,
    val storyOwnerId: String? = null,
    val sharedContentId: String? = null,
    val sharedContentOwnerId: String? = null,
    val isStoryMention: Boolean = false,
) {
    enum class Kind(val raw: String) {
        GENERAL("general"),
        STORY_MESSAGE("storyMessage"),
        STORY_EPHEMERAL("storyEphemeral"),
        SHARE_STORY("shareStory"),
        SHARE_MOMENT("shareMoment"),
        SHARE_PROFILE("shareProfile"),
        FORWARD_TEXT("forwardText"),
    }

    fun payload(): Map<String, Any> = buildMap {
        put("kind", kind.raw)
        storyId?.let { put("storyId", it) }
        storyOwnerId?.let { put("storyOwnerId", it) }
        sharedContentId?.let { put("sharedContentId", it) }
        sharedContentOwnerId?.let { put("sharedContentOwnerId", it) }
        if (isStoryMention) put("isStoryMention", true)
    }

    companion object { val General = MessageRequestInteractionContext() }
}

data class MessageRequestSendResult(
    val threadId: String,
    val messageId: String,
    val messageCount: Int,
    val limit: Int,
    val mediaUrl: String? = null,
    val mediaEncryption: EncryptedChatMediaMetadata? = null,
    val expirationDate: Date? = null,
    val allowReplay: Boolean = false,
)

/** Coordinador V2 compartido con iOS. El servidor decide conversación, solicitud o denegación. */
class MessageRequestService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {
    companion object {
        const val MESSAGE_LIMIT = 5
        private const val REGION = "europe-southwest1"
        private val ALLOWED_PENDING_TYPES = setOf(
            MessageType.TEXT,
            MessageType.EPHEMERAL,
            MessageType.SHARED_MOMENT,
            MessageType.SHARED_STORY,
            MessageType.SHARED_PROFILE,
            MessageType.VIEW_ONCE_IMAGE,
            MessageType.VIEW_ONCE_VIDEO,
        )
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val listeners = mutableMapOf<String, ListenerRegistration>()
    private var authStateListener: FirebaseAuth.AuthStateListener? = null
    private var incomingGeneration = UUID.randomUUID().toString()
    private var incomingDocuments = emptyList<com.google.firebase.firestore.DocumentSnapshot>()

    private val _pendingRequests = MutableStateFlow<List<MessageRequest>>(emptyList())
    val pendingRequests: StateFlow<List<MessageRequest>> = _pendingRequests.asStateFlow()
    private val _oldRequests = MutableStateFlow<List<MessageRequest>>(emptyList())
    val oldRequests: StateFlow<List<MessageRequest>> = _oldRequests.asStateFlow()
    private val _hiddenRequests = MutableStateFlow<List<MessageRequest>>(emptyList())
    val hiddenRequests: StateFlow<List<MessageRequest>> = _hiddenRequests.asStateFlow()
    private val _outgoingPendingRequests = MutableStateFlow<List<MessageRequest>>(emptyList())
    val outgoingPendingRequests: StateFlow<List<MessageRequest>> = _outgoingPendingRequests.asStateFlow()
    private val _automaticFilterEnabled = MutableStateFlow(true)
    val automaticFilterEnabled: StateFlow<Boolean> = _automaticFilterEnabled.asStateFlow()
    private val _customWords = MutableStateFlow<List<String>>(emptyList())
    val customWords: StateFlow<List<String>> = _customWords.asStateFlow()
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private fun ensureAuthStateListener() {
        if (authStateListener != null) return
        val listener = FirebaseAuth.AuthStateListener { auth ->
            if (auth.currentUser == null) {
                removeAllListeners()
                publishIncoming(emptyList())
                _outgoingPendingRequests.value = emptyList()
                _isLoading.value = false
                _errorMessage.value = null
            }
        }
        authStateListener = listener
        FirebaseAuth.getInstance().addAuthStateListener(listener)
    }

    fun removeAllListeners() {
        listeners.values.forEach(ListenerRegistration::remove)
        listeners.clear()
        authStateListener?.let(FirebaseAuth.getInstance()::removeAuthStateListener)
        authStateListener = null
    }

    fun listenToPendingRequests(userId: String) {
        ensureAuthStateListener()
        listeners.remove("incoming")?.remove()
        listeners.remove("preferences")?.remove()
        val generation = UUID.randomUUID().toString().also { incomingGeneration = it }
        listeners["incoming"] = db.collection("messageRequests")
            .whereEqualTo("receiverId", userId)
            .whereEqualTo("status", MessageRequest.RequestStatus.PENDING.raw)
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (generation != incomingGeneration) return@addSnapshotListener
                if (error != null) {
                    if (FirebaseAuth.getInstance().currentUser == null) publishIncoming(emptyList())
                    else _errorMessage.value = error.localizedMessage
                    return@addSnapshotListener
                }
                incomingDocuments = snapshot?.documents.orEmpty()
                scope.launch {
                    val hydrated = hydrateRequests(incomingDocuments)
                    if (generation == incomingGeneration) publishIncoming(hydrated)
                }
            }

        val preferences = db.collection("users").document(userId)
            .collection("messageRequestPreferences").document("settings")
        listeners["preferences"] = preferences.addSnapshotListener { snapshot, _ ->
            if (snapshot?.exists() != true) {
                scope.launch {
                    runCatching {
                        preferences.set(
                            mapOf(
                                "automaticFilterEnabled" to true,
                                "customWords" to emptyList<String>(),
                                "updatedAt" to FieldValue.serverTimestamp(),
                            ),
                            com.google.firebase.firestore.SetOptions.merge(),
                        ).await()
                    }
                }
            }
            _automaticFilterEnabled.value = snapshot?.getBoolean("automaticFilterEnabled") ?: true
            _customWords.value = (snapshot?.get("customWords") as? List<*>)?.filterIsInstance<String>().orEmpty()
            scope.launch { publishIncoming(hydrateRequests(incomingDocuments)) }
        }
    }

    fun listenToOutgoingPendingRequests(userId: String) {
        ensureAuthStateListener()
        listeners.remove("outgoing")?.remove()
        listeners["outgoing"] = db.collection("users").document(userId)
            .collection("messageRequestOutbox")
            .whereEqualTo("status", MessageRequest.RequestStatus.PENDING.raw)
            .orderBy("lastActivityAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    if (FirebaseAuth.getInstance().currentUser != null) _errorMessage.value = error.localizedMessage
                    return@addSnapshotListener
                }
                scope.launch {
                    _outgoingPendingRequests.value = hydrateOutgoingRequests(snapshot?.documents.orEmpty(), userId)
                }
            }
    }

    suspend fun saveHiddenWords(words: List<String>, automaticFilterEnabled: Boolean) {
        val userId = requireUser().uid
        val normalized = words.mapNotNull(::normalizedWord).distinct().sorted().take(100)
        db.collection("users").document(userId)
            .collection("messageRequestPreferences").document("settings")
            .set(
                mapOf(
                    "automaticFilterEnabled" to automaticFilterEnabled,
                    "customWords" to normalized,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
    }

    suspend fun loadHiddenWordsPreferences(): Pair<Boolean, List<String>> {
        val userId = requireUser().uid
        val reference = db.collection("users").document(userId)
            .collection("messageRequestPreferences").document("settings")
        val snapshot = reference.get().await()
        if (!snapshot.exists()) {
            reference.set(
                mapOf(
                    "automaticFilterEnabled" to true,
                    "customWords" to emptyList<String>(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            ).await()
            return true to emptyList()
        }
        val automatic = snapshot.getBoolean("automaticFilterEnabled") ?: true
        val words = (snapshot.get("customWords") as? List<*>)
            ?.filterIsInstance<String>()
            .orEmpty()
        _automaticFilterEnabled.value = automatic
        _customWords.value = words
        return automatic to words
    }

    suspend fun resolveRoute(
        receiverId: String,
        interaction: MessageRequestInteractionContext = MessageRequestInteractionContext.General,
        reserve: Boolean = true,
    ): DirectMessageRoute {
        require(receiverId.isNotBlank()) { localized(R.string.messaging_error_invalid_recipient) }
        val json = post(
            "routeDirectMessageV2",
            mapOf(
                "recipientId" to receiverId,
                "interactionKind" to interaction.kind.raw,
                "context" to interaction.payload(),
                "reserve" to reserve,
            ),
        )
        return when (json.optString("result")) {
            "conversation" -> DirectMessageRoute.Conversation(json.requiredString("conversationId"))
            "conversationDraft" -> DirectMessageRoute.ConversationDraft(json.requiredString("threadId"))
            "outgoingRequest" -> DirectMessageRoute.OutgoingRequest(
                threadId = json.requiredString("threadId"),
                messageCount = json.optInt("messageCount", 0),
                limit = json.optInt("limit", MESSAGE_LIMIT),
                cryptoConfigured = json.optBoolean("cryptoConfigured", false),
            )
            "incomingRequest" -> DirectMessageRoute.IncomingRequest(
                threadId = json.requiredString("threadId"),
                messageCount = json.optInt("messageCount", 0),
            )
            else -> throw serviceError()
        }
    }

    suspend fun activateConversationDraft(receiverId: String, threadId: String): String {
        val senderId = requireUser().uid
        val wrapped = EncryptionService.prepareMessageRequestKey(
            threadId = threadId,
            participantIds = listOf(senderId, receiverId).sorted(),
            wrappedBy = senderId,
        )
        return try {
            val json = post(
                "activateDirectConversationV2",
                mapOf("recipientId" to receiverId, "threadId" to threadId, "wrappedKeys" to wrapped),
            )
            val conversationId = json.requiredString("conversationId")
            if (json.optBoolean("usedExistingContext", false) || conversationId != threadId) {
                EncryptionService.deleteConversationKeys(threadId)
            }
            conversationId
        } catch (error: Throwable) {
            EncryptionService.deleteConversationKeys(threadId)
            throw error
        }
    }

    suspend fun appendRequestMessage(
        receiverId: String,
        text: String,
        messageType: MessageType = MessageType.TEXT,
        interaction: MessageRequestInteractionContext = MessageRequestInteractionContext.General,
        encryptedMedia: Map<String, Any>? = null,
        expirationDate: Date? = null,
        allowReplay: Boolean = false,
        requestedMessageId: String? = null,
    ): MessageRequestSendResult {
        val senderId = requireUser().uid
        require(text.isNotBlank() || encryptedMedia != null) { localized(R.string.messaging_error_empty_message) }
        require(messageType in ALLOWED_PENDING_TYPES) { localized(R.string.messaging_error_unsupported_file) }
        val prepared = prepareOutgoingThread(senderId, receiverId, interaction)
        val messageId = requestedMessageId?.trim()?.takeIf(String::isNotEmpty) ?: UUID.randomUUID().toString()
        val message = mutableMapOf<String, Any>(
            "id" to messageId,
            "clientNonce" to messageId,
            "ciphertext" to EncryptionService.encryptChatMessage(text, prepared.threadId),
            "type" to messageType.raw,
            "context" to interaction.payload(),
            "allowReplay" to allowReplay,
        )
        encryptedMedia?.let { message["media"] = it }
        expirationDate?.let { message["expirationDateMillis"] = it.time }
        val json = post("appendMessageRequestV2", mapOf("threadId" to prepared.threadId, "message" to message))
        return MessageRequestSendResult(
            threadId = prepared.threadId,
            messageId = json.optString("messageId", messageId),
            messageCount = json.optInt("messageCount", prepared.messageCount + 1),
            limit = json.optInt("limit", MESSAGE_LIMIT),
        )
    }

    suspend fun appendEphemeralMedia(
        receiverId: String,
        data: ByteArray,
        isVideo: Boolean,
        allowReplay: Boolean,
        interaction: MessageRequestInteractionContext = MessageRequestInteractionContext.General,
        expiresAt: Date? = null,
    ): MessageRequestSendResult {
        val senderId = requireUser().uid
        val prepared = prepareOutgoingThread(senderId, receiverId, interaction)
        val messageId = UUID.randomUUID().toString()
        val messageType = if (expiresAt == null) {
            if (isVideo) MessageType.VIEW_ONCE_VIDEO else MessageType.VIEW_ONCE_IMAGE
        } else MessageType.EPHEMERAL
        val contentType = if (isVideo) "video/mp4" else "image/jpeg"
        val fileExtension = if (isVideo) "mp4" else "jpg"
        val encrypted = EncryptionService.encryptChatMedia(
            data = data,
            conversationId = prepared.threadId,
            messageId = messageId,
            purpose = ChatMediaPurpose.PRIMARY,
            contentType = contentType,
            fileExtension = fileExtension,
        )
        val storagePath = "directThreads/${prepared.threadId}/$messageId/media.enc"
        val target = StorageUploadTarget(
            objectPath = storagePath,
            contentType = "application/octet-stream",
            customMetadata = mapOf(
                "ownerId" to senderId,
                "threadId" to prepared.threadId,
                "messageId" to messageId,
                "encrypted" to "true",
            ),
        )
        return try {
            MediaUploadService.uploadEncryptedBlob(target, encrypted.ciphertext)
            val media = encrypted.metadata.toFirestoreMap().toMutableMap().apply {
                put("storagePath", storagePath)
                put("kind", if (isVideo) "video" else "image")
            }
            val message = mutableMapOf<String, Any>(
                "id" to messageId,
                "clientNonce" to messageId,
                "ciphertext" to EncryptionService.encryptChatMessage(if (isVideo) "🎥" else "📷", prepared.threadId),
                "type" to messageType.raw,
                "context" to interaction.payload(),
                "media" to media,
                "allowReplay" to allowReplay,
            )
            expiresAt?.let { message["expirationDateMillis"] = it.time }
            val json = post("appendMessageRequestV2", mapOf("threadId" to prepared.threadId, "message" to message))
            MessageRequestSendResult(
                threadId = prepared.threadId,
                messageId = json.optString("messageId", messageId),
                messageCount = json.optInt("messageCount", prepared.messageCount + 1),
                limit = json.optInt("limit", MESSAGE_LIMIT),
                mediaUrl = storagePath,
                mediaEncryption = encrypted.metadata,
                expirationDate = expiresAt,
                allowReplay = allowReplay,
            )
        } catch (error: Throwable) {
            runCatching { FirebaseStorage.getInstance().reference.child(storagePath).delete().await() }
            throw error
        }
    }

    fun sendMessageRequest(
        receiverId: String,
        message: String,
        messageType: MessageType = MessageType.TEXT,
        mediaUrl: String? = null,
        thumbnailUrl: String? = null,
        onComplete: (Result<Unit>) -> Unit,
    ) {
        _isLoading.value = true
        scope.launch {
            runCatching {
                require(mediaUrl == null && thumbnailUrl == null) { localized(R.string.messaging_error_unsupported_file) }
                appendRequestMessage(receiverId, message, messageType)
            }.fold(
                onSuccess = { _isLoading.value = false; onComplete(Result.success(Unit)) },
                onFailure = { _isLoading.value = false; _errorMessage.value = it.localizedMessage; onComplete(Result.failure(it)) },
            )
        }
    }

    fun acceptRequest(request: MessageRequest, onComplete: (Result<AcceptMessageRequestResult>) -> Unit) {
        val threadId = request.id
            ?: return onComplete(Result.failure(serviceError(400, localized(R.string.message_requests_accept_error_not_available))))
        if (FirebaseAuth.getInstance().currentUser?.uid != request.receiverId) {
            return onComplete(Result.failure(serviceError(403, localized(R.string.message_requests_accept_error_forbidden))))
        }
        _isLoading.value = true
        scope.launch {
            runCatching { acceptIncomingThread(threadId) }.fold(
                onSuccess = { _isLoading.value = false; onComplete(Result.success(it)) },
                onFailure = { _isLoading.value = false; onComplete(Result.failure(it)) },
            )
        }
    }

    suspend fun acceptIncomingThread(threadId: String): AcceptMessageRequestResult {
        val json = post("acceptMessageRequestV2", mapOf("threadId" to threadId))
        val ids = json.optJSONArray("messageIds")?.strings().orEmpty()
        return AcceptMessageRequestResult(
            conversationId = json.requiredString("conversationId"),
            messageId = json.optStringOrNull("messageId") ?: ids.firstOrNull().orEmpty(),
            messageIds = ids,
        )
    }

    fun rejectRequest(request: MessageRequest, onComplete: (Result<Unit>) -> Unit) = manage(request, "reject", onComplete)
    fun cancelRequest(request: MessageRequest, onComplete: (Result<Unit>) -> Unit) = manage(request, "cancel", onComplete)
    fun blockUser(request: MessageRequest, onComplete: (Result<Unit>) -> Unit) = manage(request, "block", onComplete)
    fun reportRequest(request: MessageRequest, onComplete: (Result<Unit>) -> Unit) = manage(request, "report", onComplete)
    fun moveRequest(request: MessageRequest, folder: MessageRequestFolder, onComplete: (Result<Unit>) -> Unit) =
        manage(request, if (folder == MessageRequestFolder.HIDDEN) "moveToHidden" else "moveToRequests", onComplete)

    suspend fun consumePendingEphemeral(threadId: String, messageId: String) {
        post("consumePendingEphemeralV2", mapOf("threadId" to threadId, "messageId" to messageId))
    }

    suspend fun loadIncomingRequest(threadId: String): MessageRequest {
        val currentUserId = requireUser().uid
        val document = db.collection("messageRequests").document(threadId).get().await()
        val data = document.data.orEmpty()
        val base = MessageRequest.fromFirestoreData(data, threadId)
            ?.takeIf { it.receiverId == currentUserId }
            ?: throw serviceError(404, localized(R.string.message_requests_accept_error_not_available))
        return applyLocalFolder(hydrateTimeline(threadId, base), data["manualFolder"] as? String)
    }

    suspend fun loadOutgoingRequest(threadId: String, receiverId: String): MessageRequest {
        val senderId = requireUser().uid
        val data = db.collection("users").document(senderId)
            .collection("messageRequestOutbox").document(threadId).get().await().data.orEmpty()
        if (data["receiverId"] != receiverId) throw serviceError(404, localized(R.string.message_requests_accept_error_not_available))
        return hydrateOutgoingRequest(threadId, data, senderId)
    }

    suspend fun getPendingRequestCount(userId: String): Int =
        if (FirebaseAuth.getInstance().currentUser?.uid == userId) _pendingRequests.value.size else 0

    fun canSendRequest(senderId: String, receiverId: String, onComplete: (Boolean) -> Unit) {
        scope.launch {
            val value = if (FirebaseAuth.getInstance().currentUser?.uid != senderId) false else runCatching {
                when (val route = resolveRoute(receiverId, reserve = false)) {
                    is DirectMessageRoute.OutgoingRequest -> route.messageCount < route.limit
                    else -> true
                }
            }.getOrDefault(false)
            onComplete(value)
        }
    }

    private fun manage(request: MessageRequest, action: String, onComplete: (Result<Unit>) -> Unit) {
        val threadId = request.id ?: return onComplete(Result.failure(serviceError(400)))
        scope.launch {
            runCatching { post("manageMessageRequestV2", mapOf("threadId" to threadId, "action" to action)) }
                .fold(
                    onSuccess = { onComplete(Result.success(Unit)) },
                    onFailure = { onComplete(Result.failure(it)) },
                )
        }
    }

    private data class PreparedThread(val threadId: String, val messageCount: Int)

    private suspend fun prepareOutgoingThread(
        senderId: String,
        receiverId: String,
        interaction: MessageRequestInteractionContext,
    ): PreparedThread {
        val route = resolveRoute(receiverId, interaction)
        val outgoing = route as? DirectMessageRoute.OutgoingRequest
            ?: throw serviceError(409, localized(R.string.chat_request_error_not_allowed))
        if (outgoing.messageCount >= outgoing.limit) {
            throw serviceError(429, localized(R.string.message_requests_limit_reached))
        }
        if (!outgoing.cryptoConfigured) {
            val wrapped = EncryptionService.prepareMessageRequestKey(
                threadId = outgoing.threadId,
                participantIds = listOf(senderId, receiverId).sorted(),
                wrappedBy = senderId,
            )
            val configuration = post(
                "configureMessageRequestV2",
                mapOf("threadId" to outgoing.threadId, "wrappedKeys" to wrapped),
            )
            if (configuration.optBoolean("usedExistingContext", false)) {
                EncryptionService.deleteConversationKeys(outgoing.threadId)
            }
        }
        return PreparedThread(outgoing.threadId, outgoing.messageCount)
    }

    private suspend fun hydrateRequests(
        documents: List<com.google.firebase.firestore.DocumentSnapshot>,
    ): List<MessageRequest> = documents.mapNotNull { document ->
        val data = document.data.orEmpty()
        val base = MessageRequest.fromFirestoreData(data, document.id)
            ?.takeIf { it.schemaVersion >= 2 && it.messageCount > 0 }
            ?: return@mapNotNull null
        applyLocalFolder(hydrateTimeline(document.id, base), data["manualFolder"] as? String)
    }.sortedByDescending(MessageRequest::lastActivityAt)

    private suspend fun hydrateOutgoingRequests(
        documents: List<com.google.firebase.firestore.DocumentSnapshot>,
        senderId: String,
    ): List<MessageRequest> = documents.mapNotNull { document ->
        runCatching { hydrateOutgoingRequest(document.id, document.data.orEmpty(), senderId) }.getOrNull()
    }.filter { it.messageCount > 0 }.sortedByDescending(MessageRequest::lastActivityAt)

    private suspend fun hydrateOutgoingRequest(
        threadId: String,
        data: Map<String, Any>,
        senderId: String,
    ): MessageRequest {
        val receiverId = data["receiverId"] as? String ?: throw serviceError(404)
        val timestamp = (data["lastActivityAt"] as? Timestamp)?.toDate() ?: Date()
        val base = MessageRequest(
            id = threadId,
            senderId = senderId,
            senderUsername = data["receiverUsername"] as? String,
            senderProfileImagePath = data["receiverProfileImagePath"] as? String,
            receiverId = receiverId,
            message = "",
            timestamp = timestamp,
            status = MessageRequest.RequestStatus.PENDING,
            messageType = MessageType.TEXT,
            messageCount = (data["messageCount"] as? Number)?.toInt() ?: 0,
            schemaVersion = (data["schemaVersion"] as? Number)?.toInt() ?: 2,
            lastActivityAt = timestamp,
        )
        return hydrateTimeline(threadId, base)
    }

    private suspend fun hydrateTimeline(threadId: String, base: MessageRequest): MessageRequest = runCatching {
        val snapshot = db.collection("messageRequests").document(threadId).collection("messages")
            .orderBy("sequence", Query.Direction.ASCENDING).limit(MESSAGE_LIMIT.toLong()).get().await()
        val messages = snapshot.documents.mapNotNull { child ->
            val value = child.data.orEmpty()
            val ciphertext = value["content"] as? String ?: return@mapNotNull null
            val content = try {
                EncryptionService.decryptChatMessageStrict(ciphertext, threadId)
            } catch (_: Throwable) {
                return@mapNotNull null
            }
            val context = value["context"] as? Map<*, *> ?: emptyMap<Any, Any>()
            val encryptedMedia = value["encryptedMedia"] as? Map<*, *>
            MessageRequestMessage(
                id = child.id,
                senderId = value["senderId"] as? String ?: base.senderId,
                content = content,
                timestamp = (value["timestamp"] as? Timestamp)?.toDate() ?: base.timestamp,
                type = MessageType.from(value["type"] as? String),
                sequence = (value["sequence"] as? Number)?.toInt() ?: 0,
                mediaUrl = encryptedMedia?.get("storagePath") as? String ?: value["mediaUrl"] as? String,
                thumbnailUrl = encryptedMedia?.get("thumbnailStoragePath") as? String,
                mediaEncryption = encryptedMedia?.toMetadata(),
                contextKind = value["contextKind"] as? String ?: context["kind"] as? String ?: "general",
                storyId = context["storyId"] as? String,
                storyOwnerId = context["storyOwnerId"] as? String,
                sharedContentId = context["sharedContentId"] as? String,
                sharedContentOwnerId = context["sharedContentOwnerId"] as? String,
                isStoryMention = context["isStoryMention"] as? Boolean ?: false,
                expirationDate = (value["expirationDate"] as? Timestamp)?.toDate(),
                isViewOnce = value["isViewOnce"] as? Boolean ?: false,
                allowReplay = value["allowReplay"] as? Boolean ?: false,
            )
        }
        val last = messages.lastOrNull()
        base.copy(
            message = last?.content.orEmpty(),
            messageType = last?.type ?: base.messageType,
            mediaUrl = last?.mediaUrl,
            thumbnailUrl = last?.thumbnailUrl,
            messages = messages,
        )
    }.getOrElse {
        _errorMessage.value = it.localizedMessage
        base
    }

    private fun applyLocalFolder(request: MessageRequest, manualFolder: String?): MessageRequest {
        if (manualFolder == MessageRequestFolder.NORMAL.raw) return request.copy(folder = MessageRequestFolder.NORMAL)
        val containsHiddenWord = _automaticFilterEnabled.value && request.messages.any { message ->
            val text = normalizedText(message.content)
            _customWords.value.mapNotNull(::normalizedWord).any(text::contains)
        }
        return if (containsHiddenWord) request.copy(folder = MessageRequestFolder.HIDDEN) else request
    }

    private fun publishIncoming(requests: List<MessageRequest>) {
        _pendingRequests.value = requests.filter { it.folder == MessageRequestFolder.NORMAL }
        _oldRequests.value = requests.filter { it.folder == MessageRequestFolder.OLD }
        _hiddenRequests.value = requests.filter { it.folder == MessageRequestFolder.HIDDEN }
    }

    private suspend fun post(endpoint: String, payload: Map<String, Any>): JSONObject = withContext(Dispatchers.IO) {
        val user = requireUser()
        val token = user.getIdToken(false).await().token ?: throw serviceError(401)
        val projectId = FirebaseApp.getInstance().options.projectId ?: throw serviceError()
        val connection = URL("https://$REGION-$projectId.cloudfunctions.net/$endpoint")
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.doOutput = true
            connection.connectTimeout = 30_000
            connection.readTimeout = 30_000
            connection.outputStream.use { it.write(JSONObject(payload).toString().toByteArray()) }
            val status = connection.responseCode
            val body = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.readBytes()?.decodeToString().orEmpty()
            val json = runCatching { JSONObject(body) }.getOrDefault(JSONObject())
            if (status !in 200..299) {
                val errorCode = json.optString("errorCode", "REQUEST_V2_FAILED")
                throw serviceError(status, localizedError(errorCode), errorCode)
            }
            json
        } finally {
            connection.disconnect()
        }
    }

    private fun requireUser() = FirebaseAuth.getInstance().currentUser
        ?: throw serviceError(401, localized(R.string.messaging_error_not_authenticated))

    private fun serviceError(
        code: Int = -1,
        message: String = localized(R.string.messaging_error_service_unavailable),
        errorCode: String? = null,
    ): IllegalStateException = IllegalStateException(message).also {
        if (errorCode != null) it.addSuppressed(IllegalArgumentException("$code:$errorCode"))
    }

    private fun localizedError(code: String): String = when (code) {
        "DAILY_LIMIT" -> localized(R.string.message_requests_error_daily_limit)
        "MESSAGE_LIMIT" -> localized(R.string.message_requests_limit_reached)
        "COOLDOWN" -> localized(R.string.message_requests_error_cooldown)
        "DENIED", "REQUEST_FORBIDDEN", "SAFETY_RESTRICTED" -> localized(R.string.message_requests_error_denied)
        "INACTIVE_USER" -> localized(R.string.message_requests_error_inactive_user)
        "EPHEMERAL_EXPIRED", "EPHEMERAL_CONSUMED" -> localized(R.string.message_requests_media_unavailable)
        else -> localized(R.string.messaging_error_service_unavailable)
    }

    private fun localized(resId: Int): String = MomentsApplication.instance?.getString(resId).orEmpty()

    private fun normalizedWord(value: String): String? = normalizedText(value).trim().takeIf(String::isNotEmpty)
    private fun normalizedText(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace("\\p{M}+".toRegex(), "").lowercase()

    private fun JSONObject.requiredString(key: String): String = optStringOrNull(key)
        ?.takeIf(String::isNotBlank) ?: throw serviceError()
    private fun JSONArray.strings(): List<String> = (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }
    private fun EncryptedChatMediaMetadata.toFirestoreMap(): Map<String, Any> = toJson().let { json ->
        json.keys().asSequence().associateWith(json::get)
    }
    private fun Map<*, *>.toMetadata(): EncryptedChatMediaMetadata? {
        val json = JSONObject()
        forEach { (key, value) -> if (key is String && value != null) json.put(key, value) }
        return EncryptedChatMediaMetadata.fromJson(json)
    }
}
