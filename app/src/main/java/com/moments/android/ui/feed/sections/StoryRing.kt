package com.moments.android.ui.feed.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale
import com.moments.android.R
import com.moments.android.ui.feed.FeedCanvas
import com.moments.android.ui.feed.FeedInk
import com.moments.android.ui.feed.StoryRingColors

// Tamaños del anillo (StoryRingLayout de iOS): avatar 50, línea 3, gap ~1.5.
private val AvatarSize = 50.dp
private val OuterSize = 58.dp
private val GapSize = 53.dp

/** Círculo "Tu historia" (con +). */
@Composable
fun YourStoryRing(onClick: () -> Unit) {
    RingItem(label = stringResource(R.string.stories_your_story), imageUrl = null, own = true, onClick = onClick) {
        Icon(Icons.Filled.Add, contentDescription = null, tint = FeedInk, modifier = Modifier.size(19.dp))
    }
}

/** Círculo de historia de otro usuario. */
@Composable
fun UserStoryRing(username: String, imageUrl: String?, onClick: () -> Unit) {
    RingItem(label = username, imageUrl = imageUrl, own = false, onClick = onClick) {
        Text(username.take(1).uppercase(), color = FeedInk, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RingItem(
    label: String,
    imageUrl: String?,
    own: Boolean,
    onClick: () -> Unit,
    placeholder: @Composable () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.width(64.dp).clickable(onClick = onClick),
    ) {
        Box(
            Modifier.size(OuterSize).clip(CircleShape).background(Brush.linearGradient(StoryRingColors)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier.size(GapSize).clip(CircleShape).background(FeedCanvas).padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(AvatarSize).clip(CircleShape).background(FeedInk.copy(alpha = 0.06f)), contentAlignment = Alignment.Center) {
                    if (imageUrl != null) {
                        AsyncImage(model = imageUrl, contentDescription = label, contentScale = ContentScale.Crop, modifier = Modifier.size(AvatarSize).clip(CircleShape))
                    } else {
                        placeholder()
                    }
                }
            }
        }
        Text(
            label,
            color = FeedInk.copy(alpha = 0.76f),
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
