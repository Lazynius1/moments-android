package com.moments.android.views.feed.maps

import androidx.compose.animation.core.Animatable
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.moments.android.views.shared.modalSheetSpring
import com.moments.android.views.shared.modalSheetRubberBand
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlin.math.abs

enum class MapPanelState { Small, Medium, Large }

/** One persistent Compose surface, with three resting heights and no modal scrim. */
@Composable
fun MapImmersivePanel(
    cluster: MapPlaceCluster,
    state: MapPanelState,
    onStateChange: (MapPanelState) -> Unit,
    isLoading: Boolean,
    onStories: () -> Unit,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    onHeightChange: (Dp) -> Unit = {},
    distance: String? = null,
    content: @Composable (Dp) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val density = LocalDensity.current
    BoxWithConstraints(modifier.fillMaxWidth().navigationBarsPadding()) {
        val large = maxHeight.coerceAtLeast(80.dp)
        val medium = minOf(350.dp, large)
        val target = when (state) { MapPanelState.Small -> 80.dp; MapPanelState.Medium -> medium; MapPanelState.Large -> large }
        val scope = rememberCoroutineScope()
        val smallPx = with(density) { 80.dp.toPx() }
        val largePx = with(density) { large.toPx() }
        val mediumPx = with(density) { medium.toPx() }
        val animatedHeight = remember { Animatable(smallPx) }
        var dragHeight by remember { mutableStateOf<Float?>(null) }
        var isDragging by remember { mutableStateOf(false) }
        val renderedPx = dragHeight ?: animatedHeight.value
        val height = with(density) { renderedPx.coerceAtLeast(48.dp.toPx()).toDp() }

        LaunchedEffect(state, isDragging, target) {
            if (!isDragging) animatedHeight.animateTo(with(density) { target.toPx() }, modalSheetSpring())
        }
        LaunchedEffect(height) { onHeightChange(height) }

        fun updateDrag(delta: Float) {
            if (!isDragging) {
                isDragging = true
                scope.launch { animatedHeight.stop() }
            }
            dragHeight = modalSheetRubberBand((dragHeight ?: animatedHeight.value) - delta, smallPx, largePx, largePx)
        }

        suspend fun settle(velocity: Float = 0f) {
            val current = dragHeight ?: animatedHeight.value
            val stops = listOf(MapPanelState.Small to smallPx, MapPanelState.Medium to mediumPx, MapPanelState.Large to largePx)
            val destination = if (abs(velocity) > 700f) {
                if (velocity < 0) stops.firstOrNull { it.second > current + 16f } ?: stops.last()
                else stops.lastOrNull { it.second < current - 16f } ?: stops.first()
            } else stops.minBy { abs(it.second - current) }
            // Start the spring from the finger's final position, never the previous detent.
            animatedHeight.snapTo(current)
            dragHeight = null
            onStateChange(destination.first)
            isDragging = false
        }

        val nestedConnection = remember(largePx, mediumPx) {
            object : NestedScrollConnection {
                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y >= 0 || (dragHeight ?: animatedHeight.value) >= largePx) return Offset.Zero
                    updateDrag(available.y)
                    return Offset(0f, available.y)
                }
                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0) return Offset.Zero
                    updateDrag(available.y)
                    return Offset(0f, available.y)
                }
                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!isDragging) return Velocity.Zero
                    settle(available.y)
                    return available
                }
                override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                    if (isDragging) settle(available.y)
                    return Velocity.Zero
                }
            }
        }
        val drag = rememberDraggableState { delta -> updateDrag(delta) }
        Surface(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(height).nestedScroll(nestedConnection),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = colors.surfaceBackground,
            tonalElevation = 3.dp,
            shadowElevation = 8.dp,
        ) {
            Column {
                Column(
                    Modifier.fillMaxWidth().draggable(
                        state = drag, orientation = Orientation.Vertical,
                        onDragStarted = {
                            isDragging = true
                            dragHeight = animatedHeight.value
                            animatedHeight.stop()
                        },
                        onDragStopped = { velocity -> settle(velocity) },
                    ),
                ) {
                    Box(Modifier.align(Alignment.CenterHorizontally).padding(top = 7.dp).size(32.dp, 4.dp).background(colors.secondary.copy(alpha = 0.5f), RoundedCornerShape(50)))
                    if (state != MapPanelState.Small && onBack != null) {
                        TextButton(onClick = onBack, modifier = Modifier.padding(start = 16.dp), shape = RoundedCornerShape(50), colors = ButtonDefaults.textButtonColors(contentColor = colors.primary, containerColor = colors.primary.copy(alpha = 0.08f))) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, null, Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.maps_zone_sheet_places))
                        }
                    }
                    Row(Modifier.fillMaxWidth().height(66.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                            val story = cluster.primaryStory
                            val moment = cluster.primaryMoment
                            when {
                                story != null -> Box(Modifier.clickable(onClick = onStories)) { MapStoryPin(story) }
                                moment != null -> MapMomentPin(moment, 1)
                                else -> androidx.compose.material3.Icon(androidx.compose.ui.res.painterResource(R.drawable.attachment_map_icon), null, tint = colors.primary, modifier = Modifier.size(24.dp))
                            }
                        }
                        Column(Modifier.weight(1f).clickable { onStateChange(when (state) { MapPanelState.Small -> MapPanelState.Medium; MapPanelState.Medium -> MapPanelState.Large; MapPanelState.Large -> MapPanelState.Small }) }) {
                            Text(cluster.displayName, color = colors.primary, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(distance, stringResource(R.string.maps_place_sheet_stats, cluster.momentCount, cluster.storyCount)).joinToString(" · "), color = colors.secondary, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                        }
                        if (isLoading) CircularProgressIndicator(Modifier.size(18.dp), color = colors.primary, strokeWidth = 2.dp)
                    }
                }
                if (height > 100.dp) content((height - if (onBack != null) 150.dp else 100.dp).coerceAtLeast(40.dp))
            }
        }
    }
}
