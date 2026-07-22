package com.moments.android.views.feed.maps.mapsections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.feed.maps.MapPlaceCluster

/** Port de `MapBottomSheetSection.swift` / `LocationBottomSheet`. */
@Composable
fun MapBottomSheetSection(
    cluster: MapPlaceCluster?,
    modifier: Modifier = Modifier,
) {
    if (cluster == null) return
    Column(
        modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
            .padding(20.dp),
    ) {
        Text(cluster.displayName, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        Text(
            stringResource(R.string.maps_bottom_sheet_moments, cluster.momentCount),
            color = Color.Gray,
            fontSize = 13.sp,
        )
    }
}
