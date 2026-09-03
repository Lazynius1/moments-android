package com.moments.android.views.feed.moments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.moments.android.R
import com.moments.android.models.MediaItem
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.performance.FeedVisibilityCoordinator
import com.moments.android.services.performance.VideoMoment
import com.moments.android.services.performance.VideoMomentsIndex
import com.moments.android.services.performance.isReelsAspectFormat
import com.moments.android.services.performance.toIndexMoment
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.video.VideoLayerLease
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.video.FeedReelsExpandOverlay
import com.moments.android.views.feed.video.FeedReelsPresentation
import com.moments.android.views.feed.video.FeedVideoEndedOverlay
import com.moments.android.views.feed.video.FeedVideoPage
import com.moments.android.views.feed.video.LiveVideoTimeLabel
import com.moments.android.views.feed.video.LocalFeedReelsHost
import com.moments.android.views.feed.video.ReelsViewer
import com.moments.android.views.feed.video.ReelsBadgeOverlay
import com.moments.android.views.feed.video.VideoPosterOverlay
import com.moments.android.views.feed.video.switchVideoSurfaceToFeed
import com.moments.android.views.shared.PhotoTagOverlayView
import kotlinx.coroutines.launch

private val MediaCorner = RoundedCornerShape(MomentCarouselLayoutRules.mediaCornerRadius)

/**
 * Port de `EnhancedCarouselView` (FeedMomentComponents.swift).
 * Páginas = `MediaItemView`; vídeos = `CroppedVideoPlayer`.
 */
