package com.moments.android.views.echoes

import android.net.Uri
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import androidx.core.graphics.drawable.toBitmap
import com.moments.android.R
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.social.EchoService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.viewmodels.EchoViewModel
import com.moments.android.views.creator.components.StoryVideoGravity
import com.moments.android.views.creator.components.StoryVideoPlayerView
import com.moments.android.views.feed.maps.LocationMapView
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
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
                val drawable = (ImageLoader(context).execute(request) as? SuccessResult)?.drawable
                drawable?.toBitmap()?.let(::computeEchoOverlayTone) ?: EchoOverlayTone()
            }
        }
    }
    return tone
}

/** Same 24×24 top/bottom luminance sampling used by `computeOverlayTextTone` in Swift. */
private fun computeEchoOverlayTone(image: Bitmap): EchoOverlayTone {
    val scaled = Bitmap.createScaledBitmap(image, 24, 24, true)
    fun luminance(fromRow: Int, untilRow: Int): Float {
        var total = 0f
        var samples = 0
        for (y in fromRow until untilRow) for (x in 0 until 24) {
            val pixel = scaled.getPixel(x, y)
            total += (android.graphics.Color.red(pixel) * .299f + android.graphics.Color.green(pixel) * .587f + android.graphics.Color.blue(pixel) * .114f) / 255f
            samples++
        }
        return if (samples == 0) 0f else total / samples
    }
    return EchoOverlayTone(
        topUsesDarkForeground = luminance(0, 8) > .62f,
        bottomUsesDarkForeground = luminance(16, 24) > .62f,
    )
}

