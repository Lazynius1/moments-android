package com.moments.android.views.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.utilities.legacyPoppinsSize
import kotlinx.coroutines.delay

/** Port de `OfflineBanner` (OfflineBanner.swift). */
@Composable
fun OfflineBanner(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val connected by NetworkMonitor.isConnectedFlow.collectAsState()
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(connected) {
        if (!connected) visible = true
    }
    LaunchedEffect(connected, visible) {
        if (!connected && visible) {
            delay(4_000)
            visible = false
        }
    }
    if (connected) return
    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(
            visible = visible,
            enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        ) {
            NetworkStatusPill(
                title = stringResource(R.string.network_offline_title),
                icon = Icons.Filled.SignalWifiOff,
                iconTint = LocalContentColor.current,
                glowColor = Color.Red.copy(alpha = 0.2f),
                shadowColor = Color.Red.copy(alpha = 0.35f),
                onClick = onRetry,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
        AnimatedVisibility(
            visible = !visible,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            Icon(
                Icons.Filled.SignalWifiOff,
                contentDescription = null,
                tint = LocalContentColor.current.copy(alpha = 0.6f),
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .clickable { visible = true }
                    .padding(8.dp),
            )
        }
    }
}

/** Port de `SlowConnectionBanner` (OfflineBanner.swift). */
@Composable
fun SlowConnectionBanner(modifier: Modifier = Modifier) {
    val connected by NetworkMonitor.isConnectedFlow.collectAsState()
    val isSlow = connected && NetworkMonitor.isSlowConnection
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(isSlow) {
        if (isSlow) visible = true
    }
    LaunchedEffect(isSlow, visible) {
        if (isSlow && visible) {
            delay(5_000)
            visible = false
        }
    }
    if (!isSlow) return
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
        modifier = modifier,
    ) {
        NetworkStatusPill(
            title = stringResource(R.string.network_slow_title),
            icon = Icons.Filled.Speed,
            iconTint = Color(0xFFFFCC00),
            glowColor = Color(0xFFFF9500).copy(alpha = 0.2f),
            shadowColor = Color(0xFFFF9500).copy(alpha = 0.35f),
            trailing = {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(30.dp)
                        .clickable { visible = false }
                        .padding(8.dp),
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun NetworkStatusPill(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    glowColor: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val fontSize = legacyPoppinsSize(context, if (trailing == null) 17 else 15).sp
    Row(
        modifier
            .shadow(40.dp, CircleShape, clip = false, ambientColor = shadowColor, spotColor = shadowColor)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    colors = listOf(glowColor, Color.Transparent),
                    radius = 450f,
                ),
            )
            .momentsChromeGlass(CircleShape, interactive = false)
            .border(
                width = 1.5.dp,
                brush = Brush.linearGradient(
                    colors = listOf(Color.White.copy(alpha = 0.3f), Color.White.copy(alpha = 0.05f)),
                ),
                shape = CircleShape,
            )
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 8.dp, end = if (trailing != null) 12.dp else 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Text(
            text = title,
            color = LocalContentColor.current,
            fontSize = fontSize,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(end = if (trailing == null) 12.dp else 0.dp),
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}