@Composable
fun MomentMediaCarousel(
    moment: FeedMoment,
    consumerId: String,
    modifier: Modifier = Modifier,
    /** Si false, el caller aplica clip/shadow (como el card del feed). */
    applyOwnChrome: Boolean = true,
    showTags: Boolean = false,
    onToggleTags: () -> Unit = {},
    isImmersive: Boolean = false,
    onImmersiveChange: (Boolean) -> Unit = {},
    onPageChange: (Int) -> Unit = {},
    onTagTap: ((String) -> Unit)? = null,
    /** Altura fija (iOS cardHeight). Si null, usa aspectRatio. */
    fixedHeight: Dp? = null,
    /**
     * iOS EnhancedCarouselView recibe `mediaItems` del PostCard (ya resueltos).
     * Si null → visibleMediaItems del moment.
     */
    mediaItemsOverride: List<FeedMediaItem>? = null,
    /**
     * Sesión Reels de esta superficie.
     * Vacío (detalle) → `VideoMomentsIndex`, como iOS `reelsVideos == nil`.
     */
    reelsVideos: List<VideoMoment> = emptyList(),
) {
    // iOS: mediaItems pasados desde ModernPostCardView
    val mediaItems = mediaItemsOverride ?: moment.visibleMediaItems
    val pagerState = rememberPagerState(pageCount = { mediaItems.size.coerceAtLeast(1) })
    // iOS EnhancedCarouselView pasa `aspectRatio` del card (detected), no el de cada página
    val rawRatio = MomentCarouselLayoutRules.aspectRatioValue(moment.aspectRatio)
    val canvasAspectRatio = MomentCarouselLayoutRules.feedDisplayAspectRatio(rawRatio)
    val isCarousel = mediaItems.size > 1
    var showReelsViewer by remember { mutableStateOf(false) }
    var reelsStartSeconds by remember { mutableFloatStateOf(0f) }
    var reelsHandoffItem by remember { mutableStateOf<FeedMediaItem?>(null) }
    var reelsResumeId by remember { mutableStateOf("") }
    var reelsSourceRect by remember { mutableStateOf(Rect.Zero) }
    var isPreparingReelsDismiss by remember { mutableStateOf(false) }
    var localReelsVideos by remember { mutableStateOf<List<VideoMoment>>(emptyList()) }
    var localReelsStartIndex by remember { mutableIntStateOf(0) }
    // iOS MediaItemView.freezeReelsSession — cola de la superficie; si falta el moment, no caer a 0.
    fun resolveReelsSession(): Pair<List<VideoMoment>, Int>? {
        val videos = if (reelsVideos.isNotEmpty()) {
            reelsVideos
        } else {
            val indexed = VideoMomentsIndex.videoMoments.value
            if (indexed.isNotEmpty()) {
                indexed
            } else {
                val alone = moment.toIndexMoment()
                val url = alone.previewVideoURLString ?: alone.videoUrl
                if (!url.isNullOrBlank()) listOf(VideoMoment(alone)) else emptyList()
            }
        }
        if (videos.isEmpty()) return null
        val idx = videos.indexOfFirst { it.moment.id == moment.id }
        if (idx >= 0) return videos to idx
        val alone = moment.toIndexMoment()
        val url = alone.previewVideoURLString ?: alone.videoUrl
        if (url.isNullOrBlank()) return null
        return listOf(VideoMoment(alone)) to 0
    }
    val reelsHost = LocalFeedReelsHost.current

    LaunchedEffect(pagerState.currentPage) {
        onPageChange(pagerState.currentPage)
    }

    val chromeModifier = if (applyOwnChrome) {
        Modifier
            .shadow(8.dp, MediaCorner, clip = false)
            .clip(MediaCorner)
            .background(FeedInk.copy(alpha = 0.05f))
    } else {
        Modifier.background(FeedInk.copy(alpha = 0.05f))
    }

    val sizeModifier = if (fixedHeight != null) {
        Modifier.fillMaxWidth().height(fixedHeight)
    } else {
        Modifier.fillMaxWidth().aspectRatio(canvasAspectRatio)
    }

    fun prepareReelsDismiss() {
        if (isPreparingReelsDismiss) return
        isPreparingReelsDismiss = true
        val handoff = reelsHandoffItem
        val resumeId = reelsResumeId.ifBlank {
            if (handoff != null) {
                GlobalVideoManager.profileVideoConsumerId(moment, handoff)
            } else {
                GlobalVideoManager.profileVideoConsumerId(moment)
            }
        }
        GlobalVideoManager.capturePlaybackPosition(resumeId)
        moment.id.takeIf { it.isNotBlank() }?.let { FeedVisibilityCoordinator.pinActiveVideo(it) }
    }

    fun finishReelsHandoff() {
        val handoff = reelsHandoffItem
        val resumeId = reelsResumeId.ifBlank {
            if (handoff != null) {
                GlobalVideoManager.profileVideoConsumerId(moment, handoff)
            } else {
                GlobalVideoManager.profileVideoConsumerId(moment)
            }
        }
        switchVideoSurfaceToFeed(resumeId)
        VideoLayerLease.returnToFeed()
        GlobalVideoManager.clearPlaybackFinished(resumeId)
        moment.id.takeIf { it.isNotBlank() }?.let { FeedVisibilityCoordinator.pinActiveVideo(it) }
        GlobalVideoManager.playVideo(resumeId)
        GlobalVideoManager.completeReelsFeedHandoff(moment, handoff)
        if (reelsHost == null) {
            showReelsViewer = false
        }
        reelsHandoffItem = null
        reelsResumeId = ""
        isPreparingReelsDismiss = false
    }

    fun openReelsViewer(item: FeedMediaItem, pageConsumerId: String) {
        val session = resolveReelsSession() ?: return
        val (sessionVideos, startIndex) = session
        val handoffMedia = if (isCarousel) item else null
        GlobalVideoManager.markReelsFeedHandoff(moment, handoffMedia)
        if (!VideoLayerLease.beginReels(pageConsumerId)) {
            GlobalVideoManager.completeReelsFeedHandoff(moment, handoffMedia)
            return
        }
        GlobalVideoManager.capturePlaybackPosition(pageConsumerId)
        GlobalVideoManager.pauseAllVideos(except = pageConsumerId)
        reelsHandoffItem = handoffMedia
        reelsResumeId = pageConsumerId
        reelsStartSeconds = GlobalVideoManager.playbackPosition(pageConsumerId).toFloat()
        val presentation = FeedReelsPresentation(
            videos = sessionVideos,
            startIndex = startIndex,
            startSeconds = reelsStartSeconds.toDouble(),
            sourceRectInWindow = reelsSourceRect,
            handoffConsumerId = pageConsumerId,
            onWillDismiss = { prepareReelsDismiss() },
            onClosed = { finishReelsHandoff() },
        )
        if (reelsHost != null) {
            reelsHost.present(presentation)
        } else {
            localReelsVideos = sessionVideos
            localReelsStartIndex = startIndex
            showReelsViewer = true
        }
    }

    FeedReelsExpandOverlay(
        visible = showReelsViewer,
        sourceRectInWindow = reelsSourceRect,
        onWillDismiss = { prepareReelsDismiss() },
        onDismissed = { finishReelsHandoff() },
    ) { collapse ->
        ReelsViewer(
            videos = localReelsVideos,
            startIndex = localReelsStartIndex,
            initialStartSeconds = reelsStartSeconds.toDouble(),
            handoffConsumerId = reelsResumeId,
            onClose = collapse,
        )
    }

    Box(
        modifier
            .then(sizeModifier)
            .then(chromeModifier)
            .onGloballyPositioned { reelsSourceRect = it.boundsInWindow() },
    ) {
        if (mediaItems.isEmpty()) {
            Box(Modifier.fillMaxSize().background(FeedInk.copy(alpha = 0.08f)))
        } else if (!isCarousel) {
            // iOS EnhancedCarouselView: una sola media → MediaItemView, sin pager.
            val item = mediaItems.first()
            val pageConsumerId = GlobalVideoManager.feedVideoConsumerId(
                moment = moment,
                item = item,
                prefersUnifiedCarouselFrame = false,
            )
            MediaItemView(
                item = item,
                moment = moment,
                consumerId = pageConsumerId,
                canvasAspectRatio = canvasAspectRatio,
                prefersUnifiedCarouselFrame = false,
                showTags = showTags,
                onToggleTags = onToggleTags,
                isImmersive = isImmersive,
                allowsVideoPlayback = true,
                onTagTap = onTagTap,
                onOpenReels = { openReelsViewer(item, pageConsumerId) },
            )
        } else {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                // iOS: allowsVideoPlayback && index == currentIndex
                val item = mediaItems[page]
                val pageConsumerId = GlobalVideoManager.feedVideoConsumerId(
                    moment = moment,
                    item = item,
                    prefersUnifiedCarouselFrame = true,
                )
                MediaItemView(
                    item = item,
                    moment = moment,
                    consumerId = pageConsumerId,
                    canvasAspectRatio = canvasAspectRatio,
                    prefersUnifiedCarouselFrame = true,
                    showTags = showTags,
                    onToggleTags = onToggleTags,
                    isImmersive = isImmersive,
                    allowsVideoPlayback = page == pagerState.currentPage,
                    onTagTap = onTagTap,
                    onOpenReels = {
                        openReelsViewer(item, pageConsumerId)
                    },
                )
            }
            AnimatedVisibility(
                visible = !isImmersive,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                MomentCarouselPageIndicators(
                    count = mediaItems.size,
                    currentIndex = pagerState.currentPage,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }
    }
}

/**
 * Port de `MediaItemView` (FeedMomentComponents.swift).
 */
@Composable
private fun MediaItemView(
    item: FeedMediaItem,
    moment: FeedMoment,
    consumerId: String,
    canvasAspectRatio: Float,
    prefersUnifiedCarouselFrame: Boolean,
    showTags: Boolean,
    onToggleTags: () -> Unit,
    isImmersive: Boolean,
    allowsVideoPlayback: Boolean,
    onTagTap: ((String) -> Unit)?,
    onOpenReels: () -> Unit,
) {
    if (item.isHiddenByModeration) {
        ModeratedMediaItemView(item = item)
        return
    }

    // iOS @State loadedAspectRatio + resolvedItemAspectRatio
    var loadedAspectRatio by remember(item.id, item.url) { mutableStateOf<Float?>(null) }
    val resolvedItemAspectRatio = remember(loadedAspectRatio, item.aspectRatio, canvasAspectRatio) {
        val loaded = loadedAspectRatio
        when {
            loaded != null && loaded.isFinite() && loaded > 0f -> loaded
            else -> item.resolvedAspectRatioValue?.takeIf { it.isFinite() && it > 0f }
                ?: canvasAspectRatio
        }
    }
    // iOS usesBlurredFitLayout
    val usesBlurredFitLayout = prefersUnifiedCarouselFrame &&
        MomentCarouselLayoutRules.presentationMode(resolvedItemAspectRatio, canvasAspectRatio) ==
        MomentCarouselPresentationMode.FitWithBlur
    val tags = item.tags.orEmpty()
    // iOS CroppedVideoPlayer.isReelsFormat (chrome del card; no filtra apertura)
    val isReelsFormat = moment.isReelsAspectFormat(canvasAspectRatio)

    Box(
        Modifier.fillMaxSize(),
    ) {
        if (!prefersUnifiedCarouselFrame) {
            // iOS: RoundedRectangle.fill(.ultraThinMaterial) cuando no es carousel unificado
            Box(Modifier.fillMaxSize().background(FeedInk.copy(alpha = 0.08f)))
        }

        if (item.type == "video") {
            CroppedVideoPlayer(
                item = item,
                moment = moment,
                consumerId = consumerId,
                usesBlurredFitLayout = usesBlurredFitLayout,
                isReelsFormat = isReelsFormat,
                allowsVideoPlayback = allowsVideoPlayback,
                isImmersive = isImmersive,
                onTap = {
                    // iOS MediaItemView → CroppedVideoPlayer.onTap
                    if (tags.isNotEmpty()) {
                        onToggleTags()
                    } else {
                        onOpenReels()
                    }
                },
            )
        } else {
            // iOS image branch + onSuccess → loadedAspectRatio
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        enabled = tags.isNotEmpty(),
                        onClick = onToggleTags,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (usesBlurredFitLayout) {
                    CarouselMediaBackdropView(item = item)
                }
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(item.url)
                        .size(1080, 1920)
                        .build(),
                    contentDescription = moment.username,
                    contentScale = if (usesBlurredFitLayout) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { state: AsyncImagePainter.State.Success ->
                        val size = state.painter.intrinsicSize
                        if (size.width > 0f && size.height > 0f && size.width.isFinite() && size.height.isFinite()) {
                            val ratio = size.width / size.height
                            if (ratio.isFinite() && ratio > 0f) {
                                loadedAspectRatio = ratio
                            }
                        }
                    },
                )
            }
        }

        if (tags.isNotEmpty()) {
            PhotoTagOverlayView(
                tags = tags,
                isVisible = showTags,
                onTagTap = onTagTap,
            )
        }
    }
}

