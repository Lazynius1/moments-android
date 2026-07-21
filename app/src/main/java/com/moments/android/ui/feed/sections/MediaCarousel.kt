package com.moments.android.ui.feed.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.data.FeedMediaItem
import com.moments.android.data.FeedMoment
import com.moments.android.ui.feed.FeedInk

// Radio del media (FeedMomentCardLayout.mediaCornerRadius = 12 en iOS).
private val MediaCorner = RoundedCornerShape(12.dp)

@Composable
fun MomentMediaCarousel(moment: FeedMoment) {
    val pagerState = rememberPagerState(pageCount = { moment.mediaItems.size })
    val ratio = aspectRatioValue(moment.mediaItems.getOrNull(pagerState.currentPage)?.aspectRatio ?: moment.aspectRatio)

    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .shadow(8.dp, MediaCorner, clip = false)
            .clip(MediaCorner)
            .background(FeedInk.copy(alpha = 0.05f)),
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            MediaPage(moment.mediaItems[page], moment.username)
        }
        if (moment.mediaItems.size > 1) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(moment.mediaItems.size) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        Modifier
                            .size(width = 6.dp, height = 4.dp)
                            .clip(RoundedCornerShape(50))
                            .background(if (active) Color.White else Color.White.copy(alpha = 0.35f)),
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaPage(media: FeedMediaItem, username: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        AsyncImage(
            model = if (media.type == "video") media.thumbnailUrl ?: media.url else media.url,
            contentDescription = username,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (media.type == "video") {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.42f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }
    }
}

/** Convierte "w:h" o decimal a ratio ancho/alto (para Modifier.aspectRatio), acotado. */
private fun aspectRatioValue(raw: String?): Float {
    if (raw.isNullOrBlank()) return 1f
    val v = if (raw.contains(":")) {
        val parts = raw.split(":")
        val w = parts.getOrNull(0)?.toFloatOrNull() ?: 1f
        val h = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
        if (h != 0f) w / h else 1f
    } else {
        raw.toFloatOrNull() ?: 1f
    }
    return v.coerceIn(0.5f, 1.91f)
}
