package com.moments.android.views.story

import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.MomentsApplication
import com.moments.android.R
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.content.StoryTrayService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserProfile
import com.moments.android.services.firestore.rebuildStorySummary
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.persistence.StorySeenStateService
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.social.AffinityInteractionType
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.social.StoryRingCacheService
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.uploadMedia
import com.moments.android.services.messaging.DirectMessageRoute
import com.moments.android.services.messaging.MessageRequestInteractionContext
import com.moments.android.services.messaging.MessageRequestService
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

/**
 * Port de `Views/story/StoryViewModel.swift`.
 * Carga ring, privacy, replies, reactions, viewers, vanish y preload vía [StoryPlaybackCoordinator].
 */
class StoryViewModel(
    private val firestore: FirestoreService = FirestoreService(),
) : ViewModel() {

    /** ≡ `@Published var stories` */
    var stories by mutableStateOf<Map<String, List<Story>>>(emptyMap())
        private set

    /** ≡ `@Published var sortedStoryUserIds` — orden de afinidad. */
    var sortedStoryUserIds by mutableStateOf<List<String>>(emptyList())
        private set

    /** ≡ `@Published var ringOrderedStoryUserIds` — tú primero, luego following. */
    var ringOrderedStoryUserIds by mutableStateOf<List<String>>(emptyList())
        private set

    private var lastFetchRingUserIds: List<String> = emptyList()
    private var lockedRingNavigationOrder: List<String> = emptyList()
    private val reactionListeners = mutableMapOf<String, ListenerRegistration>()

    var hasActiveStory by mutableStateOf(false)
        private set
    var storyReactions by mutableStateOf<Map<String, List<StoryReaction>>>(emptyMap())
        private set
    var storyViewers by mutableStateOf<Map<String, List<StoryViewer>>>(emptyMap())
        private set

    /** Android UI: loading / error (StoriesView). */
    var isLoading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private val storyRepository = StoryRepository(firestore)
    private val messageRequestService = MessageRequestService()
    private val playbackCoordinator = StoryPlaybackCoordinator(MomentsApplication.instance)
    private var isFirstFetch = true
    private val authorReelJobs = mutableMapOf<String, Job>()
    private val vanishActiveWithAuthor = mutableMapOf<String, Boolean>()

    /** ≡ `NSLocalizedString` vía resources (8 locales). */
    private fun localized(resId: Int): String =
        MomentsApplication.instance?.getString(resId).orEmpty()

    /**
     * Orden de navegación preferido para el viewer (anillo del feed).
     * Preferir [ringOrderedStoryUserIds], luego [sortedStoryUserIds].
     */
    val userIds: List<String>
        get() = when {
            ringOrderedStoryUserIds.isNotEmpty() -> ringOrderedStoryUserIds
            sortedStoryUserIds.isNotEmpty() -> sortedStoryUserIds
            else -> stories.keys.toList()
        }

    /** Alias de lectura para call sites Android previos (`storiesByUser`). */
    val storiesByUser: Map<String, List<Story>> get() = stories

    fun setRingNavigationOrder(userIds: List<String>) {
        val order = userIds.filter { it.isNotEmpty() }
        lockedRingNavigationOrder = order
        if (order.isNotEmpty()) {
            lastFetchRingUserIds = order
            ringOrderedStoryUserIds = order
        }
    }

    // MARK: - Obtener historias para un usuario específico

    fun fetchStoriesForSpecificUser(userId: String, viewerId: String) {
        if (userId.isEmpty() || viewerId.isEmpty()) {
            stories = emptyMap()
            return
        }
        viewModelScope.launch {
            if (userId == viewerId) {
                val cached = LocalPersistenceService.loadStories(userId)
                if (cached.isNotEmpty()) {
                    stories = stories + (userId to cached)
                    hasActiveStory = true
                }
            }
            val allStories = runCatching { storyRepository.fetchActiveStories(userId) }.getOrElse {
                if (stories[userId] == null) stories = emptyMap()
                return@launch
            }
            val visibility = allStories.associate { story ->
                val id = story.id.orEmpty()
                id to (id.isNotEmpty() && PrivacyService.canUserViewStoryEnhanced(story, viewerId))
            }
            val userStories = allStories.filter { story ->
                val id = story.id ?: return@filter false
                visibility[id] == true
            }
            if (userStories.isEmpty()) {
                stories = stories - userId
                LocalPersistenceService.deleteStories(userId)
                return@launch
            }
            stories = mapOf(userId to userStories)
            LocalPersistenceService.deleteStories(userId)
            LocalPersistenceService.saveStories(userStories)
            for (story in userStories) {
                val storyId = story.id ?: continue
                fetchReactions(userId, storyId)
                fetchViewers(userId, storyId)
            }
            prefetchImages()
        }
    }

    /** Carga el reel de un autor (backend con privacidad) sin vaciar el resto del diccionario. */
    fun loadAuthorReelIfNeeded(authorId: String, viewerId: String) {
        if (authorId.isEmpty() || viewerId.isEmpty()) return
        if (!stories[authorId].isNullOrEmpty()) return
        if (authorReelJobs.containsKey(authorId)) return

        if (authorId == viewerId) {
            val cached = LocalPersistenceService.loadStories(authorId)
            if (cached.isNotEmpty()) {
                stories = stories + (authorId to cached)
            }
        }

        authorReelJobs[authorId] = viewModelScope.launch {
            try {
                val bundle = StoryTrayService.fetchAuthorStoryBundle(authorId)
                if (bundle != null) {
                    val visible = bundle.stories.mapNotNull { StoryRepository.decodeBackendStory(it) }
                    if (visible.isNotEmpty()) {
                        applyLoadedStories(visible, userId = authorId, viewerId = viewerId)
                        return@launch
                    }
                }
                mergeStoriesForUserLegacy(userId = authorId, viewerId = viewerId)
            } finally {
                authorReelJobs.remove(authorId)
            }
        }
    }

    /** Compat: nombre anterior usado por el deck al hacer prefetch. */
    fun mergeStoriesForUserIfNeeded(userId: String, viewerId: String) {
        loadAuthorReelIfNeeded(authorId = userId, viewerId = viewerId)
    }

    private fun applyLoadedStories(userStories: List<Story>, userId: String, viewerId: String) {
        stories = stories + (userId to userStories)
        if (userId == viewerId) {
            LocalPersistenceService.deleteStories(userId)
            LocalPersistenceService.saveStories(userStories)
        }
        for (story in userStories) {
            val storyId = story.id ?: continue
            fetchReactions(userId, storyId)
            fetchViewers(userId, storyId)
        }
        prefetchImages()
    }

    private suspend fun mergeStoriesForUserLegacy(userId: String, viewerId: String) {
        if (!stories[userId].isNullOrEmpty()) return
        val allStories = runCatching { storyRepository.fetchActiveStories(userId) }.getOrNull()
            ?.takeIf { it.isNotEmpty() } ?: return
        val visibility = allStories.associate { story ->
            val id = story.id.orEmpty()
            id to (id.isNotEmpty() && PrivacyService.canUserViewStoryEnhanced(story, viewerId))
        }
        val visible = allStories.filter { story ->
            val id = story.id ?: return@filter false
            visibility[id] == true
        }
        if (visible.isEmpty()) return
        applyLoadedStories(visible, userId = userId, viewerId = viewerId)
    }

    // MARK: - Obtener historias para usuarios (con conexiones opcionales)

    fun fetchStories(forUserId: String, includeConnections: Boolean = false) {
        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            try {
                if (includeConnections && lockedRingNavigationOrder.isNotEmpty()) {
                    val ringOrder = lockedRingNavigationOrder
                    lastFetchRingUserIds = ringOrder
                    ringOrderedStoryUserIds = ringOrder
                    checkActiveStories(forUserId)
                    loadAuthorReelIfNeeded(authorId = forUserId, viewerId = forUserId)
                    isLoading = false
                    return@launch
                }

                if (includeConnections) {
                    val followingResult = runCatching { firestore.fetchFollowing(forUserId) }
                    val ringOrder = if (followingResult.isSuccess) {
                        val followingIds = followingResult.getOrThrow().map { it.id }
                        listOf(forUserId) + followingIds.filter { it != forUserId }
                    } else {
                        listOf(forUserId)
                    }
                    lastFetchRingUserIds = ringOrder
                    ringOrderedStoryUserIds = ringOrder
                    fetchStoriesForUsers(ringOrder, viewerId = forUserId)
                    checkActiveStories(forUserId)
                } else {
                    fetchStoriesForUsers(listOf(forUserId), viewerId = forUserId)
                    checkActiveStories(forUserId)
                }
            } catch (e: Exception) {
                errorMessage = e.message
            } finally {
                isLoading = false
            }
        }
    }

    private suspend fun fetchStoriesForUsers(userIds: List<String>, viewerId: String) {
        val allStories = mutableMapOf<String, List<Story>>()
        for (userId in userIds) {
            val userStories = runCatching { storyRepository.fetchActiveStories(userId) }.getOrNull()
                ?.takeIf { it.isNotEmpty() } ?: continue
            allStories[userId] = userStories
            for (story in userStories) {
                val storyId = story.id ?: continue
                fetchReactions(userId, storyId)
                fetchViewers(userId, storyId)
            }
        }
        filterStoriesByPrivacy(allStories, viewerId)
    }

    private suspend fun filterStoriesByPrivacy(
        allStories: Map<String, List<Story>>,
        viewerId: String,
    ) {
        val filteredStories = coroutineScope {
            allStories.map { (userId, list) ->
                async {
                    val visible = list.map { story ->
                        async {
                            if (PrivacyService.canUserViewStoryEnhanced(story, viewerId)) story else null
                        }
                    }.awaitAll().filterNotNull()
                    userId to visible
                }
            }.awaitAll().toMap()
        }

        var finalSortedIds: List<String> = emptyList()
        if (lastFetchRingUserIds.isNotEmpty()) {
            finalSortedIds = if (lockedRingNavigationOrder.isNotEmpty()) {
                lockedRingNavigationOrder.filter { !(filteredStories[it].isNullOrEmpty()) }
            } else {
                lastFetchRingUserIds.filter { !(filteredStories[it].isNullOrEmpty()) }
            }
            ringOrderedStoryUserIds = if (finalSortedIds.isEmpty()) lastFetchRingUserIds else finalSortedIds
        } else {
            // Affinity sorting ≡ AffinityTracker + bestFriends/mutuals
            val bestFriends = LocalPersistenceService.loadUser(viewerId)?.bestFriends?.toSet().orEmpty()
            val mutuals = LocalPersistenceService.loadConnections(viewerId).third.map { it.id }.toSet()
            val storyUserIds = filteredStories.keys.toList()
            val affinityScores = runCatching { AffinityTracker.getScores(storyUserIds) }.getOrDefault(emptyMap())
            finalSortedIds = filteredStories.keys.map { userId ->
                var score = (affinityScores[userId] ?: 0.0) * 1000
                score += kotlin.random.Random.nextDouble(0.0, 1000.0)
                when {
                    userId in bestFriends -> score += 50_000
                    userId in mutuals -> score += 20_000
                }
                userId to score
            }.sortedByDescending { it.second }.map { it.first }
        }

        stories = filteredStories
        sortedStoryUserIds = finalSortedIds

        if (isFirstFetch) {
            LocalPersistenceService.saveStories(filteredStories.values.flatten(), sync = true)
            isFirstFetch = false
        } else {
            for ((_, uStories) in filteredStories) {
                LocalPersistenceService.saveStories(uStories)
            }
        }
        prefetchImages()
    }

    private fun prefetchImages() {
        val urls = mutableListOf<String>()
        for ((_, userStories) in stories) {
            for (story in userStories) {
                if (story.mediaItem.type == MediaItem.MediaType.IMAGE) {
                    story.mediaItem.url.takeIf { it.isNotBlank() }?.let { urls += it }
                }
                story.profileImagePath?.takeIf { it.isNotBlank() }?.let { urls += it }
            }
        }
        val limited = urls.take(10)
        if (limited.isNotEmpty()) ImagePrefetchManager.prefetch(limited)
    }

    fun checkActiveStories(userId: String) {
        viewModelScope.launch {
            hasActiveStory = runCatching { storyRepository.hasActiveStories(userId) }.getOrDefault(false)
        }
    }

    fun sendMessage(toUserId: String, storyId: String, message: String, completion: (Result<Unit>) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null) {
            completion(Result.failure(IllegalStateException(localized(R.string.messaging_error_not_authenticated))))
            return
        }
        val trimmed = message.trim()
        if (trimmed.isEmpty()) {
            completion(Result.failure(IllegalArgumentException(localized(R.string.messaging_error_empty_message))))
            return
        }
        viewModelScope.launch {
            val storyReply = storyRepository.fetchStoryReplyData(toUserId, storyId)
            if (storyReply == null) {
                completion(Result.failure(IllegalStateException(localized(R.string.story_context_menu_action_failed))))
                return@launch
            }
            val interaction = MessageRequestInteractionContext(
                kind = MessageRequestInteractionContext.Kind.STORY_MESSAGE,
                storyId = storyId,
                storyOwnerId = toUserId,
            )
            val result = runCatching {
                when (val route = messageRequestService.resolveRoute(toUserId, interaction)) {
                    is DirectMessageRoute.OutgoingRequest -> {
                        messageRequestService.appendRequestMessage(
                            receiverId = toUserId,
                            text = "💬 $trimmed",
                            interaction = interaction,
                        )
                    }
                    is DirectMessageRoute.Conversation -> sendAcceptedStoryReply(
                        route.id, currentUserId, "💬 $trimmed", storyReply.payload,
                    )
                    is DirectMessageRoute.ConversationDraft -> {
                        val conversationId = messageRequestService.activateConversationDraft(toUserId, route.threadId)
                        sendAcceptedStoryReply(conversationId, currentUserId, "💬 $trimmed", storyReply.payload)
                    }
                    is DirectMessageRoute.IncomingRequest -> {
                        val accepted = messageRequestService.acceptIncomingThread(route.threadId)
                        sendAcceptedStoryReply(accepted.conversationId, currentUserId, "💬 $trimmed", storyReply.payload)
                    }
                }
            }.map { Unit }
            completion(result)
        }
    }

    private suspend fun sendAcceptedStoryReply(
        conversationId: String,
        senderId: String,
        content: String,
        storyReplyData: Map<String, String>,
    ) {
        val vanishActive = runCatching {
            firestore.db.collection("conversations").document(conversationId).get().await()
                .getBoolean("vanishModeActive") ?: false
        }.getOrDefault(false)
        ChatService.sendStoryReplyMessage(
            conversationId = conversationId,
            senderId = senderId,
            content = content,
            storyReplyData = storyReplyData,
            isVanishModeMessage = vanishActive,
        ).getOrThrow()
    }

    // MARK: - Vanish mode en respuestas de historia

    /** Consulta de solo lectura: nunca crea conversación por mirar una historia. */
    fun fetchVanishState(withAuthor: String, completion: (Boolean) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null || withAuthor.isEmpty() || withAuthor == currentUserId) {
            completion(false)
            return
        }
        vanishActiveWithAuthor[withAuthor]?.let {
            completion(it)
            return
        }
        viewModelScope.launch {
            val vanishActive = runCatching {
                val snap = firestore.db.collection("conversations")
                    .whereArrayContains("participants", currentUserId)
                    .get().await()
                val conversation = snap.documents.firstOrNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val participants = doc.get("participants") as? List<String> ?: emptyList()
                    withAuthor in participants
                }
                conversation?.getBoolean("vanishModeActive") ?: false
            }.getOrDefault(false)
            vanishActiveWithAuthor[withAuthor] = vanishActive
            completion(vanishActive)
        }
    }

    fun sendEphemeralMoment(
        toUserId: String,
        storyId: String,
        imageJpeg: ByteArray,
        completion: (Result<Unit>) -> Unit,
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null || imageJpeg.isEmpty()) {
            completion(Result.failure(IllegalStateException(localized(R.string.messaging_error_not_authenticated))))
            return
        }
        viewModelScope.launch {
            val storyReply = storyRepository.fetchStoryReplyData(toUserId, storyId)
            if (storyReply == null) {
                completion(Result.failure(IllegalStateException(localized(R.string.story_context_menu_action_failed))))
                return@launch
            }
            val interaction = MessageRequestInteractionContext(
                kind = MessageRequestInteractionContext.Kind.STORY_EPHEMERAL,
                storyId = storyId,
                storyOwnerId = toUserId,
            )
            val route = runCatching { messageRequestService.resolveRoute(toUserId, interaction) }
                .getOrElse { completion(Result.failure(it)); return@launch }
            if (route is DirectMessageRoute.OutgoingRequest) {
                completion(runCatching {
                    messageRequestService.appendEphemeralMedia(
                        receiverId = toUserId,
                        data = imageJpeg,
                        isVideo = false,
                        allowReplay = true,
                        interaction = interaction,
                        expiresAt = Date(Date().time + 24L * 60L * 60L * 1000L),
                    )
                }.map { Unit })
                return@launch
            }
            val conversationId = runCatching {
                when (route) {
                    is DirectMessageRoute.Conversation -> route.id
                    is DirectMessageRoute.ConversationDraft -> messageRequestService.activateConversationDraft(toUserId, route.threadId)
                    is DirectMessageRoute.IncomingRequest -> messageRequestService.acceptIncomingThread(route.threadId).conversationId
                    is DirectMessageRoute.OutgoingRequest -> error("unreachable")
                }
            }.getOrElse { completion(Result.failure(it)); return@launch }
            val messageId = UUID.randomUUID().toString()
            val upload = ChatService.uploadMedia(
                data = imageJpeg,
                type = MessageType.EPHEMERAL,
                conversationId = conversationId,
                messageId = messageId,
            ).getOrNull()
            if (upload == null) {
                completion(Result.failure(IllegalStateException(localized(R.string.messaging_error_service_unavailable))))
                return@launch
            }
            val result = ChatService.sendEphemeralMessage(
                conversationId = conversationId,
                senderId = currentUserId,
                content = localized(R.string.stories_ephemeral_reply_content),
                mediaUrl = upload.mediaUrl,
                mediaObjectPath = upload.mediaObjectPath,
                thumbnailUrl = upload.thumbnailUrl,
                thumbnailObjectPath = upload.thumbnailObjectPath,
                mediaEncryption = upload.mediaEncryption,
                thumbnailEncryption = upload.thumbnailEncryption,
                expirationHours = 24,
                storyReplyData = storyReply.payload,
                messageId = messageId,
            ).map { Unit }
            completion(result)
        }
    }

    fun fetchReactions(userId: String, storyId: String) {
        reactionListeners.remove(storyId)?.remove()
        reactionListeners[storyId] = storyRepository.observeReactions(userId, storyId) { reactions ->
            storyReactions = storyReactions + (storyId to reactions)
        }
    }

    fun stopObservingReactions(storyId: String) {
        reactionListeners.remove(storyId)?.remove()
    }

    fun fetchViewers(userId: String, storyId: String, completion: ((List<StoryViewer>) -> Unit)? = null) {
        if (storyId.isBlank()) {
            completion?.invoke(emptyList())
            return
        }
        viewModelScope.launch {
            val viewers = runCatching { storyRepository.fetchViewers(userId, storyId) }.getOrDefault(emptyList())
            storyViewers = storyViewers + (storyId to viewers)
            completion?.invoke(viewers)
        }
    }

    fun markStoryAsViewed(
        userId: String,
        storyId: String,
        storyTimestamp: Date? = null,
        audience: String? = null,
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (currentUserId == userId) return
        val shouldSyncRemoteView = !IncognitoModeService.isActiveSnapshot

        StorySeenStateService.markSeen(
            viewerId = currentUserId,
            authorId = userId,
            timestamp = storyTimestamp ?: Date(),
            syncRemote = shouldSyncRemoteView,
        )
        if (!shouldSyncRemoteView) return

        viewModelScope.launch {
            runCatching {
                val user = firestore.fetchUserProfile(currentUserId)
                storyRepository.markStoryAsViewed(authorId = userId, storyId = storyId, viewer = user)
                StoryRingCacheService.invalidate(viewerId = currentUserId, authorId = userId)
            }
        }
    }

    fun deleteStory(userId: String, storyId: String, completion: (Throwable?) -> Unit) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (currentUserId == null || currentUserId != userId) {
            completion(IllegalStateException(localized(R.string.stories_error_unauthorized_delete)))
            return
        }
        viewModelScope.launch {
            val err = runCatching {
                storyRepository.softDeleteStory(userId, storyId)
                val userStories = stories[userId].orEmpty().filterNot { it.id == storyId }
                stories = stories + (userId to userStories)
                LocalPersistenceService.deleteStory(storyId)
                firestore.rebuildStorySummary(userId)
                checkActiveStories(userId)
            }.exceptionOrNull()
            completion(err)
        }
    }

    fun permanentlyDeleteStory(userId: String, storyId: String, completion: (Throwable?) -> Unit) {
        viewModelScope.launch {
            completion(runCatching { storyRepository.permanentlyDeleteStory(userId, storyId) }.exceptionOrNull())
        }
    }

    fun restoreStory(userId: String, storyId: String, completion: (Throwable?) -> Unit) {
        viewModelScope.launch {
            val err = runCatching {
                storyRepository.restoreStory(userId, storyId)
                firestore.rebuildStorySummary(userId)
                checkActiveStories(userId)
            }.exceptionOrNull()
            completion(err)
        }
    }

    fun sendReaction(toUserId: String, storyId: String, reaction: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        viewModelScope.launch {
            runCatching {
                storyRepository.addReaction(toUserId, storyId, currentUserId, reaction)
                fetchReactions(toUserId, storyId)
                AffinityTracker.trackInteraction(AffinityInteractionType.STORY_REACTION, toUserId)
            }
        }
    }

    // MARK: - UserProfile

    fun fetchStoriesForUserProfile(userId: String, viewerId: String) {
        if (userId.isEmpty() || viewerId.isEmpty()) {
            stories = emptyMap()
            return
        }
        viewModelScope.launch {
            val userStories = runCatching { storyRepository.fetchActiveStories(userId) }.getOrNull()
            if (userStories.isNullOrEmpty()) {
                stories = emptyMap()
                return@launch
            }
            filterStoriesForUserProfile(userStories, userId, viewerId)
        }
    }

    private suspend fun filterStoriesForUserProfile(
        storiesList: List<Story>,
        userId: String,
        viewerId: String,
    ) {
        val visibleIds = storiesList.mapNotNull { story ->
            val id = story.id ?: return@mapNotNull null
            if (PrivacyService.canUserViewStoryEnhanced(story, viewerId)) id else null
        }.toSet()
        val ordered = storiesList.filter { it.id in visibleIds }
        if (ordered.isNotEmpty()) {
            stories = mapOf(userId to ordered)
            for (story in ordered) {
                val storyId = story.id ?: continue
                fetchReactions(userId, storyId)
                fetchViewers(userId, storyId)
            }
        } else {
            stories = emptyMap()
        }
        prefetchImages()
    }

    // MARK: - PRELOADING (delegado a StoryPlaybackCoordinator)

    fun preloadNextStory(currentStoryId: String, allStories: List<Story>) {
        playbackCoordinator.preloadNextStory(currentStoryId, allStories)
    }

    fun preloadStory(story: Story) {
        playbackCoordinator.preloadStory(story)
    }

    fun clearPreloadCache() {
        playbackCoordinator.clearPreloadCache()
    }

    fun getPreloadedStory(storyId: String): Story? = playbackCoordinator.getPreloadedStory(storyId)

    fun getPreloadedImage(storyId: String): Bitmap? = playbackCoordinator.getPreloadedImage(storyId)

    // MARK: - Android conveniences (StoriesView)

    /**
     * Carga el ring del feed: fija orden + [fetchStories] con conexiones.
     * ≡ `setRingNavigationOrder` + `fetchStories(includeConnections: true)` en StoriesView iOS.
     */
    fun load(ringNavigationUserIds: List<String>, startAtUserId: String? = null) {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid
        if (viewerId == null) {
            errorMessage = localized(R.string.stories_error_auth_required)
            return
        }
        val ordered = ringNavigationUserIds.filter { it.isNotEmpty() }.distinct()
        // ≡ iOS: solo lock si el feed pasó ring; vacío → fetchFollowing (no inventar [startAt, me])
        if (ordered.isNotEmpty()) {
            val withStart = if (!startAtUserId.isNullOrBlank() && startAtUserId !in ordered) {
                listOf(startAtUserId) + ordered
            } else {
                ordered
            }
            setRingNavigationOrder(withStart)
        } else {
            lockedRingNavigationOrder = emptyList()
        }
        fetchStories(forUserId = viewerId, includeConnections = true)
    }

    /**
     * Lista explícita (destacados / cadenas) — ≡ init `chainStories:` de StoriesView iOS.
     * Carril sintético = [CHAIN_RAIL_ID] (`__chain__`).
     */
    fun loadExplicitStories(storiesList: List<Story>, applyPrivacyFilter: Boolean = true) {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid
        if (viewerId == null) {
            errorMessage = localized(R.string.stories_error_auth_required)
            return
        }
        isLoading = true
        errorMessage = null
        viewModelScope.launch {
            val filtered = if (applyPrivacyFilter) {
                storiesList.filter { PrivacyService.canUserViewStoryEnhanced(it, viewerId) }
            } else {
                storiesList
            }
            // ≡ loadStories chain: sort by chainPosition, luego timestamp
            val visible = filtered.sortedWith(
                compareBy({ it.chainPosition ?: Int.MAX_VALUE }, { it.timestamp }),
            )
            stories = mapOf(CHAIN_RAIL_ID to visible)
            ringOrderedStoryUserIds = if (visible.isEmpty()) emptyList() else listOf(CHAIN_RAIL_ID)
            sortedStoryUserIds = ringOrderedStoryUserIds
            for (story in visible) {
                val storyId = story.id ?: continue
                // Firestore vive bajo users/{authorId}/stories — no bajo el carril sintético
                fetchReactions(story.authorId, storyId)
                fetchViewers(story.authorId, storyId)
            }
            isLoading = false
        }
    }

    /** Actualiza el carril de cadena tras borrar una parte (≡ handleStoryDeleted chain). */
    fun replaceExplicitRail(storiesList: List<Story>) {
        stories = mapOf(CHAIN_RAIL_ID to storiesList)
        ringOrderedStoryUserIds = if (storiesList.isEmpty()) emptyList() else listOf(CHAIN_RAIL_ID)
        sortedStoryUserIds = ringOrderedStoryUserIds
    }

    /** ≡ markStoryAsViewed al abrir la story actual en el viewer. */
    fun markCurrentSeen(story: Story) {
        val storyId = story.id ?: return
        markStoryAsViewed(
            userId = story.authorId,
            storyId = storyId,
            storyTimestamp = story.timestamp,
        )
    }

    /** ≡ hydrateStoryViewerContext de ArchiveDayStoriesViewer. */
    fun hydrateStoriesForAuthor(authorId: String, list: List<Story>) {
        if (authorId.isBlank()) return
        stories = stories + (authorId to list)
        if (authorId !in sortedStoryUserIds) {
            sortedStoryUserIds = sortedStoryUserIds + authorId
        }
    }

    fun storiesFor(userId: String): List<Story> = stories[userId].orEmpty()

    override fun onCleared() {
        reactionListeners.values.forEach { it.remove() }
        reactionListeners.clear()
        authorReelJobs.values.forEach { it.cancel() }
        authorReelJobs.clear()
        playbackCoordinator.close()
        super.onCleared()
    }

    companion object {
        /** ≡ `chainModeUserId` de StoriesView.swift. */
        const val CHAIN_RAIL_ID = "__chain__"

        /** Alias legacy; preferir [CHAIN_RAIL_ID]. */
        @Deprecated("Use CHAIN_RAIL_ID", ReplaceWith("CHAIN_RAIL_ID"))
        const val EXPLICIT_RAIL_ID = CHAIN_RAIL_ID
    }
}
