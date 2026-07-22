package com.moments.android.notifications.services

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.MomentsApplication
import com.moments.android.models.NotificationType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Port de NotificationBadgeService.swift.
 * Widget/App Group → SharedPreferences en Android.
 */
object NotificationBadgeService {
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private const val PREFS = "notification_badge_prefs"

    private val _unreadNotificationsCount = MutableStateFlow(0)
    val unreadNotificationsCount: StateFlow<Int> = _unreadNotificationsCount.asStateFlow()

    private val _unreadMessagesCount = MutableStateFlow(0)
    val unreadMessagesCount: StateFlow<Int> = _unreadMessagesCount.asStateFlow()

    private val _unreadEchoesCount = MutableStateFlow(0)
    val unreadEchoesCount: StateFlow<Int> = _unreadEchoesCount.asStateFlow()

    private val _unreadTagsCount = MutableStateFlow(0)
    val unreadTagsCount: StateFlow<Int> = _unreadTagsCount.asStateFlow()

    private var messageListener: ListenerRegistration? = null
    private var currentUserId: String? = null
    private var notificationCollectJob: kotlinx.coroutines.Job? = null

    fun initialize(context: Context) {
        setupListeners()
    }

    fun setupListeners() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            cleanup()
            return
        }
        if (currentUserId == userId && messageListener != null && notificationCollectJob?.isActive == true) {
            return
        }
        if (currentUserId != userId) {
            messageListener?.remove()
            messageListener = null
            notificationCollectJob?.cancel()
            currentUserId = userId
        }
        if (notificationCollectJob?.isActive != true) {
            setupNotificationSubscription()
        }
        setupMessageListener(userId)
    }

    private fun setupNotificationSubscription() {
        notificationCollectJob?.cancel()
        notificationCollectJob = scope.launch {
            NotificationService.notifications.collectLatest { notifications ->
                val pending = notifications.filter { it.isPending }
                _unreadNotificationsCount.value = pending.size
                _unreadEchoesCount.value = pending.count { it.type == NotificationType.ECHO_SUGGESTION }
                _unreadTagsCount.value = pending.count { it.type == NotificationType.PHOTO_TAG }
                persistWidgetCounts()
                updateAppBadgeFromCounts()
            }
        }
    }

    fun refreshAllCounts(onComplete: (() -> Unit)? = null) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            onComplete?.invoke()
            return
        }
        scope.launch {
            runCatching {
                NotificationService.fetchNotificationsOnce(userId).getOrNull()?.let { notifications ->
                    val pending = notifications.filter { it.isPending }
                    _unreadNotificationsCount.value = pending.size
                    _unreadEchoesCount.value = pending.count { it.type == NotificationType.ECHO_SUGGESTION }
                    _unreadTagsCount.value = pending.count { it.type == NotificationType.PHOTO_TAG }
                    persistWidgetCounts()
                }
                val snapshot = db.collection("conversations")
                    .whereArrayContains("participants", userId)
                    .get()
                    .await()
                _unreadMessagesCount.value = countUnreadMessages(snapshot.documents.mapNotNull { it.data }, userId)
                prefs()?.edit()?.putInt(KEY_UNREAD_MESSAGES, _unreadMessagesCount.value)?.apply()
                updateAppBadgeFromCounts()
            }
            onComplete?.invoke()
        }
    }

    private fun setupMessageListener(userId: String) {
        messageListener?.remove()
        messageListener = db.collection("conversations")
            .whereArrayContains("participants", userId)
            .addSnapshotListener { snapshot, _ ->
                val docs = snapshot?.documents ?: return@addSnapshotListener
                _unreadMessagesCount.value = countUnreadMessages(docs.mapNotNull { it.data }, userId)
                prefs()?.edit()?.putInt(KEY_UNREAD_MESSAGES, _unreadMessagesCount.value)?.apply()
                updateAppBadgeFromCounts()
            }
    }

    private fun countUnreadMessages(documents: List<Map<String, Any?>>, userId: String): Int {
        var unreadConversations = 0
        for (data in documents) {
            @Suppress("UNCHECKED_CAST")
            val readStatus = data["readStatus"] as? Map<String, Boolean> ?: emptyMap()
            if (readStatus[userId] == false) unreadConversations++
        }
        return unreadConversations
    }

    fun updateAppBadgeFromCounts() {
        // Android no expone badge unificado como iOS; los counts viven en SharedPreferences para widgets futuros.
    }

    fun clearNotificationBadge() {
        _unreadNotificationsCount.value = 0
        _unreadEchoesCount.value = 0
        _unreadTagsCount.value = 0
        persistWidgetCounts()
        updateAppBadgeFromCounts()
    }

    fun clearMessageBadge() {
        _unreadMessagesCount.value = 0
        prefs()?.edit()?.putInt(KEY_UNREAD_MESSAGES, 0)?.apply()
        updateAppBadgeFromCounts()
    }

    fun clearAppBadge() = Unit

    fun cleanup() {
        messageListener?.remove()
        messageListener = null
        notificationCollectJob?.cancel()
        notificationCollectJob = null
        currentUserId = null
        _unreadNotificationsCount.value = 0
        _unreadMessagesCount.value = 0
        _unreadEchoesCount.value = 0
        _unreadTagsCount.value = 0
        persistWidgetCounts()
        prefs()?.edit()?.putInt(KEY_UNREAD_MESSAGES, 0)?.apply()
        clearAppBadge()
    }

    private fun persistWidgetCounts() {
        prefs()?.edit()?.apply {
            putInt(KEY_UNREAD_NOTIFICATIONS, _unreadNotificationsCount.value)
            putInt(KEY_UNREAD_ECHOES, _unreadEchoesCount.value)
            putInt(KEY_UNREAD_TAGS, _unreadTagsCount.value)
        }?.apply()
    }

    private fun prefs() = MomentsApplication.instance?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private const val KEY_UNREAD_NOTIFICATIONS = "widget_unread_notifications"
    private const val KEY_UNREAD_MESSAGES = "widget_unread_messages"
    private const val KEY_UNREAD_ECHOES = "widget_unread_echoes"
    private const val KEY_UNREAD_TAGS = "widget_unread_tags"
}
