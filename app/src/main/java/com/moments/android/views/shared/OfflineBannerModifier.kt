package com.moments.android.views.shared

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Overlay del banner offline.
 *
 * Android (esta sesión): más arriba, compacto a la **izquierda**, expand hacia la derecha.
 * iOS se actualizará aparte (liquid glass / animación distinta en iOS 26).
 */
@Composable
fun OfflineBannerOverlay(
    modifier: Modifier = Modifier,
    /** A la altura del FeedTypeSelector (bajo el aro / header ~88). */
    topInsetBelowSafeArea: Dp = 118.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = topInsetBelowSafeArea)
            .zIndex(9_999f),
        contentAlignment = Alignment.TopStart,
    ) {
        CollapsibleOfflineBanner()
    }
}

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
        HapticManager.shared.lightImpact()
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

    val colors = rememberAdaptiveColors()
    val primary = colors.primary
    val secondary = colors.secondary
    val capsule = RoundedCornerShape(percent = 50)
    // Glow más suave → menos “alarma” en el chrome.
    val glow = Color.Red.copy(alpha = 0.12f)
    val titleSp = with(density) { legacyPoppinsSize(context, 14).toSp() }
    val bodySp = with(density) { legacyPoppinsSize(context, 11).toSp() }
    val expandHint = stringResource(R.string.offline_banner_expand_hint)
    val springSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow,
    )

    Box(
        modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 16.dp),
        contentAlignment = Alignment.TopStart,
    ) {
        // Un solo contenedor anclado a la izquierda: compacto ↔ expandido hacia la derecha.
        AnimatedContent(
            targetState = isExpanded,
            transitionSpec = {
                if (targetState) {
                    (
                        fadeIn(springSpec) +
                            expandHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                expandFrom = Alignment.Start,
                            )
                        ) togetherWith (
                        fadeOut(springSpec) +
                            shrinkHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                shrinkTowards = Alignment.Start,
                            )
                        )
                } else {
                    (
                        fadeIn(springSpec) +
                            expandHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                expandFrom = Alignment.Start,
                            )
                        ) togetherWith (
                        fadeOut(springSpec) +
                            shrinkHorizontally(
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                    stiffness = Spring.StiffnessMediumLow,
                                ),
                                shrinkTowards = Alignment.Start,
                            )
                        )
                }.using(SizeTransform(clip = false))
            },
            label = "offlineBannerMorph",
        ) { expanded ->
            if (expanded) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .shadow(10.dp, capsule, clip = false, ambientColor = glow, spotColor = glow)
                        .momentsChromeGlass(capsule, interactive = false)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.SignalWifiOff,
                        contentDescription = null,
                        tint = primary,
                        modifier = Modifier.size(16.dp),
                    )
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(1.dp),
                    ) {
                        Text(
                            stringResource(R.string.network_offline_title),
                            color = primary,
                            fontSize = titleSp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            stringResource(R.string.offline_banner_message),
                            color = secondary,
                            fontSize = bodySp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        stringResource(R.string.network_offline_retry),
                        color = primary,
                        fontSize = bodySp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .wrapContentWidth(unbounded = false)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = ::retrySync,
                            )
                            .padding(horizontal = 4.dp, vertical = 4.dp)
                            .semantics {
                                contentDescription = context.getString(R.string.network_offline_retry)
                            },
                    )
                }
            } else {
                Box(
                    Modifier
                        .shadow(8.dp, CircleShape, clip = false, ambientColor = glow, spotColor = glow)
                        .size(36.dp)
                        .momentsChromeGlass(CircleShape, interactive = true)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = ::expandFromCompact,
                        )
                        .semantics {
                            contentDescription =
                                context.getString(R.string.network_offline_title) + ". " + expandHint
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.SignalWifiOff,
                        contentDescription = null,
                        tint = primary.copy(alpha = 0.88f),
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }
    }
}
