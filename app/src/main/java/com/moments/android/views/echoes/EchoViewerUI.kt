package com.moments.android.views.echoes

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Echo
import com.moments.android.models.EchoMomentRef
import com.moments.android.models.EchoParticipant
import com.moments.android.models.EchoParticipantStatus
import com.moments.android.models.Moment
import com.moments.android.models.resolveAspectRatioValue
import com.moments.android.services.social.EchoService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.viewmodels.EchoDeckPost
import com.moments.android.viewmodels.EchoViewModel
import com.moments.android.viewmodels.GroupedPerspective
import com.moments.android.views.components.EchoesIconGradients
import com.moments.android.views.components.EchoesIconMetrics
import com.moments.android.views.components.EchoesIconView
import com.moments.android.views.components.MomentCaptionPresentationStyle
import com.moments.android.views.components.MomentCaptionView
import com.moments.android.views.creator.components.StoryVideoGravity
import com.moments.android.views.creator.components.StoryVideoPlayerView
import com.moments.android.views.feed.maps.LocationMapView
import com.moments.android.views.feed.moments.MomentCarouselIndicatorTone
import com.moments.android.views.feed.moments.MomentCarouselLayoutRules
import com.moments.android.views.feed.moments.MomentCarouselPageIndicators
import com.moments.android.views.feed.moments.MomentCarouselPresentationMode
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private enum class DeckSwipeAxis {
    Horizontal,
    Vertical,
}

/**
 * Port 1:1 de `EchoViewerUI.swift` — mazo de postales + selector de perspectiva.
 */
