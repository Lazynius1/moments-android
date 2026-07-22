package com.moments.android.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.feed.FeedCanvas
import com.moments.android.views.feed.FeedInk

@Composable
fun NotificationDateHeader(dateString: String, isDark: Boolean) {
    val label = when (dateString) {
        "New" -> stringResource(R.string.notifications_section_new)
        "This Week" -> stringResource(R.string.notifications_section_this_week)
        "This Month" -> stringResource(R.string.notifications_section_this_month)
        "Earlier" -> stringResource(R.string.notifications_section_earlier)
        else -> dateString
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isDark) FeedInk else FeedCanvas)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = label.uppercase(),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color.White.copy(alpha = 0.62f) else Color.Black.copy(alpha = 0.6f),
        )
    }
}

@Composable
fun NotificationSkeletonRow(isDark: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = if (isDark) 0.3f else 0.2f)),
        )
        Spacer(Modifier.width(15.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray.copy(alpha = if (isDark) 0.3f else 0.2f)),
            )
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .height(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color.Gray.copy(alpha = if (isDark) 0.3f else 0.2f)),
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Gray.copy(alpha = if (isDark) 0.3f else 0.2f)),
        )
    }
}

@Composable
fun NotificationDeletionUndoToast(
    deletedCount: Int,
    isDark: Boolean,
    onUndo: () -> Unit,
) {
    val message = if (deletedCount > 1) {
        stringResource(R.string.notifications_deleted_toast_plural)
    } else {
        stringResource(R.string.notifications_deleted_toast)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.06f))
            .padding(horizontal = 18.dp, vertical = 17.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isDark) Color.White else FeedInk,
            maxLines = 1,
        )
        TextButton(onClick = onUndo) {
            Text(
                text = stringResource(R.string.notifications_deleted_undo),
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else FeedInk,
            )
        }
    }
}

@Composable
fun GlassmorphicActionButton(
    text: String,
    color: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    TextButton(onClick = onClick) {
        Text(
            text = text,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(color)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
