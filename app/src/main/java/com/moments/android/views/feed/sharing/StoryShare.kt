package com.moments.android.views.feed.sharing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.LiveUsernameContent
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.services.messaging.DirectMessageRoute
import com.moments.android.services.messaging.MessageRequestInteractionContext
import com.moments.android.services.messaging.MessageRequestService
import com.moments.android.views.messaging.services.sendSharedStoryMessage
import com.moments.android.views.story.StoryRepository
import com.moments.android.views.story.storyviewer.StoryStaticPreviewSurface
import kotlinx.coroutines.launch
import java.util.Date

// MARK: - Story share helpers

/** ≡ iOS `storyPreviewURL(for:)`. */
fun storyPreviewUrl(story: Story): String {
    story.backgroundFrameURL?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    story.backgroundBlurredFrameURL?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return story.mediaItem.url
}

/** ≡ iOS `storyMediaTypeString(for:)`. */
fun storyMediaTypeString(story: Story): String =
    if (story.mediaItem.type == MediaItem.MediaType.VIDEO) "video" else "image"

/** Compat para call sites que ya pasan URLs sueltas. */
fun storyPreviewUrl(
    backgroundFrameUrl: String?,
    backgroundBlurredFrameUrl: String?,
    mediaUrl: String,
): String {
    backgroundFrameUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    backgroundBlurredFrameUrl?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    return mediaUrl
}

fun storyMediaTypeString(isVideo: Boolean): String = if (isVideo) "video" else "image"

// MARK: - Acceso a historia compartida (privada, bloqueo, audiencia, expiración)

enum class SharedStoryAccessDenialReason {
    Expired,
    NotFound,
    Blocked,
    PrivateAccount,
    Restricted,
    ;

    val titleRes: Int
        get() = when (this) {
            PrivateAccount -> R.string.share_story_denied_private_title
            Blocked -> R.string.share_story_denied_blocked_title
            else -> R.string.share_story_unavailable
        }

    val messageRes: Int
        get() = when (this) {
            Expired -> R.string.share_story_denied_expired
            NotFound -> R.string.share_story_denied_not_found
            Blocked -> R.string.share_story_denied_blocked
            PrivateAccount -> R.string.share_story_denied_private
            Restricted -> R.string.share_story_denied_restricted
        }

    val icon: ImageVector
        get() = when (this) {
            Blocked -> Icons.Filled.Block
            PrivateAccount -> Icons.Filled.Lock
            Expired -> Icons.Filled.AccessTime
            else -> Icons.Filled.Lock
        }
}

/** ≡ iOS `SharedStoryAccessEvaluator`. */
object SharedStoryAccessEvaluator {
    private val storyRepository = StoryRepository()
    private val firestore = FirestoreService()

    suspend fun evaluate(
        authorId: String,
        storyId: String,
        payloadExpirationSeconds: Double?,
        viewerId: String,
    ): SharedStoryAccessOutcome {
        if (authorId == viewerId) {
            val story = runCatching { storyRepository.fetchStory(authorId, storyId) }.getOrNull()
            return if (story != null) {
                SharedStoryAccessOutcome.Allowed(story)
            } else {
                SharedStoryAccessOutcome.Denied(SharedStoryAccessDenialReason.NotFound)
            }
        }

        if (payloadExpirationSeconds != null) {
            val expirationMs = (payloadExpirationSeconds * 1000.0).toLong()
            if (Date().time > expirationMs) {
                return SharedStoryAccessOutcome.Denied(SharedStoryAccessDenialReason.Expired)
            }
        }

        val story = runCatching { storyRepository.fetchStory(authorId, storyId) }.getOrNull()
            ?: return SharedStoryAccessOutcome.Denied(SharedStoryAccessDenialReason.NotFound)
        return evaluate(story = story, authorId = authorId, viewerId = viewerId)
    }

