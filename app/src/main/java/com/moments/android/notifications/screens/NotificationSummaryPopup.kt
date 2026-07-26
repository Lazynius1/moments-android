package com.moments.android.notifications.screens

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.coordinators.LegacyNavigationBridge
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationService
import kotlinx.coroutines.delay

/**
 * Port de NotificationSummaryService.swift
 * Prefs `lastAppCloseTime`; umbral 30 min; delay 1.5s antes de mostrar.
 */
object NotificationSummaryService {
    private const val PREFS = "notification_summary"
    private const val KEY_LAST_CLOSE = "lastAppCloseTime"
    private const val THRESHOLD_MINUTES = 30.0
    private const val SHOW_DELAY_MS = 1_500L

    /**
     * ≡ checkShouldShowSummary — delay 1.5s (UI principal respira).
     */
    suspend fun checkShouldShowSummary(
        context: Context,
        unreadNotifications: Int,
        unreadMessages: Int,
        onShow: () -> Unit,
    ) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastCloseBits = try {
            prefs.getLong(KEY_LAST_CLOSE, 0L)
        } catch (_: ClassCastException) {
            0L
        }
        val lastClose = if (lastCloseBits != 0L) {
            Double.fromBits(lastCloseBits)
        } else {
            // Migración prefs Float antiguas
            try {
                prefs.getFloat(KEY_LAST_CLOSE, 0f).toDouble()
            } catch (_: ClassCastException) {
                0.0
            }
        }
        val now = System.currentTimeMillis() / 1000.0
        val minutesSince = (now - lastClose) / 60.0
        val shouldShow = lastClose > 0 &&
            minutesSince >= THRESHOLD_MINUTES &&
            (unreadNotifications > 0 || unreadMessages > 0)
        if (!shouldShow) return
        delay(SHOW_DELAY_MS)
        onShow()
    }

    fun markAppClosed(context: Context) {
        val now = System.currentTimeMillis() / 1000.0
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putLong(KEY_LAST_CLOSE, now.toRawBits())
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
) {
    if (!isPresented) return

    var appearAnimation by remember { mutableStateOf(false) }
    var dismissing by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = when {
            dismissing -> 0.9f
            appearAnimation -> 1f
            else -> 0.8f
        },
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "summaryScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (appearAnimation && !dismissing) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "summaryAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (appearAnimation && !dismissing) 0f else -20f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
        label = "summaryOffset",
    )

    fun dismissPopup() {
        if (dismissing) return
        dismissing = true
    }

    LaunchedEffect(isPresented) {
        appearAnimation = true
        dismissing = false
        delay(6_000)
        dismissPopup()
    }

    LaunchedEffect(dismissing) {
        if (!dismissing) return@LaunchedEffect
        delay(500)
        onDismiss()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 100.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        SummaryPill(
            unreadNotifications = unreadNotifications,
            unreadMessages = unreadMessages,
            isDark = isDark,
            modifier = Modifier
                .scale(scale)
                .alpha(alpha)
                .offset(y = offsetY.dp)
                .clickable {
                    NotificationService.markAllAsRead()
                    NotificationBadgeService.clearNotificationBadge()
                    dismissPopup()
                    if (unreadMessages > 0 && unreadNotifications == 0) {
                        LegacyNavigationBridge.showMessages()
                    } else {
                        LegacyNavigationBridge.showNotifications()
                    }
                },
        )
    }
}

/** ≡ summaryPill */
@Composable
private fun SummaryPill(
    unreadNotifications: Int,
    unreadMessages: Int,
    isDark: Boolean,
    modifier: Modifier = Modifier,
) {
    val shape = CircleShape
    val primary = if (isDark) Color.White else Color.Black
    Row(
        modifier = modifier
            .shadow(
                10.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.1f),
            )
            .momentsChromeGlass(shape, interactive = true)
            .border(
                1.5.dp,
                Brush.linearGradient(
                    listOf(
                        Color.White.copy(alpha = 0.4f),
                        Color.White.copy(alpha = 0.1f),
                        Color(0xFF6B73FF).copy(alpha = 0.2f),
                    ),
                ),
                shape,
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(end = 4.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = Color(0xFF6B73FF),
            )
            Text(
                text = stringResource(R.string.feed_summary_highlights),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = primary.copy(alpha = 0.92f),
            )
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(primary.copy(alpha = 0.1f)),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (unreadNotifications > 0) {
                SummaryItemView(
                    icon = Icons.Filled.Favorite,
                    count = unreadNotifications,
                    tint = Color.Red,
                    primary = primary,
                )
            }
            if (unreadMessages > 0) {
                SummaryItemView(
                    icon = Icons.AutoMirrored.Filled.Chat,
                    count = unreadMessages,
                    tint = Color(0xFF007AFF),
                    primary = primary,
                )
            }
        }

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = primary.copy(alpha = 0.35f),
        )
    }
}

/** ≡ SummaryItemView */
@Composable
private fun SummaryItemView(
    icon: ImageVector,
    count: Int,
    tint: Color,
    primary: Color,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = tint,
        )
        Text(
            text = count.toString(),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = primary.copy(alpha = 0.92f),
        )
    }
}
