package com.moments.android.views.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.models.MessageRequestPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.settings.SettingsProfileColors
import com.moments.android.views.settings.SettingsRoute
import com.moments.android.views.settings.SettingsSubsectionWrapper
import com.moments.android.views.settings.SettingsViewModel
import kotlinx.coroutines.tasks.await

/** Port de PrivacySection / MessageRequestPolicyRow / ConnectionVisibilityView. */

@Composable
fun PrivacySection(
    isPrivate: Boolean,
    onIsPrivateChange: (Boolean) -> Unit,
    showFollowing: Boolean,
    showFollowers: Boolean,
    viewModel: SettingsViewModel,
    onRoute: (SettingsRoute) -> Unit,
    showReadReceipts: Boolean,
    onShowReadReceiptsChange: (Boolean) -> Unit,
    blockedAccountsCount: Int,
) {
    Column {
        SettingsToggleRow(
            title = stringResource(R.string.settings_privacy_private_account),
            subtitle = stringResource(R.string.settings_privacy_private_account_desc),
            icon = if (isPrivate) Icons.Filled.Lock else Icons.Filled.LockOpen,
            checked = isPrivate,
            onCheckedChange = {
                onIsPrivateChange(it)
                viewModel.updatePrivacySettings(isPrivate = it)
            },
        )
        SettingsRow(
            icon = Icons.Filled.VisibilityOff,
            title = stringResource(R.string.settings_sections_content_visibility),
            subtitle = stringResource(R.string.settings_sections_content_visibility_subtitle),
            onClick = { onRoute(SettingsRoute.CONTENT_VISIBILITY) },
        )
        SettingsRow(
            icon = Icons.Filled.People,
            title = stringResource(R.string.settings_sections_connections),
            subtitle = connectionPrivacyStatus(showFollowing, showFollowers),
            onClick = { onRoute(SettingsRoute.CONNECTIONS) },
        )
        SettingsRow(
            icon = Icons.Filled.PanTool,
            title = stringResource(R.string.settings_sections_blocked_accounts),
            subtitle = stringResource(R.string.settings_sections_blocked_accounts_subtitle, blockedAccountsCount),
            onClick = { onRoute(SettingsRoute.BLOCKED_ACCOUNTS) },
        )
        SettingsRow(
            icon = Icons.Filled.NotificationsOff,
            title = stringResource(R.string.settings_sections_mute),
            subtitle = stringResource(R.string.settings_sections_mute_subtitle),
            onClick = { onRoute(SettingsRoute.MUTE) },
        )
        MessageRequestPolicyRow(viewModel = viewModel)
        SettingsToggleRow(
            title = stringResource(R.string.settings_privacy_read_receipts_title),
            subtitle = stringResource(R.string.settings_privacy_read_receipts_desc),
            icon = Icons.Filled.CheckCircle,
            checked = showReadReceipts,
            onCheckedChange = {
                onShowReadReceiptsChange(it)
                viewModel.updateReadReceiptsPrivacy(it)
            },
            showDivider = false,
        )
    }
}

@Composable
private fun connectionPrivacyStatus(showFollowing: Boolean, showFollowers: Boolean): String {
    val hiddenCount = (!showFollowing).toInt() + (!showFollowers).toInt()
    return when (hiddenCount) {
        0 -> stringResource(R.string.settings_privacy_connections_all_public)
        1 -> stringResource(R.string.settings_privacy_connections_hidden_singular)
        2 -> stringResource(R.string.settings_privacy_connections_all_hidden)
        else -> stringResource(R.string.settings_privacy_connections_configure)
    }
}

private fun Boolean.toInt(): Int = if (this) 1 else 0

