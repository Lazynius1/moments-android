package com.moments.android.views.messaging.media

import android.media.MediaMetadataRetriever
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.utilities.EmojiReactionDefaults
import com.moments.android.utilities.EmojiUsageTracker
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.creator.EmojiPickerView
import com.moments.android.views.creator.StoryMediaLayoutRules
import com.moments.android.views.creator.StoryMediaPresentationMode
import com.moments.android.views.creator.creatoruikit.creatorMomentsCaptureRect
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.ViewOnceConsumptionReason
import com.moments.android.views.messaging.services.ViewOnceConsumptionService
import com.moments.android.views.messaging.services.ViewOnceReplaySessionStore
import com.moments.android.views.messaging.services.markViewOnceAsViewed
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.ScreenshotProtectionMode
import com.moments.android.views.story.StoryRingAvatarView
import com.moments.android.views.story.storyviewer.GlassmorphicStoryVideoPlayer
import com.moments.android.views.story.storyviewer.StoryMediaOverlayRendererView
import com.moments.android.views.story.storyviewer.StoryReactionsStrip
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Port de `ViewOnceImmersiveViewer.swift`.
 * Canvas = `creatorMomentsCaptureRect` (misma geometría story/chat camera).
 */
@Composable
fun ViewOnceImmersiveViewer(
    message: EnhancedMessage,
    authorName: String,
    onViewed: () -> Unit,
    isReplaySession: Boolean = false,
    onReplayConsumed: () -> Unit = {},
    onMediaConsumed: () -> Unit = {},
    onSendReply: (String) -> Unit = {},
    onSendReaction: (String) -> Unit = {},
    onOpenCameraReply: () -> Unit = {},
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var progress by remember { mutableFloatStateOf(0f) }
    var duration by remember { mutableFloatStateOf(5f) }
    var paused by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var hasMarkedAsViewed by remember { mutableStateOf(false) }
    var didHandleDeletion by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var reply by remember { mutableStateOf("") }
    var replyFocused by remember { mutableStateOf(false) }
    var showReactions by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var sentConfirmation by remember { mutableStateOf(false) }
    var imageAspect by remember { mutableFloatStateOf(9f / 16f) }
    var videoAspect by remember { mutableStateOf<Float?>(null) }

    val emojiUsage = remember { EmojiUsageTracker() }
    val reactions = emojiUsage.orderedEmojis(EmojiReactionDefaults.story)
    val isVideo = message.type == MessageType.VIEW_ONCE_VIDEO
    val mediaUrl = message.mediaUrl.orEmpty()
    val relativeTime = remember(message.timestamp) {
        MomentsFormat.relativeTime(message.timestamp, MomentsFormat.RelativeTimeStyle.CONVERSATIONAL)
    }
    val currentAspect = if (isVideo) videoAspect ?: (9f / 16f) else imageAspect

    fun handleDeletionOnClose() {
        if (didHandleDeletion) return
        didHandleDeletion = true
        // ≡ allowReplay && !isReplaySession → no consume (queda replay en sesión)
        if (message.allowReplay == true && !isReplaySession) return
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        if (isReplaySession) {
            if (viewerId.isNotEmpty()) {
                ViewOnceReplaySessionStore.markConsumed(message, viewerId)
                if (!message.hasBeenReplayedBy(viewerId)) {
                    message.replayedBy = message.replayedBy.orEmpty() + viewerId
                }
            }
            onReplayConsumed()
        }
        val reason = if (isReplaySession) {
            ViewOnceConsumptionReason.REPLAY
        } else {
            ViewOnceConsumptionReason.VIEW_ONCE
        }
        // CF exige viewedBy/isViewed para replay; markViewed de la 1ª vista es async → await antes.
        scope.launch(Dispatchers.IO) {
            if (reason == ViewOnceConsumptionReason.REPLAY && viewerId.isNotEmpty()) {
                ChatService.markViewOnceAsViewed(message.conversationId, message.id, viewerId)
            }
            val error = ViewOnceConsumptionService.consumeAwait(
                message.conversationId,
                message.id,
                reason,
            )
            if (error == null) {
                withContext(Dispatchers.Main.immediate) { onMediaConsumed() }
            }
        }
    }

    fun closeViewer() {
        if (closing) return
        closing = true
        handleDeletionOnClose()
        onDismiss()
    }

    fun markAsStarted() {
        if (hasMarkedAsViewed) return
        hasMarkedAsViewed = true
        onViewed()
    }

    fun flashSentConfirmation() {
        HapticManager.shared.lightImpact()
        sentConfirmation = true
    }

    fun sendReplyText() {
        val trimmed = reply.trim()
        if (trimmed.isEmpty()) return
        onSendReply(trimmed)
        reply = ""
        focusManager.clearFocus()
        flashSentConfirmation()
    }

    fun sendReaction(emoji: String) {
        emojiUsage.increment(emoji)
        onSendReaction(emoji)
        showReactions = false
        flashSentConfirmation()
    }

    BackHandler { closeViewer() }

    LaunchedEffect(message.id) {
        GlobalVideoManager.pauseAllVideos()
        markAsStarted()
    }

    LaunchedEffect(mediaUrl, isVideo) {
        if (mediaUrl.isBlank()) return@LaunchedEffect
        if (isVideo) {
            val meta = withContext(Dispatchers.IO) { detectVideoMeta(mediaUrl) }
            meta.first?.let { duration = it.coerceAtLeast(0.1f) }
            videoAspect = meta.second
        }
    }

    DisposableEffect(message.id) {
        onDispose { handleDeletionOnClose() }
    }

    // ≡ Timer.publish 0.1s
    LaunchedEffect(paused, closing, showEmojiPicker) {
        while (!paused && !closing && !showEmojiPicker) {
            delay(100)
            progress = (progress + 0.1f).let { if (it >= duration) 0f else it }
        }
    }

    LaunchedEffect(sentConfirmation) {
        if (sentConfirmation) {
            delay(1400)
            sentConfirmation = false
        }
    }

    val imeBottom = WindowInsets.ime.getBottom(density)
    val keyboardVisible = imeBottom > 0
    val navBottom = WindowInsets.navigationBars.getBottom(density).toFloat()
    val statusTop = WindowInsets.statusBars.getTop(density).toFloat()

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0B1215))
            .offset { IntOffset(0, dragOffset.roundToInt()) },
    ) {
        val screenW = constraints.maxWidth.toFloat()
        val screenH = constraints.maxHeight.toFloat()
        val baseCanvas = creatorMomentsCaptureRect(
            inSize = Size(screenW, screenH),
            topInsetPx = statusTop,
            bottomInsetPx = navBottom,
            density = density,
        )
        // ≡ iOS: canvas y shifted by resolvedTopInset
        val canvasLeft = baseCanvas.left
        val canvasTop = baseCanvas.top + statusTop
        val canvasW = baseCanvas.width
        val canvasH = baseCanvas.height
        val canvasMidX = canvasLeft + canvasW / 2f
        val canvasMidY = canvasTop + canvasH / 2f
        val progressY = maxOf(statusTop + with(density) { 1.dp.toPx() }, canvasTop - with(density) { 26.dp.toPx() })
        val corner = storyViewerCanvasCornerRadius
        val bottomChromeH = with(density) { 170.dp.toPx() }
        val bottomPad = if (keyboardVisible) {
            with(density) { (imeBottom.toFloat() + 8.dp.toPx()).toDp() }
        } else {
            with(density) { (maxOf(navBottom, 16.dp.toPx()) + 8.dp.toPx()).toDp() }
        }

        val dragBlocked = replyFocused || keyboardVisible || showReactions || showEmojiPicker

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(dragBlocked, screenH) {
                    detectDragGestures(
                        onDrag = { change, amount ->
                            if (dragBlocked) {
                                dragOffset = 0f
                                return@detectDragGestures
                            }
                            // ≡ shouldHandleDismissDrag: start above bottom chrome
                            if (change.position.y >= screenH - bottomChromeH) {
                                dragOffset = 0f
                                return@detectDragGestures
                            }
                            if (amount.y > 0f) {
                                dragOffset = (dragOffset + amount.y).coerceAtLeast(0f)
                                change.consume()
                            }
                        },
                        onDragEnd = {
                            if (dragOffset > 100f) closeViewer() else dragOffset = 0f
                        },
                    )
                },
        ) {
            // Media canvas
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (canvasMidX - canvasW / 2f).roundToInt(),
                            (canvasMidY - canvasH / 2f).roundToInt(),
                        )
                    }
                    .size(
                        width = with(density) { canvasW.toDp() },
                        height = with(density) { canvasH.toDp() },
                    )
                    .clip(RoundedCornerShape(corner))
                    .background(Color.Black)
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                paused = true
                                tryAwaitRelease()
                                if (!replyFocused && !showReactions && !showEmojiPicker) {
                                    paused = false
                                }
                            },
                        )
                    },
            ) {
                ScreenshotProtectedView(
                    isProtected = true,
                    fillsContainer = true,
                    // View-once: FLAG_SECURE (vídeo + imagen). ContentSurface parpadea con ExoPlayer.
                    mode = ScreenshotProtectionMode.WindowFlag,
                ) {
                    ViewOnceMediaCanvas(
                        message = message,
                        mediaUrl = mediaUrl,
                        isVideo = isVideo,
                        isPaused = paused || closing || showEmojiPicker,
                        mediaAspect = currentAspect,
                        onImageAspect = { imageAspect = it },
                        onVideoLoopReset = {
                            if (progress > duration - 0.4f) progress = 0f
                        },
                    )
                }
            }

            // Progress above canvas
            ViewOnceProgressBar(
                progress = if (duration > 0f) (progress / duration).coerceIn(0f, 1f) else 0f,
                modifier = Modifier
                    .offset {
                        IntOffset(
                            (screenW / 2f - canvasW / 2f + with(density) { 12.dp.toPx() }).roundToInt(),
                            (progressY - with(density) { 1.25.dp.toPx() }).roundToInt(),
                        )
                    }
                    .width(with(density) { (canvasW - 24.dp.toPx()).toDp() }),
            )

            // Header on canvas top
            Row(
                Modifier
                    .offset {
                        IntOffset(
                            (canvasLeft + with(density) { 16.dp.toPx() }).roundToInt(),
                            (canvasTop + with(density) { 6.dp.toPx() }).roundToInt(),
                        )
                    }
                    .width(with(density) { (canvasW - 32.dp.toPx()).toDp() }),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (message.senderId.isNotEmpty()) {
                    StoryRingAvatarView(
                        userId = message.senderId,
                        size = 42.dp,
                        lineWidth = 2.1.dp,
                        showBaseStroke = true,
                        baseStrokeColor = Color.White.copy(0.16f),
                        baseStrokeWidth = 1.dp,
                    )
                } else {
                    Box(
                        Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(0.1f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Person, null, tint = Color.White.copy(0.72f), modifier = Modifier.size(17.dp))
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        authorName,
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                    )
                    Text(relativeTime, color = Color.White.copy(0.58f), fontSize = 13.sp)
                }
                ViewerCircleButton(Icons.Filled.Close, stringResource(R.string.view_once_close), ::closeViewer)
            }

            // Bottom chrome
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = bottomPad),
                verticalArrangement = Arrangement.Bottom,
            ) {
                AnimatedVisibility(
                    visible = showReactions,
                    enter = scaleIn(initialScale = 0.85f) + fadeIn(),
                    exit = scaleOut(targetScale = 0.85f) + fadeOut(),
                ) {
                    Column {
                        StoryReactionsStrip(
                            reactions = reactions,
                            showReactions = true,
                            onReaction = ::sendReaction,
                            onMoreReactions = { showEmojiPicker = true },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicTextField(
                        value = reply,
                        onValueChange = { reply = it },
                        singleLine = false,
                        maxLines = 3,
                        textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { sendReplyText() }),
                        modifier = Modifier
                            .weight(1f)
                            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                            .onFocusChanged { state ->
                                replyFocused = state.isFocused
                                paused = state.isFocused || showReactions || showEmojiPicker
                            }
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        decorationBox = { inner ->
                            if (reply.isEmpty()) {
                                Text(
                                    stringResource(R.string.view_once_reply_placeholder),
                                    color = Color.White.copy(0.45f),
                                    fontSize = 14.sp,
                                )
                            }
                            inner()
                        },
                    )
                    if (reply.isBlank()) {
                        ViewerCircleButton(
                            Icons.Filled.SentimentSatisfied,
                            stringResource(R.string.view_once_reactions),
                        ) {
                            showReactions = !showReactions
                            paused = showReactions || replyFocused
                        }
                        ViewerCircleButton(
                            Icons.Filled.CameraAlt,
                            stringResource(R.string.view_once_camera_reply),
                        ) {
                            onOpenCameraReply()
                            closeViewer()
                        }
                    } else {
                        ViewerCircleButton(
                            Icons.Filled.Send,
                            stringResource(R.string.view_once_send_reply),
                            ::sendReplyText,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = sentConfirmation,
                modifier = Modifier.align(Alignment.Center),
                enter = scaleIn(initialScale = 0.85f) + fadeIn(),
                exit = fadeOut(),
            ) {
                Row(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
                        .background(Color.Black.copy(0.55f), RoundedCornerShape(percent = 50))
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.CheckCircle, null, tint = Color.White, modifier = Modifier.size(16.dp))
                    Text(
                        stringResource(R.string.view_once_reply_sent),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }

    if (showEmojiPicker) {
        MomentsModalSheet(
            onDismissRequest = {
                showEmojiPicker = false
                if (!replyFocused && !showReactions) paused = false
            },
            largeOnly = false,
        ) {
            EmojiPickerView(
                onDismiss = {
                    showEmojiPicker = false
                    if (!replyFocused && !showReactions) paused = false
                },
                onSelect = { emoji ->
                    showEmojiPicker = false
                    sendReaction(emoji)
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ViewOnceMediaCanvas(
    message: EnhancedMessage,
    mediaUrl: String,
    isVideo: Boolean,
    isPaused: Boolean,
    mediaAspect: Float,
    onImageAspect: (Float) -> Unit,
    onVideoLoopReset: () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
        val canvasAspect = constraints.maxWidth.toFloat() / constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val presentation = StoryMediaLayoutRules.presentationMode(mediaAspect, canvasAspect)
        val contentScale = when (presentation) {
            StoryMediaPresentationMode.FILL -> ContentScale.Crop
            StoryMediaPresentationMode.FIT_WITH_BLUR -> ContentScale.Fit
        }

        if (presentation == StoryMediaPresentationMode.FIT_WITH_BLUR && mediaUrl.isNotBlank()) {
            AsyncImage(
                model = message.thumbnailUrl ?: mediaUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(36.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.35f)))
        }

        when {
            mediaUrl.isBlank() && isVideo -> {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Icon(Icons.Filled.VideocamOff, null, tint = Color.White.copy(0.5f), modifier = Modifier.size(40.dp))
                    Text(
                        stringResource(R.string.chat_video_unavailable),
                        color = Color.White.copy(0.5f),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            isVideo -> {
                GlassmorphicStoryVideoPlayer(
                    url = mediaUrl,
                    isPlaying = !isPaused,
                    onReadyToPlayChanged = {},
                    isMutedExternally = false,
                    shouldLoop = true,
                    onProgressUpdate = { fraction ->
                        if (fraction < 0.05f) onVideoLoopReset()
                    },
                    onVideoComplete = {},
                    contentScaleFit = presentation == StoryMediaPresentationMode.FIT_WITH_BLUR,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            else -> {
                val painter = rememberAsyncImagePainter(mediaUrl)
                LaunchedEffect(painter.state) {
                    val state = painter.state
                    if (state is AsyncImagePainter.State.Success) {
                        val size = state.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f) {
                            onImageAspect(size.width / size.height)
                        }
                    }
                }
                Image(
                    painter = painter,
                    contentDescription = null,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        StoryMediaOverlayRendererView(
            textOverlays = message.resolvedTextOverlays,
            stickers = message.resolvedStickers,
            drawingData = message.drawingData,
            storyId = message.id,
            userId = message.senderId,
            reportsDeckInteractionExclusion = false,
            allowsStickerHitTesting = false,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/** ≡ `ViewOnceImmersiveViewer.StoryProgressBar`. */
@Composable
private fun ViewOnceProgressBar(progress: Float, modifier: Modifier) {
    Box(modifier.height(2.5.dp).background(Color.White.copy(0.15f), RoundedCornerShape(percent = 50))) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.5.dp)
                .background(Color(0xFFFFCC33), RoundedCornerShape(percent = 50)),
        )
    }
}

@Composable
private fun ViewerCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    action: () -> Unit,
) = Box(
    Modifier
        .size(40.dp)
        .momentsChromeGlass(CircleShape, interactive = true)
        .clickable(onClick = action),
    contentAlignment = Alignment.Center,
) {
    Icon(icon, description, tint = Color.White, modifier = Modifier.size(16.dp))
}

/** durationSeconds to aspectRatio (w/h). */
private fun detectVideoMeta(url: String): Pair<Float?, Float?> {
    return runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(url, HashMap())
            val durMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 0f
            val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 0f
            val duration = durMs?.let { it / 1000f }
            val aspect = if (h > 0f) w / h else null
            duration to aspect
        }
    }.getOrDefault(null to null)
}
