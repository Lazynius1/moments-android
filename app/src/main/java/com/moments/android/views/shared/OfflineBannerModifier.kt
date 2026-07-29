package com.moments.android.views.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
 * Overlay colapsable (expandido → orb) debajo del header (~92 dp bajo safe area).
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

    Box(modifier.fillMaxWidth().padding(horizontal = 16.dp), contentAlignment = Alignment.TopCenter) {
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                (fadeIn() + scaleIn(initialScale = 0.92f)) togetherWith fadeOut()
            },
            label = "collapsibleOfflineBanner",
        ) { expanded ->
            if (expanded) {
                Row(
                    Modifier
                        .shadow(18.dp, CircleShape, clip = false, ambientColor = Color.Red.copy(0.22f), spotColor = Color.Red.copy(0.22f))
                        .momentsChromeGlass(CircleShape, interactive = false)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Filled.SignalWifiOff,
                        contentDescription = null,
                        tint = LocalContentColor.current,
                        modifier = Modifier.size(18.dp),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            stringResource(R.string.network_offline_title),
                            color = LocalContentColor.current,
                            fontSize = legacyPoppinsSize(context, 15).sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.offline_banner_message),
                            color = LocalContentColor.current.copy(alpha = 0.72f),
                            fontSize = legacyPoppinsSize(context, 11).sp,
                            maxLines = 2,
                        )
                    }
                    Text(
                        stringResource(R.string.network_offline_retry),
                        color = LocalContentColor.current,
                        fontSize = legacyPoppinsSize(context, 11).sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = ::retrySync)
                            .padding(horizontal = 6.dp, vertical = 8.dp)
                            .semantics {
                                contentDescription = context.getString(R.string.network_offline_retry)
                            },
                    )
                }
            } else {
                val expandHint = stringResource(R.string.offline_banner_expand_hint)
                Box(
                    Modifier
                        .shadow(14.dp, CircleShape, clip = false, ambientColor = Color.Red.copy(0.22f), spotColor = Color.Red.copy(0.22f))
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
}
