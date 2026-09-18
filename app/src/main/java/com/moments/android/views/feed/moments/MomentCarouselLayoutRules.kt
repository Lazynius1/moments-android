package com.moments.android.views.feed.moments

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.adaptive.AdaptiveContentWidths
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlin.math.abs
import kotlinx.coroutines.withTimeoutOrNull

/** Port de `MomentCarouselLayoutRules.swift`. */
enum class MomentCarouselPresentationMode {
    Fill,
    FitWithBlur,
}

object MomentCarouselLayoutRules {
    private const val horizontalTolerance = 0.035f
    private const val squareCutoff = 1.15f

    val mediaCornerRadius = 12.dp
    const val minAspectRatio = 0.5f
    const val maxAspectRatio = 1.91f

    fun presentationMode(
        mediaAspectRatio: Float,
        canvasAspectRatio: Float,
    ): MomentCarouselPresentationMode {
        if (!mediaAspectRatio.isFinite() || mediaAspectRatio <= 0f ||
            !canvasAspectRatio.isFinite() || canvasAspectRatio <= 0f
        ) {
            return MomentCarouselPresentationMode.Fill
        }

        val clearlyWiderThanCanvas = mediaAspectRatio > (canvasAspectRatio + horizontalTolerance)
        val isClearlyLandscape = mediaAspectRatio > squareCutoff
        return if (clearlyWiderThanCanvas && isClearlyLandscape) {
            MomentCarouselPresentationMode.FitWithBlur
        } else {
            MomentCarouselPresentationMode.Fill
        }
    }

    fun aspectRatioValue(raw: String?): Float {
        // ≡ iOS `ProcessedMedia.AspectRatio(from:).value` / `parsePersisted`
        return com.moments.android.views.creator.CreatorAspectRatio.parsePersisted(raw).value
    }

    /**
     * Ratio de display en el feed (≡ iOS MomentFeedCrop.feedCardAspect):
     * 9:16 → 4:5; rango continuo 3:4…1.91:1.
     */
    fun feedDisplayAspectRatio(raw: Float): Float {
        val safe = if (raw > 0f && raw.isFinite()) raw else 1f
        return com.moments.android.views.creator.creatoruikit.MomentFeedCrop.feedCardAspect(safe)
    }
}

object MomentCarouselIndicatorStyle {
    val dotWidth = 6.dp
    val dotHeight = 4.dp
    val spacing = 6.dp
    const val activeScale = 1.15f
    const val inactiveOpacity = 0.35f

    val canvasDark = Color(0xFF0B1215)
    val canvasLight = Color(0xFFFAF9F6)

    fun activeColor(isDark: Boolean): Color = if (isDark) canvasLight else canvasDark

    fun inactiveColor(isDark: Boolean): Color = activeColor(isDark).copy(alpha = inactiveOpacity.toFloat())
}

enum class MomentCarouselIndicatorTone {
    OnMedia,
    OnCanvas,
}

/** Port de `FeedMomentCardLayout` (MomentCarouselLayoutRules.swift). */
object FeedMomentCardLayout {
    val listHorizontalPadding = 4.dp
    /** Hueco entre cards en Android (iOS sigue con max(15, 2% altura)). */
    val rowSpacing = 8.dp
    val headerHorizontalPadding = 8.dp
    val actionRowHorizontalPadding = 4.dp
    /** Alineado con el borde del media (`actionRowHorizontalPadding`). */
    val captionHorizontalPadding = 4.dp
    val mediaCornerRadius = MomentCarouselLayoutRules.mediaCornerRadius
    val storyCanvasCornerRadius = mediaCornerRadius
    val peekCornerRadius = mediaCornerRadius

    /** iOS `FeedMomentCardLayout.continuousRoundedRect` (style .continuous ≈ default Compose). */
    val continuousRoundedRectShape = RoundedCornerShape(mediaCornerRadius)

    /** iOS `scaledMediaCornerRadius(_:)`. */
    fun scaledMediaCornerRadius(scale: Float): Dp = mediaCornerRadius * scale

    /** iOS `mediaContentWidth` = screenWidth - listHorizontalPadding * 2. */
    fun mediaContentWidth(screenWidthDp: Float): Float =
        maxOf(
            minOf(screenWidthDp, AdaptiveContentWidths.FeedMax.value) -
                listHorizontalPadding.value * 2f,
            1f,
        )

