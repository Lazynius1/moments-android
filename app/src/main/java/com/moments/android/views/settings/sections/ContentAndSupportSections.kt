package com.moments.android.views.settings.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Policy
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.android.ump.ConsentInformation
import com.google.android.ump.UserMessagingPlatform
import com.moments.android.R
import com.moments.android.ad.AdMobConfiguration
import com.moments.android.ad.findActivity
import com.moments.android.services.auth.AuthService
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsPress
import com.moments.android.views.creator.audienceselector.ContentAudience
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.settings.SettingsRoute
import com.moments.android.views.settings.SettingsViewModel
import java.util.Date

/** Port de Activity / Data / Notifications / Help / Logout / AdvancedAccountSection. */

@Composable
fun ActivitySection(
    onRoute: (SettingsRoute) -> Unit,
    onShowNovaMemory: () -> Unit,
) {
    Column {
        SettingsRow(
            attachmentIcon = AttachmentIcon.BOOKMARK,
            title = stringResource(R.string.settings_sections_saved),
            subtitle = stringResource(R.string.settings_sections_saved_subtitle),
            onClick = { onRoute(SettingsRoute.SAVED_MOMENTS) },
        )
        SettingsRow(
            icon = Icons.Filled.AccessTime,
            title = stringResource(R.string.settings_sections_your_activity),
            subtitle = stringResource(R.string.settings_sections_your_activity_subtitle),
            onClick = { onRoute(SettingsRoute.USER_ACTIVITY) },
        )
        SettingsRow(
            icon = Icons.Filled.Star,
            audienceIcon = ContentAudience.BEST_FRIENDS,
            title = stringResource(R.string.settings_sections_best_friends),
            subtitle = stringResource(R.string.settings_sections_best_friends_subtitle),
            starFillTint = true,
            onClick = { onRoute(SettingsRoute.BEST_FRIENDS) },
        )
        SettingsRow(
            icon = Icons.Filled.AutoAwesome,
            title = stringResource(R.string.nova_memory_title),
            subtitle = stringResource(R.string.nova_memory_description),
            onClick = onShowNovaMemory,
        )
    }
}

@Composable
fun DataSection(onRoute: (SettingsRoute) -> Unit) {
    Column {
        SettingsRow(
            icon = Icons.Filled.ArrowDownward,
            title = stringResource(R.string.settings_sections_download_data),
            subtitle = stringResource(R.string.settings_sections_download_data_subtitle),
            onClick = { onRoute(SettingsRoute.DATA_EXPORT) },
        )
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.Chat,
            title = stringResource(R.string.settings_sections_chat_storage),
            subtitle = stringResource(R.string.settings_sections_chat_storage_subtitle),
            onClick = { onRoute(SettingsRoute.CHAT_STORAGE) },
        )
    }
}

@Composable
fun NotificationsSection(
    @Suppress("UNUSED_PARAMETER") viewModel: SettingsViewModel,
    @Suppress("UNUSED_PARAMETER") isScheduleEnabled: Boolean,
    @Suppress("UNUSED_PARAMETER") onIsScheduleEnabledChange: (Boolean) -> Unit,
    @Suppress("UNUSED_PARAMETER") startTime: Date,
    @Suppress("UNUSED_PARAMETER") onStartTimeChange: (Date) -> Unit,
    @Suppress("UNUSED_PARAMETER") endTime: Date,
    @Suppress("UNUSED_PARAMETER") onEndTimeChange: (Date) -> Unit,
    onRoute: (SettingsRoute) -> Unit,
) {
    // schedule bindings se consumen en NotificationSettingsView (destino); iOS los pasa por el form.
    Column {
        SettingsRow(
            icon = Icons.Filled.Notifications,
            title = stringResource(R.string.settings_sections_push_notifications),
            subtitle = stringResource(R.string.settings_sections_push_notifications_subtitle),
            onClick = { onRoute(SettingsRoute.NOTIFICATION_SETTINGS) },
        )
        SettingsRow(
            icon = Icons.Filled.Email,
            title = stringResource(R.string.settings_sections_email_notifications),
            subtitle = stringResource(R.string.settings_sections_email_notifications_subtitle),
            onClick = { /* iOS action vacía */ },
        )
    }
}

