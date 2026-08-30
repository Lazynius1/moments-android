@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.moments.android.views.messaging.components

import android.net.Uri
import java.net.URL
import java.net.URLConnection
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Forward
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOff
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.rawPadding
import com.moments.android.views.feed.sharing.SharedMomentMessageBubble
import com.moments.android.views.feed.sharing.SharedProfileMessageBubble
import com.moments.android.views.feed.sharing.SharedStoryMessageBubble
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.models.ChatLocationPayload
import com.moments.android.views.story.storyviewer.StoryReplyMessageBubble
import kotlin.math.roundToInt

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
    val onLongPress: ((ChatMessageLiftSnapshot) -> Unit)? = null,
    val onViewOnceOpen: ((EnhancedMessage, Boolean) -> Unit)? = null,
    val onOpenLocation: ((EnhancedMessage) -> Unit)? = null,
    val onRetryFailed: ((EnhancedMessage) -> Unit)? = null,
    val onMentionTap: (String) -> Unit = {},
)

private fun openMessageBody(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    callbacks: ChatMessageBubbleCallbacks,
) {
    ChatMessageBodyOpen.open(
        message = message,
        isCurrentUser = isCurrentUser,
        currentUserId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
        onOpenMedia = callbacks.onOpenMedia,
        onMomentNavigation = callbacks.onMomentNavigation,
        onStoryNavigation = callbacks.onStoryNavigation,
        onViewOnceOpen = callbacks.onViewOnceOpen,
        onOpenLocation = callbacks.onOpenLocation,
        onHydrateMedia = callbacks.onHydrateMedia,
        onMessageViewed = callbacks.onMessageViewed,
    )
}

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
    isStarred: Boolean = false,
    timestampRevealState: ChatTimestampRevealState = remember { ChatTimestampRevealState() },
    callbacks: ChatMessageBubbleCallbacks = ChatMessageBubbleCallbacks(),
    onDoubleTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val swipeState = rememberChatReplySwipeState()
    val revealOffset = timestampRevealState.offset
    val tail = groupPosition == ChatMessageGroupPosition.LAST || groupPosition == ChatMessageGroupPosition.SINGLE
    val head = groupPosition == ChatMessageGroupPosition.FIRST || groupPosition == ChatMessageGroupPosition.SINGLE
    val resolvedReactions = if (isMenuSelected) null else displayReactions ?: message.reactions
    val hasReactions = !resolvedReactions.isNullOrEmpty()
    val reactionSpacing = if (hasReactions || isStarred) 6.dp else 4.dp
    val bottomPad = run {
        val base = if (tail) 5.dp else 1.dp
        if (hasReactions || isStarred) {
            base + if (tail) 8.dp else 4.dp
        } else {
            base
        }
    }
    val cornerRadius = ChatBubbleAnchorMetrics.cornerRadiusFor(message)
    val canRetry = isCurrentUser &&
        message.status == MessageStatus.FAILED &&
        callbacks.onRetryFailed != null
    val childHandlesTap = message.type == MessageType.TEXT &&
        chatTextSegments(message.content.orEmpty()).any { it.isSpoiler }
    var revealSpoilers by remember(message.id) { mutableStateOf(false) }
    val timestampAlpha = ((-revealOffset) / 40f).coerceIn(0f, 1f)

    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = if (head) 5.dp else 1.dp, end = 8.dp, bottom = bottomPad)
            .offset { IntOffset(revealOffset.roundToInt(), 0) },
        verticalAlignment = Alignment.Bottom,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(IntrinsicSize.Max)
                .rawPadding(end = (-67).dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            if (isCurrentUser) {
                // Hueco vacío a la izquierda de la burbuja propia — solo ahí el swipe de hora.
                ChatTimestampRevealGutter(
                    state = timestampRevealState,
                    isEnabled = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
            if (!isCurrentUser) {
                ChatIncomingAvatarGutter(showAvatar, otherUserId, isOtherParticipantUnavailable, callbacks.onAvatarTap)
            }
            if (canRetry) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = stringResource(R.string.messaging_retry),
                    tint = Color.Red,
                    modifier = Modifier
                        .padding(end = 2.dp)
                        .size(32.dp)
                        .combinedClickable(onClick = {
                            com.moments.android.utilities.HapticManager.shared.lightImpact()
                            callbacks.onRetryFailed?.invoke(message)
                        })
                        .padding(6.dp),
                )
            }
            Column(
                modifier = Modifier.wrapContentWidth(
                    if (isCurrentUser) Alignment.End else Alignment.Start,
                ),
                horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(reactionSpacing),
            ) {
                repliedMessage?.let {
                    StackedReplyQuote(it, isCurrentUser, otherParticipantName, callbacks.onReplyTap)
                }
                ChatBubbleReplySwipeContainer(
                    state = swipeState,
                    isOutgoing = isCurrentUser,
                    cornerRadius = cornerRadius,
                    onReply = callbacks.onReply,
                    modifier = if (onDoubleTap != null) {
                        Modifier.pointerInput(message.id) {
                            detectTapGestures(onDoubleTap = { onDoubleTap() })
                        }
                    } else {
                        Modifier
                    },
                ) {
                    ChatMessageBubbleChrome(
                        isMenuSelected = isMenuSelected,
                        isOutgoing = isCurrentUser,
                        cornerRadius = cornerRadius,
                        isFlashing = isBubbleFlashing,
                        onTap = when {
                            childHandlesTap -> {
                                { revealSpoilers = !revealSpoilers }
                            }
                            ChatMessageBodyOpen.isOpenable(
                                message,
                                isCurrentUser,
                                FirebaseAuth.getInstance().currentUser?.uid.orEmpty(),
                            ) -> {
                                { openMessageBody(message, isCurrentUser, callbacks) }
                            }
                            else -> null
                        },
                        onLongPress = callbacks.onLongPress,
                    ) {
                        val bubble: @Composable () -> Unit = {
                            GlassmorphicMessageBubble(
                                message = message,
                                reactions = resolvedReactions,
                                isCurrentUser = isCurrentUser,
                                groupPosition = groupPosition,
                                otherParticipantId = otherUserId,
                                otherParticipantName = otherParticipantName,
                                progress = progress,
                                downloadProgress = downloadProgress,
                                isDownloadingMedia = isDownloadingMedia,
                                isStarred = isStarred,
                                callbacks = callbacks,
                                revealSpoilers = revealSpoilers,
                                spoilerTapOnChrome = childHandlesTap,
                            )
                        }
                        if (message.isVanishModeMessage) {
                            com.moments.android.views.shared.ScreenshotProtectedView(
                                isProtected = true,
                                cornerRadius = cornerRadius.dp,
                                mode = com.moments.android.views.shared.ScreenshotProtectionMode.WindowFlag,
                            ) {
                                bubble()
                            }
                        } else {
                            bubble()
                        }
                    }
                }
            }
            if (!isCurrentUser) {
                ChatTimestampRevealGutter(
                    state = timestampRevealState,
                    isEnabled = true,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        }
        MessageTimestamp(
            message = message,
            isCurrentUser = isCurrentUser,
            showSeenLabel = showSeenLabel,
            modifier = Modifier
                .width(55.dp)
                .padding(start = 12.dp)
                .graphicsLayer { alpha = timestampAlpha },
        )
    }
}

