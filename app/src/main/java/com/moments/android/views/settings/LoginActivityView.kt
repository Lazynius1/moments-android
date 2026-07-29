package com.moments.android.views.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.models.LoginSession
import com.moments.android.utilities.MomentsFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port 1:1 de `LoginActivityView.swift` (606 líneas).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginActivityView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val descColor = if (isDark) Color.White.copy(0.58f) else Color.Black.copy(0.52f)
    val viewModel = remember { LoginActivityViewModel() }
    val scope = rememberCoroutineScope()

    var isLoading by remember { mutableStateOf(true) }
    var isRefreshing by remember { mutableStateOf(false) }

    val notAuth = stringResource(R.string.login_activity_error_not_authenticated)
    val loadErrorPrefix = stringResource(R.string.login_activity_load_error_prefix)
    val refreshErrorPrefix = stringResource(R.string.login_activity_refresh_error_prefix)
    val logoutErrorFormat = stringResource(R.string.login_activity_logout_session_error)
    val singleSuccess = stringResource(R.string.login_activity_logout_success_single_message)
    val allSuccess = stringResource(R.string.login_activity_logout_success_message)
    val logoutAllErrorPrefix = stringResource(R.string.login_activity_logout_all_error_prefix)

    LaunchedEffect(Unit) {
        viewModel.loadLoginActivity(notAuth, loadErrorPrefix) { isLoading = false }
        delay(8_000)
        if (isLoading) isLoading = false
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.login_activity_navigation_title),
        onNavigateBack = onNavigateBack,
    ) {
        Box(Modifier.fillMaxSize()) {
            if (isLoading) {
                Column(
                    Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(color = textColor)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.login_activity_loading),
                        fontSize = 16.sp,
                        color = Color.Gray,
                    )
                }
            } else {
                PullToRefreshBox(
                    isRefreshing = isRefreshing,
                    onRefresh = {
                        scope.launch {
                            isRefreshing = true
                            viewModel.refreshLoginActivity(notAuth, refreshErrorPrefix)
                            isRefreshing = false
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(top = 8.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        Text(
                            stringResource(R.string.login_activity_description),
                            fontSize = 13.sp,
                            color = descColor,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )

                        Column(
                            Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Text(
                                stringResource(R.string.login_activity_current_session),
                                fontSize = 16.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = textColor,
                            )
                            CurrentSessionCard(
                                session = viewModel.currentSession,
                                textColor = textColor,
                                onLogout = viewModel.currentSession?.let { session ->
                                    { viewModel.requestLogout(session) }
                                },
                            )
                        }

                        Column(
                            Modifier.padding(horizontal = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    stringResource(R.string.login_activity_other_sessions),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = textColor,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    stringResource(R.string.login_activity_logout_all),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFFFF3B30),
                                    modifier = Modifier.clickable {
                                        viewModel.showLogoutAllAlert = true
                                    },
                                )
                            }

                            if (viewModel.otherSessions.isEmpty()) {
                                Column(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 40.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    Icon(
                                        Icons.Default.Computer,
                                        contentDescription = null,
                                        tint = Color.Gray,
                                        modifier = Modifier.size(40.dp),
                                    )
                                    Text(
                                        stringResource(R.string.login_activity_no_other_sessions),
                                        fontSize = 16.sp,
                                        color = Color.Gray,
                                    )
                                }
                            } else {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    viewModel.otherSessions.forEach { session ->
                                        SessionCard(
                                            session = session,
                                            textColor = textColor,
                                            isDark = isDark,
                                            onLogout = { viewModel.requestLogout(session) },
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }

    if (viewModel.showLogoutAllAlert) {
        AlertDialog(
            onDismissRequest = { viewModel.showLogoutAllAlert = false },
            title = { Text(stringResource(R.string.login_activity_logout_all_title)) },
            text = { Text(stringResource(R.string.login_activity_logout_all_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.showLogoutAllAlert = false
                        viewModel.logoutAllSessions(notAuth, logoutAllErrorPrefix, allSuccess)
                    },
                ) {
                    Text(
                        stringResource(R.string.login_activity_logout_all_confirm),
                        color = Color(0xFFFF3B30),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showLogoutAllAlert = false }) {
                    Text(stringResource(R.string.login_activity_cancel))
                }
            },
        )
    }

    if (viewModel.showError) {
        AlertDialog(
            onDismissRequest = { viewModel.showError = false },
            title = { Text(stringResource(R.string.login_activity_error_title)) },
            text = { Text(viewModel.errorMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.showError = false }) {
                    Text(stringResource(R.string.login_activity_ok))
                }
            },
        )
    }

    if (viewModel.showLogoutSuccess) {
        AlertDialog(
            onDismissRequest = { viewModel.showLogoutSuccess = false },
            title = { Text(stringResource(R.string.login_activity_logout_success_title)) },
            text = { Text(viewModel.logoutSuccessMessage) },
            confirmButton = {
                TextButton(onClick = { viewModel.showLogoutSuccess = false }) {
                    Text(stringResource(R.string.login_activity_ok))
                }
            },
        )
    }

    viewModel.sessionPendingLogout?.let { session ->
        AlertDialog(
            onDismissRequest = { viewModel.sessionPendingLogout = null },
            title = { Text(stringResource(R.string.login_activity_logout_session_title)) },
            text = {
                Text(
                    if (viewModel.isCurrentDeviceSession(session)) {
                        stringResource(R.string.login_activity_logout_session_current_message)
                    } else {
                        stringResource(R.string.login_activity_logout_session_message, session.device)
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.confirmLogoutPendingSession(
                            notAuth,
                            logoutErrorFormat,
                            singleSuccess,
                        )
                    },
                ) {
                    Text(
                        stringResource(R.string.login_activity_logout_session_confirm),
                        color = Color(0xFFFF3B30),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.sessionPendingLogout = null }) {
                    Text(stringResource(R.string.login_activity_cancel))
                }
            },
        )
    }
}

@Composable
private fun CurrentSessionCard(
    session: LoginSession?,
    textColor: Color,
    onLogout: (() -> Unit)?,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.login_activity_active_session),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = textColor,
                modifier = Modifier.weight(1f),
            )
            if (session != null) {
                Text(
                    stringResource(R.string.login_activity_current),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF34C759),
                )
            }
        }

        if (session != null) {
            SessionDetails(session)
            if (onLogout != null) {
                SessionLogoutButton(onClick = onLogout)
            }
        } else {
            Text(
                stringResource(R.string.login_activity_no_current_session),
                fontSize = 14.sp,
                color = Color.Gray,
            )
        }

        HorizontalDivider(color = textColor.copy(alpha = 0.2f))
    }
}

