package com.moments.android.views.components

import androidx.compose.foundation.background
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp

/**
 * Port de `LocationMomentCardSkeletonView.swift`.
 * Imita `ModernLocationMomentRow`: tarjeta 180pt con overlay avatar + nombre.
 */
@Composable
fun LocationMomentCardSkeletonView(modifier: Modifier = Modifier) {
    val surfaceColor = rememberMomentsSkeletonColor()
    val overlaySurfaceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)

    Column(
        modifier = modifier
            .shimmer(isAnimating = true)
            .clearAndSetSemantics { }, // iOS: accessibilityHidden(true)
        horizontalAlignment = Alignment.Start,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp),
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(surfaceColor, RoundedCornerShape(18.dp)),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(32.dp).background(overlaySurfaceColor, CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(
                        Modifier
                            .width(90.dp)
                            .height(10.dp)
                            .background(overlaySurfaceColor, RoundedCornerShape(3.dp)),
                    )
                    Box(
                        Modifier
                            .width(50.dp)
                            .height(8.dp)
                            .background(overlaySurfaceColor, RoundedCornerShape(3.dp)),
                    )
                }
                Spacer(Modifier.weight(1f))
            }
        }

        Box(
            Modifier
                .padding(start = 12.dp, top = 10.dp, end = 12.dp)
                .width(200.dp)
                .height(12.dp)
                .background(surfaceColor, RoundedCornerShape(4.dp)),
        )
    }
}
