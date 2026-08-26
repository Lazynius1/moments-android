package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Story
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.sharing.SharedDMPreviewBottomGradient
import com.moments.android.views.feed.sharing.SharedStoryAccessDenialReason
import com.moments.android.views.feed.sharing.SharedStoryAccessEvaluator
import com.moments.android.views.feed.sharing.SharedStoryAccessOutcome
import com.moments.android.views.feed.sharing.storyMediaTypeString
import com.moments.android.views.feed.sharing.storyPreviewUrl
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.messaging.components.ChatEphemeralLayout
import com.moments.android.views.messaging.components.ChatEphemeralMessageContent
import com.moments.android.views.messaging.components.messageBubbleBackground
import com.moments.android.views.messaging.components.messageBubbleStroke
import com.moments.android.views.messaging.components.messageTextColor
import com.moments.android.views.messaging.components.replyBarSecondaryText
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.cleanupExpiredEphemeralMessages
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import java.util.Date

/** Métricas de `StoryReplyPreviewMetrics` / `StoryReplyEphemeralMetrics`. */
object StoryReplyPreviewMetrics {
    val width = 76.dp
    val height = 118.dp
    val cornerRadius = 14.dp
}

private val storyReplyRingGradient = Brush.linearGradient(
    listOf(Color(0xFF0A84FF), Color(0xFFAF52DE), Color(0xFFFF2D55)),
)

private fun storyReplyFormatTimeLeft(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0) / 1000).toInt()
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

/**
 * Port de `StoryReplyMessageBubble`.
 * `storyReplyData` vive en [EnhancedMessage] (paridad iOS).
 */
