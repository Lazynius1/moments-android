package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.DoNotDisturbOn
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.models.AppUser
import com.moments.android.models.OnlineStatus
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.shared.MomentsModalSheet

/**
 * Port de `MessagingComposerAndStatusViews.swift` — compositor de mensaje nuevo
 * y selector de estado online (sheet medium+large).
 */

@Composable
fun MessageComposerView(
    selectedUser: AppUser?,
    messageText: String,
    onMessageTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    val canSend = messageText.trim().isNotEmpty()
    val cardFill = if (colors.isDark) Color.White.copy(alpha = 0.08f) else Color.White
    val fieldShape = RoundedCornerShape(16.dp)

    Box(
        modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF007AFF).copy(alpha = 0.1f), Color(0xFF02C39A).copy(alpha = 0.1f)),
                ),
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.common_cancel),
                    color = colors.accent,
                    fontSize = 17.sp,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 8.dp),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.messaging_compose_title),
                    color = colors.primary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.weight(1f))
                // Equilibra el título centrado respecto a Cancel.
                Spacer(Modifier.width(64.dp))
            }

            selectedUser?.let { user ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    AsyncProfileImageView(
                        userId = user.id,
                        modifier = Modifier
                            .size(60.dp)
                            .clip(CircleShape),
                    )
                    Text(
                        user.username,
                        color = colors.primary,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        stringResource(R.string.messaging_write_message_to_start),
                        color = colors.secondary,
                        fontSize = 16.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Column(
                Modifier.padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                BasicTextField(
                    value = messageText,
                    onValueChange = onMessageTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                        .clip(fieldShape)
                        .background(cardFill)
                        .border(1.dp, colors.secondary.copy(alpha = 0.3f), fieldShape)
                        .padding(16.dp),
                    textStyle = TextStyle(color = colors.primary, fontSize = 16.sp),
                    cursorBrush = SolidColor(colors.accent),
                    maxLines = 6,
                    decorationBox = { inner ->
                        Box {
                            if (messageText.isEmpty()) {
                                Text(
                                    stringResource(R.string.messaging_compose_placeholder),
                                    color = colors.secondary.copy(alpha = 0.7f),
                                    fontSize = 16.sp,
                                )
                            }
                            inner()
                        }
                    },
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canSend) Color(0xFF007AFF) else colors.secondary)
                        .clickable(enabled = canSend, onClick = onSend)
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.messaging_send_message),
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Port de `OnlineStatusSelectorView`.
 * `.presentationDetents([.medium, .large])` → [MomentsModalSheet] `largeOnly = false`.
 */
@Composable
fun OnlineStatusSelectorView(
    currentStatus: OnlineStatus,
    onStatusSelected: (OnlineStatus) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val colors = AdaptiveColors(isDark)

    MomentsModalSheet(onDismissRequest = onDismiss, largeOnly = false) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.messaging_status_current),
                    color = colors.secondary,
                    fontSize = 14.sp,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        Icons.Default.Circle,
                        contentDescription = null,
                        tint = currentStatus.composeColor,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        stringResource(currentStatus.displayNameRes),
                        color = colors.primary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            OnlineStatus.entries.forEachIndexed { index, status ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            if (status == currentStatus) {
                                (if (isDark) Color.White else Color.Black).copy(alpha = 0.06f)
                            } else {
                                Color.Transparent
                            },
                        )
                        .clickable {
                            onStatusSelected(status)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        status.composeIcon,
                        contentDescription = null,
                        tint = status.composeColor,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(status.displayNameRes),
                        color = colors.primary,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (status == currentStatus) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = colors.accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                if (index < OnlineStatus.entries.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = 64.dp, end = 20.dp),
                        color = (if (isDark) Color.White else Color.Black).copy(alpha = 0.08f),
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

/** Colores/iconos UI ≡ iOS `OnlineStatus.color` / `.icon` (Material). */
private val OnlineStatus.displayNameRes: Int
    get() = when (this) {
        OnlineStatus.ONLINE -> R.string.messaging_status_online
        OnlineStatus.AWAY -> R.string.messaging_status_away
        OnlineStatus.BUSY -> R.string.messaging_status_busy
        OnlineStatus.OFFLINE -> R.string.messaging_status_offline
        OnlineStatus.INVISIBLE -> R.string.messaging_status_invisible
    }

private val OnlineStatus.composeColor: Color
    get() = when (this) {
        OnlineStatus.ONLINE -> Color.Green
        OnlineStatus.AWAY -> Color(0xFFFF9500)
        OnlineStatus.BUSY -> Color(0xFFFF3B30)
        OnlineStatus.OFFLINE, OnlineStatus.INVISIBLE -> Color.Gray
    }

private val OnlineStatus.composeIcon: ImageVector
    get() = when (this) {
        OnlineStatus.ONLINE -> Icons.Default.Circle
        OnlineStatus.AWAY -> Icons.Default.Schedule
        OnlineStatus.BUSY -> Icons.Default.DoNotDisturbOn
        OnlineStatus.OFFLINE -> Icons.Default.Circle
        OnlineStatus.INVISIBLE -> Icons.Default.RemoveRedEye
    }