@Composable
fun EchoViewerUI(
    echoId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialEcho: Echo? = null,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val viewModel = remember(echoId) { EchoViewModel(echoId, initialEcho) }
    val echo by viewModel.echo.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val perspectives by viewModel.groupedPerspectives.collectAsState()
    val perspectiveIndex by viewModel.currentPerspectiveIndex.collectAsState()
    val verticalIndex by viewModel.currentVerticalIndex.collectAsState()
    val availability by viewModel.momentAvailability.collectAsState()
    val postCaptions by viewModel.postCaptions.collectAsState()
    val postMoments by viewModel.postMoments.collectAsState()
    val postAspectRatios by viewModel.postAspectRatios.collectAsState()
    val isVideoPlaying by viewModel.isVideoPlaying.collectAsState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var deckSwipeAxis by remember { mutableStateOf<DeckSwipeAxis?>(null) }
    var showIncompleteDecision by remember { mutableStateOf(false) }
    var showLockoutAlert by remember { mutableStateOf(false) }
    var showLeaveMenu by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }
    var carouselIndex by remember { mutableIntStateOf(0) }

    val locationFallback = stringResource(R.string.echo_viewer_location_fallback)
    val currentPost = perspectives.getOrNull(perspectiveIndex)?.posts?.getOrNull(verticalIndex)

    LaunchedEffect(echoId) {
        showIncompleteDecision = viewModel.isHistoricalIncomplete
        viewModel.loadEcho()
    }
    LaunchedEffect(viewModel.isHistoricalIncomplete) {
        showIncompleteDecision = viewModel.isHistoricalIncomplete
    }
    LaunchedEffect(currentPost?.momentId, perspectiveIndex) {
        carouselIndex = 0
    }
    LaunchedEffect(currentPost?.momentId, postMoments) {
        val slides = currentPost?.let { viewModel.visibleSlides(it) }.orEmpty()
        if (slides.isNotEmpty() && carouselIndex >= slides.size) {
            carouselIndex = slides.lastIndex
        }
    }
    LaunchedEffect(currentPost?.momentId) {
        currentPost?.let { viewModel.loadPostDetailsIfNeeded(it) }
    }
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }

    fun leaveEchoAction(userId: String) {
        val id = viewModel.echo.value?.id ?: echoId
        if (id.isBlank()) {
            onDismiss()
            return
        }
        scope.launch {
            runCatching { EchoService.leaveEcho(id, userId) }
                .onSuccess { onDismiss() }
                .onFailure { error ->
                    if (error.message?.contains("echo.leave.locked") == true) {
                        showLockoutAlert = true
                    } else {
                        onDismiss()
                    }
                }
        }
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground),
    ) {
        when {
            loading -> CircularProgressIndicator(
                Modifier.align(Alignment.Center),
                color = colors.primary,
            )
            echo != null -> {
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding(),
                ) {
                    EchoSessionHeader(
                        echo = echo!!,
                        perspectiveCount = perspectives.size,
                        canOpenMap = viewModel.canOpenLocationMap,
                        showLeaveMenu = showLeaveMenu,
                        onShowLeaveMenuChange = { showLeaveMenu = it },
                        onOpenMap = {
                            HapticManager.shared.lightImpact()
                            showLocation = true
                        },
                        onLeave = {
                            FirebaseAuth.getInstance().currentUser?.uid?.let(::leaveEchoAction)
                        },
                        onDismiss = onDismiss,
                    )

                    when {
                        viewModel.canBrowseMedia && currentPost != null -> {
                            EchoDeckStage(
                                viewModel = viewModel,
                                perspectives = perspectives,
                                perspectiveIndex = perspectiveIndex,
                                verticalIndex = verticalIndex,
                                availability = availability,
                                postCaptions = postCaptions,
                                postMoments = postMoments,
                                postAspectRatios = postAspectRatios,
                                isVideoPlaying = isVideoPlaying,
                                currentPost = currentPost,
                                dragOffset = dragOffset,
                                carouselIndex = carouselIndex,
                                onCarouselIndexChange = { carouselIndex = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .pointerInput(viewModel.canBrowseMedia, perspectiveIndex, verticalIndex) {
                                        val thresholdPx = with(density) { 56.dp.toPx() }
                                        detectDragGestures(
                                            onDragStart = {
                                                dragOffset = Offset.Zero
                                                deckSwipeAxis = null
                                            },
                                            onDragCancel = {
                                                dragOffset = Offset.Zero
                                                deckSwipeAxis = null
                                            },
                                            onDragEnd = {
                                                if (viewModel.canBrowseMedia) {
                                                    when (deckSwipeAxis) {
                                                        DeckSwipeAxis.Horizontal -> {
                                                            if (dragOffset.x < -thresholdPx) {
                                                                HapticManager.shared.selection()
                                                                viewModel.switchPerspective(perspectiveIndex + 1)
                                                            } else if (dragOffset.x > thresholdPx) {
                                                                HapticManager.shared.selection()
                                                                viewModel.switchPerspective(perspectiveIndex - 1)
                                                            }
                                                        }
                                                        DeckSwipeAxis.Vertical -> {
                                                            if (dragOffset.y < -thresholdPx) {
                                                                HapticManager.shared.selection()
                                                                viewModel.switchVerticalIndex(verticalIndex + 1)
                                                            } else if (dragOffset.y > thresholdPx) {
                                                                HapticManager.shared.selection()
                                                                viewModel.switchVerticalIndex(verticalIndex - 1)
                                                            }
                                                        }
                                                        null -> Unit
                                                    }
                                                }
                                                dragOffset = Offset.Zero
                                                deckSwipeAxis = null
                                            },
                                            onDrag = { change, amount ->
                                                if (!viewModel.canBrowseMedia) return@detectDragGestures
                                                change.consume()
                                                val next = dragOffset + amount
                                                if (deckSwipeAxis == null) {
                                                    if (kotlin.math.abs(next.x) > kotlin.math.abs(next.y)) {
                                                        deckSwipeAxis = DeckSwipeAxis.Horizontal
                                                    } else if (kotlin.math.abs(next.y) > kotlin.math.abs(next.x)) {
                                                        deckSwipeAxis = DeckSwipeAxis.Vertical
                                                    }
                                                }
                                                dragOffset = when (deckSwipeAxis) {
                                                    DeckSwipeAxis.Horizontal -> Offset(next.x, 0f)
                                                    DeckSwipeAxis.Vertical -> Offset(0f, next.y)
                                                    null -> Offset.Zero
                                                }
                                            },
                                        )
                                    },
                            )
                        }
                        viewModel.isHistoricalIncomplete -> {
                            Spacer(Modifier.weight(1f))
                        }
                        else -> {
                            EchoWaitingState(
                                echo?.participants.orEmpty(),
                                Modifier.weight(1f).fillMaxWidth(),
                            )
                        }
                    }

                    if (viewModel.canBrowseMedia && perspectives.isNotEmpty()) {
                        EchoPerspectiveChooser(
                            perspectives = perspectives,
                            selectedIndex = perspectiveIndex,
                            onSelect = viewModel::switchPerspective,
                        )
                    }
                }
            }
        }

        if (showLockoutAlert) {
            GlassLockoutAlert(onDismiss = { showLockoutAlert = false })
        }
        if (showIncompleteDecision) {
            IncompleteDecisionOverlay(
                isDark = isDark,
                onDelete = {
                    FirebaseAuth.getInstance().currentUser?.uid?.let(::leaveEchoAction)
                },
                onKeep = { showIncompleteDecision = false },
            )
        }

        if (showLocation && echo != null) {
            Dialog(
                onDismissRequest = { showLocation = false },
                properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
            ) {
                LocationMapView(
                    locationName = echo?.locationName?.takeIf { it.isNotBlank() } ?: locationFallback,
                    latitude = echo?.location?.latitude,
                    longitude = echo?.location?.longitude,
                    echoHistoryUserId = FirebaseAuth.getInstance().currentUser?.uid,
                    echoHistoryOnly = true,
                    onDismiss = { showLocation = false },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun EchoSessionHeader(
    echo: Echo,
    perspectiveCount: Int,
    canOpenMap: Boolean,
    showLeaveMenu: Boolean,
    onShowLeaveMenuChange: (Boolean) -> Unit,
    onOpenMap: () -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val locationFallback = stringResource(R.string.echo_viewer_location_fallback)
    val time = MomentsFormat.smartDate(echo.createdAt, MomentsFormat.DateContext.TIME_ONLY)
    val subtitle = if (perspectiveCount > 0) "$time · $perspectiveCount" else time
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .graphicsLayer { alpha = if (canOpenMap) 1f else 0.55f }
                .clickable(enabled = canOpenMap, onClick = onOpenMap),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                Icons.Filled.LocationOn,
                contentDescription = null,
                tint = colors.primary.copy(0.88f),
                modifier = Modifier.size(13.dp),
            )
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    echo.locationName?.takeIf { it.isNotBlank() } ?: locationFallback,
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    autoSize = TextAutoSize.StepBased(minFontSize = 11.sp, maxFontSize = 15.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    subtitle,
                    color = colors.secondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Box {
            Box(
                Modifier
                    .size(36.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable { onShowLeaveMenuChange(true) },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.MoreHoriz, null, tint = colors.primary, modifier = Modifier.size(16.dp))
            }
            DropdownMenu(
                expanded = showLeaveMenu,
                onDismissRequest = { onShowLeaveMenuChange(false) },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.echo_viewer_leave), color = Color.Red) },
                    onClick = {
                        onShowLeaveMenuChange(false)
                        onLeave()
                    },
                )
            }
        }
        Box(
            Modifier
                .size(36.dp)
                .momentsChromeGlass(CircleShape, interactive = true)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, null, tint = colors.primary, modifier = Modifier.size(15.dp))
        }
    }
}

