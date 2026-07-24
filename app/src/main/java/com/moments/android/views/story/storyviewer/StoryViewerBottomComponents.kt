package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.views.story.StoryReaction
import com.moments.android.views.story.StoryViewer
import com.moments.android.views.story.latestPerUser

/** Port de `StoryReactionsStrip`. */
@Composable
fun StoryReactionsStrip(reactions: List<String>, showReactions: Boolean, onReaction: (String) -> Unit, onMoreReactions: () -> Unit, modifier: Modifier = Modifier) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp), modifier = modifier) {
        Text("Scroll for more reactions", color = Color.White.copy(alpha = .7f), fontSize = 10.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.horizontalScroll(rememberScrollState()).background(Color(0xCC1C1C1E), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 12.dp),
        ) {
            reactions.forEach { reaction -> Text(reaction, fontSize = if (showReactions) 30.sp else 15.sp, modifier = Modifier.clickable { onReaction(reaction) }) }
            Text("+", color = Color.White, fontSize = 22.sp, modifier = Modifier.background(Color.White.copy(alpha = .12f), CircleShape).clickable(onClick = onMoreReactions).padding(horizontal = 9.dp, vertical = 3.dp))
        }
    }
}

/** Port de `StoryNoInteractionsNotice`. */
@Composable
fun StoryNoInteractionsNotice(modifier: Modifier = Modifier) {
    Text("No interactions yet", color = Color.White.copy(alpha = .68f), textAlign = TextAlign.Center, fontSize = 14.sp, modifier = modifier.padding(horizontal = 20.dp, vertical = 8.dp))
}

/** Port de `StoryNavigationTouchAreas`; solo recibe taps fuera de zonas suprimidas. */
@Composable
fun StoryNavigationTouchAreas(
    sideWidthFraction: Float = StoryGestureCoordinator.NAVIGATION_SIDE_WIDTH_FRACTION,
    shouldSuppressNavigationTapAt: (Float, Float) -> Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier) {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxHeight().fillMaxWidth(sideWidthFraction).clickable { if (!shouldSuppressNavigationTapAt(0f, 0f)) onPrevious() })
            Box(Modifier.weight(1f).fillMaxHeight())
            Box(Modifier.fillMaxHeight().fillMaxWidth(sideWidthFraction).clickable { if (!shouldSuppressNavigationTapAt(1f, 0f)) onNext() })
        }
    }
}

/** Port de `StoryOwnStoryBottomBar`. */
@Composable
fun StoryOwnStoryBottomBar(
    viewers: List<StoryViewer>,
    reactions: List<StoryReaction>,
    audience: String?,
    expirationHours: Int?,
    onViewActivity: () -> Unit,
    onReactionsActivity: () -> Unit,
    showsShare: Boolean = false,
    onShare: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val recent = viewers.sortedByDescending { it.timestamp.time }.take(3)
    val unique = reactions.latestPerUser()
    val audienceText = when (audience?.trim()?.lowercase()?.replace("_", "")?.replace("-", "")) {
        "mutuals", "mutual" -> "Mutuals"
        "bestfriends", "bestfriend" -> "Best friends"
        "onlyme" -> "Only me"
        "custom", "customlist" -> "Custom"
        else -> "Everyone"
    }
    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)) {
        BottomAction(onViewActivity) {
            Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                if (recent.isEmpty()) Text("◉", fontSize = 28.sp) else recent.forEach { viewer ->
                    if (!viewer.profileImagePath.isNullOrBlank()) AsyncImage(viewer.profileImagePath, null, Modifier.size(28.dp).background(Color.DarkGray, CircleShape), contentScale = ContentScale.Crop)
                    else Text("●", color = Color.White.copy(alpha = .7f), fontSize = 22.sp)
                }
            }
            Text("Activity", fontSize = 12.sp)
        }
        BottomAction({}) {
            Text("◉", fontSize = 24.sp)
            Text(audienceText, fontSize = 12.sp)
            Text("${if (expirationHours == 48) 48 else 24}h", fontSize = 11.sp)
        }
        if (showsShare && audienceText == "Everyone") BottomAction(onShare) { Text("✈", fontSize = 24.sp); Text("Share", fontSize = 12.sp) }
        if (unique.isNotEmpty()) BottomAction(onReactionsActivity) {
            Text(unique.map { it.reaction }.distinct().take(3).joinToString(""), fontSize = 22.sp)
            Text(unique.size.toString(), fontSize = 12.sp)
        }
    }
}

@Composable
private fun BottomAction(onClick: () -> Unit, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).padding(horizontal = 8.dp)) { content() }
}
