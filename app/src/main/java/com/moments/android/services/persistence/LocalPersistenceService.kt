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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Base64
import java.util.Calendar
import java.util.Date
import java.util.UUID

/**
 * Persistencia local (SharedPreferences + filesDir JSON).
 * Port de LocalPersistenceService.swift — StorySeenStateService en archivo aparte (mismo Swift).
 * Topes: feed 100, explore 50, users 200, conversations 50, notifs 100, searches 20, msgs/chat 2000.
 * Δ líneas ≈ SwiftData/App Group/boilerplate OO vs JSON.
 */
object LocalPersistenceService {

    private const val PREFS = "moments_local_persistence"
    private const val KEY_CURRENT_USER_ID = "currentUserId"
    private const val KEY_USER_PREFIX = "user_"
    private const val KEY_PENDING_ACTIONS = "pending_actions"
    private const val KEY_CONVERSATION_PREVIEWS = "conversation_previews"
    private const val KEY_CONNECTIONS_PREFIX = "connections_"
    private const val KEY_SEARCH_HISTORY = "search_history"

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
    private val connectionsLock = Any()

    class ActionPersistenceException(message: String) : Exception(message)

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            MessagePersistenceStore.initialize(context)
            StorySeenStateService.initialize(context)
        }
    }

    private fun prefs() =
        (appContext ?: error("LocalPersistenceService.initialize required"))
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun cacheDir(): File {
        val ctx = appContext ?: error("LocalPersistenceService.initialize required")
        return File(ctx.filesDir, "local_cache").also { it.mkdirs() }
    }

    private fun momentsFile(section: String): File =
        File(File(cacheDir(), "moments"), "$section.json").apply { parentFile?.mkdirs() }

    private fun storiesFile(): File = File(cacheDir(), "stories.json")

    private fun conversationsFile(): File = File(cacheDir(), "conversations.json")

    private fun notificationsFile(): File = File(cacheDir(), "notifications.json")

    // MARK: - Current user

    fun saveCurrentUser(user: AppUser) = saveUser(user, section = "currentUser")

    fun loadCurrentUser(): AppUser? {
        prefs().getString(KEY_CURRENT_USER_ID, null)?.let { return loadUser(it) }
        // ≡ iOS: FetchDescriptor por cacheSection == "currentUser"
        prefs().all.keys.filter { it.startsWith(KEY_USER_PREFIX) }.forEach { key ->
            val raw = prefs().getString(key, null) ?: return@forEach
            val cached = CachedUser.decodeFromPrefsJson(raw) ?: return@forEach
            if (cached.cacheSection == "currentUser") return cached.toAppUser()
        }
        return null
    }

    fun clearCurrentUser() {
        val id = prefs().getString(KEY_CURRENT_USER_ID, null)
        val editor = prefs().edit().remove(KEY_CURRENT_USER_ID)
        if (id != null) editor.remove(KEY_USER_PREFIX + id)
        editor.apply()
    }

    // MARK: - Users

    fun saveUser(user: AppUser, section: String = "profile") {
        val editor = prefs().edit()
            .putString(KEY_USER_PREFIX + user.id, encodeUser(user, section))
        if (section == "currentUser") {
            editor.putString(KEY_CURRENT_USER_ID, user.id)
        }
        editor.apply()
        trimCachedUsers()
    }

    fun loadUser(userId: String): AppUser? {
        val raw = prefs().getString(KEY_USER_PREFIX + userId, null) ?: return null
        return decodeUser(raw)
    }

    // MARK: - Outbox / CachedAction

    fun saveActionOrThrow(action: CachedAction) {
        if (appContext == null) throw ActionPersistenceException("Local persistence store is unavailable.")
        val actions = loadAllActions().toMutableList()
        // ≡ iOS insert (SwiftData); evitar duplicados por id en JSON prefs.
        if (actions.any { it.id == action.id }) return
        actions += action
        saveAllActions(actions)
    }

    fun saveAction(action: CachedAction) {
        runCatching { saveActionOrThrow(action) }.onFailure { return }
        val isUpload = action.type == CachedAction.ActionType.MOMENT_UPLOAD.raw ||
            action.type == CachedAction.ActionType.STORY_UPLOAD.raw
        if (NetworkMonitor.isConnected && !isUpload) {
            // iOS: `syncPendingActions()` (requireAutomaticSync = true por defecto).
            ioScope.launch { OfflineSyncService.syncPendingActions() }
        }
    }

    fun loadPendingActions(): List<CachedAction> {
        return loadAllActions().filter {
            it.status == CachedAction.ActionStatus.PENDING.raw ||
                it.status == CachedAction.ActionStatus.EXECUTING.raw
        }.sortedBy { it.createdAt }
    }

    /** Cualquier estado (PENDING / EXECUTING / FAILED) — cancel/retry outbox. */
    fun loadAction(id: String): CachedAction? = loadAllActions().find { it.id == id }

    fun deleteAction(id: String) {
        saveAllActions(loadAllActions().filter { it.id != id })
    }

    fun hasPendingAction(id: String): Boolean = loadAllActions().any { it.id == id }

    fun markActionAttempt(id: String) {
        val actions = loadAllActions().map { action ->
            if (action.id == id) action.copy(
                retryCount = action.retryCount + 1,
                lastAttemptAt = Date(),
            ) else action
        }
        saveAllActions(actions)
    }

    fun updateActionStatus(id: String, status: CachedAction.ActionStatus, error: String? = null) {
        val actions = loadAllActions().map { action ->
            if (action.id != id) action
            else action.copy(
                status = status.raw,
                lastError = error,
                retryCount = if (status == CachedAction.ActionStatus.FAILED) action.retryCount + 1 else action.retryCount,
            )
        }
        saveAllActions(actions)
    }

    fun updateCachedMessageStatus(conversationId: String, messageId: String, status: MessageStatus) {
        MessagePersistenceStore.updateMessageStatus(conversationId, messageId, status.raw)
    }

    // MARK: - Connections (followers / following / mutuals)

    fun saveFollowers(userId: String, followers: List<AppUser>) =
        saveConnectionList(userId, followers, "follower")

    fun saveFollowing(userId: String, following: List<AppUser>) =
        saveConnectionList(userId, following, "following")

    fun saveMutuals(userId: String, mutuals: List<AppUser>) =
        saveConnectionList(userId, mutuals, "mutual")

    fun loadConnections(userId: String): Triple<List<AppUser>, List<AppUser>, List<AppUser>> {
        val connections = loadConnectionRecords(userId)
        var followers = mutableListOf<AppUser>()
        var following = mutableListOf<AppUser>()
        var mutuals = mutableListOf<AppUser>()
        for (conn in connections) {
            val user = loadUser(conn.targetId) ?: continue
            when (conn.type) {
                "follower" -> followers += user
                "mutual" -> mutuals += user
                else -> following += user
            }
        }
        return Triple(followers, following, mutuals)
    }

    fun isFollowing(targetUserId: String): Boolean {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return false
        return synchronized(connectionsLock) {
            loadConnectionRecords(currentUserId).any {
                it.targetId == targetUserId && it.type == "following"
            }
        }
    }

    /** Snapshot positivo para arranque/offline; ausencia no equivale a un estado remoto negativo. */
    fun cachedFollowRelationship(viewerId: String, targetUserId: String): Pair<Boolean, Boolean> {
        return synchronized(connectionsLock) {
            val matching = loadConnectionRecords(viewerId).filter { it.targetId == targetUserId }
            Pair(
                matching.any { it.type == "following" },
                matching.any { it.type == "mutual" },
            )
        }
    }

    fun updateCachedFollowRelationship(
        viewerId: String,
        targetUserId: String,
        isFollowing: Boolean,
        isMutual: Boolean,
    ) {
        synchronized(connectionsLock) {
            val connections = loadConnectionRecords(viewerId).toMutableList()
            connections.removeAll {
                it.targetId == targetUserId && (it.type == "following" || it.type == "mutual")
            }
            if (isFollowing) connections += CachedConnection(viewerId, targetUserId, "following")
            if (isMutual) connections += CachedConnection(viewerId, targetUserId, "mutual")
            saveConnectionRecords(viewerId, connections)
        }
    }

    private fun saveConnectionList(userId: String, users: List<AppUser>, type: String) {
        synchronized(connectionsLock) {
            val remaining = loadConnectionRecords(userId).filter { it.type != type }
            val updated = remaining + users.map { CachedConnection(userId, it.id, type) }
            saveConnectionRecords(userId, updated)
        }
        users.forEach { saveUser(it) }
    }

    private fun loadConnectionRecords(userId: String): List<CachedConnection> {
        val raw = prefs().getString(KEY_CONNECTIONS_PREFIX + userId, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CachedConnection(
                    userId = obj.getString("userId"),
                    targetId = obj.getString("targetId"),
                    type = obj.getString("type"),
                    timestamp = Date(obj.optLong("timestamp", System.currentTimeMillis())),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveConnectionRecords(userId: String, connections: List<CachedConnection>) {
        val arr = JSONArray().apply {
            connections.forEach { conn ->
                put(JSONObject().apply {
                    put("userId", conn.userId)
                    put("targetId", conn.targetId)
                    put("type", conn.type)
                    put("timestamp", conn.timestamp.time)
                })
            }
        }
        prefs().edit().putString(KEY_CONNECTIONS_PREFIX + userId, arr.toString()).apply()
    }

    // MARK: - Moments cache

    fun saveFeedMoments(moments: List<Moment>, sync: Boolean = false) =
        saveMoments(moments, section = "feed", sync = sync, maxCount = MAX_FEED_MOMENTS)

    fun saveExploreMoments(moments: List<Moment>, sync: Boolean = false) =
        saveMoments(moments, section = "explore", sync = sync, maxCount = MAX_EXPLORE_MOMENTS)

    fun saveProfileMoments(
        moments: List<Moment>,
        userId: String,
        viewerId: String? = null,
        sync: Boolean = true,
    ) = saveMoments(moments, section = profileMomentsSection(userId, viewerId), sync = sync, maxCount = 50)

    fun loadFeedMoments(): List<Moment> = loadMoments("feed", MAX_FEED_MOMENTS)

    fun loadExploreMoments(): List<Moment> = loadMoments("explore", MAX_EXPLORE_MOMENTS)

    fun loadProfileMoments(userId: String, viewerId: String? = null): List<Moment> =
        loadMoments(profileMomentsSection(userId, viewerId), maxCount = 50)

    private fun profileMomentsSection(userId: String, viewerId: String?): String =
        if (!viewerId.isNullOrEmpty()) "profile_${viewerId}_$userId" else "profile_$userId"

    private fun saveMoments(moments: List<Moment>, section: String, sync: Boolean, maxCount: Int) {
        val existing = if (sync) emptyList() else loadCachedMoments(section)
        val existingMap = existing.associateBy { it.momentId }.toMutableMap()
        moments.forEach { moment ->
            val id = moment.id ?: return@forEach
            existingMap[id] = CachedMoment.from(moment, section)
        }
        val merged = existingMap.values.sortedByDescending { it.timestamp.time }.take(maxCount)
        writeCachedMoments(section, merged)
    }

    private fun loadMoments(section: String, maxCount: Int): List<Moment> =
        loadCachedMoments(section)
            .sortedByDescending { it.timestamp.time }
            .take(maxCount)
            .mapNotNull { it.toMoment() }

    private fun loadCachedMoments(section: String): List<CachedMoment> {
        val file = momentsFile(section)
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { decodeCachedMoment(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun writeCachedMoments(section: String, moments: List<CachedMoment>) {
        val arr = JSONArray().apply { moments.forEach { put(encodeCachedMoment(it)) } }
        momentsFile(section).writeText(arr.toString())
    }

    // MARK: - Stories cache

    fun saveStories(stories: List<Story>, sync: Boolean = false) {
        val existing = if (sync) emptyList() else loadAllCachedStories()
        val map = existing.associateBy { it.id }.toMutableMap()
        stories.forEach { story ->
            val id = story.id ?: return@forEach
            CachedStory.fromStory(story)?.let { map[id] = it }
        }
        writeAllCachedStories(map.values.toList())
    }

    fun deleteStory(storyId: String) {
        writeAllCachedStories(loadAllCachedStories().filter { it.id != storyId })
    }

    fun deleteStories(userId: String) {
        writeAllCachedStories(loadAllCachedStories().filter { it.authorId != userId })
    }

    fun loadStories(userId: String): List<Story> {
        val now = Date()
        return loadAllCachedStories()
            .filter { it.authorId == userId && it.expirationDate.after(now) }
            .sortedBy { it.timestamp.time }
            .map { it.toStory() }
    }

    fun cleanupOldStories() {
        val now = Date()
        writeAllCachedStories(loadAllCachedStories().filter { it.expirationDate.after(now) })
    }

    private fun loadAllCachedStories(): List<CachedStory> {
        val file = storiesFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { decodeCachedStory(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun writeAllCachedStories(stories: List<CachedStory>) {
        val arr = JSONArray().apply { stories.forEach { put(encodeCachedStory(it)) } }
        storiesFile().writeText(arr.toString())
    }

    // MARK: - Optimistic local updates

    fun toggleMomentReactionLocally(momentId: String, reaction: String, userId: String) {
        updateMomentsAcrossSections(momentId) { cached ->
            val reactions = CachedMoment.decodeReactions(cached.reactionsData).toMutableMap()
            val users = reactions.getOrPut(reaction) { mutableListOf() }.toMutableList()
            if (users.contains(userId)) users.remove(userId) else users.add(userId)
            if (users.isEmpty()) reactions.remove(reaction) else reactions[reaction] = users
            cached.copy(reactionsData = CachedMoment.encodeReactions(reactions), lastSyncedAt = Date())
        }
    }

    fun updateCommentCountLocally(momentId: String, increment: Int) {
        updateMomentsAcrossSections(momentId) { cached ->
            val current = cached.commentCount ?: 0
            cached.copy(commentCount = maxOf(0, current + increment), lastSyncedAt = Date())
        }
    }

    fun toggleFollowLocally(currentUserId: String, targetUserId: String, isFollow: Boolean) {
        synchronized(connectionsLock) {
            val connections = loadConnectionRecords(currentUserId).toMutableList()
            connections.removeAll { it.targetId == targetUserId && it.type == "following" }
            if (isFollow) {
                connections += CachedConnection(currentUserId, targetUserId, "following")
            }
            saveConnectionRecords(currentUserId, connections)
        }
    }

    fun deleteMoment(momentId: String) {
        val momentsDir = File(cacheDir(), "moments")
        if (!momentsDir.exists()) return
        momentsDir.listFiles()?.forEach { file ->
            val section = file.nameWithoutExtension
            val updated = loadCachedMoments(section).filter { it.momentId != momentId }
            writeCachedMoments(section, updated)
        }
    }

    suspend fun deleteMoment(
        momentId: String,
        userId: String,
        imagePath: String?,
        videoUrl: String?,
    ) {
        deleteMoment(momentId)
        val payload = DeleteMomentPayload(
            momentId = momentId,
            userId = userId,
            imagePath = imagePath,
            videoUrl = videoUrl,
        )
        saveAction(
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
        val existing = loadUser(userId)
        if (existing != null) {
            if (actualOldBio == null) actualOldBio = existing.bio
            if (actualOldWebsite == null) actualOldWebsite = existing.websiteUrl
            saveUser(
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
        saveAction(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.UPDATE_PROFILE.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    suspend fun acceptFollowRequest(notificationId: String, senderId: String, recipientId: String) {
        markNotificationPending(notificationId, pending = false)
        val payload = FollowRequestActionPayload(
            notificationId = notificationId,
            senderId = senderId,
            recipientId = recipientId,
            isAccept = true,
        )
        saveAction(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.ACCEPT_FOLLOW_REQUEST.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    suspend fun rejectFollowRequest(notificationId: String, senderId: String, recipientId: String) {
        deleteNotifications(listOf(notificationId))
        val payload = FollowRequestActionPayload(
            notificationId = notificationId,
            senderId = senderId,
            recipientId = recipientId,
            isAccept = false,
        )
        saveAction(
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
        saveAction(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.REPORT_CONTENT.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    suspend fun markNotificationAsRead(notificationId: String, userId: String) {
        markNotificationPending(notificationId, pending = false)
        val payload = MarkAsReadPayload(notificationId = notificationId, userId = userId)
        saveAction(
            CachedAction(
                id = UUID.randomUUID().toString(),
                type = CachedAction.ActionType.MARK_AS_READ.raw,
                payloadData = payload.encode(),
            ),
        )
    }

    // MARK: - Search history

    fun saveSearch(query: String, type: String, targetId: String? = null) {
        val search = CachedSearch(query, type, targetId)
        val list = loadRecentSearches().filter { it.id != search.id }.toMutableList()
        list.add(0, search)
        saveSearchHistory(list.take(MAX_SEARCHES))
    }

    fun deleteSearch(id: String) {
        saveSearchHistory(loadRecentSearches().filter { it.id != id })
    }

    fun loadRecentSearches(): List<CachedSearch> {
        val raw = prefs().getString(KEY_SEARCH_HISTORY, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CachedSearch(
                    query = obj.getString("query"),
                    type = obj.getString("type"),
                    targetId = obj.stringOrNull("targetId"),
                    timestamp = Date(obj.getLong("timestamp")),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun clearSearchHistory() {
        prefs().edit().remove(KEY_SEARCH_HISTORY).apply()
    }

    // MARK: - Messaging cache (delegated)

    fun saveConversations(conversations: List<Conversation>, sync: Boolean = false) {
        val existing = if (sync) emptyList() else loadCachedConversations()
        val map = existing.associateBy { it.id }.toMutableMap()
        conversations.forEach { conv ->
            val id = conv.id ?: return@forEach
            val incoming = CachedConversation.from(conv)
            val prev = map[id]
            map[id] = if (prev != null) mergeCachedConversation(prev, incoming) else incoming
        }
        writeCachedConversations(
            map.values.sortedWith(
                compareByDescending<CachedConversation> { it.isPinned }.thenByDescending { it.timestamp },
            ).take(MAX_CONVERSATIONS),
        )
    }

    fun loadConversations(): List<Conversation> =
        loadCachedConversations()
            .sortedWith(compareByDescending<CachedConversation> { it.isPinned }.thenByDescending { it.timestamp })
            .map { it.toConversation() }

    /** Helper Android (iOS: ChatService.isConversationArchived). Lee snapshot LPS. */
    fun isConversationArchived(conversationId: String, userId: String): Boolean {
        val conversation = loadCachedConversations().firstOrNull { it.id == conversationId } ?: return false
        return conversation.toConversation().isArchived(userId)
    }

    fun saveMessages(messages: List<EnhancedMessage>, conversationId: String, sync: Boolean = false) {
        if (messages.isEmpty() && !sync) return
        val warmed = warmDiskMediaURLs(messages)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            MessagePersistenceStore.save(encodeMessages(warmed), conversationId, sync)
        }
    }

    fun appendMessages(messages: List<EnhancedMessage>, conversationId: String) {
        if (messages.isEmpty()) return
        saveMessages(messages, conversationId, sync = false)
    }

    fun reconcileMessages(messages: List<EnhancedMessage>, conversationId: String) {
        if (messages.isEmpty()) return
        val warmed = warmDiskMediaURLs(messages)
        runBlockingIo {
            MessagePersistenceStore.reconcile(encodeMessages(warmed), conversationId)
        }
    }

    fun messageExists(conversationId: String, messageId: String): Boolean =
        runBlockingIo { MessagePersistenceStore.containsMessage(conversationId, messageId) }

    fun lastMessageSyncCursor(conversationId: String): MessageSyncCursor? =
        runBlockingIo { MessagePersistenceStore.lastCursor(conversationId) }

    fun lastMessageTimestamp(conversationId: String): Date? =
        lastMessageSyncCursor(conversationId)?.timestamp

    fun loadMessagesFast(conversationId: String): List<EnhancedMessage> =
        runBlockingIo { MessagePersistenceStore.allMessages(conversationId) }
            .let { decodeMessages(it) }
            .sortedWith(compareBy<EnhancedMessage> { it.timestamp }.thenBy { it.id })

    fun loadRecentMessagesFast(
        conversationId: String,
        limit: Int,
        cutoffDate: Date? = null,
    ): List<EnhancedMessage> {
        if (limit <= 0) return emptyList()
        return runBlockingIo {
            MessagePersistenceStore.recentMessages(conversationId, limit, cutoffDate)
        }.let { encoded ->
            if (encoded.isEmpty()) emptyList() else decodeMessages(encoded)
        }
    }

    fun loadMessagesBefore(
        conversationId: String,
        cursor: MessageSyncCursor,
        cutoffDate: Date? = null,
        limit: Int,
    ): List<EnhancedMessage> {
        if (limit <= 0) return emptyList()
        return runBlockingIo {
            MessagePersistenceStore.messagesBefore(conversationId, cursor, cutoffDate, limit)
        }.let { encoded ->
            if (encoded.isEmpty()) emptyList() else decodeMessages(encoded)
        }
    }

    fun loadMessagesAfter(
        conversationId: String,
        cursor: MessageSyncCursor,
        cutoffDate: Date? = null,
        limit: Int,
    ): List<EnhancedMessage> {
        if (limit <= 0) return emptyList()
        return runBlockingIo {
            MessagePersistenceStore.messagesAfter(conversationId, cursor, cutoffDate, limit)
        }.let { encoded ->
            if (encoded.isEmpty()) emptyList() else decodeMessages(encoded)
        }
    }

    fun searchMessageIds(conversationId: String, query: String, limit: Int = 100): List<String> =
        MessagePersistenceStore.searchMessageIds(conversationId, query, limit)

    fun searchMessagesGlobally(query: String, limit: Int = 50): List<EnhancedMessage> =
        MessagePersistenceStore.searchMessagesGlobally(query, limit)

    fun markMessageDeletedForEveryone(conversationId: String, messageId: String) {
        MessagePersistenceStore.markMessageDeletedForEveryone(conversationId, messageId)
    }

    fun removeCachedMessage(conversationId: String, messageId: String) {
        MessagePersistenceStore.removeCachedMessage(conversationId, messageId)
    }

    fun markVanishMessagesDismissed(conversationId: String, messageIds: List<String>, userId: String) {
        if (messageIds.isEmpty()) return
        MessagePersistenceStore.markVanishMessagesDismissed(conversationId, messageIds.toSet(), userId)
    }

    fun updateMessageVanishExpiresAt(conversationId: String, messageId: String, expiresAt: Date) {
        MessagePersistenceStore.updateMessageVanishExpiresAt(conversationId, messageId, expiresAt)
    }

    fun updateMessageNoticeContent(conversationId: String, messageId: String, content: String) {
        MessagePersistenceStore.updateMessageNoticeContent(conversationId, messageId, content)
    }

    fun toggleMessageReactionLocally(messageId: String, emoji: String, userId: String) {
        MessagePersistenceStore.toggleMessageReactionLocally(messageId, emoji, userId)
    }

    fun unreadMessageCount(
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
            val loaded = loadMessagesFast(conversationId)
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
                saveMessages(relinked, conversationId, sync = false)
            }
            onUpdated(results)
        }
    }

    suspend fun loadMessagesInBackground(conversationId: String): List<EnhancedMessage> {
        val encoded = MessagePersistenceStore.allMessages(conversationId)
        if (encoded.isEmpty()) return emptyList()
        return decodeMessages(encoded)
    }

    fun loadMessages(conversationId: String): List<EnhancedMessage> {
        val results = loadMessagesFast(conversationId)
        if (results.isEmpty()) return emptyList()
        var relinked = false
        val warmed = results.map { msg ->
            val warm = applyDiskWarm(msg)
            if (warm.changed) {
                relinked = true
                msg.copy(mediaUrl = warm.mediaUrl ?: msg.mediaUrl, thumbnailUrl = warm.thumbnailUrl ?: msg.thumbnailUrl)
            } else msg
        }
        if (relinked) saveMessages(warmed, conversationId, sync = false)
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

    fun markMessagesAsRead(conversationId: String, messageIds: List<String>) {
        if (messageIds.isEmpty()) return
        MessagePersistenceStore.markMessagesAsRead(conversationId, messageIds.toSet())
    }

    fun markConversationReadLocally(conversationId: String, currentUserId: String) {
        val conversations = loadCachedConversations().toMutableList()
        val index = conversations.indexOfFirst { it.id == conversationId }
        if (index >= 0) {
            val cached = conversations[index]
            val readStatus = CachedConversation.decodeStringBoolMap(cached.readStatusData).toMutableMap()
            if (readStatus[currentUserId] != true) {
                readStatus[currentUserId] = true
                conversations[index] = cached.copy(readStatusData = CachedConversation.encodeStringBoolMap(readStatus))
                writeCachedConversations(conversations)
            }
        }
        MessagePersistenceStore.markAllIncomingAsRead(conversationId, currentUserId)
    }

    fun deleteConversationCache(conversationId: String) {
        val messageIds = loadMessages(conversationId).map { it.id }
        ChatCacheStore.deleteConversation(conversationId, messageIds)
        MessagePersistenceStore.deleteConversation(conversationId)
        val remaining = loadCachedConversations().filter { it.id != conversationId }
        writeCachedConversations(remaining)
        prefs().edit().apply {
            val previews = loadConversationPreviews().toMutableMap()
            previews.remove(conversationId)
            val obj = JSONObject()
            previews.forEach { (k, v) -> obj.put(k, v) }
            putString(KEY_CONVERSATION_PREVIEWS, obj.toString())
        }.apply()
    }

    fun saveNotifications(notifications: List<MomentsNotification>, sync: Boolean = false) {
        val existing = if (sync) emptyList() else loadCachedNotifications()
        val map = existing.associateBy { it.id }.toMutableMap()
        notifications.forEach { notification ->
            val id = notification.id ?: return@forEach
            map[id] = CachedNotification.from(notification)
        }
        writeCachedNotifications(map.values.sortedByDescending { it.timestamp }.take(MAX_NOTIFICATIONS))
    }

    fun loadNotifications(): List<MomentsNotification> =
        loadCachedNotifications().sortedByDescending { it.timestamp }.map { it.toNotification() }

    fun deleteNotifications(ids: List<String>) {
        if (ids.isEmpty()) return
        val idSet = ids.toSet()
        writeCachedNotifications(loadCachedNotifications().filter { it.id !in idSet })
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

    fun upsertConversationPreview(message: EnhancedMessage) {
        val ctx = appContext
        val previewText = if (ctx != null) message.preview(ctx) else messagePreview(message)
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val conversations = loadCachedConversations().toMutableList()
        val index = conversations.indexOfFirst { it.id == message.conversationId }
        if (index >= 0) {
            val cached = conversations[index]
            val readStatus = CachedConversation.decodeStringBoolMap(cached.readStatusData).toMutableMap()
            if (currentUserId.isNotEmpty() && message.senderId != currentUserId) {
                readStatus[currentUserId] = false
            }
            conversations[index] = cached.copy(
                lastMessage = previewText,
                timestamp = message.timestamp,
                lastMessageSenderId = message.senderId,
                lastSyncedAt = Date(),
                readStatusData = CachedConversation.encodeStringBoolMap(readStatus),
                lastMessageSeenAtData = if (message.senderId == currentUserId) null else cached.lastMessageSeenAtData,
                lastMessageReactionData = if (message.senderId == currentUserId) null else cached.lastMessageReactionData,
            )
        } else {
            val readStatus = mutableMapOf<String, Boolean>()
            if (currentUserId.isNotEmpty()) {
                readStatus[currentUserId] = message.senderId == currentUserId
            }
            conversations += CachedConversation(
                id = message.conversationId,
                participants = emptyList(),
                lastMessage = previewText,
                timestamp = message.timestamp,
                readStatusData = CachedConversation.encodeStringBoolMap(readStatus),
                otherParticipantId = if (message.senderId == currentUserId) "" else message.senderId,
                otherParticipantUsername = null,
                otherParticipantProfileImagePath = null,
                lastMessageSenderId = message.senderId,
                lastSyncedAt = Date(),
            )
        }
        writeCachedConversations(
            conversations.sortedWith(
                compareByDescending<CachedConversation> { it.isPinned }.thenByDescending { it.timestamp },
            ).take(MAX_CONVERSATIONS),
        )
        val previews = loadConversationPreviews().toMutableMap()
        previews[message.conversationId] = JSONObject().apply {
            put("lastMessage", previewText)
            put("timestamp", message.timestamp.time)
            put("senderId", message.senderId)
        }
        saveConversationPreviews(previews)
    }

    fun clearAllChatCache() {
        MessagePersistenceStore.clearAll()
        writeCachedConversations(emptyList())
        prefs().edit().remove(KEY_CONVERSATION_PREVIEWS).apply()
        ChatCacheStore.clearAllMedia()
    }

    fun cachedMessageCount(): Int = MessagePersistenceStore.cachedMessageCount()

    fun cachedMessageKeys(since: Date): Set<String> = MessagePersistenceStore.cachedMessageKeys(since)

    // MARK: - Cleanup

    fun cleanupOldChats() {
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
        val remaining = loadCachedConversations().filter { it.isPinned || !it.timestamp.before(chatCutoff) }
        writeCachedConversations(remaining)
        ChatCacheStore.enforceRetention()
    }

    fun cleanupOldData() {
        cleanupOldStories()
        cleanupOldChats()
        val cutoff = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -MAX_DATA_AGE_DAYS) }.time
        val momentsDir = File(cacheDir(), "moments")
        if (momentsDir.exists()) {
            momentsDir.listFiles()?.forEach { file ->
                val section = file.nameWithoutExtension
                val fresh = loadCachedMoments(section).filter { it.lastSyncedAt.after(cutoff) }
                writeCachedMoments(section, fresh)
            }
        }
        trimCachedUsersByAge(cutoff)
        trimCachedUsers()
    }

    fun clearAll() {
        prefs().edit().clear().apply()
        cacheDir().deleteRecursively()
        MessagePersistenceStore.clearAll()
    }

    fun getCacheStats(): String {
        val feed = loadCachedMoments("feed").size
        val explore = loadCachedMoments("explore").size
        val stories = loadAllCachedStories().size
        val actions = loadPendingActions().size
        val messages = cachedMessageCount()
        return "feed=$feed explore=$explore stories=$stories actions=$actions messages=$messages"
    }

    // MARK: - Internals

    private fun updateMomentsAcrossSections(momentId: String, transform: (CachedMoment) -> CachedMoment) {
        val momentsDir = File(cacheDir(), "moments")
        if (!momentsDir.exists()) return
        momentsDir.listFiles()?.forEach { file ->
            val section = file.nameWithoutExtension
            val updated = loadCachedMoments(section).map { cached ->
                if (cached.momentId == momentId) transform(cached) else cached
            }
            writeCachedMoments(section, updated)
        }
    }

    /** ≡ iOS trimCachedUsersToLimit — excluye cacheSection == currentUser. */
    private fun trimCachedUsers() {
        val entries = prefs().all.keys.filter { it.startsWith(KEY_USER_PREFIX) }.mapNotNull { key ->
            val raw = prefs().getString(key, null) ?: return@mapNotNull null
            val cached = CachedUser.decodeFromPrefsJson(raw) ?: return@mapNotNull null
            if (cached.cacheSection == "currentUser") return@mapNotNull null
            Triple(key, cached.userId, cached.lastSyncedAt)
        }
        if (entries.size <= MAX_CACHED_USERS) return
        val toRemove = entries.sortedBy { it.third.time }.take(entries.size - MAX_CACHED_USERS)
        val editor = prefs().edit()
        toRemove.forEach { (key, _, _) -> editor.remove(key) }
        editor.apply()
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

    private fun saveSearchHistory(searches: List<CachedSearch>) {
        val arr = JSONArray().apply {
            searches.forEach { search ->
                put(JSONObject().apply {
                    put("query", search.query)
                    put("type", search.type)
                    search.targetId?.let { put("targetId", it) }
                    put("timestamp", search.timestamp.time)
                })
            }
        }
        prefs().edit().putString(KEY_SEARCH_HISTORY, arr.toString()).apply()
    }

    private fun loadAllActions(): List<CachedAction> {
        val raw = prefs().getString(KEY_PENDING_ACTIONS, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CachedAction(
                    id = obj.getString("id"),
                    type = obj.getString("type"),
                    status = obj.getString("status"),
                    payloadData = Base64.getDecoder().decode(obj.getString("payloadData")),
                    createdAt = Date(obj.getLong("createdAt")),
                    retryCount = obj.optInt("retryCount"),
                    lastError = obj.optString("lastError").takeIf { obj.has("lastError") && !obj.isNull("lastError") },
                    lastAttemptAt = obj.optLong("lastAttemptAt").takeIf { obj.has("lastAttemptAt") }?.let { Date(it) },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun saveAllActions(actions: List<CachedAction>) {
        val arr = JSONArray().apply {
            actions.forEach { action ->
                put(JSONObject().apply {
                    put("id", action.id)
                    put("type", action.type)
                    put("status", action.status)
                    put("payloadData", Base64.getEncoder().encodeToString(action.payloadData))
                    put("createdAt", action.createdAt.time)
                    put("retryCount", action.retryCount)
                    action.lastError?.let { put("lastError", it) }
                    action.lastAttemptAt?.let { put("lastAttemptAt", it.time) }
                })
            }
        }
        prefs().edit().putString(KEY_PENDING_ACTIONS, arr.toString()).apply()
    }

    private fun loadConversationPreviews(): Map<String, JSONObject> {
        val raw = prefs().getString(KEY_CONVERSATION_PREVIEWS, null) ?: return emptyMap()
        return runCatching {
            val obj = JSONObject(raw)
            obj.keys().asSequence().associateWith { obj.getJSONObject(it) }
        }.getOrDefault(emptyMap())
    }

    private fun saveConversationPreviews(previews: Map<String, JSONObject>) {
        val obj = JSONObject()
        previews.forEach { (k, v) -> obj.put(k, v) }
        prefs().edit().putString(KEY_CONVERSATION_PREVIEWS, obj.toString()).apply()
    }

    private fun loadCachedConversations(): List<CachedConversation> {
        val file = conversationsFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { decodeCachedConversation(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun writeCachedConversations(conversations: List<CachedConversation>) {
        val arr = JSONArray().apply { conversations.forEach { put(encodeCachedConversation(it)) } }
        conversationsFile().writeText(arr.toString())
    }

    private fun loadCachedNotifications(): List<CachedNotification> {
        val file = notificationsFile()
        if (!file.exists()) return emptyList()
        return runCatching {
            val arr = JSONArray(file.readText())
            (0 until arr.length()).mapNotNull { decodeCachedNotification(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    private fun writeCachedNotifications(notifications: List<CachedNotification>) {
        val arr = JSONArray().apply { notifications.forEach { put(encodeCachedNotification(it)) } }
        notificationsFile().writeText(arr.toString())
    }

    private data class DiskWarmResult(val mediaUrl: String?, val thumbnailUrl: String?, val changed: Boolean)

    private fun applyDiskWarm(message: EnhancedMessage): DiskWarmResult {
        val (mediaUrl, thumbnailUrl) = ChatCacheStore.localURLsIfPresent(message)
        var changed = false
        if (mediaUrl != null && (message.mediaUrl != mediaUrl)) changed = true
        if (thumbnailUrl != null && (message.thumbnailUrl != thumbnailUrl)) changed = true
        return DiskWarmResult(mediaUrl, thumbnailUrl, changed)
    }

    /** Bloquea en IO para lecturas síncronas desde UI legacy. */
    private fun <T> runBlockingIo(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { block() }

    private fun markNotificationPending(notificationId: String, pending: Boolean) {
        val updated = loadCachedNotifications().map { notification ->
            if (notification.id == notificationId) notification.copy(isPending = pending, lastSyncedAt = Date())
            else notification
        }
        writeCachedNotifications(updated)
    }

    private fun trimCachedUsersByAge(cutoff: Date) {
        val editor = prefs().edit()
        prefs().all.keys.filter { it.startsWith(KEY_USER_PREFIX) }.forEach { key ->
            val raw = prefs().getString(key, null) ?: return@forEach
            val cached = CachedUser.decodeFromPrefsJson(raw) ?: return@forEach
            // ≡ iOS: no borrar cacheSection == currentUser
            if (cached.cacheSection == "currentUser") return@forEach
            if (cached.lastSyncedAt.before(cutoff)) {
                editor.remove(key)
            }
        }
        editor.apply()
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
