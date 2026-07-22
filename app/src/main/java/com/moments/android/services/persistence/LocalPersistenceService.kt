package com.moments.android.services.persistence

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.AppUser
import com.moments.android.models.Conversation
import com.moments.android.models.DeleteMomentPayload
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.FollowRequestActionPayload
import com.moments.android.models.MarkAsReadPayload
import com.moments.android.models.MediaItem
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageSyncCursor
import com.moments.android.models.MessageType
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
import com.moments.android.models.decodeMessages
import com.moments.android.models.encodeMessages
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
 * Port progresivo de LocalPersistenceService.swift — StorySeenStateService vive aparte.
 * Almacenamiento JSON vs SwiftData es divergencia de plataforma; búsqueda usa [SearchNormalization]
 * (paridad iOS folding diacritic/case insensitive).
 */
object LocalPersistenceService {

    private const val PREFS = "moments_local_persistence"
    private const val KEY_CURRENT_USER_ID = "currentUserId"
    private const val KEY_USER_PREFIX = "user_"
    private const val KEY_PENDING_ACTIONS = "pending_actions"
    private const val KEY_CONVERSATION_PREVIEWS = "conversation_previews"
    private const val KEY_CONNECTIONS_PREFIX = "connections_"
    private const val KEY_SEARCH_HISTORY = "search_history"

    private const val MAX_CONVERSATIONS = 200
    private const val MAX_NOTIFICATIONS = 100

    private const val MAX_FEED_MOMENTS = 100
    private const val MAX_EXPLORE_MOMENTS = 50
    private const val MAX_CACHED_USERS = 200
    private const val MAX_DATA_AGE_DAYS = 7
    private const val MAX_SEARCHES = 20

    private const val RECENT_CHAT_WINDOW_SIZE = 20
    private const val STALE_CHAT_WINDOW_SIZE = 6
    private const val STALE_CHAT_THRESHOLD_DAYS = 45

    @Volatile private var appContext: Context? = null
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    class ActionPersistenceException(message: String) : Exception(message)

