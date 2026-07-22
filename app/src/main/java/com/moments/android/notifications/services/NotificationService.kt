package com.moments.android.notifications.services

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.moments.android.models.MomentsNotification
import com.moments.android.models.NotificationType
import com.moments.android.models.toMap
import com.moments.android.services.persistence.LocalPersistenceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Port completo de Notificationservice.swift — fuente de verdad de notificaciones in-app.
 */
object NotificationService {
    private const val TAG = "NotificationService"
    private const val PAGE_SIZE = 20L
    private const val DELETION_UNDO_WINDOW_MS = 3_000L

    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _unreadCount = MutableStateFlow(0)
    val unreadCount: StateFlow<Int> = _unreadCount.asStateFlow()

    private val _notifications = MutableStateFlow<List<MomentsNotification>>(emptyList())
    val notifications: StateFlow<List<MomentsNotification>> = _notifications.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isLoadingMore = MutableStateFlow(false)
    val isLoadingMore: StateFlow<Boolean> = _isLoadingMore.asStateFlow()

    private val _canLoadMore = MutableStateFlow(true)
    val canLoadMore: StateFlow<Boolean> = _canLoadMore.asStateFlow()

    private val _pendingDeletion = MutableStateFlow<PendingNotificationDeletion?>(null)
    val pendingDeletion: StateFlow<PendingNotificationDeletion?> = _pendingDeletion.asStateFlow()

    data class PendingNotificationDeletion(
        val id: UUID = UUID.randomUUID(),
        val notifications: List<MomentsNotification>,
    )

    private var listener: ListenerRegistration? = null
    private var lastDocument: DocumentSnapshot? = null
    private val hiddenPendingDeletionIds = ConcurrentHashMap.newKeySet<String>()
    private var pendingDeletionJob: kotlinx.coroutines.Job? = null
    private var isFirstSnapshot = true

    fun initialize(context: Context) {
        startObserving()
    }

    fun startObserving() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        listener?.remove()
        _isLoading.value = true
        isFirstSnapshot = true

        val cached = LocalPersistenceService.loadNotifications()
        if (cached.isNotEmpty()) {
            _notifications.value = visibleNotifications(cached)
            updateUnreadCount()
            _isLoading.value = false
            Log.d(TAG, "Loaded ${cached.size} notifications from cache")
        }

        val query = db.collection("users").document(userId).collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(PAGE_SIZE)

