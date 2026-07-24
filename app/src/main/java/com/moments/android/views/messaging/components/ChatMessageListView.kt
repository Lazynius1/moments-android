package com.moments.android.views.messaging.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged

/** Equivalente Compose de las filas diffeables de `ChatMessageListView.swift`.
 * `payload` deja la decisión visual al renderizador que prepara `ChatRenderRow`.
 */
data class ChatListRow(val id: String, val messageIds: Set<String> = emptySet(), val visualSignature: Int = 0, val payload: Any? = null)
enum class ChatListUpdateKind { INITIAL, PREPEND_HISTORY, APPEND_MESSAGES, RECONFIGURE_ROWS, REPLACE_ALL, JUMP }
enum class ChatTimelineUpdateReason { HISTORY, INCOMING, OUTGOING, SEARCH, HIGHLIGHT, UNREAD, LAYOUT }
sealed interface ChatListScrollCommand { data object None : ChatListScrollCommand; data class Bottom(val animated: Boolean) : ChatListScrollCommand; data class FirstUnread(val messageId: String, val animated: Boolean) : ChatListScrollCommand; data class Highlight(val messageId: String, val animated: Boolean) : ChatListScrollCommand }
data class ChatViewportAnchor(val rowId: String, val offsetFromContentTop: Int)
data class ChatListUpdateTransaction(val kind: ChatListUpdateKind, val rows: List<ChatListRow>, val changedRowIds: Set<String> = emptySet(), val anchorRowId: String? = null, val scrollCommand: ChatListScrollCommand = ChatListScrollCommand.None, val reason: ChatTimelineUpdateReason)
sealed interface ChatListInitialScrollPolicy { data object AutomaticBottom : ChatListInitialScrollPolicy; data class Row(val id: String) : ChatListInitialScrollPolicy; data object Deferred : ChatListInitialScrollPolicy }
sealed interface ChatListScrollIntent { data class Bottom(val animated: Boolean) : ChatListScrollIntent; data class Row(val id: String, val animated: Boolean) : ChatListScrollIntent }

@Stable
class ChatMessageListController {
    var initialScrollPolicy by mutableStateOf<ChatListInitialScrollPolicy>(ChatListInitialScrollPolicy.AutomaticBottom)
    internal var nextIntent by mutableStateOf<ChatListScrollIntent?>(null)
    internal var command by mutableStateOf<ChatListScrollCommand>(ChatListScrollCommand.None)
    var isAtBottom by mutableStateOf(true); private set
    var contentExceedsViewport by mutableStateOf(false); private set
    var contentOffsetY by mutableIntStateOf(0); private set
    var topVisibleRowId by mutableStateOf<String?>(null); private set
    var bottomVisibleRowId by mutableStateOf<String?>(null); private set
    var firstVisibleRowIndex by mutableStateOf<Int?>(null); private set
    var distanceFromBottom by mutableIntStateOf(0); private set
    var scrollNavigationTargetRowId by mutableStateOf<String?>(null)
    fun enqueue(value: ChatListScrollIntent) { nextIntent = value }
    fun scrollToBottom(animated: Boolean) = enqueue(ChatListScrollIntent.Bottom(animated))
    fun forceScrollToBottom(animated: Boolean) = enqueue(ChatListScrollIntent.Bottom(animated))
    fun forceScrollToBottomIgnoringNavigation(animated: Boolean) = enqueue(ChatListScrollIntent.Bottom(animated))
    fun scrollToRow(id: String, animated: Boolean) = enqueue(ChatListScrollIntent.Row(id, animated))
    fun navigateToRow(id: String, animated: Boolean) { scrollNavigationTargetRowId = id; scrollToRow(id, animated) }
    fun perform(value: ChatListScrollCommand) { command = value }
    fun clearNavigationTarget() { scrollNavigationTargetRowId = null }
    internal fun consumeIntent() { nextIntent = null }
    internal fun updateViewport(state: LazyListState, rows: List<ChatListRow>) {
        val visible = state.layoutInfo.visibleItemsInfo
        firstVisibleRowIndex = visible.firstOrNull()?.index
        topVisibleRowId = visible.firstOrNull()?.index?.let(rows::getOrNull)?.id
        bottomVisibleRowId = visible.lastOrNull()?.index?.let(rows::getOrNull)?.id
        contentOffsetY = state.firstVisibleItemScrollOffset
        val last = visible.lastOrNull()?.index ?: -1
        isAtBottom = rows.isEmpty() || last >= rows.lastIndex
        distanceFromBottom = (rows.lastIndex - last).coerceAtLeast(0)
        contentExceedsViewport = state.layoutInfo.totalItemsCount > visible.size
    }
}

