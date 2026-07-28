package com.moments.android.views.creator.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest

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
    val latestIsRecording by rememberUpdatedState(isRecording)
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 1.05f else 1f,
        animationSpec = tween(150),
        label = "captureScale",
    )

    Box(
        modifier
            .scale(scale)
            .size(88.dp)
            // El shutter es un control de cámara, no chrome: anillo limpio, sin glass.
            .background(
                if (isRecording) Color(0xFFD92626) else Color.Transparent,
                CircleShape,
            )
            .border(2.dp, Color.White.copy(0.78f), CircleShape)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        tryAwaitRelease()
                        isPressed = false
                        if (longPressArmed) {
                            longPressArmed = false
                            onLongPressEnd()
                        }
                    },
                    onTap = {
                        if (latestIsRecording) {
                            onLongPressEnd()
                        } else {
                            onTap()
                        }
                    },
                    onLongPress = {
                        longPressArmed = true
                        onLongPressStart()
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
