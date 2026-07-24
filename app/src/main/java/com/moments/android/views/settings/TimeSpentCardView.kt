package com.moments.android.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.moments.android.R
import com.moments.android.services.activity.TimeSpentManager
import com.moments.android.utilities.MomentsFormat
import kotlinx.coroutines.delay
import java.util.Date

/** Port de `TimeSpentCardView.swift`: tarjeta con media diaria y barras de los últimos 7 días. */
@Composable
fun TimeSpentCardView(modifier: Modifier = Modifier, primary: Color) {
    val manager = remember { TimeSpentManager.shared }
    var data by remember { mutableStateOf<List<Pair<Date, Double>>>(emptyList()) }
    var average by remember { mutableStateOf(0.0) }

    LaunchedEffect(Unit) {
        while (true) {
            manager.updateCurrentSession()
            data = manager.getLast7DaysData()
            average = manager.getWeeklyAverage()
            delay(60_000)
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.user_activity_time_spent_card_title), fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = primary)
                Text(formatTime(average), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = primary)
                Text(stringResource(R.string.user_activity_time_spent_average), fontSize = 13.sp, color = Color.Gray)
            }
            Icon(Icons.Filled.Schedule, null, tint = primary, modifier = Modifier.size(20.dp))
        }

        val maxSecs = maxOf(data.maxOfOrNull { it.second } ?: 1.0, 3600.0)
        Row(Modifier.fillMaxWidth().height(124.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
            data.forEach { (date, seconds) ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.BottomCenter) {
                        val fraction = (seconds / maxSecs).coerceIn(0.0, 1.0).toFloat()
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    Brush.verticalGradient(
                                        listOf(primary.copy(alpha = 0.85f), primary.copy(alpha = 0.45f)),
                                    ),
                                ),
                        )
                    }
                    Text(
                        MomentsFormat.smartDate(from = date, context = MomentsFormat.DateContext.WEEKDAY_NARROW),
                        fontSize = 11.sp,
                        color = Color.Gray,
                    )
                }
            }
        }
    }
}

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
