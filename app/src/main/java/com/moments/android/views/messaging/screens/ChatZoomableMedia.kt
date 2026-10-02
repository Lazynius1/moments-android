package com.moments.android.views.messaging.screens

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize

/** Zooms the complete media surface so live stickers remain aligned with the photo. */
@Composable
internal fun ChatZoomableMedia(
    mediaId: String,
    isActive: Boolean,
    onZoomChanged: (Boolean) -> Unit,
    content: @Composable () -> Unit,
) {
    var scale by remember(mediaId) { mutableFloatStateOf(1f) }
    var offset by remember(mediaId) { mutableStateOf(Offset.Zero) }
    var viewport by remember { mutableStateOf(IntSize.Zero) }
    var mediaSize by remember { mutableStateOf(IntSize.Zero) }
    val zoomChanged by rememberUpdatedState(onZoomChanged)

    fun bounded(position: Offset, zoom: Float): Offset {
        val x = maxOf(0f, (mediaSize.width * zoom - viewport.width) / 2)
        val y = maxOf(0f, (mediaSize.height * zoom - viewport.height) / 2)
        return Offset(position.x.coerceIn(-x, x), position.y.coerceIn(-y, y))
    }
    LaunchedEffect(isActive) {
        if (!isActive) { scale = 1f; offset = Offset.Zero; zoomChanged(false) }
    }
    Box(
        Modifier.fillMaxSize().clipToBounds().onSizeChanged { viewport = it }
            .pointerInput(mediaId, isActive) {
                detectTapGestures(onDoubleTap = { point ->
                    if (isActive) {
                        if (scale > 1.01f) { scale = 1f; offset = Offset.Zero }
                        else {
                            scale = 3f
                            offset = bounded((Offset(viewport.width / 2f, viewport.height / 2f) - point) * (scale - 1), scale)
                        }
                        zoomChanged(scale > 1.01f)
                    }
                })
            }
            .pointerInput(mediaId, isActive) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.any { it.isConsumed }) break
                        val pinch = event.calculateZoom()
                        // Single-finger swipes at 1× stay unconsumed for HorizontalPager.
                        val transform = isActive && (scale > 1.01f || pinch != 1f)
                        if (transform && event.changes.any { it.pressed && it.previousPressed }) {
                            val next = (scale * pinch).coerceIn(1f, 5f)
                            val center = Offset(viewport.width / 2f, viewport.height / 2f)
                            val anchor = event.calculateCentroid(useCurrent = false) - center
                            val ratio = next / scale
                            offset = bounded(offset * ratio + anchor * (1 - ratio) + event.calculatePan(), next)
                            scale = next
                            zoomChanged(scale > 1.01f)
                            event.changes.forEach { if (it.position != it.previousPosition) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.onSizeChanged { mediaSize = it }.graphicsLayer {
            scaleX = scale; scaleY = scale
            translationX = offset.x; translationY = offset.y
        }, contentAlignment = Alignment.Center) { content() }
    }
}
