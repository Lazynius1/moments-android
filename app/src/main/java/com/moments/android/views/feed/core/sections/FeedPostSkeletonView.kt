package com.moments.android.views.feed.core.sections

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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.components.rememberMomentsSkeletonColor
import com.moments.android.views.components.shimmer
import com.moments.android.views.feed.FeedInk
import kotlinx.coroutines.delay

/**
 * Port 1:1 de `FeedPostSkeletonView.swift`.
 * Fill + [Modifier.shimmer] (host único) — sin brush sweep custom.
 */
@Composable
fun FeedPostSkeletonView(modifier: Modifier = Modifier) {
    val surface = rememberMomentsSkeletonColor()

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .shimmer(isAnimating = true),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(surface))
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier
                        .size(width = 120.dp, height = 10.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(surface),
                )
                Box(
                    Modifier
                        .size(width = 72.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(surface),
                )
            }
            Spacer(Modifier.weight(1f))
        }

        Box(
            Modifier
                .fillMaxWidth()
                .height(360.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(surface),
        )

        Box(
            Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(surface),
        )
        Box(
            Modifier
                .width(180.dp)
                .height(10.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(surface),
        )
    }
}

/** Port 1:1 de `ModernLoadingMoreView` (FeedMomentComponents.swift). */
@Composable
fun ModernLoadingMoreView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    var expand by remember { mutableStateOf(!MotionPolicy.reduceMotion) }

    LaunchedEffect(Unit) {
        if (MotionPolicy.reduceMotion) return@LaunchedEffect
        while (true) {
            expand = true
            delay(1200)
            expand = false
            delay(1200)
        }
    }

    // iOS withAnimation(.easeInOut(1.2).repeatForever) scale 0.8→1.2, opacity 0.6→1
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            MotionPolicy.reduceMotion -> 1f
            expand -> 1.2f
            else -> 0.8f
        },
        animationSpec = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "loadingMoreScale",
    )
    val opacity by androidx.compose.animation.core.animateFloatAsState(
        targetValue = when {
            MotionPolicy.reduceMotion -> 1f
            expand -> 1f
            else -> 0.6f
        },
        animationSpec = androidx.compose.animation.core.tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
        label = "loadingMoreOpacity",
    )

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