    suspend fun evaluate(
        story: Story,
        authorId: String,
        viewerId: String,
    ): SharedStoryAccessOutcome {
        val resolvedAuthorId = authorId.ifBlank { story.authorId }

        if (resolvedAuthorId == viewerId || story.authorId == viewerId) {
            return SharedStoryAccessOutcome.Allowed(story)
        }

        if (story.expirationDate.time <= Date().time) {
            return SharedStoryAccessOutcome.Denied(SharedStoryAccessDenialReason.Expired)
        }

        if (PrivacyService.checkMutualBlocks(viewerId, resolvedAuthorId)) {
            return SharedStoryAccessOutcome.Denied(SharedStoryAccessDenialReason.Blocked)
        }

        val settings = runCatching { PrivacyService.fetchPrivacySettings(resolvedAuthorId) }.getOrNull()
            ?: return SharedStoryAccessOutcome.Denied(SharedStoryAccessDenialReason.Restricted)

        if (settings.isPrivate) {
            val following = firestore.isFollowing(viewerId, resolvedAuthorId)
            if (!following) {
                return SharedStoryAccessOutcome.Denied(SharedStoryAccessDenialReason.PrivateAccount)
            }
        }

        return if (PrivacyService.canUserViewStoryEnhanced(story, viewerId)) {
            SharedStoryAccessOutcome.Allowed(story)
        } else {
            SharedStoryAccessOutcome.Denied(SharedStoryAccessDenialReason.Restricted)
        }
    }
}

sealed class SharedStoryAccessOutcome {
    data class Allowed(val story: Story) : SharedStoryAccessOutcome()
    data class Denied(val reason: SharedStoryAccessDenialReason) : SharedStoryAccessOutcome()
}

// MARK: - Share sheet (solo mensajes)

@Composable
fun StoryShareBottomSheet(
    story: Story,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    MomentsModalSheet(
        onDismissRequest = onDismiss,
        largeOnly = false,
    ) { dismiss ->
        Column(modifier.fillMaxWidth()) {
            StoryShareRecipientsPanel(story = story, onDismiss = dismiss)
        }
    }
}

// MARK: - Recipients picker (reutiliza ShareRecipientsPickerSheet de share.kt)

