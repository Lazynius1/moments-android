package com.moments.android.views.messaging.core

import com.moments.android.views.messaging.components.ChatListUpdateKind
import com.moments.android.views.messaging.components.ChatTimelineUpdateReason
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.ChatEncryptedMediaResolver
import com.moments.android.views.messaging.services.ChatMediaUploadProgressEvents
import com.moments.android.views.messaging.services.ChatMediaDownloadProgressEvents
import com.moments.android.views.messaging.services.markEphemeralAsViewed
import com.moments.android.views.messaging.services.forwardTextMessage
import com.moments.android.views.messaging.services.toggleMessageStar
import com.moments.android.views.messaging.services.listenToMessageReactions
import com.moments.android.views.messaging.services.removeMessageReactionsListener
import com.moments.android.views.messaging.services.mergeLegacyAndLiveReactions
import com.moments.android.views.messaging.services.ChatBuzzEvent
import com.moments.android.views.messaging.services.ChatBuzzProcessedStore
import com.moments.android.views.messaging.services.ChatDraftEvent
import com.moments.android.views.messaging.services.ChatDraftEvents
import com.moments.android.views.messaging.services.ConversationBuzzPreferenceEvents
import com.moments.android.views.messaging.services.ConversationForwardingPreferenceEvents
import com.moments.android.views.messaging.services.listenToBuzzEvents
import com.moments.android.views.messaging.services.removeBuzzListener
import com.moments.android.views.messaging.services.sendBuzz
import com.moments.android.views.messaging.services.setVanishMode
import com.moments.android.views.messaging.services.setVanishMessageTimer
import com.moments.android.views.messaging.services.sendChatNotice
import com.moments.android.views.messaging.services.setVanishSettingsNoticeMessageId
import com.moments.android.views.messaging.services.setVanishDisabledNoticeMessageId
import com.moments.android.views.messaging.services.clearVanishSettingsNoticeMessageId
import com.moments.android.views.messaging.services.clearVanishDisabledNoticeMessageId
import com.moments.android.views.messaging.services.updateChatNotice
import com.moments.android.views.messaging.services.stampVanishExpiry
import com.moments.android.views.messaging.services.markVanishMessagesVanishedForMe
import com.moments.android.views.messaging.services.purgeVanishMessagesLocally
import com.moments.android.views.messaging.services.reportVanishScreenshot
import com.moments.android.views.messaging.services.reportVanishScreenRecording
import com.moments.android.views.messaging.services.shouldHideVanishOnChatDismiss
import com.moments.android.views.messaging.services.everyoneHasSeen
import com.moments.android.views.messaging.services.getFileExtension
import com.moments.android.views.messaging.services.searchMessages
import com.moments.android.views.messaging.services.MessageReactionUpdate
import com.moments.android.views.messaging.services.ChatNavigationIntentStore
import com.moments.android.views.messaging.services.ChatVideoPosterGenerator
import com.moments.android.views.messaging.services.resolveVideoThumbnail
import com.moments.android.views.messaging.services.warmMessageURLsFromDiskCache
import com.moments.android.utilities.EmojiUsageStore
import com.moments.android.MomentsApplication
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.ChatMediaDownloadPolicy
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.messaging.LocalFirstMessagingSettings
import com.moments.android.services.messaging.MessagingEvents
import com.moments.android.services.messaging.VanishMessageTimer
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.social.AffinityInteractionType
import com.moments.android.services.social.AffinityTracker
import com.moments.android.views.messaging.screens.chat.MomentsChatViewModel
import com.moments.android.views.messaging.services.ChatSessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File
import java.util.Date
import java.util.UUID
import android.content.Context
import android.net.Uri

