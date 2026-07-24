@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.moments.android.views.messaging.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageStatus
import com.moments.android.models.MessageType
import com.moments.android.views.messaging.models.ChatLocationPayload
import com.moments.android.views.story.storyviewer.StoryReplyMessageBubble
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Port de `Views/Messaging/Components/ChatMessageBubbleViews.swift`.
 *
 * Las piezas de chrome, gesto de reply y texto enriquecido viven aquí hasta que
 * sus ficheros Swift homónimos se porten; las callbacks mantienen el contrato
 * del row y evitan que la vista dependa del estado de la pantalla.
 */
data class ChatMessageBubbleCallbacks(
    val onReply: () -> Unit = {},
    val onReaction: (String) -> Unit = {},
    val onAvatarTap: () -> Unit = {},
    val onReplyTap: ((String) -> Unit)? = null,
    val onMessageViewed: ((String) -> Unit)? = null,
    val onMomentNavigation: ((EnhancedMessage) -> Unit)? = null,
    val onStoryNavigation: ((EnhancedMessage) -> Unit)? = null,
    val onOpenMedia: (EnhancedMessage) -> Unit = {},
    val onStopLiveLocation: ((String) -> Unit)? = null,
    val onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    val onLongPress: (() -> Unit)? = null,
    val onViewOnceOpen: ((EnhancedMessage, Boolean) -> Unit)? = null,
)

@Composable
fun GlassmorphicMessageRow(
    message: EnhancedMessage,
    displayReactions: Map<String, List<String>>? = null,
    isCurrentUser: Boolean,
    showAvatar: Boolean,
    groupPosition: ChatMessageGroupPosition = ChatMessageGroupPosition.SINGLE,
    otherUserId: String? = null,
    isOtherParticipantUnavailable: Boolean = false,
    otherParticipantName: String,
    repliedMessage: EnhancedMessage? = null,
    isMenuSelected: Boolean = false,
    isBubbleFlashing: Boolean = false,
    progress: Double? = null,
    downloadProgress: Double? = null,
    isDownloadingMedia: Boolean = false,
    showSeenLabel: Boolean = false,
    callbacks: ChatMessageBubbleCallbacks = ChatMessageBubbleCallbacks(),
    modifier: Modifier = Modifier,
) {
    var revealTimestamp by remember(message.id) { mutableStateOf(false) }
    val horizontalReplyOffset by animateFloatAsState(if (revealTimestamp) -67f else 0f, tween(160), label = "messageTimestampOffset")
    val tail = groupPosition == ChatMessageGroupPosition.LAST || groupPosition == ChatMessageGroupPosition.SINGLE
    val head = groupPosition == ChatMessageGroupPosition.FIRST || groupPosition == ChatMessageGroupPosition.SINGLE

    Row(
        modifier.fillMaxWidth().padding(start = 8.dp, top = if (head) 5.dp else 1.dp, end = 8.dp, bottom = if (tail) 8.dp else 1.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier.weight(1f).offset { IntOffset(horizontalReplyOffset.roundToInt(), 0) }
                .pointerInput(message.id, isCurrentUser) {
                    var total = 0f
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { _, drag -> total += drag },
                        onDragEnd = {
                            if (!isCurrentUser && total < -40f) revealTimestamp = !revealTimestamp
                            if (isCurrentUser && total < -72f) callbacks.onReply()
                            total = 0f
                        },
                    )
                },
            verticalAlignment = Alignment.Bottom,
        ) {
            if (!isCurrentUser) {
                ChatIncomingAvatarGutter(showAvatar, otherUserId, isOtherParticipantUnavailable, callbacks.onAvatarTap)
            }
            Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
                repliedMessage?.let { ChatReplyQuote(it, isCurrentUser, otherParticipantName, callbacks.onReplyTap) }
                GlassmorphicMessageBubble(
                    message = message,
                    reactions = if (isMenuSelected) null else displayReactions ?: message.reactions,
                    isCurrentUser = isCurrentUser,
                    groupPosition = groupPosition,
                    otherParticipantName = otherParticipantName,
                    progress = progress,
                    downloadProgress = downloadProgress,
                    isDownloadingMedia = isDownloadingMedia,
                    callbacks = callbacks,
                    isFlashing = isBubbleFlashing,
                )
            }
        }
        AnimatedVisibility(!isCurrentUser && revealTimestamp) {
            ChatMessageTimestamp(message, showSeenLabel, Modifier.width(67.dp).padding(start = 12.dp))
        }
    }
}

