package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.moments.android.views.components.shimmer

/** Port de `ProfileHeaderSkeletonView.swift`. */
@Composable
fun ProfileHeaderSkeletonView(modifier: Modifier = Modifier) {
    val surface = if (isSystemInDarkTheme()) androidx.compose.ui.graphics.Color.White.copy(alpha = .08f) else androidx.compose.ui.graphics.Color.Black.copy(alpha = .06f)
    Column(
        modifier = modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, end = 16.dp).shimmer(true),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(surface))
            Row(modifier = Modifier.weight(1f)) {
                repeat(3) { index ->
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.width(34.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).background(surface))
                        Box(Modifier.width(48.dp).height(10.dp).clip(RoundedCornerShape(4.dp)).background(surface))
                    }
                    if (index < 2) Spacer(Modifier.weight(.15f))
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
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
    val placements = ProfileBentoLayoutPlanner.plan(kinds)
    val units = ProfileBentoLayoutPlanner.height(kinds)
    val dark = isSystemInDarkTheme()
    BoxWithConstraints(modifier = modifier.fillMaxWidth().shimmer(true)) {
        val gap = 2.dp
        val cell = (maxWidth - gap) / 2
        val gridHeight = cell * units + gap * (units - 1)
        Box(Modifier.fillMaxWidth().height(gridHeight)) {
            placements.forEach { placement ->
                val shade = if (placement.index % 3 == 0) .10f else .06f
                val surface = if (dark) androidx.compose.ui.graphics.Color.White.copy(alpha = shade) else androidx.compose.ui.graphics.Color.Black.copy(alpha = shade)
                val width = cell * placement.kind.colSpan + gap * (placement.kind.colSpan - 1)
                val height = cell * placement.kind.rowSpan + gap * (placement.kind.rowSpan - 1)
                Box(
                    Modifier
                        .offset(x = (cell + gap) * placement.column, y = (cell + gap) * placement.yUnits)
                        .width(width)
                        .height(height)
                        .background(surface),
                )
            }
        }
    }
}
