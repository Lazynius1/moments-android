package com.moments.android.views.feed.uploads

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val OrbSize = 58.dp

/** Port de `FloatingMomentUploadOverlay.swift`. */
@Composable
fun FloatingMomentUploadOverlay(
    topInset: Float,
    modifier: Modifier = Modifier,
) {
    val items = MomentUploadTracker.items
    var isExpanded by remember { mutableStateOf(false) }

    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
        AnimatedVisibility(visible = items.isNotEmpty()) {
            ColumnUploadCluster(
                items = items,
                isExpanded = isExpanded,
                onToggleExpanded = { isExpanded = !isExpanded },
                modifier = Modifier.padding(top = topInset.toInt().dp, end = 16.dp),
            )
        }
    }
}

@Composable
private fun ColumnUploadCluster(
    items: List<UploadProgressItem>,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = items.firstOrNull() ?: return
    val extraCount = maxOf(0, items.size - 1)
    val progress by animateFloatAsState(
        targetValue = active.progress.toFloat().coerceIn(0f, 1f),
        label = "uploadOrbProgress",
    )

    Column(modifier, horizontalAlignment = Alignment.End) {
        AnimatedVisibility(visible = isExpanded) {
            FeedUploadProgressRow(
                active,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .shadow(10.dp, RoundedCornerShape(16.dp)),
            )
        }
        Box(contentAlignment = Alignment.TopEnd) {
            Box(
                Modifier
                    .size(OrbSize)
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.92f))
                    .clickable(onClick = onToggleExpanded),
                contentAlignment = Alignment.Center,
            ) {
                CircularUploadRing(progress = progress)
                Text(
                    "${(progress * 100).toInt()}%",
                    color = Color(0xFF0B1215),
                    fontSize = 11.sp,
                )
            }
            if (extraCount > 0) {
                Text(
                    "+$extraCount",
                    color = Color.White,
                    fontSize = 10.sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 2.dp)
                        .background(Color.Black.copy(alpha = 0.62f), RoundedCornerShape(50))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                )
            }
        }
    }
}

@Composable
private fun CircularUploadRing(progress: Float) {
    androidx.compose.material3.CircularProgressIndicator(
        progress = { progress.coerceAtLeast(0.04f) },
        modifier = Modifier.size(OrbSize),
        strokeWidth = 4.dp,
        color = Color(0xFF00A896),
        trackColor = Color(0xFF0B1215).copy(alpha = 0.08f),
    )
}