@Composable
private fun ChatReplyQuote(message: EnhancedMessage, outgoing: Boolean, otherName: String, onTap: ((String) -> Unit)?) {
    val title = if (outgoing) otherName else stringResource(R.string.chat_input_placeholder)
    Column(
        Modifier.width(208.dp).padding(bottom = 4.dp).clip(RoundedCornerShape(10.dp))
            .background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.09f) else Color.Black.copy(.06f))
            .combinedClickable(onClick = { onTap?.invoke(message.id) }).padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(title, fontSize = 11.sp, color = Color(0xFF3F6F8F), fontWeight = FontWeight.SemiBold, maxLines = 1)
        Text(message.content ?: message.fileName ?: stringResource(R.string.chat_message_unsupported), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ChatMessageTimestamp(message: EnhancedMessage, showSeen: Boolean, modifier: Modifier = Modifier) {
    val pattern = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    Column(modifier, horizontalAlignment = Alignment.Start) {
        Text(pattern.format(message.timestamp), color = Color.Gray, fontSize = 10.sp)
        if (showSeen && message.status == MessageStatus.READ) Icon(Icons.Default.Done, null, tint = Color.Gray, modifier = Modifier.size(11.dp))
    }
}

@Composable
fun DeletedMessageBubble(message: EnhancedMessage, isCurrentUser: Boolean, modifier: Modifier = Modifier) {
    val colors = com.moments.android.views.feed.AdaptiveColors(androidx.compose.foundation.isSystemInDarkTheme())
    val (icon, label) = when (message.type) {
        MessageType.AUDIO -> Icons.Default.AudioFile to R.string.chat_deleted_audio
        MessageType.IMAGE -> Icons.Default.Image to R.string.chat_deleted_image
        MessageType.VIDEO -> Icons.Default.VideoFile to R.string.chat_deleted_video
        MessageType.FILE -> Icons.Default.Description to R.string.chat_deleted_file
        MessageType.LOCATION -> Icons.Default.LocationOn to R.string.chat_deleted_location
        MessageType.EPHEMERAL -> Icons.Default.Article to R.string.chat_deleted_ephemeral
        else -> Icons.Default.Article to R.string.chat_deleted_text
    }
    Row(
        modifier.clip(RoundedCornerShape(20.dp)).background(colors.messageBubbleBackground).padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = colors.messageTextColor.copy(.5f), modifier = Modifier.size(16.dp))
        Text(stringResource(label), color = colors.messageTextColor.copy(.6f), fontSize = 14.sp, fontStyle = FontStyle.Italic)
    }
}

@Composable
fun GlassmorphicMessageBubble(
    message: EnhancedMessage,
    reactions: Map<String, List<String>>?,
    isCurrentUser: Boolean,
    groupPosition: ChatMessageGroupPosition = ChatMessageGroupPosition.SINGLE,
    otherParticipantName: String,
    progress: Double?,
    downloadProgress: Double?,
    isDownloadingMedia: Boolean,
    callbacks: ChatMessageBubbleCallbacks,
    isFlashing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = com.moments.android.views.feed.AdaptiveColors(androidx.compose.foundation.isSystemInDarkTheme())
    if (message.isDeleted) {
        DeletedMessageBubble(message, isCurrentUser, modifier)
        return
    }
    val shape = chatBubbleShape(isCurrentUser, groupPosition)
    Box(modifier) {
        ChatMessageSwipeSurface(isCurrentUser, callbacks.onReply, callbacks.onLongPress) {
            when (message.type) {
                MessageType.TEXT -> {
                    if (message.storyReplyData != null) {
                        StoryReplyMessageBubble(message, isCurrentUser, message.storyReplyData)
                    } else {
                        Column(Modifier.clip(shape).background(if (isCurrentUser) colors.userAccentColor else colors.messageBubbleBackground).padding(horizontal = 13.dp, vertical = 9.dp)) {
                            if (message.content != null) {
                                if (message.content.startsWith("↪")) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Forward, null, tint = colors.messageTextColor.copy(.55f), modifier = Modifier.size(13.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text(stringResource(R.string.chat_forwarded), color = colors.messageTextColor.copy(.55f), fontSize = 11.sp)
                                    }
                                }
                                ChatLinkedText(message.content, colors.messageTextColor)
                                ChatLinkOpener.firstUrl(message.content)?.let { LinkPreviewCard(it, isCurrentUser, Modifier.padding(top = 8.dp)) }
                            }
                        }
                    }
                }
                MessageType.IMAGE -> MediaBubble(message, false, isCurrentUser, groupPosition, progress, downloadProgress, isDownloadingMedia, callbacks)
                MessageType.VIDEO -> MediaBubble(message, true, isCurrentUser, groupPosition, progress, downloadProgress, isDownloadingMedia, callbacks)
                MessageType.AUDIO -> ChatAudioMessageContent(message, isCurrentUser, progress, callbacks.onHydrateMedia)
                MessageType.EPHEMERAL, MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO -> {
                    ChatEphemeralMessageContent(message = message, layout = ChatEphemeralLayout.STANDARD, onHydrateMedia = callbacks.onHydrateMedia, onOpenMedia = callbacks.onOpenMedia, onMarkViewed = { viewed ->
                        callbacks.onMessageViewed?.invoke(viewed.id)
                        if (message.type.isViewOnce) callbacks.onViewOnceOpen?.invoke(message, message.isViewed)
                    })
                }
                MessageType.GIF -> ChatGifMessageBubble(message, progress)
                MessageType.STICKER -> {
                    ChatStickerMessageBubble(message, progress)
                    LaunchedEffect(message.id) { callbacks.onHydrateMedia?.invoke(message) }
                }
                MessageType.LOCATION -> ChatLocationPayload.decode(message.content.orEmpty())?.let { payload ->
                    ChatLocationMessageBubble(payload, isCurrentUser, onStopLive = { callbacks.onStopLiveLocation?.invoke(message.id) })
                } ?: ChatUnsupportedBubble(colors)
                MessageType.SHARED_MOMENT -> ChatSharedContent(R.string.chat_shared_moment, message, callbacks.onMomentNavigation)
                MessageType.SHARED_STORY -> ChatSharedContent(R.string.chat_shared_story, message, callbacks.onStoryNavigation)
                else -> ChatUnsupportedBubble(colors)
            }
        }
        if (!reactions.isNullOrEmpty()) {
            ChatReactionBadges(reactions, isCurrentUser, callbacks.onReaction, Modifier.align(if (isCurrentUser) Alignment.BottomStart else Alignment.BottomEnd).offset(y = 12.dp))
        }
        if (isFlashing) Box(Modifier.matchParentSize().clip(shape).background(Color.White.copy(.24f)))
    }
}