@Composable
private fun SessionCard(
    session: LoginSession,
    textColor: Color,
    isDark: Boolean,
    onLogout: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                session.device,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Text(
                session.timestamp.timeAgoDisplay(),
                fontSize = 12.sp,
                color = if (isDark) Color.White.copy(0.6f) else Color.Black.copy(0.5f),
            )
        }
        SessionDetails(session)
        SessionLogoutButton(onClick = onLogout)
        when {
            session.isSuspicious -> {
                Text(
                    stringResource(R.string.login_activity_session_alert_location_change),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFFF9500),
                )
            }
            session.isNewDevice -> {
                Text(
                    stringResource(R.string.login_activity_session_alert_new_device),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = SettingsProfileColors.accent(isDark),
                )
            }
        }
        HorizontalDivider(color = textColor.copy(alpha = 0.16f))
    }
}

@Composable
private fun SessionLogoutButton(onClick: () -> Unit) {
    Text(
        stringResource(R.string.login_activity_logout_session),
        fontSize = 13.sp,
        fontWeight = FontWeight.Medium,
        color = Color(0xFFFF3B30),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(top = 2.dp),
    )
}

@Composable
private fun SessionDetails(session: LoginSession) {
    val locationUnavailable = stringResource(R.string.login_activity_location_unavailable)
    val ipUnavailable = stringResource(R.string.login_activity_ip_unavailable)
    val visibleLocation = session.location.trim().ifEmpty { locationUnavailable }
    val visibleIP = session.ipAddress.trim().ifEmpty { ipUnavailable }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                stringResource(R.string.login_activity_session_location),
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Gray.copy(alpha = 0.95f),
            )
            Text(visibleLocation, fontSize = 13.sp, color = Color.Gray, maxLines = 1)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(visibleIP, fontSize = 12.sp, color = Color.Gray, maxLines = 1)
            Text("•", fontSize = 11.sp, color = Color.Gray.copy(alpha = 0.7f))
            Text(
                MomentsFormat.smartDate(session.timestamp, MomentsFormat.DateContext.MEDIUM_DATE_TIME),
                fontSize = 12.sp,
                color = Color.Gray,
                maxLines = 1,
            )
        }
    }
}
