package com.moments.android.views.messaging.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
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
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.core.ChatMessagePolicy
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import java.util.Date
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

data class ChatMessageMenuSelection(
    val rowId: String,
    val message: EnhancedMessage,
    /** Frame en coordenadas de ventana (`boundsInWindow`); el overlay lo pasa a local. */
    val anchorFrame: Rect = Rect.Zero,
    val anchorCornerRadius: Float = ChatBubbleAnchorMetrics.cornerRadiusFor(message),
    val isOutgoing: Boolean,
    val clusterMessages: List<EnhancedMessage>? = null,
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

fun Modifier.chatMenuDimmedWhenOpen(menuOpen: Boolean): Modifier =
    alpha(if (menuOpen) ChatMenuDimming.inactiveOpacity else 1f)

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
)

private data class ChatMessageMenuLayout(val reactionsCenter: Offset, val menuCenter: Offset)

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
    isPressing: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val selectionScale = when {
        isMenuSelected || isFlashing -> ChatBubbleAnchorMetrics.highlightScale
        isPressing -> ChatBubbleAnchorMetrics.pressScale
        else -> 1f
    }
    // iOS: spring.press para menú/flash; easeOut 0.12 para press.
    val animatedScale by animateFloatAsState(
        targetValue = selectionScale,
        animationSpec = if (isPressing) {
            tween(durationMillis = 120)
        } else {
            spring(
                dampingRatio = MotionPolicy.Spring.PRESS_DAMPING.toFloat(),
                stiffness = 500f,
            )
        },
        label = "bubbleChromeScale",
    )
    val highlightTint = (if (dark) Color.White else Color.Black).copy(alpha = 0.12f)
    Box(
        Modifier
            .zIndex(if (isMenuSelected || isFlashing) 1f else 0f)
            .graphicsLayer {
                scaleX = animatedScale
                scaleY = animatedScale
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
    val emojiTracker = remember { EmojiUsageTracker() }
    val primaryText = com.moments.android.extensions.MomentsChromeGlass.contentColor(dark)
    val shadowAlpha = if (dark) 0.24f else 0.12f
    val menuCorner = ChatAttachmentSheetMetrics.cornerRadius
    val isCurrentUser = item.message.senderId == currentUserId
    val isStarred = item.message.id in starredMessageIds || item.message.isStarred(currentUserId)
    val rowCount = visibleMenuRowsCount(item.message, isCurrentUser, currentUserId, forwardingPreferences)
    val systemBars = WindowInsets.systemBars
    var overlayOriginInWindow by remember { mutableStateOf(Offset.Zero) }

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
        val layout = remember(
            localSelection.rowId,
            localSelection.anchorFrame,
            rowCount,
            containerW,
            containerH,
            topMarginPx,
            bottomMarginPx,
        ) {
            menuLayout(
                selection = localSelection,
                rowCount = rowCount,
                containerWidth = containerW,
                containerHeight = containerH,
                topMarginPx = topMarginPx,
                bottomMarginPx = bottomMarginPx,
            )
        }

        // Tap fuera: iOS usa Color.clear (el dimming es de filas, no scrim negro).
        Box(Modifier.fillMaxSize().clickable(onClick = onDismiss))

        val reactionsEmojis = remember { emojiTracker.orderedEmojis(EmojiReactionDefaults.chat) }
        Row(
            Modifier
                .offset {
                    IntOffset(
                        (layout.reactionsCenter.x - 150f).roundToInt(),
                        (layout.reactionsCenter.y - 27f).roundToInt(),
                    )
                }
                .shadow(24.dp, CircleShape, ambientColor = Color.Black.copy(shadowAlpha), spotColor = Color.Black.copy(shadowAlpha))
                .clip(RoundedCornerShape(50))
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            reactionsEmojis.forEach { emoji ->
                Text(
                    emoji,
                    fontSize = 28.sp,
                    modifier = Modifier.clickable {
                        HapticManager.shared.mediumImpact()
                        onDismiss()
                        callbacks.onReaction(item.message, emoji)
                    },
                )
            }
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .border(
                        1.dp,
                        primaryText.copy(alpha = if (dark) 0.12f else 0.08f),
                        CircleShape,
                    )
                    .clickable {
                        HapticManager.shared.lightImpact()
                        onDismiss()
                        callbacks.onMoreReactions(item.message)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Add, stringResource(R.string.chat_action_more_reactions), tint = primaryText, modifier = Modifier.size(18.dp))
            }
        }

        if (!item.message.isDeleted && rowCount > 0) {
            Column(
                Modifier
                    .offset {
                        val halfH = (rowCount * 36f + 16f) / 2f
                        IntOffset(
                            (layout.menuCenter.x - 120f).roundToInt(),
                            (layout.menuCenter.y - halfH).roundToInt(),
                        )
                    }
                    .widthIn(min = 240.dp)
                    .shadow(24.dp, RoundedCornerShape(menuCorner), ambientColor = Color.Black.copy(shadowAlpha), spotColor = Color.Black.copy(shadowAlpha))
                    .clip(RoundedCornerShape(menuCorner))
                    .momentsChromeGlass(RoundedCornerShape(menuCorner), interactive = true)
                    .padding(horizontal = 8.dp, vertical = 8.dp),
            ) {
                MenuRow(R.string.chat_action_reply, Icons.AutoMirrored.Filled.Reply, primaryText) {
                    onDismiss()
                    callbacks.onReply(item.message)
                }
                if (ChatMessagePolicy.canForward(item.message, currentUserId, forwardingPreferences)) {
                    MenuRow(R.string.chat_action_forward, Icons.Default.Forward, primaryText) {
                        onDismiss()
                        callbacks.onForward(item.message)
                    }
                }
                if (!ChatMessagePolicy.isVanishRestricted(item.message)) {
                    MenuRow(
                        if (isStarred) R.string.chat_action_unstar else R.string.chat_action_star,
                        // ≡ iOS star.slash / star
                        if (isStarred) Icons.Default.StarBorder else Icons.Default.Star,
                        primaryText,
                    ) {
                        onDismiss()
                        callbacks.onToggleStar(item.message)
                    }
                }
                if (ChatMessagePolicy.canEdit(item.message, currentUserId)) {
                    MenuRow(R.string.chat_action_edit, Icons.Default.Edit, primaryText) {
                        onDismiss()
                        callbacks.onEdit(item.message)
                    }
                }
                if (ChatMessagePolicy.canCopy(item.message, currentUserId, forwardingPreferences)) {
                    MenuRow(R.string.chat_action_copy, Icons.Default.ContentCopy, primaryText) {
                        onDismiss()
                        callbacks.onCopy(item.message)
                    }
                }
                MenuRow(R.string.chat_action_delete_for_me, Icons.Default.Delete, primaryText, destructive = true) {
                    onDismiss()
                    callbacks.onDeleteForMe(item.message)
                }
                if (isCurrentUser && !item.message.isRead && isWithinDeleteLimit(item.message.timestamp)) {
                    MenuRow(R.string.chat_action_delete_for_everyone, Icons.Default.Delete, primaryText, destructive = true) {
                        onDismiss()
                        callbacks.onDeleteForEveryone(item.message)
                    }
                }
            }
        }
    }
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
        Text(stringResource(title), color = color, fontSize = 16.sp, fontWeight = FontWeight.Medium)
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

