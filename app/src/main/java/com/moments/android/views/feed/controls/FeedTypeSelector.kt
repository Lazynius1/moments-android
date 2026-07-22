package com.moments.android.views.feed.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.MomentsPressSpec
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsPress

/**
 * Port 1:1 de `FeedTypeSelector.swift` → `FloatingGlassFeedToggle`.
 * Capsule glass (momentsChromeGlass) + pill seleccionado teal→purple.
 */
@Composable
fun FloatingGlassFeedToggle(
    selectedFeedType: FeedType,
    onSelect: (FeedType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val capsule = RoundedCornerShape(percent = 50)

    // iOS: .padding(4).background { Capsule ultraThinMaterial + stroke + shadow }
    Row(
        modifier
            .shadow(
                elevation = 10.dp,
                shape = capsule,
                clip = false,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.1f),
            )
            .momentsChromeGlass(capsule, interactive = false)
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FeedType.allCases.forEach { feedType ->
            FeedTypeButton(
                type = feedType,
                selectedType = selectedFeedType,
                onClick = {
                    onSelect(feedType)
                    HapticManager.shared.selection()
                },
            )
        }
    }
}

@Composable
private fun FeedTypeButton(
    type: FeedType,
    selectedType: FeedType,
    onClick: () -> Unit,
) {
    val isSelected = selectedType == type
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val interaction = remember { MutableInteractionSource() }
    val capsule = RoundedCornerShape(percent = 50)
    val teal = Color.fromHex("00A896")
    val purple = Color(0xFFAF52DE)

    Box(
        modifier = Modifier
            .momentsPress(
                interactionSource = interaction,
                spec = MomentsPressSpec(
                    scale = 0.96f,
                    pressedOpacity = 0.92f,
                    haptic = MomentsPressDefaults.PressHaptic.NONE,
                ),
            )
            .then(
                if (isSelected) {
                    Modifier
                        .shadow(
                            elevation = 5.dp,
                            shape = capsule,
                            clip = false,
                            ambientColor = teal.copy(alpha = 0.3f),
                            spotColor = teal.copy(alpha = 0.3f),
                        )
                        .background(
                            brush = Brush.linearGradient(
                                listOf(
                                    teal.copy(alpha = 0.8f),
                                    purple.copy(alpha = 0.8f),
                                ),
                            ),
                            shape = capsule,
                        )
                } else {
                    Modifier
                },
            )
            .clip(capsule)
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = type.title(),
            color = when {
                isSelected -> Color.White
                isDark -> Color.White.copy(alpha = 0.7f)
                else -> Color.Black.copy(alpha = 0.8f)
            },
            fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
            fontWeight = FontWeight.SemiBold,
        )
    }
}
