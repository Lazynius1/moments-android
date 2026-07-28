package com.moments.android.views.login

import androidx.compose.animation.core.Animatable
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `SplashScreenView` — canvas AdaptiveColors + logo por colorScheme + scale/fade.
 * MinimalSplashScreenView (LiquidAurora) no se usa en el flujo de arranque iOS.
 */
@Composable
fun SplashScreen(onComplete: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    // ≡ iOS: dark 0B1215 / light FAF9F6 (no `Surface` fijo claro).
    val canvas = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val logoRes = if (isDark) R.drawable.splash_logo_dark else R.drawable.splash_logo_light
    // ≡ AuthColors.primary(colorScheme)
    val primary = if (isDark) Color.White else Color.Black
    val shadowAlpha = if (isDark) 0.16f else 0.08f

    val logoScale = remember { Animatable(1f) }
    val logoOpacity = remember { Animatable(1f) }
    val backgroundOpacity = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        if (MotionPolicy.reduceMotion) {
            delay(500)
            launch { logoOpacity.animateTo(0f, tween(250)) }
            launch { backgroundOpacity.animateTo(0f, tween(250)) }
            delay(320)
            onComplete()
            return@LaunchedEffect
        }
        delay(780)
        launch { logoScale.animateTo(0.84f, tween(220)) }
        delay(260) // t = 1.04s desde start
        launch { logoScale.animateTo(26f, tween(340)) }
        launch { logoOpacity.animateTo(0f, tween(340)) }
        launch { backgroundOpacity.animateTo(0f, tween(340)) }
        delay(400) // t = 1.44s
        onComplete()
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(canvas.copy(alpha = backgroundOpacity.value)),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = null,
            modifier = Modifier
                .size(156.dp)
                .shadow(
                    elevation = 18.dp,
                    ambientColor = primary.copy(alpha = shadowAlpha),
                    spotColor = primary.copy(alpha = shadowAlpha),
                )
                .scale(logoScale.value)
                .alpha(logoOpacity.value),
        )
    }
}
