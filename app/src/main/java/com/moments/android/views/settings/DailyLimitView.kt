package com.moments.android.views.settings

import android.graphics.Paint
import android.widget.EditText
import android.widget.NumberPicker
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.moments.android.R
import com.moments.android.services.activity.TimeSpentManager
import com.moments.android.utilities.HapticManager
import com.moments.android.views.permission.shared.PermissionPrimerGate
import com.moments.android.views.permission.shared.PermissionPrimerGateHost
import kotlin.math.abs

/**
 * Port 1:1 de `DailyLimitView.swift` (168 líneas).
 */
@Composable
fun DailyLimitView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val cardFill = textColor.copy(alpha = 0.06f)
    val accent = SettingsProfileColors.accent(isDark)
    val context = LocalContext.current

    val timeSpentManager = remember { TimeSpentManager.shared }
    val dailyLimitSeconds by timeSpentManager.dailyLimitSeconds.collectAsState()
    val notificationGate = remember { PermissionPrimerGate(PermissionPrimerGate.Kind.NOTIFICATIONS) }

    var isLimitEnabled by remember { mutableStateOf(false) }
    var selectedHours by remember { mutableIntStateOf(1) }
    var selectedMinutes by remember { mutableIntStateOf(0) }
    var didLoad by remember { mutableStateOf(false) }

    val hoursOffset = remember { (0..23).toList() }
    val minutesOffset = remember { (0 until 60 step 5).toList() }

    LaunchedEffect(dailyLimitSeconds, didLoad) {
        if (didLoad) return@LaunchedEffect
        val limit = dailyLimitSeconds
        if (limit != null) {
            isLimitEnabled = true
            val total = limit.toInt()
            selectedHours = total / 3600
            val rawMinutes = (total % 3600) / 60
            selectedMinutes = minutesOffset.minByOrNull { abs(it - rawMinutes) } ?: 0
        } else {
            isLimitEnabled = false
            selectedHours = 1
            selectedMinutes = 0
        }
        didLoad = true
    }

    fun saveSettings() {
        if (isLimitEnabled) {
            var hours = selectedHours
            var minutes = selectedMinutes
            if (hours == 0 && minutes == 0) {
                minutes = 5
                selectedMinutes = 5
            }
            val totalSeconds = (hours * 3600 + minutes * 60).toDouble()
            timeSpentManager.setDailyLimit(totalSeconds)
            notificationGate.requestAccess(context) {}
        } else {
            timeSpentManager.setDailyLimit(null)
        }
        HapticManager.shared.success()
        onNavigateBack()
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.user_activity_time_spent_daily_limit_title),
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
            ) {
                Text(
                    text = stringResource(R.string.user_activity_time_spent_daily_limit_desc_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = textColor,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.user_activity_time_spent_daily_limit_desc_body),
                    fontSize = 14.sp,
                    color = Color.Gray,
                    lineHeight = 20.sp,
                )
            }

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
                    text = stringResource(R.string.user_activity_time_spent_daily_limit_toggle),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = textColor,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = isLimitEnabled,
                    onCheckedChange = { isLimitEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = SettingsProfileColors.toggleTint,
                        uncheckedThumbColor = Color.White,
                        uncheckedTrackColor = Color.Gray.copy(alpha = 0.35f),
                    ),
                )
            }

            AnimatedVisibility(
                visible = isLimitEnabled,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 24.dp)
                        .background(cardFill, RoundedCornerShape(16.dp))
                        .padding(bottom = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.user_activity_time_spent_daily_limit_picker_title),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = textColor,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 16.dp, bottom = 8.dp),
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(horizontal = 8.dp),
                    ) {
                        DailyLimitWheelPicker(
                            values = hoursOffset,
                            selected = selectedHours,
                            label = { "$it h" },
                            textColor = textColor,
                            modifier = Modifier.weight(1f),
                            onSelected = { selectedHours = it },
                        )
                        DailyLimitWheelPicker(
                            values = minutesOffset,
                            selected = selectedMinutes,
                            label = { "$it min" },
                            textColor = textColor,
                            modifier = Modifier.weight(1f),
                            onSelected = { selectedMinutes = it },
                        )
                    }
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = stringResource(R.string.settings_schedule_save),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = SettingsProfileColors.accentContrastingText(isDark),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .shadow(
                        elevation = 8.dp,
                        shape = RoundedCornerShape(16.dp),
                        ambientColor = accent.copy(alpha = 0.2f),
                        spotColor = accent.copy(alpha = 0.2f),
                    )
                    .background(accent, RoundedCornerShape(16.dp))
                    .clickable(onClick = ::saveSettings)
                    .padding(vertical = 16.dp),
            )
        }
    }

    PermissionPrimerGateHost(gate = notificationGate)
}

@Composable
private fun DailyLimitWheelPicker(
    values: List<Int>,
    selected: Int,
    label: (Int) -> String,
    textColor: Color,
    modifier: Modifier = Modifier,
    onSelected: (Int) -> Unit,
) {
    val labels = remember(values) { values.map(label).toTypedArray() }
    val selectedIndex = values.indexOf(selected).coerceAtLeast(0)
    val argb = textColor.toArgb()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { ctx ->
            NumberPicker(ctx).apply {
                minValue = 0
                maxValue = values.lastIndex
                displayedValues = labels
                wrapSelectorWheel = false
                value = selectedIndex
                setOnValueChangedListener { _, _, newVal ->
                    onSelected(values[newVal])
                }
                runCatching {
                    val field = NumberPicker::class.java.getDeclaredField("mSelectorWheelPaint")
                    field.isAccessible = true
                    (field.get(this) as? Paint)?.color = argb
                    for (i in 0 until childCount) {
                        val child = getChildAt(i)
                        if (child is EditText) child.setTextColor(argb)
                    }
                    invalidate()
                }
            }
        },
        update = { picker ->
            if (picker.value != selectedIndex) {
                picker.value = selectedIndex
            }
        },
    )
}
