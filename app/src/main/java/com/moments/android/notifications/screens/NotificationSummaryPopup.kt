package com.moments.android.notifications.screens

import android.content.Context
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
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
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "summaryScale",
    )
    val alpha by animateFloatAsState(
        targetValue = if (appearAnimation && !dismissing) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
        label = "summaryAlpha",
    )
    val offsetY by animateFloatAsState(
        targetValue = if (appearAnimation && !dismissing) 0f else -20f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMediumLow),
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
            .padding(top = 106.dp, end = 20.dp),
        contentAlignment = Alignment.TopEnd,
    ) {
        SummaryPill(
            unreadNotifications = unreadNotifications,
            unreadMessages = unreadMessages,
            isDark = isDark,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.80f, 0f)
                }
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
    val shape = NotificationSummaryBubbleShape
    val primary = if (isDark) Color.White else Color.Black
    val accessibilityLabel = when {
        unreadNotifications > 0 && unreadMessages > 0 -> stringResource(
            R.string.notification_summary_both,
            unreadNotifications,
            unreadMessages,
        )
        unreadNotifications > 0 -> stringResource(
            R.string.notification_summary_notifications_only,
            unreadNotifications,
        )
        else -> stringResource(R.string.notification_summary_messages_only, unreadMessages)
    }
    Row(
        modifier = modifier
            .shadow(
                10.dp,
                shape,
                ambientColor = Color.Black.copy(alpha = 0.1f),
                spotColor = Color.Black.copy(alpha = 0.1f),
            )
            .momentsChromeGlass(shape, interactive = true)
            // 8 dp superiores pertenecen a la cola; el contenido vive sobre
            // el mismo vidrio, sin fondos o cápsulas adicionales.
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 10.dp)
            .semantics { contentDescription = accessibilityLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
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

        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(12.dp),
            tint = primary.copy(alpha = 0.35f),
        )
    }
}

/**
 * Bocadillo anclado al corazón del header.
 *
 * La cola se calcula desde el borde derecho. Como el bocadillo también se
 * alinea desde ese borde, la punta conserva su posición aunque cambie el ancho
 * por contadores de una, dos o tres cifras.
 */
private object NotificationSummaryBubbleShape : Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = with(density) {
        val tailHeight = 8.dp.toPx()
        val tailWidth = 14.dp.toPx()
        val halfTail = tailWidth / 2f
        val bodyHeight = (size.height - tailHeight).coerceAtLeast(0f)
        val radius = minOf(24.dp.toPx(), bodyHeight / 2f)
        val trailingInset = 52.dp.toPx()
        val minimumTailX = radius + halfTail
        val maximumTailX = size.width - radius - halfTail
        val tailCenterX = (size.width - trailingInset).coerceIn(minimumTailX, maximumTailX)
        val bodyTop = tailHeight

        val path = Path().apply {
            moveTo(radius, bodyTop)
            lineTo(tailCenterX - halfTail, bodyTop)
            quadraticTo(tailCenterX - halfTail * 0.45f, bodyTop, tailCenterX, 0f)
            quadraticTo(tailCenterX + halfTail * 0.45f, bodyTop, tailCenterX + halfTail, bodyTop)
            lineTo(size.width - radius, bodyTop)
            quadraticTo(size.width, bodyTop, size.width, bodyTop + radius)
            lineTo(size.width, size.height - radius)
            quadraticTo(size.width, size.height, size.width - radius, size.height)
            lineTo(radius, size.height)
            quadraticTo(0f, size.height, 0f, size.height - radius)
            lineTo(0f, bodyTop + radius)
            quadraticTo(0f, bodyTop, radius, bodyTop)
            close()
        }
        Outline.Generic(path)
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