@Composable
private fun EchoDeckStage(
    viewModel: EchoViewModel,
    perspectives: List<GroupedPerspective>,
    perspectiveIndex: Int,
    verticalIndex: Int,
    availability: Map<String, Boolean>,
    postCaptions: Map<String, String>,
    postMoments: Map<String, Moment>,
    postAspectRatios: Map<String, String>,
    isVideoPlaying: Boolean,
    currentPost: EchoDeckPost,
    dragOffset: Offset,
    carouselIndex: Int,
    onCarouselIndexChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        val hasSidePeeks = perspectives.size > 1
        val peekWidth = 18.dp
        val authorsBehind = perspectiveIndex
        val authorsAhead = maxOf(0, perspectives.size - perspectiveIndex - 1)
        val isLastAuthor = perspectives.isEmpty() || perspectiveIndex >= perspectives.size - 1
        val peekLeading = isLastAuthor
        val stackCount = if (isLastAuthor) authorsBehind else authorsAhead
        val visibleLayerCount = minOf(stackCount, 3)
        val deckLayerDepths = if (visibleLayerCount > 0) (visibleLayerCount downTo 1).toList() else emptyList()
        val sidePeekStep = 4.dp
        val bottomPeekStep = 4.dp
        val maxDeckDepth = (deckLayerDepths.maxOrNull() ?: 0).toFloat()
        val deckSideOverflow = sidePeekStep * maxDeckDepth
        val deckBottomOverflow = bottomPeekStep * maxDeckDepth
        val revealDenom = with(density) { 72.dp.toPx() }
        val dragReveal = ((if (peekLeading) dragOffset.x else -dragOffset.x) / revealDenom)
            .coerceIn(0f, 1f)
        val cardWidth = maxWidth - (if (hasSidePeeks) peekWidth * 2 else 20.dp) - deckSideOverflow
        val caption = resolvedCaption(currentPost, postCaptions, postMoments)
        val hasCaption = caption.trim().isNotEmpty()
        val slideCount = currentPost?.let { viewModel.visibleSlides(it).size } ?: 0
        val hasSlideDots = slideCount > 1
        val slideDotsOutside = if (hasSlideDots) 18.dp else 0.dp
        val captionOutside = (if (hasCaption) 52.dp else 0.dp) + slideDotsOutside
        val maxCardHeight = maxHeight - captionOutside - deckBottomOverflow - 6.dp
        val mediaRatio = resolvedMediaAspectRatio(currentPost, postAspectRatios)
        val mediaHeight = deckMediaHeight(cardWidth, mediaRatio, maxCardHeight)
        val headerHeight = 48.dp
        val cardHeight = headerHeight + mediaHeight
        val canGoPrev = perspectiveIndex > 0
        val canGoNext = perspectiveIndex < perspectives.size - 1
        val layerFill = if (isDark) Color(0xFF3A4550) else Color(0xFFC9C4BA)
        val perspective = perspectives.getOrNull(perspectiveIndex)
        val isAvailable = availability[currentPost.momentId] != false

        Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (hasSidePeeks) {
                    PerspectiveHandle(
                        leading = true,
                        width = peekWidth,
                        height = cardHeight * 0.5f,
                        enabled = canGoPrev,
                        onClick = {
                            HapticManager.shared.selection()
                            viewModel.switchPerspective(perspectiveIndex - 1)
                        },
                    )
                } else {
                    Spacer(Modifier.width(10.dp))
                }

                Column(horizontalAlignment = Alignment.Start) {
                    Box(
                        Modifier.size(
                            width = cardWidth + deckSideOverflow,
                            height = cardHeight + deckBottomOverflow,
                        ),
                        contentAlignment = if (peekLeading) Alignment.TopEnd else Alignment.TopStart,
                    ) {
                        deckLayerDepths.forEach { depth ->
                            val grow = sidePeekStep * depth + 1.5.dp * dragReveal
                            val growY = bottomPeekStep * depth + 1.5.dp * dragReveal
                            Box(
                                Modifier
                                    .zIndex((visibleLayerCount - depth).toFloat())
                                    .size(cardWidth + grow, cardHeight + growY)
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(layerFill)
                                    .border(1.dp, colors.primary.copy(0.35f), RoundedCornerShape(22.dp)),
                            )
                        }

                        EchoDeckFrontCard(
                            post = currentPost,
                            slides = currentPost.let { viewModel.visibleSlides(it) },
                            perspective = perspective,
                            isAvailable = isAvailable,
                            width = cardWidth,
                            headerHeight = headerHeight,
                            mediaHeight = mediaHeight,
                            mediaRatio = mediaRatio,
                            carouselIndex = carouselIndex,
                            onCarouselIndexChange = onCarouselIndexChange,
                            isVideoPlaying = isVideoPlaying,
                            modifier = Modifier
                                .zIndex(10f)
                                .offset {
                                    IntOffset(
                                        (dragOffset.x * 0.35f).roundToInt(),
                                        (dragOffset.y * 0.35f).roundToInt(),
                                    )
                                },
                        )

                        LayerDots(
                            postsCount = perspective?.posts?.size ?: 0,
                            verticalIndex = verticalIndex,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .padding(
                                    end = if (peekLeading) 8.dp else 8.dp + deckSideOverflow,
                                    bottom = deckBottomOverflow,
                                ),
                        )
                    }

                    if (hasSlideDots) {
                        MomentCarouselPageIndicators(
                            count = slideCount,
                            currentIndex = carouselIndex,
                            tone = MomentCarouselIndicatorTone.OnCanvas,
                            onIndexChange = onCarouselIndexChange,
                            modifier = Modifier
                                .width(cardWidth)
                                .padding(top = 8.dp)
                                .padding(start = if (peekLeading) deckSideOverflow else 0.dp),
                        )
                    }

                    if (hasCaption) {
                        MomentCaptionView(
                            content = caption,
                            onHashtagTap = {},
                            style = MomentCaptionPresentationStyle.Echo,
                            moment = viewModel.playbackMoment(currentPost).let { base ->
                                if (caption.trim() == base.content.trim()) base
                                else base.copy(content = caption)
                            },
                            modifier = Modifier
                                .width(cardWidth)
                                .padding(start = if (peekLeading) deckSideOverflow else 0.dp)
                                .padding(top = if (hasSlideDots) 4.dp else 0.dp),
                        )
                    }
                }

                if (hasSidePeeks) {
                    PerspectiveHandle(
                        leading = false,
                        width = peekWidth,
                        height = cardHeight * 0.5f,
                        enabled = canGoNext,
                        onClick = {
                            HapticManager.shared.selection()
                            viewModel.switchPerspective(perspectiveIndex + 1)
                        },
                    )
                } else {
                    Spacer(Modifier.width(10.dp))
                }
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun EchoDeckFrontCard(
    post: EchoDeckPost,
    slides: List<EchoMomentRef>,
    perspective: GroupedPerspective?,
    isAvailable: Boolean,
    width: androidx.compose.ui.unit.Dp,
    headerHeight: androidx.compose.ui.unit.Dp,
    mediaHeight: androidx.compose.ui.unit.Dp,
    mediaRatio: Float,
    carouselIndex: Int,
    onCarouselIndexChange: (Int) -> Unit,
    isVideoPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val cardShape = RoundedCornerShape(22.dp)
    Column(
        modifier
            .width(width)
            .shadow(8.dp, cardShape, ambientColor = Color.Black.copy(if (isDark) 0.28f else 0.1f))
            .clip(cardShape)
            .background(colors.surfaceBackground)
            .border(1.dp, colors.primary.copy(0.1f), cardShape),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .background(colors.surfaceBackground)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (perspective != null) {
                AsyncProfileImageView(
                    perspective.authorId,
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .border(1.dp, colors.primary.copy(0.16f), CircleShape),
                )
                Column(verticalArrangement = Arrangement.spacedBy(1.dp), modifier = Modifier.weight(1f)) {
                    Text(
                        perspective.username,
                        color = colors.primary,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        MomentsFormat.relativeTime(post.timestamp),
                        color = colors.secondary,
                        fontWeight = FontWeight.Medium,
                        fontSize = 11.sp,
                        maxLines = 1,
                    )
                }
            }
        }

        Box(
            Modifier
                .width(width)
                .height(mediaHeight)
                .clip(RoundedCornerShape(0.dp)),
        ) {
            // SurfaceView del vídeo no se difumina: no montar media si no está disponible.
            if (isAvailable && slides.isNotEmpty()) {
                EchoDeckCarousel(
                    slides = slides,
                    currentIndex = carouselIndex,
                    onIndexChange = onCarouselIndexChange,
                    isAvailable = true,
                    mediaRatio = mediaRatio,
                    isVideoPlaying = isVideoPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                UnavailablePlaceholder()
            }
        }
    }
}

@Composable
private fun EchoDeckCarousel(
    slides: List<EchoMomentRef>,
    currentIndex: Int,
    onIndexChange: (Int) -> Unit,
    isAvailable: Boolean,
    mediaRatio: Float,
    isVideoPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isCarousel = slides.size > 1
    val pagerState = rememberPagerState(initialPage = currentIndex.coerceAtLeast(0)) { slides.size.coerceAtLeast(1) }

    LaunchedEffect(currentIndex, slides.size) {
        if (slides.isEmpty()) return@LaunchedEffect
        val target = currentIndex.coerceIn(0, slides.lastIndex)
        if (pagerState.currentPage != target) {
            pagerState.scrollToPage(target)
        }
    }
    LaunchedEffect(pagerState.currentPage, pagerState.isScrollInProgress) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != currentIndex) {
            onIndexChange(pagerState.currentPage)
        }
    }

    BoxWithConstraints(modifier.background(colors.primary.copy(0.06f))) {
        val canvasRatio = maxWidth.value / maxOf(maxHeight.value, 1f)
        if (slides.isEmpty()) return@BoxWithConstraints
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = false,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val slide = slides[page]
            EchoDeckSlide(
                slide = slide,
                canvasRatio = canvasRatio,
                mediaRatio = mediaRatio,
                allowsVideo = isAvailable && page == currentIndex,
                isVideoPlaying = isVideoPlaying,
                isCarousel = isCarousel,
                onMediaTap = { direction ->
                    if (!isCarousel) return@EchoDeckSlide
                    val count = slides.size
                    val next = (currentIndex + direction + count) % count
                    if (next != currentIndex) {
                        HapticManager.shared.selection()
                        onIndexChange(next)
                    }
                },
            )
        }
    }
}

