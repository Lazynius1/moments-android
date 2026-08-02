package com.moments.android.views.settings.settingssections

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.NotificationType
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.momentsPress
import com.moments.android.views.settings.SettingsProfileColors
import com.moments.android.views.settings.SettingsSubsectionWrapper
import com.moments.android.views.settings.SettingsViewModel
import com.moments.android.views.settings.sections.SettingsSubsectionGroup
import com.moments.android.views.settings.settingsToggleCases
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Port de `NotificationSettingsView.swift`.
 */
@Composable
fun NotificationSettingsView(
    viewModel: SettingsViewModel,
    isScheduleEnabled: Boolean,
    onIsScheduleEnabledChange: (Boolean) -> Unit,
    startTime: Date,
    onStartTimeChange: (Date) -> Unit,
    endTime: Date,
    onEndTimeChange: (Date) -> Unit,
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val context = LocalContext.current
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    var isSavingSchedule by remember { mutableStateOf(false) }
    var showSavedSchedule by remember { mutableStateOf(false) }
    var showScheduleError by remember { mutableStateOf(false) }
    var scheduleErrorMessage by remember { mutableStateOf("") }

    LaunchedEffect(showSavedSchedule) {
        if (!showSavedSchedule) return@LaunchedEffect
        delay(2600)
        showSavedSchedule = false
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.settings_notifications),
        onNavigateBack = onNavigateBack,
    ) {
        Box(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                SettingsSubsectionGroup(
                    title = stringResource(R.string.settings_notifications_schedule_title),
                ) {
                    NotificationToggleRow(
                        title = stringResource(R.string.settings_notifications_schedule_enable),
                        checked = isScheduleEnabled,
                        primary = primary,
                        onCheckedChange = { enabled ->
                            onIsScheduleEnabledChange(enabled)
                            if (!enabled) viewModel.clearActiveHours()
                        },
                    )
                    if (isScheduleEnabled) {
                        HorizontalDivider(
                            Modifier.padding(start = 16.dp),
                            color = Color.Gray.copy(0.2f),
                            thickness = 0.5.dp,
                        )
                        ScheduleTimeRow(
                            label = stringResource(R.string.settings_notifications_schedule_start),
                            timeLabel = timeFormat.format(startTime),
                            primary = primary,
                            onClick = {
                                showTimePicker(context, startTime, onStartTimeChange)
                            },
                        )
                        HorizontalDivider(
                            Modifier.padding(start = 16.dp),
                            color = Color.Gray.copy(0.2f),
                            thickness = 0.5.dp,
                        )
                        ScheduleTimeRow(
                            label = stringResource(R.string.settings_notifications_schedule_end),
                            timeLabel = timeFormat.format(endTime),
                            primary = primary,
                            onClick = {
                                showTimePicker(context, endTime, onEndTimeChange)
                            },
                        )
                        SaveScheduleButton(
                            isSaving = isSavingSchedule,
                            primary = primary,
                            isDark = isDark,
                            onClick = {
                                if (isSavingSchedule) return@SaveScheduleButton
                                isSavingSchedule = true
                                HapticManager.shared.lightImpact()
                                viewModel.updateActiveHours(startTime, endTime) { error ->
                                    isSavingSchedule = false
                                    if (error != null) {
                                        HapticManager.shared.error()
                                        scheduleErrorMessage =
                                            error.localizedMessage ?: error.toString()
                                        showScheduleError = true
                                    } else {
                                        HapticManager.shared.success()
                                        showSavedSchedule = true
                                    }
                                }
                            },
                        )
                    }
                }

                SettingsSubsectionGroup(
                    title = stringResource(R.string.settings_notifications_types_title),
                ) {
                    val types = NotificationType.settingsToggleCases
                    types.forEachIndexed { index, type ->
                        NotificationToggleRow(
                            title = stringResource(type.settingsDisplayNameRes()),
                            checked = viewModel.notificationPreferences[type.raw] ?: true,
                            primary = primary,
                            onCheckedChange = {
                                viewModel.updateNotificationPreference(type.raw, it)
                            },
                        )
                        if (index < types.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(start = 16.dp),
                                color = Color.Gray.copy(0.2f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                    HorizontalDivider(
                        Modifier.padding(start = 16.dp),
                        color = Color.Gray.copy(0.2f),
                        thickness = 0.5.dp,
                    )
                    NotificationToggleRow(
                        title = stringResource(R.string.settings_notifications_gentle_reminders_title),
                        checked = viewModel.notificationPreferences["gentleReminders"] ?: true,
                        primary = primary,
                        onCheckedChange = {
                            viewModel.updateNotificationPreference("gentleReminders", it)
                        },
                    )
                }

                SettingsSubsectionGroup(
                    title = stringResource(R.string.settings_notifications_advanced_title),
                ) {
                    NotificationToggleRow(
                        title = stringResource(R.string.settings_notifications_mutuals_only),
                        checked = viewModel.notificationPreferences["commentsMutualsOnly"] ?: false,
                        primary = primary,
                        onCheckedChange = {
                            viewModel.updateNotificationPreference("commentsMutualsOnly", it)
                        },
                    )
                    HorizontalDivider(
                        Modifier.padding(start = 16.dp),
                        color = Color.Gray.copy(0.2f),
                        thickness = 0.5.dp,
                    )
                    NotificationToggleRow(
                        title = stringResource(R.string.settings_notifications_mute_old_reactions),
                        checked = viewModel.notificationPreferences["muteOldPostReactions"] ?: false,
                        primary = primary,
                        onCheckedChange = {
                            viewModel.updateNotificationPreference("muteOldPostReactions", it)
                        },
                    )
                    Text(
                        stringResource(R.string.settings_notifications_old_posts_explain),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        stringResource(R.string.settings_notifications_gentle_reminders_description),
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }

                Spacer(Modifier.height(24.dp))
            }

            AnimatedVisibility(
                visible = showSavedSchedule,
                enter = slideInVertically { -40 } + fadeIn(),
                exit = slideOutVertically { -20 } + fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 10.dp, start = 20.dp, end = 20.dp),
            ) {
                Row(
                    Modifier
                        .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = false)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF34C759),
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.settings_schedule_saved),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = primary,
                    )
                }
            }
        }
    }

    if (showScheduleError) {
        AlertDialog(
            onDismissRequest = { showScheduleError = false },
            title = { Text(stringResource(R.string.settings_error_title)) },
            text = { Text(scheduleErrorMessage) },
            confirmButton = {
                TextButton(onClick = { showScheduleError = false }) {
                    Text(stringResource(R.string.settings_ok))
                }
            },
        )
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    checked: Boolean,
    primary: Color,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            fontSize = 14.sp,
            color = primary,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = SettingsProfileColors.toggleTint,
            ),
        )
    }
}

