package com.moments.android.views.feed.uploads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.views.feed.FeedInk
import com.moments.android.views.feed.FeedTeal

/** Port de `FeedUploadProgressRow.swift`. */
@Composable
fun FeedUploadProgressRow(
    item: UploadProgressItem,
    modifier: Modifier = Modifier,
) {
    val title = item.content.ifBlank { stringResource(R.string.feed_uploading_new_moment) }
    val statusText = when (item.status) {
        UploadStatus.Initializing -> stringResource(R.string.feed_uploading_initializing)
        UploadStatus.Uploading -> stringResource(
            R.string.feed_uploading_progress,
            (item.progress * 100).toInt(),
        )
        UploadStatus.Processing -> stringResource(R.string.feed_uploading_processing)
        UploadStatus.Completed, UploadStatus.Moderated -> stringResource(R.string.feed_uploading_published)
        UploadStatus.Failed -> stringResource(R.string.feed_uploading_error)
    }
    val showProgress = item.status == UploadStatus.Uploading || item.status == UploadStatus.Processing

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White.copy(alpha = 0.92f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        UploadThumbnail(item.thumbnailUrl)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    title,
                    color = FeedInk,
                    fontWeight = FontWeight.Medium,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(statusText, color = FeedInk.copy(alpha = 0.7f), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            if (showProgress) {
                LinearProgressIndicator(
                    progress = { item.progress.toFloat().coerceIn(0f, 1f) },
                    color = FeedTeal,
                    trackColor = FeedInk.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun UploadThumbnail(url: String?) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Gray.copy(alpha = 0.3f)),
        )
    }
}