private fun menuLayout(
    selection: ChatMessageMenuSelection,
    rowCount: Int,
    containerWidth: Float,
    containerHeight: Float,
    topMarginPx: Float,
    bottomMarginPx: Float,
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

    val menuRowHeight = 36f
    val menuHeight = rowCount * menuRowHeight + 16f
    val stackGap = 10f
    val reactionsBarHeight = 54f
    val horizontalInset = 16f
    val reactionsBarEstimatedWidth = 300f
    val menuEstimatedWidth = 240f

    fun clampCenterX(centerX: Float, itemWidth: Float): Float {
        val half = itemWidth / 2f
        val minX = horizontalInset + half
        val maxX = containerWidth - horizontalInset - half
        if (maxX < minX) return containerWidth / 2f
        return centerX.coerceIn(minX, maxX)
    }

    val centerX = clampCenterX(scaled.center.x, max(reactionsBarEstimatedWidth, menuEstimatedWidth))
    val spaceAbove = scaled.top - topMarginPx
    val spaceBelow = containerHeight - bottomMarginPx - scaled.bottom
    val reactionsAbove = spaceAbove >= reactionsBarHeight + stackGap
    val reactionsBelow = !reactionsAbove && spaceBelow >= reactionsBarHeight + stackGap

    val reactionsCenterY = when {
        reactionsAbove -> max(topMarginPx + reactionsBarHeight / 2f, scaled.top - stackGap - reactionsBarHeight / 2f)
        else -> min(
            containerHeight - bottomMarginPx - reactionsBarHeight / 2f,
            scaled.bottom + stackGap + reactionsBarHeight / 2f,
        )
    }

    val menuBelowPreferred = spaceBelow >= menuHeight + stackGap + if (reactionsBelow) reactionsBarHeight + stackGap else 0f
    val menuAbovePreferred = spaceAbove >= menuHeight + stackGap + if (reactionsAbove) reactionsBarHeight + stackGap else 0f
    val menuBelow = when {
        menuBelowPreferred -> true
        menuAbovePreferred -> false
        else -> spaceBelow >= spaceAbove
    }

    val menuCenterY = if (menuBelow) {
        var anchorMaxY = scaled.bottom
        if (reactionsBelow) anchorMaxY += stackGap + reactionsBarHeight
        min(containerHeight - bottomMarginPx - menuHeight / 2f, anchorMaxY + stackGap + menuHeight / 2f)
    } else {
        var anchorMinY = scaled.top
        if (reactionsAbove) anchorMinY -= stackGap + reactionsBarHeight
        max(topMarginPx + menuHeight / 2f, anchorMinY - stackGap - menuHeight / 2f)
    }

    return ChatMessageMenuLayout(
        reactionsCenter = Offset(centerX, reactionsCenterY),
        menuCenter = Offset(clampCenterX(scaled.center.x, menuEstimatedWidth), menuCenterY),
    )
}
