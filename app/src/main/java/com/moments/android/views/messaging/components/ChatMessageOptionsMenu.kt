package com.moments.android.views.messaging.components

import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.PopupWindow
import androidx.compose.runtime.rememberCompositionContext
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.findViewTreeSavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.EmojiReactionDefaults
import com.moments.android.utilities.EmojiUsageTracker
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.creator.emojiPickerCatalog
import com.moments.android.views.creator.emojiSupportsSkinTone
import com.moments.android.views.creator.emojiWithoutSkinTone
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.core.ChatMessagePolicy
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import java.util.Date
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
    const val menuSelectionScale = 1f
    const val highlightScale = 1.03f
    const val highlightDurationMillis = 1500L
    const val pressScale = 0.97f
    const val clusterCornerRadius = 16f

    fun cornerRadiusFor(message: EnhancedMessage): Float = when (message.type) {
        MessageType.TEXT -> 20f
        MessageType.AUDIO -> 18f
        MessageType.IMAGE, MessageType.VIDEO,
        MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO,
        MessageType.LOCATION, MessageType.EPHEMERAL,
        MessageType.SHARED_MOMENT, MessageType.SHARED_STORY, MessageType.SHARED_PROFILE,
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
            radius = if (menuOpen && !isSelected) 5.dp else 0.dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )

fun Modifier.chatMenuDimmedWhenOpen(menuOpen: Boolean): Modifier =
    alpha(if (menuOpen) ChatMenuDimming.inactiveOpacity else 1f)
        .blur(
            radius = if (menuOpen) 5.dp else 0.dp,
            edgeTreatment = BlurredEdgeTreatment.Unbounded,
        )

fun Modifier.chatMenuBlurredWhenOpen(menuOpen: Boolean): Modifier =
    blur(
        radius = if (menuOpen) 5.dp else 0.dp,
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
    val onOpenMessage: (EnhancedMessage, List<EnhancedMessage>?) -> Unit = { _, _ -> },
)

internal data class ChatMessageMenuLayout(
    val messageOffsetY: Float,
    val reactionsCenter: Offset,
    val menuCenter: Offset,
    val reactionsAreAbove: Boolean,
    val actionsMaxHeight: Float,
)

private fun liftedMessageHitRect(
    anchor: Rect,
    offsetY: Float,
    scale: Float,
    isOutgoing: Boolean,
): Rect {
    val pivotX = if (isOutgoing) anchor.right else anchor.left
    val pivotY = anchor.bottom
    return Rect(
        left = pivotX + (anchor.left - pivotX) * scale,
        top = pivotY + (anchor.top - pivotY) * scale + offsetY,
        right = pivotX + (anchor.right - pivotX) * scale,
        bottom = pivotY + (anchor.bottom - pivotY) * scale + offsetY,
    )
}

/** Publica color outgoing; sin medición de layout (≡ iOS `ChatMessageRowChrome`). */
@Composable
fun ChatMessageRowChrome(
    @Suppress("UNUSED_PARAMETER") isOutgoing: Boolean,
    content: @Composable () -> Unit,
) {
    content()
}

/** Resaltado del salto a mensaje citado; lo pinta `MessageReactionOverlayBox` sobre la burbuja. */
data class ChatBubbleFlashHighlight(val shape: Shape, val tint: Color)

val LocalChatBubbleFlashHighlight = compositionLocalOf<ChatBubbleFlashHighlight?> { null }

