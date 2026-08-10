package com.moments.android.views.settings.savedmoments

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.MomentsGlassButtonPreset
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.services.cache.ImagePrefetchManager
import com.moments.android.services.cache.VideoPreloader
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.deleteMoment
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.firestore.loadSavedMoments
import com.moments.android.services.performance.FeedVisibilityCoordinator
import com.moments.android.services.performance.VideoMomentsIndex
import com.moments.android.services.performance.toVideoMoments
import com.moments.android.services.social.AffinityInteractionType
import com.moments.android.services.social.AffinityTracker
import com.moments.android.services.video.GlobalVideoManager
import com.moments.android.services.video.VideoPlaybackSelector
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.comments.ModernCommentsSheet
import com.moments.android.views.components.LiveUsernameContent
import com.moments.android.views.components.LiveUsernameText
import com.moments.android.views.explore.ExploreView
import com.moments.android.views.explore.toExploreFeedMoment
import com.moments.android.views.feed.core.StoryUserPresentationRoute
import com.moments.android.views.feed.core.sections.ModernPostCardView
import com.moments.android.views.feed.maps.LocationMapView
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.sharing.ModernShareBottomSheet
import com.moments.android.views.profile.core.sections.profileThumbnailUrl
import com.moments.android.views.profile.momentsview.EditMomentSheet
import com.moments.android.views.profile.momentsview.ModernContextMenuOverlay
import com.moments.android.views.settings.hasVideoMedia
import com.moments.android.views.shared.ScreenshotProtectedView
import com.moments.android.views.shared.momentdetail.MomentDetailSolidTopChrome
import com.moments.android.views.shared.momentdetail.rememberMomentDetailContentTopInset
import com.moments.android.views.story.StoriesView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Port de `ModernSavedMomentsDetailView` (+ header / card / AsyncSavedProfileImageView)
 * en `SavedMomentsView.swift`.
 *
 * La card reutiliza [ModernPostCardView] con `forceSaved` ≡
 * `ModernSavedDetailMomentCard` (`isSaved: .constant(true)`, `onSave` → remove alert).
 */
