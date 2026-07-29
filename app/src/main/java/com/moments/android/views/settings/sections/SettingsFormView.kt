package com.moments.android.views.settings.sections

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.views.settings.SettingsProfileColors
import com.moments.android.views.settings.SettingsRoute
import com.moments.android.views.settings.SettingsViewModel
import com.moments.android.views.settings.settingssections.OnlineStatusSection
import java.util.Date
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `SettingsFormView` (SettingsSections.swift) — formulario real de Ajustes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("UNUSED_PARAMETER")
fun SettingsFormView(
    viewModel: SettingsViewModel,
    isPrivate: Boolean,
    onIsPrivateChange: (Boolean) -> Unit,
    showFollowing: Boolean,
    onShowFollowingChange: (Boolean) -> Unit,
    showFollowers: Boolean,
    onShowFollowersChange: (Boolean) -> Unit,
    isScheduleEnabled: Boolean,
    onIsScheduleEnabledChange: (Boolean) -> Unit,
    startTime: Date,
    onStartTimeChange: (Date) -> Unit,
    endTime: Date,
    onEndTimeChange: (Date) -> Unit,
    username: String,
    onUsernameChange: (String) -> Unit,
    email: String,
    onEmailChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit,
    onShowPersonalInfo: () -> Unit,
    onShowQRCode: () -> Unit,
    onRoute: (SettingsRoute) -> Unit,
    onShowAdvancedAccountManagement: () -> Unit,
    onShowNovaMemory: () -> Unit,
    showReadReceipts: Boolean,
    onShowReadReceiptsChange: (Boolean) -> Unit,
    blockedAccountsCount: Int,
    onNavigateBack: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val canvas = SettingsProfileColors.canvas(isDark)
    val primary = SettingsProfileColors.accent(isDark)
    var animateSections by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        animateSections = true
    }

    Scaffold(
        containerColor = canvas,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.settings_main_title),
                        fontWeight = FontWeight.Bold,
                        color = primary,
                        fontSize = 18.sp,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = canvas),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            AnimatedSettingsBlock(visible = animateSections, delayMs = 0) {
                SettingsGroup(title = stringResource(R.string.settings_group_account)) {
                    ProfileSection(username = username)
                    AccountSection(
                        onShowPersonalInfo = onShowPersonalInfo,
                        onShowQRCode = onShowQRCode,
                    )
                }
            }
            AnimatedSettingsBlock(visible = animateSections, delayMs = 100) {
                SettingsGroup(title = stringResource(R.string.settings_group_security)) {
                    SecuritySection(onRoute = onRoute)
                }
            }
            AnimatedSettingsBlock(visible = animateSections, delayMs = 150) {
                SettingsGroup(title = stringResource(R.string.settings_group_privacy)) {
                    PrivacySection(
                        isPrivate = isPrivate,
                        onIsPrivateChange = onIsPrivateChange,
                        showFollowing = showFollowing,
                        showFollowers = showFollowers,
                        viewModel = viewModel,
                        onRoute = onRoute,
                        showReadReceipts = showReadReceipts,
                        onShowReadReceiptsChange = onShowReadReceiptsChange,
                        blockedAccountsCount = blockedAccountsCount,
                    )
                }
            }
            AnimatedSettingsBlock(visible = animateSections, delayMs = 200) {
                SettingsGroup(title = stringResource(R.string.settings_group_content)) {
                    ActivitySection(onRoute = onRoute, onShowNovaMemory = onShowNovaMemory)
                    ArchiveSection(onRoute = onRoute)
                }
            }
            AnimatedSettingsBlock(visible = animateSections, delayMs = 250) {
                SettingsGroup(title = stringResource(R.string.settings_group_notifications)) {
                    NotificationsSection(
                        viewModel = viewModel,
                        isScheduleEnabled = isScheduleEnabled,
                        onIsScheduleEnabledChange = onIsScheduleEnabledChange,
                        startTime = startTime,
                        onStartTimeChange = onStartTimeChange,
                        endTime = endTime,
                        onEndTimeChange = onEndTimeChange,
                        onRoute = onRoute,
                    )
                    OnlineStatusSection()
                }
            }
            AnimatedSettingsBlock(visible = animateSections, delayMs = 300) {
                SettingsGroup(title = stringResource(R.string.settings_group_data)) {
                    DataSection(onRoute = onRoute)
                }
            }
            AnimatedSettingsBlock(visible = animateSections, delayMs = 350) {
                SettingsGroup(title = stringResource(R.string.settings_group_support)) {
                    HelpSection(onRoute = onRoute)
                }
            }
            AnimatedSettingsBlock(visible = animateSections, delayMs = 400) {
                SettingsGroup(title = stringResource(R.string.settings_group_advanced)) {
                    AdvancedAccountSection(onShowAdvancedAccountManagement = onShowAdvancedAccountManagement)
                    LogoutSection(onNavigateBack = onNavigateBack)
                }
            }
            AnimatedSettingsBlock(visible = animateSections, delayMs = 500) {
                SettingsVersionFooter()
            }
        }
    }
}

@Composable
private fun AnimatedSettingsBlock(
    visible: Boolean,
    delayMs: Int,
    content: @Composable () -> Unit,
) {
    val alpha = remember { Animatable(0f) }
    val offsetY = remember { Animatable(20f) }
    val duration = ((MotionPolicy.Spring.ONBOARDING_RESPONSE) * 1000).toInt().coerceAtLeast(300)

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        delay(delayMs.toLong())
        launch {
            alpha.animateTo(1f, tween(durationMillis = duration, easing = EaseOut))
        }
        launch {
            offsetY.animateTo(0f, tween(durationMillis = duration, easing = EaseOut))
        }
    }

    Column(
        Modifier
            .alpha(alpha.value)
            .offset { IntOffset(0, offsetY.value.roundToInt()) },
    ) {
        content()
    }
}
