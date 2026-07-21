package com.moments.android.ui.feed.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.data.FeedMoment
import com.moments.android.ui.feed.FeedInk

/**
 * Píldora glass de acciones flotando abajo-derecha sobre el media.
 * Equivalente a ModernActionButtons de iOS (like / comentario / guardar / opciones).
 */
@Composable
fun PostActionButtons(moment: FeedMoment) {
    val pill = RoundedCornerShape(50)
    Row(
        Modifier
            .padding(end = 16.dp, bottom = 16.dp)
            .shadow(10.dp, pill, clip = false)
            .clip(pill)
            .background(Color.White.copy(alpha = 0.88f))
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ActionIcon(Icons.Filled.FavoriteBorder, count = if (!moment.hideLikeCounts && moment.reactionCount > 0) moment.reactionCount else null)
        if (!moment.disableComments) {
            ActionIcon(Icons.Filled.ChatBubbleOutline, count = if (moment.commentCount > 0) moment.commentCount else null, active = moment.commentCount > 0)
        }
        ActionIcon(Icons.Filled.BookmarkBorder)
        ActionIcon(Icons.Filled.MoreHoriz)
    }
}

@Composable
private fun ActionIcon(icon: ImageVector, count: Int? = null, active: Boolean = false) {
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(FeedInk.copy(alpha = 0.05f)).clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = if (active) Color(0xFF2E7DF6) else FeedInk, modifier = Modifier.size(20.dp))
        }
        if (count != null && count > 0) {
            Text(
                count.toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (active) Color(0xFF2E7DF6) else Color.Gray.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}
