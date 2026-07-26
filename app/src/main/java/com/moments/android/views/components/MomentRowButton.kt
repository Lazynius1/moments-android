package com.moments.android.views.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.MomentsPressSpec
import com.moments.android.utilities.momentsPress

/** Port de `MomentRowButtonStyle.Feedback`. */
enum class MomentRowButtonFeedback { PRESS, MENU }

/** Port Compose de `MomentRowButton.swift`. */
@Composable
fun MomentRowButton(
    action: () -> Unit,
    feedback: MomentRowButtonFeedback = MomentRowButtonFeedback.PRESS,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val press = when (feedback) {
        MomentRowButtonFeedback.PRESS -> Modifier.momentsPress(
            interaction,
            MomentsPressSpec(0.98f, 0.88f, MomentsPressDefaults.PressHaptic.NONE),
        )
        MomentRowButtonFeedback.MENU -> Modifier.momentsMenuRowFeedback(interaction)
    }
    Box(
        modifier.then(press).clickable(interactionSource = interaction, indication = null) {
            HapticManager.shared.selection()
            action()
        },
    ) { content() }
}

/** Equivalente de `.momentRowInteraction(action:)`. */
fun Modifier.momentRowInteraction(action: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this
        .momentsPress(interaction, MomentsPressSpec(0.98f, 0.88f))
        .clickable(interactionSource = interaction, indication = null) {
            HapticManager.shared.selection()
            action()
        }
}

/** Equivalente de `MomentsMenuRowButtonStyle`: fondo breve solo mientras se pulsa. */
private fun Modifier.momentsMenuRowFeedback(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val bgAlpha by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (MotionPolicy.reduceMotion) tween(0) else tween(100),
        label = "menuRowPressBg",
    )
    val bgColor = if (isSystemInDarkTheme()) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
    // iOS: el padding está en el fill del background, no desplaza el label
    drawBehind {
        if (bgAlpha > 0f) {
            val insetX = 4.dp.toPx()
            val insetY = 3.dp.toPx()
            drawRoundRect(
                color = bgColor.copy(alpha = bgAlpha),
                topLeft = Offset(insetX, insetY),
                size = Size(
                    (size.width - insetX * 2).coerceAtLeast(0f),
                    (size.height - insetY * 2).coerceAtLeast(0f),
                ),
                cornerRadius = CornerRadius(10.dp.toPx()),
            )
        }
    }
}
