package com.moments.android.views.messaging.screens.chat

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import com.moments.android.views.messaging.components.ChatListInitialScrollPolicy
import com.moments.android.views.messaging.components.ChatListScrollCommand
import com.moments.android.views.messaging.components.ChatMessageListController
import com.moments.android.views.messaging.components.VanishPullResult
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import com.moments.android.views.messaging.services.ChatScrollTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class ListBottomSnapReason { KEYBOARD, COMPOSER_RESIZED, USER_REQUESTED, INCOMING_WHILE_PINNED }

data class ChatScrollCallbacks(
    val rowsReady: () -> Boolean = { false },
    val resolveInitialTarget: () -> ChatScrollTarget? = { null },
    val shouldOpenAtBottom: () -> Boolean = { true },
    val messageRowReady: (String) -> Boolean = { false },
    val onInitialOpenFinished: (Boolean) -> Unit = {},
    val onPrefetchMedia: () -> Unit = {},
    val onPendingReactionHighlights: () -> Unit = {},
    val onPendingBuzz: () -> Unit = {},
    val onNotificationBuzzRetries: () -> Unit = {},
    val needsBuzzRetry: () -> Boolean = { false },
    val onHighlight: (Set<String>, Boolean) -> Unit = { _, _ -> },
    val onClearHighlightIntent: () -> Unit = {},
    val onVanishToggle: () -> Unit = {},
    val onPresentVanishTimer: () -> Unit = {},
)

