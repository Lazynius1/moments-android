package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import com.moments.android.views.components.EmojiSliderTrackFrame
import com.moments.android.views.story.StoryDeckGestureGate
import kotlin.math.max

// MARK: - Zonas donde el Deck Pass no debe “robar” el gesto (stickers interactivos)

/** Port de `StoryInteractionExclusionZone`. */
data class StoryInteractionExclusionZone(
    val id: String,
    val rect: Rect,
    val intents: Set<StoryGestureIntent>,
    val suppressionScope: StoryGestureSuppressionScope,
)

/**
 * Reporta un rectángulo (boundsInRoot ≡ espacio del deck) para bloquear gestos del viewer.
 * ≡ iOS `.storyDeckInteractionExclusion` + `StoryInteractionExclusionKey`.
 */
fun Modifier.storyDeckInteractionExclusion(
    id: String,
    intents: Set<StoryGestureIntent> = setOf(StoryGestureIntent.DECK_SWIPE),
    suppressionScope: StoryGestureSuppressionScope = StoryGestureSuppressionScope.SUPPRESS_DECK,
    horizontalInsetFraction: Float = 0f,
    verticalInsetPx: Float = 0f,
    onRegionChanged: (StoryInteractionExclusionZone) -> Unit,
): Modifier = onGloballyPositioned { coordinates ->
    val bounds = coordinates.boundsInRoot()
    val horizontalInset = bounds.width * horizontalInsetFraction.coerceIn(0f, 0.49f)
    onRegionChanged(
        StoryInteractionExclusionZone(
            id = id,
            rect = Rect(
                left = bounds.left + horizontalInset,
                top = bounds.top + verticalInsetPx,
                right = bounds.right - horizontalInset,
                bottom = bounds.bottom - verticalInsetPx,
            ),
            intents = intents,
            suppressionScope = suppressionScope,
        ),
    )
}

/**
 * Convenience: PreferenceKey → `deckGestureGate` upsert + cleanup al salir.
 * Defaults de intents/scope ≡ call sites sticker (`suppressStoryNavigation` + nav intents).
 */
@Composable
fun Modifier.storyDeckInteractionExclusion(
    id: String,
    gate: StoryDeckGestureGate?,
    intents: Set<StoryGestureIntent> = setOf(
        StoryGestureIntent.DECK_SWIPE,
        StoryGestureIntent.STORY_NAVIGATION_TAP,
        StoryGestureIntent.HOLD_PAUSE,
        StoryGestureIntent.REPLY_SWIPE,
    ),
    suppressionScope: StoryGestureSuppressionScope = StoryGestureSuppressionScope.SUPPRESS_STORY_NAVIGATION,
    horizontalInsetFraction: Float = 0f,
    verticalInsetPx: Float = 0f,
    enabled: Boolean = true,
): Modifier {
    DisposableEffect(id, gate, enabled) {
        onDispose { gate?.removeInteractionRegion(id) }
    }
    if (!enabled || gate == null) return this
    return storyDeckInteractionExclusion(
        id = id,
        intents = intents,
        suppressionScope = suppressionScope,
        horizontalInsetFraction = horizontalInsetFraction,
        verticalInsetPx = verticalInsetPx,
    ) { zone ->
        gate.upsertInteractionRegion(
            StoryGestureRegion(
                id = zone.id,
                rect = zone.rect,
                intents = zone.intents,
                suppressionScope = zone.suppressionScope,
            ),
        )
    }
}

// MARK: - Pan del emoji slider (prioridad frente al deck)

/**
 * ≡ `EmojiSliderVotePanOverlay`: hit box track ±44×±36 dp; normaliza X al track.
 * Devuelve `true` si el gesto terminó con éxito (`ended`), `false` si se canceló.
 */
fun Modifier.emojiSliderVotePan(
    enabled: Boolean,
    trackFrame: EmojiSliderTrackFrame,
    trackLeadingDp: Float,
    trackWidthDp: Float,
    onBegan: () -> Unit,
    onChanged: (Float) -> Unit,
    onEnded: (Float) -> Unit,
    onCancelled: () -> Unit,
): Modifier = pointerInput(enabled, trackFrame, trackLeadingDp, trackWidthDp) {
    if (!enabled) return@pointerInput
    val density = this.density
    fun valueForX(xPx: Float): Float {
        val xDp = xPx / density
        val minX = trackLeadingDp
        val maxX = trackLeadingDp + trackWidthDp
        return ((xDp.coerceIn(minX, maxX) - minX) / max(trackWidthDp, 1f)).coerceIn(0f, 1f)
    }
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val xDp = down.position.x / density
        val yDp = down.position.y / density
        val inHit = xDp >= trackFrame.x - 44f &&
            xDp <= trackFrame.x + trackFrame.width + 44f &&
            yDp >= trackFrame.y - 36f &&
            yDp <= trackFrame.y + trackFrame.height + 36f
        if (!inHit) return@awaitEachGesture

        onBegan()
        onChanged(valueForX(down.position.x))
        var lastX = down.position.x
        val completed = drag(down.id) { change ->
            change.consume()
            lastX = change.position.x
            onChanged(valueForX(change.position.x))
        }
        if (completed) {
            onEnded(valueForX(lastX))
        } else {
            onCancelled()
        }
    }
}

// MARK: - Pan de rascado reveal (no bloquea laterales)

/**
 * ≡ `RevealScratchPanOverlay`: el área lateral (fracción) no recibe hit → passthrough
 * a navegación prev/next; el centro captura el pan de scratch.
 */
@Composable
fun RevealScratchPanOverlay(
    isEnabled: Boolean,
    sidePassThroughFraction: Float = StoryGestureCoordinator.REVEAL_SIDE_PASSTHROUGH_FRACTION,
    onBegan: () -> Unit,
    onPoint: (Offset) -> Unit,
    onEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val sideInset = maxWidth * sidePassThroughFraction.coerceIn(0f, 0.49f)
        val sideInsetPx = with(LocalDensity.current) { sideInset.toPx() }
        Box(
            Modifier
                .fillMaxSize()
                .padding(horizontal = sideInset)
                .pointerInput(isEnabled, sidePassThroughFraction) {
                    if (!isEnabled) return@pointerInput
                    detectDragGestures(
                        onDragStart = { point ->
                            onBegan()
                            onPoint(Offset(point.x + sideInsetPx, point.y))
                        },
                        onDragEnd = onEnded,
                        onDragCancel = onEnded,
                        onDrag = { change, _ ->
                            change.consume()
                            onPoint(Offset(change.position.x + sideInsetPx, change.position.y))
                        },
                    )
                },
        )
    }
}
