package com.moments.android.ui.feed.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.data.StoryUser
import com.moments.android.ui.feed.FeedInk

/** Barra superior del feed: rings de historias + iconos de actividad/mensajes. */
@Composable
fun FeedHeaderBar(
    stories: List<StoryUser>,
    onCreateStory: () -> Unit,
    onOpenStory: (StoryUser) -> Unit,
    onOpenActivity: () -> Unit,
    onOpenMessages: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 12.dp, end = 4.dp),
            modifier = Modifier.weight(1f),
        ) {
            item { YourStoryRing(onClick = onCreateStory) }
            items(stories, key = { it.id }) { user ->
                UserStoryRing(username = user.username, imageUrl = null, onClick = { onOpenStory(user) })
            }
        }
        Row(
            Modifier.padding(end = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderIcon(Icons.Filled.FavoriteBorder, stringResource(R.string.feed_activity), onOpenActivity)
            HeaderIcon(Icons.AutoMirrored.Filled.Send, stringResource(R.string.feed_messages), onOpenMessages)
        }
    }
}

@Composable
private fun HeaderIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(38.dp).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = FeedInk.copy(alpha = 0.85f), modifier = Modifier.size(24.dp))
    }
}
