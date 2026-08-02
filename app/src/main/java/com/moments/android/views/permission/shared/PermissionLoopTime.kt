package com.moments.android.views.permission.shared

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis

/**
 * Tiempo continuo en segundos (≡ `TimelineView(.animation)` / `timeIntervalSinceReferenceDate`
 * relativo al inicio). Sirve para pans/scrolls con `sin`/`cos` sin Reverse brusco.
 */
@Composable
fun rememberPermissionLoopTimeSeconds(active: Boolean): Float {
    var tSec by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) {
            tSec = 0f
            return@LaunchedEffect
        }
        val start = withFrameMillis { it }
        while (true) {
            withFrameMillis { frame ->
                tSec = (frame - start) / 1000f
            }
        }
    }
    return tSec
}
