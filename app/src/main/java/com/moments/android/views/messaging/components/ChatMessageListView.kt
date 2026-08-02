package com.moments.android.views.messaging.components

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.views.messaging.core.ChatRenderRow
import com.moments.android.views.messaging.core.MessageItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/**
 * Lista de chat Compose al estilo apps grandes (Telegram/WhatsApp/Signal):
 * `LazyColumn(reverseLayout = true)` + filas newest-first en el adapter.
 *
 * Contrato de datos de [ChatListUpdateTransaction.rows]: cronológico oldest→newest
 * (igual que iOS / ViewModel). Aquí se invierte solo para el layout.
 *
 * No portamos UICollectionView/UIKit: sí el contrato de
 * transacciones/intents/commands/viewport de `ChatMessageListView.swift`.
 */

/** ≡ iOS `ChatListLayoutMetrics` (interSectionSpacing / insets de sección). */
object ChatListLayoutMetrics {
    val interGroupSpacing = 2.dp
    val sectionTopInset = 10.dp
    val sectionBottomInset = 4.dp
}

data class ChatListRow(
    val id: String,
    val messageIds: Set<String> = emptySet(),
    val visualSignature: Int = 0,
    val payload: Any? = null,
)

enum class ChatListUpdateKind { INITIAL, PREPEND_HISTORY, APPEND_MESSAGES, RECONFIGURE_ROWS, REPLACE_ALL, JUMP }

enum class ChatTimelineUpdateReason { HISTORY, INCOMING, OUTGOING, SEARCH, HIGHLIGHT, UNREAD, LAYOUT }

sealed interface ChatListScrollCommand {
    data object None : ChatListScrollCommand
    data class Bottom(val animated: Boolean) : ChatListScrollCommand
    data class FirstUnread(val messageId: String, val animated: Boolean) : ChatListScrollCommand
    data class Highlight(val messageId: String, val animated: Boolean) : ChatListScrollCommand
}

data class ChatViewportAnchor(val rowId: String, val offsetFromContentTop: Int)

data class ChatListUpdateTransaction(
    val kind: ChatListUpdateKind,
    val rows: List<ChatListRow>,
    val changedRowIds: Set<String> = emptySet(),
    val anchorRowId: String? = null,
    val scrollCommand: ChatListScrollCommand = ChatListScrollCommand.None,
    val reason: ChatTimelineUpdateReason,
)

sealed interface ChatListInitialScrollPolicy {
    data object AutomaticBottom : ChatListInitialScrollPolicy
    data class Row(val id: String) : ChatListInitialScrollPolicy
    data object Deferred : ChatListInitialScrollPolicy
}

sealed interface ChatListScrollIntent {
    data class Bottom(val animated: Boolean) : ChatListScrollIntent
    data class Row(val id: String, val animated: Boolean) : ChatListScrollIntent
}

internal data class PendingScrollRequest(val id: String, val animated: Boolean)

@Stable
class ChatMessageListController {
    /** ≡ iOS `ChatMessageListController.timestampRevealState` — compartido por todas las filas. */
    val timestampRevealState = ChatTimestampRevealState()
    var initialScrollPolicy by mutableStateOf<ChatListInitialScrollPolicy>(ChatListInitialScrollPolicy.AutomaticBottom)
    internal var nextIntent by mutableStateOf<ChatListScrollIntent?>(null)
    internal var command by mutableStateOf<ChatListScrollCommand>(ChatListScrollCommand.None)
    /** bump para forzar recomposición de filas visibles (≡ `reconfigureVisible`). */
    var reconfigureGeneration by mutableIntStateOf(0)
        private set

    var isAtBottom by mutableStateOf(true)
        private set
    /** ≡ iOS `isStrictlyAtBottom` — pegado real al mensaje más reciente. */
    var isStrictlyAtBottom by mutableStateOf(true)
        private set
    var contentExceedsViewport by mutableStateOf(false)
        private set
    var contentOffsetY by mutableIntStateOf(0)
        private set
    var topVisibleRowId by mutableStateOf<String?>(null)
        private set
    var bottomVisibleRowId by mutableStateOf<String?>(null)
        private set
    var firstVisibleRowIndex by mutableStateOf<Int?>(null)
        private set
    var distanceFromBottom by mutableIntStateOf(0)
        private set
    var scrollNavigationTargetRowId by mutableStateOf<String?>(null)

