package com.moments.android.views.messaging.core

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.Conversation
import com.moments.android.models.EnhancedMessage
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.views.messaging.services.ChatService
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port reducido de MessagingViewModel — inbox + deep-link targetConversationId.
 */
class MessagingViewModel : ViewModel() {

    var conversations by mutableStateOf<List<Conversation>>(emptyList())
        private set
    var isLoading by mutableStateOf(true)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var selectedConversation by mutableStateOf<Conversation?>(null)
        private set
    var chatMessages by mutableStateOf<List<EnhancedMessage>>(emptyList())
        private set
    var isChatLoading by mutableStateOf(false)
        private set

    private var targetWaitJob: Job? = null

    fun start(targetConversationId: String?) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            errorMessage = "Auth required"
            isLoading = false
            return
        }
        val cached = LocalPersistenceService.loadConversations()
        if (cached.isNotEmpty()) {
            conversations = cached
            isLoading = false
            maybeSelectTarget(targetConversationId)
        }
        ChatService.fetchConversations(userId) { result ->
            result.onSuccess { list ->
                conversations = list
                isLoading = false
                errorMessage = null
                maybeSelectTarget(targetConversationId)
            }.onFailure { e ->
                if (conversations.isEmpty()) {
                    errorMessage = e.message
                    isLoading = false
                }
            }
        }
    }

    fun onTargetConversationId(targetId: String?) {
        maybeSelectTarget(targetId)
    }

    fun openConversation(conversation: Conversation) {
        selectedConversation = conversation
        loadChat(conversation.id.orEmpty())
    }

    fun closeChat() {
        selectedConversation = null
        chatMessages = emptyList()
    }

    fun sendText(text: String) {
        val conv = selectedConversation ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val body = text.trim()
        if (body.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                ChatService.sendTextMessage(
                    conversationId = conv.id.orEmpty(),
                    senderId = uid,
                    content = body,
                )
                loadChat(conv.id.orEmpty())
            }
        }
    }

    override fun onCleared() {
        ChatService.stopConversationsListener()
        targetWaitJob?.cancel()
        super.onCleared()
    }

    private fun maybeSelectTarget(targetId: String?) {
        val id = targetId?.takeIf { it.isNotBlank() } ?: return
        val found = conversations.firstOrNull { it.id == id }
        if (found != null) {
            openConversation(found)
            return
        }
        targetWaitJob?.cancel()
        targetWaitJob = viewModelScope.launch {
            delay(3000)
            conversations.firstOrNull { it.id == id }?.let { openConversation(it) }
        }
    }

    private fun loadChat(conversationId: String) {
        if (conversationId.isBlank()) return
        isChatLoading = true
        viewModelScope.launch {
            val result = ChatService.fetchRecentMessages(conversationId, limit = 80)
            chatMessages = result.getOrDefault(emptyList())
            isChatLoading = false
        }
    }
}
