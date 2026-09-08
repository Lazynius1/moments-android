package com.moments.android.views.shared.tabbar

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlin.math.max

/**
 * Refcount para ocultar el dock de pestañas.
 * Varias pantallas pueden pedirlo a la vez (chat + visor de historias).
 */
@Stable
class TabBarHideController {
    private var hideCount = 0

    var isHidden by mutableStateOf(false)
        private set

    fun requestHidden(hidden: Boolean) {
        hideCount = if (hidden) hideCount + 1 else max(0, hideCount - 1)
        isHidden = hideCount > 0
    }
}

val LocalTabBarHide = staticCompositionLocalOf<TabBarHideController?> { null }

/** Oculta el dock de pestañas mientras esta pantalla está visible. */
@Composable
fun MomentsTabBarHidden(hidden: Boolean = true) {
    val controller = LocalTabBarHide.current ?: return
    DisposableEffect(controller, hidden) {
        if (hidden) controller.requestHidden(true)
        onDispose {
            if (hidden) controller.requestHidden(false)
        }
    }
}