@Composable
fun MessageRequestPolicyRow(viewModel: SettingsViewModel) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val primary = if (isDark) Color.White else Color.Black
    var policy by remember { mutableStateOf(MessageRequestPolicy.EVERYONE) }
    var hasLoaded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (hasLoaded) return@LaunchedEffect
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        hasLoaded = true
        runCatching {
            val snap = FirebaseFirestore.getInstance().collection("users").document(userId).get().await()
            val raw = snap.getString("messageRequestPolicy")
            policy = MessageRequestPolicy.from(raw)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = SettingsRowHorizontalPadding,
                    vertical = 11.dp,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(SettingsIconSlotWidth), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Email, null, tint = primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(SettingsIconTextSpacing))
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.settings_privacy_message_requests_title),
                    fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                    fontWeight = FontWeight.Medium,
                    color = primary,
                )
                Text(
                    stringResource(R.string.settings_privacy_message_requests_desc),
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    color = Color.Gray,
                )
            }
            Box {
                TextButton(onClick = { menuExpanded = true }) {
                    Text(
                        policyLabel(policy),
                        fontSize = with(density) { legacyPoppinsSize(context, 13).toSp() },
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    MessageRequestPolicy.entries.forEach { option ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (option == policy) "✓ ${policyLabel(option)}" else policyLabel(option),
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                if (option != policy) {
                                    policy = option
                                    viewModel.updateMessageRequestPolicy(option)
                                }
                            },
                        )
                    }
                }
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
private fun policyLabel(policy: MessageRequestPolicy): String = when (policy) {
    MessageRequestPolicy.EVERYONE -> stringResource(R.string.settings_privacy_message_requests_everyone)
    MessageRequestPolicy.FOLLOWING -> stringResource(R.string.settings_privacy_message_requests_following)
    MessageRequestPolicy.NOBODY -> stringResource(R.string.settings_privacy_message_requests_nobody)
}

/**
 * ≡ iOS `ConnectionVisibilityView` — destino de `SettingsRoute.connections`.
 */
@Composable
fun ConnectionVisibilityView(
    showFollowing: Boolean,
    onShowFollowingChange: (Boolean) -> Unit,
    showFollowers: Boolean,
    onShowFollowersChange: (Boolean) -> Unit,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val primary = SettingsProfileColors.accent(isDark)

    SettingsSubsectionWrapper(
        title = stringResource(R.string.settings_connection_privacy_title),
        onNavigateBack = onDismiss,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 22.dp),
        ) {
                Text(
                    stringResource(R.string.settings_privacy_control_title),
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    connectionToggleRow(
                        title = stringResource(R.string.settings_privacy_hide_following),
                        description = stringResource(R.string.settings_privacy_hide_following_desc),
                        isOn = !showFollowing,
                        onChange = { hide ->
                            onShowFollowingChange(!hide)
                            viewModel.updatePrivacySettings(showFollowing = !hide)
                            HapticManager.shared.lightImpact()
                        },
                    )
                    HorizontalDivider(Modifier.padding(start = 32.dp), color = primary.copy(0.2f), thickness = 0.5.dp)
                    connectionToggleRow(
                        title = stringResource(R.string.settings_privacy_hide_followers),
                        description = stringResource(R.string.settings_privacy_hide_followers_desc),
                        isOn = !showFollowers,
                        onChange = { hide ->
                            onShowFollowersChange(!hide)
                            viewModel.updatePrivacySettings(showFollowers = !hide)
                            HapticManager.shared.lightImpact()
                        },
                    )
                }
                Text(
                    stringResource(R.string.settings_privacy_control_description),
                    fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                    color = Color.Gray.copy(0.8f),
                    modifier = Modifier.padding(top = 14.dp),
                )
        }
    }
}

@Composable
private fun connectionToggleRow(
    title: String,
    description: String,
    isOn: Boolean,
    onChange: (Boolean) -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val primary = if (isDark) Color.White else Color.Black
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.VisibilityOff,
            null,
            tint = primary,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
            )
            Text(
                description,
                color = Color.Gray,
                fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
            )
        }
        Switch(
            checked = isOn,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedTrackColor = SettingsProfileColors.toggleTint,
                checkedThumbColor = Color.White,
            ),
        )
    }
}
