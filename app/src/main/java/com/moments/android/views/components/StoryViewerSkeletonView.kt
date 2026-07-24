package com.moments.android.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Port de `StoryViewerSkeletonView.swift`. */
@Composable
fun StoryViewerSkeletonView(segmentCount: Int = 3, modifier: Modifier = Modifier) {
    val surface = Color.White.copy(.16f)
    Column(modifier.fillMaxSize().shimmer(true)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(segmentCount.coerceAtLeast(1)) {
                Box(Modifier.weight(1f).height(2.5.dp).background(surface, RoundedCornerShape(2.dp)))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp).background(surface, CircleShape))
            Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.width(96.dp).height(12.dp).background(surface, RoundedCornerShape(3.dp)))
                Box(Modifier.width(60.dp).height(9.dp).background(surface, RoundedCornerShape(3.dp)))
            }
            Spacer(Modifier.weight(1f))
        }
    }
}
