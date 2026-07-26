package com.moments.android.views.messaging.services

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `ChatKeyboardScrollCoordinator.swift`.
 *
 * iOS: NotificationCenter (`keyboardWillChangeFrame` / `keyboardWillHide`) + overlap vs UIWindow.
 * Android: `WindowInsets.ime` (Compose) alimenta el mismo estado publicado.
 * El scroll de la lista vive aparte (`LazyListState` en `ChatMessageListView`).
 */
@Stable
class ChatKeyboardScrollCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var transitionResetJob: Job? = null

    /** ≡ `keyboardHeight` iOS (aquí en px; convertir con Density en UI). */
    var keyboardHeightPx by mutableFloatStateOf(0f)
        private set
    var isVisible by mutableStateOf(false)
        private set
    /** ≡ `animationDuration` iOS (s → ms). IME Compose no expone duración → default 250. */
    var animationDurationMillis by mutableLongStateOf(250L)
        private set
    var isTransitioning by mutableStateOf(false)
        private set

    /**
     * @param visible ≡ flag de `applyKeyboardFrame(_:visible:)` —
     *   hide fuerza altura 0 aunque el frame residual diga otra cosa.
     */
    fun updateKeyboard(
        heightPx: Float,
        durationMillis: Long = 250L,
        visible: Boolean = heightPx > 0f,
    ) {
        animationDurationMillis = durationMillis
        isTransitioning = true
        scheduleTransitionReset(afterMillis = durationMillis)

        val height = if (visible) heightPx.coerceAtLeast(0f) else 0f
        keyboardHeightPx = height
        isVisible = visible && height > 0f
    }

    fun hide(durationMillis: Long = 250L) =
        updateKeyboard(heightPx = 0f, durationMillis = durationMillis, visible = false)

    fun dispose() {
        transitionResetJob?.cancel()
        transitionResetJob = null
    }

    /** ≡ `scheduleTransitionReset(after:)` — max(duration, 50ms) + 32ms. */
    private fun scheduleTransitionReset(afterMillis: Long) {
        transitionResetJob?.cancel()
        val delayMs = maxOf(afterMillis, 50L) + 32L
        transitionResetJob = scope.launch {
            delay(delayMs)
            isTransitioning = false
        }
    }
}

@Composable
fun rememberChatKeyboardScrollCoordinator(): ChatKeyboardScrollCoordinator {
    val coordinator = remember { ChatKeyboardScrollCoordinator() }
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density).toFloat()
    LaunchedEffect(imeBottomPx) {
        if (imeBottomPx > 0f) {
            coordinator.updateKeyboard(imeBottomPx, visible = true)
        } else {
            coordinator.hide()
        }
    }
    DisposableEffect(coordinator) {
        onDispose(coordinator::dispose)
    }
    return coordinator
}
