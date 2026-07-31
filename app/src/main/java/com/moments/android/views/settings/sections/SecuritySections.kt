package com.moments.android.views.settings.sections

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.auth.AuthService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.momentsPress
import com.moments.android.views.login.linkGoogleAccount
import com.moments.android.views.messaging.components.ChatRecoverySettingsView
import com.moments.android.views.settings.SettingsProfileColors
import com.moments.android.views.settings.SettingsRoute
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Port de SecuritySection (SettingsSections.swift).
 *
 * Apple ID → Google. Passkeys 🚫 en Android.
 */
@Composable
fun SecuritySection(onRoute: (SettingsRoute) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val firebaseUser by AuthService.currentFirebaseUser.collectAsState()

    var showChatRecovery by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var alertTitleRes by remember { mutableStateOf(R.string.common_error) }
    var alertMessage by remember { mutableStateOf<String?>(null) }
    var showAlert by remember { mutableStateOf(false) }
    var showUnlinkGoogleConfirmation by remember { mutableStateOf(false) }
    var showGoogleOnlyAccessInfo by remember { mutableStateOf(false) }

    val googleLinked = remember(firebaseUser) { AuthService.isGoogleLinked }
    val passwordLinked = remember(firebaseUser) { AuthService.isPasswordLinked }
    val canUnlinkGoogle = remember(firebaseUser) { AuthService.canUnlinkGoogle }
    val isGoogleOnlyAccess = remember(firebaseUser) { AuthService.isGoogleOnlyAccess }

    LaunchedEffect(Unit) {
        AuthService.refreshLinkedProviders()
    }

    val googleSubtitle = when {
        !googleLinked -> stringResource(R.string.settings_security_google_description)
        isGoogleOnlyAccess -> stringResource(R.string.settings_security_google_only_method)
        canUnlinkGoogle -> stringResource(R.string.settings_security_google_unlink_hint)
        else -> stringResource(R.string.settings_security_google_linked)
    }

    Column {
        SettingsRow(
            icon = Icons.Filled.VpnKey,
            title = if (passwordLinked) {
                stringResource(R.string.settings_sections_password)
            } else {
                stringResource(R.string.settings_security_password_add)
            },
            subtitle = if (passwordLinked) {
                stringResource(R.string.settings_sections_password_subtitle)
            } else {
                stringResource(R.string.settings_security_password_add_description)
            },
            onClick = { onRoute(SettingsRoute.PASSWORD_CHANGE) },
        )
        SettingsRow(
            icon = Icons.Filled.Lock,
            title = stringResource(R.string.chat_recovery_settings_row_title),
            subtitle = stringResource(R.string.chat_recovery_settings_row_subtitle),
            onClick = { showChatRecovery = true },
        )

        if (googleLinked) {
            SecurityStatusRow(
                title = stringResource(R.string.settings_security_google),
                subtitle = googleSubtitle,
                isConfigured = true,
                isLoading = isLoading,
                onClick = {
                    if (canUnlinkGoogle) {
                        showUnlinkGoogleConfirmation = true
                    } else {
                        showGoogleOnlyAccessInfo = true
                    }
                },
            )
        } else {
            GoogleLinkSettingsRow(
                isLoading = isLoading,
                onLink = {
                    isLoading = true
                    scope.launch {
                        try {
                            linkGoogleAccount(context)
                            AuthService.refreshLinkedProviders()
                            HapticManager.shared.lightImpact()
                        } catch (e: CancellationException) {
                            throw e
                        } catch (e: Exception) {
                            if (e.message?.contains("cancel", ignoreCase = true) != true) {
                                alertTitleRes = R.string.common_error
                                alertMessage = e.localizedMessage
                                    ?: context.getString(R.string.auth_google_error)
                                showAlert = true
                            }
                        } finally {
                            isLoading = false
                        }
                    }
                },
            )
        }
    }

    if (showChatRecovery) {
        MomentsModalSheet(
            onDismissRequest = { showChatRecovery = false },
            largeOnly = false,
        ) { dismiss ->
            ChatRecoverySettingsView(onClose = dismiss)
        }
    }

    if (showAlert) {
        AlertDialog(
            onDismissRequest = { showAlert = false },
            title = { Text(stringResource(alertTitleRes)) },
            text = { Text(alertMessage.orEmpty()) },
            confirmButton = {
                TextButton(onClick = { showAlert = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    if (showUnlinkGoogleConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnlinkGoogleConfirmation = false },
            title = { Text(stringResource(R.string.settings_security_google_unlink_title)) },
            text = { Text(stringResource(R.string.settings_security_google_unlink_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showUnlinkGoogleConfirmation = false
                        isLoading = true
                        scope.launch {
                            try {
                                AuthService.unlinkFromGoogle()
                                AuthService.refreshLinkedProviders()
                                HapticManager.shared.lightImpact()
                                alertTitleRes = R.string.common_success
                                alertMessage =
                                    context.getString(R.string.settings_security_google_unlink_success)
                                showAlert = true
                            } catch (e: Exception) {
                                alertTitleRes = R.string.common_error
                                alertMessage = e.localizedMessage
                                showAlert = true
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_security_google_unlink_confirm),
                        color = Color(0xFFFF3B30),
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnlinkGoogleConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showGoogleOnlyAccessInfo) {
        AlertDialog(
            onDismissRequest = { showGoogleOnlyAccessInfo = false },
            title = { Text(stringResource(R.string.settings_security_google_cannot_unlink_title)) },
            text = { Text(stringResource(R.string.settings_security_google_cannot_unlink_message)) },
            confirmButton = {
                TextButton(onClick = { showGoogleOnlyAccessInfo = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showGoogleOnlyAccessInfo = false
                        onRoute(SettingsRoute.PASSWORD_CHANGE)
                    },
                ) {
                    Text(stringResource(R.string.settings_security_password_add))
                }
            },
        )
    }
}

@Composable
private fun SecurityStatusRow(
    title: String,
    subtitle: String,
    isConfigured: Boolean,
    isLoading: Boolean,
    onClick: (() -> Unit)?,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null && !isLoading) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SettingsRowHorizontalPadding,
                    vertical = 11.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.google_icon),
                contentDescription = null,
                modifier = Modifier
                    .width(SettingsIconSlotWidth)
                    .size(19.dp),
            )
            Spacer(Modifier.width(SettingsIconTextSpacing))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    title,
                    fontWeight = FontWeight.Medium,
                    color = primary,
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    subtitle,
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            when {
                isLoading -> CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = primary,
                )
                isConfigured -> Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color(0xFF34C759),
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        HorizontalDivider(
            Modifier.padding(start = SettingsDividerStart),
            color = SettingsProfileColors.outlineVariant(isDark),
            thickness = 1.dp,
        )
    }
}

@Composable
private fun GoogleLinkSettingsRow(
    isLoading: Boolean,
    onLink: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black
    val buttonBg = if (isDark) Color.White else Color.Black
    val buttonFg = if (isDark) Color.Black else Color.White

    Column(Modifier.fillMaxWidth()) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SettingsRowHorizontalPadding,
                    vertical = 11.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(R.drawable.google_icon),
                    contentDescription = null,
                    modifier = Modifier
                        .width(SettingsIconSlotWidth)
                        .size(19.dp),
                )
                Spacer(Modifier.width(SettingsIconTextSpacing))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        stringResource(R.string.settings_security_google),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Medium,
                        color = primary,
                    )
                    Text(
                        stringResource(R.string.settings_security_google_description),
                        fontSize = 12.sp,
                        color = Color.Gray,
                    )
                }
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = primary,
                    )
                }
            }

            Button(
                onClick = onLink,
                enabled = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = buttonBg,
                    contentColor = buttonFg,
                ),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.google_icon),
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.auth_google_continue),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
        HorizontalDivider(
            Modifier.padding(start = SettingsDividerStart),
            color = SettingsProfileColors.outlineVariant(isDark),
            thickness = 1.dp,
        )
    }
}
