package com.moments.android.views.settings

import android.app.TimePickerDialog
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.utilities.HapticManager
import kotlinx.coroutines.delay
import java.util.Calendar
import java.util.Date

/**
 * Port 1:1 de `RestModeView.swift` (190 líneas).
 * Horario = `activeHoursStart` / `activeHoursEnd` vía [SettingsViewModel].
 */
@Composable
fun RestModeView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val cardFill = textColor.copy(alpha = 0.06f)
    val accent = SettingsProfileColors.accent(isDark)
    val context = LocalContext.current

    val viewModel = remember { SettingsViewModel() }
    var isRestModeEnabled by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(Date()) }
    var endTime by remember { mutableStateOf(Date()) }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchUserSettings { result ->
            isLoading = false
            result.onSuccess { user ->
                val startStr = user.activeHoursStart
                val endStr = user.activeHoursEnd
                if (!startStr.isNullOrBlank() && !endStr.isNullOrBlank()) {
                    val startDt = runCatching { viewModel.dateFormatter.parse(startStr) }.getOrNull()
                    val endDt = runCatching { viewModel.dateFormatter.parse(endStr) }.getOrNull()
                    if (startDt != null && endDt != null) {
                        isRestModeEnabled = true
                        startTime = startDt
                        endTime = endDt
                        return@onSuccess
                    }
                }
                isRestModeEnabled = false
            }.onFailure {
                isRestModeEnabled = false
            }
        }
    }

    fun saveSettings() {
        if (isSaving) return
        isSaving = true
        if (isRestModeEnabled) {
            viewModel.updateActiveHours(startTime, endTime) { error ->
                isSaving = false
                if (error != null) {
                    HapticManager.shared.error()
                } else {
                    HapticManager.shared.success()
                    onNavigateBack()
                }
            }
        } else {
            viewModel.clearActiveHours()
            // ≡ iOS: delay 0.5s porque clearActiveHours no tiene completion
            // (lanzamos desde LaunchedEffect-style coroutine via remember + mutable)
        }
    }

    // Clear path needs delay — use LaunchedEffect keyed on a save-clear flag
    var pendingClearSave by remember { mutableStateOf(false) }
    LaunchedEffect(pendingClearSave) {
        if (!pendingClearSave) return@LaunchedEffect
        viewModel.clearActiveHours()
        delay(500)
        isSaving = false
        pendingClearSave = false
        HapticManager.shared.success()
        onNavigateBack()
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.user_activity_time_spent_rest_mode_title),
        onNavigateBack = onNavigateBack,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    stringResource(R.string.user_activity_time_spent_rest_mode_desc_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Text(
                    stringResource(R.string.user_activity_time_spent_rest_mode_desc_body),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp,
                )
            }

            if (isLoading) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = textColor)
                }
            } else {
                Spacer(Modifier.height(24.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .background(cardFill, RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.settings_notifications_schedule_enable),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = isRestModeEnabled,
                        onCheckedChange = { isRestModeEnabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = SettingsProfileColors.toggleTint,
                            uncheckedThumbColor = Color.White,
                            uncheckedTrackColor = Color.Gray.copy(alpha = 0.35f),
                        ),
                    )
                }

                AnimatedVisibility(
                    visible = isRestModeEnabled,
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut(),
                ) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 24.dp)
                            .background(cardFill, RoundedCornerShape(16.dp)),
                    ) {
                        RestModeTimeRow(
                            label = stringResource(R.string.settings_notifications_schedule_start),
                            timeLabel = viewModel.dateFormatter.format(startTime),
                            textColor = textColor,
                            onClick = {
                                showRestModeTimePicker(context, startTime) { startTime = it }
                            },
                        )
                        HorizontalDivider(
                            Modifier.padding(horizontal = 16.dp),
                            color = Color.Gray.copy(alpha = 0.3f),
                        )
                        RestModeTimeRow(
                            label = stringResource(R.string.settings_notifications_schedule_end),
                            timeLabel = viewModel.dateFormatter.format(endTime),
                            textColor = textColor,
                            onClick = {
                                showRestModeTimePicker(context, endTime) { endTime = it }
                            },
                        )
                    }
                }

                Spacer(Modifier.height(40.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(16.dp),
                            ambientColor = accent.copy(alpha = 0.2f),
                            spotColor = accent.copy(alpha = 0.2f),
                        )
                        .background(accent, RoundedCornerShape(16.dp))
                        .clickable(enabled = !isSaving) {
                            if (isRestModeEnabled) {
                                saveSettings()
                            } else {
                                isSaving = true
                                pendingClearSave = true
                            }
                        }
                        .padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .size(18.dp)
                                .padding(end = 4.dp),
                            color = SettingsProfileColors.accentContrastingText(isDark),
                            strokeWidth = 2.dp,
                        )
                    }
                    Text(
                        stringResource(R.string.settings_schedule_save),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SettingsProfileColors.accentContrastingText(isDark),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun RestModeTimeRow(
    label: String,
    timeLabel: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
            modifier = Modifier.weight(1f),
        )
        Text(
            timeLabel,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = textColor,
        )
    }
}

private fun showRestModeTimePicker(
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
