package com.moments.android.views.messaging.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.components.MomentRowButton
import com.moments.android.views.components.MomentRowButtonFeedback
import com.moments.android.views.messaging.core.Conversation
import kotlin.math.max

/**
 * Port de `ConversationContextMenu.swift` — selección, overlay con cutout, menú chrome glass
 * y highlight de fila.
 *
 * Cutout: iOS `blendMode(.destinationOut)` + compositingGroup → Compose
 * [CompositingStrategy.Offscreen] + [BlendMode.Clear].
 */

data class ConversationMenuData(
    val conversation: Conversation,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
)

/** Igualdad ≡ iOS: solo `conversation.id`. */
data class ConversationMenuSelection(
    val item: ConversationMenuData,
    val rowFrame: Rect,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ConversationMenuSelection) return false
        return item.conversation.id == other.item.conversation.id
    }

    override fun hashCode(): Int = item.conversation.id.hashCode()
}

data class ConversationListInteraction(
    val onTap: () -> Unit,
    val onLongPress: () -> Unit,
    val onPressingChanged: (Boolean) -> Unit,
)

data class ConversationContextMenuInsets(
    val top: Float = 0f,
    val bottom: Float = 0f,
)

object ConversationRowFrameStore {
    fun merged(current: Map<String, Rect>, next: Map<String, Rect>): Map<String, Rect> = current + next
}

private object ConversationContextMenuMetrics {
    const val menuRowHeight = 38f
    val menuCornerRadius = 16.dp
    val rowCornerRadius = 14.dp
    const val horizontalInset = 16f
    const val stackGap = 10f
}

@Composable
fun ConversationContextMenuOverlay(
    selection: ConversationMenuSelection?,
    containerSize: IntSize,
    safeAreaInsets: ConversationContextMenuInsets = ConversationContextMenuInsets(),
    onDismiss: () -> Unit,
    onMarkUnread: (Conversation) -> Unit,
    onPin: (Conversation) -> Unit,
    onMute: (Conversation) -> Unit,
    onArchive: (Conversation) -> Unit,
    onUnarchive: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val systemBars = WindowInsets.systemBars
    // Call sites a menudo pasan insets vacíos → completar con systemBars (≡ iOS safeAreaInsets).
    val resolvedInsets = ConversationContextMenuInsets(
        top = max(safeAreaInsets.top, systemBars.getTop(density).toFloat()),
        bottom = max(safeAreaInsets.bottom, systemBars.getBottom(density).toFloat()),
    )
    var rendered by remember { mutableStateOf(selection) }
    if (selection != null) rendered = selection

    AnimatedVisibility(
        visible = selection != null,
        enter = fadeIn() + scaleIn(initialScale = 0.95f),
        exit = fadeOut() + scaleOut(targetScale = 0.95f),
        modifier = modifier.fillMaxSize(),
    ) {
        val current = selection ?: rendered ?: return@AnimatedVisibility
        val layout = rememberMenuLayout(current, containerSize, resolvedInsets)
        Box(Modifier.fillMaxSize()) {
            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .clickable(onClick = onDismiss),
            ) {
                drawRect(Color.Black.copy(alpha = if (isDark) 0.50f else 0.32f))
                val cutout = current.rowFrame.scaled(0.92f)
                drawRoundRect(
                    color = Color.Black,
                    topLeft = Offset(cutout.left, cutout.top),
                    size = cutout.size,
                    cornerRadius = CornerRadius(ConversationContextMenuMetrics.rowCornerRadius.toPx()),
                    blendMode = BlendMode.Clear,
                )
            }
            ConversationContextMenuActions(
                item = current.item,
                onDismiss = onDismiss,
                onMarkUnread = onMarkUnread,
                onPin = onPin,
                onMute = onMute,
                onArchive = onArchive,
                onUnarchive = onUnarchive,
                onDelete = onDelete,
                modifier = Modifier.offset { IntOffset(layout.leadingX.toInt(), layout.topY.toInt()) },
            )
        }
    }
}

private data class MenuLayout(val leadingX: Float, val topY: Float)

@Composable
private fun rememberMenuLayout(
    selection: ConversationMenuSelection,
    containerSize: IntSize,
    safeAreaInsets: ConversationContextMenuInsets,
): MenuLayout {
    val rowFrame = selection.rowFrame.scaled(0.92f)
    val rows = if (selection.item.unreadCount == 0) 5 else 4
    val menuPanelHeight = ConversationContextMenuMetrics.menuRowHeight * rows
    val leadingX = rowFrame.left + ConversationContextMenuMetrics.horizontalInset
    val below = containerSize.height - safeAreaInsets.bottom - 12f - rowFrame.bottom
    val above = rowFrame.top - safeAreaInsets.top - 12f
    val required = menuPanelHeight + ConversationContextMenuMetrics.stackGap
    val placeBelow = when {
        below >= required -> true
        above >= required -> false
        else -> below >= above
    }
    val topY = if (placeBelow) {
        rowFrame.bottom + ConversationContextMenuMetrics.stackGap
    } else {
        max(safeAreaInsets.top + 8f, rowFrame.top - ConversationContextMenuMetrics.stackGap - menuPanelHeight)
    }
    return MenuLayout(leadingX, topY)
}

@Composable
private fun ConversationContextMenuActions(
    item: ConversationMenuData,
    onDismiss: () -> Unit,
    onMarkUnread: (Conversation) -> Unit,
    onPin: (Conversation) -> Unit,
    onMute: (Conversation) -> Unit,
    onArchive: (Conversation) -> Unit,
    onUnarchive: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    modifier: Modifier,
) {
    val conversation = item.conversation
    val shape = RoundedCornerShape(ConversationContextMenuMetrics.menuCornerRadius)
    fun run(action: (Conversation) -> Unit) {
        onDismiss()
        action(conversation)
    }

    Column(
        modifier
            .widthIn(min = 230.dp)
            .clip(shape)
            .momentsChromeGlass(shape, interactive = true),
    ) {
        if (item.unreadCount == 0) {
            ConversationContextMenuRow(
                icon = Icons.Default.Email,
                titleRes = R.string.messaging_menu_mark_unread,
                onClick = { run(onMarkUnread) },
            )
        }
        ConversationContextMenuRow(
            icon = Icons.Default.PushPin,
            // ≡ iOS pin.slash cuando pinned
            pinSlash = item.isPinned,
            titleRes = if (item.isPinned) R.string.messaging_swipe_unpin else R.string.messaging_swipe_pin,
            onClick = { run(onPin) },
        )
        ConversationContextMenuRow(
            icon = if (item.isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
            titleRes = if (item.isMuted) R.string.messaging_swipe_unmute else R.string.messaging_swipe_mute,
            onClick = { run(onMute) },
        )
        ConversationContextMenuRow(
            icon = if (item.isArchived) Icons.Default.Unarchive else Icons.Default.Archive,
            titleRes = if (item.isArchived) R.string.messaging_menu_unarchive else R.string.messaging_menu_archive,
            onClick = { run(if (item.isArchived) onUnarchive else onArchive) },
        )
        ConversationContextMenuRow(
            icon = Icons.Default.Delete,
            titleRes = R.string.notifications_delete,
            destructive = true,
            onClick = { run(onDelete) },
        )
    }
}

@Composable
private fun ConversationContextMenuRow(
    icon: ImageVector,
    titleRes: Int,
    destructive: Boolean = false,
    pinSlash: Boolean = false,
    onClick: () -> Unit,
) {
    val color = when {
        destructive -> Color.Red
        isSystemInDarkTheme() -> Color.White
        else -> Color.Black
    }
    // ≡ iOS `MomentRowButton(feedback: .menu)`
    MomentRowButton(
        action = onClick,
        feedback = MomentRowButtonFeedback.MENU,
        modifier = Modifier
            .fillMaxWidth()
            .height(ConversationContextMenuMetrics.menuRowHeight.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(ConversationContextMenuMetrics.menuRowHeight.dp)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pinSlash) {
                PinSlashIcon(tint = color, modifier = Modifier.size(18.dp))
            } else {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text(
                stringResource(titleRes),
                color = color,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

/** Aproximación visual de SF Symbol `pin.slash` (Material no tiene equivalente). */
@Composable
private fun PinSlashIcon(tint: Color, modifier: Modifier = Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Icon(Icons.Default.PushPin, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Canvas(Modifier.matchParentSize()) {
            val stroke = 1.6.dp.toPx()
            drawLine(
                color = tint,
                start = Offset(size.width * 0.12f, size.height * 0.88f),
                end = Offset(size.width * 0.88f, size.height * 0.12f),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

/** Port de `ConversationRowMenuHighlight`. */
@Composable
fun Modifier.conversationRowMenuHighlight(isSelected: Boolean): Modifier =
    if (isSelected) {
        background(
            if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color.White,
            RoundedCornerShape(ConversationContextMenuMetrics.rowCornerRadius),
        )
    } else {
        this
    }

private fun Rect.scaled(scale: Float): Rect {
    val widthDiff = width * (1f - scale)
    val heightDiff = height * (1f - scale)
    return Rect(
        left + widthDiff / 2f,
        top + heightDiff / 2f,
        right - widthDiff / 2f,
        bottom - heightDiff / 2f,
    )
}
