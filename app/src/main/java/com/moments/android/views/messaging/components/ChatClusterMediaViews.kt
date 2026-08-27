package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.ScreenshotProtectionMode
import kotlin.math.abs
import java.net.URI

@Composable
fun GlassmorphicClusterRow(
    messages: List<EnhancedMessage>,
    isCurrentUser: Boolean,
    uploadProgress: Map<String, Double>,
    onOpenCluster: (List<EnhancedMessage>) -> Unit,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    showAvatar: Boolean = false,
    otherUserId: String? = null,
    isOtherParticipantUnavailable: Boolean = false,
    otherParticipantName: String = "",
    repliedMessage: EnhancedMessage? = null,
    onAvatarTap: () -> Unit = {},
    onReply: () -> Unit = {},
    onReplyTap: ((String) -> Unit)? = null,
    displayReactions: (String) -> Map<String, List<String>>? = { null },
    onReaction: (EnhancedMessage, String) -> Unit = { _, _ -> },
    showSeenLabel: Boolean = false,
    isStarred: Boolean = false,
    isMenuSelected: Boolean = false,
    isBubbleFlashing: Boolean = false,
    onLongPress: ((EnhancedMessage, ChatMessageLiftSnapshot) -> Unit)? = null,
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (messages.isEmpty()) return
    val aggregate = clusterAggregateStatus(messages)
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 5.dp),
        horizontalArrangement = if (isCurrentUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        if (!isCurrentUser) {
            ChatIncomingAvatarGutter(showAvatar, otherUserId, isOtherParticipantUnavailable, onAvatarTap)
        }
        Column(
            modifier = Modifier.wrapContentWidth(
                if (isCurrentUser) Alignment.End else Alignment.Start,
            ),
            horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
        ) {
            repliedMessage?.let {
                StackedReplyQuote(it, isCurrentUser, otherParticipantName, onReplyTap)
            }
            Box {
                MediaGridBubble(
                    messages = messages,
                    isCurrentUser = isCurrentUser,
                    uploadProgress = uploadProgress,
                    onOpenCluster = onOpenCluster,
                    onHydrateMedia = onHydrateMedia,
                    displayReactions = displayReactions,
                    onReaction = onReaction,
                    onLongPress = onLongPress,
                    isMenuSelected = isMenuSelected,
                    isBubbleFlashing = isBubbleFlashing,
                    onReply = onReply,
                    onDoubleTap = onDoubleTap,
                )
                if (isStarred) {
                    MessageStarBadge(
                        cluster = true,
                        modifier = Modifier
                            .align(if (isCurrentUser) Alignment.TopStart else Alignment.TopEnd)
                            .offset(x = if (isCurrentUser) (-4).dp else 4.dp, y = (-4).dp),
                    )
                }
            }
            if (isCurrentUser) {
                val representative = messages.last()
                MessageTimestamp(
                    message = representative,
                    isCurrentUser = true,
                    showSeenLabel = showSeenLabel,
                    overrideStatus = aggregate,
                )
            }
        }
    }
}

/** Timestamp callers use this aggregate while the rest of the row remains media-only. */
fun clusterAggregateStatus(messages: List<EnhancedMessage>): MessageStatus =
    ClusterMessageStatusAggregator.aggregate(messages)

/** Port de `Views/Messaging/Components/ChatClusterMediaViews.swift`. */
object ClusterMessageStatusAggregator {
    private val priority = mapOf(
        MessageStatus.FAILED to -2,
        MessageStatus.PENDING to -1,
        MessageStatus.SENDING to 0,
        MessageStatus.SENT to 1,
        MessageStatus.DELIVERED to 2,
        MessageStatus.READ to 3,
    )

    fun aggregate(messages: List<EnhancedMessage>): MessageStatus =
        messages.minByOrNull { priority[it.status] ?: 0 }?.status ?: MessageStatus.SENT
}

sealed interface ClusterMessageItem {
    data class Single(val message: EnhancedMessage) : ClusterMessageItem
    data class MediaCluster(val messages: List<EnhancedMessage>) : ClusterMessageItem
}

