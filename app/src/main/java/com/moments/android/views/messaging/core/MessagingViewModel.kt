package com.moments.android.views.messaging.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.MomentsApplication
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.models.Conversation
import com.moments.android.models.EnhancedMessage
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchNewConversationSuggestions
import com.moments.android.services.firestore.searchUsers
import com.moments.android.services.messaging.LocalFirstMessagingSettings
import com.moments.android.services.messaging.MessageCatchUpService
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.views.messaging.services.ChatDraftStore
import com.moments.android.views.messaging.services.ChatScrollStateStore
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.ChatSessionEngine
import com.moments.android.views.messaging.services.areMutualFollowers
import com.moments.android.views.messaging.services.findExistingConversation
import com.moments.android.views.messaging.services.getOrCreateConversation
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date

/** Resultado de búsqueda global de mensajes (port de `GlobalMessageSearchResult`). */
data class GlobalMessageSearchResult(
    val message: EnhancedMessage,
    val conversation: Conversation,
) {
    val id: String get() = message.id
}

/**
 * Port de `Views/Messaging/Core/MessagingViewModel.swift`.
 *
 * Los `@Published` de iOS son estado de Compose; los `completion:` son lambdas. El debounce de
 * búsqueda (250 ms con `DispatchWorkItem` en iOS) se hace cancelando el `Job` anterior.
 */
