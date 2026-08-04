package com.moments.android.views.login

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `SplashScreenView`.
 *
 * Cortina estilo iOS / X: hold → squeeze 0.84 → zoom ×26 + fade del logo y del canvas.
 * El scale va en `graphicsLayer` (sin clip) para que el logo avance a pantalla completa;
 * las animaciones se **await**-ean para no cortar la cortina con un `delay` fijo.
 */
@Composable
fun SplashScreen(onComplete: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    // ≡ iOS: dark 0B1215 / light FAF9F6
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val logoRes = if (isDark) R.drawable.splash_logo_dark else R.drawable.splash_logo_light

    val logoScale = remember { Animatable(1f) }
    val logoOpacity = remember { Animatable(1f) }
    val backgroundOpacity = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (MotionPolicy.reduceMotion) {
            delay(500)
            coroutineScope {
                launch { logoOpacity.animateTo(0f, tween(250, easing = EaseOut)) }
                launch { backgroundOpacity.animateTo(0f, tween(250, easing = EaseOut)) }
            }
            onComplete()
            return@LaunchedEffect
        }

        // ≡ iOS absolute timeline: 0.78s squeeze, 1.04s explode, 1.44s done.
        delay(780)
        // Anticipación (rebote): esperar a terminar antes del zoom.
        logoScale.animateTo(0.84f, tween(durationMillis = 220, easing = EaseInOut))

        // Cortina: logo hacia la cámara (×26) + fade canvas/logo a la vez.
        coroutineScope {
            launch { logoScale.animateTo(26f, tween(durationMillis = 340, easing = EaseInOut)) }
            launch { logoOpacity.animateTo(0f, tween(durationMillis = 340, easing = EaseInOut)) }
            launch { backgroundOpacity.animateTo(0f, tween(durationMillis = 340, easing = EaseInOut)) }
        }
        onComplete()
    }

    val scale = logoScale.value
    val logoAlpha = logoOpacity.value
    val bgAlpha = backgroundOpacity.value

    Box(
        Modifier
            .fillMaxSize()
            .zIndex(2000f)
            // Canvas a pantalla completa; el fade es del color, no del Box entero,
            // para que el logo en zoom no herede un alpha prematuro del contenedor.
            .background(canvas.copy(alpha = bgAlpha)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .size(156.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = logoAlpha
                    transformOrigin = TransformOrigin.Center
                    clip = false
                },
        )
    }
}
