package com.moments.android.views.feed.video

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.moments.android.R
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.performance.VideoMoment
import com.moments.android.services.video.ReelPrebufferService
import com.moments.android.utilities.HapticManager
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Port de `ReelsViewer` (Reels.swift L12–118).
 * Shell + pager + preload; página → `ReelVideoView` + `ReelVideoPlayerManager`.
 */
@Composable
fun ReelsViewer(
    videos: List<VideoMoment>,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    startIndex: Int = 0,
    initialStartSeconds: Double = 0.0,
) {
    val safeStart = startIndex.coerceIn(0, (videos.size - 1).coerceAtLeast(0))
    val pagerState = rememberPagerState(
        initialPage = safeStart,
        pageCount = { videos.size },
    )
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    BackHandler(onBack = onClose)

    // ≡ iOS .statusBarHidden() + preferredColorScheme(.dark) + vídeo en safe area
    DisposableEffect(Unit) {
        val window = (view.context as? android.app.Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.statusBars())
        // Nav bar transparente: el vídeo pinta debajo; el comment bar aporta el fondo AdaptiveColors.
        window?.isNavigationBarContrastEnforced = false
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        onDispose {
            controller?.show(WindowInsetsCompat.Type.statusBars())
            ReelPrebufferService.discard()
        }
    }

    LaunchedEffect(Unit) {
        preloadUpcomingVideos(videos, pagerState.currentPage)
    }

    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page ->
                preloadUpcomingVideos(videos, page)
            }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            // Swipe horizontal para cerrar (≡ iOS DragGesture width > 100)
            .pointerInput(Unit) {
                var totalDx = 0f
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(totalDx) > 100f) {
                            HapticManager.shared.lightImpact()
                            onClose()
                        }
                        totalDx = 0f
                    },
                    onHorizontalDrag = { _, dragAmount ->
                        totalDx += dragAmount
                    },
                )
            },
    ) {
        if (videos.isEmpty()) return@Box

        VerticalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = 1, // ≡ abs(index - current) <= 1
            flingBehavior = PagerDefaults.flingBehavior(state = pagerState),
        ) { index ->
            val video = videos[index]
            val isCurrent = pagerState.currentPage == index
            // Solo montar player ±1 como iOS; el resto muestra poster (nunca negro vacío).
            if (abs(index - pagerState.currentPage) <= 1) {
                ReelVideoView(
                    video = video,
                    isCurrentVideo = isCurrent,
                    startAtSeconds = if (index == safeStart) initialStartSeconds else 0.0,
                    onClose = onClose,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                ReelsPosterPage(
                    posterUrl = video.posterUrlString,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }

    // Haptic solo al cambiar página (no en el primer frame)
    var lastHapticPage by remember { mutableStateOf(safeStart) }
    LaunchedEffect(pagerState.currentPage) {
        val page = pagerState.currentPage
        if (page != lastHapticPage && videos.isNotEmpty()) {
            lastHapticPage = page
            HapticManager.shared.lightImpact()
        }
    }

    // Evitar unused
    @Suppress("UNUSED_VARIABLE")
    val _scope = scope
}

/** ≡ iOS `ReelsPosterPage` — placeholder continuo al pasar reels. */
@Composable
private fun ReelsPosterPage(
    posterUrl: String?,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color.Black)) {
        VideoPosterOverlay(
            posterUrl = posterUrl,
            isReadyToPlay = false,
            contentScale = ContentScale.Fit,
        )
    }
}

/** ≡ iOS `preloadUpcomingVideos(from:)` — próximos 2 + ReelPrebufferService. */
private fun preloadUpcomingVideos(videos: List<VideoMoment>, index: Int) {
    val preloadCount = 2
    val endIndex = minOf(index + preloadCount, videos.size)
    if (index + 1 >= endIndex) return
    val upcoming = videos.subList(index + 1, endIndex)
    val urls = upcoming.flatMap { it.preloadUrlStrings }
    VideoPreloader.preloadAssets(urls)

    val next = videos[index + 1]
    // Misma URL de variant que usará el player al activar el reel.
    val nextUrl = next.playbackUrl ?: next.preloadUrlStrings.firstOrNull()
    if (!nextUrl.isNullOrBlank()) {
        runCatching { ReelPrebufferService.prebuffer(nextUrl) }
    }
}

/** Compat call sites que aún usan ReelsVideoItem. */
data class ReelsVideoItem(
    val momentId: String,
    val videoUrl: String,
    val authorId: String,
    val username: String,
    val thumbnailUrl: String? = null,
)

@Deprecated("Usar ReelsViewer(videos: List<VideoMoment>)", ReplaceWith("ReelsViewer(videos, onClose, modifier, startIndex, initialStartSeconds)"))
@Composable
fun ReelsViewerPlaceholder(
    videos: List<ReelsVideoItem>,
    startIndex: Int = 0,
    initialStartSeconds: Double = 0.0,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Bridge: sin Moment completo no hay VideoMoment — placeholder negro
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "${stringResource(R.string.feed_reels_badge)} ${startIndex + 1}/${videos.size.coerceAtLeast(1)}",
            color = Color.White,
        )
        @Suppress("UNUSED_VARIABLE")
        val start = initialStartSeconds
    }
}

/** Port de badge Reels en feed (CroppedVideoPlayer overlay). */
@Composable
fun ReelsBadgeOverlay(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(12.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.feed_reels_badge),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ReelsFullscreenPlaceholder(modifier: Modifier = Modifier) {
    ReelsViewer(videos = emptyList(), onClose = {}, modifier = modifier)
}
