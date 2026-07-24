package com.moments.android.views.messaging.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType
import java.util.Date

data class ChatMessageMenuSelection(val rowId: String, val message: EnhancedMessage, val anchorFrame: Rect = Rect.Zero, val anchorCornerRadius: Float = ChatBubbleAnchorMetrics.cornerRadiusFor(message), val isOutgoing: Boolean, val clusterMessages: List<EnhancedMessage>? = null)
object ChatBubbleAnchorMetrics { const val menuSelectionScale = 1.03f; const val highlightScale = menuSelectionScale; const val highlightDurationMillis = 1500L; const val pressScale = .97f; const val clusterCornerRadius = 16f; fun cornerRadiusFor(message: EnhancedMessage) = when (message.type) { MessageType.TEXT -> 20f; MessageType.AUDIO -> 18f; MessageType.GIF, MessageType.STICKER -> 12f; MessageType.FILE -> 14f; else -> 16f } }
object ChatMenuDimming { const val inactiveOpacity = .42f }

data class ChatMessageMenuCallbacks(val onDeleteForEveryone: (EnhancedMessage) -> Unit = {}, val onDeleteForMe: (EnhancedMessage) -> Unit = {}, val onEdit: (EnhancedMessage) -> Unit = {}, val onReply: (EnhancedMessage) -> Unit = {}, val onCopy: (EnhancedMessage) -> Unit = {}, val onForward: (EnhancedMessage) -> Unit = {}, val onToggleStar: (EnhancedMessage) -> Unit = {}, val onReaction: (EnhancedMessage, String) -> Unit = { _, _ -> }, val onMoreReactions: (EnhancedMessage) -> Unit = {})

@Composable
fun ChatMessageBubbleChrome(isMenuSelected: Boolean, isOutgoing: Boolean, isFlashing: Boolean = false, content: @Composable () -> Unit) = Box(Modifier.scale(if (isMenuSelected || isFlashing) ChatBubbleAnchorMetrics.highlightScale else 1f)) { content(); if (isFlashing) Box(Modifier.matchParentSize().background(Color.White.copy(.12f), RoundedCornerShape(16.dp))) }

/** Port del overlay: acciones filtradas por ownership/estado y barra de reacciones. */
@Composable
fun ChatMessageContextMenuOverlay(selection: ChatMessageMenuSelection?, currentUserId: String, starredMessageIds: Set<String> = emptySet(), callbacks: ChatMessageMenuCallbacks = ChatMessageMenuCallbacks(), onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(selection != null, modifier) {
        val item = selection ?: return@AnimatedVisibility
        Box(Modifier.fillMaxSize().background(Color.Black.copy(.35f)).clickable(onClick = onDismiss)) {
            Column(Modifier.align(if (item.anchorFrame.center.y < 400f) Alignment.BottomCenter else Alignment.TopCenter).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.background(Color(0xDD25262A), RoundedCornerShape(50)).padding(10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("❤️", "😂", "😮", "😢", "👍").forEach { emoji -> Text(emoji, fontSize = 26.sp, modifier = Modifier.clickable { callbacks.onReaction(item.message, emoji); onDismiss() }) }
                    Icon(Icons.Default.Add, stringResource(R.string.chat_action_more_reactions), tint = Color.White, modifier = Modifier.size(30.dp).clickable { callbacks.onMoreReactions(item.message); onDismiss() })
                }
                Column(Modifier.width(240.dp).background(Color(0xEE25262A), RoundedCornerShape(18.dp)).padding(vertical = 6.dp)) {
                    MenuRow(R.string.chat_action_reply, Icons.Default.Reply) { callbacks.onReply(item.message); onDismiss() }
                    MenuRow(R.string.chat_action_forward, Icons.Default.Forward) { callbacks.onForward(item.message); onDismiss() }
                    if (item.message.id in starredMessageIds) MenuRow(R.string.chat_action_unstar, Icons.Default.Star) { callbacks.onToggleStar(item.message); onDismiss() } else MenuRow(R.string.chat_action_star, Icons.Default.StarBorder) { callbacks.onToggleStar(item.message); onDismiss() }
                    if (item.isOutgoing && !item.message.isDeleted) MenuRow(R.string.chat_action_edit, Icons.Default.Edit) { callbacks.onEdit(item.message); onDismiss() }
                    if (item.message.type == MessageType.TEXT) MenuRow(R.string.chat_action_copy, Icons.Default.ContentCopy) { callbacks.onCopy(item.message); onDismiss() }
                    MenuRow(R.string.chat_action_delete_for_me, Icons.Default.Delete, true) { callbacks.onDeleteForMe(item.message); onDismiss() }
                    if (item.isOutgoing && !item.message.isRead && Date().time - item.message.timestamp.time < 7_200_000L) MenuRow(R.string.chat_action_delete_for_everyone, Icons.Default.Delete, true) { callbacks.onDeleteForEveryone(item.message); onDismiss() }
                }
            }
        }
    }
}

@Composable private fun MenuRow(title: Int, icon: androidx.compose.ui.graphics.vector.ImageVector, destructive: Boolean = false, action: () -> Unit) = Row(Modifier.clickable(onClick = action).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(title), color = if (destructive) Color.Red else Color.White); Spacer(Modifier.weight(1f)); Icon(icon, null, tint = if (destructive) Color.Red else Color.White, modifier = Modifier.size(18.dp)) }