@Composable
fun DeletedMessageBubble(message: EnhancedMessage, isCurrentUser: Boolean, modifier: Modifier = Modifier) {
    val colors = com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme())
    // ≡ getDeletedIcon / getDeletedText
    val (icon, label) = when (message.type) {
        MessageType.AUDIO -> Icons.Default.MicOff to R.string.chat_deleted_audio
        MessageType.IMAGE -> Icons.Default.Image to R.string.chat_deleted_image
        MessageType.VIDEO -> Icons.Default.VideocamOff to R.string.chat_deleted_video
        MessageType.FILE -> Icons.Default.Description to R.string.chat_deleted_file
        MessageType.LOCATION -> Icons.Default.LocationOff to R.string.chat_deleted_location
        MessageType.EPHEMERAL -> Icons.Default.Article to R.string.chat_deleted_ephemeral
        MessageType.TEXT -> Icons.Default.Article to R.string.chat_deleted_text
        else -> Icons.Default.Delete to R.string.chat_deleted_text
    }
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(colors.messageBubbleBackground.copy(alpha = 0.5f))
            .border(0.5.dp, colors.messageBubbleStroke, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
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
    otherParticipantId: String? = null,
    otherParticipantName: String,
    progress: Double?,
    downloadProgress: Double?,
    isDownloadingMedia: Boolean,
    isStarred: Boolean = false,
    callbacks: ChatMessageBubbleCallbacks,
    revealSpoilers: Boolean = false,
    spoilerTapOnChrome: Boolean = false,
    @Suppress("UNUSED_PARAMETER") isFlashing: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val colors = com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme())
    if (message.isDeleted) {
        DeletedMessageBubble(message, isCurrentUser, modifier)
        return
    }
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val starred = isStarred || message.isStarred(currentUserId)

    Box(modifier) {
        when (message.type) {
            MessageType.TEXT -> {
                // iOS: texto NO usa attachBubbleBadges — ChatTextBubbleView lleva overlay propio
                if (message.storyReplyData != null) {
                    AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                        StoryReplyMessageBubble(
                            message = message,
                            isCurrentUser = isCurrentUser,
                            otherParticipantId = otherParticipantId,
                            onHydrateMedia = callbacks.onHydrateMedia,
                            onOpenMedia = callbacks.onOpenMedia,
                        )
                    }
                } else {
                    val content = message.content.orEmpty()
                    Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
                        if (message.isForwarded == true) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Icon(
                                    Icons.Default.Forward,
                                    null,
                                    tint = colors.messageTextColor.copy(.55f),
                                    modifier = Modifier.size(10.dp),
                                )
                                Text(
                                    stringResource(R.string.chat_forwarded),
                                    color = colors.messageTextColor.copy(.55f),
                                    fontSize = 11.sp,
                                )
                            }
                        }
                        ChatTextBubbleView(
                            text = content,
                            isOutgoing = isCurrentUser,
                            messageId = message.id,
                            groupPosition = groupPosition,
                            reactions = reactions,
                            isStarred = starred,
                            repliedMessage = null,
                            otherParticipantName = otherParticipantName,
                            onReaction = callbacks.onReaction,
                            onMentionTap = callbacks.onMentionTap,
                            revealSpoilers = revealSpoilers,
                            spoilerTapOnChrome = spoilerTapOnChrome,
                        )
                    }
                }
            }
            MessageType.IMAGE -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                MediaBubble(message, false, isCurrentUser, groupPosition, progress, downloadProgress, isDownloadingMedia, callbacks)
            }
            MessageType.VIDEO -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                MediaBubble(message, true, isCurrentUser, groupPosition, progress, downloadProgress, isDownloadingMedia, callbacks)
            }
            MessageType.AUDIO -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                ChatAudioMessageContent(message, isCurrentUser, progress, callbacks.onHydrateMedia, groupPosition)
            }
            MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO ->
                AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                    ViewOnceMessageBubble(
                        message = message,
                        isCurrentUser = isCurrentUser,
                        otherParticipantName = otherParticipantName,
                        progress = progress,
                        currentUserId = currentUserId,
                    )
                }
            MessageType.EPHEMERAL -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                if (message.storyReplyData != null) {
                    StoryReplyMessageBubble(
                        message = message,
                        isCurrentUser = isCurrentUser,
                        otherParticipantId = otherParticipantId,
                        onHydrateMedia = callbacks.onHydrateMedia,
                        onOpenMedia = callbacks.onOpenMedia,
                    )
                } else {
                    ChatEphemeralMessageContent(
                        message = message,
                        layout = ChatEphemeralLayout.STANDARD,
                        onHydrateMedia = callbacks.onHydrateMedia,
                        onOpenMedia = callbacks.onOpenMedia,
                        onMarkViewed = { viewed -> callbacks.onMessageViewed?.invoke(viewed.id) },
                    )
                }
            }
            MessageType.GIF -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                ChatGifMessageBubble(message, progress)
                LaunchedEffect(message.id) { callbacks.onHydrateMedia?.invoke(message) }
            }
            MessageType.STICKER -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                ChatStickerMessageBubble(
                    message = message,
                    progress = progress,
                    isSending = message.status == MessageStatus.SENDING,
                )
                LaunchedEffect(message.id) { callbacks.onHydrateMedia?.invoke(message) }
            }
            MessageType.LOCATION -> {
                val payload = message.latitude?.let { lat ->
                    message.longitude?.let { lng ->
                        ChatLocationPayload(
                            lat = lat,
                            lng = lng,
                            name = message.locationName,
                            address = message.locationAddress,
                        )
                    }
                } ?: ChatLocationPayload.decode(message.content.orEmpty())
                AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                    payload?.let {
                        ChatLocationMessageBubble(
                            payload = it,
                            isCurrentUser = isCurrentUser,
                            isLive = message.isLiveLocationMessage,
                            isLiveActive = message.isLiveLocationActive,
                            expiresAt = message.liveLocationExpiresAt,
                            senderId = message.senderId,
                            onStopLive = { callbacks.onStopLiveLocation?.invoke(message.id) },
                        )
                    } ?: ChatUnsupportedBubble(colors)
                }
            }
            MessageType.SHARED_MOMENT -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                SharedMomentMessageBubble(
                    message = message,
                    isCurrentUser = isCurrentUser,
                    onTap = { callbacks.onMomentNavigation?.invoke(message) },
                )
            }
            MessageType.SHARED_STORY -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                SharedStoryMessageBubble(
                    message = message,
                    isCurrentUser = isCurrentUser,
                    onTap = { callbacks.onStoryNavigation?.invoke(message) },
                )
            }
            MessageType.SHARED_PROFILE -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                SharedProfileMessageBubble(
                    message = message,
                    isCurrentUser = isCurrentUser,
                )
            }
            else -> AttachBubbleBadges(isCurrentUser, reactions, starred, callbacks.onReaction) {
                ChatUnsupportedBubble(colors)
            }
        }
    }
}

