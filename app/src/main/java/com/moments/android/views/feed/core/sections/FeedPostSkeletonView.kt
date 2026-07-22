package com.moments.android.views.feed.core.sections

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.FeedInk
import kotlinx.coroutines.delay

/**
 * Port 1:1 de `FeedPostSkeletonView.swift`.
 */
@Composable
fun FeedPostSkeletonView(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val surface = if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f)
    val shimmer = rememberShimmerBrush(base = surface)

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(shimmer))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(width = 120.dp, height = 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmer),
                )
                Box(
                    Modifier
                        .size(width = 72.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(shimmer),
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(shimmer),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmer),
        )
        Box(
            Modifier
                .width(180.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(shimmer),
        )
    }
}

@Composable
internal fun rememberShimmerBrush(base: Color = FeedInk.copy(alpha = 0.06f)): Brush {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translate by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmerTranslate",
    )
    val mid = base.copy(alpha = (base.alpha * 2f).coerceAtMost(0.18f))
    return Brush.linearGradient(
        colors = listOf(base, mid, base),
        start = Offset(translate - 200f, 0f),
        end = Offset(translate, 0f),
    )
}

/** Port 1:1 de `ModernLoadingMoreView` (FeedMomentComponents.swift). */
@Composable
fun ModernLoadingMoreView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    var scale by remember { mutableFloatStateOf(if (MotionPolicy.reduceMotion) 1f else 0.8f) }
    var opacity by remember { mutableFloatStateOf(if (MotionPolicy.reduceMotion) 1f else 0.6f) }

    LaunchedEffect(Unit) {
        if (MotionPolicy.reduceMotion) return@LaunchedEffect
        while (true) {
            scale = 1.2f
            opacity = 1f
            delay(1200)
            scale = 0.8f
            opacity = 0.6f
            delay(1200)
        }
    }

    Row(
        modifier
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(percent = 50),
                ambientColor = if (isDark) Color.Black.copy(alpha = 0.3f) else FeedInk.copy(alpha = 0.08f),
                spotColor = if (isDark) Color.Black.copy(alpha = 0.3f) else FeedInk.copy(alpha = 0.08f),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(24.dp)
                .scale(scale)
                .graphicsLayer { alpha = opacity }
                .clip(CircleShape)
                .background(
                    Brush.linearGradient(
                        listOf(Color.fromHex("007AFF"), Color.fromHex("6B73FF")),
                    ),
                ),
        )
        Text(
            stringResource(R.string.feed_loading_more),
            color = if (isDark) Color.White.copy(alpha = 0.6f) else FeedInk.copy(alpha = 0.55f),
            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
        )
    }
}
