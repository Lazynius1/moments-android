package com.moments.android.views.messaging.screens.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.models.PendingChatContext
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.components.ChatHistoryLoadingIndicator
import com.moments.android.views.messaging.components.ChatListRow
import com.moments.android.views.messaging.components.ChatListUpdateTransaction
import com.moments.android.views.messaging.components.ChatMessageListController
import com.moments.android.views.messaging.components.ChatMessageListView
import com.moments.android.views.messaging.components.PendingRequestMessageRow
import com.moments.android.views.messaging.components.ChatConversationIntroRow
import com.moments.android.views.messaging.components.ChatHistoryStartHeader
import com.moments.android.views.messaging.components.ChatRequestDisclaimerRow
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import com.moments.android.views.messaging.core.ChatRenderRow
import com.moments.android.views.messaging.core.MessageItem

/** Port de `GlassmorphicChatView+MessageList.swift`.
 * El routing fino de scroll/búsqueda se recibe por callbacks y se completa en sus extensiones
 * Swift homónimas, sin esconder la política de filas de esta fuente.
 */
data class ChatMessageListCallbacks(
    val loadOlderHistory: () -> Unit = {},
    val retryHistoryLoad: () -> Unit = {},
    val onPrependFinished: () -> Unit = {},
    val onContentExtentChanged: (Boolean) -> Unit = {},
    val onRowsChanged: () -> Unit = {},
    val renderMessage: @Composable (MessageItem) -> Unit = {},
    val renderHeader: @Composable (ChatRenderRow.Header) -> Unit = {},
    val renderBuzz: @Composable (ChatRenderRow.Buzz) -> Unit = {},
    val renderTyping: @Composable () -> Unit = {},
)

@Stable
class ChatMessageListPresentation {
    var hasCompletedInitialScroll by mutableStateOf(false)
    var isPinnedToBottom by mutableStateOf(true)
    var scrollContentExceedsViewport by mutableStateOf(false)

    fun shouldShowHistoryLoadNotice(viewModel: EnhancedChatViewModel): Boolean =
        viewModel.historyLoadNotice.value != EnhancedChatViewModel.HistoryLoadNotice.HIDDEN &&
            hasCompletedInitialScroll && !isPinnedToBottom

    fun historyNoticeTextRes(viewModel: EnhancedChatViewModel): Int = when (viewModel.historyLoadNotice.value) {
        EnhancedChatViewModel.HistoryLoadNotice.OFFLINE -> R.string.network_offline_title
        EnhancedChatViewModel.HistoryLoadNotice.ERROR, EnhancedChatViewModel.HistoryLoadNotice.HIDDEN -> R.string.common_error
    }

    fun shouldShowRetry(viewModel: EnhancedChatViewModel): Boolean =
        viewModel.historyLoadNotice.value != EnhancedChatViewModel.HistoryLoadNotice.HIDDEN
}

@Composable
fun rememberChatMessageListPresentation() = remember { ChatMessageListPresentation() }

fun chatListRows(
    baseRows: List<ChatRenderRow>,
    pendingChatContext: PendingChatContext?,
    conversationIntroContext: PendingChatContext?,
    pendingTimelineMessage: com.moments.android.models.PendingChatTimelineMessage?,
    canLoadMore: Boolean,
    hasCompletedInitialScroll: Boolean,
    hasTypingUsers: Boolean,
): List<ChatRenderRow> {
    val rows = baseRows.toMutableList()
    val pending = pendingChatContext != null
    if (pending || (!canLoadMore && (hasCompletedInitialScroll || rows.isEmpty()))) {
        if (!pending) rows.add(0, ChatRenderRow.HistoryStart)
        if (pendingChatContext?.status !in setOf(PendingChatContext.Status.OUTGOING_REQUEST_DRAFT, PendingChatContext.Status.OUTGOING_REQUEST_BLOCKED, null)) {
            rows.add(0, ChatRenderRow.RequestDisclaimer(pendingChatContext))
        }
        rows.add(0, ChatRenderRow.ConversationIntro(pendingChatContext ?: conversationIntroContext))
        pendingTimelineMessage?.let { rows.add(minOf(3, rows.size), ChatRenderRow.PendingRequestMessage(it)) }
    }
    if (hasTypingUsers) rows += ChatRenderRow.Typing
    return rows
}

fun chatListTransaction(
    viewModel: EnhancedChatViewModel,
    rows: List<ChatRenderRow>,
    messageRowId: (String) -> String?,
): ChatListUpdateTransaction {
    val mutation = viewModel.chatTimelineMutation.value
    return ChatListUpdateTransaction(
        kind = mutation.kind,
        rows = rows.map { ChatListRow(id = it.id, messageIds = rowMessageIds(it), payload = it) },
        anchorRowId = mutation.anchorMessageId?.let(messageRowId),
        reason = mutation.reason,
    )
}

