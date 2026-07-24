package com.moments.android.views.messaging.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberCameraPositionState
import com.moments.android.R
import com.moments.android.views.messaging.models.ChatLocationPayload
import kotlinx.coroutines.delay
import java.util.Date

/** Port de `Views/Messaging/Components/ChatLocationMessageBubble.swift`. */
object ChatLocationLiveCountdownFormatter {
    fun text(expiresAt: Date, now: Date = Date()): String {
        val seconds = ((expiresAt.time - now.time) / 1_000L).coerceAtLeast(0L)
        val value = if (seconds >= 3600) "%d:%02d:%02d".format(seconds / 3600, seconds / 60 % 60, seconds % 60) else "%d:%02d".format(seconds / 60, seconds % 60)
        return value
    }
}

@Composable
fun ChatLocationMessageBubble(
    payload: ChatLocationPayload,
    isCurrentUser: Boolean,
    isLive: Boolean = false,
    isLiveActive: Boolean = false,
    expiresAt: Date? = null,
    onStopLive: (() -> Unit)? = null,
    onOpenDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var nowMillis by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(isLiveActive) { while (isLiveActive) { nowMillis = System.currentTimeMillis(); delay(1_000) } }
    val title = when { isLive && isLiveActive -> stringResource(R.string.chat_location_live_sharing); isLive -> stringResource(R.string.chat_location_live_ended); !payload.name.isNullOrBlank() -> payload.name; else -> stringResource(R.string.chat_attachment_location) }
    val subtitle = if (isLive && isLiveActive && expiresAt != null) ChatLocationLiveCountdownFormatter.text(expiresAt, Date(nowMillis)) else payload.address
    Column(modifier.width(276.dp).clip(RoundedCornerShape(18.dp)).background(Color.White.copy(if (androidx.compose.foundation.isSystemInDarkTheme()) .05f else .6f))) {
        Box(Modifier.fillMaxWidth().height(150.dp).clickable { onOpenDetail?.invoke() }) { ChatLocationMapPreview(payload.lat, payload.lng, Modifier.fillMaxSize()); Icon(Icons.Default.LocationOn, null, tint = if (isLive) Color(0xFF34C759) else Color.Red, modifier = Modifier.align(Alignment.Center).size(30.dp)) }
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { AttachmentIconView(if (isLive) AttachmentIcon.LIVE_LOCATION else AttachmentIcon.LOCATION, AttachmentIconPreset.LOCATION_BUBBLE_INFO, if (isLive && isLiveActive) Color(0xFF34C759) else Color.Gray); Column(Modifier.weight(1f)) { Text(title, fontSize = 14.sp); subtitle?.let { Text(it, fontSize = 12.sp, color = Color.Gray) } } }
        if (isCurrentUser && isLive && isLiveActive && onStopLive != null) Text(stringResource(R.string.chat_location_stop_sharing), color = Color.Red, modifier = Modifier.fillMaxWidth().clickable(onClick = onStopLive).padding(10.dp), fontSize = 13.sp)
    }
}

@Composable
private fun ChatLocationMapPreview(latitude: Double, longitude: Double, modifier: Modifier = Modifier) {
    val position = rememberCameraPositionState(); LaunchedEffect(latitude, longitude) { position.move(CameraUpdateFactory.newLatLngZoom(LatLng(latitude, longitude), 15f)) }
    GoogleMap(cameraPositionState = position, modifier = modifier) { Marker(state = com.google.maps.android.compose.rememberMarkerState(position = LatLng(latitude, longitude))) }
}

@Composable
fun ChatLocationDetailView(payload: ChatLocationPayload, isLive: Boolean = false, isLiveActive: Boolean = false, onClose: () -> Unit, onStopLive: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(modifier.fillMaxSize()) {
        ChatLocationMapPreview(payload.lat, payload.lng, Modifier.fillMaxSize())
        Icon(Icons.Default.Close, null, modifier = Modifier.align(Alignment.TopStart).padding(16.dp).size(42.dp).clip(CircleShape).background(Color.White.copy(.8f)).clickable(onClick = onClose))
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)).background(if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)).padding(18.dp)) {
            Text(payload.name ?: stringResource(R.string.chat_attachment_location), fontSize = 16.sp); payload.address?.let { Text(it, color = Color.Gray, fontSize = 13.sp) }
            Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) { LocationAction(R.string.chat_location_directions, Color(0xFF007AFF)) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=${payload.lat},${payload.lng}"))) }; LocationAction(R.string.chat_location_open_maps, Color.Gray) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:${payload.lat},${payload.lng}"))) } }
            if (isLive && isLiveActive && onStopLive != null) LocationAction(R.string.chat_location_stop_sharing, Color.Red, onStopLive)
        }
    }
}

@Composable private fun LocationAction(res: Int, tint: Color, action: () -> Unit) = Text(stringResource(res), color = Color.White, modifier = Modifier.clip(RoundedCornerShape(14.dp)).background(tint).clickable(onClick = action).padding(horizontal = 12.dp, vertical = 12.dp), fontSize = 14.sp)