/** Port of `EchoViewerUI.swift`: perspective/vertical-media viewer for an Echo. */
@Composable
fun EchoViewerUI(
    echoId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(echoId) { EchoViewModel(echoId) }
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
    var showIncompleteChoice by remember { mutableStateOf(false) }
    var showLockout by remember { mutableStateOf(false) }
    var showLeaveConfirm by remember { mutableStateOf(false) }
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

    LaunchedEffect(echoId) { viewModel.loadEcho() }
    LaunchedEffect(viewModel.isHistoricalIncomplete) { showIncompleteChoice = viewModel.isHistoricalIncomplete }
    DisposableEffect(viewModel) { onDispose { viewModel.clear() } }

    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xFF0B1215)),
    ) {
        when {
            loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
            echo == null -> EchoWaitingState(emptyList())
            else -> {
                val locationFallback = stringResource(R.string.echo_viewer_location_fallback)
                val current = viewModel.currentMoment
                if (current == null) {
                    EchoWaitingState(echo?.participants.orEmpty())
                } else {
                    val isAvailable = availability[current.momentId] != false
                    EchoPerspectiveMedia(
                        mediaUrl = current.mediaUrl,
                        thumbnailUrl = current.thumbnailUrl,
                        mediaType = current.mediaType,
                        isHorizontal = current.aspectRatio?.split(":")?.let { it.size == 2 && it[0].toIntOrNull() ?: 0 > it[1].toIntOrNull() ?: 0 } == true,
                        unavailable = !isAvailable,
                        isVideoPlaying = isVideoPlaying,
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, dragOffset.roundToInt()) }
                            .pointerInput(viewModel.canBrowseMedia) {
                                detectVerticalDragGestures(
                                    onVerticalDrag = { change, amount ->
                                        if (viewModel.canBrowseMedia) {
                                            change.consume()
                                            dragOffset += amount
                                        }
                                    },
                                    onDragEnd = {
                                        if (dragOffset < -50f) {
                                            HapticManager.shared.selection()
                                            viewModel.switchVerticalIndex(verticalIndex + 1)
                                        } else if (dragOffset > 50f) {
                                            HapticManager.shared.selection()
                                            viewModel.switchVerticalIndex(verticalIndex - 1)
                                        }
                                        dragOffset = 0f
                                    },
                                )
                            },
                    )
                }

                Column(Modifier.fillMaxSize().padding(top = 42.dp)) {
                    EchoHeader(
                        perspectives = perspectives,
                        selectedIndex = perspectiveIndex,
                        currentMomentTimestamp = current?.timestamp,
                        primaryColor = topPrimary,
                        secondaryColor = topSecondary,
                        onLeave = {
                            showLeaveConfirm = true
                        },
                        onDismiss = onDismiss,
                    )
                    if (echo != null) {
                        Row(
                            Modifier
                                .padding(horizontal = 16.dp, vertical = 10.dp)
                                .momentsChromeGlass(RoundedCornerShape(50), interactive = viewModel.canOpenLocationMap)
                                .clickable(enabled = viewModel.canOpenLocationMap) { showLocation = true }
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Filled.LocationOn, null, tint = topPrimary.copy(.88f), modifier = Modifier.size(17.dp))
                            Column(Modifier.padding(start = 8.dp)) {
                                Text(echo?.locationName?.ifBlank { locationFallback } ?: locationFallback, color = topPrimary, fontWeight = FontWeight.SemiBold)
                                Text(MomentsFormat.smartDate(echo?.createdAt ?: java.util.Date(), MomentsFormat.DateContext.TIME_ONLY), color = topSecondary, fontSize = 11.sp)
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    EchoPerspectiveSwitcher(perspectives, perspectiveIndex, bottomPrimary, bottomSecondary, viewModel::switchPerspective)
                }
                val verticalCount = perspectives.getOrNull(perspectiveIndex)?.moments?.size ?: 0
                if (verticalCount > 1) {
                    Column(
                        Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(verticalCount) { index ->
                            Box(Modifier.width(if (index == verticalIndex) 4.dp else 3.dp).height(if (index == verticalIndex) 20.dp else 10.dp).clip(CircleShape).background(Color.White.copy(if (index == verticalIndex) .92f else .28f)))
                        }
                    }
                }
            }
        }
        if (ripplePhase > 0.0) {
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(240.dp)
                    .scale(ripplePhase.toFloat())
                    .clip(CircleShape)
                    .border(2.dp, Color.White.copy((1.0 - ripplePhase).toFloat().coerceIn(0f, 1f)), CircleShape),
            )
        }
        if (showLeaveConfirm) {
            AlertDialog(
                onDismissRequest = { showLeaveConfirm = false },
                title = { Text(stringResource(R.string.echo_viewer_leave_title)) },
                text = { Text(stringResource(R.string.echo_viewer_leave_body)) },
                dismissButton = { Text(stringResource(R.string.common_cancel), modifier = Modifier.clickable { showLeaveConfirm = false }.padding(16.dp)) },
                confirmButton = { Text(stringResource(R.string.echo_viewer_leave), color = Color.Red, modifier = Modifier.clickable {
                            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                                scope.launch {
                                    runCatching { EchoService.leaveEcho(echoId, uid) }
                                        .onSuccess { onDismiss() }
                                        .onFailure { error ->
                                            if (error.message?.contains("echo.leave.locked") == true) showLockout = true else onDismiss()
                                        }
                                    }
                            }
                        }.padding(16.dp)) },
            )
        }
        if (showIncompleteChoice) {
            AlertDialog(
                onDismissRequest = { showIncompleteChoice = false },
                title = { Text(stringResource(R.string.echo_viewer_incomplete_title)) },
                text = { Text(stringResource(R.string.echo_viewer_incomplete_body)) },
                dismissButton = { Text(stringResource(R.string.echo_viewer_keep), modifier = Modifier.clickable { showIncompleteChoice = false }.padding(16.dp)) },
                confirmButton = { Text(stringResource(R.string.echo_viewer_remove), color = Color.Red, modifier = Modifier.clickable {
                    val uid = FirebaseAuth.getInstance().currentUser?.uid
                    if (uid != null) scope.launch { runCatching { EchoService.leaveEcho(echoId, uid) }.onSuccess { onDismiss() } }
                }.padding(16.dp)) },
            )
        }
        if (showLockout) AlertDialog(onDismissRequest = { showLockout = false }, title = { Text(stringResource(R.string.echo_viewer_lockout_title)) }, text = { Text(stringResource(R.string.echo_viewer_lockout_body)) }, confirmButton = { Text(stringResource(R.string.common_understood), modifier = Modifier.clickable { showLockout = false }.padding(16.dp)) })
        if (showLocation && echo != null) LocationMapView(
            locationName = echo?.locationName.orEmpty(),
            latitude = echo?.location?.latitude,
            longitude = echo?.location?.longitude,
            echoHistoryUserId = FirebaseAuth.getInstance().currentUser?.uid,
            echoHistoryOnly = true,
            onDismiss = { showLocation = false },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun EchoPerspectiveMedia(mediaUrl: String, thumbnailUrl: String?, mediaType: String, isHorizontal: Boolean, unavailable: Boolean, isVideoPlaying: Boolean, modifier: Modifier) {
    Box(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        if (isHorizontal) AsyncImage(model = thumbnailUrl ?: mediaUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().blur(20.dp))
        if (mediaType == "video") StoryVideoPlayerView(Uri.parse(mediaUrl), StoryVideoGravity.RESIZE_ASPECT_FILL, isPlaying = isVideoPlaying, modifier = Modifier.fillMaxSize())
        else AsyncImage(model = thumbnailUrl ?: mediaUrl, contentDescription = null, contentScale = if (isHorizontal) ContentScale.Fit else ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (unavailable) Box(Modifier.fillMaxSize().background(Color.Black.copy(.45f)), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Filled.VisibilityOff, null, tint = Color.White); Text(stringResource(R.string.echo_viewer_unavailable), color = Color.White) } }
    }
}

