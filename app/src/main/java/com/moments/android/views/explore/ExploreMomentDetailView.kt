package com.moments.android.views.explore

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.ad.FeedAdPlacement
import com.moments.android.ad.SmartNativeAdView
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.models.Moment
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.deleteMoment
import com.moments.android.services.firestore.loadSavedMoments
import com.moments.android.services.performance.FeedVisibilityCoordinator
import com.moments.android.services.performance.VideoMomentsIndex
import com.moments.android.services.social.AffinityInteractionType
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.views.comments.ModernCommentsSheet
import com.moments.android.views.feed.core.FeedProfileSheetRoute
import com.moments.android.views.feed.core.sections.ModernPostCardView
import com.moments.android.views.feed.maps.LocationMapView
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.core.sections.UserProfileZoomNavigationHost
import com.moments.android.views.profile.momentsview.EditMomentSheet
import com.moments.android.views.profile.momentsview.ModernContextMenuOverlay
import com.moments.android.views.settings.hasVideoMedia
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.momentdetail.FeedPinnedTopChrome
import com.moments.android.views.shared.momentdetail.MomentDetailSolidTopChrome
import com.moments.android.views.shared.momentdetail.ProfileHeaderCollapseMetrics
import com.moments.android.views.shared.momentdetail.rememberMomentDetailContentTopInset
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Port 1:1 de `ExploreMomentDetailView.swift`:
 * scroll vertical estilo feed + dismiss por gesto horizontal + overlays (peek/menu/edit/comments/map/hashtag).
 */
