package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.moments.android.views.components.rememberMomentsSkeletonColor
import com.moments.android.views.components.shimmer

/** Port de `ProfileHeaderSkeletonView.swift`. */
@Composable
fun ProfileHeaderSkeletonView(modifier: Modifier = Modifier) {
    val surface = rememberMomentsSkeletonColor()
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .shimmer(true)
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(surface))
            // ≡ HStack spacing 0 + 3 columnas maxWidth.infinity (Spacer minLength 0 ≈ 0)
            Row(Modifier.weight(1f)) {
                repeat(3) {
                    Column(
                        Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(Modifier.width(34.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(surface))
                        Box(Modifier.width(48.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(surface))
                    }
                }
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(Modifier.width(140.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).background(surface))
            Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(4.dp)).background(surface))
            Box(Modifier.width(200.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(surface))
        }
        Box(Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(10.dp)).background(surface))
    }
}

/** Port de `ProfileMomentsGridSkeletonView.swift`: anticipa el bento de contenido real. */
@Composable
fun ProfileMomentsGridSkeletonView(modifier: Modifier = Modifier) {
    val kinds = listOf(
        BentoTileKind.HERO, BentoTileKind.UNIT, BentoTileKind.UNIT,
        BentoTileKind.UNIT, BentoTileKind.TALL, BentoTileKind.UNIT,
        BentoTileKind.UNIT, BentoTileKind.UNIT, BentoTileKind.UNIT,
    )
    val onSurface = MaterialTheme.colorScheme.onSurface
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .shimmer(true)
            .clearAndSetSemantics { },
    ) {
        val frames = ProfileMomentsGridMetrics.planFrames(kinds, maxWidth)
        val gridHeight = ProfileMomentsGridMetrics.bentoHeight(kinds, maxWidth)
        Box(Modifier.fillMaxWidth().height(gridHeight)) {
            frames.forEach { frame ->
                val shade = if (frame.index % 3 == 0) 0.10f else 0.06f
                Box(
                    Modifier
                        .offset(x = frame.x, y = frame.y)
                        .width(frame.width)
                        .height(frame.height)
                        .background(onSurface.copy(alpha = shade)),
                )
            }
        }
    }
}