@Composable
private fun ChatMessageSwipeSurface(outgoing: Boolean, onReply: () -> Unit, onLongPress: (() -> Unit)?, content: @Composable () -> Unit) {
    var displacement by remember { mutableFloatStateOf(0f) }
    Box(
        Modifier.offset { IntOffset(displacement.roundToInt(), 0) }.combinedClickable(onClick = {}, onLongClick = onLongPress)
            .pointerInput(outgoing) {
                detectHorizontalDragGestures(
                    onHorizontalDrag = { _, delta -> displacement = (displacement + delta).coerceIn(-88f, 88f) },
                    onDragEnd = { if (abs(displacement) >= 72f) onReply(); displacement = 0f },
                    onDragCancel = { displacement = 0f },
                )
            },
    ) { content() }
}

@Composable
private fun MediaBubble(message: EnhancedMessage, video: Boolean, outgoing: Boolean, position: ChatMessageGroupPosition, progress: Double?, downloadProgress: Double?, downloading: Boolean, callbacks: ChatMessageBubbleCallbacks) {
    val mediaModifier = Modifier.size(208.dp, 272.dp).clip(chatBubbleShape(outgoing, position))
    if (video) {
        GlassmorphicVideoMessage(message.mediaUrl, message.thumbnailUrl, message.status == MessageStatus.SENDING, isDownloadingMedia = downloading, downloadProgress = downloadProgress, progress = progress, onTap = { callbacks.onOpenMedia(message) }, modifier = mediaModifier)
    } else {
        GlassmorphicImageMessage(message.mediaUrl, message.thumbnailUrl, message.status == MessageStatus.SENDING, isDownloadingMedia = downloading, downloadProgress = downloadProgress, progress = progress, onTap = { callbacks.onOpenMedia(message) }, modifier = mediaModifier)
    }
    LaunchedEffect(message.id) { callbacks.onHydrateMedia?.invoke(message) }
}

@Composable
private fun ChatAudioMessageContent(message: EnhancedMessage, outgoing: Boolean, sendingProgress: Double?, onHydrate: ((EnhancedMessage) -> Unit)?) {
    LaunchedEffect(message.id) { onHydrate?.invoke(message) }
    GlassmorphicAudioMessage(message.id, message.mediaUrl, message.duration ?: 0.0, message.audioWaveform, outgoing, message.status == MessageStatus.SENDING, sendingProgress)
}

@Composable
private fun ChatSharedContent(label: Int, message: EnhancedMessage, onClick: ((EnhancedMessage) -> Unit)?) {
    Row(
        Modifier.width(220.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(.12f)).combinedClickable(onClick = { onClick?.invoke(message) }).padding(13.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Article, null, tint = Color.White.copy(.8f))
        Text(stringResource(label), color = Color.White, fontSize = 14.sp)
    }
}

@Composable
private fun ChatUnsupportedBubble(colors: com.moments.android.views.feed.AdaptiveColors) {
    Text(stringResource(R.string.chat_message_unsupported), color = colors.messageTextColor.copy(.6f), modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(colors.messageBubbleBackground).padding(horizontal = 16.dp, vertical = 10.dp))
}

