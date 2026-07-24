package com.moments.android.views.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.SlowMotionVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.services.network.NetworkMonitor
import kotlinx.coroutines.delay

/** Port de `OfflineBanner.swift`, observado directamente desde NetworkMonitor. */
@Composable
fun OfflineBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val connected by NetworkMonitor.isConnectedFlow.collectAsState()
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(connected) { if (!connected) { visible = true; delay(4_000); visible = false } }
    if (connected) return
    AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        NetworkPill("No connection", Icons.Filled.SignalWifiOff, Color(0xFFFF3B30), onClick = onRetry, modifier = modifier)
    }
    if (!visible) Icon(Icons.Filled.SignalWifiOff, null, tint = Color.White.copy(.6f), modifier = modifier.size(30.dp).background(Color.Black.copy(.4f), CircleShape).padding(8.dp).clickable { visible = true })
}

/** Port de `SlowConnectionBanner.swift`. */
@Composable
fun SlowConnectionBanner(modifier: Modifier = Modifier) {
    val connected by NetworkMonitor.isConnectedFlow.collectAsState()
    val type by NetworkMonitor.connectionTypeFlow.collectAsState()
    val expensive by NetworkMonitor.isExpensiveFlow.collectAsState()
    val slow = connected && type == NetworkMonitor.ConnectionType.CELLULAR && expensive
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(slow) { if (slow) { visible = true; delay(5_000); visible = false } }
    if (slow) AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut()) {
        NetworkPill("Slow connection", Icons.Filled.SlowMotionVideo, Color(0xFFFF9500), onDismiss = { visible = false }, modifier = modifier)
    }
}

@Composable
private fun NetworkPill(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, accent: Color, onClick: (() -> Unit)? = null, onDismiss: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().background(Color(0xDD1C2025), RoundedCornerShape(28.dp)).clickable(enabled = onClick != null) { onClick?.invoke() }.padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.size(44.dp).background(Color.White.copy(.15f), CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = accent) }
        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.weight(1f))
        onDismiss?.let { Icon(Icons.Filled.Close, null, tint = Color.White.copy(.7f), modifier = Modifier.size(30.dp).clickable(onClick = it).padding(7.dp)) }
    }
}
