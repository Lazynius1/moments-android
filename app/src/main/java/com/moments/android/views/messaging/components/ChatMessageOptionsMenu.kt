package com.moments.android.views.messaging.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.EmojiReactionDefaults
import com.moments.android.utilities.EmojiUsageTracker
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.emojiPickerCatalog
import com.moments.android.views.creator.emojiSupportsSkinTone
import com.moments.android.views.creator.emojiWithoutSkinTone
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.core.ChatMessagePolicy
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import java.util.Date
import java.text.DateFormat
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class ChatMessageMenuSelection(
    val rowId: String,
    val message: EnhancedMessage,
    /** Frame en coordenadas de ventana (`boundsInWindow`); el overlay lo pasa a local. */
    val anchorFrame: Rect = Rect.Zero,
    val anchorCornerRadius: Float = ChatBubbleAnchorMetrics.cornerRadiusFor(message),
    val isOutgoing: Boolean,
    val liftedImage: ImageBitmap? = null,
    val clusterMessages: List<EnhancedMessage>? = null,
    /** Desplazamiento vertical de la fila viva seleccionada al abrir el menú. */
    val liftOffsetY: Float = 0f,
)

data class ChatMessageLiftSnapshot(
    val frame: Rect,
    val cornerRadius: Float,
    val image: ImageBitmap?,
)

object ChatBubbleAnchorMetrics {
    const val menuSelectionScale = 1.03f
    const val highlightScale = menuSelectionScale
    const val highlightDurationMillis = 1500L
    const val pressScale = 0.97f
    const val clusterCornerRadius = 16f

    fun cornerRadiusFor(message: EnhancedMessage): Float = when (message.type) {
        MessageType.TEXT -> 20f
        MessageType.AUDIO -> 18f
        MessageType.IMAGE, MessageType.VIDEO,
        MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO,
        MessageType.LOCATION, MessageType.EPHEMERAL,
        MessageType.SHARED_MOMENT, MessageType.SHARED_STORY,
        -> 16f
        MessageType.GIF, MessageType.STICKER -> 12f
        MessageType.FILE -> 14f
        else -> 16f
    }
}

object ChatMenuDimming {
    const val inactiveOpacity = 0.42f
}

fun Modifier.chatMenuDimmedUnlessSelected(isSelected: Boolean, menuOpen: Boolean): Modifier =
    alpha(if (menuOpen && !isSelected) ChatMenuDimming.inactiveOpacity else 1f)
        .blur(
            radius = if (menuOpen && !isSelected) 9.dp else 0.dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )

fun Modifier.chatMenuDimmedWhenOpen(menuOpen: Boolean): Modifier =
    alpha(if (menuOpen) ChatMenuDimming.inactiveOpacity else 1f)
        .blur(
            radius = if (menuOpen) 9.dp else 0.dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )

fun Modifier.chatMenuBlurredWhenOpen(menuOpen: Boolean): Modifier =
    blur(
        radius = if (menuOpen) 9.dp else 0.dp,
        edgeTreatment = BlurredEdgeTreatment.Unbounded,
    )

data class ChatMessageMenuCallbacks(
    val onDeleteForEveryone: (EnhancedMessage) -> Unit = {},
    val onDeleteForMe: (EnhancedMessage) -> Unit = {},
    val onEdit: (EnhancedMessage) -> Unit = {},
    val onReply: (EnhancedMessage) -> Unit = {},
    val onCopy: (EnhancedMessage) -> Unit = {},
    val onForward: (EnhancedMessage) -> Unit = {},
    val onToggleStar: (EnhancedMessage) -> Unit = {},
    val onReaction: (EnhancedMessage, String) -> Unit = { _, _ -> },
    val onMoreReactions: (EnhancedMessage) -> Unit = {},
    val onLiftOffsetChanged: (String, Float) -> Unit = { _, _ -> },
)

private data class ChatMessageMenuLayout(
    val messageOffsetY: Float,
    val reactionsCenter: Offset,
    val menuCenter: Offset,
    val reactionsAreAbove: Boolean,
)

/** Publica color outgoing; sin medición de layout (≡ iOS `ChatMessageRowChrome`). */
@Composable
fun ChatMessageRowChrome(
    @Suppress("UNUSED_PARAMETER") isOutgoing: Boolean,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalChatOutgoingBubbleColor provides Color(0xFF3F6F8F)) {
        content()
    }
}

@Composable
fun ChatMessageBubbleChrome(
    isMenuSelected: Boolean,
    isOutgoing: Boolean,
    cornerRadius: Float = 16f,
    isFlashing: Boolean = false,
    onLongPress: ((ChatMessageLiftSnapshot) -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    var isPressing by remember { mutableStateOf(false) }
    var bubbleFrame by remember { mutableStateOf(Rect.Zero) }
    val lifted = isMenuSelected || isFlashing
    val selectionScale = when {
        lifted -> ChatBubbleAnchorMetrics.highlightScale
        isPressing -> ChatBubbleAnchorMetrics.pressScale
        else -> 1f
    }
    // iOS: spring.press para menú/flash; easeOut 0.12 para press.
    val animatedScale by animateFloatAsState(
        targetValue = selectionScale,
        animationSpec = if (isPressing && !lifted) {
            tween(durationMillis = 120)
        } else {
            spring(
                dampingRatio = MotionPolicy.Spring.PRESS_DAMPING.toFloat(),
                // response 0.28 → (2π/r)² ≈ 500
                stiffness = 500f,
            )
        },
        label = "bubbleChromeScale",
    )
    val highlightTint = (if (dark) Color.White else Color.Black).copy(alpha = 0.12f)
    Box(
        Modifier
            .zIndex(if (lifted) 1f else 0f)
            .onGloballyPositioned { bubbleFrame = it.boundsInWindow() }
            .then(if (onLongPress != null) {
                Modifier.chatMessageLongPress(
                    onPressingChanged = { isPressing = it },
                    onLongPress = {
                        isPressing = false
                        onLongPress(
                            ChatMessageLiftSnapshot(
                                frame = bubbleFrame,
                                cornerRadius = cornerRadius,
                                image = null,
                            ),
                        )
                    },
                )
            } else Modifier)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
                clip = false
                transformOrigin = TransformOrigin(
                    pivotFractionX = if (isOutgoing) 1f else 0f,
                    pivotFractionY = 1f,
                )
            },
    ) {
        content()
        if (isFlashing) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(highlightTint),
            )
        }
    }
}