@Composable
private fun EchoDeckSlide(
    slide: EchoMomentRef,
    canvasRatio: Float,
    mediaRatio: Float,
    allowsVideo: Boolean,
    isVideoPlaying: Boolean,
    isCarousel: Boolean,
    onMediaTap: (Int) -> Unit,
) {
    val slideRatio = parseSlideRatio(slide.aspectRatio) ?: mediaRatio
    val mode = MomentCarouselLayoutRules.presentationMode(slideRatio, canvasRatio)
    val isFit = mode == MomentCarouselPresentationMode.FitWithBlur
    val preview = slide.thumbnailUrl?.takeIf { it.isNotBlank() } ?: slide.mediaUrl

    Box(Modifier.fillMaxSize()) {
        if (slide.mediaType == "video") {
            StoryVideoPlayerView(
                Uri.parse(slide.mediaUrl),
                if (isFit) StoryVideoGravity.RESIZE_ASPECT else StoryVideoGravity.RESIZE_ASPECT_FILL,
                isPlaying = allowsVideo && isVideoPlaying,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (isFit) {
                AsyncImage(
                    model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                        .data(preview)
                        .transformations(com.moments.android.views.creator.creatoruikit.feedCropTransformations(slide.feedCrop))
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(18.dp),
                    alpha = 0.55f,
                )
            }
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(preview)
                    .transformations(com.moments.android.views.creator.creatoruikit.feedCropTransformations(slide.feedCrop))
                    .build(),
                contentDescription = null,
                contentScale = if (isFit) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (isCarousel) {
            CarouselTapZones(onMediaTap = onMediaTap)
        }
    }
}

@Composable
private fun CarouselTapZones(onMediaTap: (Int) -> Unit) {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).fillMaxHeight()) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { onMediaTap(-1) },
            )
            Row(Modifier.height(64.dp).fillMaxWidth()) {
                Spacer(Modifier.size(72.dp, 64.dp))
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onMediaTap(-1) },
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onMediaTap(1) },
        )
    }
}

