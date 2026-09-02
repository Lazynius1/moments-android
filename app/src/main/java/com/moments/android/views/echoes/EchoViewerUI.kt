package com.moments.android.views.echoes

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.imageLoader
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Echo
import com.moments.android.models.EchoParticipant
import com.moments.android.models.EchoParticipantStatus
import com.moments.android.services.social.EchoService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.viewmodels.EchoViewModel
import com.moments.android.viewmodels.GroupedPerspective
import com.moments.android.views.components.EchoesIconGradients
import com.moments.android.views.components.EchoesIconMetrics
import com.moments.android.views.components.EchoesIconView
import com.moments.android.views.creator.components.StoryVideoGravity
import com.moments.android.views.creator.components.StoryVideoPlayerView
import com.moments.android.views.feed.maps.LocationMapView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private data class EchoOverlayTone(
    val topUsesDarkForeground: Boolean = false,
    val bottomUsesDarkForeground: Boolean = false,
)

@Composable
private fun rememberEchoOverlayTone(assetUrl: String?): EchoOverlayTone {
    val context = LocalContext.current
    var tone by remember(assetUrl) { mutableStateOf(EchoOverlayTone()) }
    LaunchedEffect(assetUrl) {
        tone = if (assetUrl.isNullOrBlank()) {
            EchoOverlayTone()
        } else {
            withContext(Dispatchers.IO) {
                val request = ImageRequest.Builder(context).data(assetUrl).allowHardware(false).build()
                val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable
                drawable?.toBitmap()?.let(::computeEchoOverlayTone) ?: EchoOverlayTone()
            }
        }
    }
    return tone
}

/** ≡ `computeOverlayTextTone` — muestreo 24×24 top/bottom. */
private fun computeEchoOverlayTone(image: Bitmap): EchoOverlayTone {
    val scaled = Bitmap.createScaledBitmap(image, 24, 24, true)
    fun luminance(fromRow: Int, untilRow: Int): Float {
        var total = 0f
        var samples = 0
        for (y in fromRow until untilRow) for (x in 0 until 24) {
            val pixel = scaled.getPixel(x, y)
            total += (android.graphics.Color.red(pixel) * .299f +
                android.graphics.Color.green(pixel) * .587f +
                android.graphics.Color.blue(pixel) * .114f) / 255f
            samples++
        }
        return if (samples == 0) 0f else total / samples
    }
    return EchoOverlayTone(
        topUsesDarkForeground = luminance(0, 8) > .62f,
        bottomUsesDarkForeground = luminance(16, 24) > .62f,
    )
}

private fun isHorizontalAspect(aspectRatio: String?): Boolean {
    val parts = aspectRatio?.split(":") ?: return false
    if (parts.size != 2) return false
    val w = parts[0].toIntOrNull() ?: return false
    val h = parts[1].toIntOrNull() ?: return false
    return w > h
}

/**
 * Port 1:1 de `EchoViewerUI.swift`.
 * Navegación 2D (perspectivas + vertical), overlays glass, mapa fullscreen.
 */
