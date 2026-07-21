package com.moments.android.ui.feed.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.data.FeedMoment
import com.moments.android.ui.feed.FeedInk
import com.moments.android.ui.feed.FeedPurple
import com.moments.android.ui.feed.FeedTeal
import java.util.concurrent.TimeUnit

/** Tarjeta de un momento — equivalente a ModernPostCardView de iOS. */
@Composable
fun PostCard(moment: FeedMoment) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 4.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        PostHeader(moment)

        Box(Modifier.fillMaxWidth().padding(horizontal = 4.dp), contentAlignment = Alignment.BottomEnd) {
            if (moment.mediaItems.isNotEmpty()) MomentMediaCarousel(moment)
            if (moment.mediaItems.isNotEmpty()) PostActionButtons(moment)
        }

        if (moment.content.isNotBlank()) {
            Text(
                moment.content,
                color = FeedInk,
                fontSize = 14.sp,
                maxLines = 3,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun PostHeader(moment: FeedMoment) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AuthorAvatar(moment)
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(moment.username, color = FeedInk, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                Text("·", color = FeedInk.copy(alpha = 0.5f), fontSize = 11.sp)
                Text(relativeTime(moment.timestamp), color = FeedInk.copy(alpha = 0.5f), fontSize = 11.sp)
            }
            moment.location?.takeIf { it.isNotBlank() }?.let { location ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = FeedTeal, modifier = Modifier.size(12.dp))
                    Text(location, color = FeedInk.copy(alpha = 0.7f), fontSize = 13.sp, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AuthorAvatar(moment: FeedMoment) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(FeedTeal.copy(alpha = 0.65f), FeedPurple.copy(alpha = 0.65f)))),
        contentAlignment = Alignment.Center,
    ) {
        val photo = moment.profileImagePath
        if (photo != null) {
            AsyncImage(model = photo, contentDescription = moment.username, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(moment.username.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun relativeTime(timestamp: Long): String {
    val elapsed = System.currentTimeMillis() - timestamp
    return when {
        elapsed < TimeUnit.MINUTES.toMillis(1) -> stringResource(R.string.time_now)
        elapsed < TimeUnit.HOURS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toMinutes(elapsed)} ${stringResource(R.string.time_min)}"
        elapsed < TimeUnit.DAYS.toMillis(1) -> "${TimeUnit.MILLISECONDS.toHours(elapsed)} ${stringResource(R.string.time_hour)}"
        elapsed < TimeUnit.DAYS.toMillis(7) -> "${TimeUnit.MILLISECONDS.toDays(elapsed)} ${stringResource(R.string.time_day)}"
        else -> "${TimeUnit.MILLISECONDS.toDays(elapsed) / 7} ${stringResource(R.string.time_week)}"
    }
}
