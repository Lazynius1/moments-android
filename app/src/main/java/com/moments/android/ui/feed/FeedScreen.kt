package com.moments.android.ui.feed

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.data.BackendFeedService
import com.moments.android.data.FeedMoment
import com.moments.android.data.FeedMediaItem
import com.moments.android.data.StoryUser
import java.util.concurrent.TimeUnit

private val Canvas = Color(0xFFFAF9F6)
private val Ink = Color(0xFF0B1215)
private val Teal = Color(0xFF00A896)
private val Violet = Color(0xFF7251C7)

@Composable
fun FeedScreen(padding: PaddingValues) {
    var following by remember { mutableStateOf(false) }
    var moments by remember { mutableStateOf<List<FeedMoment>>(emptyList()) }
    var stories by remember { mutableStateOf<List<StoryUser>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(following) { BackendFeedService.fetch(if (following) "following" else "forYou") { result -> result.onSuccess { moments = it; error = null }.onFailure { error = it.localizedMessage } } }
    LaunchedEffect(Unit) { BackendFeedService.fetchStoryUsers { it.onSuccess { users -> stories = users } } }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(padding).background(Canvas), contentPadding = PaddingValues(bottom = 22.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { FeedHeader(stories) }
        item { FeedSelector(following) { following = it } }
        if (moments.isEmpty() && error == null) item { Text(stringResource(R.string.feed_loading), Modifier.padding(24.dp), color = Ink.copy(alpha = .58f)) }
        error?.let { item { Text(stringResource(R.string.feed_error), Modifier.padding(24.dp), color = Ink.copy(alpha = .58f)) } }
        items(moments.size, key = { moments[it].id }) { index -> MomentCard(moments[index]) }
    }
}

@Composable private fun FeedHeader(stories: List<StoryUser>) {
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 12.dp, top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.weight(1f)) {
                item { StoryRing(stringResource(R.string.stories_your_story), true) }
                items(stories.size) { StoryRing(stories[it].username, false) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HeaderAction(Icons.Filled.FavoriteBorder, stringResource(R.string.feed_activity))
                HeaderAction(Icons.AutoMirrored.Filled.Send, stringResource(R.string.feed_messages))
            }
        }
    }
}

@Composable private fun StoryRing(name: String, own: Boolean) = Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(58.dp)) {
    Box(Modifier.size(52.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Teal, Violet))).padding(2.dp).clip(CircleShape).background(Canvas), contentAlignment = Alignment.Center) {
        if (own) Icon(Icons.Filled.Add, "Crear historia", tint = Ink, modifier = Modifier.size(19.dp)) else Text(name.take(1).uppercase(), color = Ink, fontWeight = FontWeight.SemiBold)
    }
    Spacer(Modifier.height(5.dp)); Text(name, color = Ink.copy(alpha = .75f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
}

@Composable private fun HeaderAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) = Box(Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(Ink.copy(alpha = .035f)).clickable { }, contentAlignment = Alignment.Center) { Icon(icon, label, tint = Ink, modifier = Modifier.size(20.dp)) }

@Composable private fun FeedSelector(following: Boolean, setFollowing: (Boolean) -> Unit) = Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp), horizontalArrangement = Arrangement.Center) {
    FeedChip(stringResource(R.string.feed_for_you), !following) { setFollowing(false) }; Spacer(Modifier.width(4.dp)); FeedChip(stringResource(R.string.feed_following), following) { setFollowing(true) }
}

@Composable private fun FeedChip(label: String, selected: Boolean, onClick: () -> Unit) = Text(label, Modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).background(if (selected) Brush.linearGradient(listOf(Teal, Violet)) else Brush.linearGradient(listOf(Ink.copy(alpha=.055f), Ink.copy(alpha=.055f)))).padding(horizontal = 14.dp, vertical = 7.dp), color = if (selected) Color.White else Ink.copy(alpha=.72f), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)

@Composable private fun MomentCard(moment: FeedMoment) = Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AuthorAvatar(moment)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(moment.username, color = Ink, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                Text(" · ${relativeTime(moment.timestamp)}", color = Ink.copy(alpha=.5f), style = MaterialTheme.typography.labelSmall)
            }
            moment.location?.let { location ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, tint = Teal, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.width(2.dp))
                    Text(location, color = Ink.copy(alpha = .62f), style = MaterialTheme.typography.labelSmall, maxLines = 1)
                }
            }
        }
    }
    if (moment.mediaItems.isNotEmpty()) MomentMediaCarousel(moment)
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        MomentAction(Icons.Filled.FavoriteBorder, if (!moment.hideLikeCounts && moment.reactionCount > 0) moment.reactionCount.toString() else null)
        if (!moment.disableComments) { Spacer(Modifier.width(18.dp)); MomentAction(Icons.Filled.ChatBubbleOutline, if (moment.commentCount > 0) moment.commentCount.toString() else null) }
        Spacer(Modifier.width(18.dp)); MomentAction(Icons.AutoMirrored.Filled.Send, null)
        Spacer(Modifier.weight(1f)); MomentAction(Icons.Filled.BookmarkBorder, null); Spacer(Modifier.width(12.dp)); MomentAction(Icons.Filled.MoreHoriz, null)
    }
    if (moment.content.isNotBlank()) Text(moment.content, color = Ink, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp))
}

@Composable private fun AuthorAvatar(moment: FeedMoment) = Box(
    Modifier.size(44.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Teal.copy(alpha = .65f), Violet.copy(alpha = .65f)))),
    contentAlignment = Alignment.Center
) {
    val photo = moment.profileImagePath
    if (photo != null) {
        AsyncImage(model = photo, contentDescription = "Perfil de ${moment.username}", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    } else {
        Text(moment.username.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable private fun MomentMediaCarousel(moment: FeedMoment) {
    val pagerState = rememberPagerState(pageCount = { moment.mediaItems.size })
    val height = mediaHeight(moment.mediaItems.getOrNull(pagerState.currentPage)?.aspectRatio ?: moment.aspectRatio)
    Box(Modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(20.dp)).background(Ink.copy(alpha = .05f))) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            MomentMediaPage(moment.mediaItems[page], moment.username)
        }
        if (moment.mediaItems.size > 1) {
            Row(
                Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                repeat(moment.mediaItems.size) { index ->
                    Box(Modifier.size(if (index == pagerState.currentPage) 7.dp else 6.dp).clip(CircleShape).background(if (index == pagerState.currentPage) Color.White else Color.White.copy(alpha = .55f)))
                }
            }
        }
    }
}

@Composable private fun MomentMediaPage(media: FeedMediaItem, username: String) = Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
    AsyncImage(
        model = if (media.type == "video") media.thumbnailUrl ?: media.url else media.url,
        contentDescription = "Momento de $username",
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
    if (media.type == "video") {
        Box(Modifier.size(48.dp).clip(CircleShape).background(Color.Black.copy(alpha = .42f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Reproducir vídeo", tint = Color.White, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable private fun MomentAction(icon: androidx.compose.ui.graphics.vector.ImageVector, count: String?) = Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Ink, modifier = Modifier.size(21.dp)); count?.let { Spacer(Modifier.width(5.dp)); Text(it, color = Ink.copy(alpha=.62f), style = MaterialTheme.typography.labelMedium) } }
private fun mediaHeight(ratio: String?): androidx.compose.ui.unit.Dp = when (ratio?.lowercase()) { "9:16" -> 510.dp; "4:5" -> 430.dp; "16:9" -> 230.dp; else -> 390.dp }

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
