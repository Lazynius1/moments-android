package com.moments.android.views.messaging.components

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mapbox.geojson.Point
import com.mapbox.maps.CameraOptions
import com.mapbox.maps.MapSnapshotOptions
import com.mapbox.maps.Size
import com.mapbox.maps.Snapshotter
import com.mapbox.maps.Style
import com.mapbox.maps.ViewAnnotationAnchor
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.ViewAnnotation
import com.mapbox.maps.extension.compose.rememberMapState
import com.mapbox.maps.extension.compose.style.MapStyle
import com.mapbox.maps.plugin.gestures.generated.GesturesSettings
import com.mapbox.maps.viewannotation.annotationAnchor
import com.mapbox.maps.viewannotation.geometry
import com.mapbox.maps.viewannotation.viewAnnotationOptions
import com.moments.android.R
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.feed.maps.FeedMaps
import com.moments.android.views.feed.maps.MapRegionStore
import com.moments.android.views.feed.maps.MomentsMapStyle
import com.moments.android.views.feed.maps.MomentsMapboxStandardStyle
import com.moments.android.views.messaging.models.ChatLocationPayload
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.Date
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.roundToInt

/** Port de `Views/Messaging/Components/ChatLocationMessageBubble.swift`. */

/** ≡ iOS `ChatMapSnapshotCache` (NSCache countLimit 80). */
object ChatMapSnapshotCache {
    private val cache = LruCache<String, Bitmap>(80)

    private fun key(lat: Double, lng: Double, widthPx: Int, heightPx: Int, dark: Boolean): String {
        val rLat = (lat * 1000).roundToInt() / 1000.0
        val rLng = (lng * 1000).roundToInt() / 1000.0
        return "$rLat,$rLng,${widthPx}x${heightPx},${if (dark) "d" else "l"}"
    }

    fun get(lat: Double, lng: Double, widthPx: Int, heightPx: Int, dark: Boolean): Bitmap? =
        cache.get(key(lat, lng, widthPx, heightPx, dark))