@Composable
fun EchoViewerUI(
    echoId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    initialEcho: Echo? = null,
) {
    val isDark = isSystemInDarkTheme()
    val viewModel = remember(echoId) { EchoViewModel(echoId, initialEcho) }
    val echo by viewModel.echo.collectAsState()
    val loading by viewModel.isLoading.collectAsState()
    val perspectives by viewModel.groupedPerspectives.collectAsState()
    val perspectiveIndex by viewModel.currentPerspectiveIndex.collectAsState()
    val verticalIndex by viewModel.currentVerticalIndex.collectAsState()
    val availability by viewModel.momentAvailability.collectAsState()
    val isVideoPlaying by viewModel.isVideoPlaying.collectAsState()
    val ripplePhase by viewModel.ripplePhase.collectAsState()
    val scope = rememberCoroutineScope()

    var dragOffset by remember { mutableFloatStateOf(0f) }
    var showIncompleteDecision by remember { mutableStateOf(false) }
    var showLockoutAlert by remember { mutableStateOf(false) }
    var showLeaveMenu by remember { mutableStateOf(false) }
    var showLocation by remember { mutableStateOf(false) }

    val toneAsset = viewModel.currentMoment?.let { moment ->
        moment.thumbnailUrl?.takeIf(String::isNotBlank)
            ?: moment.mediaUrl.takeIf { moment.mediaType == "image" }
    }
    val overlayTone = rememberEchoOverlayTone(toneAsset)
    val topPrimary = if (overlayTone.topUsesDarkForeground) Color.Black else Color.White
    val topSecondary = topPrimary.copy(alpha = if (overlayTone.topUsesDarkForeground) .66f else .72f)
    val bottomPrimary = if (overlayTone.bottomUsesDarkForeground) Color.Black else Color.White
    val bottomSecondary = bottomPrimary.copy(alpha = .66f)
    val locationFallback = stringResource(R.string.echo_viewer_location_fallback)

    LaunchedEffect(echoId) {
        showIncompleteDecision = viewModel.isHistoricalIncomplete
        viewModel.loadEcho()
    }
    LaunchedEffect(viewModel.isHistoricalIncomplete) {
        showIncompleteDecision = viewModel.isHistoricalIncomplete
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
            .background(Color(0xFF0B1215)),
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            echo == null -> EchoWaitingState(emptyList())
            else -> {
                val current = viewModel.currentMoment
                when {
                    current != null -> {
                        val isAvailable = availability[current.momentId] != false
                        EchoPerspectiveMedia(
                            mediaUrl = current.mediaUrl,
                            thumbnailUrl = current.thumbnailUrl,
                            mediaType = current.mediaType,
                            isHorizontal = isHorizontalAspect(current.aspectRatio),
                            unavailable = !isAvailable,
                            isVideoPlaying = isVideoPlaying,
                            modifier = Modifier
                                .fillMaxSize()
                                .offset { IntOffset(0, dragOffset.roundToInt()) }
                                .pointerInput(viewModel.canBrowseMedia, verticalIndex) {
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { change, amount ->
                                            if (viewModel.canBrowseMedia) {
                                                change.consume()
                                                dragOffset += amount
                                            }
                                        },
                                        onDragEnd = {
                                            if (!viewModel.canBrowseMedia) {
                                                dragOffset = 0f
                                                return@detectVerticalDragGestures
                                            }
                                            if (dragOffset < -50f) {
                                                HapticManager.shared.selection()
                                                viewModel.switchVerticalIndex(verticalIndex + 1)
                                            } else if (dragOffset > 50f) {
                                                HapticManager.shared.selection()
                                                viewModel.switchVerticalIndex(verticalIndex - 1)
                                            }
                                            dragOffset = 0f
                                        },
                                        onDragCancel = { dragOffset = 0f },
                                    )
                                },
                        )
                    }
                    viewModel.isHistoricalIncomplete -> {
                        Box(Modifier.fillMaxSize().background(Color(0xFF0B1215)))
                    }
                    else -> EchoWaitingState(echo?.participants.orEmpty())
                }

                // Overlay UI ≡ iOS VStack (safe top + header + location + Spacer + switcher)
                // navigationBarsPadding: sin esto los usernames quedan bajo la gesture/nav bar.
                Column(
                    Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(top = 8.dp),
                ) {
                    EchoHeader(
                        perspectives = perspectives,
                        selectedIndex = perspectiveIndex,
                        currentMomentTimestamp = current?.timestamp,
                        primaryColor = topPrimary,
                        secondaryColor = topSecondary,
                        showLeaveMenu = showLeaveMenu,
                        onShowLeaveMenuChange = { showLeaveMenu = it },
                        onLeave = {
                            FirebaseAuth.getInstance().currentUser?.uid?.let(::leaveEchoAction)
                        },
                        onDismiss = onDismiss,
                    )
                    LocationContextBox(
                        locationName = echo?.locationName?.takeIf { it.isNotBlank() } ?: locationFallback,
                        createdAt = echo?.createdAt,
                        enabled = viewModel.canOpenLocationMap,
                        primary = topPrimary,
                        secondary = topSecondary,
                        onClick = {
                            HapticManager.shared.lightImpact()
                            showLocation = true
                        },
                    )
                    Spacer(Modifier.weight(1f))
                    EchoPerspectiveSwitcher(
                        perspectives = perspectives,
                        selectedIndex = perspectiveIndex,
                        primaryColor = bottomPrimary,
                        secondaryColor = bottomSecondary,
                        onSelect = viewModel::switchPerspective,
                    )
                }

                // Lateral vertical indicator
                if (viewModel.canBrowseMedia) {
                    val verticalCount = perspectives.getOrNull(perspectiveIndex)?.moments?.size ?: 0
                    if (verticalCount > 1) {
                        Column(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .padding(end = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            repeat(verticalCount) { index ->
                                Box(
                                    Modifier
                                        .width(if (index == verticalIndex) 4.dp else 3.dp)
                                        .height(if (index == verticalIndex) 20.dp else 10.dp)
                                        .clip(RoundedCornerShape(50))
                                        .background(
                                            Color.White.copy(if (index == verticalIndex) .92f else .28f),
                                        ),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Ripple
        if (ripplePhase > 0.0) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .scale(ripplePhase.toFloat())
                    .clip(CircleShape)
                    .border(
                        2.dp,
                        Color.White.copy((1.0 - ripplePhase).toFloat().coerceIn(0f, 1f) * 0.3f),
                        CircleShape,
                    ),
            )
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

        // ≡ fullScreenCover LocationMapView
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

// MARK: - Components

@Composable
private fun EchoPerspectiveMedia(
    mediaUrl: String,
    thumbnailUrl: String?,
    mediaType: String,
    isHorizontal: Boolean,
    unavailable: Boolean,
    isVideoPlaying: Boolean,
    modifier: Modifier,
) {
    val preview = thumbnailUrl?.takeIf { it.isNotBlank() } ?: mediaUrl
    // ≡ iOS: perspectiveView.blur(isAvailable ? 0 : 20).overlay { unavailableOverlay }
    // Compose blur no afecta SurfaceView/ExoPlayer → si unavailable, still frame en vez de vídeo.
    Box(modifier.background(Color.Black).clip(RoundedCornerShape(0)), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .fillMaxSize()
                .then(if (unavailable) Modifier.blur(20.dp) else Modifier),
        ) {
            if (isHorizontal) {
                AsyncImage(
                    model = preview,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(20.dp),
                    alpha = 0.6f,
                )
            }
            if (mediaType == "video" && !unavailable) {
                StoryVideoPlayerView(
                    Uri.parse(mediaUrl),
                    if (isHorizontal) StoryVideoGravity.RESIZE_ASPECT else StoryVideoGravity.RESIZE_ASPECT_FILL,
                    isPlaying = isVideoPlaying,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = preview,
                    contentDescription = null,
                    contentScale = if (isHorizontal) ContentScale.Fit else ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        if (unavailable) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(0.4f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(horizontal = 40.dp),
                ) {
                    Icon(Icons.Filled.VisibilityOff, null, tint = Color.White.copy(0.8f), modifier = Modifier.size(40.dp))
                    Text(
                        stringResource(R.string.echo_viewer_unavailable),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun EchoWaitingState(participants: List<EchoParticipant>) {
    Column(
        Modifier.fillMaxSize(),
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
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
        )
        Text(
            stringResource(R.string.echo_viewer_waiting_subtitle),
            color = Color.White.copy(0.6f),
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
private fun EchoHeader(
    perspectives: List<GroupedPerspective>,
    selectedIndex: Int,
    currentMomentTimestamp: java.util.Date?,
    primaryColor: Color,
    secondaryColor: Color,
    showLeaveMenu: Boolean,
    onShowLeaveMenuChange: (Boolean) -> Unit,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (perspectives.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                perspectives.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(2.2.dp)
                            .clip(RoundedCornerShape(50))
                            .background(primaryColor.copy(if (index < selectedIndex) 0.46f else 0.18f)),
                    ) {
                        if (index == selectedIndex) {
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(primaryColor, RoundedCornerShape(50)),
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val p = perspectives.getOrNull(selectedIndex)
            if (p != null) {
                AsyncProfileImageView(
                    p.authorId,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .border(1.dp, primaryColor.copy(0.28f), CircleShape),
                )
                Column(Modifier.padding(start = 0.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        p.username,
                        color = primaryColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    currentMomentTimestamp?.let { ts ->
                        Text(
                            MomentsFormat.relativeTime(ts),
                            color = secondaryColor,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box {
                Box(
                    Modifier
                        .size(36.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable { onShowLeaveMenuChange(true) },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.MoreHoriz, null, tint = primaryColor, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(
                    expanded = showLeaveMenu,
                    onDismissRequest = { onShowLeaveMenuChange(false) },
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(stringResource(R.string.echo_viewer_leave), color = Color.Red)
                        },
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
                Icon(Icons.Filled.Close, null, tint = primaryColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
private fun LocationContextBox(
    locationName: String,
    createdAt: java.util.Date?,
    enabled: Boolean,
    primary: Color,
    secondary: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.55f }
            .scale(if (pressed) 0.97f else 1f)
            .momentsChromeGlass(RoundedCornerShape(50), interactive = enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.LocationOn, null, tint = primary.copy(0.88f), modifier = Modifier.size(13.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                locationName,
                color = primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                MomentsFormat.smartDate(createdAt ?: java.util.Date(), MomentsFormat.DateContext.TIME_ONLY),
                color = secondary,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            null,
            tint = primary.copy(0.34f),
            modifier = Modifier.size(12.dp),
        )
    }
}

@Composable
private fun EchoPerspectiveSwitcher(
    perspectives: List<GroupedPerspective>,
    selectedIndex: Int,
    primaryColor: Color,
    secondaryColor: Color,
    onSelect: (Int) -> Unit,
) {
    // ≡ iOS perspectiveSwitcher: HStack(spacing: 14) + Text.frame(maxWidth: 70)
    LazyRow(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 20.dp, vertical = 6.dp),
    ) {
        itemsIndexed(perspectives, key = { _, p -> p.id }) { index, p ->
            val selected = index == selectedIndex
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .wrapContentWidth()
                    .clickable {
                        if (index != selectedIndex) {
                            HapticManager.shared.selection()
                            onSelect(index)
                        }
                    },
            ) {
                AsyncProfileImageView(
                    p.authorId,
                    modifier = Modifier
                        .size(48.dp)
                        .scale(if (selected) 1.03f else 1f)
                        .then(
                            if (selected) Modifier.shadow(8.dp, CircleShape, spotColor = Color.White.copy(0.18f))
                            else Modifier,
                        )
                        .clip(CircleShape)
                        .border(
                            if (selected) 2.dp else 1.dp,
                            Color.White.copy(if (selected) 0.95f else 0.22f),
                            CircleShape,
                        ),
                )
                Text(
                    p.username,
                    color = if (selected) primaryColor else secondaryColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    // ≡ iOS `.frame(maxWidth: 70)` — no width fijo (separaba de más)
                    modifier = Modifier.widthIn(max = 70.dp),
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                )
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
