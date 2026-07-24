package com.moments.android.views.settings.settingssections

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.NotificationType
import com.moments.android.views.settings.SettingsViewModel

/**
 * Mirror 1:1 de `NotificationSettingsView.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsView(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f)

    var isScheduleEnabled by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.conversation_settings_preferences),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                // Section 1: Schedule
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "HORARIO DE SILENCIO",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryColor
                    )

                    NotificationToggleRow(
                        title = "Programar descanso",
                        checked = isScheduleEnabled,
                        textColor = textColor,
                        onCheckedChange = { enabled ->
                            isScheduleEnabled = enabled
                            if (!enabled) {
                                viewModel.clearActiveHours()
                            }
                        }
                    )
                }

                HorizontalDivider(color = secondaryColor.copy(alpha = 0.15f))

                // Section 2: Notification types
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "TIPOS DE NOTIFICACIÓN",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryColor
                    )

                    val notificationTypes = listOf(
                        NotificationType.LIKE to "Me gusta",
                        NotificationType.NEW_FOLLOWER to "Nuevos seguidores",
                        NotificationType.FOLLOW_REQUEST to "Solicitudes de seguimiento",
                        NotificationType.MUTUAL_CONNECTION to "Conexiones mutuas",
                        NotificationType.COMMENT to "Comentarios",
                        NotificationType.STORY_REACTION to "Reacciones a historias"
                    )

                    notificationTypes.forEach { (type, label) ->
                        val isEnabled = viewModel.notificationPreferences[type.raw] ?: true
                        NotificationToggleRow(
                            title = label,
                            checked = isEnabled,
                            textColor = textColor,
                            onCheckedChange = { checked ->
                                viewModel.updateNotificationPreference(type.raw, checked)
                            }
                        )
                    }
                }

                HorizontalDivider(color = secondaryColor.copy(alpha = 0.15f))

                // Section 3: Advanced
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "AJUSTES AVANZADOS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = secondaryColor
                    )

                    val mutualsOnly = viewModel.notificationPreferences["commentsMutualsOnly"] ?: false
                    NotificationToggleRow(
                        title = "Solo comentarios de mutuos",
                        checked = mutualsOnly,
                        textColor = textColor,
                        onCheckedChange = { checked ->
                            viewModel.updateNotificationPreference("commentsMutualsOnly", checked)
                        }
                    )

                    val muteOldReactions = viewModel.notificationPreferences["muteOldPostReactions"] ?: false
                    NotificationToggleRow(
                        title = "Silenciar reacciones en publicaciones antiguas",
                        checked = muteOldReactions,
                        textColor = textColor,
                        onCheckedChange = { checked ->
                            viewModel.updateNotificationPreference("muteOldPostReactions", checked)
                        }
                    )

                    Text(
                        text = "Evita avisos de reacciones en publicaciones de más de 30 días.",
                        fontSize = 12.sp,
                        color = secondaryColor
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    checked: Boolean,
    textColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.weight(1f)
        )

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF34C759)
            )
        )
    }
}
