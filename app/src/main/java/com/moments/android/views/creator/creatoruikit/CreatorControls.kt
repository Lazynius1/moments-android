package com.moments.android.views.creator.creatoruikit

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager

/**
 * Port de `ToolIconButton` (`CreatorControls.swift`).
 * `momentsChromeGlass` ≡ `.ultraThinMaterial` (sin blur en Android);
 * stroke blanco 0.1 ≡ `Circle().stroke(Color.white.opacity(0.1), lineWidth: 0.5)`.
 */
@Composable
fun ToolIconButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), CircleShape)
            .clickable {
                HapticManager.shared.lightImpact()
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(18.dp),
        )
    }
}
