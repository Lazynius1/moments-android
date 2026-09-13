package com.moments.android.services.persistence

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.models.DeleteMomentPayload
import com.moments.android.models.encode
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.models.FollowRequestActionPayload
import com.moments.android.models.MarkAsReadPayload
import com.moments.android.models.MediaItem
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageSyncCursor
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.models.Moment
import com.moments.android.models.MomentsNotification
import com.moments.android.models.ProfileUpdatePayload
import com.moments.android.models.ReportActionPayload
import com.moments.android.models.Story
import com.moments.android.models.cache.CachedAction
import com.moments.android.models.cache.CachedConnection
import com.moments.android.models.cache.CachedConversation
import com.moments.android.models.cache.CachedMoment
import com.moments.android.models.cache.CachedNotification
import com.moments.android.models.cache.CachedSearch
import com.moments.android.models.cache.CachedStory
import com.moments.android.models.cache.CachedUser
import com.moments.android.views.messaging.core.decodeMessages
import com.moments.android.views.messaging.core.encodeMessages
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.services.network.OfflineSyncService
import com.moments.android.services.persistence.room.ConnectionEntity
import com.moments.android.services.persistence.room.ConversationEntity
import com.moments.android.services.persistence.room.MomentEntity
import com.moments.android.services.persistence.room.MomentsRoomStore
import com.moments.android.services.persistence.room.NotificationEntity
import com.moments.android.services.persistence.room.PendingActionEntity
import com.moments.android.services.persistence.room.SearchEntity
import com.moments.android.services.persistence.room.StoryEntity
import com.moments.android.services.persistence.room.UserEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.UUID

/**
 * Persistencia local con Room.
 * Port de LocalPersistenceService.swift — StorySeenStateService en archivo aparte (mismo Swift).
 * Topes: feed 100, explore 50, users 200, conversations 50, notifs 100, searches 20, msgs/chat 2000.
 * Los payloads complejos conservan su codificación JSON dentro de BLOBs o
 * texto de Room; las claves de acceso viven en columnas indexadas.
 */
object LocalPersistenceService {

    private const val PREFS = "moments_local_persistence"
    private const val KEY_ROOM_CACHE_CUTOVER = "room_cache_cutover_v1"

    private const val MAX_CONVERSATIONS = 50
    private const val MAX_NOTIFICATIONS = 100

    private const val MAX_FEED_MOMENTS = 100
    private const val MAX_EXPLORE_MOMENTS = 50
    private const val MAX_CACHED_USERS = 200
    private const val MAX_DATA_AGE_DAYS = 7
    private const val MAX_SEARCHES = 20
    private const val MAX_MESSAGES_PER_CHAT = 2000

    private const val RECENT_CHAT_WINDOW_SIZE = 20
    private const val STALE_CHAT_WINDOW_SIZE = 6
    private const val STALE_CHAT_THRESHOLD_DAYS = 45

    @Volatile private var appContext: Context? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val actionsMutex = Mutex()
    private val connectionsMutex = Mutex()
    private val conversationsMutex = Mutex()
    private val notificationsMutex = Mutex()
    private val searchesMutex = Mutex()
    private val storiesMutex = Mutex()
    private val momentsMutex = Mutex()