object ClusterMessageGrouper {
    private const val burstWindowMillis = 60_000L

    fun shouldAppendToCluster(message: EnhancedMessage, cluster: List<EnhancedMessage>): Boolean {
        val last = cluster.lastOrNull() ?: return true
        if (message.senderId != last.senderId) return false
        val batch = message.mediaBatchId?.takeIf { it.isNotBlank() }
        val lastBatch = last.mediaBatchId?.takeIf { it.isNotBlank() }
        // ≡ iOS: si ambos tienen batch → igualdad; si no, ventana de ráfaga (un solo batch no bloquea).
        if (batch != null && lastBatch != null) return batch == lastBatch
        val delta = message.timestamp.time - last.timestamp.time
        return delta in 0..burstWindowMillis
    }

    fun group(input: List<EnhancedMessage>): List<ClusterMessageItem> {
        val result = mutableListOf<ClusterMessageItem>()
        val current = mutableListOf<EnhancedMessage>()
        fun flush() {
            if (current.isEmpty()) return
            result += if (current.size == 1) ClusterMessageItem.Single(current.first()) else ClusterMessageItem.MediaCluster(current.toList())
            current.clear()
        }
        input.forEach { message ->
            if (message.isDeleted) {
                flush()
                result += ClusterMessageItem.Single(message)
            } else if (message.type == MessageType.IMAGE || message.type == MessageType.VIDEO) {
                if (current.isEmpty() || shouldAppendToCluster(message, current)) {
                    current += message
                } else {
                    flush()
                    current += message
                }
            } else {
                flush()
                result += ClusterMessageItem.Single(message)
            }
        }
        flush()
        return result
    }
}

object ClusterMediaLayout {
    val frontWidth = 196.dp
    val frontHeight = 244.dp
    val cornerRadius = 12.dp
    val fanBottomPadding = 10.dp
    const val maxVisible = 5
    val rotations = listOf(-4f, 3f, -2.5f, 4f, -3f)
    val offsets = listOf(0.dp to 0.dp, 10.dp to (-10).dp, (-8).dp to (-19).dp, 14.dp to (-27).dp, (-6).dp to (-34).dp)
    fun fanTopPadding(count: Int): Dp =
        offsets.take(count.coerceAtLeast(1)).maxOfOrNull { -it.second }?.plus(10.dp) ?: 10.dp
    fun fanSidePadding(count: Int): Dp =
        offsets.take(count.coerceAtLeast(1)).maxOfOrNull { abs(it.first.value).dp }?.plus(12.dp) ?: 12.dp
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaGridBubble(
    messages: List<EnhancedMessage>,
    isCurrentUser: Boolean,
    uploadProgress: Map<String, Double>,
    onOpenCluster: (List<EnhancedMessage>) -> Unit,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    displayReactions: (String) -> Map<String, List<String>>? = { null },
    onReaction: (EnhancedMessage, String) -> Unit = { _, _ -> },
    onLongPress: ((EnhancedMessage, ChatMessageLiftSnapshot) -> Unit)? = null,
    isMenuSelected: Boolean = false,
    isBubbleFlashing: Boolean = false,
    onReply: () -> Unit = {},
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val front = messages.firstOrNull() ?: return
    if (messages.all { it.isDeleted }) {
        DeletedMessageBubble(front, isCurrentUser, modifier)
        return
    }
    val active = remember(messages) { messages.filterNot { it.isDeleted } }
    if (active.isEmpty()) return
    val visible = active.take(ClusterMediaLayout.maxVisible)
    val hasVideo = active.any { it.type == MessageType.VIDEO }
    val vanishProtected = active.any { it.isVanishModeMessage }
    val swipeState = rememberChatReplySwipeState()
    val density = LocalDensity.current
    val a11yLabel = stringResource(
        if (isCurrentUser) R.string.chat_cluster_sent_photos else R.string.chat_cluster_received_photos,
        active.size,
    )
    val a11yHint = stringResource(R.string.chat_a11y_open_media)

    LaunchedEffect(active.map { it.id }) {
        active.forEach { onHydrateMedia?.invoke(it) }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(
                when {
                    isCurrentUser && hasVideo -> R.string.chat_cluster_sent_items
                    isCurrentUser -> R.string.chat_cluster_sent_photos
                    hasVideo -> R.string.chat_cluster_received_items
                    else -> R.string.chat_cluster_received_photos
                },
                active.size,
            ),
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 6.dp),
        )

        ChatBubbleReplySwipeContainer(
            state = swipeState,
            isOutgoing = isCurrentUser,
            cornerRadius = ChatBubbleAnchorMetrics.clusterCornerRadius,
            onReply = onReply,
            modifier = if (onDoubleTap != null) {
                Modifier.pointerInput(active.map { it.id }) {
                    detectTapGestures(onDoubleTap = { onDoubleTap() })
                }
            } else {
                Modifier
            },
        ) {
            ChatMessageBubbleChrome(
                isMenuSelected = isMenuSelected,
                isOutgoing = isCurrentUser,
                isFlashing = isBubbleFlashing,
                onTap = { onOpenCluster(active) },
                onLongPress = { snapshot ->
                    onLongPress?.invoke(active.firstOrNull() ?: front, snapshot)
                },
            ) {
                val grid: @Composable () -> Unit = {
                    Box(
                        modifier = Modifier
                            .padding(
                                top = ClusterMediaLayout.fanTopPadding(visible.size),
                                start = ClusterMediaLayout.fanSidePadding(visible.size),
                                end = ClusterMediaLayout.fanSidePadding(visible.size),
                                bottom = ClusterMediaLayout.fanBottomPadding,
                            )
                            .size(ClusterMediaLayout.frontWidth, ClusterMediaLayout.frontHeight)
                            .semantics { contentDescription = "$a11yLabel. $a11yHint" },
                    ) {
                        // Dorso → frente (índice 0 = frontal)
                        visible.asReversed().forEachIndexed { reversedIndex, message ->
                            val index = visible.lastIndex - reversedIndex
                            val (ox, oy) = ClusterMediaLayout.offsets[index]
                            val isFront = index == 0
                            val frontReactions = if (isFront) displayReactions(message.id) else null
                            val reactionHang = MessageReactionMetrics.hangOffset(compact = false, cluster = true)
                            val edge = MessageReactionMetrics.horizontalHangOffset(
                                compact = false,
                                anchoredInsideBounds = false,
                            )
                            val hitInset = MessageReactionMetrics.clusterHitTargetInset(compact = false)
                            Box(
                                modifier = Modifier
                                    .zIndex((10 - index).toFloat())
                                    .graphicsLayer {
                                        rotationZ = ClusterMediaLayout.rotations[index]
                                        translationX = with(density) { ox.toPx() }
                                        translationY = with(density) { oy.toPx() }
                                    }
                                    .size(ClusterMediaLayout.frontWidth, ClusterMediaLayout.frontHeight)
                                    .then(
                                        if (isFront && !frontReactions.isNullOrEmpty()) {
                                            Modifier.padding(
                                                bottom = MessageReactionMetrics.reactionRowSpacing(
                                                    compact = false,
                                                    cluster = true,
                                                ),
                                            )
                                        } else {
                                            Modifier
                                        },
                                    ),
                            ) {
                                MediaGridTileView(
                                    message = message,
                                    progress = uploadProgress[message.id],
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(ClusterMediaLayout.cornerRadius)),
                                )
                                if (message.type == MessageType.VIDEO) {
                                    Icon(
                                        Icons.Default.PlayArrow,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(6.dp)
                                            .size(14.dp),
                                    )
                                }
                                if (isFront) {
                                    ClusterCountBadge(Modifier.align(Alignment.TopEnd).padding(8.dp))
                                }
                                if (isFront && !frontReactions.isNullOrEmpty()) {
                                    MessageReactionChip(
                                        reactions = frontReactions,
                                        onTap = { emoji -> onReaction(message, emoji) },
                                        compact = false,
                                        cluster = true,
                                        modifier = Modifier
                                            .align(if (isCurrentUser) Alignment.BottomStart else Alignment.BottomEnd)
                                            .offset(
                                                x = if (isCurrentUser) edge - hitInset else -edge + hitInset,
                                                y = reactionHang + hitInset,
                                            )
                                            .zIndex(10f),
                                    )
                                }
                            }
                        }
                    }
                }
                if (vanishProtected) {
                    ScreenshotProtectedView(
                        isProtected = true,
                        mode = ScreenshotProtectionMode.WindowFlag,
                    ) { grid() }
                } else {
                    grid()
                }
            }
        }
    }
}