@Composable
private fun PerspectiveHandle(
    leading: Boolean,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val shape = RoundedCornerShape(
        topStart = if (leading) 0.dp else 14.dp,
        topEnd = if (leading) 14.dp else 0.dp,
        bottomEnd = if (leading) 14.dp else 0.dp,
        bottomStart = if (leading) 0.dp else 14.dp,
    )
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val label = stringResource(
        if (leading) R.string.echo_viewer_perspective_previous else R.string.echo_viewer_perspective_next,
    )
    val scale = if (pressed && enabled) 0.92f else 1f
    Box(
        Modifier
            .width(width)
            .height(height)
            .scale(scale)
            .graphicsLayer { alpha = if (!enabled) 0.35f else if (pressed) 0.75f else 1f }
            .clip(shape)
            .background(colors.primary.copy(if (isDark) 0.16f else 0.22f))
            .border(1.dp, colors.primary.copy(if (isDark) 0.14f else 0.32f), shape)
            .semantics { contentDescription = label }
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    )
}

@Composable
private fun LayerDots(
    postsCount: Int,
    verticalIndex: Int,
    modifier: Modifier = Modifier,
) {
    if (postsCount <= 1) return
    val colors = rememberAdaptiveColors()
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        repeat(postsCount) { index ->
            val selected = index == verticalIndex
            Box(
                Modifier
                    .width(if (selected) 4.dp else 3.dp)
                    .height(if (selected) 18.dp else 9.dp)
                    .clip(RoundedCornerShape(50))
                    .background(colors.primary.copy(if (selected) 0.9f else 0.22f)),
            )
        }
    }
}