@Composable
private fun ChatReactionBadges(reactions: Map<String, List<String>>, outgoing: Boolean, onReaction: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.clip(RoundedCornerShape(50)).background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF25262A) else Color.White).padding(horizontal = 5.dp, vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        reactions.keys.sorted().take(4).forEach { emoji -> Text(emoji, fontSize = 13.sp, modifier = Modifier.combinedClickable(onClick = { onReaction(emoji) })) }
    }
}

private fun chatBubbleShape(outgoing: Boolean, position: ChatMessageGroupPosition): RoundedCornerShape {
    val joined = 6.dp
    val radius = 18.dp
    return when {
        outgoing && position == ChatMessageGroupPosition.FIRST -> RoundedCornerShape(radius, radius, joined, radius)
        outgoing && position == ChatMessageGroupPosition.MIDDLE -> RoundedCornerShape(radius, joined, joined, radius)
        outgoing && position == ChatMessageGroupPosition.LAST -> RoundedCornerShape(radius, joined, radius, radius)
        !outgoing && position == ChatMessageGroupPosition.FIRST -> RoundedCornerShape(radius, radius, radius, joined)
        !outgoing && position == ChatMessageGroupPosition.MIDDLE -> RoundedCornerShape(joined, radius, radius, joined)
        !outgoing && position == ChatMessageGroupPosition.LAST -> RoundedCornerShape(joined, radius, radius, radius)
        else -> RoundedCornerShape(radius)
    }
}

object ChatLinkOpener {
    private val expression = Regex("(?i)\\b((?:https?://|www\\.)[^\\s<]+)")
    fun firstUrl(text: String): String? = expression.find(text.replace("||", ""))?.value?.let { if (it.startsWith("www.")) "https://$it" else it }
    fun containsLink(text: String): Boolean = firstUrl(text) != null
    fun annotated(text: String, color: Color): AnnotatedString = buildAnnotatedString {
        append(text)
        expression.findAll(text.replace("||", "")).forEach { match ->
            val url = if (match.value.startsWith("www.")) "https://${match.value}" else match.value
            addStyle(SpanStyle(color = color), match.range.first, match.range.last + 1)
            addStringAnnotation("url", url, match.range.first, match.range.last + 1)
        }
    }
}

@Composable
private fun ChatLinkedText(text: String, color: Color) {
    val uriHandler = LocalUriHandler.current
    val annotated = remember(text, color) { ChatLinkOpener.annotated(text, color.copy(.88f)) }
    androidx.compose.foundation.text.ClickableText(annotated, style = androidx.compose.ui.text.TextStyle(color = color, fontSize = 15.sp), onClick = { offset -> annotated.getStringAnnotations("url", offset, offset).firstOrNull()?.let { uriHandler.openUri(it.item) } })
}

private data class LinkPreviewMetadata(val title: String?, val imageUrl: String?)

private object LinkMetadataCache {
    private val entries = ConcurrentHashMap<String, LinkPreviewMetadata?>()
    suspend fun fetch(url: String): LinkPreviewMetadata? = entries[url] ?: withContext(Dispatchers.IO) {
        runCatching {
            val connection: URLConnection = URL(url).openConnection().apply { connectTimeout = 5_000; readTimeout = 5_000; setRequestProperty("User-Agent", "Moments") }
            val html = connection.getInputStream().bufferedReader().use { it.readText().take(512_000) }
            val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.replace(Regex("<[^>]+>"), "")?.trim()
            val image = Regex("(?is)<meta[^>]+(?:property|name)=[\"']og:image[\"'][^>]+content=[\"']([^\"']+)").find(html)?.groupValues?.getOrNull(1)
            LinkPreviewMetadata(title, image)
        }.getOrNull()
    }.also { entries[url] = it }
}

@Composable
fun LinkPreviewCard(url: String, outgoing: Boolean, modifier: Modifier = Modifier) {
    var metadata by remember(url) { mutableStateOf<LinkPreviewMetadata?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(url) { metadata = LinkMetadataCache.fetch(url); loading = false }
    Column(
        modifier.width(220.dp).clip(RoundedCornerShape(13.dp)).background(if (outgoing) Color.White.copy(.16f) else Color.White.copy(.10f)).combinedClickable(onClick = { uriHandler.openUri(url) }),
    ) {
        metadata?.imageUrl?.let { AsyncImage(it, null, Modifier.fillMaxWidth().height(120.dp), contentScale = ContentScale.Crop) }
        Column(Modifier.padding(9.dp)) {
            Text(metadata?.title ?: Uri.parse(url).host.orEmpty(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (loading) Text(stringResource(R.string.chat_link_preview_loading), color = Color.White.copy(.7f), fontSize = 10.sp)
            else Text(Uri.parse(url).host.orEmpty(), color = Color.White.copy(.75f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
