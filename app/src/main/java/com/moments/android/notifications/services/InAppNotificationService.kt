package com.moments.android.notifications.services

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.moments.android.models.MomentsNotification
import com.moments.android.utilities.HapticManager
import com.moments.android.views.messaging.services.ChatSessionEngine
import java.util.ArrayDeque
import java.util.Date
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Port de InAppNotificationService.swift */
object InAppNotificationService {
    private val db = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _currentNotification = MutableStateFlow<MomentsNotification?>(null)
    val currentNotification: StateFlow<MomentsNotification?> = _currentNotification.asStateFlow()

    private val _actionToast = MutableStateFlow<InAppActionToast?>(null)
    val actionToast: StateFlow<InAppActionToast?> = _actionToast.asStateFlow()

    private val _showBanner = MutableStateFlow(false)
    val showBanner: StateFlow<Boolean> = _showBanner.asStateFlow()

    private val _hostGeneration = MutableStateFlow(0L)
    val hostGeneration: StateFlow<Long> = _hostGeneration.asStateFlow()

    private var dismissJob: Job? = null
    private var pendingExpireAction: (() -> Unit)? = null
    private var listenerStartTime = Date()
    private val reactionListeners = mutableMapOf<String, ListenerRegistration>()
    private val buzzListeners = mutableMapOf<String, ListenerRegistration>()
    private var activeDurationMs = InAppActionToast.STANDARD_DURATION_MS
    private val displayDurationMs = InAppActionToast.STANDARD_DURATION_MS

    private sealed class BannerQueueItem {
        data class Notification(val value: MomentsNotification) : BannerQueueItem()
        data class Toast(val value: InAppActionToast) : BannerQueueItem()
    }

    /** Cola FIFO: no sustituir ni perder banners mientras uno está en pantalla. */
    private val bannerQueue = ArrayDeque<BannerQueueItem>()
    /** Entre hide y clear (0.5s): nuevos items van a la cola. */
    private var isClearing = false

    fun startListening() {
        if (FirebaseAuth.getInstance().currentUser?.uid == null) return
        clearFallbackListeners()
        listenerStartTime = Date()
        syncFallbackListeners(ChatSessionEngine.notificationConversationIdsForFallback())
    }

    fun stopListening() {
        clearFallbackListeners()
        bannerQueue.clear()
        dismissManually(consumeExpire = false)
        _currentNotification.value = null
        _actionToast.value = null
        pendingExpireAction = null
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
        enqueue(BannerQueueItem.Notification(notification))
    }

    fun showActionToast(toast: InAppActionToast) {
        enqueue(BannerQueueItem.Toast(toast))
    }

    /**
     * Recreate the lightweight banner window after a Nav3 DialogScene is attached,
     * so the global in-app chrome remains above full-screen destinations.
     */
    fun bringHostToFront() {
        _hostGeneration.value += 1L
    }

    fun dismissHeldActionToast() {
        if (_actionToast.value?.holds != true) return
        dismissManually(consumeExpire = false)
    }

    /** Deshacer del toast: cancela el commit diferido y cierra sin `onExpire`. */
    fun performUndoFromActionToast() {
        val undo = _actionToast.value?.undo ?: return
        pendingExpireAction = null
        undo()
        dismissManually(consumeExpire = false)
    }

    private fun enqueue(item: BannerQueueItem) {
        // Progress held → el done / siguiente toast lo sustituye (flujo activity).
        if (_showBanner.value && _actionToast.value?.holds == true && item is BannerQueueItem.Toast) {
            present(item)
            return
        }
        if (_showBanner.value || isClearing) {
            bannerQueue.addLast(item)
            return
        }
        present(item)
    }

    private fun present(item: BannerQueueItem) {
        when (item) {
            is BannerQueueItem.Notification -> {
                _actionToast.value = null
                _currentNotification.value = item.value
                pendingExpireAction = null
                HapticManager.shared.notification(HapticManager.NotificationType.SUCCESS)
                activeDurationMs = displayDurationMs
                _showBanner.value = true
                startDismissTimer()
            }
            is BannerQueueItem.Toast -> {
                _currentNotification.value = null
                _actionToast.value = item.value
                pendingExpireAction = item.value.onExpire
                _showBanner.value = true
                if (item.value.showsProgress) {
                    dismissJob?.cancel()
                } else {
                    HapticManager.shared.notification(HapticManager.NotificationType.SUCCESS)
                    activeDurationMs = item.value.durationMs
                    startDismissTimer()
                }
            }
        }
    }

    private fun startDismissTimer() {
        dismissJob?.cancel()
        dismissJob = scope.launch {
            delay(activeDurationMs)
            dismissManually(consumeExpire = true)
        }
    }

    fun dismissManually(consumeExpire: Boolean = true) {
        if (!_showBanner.value && _actionToast.value == null && _currentNotification.value == null) {
            presentNextIfNeeded()
            return
        }
        isClearing = true
        _showBanner.value = false
        dismissJob?.cancel()
        scope.launch {
            delay(500)
            if (consumeExpire) consumePendingExpire() else pendingExpireAction = null
            _currentNotification.value = null
            _actionToast.value = null
            isClearing = false
            presentNextIfNeeded()
        }
    }

    private fun presentNextIfNeeded() {
        if (_showBanner.value || isClearing || bannerQueue.isEmpty()) return
        present(bannerQueue.removeFirst())
    }

    private fun consumePendingExpire() {
        val action = pendingExpireAction
        pendingExpireAction = null
        action?.invoke()
    }

    fun pauseDismissTimer() {
        dismissJob?.cancel()
    }

    fun resumeDismissTimerIfNeeded() {
        if (_showBanner.value && _actionToast.value?.showsProgress != true) {
            startDismissTimer()
        }
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
