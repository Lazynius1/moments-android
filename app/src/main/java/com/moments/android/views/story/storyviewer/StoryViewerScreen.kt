package com.moments.android.views.story.storyviewer

import android.content.ContentValues
import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.models.MediaItem
import com.moments.android.models.Story
import com.moments.android.views.story.StoryDeckGestureGate
import com.moments.android.views.story.StoryPlaybackCoordinator
import com.moments.android.views.story.StoryRevealStickerOverlay
import com.moments.android.views.story.StoryReaction
import com.moments.android.views.story.StoryViewer
import com.moments.android.views.story.StoryRepository
import com.moments.android.views.story.StoryChainView
import kotlinx.coroutines.launch
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.social.BestFriendsService
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URL

private enum class StoryViewerConfirmation(val title: String, val message: String, val action: String) {
    DELETE("Delete story?", "This story will be permanently deleted.", "Delete"),
    UNFOLLOW("Unfollow?", "You will stop following this creator.", "Unfollow"),
    MUTE("Mute?", "Stories from this creator will be muted.", "Mute"),
    LEAVE_BEST_FRIENDS("Leave best friends?", "You will no longer see this private audience.", "Leave"),
}

/**
 * Port MVP de `StoryViewerScreen.swift` — media + progress + header + gestos.
 * Stickers / reply / viewers / ads = stubs (no-op).
 */
