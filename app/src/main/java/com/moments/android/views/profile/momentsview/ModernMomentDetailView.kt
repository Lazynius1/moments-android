package com.moments.android.views.profile.momentsview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.momentsChromeGlass
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
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.views.comments.ModernCommentsSheet
import com.moments.android.views.components.LiveUsernameContent
import com.moments.android.views.explore.ExploreView
import com.moments.android.views.explore.toExploreFeedMoment
import com.moments.android.views.feed.core.StoryUserPresentationRoute
import com.moments.android.views.feed.core.sections.ModernPostCardView
import com.moments.android.views.feed.maps.LocationMapView
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.sharing.ModernShareBottomSheet
import com.moments.android.views.settings.hasVideoMedia
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.momentdetail.MomentDetailSolidTopChrome
import com.moments.android.views.shared.momentdetail.ProfileHeaderCollapseMetrics
import com.moments.android.views.shared.momentdetail.rememberMomentDetailContentTopInset
import com.moments.android.views.story.StoriesView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Port de `ModernMomentDetailView.swift`:
 * scroll vertical estilo feed (rejilla perfil) + dismiss horizontal + overlays.
 */
@Composable
fun ModernMomentDetailView(
    moments: List<Moment>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialIndex: Int = 0,
    initialMomentId: String? = null,
    topContentInset: Dp = 0.dp,
    restrictPlaybackToInitialIndex: Boolean = false,
    openCommentsOnAppear: Boolean = false,
    chromeTitle: String? = null,
) {
    val colors = rememberAdaptiveColors()
    val listTopInset = rememberMomentDetailContentTopInset(
        chromeBodyHeight = ProfileHeaderCollapseMetrics.profileDetailChromeBodyHeight,
    )
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp
    val feedCardHeight = (screenHeightDp * 0.58f).dp
    val feedCardHeightPx = with(density) { feedCardHeight.toPx() }
    val rowSpacing = maxOf(15.dp, (screenHeightDp * 0.02f).dp)

    var feedMoments by remember(moments) {
        mutableStateOf(moments.map { it.toExploreFeedMoment() })
    }
    var domainMoments by remember(moments) { mutableStateOf(moments) }

    val resolvedInitialIndex = remember(moments, initialIndex, initialMomentId) {
        val byId = initialMomentId?.let { id -> moments.indexOfFirst { it.id == id } }?.takeIf { it >= 0 }
        val resolved = byId ?: initialIndex
        if (moments.isEmpty()) 0 else resolved.coerceIn(0, moments.lastIndex)
    }

    var currentIndex by remember { mutableStateOf(resolvedInitialIndex) }
    var trackedMomentViewIds by remember { mutableStateOf(setOf<String>()) }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var backgroundOpacity by remember { mutableFloatStateOf(1f) }

    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuMoment by remember { mutableStateOf<FeedMoment?>(null) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showShareSheet by remember { mutableStateOf(false) }
    var storyRoute by remember { mutableStateOf<StoryUserPresentationRoute?>(null) }
    var commentsMoment by remember { mutableStateOf<FeedMoment?>(null) }
    var selectedHashtag by remember { mutableStateOf("") }
    var showExploreWithHashtag by remember { mutableStateOf(false) }
    var showingLocationMap by remember { mutableStateOf(false) }
    var selectedLocationName by remember { mutableStateOf("") }
    var selectedLocationLat by remember { mutableStateOf<Double?>(null) }
    var selectedLocationLng by remember { mutableStateOf<Double?>(null) }

    var peekImageUrl by remember { mutableStateOf<String?>(null) }
    var peekAspectRatio by remember { mutableFloatStateOf(1f) }
    var isPeeking by remember { mutableStateOf(false) }
    var peekIsProtected by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "profileDetailDrag",
    )
    val scale = if (isDragging) {
        maxOf(0.85f, 1f - kotlin.math.abs(dragOffsetPx) / 1000f)
    } else {
        1f
    }

    val currentMoment = feedMoments.getOrNull(currentIndex)

    fun trackMomentViewIfNeeded(moment: FeedMoment?) {
        val id = moment?.id?.takeIf { it.isNotBlank() } ?: return
        if (moment.authorId.isBlank() || id in trackedMomentViewIds) return
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
            // iOS: storyRoute = StoryUserPresentationRoute → StoriesView(startWithUserId:)
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
        if (next >= domainMoments.size) return
        val end = minOf(next + 8, domainMoments.size)
        val upcoming = domainMoments.subList(next, end)
        val imageUrls = upcoming.mapNotNull { it.previewImageURLString?.takeIf(String::isNotBlank) }
        if (imageUrls.isNotEmpty()) ImagePrefetchManager.prefetch(imageUrls)
        val videoUrls = VideoPlaybackSelector.preloadUrlStrings(upcoming, maxMoments = 4)
        if (videoUrls.isNotEmpty()) VideoPreloader.preloadAssets(videoUrls)
    }

    fun activateVideoForIndex(index: Int) {
        if (restrictPlaybackToInitialIndex && index != resolvedInitialIndex) return
        val moment = domainMoments.getOrNull(index) ?: return
        if (!moment.hasVideoMedia) return
        val consumerId = GlobalVideoManager.profileVideoConsumerId(moment)
        FeedVisibilityCoordinator.pinActiveVideo(consumerId)
        GlobalVideoManager.playVideo(consumerId)
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
                    onDismiss()
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
            if (openCommentsOnAppear) {
                delay(380)
                commentsMoment = feedMoments.getOrNull(resolvedInitialIndex)
            }
        }
    }

    LaunchedEffect(domainMoments.size) {
        VideoMomentsIndex.rebuild(domainMoments)
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
            GlobalVideoManager.pauseAllVideos()
            FeedVisibilityCoordinator.update(emptyMap())
        }
    }

    if (moments.isEmpty()) {
        Box(modifier.fillMaxSize().background(colors.surfaceBackground))
        return
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
                    detectHorizontalDragGestures(
                        onDragStart = { isDragging = true },
                        onDragEnd = {
                            if (dragOffsetPx > dismissThreshold) {
                                backgroundOpacity = 0f
                                scope.launch {
                                    delay(200)
                                    onDismiss()
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
                    top = listTopInset + topContentInset,
                    bottom = 24.dp,
                    start = FeedMomentCardLayout.listHorizontalPadding,
                    end = FeedMomentCardLayout.listHorizontalPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(rowSpacing),
            ) {
                itemsIndexed(
                    feedMoments,
                    key = { _, m -> "${m.authorId}_${m.id}" },
                ) { _, moment ->
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
                                // iOS overlay ModernShareBottomSheet (FeedView same pattern)
                                contextMenuMoment = moment
                                showShareSheet = true
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(feedCardHeight),
                        )
                    }
                }
            }

            MomentDetailSolidTopChrome(modifier = Modifier.align(Alignment.TopCenter)) {
                ProfileMomentDetailChrome(
                    moment = currentMoment,
                    chromeTitleOverride = chromeTitle,
                    onDismiss = onDismiss,
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

        // ≡ iOS ModernShareBottomSheet overlay (zIndex encima del context menu)
        if (showShareSheet) {
            contextMenuMoment?.let { shareTarget ->
                ModernShareBottomSheet(
                    moment = shareTarget,
                    onDismiss = { showShareSheet = false },
                )
            }
        }

        // ≡ iOS fullScreenCover StoriesView(startWithUserId:)
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
                Dialog(
                    onDismissRequest = { showEditSheet = false },
                    properties = DialogProperties(usePlatformDefaultWidth = false),
                ) {
                    Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                        EditMomentView(
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
    }
}

/** Toolbar Android: LiveUsername + subtítulo Moments sobre header sólido. */
@Composable
private fun ProfileMomentDetailChrome(
    moment: FeedMoment?,
    chromeTitleOverride: String?,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val override = chromeTitleOverride?.trim().orEmpty()
    Box(
        modifier
            .fillMaxWidth()
            .height(ProfileHeaderCollapseMetrics.profileDetailChromeBodyHeight)
            .padding(horizontal = 12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().height(ProfileHeaderCollapseMetrics.chromeHeight),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = null,
                    tint = colors.primary,
                )
            }
            Spacer(Modifier.weight(1f))
            Box(Modifier.size(36.dp))
        }
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp)
                .width(200.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (override.isNotEmpty()) {
                Text(
                    override,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else if (moment != null) {
                LiveUsernameContent(
                    userId = moment.authorId,
                    fallbackUsername = moment.username,
                ) { username ->
                    Text(
                        username,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 17.sp,
                        color = colors.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Text(
                stringResource(R.string.profile_tab_moments),
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                color = colors.secondary,
                maxLines = 1,
            )
        }
    }
}
