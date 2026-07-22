package com.moments.android.views.feed.core.sections

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import com.moments.android.services.content.FeedMediaItem
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.moments.MomentCarouselLayoutRules
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Port 1:1 de `CarouselImmersivePeekModifier` (FeedMomentComponents.swift).
 * Long-press 0.22s (maximumDistance 12) → immersive + optional fullscreen peek.
 */
fun Modifier.carouselImmersivePeekGesture(
    mediaItems: List<FeedMediaItem>,
    currentImageIndex: Int,
    detectedAspectRatio: Float,
    realAspectRatio: Float,
    onImmersiveChange: (Boolean) -> Unit,
    onPeek: ((imageUrl: String, ratio: Float, isPressing: Boolean) -> Unit)?,
): Modifier = composed {
    val itemsState = rememberUpdatedState(mediaItems)
    val indexState = rememberUpdatedState(currentImageIndex)
    val detectedState = rememberUpdatedState(detectedAspectRatio)
    val realState = rememberUpdatedState(realAspectRatio)
    val peekState = rememberUpdatedState(onPeek)
    val immersiveCb = rememberUpdatedState(onImmersiveChange)
    var isImmersive by remember { mutableStateOf(false) }

    fun endImmersive() {
        if (!isImmersive) return
        isImmersive = false
        immersiveCb.value(false)
        peekState.value?.invoke("", 1f, false)
    }

    fun activateImmersive() {
        isImmersive = true
        immersiveCb.value(true)
        HapticManager.shared.mediumImpact()

        val items = itemsState.value
        val currentItem = items.getOrNull(indexState.value) ?: items.firstOrNull()
        val shouldUseFullscreenPeek = items.size > 1 &&
            currentItem?.type == "image" &&
            currentItem?.isHiddenByModeration != true

        if (currentItem == null ||
            currentItem.type != "image" ||
            currentItem.isHiddenByModeration
        ) {
            return
        }

        val currentItemRatio = MomentCarouselLayoutRules.aspectRatioValue(currentItem.aspectRatio)
            .takeIf { it > 0f && it.isFinite() }
            ?: realState.value
        val detected = detectedState.value
        if (currentItemRatio <= 0f || !currentItemRatio.isFinite()) return
        if (!shouldUseFullscreenPeek && abs(currentItemRatio - detected) <= 0.035f) return

        peekState.value?.invoke(currentItem.url, currentItemRatio, true)
    }

    // iOS onDisappear: cancel immersiveActivationTask + end immersive
    DisposableEffect(Unit) {
        onDispose { endImmersive() }
    }

    this.pointerInput(Unit) {
        coroutineScope {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                val activation: Job = launch {
                    delay(220L)
                    activateImmersive()
                }
                try {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        val moved = hypot(
                            (change.position.x - start.x).toDouble(),
                            (change.position.y - start.y).toDouble(),
                        )
                        if (moved > 12.0 || !change.pressed) break
                        if (change.positionChanged()) change.consume()
                    }
                } finally {
                    activation.cancel()
                    endImmersive()
                }
            }
        }
    }
}