/** Port del overlay: layout anclado + acciones vía [ChatMessagePolicy] + reacciones ordenadas. */
@Composable
fun ChatMessageContextMenuOverlay(
    selection: ChatMessageMenuSelection?,
    currentUserId: String,
    forwardingPreferences: Map<String, Boolean> = emptyMap(),
    starredMessageIds: Set<String> = emptySet(),
    callbacks: ChatMessageMenuCallbacks = ChatMessageMenuCallbacks(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selection == null) return
    val item = selection
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val emojiTracker = remember { EmojiUsageTracker() }
    val primaryText = com.moments.android.extensions.MomentsChromeGlass.contentColor(dark)
    val shadowAlpha = if (dark) 0.24f else 0.12f
    val menuCorner = ChatAttachmentSheetMetrics.cornerRadius
    val isCurrentUser = item.message.senderId == currentUserId
    val showsMessageInfo = shouldShowMessageInfo(item.message, isCurrentUser)
    val isStarred = item.message.id in starredMessageIds || item.message.isStarred(currentUserId)
    val rowCount = visibleMenuRowsCount(item.message, isCurrentUser, currentUserId, forwardingPreferences)
    val systemBars = WindowInsets.systemBars
    var overlayOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    var reactionsSizePx by remember { mutableStateOf(Offset(300f, 54f)) }
    var menuSizePx by remember(item.rowId, showsMessageInfo) {
        val infoHeight = if (showsMessageInfo) 37f else 0f
        mutableStateOf(Offset(240f, (rowCount * 36f + 16f + infoHeight).coerceAtLeast(36f)))
    }
    var presented by remember(item.rowId) { mutableStateOf(false) }
    var reactionsExpanded by remember(item.rowId) { mutableStateOf(false) }
    val presentationProgress by animateFloatAsState(
        targetValue = if (presented) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = 420f),
        label = "messageContextPresentation",
    )

    LaunchedEffect(item.rowId) { presented = true }

    fun dismissThen(action: () -> Unit = {}) {
        presented = false
        scope.launch {
            if (!MotionPolicy.reduceMotion) delay(170L)
            onDismiss()
            action()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                overlayOriginInWindow = Offset(pos.x, pos.y)
            },
    ) {
        val containerW = constraints.maxWidth.toFloat()
        val containerH = constraints.maxHeight.toFloat()
        // ≡ iOS `localAnchorFrame` (global − containerFrameInGlobal)
        val localSelection = remember(item, overlayOriginInWindow) {
            val a = item.anchorFrame
            if (a.width <= 1f || a.height <= 1f) {
                item
            } else {
                item.copy(
                    anchorFrame = Rect(
                        left = a.left - overlayOriginInWindow.x,
                        top = a.top - overlayOriginInWindow.y,
                        right = a.right - overlayOriginInWindow.x,
                        bottom = a.bottom - overlayOriginInWindow.y,
                    ),
                )
            }
        }
        val topMarginPx = with(density) {
            systemBars.getTop(this).toFloat() + 12.dp.toPx()
        }
        val bottomMarginPx = with(density) {
            systemBars.getBottom(this).toFloat() + 12.dp.toPx()
        }
        // iOS usa puntos ≈ dp; clamp/offsets deben ir en px de densidad.
        val metrics = remember(density, showsMessageInfo) {
            with(density) {
                MenuLayoutMetrics(
                    menuRowHeight = 36.dp.toPx(),
                    menuVerticalPadding = (
                        16.dp + if (showsMessageInfo) {
                            37.dp
                        } else {
                            0.dp
                        }
                    ).toPx(),
                    stackGap = 10.dp.toPx(),
                    reactionsBarHeight = 54.dp.toPx(),
                    expandedReactionsHeight = 232.dp.toPx(),
                    horizontalInset = 16.dp.toPx(),
                    reactionsBarEstimatedWidth = 300.dp.toPx(),
                    menuEstimatedWidth = 218.dp.toPx(),
                )
            }
        }
        val layout = remember(
            localSelection.rowId,
            localSelection.anchorFrame,
            rowCount,
            containerW,
            containerH,
            topMarginPx,
            bottomMarginPx,
            metrics,
            reactionsExpanded,
        ) {
            menuLayout(
                selection = localSelection,
                rowCount = rowCount,
                containerWidth = containerW,
                containerHeight = containerH,
                topMarginPx = topMarginPx,
                bottomMarginPx = bottomMarginPx,
                metrics = metrics,
                reactionsExpanded = reactionsExpanded,
            )
        }
        LaunchedEffect(item.rowId, layout.messageOffsetY) {
            callbacks.onLiftOffsetChanged(item.rowId, layout.messageOffsetY)
        }

        // La atenuación se pinta debajo del contenido del chat. Aquí no hay
        // snapshot, máscara ni recorte: la única forma visible es nuestra bubble viva.
        Box(
            Modifier
                .fillMaxSize()
                .clickable { dismissThen() },
        )

        val maxPanelWidth = (containerW - metrics.horizontalInset * 2f).coerceAtLeast(0f)
        ChatReactionRail(
            message = item.message,
            isOutgoing = item.isOutgoing,
            isAboveMessage = layout.reactionsAreAbove,
            expanded = reactionsExpanded,
            onExpandedChange = { reactionsExpanded = it },
            emojiTracker = emojiTracker,
            onReaction = { emoji -> dismissThen { callbacks.onReaction(item.message, emoji) } },
            modifier = Modifier
                .widthIn(max = with(density) { maxPanelWidth.toDp() })
                .onGloballyPositioned { coords ->
                    reactionsSizePx = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                }
                .offset {
                    val maxX = (containerW - metrics.horizontalInset - reactionsSizePx.x)
                        .coerceAtLeast(metrics.horizontalInset)
                    val maxY = (containerH - bottomMarginPx - reactionsSizePx.y)
                        .coerceAtLeast(topMarginPx)
                    IntOffset(
                        (layout.reactionsCenter.x - reactionsSizePx.x / 2f)
                            .coerceIn(metrics.horizontalInset, maxX)
                            .roundToInt(),
                        (layout.reactionsCenter.y - reactionsSizePx.y / 2f)
                            .coerceIn(topMarginPx, maxY)
                            .roundToInt(),
                    )
                }
                .graphicsLayer {
                    val scale = 0.82f + 0.18f * presentationProgress
                    scaleX = scale
                    scaleY = scale
                    alpha = presentationProgress
                    transformOrigin = TransformOrigin(0.5f, if (layout.reactionsAreAbove) 1f else 0f)
                },
        )

        // Un solo popup a la vez: con el catálogo de reacciones abierto, las
        // acciones no reciben hits (no basta con alpha = 0).
        if (!item.message.isDeleted && rowCount > 0 && !reactionsExpanded) {
            Column(
                Modifier
                    .width(minOf(218.dp, with(density) { maxPanelWidth.toDp() }))
                    .onGloballyPositioned { coords ->
                        menuSizePx = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                    }
                    .offset {
                        val maxX = (containerW - metrics.horizontalInset - menuSizePx.x)
                            .coerceAtLeast(metrics.horizontalInset)
                        val maxY = (containerH - bottomMarginPx - menuSizePx.y)
                            .coerceAtLeast(topMarginPx)
                        IntOffset(
                            (layout.menuCenter.x - menuSizePx.x / 2f)
                                .coerceIn(metrics.horizontalInset, maxX)
                                .roundToInt(),
                            (layout.menuCenter.y - menuSizePx.y / 2f)
                                .coerceIn(topMarginPx, maxY)
                                .roundToInt(),
                        )
                    }
                    .shadow(24.dp, RoundedCornerShape(menuCorner), ambientColor = Color.Black.copy(shadowAlpha), spotColor = Color.Black.copy(shadowAlpha))
                    .clip(RoundedCornerShape(menuCorner))
                    .momentsChromeGlass(RoundedCornerShape(menuCorner), interactive = true)
                    .graphicsLayer {
                        alpha = presentationProgress
                        scaleX = 0.92f + 0.08f * presentationProgress
                        scaleY = 0.92f + 0.08f * presentationProgress
                    }
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                if (showsMessageInfo) {
                    MessageInfoRow(item.message, primaryText, context)
                }
                MenuRow(R.string.chat_action_reply, Icons.AutoMirrored.Filled.Reply, primaryText) {
                    dismissThen { callbacks.onReply(item.message) }
                }
                if (ChatMessagePolicy.canForward(item.message, currentUserId, forwardingPreferences)) {
                    MenuRow(R.string.chat_action_forward, Icons.Default.Forward, primaryText) {
                        dismissThen { callbacks.onForward(item.message) }
                    }
                }
                if (!ChatMessagePolicy.isVanishRestricted(item.message)) {
                    MenuRow(
                        if (isStarred) R.string.chat_action_unstar else R.string.chat_action_star,
                        // ≡ iOS star.slash / star
                        if (isStarred) Icons.Default.StarBorder else Icons.Default.Star,
                        primaryText,
                    ) {
                        dismissThen { callbacks.onToggleStar(item.message) }
                    }
                }
                if (ChatMessagePolicy.canEdit(item.message, currentUserId)) {
                    MenuRow(R.string.chat_action_edit, Icons.Default.Edit, primaryText) {
                        dismissThen { callbacks.onEdit(item.message) }
                    }
                }
                if (ChatMessagePolicy.canCopy(item.message, currentUserId, forwardingPreferences)) {
                    MenuRow(R.string.chat_action_copy, Icons.Default.ContentCopy, primaryText) {
                        dismissThen { callbacks.onCopy(item.message) }
                    }
                }
                MenuRow(R.string.chat_action_delete_for_me, Icons.Default.Delete, primaryText, destructive = true) {
                    dismissThen { callbacks.onDeleteForMe(item.message) }
                }
                if (isCurrentUser && !item.message.isRead && isWithinDeleteLimit(item.message.timestamp)) {
                    MenuRow(R.string.chat_action_delete_for_everyone, Icons.Default.Delete, primaryText, destructive = true) {
                        dismissThen { callbacks.onDeleteForEveryone(item.message) }
                    }
                }
            }
        }
    }
}