@Composable
fun StoryViewerScreen(
    story: Story,
    segmentCount: Int,
    segmentIndex: Int,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onDismiss: () -> Unit,
    onProfileTap: () -> Unit = {},
    onViewActivity: () -> Unit = {},
    onSaveStory: () -> Unit = {},
    onDeleteStory: () -> Unit = {},
    onUnfollowAuthor: () -> Unit = {},
    onMuteAuthor: () -> Unit = {},
    onReportStory: () -> Unit = {},
    onLeaveBestFriends: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onOpenChainStory: (List<Story>, Int) -> Unit = { _, _ -> },
    onContinueChain: (String, String, Int) -> Unit = { _, _, _ -> },
    viewers: List<StoryViewer> = emptyList(),
    reactions: List<StoryReaction> = emptyList(),
    onHoldChanged: (Boolean) -> Unit = {},
    gestureGate: StoryDeckGestureGate? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playbackCoordinator = remember { StoryPlaybackCoordinator() }
    var held by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<StoryViewerConfirmation?>(null) }
    var showActivity by remember { mutableStateOf(false) }
    var activityTab by remember { mutableStateOf(0) }
    var messageText by remember { mutableStateOf("") }
    var showReactions by remember { mutableStateOf(false) }
    var showChain by remember { mutableStateOf(false) }
    var immersiveHold by remember { mutableStateOf(false) }
    var zoomScale by remember { mutableStateOf(1f) }
    val zoomGesture = rememberTransformableState { zoomChange, _, _ ->
        zoomScale = (zoomScale * zoomChange).coerceIn(1f, 3f)
    }
    var authorAllowsMessages by remember { mutableStateOf(true) }
    var authorAllowsReactions by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val storyRepository = remember { StoryRepository() }
    val onNextState = rememberUpdatedState(onNext)
    val onPreviousState = rememberUpdatedState(onPrevious)

    fun pauseStoryPlayback() {
        held = true
        playbackCoordinator.pauseStory()
        onHoldChanged(true)
    }

    fun resumeStoryPlayback() {
        held = false
        playbackCoordinator.resumeStory(story, canResume = true) { onNextState.value() }
        onHoldChanged(false)
    }

    LaunchedEffect(story.id) {
        playbackCoordinator.prepareStory(story) { onNextState.value() }
    }
    LaunchedEffect(story.authorId) {
        val settings = runCatching {
            @Suppress("UNCHECKED_CAST")
            FirestoreService().db.collection("users").document(story.authorId).get().await().data
                ?.get("contentVisibilitySettings") as? Map<String, Any?>
        }.getOrNull()
        authorAllowsMessages = settings?.get("allowStoryMessages") as? Boolean ?: true
        authorAllowsReactions = settings?.get("allowStoryReactions") as? Boolean ?: true
    }
    DisposableEffect(playbackCoordinator) {
        onDispose { playbackCoordinator.close() }
    }
    LaunchedEffect(showQuickActions) {
        if (showQuickActions) pauseStoryPlayback() else if (confirmation == null) resumeStoryPlayback()
    }
    LaunchedEffect(showActivity) {
        if (showActivity) pauseStoryPlayback() else resumeStoryPlayback()
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer(scaleX = zoomScale, scaleY = zoomScale)
            .transformable(zoomGesture),
    ) {
        StoryViewerMedia(
            story = story,
            isPaused = playbackCoordinator.isPaused,
            onVideoProgress = { playbackCoordinator.updateVideoProgress(it, story) },
            onVideoComplete = {
                if (playbackCoordinator.canAdvanceAfterVideoComplete()) onNextState.value()
            },
            modifier = Modifier.fillMaxSize(),
        )

        StoryMediaOverlayRendererView(
            textOverlays = story.textOverlays.orEmpty(),
            stickers = story.stickers.orEmpty(),
            drawingData = story.drawingData,
            storyId = story.id.orEmpty(),
            userId = story.authorId,
            replayToken = segmentIndex,
            gestureGate = gestureGate,
            onPauseStory = ::pauseStoryPlayback,
            onResumeStory = ::resumeStoryPlayback,
            modifier = Modifier.fillMaxSize(),
        )

        // Gradient top for readability
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(0.55f), Color.Transparent),
                    ),
                ),
        )

        // Caption text (simple)
        story.text?.takeIf { it.isNotBlank() }?.let { caption ->
            Text(
                caption,
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 48.dp),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        // Tap zones + hold pause
        Row(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(story.id) {
                        detectTapGestures(
                            onPress = {
                                val released = tryAwaitRelease()
                                if (immersiveHold && released) {
                                    immersiveHold = false
                                    resumeStoryPlayback()
                                }
                            },
                            onLongPress = { pauseStoryPlayback(); immersiveHold = true },
                            onTap = { onPreviousState.value() },
                        )
                    },
            )
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .pointerInput(story.id) {
                        detectTapGestures(
                            onPress = {
                                val released = tryAwaitRelease()
                                if (immersiveHold && released) {
                                    immersiveHold = false
                                    resumeStoryPlayback()
                                }
                            },
                            onLongPress = { pauseStoryPlayback(); immersiveHold = true },
                            onTap = { onNextState.value() },
                        )
                    },
            )
        }

        StoryRevealStickerOverlay(
            storyId = story.id.orEmpty(),
            stickers = story.stickers.orEmpty(),
            gestureGate = gestureGate,
            onPauseStory = {
                pauseStoryPlayback()
            },
            onResumeStory = {
                resumeStoryPlayback()
            },
            modifier = Modifier.fillMaxSize(),
        )

        // El chrome queda encima del Reveal, como en `StoryViewerScreen.swift`.
        if (!immersiveHold) Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)) {
            StoryProgressRow(
                count = segmentCount.coerceAtLeast(1),
                currentIndex = segmentIndex.coerceIn(0, (segmentCount - 1).coerceAtLeast(0)),
                progress = playbackCoordinator.progress,
                audience = story.audience,
            )
            Spacer(Modifier.height(10.dp))
            StoryViewerHeader(
                username = story.username,
                profileImagePath = story.profileImagePath,
                onClose = onDismiss,
                onProfileTap = onProfileTap,
                onMore = { showQuickActions = true },
                onChain = story.chainId?.let { { showChain = true } },
            )
        }

        if (showChain) {
            val chainId = story.chainId
            val chainTitle = story.chainTitle
            if (chainId != null && chainTitle != null) StoryChainView(
                chainId = chainId,
                chainTitle = chainTitle,
                canContinueChain = true,
                initialStoryId = story.id,
                initialChainPosition = story.chainPosition,
                onDismiss = { showChain = false },
                onOpenStory = { stories, index -> showChain = false; onOpenChainStory(stories, index) },
                onContinueChain = { id, title, position -> showChain = false; onContinueChain(id, title, position) },
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (showQuickActions) {
            val isOwnStory = story.authorId == FirebaseAuth.getInstance().currentUser?.uid
            StoryQuickActionsMenu(
                isOwnStory = isOwnStory,
                canLeaveBestFriends = !isOwnStory && story.audience?.trim()?.lowercase()?.replace("_", "")?.replace("-", "") == "bestfriends",
                textColor = Color.White,
                dividerColor = Color.White.copy(.12f),
                onViewActivity = { showQuickActions = false; onViewActivity() },
                onSave = {
                    showQuickActions = false
                    scope.launch { saveStoryMediaToDevice(context, story) }
                    onSaveStory()
                },
                onDelete = { showQuickActions = false; confirmation = StoryViewerConfirmation.DELETE },
                onUnfollow = { showQuickActions = false; confirmation = StoryViewerConfirmation.UNFOLLOW },
                onMute = { showQuickActions = false; confirmation = StoryViewerConfirmation.MUTE },
                onReport = { showQuickActions = false; onReportStory() },
                onLeaveBestFriends = { showQuickActions = false; confirmation = StoryViewerConfirmation.LEAVE_BEST_FRIENDS },
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 16.dp),
            )
        }

        confirmation?.let { pending ->
            GlassmorphicStoryConfirmationDialog(
                title = pending.title,
                message = pending.message,
                confirmTitle = pending.action,
                cancelTitle = "Cancel",
                isDestructive = pending != StoryViewerConfirmation.LEAVE_BEST_FRIENDS,
                onConfirm = {
                    confirmation = null
                    scope.launch {
                        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid
                        when (pending) {
                            StoryViewerConfirmation.DELETE -> story.id?.let { storyRepository.softDeleteStory(story.authorId, it) }.also { onDeleteStory() }
                            StoryViewerConfirmation.UNFOLLOW -> if (currentUserId != null) FirestoreService().unfollowUser(currentUserId, story.authorId).also { onUnfollowAuthor() }
                            StoryViewerConfirmation.MUTE -> if (currentUserId != null) FirestoreService().db.collection("users").document(currentUserId).update(
                                "muteSettings.mutedUsers", FieldValue.arrayUnion(story.authorId),
                            ).await().also { onMuteAuthor() }
                            StoryViewerConfirmation.LEAVE_BEST_FRIENDS -> BestFriendsService().optOutFromBestFriends(story.authorId).also { onLeaveBestFriends() }
                        }
                    }
                },
                onCancel = { confirmation = null },
            )
        }

        if (showActivity) {
            GlassmorphicViewersSheet(
                story = story,
                viewers = viewers,
                reactions = reactions,
                initialTab = activityTab,
                onDismiss = { showActivity = false },
                modifier = Modifier.fillMaxSize().background(Color.Black),
            )
        } else if (story.authorId == FirebaseAuth.getInstance().currentUser?.uid) {
            StoryOwnStoryBottomBar(
                viewers = viewers,
                reactions = reactions,
                audience = story.audience,
                expirationHours = story.expirationHours,
                onViewActivity = { activityTab = 0; showActivity = true },
                onReactionsActivity = { activityTab = 1; showActivity = true },
                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
            )
        } else {
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp, vertical = 18.dp)) {
                if (showReactions && authorAllowsReactions) StoryReactionsStrip(
                    reactions = listOf("❤️", "😂", "😮", "😢", "🔥"),
                    showReactions = true,
                    onReaction = { emoji ->
                        val viewerId = FirebaseAuth.getInstance().currentUser?.uid
                        val storyId = story.id
                        if (viewerId != null && storyId != null) scope.launch { storyRepository.addReaction(story.authorId, storyId, viewerId, emoji) }
                        showReactions = false
                    },
                    onMoreReactions = {},
                    modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 10.dp),
                )
                if (authorAllowsMessages || authorAllowsReactions) Row(verticalAlignment = Alignment.CenterVertically) {
                    if (authorAllowsMessages) TextField(
                        value = messageText,
                        onValueChange = { messageText = it },
                        placeholder = { Text("Reply to story") },
                        modifier = Modifier.weight(1f),
                    ) else Spacer(Modifier.weight(1f))
                    if (authorAllowsReactions) IconButton(onClick = { showReactions = !showReactions }) { Icon(Icons.Filled.SentimentSatisfied, null, tint = Color.White) }
                    if (authorAllowsMessages) IconButton(onClick = { messageText.takeIf { it.isNotBlank() }?.let { onSendMessage(it); messageText = "" } }) { Icon(Icons.Filled.Send, null, tint = Color.White) }
                } else {
                    StoryNoInteractionsNotice(Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}

@Composable
private fun StoryProgressRow(
    count: Int,
    currentIndex: Int,
    progress: Float,
    audience: String?,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(count) { i ->
            val fill = when {
                i < currentIndex -> 1f
                i == currentIndex -> progress
                else -> 0f
            }
            GlassmorphicProgressBar(
                progress = fill,
                isActive = i == currentIndex,
                audience = audience,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StoryViewerHeader(
    username: String,
    profileImagePath: String?,
    onClose: () -> Unit,
    onProfileTap: () -> Unit,
    onMore: () -> Unit,
    onChain: (() -> Unit)?,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(model = profileImagePath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.size(36.dp).clip(CircleShape).background(Color.White.copy(0.2f)).pointerInput(Unit) { detectTapGestures(onTap = { onProfileTap() }) })
        Spacer(Modifier.width(10.dp))
        Text(
            username,
            Modifier.weight(1f),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
        )
        IconButton(onClick = onClose) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
        }
        IconButton(onClick = onMore) {
            Icon(Icons.Filled.MoreVert, contentDescription = null, tint = Color.White)
        }
        if (onChain != null) IconButton(onClick = onChain) { Text("⌁", color = Color.White, fontSize = 22.sp) }
    }
}

/** Equivalente Android de `saveStoryToDevice()` de Swift, dentro de su archivo espejo. */
private suspend fun saveStoryMediaToDevice(context: android.content.Context, story: Story) = withContext(Dispatchers.IO) {
    val isVideo = story.mediaItem.type == MediaItem.MediaType.VIDEO
    val resolver = context.contentResolver
    val mimeType = if (isVideo) "video/mp4" else "image/jpeg"
    val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "Moment_${System.currentTimeMillis()}${if (isVideo) ".mp4" else ".jpg"}")
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "Movies/Moments" else "Pictures/Moments")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
    }
    val target = resolver.insert(collection, values) ?: return@withContext
    try {
        URL(story.mediaItem.url).openStream().use { input ->
            resolver.openOutputStream(target)?.use { output -> input.copyTo(output) } ?: error("No se pudo abrir MediaStore")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            resolver.update(target, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        }
    } catch (error: Exception) {
        resolver.delete(target, null, null)
    }
}