@Composable
fun MediaGridTileView(
    message: EnhancedMessage,
    progress: Double?,
    isDownloadingMedia: Boolean = false,
    downloadProgress: Double? = null,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier = modifier
            .background(
                Color(0xFFFAF9F6).copy(alpha = if (isDark) 0.06f else 0.22f),
            ),
    ) {
        Box(Modifier.fillMaxSize()) {
            MediaGridTileContent(message, isDownloadingMedia)
        }
        when {
            isDownloadingMedia -> {
                ChatMediaDownloadProgressOverlay(
                    progress = downloadProgress ?: 0.03,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            message.status == MessageStatus.SENDING -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0xFF0B1215).copy(alpha = 0.38f)),
                    contentAlignment = Alignment.Center,
                ) {
                    MediaProgressRing(
                        progress = maxOf(progress ?: 0.03, 0.03),
                        size = 42.dp,
                        lineWidth = 3.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaGridTileContent(message: EnhancedMessage, isDownloadingMedia: Boolean) {
    val isDark = isSystemInDarkTheme()
    when (message.type) {
        MessageType.IMAGE -> when {
            isDownloadingMedia -> {
                val preview = message.previewThumbnailURLForDisplay
                if (!preview.isNullOrBlank()) {
                    AsyncImage(
                        preview,
                        null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().blur(18.dp),
                    )
                } else {
                    ChatMediaResolvingPlaceholder(Modifier.fillMaxSize())
                }
            }
            message.isMediaAwaitingManualDownload -> {
                val preview = message.previewThumbnailURLForDisplay
                if (!preview.isNullOrBlank()) {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(18.dp))
                        ChatMediaDownloadOverlay(message.formattedDownloadSize)
                    }
                } else {
                    ChatMediaManualDownloadPlaceholder(message.formattedDownloadSize, modifier = Modifier.fillMaxSize())
                }
            }
            message.isMediaPendingResolution -> ChatMediaResolvingPlaceholder(Modifier.fillMaxSize())
            !message.mediaUrl.isNullOrBlank() && message.localMediaFileIsReachable(message.mediaUrl!!) -> {
                AsyncImage(message.mediaUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            else -> MediaGridTilePlaceholder(isVideo = false, isDark = isDark)
        }
        MessageType.VIDEO -> when {
            isDownloadingMedia -> {
                val preview = message.previewThumbnailURLForDisplay
                if (!preview.isNullOrBlank()) {
                    AsyncImage(preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(18.dp))
                } else {
                    ChatMediaResolvingPlaceholder(Modifier.fillMaxSize())
                }
            }
            message.isMediaAwaitingManualDownload -> {
                val preview = message.previewThumbnailURLForDisplay
                if (!preview.isNullOrBlank()) {
                    Box(Modifier.fillMaxSize()) {
                        AsyncImage(preview, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(18.dp))
                        ChatMediaDownloadOverlay(message.formattedDownloadSize)
                    }
                } else {
                    ChatMediaManualDownloadPlaceholder(
                        sizeLabel = message.formattedDownloadSize,
                        showsVideoBadge = true,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            message.isMediaPendingResolution || message.needsVideoThumbnailForDisplay -> {
                ChatMediaResolvingPlaceholder(Modifier.fillMaxSize())
            }
            !message.thumbnailUrl.isNullOrBlank() && message.localMediaFileIsReachable(message.thumbnailUrl!!) -> {
                AsyncImage(message.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            else -> MediaGridTilePlaceholder(isVideo = true, isDark = isDark)
        }
        else -> MediaGridTilePlaceholder(isVideo = false, isDark = isDark)
    }
}

@Composable
private fun MediaGridTilePlaceholder(isVideo: Boolean, isDark: Boolean) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                if (isDark) Color(0xFFFAF9F6).copy(alpha = 0.1f) else Color(0xFF0B1215).copy(alpha = 0.06f),
                RoundedCornerShape(ClusterMediaLayout.cornerRadius),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (isVideo) Icons.Default.VideoFile else Icons.Default.Photo,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ClusterCountBadge(modifier: Modifier = Modifier) {
    androidx.compose.foundation.Image(
        painterResource(R.drawable.carousel_post_icon),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(20.dp),
    )
}

data class ClusterWrapper(val messages: List<EnhancedMessage>) {
    val id: String get() = messages.firstOrNull()?.id ?: "empty-cluster"
}

data class ClusterGallerySelection(val anchorMessageId: String, val messageIds: List<String>) {
    val id: String get() = anchorMessageId
}

enum class ClusterGalleryPresentation { MODAL, PUSHED }
enum class ClusterGalleryScope { CLUSTER, CONVERSATION_SHARED }
enum class ClusterGalleryTab { MEDIA, LINKS }

/** ≡ iOS `ClusterGalleryDetailRoute` — índice en `visibleMessages` para push de detalle. */
data class ClusterGalleryDetailRoute(val index: Int) {
    val id: Int get() = index
}

private const val galleryDeleteEveryoneWindowMillis = 7_200_000L
private val gallerySpacing = 14.dp

/**
 * Port de `ClusterGalleryView` — masonry 2 cols, selección, delete dialog, tabs Media/Links.
 * `detail` ≡ ViewBuilder iOS: host in-gallery (pop vuelve al grid), no overlay externo.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ClusterGalleryView(
    messages: List<EnhancedMessage>,
    currentUserId: String,
    scope: ClusterGalleryScope = ClusterGalleryScope.CLUSTER,
    presentation: ClusterGalleryPresentation = ClusterGalleryPresentation.MODAL,
    initialTab: ClusterGalleryTab = ClusterGalleryTab.MEDIA,
    onClose: () -> Unit,
    onOpenMedia: (EnhancedMessage) -> Unit,
    onPrepareDownload: ((EnhancedMessage) -> Unit)? = null,
    onDeleteForMe: ((List<EnhancedMessage>) -> Unit)? = null,
    onDeleteForEveryone: ((List<EnhancedMessage>) -> Unit)? = null,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    isDownloadingMedia: (String) -> Boolean = { false },
    downloadProgress: (String) -> Double? = { null },
    /** ≡ iOS `detail:` — FullScreenMedia within gallery stack; null → fallback [onOpenMedia]. */
    detail: (@Composable (message: EnhancedMessage, onDismissDetail: () -> Unit) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var tab by remember { mutableStateOf(initialTab) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var showDeleteConfirmation by remember { mutableStateOf(false) }
    var detailRoute by remember { mutableStateOf<ClusterGalleryDetailRoute?>(null) }
    val uriHandler = LocalUriHandler.current
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val contentColor = if (isDark) Color.White else Color(0xFF0B1215)
    val scroll = rememberScrollState()
    var hadGalleryContent by remember { mutableStateOf(false) }

    val available = remember(messages) { messages.filterNot { it.isDeleted } }
    val galleryIds = remember(available) { available.map { it.id } }
    val visible = remember(available, tab, scope) {
        when {
            scope == ClusterGalleryScope.CLUSTER -> available.filter { it.type == MessageType.IMAGE || it.type == MessageType.VIDEO }
            tab == ClusterGalleryTab.MEDIA -> available.filter { it.type == MessageType.IMAGE || it.type == MessageType.VIDEO }
            else -> available.filter { it.type == MessageType.TEXT && ChatLinkOpener.containsLink(it.content.orEmpty()) }
        }
    }
    val selectedMessages = remember(visible, selectedIds) { visible.filter { it.id in selectedIds } }
    val deletableForEveryone = remember(selectedMessages, currentUserId) {
        selectedMessages.filter { canDeleteGalleryMessageForEveryone(it, currentUserId) }
    }

    LaunchedEffect(galleryIds) {
        selectedIds = selectedIds.intersect(visible.map { it.id }.toSet())
        if (galleryIds.isNotEmpty()) {
            hadGalleryContent = true
        } else if (hadGalleryContent) {
            // ≡ iOS onChange: cierra solo si había ítems y ahora está vacío
            detailRoute = null
            onClose()
            return@LaunchedEffect
        }
        if (selectedIds.isEmpty()) selectionMode = false
    }
    LaunchedEffect(tab) {
        selectedIds = selectedIds.intersect(visible.map { it.id }.toSet())
        if (selectedIds.isEmpty()) selectionMode = false
        // Links tab no tiene detalle media
        if (tab == ClusterGalleryTab.LINKS) detailRoute = null
    }
    LaunchedEffect(visible.map { it.id }) {
        visible.forEach { onHydrateMedia?.invoke(it) }
        val route = detailRoute
        if (route != null && visible.isEmpty()) {
            detailRoute = null
        } else if (route != null && route.index !in visible.indices) {
            detailRoute = ClusterGalleryDetailRoute(route.index.coerceIn(0, visible.lastIndex))
        }
    }

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun toggleSelection(id: String) {
        selectedIds = if (id in selectedIds) {
            val next = selectedIds - id
            if (next.isEmpty()) selectionMode = false
            next
        } else {
            selectedIds + id
        }
    }

    fun enterSelection(id: String) {
        selectionMode = true
        selectedIds = setOf(id)
    }

    fun dismissDetail() {
        detailRoute = null
    }

    fun closeGallery() {
        // ≡ iOS closeGallery: limpia push de detalle antes de salir
        detailRoute = null
        onClose()
    }

    fun openMessage(message: EnhancedMessage) {
        if (scope == ClusterGalleryScope.CONVERSATION_SHARED && tab == ClusterGalleryTab.LINKS) {
            ChatLinkOpener.openFirstLink(message.content.orEmpty(), uriHandler::openUri)
            return
        }
        val index = visible.indexOfFirst { it.id == message.id }
        if (index < 0) return
        if ((message.type == MessageType.IMAGE || message.type == MessageType.VIDEO) && message.needsDownloadForPlayback) {
            // ≡ iOS: solo descarga; el usuario vuelve a pulsar cuando esté listo.
            (onPrepareDownload ?: onOpenMedia).invoke(message)
            return
        }
        if (detail != null) {
            // ≡ modalPath.append / pushedDetailRoute = route
            detailRoute = ClusterGalleryDetailRoute(index)
        } else {
            onOpenMedia(message)
        }
    }

    Box(modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            // Chat edge-to-edge: mismo patrón que ChatCamera / ConversationVanishModeView.
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
            .background(background),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (presentation == ClusterGalleryPresentation.MODAL) {
                    Icons.Default.Close
                } else {
                    Icons.AutoMirrored.Filled.ArrowBack
                },
                contentDescription = stringResource(R.string.common_cancel),
                tint = contentColor,
                modifier = Modifier
                    .size(40.dp)
                    .clickable(onClick = ::closeGallery)
                    .padding(8.dp),
            )
            Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.chat_gallery_title), fontWeight = FontWeight.SemiBold, color = contentColor)
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(if (selectionMode) R.string.common_cancel else R.string.chat_gallery_select),
                color = if (selectionMode) Color.Red else contentColor,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                modifier = Modifier
                    .clickable {
                        if (selectionMode) exitSelection() else selectionMode = true
                    }
                    .padding(8.dp),
            )
        }

        if (scope == ClusterGalleryScope.CONVERSATION_SHARED) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 8.dp)) {
                GalleryTabButton(
                    title = R.string.chat_gallery_tab_media,
                    selected = tab == ClusterGalleryTab.MEDIA,
                    contentColor = contentColor,
                    onClick = { tab = ClusterGalleryTab.MEDIA },
                    modifier = Modifier.weight(1f),
                )
                GalleryTabButton(
                    title = R.string.chat_gallery_tab_links,
                    selected = tab == ClusterGalleryTab.LINKS,
                    contentColor = contentColor,
                    onClick = { tab = ClusterGalleryTab.LINKS },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (tab == ClusterGalleryTab.LINKS && scope == ClusterGalleryScope.CONVERSATION_SHARED) {
            LazyColumn(
                Modifier.weight(1f).padding(horizontal = gallerySpacing, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(gallerySpacing),
            ) {
                columnItems(visible, key = { it.id }) { message ->
                    GalleryLinkCard(
                        message = message,
                        selectionMode = selectionMode,
                        selected = message.id in selectedIds,
                        isDark = isDark,
                        onClick = {
                            if (selectionMode) toggleSelection(message.id) else openMessage(message)
                        },
                        onLongClick = {
                            if (!selectionMode) enterSelection(message.id)
                        },
                    )
                }
            }
        } else {
            val (left, right) = remember(visible) { distributeGalleryColumns(visible) }
            Row(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scroll)
                    .padding(horizontal = gallerySpacing, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(gallerySpacing),
                verticalAlignment = Alignment.Top,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gallerySpacing)) {
                    left.forEach { message ->
                        GalleryMediaCard(
                            message = message,
                            selectionMode = selectionMode,
                            selected = message.id in selectedIds,
                            isDownloading = isDownloadingMedia(message.id),
                            downloadProgress = downloadProgress(message.id),
                            onClick = {
                                if (selectionMode) toggleSelection(message.id) else openMessage(message)
                            },
                            onLongClick = {
                                if (!selectionMode) enterSelection(message.id)
                            },
                            onAppear = { onHydrateMedia?.invoke(message) },
                        )
                    }
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(gallerySpacing)) {
                    right.forEach { message ->
                        GalleryMediaCard(
                            message = message,
                            selectionMode = selectionMode,
                            selected = message.id in selectedIds,
                            isDownloading = isDownloadingMedia(message.id),
                            downloadProgress = downloadProgress(message.id),
                            onClick = {
                                if (selectionMode) toggleSelection(message.id) else openMessage(message)
                            },
                            onLongClick = {
                                if (!selectionMode) enterSelection(message.id)
                            },
                            onAppear = { onHydrateMedia?.invoke(message) },
                        )
                    }
                }
            }
        }

        if (selectionMode) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.04f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chat_gallery_selected_count, selectedIds.size),
                    fontWeight = FontWeight.SemiBold,
                    color = contentColor,
                    fontSize = 14.sp,
                )
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = selectedIds.isNotEmpty()) { showDeleteConfirmation = true }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                    Text(stringResource(R.string.common_delete), color = Color.Red, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }

    if (showDeleteConfirmation) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text(stringResource(R.string.chat_gallery_delete_prompt)) },
            confirmButton = {
                Text(
                    stringResource(R.string.chat_action_delete_for_me),
                    color = Color.Red,
                    modifier = Modifier
                        .clickable {
                            onDeleteForMe?.invoke(selectedMessages)
                            showDeleteConfirmation = false
                            exitSelection()
                        }
                        .padding(8.dp),
                )
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (deletableForEveryone.isNotEmpty()) {
                        Text(
                            stringResource(R.string.chat_action_delete_for_everyone),
                            color = Color.Red,
                            modifier = Modifier
                                .clickable {
                                    onDeleteForEveryone?.invoke(deletableForEveryone)
                                    showDeleteConfirmation = false
                                    exitSelection()
                                }
                                .padding(8.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.common_cancel),
                        modifier = Modifier
                            .clickable { showDeleteConfirmation = false }
                            .padding(8.dp),
                    )
                }
            },
        )
    }

    // ≡ ClusterGalleryDetailHost + navigationDestination(item:)
    val activeRoute = detailRoute
    if (activeRoute != null && detail != null && visible.isNotEmpty()) {
        val detailMessage = visible[activeRoute.index.coerceIn(0, visible.lastIndex)]
        androidx.activity.compose.BackHandler { dismissDetail() }
        Box(
            Modifier
                .fillMaxSize()
                .zIndex(2f)
                .background(background),
        ) {
            detail(detailMessage) { dismissDetail() }
        }
    }
    } // Box
}