@Composable
private fun EchoWaitingState(participants: List<com.moments.android.models.EchoParticipant>) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(R.string.echo_viewer_waiting_title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
        Text(stringResource(R.string.echo_viewer_waiting_body), color = Color.White.copy(.6f), modifier = Modifier.padding(24.dp))
        Row { participants.forEach { participant -> AsyncProfileImageView(participant.userId, modifier = Modifier.size(40.dp).clip(CircleShape)) } }
    }
}

@Composable
private fun EchoHeader(
    perspectives: List<com.moments.android.viewmodels.GroupedPerspective>,
    selectedIndex: Int,
    currentMomentTimestamp: java.util.Date?,
    primaryColor: Color,
    secondaryColor: Color,
    onLeave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Column {
        if (perspectives.isNotEmpty()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                perspectives.forEachIndexed { index, _ ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(primaryColor.copy(if (index <= selectedIndex) .9f else .22f)),
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            val p = perspectives.getOrNull(selectedIndex)
            if (p != null) {
                AsyncProfileImageView(p.authorId, modifier = Modifier.size(36.dp).clip(CircleShape))
                Column(Modifier.padding(start = 10.dp)) {
                    Text(p.username, color = primaryColor, fontWeight = FontWeight.SemiBold)
                    currentMomentTimestamp?.let { timestamp ->
                        Text(MomentsFormat.relativeTime(timestamp), color = secondaryColor, fontSize = 11.sp)
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onLeave) { Icon(Icons.Filled.MoreHoriz, null, tint = primaryColor) }
            IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, tint = primaryColor) }
        }
    }
}

@Composable
private fun EchoPerspectiveSwitcher(perspectives: List<com.moments.android.viewmodels.GroupedPerspective>, selectedIndex: Int, primaryColor: Color, secondaryColor: Color, onSelect: (Int) -> Unit) {
    LazyRow(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        itemsIndexed(perspectives, key = { _, p -> p.id }) { index, p ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { if (index != selectedIndex) { HapticManager.shared.selection(); onSelect(index) } }) {
                AsyncProfileImageView(p.authorId, modifier = Modifier.size(48.dp).clip(CircleShape).background(Color.White.copy(.1f)))
                Text(p.username, color = if (index == selectedIndex) primaryColor else secondaryColor, fontSize = 11.sp)
            }
        }
    }
}
