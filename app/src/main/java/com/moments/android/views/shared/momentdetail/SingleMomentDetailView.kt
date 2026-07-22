package com.moments.android.views.shared.momentdetail

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.social.AffinityInteractionType
import com.moments.android.services.social.AffinityTracker
import com.moments.android.views.comments.ModernCommentsSheet
import com.moments.android.views.explore.ExploreView
import com.moments.android.views.feed.core.EditMomentPayload
import com.moments.android.views.feed.core.sections.ModernPostCardView
import com.moments.android.views.feed.maps.LocationMapView
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.momentsview.EditMomentView
import com.moments.android.views.profile.momentsview.ModernContextMenuOverlay
import com.moments.android.views.shared.ScreenshotProtectedView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Port por trozos de `SingleMomentDetailView.swift`.
 * Card estilo feed + chrome + context menu + peek + dismiss horizontal.
 *
 * Sheets: EditMoment + Comments + Explore reales; Stories/Profile = stubs.
 */
@Composable
fun SingleMomentDetailView(
    moment: FeedMoment,
    onDismiss: () -> Unit,
    chromeTitle: String? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val colors = rememberAdaptiveColors()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightDp = configuration.screenHeightDp
    val feedCardHeightPx = with(density) { (screenHeightDp * 0.58f).dp.toPx() }

    var currentMoment by remember(moment.id) { mutableStateOf(moment) }
    var trackedMomentView by remember { mutableStateOf(false) }

    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var backgroundOpacity by remember { mutableFloatStateOf(1f) }

    var showContextMenu by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }
    var showDeleteAlert by remember { mutableStateOf(false) }
    var selectedForComments by remember { mutableStateOf(false) }
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

    val resolvedChromeTitle = remember(chromeTitle, currentMoment.username) {
        val trimmedChrome = chromeTitle?.trim().orEmpty()
        when {
            trimmedChrome.isNotEmpty() -> trimmedChrome
            currentMoment.username.trim().isNotEmpty() -> currentMoment.username.trim()
            else -> null
        }
    } ?: stringResource(R.string.tab_bar_explore)

    val animatedOffset by animateFloatAsState(
        targetValue = dragOffsetPx,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 400f),
        label = "detailDrag",
    )
    val scale = if (isDragging) {
        maxOf(0.85f, 1f - kotlin.math.abs(dragOffsetPx) / 1000f)
    } else {
        1f
    }

    fun trackMomentViewIfNeeded() {
        if (trackedMomentView || currentMoment.id.isEmpty() || currentMoment.authorId.isEmpty()) return
        trackedMomentView = true
        AffinityTracker.trackInteraction(AffinityInteractionType.MOMENT_VIEW, currentMoment.authorId)
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
            NavigationEventBus.emit(CoordinatorNavigationEvent.ShowStories)
        } else {
            openUserProfile(normalized)
        }
    }

    fun handlePeek(imageUrl: String, ratio: Float, isPressing: Boolean) {
        if (isPressing) {
            peekImageUrl = imageUrl
            peekAspectRatio = ratio
            peekIsProtected = (currentMoment.audience?.lowercase() ?: "") != "everyone"
            isPeeking = true
        } else {
            isPeeking = false
            peekIsProtected = false
        }
    }

    fun deleteMoment() {
        scope.launch {
            runCatching {
                firestore.deleteMoment(userId = currentMoment.authorId, momentId = currentMoment.id)
            }.onSuccess { onDismiss() }
        }
    }

    LaunchedEffect(currentMoment.id) {
        trackMomentViewIfNeeded()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null) {
            runCatching { firestore.loadSavedMoments(uid) }
        }
        // GlobalVideoManager.pauseAllVideos / activate — cuando exista manager global.
        delay(150)
    }

    DisposableEffect(Unit) {
        onDispose {
            // iOS: pauseAllVideos + FeedVisibilityCoordinator.clear + feedViewModel.shutdown
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
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = FeedMomentCardLayout.listHorizontalPadding)
                    .padding(bottom = 24.dp),
            ) {
                Spacer(Modifier.height(ProfileHeaderCollapseMetrics.feedStyleDetailTopInset))
                val isProtected = (currentMoment.audience?.lowercase() ?: "") != "everyone"
                ScreenshotProtectedView(isProtected = isProtected) {
                    ModernPostCardView(
                        moment = currentMoment,
                        onOpenProfile = { openUserProfile(currentMoment.authorId) },
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
                        onOpenComments = { selectedForComments = true },
                        onShare = { showContextMenu = true },
                        onContextMenu = { _ -> showContextMenu = true },
                        onAuthorAvatarTap = { authorId, hasStory ->
                            handleAuthorAvatarTap(authorId, hasStory)
                        },
                        onPeek = { url, ratio, pressing -> handlePeek(url, ratio, pressing) },
                        onTagTap = { userId -> openUserProfile(userId) },
                        availableHeight = feedCardHeightPx,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            FeedPinnedTopChrome(
                title = resolvedChromeTitle,
                onDismiss = onDismiss,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }

        if (showContextMenu) {
            ModernContextMenuOverlay(
                moment = currentMoment,
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
            Box(Modifier.fillMaxSize().background(colors.surfaceBackground)) {
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

        if (showDeleteAlert) {
            AlertDialog(
                onDismissRequest = { showDeleteAlert = false },
                title = { Text(stringResource(R.string.feed_actions_delete_title)) },
                text = { Text(stringResource(R.string.feed_delete_confirm)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteAlert = false
                            deleteMoment()
                        },
                    ) {
                        Text(stringResource(R.string.feed_actions_delete), color = Color.Red)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteAlert = false }) {
                        Text(stringResource(R.string.feed_actions_cancel))
                    }
                },
            )
        }

        // sheet → EditMomentView
        if (showEditSheet) {
            Dialog(
                onDismissRequest = { showEditSheet = false },
                properties = DialogProperties(usePlatformDefaultWidth = false),
            ) {
                Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                    EditMomentView(
                        moment = currentMoment,
                        onSave = { payload ->
                            scope.launch {
                                runCatching {
                                    val coord = if (payload.locationLatitude != null && payload.locationLongitude != null) {
                                        com.moments.android.models.Moment.LocationCoordinate(
                                            latitude = payload.locationLatitude,
                                            longitude = payload.locationLongitude,
                                        )
                                    } else {
                                        null
                                    }
                                    firestore.updateMomentDetails(
                                        userId = currentMoment.authorId,
                                        momentId = currentMoment.id,
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
                                    currentMoment = currentMoment.copy(
                                        content = payload.content,
                                        audience = payload.audience,
                                        customListId = payload.customListId,
                                        location = payload.locationName.ifEmpty { null },
                                        locationCoordinate = coord,
                                    )
                                }
                                showEditSheet = false
                            }
                        },
                        onDismiss = { showEditSheet = false },
                    )
                }
            }
        }

        if (selectedForComments) {
            ModernCommentsSheet(
                moment = currentMoment,
                onDismiss = { selectedForComments = false },
            )
        }

        // ExploreView(initialSearchQuery:)
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

@Composable
private fun DetailPresentationPlaceholder(
    title: String,
    subtitle: String? = null,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                Modifier
                    .padding(32.dp)
                    .background(
                        if (isDark) Color(0xFF1C1C1E) else Color.White,
                        RoundedCornerShape(16.dp),
                    )
                    .clickable(enabled = false) {}
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(title, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(subtitle, fontSize = 14.sp, color = Color.Gray)
                }
                Spacer(Modifier.height(16.dp))
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.common_close))
                }
            }
        }
    }
}
