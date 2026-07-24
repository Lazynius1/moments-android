package com.moments.android.views.messaging.core

import com.moments.android.models.Conversation
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageSyncCursor
import com.moments.android.models.MessageType
import com.moments.android.models.MessageReactionMutation
import com.moments.android.models.ChatMessagePolicy
import com.moments.android.views.messaging.components.ChatListUpdateKind
import com.moments.android.views.messaging.components.ChatTimelineUpdateReason
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.ChatEncryptedMediaResolver
import com.moments.android.views.messaging.services.ChatMediaUploadProgressEvents
import com.moments.android.views.messaging.services.markEphemeralAsViewed
import com.moments.android.views.messaging.services.forwardTextMessage
import com.moments.android.views.messaging.services.toggleMessageStar
import com.moments.android.views.messaging.services.listenToMessageReactions
import com.moments.android.views.messaging.services.removeMessageReactionsListener
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.messaging.VanishMessageTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

/** Port en curso de `Views/Messaging/Core/ChatViewModel.swift`. */
open class EnhancedChatViewModel(
    val conversation: Conversation,
    val currentUserId: String,
    private val chatService: ChatService = ChatService,
) {
    enum class HistoryLoadNotice { HIDDEN, OFFLINE, ERROR }
    data class ChatTimelineMutation(val kind: ChatListUpdateKind, val reason: ChatTimelineUpdateReason, val anchorMessageId: String? = null) {
        companion object { val INITIAL = ChatTimelineMutation(ChatListUpdateKind.INITIAL, ChatTimelineUpdateReason.LAYOUT) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    init {
        scope.launch {
            ChatMediaUploadProgressEvents.events.collect { event ->
                setUploadProgress(event.messageId, event.progress)
            }
        }
    }
    private val _messages = MutableStateFlow<List<EnhancedMessage>>(emptyList())
    val messages: StateFlow<List<EnhancedMessage>> = _messages.asStateFlow()
    private val _chatTimelineMutation = MutableStateFlow(ChatTimelineMutation.INITIAL)
    val chatTimelineMutation: StateFlow<ChatTimelineMutation> = _chatTimelineMutation.asStateFlow()
    private val _typingUsers = MutableStateFlow<Set<String>>(emptySet()); val typingUsers = _typingUsers.asStateFlow()
    private val _uploadProgress = MutableStateFlow<Map<String, Double>>(emptyMap()); val uploadProgress = _uploadProgress.asStateFlow()
    private val _downloadProgress = MutableStateFlow<Map<String, Double>>(emptyMap()); val downloadProgress = _downloadProgress.asStateFlow()
    private val _isLoading = MutableStateFlow(false); val isLoading = _isLoading.asStateFlow()
    private val _isLoadingMore = MutableStateFlow(false); val isLoadingMore = _isLoadingMore.asStateFlow()
    private val _canLoadMore = MutableStateFlow(true); val canLoadMore = _canLoadMore.asStateFlow()
    private val _historyLoadNotice = MutableStateFlow(HistoryLoadNotice.HIDDEN); val historyLoadNotice = _historyLoadNotice.asStateFlow()
    private val _error = MutableStateFlow<String?>(null); val error = _error.asStateFlow()
    private val _isTyping = MutableStateFlow(false); val isTyping = _isTyping.asStateFlow()
    private val _typingIndicatorEnabled = MutableStateFlow(true); val typingIndicatorEnabled = _typingIndicatorEnabled.asStateFlow()
    private val _forwardingPreferences = MutableStateFlow<Map<String, Boolean>>(emptyMap()); val forwardingPreferences = _forwardingPreferences.asStateFlow()
    private val _buzzPreferences = MutableStateFlow<Map<String, Boolean>>(emptyMap()); val buzzPreferences = _buzzPreferences.asStateFlow()
    private val _isSearchingHistory = MutableStateFlow(false); val isSearchingHistory = _isSearchingHistory.asStateFlow()
    private val _searchResults = MutableStateFlow<List<String>>(emptyList()); val searchResults = _searchResults.asStateFlow()
    private val _vanishModeActive = MutableStateFlow(false); val vanishModeActive = _vanishModeActive.asStateFlow()
    private val _vanishMessageTimer = MutableStateFlow(VanishMessageTimer.DEFAULT); val vanishMessageTimer = _vanishMessageTimer.asStateFlow()
    private val _liveReactionOverlays = MutableStateFlow<Map<String, Map<String, List<String>>>>(emptyMap()); val reactionOverlays = _liveReactionOverlays.asStateFlow()
    private val localMessageStates = mutableMapOf<String, MessageStatus>()
    private val outgoingTempMessages = mutableMapOf<String, EnhancedMessage>()
    private val hydratingMediaIds = mutableSetOf<String>()
    private val downloadingMediaIds = mutableSetOf<String>()
    private val refreshingMetadataIds = mutableSetOf<String>()
    private var forcedNextTimelineMutation: ChatTimelineMutation? = null
    private val requestedHighlightMessageIds = mutableSetOf<String>()
    private val hiddenForMeMessageIds = mutableSetOf<String>()
    private val optimisticallyHiddenVanishIds = mutableSetOf<String>()
    private val sessionSeenIncomingMessageIds = mutableSetOf<String>()
    private val starredMessageIds = mutableSetOf<String>()
    private val locallyReadMessageIds = mutableSetOf<String>()
    private val historicalMessages = mutableListOf<EnhancedMessage>()
    private val realTimeMessages = mutableListOf<EnhancedMessage>()
    private var sessionListenersAttached = false
    private var typingUsersJob: Job? = null
    private var searchGeneration = 0
    private var isMaterializingConversation = false
    private val pendingMaterializationCallbacks = mutableListOf<(String?) -> Unit>()
    private var didLoadCache = false
    var messagesById: Map<String, EnhancedMessage> = emptyMap(); private set
    var messageIndexById: Map<String, Int> = emptyMap(); private set
    var unreadIncomingCount: Int = 0; private set
    var isChatVisible = false
    val conversationId: String get() = conversation.id.orEmpty()
    val isDraftConversation: Boolean get() = conversationId.isBlank()
    val canSendBuzz: Boolean get() = ChatMessagePolicy.canSendBuzz(conversation.participants, currentUserId, _buzzPreferences.value)

    fun ensureConversationExists(completion: (String?) -> Unit) {
        if (!isDraftConversation) { completion(conversationId); return }
        pendingMaterializationCallbacks += completion
        if (isMaterializingConversation) return
        isMaterializingConversation = true
        scope.launch {
            chatService.materializeConversation(conversation.otherParticipantId, currentUserId)
                .onSuccess { id ->
                    conversation.id = id
                    isMaterializingConversation = false
                    pendingMaterializationCallbacks.toList().also { pendingMaterializationCallbacks.clear() }.forEach { it(id) }
                    attachChatListenersIfNeeded()
                }
                .onFailure { throwable ->
                    _error.value = throwable.message
                    isMaterializingConversation = false
                    pendingMaterializationCallbacks.toList().also { pendingMaterializationCallbacks.clear() }.forEach { it(null) }
                }
        }
    }

    fun activateChatSession() {
        isChatVisible = true
        loadCachedMessagesIfNeeded()
        attachChatListenersIfNeeded()
    }

    fun deactivateChatSession() {
        isChatVisible = false
        setTyping(false)
    }

    fun stopListening() {
        deactivateChatSession()
        pauseChatListenersImmediately()
    }

    fun setTyping(typing: Boolean) {
        _isTyping.value = typing
        if (conversationId.isBlank()) return
        if (typing && _typingIndicatorEnabled.value) chatService.startTyping(conversationId, currentUserId)
        else chatService.stopTyping(conversationId, currentUserId)
    }

    fun sendBuzz(completion: (Result<Unit>) -> Unit = {}) {
        if (!canSendBuzz || conversationId.isBlank()) { completion(Result.failure(IllegalStateException())); return }
        scope.launch { completion(chatService.sendBuzz(conversationId, currentUserId)) }
    }

    fun setTypingIndicatorEnabled(enabled: Boolean) {
        _typingIndicatorEnabled.value = enabled
        if (!enabled) {
            _typingUsers.value = emptySet()
            setTyping(false)
        }
    }

    fun loadInitialMessages(limit: Int = initialWindowSize()) = scope.launch {
        if (conversationId.isBlank()) return@launch
        _isLoading.value = true
        chatService.fetchRecentMessages(conversationId, limit).onSuccess { incoming ->
            realTimeMessages.clear(); realTimeMessages += mergeMessages(incoming)
            historicalMessages.removeAll { historical -> historical.id in realTimeMessages.map { it.id }.toSet() }
            rebuildMessagesList()
        }.onFailure { _historyLoadNotice.value = HistoryLoadNotice.ERROR }
        _isLoading.value = false
    }

    fun commitMessagesPresentation(nextMessages: List<EnhancedMessage>) {
        val sorted = nextMessages.distinctBy { it.id }.sortedBy { it.timestamp }
        _chatTimelineMutation.value = forcedNextTimelineMutation ?: deriveTimelineMutation(_messages.value, sorted)
        forcedNextTimelineMutation = null; _messages.value = sorted
        rebuildMessageIndex(sorted); pruneUploadProgress(sorted); pruneLocalMessageStates(sorted)
    }

    fun appendOrReplaceMessage(message: EnhancedMessage) {
        val inHistorical = historicalMessages.indexOfFirst { it.id == message.id }
        val inRealtime = realTimeMessages.indexOfFirst { it.id == message.id }
        when {
            inHistorical >= 0 -> historicalMessages[inHistorical] = message
            inRealtime >= 0 -> realTimeMessages[inRealtime] = message
            message.id in outgoingTempMessages -> outgoingTempMessages[message.id] = message
            else -> realTimeMessages += message
        }
        rebuildMessagesList()
    }

    fun mergeMessages(remoteMessages: List<EnhancedMessage>): List<EnhancedMessage> {
        val merged = remoteMessages.toMutableList()
        _messages.value.filter { it.status == MessageStatus.SENDING }.forEach { local -> if (merged.none { it.id == local.id }) merged += local }
        outgoingTempMessages.values.forEach { local ->
            val index = merged.indexOfFirst { it.id == local.id }
            if (index < 0) merged += local
            else if (merged[index].mediaUrl.isNullOrBlank() && !local.mediaUrl.isNullOrBlank()) merged[index] = merged[index].copy(mediaUrl = local.mediaUrl, thumbnailUrl = merged[index].thumbnailUrl ?: local.thumbnailUrl, replyTo = merged[index].replyTo ?: local.replyTo)
        }
        _messages.value.forEach { local ->
            val index = merged.indexOfFirst { it.id == local.id }
            if (index >= 0 && !local.mediaUrl.isNullOrBlank() && merged[index].mediaUrl.isNullOrBlank()) merged[index] = merged[index].copy(mediaUrl = local.mediaUrl, thumbnailUrl = merged[index].thumbnailUrl ?: local.thumbnailUrl, replyTo = merged[index].replyTo ?: local.replyTo)
        }
        localMessageStates.forEach { (id, status) -> merged.indexOfFirst { it.id == id }.takeIf { it >= 0 }?.let { index -> if (statusPriority(status) >= statusPriority(merged[index].status) || status == MessageStatus.FAILED) merged[index] = merged[index].copy(status = status) } }
        return merged.filterNot { it.id in hiddenForMeMessageIds }.sortedBy { it.timestamp }
    }

    private fun rebuildMessagesList() {
        val byId = linkedMapOf<String, EnhancedMessage>()
        (historicalMessages + realTimeMessages + outgoingTempMessages.values)
            .filterNot { it.id in hiddenForMeMessageIds || it.id in optimisticallyHiddenVanishIds || (it.isVanishModeMessage && currentUserId in it.vanishedFor) }
            .sortedBy { it.timestamp }
            .forEach { candidate ->
                val reactions = _liveReactionOverlays.value[candidate.id]
                byId[candidate.id] = if (reactions == null) candidate else candidate.copy(reactions = reactions)
            }
        commitMessagesPresentation(byId.values.toList())
    }

    private fun applyFirestoreListenerMessages(incoming: List<EnhancedMessage>) {
        val existing = (historicalMessages + realTimeMessages + _messages.value).associateBy { it.id }
        realTimeMessages.clear()
        realTimeMessages += mergeMessages(incoming.map { message ->
            val cached = existing[message.id]
            val preserved = if (message.mediaUrl.isNullOrBlank() && !cached?.mediaUrl.isNullOrBlank()) {
                message.copy(mediaUrl = cached?.mediaUrl, thumbnailUrl = message.thumbnailUrl ?: cached?.thumbnailUrl)
            } else message
            if (!preserved.isRead && preserved.senderId != currentUserId && preserved.id in locallyReadMessageIds) preserved.copy(isRead = true) else preserved
        })
        _liveReactionOverlays.value = realTimeMessages.associate { it.id to it.reactions.orEmpty() }
        val realtimeIds = realTimeMessages.map { it.id }.toSet()
        historicalMessages.removeAll { it.id in realtimeIds }
        rebuildMessagesList()
        prefetchUnresolvedMediaIfNeeded()
        scope.launch { LocalPersistenceService.reconcileMessagesInBackground(realTimeMessages, conversationId) }
    }

    fun attachChatListenersIfNeeded() {
        if (conversationId.isBlank() || sessionListenersAttached) return
        sessionListenersAttached = true
        chatService.listenToMessages(conversationId) { result ->
            result.onSuccess(::applyFirestoreListenerMessages).onFailure { _error.value = it.message }
        }
        chatService.listenToConversationPreferences(conversationId) { forwarding, buzz, vanishActive, timer ->
            _forwardingPreferences.value = forwarding
            _buzzPreferences.value = buzz
            val wasActive = _vanishModeActive.value
            _vanishModeActive.value = vanishActive
            _vanishMessageTimer.value = timer
            if (wasActive && !vanishActive) purgeVanishMessagesLocally()
        }
        chatService.listenToMessageReactions(conversationId) { result ->
            result.onSuccess { update -> _liveReactionOverlays.value = update.reactionsByMessage }
                .onFailure { _error.value = it.message }
        }
        if (_typingIndicatorEnabled.value) {
            chatService.listenToTypingIndicators(conversationId)
            typingUsersJob?.cancel()
            typingUsersJob = scope.launch {
                chatService.typingUsers.collect { users ->
                    _typingUsers.value = if (_typingIndicatorEnabled.value) users[conversationId].orEmpty() - currentUserId else emptySet()
                }
            }
        }
    }

    fun pauseChatListenersImmediately() {
        if (!sessionListenersAttached) return
        sessionListenersAttached = false
        chatService.removeMessagesListener(conversationId)
        chatService.removeTypingListener(conversationId)
        chatService.removeConversationPreferencesListener(conversationId)
        chatService.removeMessageReactionsListener(conversationId)
        _liveReactionOverlays.value = emptyMap()
        typingUsersJob?.cancel(); typingUsersJob = null
        _typingUsers.value = emptySet()
    }

    fun applyOutgoingMessageUpdate(messageId: String, status: MessageStatus, mediaUrl: String? = null, thumbnailUrl: String? = null) {
        localMessageStates[messageId] = status; clearUploadProgress(messageId); outgoingTempMessages.remove(messageId)
        _messages.value.firstOrNull { it.id == messageId }?.let { existing ->
            appendOrReplaceMessage(existing.copy(status = status, mediaUrl = mediaUrl ?: existing.mediaUrl, thumbnailUrl = thumbnailUrl ?: existing.thumbnailUrl))
        }
    }

    fun finalizeOutgoingMediaMessage(messageId: String, sentMessage: EnhancedMessage, fallbackMediaUrl: String? = null, fallbackThumbnailUrl: String? = null) {
        val old = _messages.value.firstOrNull { it.id == messageId }
        applyOutgoingMessageUpdate(messageId, sentMessage.status, sentMessage.mediaUrl ?: fallbackMediaUrl ?: old?.mediaUrl, sentMessage.thumbnailUrl ?: fallbackThumbnailUrl ?: old?.thumbnailUrl)
    }

    fun appendOutgoingMessage(message: EnhancedMessage) {
        outgoingTempMessages[message.id] = message; appendOrReplaceMessage(message)
        if (message.status == MessageStatus.SENDING || message.status == MessageStatus.PENDING || message.status == MessageStatus.FAILED) scope.launch { LocalPersistenceService.saveMessagesInBackground(listOf(message), conversationId, sync = false) }
    }

    fun isDownloadingMedia(messageId: String): Boolean = messageId in downloadingMediaIds || messageId in hydratingMediaIds

    fun hydrateMediaIfNeeded(message: EnhancedMessage) {
        if (message.mediaObjectPath.isNullOrBlank() || message.mediaEncryption == null || message.id in hydratingMediaIds) return
        hydratingMediaIds += message.id; setDownloadProgress(message.id, .03)
        scope.launch {
            val resolved = ChatEncryptedMediaResolver.resolveForMessage(message, forceDownload = false)
            hydratingMediaIds -= message.id; clearDownloadProgress(message.id)
            resolved?.mediaUrl?.let { url ->
                appendOrReplaceMessage(message.copy(mediaUrl = url, thumbnailUrl = resolved.thumbnailUrl ?: message.thumbnailUrl))
                LocalPersistenceService.saveMessagesInBackground(listOf(_messages.value.first { it.id == message.id }), conversationId, sync = false)
            }
        }
    }

    fun refreshMediaMetadataIfNeeded(message: EnhancedMessage) {
        if (message.id in refreshingMetadataIds || conversationId.isBlank()) return
        if (!message.mediaUrl.isNullOrBlank() && !message.thumbnailUrl.isNullOrBlank()) return
        refreshingMetadataIds += message.id
        scope.launch {
            val fresh = chatService.fetchMessage(conversationId, message.id).getOrNull()
            refreshingMetadataIds -= message.id
            if (fresh != null) {
                val merged = fresh.copy(
                    mediaUrl = fresh.mediaUrl ?: message.mediaUrl,
                    thumbnailUrl = fresh.thumbnailUrl ?: message.thumbnailUrl,
                )
                appendOrReplaceMessage(merged)
                hydrateMediaIfNeeded(merged)
            }
        }
    }

    fun hydrateVideoThumbnailIfNeeded(message: EnhancedMessage) = hydrateThumbnailPreviewIfNeeded(message)

    fun hydrateThumbnailPreviewIfNeeded(message: EnhancedMessage) {
        if (message.thumbnailEncryption == null || !message.thumbnailUrl.isNullOrBlank() || message.id in hydratingMediaIds) return
        hydratingMediaIds += message.id
        setDownloadProgress(message.id, .03)
        scope.launch {
            val thumbnail = ChatEncryptedMediaResolver.resolveThumbnailURL(message, forceDownload = false)
            hydratingMediaIds -= message.id
            clearDownloadProgress(message.id)
            if (thumbnail != null) {
                val updated = message.copy(thumbnailUrl = thumbnail)
                appendOrReplaceMessage(updated)
                LocalPersistenceService.saveMessagesInBackground(listOf(updated), conversationId, sync = false)
            }
        }
    }

    fun openMediaForViewing(message: EnhancedMessage, completion: (EnhancedMessage) -> Unit) {
        if (message.mediaObjectPath.isNullOrBlank() || message.mediaEncryption == null) { completion(message); return }
        if (!downloadingMediaIds.add(message.id)) return
        setDownloadProgress(message.id, .03)
        scope.launch {
            val resolved = ChatEncryptedMediaResolver.resolveForMessage(message, forceDownload = true)
            downloadingMediaIds -= message.id; clearDownloadProgress(message.id)
            val updated = resolved?.mediaUrl?.let { message.copy(mediaUrl = it, thumbnailUrl = resolved.thumbnailUrl ?: message.thumbnailUrl) } ?: message
            appendOrReplaceMessage(updated); completion(updated)
        }
    }

    fun prefetchUnresolvedMediaIfNeeded() { _messages.value.forEach(::hydrateMediaIfNeeded) }
    fun warmDiskMediaUrls() {
        val updated = _messages.value.map { message -> ChatEncryptedMediaResolver.warmMessageURLsFromDiskCache(message).let { cached -> message.copy(mediaUrl = cached.mediaUrl ?: message.mediaUrl, thumbnailUrl = cached.thumbnailUrl ?: message.thumbnailUrl) } }
        commitMessagesPresentation(updated)
    }

    fun warmAndApplyDiskUrls(items: List<EnhancedMessage>): List<EnhancedMessage> =
        items.map { message ->
            val cached = ChatEncryptedMediaResolver.warmMessageURLsFromDiskCache(message)
            message.copy(mediaUrl = cached.mediaUrl ?: message.mediaUrl, thumbnailUrl = cached.thumbnailUrl ?: message.thumbnailUrl)
        }

    fun loadMoreMessages() = scope.launch {
        val oldest = _messages.value.firstOrNull() ?: return@launch
        if (_isLoadingMore.value || !_canLoadMore.value || conversationId.isBlank()) return@launch
        _isLoadingMore.value = true; _historyLoadNotice.value = HistoryLoadNotice.HIDDEN
        val page = LocalPersistenceService.loadMessagesBeforeInBackground(conversationId, MessageSyncCursor(oldest.timestamp, oldest.id), limit = historyPageSize)
        if (page.isNotEmpty()) {
            forcedNextTimelineMutation = ChatTimelineMutation(ChatListUpdateKind.PREPEND_HISTORY, ChatTimelineUpdateReason.HISTORY, oldest.id)
            val present = (historicalMessages + realTimeMessages).associateBy { it.id }.toMutableMap()
            page.forEach { present[it.id] = it }
            historicalMessages.clear(); historicalMessages += present.values.sortedBy { it.timestamp }
            historicalMessages.removeAll { it.id in realTimeMessages.map { item -> item.id }.toSet() }
            rebuildMessagesList()
            _canLoadMore.value = page.size >= historyPageSize
            _isLoadingMore.value = false
            return@launch
        }
        chatService.fetchMessagesBefore(conversationId, MessageSyncCursor(oldest.timestamp, oldest.id), historyPageSize)
            .onSuccess { remote ->
                if (remote.isEmpty()) _canLoadMore.value = false
                else {
                    forcedNextTimelineMutation = ChatTimelineMutation(ChatListUpdateKind.PREPEND_HISTORY, ChatTimelineUpdateReason.HISTORY, oldest.id)
                    historicalMessages.addAll(remote.filter { remoteMessage -> historicalMessages.none { it.id == remoteMessage.id } })
                    historicalMessages.sortBy { it.timestamp }
                    rebuildMessagesList()
                    scope.launch { LocalPersistenceService.saveMessagesInBackground(remote, conversationId, sync = false) }
                    _canLoadMore.value = remote.size >= historyPageSize
                }
            }
            .onFailure { _historyLoadNotice.value = HistoryLoadNotice.ERROR }
        _isLoadingMore.value = false
    }
    fun clearHistoryLoadNotice() { _historyLoadNotice.value = HistoryLoadNotice.HIDDEN }

    private fun initialWindowSize(): Int {
        val ageMillis = (Date().time - conversation.timestamp.time).coerceAtLeast(0)
        return if (ageMillis > staleChatThresholdDays * 24L * 60L * 60L * 1000L) staleChatWindowSize else recentChatWindowSize
    }

    fun loadCachedMessagesIfNeeded() = scope.launch {
        if (didLoadCache || conversationId.isBlank()) return@launch
        didLoadCache = true
        val window = LocalPersistenceService.loadRecentMessagesInBackground(conversationId, recentChatWindowSize)
        if (window.isNotEmpty()) {
            historicalMessages.clear(); historicalMessages += mergeMessages(window)
            rebuildMessagesList()
        }
        warmDiskMediaUrls(); prefetchUnresolvedMediaIfNeeded()
    }

    suspend fun navigateToMessage(messageId: String): Boolean {
        if (messageId.isBlank()) return false
        if (_messages.value.any { it.id == messageId }) return true
        if (conversationId.isBlank() || !requestedHighlightMessageIds.add(messageId)) return false
        return try {
            val all = LocalPersistenceService.loadMessagesInBackground(conversationId)
            val target = all.firstOrNull { it.id == messageId } ?: chatService.fetchMessage(conversationId, messageId).getOrNull() ?: return false
            val index = all.indexOfFirst { it.id == target.id }.takeIf { it >= 0 } ?: 0
            val radius = navigationWindowRadius
            val window = if (all.isEmpty()) listOf(target) else all.subList((index - radius).coerceAtLeast(0), (index + radius + 1).coerceAtMost(all.size)).let { if (it.any { message -> message.id == target.id }) it else it + target }
            forcedNextTimelineMutation = ChatTimelineMutation(ChatListUpdateKind.JUMP, ChatTimelineUpdateReason.HIGHLIGHT, target.id)
            historicalMessages.clear(); historicalMessages += mergeMessages(window)
            realTimeMessages.clear()
            rebuildMessagesList()
            _messages.value.any { it.id == target.id }
        } finally { requestedHighlightMessageIds.remove(messageId) }
    }

    fun updateMessageStatus(messageId: String, status: MessageStatus) {
        localMessageStates[messageId] = status
        _messages.value.firstOrNull { it.id == messageId }?.let { appendOrReplaceMessage(it.copy(status = status)) }
    }

    open fun sendTextMessage(text: String, replyTo: String? = null) {
        if (text.isBlank()) return
        if (conversationId.isBlank()) { ensureConversationExists { if (!it.isNullOrBlank()) sendTextMessage(text, replyTo) }; return }
        val messageId = UUID.randomUUID().toString()
        appendOutgoingMessage(EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = MessageType.TEXT, content = text, timestamp = Date(), status = MessageStatus.SENDING,
            replyTo = replyTo, isVanishModeMessage = _vanishModeActive.value,
        ))
        scope.launch {
            chatService.sendTextMessage(conversationId, currentUserId, text, replyTo, messageId, _vanishModeActive.value)
                .onSuccess { applyOutgoingMessageUpdate(messageId, it.status) }
                .onFailure { throwable -> _error.value = throwable.message; applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
        }
    }

    fun sendMediaMessage(
        data: ByteArray,
        type: MessageType,
        fileName: String? = null,
        mediaBatchId: String? = null,
        replyTo: String? = null,
    ) {
        if (data.isEmpty()) return
        if (conversationId.isBlank()) { ensureConversationExists { if (!it.isNullOrBlank()) sendMediaMessage(data, type, fileName, mediaBatchId, replyTo) }; return }
        val messageId = UUID.randomUUID().toString()
        appendOutgoingMessage(EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = type, fileName = fileName, fileSize = data.size.toLong(), timestamp = Date(),
            status = MessageStatus.SENDING, mediaBatchId = mediaBatchId, replyTo = replyTo, isVanishModeMessage = _vanishModeActive.value,
        ))
        scope.launch {
            chatService.sendMediaMessage(conversationId, currentUserId, type, data, fileName, messageId, mediaBatchId, _vanishModeActive.value, null, replyTo)
                .onSuccess { finalizeOutgoingMediaMessage(messageId, it) }
                .onFailure { throwable -> _error.value = throwable.message; applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
        }
    }

    fun sendImageMessage(data: ByteArray, mediaBatchId: String? = null) =
        sendMediaMessage(data, MessageType.IMAGE, "image_${UUID.randomUUID()}.jpg", mediaBatchId)

    open fun sendVideoMessage(data: ByteArray, mediaBatchId: String? = null, replyTo: String? = null) =
        sendMediaMessage(data, MessageType.VIDEO, "video_${UUID.randomUUID()}.mp4", mediaBatchId, replyTo)

    fun sendAudioMessage(data: ByteArray, duration: Double, waveform: List<Float>? = null) {
        if (data.isEmpty()) return
        if (conversationId.isBlank()) { ensureConversationExists { if (!it.isNullOrBlank()) sendAudioMessage(data, duration, waveform) }; return }
        val messageId = UUID.randomUUID().toString()
        appendOutgoingMessage(EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = MessageType.AUDIO, duration = duration, audioWaveform = waveform,
            fileName = "audio_$messageId.m4a", fileSize = data.size.toLong(), timestamp = Date(), status = MessageStatus.SENDING, isVanishModeMessage = _vanishModeActive.value,
        ))
        scope.launch {
            chatService.sendAudioMessage(conversationId, currentUserId, data, duration, waveform, messageId, _vanishModeActive.value)
                .onSuccess { finalizeOutgoingMediaMessage(messageId, it) }
                .onFailure { throwable -> _error.value = throwable.message; applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
        }
    }

    fun sendLocationMessage(latitude: Double, longitude: Double) {
        if (conversationId.isBlank()) { ensureConversationExists { if (!it.isNullOrBlank()) sendLocationMessage(latitude, longitude) }; return }
        val messageId = UUID.randomUUID().toString()
        val message = EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = MessageType.LOCATION, latitude = latitude, longitude = longitude,
            timestamp = Date(), status = MessageStatus.SENDING, isVanishModeMessage = _vanishModeActive.value,
        )
        appendOutgoingMessage(message)
        scope.launch {
            chatService.sendMessage(message).onSuccess { applyOutgoingMessageUpdate(messageId, it.status) }
                .onFailure { throwable -> _error.value = throwable.message; applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
        }
    }

    fun addReaction(message: EnhancedMessage, emoji: String) {
        if (conversationId.isBlank() || emoji.isBlank()) return
        val wasActive = currentUserId in message.reactions?.get(emoji).orEmpty()
        val current = MessageReactionMutation.apply(message.reactions, emoji, currentUserId)
        val updated = message.copy(reactions = current)
        _liveReactionOverlays.value = _liveReactionOverlays.value + (message.id to current.orEmpty())
        appendOrReplaceMessage(updated)
        LocalPersistenceService.toggleMessageReactionLocally(message.id, emoji, currentUserId)
        scope.launch { chatService.setMessageReaction(conversationId, message.id, emoji, currentUserId, !wasActive).onFailure { _error.value = it.message } }
    }

    fun editMessage(message: EnhancedMessage, newContent: String) {
        if (conversationId.isBlank() || !ChatMessagePolicy.canEdit(message, currentUserId) || newContent.isBlank()) return
        appendOrReplaceMessage(message.copy(content = newContent, editedAt = Date()))
        scope.launch { chatService.editMessage(conversationId, message.id, newContent).onFailure { _error.value = it.message } }
    }

    fun forwardTextMessage(message: EnhancedMessage, toUserIds: Set<String>) {
        if (conversationId.isBlank() || !ChatMessagePolicy.canForward(message, currentUserId, _forwardingPreferences.value) || toUserIds.isEmpty()) return
        val encryptedContent = message.content ?: return
        scope.launch {
            val plaintext = chatService.decryptMessageContent(encryptedContent, conversationId)
            chatService.forwardTextMessage(plaintext, toUserIds, currentUserId).onFailure { _error.value = it.message }
        }
    }

    fun isStarred(messageId: String): Boolean = messageId in starredMessageIds

    fun toggleStar(message: EnhancedMessage) {
        if (conversationId.isBlank()) return
        val active = starredMessageIds.add(message.id)
        if (!active) starredMessageIds.remove(message.id)
        scope.launch { chatService.toggleMessageStar(conversationId, message.id, currentUserId, active).onFailure { _error.value = it.message } }
    }

    fun deleteMessageForEveryone(message: EnhancedMessage) {
        if (conversationId.isBlank() || message.senderId != currentUserId) return
        appendOrReplaceMessage(message.copy(isDeleted = true, deletedAt = Date(), mediaUrl = null, thumbnailUrl = null))
        scope.launch { chatService.deleteMessageForEveryone(conversationId, message.id).onFailure { _error.value = it.message } }
    }

    fun applyDeletedForEveryoneLocally(message: EnhancedMessage) {
        outgoingTempMessages.remove(message.id)
        localMessageStates.remove(message.id)
        clearUploadProgress(message.id); clearDownloadProgress(message.id)
        val deleted = message.copy(isDeleted = true, deletedAt = Date(), mediaUrl = null, thumbnailUrl = null)
        historicalMessages.removeAll { it.id == message.id }
        realTimeMessages.removeAll { it.id == message.id }
        realTimeMessages += deleted
        rebuildMessagesList()
    }

    fun deleteMessageForMe(message: EnhancedMessage) {
        if (conversationId.isBlank()) return
        hiddenForMeMessageIds += message.id
        commitMessagesPresentation(_messages.value.filterNot { it.id == message.id })
        LocalPersistenceService.removeCachedMessage(conversationId, message.id)
        scope.launch { chatService.deleteMessageForMe(conversationId, message.id, currentUserId).onFailure { _error.value = it.message } }
    }

    fun applyDeletedForMeLocally(message: EnhancedMessage) {
        hiddenForMeMessageIds += message.id
        outgoingTempMessages.remove(message.id)
        localMessageStates.remove(message.id)
        clearUploadProgress(message.id); clearDownloadProgress(message.id)
        historicalMessages.removeAll { it.id == message.id }
        realTimeMessages.removeAll { it.id == message.id }
        LocalPersistenceService.removeCachedMessage(conversationId, message.id)
        rebuildMessagesList()
    }

    fun canRetryMessage(message: EnhancedMessage): Boolean =
        message.senderId == currentUserId && message.status == MessageStatus.FAILED && !message.isDeleted && conversationId.isNotBlank() && when (message.type) {
            MessageType.TEXT -> !message.content.isNullOrBlank()
            MessageType.LOCATION, MessageType.GIF, MessageType.STICKER -> true
            MessageType.IMAGE, MessageType.VIDEO, MessageType.AUDIO -> !message.mediaUrl.isNullOrBlank()
            else -> false
        }

    fun retryFailedMessage(message: EnhancedMessage) {
        if (!canRetryMessage(message)) return
        updateMessageStatus(message.id, MessageStatus.SENDING)
        scope.launch {
            val result = when (message.type) {
                MessageType.IMAGE, MessageType.VIDEO -> localFileBytes(message.mediaUrl)?.let { bytes ->
                    chatService.sendMediaMessage(conversationId, currentUserId, message.type, bytes, message.fileName, message.id, message.mediaBatchId, message.isVanishModeMessage, message.vanishExpiresAt, message.replyTo)
                } ?: chatService.sendMessage(message.copy(status = MessageStatus.SENDING))
                MessageType.AUDIO -> localFileBytes(message.mediaUrl)?.let { bytes ->
                    chatService.sendAudioMessage(conversationId, currentUserId, bytes, message.duration ?: 0.0, message.audioWaveform, message.id, message.isVanishModeMessage)
                } ?: chatService.sendMessage(message.copy(status = MessageStatus.SENDING))
                else -> chatService.sendMessage(message.copy(status = MessageStatus.SENDING))
            }
            result.onSuccess { sent -> finalizeOutgoingMediaMessage(message.id, sent, message.mediaUrl, message.thumbnailUrl) }
                .onFailure { throwable -> _error.value = throwable.message; updateMessageStatus(message.id, MessageStatus.FAILED) }
        }
    }

    private fun localFileBytes(uriText: String?): ByteArray? = runCatching {
        val path = android.net.Uri.parse(uriText).path ?: return null
        java.io.File(path).takeIf { it.isFile }?.readBytes()
    }.getOrNull()

    fun displayReactions(messageId: String): Map<String, List<String>>? =
        _liveReactionOverlays.value[messageId] ?: _messages.value.firstOrNull { it.id == messageId }?.reactions

    fun prepareMediaForViewing(message: EnhancedMessage, completion: (EnhancedMessage) -> Unit) =
        openMediaForViewing(message, completion)

    fun prefetchClusterGalleryMedia(messages: List<EnhancedMessage>) = messages.forEach(::hydrateMediaIfNeeded)

    fun markVisibleConversationAsRead() {
        if (!isChatVisible || conversationId.isBlank()) return
        val unread = _messages.value.filter { it.senderId != currentUserId && !it.isRead }.map { it.id }
        locallyReadMessageIds += unread
        sessionSeenIncomingMessageIds += unread
        if (unread.isNotEmpty()) commitMessagesPresentation(_messages.value.map { if (it.id in unread) it.copy(isRead = true, status = MessageStatus.READ) else it })
        scope.launch {
            if (unread.isEmpty()) chatService.markConversationAsRead(conversationId, currentUserId)
            else chatService.markMessagesAsRead(conversationId, unread, currentUserId, unread.lastOrNull() == _messages.value.lastOrNull { it.senderId != currentUserId }?.id)
        }
    }

    fun performSearch(query: String) {
        val normalized = query.trim()
        val generation = ++searchGeneration
        if (normalized.isEmpty() || conversationId.isBlank()) { clearSearch(); return }
        _isSearchingHistory.value = true
        scope.launch {
            val local = LocalPersistenceService.searchMessageIds(conversationId, normalized, 100)
            val memory = _messages.value.filter { listOfNotNull(it.content, it.fileName).any { value -> value.contains(normalized, ignoreCase = true) } }.map { it.id }
            if (generation == searchGeneration) {
                _searchResults.value = (local + memory).distinct()
                _isSearchingHistory.value = false
            }
        }
    }

    fun clearSearch() { ++searchGeneration; _searchResults.value = emptyList(); _isSearchingHistory.value = false }

    fun sendEphemeralMessage(content: String?, mediaUrl: String?, durationHours: Int = 24) {
        if (conversationId.isBlank()) return
        val message = EnhancedMessage(
            id = UUID.randomUUID().toString(), conversationId = conversationId, senderId = currentUserId,
            type = MessageType.EPHEMERAL, content = content, mediaUrl = mediaUrl, timestamp = Date(),
            expirationDate = Date(Date().time + durationHours.coerceAtLeast(1) * 60L * 60L * 1000L), status = MessageStatus.SENDING,
        )
        appendOutgoingMessage(message)
        scope.launch { chatService.sendMessage(message).onSuccess { applyOutgoingMessageUpdate(message.id, it.status) }.onFailure { _error.value = it.message; updateMessageStatus(message.id, MessageStatus.FAILED) } }
    }

    fun markEphemeralAsViewed(message: EnhancedMessage) {
        if (conversationId.isBlank()) return
        appendOrReplaceMessage(message.copy(isViewed = true))
        scope.launch { chatService.markEphemeralAsViewed(conversationId, message.id).onFailure { _error.value = it.message } }
    }

    val outgoingVanishMessageFlag: Boolean? get() = _vanishModeActive.value.takeIf { it }
    val marksOutgoingAsVanish: Boolean get() = _vanishModeActive.value

    fun toggleVanishMode(completion: ((Throwable?) -> Unit)? = null) {
        if (conversationId.isBlank()) { completion?.invoke(IllegalStateException()); return }
        val target = !_vanishModeActive.value
        scope.launch {
            chatService.setVanishMode(conversationId, target, if (target) _vanishMessageTimer.value else null)
                .onSuccess {
                    _vanishModeActive.value = target
                    if (!target) purgeVanishMessagesLocally()
                    sendVanishNotice(if (target) _vanishMessageTimer.value.enabledNoticeToken else VanishMessageTimer.DISABLED_NOTICE_TOKEN)
                    completion?.invoke(null)
                }
                .onFailure { throwable -> _error.value = throwable.message; completion?.invoke(throwable) }
        }
    }

    fun setVanishMessageTimer(timer: VanishMessageTimer?, completion: ((Throwable?) -> Unit)? = null) {
        if (timer == null) { if (_vanishModeActive.value) toggleVanishMode(completion) else completion?.invoke(null); return }
        if (conversationId.isBlank()) { completion?.invoke(IllegalStateException()); return }
        scope.launch {
            chatService.setVanishMessageTimer(conversationId, timer)
                .onSuccess {
                    _vanishMessageTimer.value = timer
                    if (_vanishModeActive.value) sendVanishNotice(timer.enabledNoticeToken)
                    completion?.invoke(null)
                }
                .onFailure { throwable -> _error.value = throwable.message; completion?.invoke(throwable) }
        }
    }

    fun handleChatDismissedForVanishMode() {
        if (conversationId.isBlank()) return
        markVisibleConversationAsRead()
        val eligible = _messages.value.filter { message ->
            message.isVanishModeMessage && message.type != MessageType.CHAT_NOTICE &&
                (message.senderId == currentUserId || message.id in sessionSeenIncomingMessageIds || VanishMessageTimer.isExpired(message.vanishExpiresAt))
        }.map { it.id }
        if (eligible.isEmpty()) return
        optimisticallyHiddenVanishIds += eligible
        LocalPersistenceService.markVanishMessagesDismissed(conversationId, eligible, currentUserId)
        scope.launch { chatService.markVanishMessagesVanishedForMe(conversationId, eligible, currentUserId) }
        rebuildMessagesList()
    }

    fun refreshVanishExpiryPresentation() { if (_vanishMessageTimer.value != VanishMessageTimer.ONCE_SEEN) rebuildMessagesList() }

    private fun sendVanishNotice(token: String) = scope.launch {
        chatService.sendChatNotice(conversationId, currentUserId, token).onSuccess(::appendOrReplaceMessage).onFailure { _error.value = it.message }
    }

    private fun stampVanishExpiryIfNeeded() {
        if (!_vanishModeActive.value || _vanishMessageTimer.value == VanishMessageTimer.ONCE_SEEN || conversationId.isBlank()) return
        val expiresAt = _vanishMessageTimer.value.expiresAt() ?: return
        val updated = _messages.value.map { message ->
            if (message.isVanishModeMessage && message.type != MessageType.CHAT_NOTICE && message.vanishExpiresAt == null && message.isRead) {
                scope.launch { chatService.stampVanishExpiry(conversationId, message.id, expiresAt) }
                message.copy(vanishExpiresAt = expiresAt)
            } else message
        }
        commitMessagesPresentation(updated)
    }

    private fun purgeVanishMessagesLocally() {
        val ids = _messages.value.filter { it.isVanishModeMessage && it.type != MessageType.CHAT_NOTICE }.map { it.id }
        if (ids.isEmpty()) return
        optimisticallyHiddenVanishIds += ids
        chatService.purgeVanishMessagesLocally(conversationId, ids)
        rebuildMessagesList()
    }

    fun reportVanishScreenshotIfNeeded() { reportVanishCapture(VanishMessageTimer.SCREENSHOT_NOTICE_TOKEN) }
    fun reportVanishScreenRecordingIfNeeded() { reportVanishCapture(VanishMessageTimer.SCREEN_RECORDING_NOTICE_TOKEN) }
    private fun reportVanishCapture(token: String) {
        if (!_vanishModeActive.value || conversationId.isBlank()) return
        scope.launch { chatService.reportVanishCapture(conversationId, currentUserId, token).onFailure { _error.value = it.message } }
    }

    fun setUploadProgress(messageId: String, progress: Double) { publishProgress(_uploadProgress, messageId, progress) }
    fun setDownloadProgress(messageId: String, progress: Double) { publishProgress(_downloadProgress, messageId, progress) }
    fun clearUploadProgress(messageId: String) { _uploadProgress.value = _uploadProgress.value - messageId }
    fun clearDownloadProgress(messageId: String) { _downloadProgress.value = _downloadProgress.value - messageId }

    private fun publishProgress(flow: MutableStateFlow<Map<String, Double>>, id: String, progress: Double) {
        val value = progress.coerceIn(0.0, 1.0); val previous = flow.value[id]
        if (previous == null || value <= 0 || value >= 1 || kotlin.math.abs(previous - value) >= .03) flow.value = flow.value + (id to value)
    }
    private fun rebuildMessageIndex(items: List<EnhancedMessage>) { messagesById = items.associateBy { it.id }; messageIndexById = items.mapIndexed { index, message -> message.id to index }.toMap(); unreadIncomingCount = items.count { !it.isRead && it.senderId != currentUserId } }
    private fun pruneUploadProgress(items: List<EnhancedMessage>) { val active = items.filter { it.status == MessageStatus.SENDING }.map { it.id }.toSet(); _uploadProgress.value = _uploadProgress.value.filterKeys(active::contains) }
    private fun pruneLocalMessageStates(items: List<EnhancedMessage>) { val remote = items.associateBy { it.id }; localMessageStates.entries.removeAll { (id, state) -> remote[id]?.status?.ordinal?.let { it >= state.ordinal && state != MessageStatus.FAILED } ?: false } }
    private fun statusPriority(status: MessageStatus): Int = when (status) { MessageStatus.SENDING -> 0; MessageStatus.SENT -> 1; MessageStatus.DELIVERED -> 2; MessageStatus.READ -> 3; MessageStatus.FAILED, MessageStatus.PENDING -> -1 }
    private fun deriveTimelineMutation(old: List<EnhancedMessage>, next: List<EnhancedMessage>): ChatTimelineMutation {
        if (next.isEmpty()) return ChatTimelineMutation(ChatListUpdateKind.REPLACE_ALL, ChatTimelineUpdateReason.LAYOUT)
        if (old.isEmpty()) return ChatTimelineMutation.INITIAL
        val oldIds = old.map { it.id }; val nextIds = next.map { it.id }
        if (oldIds == nextIds) return ChatTimelineMutation(ChatListUpdateKind.RECONFIGURE_ROWS, ChatTimelineUpdateReason.LAYOUT)
        if (nextIds.size > oldIds.size && nextIds.takeLast(oldIds.size) == oldIds) return ChatTimelineMutation(ChatListUpdateKind.PREPEND_HISTORY, ChatTimelineUpdateReason.HISTORY, old.firstOrNull()?.id)
        if (nextIds.size > oldIds.size && nextIds.take(oldIds.size) == oldIds) return ChatTimelineMutation(ChatListUpdateKind.APPEND_MESSAGES, if (next.last().senderId == currentUserId) ChatTimelineUpdateReason.OUTGOING else ChatTimelineUpdateReason.INCOMING)
        return ChatTimelineMutation(ChatListUpdateKind.REPLACE_ALL, ChatTimelineUpdateReason.LAYOUT)
    }
    companion object { const val recentChatWindowSize = 20; const val staleChatWindowSize = 6; const val staleChatThresholdDays = 45; const val historyPageSize = 50; const val navigationWindowRadius = 25 }
}
