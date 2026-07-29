package com.moments.android.views.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Port de `ActivityCollapsibleFilterScroll.swift`.
 * Header inline que desaparece al scrollear; chips flotantes al subir de nuevo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityCollapsibleFilterScroll(
    onRefresh: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
    scrollState: androidx.compose.foundation.ScrollState = rememberScrollState(),
    header: @Composable () -> Unit,
    floatingHeader: @Composable (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var headerHeight by remember { mutableIntStateOf(0) }
    var showFloatingFilters by remember { mutableStateOf(false) }
    var lastOffset by remember { mutableFloatStateOf(0f) }
    var refreshing by remember { mutableStateOf(false) }

    // Equivalente a `filtersScrolledAway`: el bloque de filtros ya salió por arriba.
    val filtersScrolledAway =
        headerHeight > 0 &&
            scrollState.value.toFloat() > headerHeight + ActivityFilterScrollMetrics.scrolledAwayClearance

    LaunchedEffect(scrollState, headerHeight) {
        snapshotFlow { scrollState.value.toFloat() }
            .distinctUntilChanged()
            .collect { offset ->
                val delta = offset - lastOffset
                lastOffset = offset
                val scrolledAway =
                    headerHeight > 0 &&
                        offset > headerHeight + ActivityFilterScrollMetrics.scrolledAwayClearance
                if (!scrolledAway) {
                    showFloatingFilters = false
                    return@collect
                }
                val threshold = ActivityFilterScrollMetrics.directionDeltaThreshold
                when {
                    delta < -threshold -> showFloatingFilters = true
                    delta > threshold -> showFloatingFilters = false
                }
            }
    }

    val body: @Composable () -> Unit = {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { coords -> headerHeight = coords.size.height }
                        .alpha(if (filtersScrolledAway) 0f else 1f),
                ) {
                    header()
                }
                content()
            }

            AnimatedVisibility(
                visible = filtersScrolledAway && showFloatingFilters,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                enter = slideInVertically(tween(200)) { -10 } + fadeIn(tween(200)),
                exit = slideOutVertically(tween(160)) { -10 } + fadeOut(tween(160)),
            ) {
                Box(Modifier.fillMaxWidth()) {
                    (floatingHeader ?: header).invoke()
                }
            }
        }
    }

    if (onRefresh != null) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    onRefresh()
                    refreshing = false
                }
            },
            modifier = modifier.fillMaxSize(),
        ) {
            body()
        }
    } else {
        Box(modifier.fillMaxSize()) { body() }
    }
}

private object ActivityFilterScrollMetrics {
    const val scrolledAwayClearance = 6f
    const val directionDeltaThreshold = 8f
}