    fun initialize(context: Context) {
        if (appContext == null) {
            appContext = context.applicationContext
            MessagePersistenceStore.initialize(context)
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

    fun saveCurrentUser(user: AppUser) = saveUser(user, section = "currentUser").also {
        prefs().edit().putString(KEY_CURRENT_USER_ID, user.id).apply()
    }

    fun loadCurrentUser(): AppUser? {
        val id = prefs().getString(KEY_CURRENT_USER_ID, null) ?: return null
        return loadUser(id)
    }

    fun clearCurrentUser() {
        val id = prefs().getString(KEY_CURRENT_USER_ID, null)
        val editor = prefs().edit().remove(KEY_CURRENT_USER_ID)
        if (id != null) editor.remove(KEY_USER_PREFIX + id)
        editor.apply()
    }

    // MARK: - Users

    fun saveUser(user: AppUser, section: String = "profile") {
        prefs().edit()
            .putString(KEY_USER_PREFIX + user.id, encodeUser(user, section))
            .apply()
        trimCachedUsers()
    }

    fun loadUser(userId: String): AppUser? {
        val raw = prefs().getString(KEY_USER_PREFIX + userId, null) ?: return null
        return decodeUser(raw)
    }

    // MARK: - Outbox / CachedAction

    fun saveActionOrThrow(action: CachedAction) {
        val actions = loadAllActions().toMutableList()
        if (actions.any { it.id == action.id }) return
        actions += action
        saveAllActions(actions)
    }

    fun saveAction(action: CachedAction) {
        runCatching { saveActionOrThrow(action) }.onFailure { return }
        val isUpload = action.type == CachedAction.ActionType.MOMENT_UPLOAD.raw ||
            action.type == CachedAction.ActionType.STORY_UPLOAD.raw
        if (NetworkMonitor.isConnected && !isUpload) {
            ioScope.launch { OfflineSyncService.syncPendingActions(requireAutomaticSync = false) }
        }
    }

    fun loadPendingActions(): List<CachedAction> {
        return loadAllActions().filter {
            it.status == CachedAction.ActionStatus.PENDING.raw ||
                it.status == CachedAction.ActionStatus.EXECUTING.raw
        }.sortedBy { it.createdAt }
    }

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
        return loadConnectionRecords(currentUserId).any {
            it.targetId == targetUserId && it.type == "following"
        }
    }

    private fun saveConnectionList(userId: String, users: List<AppUser>, type: String) {
        val remaining = loadConnectionRecords(userId).filter { it.type != type }
        val updated = remaining + users.map { CachedConnection(userId, it.id, type) }
        saveConnectionRecords(userId, updated)
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

    fun loadFeedMoments(): List<Moment> = loadMoments("feed")

    fun loadExploreMoments(): List<Moment> = loadMoments("explore")

    fun loadProfileMoments(userId: String, viewerId: String? = null): List<Moment> =
        loadMoments(profileMomentsSection(userId, viewerId))

    private fun profileMomentsSection(userId: String, viewerId: String?): String =
        if (!viewerId.isNullOrEmpty()) "profile_${viewerId}_$userId" else "profile_$userId"

    private fun saveMoments(moments: List<Moment>, section: String, sync: Boolean, maxCount: Int) {
        val existing = if (sync) emptyList() else loadCachedMoments(section)
        val existingMap = existing.associateBy { it.momentId }.toMutableMap()
        moments.forEach { moment ->
            val id = moment.id ?: return@forEach
            existingMap[id] = momentToCachedMoment(moment, section)
        }
        val merged = existingMap.values.sortedByDescending { it.timestamp.time }.take(maxCount)
        writeCachedMoments(section, merged)
    }

    private fun loadMoments(section: String): List<Moment> =
        loadCachedMoments(section).mapNotNull { it.toMoment() }

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
            storyToCachedStory(story)?.let { map[id] = it }
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
            val reactions = decodeReactions(cached.reactionsData).toMutableMap()
            val users = reactions.getOrPut(reaction) { mutableListOf() }.toMutableList()
            if (users.contains(userId)) users.remove(userId) else users.add(userId)
            if (users.isEmpty()) reactions.remove(reaction) else reactions[reaction] = users
            cached.copy(reactionsData = encodeReactions(reactions), lastSyncedAt = Date())
        }
    }

    fun updateCommentCountLocally(momentId: String, increment: Int) {
        updateMomentsAcrossSections(momentId) { cached ->
            val current = cached.commentCount ?: 0
            cached.copy(commentCount = maxOf(0, current + increment), lastSyncedAt = Date())
        }
    }

