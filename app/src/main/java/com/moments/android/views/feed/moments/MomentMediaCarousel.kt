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
import com.moments.android.R
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.performance.VideoMomentsIndex
import com.moments.android.services.video.SharedVideoPlayerPool
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.video.FeedVideoPage
import com.moments.android.views.feed.video.LiveVideoTimeLabel
import com.moments.android.views.feed.video.ReelsVideoItem
import com.moments.android.views.feed.video.ReelsViewerPlaceholder
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
) {
    // iOS ModernPostCardView.mediaItems → visibleMediaItems (+ legacy fallback en iOS)
    val mediaItems = moment.visibleMediaItems.ifEmpty { moment.mediaItems }
    val pagerState = rememberPagerState(pageCount = { mediaItems.size.coerceAtLeast(1) })
    // iOS EnhancedCarouselView pasa `aspectRatio` del card (detected), no el de cada página
    val rawRatio = MomentCarouselLayoutRules.aspectRatioValue(moment.aspectRatio)
    val canvasAspectRatio = MomentCarouselLayoutRules.feedDisplayAspectRatio(rawRatio)
    val isCarousel = mediaItems.size > 1
    var showReelsViewer by remember { mutableStateOf(false) }
    var reelsStartIndex by remember { mutableStateOf(0) }
    val indexedVideos by VideoMomentsIndex.videoMoments.collectAsState()

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

    if (showReelsViewer) {
        // iOS MediaItemView.fullScreenCover → ReelsViewer
        Dialog(
            onDismissRequest = {
                showReelsViewer = false
                onImmersiveChange(false)
                // iOS: completeReelsFeedHandoff + playVideo — aquí reanudamos el consumer activo
                runCatching {
                    SharedVideoPlayerPool.player(consumerId).play()
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val reels = indexedVideos.map { vm ->
                val media = vm.moment.mediaItems?.getOrNull(vm.mediaIndex)
                ReelsVideoItem(
                    momentId = vm.moment.id.orEmpty(),
                    videoUrl = media?.url ?: vm.moment.videoUrl.orEmpty(),
                    authorId = vm.moment.authorId,
                    username = vm.moment.username,
                    thumbnailUrl = media?.thumbnailUrl ?: vm.moment.imagePath,
                )
            }
            ReelsViewerPlaceholder(
                videos = reels,
                startIndex = reelsStartIndex,
                onClose = {
                    showReelsViewer = false
                    onImmersiveChange(false)
                },
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
                val allowsVideoPlayback = !isCarousel || page == pagerState.currentPage
                MediaItemView(
                    item = mediaItems[page],
                    moment = moment,
                    consumerId = if (isCarousel) "${consumerId}_$page" else consumerId,
                    canvasAspectRatio = canvasAspectRatio,
                    prefersUnifiedCarouselFrame = isCarousel,
                    showTags = showTags,
                    onToggleTags = onToggleTags,
                    isImmersive = isImmersive,
                    allowsVideoPlayback = allowsVideoPlayback,
                    onTagTap = onTagTap,
                    onOpenReels = {
                        // iOS openReelsViewer: pauseAll + isImmersive = true + showReelsViewer
                        runCatching { SharedVideoPlayerPool.player("${consumerId}_$page").pause() }
                        onImmersiveChange(true)
                        reelsStartIndex = VideoMomentsIndex.reelsStartIndex(moment.id)
                        showReelsViewer = true
                    },
                )
            }
            // iOS ModernPostCardView: MomentCarouselPageIndicators fuera del carousel, top 20
            AnimatedVisibility(
                visible = mediaItems.size > 1 && !isImmersive,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    Modifier.padding(top = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(MomentCarouselIndicatorStyle.spacing),
                ) {
                    repeat(mediaItems.size) { index ->
                        val active = index == pagerState.currentPage
                        Box(
                            Modifier
                                .size(
                                    width = MomentCarouselIndicatorStyle.dotWidth,
                                    height = MomentCarouselIndicatorStyle.dotHeight,
                                )
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (active) {
                                        MomentCarouselIndicatorStyle.activeColor(index)
                                    } else {
                                        MomentCarouselIndicatorStyle.inactiveColor
                                    },
                                ),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Port de `MediaItemView` + `CroppedVideoPlayer` (FeedMomentComponents.swift).
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

    val itemRatio = MomentCarouselLayoutRules.aspectRatioValue(item.aspectRatio)
    // iOS usesBlurredFitLayout: solo si prefersUnifiedCarouselFrame && fitWithBlur
    val usesBlurredFitLayout = prefersUnifiedCarouselFrame &&
        MomentCarouselLayoutRules.presentationMode(itemRatio, canvasAspectRatio) ==
        MomentCarouselPresentationMode.FitWithBlur
    val tags = item.tags.orEmpty()
    // iOS CroppedVideoPlayer.isReelsFormat: aspectRatio < 0.7 || currentMoment.aspectRatio == "9:16"
    val isReelsFormat = canvasAspectRatio < 0.7f || moment.aspectRatio == "9:16"

    Box(Modifier.fillMaxSize()) {
        if (!prefersUnifiedCarouselFrame) {
            // iOS: RoundedRectangle.fill(.ultraThinMaterial) cuando no es carousel unificado
            Box(Modifier.fillMaxSize().background(FeedInk.copy(alpha = 0.08f)))
        }

        if (item.type == "video") {
            CroppedVideoPlayer(
                item = item,
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
            // iOS image branch
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
                    AsyncImage(
                        model = item.url,
                        contentDescription = moment.username,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    AsyncImage(
                        model = item.url,
                        contentDescription = moment.username,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
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
    consumerId: String,
    usesBlurredFitLayout: Boolean,
    isReelsFormat: Boolean,
    allowsVideoPlayback: Boolean,
    isImmersive: Boolean,
    onTap: () -> Unit,
) {
    val posterUrl = item.thumbnailUrl?.takeIf { it.isNotBlank() }

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
                    url = item.url,
                    thumbnailUrl = posterUrl,
                    consumerId = consumerId,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 10.dp, horizontal = 6.dp),
                    allowsPlayback = true,
                    allowsPauseInteraction = true,
                    showMute = true,
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
                        totalDuration = item.videoDuration,
                        modifier = Modifier.padding(top = 8.dp, end = 8.dp),
                    )
                }
            }
            isReelsFormat -> {
                // iOS: ModernVideoPlayer(allowsPauseInteraction: false) + clear Button(onTap)
                FeedVideoPage(
                    url = item.url,
                    thumbnailUrl = posterUrl,
                    consumerId = consumerId,
                    modifier = Modifier.fillMaxSize(),
                    allowsPlayback = true,
                    allowsPauseInteraction = false,
                    showMute = true,
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
                            totalDuration = item.videoDuration,
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
                    url = item.url,
                    thumbnailUrl = posterUrl,
                    consumerId = consumerId,
                    modifier = Modifier.fillMaxSize(),
                    allowsPlayback = true,
                    allowsPauseInteraction = true,
                    showMute = true,
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
                            totalDuration = item.videoDuration,
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
