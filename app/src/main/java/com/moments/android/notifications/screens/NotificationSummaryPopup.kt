package com.moments.android.notifications.screens

import android.content.Context
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationNavigationService
import com.moments.android.notifications.services.NotificationService
import com.moments.android.views.feed.FeedCanvas
import com.moments.android.views.feed.FeedInk
import kotlinx.coroutines.delay

/** Port de NotificationSummaryService.swift */
object NotificationSummaryService {
    private const val PREFS = "notification_summary"
    private const val KEY_LAST_CLOSE = "lastAppCloseTime"
    private const val THRESHOLD_MINUTES = 30.0

    fun checkShouldShowSummary(
        context: Context,
        unreadNotifications: Int,
        unreadMessages: Int,
        onShow: () -> Unit,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastClose = prefs.getFloat(KEY_LAST_CLOSE, 0f).toDouble()
        val now = System.currentTimeMillis() / 1000.0
        val minutesSince = (now - lastClose) / 60.0
        if (lastClose > 0 && minutesSince >= THRESHOLD_MINUTES &&
            (unreadNotifications > 0 || unreadMessages > 0)
        ) {
            onShow()
        }
    }

    fun markAppClosed(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putFloat(KEY_LAST_CLOSE, (System.currentTimeMillis() / 1000.0).toFloat())
            .apply()
    }
}

/** Port de NotificationSummaryPopup.swift */
@Composable
fun NotificationSummaryPopup(
    isPresented: Boolean,
    unreadNotifications: Int,
    unreadMessages: Int,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onNavigate: () -> Unit,
) {
    if (!isPresented) return
    var appear by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (appear) 1f else 0.8f, label = "scale")
    val alpha by animateFloatAsState(if (appear) 1f else 0f, label = "alpha")

    LaunchedEffect(isPresented) {
        appear = true
        delay(6_000)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp)
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Row(
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
                .offset(y = if (appear) 0.dp else (-20).dp)
                .background(
                    if (isDark) FeedInk.copy(alpha = 0.92f) else FeedCanvas.copy(alpha = 0.95f),
                    RoundedCornerShape(24.dp),
                )
                .clickable {
                    NotificationService.markAllAsRead()
                    NotificationBadgeService.clearNotificationBadge()
                    onNavigate()
                    onDismiss()
                }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("✨", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                text = buildSummaryText(unreadNotifications, unreadMessages),
                modifier = Modifier.padding(start = 12.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDark) Color.White else FeedInk,
            )
        }
    }
}

@Composable
private fun buildSummaryText(unreadNotifications: Int, unreadMessages: Int): String {
    return when {
        unreadMessages > 0 && unreadNotifications == 0 ->
            stringResource(R.string.notification_summary_messages_only, unreadMessages)
        unreadNotifications > 0 && unreadMessages == 0 ->
            stringResource(R.string.notification_summary_notifications_only, unreadNotifications)
        else ->
            stringResource(R.string.notification_summary_both, unreadNotifications, unreadMessages)
    }
}
