package com.moments.android.views.messaging.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.ChatEphemeralTimeFormatting
import com.moments.android.views.messaging.components.ChatQuickReactionsBar
import com.moments.android.views.messaging.components.NormalVideoPlayerView
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.shared.MomentsVideoGravity
import com.moments.android.views.shared.MomentsVideoPlaybackTimeline
import com.moments.android.views.shared.MomentsVideoPlayer
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.story.StoryRingAvatarView
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Port de `FullScreenMediaView` en `ConversationSettingsView.swift` (~1874–2490).
 * Paging, header autor/tiempo, ephemeral countdown, reply chrome, drag-dismiss,
 * save feedback, screenshot-protect. Video expand AVPlayer nativo iOS → FeedVideoPage.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ConversationFullScreenMediaView(
    media: SharedMedia,
    mediaItems: List<SharedMedia> = emptyList(),
    currentUserId: String,
    otherParticipantName: String,
    displayReactions: ((String) -> Map<String, List<String>>?)? = null,
    onReaction: ((String, String) -> Unit)? = null,
    onMoreReactions: ((String) -> Unit)? = null,
    onClose: () -> Unit,
    onSendReply: (SharedMedia, String, (Result<Unit>) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val paged = remember(media.id, mediaItems) {
        if (mediaItems.isEmpty()) listOf(media) else mediaItems
    }
    val start = paged.indexOfFirst { it.id == media.id }.coerceAtLeast(0)
    val pagerState = rememberPagerState(initialPage = start, pageCount = { paged.size })
    val current = paged.getOrElse(pagerState.currentPage) { media }

    var replyText by remember { mutableStateOf("") }
    var isSendingReply by remember { mutableStateOf(false) }
    var showSaveResult by remember { mutableStateOf(false) }
    var saveResultMessage by remember { mutableStateOf("") }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showingReactionBarForMessageId by remember { mutableStateOf<String?>(null) }
    var ephemeralRemaining by remember { mutableLongStateOf(0L) }
    var expandedVideoUrl by remember { mutableStateOf<String?>(null) }

    val isOwn = current.senderId == currentUserId
    val authorName = if (isOwn) stringResource(R.string.chat_reply_you) else otherParticipantName
    val relativeTime = remember(current.id, current.timestamp) {
        MomentsFormat.relativeTime(current.timestamp, MomentsFormat.RelativeTimeStyle.CONVERSATIONAL)
    }
    val isEphemeral = current.sourceMessage?.type == MessageType.EPHEMERAL || !current.allowsSaving
    val ephemeralAccent = Color(0xFFFFCC33)
    val primaryOverlay = if (colors.isDark) Color.White else Color.Black
    val secondaryOverlay = primaryOverlay.copy(alpha = 0.58f)
    val canSend = replyText.trim().isNotEmpty() && !isSendingReply

    // Sin esto, el back del sistema hace pop del overlay Messaging → Feed.
    BackHandler {
        if (expandedVideoUrl != null) {
            expandedVideoUrl = null
        } else {
            onClose()
        }
    }

    fun isScreenshotProtected(item: SharedMedia): Boolean =
        item.sourceMessage?.isVanishModeMessage == true ||
            item.sourceMessage?.type == MessageType.EPHEMERAL ||
            !item.allowsSaving

    fun restartEphemeralCountdown() {
        val expiration = current.sourceMessage?.expirationDate ?: run {
            ephemeralRemaining = 0L
            return
        }
        ephemeralRemaining = ((expiration.time - System.currentTimeMillis()) / 1000L).coerceAtLeast(0L)
    }

    LaunchedEffect(current.id) {
        showingReactionBarForMessageId = null
        restartEphemeralCountdown()
    }
    LaunchedEffect(current.id, isEphemeral) {
        if (!isEphemeral) return@LaunchedEffect
        while (isActive && ephemeralRemaining > 0L) {
            delay(1000)
            ephemeralRemaining = (ephemeralRemaining - 1).coerceAtLeast(0L)
        }
    }
    DisposableEffect(Unit) { onDispose { } }

    fun sendReply() {
        val text = replyText.trim()
        if (text.isEmpty() || isSendingReply) return
        isSendingReply = true
        onSendReply(current, text) { result ->
            isSendingReply = false
            result.onSuccess { replyText = "" }
            result.onFailure {
                saveResultMessage = it.message.orEmpty().ifBlank {
                    context.getString(R.string.common_error)
                }
                showSaveResult = true
            }
        }
    }

    // ≡ iOS: ZStack(background + media) + safeAreaInset top/bottom → media entre header y reply
    Column(
        modifier
            .fillMaxSize()
            .background(colors.chatBackground.first())
            .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars))
            .offset { IntOffset(0, dragOffset.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (abs(dragOffset) > 120f) onClose() else dragOffset = 0f
                    },
                    onDragCancel = { dragOffset = 0f },
                    onVerticalDrag = { _, amount ->
                        dragOffset = (dragOffset + amount).coerceAtLeast(0f)
                    },
                )
            },
    ) {
        // Header ≡ headerView / avatarView (StoryRingAvatarView)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = primaryOverlay)
            }
            if (current.senderId.isNotEmpty()) {
                StoryRingAvatarView(
                    userId = current.senderId,
                    size = 40.dp,
                    lineWidth = 2.dp,
                    showBaseStroke = true,
                    baseStrokeColor = if (colors.isDark) {
                        Color.White.copy(alpha = 0.16f)
                    } else {
                        Color.Black.copy(alpha = 0.12f)
                    },
                    baseStrokeWidth = 1.dp,
                )
            } else {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(primaryOverlay.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = null,
                        tint = primaryOverlay.copy(alpha = 0.62f),
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            Column(Modifier.padding(start = 8.dp).weight(1f)) {
                Text(
                    authorName,
                    color = primaryOverlay,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (isEphemeral && ephemeralRemaining > 0L) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, null, tint = ephemeralAccent, modifier = Modifier.size(12.dp))
                        Text(
                            stringResource(
                                R.string.stories_expires_in,
                                ChatEphemeralTimeFormatting.shortLabel(ephemeralRemaining),
                            ),
                            color = ephemeralAccent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(start = 4.dp),
                            maxLines = 1,
                        )
                    }
                } else {
                    Text(relativeTime, color = secondaryOverlay, fontSize = 11.sp, maxLines = 1)
                }
            }
            if (current.allowsSaving) {
                IconButton(
                    onClick = {
                        saveConversationMediaToGallery(context, current) { ok, message ->
                            saveResultMessage = message
                            showSaveResult = true
                        }
                    },
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = stringResource(R.string.conversation_settings_save_media),
                        tint = primaryOverlay,
                    )
                }
            }
        }

        // Media entre header y reply (≡ safeAreaInset iOS)
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                userScrollEnabled = paged.size > 1 && showingReactionBarForMessageId == null && expandedVideoUrl == null,
            ) { page ->
                val item = paged[page]
                val active = page == pagerState.currentPage
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val body: @Composable () -> Unit = {
                        when (item.type) {
                            SharedMedia.Type.IMAGE -> AsyncImage(
                                item.originalUrl,
                                contentDescription = null,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp)),
                                contentScale = ContentScale.Fit,
                            )
                            SharedMedia.Type.VIDEO -> ConversationFullScreenVideoPage(
                                videoUrl = item.originalUrl,
                                isActive = active,
                                primaryOverlay = primaryOverlay,
                                onExpand = {
                                    HapticManager.shared.lightImpact()
                                    expandedVideoUrl = item.originalUrl
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(18.dp)),
                            )
                        }
                    }
                    if (isScreenshotProtected(item)) {
                        ScreenshotProtectedView(isProtected = true, fillsContainer = true) { body() }
                    } else {
                        body()
                    }
                }
            }

            if (showingReactionBarForMessageId == current.id && onReaction != null) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.001f))
                        .clickable { showingReactionBarForMessageId = null },
                    contentAlignment = Alignment.Center,
                ) {
                    ChatQuickReactionsBar(
                        onReaction = { emoji ->
                            onReaction(current.id, emoji)
                            showingReactionBarForMessageId = null
                        },
                        onMore = {
                            onMoreReactions?.invoke(current.id)
                            showingReactionBarForMessageId = null
                        },
                    )
                }
            }
        }

        // Reply composer ≡ replyComposer
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(22.dp))
                .momentsChromeGlass(RoundedCornerShape(22.dp), interactive = true)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = replyText,
                onValueChange = { replyText = it },
                placeholder = {
                    Text(stringResource(R.string.conversation_settings_reply), color = secondaryOverlay)
                },
                modifier = Modifier.weight(1f),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = primaryOverlay,
                    unfocusedTextColor = primaryOverlay,
                    cursorColor = primaryOverlay,
                ),
                singleLine = true,
            )
            IconButton(onClick = ::sendReply, enabled = canSend) {
                if (isSendingReply) {
                    CircularProgressIndicator(
                        color = primaryOverlay,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp),
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.conversation_settings_send),
                        tint = if (canSend) primaryOverlay else primaryOverlay.copy(alpha = 0.32f),
                    )
                }
            }
        }
    }

    if (showSaveResult) {
        AlertDialog(
            onDismissRequest = { showSaveResult = false },
            title = { Text(stringResource(R.string.conversation_settings_media_save_title)) },
            text = { Text(saveResultMessage) },
            confirmButton = {
                Text(
                    stringResource(R.string.common_ok),
                    modifier = Modifier
                        .clickable { showSaveResult = false }
                        .padding(16.dp),
                )
            },
        )
    }

    // ≡ iOS `showExpandedVideo` / ModalVideoPlayer
    expandedVideoUrl?.let { url ->
        NormalVideoPlayerView(
            videoUrl = url,
            onClose = { expandedVideoUrl = null },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * ≡ iOS `videoControlsOverlay` + AVPlayer embebido en FullScreenMediaView.
 * Play/pause centro, mute+expand arriba derecha, timeline abajo.
 */
@Composable
private fun ConversationFullScreenVideoPage(
    videoUrl: String,
    isActive: Boolean,
    primaryOverlay: Color,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPaused by remember(videoUrl) { mutableStateOf(false) }
    var isMuted by remember(videoUrl) { mutableStateOf(false) }
    var currentTime by remember(videoUrl) { mutableStateOf(0.0) }
    var duration by remember(videoUrl) { mutableStateOf(0.0) }
    var externalSeekTime by remember(videoUrl) { mutableStateOf<Double?>(null) }
    val effectivePaused = !isActive || isPaused

    Box(modifier) {
        MomentsVideoPlayer(
            url = videoUrl,
            isLooping = true,
            isPaused = effectivePaused,
            isMuted = isMuted,
            prioritizeSmoothPlayback = true,
            videoGravity = MomentsVideoGravity.RESIZE_ASPECT_FILL,
            onDurationReceived = { duration = maxOf(it, 0.0) },
            onProgressUpdate = { if (!effectivePaused) currentTime = maxOf(it, 0.0) },
            externalSeekTime = externalSeekTime,
            onExternalSeekConsumed = { externalSeekTime = null },
            modifier = Modifier.fillMaxSize(),
        )

        // Centro play/pause
        Box(
            Modifier
                .align(Alignment.Center)
                .size(64.dp)
                .clip(CircleShape)
                .momentsChromeGlass(CircleShape, interactive = true)
                .clickable {
                    HapticManager.shared.lightImpact()
                    isPaused = !isPaused
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                contentDescription = null,
                tint = primaryOverlay,
                modifier = Modifier.size(28.dp),
            )
        }

        // Mute + expand (arriba derecha)
        Row(
            Modifier
                .align(Alignment.TopEnd)
                .padding(12.dp)
                .clip(RoundedCornerShape(50))
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = {
                    HapticManager.shared.lightImpact()
                    isMuted = !isMuted
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                    contentDescription = null,
                    tint = primaryOverlay,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(16.dp)
                    .background(primaryOverlay.copy(alpha = 0.2f)),
            )
            IconButton(
                onClick = {
                    isPaused = true
                    onExpand()
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(
                    Icons.Default.Fullscreen,
                    contentDescription = null,
                    tint = primaryOverlay,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        MomentsVideoPlaybackTimeline(
            currentTime = currentTime,
            duration = duration,
            horizontalPadding = 18.dp,
            onSeek = { target ->
                currentTime = target
                externalSeekTime = target
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
        )
    }
}

internal fun saveConversationMediaToGallery(
    context: android.content.Context,
    media: SharedMedia,
    onResult: (Boolean, String) -> Unit,
) {
    kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
        val mime = if (media.type == SharedMedia.Type.VIDEO) "video/mp4" else "image/jpeg"
        val collection = if (media.type == SharedMedia.Type.VIDEO) {
            android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val result = runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, "moments_${media.id}")
                put(android.provider.MediaStore.MediaColumns.MIME_TYPE, mime)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/Moments")
                }
            }
            val destination = context.contentResolver.insert(collection, values)
                ?: error("insert failed")
            val sourceUri = android.net.Uri.parse(media.originalUrl)
            val source = if (sourceUri.scheme == "content" || sourceUri.scheme == "file") {
                context.contentResolver.openInputStream(sourceUri)
            } else {
                java.net.URL(media.originalUrl).openStream()
            }
            source.use { input ->
                context.contentResolver.openOutputStream(destination)?.use { output ->
                    input?.copyTo(output) ?: error("no stream")
                } ?: error("no output")
            }
            true to context.getString(R.string.conversation_settings_media_save_success)
        }.getOrElse {
            false to (it.message ?: context.getString(R.string.common_error))
        }
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
            onResult(result.first, result.second)
        }
    }
}