@Composable
fun ModernSavedMomentsDetailView(
    moments: List<Moment>,
    initialIndex: Int,
    onDismiss: () -> Unit,
    onRemoveMoment: ((Moment) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val listTopInset = rememberMomentDetailContentTopInset(chromeBodyHeight = 56.dp)
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp
    val feedCardHeight = (screenHeightDp * 0.58f).dp
    val feedCardHeightPx = with(density) { feedCardHeight.toPx() }
    val rowSpacing = maxOf(15.dp, (screenHeightDp * 0.02f).dp)

    var domainMoments by remember(moments) { mutableStateOf(moments) }
    var feedMoments by remember(moments) {
        mutableStateOf(moments.map { it.toExploreFeedMoment() })
    }

    val resolvedInitialIndex = remember(domainMoments, initialIndex) {
        if (domainMoments.isEmpty()) 0 else initialIndex.coerceIn(0, domainMoments.lastIndex)
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

    var showingRemoveAlert by remember { mutableStateOf(false) }
    var momentToRemove by remember { mutableStateOf<Moment?>(null) }

    val listState = rememberLazyListState()
    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "savedDetailDrag",
    )
    val scale = if (isDragging) {
        maxOf(0.85f, 1f - kotlin.math.abs(dragOffsetPx) / 1000f)
    } else {
        1f
    }

    val currentDomainMoment = domainMoments.getOrNull(currentIndex)

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
        val moment = domainMoments.getOrNull(index) ?: return
        if (!moment.hasVideoMedia) return
        val consumerId = GlobalVideoManager.profileVideoConsumerId(moment)
        FeedVisibilityCoordinator.pinActiveVideo(consumerId)
        GlobalVideoManager.playVideo(consumerId)
    }

    fun requestRemove(moment: Moment) {
        momentToRemove = moment
        showingRemoveAlert = true
    }

    fun confirmRemove() {
        val target = momentToRemove ?: return
        onRemoveMoment?.invoke(target)
        val id = target.id
        domainMoments = domainMoments.filter { it.id != id }
        feedMoments = feedMoments.filter { it.id != id }
        momentToRemove = null
        showingRemoveAlert = false
        if (domainMoments.isEmpty()) {
            onDismiss()
        } else {
            currentIndex = currentIndex.coerceIn(0, domainMoments.lastIndex)
        }
    }

    fun deleteContextMoment() {
        val target = contextMenuMoment ?: return
        scope.launch {
            runCatching {
                firestore.deleteMoment(userId = target.authorId, momentId = target.id)
            }.onSuccess {
                val domain = domainMoments.firstOrNull { it.id == target.id }
                if (domain != null) {
                    // ≡ iOS deleteMoment success → onRemove() (quita de guardados / lista)
                    onRemoveMoment?.invoke(domain)
                }
                domainMoments = domainMoments.filter { it.id != target.id }
                feedMoments = feedMoments.filter { it.id != target.id }
                if (domainMoments.isEmpty()) {
                    onDismiss()
                } else {
                    currentIndex = currentIndex.coerceIn(0, domainMoments.lastIndex)
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

    // iOS reelsVideos: moments.videoMoments
    val reelsVideos = remember(domainMoments) { domainMoments.toVideoMoments() }

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

    if (domainMoments.isEmpty()) {
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
                .offset { androidx.compose.ui.unit.IntOffset(animatedOffset.roundToInt(), 0) }
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
                    top = listTopInset,
                    bottom = 40.dp,
                    start = FeedMomentCardLayout.listHorizontalPadding,
                    end = FeedMomentCardLayout.listHorizontalPadding,
                ),
                verticalArrangement = Arrangement.spacedBy(40.dp),
            ) {
                itemsIndexed(
                    feedMoments,
                    key = { _, m -> "${m.authorId}_${m.id}" },
                ) { index, moment ->
                    val domain = domainMoments.getOrNull(index)
                        ?: domainMoments.firstOrNull { it.id == moment.id }
                    ScreenshotProtectedView(
                        isProtected = (moment.audience?.lowercase() ?: "") != "everyone",
                        containsHardwareVideo = moment.hasHardwareVideo,
                    ) {
                        ModernPostCardView(
                            moment = moment,
                            availableHeight = feedCardHeightPx,
                            forceSaved = true,
                            onForcedUnsave = {
                                domain?.let { requestRemove(it) }
                            },
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
                                showShareSheet = true
                            },
                            onContextMenu = { tapped ->
                                contextMenuMoment = tapped
                                showContextMenu = true
                            },
                            reelsVideos = reelsVideos,
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
                ModernSavedDetailHeader(
                    moment = currentDomainMoment,
                    onDismiss = onDismiss,
                    onRemove = {
                        currentDomainMoment?.let { requestRemove(it) }
                    },
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

        if (showShareSheet) {
            contextMenuMoment?.let { shareTarget ->
                ModernShareBottomSheet(
                    moment = shareTarget,
                    onDismiss = { showShareSheet = false },
                )
            }
        }

        storyRoute?.let { route ->
            Dialog(
                onDismissRequest = { storyRoute = null },
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
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
                            colors.surfaceBackground.copy(alpha = if (isDark) 0.92f else 0.88f),
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
                properties = DialogProperties(
                    usePlatformDefaultWidth = false,
                    decorFitsSystemWindows = false,
                ),
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

        if (showingRemoveAlert) {
            val target = momentToRemove
            AlertDialog(
                onDismissRequest = {
                    showingRemoveAlert = false
                    momentToRemove = null
                },
                title = { Text(stringResource(R.string.saved_moments_remove_title)) },
                text = {
                    if (target != null) {
                        LiveUsernameContent(
                            userId = target.authorId,
                            fallbackUsername = target.username,
                        ) { username ->
                            Text(stringResource(R.string.saved_moments_remove_message_user, username))
                        }
                    } else {
                        Text(stringResource(R.string.saved_moments_remove_message_generic))
                    }
                },
                confirmButton = {
                    TextButton(onClick = { confirmRemove() }) {
                        Text(stringResource(R.string.saved_moments_remove_confirm), color = Color(0xFFFF3B30))
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showingRemoveAlert = false
                            momentToRemove = null
                        },
                    ) {
                        Text(stringResource(R.string.saved_moments_cancel))
                    }
                },
            )
        }
    }
}

/** Port de `ModernSavedDetailHeader`. */
@Composable
fun ModernSavedDetailHeader(
    moment: Moment?,
    onDismiss: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primaryText = if (isDark) Color.White else Color.Black.copy(alpha = 0.9f)
    val secondaryText = if (isDark) Color.White.copy(alpha = 0.65f) else Color.Black.copy(alpha = 0.55f)
    val iconColor = if (isDark) Color.White else Color.Black.copy(alpha = 0.85f)
    val avatarStroke = if (isDark) Color.White.copy(0.25f) else Color.Black.copy(0.16f)

    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 0.dp),
    ) {
        Row(
            Modifier
                .padding(horizontal = 14.dp)
                .fillMaxWidth()
                .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProfileChromeIconButton(
                icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                onClick = onDismiss,
                foregroundColor = iconColor,
                preset = MomentsGlassButtonPreset.NAVIGATION_BACK,
                standaloneGlass = false,
            )

            if (moment != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    AsyncSavedProfileImageView(
                        userId = moment.authorId,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .border(1.dp, avatarStroke, CircleShape),
                    )
                    Column {
                        LiveUsernameText(
                            userId = moment.authorId,
                            fallbackUsername = moment.username,
                            color = primaryText,
                            style = androidx.compose.ui.text.TextStyle(
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            MomentsFormat.relativeTime(moment.timestamp),
                            fontSize = 10.sp,
                            color = secondaryText,
                            maxLines = 1,
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            Box(
                Modifier
                    .size(38.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.BookmarkBorder,
                    contentDescription = stringResource(R.string.saved_moments_remove),
                    tint = iconColor,
                    modifier = Modifier.size(15.dp),
                )
            }
        }
    }
}

/** Port de `AsyncSavedProfileImageView`. */
@Composable
fun AsyncSavedProfileImageView(
    userId: String,
    modifier: Modifier = Modifier,
) {
    val firestore = remember { FirestoreService() }
    var profileImageURL by remember(userId) { mutableStateOf<String?>(null) }
    var pendingUserId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        pendingUserId = userId
        profileImageURL = null
        val path = runCatching { firestore.fetchUser(userId).profileImagePath }.getOrNull()
        if (pendingUserId == userId) {
            profileImageURL = path
        }
    }

    Box(
        modifier
            .background(Color.Gray.copy(alpha = 0.3f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        val url = profileImageURL?.takeIf { it.isNotBlank() }
        if (url != null) {
            AsyncImage(
                model = profileThumbnailUrl(url),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Icon(Icons.Default.Person, null, tint = Color.Gray, modifier = Modifier.size(18.dp))
        }
    }
}
