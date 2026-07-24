package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.EnhancedMessage
import java.util.Date

/** Métricas de `StoryReplyPreviewMetrics`. */
object StoryReplyPreviewMetrics {
    val width = 76.dp
    val height = 118.dp
    val cornerRadius = 14.dp
}

private val storyReplyRing = Brush.linearGradient(listOf(Color(0xFF0A84FF), Color(0xFFAF52DE), Color(0xFFFF2D55)))

/** Port de `StoryReplyThumbnailView`. */
@Composable
fun StoryReplyThumbnailView(storyReplyData: Map<String, String>, modifier: Modifier = Modifier) {
    val mediaUrl = storyReplyData["storyMediaUrl"]
    Box(modifier.size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height).clip(RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius)).background(Color(0xFF202124))) {
        if (!mediaUrl.isNullOrBlank()) AsyncImage(mediaUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else Icon(Icons.Default.Image, null, tint = Color.White.copy(alpha = .65f), modifier = Modifier.align(Alignment.Center))
        if (storyReplyData["storyMediaType"] == "video") Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.align(Alignment.Center))
    }
}

/** Port de `StoryReplyUnavailableThumbnail`. */
@Composable
fun StoryReplyUnavailableThumbnail(reason: String, storyReplyData: Map<String, String>, modifier: Modifier = Modifier) {
    val preview = storyReplyData["storyPreviewUrl"] ?: storyReplyData["storyMediaUrl"]
    Box(modifier.size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height).clip(RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius)).background(Color.Black)) {
        if (!preview.isNullOrBlank()) AsyncImage(preview, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .35f)
        Column(Modifier.align(Alignment.Center).padding(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(if (reason == "expired") Icons.Default.Schedule else Icons.Default.Lock, null, tint = Color.White)
            Text(if (reason == "expired") stringResource(R.string.story_reply_expired) else stringResource(R.string.story_reply_unavailable), color = Color.White, fontSize = 9.sp, textAlign = TextAlign.Center)
        }
    }
}

/** Port de `StoryReplyEphemeralTapCard` y formato temporal. */
@Composable
fun StoryReplyEphemeralTapCard(previewImageUrl: String?, expirationDate: Date?, onTap: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height).clip(RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius)).background(Color(0xFF25262A)).clickable(onClick = onTap)) {
        if (!previewImageUrl.isNullOrBlank()) AsyncImage(previewImageUrl, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop, alpha = .55f)
        Column(Modifier.align(Alignment.BottomCenter).padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.story_reply_tap_to_view), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            expirationDate?.takeIf { it.after(Date()) }?.let { Text(stringResource(R.string.story_reply_expires_in, storyReplyTimeLeftLabel(it.time - Date().time)), color = Color.White.copy(alpha = .75f), fontSize = 10.sp) }
        }
    }
}

/** Port de `StoryReplyEphemeralExpiredCard`. */
@Composable
fun StoryReplyEphemeralExpiredCard(modifier: Modifier = Modifier) {
    Box(modifier.size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height).clip(RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius)).background(Color(0xFF25262A)), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Schedule, null, tint = Color.White)
            Text(stringResource(R.string.story_reply_expired), color = Color.White, textAlign = TextAlign.Center, fontSize = 11.sp)
        }
    }
}

@Composable
private fun storyReplyTimeLeftLabel(ms: Long): String {
    val minutes = (ms.coerceAtLeast(0) / 60_000).toInt()
    return if (minutes >= 60) {
        stringResource(R.string.chat_ephemeral_hours_minutes_short, minutes / 60, minutes % 60)
    } else {
        stringResource(R.string.chat_ephemeral_minutes_short, minutes)
    }
}

/**
 * Port de `StoryReplyMessageBubble`.
 * `storyReplyData` se recibe explícito mientras el contrato `EnhancedMessage`
 * Android termina de incorporar el campo persistido que iOS ya tiene.
 */
@Composable
fun StoryReplyMessageBubble(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    storyReplyData: Map<String, String>? = null,
    accessAllowed: Boolean = true,
    denialReason: String = "expired",
    modifier: Modifier = Modifier,
) {
    Column(modifier.width(280.dp)) {
        if (storyReplyData != null) {
            Text(stringResource(if (isCurrentUser) R.string.story_reply_you_replied else R.string.story_reply_replied_to_your_story), color = Color.White.copy(alpha = .7f), fontSize = 12.sp)
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.width(3.dp).size(width = 3.dp, height = StoryReplyPreviewMetrics.height).background(Color.White.copy(alpha = .35f)))
                if (accessAllowed) StoryReplyThumbnailView(storyReplyData, Modifier.padding(horizontal = 10.dp))
                else StoryReplyUnavailableThumbnail(denialReason, storyReplyData, Modifier.padding(horizontal = 10.dp))
            }
        }
        val content = message.content?.removePrefix("💬 ").orEmpty()
        if (content.isNotBlank()) Text(content, color = Color.White, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
    }
}
