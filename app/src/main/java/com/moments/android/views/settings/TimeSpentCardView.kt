package com.moments.android.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.max
import com.moments.android.R
import com.moments.android.services.activity.TimeSpentManager
import com.moments.android.utilities.MomentsFormat
import kotlinx.coroutines.delay
import java.util.Date

/**
 * Port 1:1 de `TimeSpentCardView.swift` (99 líneas).
 * Media diaria + barras de los últimos 7 días; refresh al aparecer y cada 60 s.
 */
@Composable
fun TimeSpentCardView(modifier: Modifier = Modifier) {
    val isDark = isSystemInDarkTheme()
    val accent = SettingsProfileColors.accent(isDark)
    val manager = remember { TimeSpentManager.shared }

    var data by remember { mutableStateOf<List<Pair<Date, Double>>>(emptyList()) }
    var average by remember { mutableDoubleStateOf(0.0) }

    fun refreshData() {
        manager.updateCurrentSession()
        data = manager.getLast7DaysData()
        average = manager.getWeeklyAverage()
    }

    LaunchedEffect(Unit) {
        while (true) {
            refreshData()
            delay(60_000)
        }
    }

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    stringResource(R.string.user_activity_time_spent_card_title),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent,
                )
                Text(
                    formatTimeSpent(average),
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                )
                Text(
                    stringResource(R.string.user_activity_time_spent_average),
                    fontSize = 13.sp,
                    color = Color.Gray,
                )
            }
            // ≡ SF Symbol clock.fill
            Icon(
                Icons.Filled.Schedule,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp),
            )
        }

        // Escala: al menos 1 h, o el máximo del periodo si es mayor (≡ iOS)
        val maxSecs = maxOf(data.maxOfOrNull { it.second } ?: 1.0, 3600.0)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            data.forEach { (date, seconds) ->
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.fillMaxWidth().height(100.dp),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        val fraction = (seconds / maxSecs).coerceIn(0.0, 1.0).toFloat()
                        // Mínimo 4 px visibles ≡ iOS `max(height, 4)`
                        val barHeight = max(4.dp, 100.dp * fraction)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(barHeight)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(
                                            accent.copy(alpha = 0.85f),
                                            accent.copy(alpha = 0.45f),
                                        ),
                                    ),
                                ),
                        )
                    }
                    Text(
                        MomentsFormat.smartDate(
                            from = date,
                            context = MomentsFormat.DateContext.WEEKDAY_NARROW,
                        ),
                        fontSize = 11.sp,
                        color = Color.Gray,
                    )
                }
            }
        }
    }
}

/** ≡ iOS `formatTime` — `Xh Ym` o `Ym`. */
private fun formatTimeSpent(seconds: Double): String {
    val total = seconds.toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
