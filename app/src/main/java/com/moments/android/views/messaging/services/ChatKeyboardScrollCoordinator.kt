package com.moments.android.views.messaging.services

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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

/** Port de `Views/Messaging/Services/ChatKeyboardScrollCoordinator.swift`. */
@Stable
class ChatKeyboardScrollCoordinator {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var resetJob: Job? = null

    var keyboardHeightPx by mutableFloatStateOf(0f)
        private set
    var isVisible by mutableStateOf(false)
        private set
    var animationDurationMillis by mutableStateOf(250L)
        private set
    var isTransitioning by mutableStateOf(false)
        private set

    fun updateKeyboard(heightPx: Float, durationMillis: Long = 250L) {
        animationDurationMillis = durationMillis
        keyboardHeightPx = heightPx.coerceAtLeast(0f)
        isVisible = keyboardHeightPx > 0f
        isTransitioning = true
        resetJob?.cancel()
        resetJob = scope.launch {
            delay(maxOf(durationMillis, 50L) + 32L)
            isTransitioning = false
        }
    }

    fun hide(durationMillis: Long = 250L) = updateKeyboard(0f, durationMillis)

    fun dispose() { resetJob?.cancel() }
}

@Composable
fun rememberChatKeyboardScrollCoordinator(): ChatKeyboardScrollCoordinator {
    val coordinator = remember { ChatKeyboardScrollCoordinator() }
    val density = LocalDensity.current
    val imeBottom = WindowInsets.ime.getBottom(density).toFloat()
    androidx.compose.runtime.LaunchedEffect(imeBottom) { coordinator.updateKeyboard(imeBottom) }
    androidx.compose.runtime.DisposableEffect(coordinator) { onDispose(coordinator::dispose) }
    return coordinator
}