    fun put(bitmap: Bitmap, lat: Double, lng: Double, widthPx: Int, heightPx: Int, dark: Boolean) {
        cache.put(key(lat, lng, widthPx, heightPx, dark), bitmap)
    }
}

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
/** ≡ iOS MKMapSnapshotter span 0.01. */
private const val BUBBLE_SNAPSHOT_LON_DELTA = 0.01
/** ≡ iOS detail span 0.008. */
private const val DETAIL_MAP_LON_DELTA = 0.008

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
        Column(Modifier.fillMaxWidth()) {
            Box(Modifier.fillMaxWidth().height(mapHeight)) {
                ChatLocationBubbleMapThumbnail(
                    latitude = payload.lat,
                    longitude = payload.lng,
                    isDark = isDark,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (isLive && !senderId.isNullOrBlank()) {
                        LiveLocationAvatarPin(senderId = senderId, avatarSize = 40.dp, isActive = isLiveActive)
                    } else {
                        // ≡ iOS `mappin.circle.fill` rojo (estática; live sin senderId también pin)
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = Color.Red,
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
}

/** Thumbnail estático ≡ iOS `MKMapSnapshotter` + cache. */
@Composable
private fun ChatLocationBubbleMapThumbnail(
    latitude: Double,
    longitude: Double,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val widthPx = with(density) { bubbleWidth.roundToPx() }
    val heightPx = with(density) { mapHeight.roundToPx() }
    var snapshot by remember(latitude, longitude, isDark, widthPx, heightPx) {
        mutableStateOf(
            ChatMapSnapshotCache.get(latitude, longitude, widthPx, heightPx, isDark),
        )
    }

    LaunchedEffect(latitude, longitude, isDark, widthPx, heightPx) {
        ChatMapSnapshotCache.get(latitude, longitude, widthPx, heightPx, isDark)?.let {
            snapshot = it
            return@LaunchedEffect
        }
        snapshot = null
        if (!FeedMaps.hasMapboxToken()) return@LaunchedEffect
        val bitmap = runCatching {
            captureMapboxSnapshot(
                context = context.applicationContext,
                latitude = latitude,
                longitude = longitude,
                widthPx = widthPx,
                heightPx = heightPx,
                dark = isDark,
            )
        }.getOrNull()
        if (bitmap != null) {
            ChatMapSnapshotCache.put(bitmap, latitude, longitude, widthPx, heightPx, isDark)
            snapshot = bitmap
        }
    }

    Box(
        modifier.background(
            if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f),
        ),
        contentAlignment = Alignment.Center,
    ) {
        val bmp = snapshot
        if (bmp != null) {
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = if (isDark) Color.White.copy(0.5f) else Color.Black.copy(0.35f),
            )
        }
    }
}

/** ≡ MKMapSnapshotter via Mapbox `Snapshotter`. */
private suspend fun captureMapboxSnapshot(
    context: Context,
    latitude: Double,
    longitude: Double,
    widthPx: Int,
    heightPx: Int,
    dark: Boolean,
): Bitmap? = withContext(Dispatchers.Main) {
    val zoom = MapRegionStore.zoomFromLongitudeDelta(BUBBLE_SNAPSHOT_LON_DELTA)
    val options = MapSnapshotOptions.Builder()
        .size(Size(widthPx.toFloat(), heightPx.toFloat()))
        .build()
    val snapshotter = Snapshotter(context, options)
    try {
        snapshotter.setStyleUri(if (dark) Style.DARK else Style.MAPBOX_STREETS)
        snapshotter.setCamera(
            CameraOptions.Builder()
                .center(Point.fromLngLat(longitude, latitude))
                .zoom(zoom)
                .pitch(MomentsMapStyle.CAMERA_PITCH)
                .bearing(0.0)
                .build(),
        )
        suspendCancellableCoroutine { cont ->
            cont.invokeOnCancellation {
                runCatching { snapshotter.cancel() }
                runCatching { snapshotter.destroy() }
            }
            snapshotter.start { bitmap, _ ->
                if (cont.isActive) cont.resume(bitmap)
                runCatching { snapshotter.destroy() }
            }
        }
    } catch (_: Exception) {
        runCatching { snapshotter.destroy() }
        null
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
    val detailZoom = remember { MapRegionStore.zoomFromLongitudeDelta(DETAIL_MAP_LON_DELTA) }
    val point = remember(payload.lat, payload.lng) { Point.fromLngLat(payload.lng, payload.lat) }
    val mapViewportState = rememberMapViewportState {
        setCameraOptions {
            center(point)
            zoom(detailZoom)
            pitch(MomentsMapStyle.CAMERA_PITCH)
            bearing(0.0)
        }
    }
    val mapState = rememberMapState {
        gesturesSettings = GesturesSettings {
            pitchEnabled = false
        }
    }
    val markerTint = if (isLive && isLiveActive) Color(0xFF34C759) else Color.Red
    val cardBg = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val bottomShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

    LaunchedEffect(isLive, isLiveActive) {
        if (!isLive || !isLiveActive) return@LaunchedEffect
        while (true) {
            nowMillis = System.currentTimeMillis()
            delay(1_000)
        }
    }

    LaunchedEffect(payload.lat, payload.lng) {
        mapViewportState.setCameraOptions {
            center(Point.fromLngLat(payload.lng, payload.lat))
            zoom(detailZoom)
            pitch(MomentsMapStyle.CAMERA_PITCH)
            bearing(0.0)
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
            Uri.parse(
                "geo:${payload.lat},${payload.lng}?q=${payload.lat},${payload.lng}" +
                    "(${Uri.encode(payload.name ?: context.getString(R.string.chat_attachment_location))})",
            )
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

    fun recenter() {
        mapViewportState.setCameraOptions {
            center(Point.fromLngLat(payload.lng, payload.lat))
            zoom(detailZoom)
            pitch(MomentsMapStyle.CAMERA_PITCH)
            bearing(0.0)
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        if (FeedMaps.hasMapboxToken()) {
            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                mapState = mapState,
                // ≡ iOS `.mapStyle(hybrid ? .hybrid : .standard)`
                style = {
                    if (mapTypeHybrid) {
                        MapStyle(style = Style.SATELLITE_STREETS)
                    } else {
                        MomentsMapboxStandardStyle(realisticElevation = false)
                    }
                },
            ) {
                ViewAnnotation(
                    options = viewAnnotationOptions {
                        geometry(point)
                        annotationAnchor { anchor(ViewAnnotationAnchor.CENTER) }
                        allowOverlap(true)
                    },
                ) {
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
        } else {
            Box(Modifier.fillMaxSize().background(Color.Gray.copy(0.2f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.LocationOn, null, tint = markerTint, modifier = Modifier.size(48.dp))
            }
        }

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .safeDrawingPadding()
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
                    onClick = ::recenter,
                    size = 42.dp,
                    iconSize = 16.dp,
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(bottomShape)
                .momentsChromeGlass(bottomShape, interactive = false)
                .background(cardBg.copy(alpha = 0.92f))
                // Dialog edge-to-edge (`decorFitsSystemWindows = false`): sin esto la
                // tarjeta queda bajo la barra de navegación / botones del sistema.
                .navigationBarsPadding()
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
