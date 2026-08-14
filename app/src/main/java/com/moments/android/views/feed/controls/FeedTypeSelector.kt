package com.moments.android.views.feed.controls

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.MomentsPressSpec
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsPress
import kotlinx.coroutines.delay

private val FeedAuroraColors = listOf(
    Color(0xFF007AFF),
    Color(0xFFAF52DE),
    Color(0xFFFF375F),
    Color(0xFF02C39A),
)

/**
 * Port 1:1 de `FeedTypeSelector.swift` → `FloatingGlassFeedToggle`.
 * Wordmark inicial + selector wrap-content con aurora estático.
 */
@Composable
fun FloatingGlassFeedToggle(
    selectedFeedType: FeedType,
    onSelect: (FeedType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val capsule = RoundedCornerShape(percent = 50)
    val reduceMotion = MotionPolicy.reduceMotion
    var isShowingBrand by remember { mutableStateOf(!reduceMotion) }

    LaunchedEffect(reduceMotion) {
        if (reduceMotion) {
            isShowingBrand = false
            return@LaunchedEffect
        }
        delay(2_750)
        isShowingBrand = false
    }

    // iOS: HStack wrap-content + .padding(4) + Capsule glass
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
        AnimatedContent(
            targetState = isShowingBrand,
            transitionSpec = {
                if (reduceMotion) {
                    fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                } else {
                    val direction = if (selectedFeedType == FeedType.Following) -1 else 1
                    (
                        fadeIn(tween(320)) +
                            scaleIn(tween(320), initialScale = 0.94f) +
                            slideInHorizontally(tween(320)) { direction * (it / 6) }
                        ) togetherWith (
                        fadeOut(tween(260)) +
                            scaleOut(tween(260), targetScale = 0.90f) +
                            slideOutHorizontally(tween(260)) { -direction * (it / 8) }
                        ) using SizeTransform(clip = false)
                }
            },
            label = "feed-brand-morph",
        ) { showingBrand ->
            if (showingBrand) {
                FeedBrandWordmark()
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
        }
    }
}

@Composable
private fun FeedBrandWordmark() {
    val isDark = isSystemInDarkTheme()
    val capsule = RoundedCornerShape(percent = 50)

    Box(
        modifier = Modifier
            .background(
                brush = Brush.linearGradient(
                    FeedAuroraColors.map { it.copy(alpha = 0.22f) },
                ),
                shape = capsule,
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.login_logo_wordmark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(
                if (isDark) Color.White else Color.Black.copy(alpha = 0.8f),
            ),
            modifier = Modifier
                .width(96.dp)
                .height(20.dp),
        )
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
    val auroraBrush = Brush.linearGradient(
        FeedAuroraColors.map { it.copy(alpha = 0.58f) },
    )

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
                            ambientColor = Color(0xFF007AFF).copy(alpha = 0.18f),
                            spotColor = Color(0xFF007AFF).copy(alpha = 0.18f),
                        )
                        .background(
                            brush = auroraBrush,
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
            // iOS: .padding(.horizontal, 12).padding(.vertical, 6)
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
