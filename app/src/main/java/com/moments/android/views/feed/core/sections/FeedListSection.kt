package com.moments.android.views.feed.core.sections

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.moments.android.ad.FeedAdPlacement
import com.moments.android.ad.SmartNativeAdView
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.performance.FeedVisibilityCoordinator
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.feed.controls.FeedType
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.core.FeedViewModel
import com.moments.android.views.feed.core.ModernEmptyFeedView
import com.moments.android.views.messaging.components.momentsScrollEdgeChrome
import com.moments.android.views.shared.ScreenshotProtectedView
import kotlinx.coroutines.flow.collectLatest

/** Port 1:1 de `FeedListSection.swift`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedListSection(
    viewModel: FeedViewModel,
    selectedFeedType: FeedType,
    contentTopPadding: PaddingValues,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenUserProfile: (String) -> Unit,
    onOpenHashtag: (String) -> Unit,
    onOpenLocation: (String, com.moments.android.models.Moment.LocationCoordinate?) -> Unit,
    onOpenComments: (FeedMoment) -> Unit,
    onShare: (FeedMoment) -> Unit,
    isFeedHeaderHidden: Boolean = false,
    onHeaderHiddenChange: (Boolean) -> Unit = {},
    onPeek: ((imageUrl: String, ratio: Float, isPressing: Boolean) -> Unit)? = null,
    onContextMenu: (FeedMoment) -> Unit = {},
    onAuthorAvatarTap: ((authorId: String, hasStory: Boolean) -> Unit)? = null,
    onAuthorAvatarLongPress: ((authorId: String, momentId: String, avatarFrame: Rect, postFrame: Rect) -> Unit)? = null,
    hiddenMomentId: String? = null,
    // iOS feedHeaderHeight / feedSelectorHeight (no hardcode 88/35)
    feedHeaderHeight: Dp = 88.dp,
    feedSelectorHeight: Dp = 35.dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val rowSpacing = 6.dp
    // iOS: availableHeight = screen - headerHeight - selectorHeight - tabbar - 60
    val availableHeightPx = remember(
        configuration.screenHeightDp,
        density,
        feedHeaderHeight,
        feedSelectorHeight,
    ) {
        val screenPx = with(density) { configuration.screenHeightDp.dp.toPx() }
        val headerPx = with(density) { feedHeaderHeight.toPx() }
        val selectorPx = with(density) { feedSelectorHeight.toPx() }
        val tabbarPx = with(density) { 50.dp.toPx() }
        val extraPx = with(density) { 60.dp.toPx() }
        (screenPx - headerPx - selectorPx - tabbarPx - extraPx).coerceAtLeast(200f)
    }

    val onHeaderHiddenChangeLatest by rememberUpdatedState(onHeaderHiddenChange)
    val isFeedHeaderHiddenLatest by rememberUpdatedState(isFeedHeaderHidden)
    val hideThresholdPx = with(density) { 40.dp.toPx() }
    val showThresholdPx = with(density) { 28.dp.toPx() }

    /**
     * Paridad iOS `DragGesture(minimumDistance: 8)` + simultaneousGesture:
     *   translation.height < -40 → hide
     *   translation.height >  28 → show
     *
     * Usamos onPreScroll (available) — no onPostScroll(consumed) — porque al llegar
     * al tope LazyColumn deja de consumir y el show nunca disparaba.
     * Compose: y < 0 dedo arriba / contenido sube; y > 0 dedo abajo / volver arriba.
     */
    val headerScrollConnection = remember(hideThresholdPx, showThresholdPx) {
        object : NestedScrollConnection {
            var hideAccum = 0f
            var showAccum = 0f

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                val hidden = isFeedHeaderHiddenLatest
                when {
                    dy < -0.5f -> {
                        hideAccum += -dy
                        showAccum = 0f
                        if (!hidden && hideAccum >= hideThresholdPx) {
                            hideAccum = 0f
                            onHeaderHiddenChangeLatest(true)
                        }
                    }
                    dy > 0.5f -> {
                        showAccum += dy
                        hideAccum = 0f
                        if (hidden && showAccum >= showThresholdPx) {
                            showAccum = 0f
                            onHeaderHiddenChangeLatest(false)
                        }
                    }
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                // Fling hacia arriba (velocity.y > 0 en Compose = contenido baja) → mostrar
                if (available.y > 400f && isFeedHeaderHiddenLatest) {
                    onHeaderHiddenChangeLatest(false)
                }
                hideAccum = 0f
                showAccum = 0f
                return Velocity.Zero
            }
        }
    }

    // Sticky: en el origen del feed el header siempre visible (como iOS al llegar arriba).
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            if (index == 0 && offset <= 8 && isFeedHeaderHiddenLatest) {
                onHeaderHiddenChangeLatest(false)
            }
        }
    }

    LaunchedEffect(listState, viewModel.moments) {
        snapshotFlow {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@snapshotFlow emptyMap<String, Float>()
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat().coerceAtLeast(1f)
            buildMap {
                for (item in visible) {
                    val moment = viewModel.moments.getOrNull(item.index) ?: continue
                    val visiblePx = minOf(item.offset + item.size, info.viewportEndOffset) -
                        maxOf(item.offset, info.viewportStartOffset)
                    put(moment.id, (visiblePx.toFloat() / viewport).coerceIn(0f, 1f))
                }
            }
        }.collect { visibility ->
            FeedVisibilityCoordinator.update(visibility)
            viewModel.syncMomentListeners(visibility)
        }
    }

    LaunchedEffect(viewModel.moments) {
        viewModel.rebuildVideoMomentsIndex()
    }

    // iOS feedReelsVideos = viewModel.moments.videoMoments
    val feedReelsVideos = remember(viewModel.moments) {
        viewModel.reelsVideosForFeed()
    }
    val adAfterIndices = remember(viewModel.moments, selectedFeedType) {
        val (minGap, maxGap) = if (selectedFeedType == FeedType.ForYou) 3 to 5 else 5 to 7
        FeedAdPlacement.indicesAfterWhichToShowAd(
            momentIds = viewModel.moments.map { it.id },
            minGap = minGap,
            maxGap = maxGap,
        )
    }

    LaunchedEffect(Unit) {
        NavigationEventBus.events.collectLatest { event ->
            if (event is CoordinatorNavigationEvent.ScrollFeedToTop) {
                onHeaderHiddenChangeLatest(false)
                if (!MotionPolicy.reduceMotion) {
                    listState.animateScrollToItem(0)
                } else {
                    listState.scrollToItem(0)
                }
                onRefresh()
            }
        }
    }

    LaunchedEffect(Unit) {
        listState.scrollToItem(0)
    }

    fun prefetchUpcoming(fromIndex: Int) {
        val next = fromIndex + 1
        if (next >= viewModel.moments.size) return
        val end = minOf(next + 8, viewModel.moments.size)
        val upcoming = viewModel.moments.subList(next, end)
        val imageUrls = viewModel.imagePrefetchUrls(upcoming, maxMoments = 8)
        if (imageUrls.isNotEmpty()) ImagePrefetchManager.prefetch(imageUrls)
        val videoUrls = viewModel.videoPreloadUrls(upcoming, maxMoments = 4)
        if (videoUrls.isNotEmpty()) VideoPreloader.preloadAssets(videoUrls)
    }

    PullToRefreshBox(
        isRefreshing = viewModel.isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        // Connection en el padre: recibe el gesto aunque LazyColumn ya no consuma (tope).
        Box(
            Modifier
                .fillMaxSize()
                .nestedScroll(headerScrollConnection),
        ) {
            LazyColumn(
                state = listState,
                contentPadding = contentTopPadding,
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
                // iOS .momentsScrollEdgeChrome()
                modifier = Modifier
                    .fillMaxSize()
                    .momentsScrollEdgeChrome(),
            ) {
                if (viewModel.isLoading && viewModel.moments.isEmpty()) {
                    items(4) { FeedPostSkeletonView() }
                } else {
                    itemsIndexed(
                        viewModel.moments,
                        key = { _, m ->
                            if (m.id.isNotEmpty()) "${m.authorId}_${m.id}"
                            else "${m.authorId}_${m.timestamp}_${m.content.take(24)}"
                        },
                    ) { index, moment ->
                        val isProtected = (moment.audience?.lowercase() ?: "") != "everyone"
                        val isHiddenForPreview =
                            !hiddenMomentId.isNullOrEmpty() && moment.id == hiddenMomentId
                        val hideAlpha by animateFloatAsState(
                            targetValue = if (isHiddenForPreview) 0f else 1f,
                            animationSpec = when {
                                MotionPolicy.reduceMotion -> tween(0)
                                isHiddenForPreview -> spring(dampingRatio = 0.84f, stiffness = 380f)
                                else -> tween(durationMillis = 120, easing = FastOutSlowInEasing)
                            },
                            label = "hiddenPostPreviewAlpha",
                        )
                        LaunchedEffect(moment.id) { prefetchUpcoming(index) }
                        Column(Modifier.fillMaxWidth()) {
                            Box(Modifier.graphicsLayer { alpha = hideAlpha }) {
                                ScreenshotProtectedView(
                                    isProtected = isProtected,
                                    containsHardwareVideo = moment.hasHardwareVideo,
                                ) {
                                    ModernPostCardView(
                                        moment = moment,
                                        onOpenProfile = { onOpenUserProfile(moment.authorId) },
                                        onOpenHashtag = onOpenHashtag,
                                        onOpenLocation = { name, coordinate ->
                                            onOpenLocation(name, coordinate)
                                        },
                                        onOpenComments = { onOpenComments(moment) },
                                        onShare = { onShare(moment) },
                                        onContextMenu = onContextMenu,
                                        // iOS onTagTap: onOpenUserProfile
                                        onTagTap = onOpenUserProfile,
                                        onPeek = { url, ratio, pressing ->
                                            onPeek?.invoke(url, ratio, pressing)
                                        },
                                        onNearEnd = {
                                            if (moment.id == viewModel.moments.lastOrNull()?.id) {
                                                onLoadMore()
                                            }
                                        },
                                        onAuthorAvatarTap = onAuthorAvatarTap,
                                        onAuthorAvatarLongPress = onAuthorAvatarLongPress?.let { callback ->
                                            { userId, avatarFrame, postFrame ->
                                                callback(userId, moment.id, avatarFrame, postFrame)
                                            }
                                        },
                                        availableHeight = availableHeightPx,
                                        reelsVideos = feedReelsVideos,
                                    )
                                }
                            }
                            if (index in adAfterIndices) {
                                SmartNativeAdView(
                                    slotId = "feed-${moment.id.ifBlank { "$index" }}",
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }
                        }
                    }
                }
                if (viewModel.isLoadingMore) {
                    item {
                        Box(
                            Modifier.fillMaxWidth().padding(vertical = 15.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            ModernLoadingMoreView()
                        }
                    }
                }
            }

            if (viewModel.moments.isEmpty() && !viewModel.isLoading) {
                ModernEmptyFeedView(
                    feedType = selectedFeedType,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}
