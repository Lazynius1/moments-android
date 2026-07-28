package com.moments.android.views.messaging.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moments.android.extensions.momentsChromeGlass

/** Port de `Views/Messaging/Components/ChatFloatingNavigationOverlay.swift`. */
data class ChatFloatingNavigationState(
    val showsSearchControls: Boolean = false,
    val showsScrollToBottom: Boolean = false,
) {
    val isVisible: Boolean get() = showsSearchControls || showsScrollToBottom

    companion object {
        fun resolve(
            hasCompletedInitialScroll: Boolean,
            isSearchVisible: Boolean,
            isSearchingHistory: Boolean,
            hasSearchQuery: Boolean,
            isPinnedToBottom: Boolean,
        ): ChatFloatingNavigationState {
            if (!hasCompletedInitialScroll) return ChatFloatingNavigationState()
            return ChatFloatingNavigationState(
                showsSearchControls = isSearchVisible && (isSearchingHistory || hasSearchQuery),
                showsScrollToBottom = !isPinnedToBottom && !isSearchVisible,
            )
        }
    }
}

@Composable
fun ChatFloatingNavigationOverlay(
    state: ChatFloatingNavigationState,
    isSearching: Boolean,
    canSearchGoUp: Boolean,
    canSearchGoDown: Boolean,
    pendingIncomingCount: Int,
    accentColor: Color,
    badgeTextColor: Color,
    reduceMotion: Boolean,
    onSearchPrevious: () -> Unit,
    onSearchNext: () -> Unit,
    onScrollToBottom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // `isSearching` ≡ iOS (reservado / no usado en body).
    @Suppress("UNUSED_PARAMETER")
    val unusedSearching = isSearching
    val enter = if (reduceMotion) {
        fadeIn(tween(0))
    } else {
        fadeIn() + scaleIn(initialScale = 0.85f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f))
    }
    val exit = if (reduceMotion) {
        fadeOut(tween(0))
    } else {
        fadeOut() + scaleOut(targetScale = 0.85f, animationSpec = spring(dampingRatio = 0.72f, stiffness = 400f))
    }

    Column(
        modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AnimatedVisibility(
            visible = state.showsSearchControls,
            enter = enter,
            exit = exit,
        ) {
            SearchNavigationControls(
                canSearchGoUp = canSearchGoUp,
                canSearchGoDown = canSearchGoDown,
                accentColor = accentColor,
                reduceMotion = reduceMotion,
                onSearchPrevious = onSearchPrevious,
                onSearchNext = onSearchNext,
            )
        }
        AnimatedVisibility(
            visible = state.showsScrollToBottom,
            enter = enter,
            exit = exit,
        ) {
            ChatScrollDownButton(
                pendingCount = pendingIncomingCount,
                accentColor = accentColor,
                badgeTextColor = badgeTextColor,
                onClick = onScrollToBottom,
                reduceMotion = reduceMotion,
            )
        }
    }
}

@Composable
private fun SearchNavigationControls(
    canSearchGoUp: Boolean,
    canSearchGoDown: Boolean,
    accentColor: Color,
    reduceMotion: Boolean,
    onSearchPrevious: () -> Unit,
    onSearchNext: () -> Unit,
) {
    // ≡ iOS scale 0.2→1 + opacity onAppear
    var appeared by remember { mutableStateOf(reduceMotion) }
    LaunchedEffect(Unit) {
        appeared = true
    }
    val appear by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = if (reduceMotion) tween(0) else spring(dampingRatio = 0.72f, stiffness = 400f),
        label = "searchControlsAppear",
    )
    Column(
        modifier = Modifier.graphicsLayer {
            val scale = 0.2f + 0.8f * appear
            scaleX = scale
            scaleY = scale
            alpha = appear
        },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FloatingNavigationButton(Icons.Default.KeyboardArrowUp, canSearchGoUp, accentColor, onSearchPrevious)
        FloatingNavigationButton(Icons.Default.KeyboardArrowDown, canSearchGoDown, accentColor, onSearchNext)
    }
}

@Composable
private fun FloatingNavigationButton(
    icon: ImageVector,
    enabled: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Box(
        Modifier
            .size(40.dp)
            .shadow(
                6.dp,
                CircleShape,
                ambientColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.12f),
                spotColor = Color.Black.copy(alpha = if (isDark) 0.22f else 0.12f),
            )
            .clip(CircleShape)
            .momentsChromeGlass(CircleShape, interactive = true)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = accentColor.copy(alpha = if (enabled) 1f else 0.35f),
            modifier = Modifier.size(17.dp),
        )
    }
}
