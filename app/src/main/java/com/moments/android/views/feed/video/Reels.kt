package com.moments.android.views.feed.video

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R

/** Metadatos mínimos de un reel (port parcial de `Reels.swift`). */
data class ReelsVideoItem(
    val momentId: String,
    val videoUrl: String,
    val authorId: String,
    val username: String,
    val thumbnailUrl: String? = null,
)

/** Port de `ReelsViewer` — placeholder fullscreen hasta visor completo. */
@Composable
fun ReelsViewerPlaceholder(
    videos: List<ReelsVideoItem>,
    startIndex: Int = 0,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.feed_reels_tap_to_view), color = Color.White)
    }
}

/** Port de `Reels.swift` — badge y tap en reels del feed. */
@Composable
fun ReelsBadgeOverlay(
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .padding(12.dp)
            .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(8.dp))
            .clickable(onClick = onTap)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.feed_reels_badge),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ReelsFullscreenPlaceholder(modifier: Modifier = Modifier) {
    ReelsViewerPlaceholder(videos = emptyList(), onClose = {}, modifier = modifier)
}
