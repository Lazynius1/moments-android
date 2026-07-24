package com.moments.android.views.nova.agent

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.StringRes
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.ai.type.Content
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserDataForNova
import com.moments.android.services.nova.NovaEmbeddingService
import com.moments.android.views.nova.ai.NovaAIService
import com.moments.android.views.nova.ai.NovaPromptCatalog
import com.moments.android.views.nova.conversation.NovaConversationStore
import com.moments.android.views.nova.NovaConversationTitle
import com.moments.android.views.nova.novacore.NovaChatMessage
import com.moments.android.views.nova.novacore.NovaGroundingSource
import com.moments.android.views.nova.ai.NovaAIService.GroundingMetadata
import com.moments.android.views.nova.ai.NovaAIService.NovaModelContent
import com.moments.android.views.nova.memory.NovaContextStore
import com.moments.android.views.nova.memory.NovaMemory
import com.moments.android.views.nova.memory.NovaMemoryEngine
import com.moments.android.views.nova.memory.NovaMemoryStore
import com.moments.android.views.nova.memory.NovaUserContext
import com.moments.android.views.nova.tools.NovaMomentDraftParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface NovaAgentStatus {
    data object Idle : NovaAgentStatus
    data object Thinking : NovaAgentStatus
    data class CallingTool(val name: String) : NovaAgentStatus
    data object Streaming : NovaAgentStatus
    data object AwaitingConfirmation : NovaAgentStatus
}