/** ≡ iOS `attachBubbleBadges` / `messageReactionOverlay`. */
@Composable
private fun AttachBubbleBadges(
    isOutgoing: Boolean,
    reactions: Map<String, List<String>>?,
    isStarred: Boolean,
    onReaction: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    MessageReactionOverlayBox(
        isOutgoing = isOutgoing,
        reactions = reactions,
        isStarred = isStarred,
        compact = false,
        onTap = onReaction,
        content = content,
    )
}

@Composable
private fun MediaBubble(message: EnhancedMessage, video: Boolean, outgoing: Boolean, position: ChatMessageGroupPosition, progress: Double?, downloadProgress: Double?, downloading: Boolean, callbacks: ChatMessageBubbleCallbacks) {
    val mediaModifier = Modifier.size(208.dp, 272.dp).clip(chatBubbleShape(outgoing, position))
    if (video) {
        GlassmorphicVideoMessage(
            videoUrl = message.mediaUrl,
            thumbnailUrl = message.thumbnailUrl,
            isSending = message.status == MessageStatus.SENDING,
            isResolvingMedia = (message.isMediaPendingResolution || message.needsVideoThumbnailForDisplay) &&
                !message.isMediaAwaitingManualDownload &&
                !downloading,
            isAwaitingManualDownload = message.isMediaAwaitingManualDownload && !downloading,
            isDownloadingMedia = downloading,
            downloadProgress = downloadProgress,
            downloadSizeLabel = message.formattedDownloadSize,
            progress = progress,
            modifier = mediaModifier,
        )
    } else {
        GlassmorphicImageMessage(
            imageUrl = message.mediaUrl,
            previewThumbnailUrl = message.previewThumbnailURLForDisplay ?: message.thumbnailUrl,
            isSending = message.status == MessageStatus.SENDING,
            isResolvingMedia = message.isMediaPendingResolution &&
                !message.isMediaAwaitingManualDownload &&
                !downloading,
            isAwaitingManualDownload = message.isMediaAwaitingManualDownload && !downloading,
            isDownloadingMedia = downloading,
            downloadProgress = downloadProgress,
            downloadSizeLabel = message.formattedDownloadSize,
            progress = progress,
            modifier = mediaModifier,
        )
    }
    LaunchedEffect(message.id) { callbacks.onHydrateMedia?.invoke(message) }
}