    /** Tras prepend, no disparar carga de historial hasta gesto de usuario. */
    internal var suppressHistoryLoadUntilNextUserScroll by mutableStateOf(false)
    internal var pendingScroll by mutableStateOf<PendingScrollRequest?>(null)
    internal var allowForceScrollDuringNavigation by mutableStateOf(false)
    internal var isProgrammaticScroll by mutableStateOf(false)

    private var messageIdToRowId: Map<String, String> = emptyMap()
    private var orderedRowIds: List<String> = emptyList()
    private val rowFramesInWindow = mutableMapOf<String, Rect>()

    fun enqueue(value: ChatListScrollIntent) {
        nextIntent = value
    }

    fun scrollToBottom(animated: Boolean) {
        if (scrollNavigationTargetRowId != null) return
        enqueue(ChatListScrollIntent.Bottom(animated))
    }

    fun forceScrollToBottom(animated: Boolean) {
        allowForceScrollDuringNavigation = false
        enqueue(ChatListScrollIntent.Bottom(animated))
    }

    fun forceScrollToBottomIgnoringNavigation(animated: Boolean) {
        allowForceScrollDuringNavigation = true
        enqueue(ChatListScrollIntent.Bottom(animated))
    }

    fun scrollToRow(id: String, animated: Boolean) = enqueue(ChatListScrollIntent.Row(id, animated))

    fun navigateToRow(id: String, animated: Boolean) {
        scrollNavigationTargetRowId = id
        scrollToRow(id, animated)
    }

    fun perform(value: ChatListScrollCommand) {
        command = value
    }

    fun clearNavigationTarget() {
        scrollNavigationTargetRowId = null
    }

    /** Compose recompone por estado; bump para call sites iOS de search/flash/menu. */
    fun reconfigureVisible() {
        reconfigureGeneration++
    }

    fun reconfigure(messageIds: List<String>) {
        if (messageIds.any { messageIdToRowId.containsKey(it) || orderedRowIds.contains(it) }) {
            reconfigureGeneration++
        }
    }

    fun resolvedRowId(forMessageId: String): String =
        messageIdToRowId[forMessageId] ?: forMessageId

    fun containsRow(id: String): Boolean {
        val resolved = resolvedRowId(id)
        return orderedRowIds.contains(resolved)
    }

    /**
     * Frame de fila en coordenadas de ventana (≡ iOS UIKit `frameInWindow`).
     * Se rellena vía [reportRowFrame] en el item Compose.
     */
    fun frameInWindow(forRowId: String): Rect? = rowFramesInWindow[forRowId]

    /**
     * ≡ iOS `resetVanishPullState` — limpia lift/overlay del pull-to-vanish.
     */
    var vanishPullResetSignal by mutableIntStateOf(0)
        private set

    fun resetVanishPullState(@Suppress("UNUSED_PARAMETER") animated: Boolean) {
        vanishPullResetSignal++
    }

    internal fun consumeIntent() {
        nextIntent = null
        allowForceScrollDuringNavigation = false
    }

    internal fun consumeCommand() {
        command = ChatListScrollCommand.None
    }

    internal fun syncRows(chronoRows: List<ChatListRow>) {
        orderedRowIds = chronoRows.map { it.id }
        messageIdToRowId = buildMap {
            chronoRows.forEach { row ->
                row.messageIds.forEach { put(it, row.id) }
            }
        }
    }

    internal fun reportRowFrame(rowId: String, frame: Rect?) {
        if (frame == null || frame.isEmpty) rowFramesInWindow.remove(rowId)
        else rowFramesInWindow[rowId] = frame
    }

    internal fun onUserScrollBegan() {
        suppressHistoryLoadUntilNextUserScroll = false
        if (scrollNavigationTargetRowId != null) {
            scrollNavigationTargetRowId = null
        }
    }

