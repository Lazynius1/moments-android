package com.moments.android.views.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Port de `UserRowSkeletonView.swift`. */
@Composable
fun UserRowSkeletonView(avatarSize: Dp = 40.dp, modifier: Modifier = Modifier) {
    val surface = if (isSystemInDarkTheme()) Color.White.copy(.08f) else Color.Black.copy(.06f)
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp).shimmer(true), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(avatarSize).background(surface, CircleShape))
        Spacer(Modifier.width(14.dp))
        Box(Modifier.width(120.dp).height(14.dp).background(surface, RoundedCornerShape(4.dp)))
        Spacer(Modifier.weight(1f))
        Box(Modifier.size(24.dp).background(surface, CircleShape))
    }
}

/** Port de `UserRowSkeletonList`. */
@Composable
fun UserRowSkeletonList(rows: Int = 5, avatarSize: Dp = 40.dp, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth()) { repeat(rows.coerceAtLeast(0)) { UserRowSkeletonView(avatarSize) } }
}