/**
 * Port de `CroppedVideoPlayer.videoPosterFallback`.
 */
@Composable
private fun CroppedVideoPoster(
    posterUrl: String?,
    onTap: (() -> Unit)? = null,
) {
    val modifier = if (onTap != null) {
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onTap,
            )
    } else {
        Modifier.fillMaxSize()
    }
    Box(modifier) {
        if (!posterUrl.isNullOrBlank()) {
            VideoPosterOverlay(
                posterUrl = posterUrl,
                isReadyToPlay = false,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
        }
    }
}

/**
 * Port de `CroppedVideoPlayer` (FeedMomentComponents.swift).
 */
@Composable
private fun CroppedVideoPlayer(
    item: FeedMediaItem,
    moment: FeedMoment,
    consumerId: String,
    usesBlurredFitLayout: Boolean,
    isReelsFormat: Boolean,
    allowsVideoPlayback: Boolean,
    isImmersive: Boolean,
    onTap: () -> Unit,
) {
    // iOS videoPosterURLString(for:) + item.thumbnailUrl fallback
    val posterUrl = GlobalVideoManager.videoPosterUrl(moment, item)
        ?: item.thumbnailUrl
        ?: moment.thumbnailUrl
        ?: moment.imagePath
    val domainMediaItem = remember(item) { item.toDomainMediaItem() }
    val playbackUrl = remember(domainMediaItem) {
        VideoPlaybackSelector.source(forItem = domainMediaItem)?.playbackUrl ?: item.url
    }
    val totalDuration = item.videoDuration ?: moment.videoDuration
    val finishedIds by GlobalVideoManager.finishedPlaybackIdsFlow.collectAsState()
    val hasFinishedPlayback = finishedIds.contains(consumerId)
    val activeMomentId by FeedVisibilityCoordinator.activeVideoMomentIdFlow.collectAsState()
    val warmingMomentId by FeedVisibilityCoordinator.warmingVideoMomentIdFlow.collectAsState()
    val shouldMountPlayer = allowsVideoPlayback && (
        GlobalVideoManager.visibilityMatches(activeMomentId, consumerId) ||
            GlobalVideoManager.visibilityMatches(warmingMomentId, consumerId)
        )
    // iOS mute overlay siempre si allowsVideoPlayback (código); comment dice hide immersive —
    // seguimos el código: visible cuando allowsVideoPlayback. FeedVideoPage hideMute en immersive
    // para no tapear mute encima de Reels. Oculto si el clip ya terminó (overlay “Ver otra vez”).
    val showMute = allowsVideoPlayback && !isImmersive && !hasFinishedPlayback

    Box(Modifier.fillMaxSize()) {
        when {
            !allowsVideoPlayback -> {
                CroppedVideoPoster(posterUrl = posterUrl)
            }
            usesBlurredFitLayout -> {
                CarouselMediaBackdropView(item = item)
                if (shouldMountPlayer) {
                    FeedVideoPage(
                        url = playbackUrl,
                        thumbnailUrl = posterUrl,
                        consumerId = consumerId,
                        mediaItem = domainMediaItem,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(vertical = 10.dp, horizontal = 6.dp),
                        allowsPlayback = true,
                        allowsPauseInteraction = true,
                        showMute = showMute,
                        onTap = null,
                    )
                } else {
                    CroppedVideoPoster(posterUrl = posterUrl, onTap = onTap)
                }
                AnimatedVisibility(
                    visible = !isImmersive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopEnd),
                ) {
                    LiveVideoTimeLabel(
                        consumerId = consumerId,
                        totalDuration = totalDuration,
                        modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                    )
                }
            }
            isReelsFormat -> {
                // iOS: ModernVideoPlayer(allowsPauseInteraction: false) + clear Button(onTap)
                if (shouldMountPlayer) {
                    FeedVideoPage(
                        url = playbackUrl,
                        thumbnailUrl = posterUrl,
                        consumerId = consumerId,
                        mediaItem = domainMediaItem,
                        modifier = Modifier.fillMaxSize(),
                        allowsPlayback = true,
                        allowsPauseInteraction = false,
                        showMute = showMute,
                        onTap = onTap,
                    )
                } else {
                    CroppedVideoPoster(posterUrl = posterUrl, onTap = onTap)
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color(0x000B1215), Color(0x4D0B1215)),
                            ),
                        ),
                )
                AnimatedVisibility(
                    visible = !isImmersive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        Row(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(start = 12.dp, top = 12.dp)
                                .clip(RoundedCornerShape(percent = 50))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color(0xFFAF52DE).copy(0.8f), Color(0xFFFF2D55).copy(0.8f)),
                                    ),
                                )
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(10.dp),
                            )
                            Text(
                                stringResource(R.string.feed_reels_badge),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        LiveVideoTimeLabel(
                            consumerId = consumerId,
                            totalDuration = totalDuration,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 12.dp, end = 12.dp),
                        )
                    }
                }
            }
            else -> {
                // iOS horizontal videos branch
                if (shouldMountPlayer) {
                    FeedVideoPage(
                        url = playbackUrl,
                        thumbnailUrl = posterUrl,
                        consumerId = consumerId,
                        mediaItem = domainMediaItem,
                        modifier = Modifier.fillMaxSize(),
                        allowsPlayback = true,
                        allowsPauseInteraction = true,
                        showMute = showMute,
                        onTap = null,
                    )
                } else {
                    CroppedVideoPoster(posterUrl = posterUrl, onTap = onTap)
                }
                AnimatedVisibility(
                    visible = !isImmersive,
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(Modifier.fillMaxSize()) {
                        LiveVideoTimeLabel(
                            consumerId = consumerId,
                            totalDuration = totalDuration,
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp),
                        )
                        Icon(
                            Icons.Filled.OpenInFull,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 8.dp)
                                .background(Color(0xFF0B1215).copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(6.dp)
                                .size(14.dp),
                        )
                    }
                }
            }
        }
        if (allowsVideoPlayback && hasFinishedPlayback) {
            FeedVideoEndedOverlay(
                onWatchAgain = { GlobalVideoManager.replayFromStart(consumerId) },
            )
        }
    }
}