@Composable
fun StoryReplyMessageBubble(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    otherParticipantId: String? = null,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    onOpenMedia: ((EnhancedMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val adaptive = rememberAdaptiveColors()
    val storyReplyData = message.storyReplyData

    LaunchedEffect(message.id, message.type, message.expirationDate, message.isDeleted) {
        if (message.type == MessageType.EPHEMERAL &&
            !message.isDeleted &&
            message.expirationDate?.let { it.before(Date()) } == true
        ) {
            delay(1_000)
            runCatching { ChatService.cleanupExpiredEphemeralMessages() }
        }
    }

    Column(
        modifier
            .width(280.dp)
            .padding(vertical = 2.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (storyReplyData != null) {
            Text(
                stringResource(
                    if (isCurrentUser) R.string.story_reply_you_replied
                    else R.string.story_reply_replied_to_your_story,
                ),
                color = adaptive.replyBarSecondaryText,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                textAlign = if (isCurrentUser) TextAlign.End else TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
            StoryReplyThreadedColumn(
                storyReplyData = storyReplyData,
                message = message,
                isCurrentUser = isCurrentUser,
                otherParticipantId = otherParticipantId,
                onHydrateMedia = onHydrateMedia,
                onOpenMedia = onOpenMedia,
            )
        } else {
            StoryReplyBody(
                message = message,
                isCurrentUser = isCurrentUser,
                onHydrateMedia = onHydrateMedia,
                onOpenMedia = onOpenMedia,
            )
        }
    }
}

@Composable
private fun StoryReplyThreadedColumn(
    storyReplyData: Map<String, String>,
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    otherParticipantId: String?,
    onHydrateMedia: ((EnhancedMessage) -> Unit)?,
    onOpenMedia: ((EnhancedMessage) -> Unit)?,
) {
    val adaptive = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val lineColor = adaptive.replyBarSecondaryText.copy(if (isDark) 0.55f else 0.4f)
    val threadSpacing = 10.dp
    val messageInset = 2.5.dp + threadSpacing

    Column(
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(threadSpacing)) {
            if (!isCurrentUser) {
                Box(
                    Modifier
                        .width(2.5.dp)
                        .height(StoryReplyPreviewMetrics.height)
                        .clip(RoundedCornerShape(50))
                        .background(lineColor),
                )
            }
            StoryReplyGatedThumbnailView(
                storyReplyData = storyReplyData,
                messageSenderId = message.senderId,
                otherParticipantId = otherParticipantId,
            )
            if (isCurrentUser) {
                Box(
                    Modifier
                        .width(2.5.dp)
                        .height(StoryReplyPreviewMetrics.height)
                        .clip(RoundedCornerShape(50))
                        .background(lineColor),
                )
            }
        }
        Box(
            Modifier.padding(
                start = if (isCurrentUser) 0.dp else messageInset,
                end = if (isCurrentUser) messageInset else 0.dp,
            ),
        ) {
            StoryReplyBody(
                message = message,
                isCurrentUser = isCurrentUser,
                onHydrateMedia = onHydrateMedia,
                onOpenMedia = onOpenMedia,
            )
        }
    }
}

@Composable
private fun StoryReplyBody(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    onHydrateMedia: ((EnhancedMessage) -> Unit)?,
    onOpenMedia: ((EnhancedMessage) -> Unit)?,
) {
    when {
        message.type == MessageType.EPHEMERAL ||
            message.type == MessageType.VIEW_ONCE_IMAGE ||
            message.type == MessageType.VIEW_ONCE_VIDEO -> {
            val valid = message.expirationDate?.after(Date()) ?: true
            if (message.isDeleted || !valid) {
                StoryReplyEphemeralExpiredCard()
            } else {
                ChatEphemeralMessageContent(
                    message = message,
                    layout = ChatEphemeralLayout.COMPACT,
                    onHydrateMedia = onHydrateMedia,
                    onOpenMedia = onOpenMedia,
                )
            }
        }
        else -> StoryTextReplyContent(message = message, isCurrentUser = isCurrentUser)
    }
}

/** Port de `StoryTextReplyContent`. */
@Composable
fun StoryTextReplyContent(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier,
) {
    val adaptive = rememberAdaptiveColors()
    val content = message.content ?: return
    val clean = if (content.startsWith("💬 ")) content.drop(2) else content
    Text(
        clean,
        color = adaptive.messageTextColor,
        fontSize = 15.sp,
        textAlign = if (isCurrentUser) TextAlign.End else TextAlign.Start,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Port de `StoryReplyGatedThumbnailView`. */
@Composable
fun StoryReplyGatedThumbnailView(
    storyReplyData: Map<String, String>,
    messageSenderId: String,
    otherParticipantId: String?,
    modifier: Modifier = Modifier,
) {
    var canViewStory by remember { mutableStateOf(false) }
    var denialReason by remember { mutableStateOf(SharedStoryAccessDenialReason.Expired) }
    var isLoading by remember { mutableStateOf(true) }
    var displayData by remember(storyReplyData) { mutableStateOf(storyReplyData) }
    var resolvedStory by remember(storyReplyData) { mutableStateOf<Story?>(null) }

    LaunchedEffect(storyReplyData, messageSenderId, otherParticipantId) {
        isLoading = true
        displayData = storyReplyData
        val storyId = storyReplyData["storyId"].orEmpty()
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (storyId.isEmpty() || viewerId.isEmpty()) {
            canViewStory = false
            denialReason = SharedStoryAccessDenialReason.Restricted
            isLoading = false
            return@LaunchedEffect
        }
        val authorId = resolvedStoryAuthorId(storyReplyData, messageSenderId, otherParticipantId, viewerId)
        if (authorId.isEmpty()) {
            canViewStory = false
            denialReason = SharedStoryAccessDenialReason.Restricted
            isLoading = false
            return@LaunchedEffect
        }
        val payloadExpiration = storyReplyData["storyExpiration"]?.toDoubleOrNull()
        when (
            val result = SharedStoryAccessEvaluator.evaluate(
                authorId = authorId,
                storyId = storyId,
                payloadExpirationSeconds = payloadExpiration,
                viewerId = viewerId,
            )
        ) {
            is SharedStoryAccessOutcome.Allowed -> {
                val story = result.story
                displayData = storyReplyData + mapOf(
                    "storyId" to story.id.orEmpty(),
                    "storyAuthor" to story.username,
                    "storyAuthorId" to story.authorId,
                    "storyPreviewUrl" to storyPreviewUrl(story),
                    "storyMediaUrl" to story.mediaItem.url,
                    "storyMediaType" to storyMediaTypeString(story),
                    "storyExpiration" to (story.expirationDate.time / 1000.0).toString(),
                    "storyTimestamp" to (story.timestamp.time / 1000.0).toString(),
                )
                resolvedStory = story
                canViewStory = true
                denialReason = SharedStoryAccessDenialReason.Expired
            }
            is SharedStoryAccessOutcome.Denied -> {
                canViewStory = false
                denialReason = result.reason
            }
        }
        isLoading = false
    }

    Box(modifier) {
        when {
            isLoading -> StoryReplyThumbnailSkeleton()
            canViewStory -> StoryReplyThumbnailView(displayData, story = resolvedStory)
            else -> StoryReplyUnavailableThumbnail(denialReason, displayData)
        }
    }
}

private fun resolvedStoryAuthorId(
    storyReplyData: Map<String, String>,
    messageSenderId: String,
    otherParticipantId: String?,
    viewerId: String,
): String {
    val authorId = storyReplyData["storyAuthorId"].orEmpty()
    if (authorId.isNotEmpty()) return authorId
    return if (messageSenderId == viewerId) otherParticipantId.orEmpty() else viewerId
}

@Composable
private fun StoryReplyThumbnailSkeleton(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier
            .size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height)
            .clip(RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .background(Color.White.copy(if (isDark) 0.08f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = if (isDark) Color.White.copy(0.6f) else Color.Gray,
            strokeWidth = 2.dp,
        )
    }
}

/** Port de `StoryReplyUnavailableThumbnail`. */
@Composable
fun StoryReplyUnavailableThumbnail(
    reason: SharedStoryAccessDenialReason,
    storyReplyData: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val preview = storyReplyData["storyPreviewUrl"]?.trim()?.takeIf { it.isNotEmpty() }
        ?: storyReplyData["storyMediaUrl"]
    val innerRadius = StoryReplyPreviewMetrics.cornerRadius - 2.dp
    Box(
        modifier
            .size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height)
            .border(2.dp, storyReplyRingGradient, RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .padding(2.dp)
            .clip(RoundedCornerShape(innerRadius)),
    ) {
        if (!preview.isNullOrBlank()) {
            AsyncImage(
                model = preview,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(18.dp),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.White.copy(if (isSystemInDarkTheme()) 0.1f else 0.15f)))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.55f)))
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(reason.icon, contentDescription = null, tint = Color.White.copy(0.9f), modifier = Modifier.size(16.dp))
            Text(
                stringResource(reason.titleRes),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 9.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Port de `StoryReplyThumbnailView`. */
@Composable
fun StoryReplyThumbnailView(
    storyReplyData: Map<String, String>,
    modifier: Modifier = Modifier,
    story: Story? = null,
) {
    val adaptive = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val mediaUrl = storyReplyData["storyMediaUrl"]
    val isVideo = storyReplyData["storyMediaType"] == "video"
    val innerRadius = StoryReplyPreviewMetrics.cornerRadius - 2.dp
    Box(
        modifier
            .size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height)
            .border(2.dp, storyReplyRingGradient, RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .padding(2.dp)
            .clip(RoundedCornerShape(innerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        if (story != null) {
            StoryStaticPreviewSurface(story = story, modifier = Modifier.fillMaxSize())
        } else if (!mediaUrl.isNullOrBlank()) {
            AsyncImage(
                model = mediaUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                Modifier.fillMaxSize().background(Color.White.copy(if (isDark) 0.1f else 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, tint = adaptive.replyBarSecondaryText, modifier = Modifier.size(14.dp))
            }
        }
        if (isVideo) {
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
            }
        }
    }
}

/** Port de `StoryReplyEphemeralTapCard`. */
@Composable
fun StoryReplyEphemeralTapCard(
    previewImageUrl: String?,
    expirationDate: Date?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val adaptive = rememberAdaptiveColors()
    Box(
        modifier
            .size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height)
            .clip(RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .border(2.dp, storyReplyRingGradient, RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .clickable(onClick = onTap),
    ) {
        if (!previewImageUrl.isNullOrBlank()) {
            AsyncImage(
                model = previewImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(22.dp),
            )
        } else {
            Box(Modifier.fillMaxSize().background(adaptive.messageBubbleBackground))
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.35f)))
        Column(Modifier.fillMaxSize()) {
            Spacer(Modifier.weight(1f))
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AttachmentIconView(
                    icon = AttachmentIcon.EPHEMERAL,
                    preset = AttachmentIconPreset.STORY_EPHEMERAL,
                    tintColor = Color.White.copy(0.95f),
                )
            }
            Spacer(Modifier.weight(1f))
            SharedDMPreviewBottomGradient(Modifier.fillMaxWidth())
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp)
                    .padding(bottom = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.story_reply_tap_to_view),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
                if (expirationDate != null && expirationDate.after(Date())) {
                    Text(
                        stringResource(
                            R.string.story_reply_expires_in,
                            storyReplyFormatTimeLeft(expirationDate.time - Date().time),
                        ),
                        color = Color.White.copy(0.75f),
                        fontSize = 10.sp,
                    )
                }
            }
        }
    }
}

/** Port de `StoryReplyEphemeralImageCard`. */
@Composable
fun StoryReplyEphemeralImageCard(
    imageUrl: String,
    expirationDate: Date?,
    modifier: Modifier = Modifier,
) {
    var showFullScreen by remember { mutableStateOf(false) }
    Box(
        modifier
            .size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height)
            .clip(RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .clickable { showFullScreen = true },
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (expirationDate != null && expirationDate.after(Date())) {
            Text(
                storyReplyFormatTimeLeft(expirationDate.time - Date().time),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .border(0.5.dp, Color.White.copy(0.35f), RoundedCornerShape(50))
                    .background(Color.Black.copy(0.55f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    if (showFullScreen) {
        FullScreenEphemeralImageView(
            imageUrl = imageUrl,
            expirationDate = expirationDate,
            onDismiss = { showFullScreen = false },
        )
    }
}

/** Port de `StoryReplyEphemeralExpiredCard`. */
@Composable
fun StoryReplyEphemeralExpiredCard(modifier: Modifier = Modifier) {
    val adaptive = rememberAdaptiveColors()
    Box(
        modifier
            .size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height)
            .clip(RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .border(0.5.dp, adaptive.messageBubbleStroke, RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .background(adaptive.messageBubbleBackground),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.45f)))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.padding(horizontal = 8.dp),
        ) {
            Icon(
                SharedStoryAccessDenialReason.Expired.icon,
                contentDescription = null,
                tint = Color.White.copy(0.85f),
                modifier = Modifier.size(20.dp),
            )
            Text(
                stringResource(R.string.stories_ephemeral_expired),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

/** Port de `StoryReplyEphemeralResolvingCard`. */
@Composable
fun StoryReplyEphemeralResolvingCard(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    Box(
        modifier
            .size(StoryReplyPreviewMetrics.width, StoryReplyPreviewMetrics.height)
            .clip(RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .border(2.dp, storyReplyRingGradient, RoundedCornerShape(StoryReplyPreviewMetrics.cornerRadius))
            .background(Color.White.copy(if (isDark) 0.08f else 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            color = if (isDark) Color.White.copy(0.7f) else Color.Gray,
            strokeWidth = 2.dp,
        )
    }
}

/** Port de `EphemeralStoryReplyContent`. */
@Composable
fun EphemeralStoryReplyContent(
    message: EnhancedMessage,
    showContent: Boolean,
    onShowContentChange: (Boolean) -> Unit,
    onHydrateMedia: ((EnhancedMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var hasBeenViewed by remember(message.id) { mutableStateOf(message.isViewed) }
    val previewImageUrl = message.thumbnailUrl ?: message.mediaUrl
    val resolvedMediaUrl = message.mediaUrl?.takeIf { it.isNotBlank() }
        ?: message.thumbnailUrl?.takeIf { it.isNotBlank() }
    val valid = message.expirationDate?.after(Date()) ?: true

    LaunchedEffect(message.id) {
        hasBeenViewed = message.isViewed
        onHydrateMedia?.invoke(message)
    }

    Box(modifier) {
        when {
            message.isDeleted || !valid -> StoryReplyEphemeralExpiredCard()
            !showContent && !hasBeenViewed -> {
                StoryReplyEphemeralTapCard(
                    previewImageUrl = previewImageUrl,
                    expirationDate = message.expirationDate,
                    onTap = {
                        onShowContentChange(true)
                        hasBeenViewed = true
                        onHydrateMedia?.invoke(message)
                    },
                )
            }
            resolvedMediaUrl != null -> {
                StoryReplyEphemeralImageCard(
                    imageUrl = resolvedMediaUrl,
                    expirationDate = message.expirationDate,
                )
            }
            message.isMediaPendingResolution -> StoryReplyEphemeralResolvingCard()
            else -> StoryReplyEphemeralExpiredCard()
        }
    }
}

/** Port de `ClickableEphemeralImageContent`. */
@Composable
fun ClickableEphemeralImageContent(
    imageUrl: String,
    expirationDate: Date?,
    modifier: Modifier = Modifier,
) {
    var showFullScreen by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth(0.7f)
            .height(300.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(16.dp))
            .clickable { showFullScreen = true },
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (expirationDate != null && expirationDate.after(Date())) {
            Text(
                storyReplyFormatTimeLeft(expirationDate.time - Date().time),
                color = Color.White,
                fontSize = 10.sp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(0.6f), RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
    if (showFullScreen) {
        FullScreenEphemeralImageView(
            imageUrl = imageUrl,
            expirationDate = expirationDate,
            onDismiss = { showFullScreen = false },
        )
    }
}

/** Port de `FullScreenEphemeralImageView`. */
@Composable
fun FullScreenEphemeralImageView(
    imageUrl: String,
    expirationDate: Date?,
    onDismiss: () -> Unit,
) {
    var timeLeftMs by remember { mutableLongStateOf(0L) }

    LaunchedEffect(expirationDate) {
        val end = expirationDate ?: return@LaunchedEffect
        while (isActive) {
            val left = (end.time - Date().time).coerceAtLeast(0L)
            timeLeftMs = left
            if (left <= 0L) {
                onDismiss()
                break
            }
            delay(1_000)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
        ) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.common_close),
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                    fontSize = 16.sp,
                    modifier = Modifier.clickable(onClick = onDismiss),
                )
                Spacer(Modifier.weight(1f))
                if (timeLeftMs > 0) {
                    Text(
                        stringResource(R.string.story_reply_expires_in, storyReplyFormatTimeLeft(timeLeftMs)),
                        color = Color.White.copy(0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier
                            .background(Color.Black.copy(0.5f), RoundedCornerShape(50))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

// MARK: - Glassmorphic Extensions (≡ View.glassmorphic / storyGlassmorphic)

/**
 * Chrome canvas elevado (fill + stroke adaptativos vía [momentsChromeGlass]).
 */
fun Modifier.glassmorphic(shape: Shape = RectangleShape): Modifier =
    this.momentsChromeGlass(shape, interactive = false)

/**
 * Chrome canvas elevado para overlays del story viewer.
 */
fun Modifier.storyGlassmorphic(shape: Shape = RectangleShape): Modifier =
    this.momentsChromeGlass(shape, interactive = false)