@Composable
private fun EchoPerspectiveChooser(
    perspectives: List<GroupedPerspective>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .background(colors.surfaceBackground)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp)
            .padding(top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        perspectives.forEachIndexed { index, perspective ->
            val selected = index == selectedIndex
            val scale by animateFloatAsState(if (selected) 1.04f else 1f, tween(180), label = "echoPerspectiveScale")
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) {
                    if (index != selectedIndex) {
                        HapticManager.shared.selection()
                        onSelect(index)
                    }
                },
            ) {
                Box {
                    AsyncProfileImageView(
                        perspective.authorId,
                        modifier = Modifier
                            .size(48.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .border(
                                if (selected) 2.5.dp else 1.dp,
                                if (selected) colors.accent else colors.primary.copy(0.18f),
                                CircleShape,
                            ),
                    )
                    if (perspective.posts.size > 1) {
                        Text(
                            "${perspective.posts.size}",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.primary,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .offset(x = 4.dp, y = 2.dp)
                                .background(colors.surfaceBackground, RoundedCornerShape(50))
                                .border(1.dp, colors.primary.copy(0.16f), RoundedCornerShape(50))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    perspective.username,
                    color = if (selected) colors.primary else colors.secondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 72.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun UnavailablePlaceholder() {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.surfaceBackground)
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            null,
            tint = colors.secondary,
            modifier = Modifier.size(40.dp),
        )
        Text(
            stringResource(R.string.echo_viewer_unavailable),
            color = colors.primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
    }
}

private fun resolvedCaption(
    post: EchoDeckPost?,
    postCaptions: Map<String, String>,
    postMoments: Map<String, Moment>,
): String {
    if (post == null) return ""
    return postCaptions[post.momentId] ?: postMoments[post.momentId]?.content.orEmpty()
}

private fun resolvedMediaAspectRatio(post: EchoDeckPost, postAspectRatios: Map<String, String>): Float {
    val raw = postAspectRatios[post.momentId] ?: post.aspectRatio
    return parseAspectRatio(raw)
}

private fun parseAspectRatio(raw: String?): Float {
    if (raw.isNullOrBlank()) return 1f
    val normalized = raw.trim()
    val parts = normalized.split(":")
    if (parts.size == 2) {
        val width = parts[0].toDoubleOrNull()
        val height = parts[1].toDoubleOrNull()
        if (width != null && height != null && height > 0) {
            val ratio = (width / height).toFloat()
            if (ratio.isFinite() && ratio > 0f) return ratio
        }
    }
    return resolveAspectRatioValue(normalized, null) ?: 1f
}

private fun parseSlideRatio(raw: String?): Float? {
    if (raw.isNullOrBlank()) return null
    val parts = raw.split(":")
    if (parts.size == 2) {
        val width = parts[0].toDoubleOrNull()
        val height = parts[1].toDoubleOrNull()
        if (width != null && height != null && height > 0) {
            val ratio = (width / height).toFloat()
            if (ratio.isFinite() && ratio > 0f) return ratio
        }
    }
    return null
}

private fun deckMediaHeight(
    cardWidth: androidx.compose.ui.unit.Dp,
    mediaRatio: Float,
    maxCardHeight: androidx.compose.ui.unit.Dp,
): androidx.compose.ui.unit.Dp {
    val headerReserve = 48.dp
    val available = maxOf(200.dp, maxCardHeight - headerReserve)
    val ideal = cardWidth / maxOf(mediaRatio, 0.01f)
    val softCap = cardWidth * 1.45f
    return minOf(maxOf(ideal, 200.dp), available, softCap)
}

@Composable
private fun EchoWaitingState(
    participants: List<EchoParticipant>,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        EchoesIconView(
            size = EchoesIconMetrics.viewerLoading,
            gradient = EchoesIconGradients.brandDiagonal,
        )
        Spacer(Modifier.height(24.dp))
        Text(
            stringResource(R.string.echo_viewer_waiting_title),
            color = colors.primary,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
        Text(
            stringResource(R.string.echo_viewer_waiting_subtitle),
            color = colors.secondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(top = 10.dp)) {
            Box(
                Modifier.width(
                    if (participants.isEmpty()) 0.dp
                    else (40 + (participants.size - 1) * 30).dp,
                ).height(40.dp),
            ) {
                participants.forEachIndexed { index, p ->
                    val accepted = p.status == EchoParticipantStatus.ACCEPTED
                    AsyncProfileImageView(
                        userId = p.userId,
                        modifier = Modifier
                            .offset(x = (30 * index).dp)
                            .size(40.dp)
                            .graphicsLayer { alpha = if (accepted) 1f else 0.4f }
                            .clip(CircleShape)
                            .border(
                                2.dp,
                                if (accepted) Color(0xFFFF9500) else Color.White.copy(0.2f),
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

@Composable
private fun GlassLockoutAlert(onDismiss: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.4f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 40.dp)
                .width(300.dp)
                .momentsChromeGlass(RoundedCornerShape(24.dp), interactive = false)
                .background(Color.Black.copy(0.24f), RoundedCornerShape(24.dp))
                .padding(22.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(R.string.echo_leave_locked),
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            Text(
                stringResource(R.string.echo_viewer_ok),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .background(Color.Black.copy(0.22f), RoundedCornerShape(50))
                    .clickable(onClick = onDismiss)
                    .padding(vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun IncompleteDecisionOverlay(
    isDark: Boolean,
    onDelete: () -> Unit,
    onKeep: () -> Unit,
) {
    val primary = if (isDark) Color.White else Color.Black
    val secondary = primary.copy(0.72f)
    val divider = primary.copy(0.12f)
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(0.42f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .width(320.dp)
                .shadow(24.dp, RoundedCornerShape(28.dp), spotColor = Color.Black.copy(0.24f))
                .momentsChromeGlass(RoundedCornerShape(28.dp), interactive = false),
        ) {
            Column(
                Modifier.padding(horizontal = 22.dp).padding(top = 22.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    stringResource(R.string.echo_viewer_incomplete_title),
                    color = primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                )
                Text(
                    stringResource(R.string.echo_viewer_incomplete_body),
                    color = secondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 15.sp,
                    textAlign = TextAlign.Center,
                )
            }
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(divider))
            Text(
                stringResource(R.string.echo_viewer_incomplete_delete),
                color = Color.Red,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDelete)
                    .padding(vertical = 17.dp),
            )
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(divider))
            Text(
                stringResource(R.string.echo_viewer_keep),
                color = primary,
                fontWeight = FontWeight.Medium,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onKeep)
                    .padding(vertical = 17.dp),
            )
        }
    }
}
