package com.moments.android.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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

/**
 * Port de `TimeSpentDetailsView.swift` + `TimeSpentCardView.swift`: cabecera, tarjeta con media
 * diaria y barras de 7 días, y filas a Límite diario / Modo descanso (pantallas ya portadas).
 */
@Composable
fun TimeSpentDetailsView(
    onNavigateBack: () -> Unit = {},
    onOpenDailyLimit: () -> Unit = {},
    onOpenRestMode: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val background = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (isDark) Color.White else Color.Black

    Box(Modifier.fillMaxSize().background(background)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onNavigateBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = primary)
                }
                Text(stringResource(R.string.user_activity_time_spent_nav_title), fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = primary)
            }

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(Modifier.padding(horizontal = 16.dp).padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.user_activity_time_spent_details_title), fontSize = 28.sp, fontWeight = FontWeight.Bold, color = primary)
                    Text(stringResource(R.string.user_activity_time_spent_details_subtitle), fontSize = 14.sp, color = Color.Gray, lineHeight = 20.sp)
                }

                TimeSpentCardView(Modifier.padding(horizontal = 16.dp), primary = primary)

                Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    TimeSpentSettingsRow(
                        title = stringResource(R.string.user_activity_time_spent_daily_limit_title),
                        subtitle = stringResource(R.string.user_activity_time_spent_daily_limit_subtitle),
                        primary = primary,
                        onClick = onOpenDailyLimit,
                    )
                    TimeSpentSettingsRow(
                        title = stringResource(R.string.user_activity_time_spent_rest_mode_title),
                        subtitle = stringResource(R.string.user_activity_time_spent_rest_mode_subtitle),
                        primary = primary,
                        onClick = onOpenRestMode,
                    )
                }
            }
        }
    }
}

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

@Composable
private fun TimeSpentSettingsRow(title: String, subtitle: String, primary: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = primary)
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = Color.Gray.copy(alpha = 0.8f), modifier = Modifier.size(16.dp))
    }
}

private fun formatTime(seconds: Double): String {
    val total = seconds.toInt()
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}