@Composable
fun rememberChatMessageListController() = remember { ChatMessageListController() }

/** Port Compose de la lista UIKit: conserva ancla al prepend, solo autoscroll si estaba abajo,
 * serializa intenciones y prefetch de historial antes de alcanzar el borde superior. */
@Composable
fun ChatMessageListView(
    transaction: ChatListUpdateTransaction,
    controller: ChatMessageListController,
    onReachedTop: () -> Unit,
    onContentExtentChanged: (Boolean) -> Unit = {},
    onPrependFinished: () -> Unit = {},
    onPrefetchRows: (List<ChatListRow>) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    rowContent: @Composable (ChatListRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    var rows by remember { mutableStateOf(emptyList<ChatListRow>()) }
    var rowIndex by remember { mutableStateOf(emptyMap<String, Int>()) }
    var messageRowIndex by remember { mutableStateOf(emptyMap<String, Int>()) }
    var previousBottom by remember { mutableStateOf(true) }

    LaunchedEffect(transaction) {
        val oldRows = rows
        val oldFirst = state.firstVisibleItemIndex
        val oldOffset = state.firstVisibleItemScrollOffset
        val previousAnchor = oldRows.getOrNull(oldFirst)?.id
        previousBottom = controller.isAtBottom
        rows = transaction.rows
        rowIndex = rows.mapIndexed { index, row -> row.id to index }.toMap()
        messageRowIndex = buildMap {
            rows.forEachIndexed { index, row -> row.messageIds.forEach { messageId -> put(messageId, index) } }
        }
        val actualPrepend = transaction.kind == ChatListUpdateKind.PREPEND_HISTORY || (previousAnchor != null && rowIndex[previousAnchor]?.let { it > oldFirst } == true)
        when {
            actualPrepend && previousAnchor != null && rowIndex.containsKey(previousAnchor) -> {
                state.scrollToItem(rowIndex.getValue(previousAnchor), oldOffset)
                onPrependFinished()
            }
            transaction.kind == ChatListUpdateKind.INITIAL && rows.isNotEmpty() && controller.initialScrollPolicy != ChatListInitialScrollPolicy.Deferred -> {
                val target = (controller.initialScrollPolicy as? ChatListInitialScrollPolicy.Row)?.id?.let(rowIndex::get) ?: rows.lastIndex
                state.scrollToItem(target)
            }
            previousBottom && transaction.kind == ChatListUpdateKind.APPEND_MESSAGES && controller.scrollNavigationTargetRowId == null && rows.isNotEmpty() -> state.scrollToItem(rows.lastIndex)
        }
        when (val command = transaction.scrollCommand) { is ChatListScrollCommand.Bottom -> if (rows.isNotEmpty()) state.scrollToItem(rows.lastIndex); is ChatListScrollCommand.FirstUnread -> (messageRowIndex[command.messageId] ?: rowIndex[command.messageId])?.let { state.scrollToItem(it) }; is ChatListScrollCommand.Highlight -> (messageRowIndex[command.messageId] ?: rowIndex[command.messageId])?.let { state.scrollToItem(it) }; ChatListScrollCommand.None -> Unit }
    }

    LaunchedEffect(controller.nextIntent, rowIndex, rows) {
        val intent = controller.nextIntent
        when (intent) {
            is ChatListScrollIntent.Bottom -> if (rows.isNotEmpty()) state.animateScrollToItem(rows.lastIndex)
            is ChatListScrollIntent.Row -> rowIndex[intent.id]?.let { state.animateScrollToItem(it); controller.clearNavigationTarget() }
            null -> Unit
        }
        if (intent != null) controller.consumeIntent()
    }
    LaunchedEffect(state, rows) {
        snapshotFlow { state.layoutInfo.visibleItemsInfo.map { it.index } }.distinctUntilChanged().collect {
            controller.updateViewport(state, rows); onContentExtentChanged(controller.contentExceedsViewport)
            val first = state.firstVisibleItemIndex
            if (first <= 10 && rows.isNotEmpty()) onReachedTop()
            val prefetch = rows.subList((first - 40).coerceAtLeast(0), first.coerceAtMost(rows.size)); if (prefetch.isNotEmpty()) onPrefetchRows(prefetch)
        }
    }
    LazyColumn(state = state, modifier = modifier.fillMaxSize(), contentPadding = contentPadding) { itemsIndexed(rows, key = { _, row -> row.id }) { _, row -> rowContent(row) } }
}
