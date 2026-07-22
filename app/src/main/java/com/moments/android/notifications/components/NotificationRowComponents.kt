package com.moments.android.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.notifications.core.NotificationRowMetrics

@Composable
fun NotificationLeadingAvatarView(
    senderIds: List<String>,
    profilePaths: Map<String, String?>,
    isDark: Boolean,
    onPrimaryTap: () -> Unit,
    onSecondaryTap: (() -> Unit)? = null,
) {
    if (senderIds.isEmpty()) {
        Box(
            modifier = Modifier
                .size(NotificationRowMetrics.AVATAR_SIZE_DP.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.2f)),
        )
        return
    }
    if (senderIds.size == 1) {
        NotificationAvatar(
            userId = senderIds.first(),
            imagePath = profilePaths[senderIds.first()],
            sizeDp = NotificationRowMetrics.AVATAR_SIZE_DP,
            isDark = isDark,
            onClick = onPrimaryTap,
        )
        return
    }
    Box(modifier = Modifier.size(NotificationRowMetrics.stackedRowWidthDp.dp, NotificationRowMetrics.STACKED_AVATAR_SIZE_DP.dp)) {
        NotificationAvatar(
            userId = senderIds.getOrNull(1) ?: senderIds.first(),
            imagePath = profilePaths[senderIds.getOrNull(1)],
            sizeDp = NotificationRowMetrics.STACKED_AVATAR_SIZE_DP,
            isDark = isDark,
            onClick = { onSecondaryTap?.invoke() },
            modifier = Modifier.align(Alignment.CenterStart),
        )
        NotificationAvatar(
            userId = senderIds.first(),
            imagePath = profilePaths[senderIds.first()],
            sizeDp = NotificationRowMetrics.STACKED_AVATAR_SIZE_DP,
            isDark = isDark,
            onClick = onPrimaryTap,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .offset(x = (-NotificationRowMetrics.stackedOverlapDp).dp),
        )
    }
}

@Composable
fun NotificationAvatar(
    userId: String,
    imagePath: String?,
    sizeDp: Float,
    isDark: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(sizeDp.dp)
            .clip(CircleShape)
            .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (!imagePath.isNullOrBlank()) {
            AsyncImage(
                model = imagePath,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(
                text = userId.take(1).uppercase(),
                fontSize = (sizeDp * 0.35f).sp,
                fontWeight = FontWeight.SemiBold,
                color = if (isDark) Color.White else Color.Black,
            )
        }
    }
}

@Composable
fun NotificationStoryThumbnail(
    imageUrl: String?,
    isLoading: Boolean,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(
                NotificationRowMetrics.STORY_THUMB_WIDTH_DP.dp,
                NotificationRowMetrics.STORY_THUMB_HEIGHT_DP.dp,
            )
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(NotificationRowMetrics.STORY_THUMB_CORNER_RADIUS_DP.dp))
            .background(Color.Gray.copy(alpha = if (isDark) 0.25f else 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}

@Composable
fun NotificationMomentThumbnail(
    imageUrl: String?,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(Color.Gray.copy(alpha = if (isDark) 0.25f else 0.15f)),
        contentAlignment = Alignment.Center,
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(model = imageUrl, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        }
    }
}