@Composable
fun ChatMessageBubbleChrome(
    isMenuSelected: Boolean,
    isOutgoing: Boolean,
    cornerRadius: Float = 16f,
    isFlashing: Boolean = false,
    /** Forma real de la burbuja: el flash se pinta dentro de ella y no sobre el hueco de reacciones. */
    flashShape: Shape? = null,
    onTap: (() -> Unit)? = null,
    onLongPress: ((ChatMessageLiftSnapshot) -> Unit)? = null,
    childHandlesTap: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    var isPressing by remember { mutableStateOf(false) }
    var bubbleFrame by remember { mutableStateOf(Rect.Zero) }
    val lifted = isMenuSelected || isFlashing
    val selectionScale = when {
        isMenuSelected -> ChatBubbleAnchorMetrics.menuSelectionScale
        isFlashing -> ChatBubbleAnchorMetrics.highlightScale
        isPressing -> ChatBubbleAnchorMetrics.pressScale
        else -> 1f
    }
    // iOS: spring.press para menú/flash; easeOut 0.12 para press.
    val animatedScale by animateFloatAsState(
        targetValue = selectionScale,
        animationSpec = when {
            isPressing && !lifted -> tween(durationMillis = 120)
            isMenuSelected -> spring(dampingRatio = 0.84f, stiffness = 224f)
            else -> spring(
                dampingRatio = MotionPolicy.Spring.PRESS_DAMPING.toFloat(),
                stiffness = 500f,
            )
        },
        label = "bubbleChromeScale",
    )
    val highlightTint = (if (dark) Color.White else Color.Black).copy(alpha = 0.12f)
    val longPressHandler = onLongPress
    Box(
        Modifier
            .zIndex(if (lifted) 1f else 0f)
            .onGloballyPositioned { bubbleFrame = it.boundsInWindow() }
            .then(
                if (longPressHandler != null || onTap != null) {
                    Modifier.chatMessagePressClassifier(
                        onPressingChanged = { isPressing = it },
                        onTap = onTap,
                        childHandlesTap = childHandlesTap,
                        onLongPress = {
                            if (longPressHandler == null) return@chatMessagePressClassifier
                            isPressing = false
                            longPressHandler(
                                ChatMessageLiftSnapshot(
                                    frame = bubbleFrame,
                                    cornerRadius = cornerRadius,
                                    image = null,
                                ),
                            )
                        },
                    )
                } else {
                    Modifier
                },
            )
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
        val highlight = if (isFlashing && flashShape != null) ChatBubbleFlashHighlight(flashShape, highlightTint) else null
        CompositionLocalProvider(
            LocalChatMenuBadgesHidden provides isMenuSelected,
            LocalChatBubbleFlashHighlight provides highlight,
        ) {
            content()
        }
        // Sin forma (ráfagas): resaltado del contenedor completo, como antes.
        if (isFlashing && flashShape == null) {
            Box(
                Modifier
                    .matchParentSize()
                    .clip(RoundedCornerShape(cornerRadius.dp))
                    .background(highlightTint),
            )
        }
    }
}

