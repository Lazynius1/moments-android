package com.moments.android.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.composed
import androidx.compose.ui.unit.dp
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
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
        MomentRowButtonFeedback.PRESS -> Modifier.momentsPress(interaction, com.moments.android.utilities.MomentsPressSpec(.98f, .88f, MomentsPressDefaults.PressHaptic.NONE))
        MomentRowButtonFeedback.MENU -> Modifier.momentsMenuRowFeedback(interaction)
    }
    androidx.compose.foundation.layout.Box(
        modifier.then(press).clickable(interactionSource = interaction, indication = null) {
            HapticManager.shared.selection()
            action()
        },
    ) { content() }
}

/** Equivalente de `.momentRowInteraction(action:)`. */
fun Modifier.momentRowInteraction(action: () -> Unit): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    this.momentsPress(interaction, com.moments.android.utilities.MomentsPressSpec(.98f, .88f))
        .clickable(interactionSource = interaction, indication = null) { HapticManager.shared.selection(); action() }
}

/** Equivalente de `MomentsMenuRowButtonStyle`: fondo breve solo mientras se pulsa. */
private fun Modifier.momentsMenuRowFeedback(interaction: MutableInteractionSource): Modifier = composed {
    val pressed by interaction.collectIsPressedAsState()
    val color = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.12f) else Color.Black.copy(.08f)
    background(if (pressed) color else Color.Transparent, RoundedCornerShape(10.dp)).padding(horizontal = if (pressed) 4.dp else 0.dp, vertical = if (pressed) 3.dp else 0.dp)
}
