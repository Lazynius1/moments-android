package com.moments.android.views.messaging.components

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moments.android.extensions.momentsChromeGlass

/** Port de `Views/Messaging/Components/ChatFloatingNavigationOverlay.swift`. */
data class ChatFloatingNavigationState(
    val showsSearchControls: Boolean = false,
    val showsScrollToBottom: Boolean = false,
) {
    val isVisible: Boolean get() = showsSearchControls || showsScrollToBottom

    companion object {
        fun resolve(hasCompletedInitialScroll: Boolean, isSearchVisible: Boolean, isSearchingHistory: Boolean, hasSearchQuery: Boolean, isPinnedToBottom: Boolean): ChatFloatingNavigationState {
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
    Column(modifier, horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (state.showsSearchControls) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FloatingNavigationButton(Icons.Default.KeyboardArrowUp, canSearchGoUp, accentColor, onSearchPrevious)
                FloatingNavigationButton(Icons.Default.KeyboardArrowDown, canSearchGoDown, accentColor, onSearchNext)
            }
        }
        if (state.showsScrollToBottom) ChatScrollDownButton(pendingIncomingCount, accentColor, badgeTextColor, onScrollToBottom)
    }
}

@Composable
private fun FloatingNavigationButton(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, accentColor: Color, onClick: () -> Unit) {
    Box(Modifier.size(40.dp).clip(CircleShape).momentsChromeGlass(CircleShape, interactive = true).clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = accentColor.copy(alpha = if (enabled) 1f else .35f), modifier = Modifier.size(17.dp))
    }
}
