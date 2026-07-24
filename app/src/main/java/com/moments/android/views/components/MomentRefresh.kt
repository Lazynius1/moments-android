package com.moments.android.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Estado compartido del port de `MomentRefreshState`. */
object MomentRefreshState {
    const val threshold = 90f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _pull = MutableStateFlow(0f)
    val pull = _pull.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    var action: (suspend () -> Unit)? = null
    val heldPull: Float get() = if (_isRefreshing.value) threshold else _pull.value.coerceAtMost(threshold)

    fun updatePull(value: Float) {
        if (_isRefreshing.value) return
        _pull.value = value.coerceAtLeast(0f)
        if (_pull.value >= threshold) startRefresh()
    }

    fun startRefresh() {
        val currentAction = action ?: return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _pull.value = threshold
        scope.launch {
            currentAction()
            _isRefreshing.value = false
            _pull.value = 0f
        }
    }
}

/** Host global de la gota de refresh; no intercepta el gesto del scroll. */
@Composable
fun MomentRefreshOverlayHost(modifier: Modifier = Modifier) {
    val pull by MomentRefreshState.pull.collectAsState()
    val refreshing by MomentRefreshState.isRefreshing.collectAsState()
    if (pull <= 2f && !refreshing) return
    Box(modifier.fillMaxWidth().statusBarsPadding(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .offset(y = (MomentRefreshState.heldPull * .7f).dp)
                .size(40.dp)
                .background(Color(0xCC1C2025), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
        }
    }
}
