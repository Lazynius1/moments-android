package com.moments.android.views.feed.maps

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.models.Moment
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.deleteMoment
import com.moments.android.services.firestore.loadSavedMoments
import com.moments.android.services.performance.FeedVisibilityCoordinator
import com.moments.android.services.performance.VideoMomentsIndex
import com.moments.android.services.social.AffinityInteractionType
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.views.comments.ModernCommentsSheet
import com.moments.android.views.explore.ExploreView
import com.moments.android.views.feed.core.StoryUserPresentationRoute
import com.moments.android.views.feed.core.sections.ModernPostCardView
import com.moments.android.views.feed.maps.mapssections.MomentUnavailableOverlay
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.core.sections.ProfileStickyChromeContainer
import com.moments.android.views.profile.momentsview.EditMomentView
import com.moments.android.views.profile.momentsview.ModernContextMenuOverlay
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.momentdetail.FeedPinnedTopChrome
import com.moments.android.views.shared.momentdetail.ProfileHeaderCollapseMetrics
import com.moments.android.views.story.StoriesView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * Port de `LocationMomentDetailView` (path activo en Maps.swift / LocationMomentDetailView.swift).
 * iOS usa `ModernPostCardView` en scroll; `LocationMomentCard` y derivados son código muerto en iOS.
 */