    class ActionPersistenceException(message: String) : Exception(message)

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            clearLegacyCacheForRoomCutover(context.applicationContext)
            MomentsRoomStore.initialize(context)
            MessagePersistenceStore.initialize(context)
            StorySeenStateService.initialize(context)
        }
    }

    /**
     * Android aún no tenía usuarios a los que migrar una caché activa. Hacemos
     * el corte una sola vez: el contenido remoto se hidrata de nuevo y no se
     * conservan acciones que solo existieran en la instalación anterior.
     */
    private fun clearLegacyCacheForRoomCutover(context: Context) {
        val legacyPrefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (legacyPrefs.getBoolean(KEY_ROOM_CACHE_CUTOVER, false)) return
        File(context.filesDir, "local_cache").deleteRecursively()
        File(context.filesDir, "message_cache").deleteRecursively()
        legacyPrefs.edit().clear().putBoolean(KEY_ROOM_CACHE_CUTOVER, true).commit()
        context.getSharedPreferences("moments_story_seen", Context.MODE_PRIVATE).edit().clear().commit()
    }

    // MARK: - Current user

    suspend fun loadCurrentUserAsync(): AppUser? = withContext(Dispatchers.IO) {
        MomentsRoomStore.dao().currentUser()?.payload
            ?.let(CachedUser::decodeFromPrefsJson)
            ?.toAppUser()
    }

    // MARK: - Users

    suspend fun loadUserAsync(userId: String): AppUser? = withContext(Dispatchers.IO) {
        MomentsRoomStore.dao().usersById(userId)
            .firstNotNullOfOrNull { it.payload.let(CachedUser::decodeFromPrefsJson)?.toAppUser() }
    }

    suspend fun saveCurrentUserAsync(user: AppUser) = saveUserAsync(user, section = "currentUser")

    suspend fun saveUserAsync(user: AppUser, section: String = "profile") = withContext(Dispatchers.IO) {
        val cached = CachedUser.from(user, section)
        MomentsRoomStore.dao().upsertUsers(
            listOf(UserEntity(cached.userId, cached.cacheSection, cached.lastSyncedAt.time, cached.encodeToPrefsJson())),
        )
        val entries = MomentsRoomStore.dao().cachedNonCurrentUsers()
        if (entries.size > MAX_CACHED_USERS) {
            entries.sortedBy { it.lastSyncedAt }
                .take(entries.size - MAX_CACHED_USERS)
                .forEach { MomentsRoomStore.dao().deleteUser(it.userId, it.cacheSection) }
        }
    }

    suspend fun clearCurrentUserAsync() = withContext(Dispatchers.IO) {
        MomentsRoomStore.dao().currentUser()?.let { MomentsRoomStore.dao().deleteUser(it.userId, it.cacheSection) }
    }

    // MARK: - Outbox / CachedAction

    suspend fun saveActionOrThrowAsync(action: CachedAction) = withContext(Dispatchers.IO) {
        if (appContext == null) throw ActionPersistenceException("Local persistence store is unavailable.")
        actionsMutex.withLock {
            val actions = loadActionsFromRoom()
            if (actions.none { it.id == action.id }) writeActionsToRoom(actions + action)
        }
    }

    suspend fun saveActionAsync(action: CachedAction) {
        runCatching { saveActionOrThrowAsync(action) }.onFailure { return }
        val isUpload = action.type == CachedAction.ActionType.MOMENT_UPLOAD.raw ||
            action.type == CachedAction.ActionType.STORY_UPLOAD.raw
        if (NetworkMonitor.isConnected && !isUpload) {
            ioScope.launch { OfflineSyncService.syncPendingActions() }
        }
    }

    suspend fun loadPendingActionsAsync(): List<CachedAction> = withContext(Dispatchers.IO) {
        loadActionsFromRoom().filter {
            it.status == CachedAction.ActionStatus.PENDING.raw ||
                it.status == CachedAction.ActionStatus.EXECUTING.raw
        }.sortedBy { it.createdAt }
    }

    suspend fun loadActionAsync(id: String): CachedAction? = withContext(Dispatchers.IO) {
        loadActionsFromRoom().find { it.id == id }
    }

    suspend fun hasPendingActionAsync(id: String): Boolean = withContext(Dispatchers.IO) {
        loadActionsFromRoom().any { it.id == id }
    }

    suspend fun deleteActionAsync(id: String) = mutateActionsAsync { actions -> actions.filter { it.id != id } }

    suspend fun markActionAttemptAsync(id: String) = mutateActionsAsync { actions ->
        actions.map { action ->
            if (action.id == id) action.copy(retryCount = action.retryCount + 1, lastAttemptAt = Date()) else action
        }
    }

    suspend fun updateActionStatusAsync(
        id: String,
        status: CachedAction.ActionStatus,
        error: String? = null,
    ) = mutateActionsAsync { actions ->
        actions.map { action ->
            if (action.id != id) action else action.copy(
                status = status.raw,
                lastError = error,
                retryCount = if (status == CachedAction.ActionStatus.FAILED) action.retryCount + 1 else action.retryCount,
            )
        }
    }

    suspend fun updateActionPayloadAsync(id: String, payloadData: ByteArray) = mutateActionsAsync { actions ->
        actions.map { if (it.id == id) it.copy(payloadData = payloadData) else it }
    }

    private suspend fun mutateActionsAsync(transform: (List<CachedAction>) -> List<CachedAction>) =
        withContext(Dispatchers.IO) {
            actionsMutex.withLock { writeActionsToRoom(transform(loadActionsFromRoom())) }
        }

    private suspend fun loadActionsFromRoom(): List<CachedAction> = MomentsRoomStore.dao().allActions().map {
        CachedAction(
            id = it.id,
            type = it.type,
            status = it.status,
            payloadData = it.payload,
            createdAt = Date(it.createdAt),
            retryCount = it.retryCount,
            lastError = it.lastError,
            lastAttemptAt = it.lastAttemptAt?.let(::Date),
        )
    }

    private suspend fun writeActionsToRoom(actions: List<CachedAction>) {
        MomentsRoomStore.dao().replaceActions(actions.map {
            PendingActionEntity(
                id = it.id,
                type = it.type,
                status = it.status,
                payload = it.payloadData,
                createdAt = it.createdAt.time,
                retryCount = it.retryCount,
                lastError = it.lastError,
                lastAttemptAt = it.lastAttemptAt?.time,
            )
        })
    }

    suspend fun updateCachedMessageStatusAsync(conversationId: String, messageId: String, status: MessageStatus) {
        MessagePersistenceStore.updateMessageStatus(conversationId, messageId, status.raw)
    }

    // MARK: - Connections (followers / following / mutuals)

    suspend fun loadConnectionsAsync(userId: String): Triple<List<AppUser>, List<AppUser>, List<AppUser>> =
        withContext(Dispatchers.IO) {
            val records = MomentsRoomStore.dao().connectionsFor(userId)
            val usersById = records.map { it.targetId }.distinct().associateWith { targetId ->
                MomentsRoomStore.dao().usersById(targetId)
                    .firstNotNullOfOrNull { it.payload.let(CachedUser::decodeFromPrefsJson)?.toAppUser() }
            }
            val followers = mutableListOf<AppUser>()
            val following = mutableListOf<AppUser>()
            val mutuals = mutableListOf<AppUser>()
            records.forEach { record ->
                val user = usersById[record.targetId] ?: return@forEach
                when (record.type) {
                    "follower" -> followers += user
                    "mutual" -> mutuals += user
                    else -> following += user
                }
            }
            Triple(followers, following, mutuals)
        }

    suspend fun saveFollowersAsync(userId: String, followers: List<AppUser>) =
        saveConnectionListAsync(userId, followers, "follower")

    suspend fun saveFollowingAsync(userId: String, following: List<AppUser>) =
        saveConnectionListAsync(userId, following, "following")

    suspend fun saveMutualsAsync(userId: String, mutuals: List<AppUser>) =
        saveConnectionListAsync(userId, mutuals, "mutual")

    suspend fun isFollowingAsync(targetUserId: String): Boolean = withContext(Dispatchers.IO) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@withContext false
        MomentsRoomStore.dao().connectionsFor(currentUserId).any {
            it.targetId == targetUserId && it.type == "following"
        }
    }

    suspend fun cachedFollowRelationshipAsync(
        viewerId: String,
        targetUserId: String,
    ): Pair<Boolean, Boolean> = withContext(Dispatchers.IO) {
        val matching = MomentsRoomStore.dao().connectionsFor(viewerId).filter { it.targetId == targetUserId }
        Pair(
            matching.any { it.type == "following" },
            matching.any { it.type == "mutual" },
        )
    }

    suspend fun updateCachedFollowRelationshipAsync(
        viewerId: String,
        targetUserId: String,
        isFollowing: Boolean,
        isMutual: Boolean,
    ) = withContext(Dispatchers.IO) {
        connectionsMutex.withLock {
            val connections = MomentsRoomStore.dao().connectionsFor(viewerId).toMutableList()
            connections.removeAll {
                it.targetId == targetUserId && (it.type == "following" || it.type == "mutual")
            }
            val now = Date().time
            if (isFollowing) connections += ConnectionEntity(viewerId, targetUserId, "following", now)
            if (isMutual) connections += ConnectionEntity(viewerId, targetUserId, "mutual", now)
            MomentsRoomStore.dao().replaceConnectionsFor(viewerId, connections)
        }
    }

    suspend fun toggleFollowLocallyAsync(
        currentUserId: String,
        targetUserId: String,
        isFollow: Boolean,
    ) = withContext(Dispatchers.IO) {
        connectionsMutex.withLock {
            val connections = MomentsRoomStore.dao().connectionsFor(currentUserId).toMutableList()
            connections.removeAll { it.targetId == targetUserId && it.type == "following" }
            if (isFollow) {
                connections += ConnectionEntity(currentUserId, targetUserId, "following", Date().time)
            }
            MomentsRoomStore.dao().replaceConnectionsFor(currentUserId, connections)
        }
    }

    private suspend fun saveConnectionListAsync(userId: String, users: List<AppUser>, type: String) =
        withContext(Dispatchers.IO) {
            connectionsMutex.withLock {
                val existing = MomentsRoomStore.dao().connectionsFor(userId)
                val updated = existing.filter { it.type != type } + users.map {
                    ConnectionEntity(userId, it.id, type, Date().time)
                }
                MomentsRoomStore.dao().replaceConnectionsFor(userId, updated)
                if (users.isNotEmpty()) {
                    MomentsRoomStore.dao().upsertUsers(users.map {
                        val cached = CachedUser.from(it, "profile")
                        UserEntity(cached.userId, cached.cacheSection, cached.lastSyncedAt.time, cached.encodeToPrefsJson())
                    })
                }
            }
        }

    // MARK: - Moments cache

    suspend fun loadFeedMomentsAsync(): List<Moment> = loadMomentsAsync("feed", MAX_FEED_MOMENTS)

    suspend fun loadExploreMomentsAsync(): List<Moment> = loadMomentsAsync("explore", MAX_EXPLORE_MOMENTS)

    suspend fun loadProfileMomentsAsync(userId: String, viewerId: String? = null): List<Moment> =
        loadMomentsAsync(profileMomentsSection(userId, viewerId), maxCount = 50)

    suspend fun saveFeedMomentsAsync(moments: List<Moment>, sync: Boolean = false) =
        saveMomentsAsync(moments, "feed", sync, MAX_FEED_MOMENTS)

    suspend fun saveExploreMomentsAsync(moments: List<Moment>, sync: Boolean = false) =
        saveMomentsAsync(moments, "explore", sync, MAX_EXPLORE_MOMENTS)

    suspend fun saveProfileMomentsAsync(
        moments: List<Moment>,
        userId: String,
        viewerId: String? = null,
        sync: Boolean = true,
    ) = saveMomentsAsync(moments, profileMomentsSection(userId, viewerId), sync, 50)

    private fun profileMomentsSection(userId: String, viewerId: String?): String =
        if (!viewerId.isNullOrEmpty()) "profile_${viewerId}_$userId" else "profile_$userId"

    private suspend fun loadMomentsAsync(section: String, maxCount: Int): List<Moment> =
        withContext(Dispatchers.IO) {
            MomentsRoomStore.dao().momentsIn(section)
                .mapNotNull { entity ->
                    runCatching { decodeCachedMoment(JSONObject(entity.payload)) }.getOrNull()
                }
                .sortedByDescending { it.timestamp.time }
                .take(maxCount)
                .mapNotNull { it.toMoment() }
        }

    private suspend fun saveMomentsAsync(
        moments: List<Moment>,
        section: String,
        sync: Boolean,
        maxCount: Int,
    ) = withContext(Dispatchers.IO) {
        momentsMutex.withLock {
            val existing = if (sync) emptyList() else MomentsRoomStore.dao().momentsIn(section).mapNotNull { entity ->
                runCatching { decodeCachedMoment(JSONObject(entity.payload)) }.getOrNull()
            }
            val merged = existing.associateBy { it.momentId }.toMutableMap()
            moments.forEach { moment ->
                val id = moment.id ?: return@forEach
                merged[id] = CachedMoment.from(moment, section)
            }
            val rows = merged.values.sortedByDescending { it.timestamp.time }.take(maxCount).map {
                MomentEntity(it.momentId, it.feedSection, it.timestamp.time, it.lastSyncedAt.time, encodeCachedMoment(it).toString())
            }
            MomentsRoomStore.dao().replaceMomentsIn(section, rows)
        }
    }

    // MARK: - Stories cache

    suspend fun loadStoriesAsync(userId: String): List<Story> = withContext(Dispatchers.IO) {
        val now = Date()
        MomentsRoomStore.dao().storiesByAuthor(userId)
            .mapNotNull { entity -> runCatching { decodeCachedStory(JSONObject(entity.payload)) }.getOrNull() }
            .filter { it.expirationDate.after(now) }
            .sortedBy { it.timestamp.time }
            .map { it.toStory() }
    }

    suspend fun saveStoriesAsync(stories: List<Story>, sync: Boolean = false) = withContext(Dispatchers.IO) {
        storiesMutex.withLock {
            val existing = if (sync) emptyList() else MomentsRoomStore.dao().allStories().mapNotNull { entity ->
                runCatching { decodeCachedStory(JSONObject(entity.payload)) }.getOrNull()
            }
            val merged = existing.associateBy { it.id }.toMutableMap()
            stories.forEach { story ->
                val id = story.id ?: return@forEach
                CachedStory.fromStory(story)?.let { merged[id] = it }
            }
            MomentsRoomStore.dao().replaceStories(merged.values.map {
                StoryEntity(it.id, it.authorId, it.timestamp.time, it.expirationDate.time, it.cachedAt.time, encodeCachedStory(it).toString())
            })
        }
    }

    suspend fun deleteStoryAsync(storyId: String) = withContext(Dispatchers.IO) {
        storiesMutex.withLock { MomentsRoomStore.dao().deleteStory(storyId) }
    }

    suspend fun deleteStoriesAsync(userId: String) = withContext(Dispatchers.IO) {
        storiesMutex.withLock { MomentsRoomStore.dao().deleteStoriesByAuthor(userId) }
    }

    suspend fun cleanupOldStoriesAsync() = withContext(Dispatchers.IO) {
        storiesMutex.withLock { MomentsRoomStore.dao().deleteExpiredStories(Date().time) }
    }

    // MARK: - Optimistic local updates

    suspend fun deleteMomentAsync(momentId: String) = withContext(Dispatchers.IO) {
        momentsMutex.withLock { MomentsRoomStore.dao().deleteMoment(momentId) }
    }

    suspend fun toggleMomentReactionLocallyAsync(momentId: String, reaction: String, userId: String) =
        updateMomentsAcrossSectionsAsync(momentId) { cached ->
            val reactions = CachedMoment.decodeReactions(cached.reactionsData).toMutableMap()
            val users = reactions.getOrPut(reaction) { mutableListOf() }.toMutableList()
            if (users.contains(userId)) users.remove(userId) else users.add(userId)
            if (users.isEmpty()) reactions.remove(reaction) else reactions[reaction] = users
            cached.copy(reactionsData = CachedMoment.encodeReactions(reactions), lastSyncedAt = Date())
        }

    suspend fun updateCommentCountLocallyAsync(momentId: String, increment: Int) =
        updateMomentsAcrossSectionsAsync(momentId) { cached ->
            val current = cached.commentCount ?: 0
            cached.copy(commentCount = maxOf(0, current + increment), lastSyncedAt = Date())
        }

    suspend fun deleteMoment(
        momentId: String,
        userId: String,
        imagePath: String?,
        videoUrl: String?,
    ) {
        deleteMomentAsync(momentId)
        val payload = DeleteMomentPayload(
            momentId = momentId,
            userId = userId,
            imagePath = imagePath,
            videoUrl = videoUrl,
        )
        saveActionAsync(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.DELETE_MOMENT.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    // MARK: - Outbox UI helpers

    suspend fun updateProfile(
        userId: String,
        bio: String?,
        oldBio: String? = null,
        website: String? = null,
        oldWebsite: String? = null,
        interests: List<String>? = null,
        profileImageLocalPath: String? = null,
    ) {
        var actualOldBio = oldBio
        var actualOldWebsite = oldWebsite
        val existing = loadUserAsync(userId)
        if (existing != null) {
            if (actualOldBio == null) actualOldBio = existing.bio
            if (actualOldWebsite == null) actualOldWebsite = existing.websiteUrl
            saveUserAsync(
                existing.copy(
                    bio = bio ?: existing.bio,
                    websiteUrl = website ?: existing.websiteUrl,
                    interests = interests ?: existing.interests,
                    profileImagePath = profileImageLocalPath ?: existing.profileImagePath,
                ),
            )
        }
        val payload = ProfileUpdatePayload(
            userId = userId,
            bio = bio,
            oldBio = actualOldBio,
            websiteUrl = website,
            oldWebsiteUrl = actualOldWebsite,
            interests = interests,
            profileImageLocalPath = profileImageLocalPath,
            isImageUpdate = profileImageLocalPath != null,
        )
        saveActionAsync(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.UPDATE_PROFILE.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    suspend fun acceptFollowRequest(notificationId: String, senderId: String, recipientId: String) {
        markNotificationPendingAsync(notificationId, pending = false)
        val payload = FollowRequestActionPayload(
            notificationId = notificationId,
            senderId = senderId,
            recipientId = recipientId,
            isAccept = true,
        )
        saveActionAsync(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.ACCEPT_FOLLOW_REQUEST.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    suspend fun rejectFollowRequest(notificationId: String, senderId: String, recipientId: String) {
        deleteNotificationsAsync(listOf(notificationId))
        val payload = FollowRequestActionPayload(
            notificationId = notificationId,
            senderId = senderId,
            recipientId = recipientId,
            isAccept = false,
        )
        saveActionAsync(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.REJECT_FOLLOW_REQUEST.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    suspend fun reportContent(
        reporterId: String,
        reportedUserId: String,
        reportedContentType: String,
        reportedContentId: String,
        category: String,
        description: String,
        priority: String,
    ) {
        val payload = ReportActionPayload(
            reporterId = reporterId,
            reportedUserId = reportedUserId,
            reportedContentType = reportedContentType,
            reportedContentId = reportedContentId,
            category = category,
            description = description,
            priority = priority,
        )
        saveActionAsync(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.REPORT_CONTENT.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    suspend fun markNotificationAsRead(notificationId: String, userId: String) {
        markNotificationPendingAsync(notificationId, pending = false)
        val payload = MarkAsReadPayload(notificationId = notificationId, userId = userId)
        saveActionAsync(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.MARK_AS_READ.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    // MARK: - Search history

    suspend fun loadRecentSearchesAsync(): List<CachedSearch> = withContext(Dispatchers.IO) {
        MomentsRoomStore.dao().allSearches().map {
            CachedSearch(it.query, it.type, it.targetId, Date(it.timestamp))
        }
    }

    suspend fun saveSearchAsync(query: String, type: String, targetId: String? = null) =
        withContext(Dispatchers.IO) {
            searchesMutex.withLock {
                val search = CachedSearch(query, type, targetId)
                val list = MomentsRoomStore.dao().allSearches().map {
                    CachedSearch(it.query, it.type, it.targetId, Date(it.timestamp))
                }.filter { it.id != search.id }.toMutableList()
                list.add(0, search)
                MomentsRoomStore.dao().replaceSearches(
                    list.take(MAX_SEARCHES).map { SearchEntity(it.id, it.query, it.type, it.targetId, it.timestamp.time) },
                )
            }
        }

    suspend fun deleteSearchAsync(id: String) = withContext(Dispatchers.IO) {
        searchesMutex.withLock {
            val remaining = MomentsRoomStore.dao().allSearches().filter { entity ->
                CachedSearch(entity.query, entity.type, entity.targetId, Date(entity.timestamp)).id != id
            }
            MomentsRoomStore.dao().replaceSearches(remaining)
        }
    }

    suspend fun clearSearchHistoryAsync() = withContext(Dispatchers.IO) {
        searchesMutex.withLock { MomentsRoomStore.dao().deleteAllSearches() }
    }

    // MARK: - Messaging cache (delegated)
    suspend fun loadConversationsAsync(): List<Conversation> = withContext(Dispatchers.IO) {
        MomentsRoomStore.dao().allConversations()
            .mapNotNull { entity ->
                runCatching { decodeCachedConversation(JSONObject(entity.payload)) }.getOrNull()
            }
            .sortedWith(compareByDescending<CachedConversation> { it.isPinned }.thenByDescending { it.timestamp })
            .map { it.toConversation() }
    }

    suspend fun saveConversationsAsync(conversations: List<Conversation>, sync: Boolean = false) =
        withContext(Dispatchers.IO) {
            conversationsMutex.withLock {
                val existing = if (sync) emptyList() else MomentsRoomStore.dao().allConversations().mapNotNull { entity ->
                    runCatching { decodeCachedConversation(JSONObject(entity.payload)) }.getOrNull()
                }
                val merged = existing.associateBy { it.id }.toMutableMap()
                conversations.forEach { conversation ->
                    val id = conversation.id ?: return@forEach
                    val incoming = CachedConversation.from(conversation)
                    merged[id] = merged[id]?.let { mergeCachedConversation(it, incoming) } ?: incoming
                }
                MomentsRoomStore.dao().replaceConversations(
                    merged.values.sortedWith(
                        compareByDescending<CachedConversation> { it.isPinned }.thenByDescending { it.timestamp },
                    ).take(MAX_CONVERSATIONS).map {
                        ConversationEntity(it.id, it.isPinned, it.timestamp.time, it.lastSyncedAt.time, encodeCachedConversation(it).toString())
                    },
                )
            }
        }

    suspend fun saveMessagesAsync(messages: List<EnhancedMessage>, conversationId: String, sync: Boolean = false) {
        if (messages.isEmpty() && !sync) return
        val warmed = warmDiskMediaURLs(messages)
        MessagePersistenceStore.save(encodeMessages(warmed), conversationId, sync)
    }

    suspend fun appendMessagesAsync(messages: List<EnhancedMessage>, conversationId: String) {
        if (messages.isEmpty()) return
        saveMessagesAsync(messages, conversationId, sync = false)
    }

    suspend fun reconcileMessagesAsync(messages: List<EnhancedMessage>, conversationId: String) {
        if (messages.isEmpty()) return
        val warmed = warmDiskMediaURLs(messages)
        MessagePersistenceStore.reconcile(encodeMessages(warmed), conversationId)
    }

    suspend fun messageExistsAsync(conversationId: String, messageId: String): Boolean =
        MessagePersistenceStore.containsMessage(conversationId, messageId)

    suspend fun lastMessageSyncCursorAsync(conversationId: String): MessageSyncCursor? =
        MessagePersistenceStore.lastCursor(conversationId)

    suspend fun lastMessageTimestampAsync(conversationId: String): Date? =
        lastMessageSyncCursorAsync(conversationId)?.timestamp

    suspend fun loadMessagesFastAsync(conversationId: String): List<EnhancedMessage> =
        MessagePersistenceStore.allMessages(conversationId)
            .let { decodeMessages(it) }
            .sortedWith(compareBy<EnhancedMessage> { it.timestamp }.thenBy { it.id })

    suspend fun loadRecentMessagesFastAsync(
        conversationId: String,
        limit: Int,
        cutoffDate: Date? = null,
    ): List<EnhancedMessage> {
        if (limit <= 0) return emptyList()
        return MessagePersistenceStore.recentMessages(conversationId, limit, cutoffDate).let { encoded ->
            if (encoded.isEmpty()) emptyList() else decodeMessages(encoded)
        }
    }

    suspend fun loadMessagesBeforeAsync(
        conversationId: String,
        cursor: MessageSyncCursor,
        cutoffDate: Date? = null,
        limit: Int,
    ): List<EnhancedMessage> {
        if (limit <= 0) return emptyList()
        return MessagePersistenceStore.messagesBefore(conversationId, cursor, cutoffDate, limit).let { encoded ->
            if (encoded.isEmpty()) emptyList() else decodeMessages(encoded)
        }
    }

    suspend fun loadMessagesAfterAsync(
        conversationId: String,
        cursor: MessageSyncCursor,
        cutoffDate: Date? = null,
        limit: Int,
    ): List<EnhancedMessage> {
        if (limit <= 0) return emptyList()
        return MessagePersistenceStore.messagesAfter(conversationId, cursor, cutoffDate, limit).let { encoded ->
            if (encoded.isEmpty()) emptyList() else decodeMessages(encoded)
        }
    }

    suspend fun searchMessageIdsAsync(conversationId: String, query: String, limit: Int = 100): List<String> =
        MessagePersistenceStore.searchMessageIds(conversationId, query, limit)

    suspend fun searchMessagesGloballyAsync(query: String, limit: Int = 50): List<EnhancedMessage> =
        MessagePersistenceStore.searchMessagesGlobally(query, limit)

    suspend fun markMessageDeletedForEveryoneAsync(conversationId: String, messageId: String) {
        MessagePersistenceStore.markMessageDeletedForEveryone(conversationId, messageId)
    }

    suspend fun removeCachedMessageAsync(conversationId: String, messageId: String) {
        MessagePersistenceStore.removeCachedMessage(conversationId, messageId)
    }

    suspend fun markVanishMessagesDismissedAsync(conversationId: String, messageIds: List<String>, userId: String) {
        if (messageIds.isEmpty()) return
        MessagePersistenceStore.markVanishMessagesDismissed(conversationId, messageIds.toSet(), userId)
    }

    suspend fun updateMessageVanishExpiresAtAsync(conversationId: String, messageId: String, expiresAt: Date) {
        MessagePersistenceStore.updateMessageVanishExpiresAt(conversationId, messageId, expiresAt)
    }

    suspend fun updateMessageNoticeContentAsync(conversationId: String, messageId: String, content: String) {
        MessagePersistenceStore.updateMessageNoticeContent(conversationId, messageId, content)
    }

    suspend fun toggleMessageReactionLocallyAsync(messageId: String, emoji: String, userId: String) {
        MessagePersistenceStore.toggleMessageReactionLocally(messageId, emoji, userId)
    }

    suspend fun unreadMessageCountAsync(
        conversationId: String,
        currentUserId: String,
        lastReadAt: Date? = null,
    ): Int = MessagePersistenceStore.unreadMessageCount(conversationId, currentUserId, lastReadAt)

    fun warmDiskMediaURLs(messages: List<EnhancedMessage>): List<EnhancedMessage> =
        messages.map { msg ->
            val warm = applyDiskWarm(msg)
            if (!warm.changed) msg
            else msg.copy(
                mediaUrl = warm.mediaUrl ?: msg.mediaUrl,
                thumbnailUrl = warm.thumbnailUrl ?: msg.thumbnailUrl,
            )
        }

    fun scheduleWarmDiskMediaURLs(
        conversationId: String,
        onUpdated: (List<EnhancedMessage>) -> Unit,
    ) {
        ioScope.launch {
            val loaded = loadMessagesFastAsync(conversationId)
            if (loaded.isEmpty()) return@launch
            val relinked = mutableListOf<EnhancedMessage>()
            val results = loaded.map { msg ->
                val warm = applyDiskWarm(msg)
                if (!warm.changed) msg
                else {
                    val updated = msg.copy(
                        mediaUrl = warm.mediaUrl ?: msg.mediaUrl,
                        thumbnailUrl = warm.thumbnailUrl ?: msg.thumbnailUrl,
                    )
                    relinked += updated
                    updated
                }
            }
            if (relinked.isNotEmpty()) {
                saveMessagesAsync(relinked, conversationId, sync = false)
            }
            onUpdated(results)
        }
    }

    suspend fun loadMessagesInBackground(conversationId: String): List<EnhancedMessage> {
        val encoded = MessagePersistenceStore.allMessages(conversationId)
        if (encoded.isEmpty()) return emptyList()
        return decodeMessages(encoded)
    }

    suspend fun loadMessagesAsync(conversationId: String): List<EnhancedMessage> {
        val results = loadMessagesFastAsync(conversationId)
        if (results.isEmpty()) return emptyList()
        var relinked = false
        val warmed = results.map { msg ->
            val warm = applyDiskWarm(msg)
            if (warm.changed) {
                relinked = true
                msg.copy(mediaUrl = warm.mediaUrl ?: msg.mediaUrl, thumbnailUrl = warm.thumbnailUrl ?: msg.thumbnailUrl)
            } else msg
        }
        if (relinked) saveMessagesAsync(warmed, conversationId, sync = false)
        return warmed
    }

    suspend fun loadRecentMessagesInBackground(
        conversationId: String,
        limit: Int,
        cutoffDate: Date? = null,
    ): List<EnhancedMessage> {
        val encoded = MessagePersistenceStore.recentMessages(conversationId, limit, cutoffDate)
        if (encoded.isEmpty()) return emptyList()
        return decodeMessages(encoded)
    }

    suspend fun loadMessagesBeforeInBackground(
        conversationId: String,
        cursor: MessageSyncCursor,
        cutoffDate: Date? = null,
        limit: Int,
    ): List<EnhancedMessage> {
        val encoded = MessagePersistenceStore.messagesBefore(conversationId, cursor, cutoffDate, limit)
        if (encoded.isEmpty()) return emptyList()
        return decodeMessages(encoded)
    }

    suspend fun loadMessagesAfterInBackground(
        conversationId: String,
        cursor: MessageSyncCursor,
        cutoffDate: Date? = null,
        limit: Int,
    ): List<EnhancedMessage> {
        val encoded = MessagePersistenceStore.messagesAfter(conversationId, cursor, cutoffDate, limit)
        if (encoded.isEmpty()) return emptyList()
        return decodeMessages(encoded)
    }

    suspend fun markMessagesAsReadAsync(conversationId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        MessagePersistenceStore.markMessagesAsRead(conversationId, messageIds.toSet())
    }

    suspend fun markConversationReadLocallyAsync(conversationId: String, currentUserId: String) {
        val conversations = loadConversationsAsync().toMutableList()
        val index = conversations.indexOfFirst { it.id == conversationId }
        if (index >= 0) {
            val cached = conversations[index]
            val readStatus = cached.readStatus.toMutableMap()
            if (readStatus[currentUserId] != true) {
                readStatus[currentUserId] = true
                conversations[index] = cached.copy(readStatus = readStatus)
                saveConversationsAsync(conversations, sync = true)
            }
        }
        MessagePersistenceStore.markAllIncomingAsRead(conversationId, currentUserId)
    }

    suspend fun deleteConversationCacheAsync(conversationId: String) {
        val messageIds = loadMessagesAsync(conversationId).map { it.id }
        ChatCacheStore.deleteConversation(conversationId, messageIds)
        MessagePersistenceStore.deleteConversation(conversationId)
        val remaining = loadConversationsAsync().filter { it.id != conversationId }
        saveConversationsAsync(remaining, sync = true)
    }

    suspend fun loadNotificationsAsync(): List<MomentsNotification> = withContext(Dispatchers.IO) {
        MomentsRoomStore.dao().allNotifications()
            .mapNotNull { entity ->
                runCatching { decodeCachedNotification(JSONObject(entity.payload)) }.getOrNull()
            }
            .sortedByDescending { it.timestamp }
            .map { it.toNotification() }
    }

    suspend fun saveNotificationsAsync(notifications: List<MomentsNotification>, sync: Boolean = false) =
        withContext(Dispatchers.IO) {
            notificationsMutex.withLock {
                val existing = if (sync) emptyList() else MomentsRoomStore.dao().allNotifications().mapNotNull { entity ->
                    runCatching { decodeCachedNotification(JSONObject(entity.payload)) }.getOrNull()
                }
                val merged = existing.associateBy { it.id }.toMutableMap()
                notifications.forEach { notification ->
                    val id = notification.id ?: return@forEach
                    merged[id] = CachedNotification.from(notification)
                }
                MomentsRoomStore.dao().replaceNotifications(
                    merged.values.sortedByDescending { it.timestamp }.take(MAX_NOTIFICATIONS).map {
                        NotificationEntity(it.id, it.timestamp.time, it.isPending, it.lastSyncedAt.time, encodeCachedNotification(it).toString())
                    },
                )
            }
        }

    suspend fun deleteNotificationsAsync(ids: List<String>) = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext
        notificationsMutex.withLock {
            val idSet = ids.toSet()
            val remaining = MomentsRoomStore.dao().allNotifications().filter { it.id !in idSet }
            MomentsRoomStore.dao().replaceNotifications(remaining)
        }
    }

    suspend fun saveMessagesInBackground(
        messages: List<EnhancedMessage>,
        conversationId: String,
        sync: Boolean,
    ) {
        if (messages.isEmpty() && !sync) return
        val warmed = warmDiskMediaURLs(messages)
        MessagePersistenceStore.save(encodeMessages(warmed), conversationId, sync)
    }

    suspend fun appendMessagesInBackground(messages: List<EnhancedMessage>, conversationId: String) {
        if (messages.isEmpty()) return
        saveMessagesInBackground(messages, conversationId, sync = false)
    }

    suspend fun reconcileMessagesInBackground(messages: List<EnhancedMessage>, conversationId: String) {
        if (messages.isEmpty()) return
        val warmed = warmDiskMediaURLs(messages)
        MessagePersistenceStore.reconcile(encodeMessages(warmed), conversationId)
    }

    suspend fun messageExistsInBackground(conversationId: String, messageId: String): Boolean =
        MessagePersistenceStore.containsMessage(conversationId, messageId)

    suspend fun lastMessageSyncCursorInBackground(conversationId: String): MessageSyncCursor? =
        MessagePersistenceStore.lastCursor(conversationId)

    suspend fun upsertConversationPreviewAsync(message: EnhancedMessage) {
        val ctx = appContext
        val previewText = if (ctx != null) message.preview(ctx) else messagePreview(message)
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val conversations = loadConversationsAsync().toMutableList()
        val index = conversations.indexOfFirst { it.id == message.conversationId }
        if (index >= 0) {
            val cached = conversations[index]
            val readStatus = cached.readStatus.toMutableMap()
            if (currentUserId.isNotEmpty() && message.senderId != currentUserId) {
                readStatus[currentUserId] = false
            }
            conversations[index] = cached.copy(
                lastMessage = previewText,
                timestamp = message.timestamp,
                lastMessageSenderId = message.senderId,
                readStatus = readStatus,
                lastMessageSeenAt = if (message.senderId == currentUserId) null else cached.lastMessageSeenAt,
                lastMessageReaction = if (message.senderId == currentUserId) null else cached.lastMessageReaction,
            )
        } else {
            val readStatus = mutableMapOf<String, Boolean>()
            if (currentUserId.isNotEmpty()) {
                readStatus[currentUserId] = message.senderId == currentUserId
            }
            conversations += Conversation(
                id = message.conversationId,
                participants = emptyList(),
                lastMessage = previewText,
                timestamp = message.timestamp,
                readStatus = readStatus,
                otherParticipantId = if (message.senderId == currentUserId) "" else message.senderId,
                otherParticipantUsername = null,
                otherParticipantProfileImagePath = null,
                lastMessageSenderId = message.senderId,
            )
        }
        saveConversationsAsync(
            conversations.sortedWith(
                compareByDescending<Conversation> { it.isPinned == true }.thenByDescending { it.timestamp },
            ).take(MAX_CONVERSATIONS),
            sync = true,
        )
    }

    suspend fun clearAllChatCacheAsync() {
        MessagePersistenceStore.clearAll()
        saveConversationsAsync(emptyList(), sync = true)
        ChatCacheStore.clearAllMedia()
    }

    suspend fun cachedMessageCountAsync(): Int = MessagePersistenceStore.cachedMessageCount()

    suspend fun cachedMessageKeysAsync(since: Date): Set<String> = MessagePersistenceStore.cachedMessageKeys(since)

    // MARK: - Cleanup

    suspend fun cleanupOldChatsAsync() {
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -MAX_DATA_AGE_DAYS) }.time
        val staleThreshold = Calendar.getInstance().apply {
            add(Calendar.DAY_OF_YEAR, -STALE_CHAT_THRESHOLD_DAYS)
        }.time
        MessagePersistenceStore.cleanupOldChats(
            cutoffDate = cutoff,
            staleThresholdDate = staleThreshold,
            recentWindow = RECENT_CHAT_WINDOW_SIZE,
            staleWindow = STALE_CHAT_WINDOW_SIZE,
        )
        val chatCutoff = cutoff
        val remaining = loadConversationsAsync().filter { it.isPinned == true || !it.timestamp.before(chatCutoff) }
        saveConversationsAsync(remaining, sync = true)
        ChatCacheStore.enforceRetention()
    }

    suspend fun cleanupOldDataAsync() {
        cleanupOldStoriesAsync()
        cleanupOldChatsAsync()
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -MAX_DATA_AGE_DAYS) }.time
        withContext(Dispatchers.IO) { MomentsRoomStore.dao().deleteMomentsBefore(cutoff.time) }
        trimCachedUsersByAgeAsync(cutoff)
        trimCachedUsersAsync()
    }

    // MARK: - Internals
    private suspend fun updateMomentsAcrossSectionsAsync(
        momentId: String,
        transform: (CachedMoment) -> CachedMoment,
    ) = withContext(Dispatchers.IO) {
        momentsMutex.withLock {
            val dao = MomentsRoomStore.dao()
            val affected = dao.allMoments().filter { it.momentId == momentId }.map { it.feedSection }.distinct()
            affected.forEach { section ->
                val updated = dao.momentsIn(section).mapNotNull { entity ->
                    runCatching { decodeCachedMoment(JSONObject(entity.payload)) }.getOrNull()
                }.map { cached ->
                    if (cached.momentId == momentId) transform(cached) else cached
                }
                dao.replaceMomentsIn(
                    section,
                    updated.map {
                        MomentEntity(
                            feedSection = section,
                            momentId = it.momentId,
                            timestamp = it.timestamp.time,
                            lastSyncedAt = it.lastSyncedAt.time,
                            payload = encodeCachedMoment(it).toString(),
                        )
                    },
                )
            }
        }
    }

    private suspend fun trimCachedUsersAsync() = withContext(Dispatchers.IO) {
        val dao = MomentsRoomStore.dao()
        val entries = dao.cachedNonCurrentUsers()
        if (entries.size <= MAX_CACHED_USERS) return@withContext
        entries.sortedBy { it.lastSyncedAt }
            .take(entries.size - MAX_CACHED_USERS)
            .forEach { dao.deleteUser(it.userId, it.cacheSection) }
    }

    /** ≡ iOS updateCachedConversation — no retrocede lastMessage/timestamp. */
    private fun mergeCachedConversation(
        existing: CachedConversation,
        new: CachedConversation,
    ): CachedConversation {
        val (timestamp, lastMessage) = when {
            new.timestamp.after(existing.timestamp) -> new.timestamp to new.lastMessage
            new.timestamp.time == existing.timestamp.time -> existing.timestamp to new.lastMessage
            else -> existing.timestamp to existing.lastMessage
        }
        return existing.copy(
            participants = new.participants,
            readStatusData = new.readStatusData,
            otherParticipantId = new.otherParticipantId,
            otherParticipantUsername = new.otherParticipantUsername,
            otherParticipantProfileImagePath = new.otherParticipantProfileImagePath,
            isPinned = new.isPinned,
            isMuted = new.isMuted,
            isArchived = new.isArchived,
            readReceiptPreferencesData = new.readReceiptPreferencesData,
            forwardingPreferencesData = new.forwardingPreferencesData,
            lastDeletedAtData = new.lastDeletedAtData,
            lastReadAtData = new.lastReadAtData,
            vanishModeActive = new.vanishModeActive,
            lastMessageSenderId = new.lastMessageSenderId ?: existing.lastMessageSenderId,
            lastMessageSeenAtData = new.lastMessageSeenAtData,
            lastMessageReactionData = new.lastMessageReactionData,
            lastSyncedAt = Date(),
            timestamp = timestamp,
            lastMessage = lastMessage,
        )
    }

    private data class DiskWarmResult(val mediaUrl: String?, val thumbnailUrl: String?, val changed: Boolean)

    private fun applyDiskWarm(message: EnhancedMessage): DiskWarmResult {
        val (mediaUrl, thumbnailUrl) = ChatCacheStore.localURLsIfPresent(message)
        var changed = false
        if (mediaUrl != null && (message.mediaUrl != mediaUrl)) changed = true
        if (thumbnailUrl != null && (message.thumbnailUrl != thumbnailUrl)) changed = true
        return DiskWarmResult(mediaUrl, thumbnailUrl, changed)
    }

    private suspend fun markNotificationPendingAsync(notificationId: String, pending: Boolean) =
        withContext(Dispatchers.IO) {
            notificationsMutex.withLock {
                val updated = MomentsRoomStore.dao().allNotifications().mapNotNull { entity ->
                    runCatching { decodeCachedNotification(JSONObject(entity.payload)) }.getOrNull()
                }.map { notification ->
                    if (notification.id == notificationId) {
                        notification.copy(isPending = pending, lastSyncedAt = Date())
                    } else notification
                }
                MomentsRoomStore.dao().replaceNotifications(updated.map {
                    NotificationEntity(
                        it.id,
                        it.timestamp.time,
                        it.isPending,
                        it.lastSyncedAt.time,
                        encodeCachedNotification(it).toString(),
                    )
                })
            }
        }

    private suspend fun trimCachedUsersByAgeAsync(cutoff: Date) = withContext(Dispatchers.IO) {
        MomentsRoomStore.dao().deleteUsersBefore(cutoff.time)
    }

    private fun messagePreview(message: EnhancedMessage): String {
        if (message.isVanishModeMessage && message.type != MessageType.CHAT_NOTICE) {
            return message.type.conversationPreviewLabel()
        }
        return when (message.type) {
            MessageType.TEXT -> message.content.orEmpty()
            MessageType.IMAGE -> "Photo"
            MessageType.VIDEO -> "Video"
            MessageType.AUDIO -> "Audio"
            MessageType.GIF -> "GIF"
            MessageType.STICKER -> "Sticker"
            MessageType.LOCATION -> "Location"
            MessageType.FILE -> "📎 ${message.fileName ?: "File"}"
            MessageType.EPHEMERAL -> "Ephemeral message"
            MessageType.SHARED_MOMENT -> "Shared moment"
            MessageType.SHARED_STORY -> "Shared story"
            MessageType.SHARED_PROFILE -> "Shared profile"
            MessageType.VIEW_ONCE_IMAGE -> "Photo"
            MessageType.VIEW_ONCE_VIDEO -> "Video"
            MessageType.CHAT_NOTICE -> chatNoticePreviewText(message.content.orEmpty())
        }
    }

    private fun chatNoticePreviewText(token: String): String = when {
        token.contains("vanish.enabled", ignoreCase = true) -> "Disappearing messages on"
        token.contains("vanish.disabled", ignoreCase = true) -> "Disappearing messages off"
        token.contains("screenshot", ignoreCase = true) -> "Screenshot"
        token.contains("screenRecording", ignoreCase = true) -> "Screen recording"
        else -> token
    }

    private fun MessageType.conversationPreviewLabel(): String = when (this) {
        MessageType.TEXT -> "Message"
        MessageType.IMAGE, MessageType.VIEW_ONCE_IMAGE -> "Photo"
        MessageType.VIDEO, MessageType.VIEW_ONCE_VIDEO -> "Video"
        MessageType.AUDIO -> "Audio"
        MessageType.GIF -> "GIF"
        MessageType.STICKER -> "Sticker"
        MessageType.LOCATION -> "Location"
        MessageType.FILE -> "File"
        MessageType.EPHEMERAL -> "Ephemeral message"
        MessageType.SHARED_MOMENT -> "Shared moment"
        MessageType.SHARED_STORY -> "Shared story"
        MessageType.SHARED_PROFILE -> "Shared profile"
        MessageType.CHAT_NOTICE -> "Notice"
    }

    private fun encodeUser(user: AppUser, section: String): String =
        CachedUser.from(user, section).encodeToPrefsJson()

    private fun decodeUser(raw: String): AppUser? =
        CachedUser.decodeFromPrefsJson(raw)?.toAppUser()
}

// MARK: - CachedMoment prefs I/O (from/toMoment viven en CachedMoment)

private fun encodeCachedMoment(cached: CachedMoment): JSONObject = JSONObject().apply {
    put("momentId", cached.momentId)
    put("authorId", cached.authorId)
    put("username", cached.username)
    put("content", cached.content)
    put("imagePath", cached.imagePath)
    put("videoUrl", cached.videoUrl)
    put("timestamp", cached.timestamp.time)
    put("commentCount", cached.commentCount)
    put("profileImagePath", cached.profileImagePath)
    put("location", cached.location)
    put("audience", cached.audience)
    put("aspectRatio", cached.aspectRatio)
    put("thumbnailUrl", cached.thumbnailUrl)
    put("videoDuration", cached.videoDuration)
    put("videoFileSize", cached.videoFileSize)
    put("videoResolution", cached.videoResolution)
    put("customListId", cached.customListId)
    put("disableComments", cached.disableComments)
    put("hideLikeCounts", cached.hideLikeCounts)
    put("allowSharing", cached.allowSharing)
    cached.scheduledDate?.let { put("scheduledDate", it.time) }
    cached.isPinned?.let { put("isPinned", it) }
    cached.pinnedAt?.let { put("pinnedAt", it.time) }
    cached.gridPreviewScale?.let { put("gridPreviewScale", it) }
    cached.gridPreviewOffsetX?.let { put("gridPreviewOffsetX", it) }
    cached.gridPreviewOffsetY?.let { put("gridPreviewOffsetY", it) }
    cached.gridPreviewFitMode?.let { put("gridPreviewFitMode", it) }
    cached.gridPreviewBackground?.let { put("gridPreviewBackground", it) }
    put("hasHiddenLayers", cached.hasHiddenLayers)
    put("hiddenLayerCount", cached.hiddenLayerCount)
    cached.locationLatitude?.let { put("locationLatitude", it) }
    cached.locationLongitude?.let { put("locationLongitude", it) }
    cached.reactionsData?.let { put("reactionsData", Base64.getEncoder().encodeToString(it)) }
    cached.mediaItemsData?.let { put("mediaItemsData", Base64.getEncoder().encodeToString(it)) }
    cached.taggedUsersData?.let { put("taggedUsersData", Base64.getEncoder().encodeToString(it)) }
    cached.mentionedUsersData?.let { put("mentionedUsersData", Base64.getEncoder().encodeToString(it)) }
    put("lastSyncedAt", cached.lastSyncedAt.time)
    put("feedSection", cached.feedSection)
}

private fun decodeCachedMoment(obj: JSONObject): CachedMoment? = runCatching {
    CachedMoment(
        momentId = obj.getString("momentId"),
        authorId = obj.getString("authorId"),
        username = obj.optString("username"),
        content = obj.optString("content"),
        imagePath = obj.stringOrNull("imagePath"),
        videoUrl = obj.stringOrNull("videoUrl"),
        timestamp = Date(obj.getLong("timestamp")),
        commentCount = obj.optIntOrNull("commentCount") ?: obj.optInt("commentCount"),
        profileImagePath = obj.stringOrNull("profileImagePath"),
        location = obj.stringOrNull("location"),
        audience = obj.stringOrNull("audience"),
        aspectRatio = obj.stringOrNull("aspectRatio"),
        thumbnailUrl = obj.stringOrNull("thumbnailUrl"),
        videoDuration = obj.optDoubleOrNull("videoDuration"),
        videoFileSize = obj.optLongOrNull("videoFileSize"),
        videoResolution = obj.stringOrNull("videoResolution"),
        customListId = obj.stringOrNull("customListId"),
        disableComments = if (obj.has("disableComments")) obj.optBoolean("disableComments") else false,
        hideLikeCounts = if (obj.has("hideLikeCounts")) obj.optBoolean("hideLikeCounts") else false,
        allowSharing = obj.optBoolean("allowSharing", true),
        scheduledDate = obj.optLongOrNull("scheduledDate")?.let { Date(it) },
        isPinned = if (obj.has("isPinned") && !obj.isNull("isPinned")) obj.optBoolean("isPinned") else null,
        pinnedAt = obj.optLongOrNull("pinnedAt")?.let { Date(it) },
        gridPreviewScale = obj.optDoubleOrNull("gridPreviewScale"),
        gridPreviewOffsetX = obj.optDoubleOrNull("gridPreviewOffsetX"),
        gridPreviewOffsetY = obj.optDoubleOrNull("gridPreviewOffsetY"),
        gridPreviewFitMode = obj.stringOrNull("gridPreviewFitMode"),
        gridPreviewBackground = obj.stringOrNull("gridPreviewBackground"),
        hasHiddenLayers = if (obj.has("hasHiddenLayers")) obj.optBoolean("hasHiddenLayers") else false,
        hiddenLayerCount = obj.optIntOrNull("hiddenLayerCount") ?: 0,
        locationLatitude = obj.optDoubleOrNull("locationLatitude"),
        locationLongitude = obj.optDoubleOrNull("locationLongitude"),
        reactionsData = obj.stringOrNull("reactionsData")?.let { Base64.getDecoder().decode(it) },
        mediaItemsData = obj.stringOrNull("mediaItemsData")?.let { Base64.getDecoder().decode(it) },
        taggedUsersData = obj.stringOrNull("taggedUsersData")?.let { Base64.getDecoder().decode(it) },
        mentionedUsersData = obj.stringOrNull("mentionedUsersData")?.let { Base64.getDecoder().decode(it) },
        lastSyncedAt = Date(obj.optLong("lastSyncedAt", System.currentTimeMillis())),
        feedSection = obj.optString("feedSection", "feed"),
    )
}.getOrNull()

private fun encodeCachedStory(story: CachedStory): JSONObject = JSONObject().apply {
    put("id", story.id)
    put("authorId", story.authorId)
    put("username", story.username)
    put("profileImagePath", story.profileImagePath)
    put("timestamp", story.timestamp.time)
    put("expirationDate", story.expirationDate.time)
    put("expirationHours", story.expirationHours)
    put("mediaItemData", Base64.getEncoder().encodeToString(story.mediaItemData))
    put("audience", story.audience)
    put("customListId", story.customListId)
    put("text", story.text)
    story.textPositionData?.let { put("textPositionData", Base64.getEncoder().encodeToString(it)) }
    put("textStyle", story.textStyle)
    story.textOverlayMetadataData?.let { put("textOverlayMetadataData", Base64.getEncoder().encodeToString(it)) }
    story.textOverlaysData?.let { put("textOverlaysData", Base64.getEncoder().encodeToString(it)) }
    story.stickersData?.let { put("stickersData", Base64.getEncoder().encodeToString(it)) }
    put("aspectRatio", story.aspectRatio)
    put("backgroundFrameURL", story.backgroundFrameURL)
    put("backgroundBlurredFrameURL", story.backgroundBlurredFrameURL)
    put("chainId", story.chainId)
    put("chainPosition", story.chainPosition)
    put("chainTitle", story.chainTitle)
    story.drawingData?.let { put("drawingData", Base64.getEncoder().encodeToString(it)) }
    put("cachedAt", story.cachedAt.time)
}

private fun decodeCachedStory(obj: JSONObject): CachedStory? = runCatching {
    CachedStory(
        id = obj.getString("id"),
        authorId = obj.getString("authorId"),
        username = obj.optString("username"),
        profileImagePath = obj.stringOrNull("profileImagePath"),
        timestamp = Date(obj.getLong("timestamp")),
        expirationDate = Date(obj.getLong("expirationDate")),
        expirationHours = obj.optIntOrNull("expirationHours"),
        mediaItemData = Base64.getDecoder().decode(obj.getString("mediaItemData")),
        audience = obj.stringOrNull("audience"),
        customListId = obj.stringOrNull("customListId"),
        text = obj.stringOrNull("text"),
        textPositionData = obj.stringOrNull("textPositionData")?.let { Base64.getDecoder().decode(it) },
        textStyle = obj.stringOrNull("textStyle"),
        textOverlayMetadataData = obj.stringOrNull("textOverlayMetadataData")?.let { Base64.getDecoder().decode(it) },
        textOverlaysData = obj.stringOrNull("textOverlaysData")?.let { Base64.getDecoder().decode(it) },
        stickersData = obj.stringOrNull("stickersData")?.let { Base64.getDecoder().decode(it) },
        aspectRatio = obj.stringOrNull("aspectRatio"),
        backgroundFrameURL = obj.stringOrNull("backgroundFrameURL"),
        backgroundBlurredFrameURL = obj.stringOrNull("backgroundBlurredFrameURL"),
        chainId = obj.stringOrNull("chainId"),
        chainPosition = obj.optIntOrNull("chainPosition"),
        chainTitle = obj.stringOrNull("chainTitle"),
        drawingData = obj.stringOrNull("drawingData")?.let { Base64.getDecoder().decode(it) },
        cachedAt = Date(obj.optLong("cachedAt", System.currentTimeMillis())),
    )
}.getOrNull()

private fun JSONArray.toStringList(): List<String> =
    (0 until length()).mapNotNull { optString(it).takeIf { s -> s.isNotBlank() } }

private fun JSONObject.stringOrNull(name: String): String? = when (val value = opt(name)) {
    null, JSONObject.NULL -> null
    is String -> value.trim().takeUnless { it.isEmpty() || it.equals("null", ignoreCase = true) }
    else -> null
}

private fun JSONObject.optDoubleOrNull(name: String): Double? =
    if (has(name) && !isNull(name)) optDouble(name) else null

private fun JSONObject.optLongOrNull(name: String): Long? =
    if (has(name) && !isNull(name)) optLong(name) else null

private fun JSONObject.optIntOrNull(name: String): Int? =
    if (has(name) && !isNull(name)) optInt(name) else null

// MARK: - CachedConversation / Notification conversions

private fun encodeCachedConversation(cached: CachedConversation): JSONObject = JSONObject().apply {
    put("id", cached.id)
    put("participants", JSONArray(cached.participants))
    put("lastMessage", cached.lastMessage)
    put("timestamp", cached.timestamp.time)
    cached.readStatusData?.let { put("readStatusData", Base64.getEncoder().encodeToString(it)) }
    put("otherParticipantId", cached.otherParticipantId)
    put("otherParticipantUsername", cached.otherParticipantUsername)
    put("otherParticipantProfileImagePath", cached.otherParticipantProfileImagePath)
    put("isPinned", cached.isPinned)
    put("isMuted", cached.isMuted)
    put("isArchived", cached.isArchived)
    cached.readReceiptPreferencesData?.let { put("readReceiptPreferencesData", Base64.getEncoder().encodeToString(it)) }
    cached.forwardingPreferencesData?.let { put("forwardingPreferencesData", Base64.getEncoder().encodeToString(it)) }
    cached.lastDeletedAtData?.let { put("lastDeletedAtData", Base64.getEncoder().encodeToString(it)) }
    cached.lastReadAtData?.let { put("lastReadAtData", Base64.getEncoder().encodeToString(it)) }
    put("lastMessageSenderId", cached.lastMessageSenderId)
    cached.lastMessageSeenAtData?.let { put("lastMessageSeenAtData", Base64.getEncoder().encodeToString(it)) }
    cached.lastMessageReactionData?.let { put("lastMessageReactionData", Base64.getEncoder().encodeToString(it)) }
    put("lastSyncedAt", cached.lastSyncedAt.time)
    put("vanishModeActive", cached.vanishModeActive)
}

private fun decodeCachedConversation(obj: JSONObject): CachedConversation? = runCatching {
    CachedConversation(
        id = obj.getString("id"),
        participants = obj.optJSONArray("participants")?.toStringList() ?: emptyList(),
        lastMessage = obj.stringOrNull("lastMessage"),
        timestamp = Date(obj.getLong("timestamp")),
        readStatusData = obj.stringOrNull("readStatusData")?.let { Base64.getDecoder().decode(it) },
        otherParticipantId = obj.optString("otherParticipantId", ""),
        otherParticipantUsername = obj.stringOrNull("otherParticipantUsername"),
        otherParticipantProfileImagePath = obj.stringOrNull("otherParticipantProfileImagePath"),
        isPinned = obj.optBoolean("isPinned"),
        isMuted = obj.optBoolean("isMuted"),
        isArchived = obj.optBoolean("isArchived"),
        readReceiptPreferencesData = obj.stringOrNull("readReceiptPreferencesData")?.let { Base64.getDecoder().decode(it) },
        forwardingPreferencesData = obj.stringOrNull("forwardingPreferencesData")?.let { Base64.getDecoder().decode(it) },
        lastDeletedAtData = obj.stringOrNull("lastDeletedAtData")?.let { Base64.getDecoder().decode(it) },
        lastReadAtData = obj.stringOrNull("lastReadAtData")?.let { Base64.getDecoder().decode(it) },
        lastMessageSenderId = obj.stringOrNull("lastMessageSenderId"),
        lastMessageSeenAtData = obj.stringOrNull("lastMessageSeenAtData")?.let { Base64.getDecoder().decode(it) },
        lastMessageReactionData = obj.stringOrNull("lastMessageReactionData")?.let { Base64.getDecoder().decode(it) },
        lastSyncedAt = Date(obj.optLong("lastSyncedAt", System.currentTimeMillis())),
        vanishModeActive = obj.optBoolean("vanishModeActive"),
    )
}.getOrNull()

private fun encodeCachedNotification(cached: CachedNotification): JSONObject = JSONObject().apply {
    put("id", cached.id)
    put("type", cached.type)
    put("senderId", cached.senderId)
    put("senderUsername", cached.senderUsername)
    put("timestamp", cached.timestamp.time)
    put("isPending", cached.isPending)
    put("title", cached.title)
    put("message", cached.message)
    put("downloadURL", cached.downloadURL)
    put("momentId", cached.momentId)
    put("visitCount", cached.visitCount)
    put("storyId", cached.storyId)
    put("storyAuthorId", cached.storyAuthorId)
    put("storyPreviewUrl", cached.storyPreviewUrl)
    put("reaction", cached.reaction)
    put("reactionCount", cached.reactionCount)
    put("commentId", cached.commentId)
    put("echoId", cached.echoId)
    put("moderationScope", cached.moderationScope)
    put("totalParts", cached.totalParts)
    put("chainRole", cached.chainRole)
    put("lastSyncedAt", cached.lastSyncedAt.time)
}

private fun decodeCachedNotification(obj: JSONObject): CachedNotification? = runCatching {
    CachedNotification(
        id = obj.getString("id"),
        type = obj.getString("type"),
        senderId = obj.getString("senderId"),
        senderUsername = obj.optString("senderUsername"),
        timestamp = Date(obj.getLong("timestamp")),
        isPending = obj.optBoolean("isPending", true),
        title = obj.stringOrNull("title"),
        message = obj.stringOrNull("message"),
        downloadURL = obj.stringOrNull("downloadURL"),
        momentId = obj.stringOrNull("momentId"),
        visitCount = obj.optIntOrNull("visitCount"),
        storyId = obj.stringOrNull("storyId"),
        storyAuthorId = obj.stringOrNull("storyAuthorId"),
        storyPreviewUrl = obj.stringOrNull("storyPreviewUrl"),
        reaction = obj.stringOrNull("reaction"),
        reactionCount = obj.optIntOrNull("reactionCount"),
        commentId = obj.stringOrNull("commentId"),
        echoId = obj.stringOrNull("echoId"),
        moderationScope = obj.stringOrNull("moderationScope"),
        totalParts = obj.optIntOrNull("totalParts"),
        chainRole = obj.stringOrNull("chainRole"),
        lastSyncedAt = Date(obj.optLong("lastSyncedAt", System.currentTimeMillis())),
    )
}.getOrNull()
