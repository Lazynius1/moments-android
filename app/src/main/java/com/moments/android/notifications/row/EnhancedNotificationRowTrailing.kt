package com.moments.android.notifications.row

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.NotificationType
import com.moments.android.notifications.components.GlassmorphicActionButton
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationsViewModel

/** Port de EnhancedNotificationRow+Trailing.swift */
object EnhancedNotificationRowTrailing {
    @Composable
    fun requestTrailing(group: NotificationGroup, viewModel: NotificationsViewModel, isDark: Boolean) {
        GlassmorphicActionButton(
            text = stringResource(R.string.notifications_accept),
            color = Color(0xFF00A896),
            isDark = isDark,
            onClick = { viewModel.acceptFollowRequest(group) },
        )
        GlassmorphicActionButton(
            text = stringResource(R.string.notifications_reject),
            color = Color.Gray,
            isDark = isDark,
            onClick = { viewModel.rejectFollowRequest(group) },
        )
    }

    @Composable
    fun defaultTrailing(group: NotificationGroup, isDark: Boolean) {
        val emoji = when (group.notifications.first().type) {
            NotificationType.CHAT_BUZZ -> "⚡"
            NotificationType.MESSAGE_REACTION -> group.notifications.first().reaction ?: "❤️"
            NotificationType.ECHO_SUGGESTION -> "📍"
            else -> null
        }
        emoji?.let { iconTrailing(it) }
    }

    @Composable
    fun iconTrailing(symbol: String) {
        Text(
            text = symbol,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(end = 4.dp),
        )
    }
}

object EnhancedNotificationRowMessagesTrailing {
    @Composable
    fun chatTrailing(isDark: Boolean) = EnhancedNotificationRowTrailing.iconTrailing("💬")
}