@Composable
private fun ScheduleTimeRow(
    label: String,
    timeLabel: String,
    primary: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .momentsPress(interaction, MomentsPressDefaults.momentsPressSubtle)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, fontSize = 14.sp, color = primary, modifier = Modifier.weight(1f))
        Text(timeLabel, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = primary)
    }
}

@Composable
private fun SaveScheduleButton(
    isSaving: Boolean,
    primary: Color,
    isDark: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val bg = (if (isDark) Color.Black else Color.White).copy(alpha = 0.2f)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .scale(if (isSaving) 0.98f else 1f)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.5.dp, SettingsProfileColors.accentStroke(isDark, 0.5f), RoundedCornerShape(8.dp))
            .momentsPress(interaction, MomentsPressDefaults.momentsPressSubtle)
            .clickable(
                enabled = !isSaving,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSaving) {
            MomentsCircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.settings_schedule_saving),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
            )
        } else {
            Icon(
                Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = primary,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.settings_schedule_save),
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = primary,
            )
        }
    }
}

private fun showTimePicker(
    context: android.content.Context,
    current: Date,
    onPicked: (Date) -> Unit,
) {
    val cal = Calendar.getInstance().apply { time = current }
    TimePickerDialog(
        context,
        { _, hour, minute ->
            val next = Calendar.getInstance().apply {
                time = current
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
            }
            onPicked(next.time)
        },
        cal.get(Calendar.HOUR_OF_DAY),
        cal.get(Calendar.MINUTE),
        true,
    ).show()
}

private fun NotificationType.settingsDisplayNameRes(): Int = when (this) {
    NotificationType.LIKE -> R.string.notification_type_like
    NotificationType.NEW_FOLLOWER -> R.string.notification_type_new_follower
    NotificationType.FOLLOW_REQUEST -> R.string.notification_type_follow_request
    NotificationType.MUTUAL_CONNECTION -> R.string.notification_type_mutual_connection
    NotificationType.COMMENT -> R.string.notification_type_comment
    NotificationType.STORY_REACTION -> R.string.notification_type_story_reaction
    else -> R.string.notification_type_like
}
