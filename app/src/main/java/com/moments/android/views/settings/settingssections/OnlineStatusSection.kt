package com.moments.android.views.settings.settingssections

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.models.OnlineStatus
import com.moments.android.services.messaging.OnlineStatusService

/**
 * Mirror 1:1 de `OnlineStatusSection.swift`.
 */
@Composable
fun OnlineStatusSection(
    onlineStatusService: OnlineStatusService = OnlineStatusService.shared
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    val currentStatus by onlineStatusService.currentUserStatus.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }

    val statusLabel = when (currentStatus) {
        OnlineStatus.ONLINE -> "En línea"
        OnlineStatus.AWAY -> "Ausente"
        OnlineStatus.BUSY -> "Ocupado"
        OnlineStatus.INVISIBLE -> "Invisible"
        OnlineStatus.OFFLINE -> "Desconectado"
    }

    val statusColor = when (currentStatus) {
        OnlineStatus.ONLINE -> Color(0xFF34C759)
        OnlineStatus.AWAY -> Color(0xFFFF9500)
        OnlineStatus.BUSY -> Color(0xFFFF3B30)
        OnlineStatus.INVISIBLE, OnlineStatus.OFFLINE -> Color(0xFF8E8E93)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(statusColor)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Estado de presencia",
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            Text(
                text = "Estado actual: $statusLabel",
                fontSize = 12.sp,
                color = secondaryColor
            )
        }

        Box {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(textColor.copy(alpha = 0.08f))
                    .clickable { menuExpanded = true }
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = statusLabel,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "Expand",
                    tint = textColor,
                    modifier = Modifier.size(18.dp)
                )
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                val options = listOf(
                    OnlineStatus.ONLINE to "En línea",
                    OnlineStatus.AWAY to "Ausente",
                    OnlineStatus.BUSY to "Ocupado",
                    OnlineStatus.INVISIBLE to "Invisible"
                )
                options.forEach { (status, label) ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        onClick = {
                            onlineStatusService.setStatus(status)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}