    /**
     * Con `reverseLayout`, índice 0 = fondo (mensaje más reciente).
     * “Arriba” (historial viejo) = índices altos.
     *
     * [distanceFromBottom] ≡ iOS (píxeles al borde inferior), no índice de fila —
     * search usa umbral `> 16`.
     */
    internal fun updateViewport(state: LazyListState, displayRows: List<ChatListRow>) {
        val layoutInfo = state.layoutInfo
        val visible = layoutInfo.visibleItemsInfo
        firstVisibleRowIndex = visible.firstOrNull()?.index
        topVisibleRowId = visible.maxByOrNull { it.index }?.index?.let(displayRows::getOrNull)?.id
        bottomVisibleRowId = visible.minByOrNull { it.index }?.index?.let(displayRows::getOrNull)?.id
        contentOffsetY = state.firstVisibleItemScrollOffset
        val nearestBottom = visible.minOfOrNull { it.index } ?: Int.MAX_VALUE
        val offsetNearBottom = nearestBottom == 0 && state.firstVisibleItemScrollOffset <= 8
        val lastNewestVisible = displayRows.isEmpty() || nearestBottom == 0
        val strict = offsetNearBottom && lastNewestVisible
        isStrictlyAtBottom = strict
        isAtBottom = strict
        distanceFromBottom = when {
            displayRows.isEmpty() || visible.isEmpty() -> 0
            state.firstVisibleItemIndex == 0 -> state.firstVisibleItemScrollOffset.coerceAtLeast(0)
            else -> {
                val avgSize = visible.map { it.size }.average().toInt().coerceAtLeast(1)
                (state.firstVisibleItemIndex * avgSize + state.firstVisibleItemScrollOffset).coerceAtLeast(0)
            }
        }
        val viewport = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        val visibleSpan = visible.sumOf { it.size }
        contentExceedsViewport = layoutInfo.totalItemsCount > visible.size || visibleSpan > viewport
    }
}

@Composable
fun rememberChatMessageListController() = remember { ChatMessageListController() }

internal fun normalizeTransactionKind(
    requested: ChatListUpdateKind,
    anchorRowId: String?,
    oldIds: List<String>,
    newIds: List<String>,
    changedRowIds: Set<String>,
): ChatListUpdateKind {
    if (oldIds.isEmpty()) return ChatListUpdateKind.INITIAL
    if (requested == ChatListUpdateKind.JUMP) return ChatListUpdateKind.JUMP
    if (requested == ChatListUpdateKind.PREPEND_HISTORY) return ChatListUpdateKind.PREPEND_HISTORY

    if (anchorRowId != null) {
        val oldAnchor = oldIds.indexOf(anchorRowId)
        val newAnchor = newIds.indexOf(anchorRowId)
        if (oldAnchor >= 0 && newAnchor > oldAnchor) return ChatListUpdateKind.PREPEND_HISTORY
    }

    if (newIds == oldIds) {
        return if (changedRowIds.isEmpty()) requested else ChatListUpdateKind.RECONFIGURE_ROWS
    }
    if (newIds.size > oldIds.size) {
        if (newIds.takeLast(oldIds.size) == oldIds || isLikelyHistoryPrepend(oldIds, newIds)) {
            return ChatListUpdateKind.PREPEND_HISTORY
        }
        if (newIds.take(oldIds.size) == oldIds) return ChatListUpdateKind.APPEND_MESSAGES
    }
    return if (requested == ChatListUpdateKind.JUMP) ChatListUpdateKind.JUMP else ChatListUpdateKind.REPLACE_ALL
}

private fun isLikelyHistoryPrepend(oldIds: List<String>, newIds: List<String>): Boolean {
    if (oldIds.isEmpty() || newIds.size <= oldIds.size) return false
    val oldFirst = oldIds.first()
    val idx = newIds.indexOf(oldFirst)
    return idx > 0 && newIds.subList(idx, newIds.size) == oldIds
}