@Composable
fun ExploreMomentDetailView(
    moments: List<Moment>,
    initialIndex: Int = 0,
    onNavigateBack: () -> Unit = {},
    initialMomentId: String? = null,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp
    val feedCardHeight = (screenHeightDp * 0.58f).dp
    val feedCardHeightPx = with(density) { feedCardHeight.toPx() }
    val rowSpacing = FeedMomentCardLayout.rowSpacing

    var feedMoments by remember(moments) {
        mutableStateOf(moments.map { it.toExploreFeedMoment() })
    }
    var domainMoments by remember(moments) { mutableStateOf(moments) }

    val resolvedInitialIndex = remember(moments, initialIndex, initialMomentId) {
        val matched = initialMomentId?.let { id -> moments.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        val raw = matched ?: initialIndex
        if (moments.isEmpty()) 0 else raw.coerceIn(0, moments.lastIndex)
    }

    var currentIndex by remember { mutableStateOf(resolvedInitialIndex) }
    var trackedMomentViewIds by remember { mutableStateOf(setOf<String>()) }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var backgroundOpacity by remember { mutableFloatStateOf(1f) }

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuMoment by remember { mutableStateOf<FeedMoment?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var commentsMoment by remember { mutableStateOf<FeedMoment?>(null) }
    var selectedHashtag by remember { mutableStateOf("") }
    var showExploreWithHashtag by remember { mutableStateOf(false) }
    var showingLocationMap by remember { mutableStateOf(false) }
    var selectedLocationName by remember { mutableStateOf("") }
    var selectedLocationLat by remember { mutableStateOf<Double?>(null) }
    var selectedLocationLng by remember { mutableStateOf<Double?>(null) }
    var profileRoute by remember { mutableStateOf<FeedProfileSheetRoute?>(null) }

    var peekImageUrl by remember { mutableStateOf<String?>(null) }
    var peekAspectRatio by remember { mutableFloatStateOf(1f) }
    var isPeeking by remember { mutableStateOf(false) }
    var peekIsProtected by remember { mutableStateOf(false) }

    val listTopInset = rememberMomentDetailContentTopInset()
    val listState = rememberLazyListState()
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "exploreDetailDrag",
    )
    val scale = if (isDragging) {
        maxOf(0.85f, 1f - kotlin.math.abs(dragOffsetPx) / 1000f)
    } else {
        1f
    }

    fun trackMomentViewIfNeeded(moment: FeedMoment?) {
        val id = moment?.id?.takeIf { it.isNotBlank() } ?: return
        if (moment.authorId.isBlank() || id in trackedMomentViewIds) return
        trackedMomentViewIds = trackedMomentViewIds + id
        AffinityTracker.trackInteraction(AffinityInteractionType.MOMENT_VIEW, moment.authorId)
    }

    fun openUserProfile(userId: String) {
        val normalized = userId.trim()
        if (normalized.isEmpty()) return
        profileRoute = FeedProfileSheetRoute(normalized)
    }

    fun handleAuthorAvatarTap(userId: String, hasStory: Boolean) {
        val normalized = userId.trim()
        if (normalized.isEmpty()) return
        if (hasStory) {
            NavigationEventBus.emit(CoordinatorNavigationEvent.ShowStoriesStartingAt(normalized))
        } else {
            openUserProfile(normalized)
        }
    }

    fun handlePeek(imageUrl: String, ratio: Float, isPressing: Boolean, moment: FeedMoment) {
        if (isPressing) {
            peekImageUrl = imageUrl
            peekAspectRatio = ratio
            peekIsProtected = (moment.audience?.lowercase() ?: "") != "everyone"
            isPeeking = true
        } else {
            isPeeking = false
            peekIsProtected = false
        }
    }

    fun prefetchUpcoming(fromIndex: Int) {
        val next = fromIndex + 1
        if (next >= domainMoments.size) return
        val end = minOf(next + 8, domainMoments.size)
        val upcoming = domainMoments.subList(next, end)
        val imageUrls = VideoPlaybackSelector.imagePrefetchUrlStrings(upcoming, maxMoments = 8)
        if (imageUrls.isNotEmpty()) ImagePrefetchManager.prefetch(imageUrls)
        val videoUrls = VideoPlaybackSelector.preloadUrlStrings(upcoming, maxMoments = 4)
        if (videoUrls.isNotEmpty()) VideoPreloader.preloadAssets(videoUrls)
    }

    fun activateVideoForIndex(index: Int) {
        val moment = domainMoments.getOrNull(index) ?: return
        if (!moment.hasVideoMedia) return
        val id = moment.id ?: return
        FeedVisibilityCoordinator.pinActiveVideo(id)
    }

    fun deleteContextMoment() {
        val target = contextMenuMoment ?: return
        scope.launch {
            runCatching {
                firestore.deleteMoment(userId = target.authorId, momentId = target.id)
            }.onSuccess {
                val idx = feedMoments.indexOfFirst { it.id == target.id }
                if (idx < 0) return@onSuccess
                feedMoments = feedMoments.toMutableList().also { it.removeAt(idx) }
                domainMoments = domainMoments.toMutableList().also { list ->
                    list.removeAll { it.id == target.id }
                }
                if (feedMoments.isEmpty()) {
                    onNavigateBack()
                } else {
                    currentIndex = currentIndex.coerceIn(0, feedMoments.lastIndex)
                }
            }
        }
    }

    LaunchedEffect(resolvedInitialIndex, feedMoments.size) {
        currentIndex = resolvedInitialIndex
        if (feedMoments.isNotEmpty()) {
            listState.scrollToItem(resolvedInitialIndex)
            trackMomentViewIfNeeded(feedMoments.getOrNull(resolvedInitialIndex))
            VideoMomentsIndex.rebuild(domainMoments)
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                runCatching { firestore.loadSavedMoments(uid) }
            }
            delay(150)
            activateVideoForIndex(resolvedInitialIndex)
        }
    }

    LaunchedEffect(domainMoments.size) {
        VideoMomentsIndex.rebuild(domainMoments)
    }

    val adAfterIndices = remember(feedMoments) {
        FeedAdPlacement.indicesAfterWhichToShowAd(
            momentIds = feedMoments.map { it.id },
            minGap = 3,
            maxGap = 5,
        )
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo
                .minByOrNull { kotlin.math.abs(it.offset) }
                ?.index
        }
            .distinctUntilChanged()
            .collect { index ->
                if (index != null) {
                    currentIndex = index
                    trackMomentViewIfNeeded(feedMoments.getOrNull(index))
                    activateVideoForIndex(index)
                    prefetchUpcoming(index)
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            FeedVisibilityCoordinator.update(emptyMap())
        }
    }

    UserProfileZoomNavigationHost(
        profileRoute = profileRoute,
        onProfileRouteChange = { profileRoute = it },
        modifier = modifier.fillMaxSize(),
    ) { _ ->
    Box(
        Modifier
            .fillMaxSize()
            .background(colors.surfaceBackground.copy(alpha = backgroundOpacity)),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .offset { IntOffset(animatedOffset.roundToInt(), 0) }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .pointerInput(Unit) {
                    val dismissThreshold = with(density) { 120.dp.toPx() }
                    val opacityDenom = with(density) { 200.dp.toPx() }
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            val velocityOk = dragOffsetPx > dismissThreshold
                            if (velocityOk) {
                                backgroundOpacity = 0f
                                scope.launch {
                                    delay(200)
                                    onNavigateBack()
                                }
                            } else {
                                dragOffsetPx = 0f
                                isDragging = false
                                backgroundOpacity = 1f
                            }
                        },
                        onDragCancel = {
                            dragOffsetPx = 0f
                            isDragging = false
                            backgroundOpacity = 1f
                        },
                        onHorizontalDrag = { _, dragAmount ->
                            if (dragAmount > 0 || dragOffsetPx > 0) {
                                dragOffsetPx = (dragOffsetPx + dragAmount).coerceAtLeast(0f)
                                val progress = (dragOffsetPx / opacityDenom).coerceIn(0f, 1f)
                                backgroundOpacity = 1f - (progress * 0.4f)
                            }
                        },
                    )
                },
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    top = listTopInset,
                    bottom = 24.dp,
                    start = FeedMomentCardLayout.listHorizontalPadding,
                    end = FeedMomentCardLayout.listHorizontalPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                itemsIndexed(
                    feedMoments,
                    key = { _, m -> "${m.authorId}_${m.id}" },
                ) { index, moment ->
                    Column(Modifier.fillMaxWidth()) {
                        ScreenshotProtectedView(
                            isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                            containsHardwareVideo = moment.hasHardwareVideo,
                        ) {
                            ModernPostCardView(
                                moment = moment,
                                availableHeight = feedCardHeightPx,
                                onOpenProfile = { openUserProfile(moment.authorId) },
                                onOpenHashtag = { tag ->
                                    selectedHashtag = if (tag.startsWith("#")) tag else "#$tag"
                                    showExploreWithHashtag = true
                                },
                                onOpenLocation = { name, coordinate ->
                                    selectedLocationName = name
                                    selectedLocationLat = coordinate?.latitude
                                    selectedLocationLng = coordinate?.longitude
                                    showingLocationMap = true
                                },
                                onOpenComments = { commentsMoment = moment },
                                onShare = {
                                    contextMenuMoment = moment
                                    showContextMenu = true
                                },
                                onContextMenu = { tapped ->
                                    contextMenuMoment = tapped
                                    showContextMenu = true
                                },
                                reelsVideos = emptyList(),
                                onAuthorAvatarTap = { authorId, hasStory ->
                                    handleAuthorAvatarTap(authorId, hasStory)
                                },
                                onPeek = { url, ratio, pressing ->
                                    handlePeek(url, ratio, pressing, moment)
                                },
                                onTagTap = { userId -> openUserProfile(userId) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(feedCardHeight),
                            )
                        }
                        // iOS For You cadence. Not in Reels (aspect not always vertical).
                        if (index in adAfterIndices) {
                            SmartNativeAdView(
                                slotId = "explore-${moment.id.ifBlank { "$index" }}",
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                }
            }

            MomentDetailSolidTopChrome(modifier = Modifier.align(Alignment.TopCenter)) {
                FeedPinnedTopChrome(
                    title = stringResource(R.string.explore_title),
                    onDismiss = onNavigateBack,
                    applySafeAreaTop = false,
                )
            }
        }

        contextMenuMoment?.let { menuMoment ->
            if (showContextMenu) {
                ModernContextMenuOverlay(
                    moment = menuMoment,
                    isPresented = true,
                    onPresentedChange = { if (!it) showContextMenu = false },
                    onEdit = {
                        showEditSheet = true
                        showContextMenu = false
                    },
                    onDelete = {
                        showContextMenu = false
                        deleteContextMoment()
                    },
                    onReport = {},
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (isPeeking && peekImageUrl != null) {
            ScreenshotProtectedView(isProtected = peekIsProtected, fillsContainer = true) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .background(
                            colors.surfaceBackground.copy(alpha = if (isSystemInDarkTheme()) 0.92f else 0.88f),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    val w = maxWidth - 32.dp
                    val h = w / peekAspectRatio.coerceAtLeast(0.1f)
                    AsyncImage(
                        model = peekImageUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(w)
                            .height(h)
                            .shadow(20.dp, RoundedCornerShape(FeedMomentCardLayout.mediaCornerRadius))
                            .clip(RoundedCornerShape(FeedMomentCardLayout.mediaCornerRadius)),
                    )
                }
            }
        }

        if (showingLocationMap) {
            Dialog(
                onDismissRequest = { showingLocationMap = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                LocationMapView(
                    locationName = selectedLocationName.ifEmpty {
                        stringResource(R.string.feed_location_default)
                    },
                    latitude = selectedLocationLat,
                    longitude = selectedLocationLng,
                    onDismiss = { showingLocationMap = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        if (showEditSheet) {
            val editTarget = contextMenuMoment
            if (editTarget != null) {
                EditMomentSheet(
                            moment = editTarget,
                            onSave = { payload ->
                                scope.launch {
                                    runCatching {
                                        val coord = if (payload.locationLatitude != null && payload.locationLongitude != null) {
                                            Moment.LocationCoordinate(
                                                latitude = payload.locationLatitude,
                                                longitude = payload.locationLongitude,
                                            )
                                        } else {
                                            null
                                        }
                                        firestore.updateMomentDetails(
                                            userId = editTarget.authorId,
                                            momentId = editTarget.id,
                                            content = payload.content,
                                            audience = payload.audience,
                                            customListId = payload.customListId,
                                            customViewers = payload.customViewers,
                                            taggedUsers = payload.taggedUsers,
                                            mentionedUsers = payload.mentionedUsers,
                                            location = payload.locationName.ifEmpty { null },
                                            locationCoordinate = coord,
                                            mediaItems = payload.mediaItems,
                                        )
                                        val updated = firestore.fetchMoment(editTarget.id, editTarget.authorId)
                                        val idx = feedMoments.indexOfFirst { it.id == editTarget.id }
                                        if (idx >= 0) {
                                            feedMoments = feedMoments.toMutableList().also {
                                                it[idx] = updated.toExploreFeedMoment()
                                            }
                                            domainMoments = domainMoments.toMutableList().also {
                                                val dIdx = it.indexOfFirst { m -> m.id == editTarget.id }
                                                if (dIdx >= 0) it[dIdx] = updated
                                            }
                                        }
                                    }
                                    showEditSheet = false
                                }
                            },
                            onDismiss = { showEditSheet = false },
                        )
            }
        }

        commentsMoment?.let { moment ->
            ModernCommentsSheet(
                moment = moment,
                onDismiss = { commentsMoment = null },
                onOpenProfile = { userId ->
                    commentsMoment = null
                    openUserProfile(userId)
                },
                onOpenStory = { userId ->
                    commentsMoment = null
                    val normalized = userId.trim()
                    if (normalized.isNotEmpty()) {
                        NavigationEventBus.emit(CoordinatorNavigationEvent.ShowStoriesStartingAt(normalized))
                    }
                },
            )
        }

        if (showExploreWithHashtag) {
            Dialog(
                onDismissRequest = { showExploreWithHashtag = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize()) {
                    ExploreView(
                        initialSearchQuery = selectedHashtag,
                        isDismissable = true,
                        onDismiss = { showExploreWithHashtag = false },
                    )
                }
            }
        }
    }
    } // UserProfileZoomNavigationHost
}
