package com.moments.android.views.shared

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SignalWifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.network.NetworkMonitor
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `OfflineBannerModifier.swift` / `CollapsibleOfflineBanner`.
 *
 * Tipografía: [legacyPoppinsSize] devuelve **px** → usar `.toSp()` con [LocalDensity]
 * (nunca `.sp` directo; eso infla el texto ~density× y pisa “Modo”/“offline”).
 */
@Composable
fun OfflineBannerOverlay(
    modifier: Modifier = Modifier,
    topInsetBelowSafeArea: Dp = 92.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = topInsetBelowSafeArea)
            .zIndex(9_999f),
        contentAlignment = Alignment.TopCenter,
    ) {
        CollapsibleOfflineBanner()
    }
}

/** ≡ `CollapsibleOfflineBanner` en OfflineBannerModifier.swift. */
@Composable
fun CollapsibleOfflineBanner(modifier: Modifier = Modifier) {
    val connected by NetworkMonitor.isConnectedFlow.collectAsState()
    var isExpanded by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    var collapseJob by remember { mutableStateOf<Job?>(null) }
    val context = LocalContext.current
    val density = LocalDensity.current

    fun cancelCollapse() {
        collapseJob?.cancel()
        collapseJob = null
    }

    fun scheduleCollapse() {
        cancelCollapse()
        collapseJob = scope.launch {
            delay(4_000)
            isExpanded = false
        }
    }

    fun handleBecameOffline() {
        isExpanded = true
        scheduleCollapse()
    }

    fun expandFromCompact() {
        cancelCollapse()
        isExpanded = true
        scheduleCollapse()
    }

    fun retrySync() {
        HapticManager.shared.lightImpact()
        cancelCollapse()
        isExpanded = true
        scheduleCollapse()
        NavigationEventBus.emit(CoordinatorNavigationEvent.ForceFeedRefresh)
    }

    DisposableEffect(Unit) {
        if (!NetworkMonitor.isConnected) handleBecameOffline()
        onDispose { cancelCollapse() }
    }

    LaunchedEffect(connected) {
        if (connected) {
            cancelCollapse()
            isExpanded = true
        } else {
            handleBecameOffline()
        }
    }

    if (connected) return

    val capsule = RoundedCornerShape(percent = 50)
    val glow = Color.Red.copy(alpha = 0.22f)
    val titleSp = with(density) { legacyPoppinsSize(context, 15).toSp() }
    val bodySp = with(density) { legacyPoppinsSize(context, 11).toSp() }

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        AnimatedVisibility(
            visible = isExpanded,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut() + scaleOut(targetScale = 0.92f),
        ) {
            // ≡ iOS: icon | texto (flex) | Reintentar (ancho intrínseco, nunca truncado).
            // Padding vertical 8 (antes 10) → cápsula menos “gorda”.
            Row(
                Modifier
                    .fillMaxWidth()
                    .shadow(18.dp, capsule, clip = false, ambientColor = glow, spotColor = glow)
                    .momentsChromeGlass(capsule, interactive = false)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Filled.SignalWifiOff,
                    contentDescription = null,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(18.dp),
                )
                Column(
                    Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        stringResource(R.string.network_offline_title),
                        color = LocalContentColor.current,
                        fontSize = titleSp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.offline_banner_message),
                        color = LocalContentColor.current.copy(alpha = 0.72f),
                        fontSize = bodySp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = bodySp * 1.25f,
                    )
                }
                Text(
                    stringResource(R.string.network_offline_retry),
                    color = LocalContentColor.current,
                    fontSize = bodySp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .wrapContentWidth(unbounded = false)
                        .clickable(onClick = ::retrySync)
                        .padding(horizontal = 4.dp, vertical = 4.dp)
                        .semantics {
                            contentDescription = context.getString(R.string.network_offline_retry)
                        },
                )
            }
        }

        AnimatedVisibility(
            visible = !isExpanded,
            enter = fadeIn() + scaleIn(initialScale = 0.92f),
            exit = fadeOut(),
        ) {
            val expandHint = stringResource(R.string.offline_banner_expand_hint)
            Box(
                Modifier
                    .shadow(14.dp, CircleShape, clip = false, ambientColor = glow, spotColor = glow)
                    .size(44.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(onClick = ::expandFromCompact)
                    .semantics {
                        contentDescription =
                            context.getString(R.string.network_offline_title) + ". " + expandHint
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.SignalWifiOff,
                    contentDescription = null,
                    tint = LocalContentColor.current,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
