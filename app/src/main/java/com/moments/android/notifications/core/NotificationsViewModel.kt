package com.moments.android.notifications.core

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldPath
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.notifications.services.NotificationService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

/** Port de NotificationsViewModel.swift */
class NotificationsViewModel(
    private val firestoreService: FirestoreService = FirestoreService(),
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val db = FirebaseFirestore.getInstance()

    private val _notifications = MutableStateFlow<List<MomentsNotification>>(emptyList())
    val notifications: StateFlow<List<MomentsNotification>> = _notifications.asStateFlow()

    private val _groupedByDate = MutableStateFlow<Map<String, List<NotificationGroup>>>(emptyMap())
    val groupedByDate: StateFlow<Map<String, List<NotificationGroup>>> = _groupedByDate.asStateFlow()

    private val _dateKeys = MutableStateFlow<List<String>>(emptyList())
    val dateKeys: StateFlow<List<String>> = _dateKeys.asStateFlow()

    private val _groupedNotifications = MutableStateFlow<List<NotificationGroup>>(emptyList())
    val groupedNotifications: StateFlow<List<NotificationGroup>> = _groupedNotifications.asStateFlow()

    private val _selectedTab = MutableStateFlow(NotificationsTab.ALL)
    val selectedTab: StateFlow<NotificationsTab> = _selectedTab.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _showError = MutableStateFlow(false)
    val showError: StateFlow<Boolean> = _showError.asStateFlow()

    private val _errorMessage = MutableStateFlow("")
    val errorMessage: StateFlow<String> = _errorMessage.asStateFlow()

    private val _pendingRequestsCount = MutableStateFlow(0)
    val pendingRequestsCount: StateFlow<Int> = _pendingRequestsCount.asStateFlow()

    private val _hasUnreadNotifications = MutableStateFlow(false)
    val hasUnreadNotifications: StateFlow<Boolean> = _hasUnreadNotifications.asStateFlow()

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _pendingDeletion = MutableStateFlow<NotificationService.PendingNotificationDeletion?>(null)
    val pendingDeletion: StateFlow<NotificationService.PendingNotificationDeletion?> = _pendingDeletion.asStateFlow()

    private val userProfileImageCache = mutableMapOf<String, String>()

    enum class NotificationsTab {
        ALL, REACTIONS, FOLLOWS, COMMENTS, STORY_REACTIONS, REQUESTS,
    }

    init {
        scope.launch {
            NotificationService.notifications.collectLatest {
                _notifications.value = it
                groupNotifications()
                updatePendingCounts()
            }
        }
        scope.launch { NotificationService.isLoading.collectLatest { _isLoading.value = it } }
        scope.launch { NotificationService.isLoadingMore.collectLatest { _isLoadingMore.value = it } }
        scope.launch { NotificationService.canLoadMore.collectLatest { _canLoadMore.value = it } }
        scope.launch { NotificationService.pendingDeletion.collectLatest { _pendingDeletion.value = it } }
    }

    fun refreshNotifications() {
        NotificationService.startObserving()
    }

    fun loadMoreNotifications() = NotificationService.loadMore()

    fun setSelectedTab(tab: NotificationsTab) {
        _selectedTab.value = tab
        groupNotifications()
    }

    fun markAsRead(notification: MomentsNotification) = NotificationService.markAsRead(notification)
    fun markAllAsRead() = NotificationService.markAllAsRead()
    fun deleteNotification(notification: MomentsNotification) = NotificationService.deleteNotification(notification)
    fun deleteNotificationGroup(group: NotificationGroup) = NotificationService.stageDeletion(group.notifications)
    fun undoPendingDeletion() = NotificationService.undoPendingDeletion()
    fun commitPendingDeletion() = NotificationService.commitPendingDeletion()

    fun acceptFollowRequest(group: NotificationGroup) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val notification = group.notifications.firstOrNull { it.type == NotificationType.FOLLOW_REQUEST } ?: return
        val notificationId = notification.id ?: return
        scope.launch {
            LocalPersistenceService.acceptFollowRequest(notificationId, notification.senderId, userId)
            _notifications.value = _notifications.value.map {
                if (it.id == notificationId) it.copy(isPending = false) else it
            }
            groupNotifications()
            updatePendingCounts()
        }
    }

    fun rejectFollowRequest(group: NotificationGroup) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            group.notifications.filter { it.type == NotificationType.FOLLOW_REQUEST }.forEach { notification ->
                val notificationId = notification.id ?: return@forEach
                LocalPersistenceService.rejectFollowRequest(notificationId, notification.senderId, userId)
                _notifications.value = _notifications.value.filter { it.id != notificationId }
            }
            groupNotifications()
            updatePendingCounts()
        }
    }

    fun checkIfFollowing(currentUserId: String, targetUserId: String, onResult: (Boolean) -> Unit) {
        val cached = LocalPersistenceService.isFollowing(targetUserId)
        onResult(cached)
        scope.launch {
            val network = firestoreService.isFollowing(currentUserId, targetUserId)
            if (network != cached) onResult(network)
        }
    }

    fun followUser(currentUserId: String, targetUserId: String, onComplete: (Throwable?) -> Unit) {
        scope.launch {
            onComplete(runCatching { firestoreService.followUser(currentUserId, targetUserId) }.exceptionOrNull())
        }
    }

    fun unfollowUser(currentUserId: String, targetUserId: String, onComplete: (Throwable?) -> Unit) {
        scope.launch {
            onComplete(runCatching { firestoreService.unfollowUser(currentUserId, targetUserId) }.exceptionOrNull())
        }
    }

    fun cancelFollowRequest(currentUserId: String, targetUserId: String, onComplete: (Throwable?) -> Unit) {
        scope.launch {
            onComplete(runCatching { firestoreService.cancelFollowRequest(currentUserId, targetUserId) }.exceptionOrNull())
        }
    }

    fun getProfileImagePath(userId: String): String? = userProfileImageCache[userId]
    fun updateProfileImageCache(userId: String, imagePath: String?) {
        if (imagePath != null) userProfileImageCache[userId] = imagePath
    }

    private fun updatePendingCounts() {
        _hasUnreadNotifications.value = NotificationService.unreadCount.value > 0
        _pendingRequestsCount.value = _notifications.value.count {
            it.type == NotificationType.FOLLOW_REQUEST && it.isPending
        }
    }

    private fun groupNotifications() {
        val filtered = _notifications.value.filter { matchesTab(it, _selectedTab.value) }
        val groupedDict = mutableMapOf<String, MutableList<MomentsNotification>>()

        for (notification in filtered) {
            val key = when {
                notification.type == NotificationType.NEW_FOLLOWER || notification.type == NotificationType.MUTUAL_CONNECTION ->
                    "${notification.type.raw}_agg_${sectionKey(notification.timestamp)}"
                isPerActorSocialNotification(notification.type) ->
                    "${notification.type.raw}_${notification.senderId}"
                notification.type == NotificationType.STORY_CHAIN_CONTINUED -> {
                    val chain = notification.chainId ?: notification.storyId ?: "general"
                    "storyChainContinued_$chain"
                }
                else -> {
                    val contentId = notification.commentId ?: notification.storyId ?: notification.momentId ?: "general"
                    val context = notification.mentionContext ?: inferredMentionContext(notification)
                    "${notification.type.raw}_${context}_$contentId"
                }
            }
            groupedDict.getOrPut(key) { mutableListOf() }.add(notification)
        }

        val groups = groupedDict.map { (key, list) ->
            val sorted = list.sortedByDescending { it.timestamp }
            val notifications = if ("_agg_" in key) {
                val seen = mutableSetOf<String>()
                sorted.filter { seen.add(it.senderId) }
            } else sorted
            NotificationGroup(key, notifications)
        }

        val tempSections = mutableMapOf<String, MutableList<NotificationGroup>>()
        groups.forEach { group ->
            val section = sectionKey(group.notifications.first().timestamp)
            tempSections.getOrPut(section) { mutableListOf() }.add(group)
        }
        tempSections.values.forEach { sectionGroups ->
            sectionGroups.sortByDescending { it.notifications.first().timestamp }
        }

        _groupedByDate.value = tempSections
        _dateKeys.value = listOf("New", "This Week", "This Month", "Earlier").filter { it in tempSections }
        _groupedNotifications.value = groups.sortedByDescending { it.notifications.first().timestamp }
        preloadSenderProfiles(filtered)
    }

    private fun matchesTab(notification: MomentsNotification, tab: NotificationsTab): Boolean = when (tab) {
        NotificationsTab.ALL -> true
        NotificationsTab.REACTIONS -> notification.type == NotificationType.REACTION
        NotificationsTab.FOLLOWS -> notification.type in setOf(
            NotificationType.NEW_FOLLOWER, NotificationType.MUTUAL_CONNECTION, NotificationType.REQUEST_ACCEPTED,
        )
        NotificationsTab.COMMENTS -> notification.type in setOf(NotificationType.COMMENT, NotificationType.LIKE) ||
            isMomentOrCommentMention(notification)
        NotificationsTab.STORY_REACTIONS -> notification.type in setOf(
            NotificationType.STORY_REACTION, NotificationType.STORY_CHAIN_CONTINUED,
        ) || isStoryMention(notification)
        NotificationsTab.REQUESTS -> notification.type == NotificationType.FOLLOW_REQUEST
    }

    private fun isStoryMention(notification: MomentsNotification) =
        notification.type == NotificationType.MENTION &&
            (notification.mentionContext == "story" || notification.storyId != null)

    private fun isMomentOrCommentMention(notification: MomentsNotification) =
        notification.type == NotificationType.MENTION && !isStoryMention(notification)

    private fun inferredMentionContext(notification: MomentsNotification): String = when {
        notification.type != NotificationType.MENTION -> "default"
        notification.storyId != null -> "story"
        notification.commentId != null -> "comment"
        notification.momentId != null -> "moment"
        else -> "mention"
    }

    private fun sectionKey(date: Date): String {
        val calendar = Calendar.getInstance()
        val now = Date()
        return when {
            isSameDay(calendar, date, now) || isYesterday(calendar, date, now) -> "New"
            calendar.apply { time = now; add(Calendar.DAY_OF_YEAR, -7) }.time.before(date) -> "This Week"
            calendar.apply { time = now; add(Calendar.MONTH, -1) }.time.before(date) -> "This Month"
            else -> "Earlier"
        }
    }

    private fun isSameDay(calendar: Calendar, a: Date, b: Date): Boolean {
        calendar.time = a
        val y1 = calendar.get(Calendar.YEAR)
        val d1 = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.time = b
        return y1 == calendar.get(Calendar.YEAR) && d1 == calendar.get(Calendar.DAY_OF_YEAR)
    }

    private fun isYesterday(calendar: Calendar, date: Date, now: Date): Boolean {
        calendar.time = now
        calendar.add(Calendar.DAY_OF_YEAR, -1)
        val y = calendar.get(Calendar.YEAR)
        val d = calendar.get(Calendar.DAY_OF_YEAR)
        calendar.time = date
        return calendar.get(Calendar.YEAR) == y && calendar.get(Calendar.DAY_OF_YEAR) == d
    }

    private fun preloadSenderProfiles(notifications: List<MomentsNotification>) {
        val uncached = notifications.map { it.senderId }.filter { it.isNotEmpty() && it !in userProfileImageCache }.distinct()
        if (uncached.isEmpty()) return
        scope.launch {
            uncached.chunked(30).forEach { chunk ->
                runCatching {
                    db.collection("users").whereIn(FieldPath.documentId(), chunk).get().await()
                }.getOrNull()?.documents?.forEach { doc ->
                    doc.getString("profileImagePath")?.takeIf { it.isNotEmpty() }?.let {
                        userProfileImageCache[doc.id] = it
                    }
                }
            }
        }
    }
}