@Composable
fun StoryShareRecipientsPanel(
    story: Story,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var deliveryFeedback by remember { mutableStateOf<String?>(null) }

    LiveUsernameContent(userId = story.authorId, fallbackUsername = story.username) { username ->
        ShareRecipientsPickerSheet(
            title = stringResource(R.string.share_story_title),
            subtitle = stringResource(R.string.share_story_by, username),
            showsBackButton = false,
            onDismiss = onDismiss,
            onSend = { selectedUsers, _ ->
                val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@ShareRecipientsPickerSheet
                if (story.id.isNullOrBlank()) return@ShareRecipientsPickerSheet
                val freshUsername = UserCacheService.getCachedUser(story.authorId)?.username
                    ?: story.username
                val shareText = context.getString(R.string.share_story_by, freshUsername)
                scope.launch {
                    val coordinator = MessageRequestService()
                    val failures = mutableListOf<String>()
                    selectedUsers.forEach { userId ->
                        if (userId.isBlank()) {
                            failures += context.getString(R.string.messaging_error_invalid_recipient)
                            return@forEach
                        }
                        runCatching {
                            val interaction = MessageRequestInteractionContext(
                                kind = MessageRequestInteractionContext.Kind.SHARE_STORY,
                                storyId = story.id,
                                storyOwnerId = story.authorId,
                                sharedContentId = story.id,
                                sharedContentOwnerId = story.authorId,
                            )
                            when (val route = coordinator.resolveRoute(userId, interaction)) {
                                is DirectMessageRoute.Conversation -> route.id
                                is DirectMessageRoute.ConversationDraft -> coordinator.activateConversationDraft(userId, route.threadId)
                                is DirectMessageRoute.IncomingRequest -> coordinator.acceptIncomingThread(route.threadId).conversationId
                                is DirectMessageRoute.OutgoingRequest -> {
                                    coordinator.appendRequestMessage(
                                        receiverId = userId,
                                        text = shareText,
                                        messageType = MessageType.SHARED_STORY,
                                        interaction = interaction,
                                    )
                                    null
                                }
                            }?.let { conversationId ->
                                ChatService.sendSharedStoryMessage(
                                    conversationId = conversationId,
                                    senderId = uid,
                                    story = story,
                                    shareText = shareText,
                                ).getOrThrow()
                            }
                        }.onFailure { failures += it.localizedMessage ?: context.getString(R.string.common_error) }
                    }
                    if (selectedUsers.isNotEmpty() && failures.isEmpty()) {
                        HapticManager.shared.success()
                        onDismiss()
                    } else {
                        deliveryFeedback = failures.firstOrNull() ?: context.getString(R.string.common_error)
                    }
                }
            },
        )
    }
    deliveryFeedback?.let { message ->
        AlertDialog(
            onDismissRequest = { deliveryFeedback = null },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { deliveryFeedback = null }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

// Chat bubble: preview 9:16 sin anillo

@Composable
fun SharedStoryMessageBubble(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    @Suppress("UNUSED_PARAMETER") onTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var canViewStory by remember(message.id) { mutableStateOf<Boolean?>(null) }
    var displayData by remember(message.id) { mutableStateOf(message.sharedStoryData) }
    var resolvedStory by remember(message.id) { mutableStateOf<Story?>(null) }
    var denialReason by remember(message.id) {
        mutableStateOf<SharedStoryAccessDenialReason?>(null)
    }
    var isLoading by remember(message.id) { mutableStateOf(true) }

    LaunchedEffect(message.id, message.sharedStoryData) {
        val data = message.sharedStoryData
        displayData = data
        val storyId = data?.get("storyId")
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
        if (data == null || storyId.isNullOrBlank() || currentUserId.isNullOrBlank()) {
            canViewStory = false
            denialReason = SharedStoryAccessDenialReason.Restricted
            isLoading = false
            return@LaunchedEffect
        }
        val authorId = data["storyAuthorId"]?.takeIf { it.isNotBlank() } ?: message.senderId
        val payloadExpiration = data["storyExpiration"]?.toDoubleOrNull()
        val result = SharedStoryAccessEvaluator.evaluate(
            authorId = authorId,
            storyId = storyId,
            payloadExpirationSeconds = payloadExpiration,
            viewerId = currentUserId,
        )
        when (result) {
            is SharedStoryAccessOutcome.Allowed -> {
                val story = result.story
                val author = UserCacheService.getCachedUser(story.authorId)?.username ?: story.username
                displayData = data + mapOf(
                    "storyId" to story.id.orEmpty(),
                    "storyAuthor" to author,
                    "storyAuthorId" to story.authorId,
                    "storyPreviewUrl" to storyPreviewUrl(story),
                    "storyMediaType" to storyMediaTypeString(story),
                    "storyExpiration" to (story.expirationDate.time / 1000.0).toString(),
                    "storyTimestamp" to (story.timestamp.time / 1000.0).toString(),
                )
                resolvedStory = story
                canViewStory = true
                denialReason = null
            }
            is SharedStoryAccessOutcome.Denied -> {
                canViewStory = false
                denialReason = result.reason
            }
        }
        isLoading = false
    }

    val align = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    Box(modifier.fillMaxWidth(), contentAlignment = align) {
        when {
            isLoading -> SharedStoryPreviewSkeleton(
                Modifier.padding(vertical = 4.dp),
            )
            canViewStory == true && displayData != null -> {
                Box(Modifier.padding(vertical = 4.dp)) {
                    StoryBubbleContent(
                        sharedStoryData = displayData!!,
                        isCurrentUser = isCurrentUser,
                        story = resolvedStory,
                    )
                }
            }
            else -> BlockedStoryBubble(
                reason = denialReason ?: SharedStoryAccessDenialReason.Restricted,
                sharedStoryData = displayData,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
fun BlockedStoryBubble(
    reason: SharedStoryAccessDenialReason,
    sharedStoryData: Map<String, String>?,
    modifier: Modifier = Modifier,
) {
    SharedStoryUnavailablePreview(
        title = stringResource(reason.titleRes),
        icon = reason.icon,
        previewImageURL = sharedStoryData?.get("storyPreviewUrl"),
        authorId = sharedStoryData?.get("storyAuthorId"),
        authorName = sharedStoryData?.get("storyAuthor"),
        modifier = modifier,
    )
}

@Composable
fun StoryBubbleContent(
    sharedStoryData: Map<String, String>,
    isCurrentUser: Boolean,
    story: Story? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(vertical = 4.dp),
        horizontalAlignment = if (isCurrentUser) Alignment.End else Alignment.Start,
    ) {
        StoryPreviewCard(sharedStoryData = sharedStoryData, story = story)
    }
}

object StoryShareCardMetrics {
    val width = 180.dp
    val height = 320.dp
    val cornerRadius = 18.dp
}

@Composable
private fun SharedStoryPreviewSkeleton(modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(StoryShareCardMetrics.cornerRadius)
    Box(
        modifier
            .size(StoryShareCardMetrics.width, StoryShareCardMetrics.height)
            .clip(shape)
            .background(Color.White.copy(0.1f)),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(
            color = Color.White.copy(0.65f),
            strokeWidth = 2.dp,
            modifier = Modifier.size(24.dp),
        )
    }
}

@Composable
private fun SharedStoryUnavailablePreview(
    title: String,
    icon: ImageVector,
    previewImageURL: String?,
    authorId: String?,
    authorName: String?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(StoryShareCardMetrics.cornerRadius)

    Box(
        modifier
            .size(StoryShareCardMetrics.width, StoryShareCardMetrics.height)
            .clip(shape),
    ) {
        if (!previewImageURL.isNullOrBlank()) {
            AsyncImage(
                model = previewImageURL,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(18.dp),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color.White.copy(0.14f), Color.White.copy(0.06f)),
                        ),
                    ),
            )
        }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(0.48f)))
        Column(
            Modifier.align(Alignment.Center).padding(horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(icon, contentDescription = null, tint = Color.White.copy(0.92f), modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(8.dp))
            Text(
                title,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
        }
        SharedDMPreviewAuthorRow(
            authorId = authorId,
            authorName = authorName,
            useStoryRing = true,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(horizontal = 12.dp, vertical = 12.dp),
        )
    }
}

/** Historia compartida en DM: 9:16 sin anillo; autor encima. */
@Composable
fun StoryPreviewCard(
    sharedStoryData: Map<String, String>,
    story: Story? = null,
    modifier: Modifier = Modifier,
) {
    val isVideo = sharedStoryData["storyMediaType"] == "video"
    val shape = RoundedCornerShape(StoryShareCardMetrics.cornerRadius)

    Box(
        modifier
            .size(StoryShareCardMetrics.width, StoryShareCardMetrics.height)
            .clip(shape),
    ) {
        if (story != null) {
            StoryStaticPreviewSurface(story = story, modifier = Modifier.fillMaxSize())
        } else {
            StoryVisualContent(sharedStoryData = sharedStoryData, modifier = Modifier.fillMaxSize())
        }

        Column(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxWidth().height(72.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(0.5f), Color.Transparent),
                            ),
                        ),
                )
                SharedDMPreviewAuthorRow(
                    authorId = sharedStoryData["storyAuthorId"],
                    authorName = sharedStoryData["storyAuthor"],
                    useStoryRing = true,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.weight(1f))
        }

        if (isVideo) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(0.35f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun StoryVisualContent(
    sharedStoryData: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val previewUrl = sharedStoryData["storyPreviewUrl"]?.takeIf { it.isNotBlank() }
    Box(modifier.background(Color.White.copy(0.12f)), contentAlignment = Alignment.Center) {
        if (previewUrl != null) {
            var loading by remember(previewUrl) { mutableStateOf(true) }
            AsyncImage(
                model = previewUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { loading = false },
                onError = { loading = false },
                onLoading = { loading = true },
            )
            if (loading) {
                Box(
                    Modifier.fillMaxSize().background(Color.White.copy(0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        } else {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = Color.White.copy(0.5f),
                modifier = Modifier.size(28.dp),
            )
        }
    }
}
