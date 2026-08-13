package com.moments.android.adaptive

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Rect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowMetricsCalculator
import kotlinx.coroutines.flow.collectLatest

enum class AdaptiveWidthClass { Compact, Medium, Expanded }

enum class MomentsFoldPosture { Flat, Book, Tabletop }

@Immutable
data class AdaptiveWindowState(
    val width: Dp,
    val height: Dp,
    val widthClass: AdaptiveWidthClass,
    val isLargeDevice: Boolean,
    val foldPosture: MomentsFoldPosture,
    val hingeBounds: DpRect?,
    val isSeparating: Boolean,
) {
    val isCompactHandset: Boolean
        get() = !isLargeDevice && foldPosture == MomentsFoldPosture.Flat

    val isLargeScreen: Boolean
        get() = !isCompactHandset

    /**
     * Stories follow the current viewport. A foldable cover can retain a tablet
     * smallest-width value even though the visible window is compact.
     */
    val usesLargeStoryLayout: Boolean
        get() = widthClass != AdaptiveWidthClass.Compact || isSeparating

    val supportsTwoPanes: Boolean
        get() = widthClass != AdaptiveWidthClass.Compact || isSeparating

    val supportsThreePanes: Boolean
        get() = widthClass == AdaptiveWidthClass.Expanded && width >= 1200.dp
}

object AdaptiveContentWidths {
    /** Instagram tablet ronda 594 dp; 600 dp conserva la escala móvil sin estirar el post. */
    val FeedMax = 600.dp
    /** Visor tablet: algo mayor que Creator para aprovechar el escenario inmersivo. */
    val StoryViewerMax = 560.dp
    val StoryMax = 520.dp
    val SupportingPaneMin = 320.dp
    val SupportingPaneMax = 360.dp
    val DialogMax = 720.dp
}

val LocalAdaptiveWindowState = staticCompositionLocalOf<AdaptiveWindowState> {
    error("AdaptiveWindowProvider is missing")
}

@Composable
fun AdaptiveWindowProvider(content: @Composable () -> Unit) {
    val window = rememberAdaptiveWindowState()
    CompositionLocalProvider(LocalAdaptiveWindowState provides window, content = content)
}

@Stable
private class AdaptiveWindowStateHolder(initial: AdaptiveWindowState) {
    var value by mutableStateOf(initial)
}

@Composable
fun rememberAdaptiveWindowState(): AdaptiveWindowState {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val density = LocalDensity.current
    // Reading configuration intentionally invalidates current window metrics after resize/rotation.
    @Suppress("UNUSED_VARIABLE")
    val configuration = LocalConfiguration.current
    val isLargeDevice = configuration.smallestScreenWidthDp >= 600

    val initial = remember(activity, configuration, density) {
        calculateAdaptiveWindowState(activity, density.density, isLargeDevice, foldingFeature = null)
    }
    val holder = remember { AdaptiveWindowStateHolder(initial) }

    LaunchedEffect(activity, configuration, density) {
        holder.value = calculateAdaptiveWindowState(activity, density.density, isLargeDevice, foldingFeature = null)
        if (activity == null) return@LaunchedEffect
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collectLatest { layoutInfo ->
                val fold = layoutInfo.displayFeatures.filterIsInstance<FoldingFeature>().firstOrNull()
                holder.value = calculateAdaptiveWindowState(activity, density.density, isLargeDevice, fold)
            }
    }
    return holder.value
}

internal fun adaptiveWidthClass(width: Dp): AdaptiveWidthClass = when {
    width < 600.dp -> AdaptiveWidthClass.Compact
    width < 840.dp -> AdaptiveWidthClass.Medium
    else -> AdaptiveWidthClass.Expanded
}

private fun calculateAdaptiveWindowState(
    activity: Activity?,
    density: Float,
    isLargeDevice: Boolean,
    foldingFeature: FoldingFeature?,
): AdaptiveWindowState {
    val bounds = activity?.let {
        WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(it).bounds
    } ?: Rect(0, 0, 0, 0)
    val safeDensity = density.coerceAtLeast(1f)
    val width = (bounds.width() / safeDensity).dp
    val height = (bounds.height() / safeDensity).dp
    val posture = when {
        foldingFeature?.state != FoldingFeature.State.HALF_OPENED -> MomentsFoldPosture.Flat
        foldingFeature.orientation == FoldingFeature.Orientation.HORIZONTAL -> MomentsFoldPosture.Tabletop
        else -> MomentsFoldPosture.Book
    }
    val hinge = foldingFeature?.bounds?.takeUnless(Rect::isEmpty)?.let {
        DpRect(
            left = (it.left / safeDensity).dp,
            top = (it.top / safeDensity).dp,
            right = (it.right / safeDensity).dp,
            bottom = (it.bottom / safeDensity).dp,
        )
    }
    return AdaptiveWindowState(
        width = width,
        height = height,
        widthClass = adaptiveWidthClass(width),
        isLargeDevice = isLargeDevice,
        foldPosture = posture,
        hingeBounds = hinge,
        isSeparating = foldingFeature?.isSeparating == true,
    )
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
