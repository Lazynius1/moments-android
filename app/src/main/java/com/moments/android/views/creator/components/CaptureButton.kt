package com.moments.android.views.creator.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.momentsChromeGlass
import kotlinx.coroutines.delay

/**
 * Port de `CaptureButton.swift`: tap = foto; long-press ≥0.5s = vídeo.
 */
@Composable
fun CaptureButton(
    isRecording: Boolean,
    onTap: () -> Unit,
    onLongPressStart: () -> Unit,
    onLongPressEnd: () -> Unit,
    modifier: Modifier = Modifier,
    lensIconURL: String? = null,
) {
    var isPressed by remember { mutableStateOf(false) }
    var longPressArmed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1f,
        animationSpec = tween(150),
        label = "captureScale",
    )

    LaunchedEffect(isPressed) {
        if (!isPressed) {
            longPressArmed = false
            return@LaunchedEffect
        }
        longPressArmed = false
        delay(500)
        if (isPressed) {
            longPressArmed = true
            onLongPressStart()
        }
    }

    Box(
        modifier
            .scale(scale)
            .size(88.dp)
            .momentsChromeGlass(
                shape = CircleShape,
                interactive = true,
                tintOpacity = if (isRecording) 0.55f else MomentsChromeGlass.defaultTintOpacity,
                tint = if (isRecording) Color.Red else null,
            )
            .border(1.5.dp, Color.White.copy(0.22f), CircleShape)
            .pointerInput(isRecording) {
                detectDragGestures(
                    onDragStart = { isPressed = true },
                    onDrag = { change, _ -> change.consume() },
                    onDragCancel = {
                        isPressed = false
                        if (isRecording || longPressArmed) onLongPressEnd()
                    },
                    onDragEnd = {
                        val wasLong = isRecording || longPressArmed
                        isPressed = false
                        if (wasLong) {
                            onLongPressEnd()
                        } else {
                            onTap()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (!lensIconURL.isNullOrBlank()) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(lensIconURL)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White, CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(68.dp)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}
