package com.moments.android.views.messaging.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.Conversation
import kotlin.math.max

/** Port de `Views/Messaging/Components/ConversationContextMenu.swift`. */
data class ConversationMenuData(
    val conversation: Conversation,
    val unreadCount: Int = 0,
    val isPinned: Boolean = false,
    val isMuted: Boolean = false,
    val isArchived: Boolean = false,
)

data class ConversationMenuSelection(
    val item: ConversationMenuData,
    val rowFrame: Rect,
)

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

@Composable
fun ConversationContextMenuOverlay(
    selection: ConversationMenuSelection?,
    containerSize: IntSize,
    safeAreaInsets: ConversationContextMenuInsets,
    onDismiss: () -> Unit,
    onMarkUnread: (Conversation) -> Unit,
    onPin: (Conversation) -> Unit,
    onMute: (Conversation) -> Unit,
    onArchive: (Conversation) -> Unit,
    onUnarchive: (Conversation) -> Unit,
    onDelete: (Conversation) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selection == null) return
    val menu = ConversationContextMenuMetrics(selection, containerSize, safeAreaInsets)
    val isDark = isSystemInDarkTheme()
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().clickable(onClick = onDismiss)) {
            drawContextMenuDimLayer(selection.rowFrame, isDark)
        }
        ConversationContextMenuActions(
            item = selection.item,
            onDismiss = onDismiss,
            onMarkUnread = onMarkUnread,
            onPin = onPin,
            onMute = onMute,
            onArchive = onArchive,
            onUnarchive = onUnarchive,
            onDelete = onDelete,
            modifier = Modifier.offset { IntOffset(menu.leadingX.toInt(), menu.topY.toInt()) },
        )
    }
}

private fun DrawScope.drawContextMenuDimLayer(rowFrame: Rect, isDark: Boolean) {
    drawRect(Color.Black.copy(alpha = if (isDark) .50f else .32f))
    val cutout = rowFrame.scaled(.92f)
    drawRoundRect(
        color = Color.Transparent,
        topLeft = Offset(cutout.left, cutout.top),
        size = cutout.size,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx()),
        blendMode = BlendMode.Clear,
    )
}

private class ConversationContextMenuMetrics(
    selection: ConversationMenuSelection,
    private val containerSize: IntSize,
    private val safeAreaInsets: ConversationContextMenuInsets,
) {
    private val rowFrame = selection.rowFrame.scaled(.92f)
    private val rows = if (selection.item.unreadCount == 0) 5 else 4
    private val menuPanelHeight = 38f * rows

    val leadingX: Float = rowFrame.left + 16f
    val topY: Float
        get() = if (shouldPlaceBelow()) rowFrame.bottom + 10f else max(safeAreaInsets.top + 8f, rowFrame.top - 10f - menuPanelHeight)

    private fun shouldPlaceBelow(): Boolean {
        val below = containerSize.height - safeAreaInsets.bottom - 12f - rowFrame.bottom
        val above = rowFrame.top - safeAreaInsets.top - 12f
        val required = menuPanelHeight + 10f
        return when {
            below >= required -> true
            above >= required -> false
            else -> below >= above
        }
    }
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
    val rowAction: ((Conversation) -> Unit) -> () -> Unit = { action -> { onDismiss(); action(conversation) } }
    Surface(
        modifier = modifier.width(230.dp),
        shape = RoundedCornerShape(16.dp),
        color = if (isSystemInDarkTheme()) Color(0xFF1C1C1E).copy(alpha = .94f) else Color.White.copy(alpha = .96f),
    ) {
        Column {
            if (item.unreadCount == 0) {
                ConversationContextMenuRow(Icons.Default.Email, R.string.messaging_menu_mark_unread, action = rowAction(onMarkUnread))
            }
            ConversationContextMenuRow(
                Icons.Default.PushPin,
                if (item.isPinned) R.string.messaging_swipe_unpin else R.string.messaging_swipe_pin,
                action = rowAction(onPin),
            )
            ConversationContextMenuRow(
                if (item.isMuted) Icons.Default.Notifications else Icons.Default.NotificationsOff,
                if (item.isMuted) R.string.messaging_swipe_unmute else R.string.messaging_swipe_mute,
                action = rowAction(onMute),
            )
            ConversationContextMenuRow(
                Icons.Default.Archive,
                if (item.isArchived) R.string.messaging_menu_unarchive else R.string.messaging_menu_archive,
                action = rowAction(if (item.isArchived) onUnarchive else onArchive),
            )
            ConversationContextMenuRow(Icons.Default.Delete, R.string.notifications_delete, destructive = true, action = rowAction(onDelete))
        }
    }
}

@Composable
private fun ConversationContextMenuRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    titleRes: Int,
    destructive: Boolean = false,
    action: () -> Unit,
) {
    val color = if (destructive) Color.Red else if (isSystemInDarkTheme()) Color.White else Color.Black
    Row(
        modifier = Modifier.fillMaxWidth().height(38.dp).clickable(onClick = action).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = color, modifier = Modifier.width(18.dp))
        Spacer(Modifier.width(12.dp))
        Text(stringResource(titleRes), color = color, fontSize = 14.5.sp)
    }
}

@Composable
fun Modifier.conversationRowMenuHighlight(isSelected: Boolean): Modifier =
    if (isSelected) background(if (isSystemInDarkTheme()) Color(0xFF1C1C1E) else Color.White, RoundedCornerShape(14.dp)) else this

private fun Rect.scaled(scale: Float): Rect {
    val widthDiff = width * (1f - scale)
    val heightDiff = height * (1f - scale)
    return Rect(left + widthDiff / 2f, top + heightDiff / 2f, right - widthDiff / 2f, bottom - heightDiff / 2f)
}