@Stable
class GlassmorphicChatScrollController(
    private val viewModel: EnhancedChatViewModel,
    private val listController: ChatMessageListController,
    private val callbacks: ChatScrollCallbacks = ChatScrollCallbacks(),
    private val reduceMotion: () -> Boolean = { false },
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var initialScrollJob: Job? = null
    private var bottomSnapJob: Job? = null
    private var composerSnapJob: Job? = null
    private var highlightScrollJob: Job? = null

    var hasCompletedInitialScroll by mutableStateOf(false)
        private set
    var isPinnedToBottom by mutableStateOf(true)
        private set
    var listIsAtBottom by mutableStateOf(true)
        private set
    var didReapplyFrozenScrollPosition by mutableStateOf(false)
        private set
    var pendingInitialScrollRoute by mutableStateOf(false)
        private set
    var frozenInitialScrollTarget by mutableStateOf<ChatScrollTarget?>(null)
        private set
    var pendingIncomingMessages by mutableStateOf(0)
        private set
    var pendingSearchHighlightId by mutableStateOf<String?>(null)
        private set
    var scrollContentExceedsViewport by mutableStateOf(false)
        private set
    var lastComposerHeight by mutableStateOf<Dp?>(null)
        private set

    fun loadOlderHistoryIfNeeded() {
        if (!hasCompletedInitialScroll || viewModel.isLoadingMore.value || !viewModel.canLoadMore.value || viewModel.messages.value.isEmpty()) return
        viewModel.loadMoreMessages()
    }

    fun configureListInitialScrollPolicy() {
        listController.initialScrollPolicy = ChatListInitialScrollPolicy.Deferred
    }

    fun timelineScrollCommand(target: ChatScrollTarget, animated: Boolean): ChatListScrollCommand? = when (target) {
        is ChatScrollTarget.Bottom -> ChatListScrollCommand.Bottom(animated)
        is ChatScrollTarget.FirstUnread -> target.messageId.takeIf(callbacks.messageRowReady)?.let { ChatListScrollCommand.FirstUnread(it, animated) }
        is ChatScrollTarget.HighlightedMessage -> target.messageId.takeIf(callbacks.messageRowReady)?.let { ChatListScrollCommand.Highlight(it, animated) }
    }

    fun routeInitialScroll() {
        if (!callbacks.rowsReady()) return
        val highlight = pendingSearchHighlightId
        if (!highlight.isNullOrBlank()) {
            scheduleSearchHighlightScroll(highlight)
            return
        }
        if (!hasCompletedInitialScroll) {
            pendingInitialScrollRoute = true
            if (frozenInitialScrollTarget == null || callbacks.shouldOpenAtBottom()) {
                frozenInitialScrollTarget = callbacks.resolveInitialTarget()
            }
            val target = frozenInitialScrollTarget ?: return
            if (target.pinsToBottom) scheduleInitialScrollToBottom()
            else {
                scrollToTarget(target, animated = false)
                finishInitialOpen(false)
            }
            pendingInitialScrollRoute = false
        } else if (!didReapplyFrozenScrollPosition && (isPinnedToBottom || callbacks.shouldOpenAtBottom())) {
            didReapplyFrozenScrollPosition = true
            listController.perform(ChatListScrollCommand.Bottom(animated = false))
        }
    }

    fun scrollToTarget(target: ChatScrollTarget, animated: Boolean) {
        val command = timelineScrollCommand(target, animated) ?: return
        if (target.pinsToBottom) pendingIncomingMessages = 0 else {
            isPinnedToBottom = false
            listIsAtBottom = false
        }
        listController.perform(command)
    }

    fun scheduleInitialScrollToBottom() {
        if (pendingSearchHighlightId != null) return
        initialScrollJob?.cancel()
        initialScrollJob = scope.launch {
            for (waitMs in listOf(0L, 80L, 200L)) {
                if (waitMs > 0) delay(waitMs)
                if (!callbacks.rowsReady()) continue
                listController.perform(ChatListScrollCommand.Bottom(animated = false))
                break
            }
            finishInitialOpen(true)
        }
    }

    fun finishInitialOpen(pinsToBottom: Boolean) {
        hasCompletedInitialScroll = true
        pendingInitialScrollRoute = false
        isPinnedToBottom = pinsToBottom
        listIsAtBottom = pinsToBottom
        if (pinsToBottom) frozenInitialScrollTarget = ChatScrollTarget.Bottom(viewModel.messages.value.lastOrNull()?.id)
        callbacks.onPrefetchMedia()
        callbacks.onPendingReactionHighlights()
        callbacks.onPendingBuzz()
        callbacks.onNotificationBuzzRetries()
        callbacks.onInitialOpenFinished(pinsToBottom)
    }

    fun scrollToBottomFromUserAction(animated: Boolean = true) {
        bottomSnapJob?.cancel()
        highlightScrollJob?.cancel()
        pendingSearchHighlightId = null
        listController.clearNavigationTarget()
        if (!callbacks.rowsReady()) return
        pendingIncomingMessages = 0
        isPinnedToBottom = false
        listIsAtBottom = false
        listController.forceScrollToBottomIgnoringNavigation(animated && !reduceMotion())
    }

    fun scheduleListBottomSnap(reason: ListBottomSnapReason, keyboardDurationMillis: Long = 0L, animated: Boolean? = null) {
        if (reason != ListBottomSnapReason.USER_REQUESTED && pendingSearchHighlightId != null) return
        if (reason == ListBottomSnapReason.USER_REQUESTED) {
            scrollToBottomFromUserAction(animated ?: true)
            return
        }
        bottomSnapJob?.cancel()
        bottomSnapJob = scope.launch {
            val wait = when (reason) {
                ListBottomSnapReason.KEYBOARD -> keyboardDurationMillis + 16L
                ListBottomSnapReason.COMPOSER_RESIZED -> 50L
                ListBottomSnapReason.INCOMING_WHILE_PINNED, ListBottomSnapReason.USER_REQUESTED -> 0L
            }
            if (wait > 0) delay(wait)
            if (callbacks.rowsReady() && isPinnedToBottom) {
                val shouldAnimate = (animated ?: (reason == ListBottomSnapReason.KEYBOARD || reason == ListBottomSnapReason.COMPOSER_RESIZED)) && !reduceMotion()
                listController.perform(ChatListScrollCommand.Bottom(shouldAnimate))
            }
        }
    }

    fun handleComposerHeightChange(height: Dp) {
        val previous = lastComposerHeight
        lastComposerHeight = height
        if (!hasCompletedInitialScroll || !isPinnedToBottom || previous == null || kotlin.math.abs(height.value - previous.value) <= .5f) return
        composerSnapJob?.cancel()
        composerSnapJob = scope.launch { delay(50L); scheduleListBottomSnap(ListBottomSnapReason.COMPOSER_RESIZED) }
    }

    fun handleLastMessageChange(oldMessageId: String?, lastMessageId: String?) {
        if (lastMessageId == null || oldMessageId == null || !hasCompletedInitialScroll) return
        val mine = viewModel.messages.value.lastOrNull()?.senderId == viewModel.currentUserId
        if (mine && !isPinnedToBottom) scheduleListBottomSnap(ListBottomSnapReason.USER_REQUESTED, animated = true)
        else if (!mine && !viewModel.isLoadingMore.value && !isPinnedToBottom) {
            pendingIncomingMessages += 1
        }
    }

    fun handleListAtBottomChange(atBottom: Boolean) {
        isPinnedToBottom = atBottom
        listIsAtBottom = atBottom
    }

    fun handleVanishPullReleased(result: VanishPullResult) {
        if (!result.completed || result.effectivePull <= 0f) return
        val activating = !viewModel.vanishModeActive.value
        callbacks.onVanishToggle()
        if (activating) callbacks.onPresentVanishTimer()
    }

    fun scheduleSearchHighlightScroll(messageId: String) {
        initialScrollJob?.cancel(); bottomSnapJob?.cancel(); composerSnapJob?.cancel(); highlightScrollJob?.cancel()
        if (messageId.isBlank()) return
        pendingSearchHighlightId = messageId
        isPinnedToBottom = false; listIsAtBottom = false; didReapplyFrozenScrollPosition = true; hasCompletedInitialScroll = true
        frozenInitialScrollTarget = ChatScrollTarget.HighlightedMessage(messageId)
        highlightScrollJob = scope.launch {
            val loaded = viewModel.messages.value.any { it.id == messageId } || viewModel.navigateToMessage(messageId)
            if (!loaded || pendingSearchHighlightId != messageId) {
                pendingSearchHighlightId = null
                frozenInitialScrollTarget = null
                listController.clearNavigationTarget()
                return@launch
            }
            if (callbacks.messageRowReady(messageId)) completeSearchHighlightScroll(messageId)
            else scrollToTarget(ChatScrollTarget.HighlightedMessage(messageId), animated = false)
        }
    }

    fun completeSearchHighlightScroll(messageId: String) {
        scrollToTarget(ChatScrollTarget.HighlightedMessage(messageId), animated = false)
        callbacks.onHighlight(setOf(messageId), false)
        finishInitialOpen(false)
        pendingSearchHighlightId = null
        frozenInitialScrollTarget = null
    }

    fun handleJumpToMessageFromOutside(messageId: String) {
        if (messageId.isNotBlank()) scheduleSearchHighlightScroll(messageId)
    }

    fun processPendingReactionHighlights(messageIds: Set<String>, shouldScroll: Boolean) {
        if (messageIds.isEmpty()) return
        if (shouldScroll) messageIds.firstOrNull()?.let { scrollToTarget(ChatScrollTarget.HighlightedMessage(it), !reduceMotion()) }
        callbacks.onHighlight(messageIds, false)
        callbacks.onClearHighlightIntent()
    }

    fun processPendingBuzz() = callbacks.onPendingBuzz()

    fun scheduleNotificationBuzzRetries() {
        if (!callbacks.needsBuzzRetry()) return
        scope.launch {
            listOf(250L, 700L, 1_500L, 2_500L, 4_000L, 5_000L).forEach { wait ->
                delay(wait)
                if (!callbacks.needsBuzzRetry()) return@launch
                callbacks.onPendingBuzz()
            }
        }
    }

    fun allowsVerticalScrolling(): Boolean = hasCompletedInitialScroll && scrollContentExceedsViewport
    fun distanceFromBottom(): Int = listController.distanceFromBottom
    fun clearSearchHighlight() {
        highlightScrollJob?.cancel()
        pendingSearchHighlightId = null
    }
    fun updateContentExtent(exceedsViewport: Boolean) { scrollContentExceedsViewport = exceedsViewport }

    fun dispose() { initialScrollJob?.cancel(); bottomSnapJob?.cancel(); composerSnapJob?.cancel(); highlightScrollJob?.cancel() }
}
