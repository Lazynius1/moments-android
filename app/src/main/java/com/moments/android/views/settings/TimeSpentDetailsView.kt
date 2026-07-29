package com.moments.android.views.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port 1:1 de `TimeSpentDetailsView.swift` (95 líneas).
 * Cabecera, [TimeSpentCardView], filas a DailyLimit / RestMode; PTR ≡ `.momentRefresh` 400 ms.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeSpentDetailsView(
    onNavigateBack: () -> Unit = {},
    onOpenDailyLimit: () -> Unit = {},
    onOpenRestMode: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val primary = SettingsProfileColors.accent(isDark)
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.user_activity_time_spent_nav_title),
        onNavigateBack = onNavigateBack,
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    // ≡ iOS momentRefresh { sleep 400_000_000 }
                    delay(400)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        stringResource(R.string.user_activity_time_spent_details_title),
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Bold,
                        color = primary,
                    )
                    Text(
                        stringResource(R.string.user_activity_time_spent_details_subtitle),
                        fontSize = 14.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp,
                    )
                }

                TimeSpentCardView(Modifier.padding(horizontal = 16.dp))

                Column(
                    Modifier
                        .padding(horizontal = 16.dp)
                        .padding(top = 16.dp),
                ) {
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

/** ≡ iOS `TimeSpentSettingsRow`. */
@Composable
private fun TimeSpentSettingsRow(
    title: String,
    subtitle: String,
    primary: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Medium, color = primary)
            Text(subtitle, fontSize = 13.sp, color = Color.Gray)
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color.Gray.copy(alpha = 0.8f),
            modifier = Modifier.size(14.dp),
        )
    }
}
