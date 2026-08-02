package com.moments.android.views.feed.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.ui.AspectRatioFrameLayout
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.rawPadding
import com.moments.android.models.Moment
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.deleteMoment
import com.moments.android.services.performance.VideoMoment
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.social.StoryRingResolverService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.comments.ModernCommentsSheet
import com.moments.android.views.components.CurrentUserVerifiedBadge
import com.moments.android.views.components.MomentCaptionPresentationStyle
import com.moments.android.views.components.MomentCaptionView
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.feed.reactions.EpicReactionButton
import com.moments.android.views.feed.reactions.ReactionType
import com.moments.android.views.feed.sharing.ModernShareBottomSheet
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.profile.momentsview.ModernContextMenuOverlay
import com.moments.android.views.story.StoriesView
import com.moments.android.views.story.StorySegmentedRing
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.roundToInt

/**
 * Port de `ReelVideoView` (Reels.swift L120–886).
 * Chrome, gestos, scrub, comments/share/context, story ring, double-tap feel.
 */
@Composable
fun ReelVideoView(
    video: VideoMoment,
    isCurrentVideo: Boolean,
    startAtSeconds: Double,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val playerManager = remember { ReelVideoPlayerManager() }
    val firestore = remember { FirestoreService() }
    val isDark = isSystemInDarkTheme()
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    val feedMoment = remember(video.moment.id, video.mediaIndex) { video.moment.toFeedMomentForReels() }

    var showComments by remember { mutableStateOf(false) }
    var commentCount by remember { mutableIntStateOf(video.moment.commentCount) }
    var isDoubleTapAnimating by remember { mutableStateOf(false) }
    var showContextMenu by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var storyRouteUserId by remember { mutableStateOf<String?>(null) }
    var hasStory by remember { mutableStateOf(false) }
    var hasUnseenStory by remember { mutableStateOf(false) }
    var storyCount by remember { mutableIntStateOf(0) }
    var storyViewedStatus by remember { mutableStateOf<List<Boolean>>(emptyList()) }
    var storyAudiences by remember { mutableStateOf<List<String?>>(emptyList()) }
    var liveAuthorUsername by remember { mutableStateOf("") }
    var isDraggingProgress by remember { mutableStateOf(false) }
    var wasPlayingBeforeDrag by remember { mutableStateOf(false) }
    var isReelCaptionExpanded by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableDoubleStateOf(0.0) }
    var progressBarWidthPx by remember { mutableFloatStateOf(0f) }

    val chromePrimary = if (isDark) Color.White else Color(0xFF0B1215)
    val chromeSecondary = if (isDark) Color.White.copy(0.78f) else Color(0xFF0B1215).copy(0.72f)
    val chromeTertiary = if (isDark) Color.White.copy(0.72f) else Color(0xFF0B1215).copy(0.58f)
    val bottomBarBg = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    // ≡ iOS bottomBarHeight 68 + nav inset − overlap (-6) → caption/acciones encima del progress.
    val bottomBarHeight = 68.dp
    val progressCommentOverlap = 6.dp
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val bottomChromeClearance = bottomBarHeight + navBottom - progressCommentOverlap + 8.dp

    val displayAuthorUsername = liveAuthorUsername.trim().ifEmpty { video.moment.username }

    val resizeMode = remember(video.moment.aspectRatio) {
        when (video.moment.aspectRatio) {
            "9:16", "1:1", "4:5" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun setupVideo() {
        playerManager.setupPlayer(video, startAtSeconds, context)
    }

    fun loadCommentCount() {
        val momentId = video.moment.id ?: return
        scope.launch {
            runCatching {
                val snap = FirebaseFirestore.getInstance()
                    .collection("users").document(video.moment.authorId)
                    .collection("moments").document(momentId)
                    .collection("comments")
                    .get()
                    .await()
                commentCount = snap.size()
            }
        }
    }

    fun checkUserStories() {
        val viewerId = uid ?: return
        val authorId = video.moment.authorId
        if (authorId.isBlank()) return
        scope.launch {
            val snapshot = StoryRingResolverService.resolve(
                viewerId = viewerId,
                authorId = authorId,
                privacyService = PrivacyService,
            )
            hasStory = snapshot.hasStory
            hasUnseenStory = snapshot.hasUnseenStory
            storyCount = snapshot.storyCount
            storyViewedStatus = snapshot.storyViewedStatus
            storyAudiences = snapshot.storyAudiences
        }
    }

    fun refreshAuthorUsername() {
        val authorId = video.moment.authorId.trim()
        if (authorId.isEmpty()) {
            liveAuthorUsername = ""
            return
        }
        UserCacheService.refreshUser(authorId) { user ->
            val fetched = user?.username?.trim().orEmpty()
            if (video.moment.authorId.trim() == authorId) {
                liveAuthorUsername = fetched
            }
        }
    }

    fun handleDoubleTap() {
        HapticManager.shared.heavyImpact()
        isDoubleTapAnimating = true
        val momentId = video.moment.id
        val userId = uid
        if (momentId.isNullOrBlank() || userId.isNullOrBlank()) {
            scope.launch {
                delay(1000)
                isDoubleTapAnimating = false
            }
            return
        }
        scope.launch {
            runCatching {
                firestore.addReaction(
                    momentId = momentId,
                    reaction = ReactionType.Feel.rawValue,
                    userId = userId,
                    authorId = video.moment.authorId,
                )
            }
            delay(1000)
            isDoubleTapAnimating = false
        }
    }

    fun deleteMoment() {
        val momentId = video.moment.id ?: return
        showContextMenu = false
        scope.launch {
            runCatching {
                firestore.deleteMoment(userId = video.moment.authorId, momentId = momentId)
                LocalPersistenceService.deleteMoment(momentId)
                onClose()
            }
        }
    }

    fun openProfile() {
        val authorId = video.moment.authorId
        if (authorId.isBlank()) return
        val me = uid
        if (me != null && me == authorId) {
            NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToOwnProfileTab)
        } else {
            NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToUserProfileInFeed(authorId))
        }
    }

    LaunchedEffect(isCurrentVideo, video.id) {
        if (isCurrentVideo) {
            setupVideo()
            loadCommentCount()
            checkUserStories()
            refreshAuthorUsername()
        } else {
            playerManager.pause()
        }
    }

    LaunchedEffect(video.moment.id) {
        isReelCaptionExpanded = false
    }

    DisposableEffect(Unit) {
        onDispose { playerManager.cleanup() }
    }

    Box(modifier.background(Color.Black)) {
        val exo = playerManager.player
        if (exo != null) {
            VideoPlayerRepresentable(
                player = exo,
                resizeMode = resizeMode,
                onProgress = { /* manager.observePlayback owns progress */ },
                onBuffering = { playerManager.isBuffering = it },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            ReelLoadingPlaceholder(
                isLoaded = playerManager.isLoaded,
                isBuffering = playerManager.isBuffering,
                imagePath = video.moment.imagePath,
                onStartBuffering = { playerManager.isBuffering = true },
            )
        }

        // Capa de gestos (tap play/pause, double-tap feel)
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            HapticManager.shared.lightImpact()
                            playerManager.togglePlayback()
                        },
                        onDoubleTap = { handleDoubleTap() },
                    )
                },
        )

        AnimatedVisibility(
            visible = isDoubleTapAnimating,
            enter = scaleIn(initialScale = 0.1f) + fadeIn(),
            exit = scaleOut(targetScale = 1.5f) + fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = Color(0xFFFF2D55),
                modifier = Modifier.size(80.dp),
            )
        }

        // Chrome overlay
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable {
                                HapticManager.shared.mediumImpact()
                                onClose()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Close, null, tint = chromePrimary, modifier = Modifier.size(16.dp))
                    }
                    Box(
                        Modifier
                            .size(38.dp)
                            .momentsChromeGlass(CircleShape, interactive = true)
                            .clickable {
                                HapticManager.shared.lightImpact()
                                playerManager.toggleMute()
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (playerManager.isMuted) Icons.Filled.VolumeOff else Icons.Filled.VolumeUp,
                            contentDescription = stringResource(
                                if (playerManager.isMuted) R.string.feed_video_unmute
                                else R.string.feed_video_mute,
                            ),
                            tint = chromePrimary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            // Gradiente: misma área útil que iOS (300−22) + clearance del comment/progress.
            val gradientHeight = 300.dp - 22.dp + bottomChromeClearance
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(gradientHeight)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(0.2f), Color.Black.copy(0.78f)),
                        ),
                    ),
            ) {
                Row(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        // ≡ iOS overlay comment bar: subir chrome por encima del progress + surface.
                        .padding(bottom = bottomChromeClearance),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(42.dp)
                                    .clickable {
                                        if (video.moment.authorId.isBlank()) return@clickable
                                        if (hasStory) {
                                            storyRouteUserId = video.moment.authorId
                                        } else {
                                            openProfile()
                                        }
                                    },
                            ) {
                                AsyncProfileImageView(
                                    userId = video.moment.authorId,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(CircleShape),
                                )
                                StorySegmentedRing(
                                    storyCount = storyCount,
                                    hasStory = hasStory,
                                    hasUnseenStory = hasUnseenStory,
                                    storyViewedStatus = storyViewedStatus,
                                    storyAudiences = storyAudiences,
                                    isOwnStory = video.moment.authorId == uid,
                                    ringSize = 42.dp,
                                    lineWidth = 2.5.dp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Row(
                                    Modifier.clickable(onClick = ::openProfile),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        displayAuthorUsername,
                                        color = chromePrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (video.moment.authorId == uid) {
                                        CurrentUserVerifiedBadge(size = 14.dp)
                                    } else {
                                        VerifiedBadgeView(userId = video.moment.authorId, size = 14.dp)
                                    }
                                }
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        MomentsFormat.relativeTime(from = video.moment.timestamp),
                                        color = chromeSecondary,
                                        fontSize = 12.sp,
                                    )
                                    val loc = video.moment.location
                                    if (!loc.isNullOrBlank()) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Icon(
                                                Icons.Filled.LocationOn,
                                                null,
                                                tint = chromeTertiary,
                                                modifier = Modifier.size(9.dp),
                                            )
                                            Text(
                                                loc,
                                                color = chromeTertiary,
                                                fontSize = 12.sp,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        MomentCaptionView(
                            content = video.moment.content,
                            onHashtagTap = {},
                            style = MomentCaptionPresentationStyle.Reels,
                            moment = video.moment,
                            isReelsCaptionExpanded = isReelCaptionExpanded,
                            onReelsCaptionExpandedChange = { isReelCaptionExpanded = it },
                            modifier = Modifier.rawPadding(start = (-12).dp),
                        )
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(18.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = 6.dp),
                    ) {
                        EpicReactionButton(
                            moment = feedMoment,
                            showCount = video.moment.authorId == uid || !video.moment.hideLikeCounts,
                            sizeDp = 56f,
                            emojiSizeSp = 28f,
                            pickerXOffset = -110f,
                        )

                        if (!video.moment.disableComments) {
                            EnhancedReelActionButton(
                                icon = null,
                                vectorIcon = Icons.Filled.ChatBubble,
                                count = commentCount,
                                isActive = commentCount > 0,
                                activeColor = Color(0xFF007AFF),
                                onClick = { showComments = true },
                            )
                        }

                        val aud = video.moment.audience?.trim()?.lowercase().orEmpty()
                        val isEveryone = aud.isEmpty() || aud == "everyone"
                        if (video.moment.allowSharing && isEveryone) {
                            EnhancedReelActionButton(
                                icon = AttachmentIcon.SHARE,
                                vectorIcon = null,
                                count = null,
                                isActive = false,
                                activeColor = Color(0xFF34C759),
                                onClick = { showShareSheet = true },
                            )
                        }

                        EnhancedReelActionButton(
                            icon = null,
                            vectorIcon = Icons.Filled.MoreHoriz,
                            count = null,
                            isActive = false,
                            activeColor = Color.White,
                            onClick = { showContextMenu = !showContextMenu },
                        )
                    }
                }
            }
        }

        // Progress pegado a la surface de comentario (≡ iOS VStack spacing: -6).
        // Vídeo edge-to-edge: sin navigationBarsPadding en el stack; el inset va dentro del bar.
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy((-progressCommentOverlap)),
        ) {
            if (playerManager.duration > 0) {
                val displayProgress =
                    if (isDraggingProgress) scrubProgress else playerManager.progress
                val barHeight = if (isDraggingProgress) 6.dp else 2.5.dp
                val density = LocalDensity.current
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(30.dp)
                        .onSizeChanged { progressBarWidthPx = it.width.toFloat() }
                        .pointerInput(playerManager.duration) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    HapticManager.shared.lightImpact()
                                    wasPlayingBeforeDrag = playerManager.isPlaying
                                    playerManager.pause()
                                    isDraggingProgress = true
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val p = (offset.x / w).toDouble().coerceIn(0.0, 1.0)
                                    scrubProgress = p
                                    playerManager.updateProgress(p)
                                    playerManager.seekToProgress(p)
                                },
                                onDragEnd = {
                                    isDraggingProgress = false
                                    playerManager.seekToProgress(scrubProgress, precise = true)
                                    if (wasPlayingBeforeDrag) playerManager.play()
                                },
                                onDragCancel = {
                                    isDraggingProgress = false
                                    if (wasPlayingBeforeDrag) playerManager.play()
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val w = size.width.toFloat().coerceAtLeast(1f)
                                    val p = (change.position.x / w).toDouble().coerceIn(0.0, 1.0)
                                    scrubProgress = p
                                    playerManager.updateProgress(p)
                                    playerManager.seekToProgress(p)
                                },
                            )
                        },
                    contentAlignment = Alignment.BottomStart,
                ) {
                    // ≡ iOS frame height 12 — barra visual abajo, hit area 30.
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(12.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .background(Color.White.copy(0.24f)),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth(displayProgress.toFloat().coerceIn(0f, 1f))
                                .height(barHeight)
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFF4158D0), Color(0xFFC850C0)),
                                    ),
                                ),
                        )
                        if (isDraggingProgress) {
                            val thumbPx = with(density) { 12.dp.toPx() }
                            val x = ((progressBarWidthPx * displayProgress.toFloat()) - thumbPx / 2f)
                                .roundToInt()
                            Box(
                                Modifier
                                    .offset { IntOffset(x = x, y = 0) }
                                    .size(12.dp)
                                    .background(Color.White, CircleShape),
                            )
                        }
                    }
                }
            }

            // reelCommentBar — fondo AdaptiveColors hasta el borde (safe area); padding inset interno.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(bottomBarBg)
                    .navigationBarsPadding()
                    .height(bottomBarHeight)
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp, bottom = 2.dp),
            ) {
                if (!video.moment.disableComments) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.06f),
                            )
                            .clickable { showComments = true }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.ChatBubble,
                            null,
                            tint = chromeTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            stringResource(R.string.comments_add_placeholder),
                            color = chromeTertiary,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showContextMenu,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ModernContextMenuOverlay(
                moment = feedMoment,
                isPresented = showContextMenu,
                onPresentedChange = { showContextMenu = it },
                onEdit = {},
                onDelete = { showDeleteAlert = true },
                onReport = {},
            )
        }

        AnimatedVisibility(
            visible = showShareSheet,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            ModernShareBottomSheet(
                moment = feedMoment,
                onDismiss = { showShareSheet = false },
            )
        }
    }

    if (showComments) {
        ModernCommentsSheet(
            moment = feedMoment,
            onDismiss = {
                showComments = false
                loadCommentCount()
            },
            onOpenStory = { userId ->
                showComments = false
                storyRouteUserId = userId
            },
            onOpenProfile = { userId ->
                showComments = false
                val me = uid
                if (me != null && me == userId) {
                    NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToOwnProfileTab)
                } else {
                    NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToUserProfileInFeed(userId))
                }
            },
        )
    }

    storyRouteUserId?.let { storyUid ->
        Dialog(
            onDismissRequest = { storyRouteUserId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            StoriesView(
                startWithUserId = storyUid,
                onDismiss = { storyRouteUserId = null },
            )
        }
    }

    if (showDeleteAlert) {
        AlertDialog(
            onDismissRequest = { showDeleteAlert = false },
            title = { Text(stringResource(R.string.reels_delete_title)) },
            text = { Text(stringResource(R.string.reels_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteAlert = false
                    deleteMoment()
                }) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAlert = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReelLoadingPlaceholder(
    isLoaded: Boolean,
    isBuffering: Boolean,
    imagePath: String?,
    onStartBuffering: () -> Unit,
) {
    LaunchedEffect(Unit) { onStartBuffering() }
    val infinite = rememberInfiniteTransition(label = "reelLoad")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1000, easing = LinearEasing), RepeatMode.Restart),
        label = "spin",
    )

    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (!imagePath.isNullOrBlank()) {
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(3.dp),
                alpha = 0.2f,
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            Box(Modifier.size(50.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = Color.White.copy(0.2f),
                        style = Stroke(width = 2.dp.toPx()),
                    )
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotation },
                ) {
                    drawArc(
                        brush = Brush.linearGradient(listOf(Color.White, Color.White.copy(0.3f))),
                        startAngle = -90f,
                        sweepAngle = 288f,
                        useCenter = false,
                        style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
                    )
                }
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(
                        if (isLoaded) R.string.feed_reels_video_starting
                        else R.string.feed_reels_video_loading,
                    ),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
                if (isBuffering) {
                    Text(
                        stringResource(R.string.feed_reels_video_optimizing),
                        color = Color.White.copy(0.6f),
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Port de `EnhancedReelActionButton` (Reels.swift L1066–1139).
 */
@Composable
fun EnhancedReelActionButton(
    icon: AttachmentIcon?,
    vectorIcon: ImageVector?,
    count: Int?,
    isActive: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isPressed by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .scale(if (isPressed) 0.95f else 1f)
                .momentsChromeGlass(CircleShape, interactive = true)
                .drawBehind {
                    if (isActive) {
                        drawCircle(
                            color = activeColor.copy(alpha = 0.55f),
                            style = Stroke(width = 1.8.dp.toPx()),
                        )
                    }
                }
                .clickable {
                    HapticManager.shared.mediumImpact()
                    isPressed = true
                    onClick()
                    scope.launch {
                        delay(100)
                        isPressed = false
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                icon != null -> AttachmentIconView(
                    icon = icon,
                    preset = AttachmentIconPreset.REELS_SIDEBAR,
                    tintColor = if (isActive) activeColor else Color.White,
                    modifier = Modifier.scale(if (isActive) 1.1f else 1f),
                )
                vectorIcon != null -> Icon(
                    vectorIcon,
                    contentDescription = null,
                    tint = if (isActive) activeColor else Color.White,
                    modifier = Modifier
                        .size(24.dp)
                        .scale(if (isActive) 1.1f else 1f),
                )
            }
        }

        if (count != null && count > 0) {
            Text(
                MomentsFormat.count(count, MomentsFormat.CountStyle.SOCIAL_METRIC),
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .background(Color.White.copy(0.15f), RoundedCornerShape(percent = 50))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/** Moment → FeedMoment para sheets/botones del chrome de Reels. */
internal fun Moment.toFeedMomentForReels(): FeedMoment {
    val media = (mediaItems ?: emptyList()).mapIndexed { index, item ->
        FeedMediaItem(
            id = item.id.ifBlank { "$index" },
            type = item.type.raw,
            url = item.url,
            thumbnailUrl = item.thumbnailUrl,
            aspectRatio = item.aspectRatio,
            isHiddenByModeration = item.isHiddenByModeration,
            tags = item.tags,
            videoDuration = item.videoDuration,
        )
    }.ifEmpty {
        buildList {
            imagePath?.takeIf { it.isNotBlank() }?.let {
                add(FeedMediaItem(id = "img", type = "image", url = it, thumbnailUrl = null, aspectRatio = aspectRatio))
            }
            videoUrl?.takeIf { it.isNotBlank() }?.let {
                add(FeedMediaItem(id = "vid", type = "video", url = it, thumbnailUrl = thumbnailUrl, aspectRatio = aspectRatio))
            }
        }
    }
    return FeedMoment(
        id = id.orEmpty(),
        authorId = authorId,
        username = username,
        content = content,
        timestamp = timestamp.time,
        profileImagePath = profileImagePath,
        location = location,
        mediaItems = media,
        aspectRatio = aspectRatio,
        commentCount = commentCount,
        reactionCount = reactions.values.sumOf { it.size },
        hideLikeCounts = hideLikeCounts,
        disableComments = disableComments,
        allowSharing = allowSharing,
        hasHiddenLayers = hasHiddenLayers,
        hiddenLayerCount = hiddenLayerCount,
        audience = audience,
        customListId = customListId,
        isArchived = isArchived,
        locationCoordinate = locationCoordinate,
        thumbnailUrl = thumbnailUrl,
        imagePath = imagePath,
        videoDuration = videoDuration,
    )
}