    @Composable
    fun mediaContentWidth(): Float {
        val w = LocalConfiguration.current.screenWidthDp.toFloat()
        return mediaContentWidth(w)
    }
}

/** Port de `MomentCarouselPageIndicators` (MomentCarouselLayoutRules.swift). Hold + drag = scrub IG. */
@Composable
fun MomentCarouselPageIndicators(
    count: Int,
    currentIndex: Int,
    modifier: Modifier = Modifier,
    tone: MomentCarouselIndicatorTone = MomentCarouselIndicatorTone.OnMedia,
    onIndexChange: ((Int) -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val colors = rememberAdaptiveColors()
    val activeFill = MomentCarouselIndicatorStyle.activeColor(isDark)
    val inactiveFill = MomentCarouselIndicatorStyle.inactiveColor(isDark)
    var isScrubbing by remember { mutableStateOf(false) }
    val hapticView = LocalView.current
    val currentIndexState = rememberUpdatedState(currentIndex)
    val onIndexChangeState = rememberUpdatedState(onIndexChange)
    val countState = rememberUpdatedState(count)

    val spacing = if (isScrubbing) MomentCarouselIndicatorStyle.spacing + 4.dp else MomentCarouselIndicatorStyle.spacing
    val height by animateDpAsState(
        targetValue = if (isScrubbing) 36.dp else 24.dp,
        animationSpec = spring(dampingRatio = 0.86f, stiffness = Spring.StiffnessMediumLow),
        label = "carouselIndicatorHeight",
    )

    Box(
        modifier
            .height(height)
            .fillMaxWidth()
            .pointerInput(count) {
                if (onIndexChangeState.value == null || count < 2) return@pointerInput
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val pointerId = down.id
                    val start = down.position
                    var lastX = start.x
                    var enteredScrub = false

                    fun indexAt(x: Float): Int {
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val total = countState.value
                        if (total <= 1) return 0
                        val t = (x / width).coerceIn(0f, 0.999f)
                        return (t * total).toInt().coerceIn(0, total - 1)
                    }

                    fun commit(next: Int) {
                        val latest = currentIndexState.value
                        if (next == latest) return
                        onIndexChangeState.value?.invoke(next)
                        HapticManager.shared.selection(hapticView)
                    }

                    val holdStarted = System.nanoTime()
                    while (!enteredScrub) {
                        val remainingMs = 160L - (System.nanoTime() - holdStarted) / 1_000_000L
                        if (remainingMs <= 0L) {
                            enteredScrub = true
                            break
                        }
                        val event = withTimeoutOrNull(remainingMs) {
                            awaitPointerEvent(PointerEventPass.Initial)
                        }
                        if (event == null) {
                            enteredScrub = true
                            break
                        }
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: return@awaitEachGesture
                        if (!change.pressed) return@awaitEachGesture
                        lastX = change.position.x
                        val dx = abs(change.position.x - start.x)
                        val dy = abs(change.position.y - start.y)
                        if (dx > slop && dx > dy) {
                            change.consume()
                            enteredScrub = true
                            break
                        }
                        if (dy > slop && dy >= dx) return@awaitEachGesture
                    }

                    isScrubbing = true
                    HapticManager.shared.lightImpact(hapticView)
                    commit(indexAt(lastX))

                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                        change.consume()
                        if (!change.pressed) break
                        commit(indexAt(change.position.x))
                    }
                    isScrubbing = false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(spacing),
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .then(
                    if (isScrubbing) {
                        // iOS: Capsule ultraThinMaterial + stroke primary 0.16.
                        // Android: controlSurface (opaco, distinto del canvas) + el mismo stroke.
                        Modifier
                            .background(colors.controlSurface)
                            .border(1.dp, colors.primary.copy(alpha = 0.16f), RoundedCornerShape(50))
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    } else {
                        Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    },
                ),
        ) {
            repeat(count) { index ->
                val active = index == currentIndex
                Box(
                    Modifier
                        .size(
                            width = if (isScrubbing) 8.dp else MomentCarouselIndicatorStyle.dotWidth,
                            height = if (isScrubbing) 8.dp else MomentCarouselIndicatorStyle.dotHeight,
                        )
                        .scale(if (active) MomentCarouselIndicatorStyle.activeScale else 1f)
                        .clip(RoundedCornerShape(50))
                        .background(if (active) activeFill else inactiveFill),
                )
            }
        }
    }
}