private data class InlineSkinToneSelection(val baseEmoji: String, val anchorKey: String)

@Composable
private fun ChatReactionRail(
    message: EnhancedMessage,
    isOutgoing: Boolean,
    isAboveMessage: Boolean,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    emojiTracker: EmojiUsageTracker,
    onReaction: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val primaryText = com.moments.android.extensions.MomentsChromeGlass.contentColor(dark)
    val revision by emojiTracker.revision.collectAsState()
    val quick = remember(revision) { emojiTracker.orderedEmojis(EmojiReactionDefaults.chat) }
    val allEmojis = remember(revision) {
        (emojiTracker.recentlyUsed(12) + EmojiReactionDefaults.chat + emojiPickerCatalog()).distinct()
    }
    val frames = remember { mutableStateMapOf<String, Rect>() }
    var railOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    var toneSelection by remember { mutableStateOf<InlineSkinToneSelection?>(null) }
    val panelShape = RoundedCornerShape(23.dp)

    fun select(emoji: String) {
        HapticManager.shared.mediumImpact()
        emojiTracker.increment(emoji)
        toneSelection = null
        onReaction(emoji)
    }

    BoxWithConstraints(
        modifier.onGloballyPositioned { railOriginInWindow = it.positionInWindow() },
    ) {
        val panelWidth = if (expanded) minOf(maxWidth, 350.dp) else androidx.compose.ui.unit.Dp.Unspecified
        Column(
            Modifier
                .then(if (expanded) Modifier.width(panelWidth) else Modifier)
                .shadow(
                    24.dp,
                    panelShape,
                    ambientColor = Color.Black.copy(if (dark) 0.24f else 0.12f),
                    spotColor = Color.Black.copy(if (dark) 0.24f else 0.12f),
                )
                .clip(panelShape)
                .momentsChromeGlass(panelShape, interactive = true),
        ) {
            Row(
                Modifier.height(46.dp).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                quick.forEach { emoji ->
                    InlineReactionEmoji(
                        emoji = emoji,
                        anchorKey = "quick:$emoji",
                        fontSize = 28,
                        onFrame = { key, frame -> frames[key] = frame },
                        onTap = { select(emoji) },
                        onLongPress = {
                            val base = emojiWithoutSkinTone(emoji)
                            if (emojiSupportsSkinTone(base)) {
                                HapticManager.shared.mediumImpact()
                                onExpandedChange(true)
                                toneSelection = InlineSkinToneSelection(base, "quick:$emoji")
                            } else {
                                select(emoji)
                            }
                        },
                    )
                }
                Box(
                    Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .border(1.dp, primaryText.copy(if (dark) 0.12f else 0.08f), CircleShape)
                        .clickable {
                            HapticManager.shared.lightImpact()
                            toneSelection = null
                            onExpandedChange(!expanded)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.ExpandMore,
                        stringResource(R.string.chat_action_more_reactions),
                        tint = primaryText,
                        modifier = Modifier.size(20.dp).rotate(if (expanded) 180f else 0f),
                    )
                }
            }

            AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
                Column {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(primaryText.copy(alpha = 0.1f)))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(7),
                        modifier = Modifier.height(185.dp).padding(10.dp),
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        items(allEmojis, key = { it }) { emoji ->
                            InlineReactionEmoji(
                                emoji = emoji,
                                anchorKey = "grid:$emoji",
                                fontSize = 27,
                                onFrame = { key, frame -> frames[key] = frame },
                                onTap = { select(emoji) },
                                onLongPress = {
                                    val base = emojiWithoutSkinTone(emoji)
                                    if (emojiSupportsSkinTone(base)) {
                                        HapticManager.shared.mediumImpact()
                                        toneSelection = InlineSkinToneSelection(base, "grid:$emoji")
                                    } else {
                                        select(emoji)
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }

        ChatReactionRailTail(
            isOutgoing = isOutgoing,
            pointsDown = isAboveMessage,
            modifier = Modifier.align(
                when {
                    isAboveMessage && isOutgoing -> Alignment.BottomEnd
                    isAboveMessage -> Alignment.BottomStart
                    isOutgoing -> Alignment.TopEnd
                    else -> Alignment.TopStart
                },
            ).offset(
                x = if (isOutgoing) (-23).dp else 23.dp,
                y = if (isAboveMessage) 20.dp else (-20).dp,
            ),
        )

        val selectedTone = toneSelection
        val windowAnchor = selectedTone?.let { frames[it.anchorKey] }
        if (selectedTone != null && windowAnchor != null) {
            val anchor = Rect(
                windowAnchor.left - railOriginInWindow.x,
                windowAnchor.top - railOriginInWindow.y,
                windowAnchor.right - railOriginInWindow.x,
                windowAnchor.bottom - railOriginInWindow.y,
            )
            val bubbleWidth = 264.dp
            val bubbleHeight = 54.dp
            val widthPx = with(density) { bubbleWidth.toPx() }
            val heightPx = with(density) { bubbleHeight.toPx() }
            val panelWidthPx = constraints.maxWidth.toFloat()
            val panelHeightPx = constraints.maxHeight.toFloat()
            val edgeInsetPx = with(density) { 4.dp.toPx() }
            val centerX = anchor.center.x.coerceIn(
                widthPx / 2f + edgeInsetPx,
                maxOf(widthPx / 2f + edgeInsetPx, panelWidthPx - widthPx / 2f - edgeInsetPx),
            )
            val below = anchor.bottom + with(density) { 8.dp.toPx() } + heightPx / 2f
            val above = anchor.top - with(density) { 8.dp.toPx() } - heightPx / 2f
            val preferred = if (isAboveMessage) below else above
            val preferredFits = preferred - heightPx / 2f >= 0f && preferred + heightPx / 2f <= panelHeightPx
            val centerY = (if (preferredFits) preferred else if (isAboveMessage) above else below)
                .coerceIn(heightPx / 2f, maxOf(heightPx / 2f, panelHeightPx - heightPx / 2f))

            Row(
                Modifier
                    .offset {
                        IntOffset(
                            (centerX - widthPx / 2f).roundToInt(),
                            (centerY - heightPx / 2f).roundToInt(),
                        )
                    }
                    .width(bubbleWidth)
                    .height(bubbleHeight)
                    .zIndex(20f)
                    .shadow(18.dp, RoundedCornerShape(19.dp))
                    .clip(RoundedCornerShape(19.dp))
                    .momentsChromeGlass(RoundedCornerShape(19.dp), interactive = true)
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                listOf("", "🏻", "🏼", "🏽", "🏾", "🏿").forEach { suffix ->
                    val variant = selectedTone.baseEmoji + suffix
                    Text(
                        variant,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .size(40.dp)
                            .clickable { select(variant) },
                    )
                }
            }
        }
    }
}

@Composable
private fun InlineReactionEmoji(
    emoji: String,
    anchorKey: String,
    fontSize: Int,
    onFrame: (String, Rect) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    Text(
        emoji,
        fontSize = fontSize.sp,
        modifier = Modifier
            .size(34.dp)
            .onGloballyPositioned { onFrame(anchorKey, it.boundsInWindow()) }
            .pointerInput(emoji, anchorKey) {
                detectTapGestures(onTap = { onTap() }, onLongPress = { onLongPress() })
            },
    )
}

@Composable
private fun ChatReactionRailTail(
    isOutgoing: Boolean,
    pointsDown: Boolean,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    Canvas(modifier.size(24.dp, 28.dp)) {
        val largeRadius = 8.dp.toPx()
        val smallRadius = 4.dp.toPx()
        val largeCenter = Offset(12.dp.toPx(), if (pointsDown) 8.dp.toPx() else 20.dp.toPx())
        val bendsRight = !isOutgoing
        val smallCenter = Offset(if (bendsRight) 21.dp.toPx() else 3.dp.toPx(), if (pointsDown) 22.dp.toPx() else 6.dp.toPx())
        val color = if (dark) Color(0xE643464D) else Color(0xEDF4F4F4)
        drawCircle(color, largeRadius, largeCenter)
        drawCircle(color, smallRadius, smallCenter)
    }
}

@Composable
private fun MessageInfoRow(
    message: EnhancedMessage,
    primaryText: Color,
    context: android.content.Context,
) {
    val receiptTime = readReceiptTime(message)
    Row(
        Modifier.fillMaxWidth().height(36.dp).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MessageStatusIcon(MessageStatus.READ)
        if (receiptTime != null) {
            Text(
                "${com.moments.android.views.messaging.core.MessageStatus.READ.displayName(context)} ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(receiptTime)}",
                color = primaryText,
                fontSize = 13.sp,
            )
        }
        Spacer(Modifier.weight(1f))
        if (message.editedAt != null) {
            Text(stringResource(R.string.chat_edited), color = primaryText.copy(alpha = 0.52f), fontSize = 10.sp)
        }
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(primaryText.copy(alpha = 0.08f)))
}

private fun readReceiptTime(message: EnhancedMessage): Date? = message.readAtBy
    ?.filterKeys { it != message.senderId }
    ?.values
    ?.maxOrNull()

private fun shouldShowMessageInfo(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
): Boolean {
    if (!isCurrentUser) return false
    return readReceiptTime(message) != null
}

@Composable
fun GlassActionButton(
    title: String,
    icon: ImageVector,
    @Suppress("UNUSED_PARAMETER") adaptiveColors: AdaptiveColors,
    isDestructive: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val color = if (isDestructive) Color.Red else com.moments.android.extensions.MomentsChromeGlass.contentColor(dark)
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.width(24.dp).size(18.dp))
        Text(title, color = color, fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun MenuRow(
    title: Int,
    icon: ImageVector,
    primaryTextColor: Color,
    destructive: Boolean = false,
    action: () -> Unit,
) {
    val color = if (destructive) Color.Red else primaryTextColor
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clickable {
                // ≡ iOS `MomentRowButton(feedback: .menu)`
                HapticManager.shared.selection()
                action()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            stringResource(title),
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
        Spacer(Modifier.weight(1f))
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
    }
}

private fun isWithinDeleteLimit(timestamp: Date): Boolean =
    Date().time - timestamp.time < 7_200_000L

private fun visibleMenuRowsCount(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    currentUserId: String,
    forwardingPreferences: Map<String, Boolean>,
): Int {
    if (message.isDeleted) return 0
    var count = 2 // Reply + DeleteForMe
    if (!ChatMessagePolicy.isVanishRestricted(message)) count += 1
    if (ChatMessagePolicy.canForward(message, currentUserId, forwardingPreferences)) count += 1
    if (ChatMessagePolicy.canEdit(message, currentUserId)) count += 1
    if (ChatMessagePolicy.canCopy(message, currentUserId, forwardingPreferences)) count += 1
    if (isCurrentUser && !message.isRead && isWithinDeleteLimit(message.timestamp)) count += 1
    return count
}

private data class MenuLayoutMetrics(
    val menuRowHeight: Float,
    val menuVerticalPadding: Float,
    val stackGap: Float,
    val reactionsBarHeight: Float,
    val expandedReactionsHeight: Float,
    val horizontalInset: Float,
    val reactionsBarEstimatedWidth: Float,
    val menuEstimatedWidth: Float,
)

private fun menuLayout(
    selection: ChatMessageMenuSelection,
    rowCount: Int,
    containerWidth: Float,
    containerHeight: Float,
    topMarginPx: Float,
    bottomMarginPx: Float,
    metrics: MenuLayoutMetrics,
    reactionsExpanded: Boolean,
): ChatMessageMenuLayout {
    val scale = ChatBubbleAnchorMetrics.menuSelectionScale
    val anchor = selection.anchorFrame
    val scaled = if (anchor.width <= 1f || anchor.height <= 1f) {
        // Fallback centrado si aún no hay frame medido
        Rect(
            left = containerWidth / 2f - 100f,
            top = containerHeight / 2f - 80f,
            right = containerWidth / 2f + 100f,
            bottom = containerHeight / 2f + 80f,
        )
    } else {
        val wDiff = anchor.width * (scale - 1f)
        val hDiff = anchor.height * (scale - 1f)
        Rect(
            left = anchor.left - wDiff / 2f,
            top = anchor.top - hDiff / 2f,
            right = anchor.right + wDiff / 2f,
            bottom = anchor.bottom + hDiff / 2f,
        )
    }

    val menuHeight = rowCount * metrics.menuRowHeight + metrics.menuVerticalPadding
    val stackGap = metrics.stackGap
    val reactionsBarHeight = if (reactionsExpanded) metrics.expandedReactionsHeight else metrics.reactionsBarHeight
    val horizontalInset = metrics.horizontalInset
    val reactionsBarEstimatedWidth = if (reactionsExpanded) {
        min(containerWidth - metrics.horizontalInset * 2f, metrics.reactionsBarEstimatedWidth + 50f)
    } else {
        metrics.reactionsBarEstimatedWidth
    }
    val menuEstimatedWidth = metrics.menuEstimatedWidth

    fun clampCenterX(centerX: Float, itemWidth: Float): Float {
        val half = itemWidth / 2f
        val minX = horizontalInset + half
        val maxX = containerWidth - horizontalInset - half
        if (maxX < minX) return containerWidth / 2f
        return centerX.coerceIn(minX, maxX)
    }

    val centerX = clampCenterX(scaled.center.x, max(reactionsBarEstimatedWidth, menuEstimatedWidth))
    // Encaja reacciones + mensaje + acciones en el viewport desplazando la fila
    // viva (no una copia de la burbuja).
    val minimumMessageTop = topMarginPx + reactionsBarHeight + stackGap
    val maximumMessageTop = containerHeight - bottomMarginPx - menuHeight - stackGap - scaled.height
    val targetMessageTop = if (maximumMessageTop >= minimumMessageTop) {
        scaled.top.coerceIn(minimumMessageTop, maximumMessageTop)
    } else {
        minimumMessageTop
    }
    val messageOffsetY = targetMessageTop - scaled.top
    val shiftedTop = scaled.top + messageOffsetY
    val shiftedBottom = scaled.bottom + messageOffsetY
    val reactionsCenterY = shiftedTop - stackGap - reactionsBarHeight / 2f
    val menuCenterY = shiftedBottom + stackGap + menuHeight / 2f

    return ChatMessageMenuLayout(
        messageOffsetY = messageOffsetY,
        reactionsCenter = Offset(centerX, reactionsCenterY),
        menuCenter = Offset(clampCenterX(scaled.center.x, menuEstimatedWidth), menuCenterY),
        reactionsAreAbove = true,
    )
}
