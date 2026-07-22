package com.moments.android.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.moments.android.notifications.components.NotificationLeadingAvatarView
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.notifications.core.uniqueSenderIds
import com.moments.android.views.feed.FeedCanvas
import com.moments.android.views.feed.FeedInk

/** Port de NotificationGroupedFollowersOverlay.swift */
@Composable
fun NotificationGroupedFollowersOverlay(
    group: NotificationGroup,
    viewModel: NotificationsViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val senderIds = uniqueSenderIds(group)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(if (isDark) FeedInk else FeedCanvas, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = group.notifications.firstOrNull()?.senderUsername ?: "",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = if (isDark) Color.White else FeedInk,
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(senderIds) { userId ->
                    val notification = group.notifications.first { it.senderId == userId }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenProfile(userId) }
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        NotificationLeadingAvatarView(
                            senderIds = listOf(userId),
                            profilePaths = mapOf(userId to viewModel.getProfileImagePath(userId)),
                            isDark = isDark,
                            onPrimaryTap = { onOpenProfile(userId) },
                        )
                        Text(
                            text = notification.senderUsername,
                            color = if (isDark) Color.White else FeedInk,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}
