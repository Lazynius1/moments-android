package com.moments.android.notifications.services

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.models.MomentsNotification
import com.moments.android.views.messaging.services.ChatSessionEngine
import com.moments.android.utilities.HapticManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.Date

/** Port de InAppNotificationService.swift */
object InAppNotificationService {
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _currentNotification = MutableStateFlow<MomentsNotification?>(null)
    val currentNotification: StateFlow<MomentsNotification?> = _currentNotification.asStateFlow()

    private val _showBanner = MutableStateFlow(false)
    val showBanner: StateFlow<Boolean> = _showBanner.asStateFlow()

    private var dismissJob: Job? = null
    private var listenerStartTime = Date()
    private val reactionListeners = mutableMapOf<String, ListenerRegistration>()
    private val buzzListeners = mutableMapOf<String, ListenerRegistration>()
    private const val DISPLAY_DURATION_MS = 4_000L

    fun startListening() {
        if (FirebaseAuth.getInstance().currentUser?.uid == null) return
        clearFallbackListeners()
        listenerStartTime = Date()
        syncFallbackListeners(ChatSessionEngine.notificationConversationIdsForFallback())
    }

    fun stopListening() {
        clearFallbackListeners()
        dismissManually()
        _currentNotification.value = null
    }

    fun syncFallbackListeners(conversationIds: List<String>) {
        val targetIds = conversationIds.filter { it.isNotBlank() }.take(5).toSet()
        reactionListeners.keys.filter { it !in targetIds }.forEach { id ->
            reactionListeners.remove(id)?.remove()
        }
        buzzListeners.keys.filter { it !in targetIds }.forEach { id ->
            buzzListeners.remove(id)?.remove()
        }
        targetIds.forEach { conversationId ->
            attachReactionFallbackListener(conversationId)
            attachBuzzFallbackListener(conversationId)
        }
    }

    fun handleNewNotification(notification: MomentsNotification) {
        NotificationPresentationCoordinator.present(notification, NotificationPresentationSource.LOCAL)
    }

    fun display(notification: MomentsNotification) {
        _currentNotification.value = notification
        _showBanner.value = true
        HapticManager.shared.notification(HapticManager.NotificationType.SUCCESS)
        startDismissTimer()
    }

    private fun startDismissTimer() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(DISPLAY_DURATION_MS)
            _showBanner.value = false
            delay(500)
            if (!_showBanner.value) _currentNotification.value = null
        }
    }

    fun dismissManually() {
        _showBanner.value = false
        dismissJob?.cancel()
        scope.launch {
            delay(500)
            if (!_showBanner.value) _currentNotification.value = null
        }
    }

    fun pauseDismissTimer() {
        dismissJob?.cancel()
    }

    fun resumeDismissTimerIfNeeded() {
        if (_showBanner.value) startDismissTimer()
    }

    private fun clearFallbackListeners() {
        reactionListeners.values.forEach { it.remove() }
        buzzListeners.values.forEach { it.remove() }
        reactionListeners.clear()
        buzzListeners.clear()
    }

    private fun attachReactionFallbackListener(conversationId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (reactionListeners.containsKey(conversationId)) return

        val registration = db.collectionGroup("messageReactions")
            .whereEqualTo("conversationId", conversationId)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) return@forEach
                    val data = change.document.data
                    val reactorId = data["userId"] as? String ?: change.document.id
                    if (reactorId == currentUserId) return@forEach
                    val messageId = data["messageId"] as? String ?: return@forEach
                    val emoji = data["emoji"] as? String ?: return@forEach
                    if (messageId.isBlank() || emoji.isBlank()) return@forEach
                    val timestamp = (data["timestamp"] as? com.google.firebase.Timestamp)?.toDate()
                    if (timestamp != null && !timestamp.after(listenerStartTime)) return@forEach

                    scope.launch {
                        if (!NotificationPresentationCoordinator.isMessageAuthoredByCurrentUser(conversationId, messageId)) return@launch
                        val username = NotificationPresentationCoordinator.fetchSenderUsername(reactorId)
                        NotificationPresentationCoordinator.presentMessageReactionFallback(
                            conversationId, messageId, reactorId, username, emoji, null,
                        )
                    }
                }
            }
        reactionListeners[conversationId] = registration
    }

    private fun attachBuzzFallbackListener(conversationId: String) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (buzzListeners.containsKey(conversationId)) return

        val registration = db.collection("conversations").document(conversationId)
            .collection("buzzEvents")
            .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.documentChanges?.forEach { change ->
                    if (change.type != com.google.firebase.firestore.DocumentChange.Type.ADDED) return@forEach
                    val data = change.document.data
                    if (data["type"] as? String != "buzz") return@forEach
                    val senderId = data["senderId"] as? String ?: return@forEach
                    if (senderId == currentUserId) return@forEach
                    val createdAt = (data["createdAt"] as? com.google.firebase.Timestamp)?.toDate()
                    if (createdAt != null && !createdAt.after(listenerStartTime)) return@forEach

                    scope.launch {
                        val username = NotificationPresentationCoordinator.fetchSenderUsername(senderId)
                        NotificationPresentationCoordinator.presentChatBuzzFallback(
                            conversationId, change.document.id, senderId, username,
                        )
                    }
                }
            }
        buzzListeners[conversationId] = registration
    }
}