fun chatRenderRowVisualSignature(row: ChatRenderRow): Int = when (row) {
    is ChatRenderRow.ConversationIntro -> 31 * 5 + (row.context?.id?.hashCode() ?: 0)
    is ChatRenderRow.RequestDisclaimer -> 31 * 6 + (row.context?.id?.hashCode() ?: 0) + (row.context?.status?.hashCode() ?: 0)
    is ChatRenderRow.PendingRequestMessage ->
        31 * 7 + row.message.id.hashCode() + row.message.text.hashCode() + row.message.isOutgoing.hashCode()
    is ChatRenderRow.Header -> 31 * 0 + (row.date.time / 1000L).toInt()
    is ChatRenderRow.Message -> 31 * 1 + messageItemVisualSignature(row.item)
    is ChatRenderRow.Buzz ->
        31 * 2 + row.event.id.hashCode() + row.event.senderId.hashCode() + row.event.createdAt.time.hashCode()
    ChatRenderRow.Typing -> 3
    ChatRenderRow.HistoryStart -> 4
}

private fun messageItemVisualSignature(item: MessageItem): Int = when (item) {
    is MessageItem.Single -> {
        val m = item.message
        var h = m.id.hashCode()
        h = 31 * h + m.status.hashCode()
        h = 31 * h + (m.content?.hashCode() ?: 0)
        h = 31 * h + (m.mediaUrl?.hashCode() ?: 0)
        h = 31 * h + (m.thumbnailUrl?.hashCode() ?: 0)
        h = 31 * h + m.isDeleted.hashCode()
        h = 31 * h + (m.reactions?.hashCode() ?: 0)
        h = 31 * h + m.type.hashCode()
        h
    }
    is MessageItem.MediaCluster -> {
        var h = 31 * 1 + item.messages.size
        item.messages.forEach { m ->
            h = 31 * h + m.id.hashCode()
            h = 31 * h + m.status.hashCode()
            h = 31 * h + (m.mediaUrl?.hashCode() ?: 0)
            h = 31 * h + m.isDeleted.hashCode()
        }
        h
    }
}

