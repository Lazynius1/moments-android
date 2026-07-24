package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.EnhancedMessage
import java.util.Date
import kotlin.math.max

/** Port de `Views/Messaging/Components/ChatEphemeralMessageViews.swift`. */
enum class ChatEphemeralLayout(val width: Dp, val height: Dp, val cornerRadius: Dp, val iconPreset: AttachmentIconPreset) {
    COMPACT(76.dp, 118.dp, 14.dp, AttachmentIconPreset.STORY_EPHEMERAL),
    STANDARD(188.dp, 240.dp, 18.dp, AttachmentIconPreset.CHAT_EPHEMERAL_PLACEHOLDER),
}

object ChatEphemeralTimeFormatting {
    fun remainingSeconds(expirationDate: Date, now: Date = Date()): Long = max(0L, (expirationDate.time - now.time) / 1_000L)
}

@Composable
private fun ChatEphemeralTimeLabel(expirationDate: Date, now: Date = Date()): String {
    val remaining = ChatEphemeralTimeFormatting.remainingSeconds(expirationDate, now)
    val hours = remaining / 3_600
    val minutes = (remaining % 3_600) / 60
    return when {
        hours >= 24 -> stringResource(R.string.chat_ephemeral_days_short, hours / 24)
        hours > 0 -> stringResource(R.string.chat_ephemeral_hours_minutes_short, hours, minutes)
        minutes > 0 -> stringResource(R.string.chat_ephemeral_minutes_short, minutes)
        else -> stringResource(R.string.chat_ephemeral_less_than_minute)
    }
}

private val ephemeralAccent = Color(0xFFFFCC33)
private val ephemeralSecondary = Color(0xFFFF9500)
private val ephemeralGradient = Brush.linearGradient(listOf(Color(0xFF1C1C1E), Color(0xFF2A2418)))

@Composable
fun ChatEphemeralMessageContent(
    message: EnhancedMessage,
    layout: ChatEphemeralLayout,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    onOpenMedia: ((EnhancedMessage) -> Unit)? = null,
    onMarkViewed: ((EnhancedMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showContent by remember(message.id) { mutableStateOf(message.isViewed) }
    val valid = message.expirationDate?.after(Date()) ?: true
    val preview = message.thumbnailUrl ?: message.mediaUrl
    when {
        message.isDeleted || !valid -> ChatEphemeralExpiredCard(layout, modifier)
        !showContent && !message.isViewed -> ChatEphemeralTapCard(layout, preview, message.expirationDate, modifier) {
            showContent = true; onHydrateMedia?.invoke(message); onMarkViewed?.invoke(message)
        }
        !message.mediaUrl.isNullOrBlank() || !message.thumbnailUrl.isNullOrBlank() -> ChatEphemeralImageCard(layout, message.mediaUrl ?: message.thumbnailUrl.orEmpty(), message.expirationDate, modifier) { onOpenMedia?.invoke(message) }
        else -> ChatEphemeralResolvingCard(layout, modifier)
    }
}

@Composable
fun ChatEphemeralTapCard(layout: ChatEphemeralLayout, previewImageUrl: String?, expirationDate: Date?, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val shape = RoundedCornerShape(layout.cornerRadius)
    Box(modifier.size(layout.width, layout.height).clip(shape).clickable(onClick = onTap)) {
        if (previewImageUrl.isNullOrBlank()) Box(Modifier.fillMaxSize().background(ephemeralGradient)) else AsyncImage(previewImageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        Box(Modifier.fillMaxSize().background(Color.Black.copy(.42f)))
        Column(Modifier.fillMaxSize().padding(bottom = if (layout == ChatEphemeralLayout.COMPACT) 10.dp else 14.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
            AttachmentIconView(AttachmentIcon.EPHEMERAL, layout.iconPreset, ephemeralAccent.copy(.95f)); Spacer(Modifier.weight(1f))
            Text(stringResource(R.string.chat_tap_to_view), color = Color.White, fontSize = if (layout == ChatEphemeralLayout.COMPACT) 11.sp else 13.sp, fontWeight = FontWeight.SemiBold)
            Text(stringResource(R.string.chat_ephemeral_title), color = Color.White.copy(.78f), fontSize = if (layout == ChatEphemeralLayout.COMPACT) 10.sp else 11.sp)
            expirationDate?.takeIf { it.after(Date()) }?.let { Text(stringResource(R.string.stories_expires_in, ChatEphemeralTimeLabel(it)), color = ephemeralAccent.copy(.9f), fontSize = 10.sp) }
        }
    }
}

@Composable
fun ChatEphemeralImageCard(layout: ChatEphemeralLayout, imageUrl: String, expirationDate: Date?, modifier: Modifier = Modifier, onTap: () -> Unit) {
    val shape = RoundedCornerShape(layout.cornerRadius)
    Box(modifier.size(layout.width, layout.height).clip(shape).clickable(onClick = onTap)) {
        AsyncImage(imageUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        expirationDate?.takeIf { it.after(Date()) }?.let { Text(ChatEphemeralTimeLabel(it), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(.55f)).padding(horizontal = 8.dp, vertical = 4.dp)) }
    }
}

@Composable fun ChatEphemeralResolvingCard(layout: ChatEphemeralLayout, modifier: Modifier = Modifier) = Box(modifier.size(layout.width, layout.height).clip(RoundedCornerShape(layout.cornerRadius)).background(ephemeralGradient), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) { CircularProgressIndicator(color = ephemeralAccent); Text(stringResource(R.string.common_loading), color = Color.White.copy(.72f), fontSize = 11.sp) } }
@Composable fun ChatEphemeralExpiredCard(layout: ChatEphemeralLayout, modifier: Modifier = Modifier) = Box(modifier.size(layout.width, layout.height).clip(RoundedCornerShape(layout.cornerRadius)).background(Color.White.copy(.06f)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Default.HourglassBottom, null, tint = Color.White.copy(.45f), modifier = Modifier.size(if (layout == ChatEphemeralLayout.COMPACT) 18.dp else 22.dp)); Text(stringResource(R.string.stories_ephemeral_expired), color = Color.White.copy(.55f), fontSize = if (layout == ChatEphemeralLayout.COMPACT) 10.sp else 12.sp) } }
