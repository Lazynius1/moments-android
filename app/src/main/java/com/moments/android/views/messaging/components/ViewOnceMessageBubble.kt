package com.moments.android.views.messaging.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageType

/** Port de `Views/Messaging/Components/ViewOnceMessageBubble.swift`. */
data class ViewOnceMessageState(
    val allowReplay: Boolean = false,
    val replayAvailableInCurrentChatSession: Boolean = false,
    val replayConsumedInCurrentChatSession: Boolean = false,
    val hasBeenReplayedByCurrentUser: Boolean = false,
    val replayedByAnyone: Boolean = false,
)

@Composable
fun ViewOnceMessageBubble(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    otherParticipantName: String,
    progress: Double?,
    state: ViewOnceMessageState = ViewOnceMessageState(),
    onOpenViewer: ((replay: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val replayAvailable = state.allowReplay && state.replayAvailableInCurrentChatSession && !state.replayConsumedInCurrentChatSession && !state.hasBeenReplayedByCurrentUser
    val effectiveViewed = message.isViewed || state.replayAvailableInCurrentChatSession
    when {
        isCurrentUser -> ViewOnceSentBubble(message, progress, state.replayedByAnyone, modifier)
        effectiveViewed && replayAvailable -> ViewOnceReplayBubble(onTap = { onOpenViewer?.invoke(true) }, modifier = modifier)
        effectiveViewed -> ViewOnceOpenedBubble(modifier)
        else -> ViewOnceUnreadBubble(message, onTap = { onOpenViewer?.invoke(false) }, modifier = modifier)
    }
}

@Composable
private fun ViewOncePillBubble(icon: @Composable () -> Unit, label: String, muted: Boolean = false, unread: Boolean = false, onTap: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme(); val text = if (isDark) Color.White else Color.Black
    Row(modifier.clip(RoundedCornerShape(50)).background(if (isDark) Color.White.copy(.12f) else Color.Black.copy(.06f)).then(if (onTap == null) Modifier else Modifier.clickable(onClick = onTap)).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) { Canvas(Modifier.size(30.dp)) { drawCircle(text.copy(.7f), style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())))) }; icon() }
        Text(label, color = text.copy(if (muted) .45f else 1f), fontSize = 14.sp)
        if (unread) Box(Modifier.size(8.dp).background(Color(0xFFFF2D92), RoundedCornerShape(50)))
    }
}

@Composable private fun ViewOnceUnreadBubble(message: EnhancedMessage, onTap: () -> Unit, modifier: Modifier) = ViewOncePillBubble(icon = { Icon(if (message.type == MessageType.VIEW_ONCE_VIDEO) Icons.Default.PlayArrow else Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp)) }, label = stringResource(message.viewOnceTypeRes), unread = true, onTap = onTap, modifier = modifier)
@Composable private fun ViewOnceReplayBubble(onTap: () -> Unit, modifier: Modifier) = ViewOncePillBubble(icon = { Icon(Icons.Default.Replay, null, modifier = Modifier.size(16.dp)) }, label = stringResource(R.string.chat_view_once_tap_to_replay), onTap = onTap, modifier = modifier)
@Composable private fun ViewOnceOpenedBubble(modifier: Modifier) = ViewOncePillBubble(icon = { Icon(Icons.Default.VisibilityOff, null, modifier = Modifier.size(16.dp)) }, label = stringResource(R.string.chat_view_once_already_viewed), muted = true, modifier = modifier)
@Composable private fun ViewOnceSentBubble(message: EnhancedMessage, progress: Double?, replayed: Boolean, modifier: Modifier) { val label = when { message.isViewed && replayed -> R.string.chat_view_once_replayed; message.isViewed -> R.string.chat_view_once_viewed; else -> message.viewOnceTypeRes }; ViewOncePillBubble(icon = { when { message.status == MessageStatus.SENDING && progress != null -> MediaProgressRing(progress, 26.dp, 2.dp); message.isViewed -> Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)); else -> Icon(if (message.type == MessageType.VIEW_ONCE_VIDEO) Icons.Default.PlayArrow else Icons.Default.CameraAlt, null, modifier = Modifier.size(16.dp)) } }, label = stringResource(label), muted = message.isViewed, modifier = modifier) }
private val EnhancedMessage.viewOnceTypeRes: Int get() = when (type) { MessageType.VIEW_ONCE_IMAGE -> R.string.chat_view_once_photo; MessageType.VIEW_ONCE_VIDEO -> R.string.chat_view_once_video; else -> R.string.chat_view_once_media }
