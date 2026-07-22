package com.moments.android.views.login

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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.views.shared.Surface
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween

/** Splash de arranque — equivalente a SplashScreenView de iOS (logo que escala y hace fade). */
@Composable
fun SplashScreen(onComplete: () -> Unit) {
    val scale = remember { Animatable(1f) }
    val alpha = remember { Animatable(1f) }
    val isDark = isSystemInDarkTheme()
    val logoRes = if (isDark) R.drawable.splash_logo_dark else R.drawable.splash_logo_light

    LaunchedEffect(Unit) {
        delay(780)
        launch { scale.animateTo(0.84f, animationSpec = tween(220)) }
        delay(260)
        launch { scale.animateTo(26f, animationSpec = tween(340)) }
        launch { alpha.animateTo(0f, animationSpec = tween(340)) }
        delay(400)
        onComplete()
    }

    Box(
        Modifier.fillMaxSize().background(Surface).alpha(alpha.value),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(logoRes),
            contentDescription = null,
            modifier = Modifier.size(156.dp).scale(scale.value),
        )
    }
}