/** Port en curso de `Views/Messaging/Core/ChatViewModel.swift` (`EnhancedChatViewModel`). */
open class EnhancedChatViewModel(
    val conversation: Conversation,
    val currentUserId: String,
    private val chatService: ChatService = ChatService,
) {
    enum class HistoryLoadNotice { HIDDEN, OFFLINE, ERROR }
    enum class ChatSessionMode { IDLE, ACTIVE }
    data class ChatTimelineMutation(val kind: ChatListUpdateKind, val reason: ChatTimelineUpdateReason, val anchorMessageId: String? = null) {
        companion object { val INITIAL = ChatTimelineMutation(ChatListUpdateKind.INITIAL, ChatTimelineUpdateReason.LAYOUT) }
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
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

    /** ≡ `viewModel.error = …` en iOS. */
    fun reportError(message: String?) {
        _error.value = message
    }

    fun clearError() {
        _error.value = null
    }
    private val _isTyping = MutableStateFlow(false); val isTyping = _isTyping.asStateFlow()
    private val _typingIndicatorEnabled = MutableStateFlow(true); val typingIndicatorEnabled = _typingIndicatorEnabled.asStateFlow()
    private val _forwardingPreferences = MutableStateFlow(conversation.forwardingPreferences.orEmpty())
    val forwardingPreferences = _forwardingPreferences.asStateFlow()
    private val _buzzPreferences = MutableStateFlow(conversation.buzzPreferences.orEmpty())
    val buzzPreferences = _buzzPreferences.asStateFlow()
    private val _isSearchingHistory = MutableStateFlow(false); val isSearchingHistory = _isSearchingHistory.asStateFlow()
    private val _searchResults = MutableStateFlow<List<String>>(emptyList()); val searchResults = _searchResults.asStateFlow()
    private val _vanishModeActive = MutableStateFlow(conversation.vanishModeActive == true)
    val vanishModeActive = _vanishModeActive.asStateFlow()
    private val _vanishMessageTimer = MutableStateFlow(VanishMessageTimer.fromStored(conversation.vanishMessageTimer))
    val vanishMessageTimer = _vanishMessageTimer.asStateFlow()
    private val _liveReactionOverlays = MutableStateFlow<Map<String, Map<String, List<String>>>>(emptyMap()); val reactionOverlays = _liveReactionOverlays.asStateFlow()
    private val _buzzEvents = MutableStateFlow<List<ChatBuzzEvent>>(emptyList()); val buzzEvents = _buzzEvents.asStateFlow()
    private val _latestBuzzEvent = MutableStateFlow<ChatBuzzEvent?>(null); val latestBuzzEvent = _latestBuzzEvent.asStateFlow()
    private val _isLoadingOlderHistory = MutableStateFlow(false); val isLoadingOlderHistory = _isLoadingOlderHistory.asStateFlow()
    private val seenBuzzEventIds = mutableSetOf<String>()
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
    private var chatSessionMode: ChatSessionMode = ChatSessionMode.IDLE
    private var listenerPauseJob: Job? = null
    private var hasReceivedInitialReactionSnapshot = false
    private var typingUsersJob: Job? = null
    private var typingStopJob: Job? = null
    private var searchDebounceJob: Job? = null
    private var searchGeneration = 0
    private var activeSearchToken = 0
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

    init {
        // ≡ setupLocalStatusListener / progress + MessageStatusUpdated
        scope.launch {
            ChatMediaUploadProgressEvents.events.collect { event ->
                setUploadProgress(event.messageId, event.progress)
            }
        }
        scope.launch {
            ChatMediaDownloadProgressEvents.events.collect { event ->
                setDownloadProgress(event.messageId, event.progress)
            }
        }
        scope.launch {
            chatService.messageStatusUpdates.collect { update ->
                if (update.conversationId != conversationId) return@collect
                updateMessageStatus(update.messageId, update.status)
            }
        }
        setupIngestListener()
        setupConversationPreferenceListener()
        refreshTypingIndicatorPreference()
        refreshForwardingPreference()
    }

    /** ≡ `setupConversationPreferenceListener` — forwarding/buzz en vivo desde ConversationSettings. */
    private fun setupConversationPreferenceListener() {
        scope.launch {
            ConversationForwardingPreferenceEvents.events.collect { event ->
                if (event.conversationId != conversationId) return@collect
                _forwardingPreferences.value = _forwardingPreferences.value + (event.userId to event.allowsForwarding)
            }
        }
        scope.launch {
            ConversationBuzzPreferenceEvents.events.collect { event ->
                if (event.conversationId != conversationId) return@collect
                _buzzPreferences.value = _buzzPreferences.value + (event.userId to event.allowsBuzz)
            }
        }
    }

    /** ≡ `setupIngestListener` — merge local-first cuando hay ingest. */
    private fun setupIngestListener() {
        scope.launch {
            MessagingEvents.messagesIngested.collect { event ->
                if (event.conversationId != conversationId) return@collect
                mergeMessagesFromLocalCache()
            }
        }
    }

    fun ensureConversationExists(completion: (String?) -> Unit) {
        if (!isDraftConversation) { completion(conversationId); return }
        pendingMaterializationCallbacks += completion
        if (isMaterializingConversation) return
        isMaterializingConversation = true
        scope.launch {
            chatService.materializeConversation(conversation.otherParticipantId, currentUserId)
                .onSuccess { id ->
                    isMaterializingConversation = false
                    adoptMaterializedConversationId(id)
                    pendingMaterializationCallbacks.toList().also { pendingMaterializationCallbacks.clear() }.forEach { it(id) }
                }
                .onFailure { throwable ->
                    _error.value = throwable.message
                    isMaterializingConversation = false
                    pendingMaterializationCallbacks.toList().also { pendingMaterializationCallbacks.clear() }.forEach { it(null) }
                }
        }
    }

    /** ≡ `adoptMaterializedConversationId`. */
    private fun adoptMaterializedConversationId(newId: String) {
        if (!isDraftConversation || newId.isBlank()) return
        conversation.id = newId
        (this as? MomentsChatViewModel)?.let { session ->
            ChatSessionEngine.registerMaterializedSession(session, newId)
        }
        ChatSessionEngine.activate(newId)
    }

    fun activateChatSession() {
        listenerPauseJob?.cancel()
        listenerPauseJob = null
        chatSessionMode = ChatSessionMode.ACTIVE
        isChatVisible = true
        refreshTypingIndicatorPreference()
        refreshForwardingPreference()
        loadCachedMessagesIfNeeded()
        attachChatListenersIfNeeded()
        if (conversationId.isNotBlank()) {
            scope.launch { EncryptionService.preloadConversationKeys(listOf(conversationId)) }
        }
    }

    fun deactivateChatSession() {
        isChatVisible = false
        setTyping(false)
        if (LocalFirstMessagingSettings.isEnabled) {
            pauseChatListenersImmediately()
            return
        }
        listenerPauseJob?.cancel()
        listenerPauseJob = scope.launch {
            delay(listenerPauseTTLMillis)
            if (!isChatVisible && chatSessionMode != ChatSessionMode.ACTIVE) {
                pauseChatListenersImmediately()
            }
        }
    }

    fun stopListening() {
        pauseChatListenersImmediately()
        isChatVisible = false
    }

    fun setTyping(typing: Boolean) {
        // ≡ handleTypingIndicator — auto-stop a los 3s como Timer iOS.
        typingStopJob?.cancel()
        typingStopJob = null
        _isTyping.value = typing
        if (conversationId.isBlank()) return
        if (!_typingIndicatorEnabled.value) {
            chatService.stopTyping(conversationId, currentUserId)
            return
        }
        if (typing) {
            chatService.startTyping(conversationId, currentUserId)
            typingStopJob = scope.launch {
                delay(3_000)
                if (_isTyping.value) setTyping(false)
            }
        } else {
            chatService.stopTyping(conversationId, currentUserId)
        }
    }

    fun sendBuzz(completion: (Result<Unit>) -> Unit = {}) {
        if (!canSendBuzz) {
            completion(Result.failure(IllegalStateException("chat.buzz.blocked")))
            return
        }
        if (conversationId.isBlank()) {
            completion(Result.failure(IllegalStateException("conversation id invalid")))
            return
        }
        scope.launch { completion(chatService.sendBuzz(conversationId, currentUserId)) }
    }

    /** ≡ `pendingReplayBuzzEvent(within:)` — último buzz entrante no reproducido (~5 min). */
    fun pendingReplayBuzzEvent(
        withinMillis: Long = ChatBuzzProcessedStore.replayWindowMillis,
    ): ChatBuzzEvent? {
        if (conversationId.isBlank()) return null
        val cutoff = Date(System.currentTimeMillis() - withinMillis)
        return _buzzEvents.value
            .filter { event ->
                event.senderId != currentUserId &&
                    !event.createdAt.before(cutoff) &&
                    !ChatBuzzProcessedStore.isProcessed(event.id, conversationId)
            }
            .maxByOrNull { it.createdAt }
    }

    fun setTypingIndicatorEnabled(enabled: Boolean) {
        _typingIndicatorEnabled.value = enabled
        if (conversationId.isNotBlank()) applyTypingPreference(conversationId)
        else if (!enabled) {
            _typingUsers.value = emptySet()
            setTyping(false)
        }
    }

    /** ≡ `refreshForwardingPreference`. */
    fun refreshForwardingPreference() {
        if (conversationId.isBlank()) return
        val prefs = chatSettingsPrefs() ?: return
        val androidKey = "forwarding_$conversationId"
        val iosKey = "chat_forwarding_enabled_$conversationId"
        val stored = when {
            prefs.contains(androidKey) -> prefs.getBoolean(androidKey, true)
            prefs.contains(iosKey) -> prefs.getBoolean(iosKey, true)
            else -> return
        }
        _forwardingPreferences.value = _forwardingPreferences.value + (currentUserId to stored)
    }

    /** ≡ carga local de buzzPreferences del usuario actual. */
    fun refreshBuzzPreference() {
        if (conversationId.isBlank()) return
        val prefs = chatSettingsPrefs() ?: return
        val androidKey = "buzz_$conversationId"
        val iosKey = "chat_buzz_enabled_$conversationId"
        val stored = when {
            prefs.contains(androidKey) -> prefs.getBoolean(androidKey, true)
            prefs.contains(iosKey) -> prefs.getBoolean(iosKey, true)
            else -> return
        }
        _buzzPreferences.value = _buzzPreferences.value + (currentUserId to stored)
    }

    private fun typingIndicatorPreferenceKey(conversationId: String) =
        "chat_typing_indicator_enabled_$conversationId"

    private fun resolvedTypingIndicatorPreference(conversationId: String): Boolean {
        val prefs = chatSettingsPrefs() ?: return true
        val androidKey = "typing_$conversationId"
        val iosKey = typingIndicatorPreferenceKey(conversationId)
        return when {
            prefs.contains(androidKey) -> prefs.getBoolean(androidKey, true)
            prefs.contains(iosKey) -> prefs.getBoolean(iosKey, true)
            prefs.contains("chat_typing_indicator_enabled") ->
                prefs.getBoolean("chat_typing_indicator_enabled", true)
            else -> true
        }
    }

    private fun applyTypingPreference(conversationId: String) {
        if (_typingIndicatorEnabled.value) {
            if (sessionListenersAttached) {
                chatService.listenToTypingIndicators(conversationId)
                setupTypingUsersSubscription(conversationId)
            }
        } else {
            _typingUsers.value = emptySet()
            chatService.stopTyping(conversationId, currentUserId)
            chatService.removeTypingListener(conversationId)
            typingUsersJob?.cancel()
            typingUsersJob = null
            setTyping(false)
        }
    }

    fun refreshTypingIndicatorPreference() {
        if (conversationId.isBlank()) return
        _typingIndicatorEnabled.value = resolvedTypingIndicatorPreference(conversationId)
        applyTypingPreference(conversationId)
    }

    private fun setupTypingUsersSubscription(conversationId: String) {
        typingUsersJob?.cancel()
        typingUsersJob = scope.launch {
            chatService.typingUsers.collect { users ->
                _typingUsers.value = if (_typingIndicatorEnabled.value) {
                    users[conversationId].orEmpty() - currentUserId
                } else {
                    emptySet()
                }
            }
        }
    }

    private fun chatSettingsPrefs() =
        MomentsApplication.instance?.getSharedPreferences("conversation_settings", Context.MODE_PRIVATE)

    fun loadInitialMessages(limit: Int = initialWindowSize()) = scope.launch {
        if (conversationId.isBlank()) return@launch
        _isLoading.value = true
        chatService.fetchRecentMessages(
            conversationId,
            limit,
            cutoffDate = effectiveDeletedAtCutoff(),
        ).onSuccess { incoming ->
            realTimeMessages.clear(); realTimeMessages += mergeMessages(incoming)
            historicalMessages.removeAll { historical -> historical.id in realTimeMessages.map { it.id }.toSet() }
            rebuildMessagesList()
        }.onFailure { _historyLoadNotice.value = HistoryLoadNotice.ERROR }
        _isLoading.value = false
    }

    fun commitMessagesPresentation(nextMessages: List<EnhancedMessage>) {
        // No reordenar aquí; el orden ya viene de messageTimelinePrecedes.
        _chatTimelineMutation.value = forcedNextTimelineMutation
            ?: deriveTimelineMutation(_messages.value, nextMessages)
        forcedNextTimelineMutation = null
        _messages.value = nextMessages
        rebuildMessageIndex(nextMessages)
        pruneUploadProgress(nextMessages)
        pruneLocalMessageStates(nextMessages)
        (this as? MomentsChatViewModel)?.syncMessagePresentation()
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

    fun mergeMessages(remoteMessages: List<EnhancedMessage>): List<EnhancedMessage> =
        preserveTemporaryMessages(remoteMessages)
            .filterNot { it.id in hiddenForMeMessageIds }
            .sortedWith(messageTimelineComparator)

    /** ≡ `mergeMessagesFromLocalCache()` — catch-up al volver al chat. */
    fun mergeMessagesFromLocalCache() {
        if (conversationId.isBlank()) return
        scope.launch {
            val recent = LocalPersistenceService.loadRecentMessagesInBackground(
                conversationId = conversationId,
                limit = 50,
                cutoffDate = effectiveDeletedAtCutoff(),
            )
            mergeRecentMessagesFromLocalCache(recent)
        }
    }

    private fun mergeRecentMessagesFromLocalCache(recent: List<EnhancedMessage>) {
        if (recent.isEmpty()) return
        val knownIds = (realTimeMessages + historicalMessages).map { it.id }.toSet()
        val oldestVisible = historicalMessages.firstOrNull()?.timestamp
            ?: _messages.value.firstOrNull()?.timestamp
            ?: Date(0)
        val incoming = recent
            .filter { it.id !in knownIds && !it.timestamp.before(oldestVisible) }
            .sortedWith(messageTimelineComparator)
        if (incoming.isEmpty()) return
        historicalMessages += incoming
        historicalMessages.sortWith(messageTimelineComparator)
        rebuildMessagesList()
        prefetchUnresolvedMediaIfNeeded()
        (this as? MomentsChatViewModel)?.syncMessagePresentation()
    }

    private val messageTimelineComparator =
        Comparator<EnhancedMessage> { lhs, rhs ->
            MessageSyncCursor(lhs.timestamp, lhs.id).compareTo(MessageSyncCursor(rhs.timestamp, rhs.id))
        }

    private fun messagesRespectingDeletionCutoff(messages: List<EnhancedMessage>): List<EnhancedMessage> {
        val cutoff = effectiveDeletedAtCutoff() ?: return messages
        return messages.filter { it.timestamp.after(cutoff) }
    }

    private fun isVanishMessageHiddenFromCurrentUser(message: EnhancedMessage): Boolean {
        if (!message.isVanishModeMessage) return false
        if (message.id in optimisticallyHiddenVanishIds) return true
        if (VanishMessageTimer.isExpired(message.vanishExpiresAt)) return true
        return message.isVanished(currentUserId)
    }

    private fun rebuildMessagesList() {
        // 1. Real-time primero (prioridad de estado) + históricos.
        val allMessages = messagesRespectingDeletionCutoff(realTimeMessages + historicalMessages)
            .filterNot { it.id in hiddenForMeMessageIds }
            .filterNot(::isVanishMessageHiddenFromCurrentUser)

        // 2. Deduplicar por ID (se queda con el primero = real-time).
        val seenIds = mutableSetOf<String>()
        val unique = allMessages.filter { seenIds.add(it.id) }

        // 3. Ordenar con cursor compartido.
        val sorted = unique.sortedWith(messageTimelineComparator)

        // 4. Temporales + estados locales + URLs disco + read local.
        var finalMessages = preserveTemporaryMessages(sorted)
            .map(::withPreservedLocalReadState)
            .toMutableList()
        persistDiskCachedMediaURLs(finalMessages)
        commitMessagesPresentation(finalMessages)
        syncLiveReactionOverlays(finalMessages)

        for (id in outgoingTempMessages.keys.toList()) {
            val msg = finalMessages.firstOrNull { it.id == id }
            if (msg != null && msg.status != MessageStatus.SENDING) {
                outgoingTempMessages.remove(id)
            }
        }
    }

    /** ≡ `preserveTemporaryMessages`. */
    private fun preserveTemporaryMessages(newMessages: List<EnhancedMessage>): List<EnhancedMessage> {
        val merged = newMessages.toMutableList()

        _messages.value.filter { it.status == MessageStatus.SENDING }.forEach { temp ->
            if (merged.none { it.id == temp.id }) merged += temp
        }

        for ((_, temp) in outgoingTempMessages) {
            val index = merged.indexOfFirst { it.id == temp.id }
            if (index >= 0) {
                val current = merged[index]
                if (isReachableLocalFile(temp.mediaUrl) && current.mediaUrl == null) {
                    merged[index] = current.copy(mediaUrl = temp.mediaUrl)
                }
            } else {
                merged += temp
            }
        }

        for ((messageId, localStatus) in localMessageStates) {
            val index = merged.indexOfFirst { it.id == messageId }
            if (index < 0) continue
            val firestoreStatus = merged[index].status
            if (statusPriority(localStatus) > statusPriority(firestoreStatus) || localStatus == MessageStatus.FAILED) {
                merged[index] = merged[index].copy(status = localStatus)
            }
        }

        for (existing in _messages.value) {
            val index = merged.indexOfFirst { it.id == existing.id }
            if (index < 0) continue
            var current = merged[index]
            if (current.isDeleted || existing.isDeleted) continue
            if (current.replyTo == null && existing.replyTo != null) {
                current = current.copy(replyTo = existing.replyTo)
            }
            if (isReachableLocalFile(existing.mediaUrl) &&
                (current.mediaUrl == null || current.hasMissingLocalMedia)
            ) {
                current = current.copy(mediaUrl = existing.mediaUrl)
            }
            if (isReachableLocalFile(existing.thumbnailUrl) &&
                (current.thumbnailUrl == null || current.hasMissingLocalThumbnail)
            ) {
                current = current.copy(thumbnailUrl = existing.thumbnailUrl)
            }
            if (existing.reactions != null) {
                current = current.copy(
                    reactions = chatService.mergeLegacyAndLiveReactions(existing.reactions, current.reactions),
                )
            }
            // Conservar estado live local si el snapshot/cache llega incompleto.
            current = preserveLiveLocationFields(from = existing, into = current)
            merged[index] = current
        }

        return merged.sortedWith(messageTimelineComparator)
    }

    /** No pisar flags live / stop optimista con un mensaje cacheado sin esos campos. */
    private fun preserveLiveLocationFields(from: EnhancedMessage, into: EnhancedMessage): EnhancedMessage {
        var current = into
        if (from.isLiveLocation == true && current.isLiveLocation != true) {
            current = current.copy(
                isLiveLocation = from.isLiveLocation,
                liveLocationExpiresAt = current.liveLocationExpiresAt ?: from.liveLocationExpiresAt,
                liveLocationDuration = current.liveLocationDuration ?: from.liveLocationDuration,
                liveLocationSessionId = current.liveLocationSessionId ?: from.liveLocationSessionId,
            )
        }
        if (from.liveLocationStoppedAt != null && current.liveLocationStoppedAt == null) {
            current = current.copy(liveLocationStoppedAt = from.liveLocationStoppedAt)
        }
        if (from.liveLocationExpiresAt != null && current.liveLocationExpiresAt == null) {
            current = current.copy(liveLocationExpiresAt = from.liveLocationExpiresAt)
        }
        if (from.locationUpdatedAt != null && current.locationUpdatedAt == null) {
            current = current.copy(locationUpdatedAt = from.locationUpdatedAt)
        }
        return current
    }

    private fun withPreservedLocalReadState(incoming: EnhancedMessage): EnhancedMessage {
        if (incoming.isRead || incoming.senderId == currentUserId) return incoming
        if (incoming.id in locallyReadMessageIds || currentUserId in incoming.readBy.orEmpty()) {
            return incoming.copy(isRead = true)
        }
        val lastRead = conversation.lastReadAt?.get(currentUserId)
        if (lastRead != null && !incoming.timestamp.after(lastRead)) {
            return incoming.copy(isRead = true)
        }
        return incoming
    }

    private fun isReachableLocalFile(url: String?): Boolean {
        if (url.isNullOrBlank()) return false
        return runCatching {
            val uri = Uri.parse(url)
            uri.scheme == "file" && uri.path != null && File(uri.path!!).isFile
        }.getOrDefault(false)
    }

    private fun persistDiskCachedMediaURLs(messages: MutableList<EnhancedMessage>) {
        val warmed = warmAndApplyDiskUrls(messages)
        var changed = false
        for (i in warmed.indices) {
            if (warmed[i].mediaUrl != messages[i].mediaUrl || warmed[i].thumbnailUrl != messages[i].thumbnailUrl) {
                messages[i] = warmed[i]
                changed = true
            }
        }
        if (changed && conversationId.isNotBlank()) {
            scope.launch {
                LocalPersistenceService.saveMessagesInBackground(warmed, conversationId, sync = false)
            }
        }
    }

    private fun syncLiveReactionOverlays(messages: List<EnhancedMessage>) {
        val overlays = _liveReactionOverlays.value.toMutableMap()
        for (message in messages) {
            val reactions = message.reactions ?: continue
            if (reactions.isEmpty()) continue
            overlays[message.id] = chatService.mergeLegacyAndLiveReactions(
                overlays[message.id],
                reactions,
            ) ?: reactions
        }
        _liveReactionOverlays.value = overlays
    }

    private fun applyFirestoreListenerMessages(incoming: List<EnhancedMessage>) {
        // Mensajes que salen del limitToLast se promueven a histórico (no se pierden).
        val newSet = incoming.map { it.id }.toSet()
        val droppedMessages = realTimeMessages.filter { it.id !in newSet }
        if (droppedMessages.isNotEmpty()) {
            // Vanish que desaparecen del snapshot = purga server-side → no promover.
            val droppedVanishIds = droppedMessages
                .filter { it.isVanishModeMessage && it.type != MessageType.CHAT_NOTICE }
                .map { it.id }
            if (droppedVanishIds.isNotEmpty()) {
                optimisticallyHiddenVanishIds += droppedVanishIds
                for (id in droppedVanishIds) outgoingTempMessages.remove(id)
                chatService.purgeVanishMessagesLocally(conversationId, droppedVanishIds)
            }
            val droppedVanishIdSet = droppedVanishIds.toSet()
            val promotable = messagesRespectingDeletionCutoff(droppedMessages)
                .filter { it.id !in droppedVanishIdSet }
            val existingIds = (historicalMessages + realTimeMessages).map { it.id }.toSet()
            historicalMessages += promotable.filter { it.id !in existingIds }
        }

        val existing = (historicalMessages + realTimeMessages + _messages.value).associateBy { it.id }
        realTimeMessages.clear()
        realTimeMessages += incoming.map { message ->
            val cached = existing[message.id]
            var preserved = if (message.mediaUrl.isNullOrBlank() && !cached?.mediaUrl.isNullOrBlank()) {
                message.copy(
                    mediaUrl = cached?.mediaUrl,
                    thumbnailUrl = message.thumbnailUrl ?: cached?.thumbnailUrl,
                )
            } else message
            preserved = withPreservedLocalReadState(preserved)
            if (cached != null) {
                val vanished = ((preserved.vanishedFor) + cached.vanishedFor).distinct()
                if (vanished != preserved.vanishedFor) preserved = preserved.copy(vanishedFor = vanished)
                // No pisar un stop / live optimista: el serverTimestamp puede llegar null un frame
                // y el listener borraría liveLocationStoppedAt → UI “sigue en vivo”.
                preserved = preserveLiveLocationFields(from = cached, into = preserved)
            }
            if (preserved.id in optimisticallyHiddenVanishIds && currentUserId !in preserved.vanishedFor) {
                preserved = preserved.copy(vanishedFor = preserved.vanishedFor + currentUserId)
            }
            preserved
        }
        val realtimeIds = realTimeMessages.map { it.id }.toSet()
        historicalMessages.removeAll { it.id in realtimeIds }
        rebuildMessagesList()
        prefetchUnresolvedMediaIfNeeded()
        scope.launch { LocalPersistenceService.reconcileMessagesInBackground(realTimeMessages, conversationId) }
    }

    fun attachChatListenersIfNeeded() {
        if (conversationId.isBlank() || sessionListenersAttached) return
        sessionListenersAttached = true
        chatService.listenToMessages(
            conversationId,
            cutoffDate = effectiveDeletedAtCutoff(),
            replaceExisting = false,
        ) { result ->
            result.onSuccess(::applyFirestoreListenerMessages).onFailure { _error.value = it.message }
        }
        chatService.listenToConversationPreferences(conversationId) {
                forwarding, buzz, vanishActive, timer, enabledNoticeId, disabledNoticeId ->
            _forwardingPreferences.value = forwarding
            _buzzPreferences.value = buzz
            val wasActive = _vanishModeActive.value
            _vanishModeActive.value = vanishActive
            _vanishMessageTimer.value = timer
            conversation.vanishMessageTimer = timer.raw
            conversation.vanishModeActive = vanishActive

            val oldEnabledId = conversation.vanishSettingsNoticeMessageId
            if (!oldEnabledId.isNullOrBlank() && oldEnabledId != enabledNoticeId) {
                removeMessageFromLocalStores(oldEnabledId)
                LocalPersistenceService.removeCachedMessage(conversationId, oldEnabledId)
            }
            val oldDisabledId = conversation.vanishDisabledNoticeMessageId
            if (!oldDisabledId.isNullOrBlank() && oldDisabledId != disabledNoticeId) {
                removeMessageFromLocalStores(oldDisabledId)
                LocalPersistenceService.removeCachedMessage(conversationId, oldDisabledId)
            }
            conversation.vanishSettingsNoticeMessageId = enabledNoticeId
            conversation.vanishDisabledNoticeMessageId = disabledNoticeId

            if (wasActive && !vanishActive) {
                purgeVanishMessagesLocally()
                ChatDraftEvents.emit(ChatDraftEvent.VanishModeChanged(conversationId, false))
            } else if (!wasActive && vanishActive) {
                ChatDraftEvents.emit(ChatDraftEvent.VanishModeChanged(conversationId, true))
            }
        }
        chatService.listenToMessageReactions(conversationId, replaceExisting = false) { result ->
            result.onSuccess { update -> applyReactionUpdate(update, conversationId) }
                .onFailure { _error.value = it.message }
        }
        chatService.listenToBuzzEvents(
            conversationId = conversationId,
            cutoffDate = effectiveDeletedAtCutoff(),
            replaceExisting = false,
        ) { event, isInitialSnapshot ->
            val isIncoming = event.senderId != currentUserId
            if (isIncoming && _buzzPreferences.value[currentUserId] == false) {
                seenBuzzEventIds.add(event.id)
                return@listenToBuzzEvents
            }
            if (!seenBuzzEventIds.add(event.id)) return@listenToBuzzEvents
            _buzzEvents.value = (_buzzEvents.value + event).sortedBy { it.createdAt }
            rebuildMessagesList()
            if (!isInitialSnapshot) _latestBuzzEvent.value = event
        }

        applyTypingPreference(conversationId)
    }

    /**
     * Port de `mergeConversationReadMetadata(from:)`.
     *
     * La sesión se cachea en [ChatSessionEngine]: al reutilizarla hay que traer el `lastReadAt`
     * fresco de la lista, o el saneado de leídos trabajaría con datos viejos.
     */
    fun mergeConversationReadMetadata(fresh: Conversation) {
        if (fresh.id.isNullOrEmpty() || fresh.id != conversation.id) return
        fresh.lastReadAt?.let { if (it != conversation.lastReadAt) conversation.lastReadAt = it }
        if (fresh.lastMessageSenderId != conversation.lastMessageSenderId) {
            conversation.lastMessageSenderId = fresh.lastMessageSenderId
        }
        if (fresh.lastMessageSeenAt != conversation.lastMessageSeenAt) {
            conversation.lastMessageSeenAt = fresh.lastMessageSeenAt
        }
        if (fresh.lastMessageReaction != conversation.lastMessageReaction) {
            conversation.lastMessageReaction = fresh.lastMessageReaction
        }
        // lastDeletedAt también afecta al cutoff visible.
        if (fresh.lastDeletedAt != conversation.lastDeletedAt) {
            conversation.lastDeletedAt = fresh.lastDeletedAt
        }
    }

    /** Port de `effectiveDeletedAtCutoff()`: los eventos previos al borrado del usuario no cuentan. */
    private fun effectiveDeletedAtCutoff(): Date? =
        conversation.deletedAtCutoff(currentUserId) ?: chatService.deletedAtCutoff(conversationId)

    /** Consumido por la UI tras reproducir el shake, para no repetirlo. */
    fun clearLatestBuzzEvent() {
        _latestBuzzEvent.value = null
    }

    fun pauseChatListenersImmediately() {
        listenerPauseJob?.cancel()
        listenerPauseJob = null
        if (!sessionListenersAttached) {
            chatSessionMode = ChatSessionMode.IDLE
            return
        }
        sessionListenersAttached = false
        chatSessionMode = ChatSessionMode.IDLE
        hasReceivedInitialReactionSnapshot = false
        chatService.removeMessagesListener(conversationId)
        chatService.removeTypingListener(conversationId)
        chatService.removeConversationPreferencesListener(conversationId)
        chatService.removeMessageReactionsListener(conversationId)
        chatService.removeBuzzListener(conversationId)
        if (conversationId.isNotBlank()) chatService.stopTyping(conversationId, currentUserId)
        _liveReactionOverlays.value = emptyMap()
        typingUsersJob?.cancel(); typingUsersJob = null
        _typingUsers.value = emptySet()
    }

    /** ≡ `applyReactionUpdate`. */
    private fun applyReactionUpdate(update: MessageReactionUpdate, conversationId: String) {
        val affectedIds = update.changedMessageIds + update.reactionsByMessage.keys
        if (affectedIds.isEmpty()) return

        val overlays = _liveReactionOverlays.value.toMutableMap()
        for (messageId in affectedIds) {
            val liveReactions = update.reactionsByMessage[messageId]
            mutateReactionState(messageId) { it.copy(reactions = liveReactions) }
            if (!liveReactions.isNullOrEmpty()) overlays[messageId] = liveReactions
            else overlays.remove(messageId)
        }

        if (hasReceivedInitialReactionSnapshot && isChatVisible) {
            for (messageId in update.changedMessageIds) {
                val message = _messages.value.firstOrNull { it.id == messageId } ?: continue
                if (message.senderId != currentUserId) continue
                val liveReactions = update.reactionsByMessage[messageId] ?: continue
                if (liveReactions.isEmpty() || !reactionIncludesOtherParticipant(liveReactions)) continue
                ChatNavigationIntentStore.emitMessageReactionHighlight(conversationId, messageId)
            }
        }
        hasReceivedInitialReactionSnapshot = true
        _liveReactionOverlays.value = overlays
        rebuildMessagesList()
        if (conversationId.isNotBlank()) {
            scope.launch {
                LocalPersistenceService.saveMessagesInBackground(_messages.value, conversationId, sync = false)
            }
        }
    }

    private fun mutateReactionState(messageId: String, transform: (EnhancedMessage) -> EnhancedMessage) {
        fun patch(list: MutableList<EnhancedMessage>) {
            val i = list.indexOfFirst { it.id == messageId }
            if (i >= 0) list[i] = transform(list[i])
        }
        patch(realTimeMessages)
        patch(historicalMessages)
        outgoingTempMessages[messageId]?.let { outgoingTempMessages[messageId] = transform(it) }
    }

    private fun reactionIncludesOtherParticipant(reactions: Map<String, List<String>>): Boolean =
        reactions.values.any { userIds -> userIds.any { it != currentUserId } }

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

    private fun messageNeedsMediaHydration(message: EnhancedMessage): Boolean = message.isMediaPendingResolution

    fun hydrateMediaIfNeeded(message: EnhancedMessage) {
        if (message.isMediaAwaitingManualDownload) {
            hydrateThumbnailPreviewIfNeeded(message)
            return
        }
        if (!ChatMediaDownloadPolicy.shouldDownloadAutomatically()) return
        // Vídeos: solo miniatura. El .mp4 completo al abrir.
        if (message.type == MessageType.VIDEO) {
            hydrateVideoThumbnailIfNeeded(message)
            return
        }
        if (!messageNeedsMediaHydration(message)) {
            if (message.type == MessageType.IMAGE &&
                message.mediaUrl == null &&
                (message.mediaObjectPath == null || message.mediaEncryption == null)
            ) {
                refreshMediaMetadataIfNeeded(message)
            }
            return
        }
        if (message.id in hydratingMediaIds) return
        hydratingMediaIds += message.id
        setDownloadProgress(message.id, 0.03)
        prepareMediaForViewing(message, forceDownload = false) {
            hydratingMediaIds -= message.id
            clearDownloadProgress(message.id)
        }
    }

    fun refreshMediaMetadataIfNeeded(message: EnhancedMessage) {
        if (message.type != MessageType.IMAGE && message.type != MessageType.VIDEO) return
        if (conversationId.isBlank()) return
        val missingMain = message.mediaObjectPath == null || message.mediaEncryption == null
        val needsThumb = message.type == MessageType.VIDEO && message.needsVideoThumbnailForDisplay
        val missingThumbMeta = message.thumbnailObjectPath == null || message.thumbnailEncryption == null
        when {
            message.type == MessageType.IMAGE && !missingMain -> return
            !missingMain && !(needsThumb && missingThumbMeta) -> {
                if (needsThumb) hydrateVideoThumbnailIfNeeded(message)
                return
            }
        }
        if (!refreshingMetadataIds.add(message.id)) return
        scope.launch {
            val fresh = chatService.fetchMessage(conversationId, message.id).getOrNull()
            refreshingMetadataIds -= message.id
            if (fresh != null) applyRefreshedMediaMessage(fresh)
        }
    }

    /** ≡ `applyRefreshedMediaMessage`. */
    private fun applyRefreshedMediaMessage(fresh: EnhancedMessage) {
        val existing = _messages.value.firstOrNull { it.id == fresh.id }
        val merged = fresh.copy(
            mediaUrl = fresh.mediaUrl ?: existing?.mediaUrl,
            thumbnailUrl = fresh.thumbnailUrl ?: existing?.thumbnailUrl,
        )
        fun patch(list: MutableList<EnhancedMessage>) {
            val i = list.indexOfFirst { it.id == merged.id }
            if (i >= 0) list[i] = merged
        }
        patch(historicalMessages)
        patch(realTimeMessages)
        rebuildMessagesList()
        if (conversationId.isNotBlank()) {
            scope.launch {
                LocalPersistenceService.saveMessagesInBackground(listOf(merged), conversationId, sync = false)
            }
        }
        val updated = _messages.value.firstOrNull { it.id == merged.id } ?: merged
        if (updated.type == MessageType.VIDEO) hydrateVideoThumbnailIfNeeded(updated)
        else hydrateMediaIfNeeded(updated)
    }

    fun hydrateVideoThumbnailIfNeeded(message: EnhancedMessage) {
        if (message.type != MessageType.VIDEO || !message.needsVideoThumbnailForDisplay) return
        if (!ChatMediaDownloadPolicy.shouldDownloadAutomatically()) return

        // Caso 1: miniatura cifrada en Storage.
        if (message.thumbnailObjectPath != null && message.thumbnailEncryption != null) {
            val thumbnailKey = "thumb_${message.id}"
            if (!hydratingMediaIds.add(thumbnailKey)) return
            scope.launch {
                val resolvedThumb = chatService.resolveVideoThumbnail(message, forceDownload = false)
                hydratingMediaIds -= thumbnailKey
                if (resolvedThumb.isNullOrBlank()) return@launch
                val updated = (_messages.value.firstOrNull { it.id == message.id } ?: message)
                    .copy(thumbnailUrl = resolvedThumb)
                appendOrReplaceMessage(updated)
                if (conversationId.isNotBlank()) {
                    LocalPersistenceService.saveMessagesInBackground(listOf(updated), conversationId, sync = false)
                }
            }
            return
        }

        // Caso 2: vídeo ya disponible → generar poster sin bajar el .mp4 completo.
        if (!message.mediaUrl.isNullOrBlank()) {
            generateVideoPosterIfPossible(message)
            return
        }

        // Caso 3: solo vídeo cifrado → descargar y luego poster.
        if (message.mediaObjectPath != null && message.mediaEncryption != null) {
            if (!hydratingMediaIds.add(message.id)) return
            setDownloadProgress(message.id, 0.03)
            prepareMediaForViewing(message, forceDownload = false) { updated ->
                hydratingMediaIds -= message.id
                clearDownloadProgress(message.id)
                generateVideoPosterIfPossible(updated)
            }
            return
        }

        refreshMediaMetadataIfNeeded(message)
    }

    /** ≡ `generateVideoPosterIfPossible`. */
    private fun generateVideoPosterIfPossible(message: EnhancedMessage) {
        if (!message.needsVideoThumbnailForDisplay) return
        val mediaUrl = message.mediaUrl ?: return
        val posterKey = "poster_${message.id}"
        if (!hydratingMediaIds.add(posterKey)) return
        scope.launch {
            val poster = ChatVideoPosterGenerator.poster(mediaUrl, message.id)
            hydratingMediaIds -= posterKey
            if (poster.isNullOrBlank()) return@launch
            val updated = (_messages.value.firstOrNull { it.id == message.id } ?: message)
                .copy(thumbnailUrl = poster)
            appendOrReplaceMessage(updated)
            if (conversationId.isNotBlank()) {
                LocalPersistenceService.saveMessagesInBackground(listOf(updated), conversationId, sync = false)
            }
        }
    }

    fun hydrateThumbnailPreviewIfNeeded(message: EnhancedMessage) {
        if (message.thumbnailObjectPath == null || message.thumbnailEncryption == null) return
        if (!message.thumbnailUrl.isNullOrBlank() && !message.hasMissingLocalThumbnail) return
        val previewKey = "thumb_preview_${message.id}"
        if (!hydratingMediaIds.add(previewKey)) return
        scope.launch {
            val thumbnail = chatService.resolveVideoThumbnail(message, forceDownload = false)
            hydratingMediaIds -= previewKey
            if (thumbnail == null) return@launch
            val updated = (_messages.value.firstOrNull { it.id == message.id } ?: message)
                .copy(thumbnailUrl = thumbnail)
            appendOrReplaceMessage(updated)
            if (conversationId.isNotBlank()) {
                LocalPersistenceService.saveMessagesInBackground(listOf(updated), conversationId, sync = false)
            }
        }
    }

    fun openMediaForViewing(message: EnhancedMessage, completion: (EnhancedMessage) -> Unit) {
        if (!message.needsDownloadForPlayback) {
            completion(message)
            return
        }
        if (!downloadingMediaIds.add(message.id)) return
        setDownloadProgress(message.id, 0.03)
        prepareMediaForViewing(message, forceDownload = true) { updated ->
            downloadingMediaIds -= message.id
            clearDownloadProgress(message.id)
            completion(updated)
        }
    }

    /** ≡ `prepareMediaForViewing(_:forceDownload:completion:)`. */
    fun prepareMediaForViewing(
        message: EnhancedMessage,
        forceDownload: Boolean = true,
        completion: (EnhancedMessage) -> Unit,
    ) {
        if (message.hasLocalMediaReadyForViewer && !message.hasMissingLocalMedia) {
            completion(message)
            return
        }
        if ((message.type == MessageType.GIF || message.type == MessageType.STICKER) &&
            message.hasMissingLocalMedia &&
            message.mediaObjectPath == null
        ) {
            val cleared = message.copy(mediaUrl = null)
            appendOrReplaceMessage(cleared)
            completion(_messages.value.firstOrNull { it.id == message.id } ?: cleared)
            return
        }
        if (message.mediaObjectPath == null || message.mediaEncryption == null) {
            completion(message)
            return
        }
        scope.launch {
            setDownloadProgress(message.id, 0.03)
            try {
                val resolved = ChatEncryptedMediaResolver.resolveForMessage(message, forceDownload = forceDownload)
                if (resolved?.mediaUrl == null) {
                    completion(message)
                    return@launch
                }
                val updated = message.copy(
                    mediaUrl = resolved.mediaUrl,
                    thumbnailUrl = resolved.thumbnailUrl ?: message.thumbnailUrl,
                )
                appendOrReplaceMessage(updated)
                if (conversationId.isNotBlank()) {
                    LocalPersistenceService.saveMessagesInBackground(
                        listOf(_messages.value.firstOrNull { it.id == message.id } ?: updated),
                        conversationId,
                        sync = false,
                    )
                }
                completion(_messages.value.firstOrNull { it.id == message.id } ?: updated)
            } finally {
                clearDownloadProgress(message.id)
            }
        }
    }

    fun prefetchUnresolvedMediaIfNeeded() {
        if (!ChatMediaDownloadPolicy.shouldDownloadAutomatically()) return
        for (message in _messages.value) {
            if (messageNeedsMediaHydration(message)) hydrateMediaIfNeeded(message)
        }
    }

    fun warmDiskMediaUrls() {
        val mutable = _messages.value.toMutableList()
        val changed = warmAndApplyDiskUrls(mutable)
        if (changed.isEmpty()) return
        applyWarmedMessagesFromDisk(changed)
    }

    /** ≡ `warmAndApplyDiskURLs(to:)` — muta lista y devuelve los cambiados. */
    fun warmAndApplyDiskUrls(items: MutableList<EnhancedMessage>): List<EnhancedMessage> {
        val changed = mutableListOf<EnhancedMessage>()
        for (i in items.indices) {
            if (items[i].isDeleted) continue
            val warmed = chatService.warmMessageURLsFromDiskCache(items[i])
            var didChange = false
            var next = items[i]
            val mediaUrl = warmed.mediaUrl
            if (mediaUrl != null && (next.mediaUrl != mediaUrl || next.hasMissingLocalMedia)) {
                next = next.copy(mediaUrl = mediaUrl)
                didChange = true
            }
            val thumbnailUrl = warmed.thumbnailUrl
            if (thumbnailUrl != null && (next.thumbnailUrl != thumbnailUrl || next.hasMissingLocalThumbnail)) {
                next = next.copy(thumbnailUrl = thumbnailUrl)
                didChange = true
            }
            if (didChange) {
                items[i] = next
                changed += next
            }
        }
        return changed
    }

    /** ≡ `scheduleAsyncDiskURLWarm`. */
    private fun scheduleAsyncDiskURLWarm() {
        if (conversationId.isBlank()) return
        LocalPersistenceService.scheduleWarmDiskMediaURLs(conversationId) { warmed ->
            scope.launch { applyWarmedMessagesFromDisk(warmed) }
        }
    }

    /** ≡ `applyWarmedMessagesFromDisk`. */
    private fun applyWarmedMessagesFromDisk(warmed: List<EnhancedMessage>) {
        if (warmed.isEmpty()) return
        val byId = warmed.associateBy { it.id }
        var didChange = false
        fun patch(list: MutableList<EnhancedMessage>) {
            for (i in list.indices) {
                val source = byId[list[i].id] ?: continue
                var next = list[i]
                var changed = false
                if (next.mediaUrl != source.mediaUrl) {
                    next = next.copy(mediaUrl = source.mediaUrl)
                    changed = true
                }
                if (next.thumbnailUrl != source.thumbnailUrl) {
                    next = next.copy(thumbnailUrl = source.thumbnailUrl)
                    changed = true
                }
                if (changed) {
                    list[i] = next
                    didChange = true
                }
            }
        }
        patch(historicalMessages)
        patch(realTimeMessages)
        if (!didChange) return
        rebuildMessagesList()
        prefetchUnresolvedMediaIfNeeded()
        (this as? MomentsChatViewModel)?.syncMessagePresentation()
    }

    /**
     * Carga página anterior: disco primero; si hay prepend visible, sale y el
     * siguiente scroll continúa. Si no, Firebase; fin solo cuando no hay más.
     */
    fun loadMoreMessages() = scope.launch {
        val oldest = _messages.value.firstOrNull() ?: return@launch
        if (_isLoadingMore.value || !_canLoadMore.value || conversationId.isBlank()) return@launch
        _isLoadingMore.value = true
        _isLoadingOlderHistory.value = true
        _historyLoadNotice.value = HistoryLoadNotice.HIDDEN

        val cutoff = effectiveDeletedAtCutoff()
        val pageSize = historyPageSize
        var remoteCursor = MessageSyncCursor(oldest.timestamp, oldest.id)
        var didPrependVisible = false

        val localPage = LocalPersistenceService.loadMessagesBeforeInBackground(
            conversationId = conversationId,
            cursor = remoteCursor,
            cutoffDate = cutoff,
            limit = pageSize,
        )
        if (localPage.isNotEmpty()) {
            if (prependHistoryPage(localPage)) {
                didPrependVisible = true
                // Tras prepend local visible, salir con canLoadMore=true.
                // No pedir Firebase en la misma pasada: un snapshot vacío/corto
                // marcaría canLoadMore=false y cortaría el historial.
                finishHistoryLoad(canLoadMore = true)
                scheduleHistoryScrollRestorationFallback()
                return@launch
            } else {
                localPage.firstOrNull()?.let { oldestExamined ->
                    remoteCursor = MessageSyncCursor(oldestExamined.timestamp, oldestExamined.id)
                }
            }
        }

        if (!NetworkMonitor.isConnected) {
            if (didPrependVisible) {
                finishHistoryLoad(canLoadMore = true)
                scheduleHistoryScrollRestorationFallback()
            } else {
                _historyLoadNotice.value = HistoryLoadNotice.OFFLINE
                finishHistoryLoad(canLoadMore = _canLoadMore.value)
                endHistoryScrollRestoration()
            }
            return@launch
        }

        while (true) {
            val page = chatService.fetchOlderMessages(
                conversationId,
                remoteCursor,
                cutoffDate = cutoff,
                limit = pageSize,
            ).getOrElse {
                _historyLoadNotice.value = HistoryLoadNotice.ERROR
                finishHistoryLoad(canLoadMore = true)
                if (didPrependVisible) {
                    scheduleHistoryScrollRestorationFallback()
                } else {
                    endHistoryScrollRestoration()
                }
                return@launch
            }
            val existingIds = (historicalMessages + realTimeMessages).map { it.id }.toSet()
            val novel = page.messages
                .filter { it.id !in existingIds }
                .sortedWith(messageTimelineComparator)
            if (novel.isNotEmpty()) {
                LocalPersistenceService.appendMessagesInBackground(novel, conversationId)
                if (prependHistoryPage(novel)) {
                    finishHistoryLoad(canLoadMore = page.hasMore)
                    scheduleHistoryScrollRestorationFallback()
                    return@launch
                }
            }
            val next = page.nextCursor
            if (!page.hasMore || next == null || next >= remoteCursor) {
                finishHistoryLoad(canLoadMore = false)
                endHistoryScrollRestoration()
                return@launch
            }
            remoteCursor = next
        }
    }

    /** Si `onPrependFinished` no llega (kind mal clasificado / race con listener), desbloquea load more. */
    private fun scheduleHistoryScrollRestorationFallback() {
        scope.launch {
            delay(900)
            if (_isLoadingOlderHistory.value) endHistoryScrollRestoration()
        }
    }

    /** ≡ `prependHistoryPage`. @return true si aparecieron mensajes nuevos visibles. */
    private fun prependHistoryPage(page: List<EnhancedMessage>): Boolean {
        if (page.isEmpty()) return false
        val existingIds = (historicalMessages + realTimeMessages).map { it.id }.toSet()
        val novel = page.filter { it.id !in existingIds }
        if (novel.isEmpty()) return false
        val previousVisibleIds = _messages.value.map { it.id }.toSet()
        historicalMessages.addAll(0, novel)
        historicalMessages.sortWith(messageTimelineComparator)
        forcedNextTimelineMutation = ChatTimelineMutation(
            ChatListUpdateKind.PREPEND_HISTORY,
            ChatTimelineUpdateReason.HISTORY,
            _messages.value.firstOrNull()?.id,
        )
        rebuildMessagesList()
        scope.launch { prefetchUnresolvedMediaIfNeeded() }
        return _messages.value.any { it.id !in previousVisibleIds }
    }

    private fun finishHistoryLoad(canLoadMore: Boolean) {
        _canLoadMore.value = canLoadMore
        _isLoadingMore.value = false
    }

    /** La vista llama esto cuando el scroll quedó re-anclado tras prepend. */
    fun endHistoryScrollRestoration() {
        _isLoadingOlderHistory.value = false
    }

    fun clearHistoryLoadNotice() { _historyLoadNotice.value = HistoryLoadNotice.HIDDEN }

    private fun initialWindowSize(): Int {
        val ageMillis = (Date().time - conversation.timestamp.time).coerceAtLeast(0)
        return if (ageMillis > staleChatThresholdDays * 24L * 60L * 60L * 1000L) staleChatWindowSize else recentChatWindowSize
    }

    fun loadCachedMessagesIfNeeded() = scope.launch {
        if (didLoadCache || conversationId.isBlank()) return@launch
        didLoadCache = true
        val cutoff = effectiveDeletedAtCutoff()
        val windowSize = initialWindowSize()
        val scanLimit = maxOf(windowSize, 50)
        var recent = LocalPersistenceService.loadRecentMessagesInBackground(
            conversationId,
            scanLimit,
            cutoffDate = cutoff,
        )
        if (conversation.readStatus[currentUserId] == true) {
            val hasUnreadIncoming = recent.any { it.senderId != currentUserId && !it.isRead }
            if (!hasUnreadIncoming) {
                LocalPersistenceService.markConversationReadLocally(conversationId, currentUserId)
                recent = recent.map { message ->
                    if (message.senderId != currentUserId && !message.isRead) message.copy(isRead = true) else message
                }
            }
        }
        // Mensajes outgoing stuck en sending/pending/failed → outgoingTempMessages
        val now = Date().time
        recent = recent.map { message ->
            if (message.senderId != currentUserId) return@map message
            when (message.status) {
                MessageStatus.SENDING, MessageStatus.PENDING, MessageStatus.FAILED -> {
                    var updated = message
                    if (message.status == MessageStatus.SENDING &&
                        now - message.timestamp.time > 60_000L
                    ) {
                        val queued = LocalPersistenceService.hasPendingAction(message.id)
                        updated = message.copy(
                            status = if (queued) MessageStatus.PENDING else MessageStatus.FAILED,
                        )
                        LocalPersistenceService.updateCachedMessageStatus(
                            conversationId,
                            message.id,
                            updated.status,
                        )
                    }
                    outgoingTempMessages[updated.id] = updated
                    updated
                }
                else -> message
            }
        }
        if (recent.isEmpty()) return@launch
        historicalMessages.clear()
        historicalMessages += recent.takeLast(windowSize).sortedWith(messageTimelineComparator)
        hydrateLocallyHiddenVanishMessages(recent)
        rebuildMessagesList()
        scheduleAsyncDiskURLWarm()
        prefetchUnresolvedMediaIfNeeded()
        (this@EnhancedChatViewModel as? MomentsChatViewModel)?.syncMessagePresentation()
    }

    /** ≡ `hydrateLocallyHiddenVanishMessages(from:)`. */
    private fun hydrateLocallyHiddenVanishMessages(loaded: List<EnhancedMessage>) {
        val hiddenIds = loaded
            .filter { it.isVanishModeMessage && it.isVanished(currentUserId) }
            .map { it.id }
        if (hiddenIds.isEmpty()) return
        optimisticallyHiddenVanishIds += hiddenIds
    }

    suspend fun navigateToMessage(messageId: String): Boolean {
        if (messageId.isBlank()) return false
        if (_messages.value.any { it.id == messageId }) return true
        if (conversationId.isBlank()) return false
        if (!requestedHighlightMessageIds.add(messageId)) {
            return _messages.value.any { it.id == messageId }
        }
        return try {
            val cutoff = effectiveDeletedAtCutoff()
            val radius = navigationWindowRadius

            val anchor = (historicalMessages + realTimeMessages).firstOrNull { it.id == messageId }
                ?: LocalPersistenceService.loadMessagesInBackground(conversationId)
                    .firstOrNull { it.id == messageId }
                ?: chatService.fetchMessage(conversationId, messageId).getOrNull()?.also {
                    LocalPersistenceService.appendMessagesInBackground(listOf(it), conversationId)
                }
                ?: return false

            val cursor = MessageSyncCursor(anchor.timestamp, anchor.id)
            val cachedBefore = LocalPersistenceService.loadMessagesBeforeInBackground(
                conversationId, cursor, cutoffDate = cutoff, limit = radius,
            )
            val cachedAfter = LocalPersistenceService.loadMessagesAfterInBackground(
                conversationId, cursor, cutoffDate = cutoff, limit = radius + 1,
            )
            var window = mergeNavigationWindow(cachedBefore, anchor, cachedAfter)
            val expected = radius * 2 + 1
            var reachedStart: Boolean? = null

            if ((window.size < expected || window.none { it.id == messageId }) && NetworkMonitor.isConnected) {
                val olderPage = chatService.fetchOlderMessages(
                    conversationId, cursor, cutoffDate = cutoff, limit = radius,
                ).getOrNull()
                val newer = chatService.fetchMessagesAfter(
                    conversationId, cursor, limit = radius, cutoffDate = cutoff,
                ).getOrNull().orEmpty()
                if (olderPage != null) {
                    reachedStart = !olderPage.hasMore
                    window = mergeNavigationWindow(olderPage.messages, anchor, newer)
                    if (window.isNotEmpty()) {
                        LocalPersistenceService.appendMessagesInBackground(window, conversationId)
                    }
                }
            }

            if (window.none { it.id == messageId }) return false
            applyMessageNavigationWindow(window, messageId, reachedStart)
            _messages.value.any { it.id == messageId }
        } finally {
            requestedHighlightMessageIds.remove(messageId)
        }
    }

    private fun mergeNavigationWindow(
        before: List<EnhancedMessage>,
        anchor: EnhancedMessage,
        after: List<EnhancedMessage>,
    ): List<EnhancedMessage> {
        val seen = mutableSetOf<String>()
        val merged = ArrayList<EnhancedMessage>(before.size + after.size + 1)
        for (message in before + anchor + after) {
            if (seen.add(message.id)) merged += message
        }
        return merged.sortedWith(messageTimelineComparator)
    }

    private fun applyMessageNavigationWindow(
        window: List<EnhancedMessage>,
        anchorMessageId: String,
        reachedStartOfHistory: Boolean?,
    ) {
        val sorted = window.sortedWith(messageTimelineComparator)
        val lastInWindow = sorted.lastOrNull() ?: return
        val windowIds = sorted.map { it.id }.toSet()
        val windowEnd = MessageSyncCursor(lastInWindow.timestamp, lastInWindow.id)

        historicalMessages.clear()
        historicalMessages += sorted
        realTimeMessages.retainAll { message ->
            message.id !in windowIds &&
                MessageSyncCursor(message.timestamp, message.id).isAfter(windowEnd)
        }

        forcedNextTimelineMutation = ChatTimelineMutation(
            ChatListUpdateKind.JUMP,
            ChatTimelineUpdateReason.HIGHLIGHT,
            anchorMessageId,
        )
        _canLoadMore.value = !(reachedStartOfHistory ?: false)
        rebuildMessagesList()
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
                .onSuccess {
                    applyOutgoingMessageUpdate(messageId, it.status)
                    trackSuccessfulDirectMessage()
                }
                .onFailure { throwable -> _error.value = throwable.message; applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
        }
    }

    fun sendMediaMessage(
        data: ByteArray,
        type: MessageType,
        fileName: String? = null,
        mediaBatchId: String? = null,
        replyTo: String? = null,
        mediaWidth: Int? = null,
        mediaHeight: Int? = null,
    ) {
        if (data.isEmpty()) return
        if (conversationId.isBlank()) {
            ensureConversationExists { if (!it.isNullOrBlank()) sendMediaMessage(data, type, fileName, mediaBatchId, replyTo, mediaWidth, mediaHeight) }
            return
        }
        val messageId = UUID.randomUUID().toString()
        val ext = fileName?.substringAfterLast('.', missingDelimiterValue = chatService.getFileExtension(type))
            ?: chatService.getFileExtension(type)
        val localPreview = localOutgoingPreviewURL(data, conversationId, messageId, ext)
        appendOutgoingMessage(
            EnhancedMessage(
                id = messageId,
                conversationId = conversationId,
                senderId = currentUserId,
                type = type,
                mediaUrl = localPreview,
                fileName = fileName,
                fileSize = data.size.toLong(),
                mediaWidth = mediaWidth,
                mediaHeight = mediaHeight,
                timestamp = Date(),
                status = MessageStatus.SENDING,
                mediaBatchId = mediaBatchId,
                replyTo = replyTo,
                isVanishModeMessage = _vanishModeActive.value,
            ),
        )
        scope.launch {
            chatService.sendMediaMessage(
                conversationId, currentUserId, type, data, fileName, messageId, mediaBatchId,
                _vanishModeActive.value, null, replyTo,
            )
                .onSuccess {
                    finalizeOutgoingMediaMessage(messageId, it, fallbackMediaUrl = localPreview)
                    trackSuccessfulDirectMessage()
                }
                .onFailure { throwable ->
                    _error.value = throwable.message
                    applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED)
                }
        }
    }

    /** ≡ `localOutgoingPreviewURL` — escribe bytes en cache descifrado para preview inmediato. */
    private fun localOutgoingPreviewURL(
        data: ByteArray,
        conversationId: String,
        messageId: String,
        fileExtension: String,
    ): String? = runCatching {
        val file = ChatCacheStore.writeDecryptedMedia(
            data = data,
            conversationId = conversationId,
            messageId = messageId,
            purpose = ChatMediaPurpose.PRIMARY,
            fileExtension = fileExtension,
        )
        Uri.fromFile(file).toString()
    }.getOrNull()

    fun sendImageMessage(data: ByteArray, mediaBatchId: String? = null, mediaWidth: Int? = null, mediaHeight: Int? = null) =
        sendMediaMessage(data, MessageType.IMAGE, "image_${UUID.randomUUID()}.jpg", mediaBatchId, mediaWidth = mediaWidth, mediaHeight = mediaHeight)

    open fun sendVideoMessage(data: ByteArray, mediaBatchId: String? = null, replyTo: String? = null) =
        sendMediaMessage(data, MessageType.VIDEO, "video_${UUID.randomUUID()}.mp4", mediaBatchId, replyTo)

    fun sendAudioMessage(data: ByteArray, duration: Double, waveform: List<Float>? = null) {
        if (data.isEmpty()) return
        if (conversationId.isBlank()) { ensureConversationExists { if (!it.isNullOrBlank()) sendAudioMessage(data, duration, waveform) }; return }
        val messageId = UUID.randomUUID().toString()
        val localPreview = localOutgoingPreviewURL(data, conversationId, messageId, "m4a")
        appendOutgoingMessage(EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = MessageType.AUDIO, mediaUrl = localPreview, duration = duration, audioWaveform = waveform,
            fileName = "audio_$messageId.m4a", fileSize = data.size.toLong(), timestamp = Date(),
            status = MessageStatus.SENDING, isVanishModeMessage = _vanishModeActive.value,
        ))
        scope.launch {
            chatService.sendAudioMessage(conversationId, currentUserId, data, duration, waveform, messageId, _vanishModeActive.value)
                .onSuccess {
                    finalizeOutgoingMediaMessage(messageId, it, fallbackMediaUrl = localPreview)
                    trackSuccessfulDirectMessage()
                }
                .onFailure { throwable -> _error.value = throwable.message; applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED) }
        }
    }

    fun sendLocationMessage(
        latitude: Double,
        longitude: Double,
        name: String? = null,
        address: String? = null,
    ) {
        if (conversationId.isBlank()) {
            ensureConversationExists { if (!it.isNullOrBlank()) sendLocationMessage(latitude, longitude, name, address) }
            return
        }
        val messageId = UUID.randomUUID().toString()
        val message = EnhancedMessage(
            id = messageId, conversationId = conversationId, senderId = currentUserId,
            type = MessageType.LOCATION, latitude = latitude, longitude = longitude,
            locationName = name, locationAddress = address, isLiveLocation = false,
            timestamp = Date(), status = MessageStatus.SENDING, isVanishModeMessage = _vanishModeActive.value,
        )
        appendOutgoingMessage(message)
        scope.launch {
            chatService.sendStaticLocationMessage(
                conversationId = conversationId,
                senderId = currentUserId,
                latitude = latitude,
                longitude = longitude,
                name = name,
                address = address,
                messageId = messageId,
                isVanishModeMessage = _vanishModeActive.value,
            ).onSuccess {
                applyOutgoingMessageUpdate(messageId, it.status)
                trackSuccessfulDirectMessage()
            }
                .onFailure { throwable ->
                    _error.value = throwable.message
                    applyOutgoingMessageUpdate(messageId, MessageStatus.FAILED)
                }
        }
    }

    /** ≡ `trackSuccessfulDirectMessage`. */
    private fun trackSuccessfulDirectMessage() {
        val targetUserId = conversation.otherParticipantId
        if (targetUserId.isBlank()) return
        AffinityTracker.trackInteraction(AffinityInteractionType.DIRECT_MESSAGE, targetUserId)
    }

    fun addReaction(message: EnhancedMessage, emoji: String) {
        if (conversationId.isBlank() || emoji.isBlank()) return
        EmojiUsageStore.increment(emoji, currentUserId)
        val updated = MessageReactionMutation.apply(message.reactions, emoji, currentUserId)
        setLiveReactions(updated, message.id)
        scope.launch {
            chatService.addReaction(conversationId, message.id, emoji, currentUserId)
                .onFailure { _error.value = it.message }
        }
    }

    /** ≡ `setLiveReactions(_:for:)`. */
    private fun setLiveReactions(reactions: Map<String, List<String>>?, messageId: String) {
        val overlays = _liveReactionOverlays.value.toMutableMap()
        if (!reactions.isNullOrEmpty()) overlays[messageId] = reactions
        else overlays.remove(messageId)
        _liveReactionOverlays.value = overlays
        mutateReactionState(messageId) { it.copy(reactions = reactions) }
        rebuildMessagesList()
        if (conversationId.isNotBlank()) {
            scope.launch {
                LocalPersistenceService.saveMessagesInBackground(_messages.value, conversationId, sync = false)
            }
        }
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
        appendOrReplaceMessage(message.copy(isDeleted = true, deletedAt = Date(), mediaUrl = null, thumbnailUrl = null, content = null))
        scope.launch {
            chatService.deleteMessageWithCleanup(conversationId, message.id)
                .onFailure { _error.value = it.message }
        }
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

    fun canRetryMessage(message: EnhancedMessage): Boolean {
        if (message.senderId != currentUserId ||
            message.status != MessageStatus.FAILED ||
            message.isDeleted ||
            conversationId.isBlank()
        ) {
            return false
        }
        return when (message.type) {
            MessageType.TEXT -> !message.content.isNullOrBlank()
            MessageType.LOCATION ->
                message.isLiveLocation != true && message.latitude != null && message.longitude != null
            MessageType.GIF, MessageType.STICKER ->
                giphyReferenceId(message) != null && message.mediaUrl != null
            MessageType.IMAGE, MessageType.VIDEO, MessageType.AUDIO ->
                retryMediaFile(message) != null
            else -> false
        }
    }

    fun retryFailedMessage(message: EnhancedMessage) {
        if (!canRetryMessage(message) || conversationId.isBlank()) {
            _error.value = "Message can no longer be resent"
            return
        }
        val messageId = message.id
        val isVanish = message.isVanishModeMessage
        updateMessageStatus(messageId, MessageStatus.SENDING)
        scope.launch {
            val result: Result<EnhancedMessage> = when (message.type) {
                MessageType.TEXT -> chatService.sendTextMessage(
                    conversationId, currentUserId, message.content.orEmpty(),
                    message.replyTo, messageId, isVanish, message.vanishExpiresAt,
                )
                MessageType.LOCATION -> chatService.sendStaticLocationMessage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    latitude = message.latitude ?: 0.0,
                    longitude = message.longitude ?: 0.0,
                    name = message.locationName,
                    address = message.locationAddress,
                    messageId = messageId,
                    isVanishModeMessage = isVanish,
                )
                MessageType.GIF, MessageType.STICKER -> chatService.sendGiphyReferenceMessage(
                    conversationId = conversationId,
                    senderId = currentUserId,
                    type = message.type,
                    giphyId = giphyReferenceId(message).orEmpty(),
                    mediaUrl = message.mediaUrl.orEmpty(),
                    width = message.mediaWidth ?: 0,
                    height = message.mediaHeight ?: 0,
                    messageId = messageId,
                    isVanishModeMessage = isVanish,
                    replyTo = message.replyTo,
                )
                MessageType.IMAGE, MessageType.VIDEO, MessageType.AUDIO -> {
                    val file = retryMediaFile(message)
                    val bytes = file?.readBytes()
                    if (bytes == null) {
                        _error.value = "Message can no longer be resent"
                        updateMessageStatus(messageId, MessageStatus.FAILED)
                        return@launch
                    }
                    val fallbackUrl = Uri.fromFile(file).toString()
                    if (message.type == MessageType.AUDIO) {
                        chatService.sendAudioMessage(
                            conversationId, currentUserId, bytes,
                            message.duration ?: 0.0, message.audioWaveform, messageId, isVanish,
                        ).onSuccess {
                            finalizeOutgoingMediaMessage(messageId, it, fallbackMediaUrl = fallbackUrl)
                            trackSuccessfulDirectMessage()
                        }
                            .onFailure { throwable ->
                                _error.value = throwable.message
                                updateMessageStatus(messageId, MessageStatus.FAILED)
                            }
                        return@launch
                    }
                    chatService.sendMediaMessage(
                        conversationId, currentUserId, message.type, bytes, message.fileName,
                        messageId, message.mediaBatchId, isVanish, message.vanishExpiresAt, message.replyTo,
                    ).onSuccess {
                        finalizeOutgoingMediaMessage(messageId, it, fallbackMediaUrl = fallbackUrl)
                        trackSuccessfulDirectMessage()
                    }
                        .onFailure { throwable ->
                            _error.value = throwable.message
                            updateMessageStatus(messageId, MessageStatus.FAILED)
                        }
                    return@launch
                }
                else -> {
                    updateMessageStatus(messageId, MessageStatus.FAILED)
                    return@launch
                }
            }
            result.onSuccess {
                applyOutgoingMessageUpdate(messageId, it.status)
                trackSuccessfulDirectMessage()
            }
                .onFailure { throwable ->
                    _error.value = throwable.message
                    updateMessageStatus(messageId, MessageStatus.FAILED)
                }
        }
    }

    private fun retryMediaFile(message: EnhancedMessage): File? {
        if (conversationId.isBlank()) return null
        val file = ChatCacheStore.decryptedMediaFile(
            conversationId = conversationId,
            messageId = message.id,
            purpose = ChatMediaPurpose.PRIMARY,
            fileExtension = chatService.getFileExtension(message.type),
        )
        return file.takeIf { it.isFile }
    }

    private fun giphyReferenceId(message: EnhancedMessage): String? {
        val fileName = message.fileName ?: return null
        if (!fileName.startsWith("giphy_")) return null
        return fileName.removePrefix("giphy_").takeIf { it.isNotEmpty() }
    }

    fun displayReactions(messageId: String): Map<String, List<String>>? =
        _liveReactionOverlays.value[messageId] ?: _messages.value.firstOrNull { it.id == messageId }?.reactions

    fun prefetchClusterGalleryMedia(clusterMessages: List<EnhancedMessage>) {
        var didUpdate = false
        for (clusterMessage in clusterMessages) {
            val current = _messages.value.firstOrNull { it.id == clusterMessage.id }
            if (current == null) {
                refreshMediaMetadataIfNeeded(clusterMessage)
                hydrateMediaIfNeeded(clusterMessage)
                continue
            }
            if (current.mediaObjectPath == null || current.mediaEncryption == null) {
                refreshMediaMetadataIfNeeded(current)
            }
            val warmed = chatService.warmMessageURLsFromDiskCache(current)
            var next = current
            val mediaUrl = warmed.mediaUrl
            if (mediaUrl != null && (next.mediaUrl != mediaUrl || next.hasMissingLocalMedia)) {
                next = next.copy(mediaUrl = mediaUrl)
                didUpdate = true
            }
            val thumbnailUrl = warmed.thumbnailUrl
            if (thumbnailUrl != null && (next.thumbnailUrl != thumbnailUrl || next.hasMissingLocalThumbnail)) {
                next = next.copy(thumbnailUrl = thumbnailUrl)
                didUpdate = true
            }
            if (next != current) appendOrReplaceMessage(next)
            if (next.type == MessageType.VIDEO) {
                if (ChatMediaDownloadPolicy.shouldDownloadAutomatically()) hydrateVideoThumbnailIfNeeded(next)
            } else if (ChatMediaDownloadPolicy.shouldDownloadAutomatically()) {
                hydrateMediaIfNeeded(next)
            }
        }
        if (didUpdate && conversationId.isNotBlank()) {
            scope.launch {
                val toSave = clusterMessages.mapNotNull { m -> _messages.value.firstOrNull { it.id == m.id } }
                LocalPersistenceService.saveMessagesInBackground(toSave, conversationId, sync = false)
            }
        }
    }

    /**
     * ≡ `markVisibleConversationAsRead(sealsVanish:)`.
     * Al salir del chat pasar `sealsVanish = false` para no expirar vanish no visto.
     */
    fun markVisibleConversationAsRead(sealsVanish: Boolean = true) {
        if (!isChatVisible) return
        val markedIds = applyOptimisticReadLocally(sealsVanish)
        markUnreadMessagesAsRead(markedIds)
    }

    private fun applyOptimisticReadLocally(sealsVanish: Boolean = true): List<String> {
        val markedIds = mutableListOf<String>()

        fun markList(list: MutableList<EnhancedMessage>) {
            for (i in list.indices) {
                val message = list[i]
                if (message.senderId == currentUserId || message.isRead) continue
                list[i] = message.copy(isRead = true)
                markedIds += message.id
            }
        }

        markList(realTimeMessages)
        markList(historicalMessages)

        if (conversationId.isNotBlank()) {
            if (markedIds.isNotEmpty()) {
                LocalPersistenceService.markMessagesAsRead(conversationId, markedIds)
            }
            LocalPersistenceService.markConversationReadLocally(conversationId, currentUserId)
            ChatDraftEvents.emit(ChatDraftEvent.MarkedReadLocally(conversationId))
        }

        locallyReadMessageIds += markedIds
        if (markedIds.isEmpty()) return markedIds

        if (sealsVanish) {
            sessionSeenIncomingMessageIds += markedIds
            stampVanishExpiryIfNeeded(markedIds.toSet())
        }
        rebuildMessagesList()
        return markedIds
    }

    private fun markUnreadMessagesAsRead(messageIds: List<String>) {
        if (conversationId.isBlank()) return
        scope.launch {
            if (messageIds.isEmpty()) {
                chatService.markConversationAsRead(conversationId, currentUserId)
            } else {
                val lastIncoming = _messages.value.lastOrNull { it.senderId != currentUserId }
                val marksLast = lastIncoming != null && lastIncoming.id in messageIds
                chatService.markMessagesAsRead(conversationId, messageIds, currentUserId, marksLast)
            }
        }
    }

    fun performSearch(query: String) {
        searchDebounceJob?.cancel()
        val trimmed = query.trim()
        if (trimmed.isEmpty() || conversationId.isBlank()) {
            clearSearch()
            return
        }
        val token = ++activeSearchToken
        searchDebounceJob = scope.launch {
            delay(300)
            if (token != activeSearchToken) return@launch
            val inMemoryMatches = _messages.value.filter { message ->
                listOfNotNull(message.content, message.fileName)
                    .joinToString(" ")
                    .contains(trimmed, ignoreCase = true)
            }.map { it.id }
            val localIds = LocalPersistenceService.searchMessageIds(conversationId, trimmed, 100)
            var merged = mergeSearchResultIds(localIds + inMemoryMatches)
            _searchResults.value = merged
            if (merged.size >= 100) {
                _isSearchingHistory.value = false
                return@launch
            }
            _isSearchingHistory.value = true
            val excluding = merged.toSet()
            val remote = if (NetworkMonitor.isConnected) {
                chatService.searchMessages(conversationId, trimmed, excluding, 100 - merged.size)
                    .onFailure { _error.value = it.message }
                    .getOrNull()
                    .orEmpty()
            } else {
                emptyList()
            }
            if (token != activeSearchToken) return@launch
            _isSearchingHistory.value = false
            if (remote.isNotEmpty()) {
                LocalPersistenceService.appendMessages(remote, conversationId)
                merged = mergeSearchResultIds(merged + remote.map { it.id })
                _searchResults.value = merged
            }
        }
    }

    fun clearSearch() {
        searchDebounceJob?.cancel()
        ++activeSearchToken
        ++searchGeneration
        _searchResults.value = emptyList()
        _isSearchingHistory.value = false
    }

    fun searchMessages(query: String) = performSearch(query)

    fun sendEphemeralMessage(content: String?, mediaUrl: String?, durationHours: Int = 24) {
        if (conversationId.isBlank()) return
        val messageId = UUID.randomUUID().toString()
        val expirationDate = Date(Date().time + durationHours.coerceAtLeast(1) * 60L * 60L * 1000L)
        val optimistic = EnhancedMessage(
            id = messageId,
            conversationId = conversationId,
            senderId = currentUserId,
            type = MessageType.EPHEMERAL,
            content = content,
            mediaUrl = mediaUrl,
            timestamp = Date(),
            expirationDate = expirationDate,
            status = MessageStatus.SENDING,
        )
        appendOutgoingMessage(optimistic)
        scope.launch {
            chatService.sendEphemeralMessage(
                conversationId = conversationId,
                senderId = currentUserId,
                content = content,
                mediaUrl = mediaUrl,
                expirationHours = durationHours,
                messageId = messageId,
            ).onSuccess { applyOutgoingMessageUpdate(messageId, it.status) }
                .onFailure { _error.value = it.message; updateMessageStatus(messageId, MessageStatus.FAILED) }
        }
    }

    fun markEphemeralAsViewed(message: EnhancedMessage) {
        if (conversationId.isBlank()) return
        appendOrReplaceMessage(message.copy(isViewed = true))
        scope.launch { chatService.markEphemeralAsViewed(conversationId, message.id).onFailure { _error.value = it.message } }
    }

    val outgoingVanishMessageFlag: Boolean? get() = _vanishModeActive.value.takeIf { it }
    val marksOutgoingAsVanish: Boolean get() = _vanishModeActive.value

    /**
     * ≡ sync local tras `ConversationSettingsView.updateVanishSettings` /
     * notificación `conversationVanishModeDidChange` (antes de que el snapshot
     * de prefs reafirme el mismo valor).
     */
    fun applyVanishSettingsFromSettings(active: Boolean, timer: VanishMessageTimer) {
        val wasActive = _vanishModeActive.value
        _vanishModeActive.value = active
        _vanishMessageTimer.value = timer
        conversation.vanishModeActive = active
        conversation.vanishMessageTimer = if (active) timer.raw else null
        if (wasActive && !active) {
            purgeVanishMessagesLocally()
        }
    }

    fun toggleVanishMode(completion: ((Throwable?) -> Unit)? = null) {
        if (conversationId.isBlank()) { completion?.invoke(IllegalStateException()); return }
        val target = !_vanishModeActive.value
        scope.launch {
            chatService.setVanishMode(
                conversationId,
                target,
                currentUserId,
                if (target) _vanishMessageTimer.value else null,
            )
                .onSuccess {
                    _vanishModeActive.value = target
                    conversation.vanishModeActive = target
                    if (!target) purgeVanishMessagesLocally()
                    ChatDraftEvents.emit(ChatDraftEvent.VanishModeChanged(conversationId, target))
                    if (target) publishVanishEnabledNotice(completion)
                    else publishVanishDisabledNotice(completion)
                }
                .onFailure { throwable -> _error.value = throwable.message; completion?.invoke(throwable) }
        }
    }

    fun setVanishMessageTimer(timer: VanishMessageTimer?, completion: ((Throwable?) -> Unit)? = null) {
        if (timer == null) {
            if (_vanishModeActive.value) toggleVanishMode(completion) else completion?.invoke(null)
            return
        }
        if (conversationId.isBlank()) { completion?.invoke(IllegalStateException()); return }
        scope.launch {
            chatService.setVanishMessageTimer(conversationId, timer)
                .onSuccess {
                    _vanishMessageTimer.value = timer
                    conversation.vanishMessageTimer = timer.raw
                    if (_vanishModeActive.value) {
                        updateVanishEnabledNotice(timer.enabledNoticeToken, completion)
                    } else {
                        completion?.invoke(null)
                    }
                }
                .onFailure { throwable -> _error.value = throwable.message; completion?.invoke(throwable) }
        }
    }

    fun handleChatDismissedForVanishMode() {
        if (conversationId.isBlank()) return
        // Elegibilidad ANTES del mark-read en bloque (≡ iOS).
        val eligibleIds = _messages.value
            .filter { message ->
                if (!message.shouldHideVanishOnChatDismiss(currentUserId, _vanishMessageTimer.value)) return@filter false
                if (message.senderId == currentUserId) return@filter true
                message.id in sessionSeenIncomingMessageIds ||
                    VanishMessageTimer.isExpired(message.vanishExpiresAt)
            }
            .map { it.id }

        applyOptimisticReadLocally(sealsVanish = false)

        if (eligibleIds.isEmpty()) return
        optimisticallyHiddenVanishIds += eligibleIds
        LocalPersistenceService.markVanishMessagesDismissed(conversationId, eligibleIds, currentUserId)
        scope.launch { chatService.markVanishMessagesVanishedForMe(conversationId, eligibleIds, currentUserId) }
        rebuildMessagesList()
    }

    fun refreshVanishExpiryPresentation() {
        if (_vanishMessageTimer.value != VanishMessageTimer.ONCE_SEEN) rebuildMessagesList()
    }

    /** ≡ `resolveVanishEnabledNoticeMessageId`. */
    private fun resolveVanishEnabledNoticeMessageId(): String? {
        val stored = conversation.vanishSettingsNoticeMessageId
        if (!stored.isNullOrBlank() &&
            _messages.value.any { it.id == stored && !it.isDeleted }
        ) {
            return stored
        }
        return _messages.value.lastOrNull { message ->
            message.type == MessageType.CHAT_NOTICE &&
                !message.isDeleted &&
                message.content?.startsWith("disappearing:enabled:") == true
        }?.id
    }

    /** ≡ `resolveVanishDisabledNoticeMessageId`. */
    private fun resolveVanishDisabledNoticeMessageId(): String? {
        val stored = conversation.vanishDisabledNoticeMessageId
        if (!stored.isNullOrBlank() &&
            _messages.value.any { it.id == stored && !it.isDeleted }
        ) {
            return stored
        }
        return _messages.value.lastOrNull { message ->
            message.type == MessageType.CHAT_NOTICE &&
                !message.isDeleted &&
                message.content == VanishMessageTimer.DISABLED_NOTICE_TOKEN
        }?.id
    }

    /** Activar vanish: borra el último notice "turned off" y publica uno nuevo "turned on". */
    private fun publishVanishEnabledNotice(completion: ((Throwable?) -> Unit)? = null) {
        if (conversationId.isBlank()) {
            completion?.invoke(IllegalStateException())
            return
        }
        scope.launch {
            removeVanishDisabledNoticeIfNeeded()
            val noticeKey = _vanishMessageTimer.value.enabledNoticeToken
            chatService.sendChatNotice(conversationId, currentUserId, noticeKey)
                .onSuccess { message ->
                    conversation.vanishSettingsNoticeMessageId = message.id
                    chatService.setVanishSettingsNoticeMessageId(conversationId, message.id)
                    completion?.invoke(null)
                }
                .onFailure { throwable ->
                    _error.value = throwable.message
                    completion?.invoke(throwable)
                }
        }
    }

    private suspend fun removeVanishEnabledNoticeIfNeeded() {
        val noticeId = resolveVanishEnabledNoticeMessageId() ?: return
        removeMessageFromLocalStores(noticeId)
        LocalPersistenceService.removeCachedMessage(conversationId, noticeId)
        conversation.vanishSettingsNoticeMessageId = null
        chatService.clearVanishSettingsNoticeMessageId(conversationId)
        chatService.deleteMessage(conversationId, noticeId)
        rebuildMessagesList()
    }

    /** Desactivar vanish: notice "turned off" (anti-spam si ya hay uno activo). */
    private fun publishVanishDisabledNotice(completion: ((Throwable?) -> Unit)? = null) {
        if (conversationId.isBlank()) {
            completion?.invoke(IllegalStateException())
            return
        }
        scope.launch {
            removeVanishEnabledNoticeIfNeeded()
            val noticeKey = VanishMessageTimer.DISABLED_NOTICE_TOKEN
            val existingId = resolveVanishDisabledNoticeMessageId()
            if (existingId != null) {
                updateLocalNoticeContent(existingId, noticeKey)
                chatService.updateChatNotice(conversationId, existingId, noticeKey)
                    .onSuccess { completion?.invoke(null) }
                    .onFailure { throwable ->
                        _error.value = throwable.message
                        completion?.invoke(throwable)
                    }
                return@launch
            }
            chatService.sendChatNotice(conversationId, currentUserId, noticeKey)
                .onSuccess { message ->
                    conversation.vanishDisabledNoticeMessageId = message.id
                    chatService.setVanishDisabledNoticeMessageId(conversationId, message.id)
                    completion?.invoke(null)
                }
                .onFailure { throwable ->
                    _error.value = throwable.message
                    completion?.invoke(throwable)
                }
        }
    }

    /** Cambiar timer: actualiza solo el notice enabled existente (no duplica). */
    private fun updateVanishEnabledNotice(noticeKey: String, completion: ((Throwable?) -> Unit)? = null) {
        if (conversationId.isBlank()) {
            completion?.invoke(IllegalStateException())
            return
        }
        scope.launch {
            val noticeId = resolveVanishEnabledNoticeMessageId()
            if (noticeId != null) {
                updateLocalNoticeContent(noticeId, noticeKey)
                chatService.updateChatNotice(conversationId, noticeId, noticeKey)
                    .onSuccess { completion?.invoke(null) }
                    .onFailure { throwable ->
                        _error.value = throwable.message
                        completion?.invoke(throwable)
                    }
                return@launch
            }
            chatService.sendChatNotice(conversationId, currentUserId, noticeKey)
                .onSuccess { message ->
                    conversation.vanishSettingsNoticeMessageId = message.id
                    chatService.setVanishSettingsNoticeMessageId(conversationId, message.id)
                    completion?.invoke(null)
                }
                .onFailure { throwable ->
                    _error.value = throwable.message
                    completion?.invoke(throwable)
                }
        }
    }

    private suspend fun removeVanishDisabledNoticeIfNeeded() {
        val noticeId = resolveVanishDisabledNoticeMessageId() ?: return
        removeMessageFromLocalStores(noticeId)
        LocalPersistenceService.removeCachedMessage(conversationId, noticeId)
        conversation.vanishDisabledNoticeMessageId = null
        chatService.clearVanishDisabledNoticeMessageId(conversationId)
        chatService.deleteMessage(conversationId, noticeId)
        rebuildMessagesList()
    }

    private fun updateLocalNoticeContent(messageId: String, noticeKey: String) {
        fun patch(list: MutableList<EnhancedMessage>) {
            val i = list.indexOfFirst { it.id == messageId }
            if (i >= 0) list[i] = list[i].replacingContent(noticeKey)
        }
        patch(realTimeMessages)
        patch(historicalMessages)
        rebuildMessagesList()
        if (conversationId.isNotBlank()) {
            LocalPersistenceService.updateMessageNoticeContent(conversationId, messageId, noticeKey)
        }
    }

    /** ≡ `removeMessageFromLocalStores`. */
    private fun removeMessageFromLocalStores(messageId: String) {
        realTimeMessages.removeAll { it.id == messageId }
        historicalMessages.removeAll { it.id == messageId }
        outgoingTempMessages.remove(messageId)
        localMessageStates.remove(messageId)
        clearUploadProgress(messageId)
        clearDownloadProgress(messageId)
        _liveReactionOverlays.value = _liveReactionOverlays.value - messageId
    }

    /** ≡ `stampVanishExpiryIfNeeded(messageIds:)` — ancla expiresAt cuando todos han visto. */
    private fun stampVanishExpiryIfNeeded(messageIds: Set<String>? = null) {
        if (!_vanishModeActive.value ||
            _vanishMessageTimer.value == VanishMessageTimer.ONCE_SEEN ||
            conversationId.isBlank()
        ) {
            return
        }
        val expiresAt = _vanishMessageTimer.value.expiresAt() ?: return
        var stampedAny = false

        fun tryStamp(list: MutableList<EnhancedMessage>) {
            for (i in list.indices) {
                val message = list[i]
                if (!message.isVanishModeMessage || message.type == MessageType.CHAT_NOTICE) continue
                if (message.vanishExpiresAt != null) continue
                if (!message.everyoneHasSeen(currentUserId)) continue
                if (messageIds != null && message.id !in messageIds) continue
                list[i] = message.copy(vanishExpiresAt = expiresAt)
                stampedAny = true
                scope.launch { chatService.stampVanishExpiry(conversationId, message.id, expiresAt) }
            }
        }

        tryStamp(realTimeMessages)
        tryStamp(historicalMessages)
        if (stampedAny) rebuildMessagesList()
    }

    private fun purgeVanishMessagesLocally() {
        val ids = _messages.value
            .filter { it.isVanishModeMessage && it.type != MessageType.CHAT_NOTICE }
            .map { it.id }
        if (ids.isEmpty()) return
        optimisticallyHiddenVanishIds += ids
        chatService.purgeVanishMessagesLocally(conversationId, ids)
        rebuildMessagesList()
    }

    fun reportVanishScreenshotIfNeeded() {
        if (!_vanishModeActive.value || conversationId.isBlank()) return
        scope.launch {
            chatService.reportVanishScreenshot(conversationId, currentUserId)
                .onFailure { _error.value = it.message }
        }
    }

    fun reportVanishScreenRecordingIfNeeded() {
        if (!_vanishModeActive.value || conversationId.isBlank()) return
        scope.launch {
            chatService.reportVanishScreenRecording(conversationId, currentUserId)
                .onFailure { _error.value = it.message }
        }
    }

    fun setUploadProgress(messageId: String, progress: Double) {
        if (!shouldPublishProgressUpdate(_uploadProgress.value[messageId], progress)) return
        _uploadProgress.value = _uploadProgress.value + (messageId to progress)
    }

    fun setDownloadProgress(messageId: String, progress: Double) {
        if (!shouldPublishProgressUpdate(_downloadProgress.value[messageId], progress)) return
        _downloadProgress.value = _downloadProgress.value + (messageId to progress)
    }

    fun clearUploadProgress(messageId: String) {
        if (messageId !in _uploadProgress.value) return
        _uploadProgress.value = _uploadProgress.value - messageId
    }

    fun clearDownloadProgress(messageId: String) {
        if (messageId !in _downloadProgress.value) return
        _downloadProgress.value = _downloadProgress.value - messageId
    }

    /** ≡ `shouldPublishProgressUpdate(previous:next:)`. */
    private fun shouldPublishProgressUpdate(previous: Double?, next: Double): Boolean {
        if (previous == null) return true
        if (next <= 0.0 || next >= 1.0) return next != previous
        return kotlin.math.abs(next - previous) >= 0.03
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
    companion object {
        const val recentChatWindowSize = 20
        const val staleChatWindowSize = 6
        const val staleChatThresholdDays = 45
        const val historyPageSize = 50
        const val navigationWindowRadius = 25
        /** ≡ iOS `listenerPauseTTL` (180s). */
        const val listenerPauseTTLMillis = 180_000L

        fun mergeSearchResultIds(ids: List<String>): List<String> {
            val seen = mutableSetOf<String>()
            val merged = mutableListOf<String>()
            for (id in ids) {
                if (seen.add(id)) merged += id
            }
            return merged
        }
    }
}
