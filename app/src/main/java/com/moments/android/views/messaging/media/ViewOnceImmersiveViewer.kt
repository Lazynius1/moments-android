package com.moments.android.views.messaging.media

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType
import com.moments.android.utilities.EmojiReactionDefaults
import com.moments.android.utilities.EmojiUsageTracker
import com.moments.android.views.feed.video.FeedVideoPage
import com.moments.android.views.messaging.services.ViewOnceConsumptionReason
import com.moments.android.views.messaging.services.ViewOnceConsumptionService
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.story.storyviewer.StoryMediaOverlayRendererView
import com.moments.android.views.story.storyviewer.StoryReactionsStrip
import kotlinx.coroutines.delay

/** Visor inmersivo protegido de media view-once, espejo del flujo Swift. */
@Composable
fun ViewOnceImmersiveViewer(
    message: EnhancedMessage,
    authorName: String,
    onViewed: () -> Unit,
    isReplaySession: Boolean = false,
    onReplayConsumed: () -> Unit = {},
    onSendReply: (String) -> Unit = {},
    onSendReaction: (String) -> Unit = {},
    onOpenCameraReply: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(5f) }
    var paused by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var reply by remember { mutableStateOf("") }
    var showReactions by remember { mutableStateOf(false) }
    var sentConfirmation by remember { mutableStateOf(false) }
    val emojiUsage = remember { EmojiUsageTracker() }
    val reactions = emojiUsage.orderedEmojis(EmojiReactionDefaults.story)
    val isVideo = message.type == MessageType.VIEW_ONCE_VIDEO

    fun consumeAndDismiss() {
        if (closing) return
        closing = true
        if (message.allowReplay != true || isReplaySession) {
            ViewOnceConsumptionService.consume(message.conversationId, message.id, if (isReplaySession) ViewOnceConsumptionReason.REPLAY else ViewOnceConsumptionReason.VIEW_ONCE) { }
            if (isReplaySession) onReplayConsumed()
        }
        onDismiss()
    }
    fun confirmSent() { sentConfirmation = true }

    LaunchedEffect(message.id) { onViewed() }
    LaunchedEffect(paused, closing, isVideo) {
        while (!paused && !closing) { delay(100); progress = (progress + .1f).let { if (it >= duration) 0f else it } }
    }
    LaunchedEffect(sentConfirmation) { if (sentConfirmation) { delay(1400); sentConfirmation = false } }

    Box(modifier.fillMaxSize().background(Color(0xFF0B1215)).pointerInput(showReactions) {
        detectDragGestures(
            onDrag = { change, amount -> if (!showReactions && amount.y > 0f) { dragOffset = (dragOffset + amount.y).coerceAtLeast(0f); change.consume() } },
            onDragEnd = { if (dragOffset > 100f) consumeAndDismiss() else dragOffset = 0f },
        )
    }) {
        Box(Modifier.fillMaxSize().padding(20.dp).clip(RoundedCornerShape(28.dp))) {
            ScreenshotProtectedView(isProtected = true, fillsContainer = true) {
                Box(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures(onPress = { paused = true; tryAwaitRelease(); paused = false }) }) {
                    if (isVideo) FeedVideoPage(message.mediaUrl.orEmpty(), message.thumbnailUrl, "view-once-${message.id}", Modifier.fillMaxSize(), showMute = false)
                    else AsyncImage(message.mediaUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    StoryMediaOverlayRendererView(message.resolvedTextOverlays, message.resolvedStickers, message.drawingData, message.id, message.senderId, modifier = Modifier.fillMaxSize())
                }
            }
        }
        ViewOnceProgress(progress / duration, Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 18.dp))
        Row(Modifier.fillMaxWidth().padding(28.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(authorName, color = Color.White); Text(message.timestamp.timeAgoDisplay(), color = Color.White.copy(.58f)) }
            ViewerCircleButton(Icons.Filled.Close, stringResource(R.string.view_once_close), ::consumeAndDismiss)
        }
        Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.Bottom) {
            if (showReactions) StoryReactionsStrip(reactions, true, { emoji -> emojiUsage.increment(emoji); onSendReaction(emoji); showReactions = false; confirmSent() }, { })
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(reply, { reply = it }, placeholder = { Text(stringResource(R.string.view_once_reply_placeholder)) }, modifier = Modifier.weight(1f).clip(RoundedCornerShape(24.dp)))
                if (reply.isBlank()) {
                    ViewerCircleButton(Icons.Filled.SentimentSatisfied, stringResource(R.string.view_once_reactions), { showReactions = !showReactions })
                    ViewerCircleButton(Icons.Filled.CameraAlt, stringResource(R.string.view_once_camera_reply), { onOpenCameraReply(); consumeAndDismiss() })
                } else ViewerCircleButton(Icons.Filled.Send, stringResource(R.string.view_once_send_reply), { onSendReply(reply.trim()); reply = ""; confirmSent() })
            }
        }
        if (sentConfirmation) Text(stringResource(R.string.view_once_reply_sent), color = Color.White, modifier = Modifier.align(Alignment.Center).background(Color.Black.copy(.55f), RoundedCornerShape(24.dp)).padding(18.dp))
    }
}

@Composable private fun ViewOnceProgress(progress: Float, modifier: Modifier) = Box(modifier.height(3.dp).background(Color.White.copy(.15f), CircleShape)) { Box(Modifier.fillMaxWidth(progress.coerceIn(0f, 1f)).height(3.dp).background(Color(0xFFFFCC33), CircleShape)) }
@Composable private fun ViewerCircleButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, action: () -> Unit) = Box(Modifier.padding(start = 8.dp).size(40.dp).clip(CircleShape).background(Color.White.copy(.12f)).clickable(onClick = action), contentAlignment = Alignment.Center) { Icon(icon, description, tint = Color.White) }