        listener = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                _isLoading.value = false
                return@addSnapshotListener
            }
            val documents = snapshot?.documents ?: run {
                _isLoading.value = false
                return@addSnapshotListener
            }
            lastDocument = documents.lastOrNull()
            _canLoadMore.value = documents.size >= PAGE_SIZE
            val fetched = documents.mapNotNull { decodeNotificationDocument(it) }
            _notifications.value = visibleNotifications(fetched)
            LocalPersistenceService.saveNotifications(_notifications.value, sync = isFirstSnapshot)
            isFirstSnapshot = false
            updateUnreadCount()
            _isLoading.value = false
        }
    }

    fun stopObserving() {
        listener?.remove()
        listener = null
    }

    fun loadMore() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val lastDoc = lastDocument ?: return
        if (!_canLoadMore.value || _isLoadingMore.value) return

        scope.launch {
            _isLoadingMore.value = true
            runCatching {
                val snapshot = db.collection("users").document(userId).collection("notifications")
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .startAfter(lastDoc)
                    .limit(PAGE_SIZE)
                    .get()
                    .await()

                lastDocument = snapshot.documents.lastOrNull()
                _canLoadMore.value = snapshot.size() >= PAGE_SIZE
                val newNotifications = visibleNotifications(
                    snapshot.documents.mapNotNull { decodeNotificationDocument(it) },
                )
                _notifications.value = _notifications.value + newNotifications
            }
            _isLoadingMore.value = false
        }
    }

    private fun updateUnreadCount() {
        _unreadCount.value = _notifications.value.count { it.isPending }
        NotificationBadgeService.updateAppBadgeFromCounts()
    }

    private fun decodeNotificationDocument(doc: DocumentSnapshot): MomentsNotification? {
        val data = doc.data ?: return null
        val notification = MomentsNotification.from(doc.id, data) ?: return null
        return if (notification.id.isNullOrBlank()) notification.copy(id = doc.id) else notification
    }

    private fun saveNotification(notification: MomentsNotification, forUserId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (forUserId == currentUserId) return

        scope.launch {
            runCatching {
                var toSave = notification
                val ref = if (!notification.id.isNullOrBlank()) {
                    db.collection("users").document(forUserId)
                        .collection("notifications").document(notification.id!!)
                } else {
                    db.collection("users").document(forUserId)
                        .collection("notifications").document().also {
                            toSave = notification.copy(id = it.id)
                        }
                }
                ref.set(toSave.toMap()).await()
                LocalPersistenceService.saveNotifications(listOf(toSave))
            }.onFailure { Log.e(TAG, "Error saving notification: $it") }
        }
    }

    fun sendInteractionNotification(
        type: NotificationType,
        targetUserId: String,
        notificationId: String? = null,
        momentId: String? = null,
        storyId: String? = null,
        storyAuthorId: String? = null,
        commentId: String? = null,
        reaction: String? = null,
        senderUsername: String? = null,
        echoId: String? = null,
        mentionContext: String? = null,
        targetAuthorId: String? = null,
        targetAuthorUsername: String? = null,
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val username = senderUsername
            ?: LocalPersistenceService.loadCurrentUser()?.username
            ?: "Someone"

        val notification = MomentsNotification(
            id = notificationId,
            type = type,
            senderId = currentUserId,
            senderUsername = username,
            timestamp = Date(),
            isPending = true,
            momentId = momentId,
            storyId = storyId,
            storyAuthorId = storyAuthorId,
            mentionContext = mentionContext,
            targetAuthorId = targetAuthorId,
            targetAuthorUsername = targetAuthorUsername,
            reaction = reaction,
            commentId = commentId,
            echoId = echoId,
        )
        saveNotification(notification, targetUserId)
    }

    fun sendMentionNotification(targetUserId: String, momentId: String? = null, storyId: String? = null) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val context = when {
            storyId != null -> "story"
            momentId != null -> "moment"
            else -> null
        }
        sendInteractionNotification(
            type = NotificationType.MENTION,
            targetUserId = targetUserId,
            momentId = momentId,
            storyId = storyId,
            storyAuthorId = if (storyId != null) currentUserId else null,
            mentionContext = context,
            targetAuthorId = if (storyId != null) currentUserId else null,
        )
    }

    fun sendStoryMentionNotification(targetUserId: String, storyId: String, storyAuthorId: String) {
        sendInteractionNotification(
            type = NotificationType.MENTION,
            targetUserId = targetUserId,
            storyId = storyId,
            storyAuthorId = storyAuthorId,
            mentionContext = "story",
            targetAuthorId = storyAuthorId,
        )
    }

    fun sendMomentMentionNotification(
        targetUserId: String,
        momentId: String,
        momentAuthorId: String? = null,
        momentAuthorUsername: String? = null,
        commentText: String? = null,
        senderUsername: String? = null,
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        val notificationId = currentUserId?.let { "mention_moment_${momentId}_${it}_$targetUserId" }
        sendInteractionNotification(
            type = NotificationType.MENTION,
            targetUserId = targetUserId,
            notificationId = notificationId,
            momentId = momentId,
            reaction = commentText,
            senderUsername = senderUsername,
            mentionContext = "moment",
            targetAuthorId = momentAuthorId,
            targetAuthorUsername = momentAuthorUsername,
        )
    }

    fun sendCommentMentionNotification(
        targetUserId: String,
        momentId: String,
        momentAuthorId: String? = null,
        momentAuthorUsername: String? = null,
        commentId: String,
        commentText: String? = null,
        senderUsername: String? = null,
    ) {
        sendInteractionNotification(
            type = NotificationType.MENTION,
            targetUserId = targetUserId,
            momentId = momentId,
            commentId = commentId,
            reaction = commentText,
            senderUsername = senderUsername,
            mentionContext = "comment",
            targetAuthorId = momentAuthorId,
            targetAuthorUsername = momentAuthorUsername,
        )
    }

    fun sendPhotoTagNotification(
        targetUserId: String,
        momentId: String,
        momentAuthorId: String,
        momentAuthorUsername: String? = null,
        momentTitle: String? = null,
    ) {
        sendInteractionNotification(
            type = NotificationType.PHOTO_TAG,
            targetUserId = targetUserId,
            momentId = momentId,
            reaction = momentTitle,
            mentionContext = "photoTag",
            targetAuthorId = momentAuthorId,
            targetAuthorUsername = momentAuthorUsername,
        )
    }

    fun markAsRead(notification: MomentsNotification) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val notificationId = notification.id ?: return
        scope.launch {
            LocalPersistenceService.markNotificationAsRead(notificationId, userId)
        }
        _notifications.value = _notifications.value.map {
            if (it.id == notification.id) it.copy(isPending = false) else it
        }
        updateUnreadCount()
    }

    fun markAllAsRead() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val visiblePendingIds = _notifications.value.mapNotNull { if (it.isPending) it.id else null }
        _notifications.value = _notifications.value.map { it.copy(isPending = false) }
        _unreadCount.value = 0
        NotificationBadgeService.updateAppBadgeFromCounts()

        scope.launch {
            markSpecificNotificationsAsRead(userId, visiblePendingIds)
            markAllAsReadBatch(userId)
            markAllAsReadByScan(userId)
        }
    }

    private suspend fun markAllAsReadBatch(userId: String) {
        runCatching {
            val snapshot = db.collection("users").document(userId).collection("notifications")
                .whereEqualTo("isPending", true)
                .limit(500)
                .get()
                .await()
            if (snapshot.isEmpty) {
                markAllAsReadLegacyBatch(userId)
                return
            }
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.update(doc.reference, mapOf("isPending" to false, "isRead" to true))
            }
            batch.commit().await()
            if (snapshot.size() == 500) markAllAsReadBatch(userId) else markAllAsReadLegacyBatch(userId)
        }.onFailure { Log.e(TAG, "Error marking batch as read: $it") }
    }

    private suspend fun markAllAsReadLegacyBatch(userId: String) {
        runCatching {
            val snapshot = db.collection("users").document(userId).collection("notifications")
                .whereEqualTo("isRead", false)
                .limit(500)
                .get()
                .await()
            if (snapshot.isEmpty) return
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                batch.update(doc.reference, mapOf("isPending" to false, "isRead" to true))
            }
            batch.commit().await()
            if (snapshot.size() == 500) markAllAsReadLegacyBatch(userId)
        }.onFailure { Log.e(TAG, "Error marking legacy batch as read: $it") }
    }

    private suspend fun markSpecificNotificationsAsRead(userId: String, ids: List<String>) {
        if (ids.isEmpty()) return
        ids.distinct().chunked(400).forEach { chunk ->
            runCatching {
                val batch = db.batch()
                chunk.forEach { id ->
                    val ref = db.collection("users").document(userId)
                        .collection("notifications").document(id)
                    batch.set(ref, mapOf("isPending" to false, "isRead" to true), com.google.firebase.firestore.SetOptions.merge())
                }
                batch.commit().await()
            }.onFailure { Log.e(TAG, "Error marking specific notifications as read: $it") }
        }
    }

    private suspend fun markAllAsReadByScan(userId: String, startAfter: DocumentSnapshot? = null) {
        runCatching {
            var query = db.collection("users").document(userId).collection("notifications")
                .whereEqualTo("isRead", false)
                .limit(500)
            if (startAfter != null) query = query.startAfter(startAfter)
            val snapshot = query.get().await()
            if (snapshot.isEmpty) return
            val batch = db.batch()
            snapshot.documents.forEach { doc ->
                val data = doc.data ?: return@forEach
                val isPending = data["isPending"] as? Boolean
                val isRead = data["isRead"] as? Boolean
                if (isPending != false || isRead != true) {
                    batch.set(
                        doc.reference,
                        mapOf("isPending" to false, "isRead" to true),
                        com.google.firebase.firestore.SetOptions.merge(),
                    )
                }
            }
            batch.commit().await()
            if (snapshot.size() == 500) {
                snapshot.documents.lastOrNull()?.let { markAllAsReadByScan(userId, it) }
            }
        }.onFailure { Log.e(TAG, "Error marking scan batch as read: $it") }
    }

    fun deleteNotification(notification: MomentsNotification) = stageDeletion(listOf(notification))

    fun deleteNotifications(notificationsToDelete: List<MomentsNotification>) =
        stageDeletion(notificationsToDelete)

    fun stageDeletion(notificationsToDelete: List<MomentsNotification>) {
        if (FirebaseAuth.getInstance().currentUser?.uid == null) return
        val valid = notificationsToDelete.filter { !it.id.isNullOrBlank() }
        if (valid.isEmpty()) return
        if (_pendingDeletion.value != null) commitPendingDeletion()

        val ids = valid.mapNotNull { it.id }.toSet()
        hiddenPendingDeletionIds.addAll(ids)
        removeFromLocalState(valid)
        _pendingDeletion.value = PendingNotificationDeletion(notifications = valid)
        schedulePendingDeletionCommit()
    }

    fun undoPendingDeletion() {
        val pending = _pendingDeletion.value ?: return
        pendingDeletionJob?.cancel()
        pendingDeletionJob = null
        val ids = pending.notifications.mapNotNull { it.id }.toSet()
        hiddenPendingDeletionIds.removeAll(ids)
        val existingIds = _notifications.value.mapNotNull { it.id }.toSet()
        val restored = pending.notifications.filter { it.id !in existingIds }
        _notifications.value = (_notifications.value + restored).sortedByDescending { it.timestamp }
        LocalPersistenceService.saveNotifications(restored, sync = false)
        _pendingDeletion.value = null
        updateUnreadCount()
    }

    fun commitPendingDeletion() {
        val pending = _pendingDeletion.value ?: return
        pendingDeletionJob?.cancel()
        pendingDeletionJob = null
        commitDeletionToFirestore(pending.notifications)
        hiddenPendingDeletionIds.removeAll(pending.notifications.mapNotNull { it.id }.toSet())
        _pendingDeletion.value = null
    }

    private fun schedulePendingDeletionCommit() {
        val pendingId = _pendingDeletion.value?.id
        pendingDeletionJob?.cancel()
        pendingDeletionJob = scope.launch {
            delay(DELETION_UNDO_WINDOW_MS)
            if (_pendingDeletion.value?.id == pendingId) commitPendingDeletion()
        }
    }

    private fun visibleNotifications(fetched: List<MomentsNotification>): List<MomentsNotification> {
        if (hiddenPendingDeletionIds.isEmpty()) return fetched
        return fetched.filter { it.id !in hiddenPendingDeletionIds }
    }

    private fun removeFromLocalState(notificationsToDelete: List<MomentsNotification>) {
        val idSet = notificationsToDelete.mapNotNull { it.id }.toSet()
        _notifications.value = _notifications.value.filter { it.id !in idSet }
        updateUnreadCount()
        LocalPersistenceService.deleteNotifications(idSet.toList())
    }

    private fun commitDeletionToFirestore(notificationsToDelete: List<MomentsNotification>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        val ids = notificationsToDelete.mapNotNull { it.id }
        if (ids.isEmpty()) return
        scope.launch {
            runCatching {
                val batch = db.batch()
                ids.forEach { id ->
                    val ref = db.collection("users").document(userId)
                        .collection("notifications").document(id)
                    batch.delete(ref)
                }
                batch.commit().await()
            }.onFailure { Log.e(TAG, "Error deleting notifications: $it") }
        }
    }

    fun removeNotification(
        type: NotificationType,
        senderId: String,
        recipientId: String,
        momentId: String? = null,
        storyId: String? = null,
        commentId: String? = null,
        reaction: String? = null,
    ) {
        scope.launch {
            runCatching {
                var query = db.collection("users").document(recipientId).collection("notifications")
                    .whereEqualTo("type", type.raw)
                    .whereEqualTo("senderId", senderId)
                momentId?.let { query = query.whereEqualTo("momentId", it) }
                storyId?.let { query = query.whereEqualTo("storyId", it) }
                commentId?.let { query = query.whereEqualTo("commentId", it) }
                reaction?.let { query = query.whereEqualTo("reaction", it) }
                val snapshot = query.get().await()
                val batch = db.batch()
                snapshot.documents.forEach { batch.delete(it.reference) }
                batch.commit().await()
            }.onFailure { Log.e(TAG, "Error removing notification: $it") }
        }
    }

    suspend fun fetchNotificationsOnce(userId: String): Result<List<MomentsNotification>> = runCatching {
        db.collection("users").document(userId).collection("notifications")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .get()
            .await()
            .documents
            .mapNotNull { decodeNotificationDocument(it) }
    }

    fun resetOnSignOut() {
        stopObserving()
        _notifications.value = emptyList()
        _unreadCount.value = 0
        _pendingDeletion.value = null
        hiddenPendingDeletionIds.clear()
        lastDocument = null
        isFirstSnapshot = true
    }
}
