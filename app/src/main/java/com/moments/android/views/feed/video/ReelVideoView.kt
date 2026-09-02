package com.moments.android.views.feed.video

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.requiredSize
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
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.ui.AspectRatioFrameLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.rawPadding
import com.moments.android.models.Moment
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.checkIfSaved
import com.moments.android.services.firestore.deleteMoment
import com.moments.android.services.firestore.toggleSaveMoment
import com.moments.android.services.performance.VideoMoment
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.services.social.StoryRingResolverService
import com.moments.android.services.video.VideoLayerRole
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.comments.ModernCommentsSheet
import com.moments.android.views.components.CurrentUserVerifiedBadge
import com.moments.android.views.components.MomentCaptionPresentationStyle
import com.moments.android.views.components.MomentCaptionView
import com.moments.android.views.components.RailCountBadge
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
import com.moments.android.coordinators.AsyncProfileImageView
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
    handoffConsumerId: String? = null,
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
    var isSaved by remember(video.moment.id) { mutableStateOf(false) }
    var isSaveLoading by remember { mutableStateOf(false) }
    var scrubProgress by remember { mutableDoubleStateOf(0.0) }
    var progressBarWidthPx by remember { mutableFloatStateOf(0f) }
    var commentsSheetTopFraction by remember { mutableFloatStateOf(0.5f) }
    var hasRenderedFirstFrame by remember(video.id) { mutableStateOf(false) }
    val savedMomentIds by firestore.savedMomentIds.collectAsState()

    val mediaChromePrimary = Color.White
    val mediaChromeSecondary = Color.White.copy(alpha = 0.82f)
    val mediaChromeTertiary = Color.White.copy(alpha = 0.72f)
    val mediaControlBackground = Color(0xFF151D21)
    val bottomBarTertiary = if (isDark) Color.White.copy(0.72f) else Color(0xFF0B1215).copy(0.58f)
    val bottomBarBg = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val bottomBarHeight = 46.dp
    val progressCommentOverlap = 6.dp
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val chromeBottomPadding = navBottom
    val bottomChromeClearance = 2.5.dp + bottomBarHeight + chromeBottomPadding

    val displayAuthorUsername = liveAuthorUsername.trim().ifEmpty { video.moment.username }
    val windowHeightPx = LocalWindowInfo.current.containerSize.height.coerceAtLeast(1)
    val videoScale = if (showComments) commentsSheetTopFraction.coerceIn(0f, 1f) else 1f

    val resizeMode = remember(video.moment.aspectRatio) {
        when (video.moment.aspectRatio) {
            "9:16", "1:1", "4:5" -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        }
    }

    fun setupVideo() {
        playerManager.setupPlayer(video, startAtSeconds, context, handoffConsumerId)
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

    fun checkIfSaved() {
        val userId = uid ?: return
        val momentId = video.moment.id ?: return
        if (savedMomentIds.isNotEmpty()) {
            isSaved = savedMomentIds.contains(momentId)
            return
        }
        scope.launch {
            runCatching { firestore.checkIfSaved(userId, momentId) }
                .onSuccess { isSaved = it }
        }
    }

    fun toggleSave() {
        val userId = uid ?: return
        val momentId = video.moment.id ?: return
        if (isSaveLoading) return
        isSaved = !isSaved
        isSaveLoading = true
        scope.launch {
            val error = runCatching { firestore.toggleSaveMoment(userId, momentId) }.exceptionOrNull()
            isSaveLoading = false
            if (error != null) isSaved = !isSaved
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
            checkIfSaved()
        } else {
            playerManager.pause()
        }
    }

    LaunchedEffect(savedMomentIds, video.moment.id) {
        val momentId = video.moment.id ?: return@LaunchedEffect
        if (savedMomentIds.isNotEmpty()) {
            isSaved = savedMomentIds.contains(momentId)
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
        val posterContentScale =
            if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) ContentScale.Fit else ContentScale.Crop

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = videoScale
                    scaleY = videoScale
                    transformOrigin = TransformOrigin(0.5f, 0f)
                },
        ) {
            if (exo != null) {
                VideoPlayerRepresentable(
                    player = exo,
                    resizeMode = resizeMode,
                    consumerId = playerManager.consumerId.orEmpty().ifEmpty { handoffConsumerId.orEmpty() },
                    layerRole = VideoLayerRole.Reels,
                    onProgress = { /* manager.observePlayback owns progress */ },
                    onBuffering = { playerManager.isBuffering = it },
                    onFirstFrameRendered = { hasRenderedFirstFrame = true },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black))
            }

            // El handoff desde feed conserva el mismo ExoPlayer: no interponer
            // un thumbnail congelado sobre el vídeo durante la expansión.
            if (handoffConsumerId == null) {
                VideoPosterOverlay(
                    posterUrl = video.posterUrlString,
                    isReadyToPlay = playerManager.isLoaded &&
                        exo != null &&
                        hasRenderedFirstFrame,
                    contentScale = posterContentScale,
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
        }

        AnimatedVisibility(
            visible = isDoubleTapAnimating && !showComments,
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

        // Al pausar: mute (pequeño) + play centrado — ≡ iOS Reels.swift.
        AnimatedVisibility(
            visible = playerManager.player != null &&
                !playerManager.isPlaying &&
                !isDraggingProgress &&
                !showComments,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    Modifier
                        .size(28.dp)
                        .momentsChromeGlass(CircleShape, interactive = true, tint = mediaControlBackground)
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
                        tint = mediaChromePrimary,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Box(
                    Modifier
                        .size(64.dp)
                        .momentsChromeGlass(CircleShape, interactive = true, tint = mediaControlBackground)
                        .clickable {
                            HapticManager.shared.lightImpact()
                            playerManager.play()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = stringResource(R.string.feed_video_play),
                        tint = mediaChromePrimary,
                        modifier = Modifier
                            .size(28.dp)
                            .offset(x = 1.dp),
                    )
                }
            }
        }

        // Chrome overlay
        Column(Modifier.fillMaxSize().alpha(if (showComments) 0f else 1f)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Box(
                    Modifier
                        .size(34.dp)
                        .momentsChromeGlass(CircleShape, interactive = true, tint = mediaControlBackground)
                        .clickable {
                            HapticManager.shared.mediumImpact()
                            onClose()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, null, tint = mediaChromePrimary, modifier = Modifier.size(15.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            val gradientHeight = maxOf(300.dp, 293.dp + navBottom)
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
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Column(
                        Modifier
                            .weight(1f)
                            .padding(bottom = bottomChromeClearance + 6.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // ≡ iOS Reels: foto 42 + StorySegmentedRing overlay (sin hueco).
                            val reelAvatarSize = 42.dp
                            val reelRingLine = 2.5.dp
                            val reelRingOuter = reelAvatarSize + reelRingLine + 2.dp
                            Box(
                                Modifier
                                    .size(reelRingOuter)
                                    .clickable {
                                        if (video.moment.authorId.isBlank()) return@clickable
                                        if (hasStory) {
                                            storyRouteUserId = video.moment.authorId
                                        } else {
                                            openProfile()
                                        }
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                AsyncProfileImageView(
                                    userId = video.moment.authorId,
                                    modifier = Modifier
                                        .size(reelAvatarSize)
                                        .clip(CircleShape),
                                )
                                StorySegmentedRing(
                                    storyCount = storyCount,
                                    hasStory = hasStory,
                                    hasUnseenStory = hasUnseenStory,
                                    storyViewedStatus = storyViewedStatus,
                                    storyAudiences = storyAudiences,
                                    isOwnStory = video.moment.authorId == uid,
                                    ringSize = reelAvatarSize,
                                    lineWidth = reelRingLine,
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
                                        color = mediaChromePrimary,
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
                                        color = mediaChromeSecondary,
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
                                                tint = mediaChromeTertiary,
                                                modifier = Modifier.size(9.dp),
                                            )
                                            Text(
                                                loc,
                                                color = mediaChromeTertiary,
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
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(bottom = bottomChromeClearance + 26.dp),
                    ) {
                        EpicReactionButton(
                            moment = feedMoment,
                            showCount = video.moment.authorId == uid || !video.moment.hideLikeCounts,
                            sizeDp = 44f,
                            emojiSizeSp = 22f,
                            pickerXOffset = -110f,
                            chromeOnMedia = true,
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

                        EnhancedReelActionButton(
                            icon = AttachmentIcon.BOOKMARK,
                            vectorIcon = null,
                            count = null,
                            isActive = isSaved,
                            activeColor = Color(0xFFFFCC00),
                            onClick = { toggleSave() },
                        )

                        EnhancedReelActionButton(
                            icon = null,
                            vectorIcon = Icons.Filled.MoreHoriz,
                            count = null,
                            isActive = false,
                            activeColor = mediaChromePrimary,
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
                .fillMaxWidth()
                .alpha(if (showComments) 0f else 1f),
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
                    .padding(top = 6.dp),
            ) {
                if (!video.moment.disableComments) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(40.dp)
                            .clip(RoundedCornerShape(percent = 50))
                            .background(
                                if (isDark) Color.White.copy(0.06f) else Color.Black.copy(0.06f),
                            )
                            .border(
                                1.dp,
                                if (isDark) Color.White.copy(0.10f) else Color.Black.copy(0.10f),
                                RoundedCornerShape(percent = 50),
                            )
                            .clickable { showComments = true }
                            .padding(horizontal = 18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(
                            Icons.Filled.ChatBubble,
                            null,
                            tint = bottomBarTertiary,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            stringResource(R.string.comments_add_placeholder),
                            color = bottomBarTertiary,
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
                onDelete = {
                    showContextMenu = false
                    deleteMoment()
                },
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
            keepBackgroundVisible = true,
            onSheetOffsetChanged = { offsetPx ->
                commentsSheetTopFraction = (offsetPx / windowHeightPx).coerceIn(0f, 1f)
            },
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
    val chromeInk = Color.White
    val controlBackground = Color(0xFF151D21)
    val inactiveTint = chromeInk
    Box(modifier.requiredSize(44.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .scale(if (isPressed) 0.95f else 1f)
                .momentsChromeGlass(CircleShape, interactive = true, tint = controlBackground)
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
                    tintColor = if (isActive) activeColor else inactiveTint,
                    modifier = Modifier.scale(if (isActive) 1.1f else 1f),
                )
                vectorIcon != null -> Icon(
                    vectorIcon,
                    contentDescription = null,
                    tint = if (isActive) activeColor else inactiveTint,
                    modifier = Modifier
                        .size(20.dp)
                        .scale(if (isActive) 1.1f else 1f),
                )
            }
        }

        if (count != null && count > 0) {
            RailCountBadge(
                text = MomentsFormat.count(count, MomentsFormat.CountStyle.SOCIAL_METRIC),
                background = if (isActive) activeColor.copy(alpha = 0.82f) else Color.Gray.copy(alpha = 0.68f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp),
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