    fun toggleFollowLocally(currentUserId: String, targetUserId: String, isFollow: Boolean) {
        val connections = loadConnectionRecords(currentUserId).toMutableList()
        connections.removeAll { it.targetId == targetUserId && it.type == "following" }
        if (isFollow) {
            connections += CachedConnection(currentUserId, targetUserId, "following")
        }
        saveConnectionRecords(currentUserId, connections)
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
                payloadData = encodeDeleteMomentPayload(payload),
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
                payloadData = encodeProfileUpdatePayload(payload),
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
                payloadData = encodeFollowRequestPayload(payload),
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
                payloadData = encodeFollowRequestPayload(payload),
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
                payloadData = encodeReportPayload(payload),
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
                payloadData = encodeMarkAsReadPayload(payload),
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
            map[id] = conversationToCached(conv)
        }
        writeCachedConversations(map.values.sortedWith(compareByDescending<CachedConversation> { it.isPinned }.thenByDescending { it.timestamp }).take(MAX_CONVERSATIONS))
    }

    fun loadConversations(): List<Conversation> =
        loadCachedConversations()
            .sortedWith(compareByDescending<CachedConversation> { it.isPinned }.thenByDescending { it.timestamp })
            .map { it.toConversation() }

    fun isConversationArchived(conversationId: String, userId: String): Boolean {
        val conversation = loadCachedConversations().firstOrNull { it.id == conversationId } ?: return false
        return conversation.isArchived
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
            val readStatus = decodeStringBoolMap(cached.readStatusData).toMutableMap()
            if (readStatus[currentUserId] != true) {
                readStatus[currentUserId] = true
                conversations[index] = cached.copy(readStatusData = encodeStringBoolMap(readStatus))
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
            map[id] = notificationToCached(notification)
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
        val previewText = messagePreview(message)
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val conversations = loadCachedConversations().toMutableList()
        val index = conversations.indexOfFirst { it.id == message.conversationId }
        if (index >= 0) {
            val cached = conversations[index]
            val readStatus = decodeStringBoolMap(cached.readStatusData).toMutableMap()
            if (currentUserId.isNotEmpty() && message.senderId != currentUserId) {
                readStatus[currentUserId] = false
            }
            conversations[index] = cached.copy(
                lastMessage = previewText,
                timestamp = message.timestamp,
                lastMessageSenderId = message.senderId,
                lastSyncedAt = Date(),
                readStatusData = encodeStringBoolMap(readStatus),
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
                readStatusData = encodeStringBoolMap(readStatus),
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

    private fun trimCachedUsers() {
        val allKeys = prefs().all.keys.filter { it.startsWith(KEY_USER_PREFIX) }
        if (allKeys.size <= MAX_CACHED_USERS) return
        val users = allKeys.mapNotNull { key ->
            prefs().getString(key, null)?.let { raw ->
                runCatching {
                    val json = JSONObject(raw)
                    key.removePrefix(KEY_USER_PREFIX) to Date(json.optLong("lastSyncedAt", 0L))
                }.getOrNull()
            }
        }.sortedBy { it.second }
        val toRemove = users.take(allKeys.size - MAX_CACHED_USERS)
        val editor = prefs().edit()
        toRemove.forEach { (id, _) -> editor.remove(KEY_USER_PREFIX + id) }
        editor.apply()
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
        val currentUserId = prefs().getString(KEY_CURRENT_USER_ID, null)
        val editor = prefs().edit()
        prefs().all.keys.filter { it.startsWith(KEY_USER_PREFIX) }.forEach { key ->
            val userId = key.removePrefix(KEY_USER_PREFIX)
            if (userId == currentUserId) return@forEach
            val raw = prefs().getString(key, null) ?: return@forEach
            val lastSyncedAt = runCatching {
                Date(JSONObject(raw).optLong("lastSyncedAt", System.currentTimeMillis()))
            }.getOrDefault(Date())
            if (lastSyncedAt.before(cutoff)) {
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
        MessageType.CHAT_NOTICE -> "Notice"
    }

    private fun encodeProfileUpdatePayload(payload: ProfileUpdatePayload): ByteArray = JSONObject().apply {
        put("userId", payload.userId)
        payload.bio?.let { put("bio", it) }
        payload.oldBio?.let { put("oldBio", it) }
        payload.websiteUrl?.let { put("websiteUrl", it) }
        payload.oldWebsiteUrl?.let { put("oldWebsiteUrl", it) }
        payload.interests?.let { put("interests", JSONArray(it)) }
        payload.profileImageLocalPath?.let { put("profileImageLocalPath", it) }
        put("isImageUpdate", payload.isImageUpdate)
    }.toString().toByteArray()

    private fun encodeFollowRequestPayload(payload: FollowRequestActionPayload): ByteArray = JSONObject().apply {
        put("notificationId", payload.notificationId)
        put("senderId", payload.senderId)
        put("recipientId", payload.recipientId)
        put("isAccept", payload.isAccept)
    }.toString().toByteArray()

    private fun encodeReportPayload(payload: ReportActionPayload): ByteArray = JSONObject().apply {
        put("reporterId", payload.reporterId)
        put("reportedUserId", payload.reportedUserId)
        put("reportedContentType", payload.reportedContentType)
        put("reportedContentId", payload.reportedContentId)
        put("category", payload.category)
        put("description", payload.description)
        put("priority", payload.priority)
    }.toString().toByteArray()

    private fun encodeMarkAsReadPayload(payload: MarkAsReadPayload): ByteArray = JSONObject().apply {
        put("notificationId", payload.notificationId)
        put("userId", payload.userId)
    }.toString().toByteArray()

    private fun encodeDeleteMomentPayload(payload: DeleteMomentPayload): ByteArray = JSONObject().apply {
        put("momentId", payload.momentId)
        put("userId", payload.userId)
        payload.imagePath?.let { put("imagePath", it) }
        payload.videoUrl?.let { put("videoUrl", it) }
    }.toString().toByteArray()

    private fun encodeUser(user: AppUser, section: String): String = JSONObject().apply {
        put("id", user.id)
        put("username", user.username)
        put("email", user.email)
        put("bio", user.bio)
        put("profileImagePath", user.profileImagePath)
        put("websiteUrl", user.websiteUrl)
        put("profileNote", user.profileNote)
        put("followersCount", user.followersCount)
        put("followingCount", user.followingCount)
        put("momentsCount", user.momentsCount)
        put("isVerified", user.isVerified)
        put("isPrivate", user.isPrivate)
        put("isActive", user.isActive)
        put("showMutuals", user.showMutuals)
        put("showFollowing", user.showFollowing)
        put("showFollowers", user.showFollowers)
        put("showReadReceipts", user.showReadReceipts)
        put("selectedProfileTheme", user.selectedProfileTheme)
        put("interests", JSONArray(user.interests))
        put("blockedUsers", JSONArray(user.blockedUsers))
        put("bestFriends", JSONArray(user.bestFriends))
        put("cacheSection", section)
        put("lastSyncedAt", System.currentTimeMillis())
    }.toString()

    private fun decodeUser(raw: String): AppUser? = runCatching {
        val json = JSONObject(raw)
        AppUser(
            id = json.getString("id"),
            username = json.optString("username", "Usuario Desconocido"),
            email = json.optString("email"),
            interests = json.optJSONArray("interests")?.toStringList() ?: emptyList(),
            profileImagePath = json.stringOrNull("profileImagePath"),
            bio = json.stringOrNull("bio"),
            blockedUsers = json.optJSONArray("blockedUsers")?.toStringList() ?: emptyList(),
            isPrivate = json.optBoolean("isPrivate", false),
            showMutuals = json.optBoolean("showMutuals", true),
            showFollowing = json.optBoolean("showFollowing", true),
            showFollowers = json.optBoolean("showFollowers", true),
            bestFriends = json.optJSONArray("bestFriends")?.toStringList() ?: emptyList(),
            websiteUrl = json.stringOrNull("websiteUrl"),
            profileNote = json.stringOrNull("profileNote"),
            followersCount = json.optInt("followersCount"),
            followingCount = json.optInt("followingCount"),
            momentsCount = json.optInt("momentsCount"),
            isActive = json.optBoolean("isActive", true),
            selectedProfileTheme = json.stringOrNull("selectedProfileTheme"),
            isVerified = json.optBoolean("isVerified", false),
            showReadReceipts = json.optBoolean("showReadReceipts", true),
        )
    }.getOrNull()
}

// MARK: - CachedMoment conversions

private fun momentToCachedMoment(moment: Moment, section: String): CachedMoment = CachedMoment(
    momentId = moment.id ?: java.util.UUID.randomUUID().toString(),
    authorId = moment.authorId,
    username = moment.username,
    content = moment.content,
    imagePath = moment.imagePath,
    videoUrl = moment.videoUrl,
    timestamp = moment.timestamp,
    commentCount = moment.commentCount,
    profileImagePath = moment.profileImagePath,
    location = moment.location,
    audience = moment.audience,
    aspectRatio = moment.aspectRatio,
    thumbnailUrl = moment.thumbnailUrl,
    videoDuration = moment.videoDuration,
    videoFileSize = moment.videoFileSize,
    videoResolution = moment.videoResolution,
    customListId = moment.customListId,
    disableComments = moment.disableComments,
    hideLikeCounts = moment.hideLikeCounts,
    allowSharing = moment.allowSharing,
    scheduledDate = moment.scheduledDate,
    isPinned = moment.isPinned,
    pinnedAt = moment.pinnedAt,
    gridPreviewScale = moment.gridPreviewScale,
    gridPreviewOffsetX = moment.gridPreviewOffsetX,
    gridPreviewOffsetY = moment.gridPreviewOffsetY,
    gridPreviewFitMode = moment.gridPreviewFitMode,
    gridPreviewBackground = moment.gridPreviewBackground,
    hasHiddenLayers = moment.hasHiddenLayers,
    hiddenLayerCount = moment.hiddenLayerCount,
    locationLatitude = moment.locationCoordinate?.latitude,
    locationLongitude = moment.locationCoordinate?.longitude,
    reactionsData = encodeReactions(moment.reactions),
    mediaItemsData = encodeMediaItems(moment.mediaItems),
    taggedUsersData = moment.taggedUsers?.let { JSONArray(it).toString().toByteArray() },
    mentionedUsersData = moment.mentionedUsers?.let { JSONArray(it).toString().toByteArray() },
    lastSyncedAt = Date(),
    feedSection = section,
)

private fun CachedMoment.toMoment(): Moment? = Moment(
    id = momentId,
    authorId = authorId,
    username = username,
    content = content,
    imagePath = imagePath,
    videoUrl = videoUrl,
    timestamp = timestamp,
    reactions = decodeReactions(reactionsData),
    commentCount = commentCount ?: 0,
    profileImagePath = profileImagePath,
    taggedUsers = taggedUsersData?.let { JSONArray(String(it)).toStringList() },
    mentionedUsers = mentionedUsersData?.let { JSONArray(String(it)).toStringList() },
    location = location,
    locationCoordinate = if (locationLatitude != null && locationLongitude != null) {
        Moment.LocationCoordinate(locationLatitude, locationLongitude)
    } else null,
    audience = audience,
    mediaItems = decodeMediaItems(mediaItemsData),
    aspectRatio = aspectRatio,
    customListId = customListId,
    thumbnailUrl = thumbnailUrl,
    videoDuration = videoDuration,
    videoFileSize = videoFileSize,
    videoResolution = videoResolution,
    disableComments = disableComments ?: false,
    hideLikeCounts = hideLikeCounts ?: false,
    allowSharing = allowSharing ?: true,
    scheduledDate = scheduledDate,
    isPinned = isPinned,
    pinnedAt = pinnedAt,
    gridPreviewScale = gridPreviewScale,
    gridPreviewOffsetX = gridPreviewOffsetX,
    gridPreviewOffsetY = gridPreviewOffsetY,
    gridPreviewFitMode = gridPreviewFitMode,
    gridPreviewBackground = gridPreviewBackground,
    hasHiddenLayers = hasHiddenLayers ?: false,
    hiddenLayerCount = hiddenLayerCount ?: 0,
)

private fun storyToCachedStory(story: Story): CachedStory? {
    val id = story.id ?: return null
    val mediaJson = encodeMediaItem(story.mediaItem) ?: return null
    return CachedStory(
        id = id,
        authorId = story.authorId,
        username = story.username,
        profileImagePath = story.profileImagePath,
        timestamp = story.timestamp,
        expirationDate = story.expirationDate,
        expirationHours = story.expirationHours,
        mediaItemData = mediaJson,
        audience = story.audience,
        customListId = story.customListId,
        text = story.text,
        textPositionData = story.textPosition?.let { JSONObject(mapOf("x" to it.x, "y" to it.y)).toString().toByteArray() },
        textStyle = story.textStyle,
        aspectRatio = story.aspectRatio,
        backgroundFrameURL = story.backgroundFrameURL,
        backgroundBlurredFrameURL = story.backgroundBlurredFrameURL,
        chainId = story.chainId,
        chainPosition = story.chainPosition,
        chainTitle = story.chainTitle,
        drawingData = story.drawingData,
        cachedAt = Date(),
    )
}

private fun CachedStory.toStory(): Story {
    val mediaItem = decodeMediaItem(mediaItemData) ?: MediaItem(type = MediaItem.MediaType.IMAGE, url = "")
    val textPosition = textPositionData?.let {
        val obj = JSONObject(String(it))
        com.moments.android.models.Point(obj.getDouble("x"), obj.getDouble("y"))
    }
    return Story(
        id = id,
        authorId = authorId,
        duration = 15.0,
        expirationHours = expirationHours ?: if (chainId != null) 48 else 24,
        expirationDate = expirationDate,
        mediaItem = mediaItem,
        profileImagePath = profileImagePath,
        timestamp = timestamp,
        username = username,
        audience = audience,
        customListId = customListId,
        text = text,
        textPosition = textPosition,
        textStyle = textStyle,
        aspectRatio = aspectRatio,
        backgroundFrameURL = backgroundFrameURL,
        backgroundBlurredFrameURL = backgroundBlurredFrameURL,
        chainId = chainId,
        chainPosition = chainPosition,
        chainTitle = chainTitle,
        drawingData = drawingData,
    )
}

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
        commentCount = obj.optInt("commentCount"),
        profileImagePath = obj.stringOrNull("profileImagePath"),
        location = obj.stringOrNull("location"),
        audience = obj.stringOrNull("audience"),
        aspectRatio = obj.stringOrNull("aspectRatio"),
        thumbnailUrl = obj.stringOrNull("thumbnailUrl"),
        videoDuration = obj.optDoubleOrNull("videoDuration"),
        videoFileSize = obj.optLongOrNull("videoFileSize"),
        videoResolution = obj.stringOrNull("videoResolution"),
        customListId = obj.stringOrNull("customListId"),
        disableComments = obj.optBoolean("disableComments"),
        hideLikeCounts = obj.optBoolean("hideLikeCounts"),
        allowSharing = obj.optBoolean("allowSharing", true),
        scheduledDate = obj.optLongOrNull("scheduledDate")?.let { Date(it) },
        hasHiddenLayers = obj.optBoolean("hasHiddenLayers"),
        hiddenLayerCount = obj.optInt("hiddenLayerCount"),
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

private fun encodeReactions(reactions: Map<String, List<String>>): ByteArray =
    JSONObject(reactions.mapValues { (_, v) -> JSONArray(v) }).toString().toByteArray()

private fun decodeReactions(data: ByteArray?): Map<String, List<String>> {
    if (data == null) return emptyMap()
    return runCatching {
        val obj = JSONObject(String(data))
        obj.keys().asSequence().associateWith { key ->
            obj.getJSONArray(key).toStringList()
        }
    }.getOrDefault(emptyMap())
}

private fun encodeMediaItems(items: List<MediaItem>?): ByteArray? {
    if (items == null) return null
    val arr = JSONArray()
    items.forEach { item ->
        arr.put(JSONObject().apply {
            put("id", item.id)
            put("type", item.type.raw)
            put("url", item.url)
            item.aspectRatio?.let { put("aspectRatio", it) }
            item.thumbnailUrl?.let { put("thumbnailUrl", it) }
        })
    }
    return arr.toString().toByteArray()
}

private fun decodeMediaItems(data: ByteArray?): List<MediaItem>? {
    if (data == null) return null
    return runCatching {
        val arr = JSONArray(String(data))
        (0 until arr.length()).mapNotNull { i ->
            arr.optJSONObject(i)?.let { obj ->
                val type = MediaItem.MediaType.entries.firstOrNull { it.raw == obj.optString("type") }
                    ?: MediaItem.MediaType.IMAGE
                MediaItem(
                    id = obj.optString("id"),
                    type = type,
                    url = obj.getString("url"),
                    aspectRatio = obj.stringOrNull("aspectRatio"),
                    thumbnailUrl = obj.stringOrNull("thumbnailUrl"),
                )
            }
        }
    }.getOrNull()
}

private fun encodeMediaItem(item: MediaItem): ByteArray? = runCatching {
    JSONObject().apply {
        put("id", item.id)
        put("type", item.type.raw)
        put("url", item.url)
        item.aspectRatio?.let { put("aspectRatio", it) }
        item.thumbnailUrl?.let { put("thumbnailUrl", it) }
    }.toString().toByteArray()
}.getOrNull()

private fun decodeMediaItem(data: ByteArray): MediaItem? = runCatching {
    val obj = JSONObject(String(data))
    val type = MediaItem.MediaType.entries.firstOrNull { it.raw == obj.optString("type") }
        ?: MediaItem.MediaType.IMAGE
    MediaItem(
        id = obj.optString("id"),
        type = type,
        url = obj.getString("url"),
        aspectRatio = obj.stringOrNull("aspectRatio"),
        thumbnailUrl = obj.stringOrNull("thumbnailUrl"),
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

private fun conversationToCached(conversation: Conversation): CachedConversation = CachedConversation(
    id = conversation.id ?: java.util.UUID.randomUUID().toString(),
    participants = conversation.participants,
    lastMessage = conversation.lastMessage,
    timestamp = conversation.timestamp,
    readStatusData = encodeStringBoolMap(conversation.readStatus),
    otherParticipantId = conversation.otherParticipantId,
    otherParticipantUsername = conversation.otherParticipantUsername,
    otherParticipantProfileImagePath = conversation.otherParticipantProfileImagePath,
    lastSyncedAt = Date(),
)

private fun CachedConversation.toConversation(): Conversation = Conversation(
    id = id,
    participants = participants,
    lastMessage = lastMessage,
    timestamp = timestamp,
    readStatus = decodeStringBoolMap(readStatusData),
    otherParticipantId = otherParticipantId,
    otherParticipantUsername = otherParticipantUsername,
    otherParticipantProfileImagePath = otherParticipantProfileImagePath,
)

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

private fun notificationToCached(notification: MomentsNotification): CachedNotification = CachedNotification(
    id = notification.id ?: java.util.UUID.randomUUID().toString(),
    type = notification.type.raw,
    senderId = notification.senderId,
    senderUsername = notification.senderUsername,
    timestamp = notification.timestamp,
    isPending = notification.isPending,
    title = notification.title,
    message = notification.message,
    downloadURL = notification.downloadURL,
    momentId = notification.momentId,
    visitCount = notification.visitCount,
    storyId = notification.storyId,
    storyAuthorId = notification.storyAuthorId,
    storyPreviewUrl = notification.storyPreviewUrl,
    reaction = notification.reaction,
    reactionCount = notification.reactionCount,
    commentId = notification.commentId,
    echoId = notification.echoId,
    moderationScope = notification.moderationScope,
    totalParts = notification.totalParts,
    chainRole = notification.chainRole,
    lastSyncedAt = Date(),
)

private fun CachedNotification.toNotification(): MomentsNotification = MomentsNotification(
    id = id,
    type = com.moments.android.models.NotificationType.from(type)
        ?: com.moments.android.models.NotificationType.MESSAGE,
    senderId = senderId,
    senderUsername = senderUsername,
    timestamp = timestamp,
    isPending = isPending,
    title = title,
    message = message,
    downloadURL = downloadURL,
    momentId = momentId,
    visitCount = visitCount,
    storyId = storyId,
    storyAuthorId = storyAuthorId,
    storyPreviewUrl = storyPreviewUrl,
    reaction = reaction,
    reactionCount = reactionCount,
    commentId = commentId,
    echoId = echoId,
    moderationScope = moderationScope,
    totalParts = totalParts,
    chainRole = chainRole,
)

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

private fun encodeStringBoolMap(map: Map<String, Boolean>): ByteArray =
    JSONObject(map).toString().toByteArray()

private fun decodeStringBoolMap(data: ByteArray?): Map<String, Boolean> {
    if (data == null) return emptyMap()
    return runCatching {
        val obj = JSONObject(String(data))
        obj.keys().asSequence().associateWith { obj.getBoolean(it) }
    }.getOrDefault(emptyMap())
}