/** Ventana nativa para presentar el menú sobre el IME. */
@Composable
private fun ChatMessageMenuPopup(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val parent = LocalView.current
    val compositionContext = rememberCompositionContext()
    val latestContent by rememberUpdatedState(content)
    val latestDismiss by rememberUpdatedState(onDismiss)
    DisposableEffect(parent, compositionContext) {
        val host = ComposeView(parent.context).apply {
            setParentCompositionContext(compositionContext)
            parent.findViewTreeLifecycleOwner()?.let { setViewTreeLifecycleOwner(it) }
            parent.findViewTreeSavedStateRegistryOwner()?.let { setViewTreeSavedStateRegistryOwner(it) }
            setContent { latestContent() }
            isFocusableInTouchMode = true
            setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) latestDismiss()
                    true
                } else false
            }
        }
        val popup = PopupWindow(host, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, true).apply {
            setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            inputMethodMode = PopupWindow.INPUT_METHOD_NOT_NEEDED
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_NOTHING
            isClippingEnabled = false
            setIsLaidOutInScreen(true)
            isAttachedInDecor = false
            animationStyle = 0
        }
        popup.setOnDismissListener { latestDismiss() }
        popup.showAtLocation(parent, Gravity.TOP or Gravity.START, 0, 0)
        host.requestFocus()
        onDispose {
            popup.setOnDismissListener(null)
            popup.dismiss()
            host.disposeComposition()
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
    isGroup: Boolean = false,
    callbacks: ChatMessageMenuCallbacks = ChatMessageMenuCallbacks(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selection == null) return
    val item = selection
    val dark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val emojiTracker = remember { EmojiUsageTracker() }
    val primaryText = com.moments.android.extensions.MomentsChromeGlass.contentColor(dark)
    val shadowAlpha = if (dark) 0.24f else 0.12f
    val menuCorner = ChatAttachmentSheetMetrics.cornerRadius
    val isCurrentUser = item.message.senderId == currentUserId
    val showsMessageInfo = shouldShowMessageInfo(item.message, isCurrentUser)
    val showsGroupReaders = shouldShowGroupReaders(item.message, isCurrentUser, isGroup)
    val extraHeaderHeightDp = extraMenuHeaderHeight(showsMessageInfo, showsGroupReaders)
    val isStarred = item.message.id in starredMessageIds || item.message.isStarred(currentUserId)
    val rowCount = visibleMenuRowsCount(item.message, isCurrentUser, currentUserId, forwardingPreferences)
    val systemBars = WindowInsets.systemBars
    val sourceView = LocalView.current
    val rootView = sourceView.rootView
    // La burbuja se mide en la ventana de la actividad; el popup tiene otra.
    val sourceWindowOrigin = remember(item.rowId, sourceView) {
        val screen = IntArray(2)
        val window = IntArray(2)
        sourceView.getLocationOnScreen(screen)
        sourceView.getLocationInWindow(window)
        Offset((screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat())
    }
    var overlayOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    var reactionsSizePx by remember { mutableStateOf(with(density) { Offset(minOf(320.dp.toPx(), rootView.width.toFloat() - 32.dp.toPx()), 64.dp.toPx()) }) }
    var menuSizePx by remember(item.rowId, showsMessageInfo, showsGroupReaders) {
        val infoHeight = extraHeaderHeightDp.value
        mutableStateOf(Offset(240f, (rowCount * 36f + 16f + infoHeight).coerceAtLeast(36f)))
    }
    var presented by remember(item.rowId) { mutableStateOf(false) }
    var dismissing by remember(item.rowId) { mutableStateOf(false) }
    var reactionsExpanded by remember(item.rowId) { mutableStateOf(false) }
    var reactionDocking by remember(item.rowId) { mutableFloatStateOf(0f) }
    val presentationProgress by animateFloatAsState(
        targetValue = if (presented) 1f else 0f,
        animationSpec = if (presented) {
            spring(dampingRatio = 0.84f, stiffness = 224f)
        } else {
            tween(durationMillis = 260)
        },
        label = "messageContextPresentation",
    )

    val railProgress by animateFloatAsState(
        targetValue = if (presented) 1f else 0f,
        animationSpec = tween(if (MotionPolicy.reduceMotion) 0 else if (presented) 420 else 300, easing = LinearEasing),
        label = "reactionRailFormation",
    )

    LaunchedEffect(item.rowId) { presented = true }

    fun dismissThen(action: () -> Unit = {}) {
        if (dismissing) return
        dismissing = true
        presented = false
        scope.launch {
            if (!MotionPolicy.reduceMotion) delay(320L)
            onDismiss()
            action()
        }
    }

    BackHandler {
        if (reactionsExpanded) reactionsExpanded = false else dismissThen()
    }

    ChatMessageMenuPopup(onDismiss = { dismissThen() }) {
    val popupView = LocalView.current
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .onGloballyPositioned { coords ->
                val pos = coords.positionInWindow()
                val screen = IntArray(2)
                val window = IntArray(2)
                popupView.getLocationOnScreen(screen)
                popupView.getLocationInWindow(window)
                // Origen del overlay expresado en la ventana de la actividad.
                overlayOriginInWindow = pos + Offset(
                    (screen[0] - window[0]).toFloat(), (screen[1] - window[1]).toFloat(),
                ) - sourceWindowOrigin
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
            // El popup no se redimensiona por IME; todo el viewport es utilizable.
            systemBars.getBottom(this).toFloat() + 12.dp.toPx()
        }
        // iOS usa puntos ≈ dp; clamp/offsets deben ir en px de densidad.
        val metrics = remember(density, showsMessageInfo, showsGroupReaders) {
            with(density) {
                MenuLayoutMetrics(
                    menuRowHeight = 36.dp.toPx(),
                    menuVerticalPadding = (
                        16.dp + extraHeaderHeightDp
                    ).toPx(),
                    stackGap = 10.dp.toPx(),
                    reactionsBarHeight = 64.dp.toPx(),
                    expandedReactionsHeight = 250.dp.toPx(),
                    horizontalInset = 16.dp.toPx(),
                    reactionsBarEstimatedWidth = 320.dp.toPx(),
                    menuEstimatedWidth = 240.dp.toPx(),
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

        val presentedOffsetY = layout.messageOffsetY * presentationProgress
        val liftScale = 1f + (ChatBubbleAnchorMetrics.menuSelectionScale - 1f) * presentationProgress
        val liftedHitRect = liftedMessageHitRect(
            anchor = localSelection.anchorFrame,
            offsetY = presentedOffsetY,
            scale = liftScale,
            isOutgoing = localSelection.isOutgoing,
        )
        val latestHitRect = rememberUpdatedState(liftedHitRect)
        val latestOnOpen = rememberUpdatedState(callbacks.onOpenMessage)
        val openMessage = item.message
        val openCluster = item.clusterMessages

        // Dimmer: tap fuera de la burbuja cierra; tap en la burbuja elevada abre.
        Box(
            Modifier
                .fillMaxSize()
                .drawWithContent {
                    // Oscurecimiento uniforme, sin un recorte rectangular sobre el mensaje.
                    drawRect(Color.Black.copy(alpha = (if (dark) 0.18f else 0.10f) * presentationProgress))
                    drawContent()
                }
                .pointerInput(item.rowId) {
                    detectTapGestures { offset ->
                        if (latestHitRect.value.contains(offset)) {
                            dismissThen { latestOnOpen.value(openMessage, openCluster) }
                        } else {
                            dismissThen()
                        }
                    }
                },
        )

        val maxPanelWidth = (containerW - metrics.horizontalInset * 2f).coerceAtLeast(0f)
        val railEdgeInset = if (localSelection.anchorFrame.width * ChatBubbleAnchorMetrics.menuSelectionScale >= reactionsSizePx.x * 0.85f) with(density) { 4.dp.toPx() } else metrics.horizontalInset
        ChatReactionRail(
            progress = railProgress,
            onDockingChanged = { reactionDocking = it },
            isOutgoing = item.isOutgoing,
            isAboveMessage = layout.reactionsAreAbove,
            connectorAnchorX = reactionConnectorSourceX(
                anchor = liftedMessageHitRect(
                    anchor = localSelection.anchorFrame,
                    offsetY = layout.messageOffsetY,
                    scale = ChatBubbleAnchorMetrics.menuSelectionScale,
                    isOutgoing = localSelection.isOutgoing,
                ),
                railLeft = (layout.reactionsCenter.x - reactionsSizePx.x / 2f).coerceIn(
                    railEdgeInset,
                    (containerW - railEdgeInset - reactionsSizePx.x).coerceAtLeast(railEdgeInset),
                ),
                railWidth = reactionsSizePx.x,
                containerWidth = containerW,
                inset = with(density) { 32.dp.toPx() },
                isOutgoing = item.isOutgoing,
            ),
            expanded = reactionsExpanded,
            onExpandedChange = { reactionsExpanded = it },
            emojiTracker = emojiTracker,
            onReaction = { emoji -> dismissThen { callbacks.onReaction(item.message, emoji) } },
            modifier = Modifier
                .width(minOf(with(density) { maxPanelWidth.toDp() }, 320.dp))
                .onGloballyPositioned { coords ->
                    reactionsSizePx = Offset(coords.size.width.toFloat(), coords.size.height.toFloat())
                }
                .offset {
                    val maxX = (containerW - railEdgeInset - reactionsSizePx.x)
                        .coerceAtLeast(railEdgeInset)
                    val maxY = (containerH - bottomMarginPx - reactionsSizePx.y)
                        .coerceAtLeast(topMarginPx)
                    IntOffset(
                        (layout.reactionsCenter.x - reactionsSizePx.x / 2f)
                            .coerceIn(railEdgeInset, maxX)
                            .roundToInt(),
                        (layout.reactionsCenter.y - reactionsSizePx.y / 2f)
                            .coerceIn(topMarginPx, maxY)
                            .roundToInt(),
                    )
                },
        )

        // The connector protrudes beyond the rail; give it a root-level hit target.
        if (!reactionsExpanded && railProgress > 0.95f && reactionDocking < 0.9f) {
            val railLeft = (layout.reactionsCenter.x - reactionsSizePx.x / 2f).coerceIn(
                railEdgeInset,
                (containerW - railEdgeInset - reactionsSizePx.x).coerceAtLeast(railEdgeInset),
            )
            val railTop = (layout.reactionsCenter.y - reactionsSizePx.y / 2f).coerceIn(
                topMarginPx,
                (containerH - bottomMarginPx - reactionsSizePx.y).coerceAtLeast(topMarginPx),
            )
            val sourceX = reactionConnectorSourceX(
                anchor = liftedMessageHitRect(localSelection.anchorFrame, layout.messageOffsetY, ChatBubbleAnchorMetrics.menuSelectionScale, localSelection.isOutgoing),
                railLeft = railLeft, railWidth = reactionsSizePx.x,
                containerWidth = containerW,
                inset = with(density) { 32.dp.toPx() }, isOutgoing = item.isOutgoing,
            )
            Box(Modifier.offset {
                IntOffset(
                    (railLeft + sourceX - 26.dp.toPx()).roundToInt(),
                    (railTop + (if (layout.reactionsAreAbove) 78.dp.toPx() else -14.dp.toPx()) - 26.dp.toPx()).roundToInt(),
                )
            }.size(52.dp).zIndex(30f).clickable {
                HapticManager.shared.lightImpact()
                reactionsExpanded = true
            })
        }

        // Un solo popup a la vez: con el catálogo de reacciones abierto, las
        // acciones no reciben hits (no basta con alpha = 0).
        if (!item.message.isDeleted && rowCount > 0 && !reactionsExpanded) {
            Column(
                Modifier
                    .width(minOf(240.dp, with(density) { maxPanelWidth.toDp() }))
                    .heightIn(max = with(density) { layout.actionsMaxHeight.toDp() })
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
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .clipToBounds(),
            ) {
                if (showsMessageInfo) {
                    MessageInfoRow(item.message, primaryText)
                }
                if (showsGroupReaders) {
                    GroupReadReceiptsRow(item.message, primaryText)
                }
                if (showsMessageInfo || showsGroupReaders) {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(primaryText.copy(alpha = 0.08f)))
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
}

private data class InlineSkinToneSelection(val baseEmoji: String, val anchorKey: String)

@Composable
private fun ChatReactionRail(
    progress: Float,
    onDockingChanged: (Float) -> Unit,
    isOutgoing: Boolean,
    isAboveMessage: Boolean,
    connectorAnchorX: Float,
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
    val quick = remember(revision) {
        val candidates = (EmojiReactionDefaults.chat + emojiTracker.recentlyUsed(20) + emojiPickerCatalog().take(32)).distinct()
        emojiTracker.orderedEmojis(candidates, limit = 20)
    }
    val allEmojis = remember(revision) {
        (emojiTracker.recentlyUsed(12) + EmojiReactionDefaults.chat + emojiPickerCatalog()).distinct()
    }
    val quickScroll = rememberLazyListState()
    val dockingTarget by remember(quickScroll, quick.size, density) {
        derivedStateOf {
            val info = quickScroll.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()
            if (last == null) 0f else if (!quickScroll.canScrollForward) 1f else {
                val remainingItems = quick.size - last.index
                val remaining = last.offset + last.size + remainingItems * with(density) { 48.dp.toPx() } + info.afterContentPadding - info.viewportEndOffset
                (1f - remaining / with(density) { 96.dp.toPx() }).coerceIn(0f, 1f)
            }
        }
    }
    val docking by animateFloatAsState(dockingTarget, tween(if (MotionPolicy.reduceMotion) 0 else 160), label = "reactionSmileDocking")
    LaunchedEffect(docking) { onDockingChanged(docking) }
    val frames = remember { mutableStateMapOf<String, Rect>() }
    var railOriginInWindow by remember { mutableStateOf(Offset.Zero) }
    var toneSelection by remember { mutableStateOf<InlineSkinToneSelection?>(null) }
    val surface = com.moments.android.extensions.MomentsChromeGlass.canvasTint(dark)
    val visible = reactionPhase(progress, 0.30f, 0.82f)
    val formationShape = remember(progress, connectorAnchorX, expanded) {
        object : Shape {
            override fun createOutline(size: androidx.compose.ui.geometry.Size, layoutDirection: androidx.compose.ui.unit.LayoutDirection, density: androidx.compose.ui.unit.Density): Outline {
                val spread = reactionPhase(progress, 0.20f, 0.78f)
                val diameter = with(density) { 40.dp.toPx() } * reactionPhase(progress, 0f, 0.26f)
                val width = diameter + (size.width - diameter) * spread
                val height = diameter + (size.height - diameter) * spread
                val x = connectorAnchorX + (size.width / 2f - connectorAnchorX) * spread
                return Outline.Rounded(RoundRect(x - width / 2f, (size.height - height) / 2f, x + width / 2f, (size.height + height) / 2f, CornerRadius(if (expanded) minOf(height / 2f, with(density) { 32.dp.toPx() }) else height / 2f)))
            }
        }
    }

    fun select(emoji: String) {
        HapticManager.shared.mediumImpact()
        emojiTracker.increment(emoji)
        toneSelection = null
        onReaction(emoji)
    }

    BoxWithConstraints(
        modifier.onGloballyPositioned { railOriginInWindow = it.positionInWindow() },
    ) {
        Box(Modifier.fillMaxWidth().height(if (expanded) 250.dp else 64.dp)) {
            Box(Modifier.fillMaxSize().clip(formationShape).background(surface))
            Column(Modifier.fillMaxSize().graphicsLayer { shape = formationShape; clip = true; alpha = visible }) {
            LazyRow(
                Modifier.fillMaxWidth().height(64.dp),
                state = quickScroll,
                contentPadding = PaddingValues(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                rowItems(quick, key = { it }) { emoji ->
                    Box(Modifier.size(48.dp, 64.dp), contentAlignment = Alignment.Center) {
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
                }
                item(key = "reaction-catalog") {
                    Box(Modifier.size(48.dp, 64.dp).alpha(docking)
                        .clickable(enabled = docking > 0.9f) {
                            HapticManager.shared.lightImpact()
                            toneSelection = null
                            onExpandedChange(!expanded)
                        }, contentAlignment = Alignment.Center) {
                        Icon(painterResource(R.drawable.chat_reaction_smile_icon),
                            stringResource(R.string.chat_action_more_reactions),
                            tint = primaryText, modifier = Modifier.size(24.dp))
                    }
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

        }
        if (!expanded) {
            val tail = reactionPhase(progress, 0.50f, 0.89f) * (1f - docking)
            val medium = reactionPhase(progress, 0.58f, 0.94f)
            val small = reactionPhase(progress, 0.65f, 0.98f)
            val face = reactionPhase(progress, 0.60f, 1f) * (1f - docking)
            val direction = if (isAboveMessage) 1f else -1f
            val edge = if (isAboveMessage) 64f else 0f
            val faceY = edge + direction * (-10f + 24f * tail)
            val mediumY = faceY + direction * (14f + 10f * medium - 10f * docking)
            val smallY = mediumY + direction * (9f + 8f * small)
            val bend = if (isOutgoing) -1f else 1f
            Canvas(Modifier.fillMaxWidth().height(64.dp)) {
                val mediumX = connectorAnchorX + bend * (16f * medium * (1f - docking) - 8f * docking).dp.toPx()
                val smallX = connectorAnchorX + bend * ((16f * medium + 6f * small) * (1f - docking) - 2f * docking).dp.toPx()
                drawCircle(surface, 24.dp.toPx() * tail, Offset(connectorAnchorX, faceY.dp.toPx()))
                drawCircle(surface, 12.dp.toPx() * medium, Offset(mediumX, mediumY.dp.toPx()))
                drawCircle(surface, 5.dp.toPx() * small, Offset(smallX, smallY.dp.toPx()))
            }
            Box(
                Modifier.offset { IntOffset((connectorAnchorX - 24.dp.toPx()).roundToInt(), (faceY.dp.toPx() - 24.dp.toPx()).roundToInt()) }
                    .size(48.dp).clip(CircleShape)
                    ,
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.chat_reaction_smile_icon), stringResource(R.string.chat_action_more_reactions),
                    tint = primaryText, modifier = Modifier.size(26.dp).alpha(face))
            }
        }

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

/** Keep the complete smile and tail inside the viewport, including wide received rows. */
internal fun reactionConnectorSourceX(
    anchor: Rect,
    railLeft: Float,
    railWidth: Float,
    inset: Float,
    isOutgoing: Boolean,
    containerWidth: Float,
): Float {
    val source = if (isOutgoing) anchor.left - inset else anchor.right + inset
    val clearance = min(inset, containerWidth / 2f)
    val visibleSource = source.coerceIn(clearance, containerWidth - clearance)
    return (visibleSource - railLeft).coerceIn(0f, railWidth)
}

@Composable
private fun MessageInfoRow(
    message: EnhancedMessage,
    primaryText: Color,
) {
    val receiptTime = readReceiptTime(message)
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clipToBounds()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        MessageStatusIcon(MessageStatus.READ)
        if (receiptTime != null) {
            Text(
                // ≡ iOS: «Visto hoy, 14:32» · «Visto ayer, 23:00» · «Visto el lun, 14:32» · «Visto el 24 sept, 14:32».
                MomentsFormat.seenReceipt(receiptTime),
                color = primaryText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier.weight(1f),
            )
        } else {
            Spacer(Modifier.weight(1f))
        }
        if (message.editedAt != null) {
            Text(
                stringResource(R.string.chat_edited),
                color = primaryText.copy(alpha = 0.52f),
                fontSize = 10.sp,
                maxLines = 1,
                softWrap = false,
            )
        }
    }
}

private const val MaxVisibleGroupReaders = 7

/** Solo quien tiene acuses activos: `readAtBy` (`readBy` incluye también a quien los desactivó). */
private fun groupReaderIds(message: EnhancedMessage): List<String> {
    val distantPast = Date(0)
    return message.readAtBy
        .orEmpty()
        .keys
        .filter { it != message.senderId }
        .sortedWith(
            compareByDescending<String> { message.readAtBy?.get(it) ?: distantPast }
                .thenBy { it },
        )
}

private fun shouldShowGroupReaders(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    isGroup: Boolean,
): Boolean = isGroup && isCurrentUser && groupReaderIds(message).isNotEmpty()

private fun extraMenuHeaderHeight(showsMessageInfo: Boolean, showsGroupReaders: Boolean): androidx.compose.ui.unit.Dp {
    var extra = 0.dp
    if (showsMessageInfo) extra += 36.dp
    if (showsGroupReaders) extra += 36.dp
    if (showsMessageInfo || showsGroupReaders) extra += 1.dp
    return extra
}

@Composable
private fun GroupReadReceiptsRow(
    message: EnhancedMessage,
    primaryText: Color,
) {
    val ids = remember(message.id, message.readBy, message.readAtBy) { groupReaderIds(message) }
    val visible = ids.take(MaxVisibleGroupReaders)
    val remaining = ids.size - visible.size
    Row(
        Modifier
            .fillMaxWidth()
            .height(36.dp)
            .clipToBounds()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StackedReaderAvatars(visible)
        if (remaining > 0) {
            Text(
                stringResource(R.string.chat_read_and_more, remaining),
                color = primaryText,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                softWrap = false,
                modifier = Modifier.weight(1f, fill = false),
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun StackedReaderAvatars(
    userIds: List<String>,
    avatarSize: androidx.compose.ui.unit.Dp = 22.dp,
    overlap: androidx.compose.ui.unit.Dp = 8.dp,
) {
    if (userIds.isEmpty()) return
    val cutoutDiameter = avatarSize + 3.dp
    val width = avatarSize + (avatarSize - overlap) * max(0, userIds.lastIndex)
    Box(Modifier.size(width, avatarSize)) {
        userIds.forEachIndexed { index, id ->
            val isLast = index == userIds.lastIndex
            Box(
                Modifier
                    .offset(x = (avatarSize - overlap) * index)
                    .size(avatarSize)
                    .zIndex(index.toFloat())
                    .then(
                        if (isLast) {
                            Modifier
                        } else {
                            Modifier
                                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                                .drawWithContent {
                                    drawContent()
                                    val cutCenter = Offset(
                                        x = center.x + (avatarSize - overlap).toPx(),
                                        y = center.y,
                                    )
                                    drawCircle(
                                        color = Color.Black,
                                        radius = cutoutDiameter.toPx() / 2f,
                                        center = cutCenter,
                                        blendMode = BlendMode.Clear,
                                    )
                                }
                        },
                    )
                    .clip(CircleShape),
            ) {
                AsyncProfileImageView(userId = id, modifier = Modifier.matchParentSize())
            }
        }
    }
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
            .clipToBounds()
            .clickable {
                // ≡ iOS `MomentRowButton(feedback: .menu)`
                HapticManager.shared.selection()
                action()
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.size(16.dp))
        Text(
            stringResource(title),
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            softWrap = false,
            modifier = Modifier.weight(1f),
        )
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

internal data class MenuLayoutMetrics(
    val menuRowHeight: Float,
    val menuVerticalPadding: Float,
    val stackGap: Float,
    val reactionsBarHeight: Float,
    val expandedReactionsHeight: Float,
    val horizontalInset: Float,
    val reactionsBarEstimatedWidth: Float,
    val menuEstimatedWidth: Float,
)

internal fun menuLayout(
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
        liftedMessageHitRect(anchor, 0f, scale, selection.isOutgoing)
    }

    val naturalMenuHeight = rowCount * metrics.menuRowHeight + metrics.menuVerticalPadding

    val stackGap = metrics.stackGap
    val reactionMessageGap = stackGap * 0.6f
    val reactionsBarHeight = if (reactionsExpanded) metrics.expandedReactionsHeight else metrics.reactionsBarHeight
    val availableHeight = (containerHeight - topMarginPx - bottomMarginPx).coerceAtLeast(1f)
    // Tall media can overlap the popup, but actions stay reachable above the IME.
    val actionsMaxHeight = min(naturalMenuHeight, max(
        metrics.menuRowHeight + metrics.menuVerticalPadding,
        availableHeight - scaled.height - reactionsBarHeight - stackGap - reactionMessageGap,
    )).coerceIn(1f, availableHeight)
    val menuHeight = if (reactionsExpanded) 0f else actionsMaxHeight
    val horizontalInset = metrics.horizontalInset
    val reactionsBarEstimatedWidth = min(containerWidth - metrics.horizontalInset * 2f, metrics.reactionsBarEstimatedWidth)
    val menuEstimatedWidth = metrics.menuEstimatedWidth

    fun clampCenterX(centerX: Float, itemWidth: Float, inset: Float = horizontalInset): Float {
        val half = itemWidth / 2f
        val minX = inset + half
        val maxX = containerWidth - inset - half
        if (maxX < minX) return containerWidth / 2f
        return centerX.coerceIn(minX, maxX)
    }

    fun clampCenterY(centerY: Float, itemHeight: Float): Float {
        val half = itemHeight / 2f
        val minY = topMarginPx + half
        val maxY = containerHeight - bottomMarginPx - half
        if (maxY < minY) return containerHeight / 2f
        return centerY.coerceIn(minY, maxY)
    }

    val isWideMessage = scaled.width >= reactionsBarEstimatedWidth * 0.85f
    val railOverhang = metrics.reactionsBarHeight * ((if (isWideMessage) 56f else 44f) / 64f)
    val centerX = clampCenterX(
        if (selection.isOutgoing) scaled.left - railOverhang + reactionsBarEstimatedWidth / 2f
        else scaled.right + railOverhang - reactionsBarEstimatedWidth / 2f,
        reactionsBarEstimatedWidth,
        if (isWideMessage) horizontalInset * 0.25f else horizontalInset,
    )
    // Encaja reacciones + mensaje + acciones en el viewport desplazando la fila
    // viva (no una copia de la burbuja).
    val minimumMessageTop = topMarginPx + reactionsBarHeight + reactionMessageGap
    val maximumMessageTop = containerHeight - bottomMarginPx - menuHeight - stackGap - scaled.height
    val targetMessageTop = if (maximumMessageTop >= minimumMessageTop) {
        scaled.top.coerceIn(minimumMessageTop, maximumMessageTop)
    } else {
        minimumMessageTop
    }
    val messageOffsetY = targetMessageTop - scaled.top
    val shiftedTop = scaled.top + messageOffsetY
    val shiftedBottom = scaled.bottom + messageOffsetY
    val reactionsCenterY = shiftedTop - reactionMessageGap - reactionsBarHeight / 2f
    val menuCenterY = shiftedBottom + stackGap + menuHeight / 2f

    return ChatMessageMenuLayout(
        messageOffsetY = messageOffsetY,
        reactionsCenter = Offset(
            centerX,
            clampCenterY(reactionsCenterY, reactionsBarHeight),
        ),
        menuCenter = Offset(
            clampCenterX(scaled.center.x, menuEstimatedWidth),
            clampCenterY(menuCenterY, menuHeight),
        ),
        reactionsAreAbove = true,
        actionsMaxHeight = actionsMaxHeight,
    )
}

private fun reactionPhase(progress: Float, start: Float, end: Float): Float {
    val t = ((progress - start) / (end - start)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}