/** Port de `CarouselMediaBackdropView` (FeedMomentComponents.swift). */
@Composable
private fun CarouselMediaBackdropView(item: FeedMediaItem) {
    val url = when {
        item.type == "image" -> item.url
        !item.thumbnailUrl.isNullOrBlank() -> item.thumbnailUrl
        else -> null
    }
    Box(Modifier.fillMaxSize()) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // iOS .saturation(0.9)
                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setToSaturation(0.9f) }),
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        } else {
            Box(Modifier.fillMaxSize().background(FeedInk.copy(alpha = 0.12f)))
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Transparent,
                            Color.Black.copy(alpha = 0.22f),
                        ),
                    ),
                ),
        )
    }
}

/** Port de `ModeratedMediaItemView` (FeedMomentComponents.swift). */
@Composable
fun ModeratedMediaItemView(
    item: FeedMediaItem,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val desaturate = ColorMatrixColorFilter(ColorMatrix().apply { setToSaturation(0f) })
    val backdropUrl = when {
        item.type == "image" -> item.url
        !item.thumbnailUrl.isNullOrBlank() -> item.thumbnailUrl
        else -> null
    }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (backdropUrl != null) {
            AsyncImage(
                model = backdropUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = desaturate,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(20.dp),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.18f)))
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.4f)),
            )
        }

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.52f), Color.Black.copy(alpha = 0.36f)),
                    ),
                ),
        )

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 24.dp),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.92f),
                modifier = Modifier.size(24.dp),
            )
            Text(
                stringResource(R.string.media_moderation_hidden_title),
                color = Color.White,
                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.media_moderation_hidden_subtitle),
                color = Color.White.copy(alpha = 0.78f),
                fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** FeedMediaItem → MediaItem de dominio (variants + poster) para adaptive playback. */
private fun FeedMediaItem.toDomainMediaItem(): MediaItem = MediaItem(
    id = id,
    type = MediaItem.MediaType.from(type),
    url = url,
    aspectRatio = aspectRatio,
    thumbnailUrl = thumbnailUrl,
    videoDuration = videoDuration,
    videoVariants = videoVariants,
    hlsMasterUrl = hlsMasterUrl,
    tags = tags,
)
