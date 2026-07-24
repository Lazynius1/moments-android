package com.moments.android.views.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R

/**
 * Port de `TimeSpentDetailsView.swift`: cabecera, tarjeta de tiempo de uso ([TimeSpentCardView],
 * archivo aparte) y filas a Límite diario / Modo descanso (pantallas ya portadas).
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