@Composable
fun LocationMomentDetailView(
    moments: List<Moment>,
    initialIndex: Int,
    locationName: String,
    momentAvailability: Map<String, Boolean> = emptyMap(),
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val colors = rememberAdaptiveColors()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val feedCardHeightPx = with(density) { (configuration.screenHeightDp * 0.58f).dp.toPx() }
    val listState = rememberLazyListState()

    var feedMoments by remember(moments) { mutableStateOf(moments.map { it.toFeedMomentForMap() }) }
    var currentIndex by remember { mutableIntStateOf(initialIndex.coerceIn(0, (feedMoments.size - 1).coerceAtLeast(0))) }
    var trackedMomentViewIds by remember { mutableStateOf(setOf<String>()) }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var backgroundOpacity by remember { mutableFloatStateOf(1f) }

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuMoment by remember { mutableStateOf<FeedMoment?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var commentsMoment by remember { mutableStateOf<FeedMoment?>(null) }
    var selectedHashtag by remember { mutableStateOf("") }
    var showExploreWithHashtag by remember { mutableStateOf(false) }

    var peekImageUrl by remember { mutableStateOf<String?>(null) }
    var peekAspectRatio by remember { mutableFloatStateOf(1f) }
    var isPeeking by remember { mutableStateOf(false) }
    var peekIsProtected by remember { mutableStateOf(false) }

    var storyRoute by remember { mutableStateOf<StoryUserPresentationRoute?>(null) }
    var contentMinY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }
    var initialContentMinY by remember { mutableFloatStateOf(Float.POSITIVE_INFINITY) }

    var locationDisplayTitle by remember(locationName) { mutableStateOf(locationName) }

    val chromeBlurProgress = ProfileHeaderCollapseMetrics.detailScrollChromeBlurProgress(
        contentMinY = contentMinY,
        initialContentMinY = initialContentMinY,
    )

    val basePlaceName = remember(locationName, feedMoments, currentIndex) {
        val trimmed = locationName.trim()
        if (trimmed.isNotEmpty()) return@remember trimmed
        feedMoments.getOrNull(currentIndex)?.location?.trim()?.takeIf { it.isNotEmpty() }
            ?: locationName
    }

    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "locationDetailDrag",
    )
    val scale = if (isDragging) maxOf(0.85f, 1f - kotlin.math.abs(dragOffsetPx) / 1000f) else 1f

    val firstVisibleIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }

    fun trackMomentViewIfNeeded(moment: FeedMoment?) {
        val id = moment?.id?.takeIf { it.isNotEmpty() } ?: return
        if (moment.authorId.isEmpty() || id in trackedMomentViewIds) return
        trackedMomentViewIds = trackedMomentViewIds + id
        AffinityTracker.trackInteraction(AffinityInteractionType.MOMENT_VIEW, moment.authorId)
    }

    fun openUserProfile(userId: String) {
        val normalized = userId.trim()
        if (normalized.isEmpty()) return
        NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToUserProfileInFeed(normalized))
    }

    fun handleAuthorAvatarTap(userId: String, hasStory: Boolean) {
        val normalized = userId.trim()
        if (normalized.isEmpty()) return
        if (hasStory) {
            // iOS: storyRoute = StoryUserPresentationRoute(userId:)
            storyRoute = StoryUserPresentationRoute(normalized)
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
        if (next >= moments.size) return
        val upcoming = moments.subList(next, minOf(next + 8, moments.size))
        val imageUrls = upcoming.mapNotNull { it.previewImageURLString?.takeIf(String::isNotBlank) }
        if (imageUrls.isNotEmpty()) ImagePrefetchManager.prefetch(imageUrls)
        // iOS: VideoPlaybackSelector.shared.preloadURLStrings(from:maxMoments: 4)
        val videoUrls = VideoPlaybackSelector.preloadUrlStrings(upcoming, maxMoments = 4)
        if (videoUrls.isNotEmpty()) VideoPreloader.preloadAssets(videoUrls)
    }

    fun activateVideoForIndex(index: Int) {
        val moment = moments.getOrNull(index) ?: return
        if (!moment.mapHasVideoMedia) return
        val consumerId = GlobalVideoManager.profileVideoConsumerId(moment)
        FeedVisibilityCoordinator.pinActiveVideo(consumerId)
        GlobalVideoManager.playVideo(consumerId)
    }

    fun dismissLocationDetail() {
        backgroundOpacity = 0f
        onDismiss()
    }

    fun deleteMoment() {
        val moment = contextMenuMoment ?: return
        scope.launch {
            runCatching {
                firestore.deleteMoment(userId = moment.authorId, momentId = moment.id)
            }.onSuccess {
                val next = feedMoments.filterNot { it.id == moment.id }
                feedMoments = next
                if (next.isEmpty()) {
                    dismissLocationDetail()
                } else {
                    currentIndex = currentIndex.coerceIn(0, next.lastIndex)
                }
            }
        }
    }

    LaunchedEffect(locationName, moments, currentIndex) {
        locationDisplayTitle = basePlaceName
        val coord = moments.getOrNull(currentIndex)?.locationCoordinate
            ?: moments.firstNotNullOfOrNull { it.locationCoordinate }
        locationDisplayTitle = withContext(Dispatchers.IO) {
            MapLocationDisplayFormatter.resolveTitle(
                context = context,
                place = basePlaceName,
                latitude = coord?.latitude,
                longitude = coord?.longitude,
            )
        }
    }

    LaunchedEffect(Unit) {
        val target = initialIndex.coerceIn(0, (feedMoments.size - 1).coerceAtLeast(0))
        currentIndex = target
        trackMomentViewIfNeeded(feedMoments.getOrNull(target))
        VideoMomentsIndex.rebuild(moments)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) runCatching { firestore.loadSavedMoments(uid) }
        GlobalVideoManager.pauseAllVideos()
        delay(150)
        activateVideoForIndex(target)
        if (target > 0 && feedMoments.isNotEmpty()) {
            listState.scrollToItem(target)
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.firstOrNull()?.offset?.toFloat()
                ?: Float.POSITIVE_INFINITY
        }
            .distinctUntilChanged()
            .collect { minY ->
                contentMinY = minY
                if (!initialContentMinY.isFinite() || initialContentMinY > 10_000f) {
                    initialContentMinY = minY
                }
            }
    }

    LaunchedEffect(listState, feedMoments) {
        // iOS: .onPreferenceChange(MomentVisibilityPreference) → FeedVisibilityCoordinator.update
        snapshotFlow {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo
            if (visible.isEmpty()) return@snapshotFlow emptyMap<String, Float>()
            val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat().coerceAtLeast(1f)
            buildMap {
                for (item in visible) {
                    val moment = feedMoments.getOrNull(item.index) ?: continue
                    val id = moment.id.takeIf { it.isNotEmpty() } ?: continue
                    val visiblePx = minOf(item.offset + item.size, info.viewportEndOffset) -
                        maxOf(item.offset, info.viewportStartOffset)
                    put(id, (visiblePx.toFloat() / viewport).coerceIn(0f, 1f))
                }
            }
        }.collect { visibility ->
            FeedVisibilityCoordinator.update(visibility)
        }
    }

    LaunchedEffect(firstVisibleIndex) {
        currentIndex = firstVisibleIndex
        trackMomentViewIfNeeded(feedMoments.getOrNull(firstVisibleIndex))
        activateVideoForIndex(firstVisibleIndex)
        prefetchUpcoming(firstVisibleIndex)
    }

    LaunchedEffect(moments.size) {
        VideoMomentsIndex.rebuild(moments)
    }

    DisposableEffect(Unit) {
        onDispose {
            GlobalVideoManager.pauseAllVideos()
            FeedVisibilityCoordinator.update(emptyMap())
        }
    }

    Box(
        modifier
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
                    val velocityTracker = VelocityTracker()
                    detectHorizontalDragGestures(
                        onDragStart = {
                            isDragging = true
                            velocityTracker.resetTracking()
                        },
                        onDragEnd = {
                            // iOS: translation > 120 || predictedEndTranslation.width > 300
                            val velocityX = velocityTracker.calculateVelocity().x
                            velocityTracker.resetTracking()
                            if (dragOffsetPx > dismissThreshold || velocityX > 300f) {
                                backgroundOpacity = 0f
                                scope.launch {
                                    delay(200)
                                    dismissLocationDetail()
                                }
                            } else {
                                dragOffsetPx = 0f
                                isDragging = false
                                backgroundOpacity = 1f
                            }
                        },
                        onDragCancel = {
                            velocityTracker.resetTracking()
                            dragOffsetPx = 0f
                            isDragging = false
                            backgroundOpacity = 1f
                        },
                        onHorizontalDrag = { change, dragAmount ->
                            velocityTracker.addPosition(change.uptimeMillis, change.position)
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
                    top = ProfileHeaderCollapseMetrics.feedStyleDetailTopInset,
                    bottom = 24.dp,
                    start = FeedMomentCardLayout.listHorizontalPadding,
                    end = FeedMomentCardLayout.listHorizontalPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(
                    maxOf(15.dp, (configuration.screenHeightDp * 0.02f).dp),
                ),
            ) {
                itemsIndexed(feedMoments, key = { _, m -> "${m.authorId}_${m.id}" }) { index, moment ->
                    val source = moments.getOrNull(index)
                    val availabilityKey = source?.mapAvailabilityKey ?: moment.id
                    val available = momentAvailability[availabilityKey] ?: true
                    val isProtected = (moment.audience?.lowercase() ?: "") != "everyone"
                    Box(Modifier.fillMaxWidth()) {
                        ScreenshotProtectedView(isProtected = isProtected) {
                            ModernPostCardView(
                                moment = moment,
                                availableHeight = feedCardHeightPx,
                                onOpenProfile = { openUserProfile(moment.authorId) },
                                onOpenHashtag = { tag ->
                                    selectedHashtag = if (tag.startsWith("#")) tag else "#$tag"
                                    showExploreWithHashtag = true
                                },
                                onOpenLocation = { _, _ -> },
                                onOpenComments = { commentsMoment = moment },
                                onShare = {
                                    contextMenuMoment = moment
                                    showContextMenu = true
                                },
                                onContextMenu = { tapped ->
                                    contextMenuMoment = tapped
                                    showContextMenu = true
                                },
                                onAuthorAvatarTap = { authorId, hasStory ->
                                    handleAuthorAvatarTap(authorId, hasStory)
                                },
                                onPeek = { url, ratio, pressing ->
                                    handlePeek(url, ratio, pressing, moment)
                                },
                                onTagTap = { userId -> openUserProfile(userId) },
                                onNearEnd = { prefetchUpcoming(index) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (!available) Modifier.blur(14.dp) else Modifier),
                            )
                        }
                        if (!available) {
                            MomentUnavailableOverlay(
                                compact = false,
                                cornerRadius = 20.dp,
                                modifier = Modifier.matchParentSize(),
                            )
                        }
                    }
                }
            }

            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .zIndex(10f),
            ) {
                // iOS: ProfileStickyChromeContainer(blurProgress:blurFadeTail:locationChromeBlurFadeTail)
                ProfileStickyChromeContainer(
                    blurProgress = chromeBlurProgress,
                    tabsArePinned = false,
                    chrome = {
                        FeedPinnedTopChrome(
                            title = locationDisplayTitle.ifBlank {
                                stringResource(R.string.feed_location_default)
                            },
                            onDismiss = ::dismissLocationDetail,
                        )
                    },
                )
            }
        }

        if (showContextMenu && contextMenuMoment != null) {
            ModernContextMenuOverlay(
                moment = contextMenuMoment!!,
                isPresented = true,
                onPresentedChange = { if (!it) showContextMenu = false },
                onEdit = {
                    showEditSheet = true
                    showContextMenu = false
                },
                onDelete = {
                    showDeleteAlert = true
                    showContextMenu = false
                },
                onReport = {},
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isPeeking && peekImageUrl != null) {
            ScreenshotProtectedView(isProtected = peekIsProtected, fillsContainer = true) {
                BoxWithConstraints(
                    Modifier
                        .fillMaxSize()
                        .background(
                            colors.surfaceBackground.copy(
                                alpha = if (isSystemInDarkTheme()) 0.92f else 0.88f,
                            ),
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

        if (showDeleteAlert) {
            AlertDialog(
                onDismissRequest = { showDeleteAlert = false },
                title = { Text(stringResource(R.string.location_moment_detail_delete_title)) },
                text = { Text(stringResource(R.string.location_moment_detail_delete_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteAlert = false
                            deleteMoment()
                        },
                    ) {
                        Text(stringResource(R.string.location_moment_detail_delete_confirm), color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAlert = false }) {
                        Text(stringResource(R.string.location_moment_detail_delete_cancel))
                    }
                },
            )
        }

        if (showEditSheet && contextMenuMoment != null) {
            val editing = contextMenuMoment!!
            Dialog(
                onDismissRequest = { showEditSheet = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                    EditMomentView(
                        moment = editing,
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
                                        userId = editing.authorId,
                                        momentId = editing.id,
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
                                    val refreshed = runCatching {
                                        firestore.fetchMoment(editing.id, editing.authorId)
                                    }.getOrNull()?.toFeedMomentForMap()
                                    if (refreshed != null) {
                                        feedMoments = feedMoments.map {
                                            if (it.id == editing.id) refreshed else it
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
        }

        commentsMoment?.let { moment ->
            ModernCommentsSheet(
                moment = moment,
                onDismiss = { commentsMoment = null },
                onOpenStory = { userId ->
                    commentsMoment = null
                    val normalized = userId.trim()
                    if (normalized.isNotEmpty()) {
                        storyRoute = StoryUserPresentationRoute(normalized)
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

        // iOS: .fullScreenCover(item: $storyRoute) { StoriesView(startWithUserId:) }
        storyRoute?.let { route ->
            Dialog(
                onDismissRequest = { storyRoute = null },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                StoriesView(
                    startWithUserId = route.userId,
                    onDismiss = { storyRoute = null },
                )
            }
        }
    }
}

internal fun Moment.toFeedMomentForMap(): FeedMoment {
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
    val reactionTotal = reactions.values.sumOf { it.size }
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
        reactionCount = reactionTotal,
        hideLikeCounts = hideLikeCounts,
        disableComments = disableComments,
        allowSharing = allowSharing,
        hasHiddenLayers = hasHiddenLayers,
        hiddenLayerCount = hiddenLayerCount,
        audience = audience,
        customListId = customListId,
        isArchived = isArchived,
        locationCoordinate = locationCoordinate,
    )
}