class MessagingViewModel(
    private val firestoreService: FirestoreService = FirestoreService(),
) : ViewModel() {

    var conversations by mutableStateOf<List<Conversation>>(emptyList()); private set
    var archivedConversations by mutableStateOf<List<Conversation>>(emptyList()); private set
    var suggestedUsers by mutableStateOf<List<AppUser>>(emptyList()); private set
    var hasUnreadMessages by mutableStateOf(false); private set
    var selectedConversation by mutableStateOf<Conversation?>(null); private set
    var errorMessage by mutableStateOf<String?>(null); private set
    var requiresMessageRequest by mutableStateOf(false); private set

    var filteredConversations by mutableStateOf<List<Conversation>>(emptyList()); private set
    var searchedUsers by mutableStateOf<List<AppUser>>(emptyList()); private set
    var searchedMessages by mutableStateOf<List<GlobalMessageSearchResult>>(emptyList()); private set
    var isSearchingContent by mutableStateOf(false); private set

    var isLoading by mutableStateOf(true); private set

    private var isFirstFetch = true
    private var searchJob: Job? = null
    private var userSearchJob: Job? = null
    private var activeSearchQuery: String = ""
    private var activeUserSearchQuery: String = ""
    private val locallyReadConversationIds = mutableSetOf<String>()
    private var targetWaitJob: Job? = null

    private val currentUserId: String? get() = FirebaseAuth.getInstance().currentUser?.uid

    private fun localized(resId: Int): String = MomentsApplication.instance?.getString(resId).orEmpty()
    private fun localized(resId: Int, vararg args: Any): String =
        MomentsApplication.instance?.getString(resId, *args).orEmpty()

    // MARK: - Carga de conversaciones

    fun start(targetConversationId: String? = null) {
        val userId = currentUserId
        if (userId == null) {
            errorMessage = localized(R.string.messaging_error_not_authenticated)
            isLoading = false
            return
        }
        fetchConversations(userId)
        maybeSelectTarget(targetConversationId)
    }

    fun fetchConversations(userId: String) {
        val cached = sortConversationsForInbox(LocalPersistenceService.loadConversations())
        if (cached.isNotEmpty()) {
            val active = reconcilingOptimisticReadState(cached.filterNot { it.isArchived(userId) }, userId)
            val archived = reconcilingOptimisticReadState(cached.filter { it.isArchived(userId) }, userId)
            conversations = active
            archivedConversations = archived
            hasUnreadMessages = (active + archived).any { it.readStatus[userId] != true }
            isLoading = false
        }

        ChatService.fetchConversations(userId) { result ->
            result.onSuccess { incoming ->
                val filtered = incoming.filter { !it.id.isNullOrEmpty() }
                val active = reconcilingOptimisticReadState(
                    sortConversationsForInbox(filtered.filterNot { it.isArchived(userId) }),
                    userId,
                )
                val archived = reconcilingOptimisticReadState(
                    sortConversationsForInbox(filtered.filter { it.isArchived(userId) }),
                    userId,
                )
                conversations = active
                archivedConversations = archived
                hasUnreadMessages = (active + archived).any { it.readStatus[userId] != true }
                errorMessage = null
                isLoading = false

                LocalPersistenceService.saveConversations(active + archived, sync = isFirstFetch)
                isFirstFetch = false

                // Como iOS: calentar las sesiones de las conversaciones más recientes para que
                // abrirlas sea inmediato (caché de ChatSessionEngine).
                ChatSessionEngine.preloadRecentSessions(active, limit = 5)

                if (LocalFirstMessagingSettings.isEnabled) {
                    MessageCatchUpService.syncRecent(active + archived)
                }
            }.onFailure { error ->
                if (conversations.isEmpty()) {
                    errorMessage = localized(R.string.messaging_error_load_conversations, error.message.orEmpty())
                    isLoading = false
                }
            }
        }
    }

    // MARK: - Estado de lectura optimista

    fun markConversationReadOptimistically(conversationId: String) {
        val userId = currentUserId ?: return
        locallyReadConversationIds.add(conversationId)
        fun markRead(list: List<Conversation>) = list.map {
            if (it.id == conversationId) it.copy(readStatus = it.readStatus + (userId to true)) else it
        }
        conversations = markRead(conversations)
        archivedConversations = markRead(archivedConversations)
        hasUnreadMessages = (conversations + archivedConversations).any { it.readStatus[userId] != true }
    }

    private fun reconcilingOptimisticReadState(list: List<Conversation>, userId: String): List<Conversation> {
        if (locallyReadConversationIds.isEmpty()) return list
        return list.map { conversation ->
            val id = conversation.id
            if (id == null || id !in locallyReadConversationIds) return@map conversation
            if (conversation.readStatus[userId] == true) {
                locallyReadConversationIds.remove(id)
                conversation
            } else {
                conversation.copy(readStatus = conversation.readStatus + (userId to true))
            }
        }
    }

    // MARK: - Orden de la bandeja

    private fun sortConversationsForInbox(list: List<Conversation>): List<Conversation> {
        val userId = currentUserId
        return list.sortedWith(
            compareByDescending<Conversation> { it.isPinned(userId) }
                .thenByDescending { hasDraft(it, userId) }
                .thenByDescending { it.timestamp },
        )
    }

    private fun hasDraft(conversation: Conversation, userId: String?): Boolean {
        val conversationId = conversation.id ?: return false
        val context = MomentsApplication.instance ?: return false
        return ChatDraftStore.draft(context, conversationId, userId).isNotBlank()
    }

    fun refreshDraftOrdering() {
        conversations = sortConversationsForInbox(conversations)
        archivedConversations = sortConversationsForInbox(archivedConversations)
        filteredConversations = sortConversationsForInbox(filteredConversations)
    }

    fun archivedUnreadCount(userId: String): Int =
        archivedConversations.count { it.readStatus[userId] != true }

    // MARK: - Búsqueda (bandeja: conversaciones + usuarios + mensajes)

    fun searchConversationsAndUsers(query: String) {
        val trimmed = query.trim()
        activeSearchQuery = trimmed
        searchJob?.cancel()

        if (trimmed.isEmpty()) {
            clearSearch()
            return
        }

        isSearchingContent = true
        val lowered = trimmed.lowercase()
        val context = MomentsApplication.instance

        filteredConversations = (conversations + archivedConversations).filter { conversation ->
            val username = conversation.otherParticipantUsername?.lowercase().orEmpty()
            val lastMessage = conversation.lastMessage?.lowercase().orEmpty()
            val draft = conversation.id
                ?.let { id -> context?.let { ChatDraftStore.draft(it, id).lowercase() } }
                .orEmpty()
            username.contains(lowered) || lastMessage.contains(lowered) || draft.contains(lowered)
        }

        searchedMessages = globalMessageResults(trimmed)

        val existingUserIds = (conversations + archivedConversations).map { it.otherParticipantId }.toSet()
        searchJob = viewModelScope.launch {
            delay(250) // debounce, como el DispatchWorkItem de iOS
            val users = runCatching { firestoreService.searchUsers(trimmed) }.getOrDefault(emptyList())
            if (activeSearchQuery != trimmed) return@launch
            isSearchingContent = false
            searchedUsers = users.filter { it.id != currentUserId && it.id !in existingUserIds }
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        activeSearchQuery = ""
        filteredConversations = emptyList()
        searchedUsers = emptyList()
        searchedMessages = emptyList()
        isSearchingContent = false
    }

    /**
     * Búsqueda global sobre el caché local (100% local, como iOS: con E2E, escanear en remoto
     * obligaría a descargar y descifrar todo el historial).
     */
    private fun globalMessageResults(query: String): List<GlobalMessageSearchResult> {
        val matches = LocalPersistenceService.searchMessagesGlobally(query)
        if (matches.isEmpty()) return emptyList()
        val byId = (conversations + archivedConversations).mapNotNull { conv ->
            conv.id?.let { it to conv }
        }.toMap()
        return matches.mapNotNull { message ->
            byId[message.conversationId]?.let { GlobalMessageSearchResult(message, it) }
        }
    }

    // MARK: - Usuarios para conversación nueva

    fun searchUsers(query: String) {
        val trimmed = query.trim()
        activeUserSearchQuery = trimmed
        userSearchJob?.cancel()

        if (trimmed.isEmpty()) {
            loadNewConversationSuggestions()
            return
        }

        userSearchJob = viewModelScope.launch {
            delay(250)
            runCatching { firestoreService.searchUsers(trimmed) }
                .onSuccess { users ->
                    if (activeUserSearchQuery != trimmed) return@onSuccess
                    suggestedUsers = users
                }
                .onFailure { error ->
                    errorMessage = localized(R.string.messaging_error_search_users, error.message.orEmpty())
                }
        }
    }

    private fun loadNewConversationSuggestions() {
        val recentPartnerIds = conversations.map { it.otherParticipantId }
        viewModelScope.launch {
            runCatching { firestoreService.fetchNewConversationSuggestions(recentPartnerIds) }
                .onSuccess { users ->
                    if (activeUserSearchQuery.isNotEmpty()) return@onSuccess
                    suggestedUsers = users
                    errorMessage = null
                }
                .onFailure { error ->
                    errorMessage = localized(R.string.messaging_error_search_users, error.message.orEmpty())
                }
        }
    }

    // MARK: - Iniciar conversación

    /**
     * Port de `startConversation(with:from:initialMessage:completion:)`.
     *
     * Sin mensaje inicial no se crea documento en Firestore: se abre un borrador local y solo se
     * persiste al enviar el primero. Si no hay conversación previa ni follow mutuo, marca
     * [requiresMessageRequest] y devuelve null — la UI abre entonces el chat en modo solicitud.
     */
    fun startConversation(
        user: AppUser,
        fromUserId: String,
        initialMessage: String? = null,
        completion: (Conversation?) -> Unit = {},
    ) {
        requiresMessageRequest = false
        val trimmedInitial = initialMessage?.trim().orEmpty()

        val existing = conversations.firstOrNull { it.otherParticipantId == user.id && it.id != null }
        if (existing != null) {
            if (trimmedInitial.isEmpty()) {
                selectedConversation = existing
                completion(existing)
                return
            }
            val conversationId = existing.id
            if (conversationId == null) {
                errorMessage = localized(R.string.messaging_error_start_conversation_failed)
                completion(null)
                return
            }
            viewModelScope.launch {
                runCatching {
                    ChatService.sendTextMessage(conversationId, fromUserId, trimmedInitial)
                }.onSuccess {
                    val updated = existing.copy(timestamp = Date())
                    selectedConversation = updated
                    conversations = conversations.map { if (it.id == conversationId) updated else it }
                    fetchConversations(fromUserId)
                    errorMessage = null
                    requiresMessageRequest = false
                    completion(updated)
                }.onFailure { error ->
                    errorMessage = localized(R.string.messaging_error_send_message, error.message.orEmpty())
                    requiresMessageRequest = false
                    completion(null)
                }
            }
            return
        }

        viewModelScope.launch {
            val canSend = runCatching { PrivacyService.canSendMessage(fromUserId, user.id) }.getOrDefault(false)
            if (!canSend) {
                errorMessage = localized(R.string.messaging_error_cannot_start)
                requiresMessageRequest = false
                completion(null)
                return@launch
            }

            if (trimmedInitial.isEmpty()) {
                // Si ya existe conversación (aunque esté "borrada" o no cargada en la lista local)
                // se abre directa: no se exige solicitud nueva.
                val existingId = ChatService.findExistingConversation(fromUserId, user.id).getOrNull()
                if (existingId != null) {
                    val conversation = Conversation(
                        id = existingId,
                        participants = listOf(fromUserId, user.id).sorted(),
                        lastMessage = "",
                        timestamp = Date(),
                        readStatus = mapOf(fromUserId to true, user.id to false),
                        otherParticipantId = user.id,
                        otherParticipantUsername = user.username,
                        otherParticipantProfileImagePath = user.profileImagePath,
                    )
                    selectedConversation = conversation
                    errorMessage = null
                    requiresMessageRequest = false
                    completion(conversation)
                    return@launch
                }

                // Sin conversación previa: follow mutuo → borrador local; si no → solicitud.
                val mutual = runCatching { ChatService.areMutualFollowers(fromUserId, user.id) }.getOrDefault(false)
                if (mutual) {
                    val draft = Conversation(
                        id = null,
                        participants = listOf(fromUserId, user.id).sorted(),
                        lastMessage = "",
                        timestamp = Date(),
                        readStatus = mapOf(fromUserId to true, user.id to false),
                        otherParticipantId = user.id,
                        otherParticipantUsername = user.username,
                        otherParticipantProfileImagePath = user.profileImagePath,
                    )
                    selectedConversation = draft
                    errorMessage = null
                    requiresMessageRequest = false
                    completion(draft)
                } else {
                    errorMessage = localized(R.string.messaging_error_message_request_required)
                    requiresMessageRequest = true
                    completion(null)
                }
                return@launch
            }

            ChatService.getOrCreateConversation(fromUserId, user.id, initialMessage)
                .onSuccess { conversationId ->
                    val conversation = Conversation(
                        id = conversationId,
                        participants = listOf(fromUserId, user.id).sorted(),
                        lastMessage = trimmedInitial,
                        timestamp = Date(),
                        readStatus = mapOf(fromUserId to true, user.id to false),
                        otherParticipantId = user.id,
                        otherParticipantUsername = user.username,
                        otherParticipantProfileImagePath = user.profileImagePath,
                    )
                    selectedConversation = conversation
                    if (conversations.none { it.id == conversationId }) {
                        conversations = listOf(conversation) + conversations
                    }
                    fetchConversations(fromUserId)
                    errorMessage = null
                    requiresMessageRequest = false
                    completion(conversation)
                }
                .onFailure { error ->
                    // El backend rechaza por falta de follow mutuo → hace falta solicitud.
                    val message = error.message.orEmpty().lowercase()
                    if (message.contains("403") || message.contains("mutual") ||
                        message.contains("no siguen mutuamente") || message.contains("solicitud")
                    ) {
                        errorMessage = localized(R.string.messaging_error_message_request_required)
                        requiresMessageRequest = true
                    } else {
                        errorMessage = localized(R.string.messaging_error_create_conversation, error.message.orEmpty())
                        requiresMessageRequest = false
                    }
                    completion(null)
                }
        }
    }

    // MARK: - Acciones sobre conversaciones

    fun openConversation(conversation: Conversation) {
        selectedConversation = conversation
    }

    fun closeChat() {
        selectedConversation = null
    }

    fun deleteConversation(conversation: Conversation) {
        val conversationId = conversation.id?.takeIf { it.isNotEmpty() } ?: return
        val userId = currentUserId?.takeIf { it.isNotEmpty() } ?: return

        conversations = conversations.filterNot { it.id == conversationId }
        archivedConversations = archivedConversations.filterNot { it.id == conversationId }
        filteredConversations = filteredConversations.filterNot { it.id == conversationId }
        hasUnreadMessages = (conversations + archivedConversations).any { it.readStatus[userId] != true }
        ChatSessionEngine.invalidateSession(conversationId)
        MomentsApplication.instance?.let { ChatScrollStateStore.clear(it, conversationId) }
        LocalPersistenceService.saveConversations(conversations + archivedConversations, sync = true)
        LocalPersistenceService.deleteConversationCache(conversationId)

        viewModelScope.launch {
            ChatService.deleteConversationsBetweenUsers(userId, conversation.otherParticipantId)
                .onFailure { errorMessage = localized(R.string.messaging_error_delete_conversation, it.message.orEmpty()) }
        }
    }

    fun markConversationAsUnread(conversation: Conversation) {
        val userId = currentUserId ?: return
        val id = conversation.id ?: return
        val updated = conversation.copy(readStatus = conversation.readStatus + (userId to false))
        conversations = conversations.map { if (it.id == id) updated else it }
        archivedConversations = archivedConversations.map { if (it.id == id) updated else it }
        hasUnreadMessages = true
        viewModelScope.launch {
            ChatService.markConversationAsUnread(id, userId)
                .onFailure { errorMessage = localized(R.string.messaging_error_mark_unread, it.message.orEmpty()) }
        }
    }

    fun updateVanishMode(conversationId: String, active: Boolean) {
        fun patch(list: List<Conversation>) = list.map {
            if (it.id == conversationId) it.also { c -> c.vanishModeActive = active } else it
        }
        conversations = patch(conversations)
        archivedConversations = patch(archivedConversations)
        filteredConversations = patch(filteredConversations)
        LocalPersistenceService.saveConversations(conversations + archivedConversations, sync = false)
    }

    /** Port de `applyLocalConversationState` para fijar: actualiza local y reordena la bandeja. */
    fun togglePinned(conversation: Conversation) {
        val userId = currentUserId ?: return
        val conversationId = conversation.id ?: return
        val pinned = conversation.isPinned(userId)
        val ids = conversation.pinnedByUserIds.orEmpty().toMutableList()
        if (pinned) ids.remove(userId) else if (userId !in ids) ids.add(userId)
        val updated = conversation.copy(pinnedByUserIds = ids.takeIf { it.isNotEmpty() })

        fun patch(list: List<Conversation>) =
            sortConversationsForInbox(list.map { if (it.id == conversationId) updated else it })
        conversations = patch(conversations)
        archivedConversations = patch(archivedConversations)
        filteredConversations = patch(filteredConversations)
        LocalPersistenceService.saveConversations(conversations + archivedConversations, sync = true)

        viewModelScope.launch {
            val result = if (pinned) ChatService.unpinConversation(conversationId, userId)
            else ChatService.pinConversation(conversationId, userId)
            result.onFailure { errorMessage = it.message }
        }
    }

    /** Silenciar/reactivar notificaciones de la conversación. */
    fun toggleMuted(conversation: Conversation) {
        val userId = currentUserId ?: return
        val conversationId = conversation.id ?: return
        val muted = conversation.isMuted(userId)
        val ids = conversation.mutedByUserIds.orEmpty().toMutableList()
        if (muted) ids.remove(userId) else if (userId !in ids) ids.add(userId)
        val updated = conversation.copy(mutedByUserIds = ids.takeIf { it.isNotEmpty() })

        fun patch(list: List<Conversation>) = list.map { if (it.id == conversationId) updated else it }
        conversations = patch(conversations)
        archivedConversations = patch(archivedConversations)
        filteredConversations = patch(filteredConversations)
        LocalPersistenceService.saveConversations(conversations + archivedConversations, sync = true)

        viewModelScope.launch {
            val result = if (muted) ChatService.unmuteConversation(conversationId, userId)
            else ChatService.muteConversation(conversationId, userId)
            result.onFailure { errorMessage = it.message }
        }
    }

    fun archiveConversation(conversation: Conversation) = updateArchiveState(conversation, true)

    fun unarchiveConversation(conversation: Conversation) = updateArchiveState(conversation, false)

    private fun updateArchiveState(conversation: Conversation, archived: Boolean) {
        val userId = currentUserId ?: return
        val conversationId = conversation.id ?: return
        val archivedIds = conversation.archivedByUserIds.orEmpty().toMutableList()
        if (archived) { if (userId !in archivedIds) archivedIds.add(userId) } else archivedIds.remove(userId)
        val updated = conversation.copy(archivedByUserIds = archivedIds.takeIf { it.isNotEmpty() })

        if (archived) {
            conversations = conversations.filterNot { it.id == conversationId }
            archivedConversations = sortConversationsForInbox(
                listOf(updated) + archivedConversations.filterNot { it.id == conversationId },
            )
        } else {
            archivedConversations = archivedConversations.filterNot { it.id == conversationId }
            conversations = sortConversationsForInbox(
                listOf(updated) + conversations.filterNot { it.id == conversationId },
            )
        }
        LocalPersistenceService.saveConversations(conversations + archivedConversations, sync = true)

        viewModelScope.launch {
            val result = if (archived) ChatService.archiveConversation(conversationId, userId)
            else ChatService.unarchiveConversation(conversationId, userId)
            result.onFailure { errorMessage = it.message }
        }
    }

    fun stopListening() {
        ChatService.stopConversationsListener()
    }

    fun onTargetConversationId(targetId: String?) = maybeSelectTarget(targetId)

    private fun maybeSelectTarget(targetId: String?) {
        val id = targetId?.takeIf { it.isNotBlank() } ?: return
        (conversations + archivedConversations).firstOrNull { it.id == id }?.let {
            openConversation(it)
            return
        }
        targetWaitJob?.cancel()
        targetWaitJob = viewModelScope.launch {
            repeat(20) {
                delay(250)
                (conversations + archivedConversations).firstOrNull { it.id == id }?.let { found ->
                    openConversation(found)
                    return@launch
                }
            }
        }
    }

    override fun onCleared() {
        searchJob?.cancel()
        userSearchJob?.cancel()
        targetWaitJob?.cancel()
        ChatService.stopConversationsListener()
        super.onCleared()
    }
}