/** Android counterpart of `NovaAgent.swift`. */
class NovaAgent(
    context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val ai: NovaAIService = NovaAIService,
    private val memoryStore: NovaMemoryStore = NovaMemoryStore,
    private val contextStore: NovaContextStore = NovaContextStore,
    private val conversationStore: NovaConversationStore = NovaConversationStore,
    private val firestoreService: FirestoreService = FirestoreService(),
) : ViewModel() {
    private val appContext = context.applicationContext

    init {
        NovaConversationStore.initialize(appContext)
    }

    var inputText by mutableStateOf("")
    var selectedImage by mutableStateOf<Bitmap?>(null)
    var conversationHistory by mutableStateOf<List<NovaChatMessage>>(emptyList())
        private set
    var isLoading by mutableStateOf(false)
        private set
    var showSuggestedOptions by mutableStateOf(true)
        private set
    var showCelebration by mutableStateOf(false)
    var userData by mutableStateOf<AppUser?>(null)
        private set
    var userMemory by mutableStateOf<NovaMemory?>(null)
        private set
    var userContext by mutableStateOf<NovaUserContext?>(null)
        private set
    var hasMemoryLoaded by mutableStateOf(false)
        private set
    var conversationTitles by mutableStateOf<List<NovaConversationTitle>>(emptyList())
        private set
    var agentStatus by mutableStateOf<NovaAgentStatus>(NovaAgentStatus.Idle)
        private set
    var activeToolDisplayName by mutableStateOf<String?>(null)
        private set
    var pendingAction by mutableStateOf<NovaPendingAction?>(null)
        private set

    fun updateShowSuggestedOptions(show: Boolean) {
        showSuggestedOptions = show
    }

    private var stagedMomentImage: Bitmap? = null
    private var chatSession: NovaAIService.ChatSession? = null
    private var toolExecutor: NovaToolExecutor? = null
    private var currentConversationId: String? = null
    private var sendJob: Job? = null
    private var confirmationDeferred: CompletableDeferred<Boolean>? = null
    private var lastFinalizedFingerprint: String? = null
    private var internalHistorySummary: String? = null
    private var memoryObserverJob: Job? = null

    val currentUserDisplayName: String
        get() = userMemory?.preferredName ?: userData?.username ?: string(R.string.nova_user)

    val welcomeSuggestions: List<NovaWelcomeSuggestion>
        get() = NovaWelcomeSuggestion.defaults

    fun fetchUserData() {
        val userId = auth.currentUser?.uid ?: return
        isLoading = true
        installMemoryObserver(userId)
        viewModelScope.launch {
            runCatching { firestoreService.fetchUserDataForNova(userId) }
                .onSuccess { user ->
                    userData = user
                    loadMemoryAndContext(userId)
                    loadConversationTitles()
                    bootstrapChatSession()
                }
            isLoading = false
        }
    }

    fun reloadMemoryFromStore() {
        val userId = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            memoryStore.invalidateCache(userId)
            contextStore.invalidateCache(userId)
            loadMemoryAndContext(userId)
            bootstrapChatSession()
        }
    }

    /** Preserved for UI compatibility. */
    fun checkPhotoLibraryPermission() = Unit

    fun confirmPendingAction() {
        pendingAction = null
        isLoading = true
        agentStatus = NovaAgentStatus.CallingTool("confirmed")
        confirmationDeferred?.complete(true)
        confirmationDeferred = null
    }

    fun cancelPendingAction() {
        if (pendingAction?.kind == NovaPendingAction.Kind.CREATE_MOMENT) stagedMomentImage = null
        pendingAction = null
        isLoading = true
        agentStatus = NovaAgentStatus.Streaming
        confirmationDeferred?.complete(false)
        confirmationDeferred = null
    }

    fun handleConfirmationDismissed() {
        if (confirmationDeferred != null) cancelPendingAction()
    }

    private suspend fun waitForUserConfirmation(action: NovaPendingAction): Boolean {
        val deferred = CompletableDeferred<Boolean>()
        confirmationDeferred = deferred
        pendingAction = action
        agentStatus = NovaAgentStatus.AwaitingConfirmation
        activeToolDisplayName = null
        isLoading = false
        delay(150)
        return deferred.await()
    }

    suspend fun loadConversationTitles() {
        val userId = auth.currentUser?.uid ?: return
        conversationTitles = conversationStore.loadConversationTitles(userId)
    }

    fun startNewConversation() {
        viewModelScope.launch {
            finalizeConversationIfNeeded()
            currentConversationId = null
            lastFinalizedFingerprint = null
            internalHistorySummary = null
            stagedMomentImage = null
            conversationHistory = listOf(
                NovaChatMessage(
                    text = string(R.string.nova_chat_encryption_notice),
                    isUser = false,
                    isSystem = true,
                ),
            )
            chatSession = null
            bootstrapChatSession()
            showSuggestedOptions = true
            agentStatus = NovaAgentStatus.Idle
        }
    }

    suspend fun loadConversation(conversationId: String) {
        val userId = auth.currentUser?.uid ?: return
        finalizeConversationIfNeeded()
        lastFinalizedFingerprint = null
        isLoading = true
        internalHistorySummary = null
        stagedMomentImage = null
        conversationHistory = conversationStore.loadConversation(conversationId, userId)
        currentConversationId = conversationId
        rebuildChatFromHistoryAsync()
        isLoading = false
        showSuggestedOptions = false
    }

    suspend fun deleteConversation(conversationId: String) {
        val userId = auth.currentUser?.uid ?: return
        if (conversationStore.deleteConversation(conversationId, userId)) {
            conversationTitles = conversationTitles.filterNot { it.id == conversationId }
            if (currentConversationId == conversationId) startNewConversation()
        }
    }

    fun sendMessage() {
        val trimmed = inputText.trim()
        if (trimmed.isEmpty() && selectedImage == null) return
        if (userData == null || !hasMemoryLoaded) return
        val userId = auth.currentUser?.uid ?: return

        val userText = trimmed.ifEmpty { string(R.string.nova_image_fallback_prompt) }
        val image = selectedImage
        if (image != null) stagedMomentImage = image
        inputText = ""
        selectedImage = null
        conversationHistory = conversationHistory + NovaChatMessage(text = userText, isUser = true, image = image)
        isLoading = true
        agentStatus = NovaAgentStatus.Thinking
        activeToolDisplayName = null
        val botId = UUID.randomUUID().toString()
        conversationHistory = conversationHistory + NovaChatMessage(id = botId, text = "", isUser = false)

        sendJob?.cancel()
        sendJob = viewModelScope.launch {
            runCatching { runTurn(userId, userText, image, botId) }
                .onFailure { replaceBotText(botId, string(R.string.nova_error_generic)) }
            isLoading = false
            agentStatus = NovaAgentStatus.Idle
            activeToolDisplayName = null
            saveCurrentConversation()
        }
    }

    private suspend fun runTurn(userId: String, userText: String, image: Bitmap?, botId: String) {
        if (chatSession == null) bootstrapChatSession()
        val chat = chatSession ?: throw NovaAgentError.MissingUser
        val executor = toolExecutor ?: throw NovaAgentError.MissingUser
        val mediaImage = image ?: stagedMomentImage
        executor.resetTurn()
        executor.attachedImageForTurn = mediaImage
        val memoryContext = relevantFactsContext(userText)
        val pending = listOf(NovaModelContent.user(NovaAIService.userParts(userText, image, memoryContext)))
        val historySnapshot = chat.history
        var streamedText = ""
        var sawToolCalls = false
        var streamFailed = false

        try {
            chat.sendMessageStream(pending).collect { chunk ->
                chunk.groundingMetadata?.let { mergeGrounding(it, botId) }
                if (chunk.functionCalls.isNotEmpty()) sawToolCalls = true
                if (!sawToolCalls && !chunk.text.isNullOrEmpty()) {
                    streamedText += chunk.text
                    if (agentStatus !is NovaAgentStatus.Streaming) agentStatus = NovaAgentStatus.Streaming
                    replaceBotText(botId, streamedText)
                }
            }
        } catch (_: Exception) {
            streamFailed = true
        }

        if (!sawToolCalls && !streamFailed) {
            if (mediaImage != null && handleMomentPublishFallback(userText, mediaImage, chat, botId)) return
            if (streamedText.isNotEmpty()) return
        }

        bootstrapChatSessionFromNativeHistory(historySnapshot)
        val freshChat = chatSession ?: throw NovaAgentError.MissingUser
        val freshExecutor = toolExecutor ?: throw NovaAgentError.MissingUser
        freshExecutor.resetTurn()
        freshExecutor.attachedImageForTurn = mediaImage
        updateBot(botId) { it.copy(text = "", groundingSources = emptyList(), searchSuggestionsHtml = null) }
        agentStatus = NovaAgentStatus.Thinking
        val response = freshChat.sendMessage(pending)
        if (response.functionCalls.isNotEmpty()) {
            replaceBotText(botId, string(R.string.nova_confirm_preparing))
            handleToolCalls(response.functionCalls, freshChat, mutableSetOf(), botId)?.let { replaceBotText(botId, it) }
            return
        }
        if (mediaImage != null && handleMomentPublishFallback(userText, mediaImage, freshChat, botId)) return
        replaceBotText(botId, response.text.orEmpty())
        response.groundingMetadata?.let { mergeGrounding(it, botId) }
    }

    private fun mergeGrounding(metadata: GroundingMetadata, botId: String) {
        updateBot(botId) { message ->
            val existing = message.groundingSources.associateBy { it.url }.toMutableMap()
            metadata.chunks.forEach { chunk ->
                val url = chunk.url?.trim().orEmpty()
                if (url.isNotEmpty() && existing[url] == null) {
                    existing[url] = NovaGroundingSource(title = chunk.title?.trim().takeUnless { it.isNullOrEmpty() } ?: url, url = url)
                }
            }
            message.copy(
                groundingSources = existing.values.toList(),
                searchSuggestionsHtml = metadata.searchEntryPointHtml?.takeIf { it.isNotBlank() } ?: message.searchSuggestionsHtml,
            )
        }
    }

    private suspend fun relevantFactsContext(query: String): String? {
        val facts = userMemory?.facts ?: return null
        if (facts.size <= 10) return null
        val topIds = facts.sortedByDescending { it.relevanceScore }.take(10).map { it.id }.toSet()
        val relevant = NovaEmbeddingService.findSimilarFacts(query, facts.filterNot { it.id in topIds }, limit = 4)
        return relevant.takeIf { it.isNotEmpty() }?.joinToString("\n") { "- [${it.type.rawValue}] ${it.content}" }
    }

    private suspend fun handleToolCalls(
        calls: List<NovaAIService.FunctionCall>,
        chat: NovaAIService.ChatSession,
        seen: MutableSet<String>,
        botId: String,
        depth: Int = 1,
    ): String? {
        val executor = toolExecutor ?: return null
        if (depth > NovaToolExecutor.maxStepsPerTurn) throw NovaAgentError.StepLimitReached
        val uniqueCalls = calls.filter { seen.add("${it.name}-${it.arguments}") }
        if (uniqueCalls.isEmpty()) return null
        if (pendingAction == null) {
            agentStatus = NovaAgentStatus.CallingTool(uniqueCalls.first().name)
            activeToolDisplayName = toolDisplayName(uniqueCalls.first().name)
        }
        val responses = executor.execute(uniqueCalls)
        NovaToolExecutor.momentSuccessMessage(appContext, responses)?.let { success ->
            val response = runCatching { chat.sendFunctionResponses(responses) }.getOrNull()
            response?.groundingMetadata?.let { mergeGrounding(it, botId) }
            activeToolDisplayName = null
            agentStatus = NovaAgentStatus.Streaming
            return response?.text?.takeIf { it.isNotEmpty() } ?: success
        }
        val response = chat.sendFunctionResponses(responses)
        response.groundingMetadata?.let { mergeGrounding(it, botId) }
        activeToolDisplayName = null
        if (response.functionCalls.isNotEmpty()) return handleToolCalls(response.functionCalls, chat, seen, botId, depth + 1)
        agentStatus = NovaAgentStatus.Streaming
        return response.text
    }

    private suspend fun handleMomentPublishFallback(userText: String, image: Bitmap, chat: NovaAIService.ChatSession, botId: String): Boolean {
        val nudge = chat.sendMessage(listOf(NovaModelContent.userText(NovaPromptCatalog.createMomentToolNudge)))
        if (nudge.functionCalls.isNotEmpty()) {
            replaceBotText(botId, string(R.string.nova_confirm_preparing))
            handleToolCalls(nudge.functionCalls, chat, mutableSetOf(), botId)?.let { replaceBotText(botId, it) }
            return true
        }
        val draft = NovaMomentDraftParser.parse(userText) ?: return false
        val args = buildMap<String, Any?> {
            put("content", draft.content)
            put("audience", draft.audience)
            draft.targetUsername?.takeIf { it.isNotEmpty() }?.let { put("target_username", it) }
            draft.customListName?.takeIf { it.isNotEmpty() }?.let { put("custom_list_name", it) }
        }
        val action = NovaPendingAction.from(appContext, "create_moment", args, image) ?: return false
        replaceBotText(botId, string(R.string.nova_confirm_preparing))
        if (!waitForUserConfirmation(action)) {
            replaceBotText(botId, string(R.string.nova_confirm_cancelled))
            return true
        }
        val executor = toolExecutor ?: return false
        isLoading = true
        val result = executor.executeCreateMoment(args, image)
        isLoading = false
        val audienceLabel = result["audience_label"] as? String
        val text = when {
            result["success"] == true && audienceLabel != null -> string(R.string.nova_moment_published, audienceLabel)
            result["success"] == true -> string(R.string.nova_moment_published_generic)
            result["error"] is String -> string(R.string.nova_moment_failed, result["error"] as String)
            else -> string(R.string.nova_moment_failed_generic)
        }
        replaceBotText(botId, text)
        return true
    }

    private fun toolDisplayName(tool: String): String = when (tool) {
        "get_activity_summary", "get_weekly_summary", "get_profile_visits", "get_story_chain_info", "get_my_profile_snapshot", "get_recent_moments_summary", "get_recent_stories_summary", "get_profile_and_content_overview", "get_user_profile_snapshot", "get_moment_details", "get_echo_history_summary" -> string(R.string.nova_agent_tool_activity)
        "remember_fact", "update_user_preference" -> string(R.string.nova_agent_tool_memory)
        "create_moment" -> string(R.string.nova_agent_tool_moment)
        "list_audience_lists" -> string(R.string.nova_agent_tool_lists)
        "get_connection_suggestions", "get_followers_summary", "get_following_summary", "get_mutuals", "get_mutual_connections", "get_shared_interest_users", "find_user_by_username", "send_follow_request" -> string(R.string.nova_agent_tool_connections)
        else -> string(R.string.nova_agent_tool_generic)
    }

    fun regenerateLastResponse() {
        if (isLoading || confirmationDeferred != null) return
        val index = conversationHistory.indexOfLast { it.isUser }
        if (index < 0) return
        val message = conversationHistory[index]
        sendJob?.cancel()
        viewModelScope.launch {
            conversationHistory = conversationHistory.take(index)
            rebuildChatFromHistoryAsync()
            inputText = message.text
            selectedImage = message.image
            sendMessage()
        }
    }

    fun beginEditingLastUserMessage() {
        if (isLoading || confirmationDeferred != null) return
        val index = conversationHistory.indexOfLast { it.isUser }
        if (index < 0) return
        val message = conversationHistory[index]
        sendJob?.cancel()
        viewModelScope.launch {
            conversationHistory = conversationHistory.take(index)
            rebuildChatFromHistoryAsync()
            inputText = message.text
            selectedImage = message.image
        }
    }

    val canRetouchLastExchange: Boolean
        get() = !isLoading && confirmationDeferred == null && conversationHistory.any { it.isUser }

    private fun bootstrapChatSession(history: List<NovaModelContent> = emptyList()) {
        val user = userData ?: return
        val instruction = NovaContextAssembler.systemInstruction(user.username, userMemory, userContext, internalHistorySummary)
        chatSession = ai.startChat(instruction, history)
        val userId = auth.currentUser?.uid ?: return
        toolExecutor = NovaToolExecutor(appContext, userId).also { executor ->
            executor.onMemoryUpdated = { memory ->
                userMemory = memory
                rebuildChatFromHistory()
            }
            executor.onMomentCreated = {
                stagedMomentImage = null
                showCelebration = true
            }
            executor.requestUserConfirmation = { action -> waitForUserConfirmation(action) }
        }
    }

    private fun bootstrapChatSessionFromNativeHistory(history: List<Content>) {
        val user = userData ?: return
        val instruction = NovaContextAssembler.systemInstruction(user.username, userMemory, userContext, internalHistorySummary)
        chatSession = ai.startChatWithNativeHistory(instruction, history)
        val userId = auth.currentUser?.uid ?: return
        toolExecutor = NovaToolExecutor(appContext, userId).also { executor ->
            executor.onMemoryUpdated = { memory -> userMemory = memory; rebuildChatFromHistory() }
            executor.onMomentCreated = { stagedMomentImage = null; showCelebration = true }
            executor.requestUserConfirmation = { action -> waitForUserConfirmation(action) }
        }
    }

    private fun rebuildChatFromHistory() {
        viewModelScope.launch { rebuildChatFromHistoryAsync() }
    }

    private suspend fun rebuildChatFromHistoryAsync() {
        val meaningful = conversationHistory.filter { !it.isSystem && (it.text.isNotBlank() || it.image != null) }
        if (meaningful.isEmpty()) {
            internalHistorySummary = null
            bootstrapChatSession()
            return
        }
        val payload = buildHistoryPayload(meaningful)
        internalHistorySummary = payload.second
        bootstrapChatSession(payload.first)
    }

    private suspend fun buildHistoryPayload(messages: List<NovaChatMessage>): Pair<List<NovaModelContent>, String?> {
        if (messages.size <= COMPACTION_THRESHOLD) return modelHistory(messages) to null
        val older = messages.dropLast(12)
        val recent = messages.takeLast(12)
        val transcript = older.joinToString("\n") { "${if (it.isUser) "User" else "Nova"}: ${it.text.take(1200)}" }
        return runCatching { modelHistory(recent) to ai.compactHistory(transcript) }
            .getOrElse { modelHistory(messages.takeLast(18)) to null }
    }

    private fun modelHistory(messages: List<NovaChatMessage>): List<NovaModelContent> = messages.mapNotNull { message ->
        when {
            message.isUser -> NovaModelContent.user(NovaAIService.userParts(message.text, message.image))
            message.text.isNotBlank() -> NovaModelContent.modelText(message.text.trim())
            else -> null
        }
    }

    private suspend fun loadMemoryAndContext(userId: String) {
        userMemory = memoryStore.loadMemory(userId)
        userContext = contextStore.loadContext(userId)
        hasMemoryLoaded = true
    }

    private suspend fun saveCurrentConversation() {
        val userId = auth.currentUser?.uid ?: return
        if (conversationHistory.none { it.isUser }) return
        val existingId = currentConversationId
        currentConversationId = if (existingId != null) {
            conversationStore.updateConversation(existingId, userId, conversationHistory)
            existingId
        } else {
            conversationStore.saveConversation(userId, conversationHistory)
        }
        loadConversationTitles()
    }

    private fun conversationFingerprint(): String? {
        val meaningful = conversationHistory.filter { !it.isSystem && it.text.isNotBlank() }
        if (meaningful.none { it.isUser }) return null
        return "${currentConversationId ?: "draft"}-${meaningful.size}-${meaningful.last().id}"
    }

    suspend fun finalizeOnExit() = finalizeConversationIfNeeded()

    private suspend fun finalizeConversationIfNeeded() {
        val userId = auth.currentUser?.uid ?: return
        val fingerprint = conversationFingerprint() ?: return
        if (fingerprint == lastFinalizedFingerprint) return
        lastFinalizedFingerprint = fingerprint
        NovaMemoryEngine.scheduleConversationFinalize(userId, currentConversationId, conversationHistory)
    }

    private fun installMemoryObserver(userId: String) {
        if (memoryObserverJob != null) return
        memoryObserverJob = viewModelScope.launch {
            memoryStore.observeUpdates(userId).collect {
                memoryStore.invalidateCache(userId)
                contextStore.invalidateCache(userId)
                loadMemoryAndContext(userId)
                rebuildChatFromHistoryAsync()
            }
        }
    }

    private fun updateBot(id: String, transform: (NovaChatMessage) -> NovaChatMessage) {
        conversationHistory = conversationHistory.map { if (it.id == id) transform(it) else it }
    }

    private fun replaceBotText(id: String, text: String) = updateBot(id) { it.copy(text = text) }

    private fun string(@StringRes id: Int, vararg formatArgs: Any): String = appContext.getString(id, *formatArgs)

    override fun onCleared() {
        sendJob?.cancel()
        memoryObserverJob?.cancel()
        confirmationDeferred?.cancel()
        super.onCleared()
    }

    private companion object {
        const val COMPACTION_THRESHOLD = 24
    }
}

data class NovaWelcomeSuggestion(
    @StringRes val titleRes: Int,
    @StringRes val promptRes: Int,
    val icon: String,
) {
    companion object {
        val defaults = listOf(
            NovaWelcomeSuggestion(R.string.nova_suggestions_write_help_title, R.string.nova_suggestions_write_help_prompt, "pencil.line"),
            NovaWelcomeSuggestion(R.string.nova_suggestions_study_tips_title, R.string.nova_suggestions_study_tips_prompt, "book"),
            NovaWelcomeSuggestion(R.string.nova_suggestions_interests_title, R.string.nova_suggestions_interests_prompt, "heart"),
            NovaWelcomeSuggestion(R.string.nova_suggestions_advice_title, R.string.nova_suggestions_advice_prompt, "lightbulb"),
        )
    }
}

sealed class NovaAgentError : Exception() {
    data object StepLimitReached : NovaAgentError()
    data object MissingUser : NovaAgentError()
    data object MemoryNotLoaded : NovaAgentError()
}