fun rowMessageIds(row: ChatRenderRow): Set<String> = when (row) {
    is ChatRenderRow.Message -> when (val item = row.item) {
        is MessageItem.Single -> setOf(item.message.id)
        is MessageItem.MediaCluster -> item.messages.mapTo(linkedSetOf()) { it.id }
    }
    else -> emptySet()
}

fun changedProgressKeys(old: Map<String, Double>, new: Map<String, Double>): Set<String> =
    (old.keys + new.keys).filterTo(linkedSetOf()) { old[it] != new[it] }

fun prefetchMediaForRows(rows: List<ChatRenderRow>, viewModel: EnhancedChatViewModel) {
    rows.flatMap { row ->
        when (row) {
            is ChatRenderRow.Message -> when (val item = row.item) {
                is MessageItem.Single -> listOf(item.message)
                is MessageItem.MediaCluster -> item.messages
            }
            else -> emptyList()
        }
    }.forEach(viewModel::hydrateMediaIfNeeded)
}

@Composable
fun GlassmorphicChatMessageList(
    transaction: ChatListUpdateTransaction,
    listController: ChatMessageListController,
    presentation: ChatMessageListPresentation,
    viewModel: EnhancedChatViewModel,
    adaptiveColors: AdaptiveColors,
    fallbackName: String,
    fallbackUserId: String,
    callbacks: ChatMessageListCallbacks,
    modifier: Modifier = Modifier,
) {
    LaunchedEffect(transaction.rows.map { it.id }) { callbacks.onRowsChanged() }
    LaunchedEffect(listController.isAtBottom) { presentation.isPinnedToBottom = listController.isAtBottom }
    Box(modifier.fillMaxSize()) {
        ChatMessageListView(
            transaction = transaction,
            controller = listController,
            onReachedTop = callbacks.loadOlderHistory,
            onContentExtentChanged = { exceeds ->
                presentation.scrollContentExceedsViewport = exceeds
                callbacks.onContentExtentChanged(exceeds)
            },
            onPrependFinished = callbacks.onPrependFinished,
            onPrefetchRows = { listRows ->
                prefetchMediaForRows(listRows.mapNotNull { it.payload as? ChatRenderRow }, viewModel)
            },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
            rowContent = { listRow ->
                when (val row = listRow.payload as? ChatRenderRow) {
                    is ChatRenderRow.ConversationIntro -> ChatConversationIntroRow(row.context, fallbackName, fallbackUserId, adaptiveColors)
                    is ChatRenderRow.RequestDisclaimer -> ChatRequestDisclaimerRow(requestDisclaimerRes(row.context), adaptiveColors, Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    is ChatRenderRow.PendingRequestMessage -> PendingRequestMessageRow(row.message, adaptiveColors, Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
                    is ChatRenderRow.HistoryStart -> ChatHistoryStartHeader(adaptiveColors)
                    is ChatRenderRow.Message -> callbacks.renderMessage(row.item)
                    is ChatRenderRow.Header -> callbacks.renderHeader(row)
                    is ChatRenderRow.Buzz -> callbacks.renderBuzz(row)
                    ChatRenderRow.Typing -> callbacks.renderTyping()
                    null -> Unit
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        AnimatedVisibility(
            presentation.shouldShowHistoryLoadNotice(viewModel),
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 10.dp),
        ) {
            ChatHistoryLoadingIndicator(
                adaptiveColors = adaptiveColors,
                textRes = presentation.historyNoticeTextRes(viewModel),
                showsProgress = false,
                retryTextRes = if (presentation.shouldShowRetry(viewModel)) R.string.messaging_retry else null,
                onTap = if (presentation.shouldShowRetry(viewModel)) {
                    { viewModel.clearHistoryLoadNotice(); callbacks.retryHistoryLoad() }
                } else null,
            )
        }
    }
}

private fun requestDisclaimerRes(context: PendingChatContext?): Int = when (context?.status) {
    PendingChatContext.Status.INCOMING_REQUEST_PENDING -> R.string.chat_request_disclaimer_incoming
    PendingChatContext.Status.OUTGOING_REQUEST_SENT -> R.string.chat_request_disclaimer_sent
    PendingChatContext.Status.OUTGOING_REQUEST_DRAFT,
    PendingChatContext.Status.OUTGOING_REQUEST_BLOCKED -> R.string.chat_request_disclaimer_outgoing
    PendingChatContext.Status.NORMAL_CONVERSATION,
    null -> R.string.chat_intro_disclaimer_normal
}