@Composable
fun HelpSection(onRoute: (SettingsRoute) -> Unit) {
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    var requiresPrivacyOptions by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val status = UserMessagingPlatform.getConsentInformation(context)
            .privacyOptionsRequirementStatus
        requiresPrivacyOptions =
            status == ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    Column {
        SettingsRow(
            icon = Icons.Filled.Gavel,
            title = stringResource(R.string.settings_sections_content_reviews),
            subtitle = stringResource(R.string.settings_sections_content_reviews_subtitle),
            onClick = { onRoute(SettingsRoute.MODERATION_REVIEWS) },
        )
        SettingsRow(
            icon = Icons.AutoMirrored.Filled.HelpOutline,
            title = stringResource(R.string.settings_sections_help_center),
            subtitle = stringResource(R.string.settings_sections_help_center_subtitle),
            isExternal = true,
            onClick = { uriHandler.openUri("https://momentsapp.app/help") },
        )
        SettingsRow(
            icon = Icons.Filled.ReportProblem,
            title = stringResource(R.string.settings_sections_report_problem),
            subtitle = stringResource(R.string.settings_sections_report_problem_subtitle),
            isExternal = true,
            onClick = { uriHandler.openUri("https://momentsapp.app/report") },
        )
        SettingsRow(
            icon = Icons.Filled.Policy,
            title = stringResource(R.string.settings_sections_terms_of_use),
            subtitle = "",
            isExternal = true,
            onClick = { uriHandler.openUri("https://momentsapp.app/terms") },
        )
        SettingsRow(
            icon = Icons.Filled.Policy,
            title = stringResource(R.string.settings_sections_privacy_policy),
            subtitle = "",
            isExternal = true,
            onClick = { uriHandler.openUri("https://momentsapp.app/privacy") },
        )
        if (requiresPrivacyOptions) {
            SettingsRow(
                icon = Icons.Filled.Policy,
                title = stringResource(R.string.settings_sections_ad_privacy),
                subtitle = stringResource(R.string.settings_sections_ad_privacy_subtitle),
                onClick = {
                    context.findActivity()?.let { AdMobConfiguration.showPrivacyOptionsForm(it) }
                },
            )
        }
    }
}

@Composable
fun AdvancedAccountSection(onShowAdvancedAccountManagement: () -> Unit) {
    val isDark = isSystemInDarkTheme()
    val context = LocalContext.current
    val density = LocalDensity.current
    val primary = if (isDark) Color.White else Color.Black
    val interaction = remember { MutableInteractionSource() }

    Column(
        Modifier
            .fillMaxWidth()
            .momentsPress(interaction, MomentsPressDefaults.momentsPressSubtle)
            .clickable(interactionSource = interaction, indication = null, onClick = onShowAdvancedAccountManagement),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 11.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Settings, null, tint = primary, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.settings_advanced_title),
                fontSize = with(density) { legacyPoppinsSize(context, 15).toSp() },
                fontWeight = FontWeight.Medium,
                color = primary,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                null,
                tint = Color.Gray.copy(0.3f),
                modifier = Modifier.size(12.dp),
            )
        }
        HorizontalDivider(
            Modifier.padding(start = 42.dp),
            color = primary.copy(0.2f),
            thickness = 0.5.dp,
        )
    }
}

@Composable
fun LogoutSection(onNavigateBack: () -> Unit) {
    var showLogoutAlert by remember { mutableStateOf(false) }

    SettingsRow(
        icon = Icons.AutoMirrored.Filled.Logout,
        title = stringResource(R.string.settings_logout),
        subtitle = null,
        isDestructive = true,
        onClick = { showLogoutAlert = true },
    )

    if (showLogoutAlert) {
        AlertDialog(
            onDismissRequest = { showLogoutAlert = false },
            title = { Text(stringResource(R.string.settings_logout_alert_title)) },
            text = { Text(stringResource(R.string.settings_logout_alert_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showLogoutAlert = false
                        AuthService.logout()
                        onNavigateBack()
                    },
                ) {
                    Text(stringResource(R.string.settings_logout_alert_confirm), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutAlert = false }) {
                    Text(stringResource(R.string.settings_logout_alert_cancel))
                }
            },
        )
    }
}
