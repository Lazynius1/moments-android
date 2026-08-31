package com.moments.android.views.profile.core

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.R
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Android counterpart of iOS `ProfileContextFlipTransition`. */
data class ProfileContextFlipConfiguration(
    val preferredHeight: Dp,
    val sourceCornerRadius: Dp,
    val destinationCornerRadius: Dp = 28.dp,
    val showExternalClose: Boolean = false,
) {
    companion object {
        val Qr = ProfileContextFlipConfiguration(
            preferredHeight = 500.dp,
            sourceCornerRadius = 50.dp,
        )
        val Incognito = ProfileContextFlipConfiguration(
            // Deliberadamente más compacto que iOS: deja visible el contexto Android.
            preferredHeight = 560.dp,
            sourceCornerRadius = 24.dp,
            showExternalClose = true,
        )
        val SettingsQr = ProfileContextFlipConfiguration(
            preferredHeight = 500.dp,
            sourceCornerRadius = 18.dp,
        )
    }
}

@Composable
fun ProfileContextFlipTransition(
    sourceBounds: Rect,
    configuration: ProfileContextFlipConfiguration,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    source: @Composable BoxScope.() -> Unit,
    destination: @Composable (close: () -> Unit) -> Unit,
) {
    val progress = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    var isClosing by remember { mutableStateOf(false) }

    fun close() {
        if (isClosing) return
        isClosing = true
        scope.launch {
            progress.animateTo(
                targetValue = 0f,
                animationSpec = tween(400, easing = FastOutSlowInEasing),
            )
            onDismiss()
        }
    }

    BackHandler(onBack = ::close)
    LaunchedEffect(Unit) {
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(440, easing = FastOutSlowInEasing),
        )
    }

    val dark = isSystemInDarkTheme()
    val destinationBackground = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val horizontalInsetPx = with(density) { 18.dp.toPx() }
        val verticalInsetPx = with(density) { 16.dp.toPx() }
        val preferredHeightPx = with(density) { configuration.preferredHeight.toPx() }
        val destinationHeight = preferredHeightPx.coerceAtMost(heightPx - verticalInsetPx * 2f)
        val destinationBounds = Rect(
            left = horizontalInsetPx,
            top = (heightPx - destinationHeight) / 2f,
            right = widthPx - horizontalInsetPx,
            bottom = (heightPx + destinationHeight) / 2f,
        )
        val safeSource = sourceBounds.takeIf { it.width > 0f && it.height > 0f }
            ?: Rect(
                left = widthPx / 2f - 24f,
                top = verticalInsetPx,
                right = widthPx / 2f + 24f,
                bottom = verticalInsetPx + 48f,
            )
        val frame = profileFlipLerpRect(safeSource, destinationBounds, progress.value)
        val cornerPx = with(density) {
            profileFlipLerp(
                configuration.sourceCornerRadius.toPx(),
                configuration.destinationCornerRadius.toPx(),
                progress.value,
            )
        }
        val corner = with(density) { cornerPx.toDp() }

        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.42f * progress.value))
                .clickable(onClick = ::close),
        )

        Box(
            Modifier
                .offset { IntOffset(frame.left.roundToInt(), frame.top.roundToInt()) }
                .size(
                    width = with(density) { frame.width.toDp() },
                    height = with(density) { frame.height.toDp() },
                )
                .graphicsLayer {
                    rotationY = 180f * progress.value
                    cameraDistance = 28f * density.density
                    shape = RoundedCornerShape(corner)
                    clip = true
                    shadowElevation = 24f * density.density * progress.value
                }
                .zIndex(1f),
        ) {
            if (progress.value < 0.5f) {
                source()
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(destinationBackground)
                        .graphicsLayer { rotationY = 180f },
                ) {
                    destination(::close)
                    if (configuration.showExternalClose && progress.value > 0.82f) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .size(54.dp)
                                .clickable(onClick = ::close),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = stringResource(R.string.common_close),
                                tint = if (dark) Color.White else Color(0xFF0B1215),
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun profileFlipLerpRect(start: Rect, end: Rect, progress: Float): Rect = Rect(
    left = profileFlipLerp(start.left, end.left, progress),
    top = profileFlipLerp(start.top, end.top, progress),
    right = profileFlipLerp(start.right, end.right, progress),
    bottom = profileFlipLerp(start.bottom, end.bottom, progress),
)

private fun profileFlipLerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress
