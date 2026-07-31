package com.moments.android.views.settings.settingssections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.OnlineStatus
import com.moments.android.services.messaging.OnlineStatusService
import com.moments.android.views.settings.sections.SettingsIconSlotWidth
import com.moments.android.views.settings.sections.SettingsIconTextSpacing
import com.moments.android.views.settings.sections.SettingsRowHorizontalPadding

/**
 * Port de `OnlineStatusSection.swift`.
 */
@Composable
fun OnlineStatusSection(
    onlineStatusService: OnlineStatusService = OnlineStatusService.shared,
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val currentStatus by onlineStatusService.currentUserStatus.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    val statusLabel = stringResource(currentStatus.displayNameRes())
    val statusColor = Color(currentStatus.colorArgb)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(
                horizontal = SettingsRowHorizontalPadding,
                vertical = 11.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = currentStatus.materialIcon(),
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier
                .width(SettingsIconSlotWidth)
                .size(19.dp),
        )
        Spacer(Modifier.width(SettingsIconTextSpacing))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                stringResource(R.string.settings_online_status_title),
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
            )
            Text(
                stringResource(R.string.settings_online_status_current, statusLabel),
                fontSize = 12.sp,
                color = Color.Gray,
            )
        }

        Box {
            Row(
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(textColor.copy(alpha = 0.08f))
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.settings_online_status_select),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                )
                Icon(
                    Icons.Filled.UnfoldMore,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(10.dp),
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                OnlineStatus.entries.forEach { status ->
                    DropdownMenuItem(
                        text = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    status.materialIcon(),
                                    contentDescription = null,
                                    tint = Color(status.colorArgb),
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(stringResource(status.displayNameRes()))
                            }
                        },
                        onClick = {
                            onlineStatusService.setGlobalStatus(status)
                            menuExpanded = false
                        },
                    )
                }
            }
        }
    }
}

private fun OnlineStatus.displayNameRes(): Int = when (this) {
    OnlineStatus.ONLINE -> R.string.online_status_online
    OnlineStatus.AWAY -> R.string.online_status_away
    OnlineStatus.BUSY -> R.string.online_status_busy
    OnlineStatus.OFFLINE -> R.string.online_status_offline
    OnlineStatus.INVISIBLE -> R.string.online_status_invisible
}

private fun OnlineStatus.materialIcon(): ImageVector = when (this) {
    OnlineStatus.ONLINE -> Icons.Filled.Circle
    OnlineStatus.AWAY -> Icons.Filled.NightsStay
    OnlineStatus.BUSY -> Icons.Filled.Error
    OnlineStatus.OFFLINE -> Icons.Filled.RadioButtonUnchecked
    OnlineStatus.INVISIBLE -> Icons.Filled.VisibilityOff
}