@Composable
private fun ChatAudioMessageContent(
    message: EnhancedMessage,
    outgoing: Boolean,
    sendingProgress: Double?,
    onHydrate: ((EnhancedMessage) -> Unit)?,
    groupPosition: ChatMessageGroupPosition = ChatMessageGroupPosition.SINGLE,
) {
    LaunchedEffect(message.id) { onHydrate?.invoke(message) }
    GlassmorphicAudioMessage(
        messageId = message.id,
        audioUrl = message.mediaUrl,
        duration = message.duration ?: 0.0,
        waveformSamples = message.audioWaveform,
        isCurrentUser = outgoing,
        isSending = message.status == MessageStatus.SENDING,
        progress = sendingProgress,
        groupPosition = groupPosition,
    )
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
    fun openFirstLink(text: String, openUri: (String) -> Unit) {
        firstUrl(text)?.let(openUri)
    }
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
fun LinkPreviewCard(
    url: String,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    var metadata by remember(url) { mutableStateOf<LinkPreviewMetadata?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    val uriHandler = LocalUriHandler.current
    val dark = isSystemInDarkTheme()
    val host = remember(url) { Uri.parse(url).host.orEmpty() }
    val panelBg = when {
        embedded && outgoing -> Color.White.copy(.16f)
        embedded && dark -> Color.White.copy(.08f)
        embedded -> Color.White.copy(.55f)
        dark -> Color.White.copy(.08f)
        else -> Color.White.copy(.6f)
    }
    val titleColor = when {
        embedded && outgoing -> Color.White
        dark -> Color.White
        else -> Color.Black
    }
    val hostColor = when {
        embedded && outgoing -> Color.White.copy(.85f)
        else -> Color(0xFF007AFF)
    }
    val corner = if (embedded) 13.dp else 10.dp
    val imageMax = if (embedded) 150.dp else 120.dp
    LaunchedEffect(url) { metadata = LinkMetadataCache.fetch(url); loading = false }
    Column(
        modifier
            .then(if (embedded) Modifier.fillMaxWidth() else Modifier.width(240.dp))
            .clip(RoundedCornerShape(corner))
            .background(panelBg)
            .then(
                if (!embedded) Modifier.border(1.dp, Color.White.copy(if (dark) .1f else .3f), RoundedCornerShape(corner))
                else Modifier,
            )
            .combinedClickable(onClick = {
                com.moments.android.utilities.HapticManager.shared.lightImpact()
                uriHandler.openUri(url)
            }),
    ) {
        when {
            loading -> {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.size(14.dp), color = hostColor, strokeWidth = 1.5.dp)
                    Text(host.ifBlank { url }, color = if (embedded && outgoing) Color.White.copy(.8f) else Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            metadata?.title != null || metadata != null -> {
                val title = metadata?.title ?: host.ifBlank { url }
                metadata?.imageUrl?.let {
                    AsyncImage(it, null, Modifier.fillMaxWidth().height(imageMax), contentScale = ContentScale.Crop)
                }
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(title, color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(host, color = hostColor, fontSize = 10.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            else -> {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Link, null, tint = hostColor, modifier = Modifier.size(12.dp))
                    Text(host.ifBlank { url }, color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}