private fun canDeleteGalleryMessageForEveryone(message: EnhancedMessage, currentUserId: String): Boolean =
    message.senderId == currentUserId &&
        !message.isDeleted &&
        !message.isRead &&
        System.currentTimeMillis() - message.timestamp.time < galleryDeleteEveryoneWindowMillis

/** ≡ iOS `distribute` — balancea altura relativa 1/aspectRatio en dos columnas. */
private fun distributeGalleryColumns(items: List<EnhancedMessage>): Pair<List<EnhancedMessage>, List<EnhancedMessage>> {
    val left = mutableListOf<EnhancedMessage>()
    val right = mutableListOf<EnhancedMessage>()
    var leftHeight = 0f
    var rightHeight = 0f
    items.forEach { message ->
        val relativeHeight = 1f / galleryAspectRatio(message)
        if (leftHeight <= rightHeight) {
            left += message
            leftHeight += relativeHeight
        } else {
            right += message
            rightHeight += relativeHeight
        }
    }
    return left to right
}

@Composable
private fun GalleryTabButton(
    @androidx.annotation.StringRes title: Int,
    selected: Boolean,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            stringResource(title),
            color = if (selected) contentColor else Color.Gray,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            fontSize = 13.sp,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(2.dp)
                .background(if (selected) Color(0xFF007AFF) else Color.Transparent),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryMediaCard(
    message: EnhancedMessage,
    selectionMode: Boolean,
    selected: Boolean,
    isDownloading: Boolean,
    downloadProgress: Double?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onAppear: () -> Unit,
) {
    val ratio = galleryAspectRatio(message)
    LaunchedEffect(message.id) { onAppear() }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        MediaGridTileView(
            message = message,
            progress = null,
            isDownloadingMedia = isDownloading,
            downloadProgress = downloadProgress,
            modifier = Modifier.fillMaxSize(),
        )
        if (message.type == MessageType.VIDEO && !isDownloading) {
            Row(
                Modifier.align(Alignment.BottomStart).padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(11.dp))
                message.formattedDuration?.let {
                    Text(it, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        if (selectionMode && selected) {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
        }
        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GalleryLinkCard(
    message: EnhancedMessage,
    selectionMode: Boolean,
    selected: Boolean,
    isDark: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val raw = message.content.orEmpty()
    val host = ChatLinkOpener.firstUrl(raw)?.let { runCatching { URI(it).host }.getOrNull() }.orEmpty()
    Box(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = if (isDark) 0.08f else 0.6f))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(min = 96.dp)
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Link, null, tint = Color(0xFF007AFF), modifier = Modifier.size(20.dp))
                if (host.isNotEmpty()) {
                    Text(host, color = Color(0xFF007AFF), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            Text(
                raw,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = if (isDark) Color.White.copy(0.85f) else Color.Black.copy(0.85f),
            )
        }
        if (selectionMode && selected) {
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.38f)))
        }
        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                contentDescription = null,
                tint = if (selected) Color.White else Color.White.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(22.dp),
            )
        }
    }
}

private fun galleryAspectRatio(message: EnhancedMessage): Float =
    message.mediaWidth?.let { width ->
        message.mediaHeight?.takeIf { it > 0 }?.let { height ->
            (width.toFloat() / height).coerceIn(0.5f, 1.9f)
        }
    } ?: 0.8f

@Composable
fun GlassmorphicMediaSelectionSheet(
    messages: List<EnhancedMessage>,
    onSelect: (EnhancedMessage) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color(0xFF0B1215)
    val background = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(background)
            .padding(bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(top = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.chat_reply_select_item),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
            )
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Close,
                contentDescription = stringResource(R.string.common_cancel),
                tint = primary.copy(alpha = 0.6f),
                modifier = Modifier.size(24.dp).clickable(onClick = onCancel),
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            items(messages, key = { it.id }) { message ->
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onSelect(message) },
                ) {
                    MediaGridTileView(message, null, modifier = Modifier.fillMaxSize())
                    if (message.type == MessageType.VIDEO) {
                        Icon(
                            Icons.Default.PlayArrow,
                            null,
                            tint = Color.White,
                            modifier = Modifier.align(Alignment.BottomStart).padding(10.dp).size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
