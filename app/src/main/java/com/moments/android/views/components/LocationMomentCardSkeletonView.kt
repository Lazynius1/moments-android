package com.moments.android.views.components

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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.moments.android.views.feed.core.sections.rememberShimmerBrush

/** Port de `LocationMomentCardSkeletonView.swift`. */
@Composable
fun LocationMomentCardSkeletonView(modifier: Modifier = Modifier) {
    val surface = if (isSystemInDarkTheme()) Color.White.copy(.08f) else Color.Black.copy(.06f)
    val shimmer = rememberShimmerBrush(surface)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(180.dp).background(shimmer, RoundedCornerShape(18.dp))) {
            Row(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(32.dp).background(Color.White.copy(.22f), CircleShape))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.width(90.dp).height(10.dp).background(Color.White.copy(.22f), RoundedCornerShape(3.dp)))
                    Box(Modifier.width(50.dp).height(8.dp).background(Color.White.copy(.22f), RoundedCornerShape(3.dp)))
                }
                Spacer(Modifier.weight(1f))
            }
        }
        Box(Modifier.width(200.dp).height(12.dp).padding(start = 12.dp, top = 10.dp).background(shimmer, RoundedCornerShape(4.dp)))
    }
}