@Composable
fun ChatMessageListView(
    transaction: ChatListUpdateTransaction,
    controller: ChatMessageListController,
    onReachedTop: () -> Unit,
    onContentExtentChanged: (Boolean) -> Unit = {},
    onPrependFinished: () -> Unit = {},
    onPrefetchRows: (List<ChatListRow>) -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
    /** ≡ iOS `isVanishGestureEnabled` */
    isVanishGestureEnabled: Boolean = true,
    /** ≡ iOS `isVanishModeActive` */
    isVanishModeActive: Boolean = false,
    /** ≡ iOS `composerBottomInset` (para posicionar overlay). */
    composerBottomInset: Dp = 0.dp,
    /** Row id con menú/highlight activo — eleva el slot LazyColumn (≡ iOS zIndex 100). */
    elevatedRowId: String? = null,
    onVanishPullReleased: (VanishPullResult) -> Unit = {},
    rowContent: @Composable (ChatListRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state = rememberLazyListState()
    val vanishPull = rememberChatVanishPullState()
    val onVanishPullReleasedState = rememberUpdatedState(onVanishPullReleased)
    val vanishConnection = rememberVanishPullNestedScrollConnection(
        state = vanishPull,
        listState = state,
        enabled = { isVanishGestureEnabled && !controller.isProgrammaticScroll },
        onReleased = { onVanishPullReleasedState.value(it) },
    )
    var chronoRows by remember { mutableStateOf(emptyList<ChatListRow>()) }
    var displayRows by remember { mutableStateOf(emptyList<ChatListRow>()) }
    var displayIndexById by remember { mutableStateOf(emptyMap<String, Int>()) }
    var displayIndexByMessageId by remember { mutableStateOf(emptyMap<String, Int>()) }
    var hasLoadedInitial by remember { mutableStateOf(false) }
    var historyLoadArmed by remember { mutableStateOf(true) }

    fun rebuildIndices(chrono: List<ChatListRow>) {
        chronoRows = chrono
        displayRows = chrono.asReversed()
        displayIndexById = displayRows.mapIndexed { index, row -> row.id to index }.toMap()
        displayIndexByMessageId = buildMap {
            displayRows.forEachIndexed { index, row ->
                row.messageIds.forEach { put(it, index) }
            }
        }
        controller.syncRows(chrono)
    }

    suspend fun LazyListState.goTo(displayIndex: Int, animated: Boolean) {
        if (displayIndex !in displayRows.indices) return
        controller.isProgrammaticScroll = true
        try {
            if (animated) animateScrollToItem(displayIndex) else scrollToItem(displayIndex)
        } finally {
            controller.isProgrammaticScroll = false
        }
    }

    suspend fun resolveRowDisplayIndex(id: String): Int? =
        displayIndexById[id]
            ?: displayIndexByMessageId[id]
            ?: displayIndexById[controller.resolvedRowId(id)]

    suspend fun applyScrollCommand(command: ChatListScrollCommand) {
        when (command) {
            is ChatListScrollCommand.Bottom -> {
                if (displayRows.isEmpty()) return
                // ≡ forceScrollToBottom(allowDuringNavigation: true)
                controller.resetVanishPullState(animated = false)
                state.goTo(0, command.animated)
            }
            is ChatListScrollCommand.FirstUnread -> {
                controller.scrollNavigationTargetRowId = null
                val index = resolveRowDisplayIndex(command.messageId)
                if (index != null) state.goTo(index, command.animated)
                else controller.pendingScroll = PendingScrollRequest(command.messageId, command.animated)
            }
            is ChatListScrollCommand.Highlight -> {
                controller.scrollNavigationTargetRowId = command.messageId
                val index = resolveRowDisplayIndex(command.messageId)
                if (index != null) {
                    state.goTo(index, command.animated)
                    // Clear when settled ≈ clearNavigationTargetIfSettled
                } else {
                    controller.pendingScroll = PendingScrollRequest(command.messageId, command.animated)
                }
            }
            ChatListScrollCommand.None -> Unit
        }
    }

    suspend fun resolvePendingScrollIfPossible() {
        val navId = controller.scrollNavigationTargetRowId
        if (navId != null) {
            resolveRowDisplayIndex(navId)?.let { index ->
                controller.pendingScroll = null
                state.goTo(index, animated = false)
                return
            }
        }
        val pending = controller.pendingScroll ?: return
        val index = resolveRowDisplayIndex(pending.id) ?: return
        controller.pendingScroll = null
        state.goTo(index, pending.animated)
    }

    LaunchedEffect(transaction) {
        val oldIds = chronoRows.map { it.id }
        val newIds = transaction.rows.map { it.id }
        val oldById = chronoRows.associateBy { it.id }
        val newById = transaction.rows.associateBy { it.id }
        val detectedChanged = newIds.filter { id ->
            val old = oldById[id] ?: return@filter false
            val neu = newById[id] ?: return@filter false
            old.visualSignature != neu.visualSignature
        }.toSet()
        val changed = detectedChanged + transaction.changedRowIds.filter { newById.containsKey(it) }

        if (newIds == oldIds && hasLoadedInitial && changed.isEmpty()) {
            applyScrollCommand(transaction.scrollCommand)
            return@LaunchedEffect
        }

        val wasAtBottom = controller.isAtBottom
        val oldFirst = state.firstVisibleItemIndex
        val oldOffset = state.firstVisibleItemScrollOffset
        val previousAnchorId = displayRows.getOrNull(oldFirst)?.id

        val normalized = normalizeTransactionKind(
            requested = transaction.kind,
            anchorRowId = transaction.anchorRowId,
            oldIds = oldIds,
            newIds = newIds,
            changedRowIds = changed,
        )

        rebuildIndices(transaction.rows)

        if (normalized == ChatListUpdateKind.PREPEND_HISTORY) {
            controller.suppressHistoryLoadUntilNextUserScroll = true
            val anchorId = previousAnchorId ?: transaction.anchorRowId
            if (anchorId != null && displayIndexById.containsKey(anchorId)) {
                val target = displayIndexById.getValue(anchorId)
                controller.isProgrammaticScroll = true
                try {
                    state.scrollToItem(target, oldOffset)
                } finally {
                    controller.isProgrammaticScroll = false
                }
            }
            onPrependFinished()
            applyScrollCommand(transaction.scrollCommand)
            resolvePendingScrollIfPossible()
            return@LaunchedEffect
        }

        when {
            normalized == ChatListUpdateKind.INITIAL || !hasLoadedInitial -> {
                hasLoadedInitial = true
                if (displayRows.isNotEmpty() &&
                    controller.initialScrollPolicy != ChatListInitialScrollPolicy.Deferred
                ) {
                    when (val policy = controller.initialScrollPolicy) {
                        is ChatListInitialScrollPolicy.Row ->
                            resolveRowDisplayIndex(policy.id)?.let { state.goTo(it, false) }
                                ?: state.goTo(0, false)
                        ChatListInitialScrollPolicy.AutomaticBottom,
                        ChatListInitialScrollPolicy.Deferred -> state.goTo(0, false)
                    }
                }
            }
            wasAtBottom &&
                controller.scrollNavigationTargetRowId == null &&
                normalized == ChatListUpdateKind.APPEND_MESSAGES &&
                displayRows.isNotEmpty() -> {
                state.goTo(0, animated = false)
            }
            normalized == ChatListUpdateKind.RECONFIGURE_ROWS &&
                !wasAtBottom &&
                controller.scrollNavigationTargetRowId == null &&
                previousAnchorId != null &&
                displayIndexById.containsKey(previousAnchorId) -> {
                // Mantener ancla al reconfigurar (status/reactions) sin saltar al fondo.
                val target = displayIndexById.getValue(previousAnchorId)
                controller.isProgrammaticScroll = true
                try {
                    state.scrollToItem(target, oldOffset)
                } finally {
                    controller.isProgrammaticScroll = false
                }
            }
        }

        applyScrollCommand(transaction.scrollCommand)
        resolvePendingScrollIfPossible()
    }

    LaunchedEffect(controller.command, displayIndexById, displayRows, controller.reconfigureGeneration) {
        val command = controller.command
        if (command is ChatListScrollCommand.None) return@LaunchedEffect
        applyScrollCommand(command)
        controller.consumeCommand()
    }

    LaunchedEffect(controller.nextIntent, displayIndexById, displayRows) {
        val intent = controller.nextIntent ?: return@LaunchedEffect
        when (intent) {
            is ChatListScrollIntent.Bottom -> {
                val allow = controller.allowForceScrollDuringNavigation
                if (!allow && controller.scrollNavigationTargetRowId != null) {
                    controller.consumeIntent()
                    return@LaunchedEffect
                }
                // scrollToBottom salta si ya estrictamente abajo (salvo force vía allow flag)
                if (!allow && controller.isStrictlyAtBottom) {
                    controller.consumeIntent()
                    return@LaunchedEffect
                }
                controller.resetVanishPullState(animated = false)
                if (displayRows.isNotEmpty()) state.goTo(0, intent.animated)
            }
            is ChatListScrollIntent.Row -> {
                val index = resolveRowDisplayIndex(intent.id)
                if (index != null) {
                    state.goTo(index, intent.animated)
                } else {
                    controller.pendingScroll = PendingScrollRequest(intent.id, intent.animated)
                }
            }
        }
        controller.consumeIntent()
    }

    // Gesto de usuario: liberar suppress + nav target (≡ scrollViewWillBeginDragging).
    // NO terminar vanish aquí: al enganchar el pull, isScrollInProgress pasa a false
    // (la lista ya no scrollea) y eso mataba el gesto a los pocos px → shake.
    LaunchedEffect(state) {
        snapshotFlow { state.isScrollInProgress }
            .distinctUntilChanged()
            .collect { scrolling ->
                if (scrolling && !controller.isProgrammaticScroll) {
                    controller.onUserScrollBegan()
                }
            }
    }

    LaunchedEffect(controller.vanishPullResetSignal) {
        if (controller.vanishPullResetSignal > 0) vanishPull.reset()
    }

    LaunchedEffect(isVanishGestureEnabled) {
        if (!isVanishGestureEnabled) vanishPull.reset()
    }

    LaunchedEffect(state, displayRows) {
        snapshotFlow {
            state.layoutInfo.visibleItemsInfo.map { it.index to it.offset }
        }
            .distinctUntilChanged()
            .collect {
                controller.updateViewport(state, displayRows)
                onContentExtentChanged(controller.contentExceedsViewport)

                // ≡ iOS: solo limpiar si el usuario se fue del fondo y ya no arrastra vanish.
                if (!controller.isStrictlyAtBottom &&
                    vanishPull.isActive &&
                    !vanishPull.isDragging
                ) {
                    vanishPull.reset()
                }

                val topish = state.layoutInfo.visibleItemsInfo.maxOfOrNull { it.index } ?: 0
                if (historyLoadArmed &&
                    !controller.suppressHistoryLoadUntilNextUserScroll &&
                    displayRows.isNotEmpty() &&
                    topish >= (displayRows.lastIndex - 10).coerceAtLeast(0)
                ) {
                    historyLoadArmed = false
                    onReachedTop()
                    delay(350)
                    historyLoadArmed = true
                }

                val first = state.firstVisibleItemIndex
                val from = (first - 5).coerceAtLeast(0)
                val to = (first + 40).coerceAtMost(displayRows.size)
                if (from < to) onPrefetchRows(displayRows.subList(from, to))
            }
    }

    // Si el nav target sigue visible y el usuario no scrollea, podemos limpiarlo al estabilizar.
    LaunchedEffect(controller.scrollNavigationTargetRowId, displayIndexById) {
        val nav = controller.scrollNavigationTargetRowId ?: return@LaunchedEffect
        snapshotFlow { state.isScrollInProgress }
            .filter { !it }
            .collect {
                val index = resolveRowDisplayIndex(nav) ?: return@collect
                val visible = state.layoutInfo.visibleItemsInfo.any { it.index == index }
                if (visible) controller.clearNavigationTarget()
            }
    }

    val reconfigureGen = controller.reconfigureGeneration
    val liftPx = vanishPull.lift
    // Solo elevación del vanish pull. Un offset fijo de reposo (+Y) empujaba el
    // último mensaje debajo del composer (contentPadding ya despeja el input).
    val threadTranslationPx = -liftPx
    // Patrón sinasamaki: nestedScroll en el padre; LazyColumn con overscrollEffect=null
    // para que el sobrante llegue a onPostScroll (el glow nativo se lo comía).
    Box(
        modifier
            .fillMaxSize()
            .nestedScroll(vanishConnection)
            .pointerInput(vanishPull) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            if (event.changes.all { !it.pressed }) break
                        }
                    } finally {
                        if (vanishPull.isActive) {
                            onVanishPullReleasedState.value(vanishPull.finishRelease())
                        }
                    }
                }
            },
    ) {
        LazyColumn(
            state = state,
            reverseLayout = true,
            overscrollEffect = null,
            verticalArrangement = Arrangement.spacedBy(ChatListLayoutMetrics.interGroupSpacing),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { translationY = threadTranslationPx },
            contentPadding = contentPadding,
        ) {
            itemsIndexed(
                items = displayRows,
                key = { _, row -> "${row.id}|${row.visualSignature}|$reconfigureGen" },
            ) { _, row ->
                // ≡ iOS `.zIndex(100)` en la fila seleccionada: el lift/scale debe
                // ganar a vecinos del LazyColumn (zIndex solo vale entre siblings).
                Box(
                    Modifier
                        .zIndex(if (row.id == elevatedRowId) 100f else 0f)
                        .onGloballyPositioned { coords ->
                            controller.reportRowFrame(row.id, coords.boundsInWindow())
                        },
                ) {
                    rowContent(row)
                }
            }
        }
        ChatVanishPullOverlay(
            conversationLift = vanishPull.lift,
            progress = vanishPull.progress,
            isActive = isVanishModeActive,
            isDragging = vanishPull.isDragging,
            composerBottomInset = composerBottomInset,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}
