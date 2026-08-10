package com.moments.android.views.feed.moments

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ColorMatrixColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import com.moments.android.R
import com.moments.android.models.MediaItem
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.performance.VideoMoment
import com.moments.android.services.performance.isReelsAspectFormat
import com.moments.android.services.performance.toIndexMoment
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.video.FeedVideoPage
import com.moments.android.views.feed.video.LiveVideoTimeLabel
import com.moments.android.views.feed.video.ReelsViewer
import com.moments.android.views.feed.video.ReelsBadgeOverlay
import com.moments.android.views.shared.PhotoTagOverlayView

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
     * Sesión Reels de esta superficie (feed / perfil / explore…).
     * iOS `MediaItemView.reelsVideos` — nunca VideoMomentsIndex.shared.
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
    // iOS MediaItemView.resolvedReelsVideos / resolvedReelsStartIndex — solo 9:16
    val resolvedReelsVideos = remember(reelsVideos, moment.id, moment.mediaItems, canvasAspectRatio) {
        val surface = if (reelsVideos.isNotEmpty()) {
            reelsVideos
        } else if (moment.isReelsAspectFormat(canvasAspectRatio)) {
            val alone = moment.toIndexMoment()
            val url = alone.previewVideoURLString ?: alone.videoUrl
            if (!url.isNullOrBlank()) listOf(VideoMoment(alone)) else emptyList()
        } else {
            emptyList()
        }
        surface.filter { it.moment.isReelsAspectFormat() }
    }
    val resolvedReelsStartIndex = remember(resolvedReelsVideos, moment.id) {
        resolvedReelsVideos.indexOfFirst { it.moment.id == moment.id }.takeIf { it >= 0 } ?: 0
    }

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

    fun dismissReelsViewer() {
        showReelsViewer = false
        onImmersiveChange(false)
        // iOS fullScreenCover onDismiss: completeReelsFeedHandoff + playVideo
        GlobalVideoManager.completeReelsFeedHandoff(moment, reelsHandoffItem)
        val resumeId = if (reelsHandoffItem != null) {
            GlobalVideoManager.profileVideoConsumerId(moment, reelsHandoffItem!!)
        } else {
            GlobalVideoManager.profileVideoConsumerId(moment)
        }
        GlobalVideoManager.playVideo(resumeId)
        reelsHandoffItem = null
    }

    if (showReelsViewer) {
        // iOS MediaItemView.fullScreenCover → ReelsViewer
        Dialog(
            onDismissRequest = { dismissReelsViewer() },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                // Vídeo edge-to-edge (safe area), como momentos / stories.
                decorFitsSystemWindows = false,
            ),
        ) {
            ReelsViewer(
                videos = resolvedReelsVideos,
                startIndex = resolvedReelsStartIndex,
                initialStartSeconds = reelsStartSeconds.toDouble(),
                onClose = { dismissReelsViewer() },
            )
        }
    }

    Box(
        modifier
            .then(sizeModifier)
            .then(chromeModifier),
    ) {
        if (mediaItems.isEmpty()) {
            Box(Modifier.fillMaxSize().background(FeedInk.copy(alpha = 0.08f)))
        } else {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                // iOS: allowsVideoPlayback && index == currentIndex
                val item = mediaItems[page]
                val allowsVideoPlayback = !isCarousel || page == pagerState.currentPage
                val pageConsumerId = GlobalVideoManager.feedVideoConsumerId(
                    moment = moment,
                    item = item,
                    prefersUnifiedCarouselFrame = isCarousel,
                )
                MediaItemView(
                    item = item,
                    moment = moment,
                    consumerId = pageConsumerId,
                    canvasAspectRatio = canvasAspectRatio,
                    prefersUnifiedCarouselFrame = isCarousel,
                    showTags = showTags,
                    onToggleTags = onToggleTags,
                    isImmersive = isImmersive,
                    allowsVideoPlayback = allowsVideoPlayback,
                    onTagTap = onTagTap,
                    onOpenReels = {
                        // iOS: solo 9:16 abre ReelsViewer
                        if (moment.isReelsAspectFormat(canvasAspectRatio)) {
                            val handoffMedia = if (isCarousel) item else null
                            GlobalVideoManager.capturePlaybackPosition(pageConsumerId)
                            GlobalVideoManager.markReelsFeedHandoff(moment, handoffMedia)
                            GlobalVideoManager.pauseAllVideos()
                            onImmersiveChange(true)
                            reelsHandoffItem = handoffMedia
                            reelsStartSeconds = GlobalVideoManager.playbackPosition(pageConsumerId).toFloat()
                            showReelsViewer = true
                        }
                    },
                )
            }
            AnimatedVisibility(
                visible = mediaItems.size > 1 && !isImmersive,
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
    // iOS CroppedVideoPlayer.isReelsFormat → Moment.isReelsAspectFormat (solo 9:16)
    val isReelsFormat = moment.isReelsAspectFormat(canvasAspectRatio)

    // iOS isVisible + opacity/scale appear (solo si !prefersUnifiedCarouselFrame)
    var isVisible by remember(item.id) { mutableStateOf(prefersUnifiedCarouselFrame) }
    LaunchedEffect(prefersUnifiedCarouselFrame, item.id) {
        if (prefersUnifiedCarouselFrame) {
            isVisible = true
        } else {
            isVisible = true
        }
    }
    DisposableEffect(item.id) {
        onDispose {
            if (!prefersUnifiedCarouselFrame) isVisible = false
        }
    }
    val appearAlpha by animateFloatAsState(
        targetValue = if (prefersUnifiedCarouselFrame || isVisible) 1f else 0.8f,
        animationSpec = if (prefersUnifiedCarouselFrame) tween(0) else tween(400),
        label = "mediaAppearAlpha",
    )
    val appearScale by animateFloatAsState(
        targetValue = if (prefersUnifiedCarouselFrame || isVisible) 1f else 0.98f,
        animationSpec = if (prefersUnifiedCarouselFrame) tween(0) else tween(400),
        label = "mediaAppearScale",
    )

    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(0.dp))
            .graphicsLayer {
                alpha = appearAlpha
                scaleX = appearScale
                scaleY = appearScale
            },
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
                    model = item.url,
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
    // iOS mute overlay siempre si allowsVideoPlayback (código); comment dice hide immersive —
    // seguimos el código: visible cuando allowsVideoPlayback. FeedVideoPage hideMute en immersive
    // para no tapear mute encima de Reels.
    val showMute = allowsVideoPlayback && !isImmersive

    Box(Modifier.fillMaxSize()) {
        when {
            !allowsVideoPlayback -> {
                // iOS videoPosterFallback
                if (!posterUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = posterUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.12f)))
                }
            }
            usesBlurredFitLayout -> {
                CarouselMediaBackdropView(item = item)
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
                        // iOS: arrow.up.right.square bottom trailing
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
    tags = tags,
)
