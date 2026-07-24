package com.moments.android.views.profile.core.sections

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.Moment

/** Port de `ProfileGridMomentMenu.swift`: gesture, hero card and visitor action rail. */
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.profileMomentThumbnailGesture(onTap: () -> Unit, onLongPress: (() -> Unit)? = null): Modifier = combinedClickable(onClick = onTap, onLongClick = { onLongPress?.invoke() })

@Composable fun ProfileGridHeroCard(moment: Moment, width: Dp, showsChrome: Boolean = true, showsAudience: Boolean = true, onOpenMoment: () -> Unit) {
    val media = moment.visibleMediaItems.firstOrNull()
    val height = (width.value / ProfileGridHeroLayout.aspect(moment.aspectRatio)).dp
    Column(Modifier.width(width).clip(RoundedCornerShape(12.dp)).profileMomentThumbnailGesture(onOpenMoment)) {
        Box(Modifier.fillMaxWidth().height(height)) {
            val url = media?.url ?: moment.previewImageURLString
            if (!url.isNullOrEmpty()) AsyncImage(url, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Text(moment.content, Modifier.fillMaxSize().padding(20.dp), color = Color.White, textAlign = TextAlign.Center)
            if (media?.type?.raw == "video") Icon(Icons.Default.PlayCircle, null, tint = Color.White, modifier = Modifier.align(Alignment.Center).size(36.dp))
        }
        if (showsChrome) Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(moment.profileImagePath, null, Modifier.size(36.dp).clip(CircleShape), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(moment.username, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1); moment.location?.takeIf { it.isNotBlank() }?.let { Text(it, fontSize = 11.sp, maxLines = 1) } }
            if (showsAudience) Text(moment.audience.orEmpty(), fontSize = 11.sp)
        }
    }
}

@Composable fun ProfileGridVisitorActionBar(moment: Moment, canShare: Boolean, onComment: () -> Unit, onShare: () -> Unit, onReact: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf("❤️", "😂", "🔥", "👏").forEach { reaction -> Text(reaction, Modifier.profileMomentThumbnailGesture(onTap = { onReact(reaction) }), fontSize = 22.sp) }
        Spacer(Modifier.weight(1f))
        if (!moment.disableComments) Icon(Icons.Default.ChatBubbleOutline, stringResource(R.string.comments_title), Modifier.size(28.dp).profileMomentThumbnailGesture(onComment))
        Icon(Icons.Default.Send, stringResource(R.string.context_menu_share_moment), Modifier.size(28.dp).profileMomentThumbnailGesture(onTap = { if (canShare) onShare() }), tint = if (canShare) Color.Unspecified else Color.Gray)
    }
}
