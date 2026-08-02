package com.moments.android.views.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Port de `MomentRefreshState` (MomentRefresh.swift). */
object MomentRefreshState {
    const val threshold = 90f
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _pull = MutableStateFlow(0f)
    val pull = _pull.asStateFlow()
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()

    @Volatile
    var action: (suspend () -> Unit)? = null

    val isActive: Boolean get() = _pull.value > 2f || _isRefreshing.value
    val heldPull: Float get() = if (_isRefreshing.value) threshold else _pull.value.coerceAtMost(threshold)

    fun updatePull(value: Float) {
        if (_isRefreshing.value) return
        _pull.value = value.coerceAtLeast(0f)
        if (_pull.value >= threshold) startRefresh()
    }

    fun cancelPullIfIdle() {
        if (_isRefreshing.value) return
        if (_pull.value > 0f && _pull.value < threshold) {
            _pull.value = 0f
        }
    }

    private fun startRefresh() {
        val currentAction = action ?: return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        _pull.value = threshold
        scope.launch {
            runCatching { currentAction() }
            _isRefreshing.value = false
            _pull.value = 0f
        }
    }
}

/**
 * Port de `.momentRefresh { }` — detecta overscroll al tope y alimenta el estado compartido.
 * La gota visual va con [MomentRefreshOverlayHost].
 */
fun Modifier.momentRefresh(action: suspend () -> Unit): Modifier = composed {
    val latestAction by rememberUpdatedState(action)
    DisposableEffect(Unit) {
        MomentRefreshState.action = { latestAction() }
        onDispose {
            MomentRefreshState.action = null
            MomentRefreshState.cancelPullIfIdle()
        }
    }
    val connection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                val pull = MomentRefreshState.pull.value
                // Ya tirando: absorber el gesto hacia arriba para “soltar” la gota.
                if (pull > 0f && available.y < 0f) {
                    val consumed = available.y.coerceAtLeast(-pull)
                    MomentRefreshState.updatePull(pull + consumed)
                    return Offset(0f, consumed)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // Solo overscroll no consumido por LazyColumn/ScrollView (tope).
                if (available.y > 0f) {
                    MomentRefreshState.updatePull(MomentRefreshState.pull.value + available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                MomentRefreshState.cancelPullIfIdle()
                return Velocity.Zero
            }
        }
    }
    nestedScroll(connection)
}

/** Port de `.momentRefreshOverlayHost()` — en Compose la gota es un overlay composable. */
fun Modifier.momentRefreshOverlayHost(): Modifier = this

/**
 * Host global de la gota (paridad del overlay iOS).
 * Colocar encima del contenido (p. ej. en un `Box` raíz), no intercepta gestos.
 */
@Composable
fun MomentRefreshOverlayHost(modifier: Modifier = Modifier) {
    val pull by MomentRefreshState.pull.collectAsState()
    val refreshing by MomentRefreshState.isRefreshing.collectAsState()
    if (pull <= 2f && !refreshing) return
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        MomentRefreshGota()
    }
}

/** Port de `MomentRefreshGota` (iOS 26+). Ancla simplificada al status bar en Android. */
@Composable
fun MomentRefreshGota(modifier: Modifier = Modifier) {
    val refreshing by MomentRefreshState.isRefreshing.collectAsState()
    val isDark = isSystemInDarkTheme()
    val density = LocalDensity.current
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val safeTop = with(density) { 44.dp.toPx() }
    val anchor = rememberAnchorSpec(screenWidth.value, safeTop)
    val travel = MomentRefreshState.heldPull * 0.7f
    val travelDp = with(density) { travel.toDp() }
    val activeAlpha by animateFloatAsState(
        targetValue = if (MomentRefreshState.isActive) 1f else 0f,
        animationSpec = if (MotionPolicy.reduceMotion) tween(0) else tween(150),
        label = "momentRefreshActive",
    )
    val travelAnimated by animateFloatAsState(
        targetValue = travelDp.value,
        animationSpec = if (MotionPolicy.reduceMotion) tween(0) else tween(320),
        label = "momentRefreshTravel",
    )
    Box(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .offset(y = anchor.topOffset)
            .graphicsLayer { alpha = activeAlpha },
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            Modifier
                .size(anchor.width, anchor.height)
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false),
        )
        Box(
            Modifier
                .offset(y = ((anchor.height - 40.dp) / 2) + travelAnimated.dp)
                .size(40.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            if (refreshing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = if (isDark) Color.White else Color.Black,
                    strokeWidth = 2.dp,
                )
            }
        }
    }
}

private data class RefreshAnchorSpec(
    val width: androidx.compose.ui.unit.Dp,
    val height: androidx.compose.ui.unit.Dp,
    val topOffset: androidx.compose.ui.unit.Dp,
)

@Composable
private fun rememberAnchorSpec(widthDp: Float, safeTopPx: Float): RefreshAnchorSpec {
    val density = LocalDensity.current
    val safeTopDp = with(density) { safeTopPx.toDp() }
    return remember(widthDp, safeTopDp) {
        if (safeTopDp >= 55.dp) {
            RefreshAnchorSpec(width = 126.dp, height = 37.dp, topOffset = 14.dp - safeTopDp)
        } else if (safeTopDp >= 40.dp) {
            val isWideNotch = widthDp >= 410f || (widthDp <= 380f && safeTopDp <= 46.dp)
            if (isWideNotch) {
                RefreshAnchorSpec(width = 209.dp, height = 30.dp, topOffset = 2.dp - safeTopDp)
            } else {
                RefreshAnchorSpec(width = 162.dp, height = 33.dp, topOffset = 2.dp - safeTopDp)
            }
        } else {
            RefreshAnchorSpec(
                width = 120.dp,
                height = 30.dp,
                topOffset = maxOf(0.dp, safeTopDp - 30.dp) - safeTopDp,
            )
        }
    }
}
