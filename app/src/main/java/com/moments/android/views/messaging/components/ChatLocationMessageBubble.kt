package com.moments.android.views.messaging.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MarkerComposable
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.rememberMarkerState
import com.moments.android.R
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.messaging.models.ChatLocationPayload
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.max

/** Port de `Views/Messaging/Components/ChatLocationMessageBubble.swift`. */
object ChatLocationLiveCountdownFormatter {
    /** Devuelve solo el valor `H:MM:SS` / `M:SS` — el wrapper localizado va en el call site Compose. */
    fun value(expiresAt: Date, now: Date = Date()): String {
        val seconds = max(0L, (expiresAt.time - now.time) / 1_000L)
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (hours > 0) {
            "%d:%02d:%02d".format(hours, minutes, secs)
        } else {
            "%d:%02d".format(minutes, secs)
        }
    }
}

private val bubbleWidth = 276.dp
private val mapHeight = 150.dp
private val bubbleShape = RoundedCornerShape(18.dp)

@Composable
fun ChatLocationMessageBubble(
    payload: ChatLocationPayload,
    isCurrentUser: Boolean,
    isLive: Boolean = false,
    isLiveActive: Boolean = false,
    expiresAt: Date? = null,
    senderId: String? = null,
    onStopLive: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var showDetail by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val isDark = isSystemInDarkTheme()
    val canStopLive = isCurrentUser && isLive && isLiveActive && onStopLive != null

    LaunchedEffect(isLiveActive) {
        if (!isLiveActive) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val title = when {
        isLive && isLiveActive -> stringResource(R.string.chat_location_live_sharing)
        isLive -> stringResource(R.string.chat_location_live_ended)
        !payload.name.isNullOrBlank() -> payload.name
        else -> stringResource(R.string.chat_attachment_location)
    }
    val subtitle = when {
        isLive && isLiveActive && expiresAt != null -> stringResource(
            R.string.chat_location_live_remaining,
            ChatLocationLiveCountdownFormatter.value(expiresAt, Date(nowMillis)),
        )
        !payload.address.isNullOrBlank() -> payload.address
        else -> null
    }
    val iconTint = when {
        isLive && isLiveActive -> Color(0xFF34C759)
        isDark -> Color.White.copy(alpha = 0.7f)
        else -> Color.Black.copy(alpha = 0.6f)
    }

    Column(
        modifier
            .width(bubbleWidth)
            .clip(bubbleShape)
            .border(
                0.5.dp,
                if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.08f),
                bubbleShape,
            ),
    ) {
        Column(Modifier.clickable { showDetail = true }) {
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                ChatLocationMapPreview(
                    latitude = payload.lat,
                    longitude = payload.lng,
                    interactive = false,
                    showDefaultMarker = false,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isLive && !senderId.isNullOrBlank()) {
                        LiveLocationAvatarPin(senderId = senderId, avatarSize = 40.dp, isActive = isLiveActive)
                    } else {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = if (isLive) Color(0xFF34C759) else Color.Red,
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.6f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AttachmentIconView(
                    icon = if (isLive) AttachmentIcon.LIVE_LOCATION else AttachmentIcon.LOCATION,
                    preset = AttachmentIconPreset.LOCATION_BUBBLE_INFO,
                    tintColor = iconTint,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = if (isDark) Color.White else Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = if (isLive) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(
                            it,
                            color = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.5f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (canStopLive) {
            HorizontalDivider(color = if (isDark) Color.White.copy(0.08f) else Color.Black.copy(0.08f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.6f))
                    .clickable { onStopLive?.invoke() }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.StopCircle, null, tint = Color.Red, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(R.string.chat_location_stop_sharing),
                    color = Color.Red,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }

    if (showDetail) {
        Dialog(
            onDismissRequest = { showDetail = false },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            ChatLocationDetailView(
                payload = payload,
                isLive = isLive,
                isLiveActive = isLiveActive,
                expiresAt = expiresAt,
                canStopLive = canStopLive,
                senderId = senderId,
                onClose = { showDetail = false },
                onStopLive = onStopLive,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun ChatLocationMapPreview(
    latitude: Double,
    longitude: Double,
    interactive: Boolean,
    modifier: Modifier = Modifier,
    mapType: MapType = MapType.NORMAL,
    showDefaultMarker: Boolean = true,
    markerContent: (@Composable () -> Unit)? = null,
) {
    val latLng = LatLng(latitude, longitude)
    val position = rememberCameraPositionState {
        this.position = CameraPosition.fromLatLngZoom(latLng, if (interactive) 15.5f else 15f)
    }
    LaunchedEffect(latitude, longitude) {
        position.move(CameraUpdateFactory.newLatLngZoom(latLng, if (interactive) 15.5f else 15f))
    }
    GoogleMap(
        cameraPositionState = position,
        modifier = modifier,
        properties = MapProperties(mapType = mapType),
        uiSettings = MapUiSettings(
            zoomControlsEnabled = false,
            scrollGesturesEnabled = interactive,
            zoomGesturesEnabled = interactive,
            tiltGesturesEnabled = false,
            rotationGesturesEnabled = false,
            mapToolbarEnabled = false,
            myLocationButtonEnabled = false,
        ),
    ) {
        when {
            markerContent != null -> {
                MarkerComposable(state = rememberMarkerState(position = latLng)) {
                    markerContent()
                }
            }
            showDefaultMarker -> {
                MarkerComposable(state = rememberMarkerState(position = latLng)) {
                    Icon(Icons.Default.LocationOn, null, tint = Color.Red, modifier = Modifier.size(34.dp))
                }
            }
        }
    }
}

@Composable
fun ChatLocationDetailView(
    payload: ChatLocationPayload,
    isLive: Boolean = false,
    isLiveActive: Boolean = false,
    expiresAt: Date? = null,
    canStopLive: Boolean = false,
    senderId: String? = null,
    accentColor: Color = Color(0xFF007AFF),
    accentColorRed: Color = Color.Red,
    onClose: () -> Unit,
    onStopLive: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()
    val scope = rememberCoroutineScope()
    var mapTypeHybrid by remember { mutableStateOf(false) }
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val latLng = LatLng(payload.lat, payload.lng)
    val camera = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(latLng, 15.5f)
    }
    val markerTint = if (isLive && isLiveActive) Color(0xFF34C759) else Color.Red
    val cardBg = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)

    LaunchedEffect(isLive, isLiveActive) {
        if (!isLive || !isLiveActive) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val title = when {
        isLive && isLiveActive -> stringResource(R.string.chat_location_live_sharing)
        isLive -> stringResource(R.string.chat_location_live_ended)
        !payload.name.isNullOrBlank() -> payload.name
        else -> stringResource(R.string.chat_attachment_location)
    }
    val subtitle = when {
        isLive && isLiveActive && expiresAt != null -> stringResource(
            R.string.chat_location_live_remaining,
            ChatLocationLiveCountdownFormatter.value(expiresAt, Date(nowMillis)),
        )
        !payload.address.isNullOrBlank() -> payload.address
        else -> null
    }

    fun openMaps(directions: Boolean) {
        val uri = if (directions) {
            Uri.parse("google.navigation:q=${payload.lat},${payload.lng}")
        } else {
            Uri.parse("geo:${payload.lat},${payload.lng}?q=${payload.lat},${payload.lng}(${Uri.encode(payload.name ?: context.getString(R.string.chat_attachment_location))})")
        }
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    fun stopLiveSharing() {
        onClose()
        scope.launch {
            delay(350)
            onStopLive?.invoke()
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        GoogleMap(
            cameraPositionState = camera,
            modifier = Modifier.fillMaxSize(),
            properties = MapProperties(mapType = if (mapTypeHybrid) MapType.HYBRID else MapType.NORMAL),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
        ) {
            MarkerComposable(state = rememberMarkerState(position = latLng)) {
                if (isLive && !senderId.isNullOrBlank()) {
                    LiveLocationAvatarPin(senderId = senderId, avatarSize = 48.dp, isActive = isLiveActive)
                } else {
                    Icon(
                        Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = markerTint,
                        modifier = Modifier
                            .size(34.dp)
                            .shadow(3.dp, CircleShape),
                    )
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            ProfileChromeIconButton(Icons.Default.Close, onClick = onClose, size = 42.dp, iconSize = 16.dp)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileChromeIconButton(
                    if (mapTypeHybrid) Icons.Default.Map else Icons.Default.Public,
                    onClick = { mapTypeHybrid = !mapTypeHybrid },
                    size = 42.dp,
                    iconSize = 16.dp,
                )
                ProfileChromeIconButton(
                    Icons.Default.MyLocation,
                    onClick = {
                        camera.move(CameraUpdateFactory.newLatLngZoom(latLng, 15.5f))
                    },
                    size = 42.dp,
                    iconSize = 16.dp,
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
                .momentsChromeGlass(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp), interactive = false)
                .background(cardBg.copy(alpha = 0.92f))
                .padding(horizontal = 18.dp)
                .padding(top = 18.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                AttachmentIconView(
                    icon = if (isLive) AttachmentIcon.LIVE_LOCATION else AttachmentIcon.LOCATION,
                    preset = AttachmentIconPreset.LOCATION_DETAIL_CARD,
                    tintColor = markerTint,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        title,
                        color = if (isDark) Color.White else Color.Black,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = if (isLive) 2 else 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.let {
                        Text(
                            it,
                            color = if (isDark) Color.White.copy(0.65f) else Color.Black.copy(0.55f),
                            fontSize = 13.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                LocationActionButton(
                    label = stringResource(R.string.chat_location_directions),
                    icon = Icons.Default.TurnRight,
                    tint = accentColor,
                    isDark = isDark,
                    onClick = { openMaps(directions = true) },
                    modifier = Modifier.weight(1f),
                )
                LocationActionButton(
                    label = stringResource(R.string.chat_location_open_maps),
                    icon = Icons.Default.Map,
                    tint = null,
                    isDark = isDark,
                    onClick = { openMaps(directions = false) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (canStopLive) {
                LocationActionButton(
                    label = stringResource(R.string.chat_location_stop_sharing),
                    icon = Icons.Default.StopCircle,
                    tint = accentColorRed,
                    isDark = isDark,
                    onClick = ::stopLiveSharing,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun LocationActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color?,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    val stroke = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
    val content = if (tint == null) {
        if (isDark) Color.White else Color.Black
    } else {
        Color.White
    }
    Row(
        modifier
            .clip(shape)
            .momentsChromeGlass(shape, interactive = true, tint = tint?.copy(alpha = 0.92f))
            .border(1.dp, stroke, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = content, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = content, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

/** Port de `LiveLocationAvatarPin`. */
@Composable
fun LiveLocationAvatarPin(
    senderId: String,
    avatarSize: Dp = 44.dp,
    isActive: Boolean = true,
    modifier: Modifier = Modifier,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(avatarSize + 8.dp)
                    .shadow(3.dp, CircleShape, ambientColor = Color.Black.copy(0.25f), spotColor = Color.Black.copy(0.25f))
                    .clip(CircleShape)
                    .background(Color.White),
            )
            StoryRingAvatarView(userId = senderId, size = avatarSize)
            if (!isActive) {
                Box(
                    Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.35f)),
                )
            }
        }
        Canvas(Modifier.size(width = 16.dp, height = 10.dp).offset(y = (-2).dp)) {
            val path = Path().apply {
                moveTo(size.width / 2f, size.height)
                lineTo(0f, 0f)
                lineTo(size.width, 0f)
                close()
            }
            drawPath(path, Color.White)
        }
    }
}
