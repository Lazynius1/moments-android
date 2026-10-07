@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.moments.android.views.messaging.components

import androidx.compose.ui.platform.LocalContext
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.heightIn
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
    val resolvedReactions = displayReactions ?: message.reactions
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
                // La cita acompaña a la burbuja durante el swipe-to-reply (≡ iOS offset compartido).
                val swipeFollow = Modifier.offset { IntOffset(swipeState.dragOffset.roundToInt(), 0) }
                repliedMessage?.let {
                    StackedReplyQuote(it, isCurrentUser, otherParticipantName, callbacks.onReplyTap, modifier = swipeFollow)
                }
                Column(horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start) {
                ChatTranslationContainer(
                    text = message.content.orEmpty(),
                    messageId = message.id,
                    isOutgoing = isCurrentUser ||
                        message.isDeleted ||
                        message.type != MessageType.TEXT ||
                        message.storyReplyData != null,
                ) { displayedText ->
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
                        flashShape = chatMessageBubbleShape(message, isCurrentUser, groupPosition),
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
                                translatedTextOverride = displayedText,
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
                // "Editado" bajo la burbuja, del lado del autor (≡ iOS caption timestamp).
                if (message.editedAt != null && !message.isDeleted) {
                    Text(
                        text = stringResource(R.string.chat_edited),
                        fontSize = 11.sp,
                        color = chatFloatingTextColor(com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme()).timestampColor),
                        style = androidx.compose.ui.text.TextStyle(shadow = chatFloatingTextShadow()),
                        modifier = swipeFollow.padding(top = 2.dp, start = 4.dp, end = 4.dp),
                    )
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
fun DeletedMessageBubble(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier,
    groupPosition: ChatMessageGroupPosition = ChatMessageGroupPosition.SINGLE,
) {
    val colors = com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme())
    // ≡ iOS: une esquinas en ráfagas como el texto (radio 20, unida 5).
    val shape = chatBubbleShape(
        side = if (isCurrentUser) ChatBubbleSide.TRAILING else ChatBubbleSide.LEADING,
        position = groupPosition,
    )
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
            .clip(shape)
            .background(colors.messageBubbleBackground)
            .border(0.5.dp, colors.messageBubbleStroke, shape)
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
    translatedTextOverride: String? = null,
) {
    val colors = com.moments.android.views.feed.AdaptiveColors(isSystemInDarkTheme())
    if (message.isDeleted) {
        DeletedMessageBubble(message, isCurrentUser, modifier, groupPosition)
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
                                text = translatedTextOverride ?: content,
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
    val dimensions = rememberChatMediaDimensions(message)
    val photoVideoSize = ChatMediaCardLayout.standaloneSize(
        dimensions?.first, dimensions?.second,
        ChatBubbleLayoutWidth.capped(
            ChatBubbleLayoutWidth.maxTextBubbleWidth(isOutgoing = outgoing),
            gutter = if (outgoing) 64.dp else 88.dp,
        ),
    )
    // La forma agrupada externa manda: se pasa al contenido para que no recorte con radio fijo.
    val shape = chatBubbleShape(
        side = if (outgoing) ChatBubbleSide.TRAILING else ChatBubbleSide.LEADING,
        position = position,
        cornerRadius = ChatMediaBubbleMetrics.cornerRadius,
    )
    val mediaModifier = Modifier.size(photoVideoSize).clip(shape)
    Box(mediaModifier) {
    if (video) {
        GlassmorphicVideoMessage(
            videoUrl = message.mediaUrl,
            thumbnailUrl = message.thumbnailUrl,
            isSending = message.status == MessageStatus.SENDING,
            isResolvingMedia = (message.isMediaPendingResolution || message.needsVideoThumbnailForDisplay) &&

                !downloading,
            isDownloadingMedia = downloading,
            downloadProgress = downloadProgress,
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
        )
    } else {
        GlassmorphicImageMessage(
            imageUrl = message.mediaUrl,
            previewThumbnailUrl = message.previewThumbnailURLForDisplay ?: message.thumbnailUrl,
            isSending = message.status == MessageStatus.SENDING,
            isResolvingMedia = message.isMediaPendingResolution &&

                !downloading,
            isDownloadingMedia = downloading,
            downloadProgress = downloadProgress,
            progress = progress,
            modifier = Modifier.fillMaxSize(),
            shape = shape,
        )
    }
        com.moments.android.views.messaging.media.ChatMessageStaticOverlay(message, modifier = Modifier.matchParentSize())
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

/** ≡ iOS `ChatMediaViews.bubbleShape` (radio de foto/vídeo; la esquina unida es la del texto). */
object ChatMediaBubbleMetrics {
    val cornerRadius = 16.dp
}

/** Forma real de la burbuja según tipo y posición en la ráfaga (flash al saltar a una cita). */
private fun chatMessageBubbleShape(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    position: ChatMessageGroupPosition,
): androidx.compose.ui.graphics.Shape? {
    val side = if (isCurrentUser) ChatBubbleSide.TRAILING else ChatBubbleSide.LEADING
    return when {
        // Borrados no pasan por MessageReactionOverlayBox: el chrome resalta el contenedor (sin reacciones).
        message.isDeleted -> null
        message.type == MessageType.TEXT && message.storyReplyData == null -> chatBubbleShape(side, position)
        message.type == MessageType.IMAGE || message.type == MessageType.VIDEO ->
            chatBubbleShape(side, position, cornerRadius = ChatMediaBubbleMetrics.cornerRadius)
        message.type == MessageType.AUDIO -> chatBubbleShape(side, position, cornerRadius = 18.dp)
        else -> RoundedCornerShape(ChatBubbleAnchorMetrics.cornerRadiusFor(message).dp)
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

/** ≡ iOS `LinkPreviewCard.embedded*`: margen hasta el borde de la burbuja y ancho máximo con enlace. */
object LinkPreviewMetrics {
    val embeddedInset = 4.dp
    val embeddedMaxWidth = 260.dp
    val compactThumbSize = 52.dp
    const val largeImageAspect = 1.91f
    val largeImageMaxHeight = 140.dp
}

/** Tarjeta integrada: imagen grande si es horizontal, si no fila compacta con miniatura (≡ iOS). */
@Composable
private fun EmbeddedLinkPreviewCard(url: String, outgoing: Boolean, modifier: Modifier = Modifier) {
    var metadata by remember(url) { mutableStateOf<LinkPreviewMetadata?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    var imageSize by remember(url) { mutableStateOf<androidx.compose.ui.geometry.Size?>(null) }
    val uriHandler = LocalUriHandler.current
    val dark = isSystemInDarkTheme()
    val host = remember(url) { Uri.parse(url).host.orEmpty() }
    val onOutgoing = chatBubbleTextColor(LocalChatOutgoingBubbleColor.current)
    val panelBg = when {
        outgoing -> onOutgoing.copy(.16f)
        dark -> Color.White.copy(.08f)
        else -> Color.White.copy(.55f)
    }
    val titleColor = if (outgoing) onOutgoing else if (dark) Color.White else Color.Black
    val hostColor = if (outgoing) onOutgoing.copy(.75f) else titleColor.copy(.55f)
    LaunchedEffect(url) { metadata = LinkMetadataCache.fetch(url); loading = false }
    val imageUrl = metadata?.imageUrl
    val size = imageSize
    val showsLarge = !loading && imageUrl != null && size != null &&
        size.width >= 300f && size.width / maxOf(size.height, 1f) >= 1.3f
    val title = if (loading) host.ifBlank { url } else metadata?.title ?: host.ifBlank { url }
    val shape = RoundedCornerShape(16.dp)
    val text: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, color = titleColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(host, color = hostColor, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(panelBg)
            .combinedClickable(onClick = {
                com.moments.android.utilities.HapticManager.shared.lightImpact()
                uriHandler.openUri(url)
            }),
    ) {
        if (showsLarge) {
            AsyncImage(
                imageUrl,
                null,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(LinkPreviewMetrics.largeImageAspect)
                    .heightIn(max = LinkPreviewMetrics.largeImageMaxHeight),
                contentScale = ContentScale.Crop,
            )
            Box(Modifier.padding(start = 9.dp, end = 9.dp, top = 7.dp, bottom = 8.dp)) { text() }
        } else {
            Row(
                Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(LinkPreviewMetrics.compactThumbSize)
                        .clip(RoundedCornerShape(10.dp))
                        .background(titleColor.copy(.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        loading -> CircularProgressIndicator(Modifier.size(16.dp), color = titleColor.copy(.7f), strokeWidth = 1.5.dp)
                        imageUrl != null -> AsyncImage(
                            // Tamaño original: decide imagen grande vs. compacta con las medidas reales, no con la miniatura de 52.
                            coil.request.ImageRequest.Builder(LocalContext.current)
                                .data(imageUrl)
                                .size(coil.size.Size.ORIGINAL)
                                .build(),
                            null,
                            Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                            onSuccess = { state -> imageSize = state.painter.intrinsicSize },
                        )
                        else -> Icon(Icons.Default.Link, null, tint = titleColor.copy(.7f), modifier = Modifier.size(18.dp))
                    }
                }
                Box(Modifier.weight(1f)) { text() }
            }
        }
    }
}

@Composable
fun LinkPreviewCard(
    url: String,
    outgoing: Boolean,
    modifier: Modifier = Modifier,
    embedded: Boolean = false,
) {
    if (embedded) {
        EmbeddedLinkPreviewCard(url, outgoing, modifier)
        return
    }
    var metadata by remember(url) { mutableStateOf<LinkPreviewMetadata?>(null) }
    var loading by remember(url) { mutableStateOf(true) }
    val uriHandler = LocalUriHandler.current
    val dark = isSystemInDarkTheme()
    val host = remember(url) { Uri.parse(url).host.orEmpty() }
    val panelBg = when {
        embedded && outgoing -> chatBubbleTextColor(LocalChatOutgoingBubbleColor.current).copy(.16f)
        embedded && dark -> Color.White.copy(.08f)
        embedded -> Color.White.copy(.55f)
        dark -> Color.White.copy(.08f)
        else -> Color.White.copy(.6f)
    }
    // Saliente: color de texto de la burbuja elegida (no blanco fijo).
    val onOutgoing = chatBubbleTextColor(LocalChatOutgoingBubbleColor.current)
    val titleColor = when {
        embedded && outgoing -> onOutgoing
        dark -> Color.White
        else -> Color.Black
    }
    val hostColor = when {
        embedded && outgoing -> onOutgoing.copy(.85f)
        else -> chatSystemBlue(dark)
    }
    val corner = if (embedded) 13.dp else 10.dp
    val imageMax = if (embedded) 150.dp else 120.dp
    val standaloneCardWidth = ChatBubbleLayoutWidth.capped(240.dp)
    val reservedMinHeight = if (!embedded) imageMax + 56.dp else null
    LaunchedEffect(url) { metadata = LinkMetadataCache.fetch(url); loading = false }
    Column(
        modifier
            .then(
                if (embedded) Modifier.fillMaxWidth()
                else Modifier
                    .width(standaloneCardWidth)
                    .then(reservedMinHeight?.let { Modifier.heightIn(min = it) } ?: Modifier),
            )
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
                    Text(host.ifBlank { url }, color = if (embedded && outgoing) onOutgoing.copy(.8f) else Color.Gray, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
