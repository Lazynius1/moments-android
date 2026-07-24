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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.services.auth.AuthService

/**
 * Mirror 1:1 de `SettingsView.swift` con i18n multilingüe (`stringResource`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    onNavigateBack: () -> Unit = {},
    onNavigateToRoute: (String) -> Unit = {}
) {
    val isDark = isSystemInDarkTheme()
    val backgroundColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val textColor = if (isDark) Color.White else Color.Black
    val secondaryTextColor = if (isDark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.45f)

    val viewModel = remember { SettingsViewModel() }
    var isLoading by remember { mutableStateOf(true) }
    var user by remember { mutableStateOf<AppUser?>(null) }
    var isPrivate by remember { mutableStateOf(false) }
    var showFollowing by remember { mutableStateOf(true) }
    var showFollowers by remember { mutableStateOf(true) }
    var showReadReceipts by remember { mutableStateOf(true) }
    var blockedAccountsCount by remember { mutableIntStateOf(0) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }

    var showAdvancedSheet by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        isLoading = true
        viewModel.fetchUserSettings { result ->
            val loadedUser = result.getOrNull()
            if (loadedUser != null) {
                user = loadedUser
                isPrivate = loadedUser.isPrivate
                showFollowing = loadedUser.showFollowing
                showFollowers = loadedUser.showFollowers
                showReadReceipts = loadedUser.showReadReceipts
                blockedAccountsCount = loadedUser.blockedUsers.size
                username = loadedUser.username
                email = loadedUser.email
            }
            isLoading = false
        }
    }

    Scaffold(
        containerColor = backgroundColor,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(id = R.string.settings_main_title),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = textColor
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = backgroundColor)
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(color = textColor)
                        Text(
                            text = stringResource(id = R.string.settings_loading),
                            color = textColor,
                            fontSize = 15.sp
                        )
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    user?.let { appUser ->
                        SettingsProfileCard(
                            user = appUser,
                            textColor = textColor,
                            secondaryColor = secondaryTextColor
                        )
                    }

                    // Group: Account & Security
                    SettingsGroup(title = stringResource(id = R.string.settings_section_account_security), subtitleColor = secondaryTextColor) {
                        SettingsRowItem(
                            icon = Icons.Default.Person,
                            title = stringResource(id = R.string.settings_personal_info),
                            subtitle = email.ifEmpty { "@$username" },
                            textColor = textColor,
                            onClick = { onNavigateToRoute("personal_info") }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.QrCode,
                            title = stringResource(id = R.string.settings_qr_code),
                            subtitle = stringResource(id = R.string.settings_qr_code_desc),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("qr_code") }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.Key,
                            title = stringResource(id = R.string.settings_change_password),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("password_change") }
                        )
                    }

                    // Group: Privacy
                    SettingsGroup(title = stringResource(id = R.string.settings_section_privacy), subtitleColor = secondaryTextColor) {
                        SettingsSwitchItem(
                            icon = Icons.Default.Lock,
                            title = stringResource(id = R.string.settings_private_account),
                            subtitle = stringResource(id = R.string.settings_private_account_desc),
                            checked = isPrivate,
                            textColor = textColor,
                            onCheckedChange = { checked ->
                                isPrivate = checked
                                viewModel.updatePrivacySettings(isPrivate = checked)
                            }
                        )
                        SettingsSwitchItem(
                            icon = Icons.Default.PrivacyTip,
                            title = stringResource(id = R.string.settings_read_receipts),
                            subtitle = stringResource(id = R.string.settings_read_receipts_desc),
                            checked = showReadReceipts,
                            textColor = textColor,
                            onCheckedChange = { checked ->
                                showReadReceipts = checked
                                viewModel.updateReadReceiptsPrivacy(checked)
                            }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.Star,
                            title = stringResource(id = R.string.settings_best_friends),
                            subtitle = stringResource(id = R.string.settings_best_friends_desc),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("best_friends") }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.Block,
                            title = stringResource(id = R.string.settings_blocked_users),
                            subtitle = if (blockedAccountsCount > 0) "$blockedAccountsCount" else null,
                            textColor = textColor,
                            onClick = { onNavigateToRoute("blocked_users") }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.VolumeMute,
                            title = stringResource(id = R.string.settings_muted_users),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("mute_settings") }
                        )
                    }

                    // Group: Activity & Content
                    SettingsGroup(title = stringResource(id = R.string.settings_section_activity), subtitleColor = secondaryTextColor) {
                        SettingsRowItem(
                            icon = Icons.Default.Timer,
                            title = stringResource(id = R.string.settings_user_activity),
                            subtitle = stringResource(id = R.string.settings_user_activity_desc),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("user_activity") }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.FolderZip,
                            title = stringResource(id = R.string.settings_archived_stories),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("archived_stories") }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.Notifications,
                            title = stringResource(id = R.string.settings_notifications),
                            subtitle = stringResource(id = R.string.settings_notifications_desc),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("notifications_settings") }
                        )
                    }

                    // Group: Data & Storage
                    SettingsGroup(title = stringResource(id = R.string.settings_section_data), subtitleColor = secondaryTextColor) {
                        SettingsRowItem(
                            icon = Icons.Default.Storage,
                            title = stringResource(id = R.string.settings_chat_storage),
                            subtitle = stringResource(id = R.string.settings_chat_storage_desc),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("chat_storage") }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.Download,
                            title = stringResource(id = R.string.settings_data_export),
                            subtitle = stringResource(id = R.string.settings_data_export_desc),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("data_export") }
                        )
                    }

                    // Group: Support & Legal
                    SettingsGroup(title = stringResource(id = R.string.settings_section_support), subtitleColor = secondaryTextColor) {
                        SettingsRowItem(
                            icon = Icons.Default.Gavel,
                            title = stringResource(id = R.string.settings_appeals),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("moderation_reviews") }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.HelpOutline,
                            title = stringResource(id = R.string.settings_privacy_policy),
                            textColor = textColor,
                            onClick = { onNavigateToRoute("privacy_policy") }
                        )
                    }

                    // Group: Danger Zone / Advanced
                    SettingsGroup(title = stringResource(id = R.string.settings_section_advanced), subtitleColor = secondaryTextColor) {
                        SettingsRowItem(
                            icon = Icons.Default.Security,
                            title = stringResource(id = R.string.settings_account_management),
                            subtitle = stringResource(id = R.string.settings_account_management_desc),
                            textColor = textColor,
                            onClick = { showAdvancedSheet = true }
                        )
                        SettingsRowItem(
                            icon = Icons.Default.Logout,
                            title = stringResource(id = R.string.settings_logout),
                            textColor = Color(0xFFFF453A),
                            isDestructive = true,
                            onClick = {
                                AuthService.logout()
                                onNavigateBack()
                            }
                        )
                    }

                    SettingsFooter(secondaryColor = secondaryTextColor)

                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun SettingsProfileCard(
    user: AppUser,
    textColor: Color,
    secondaryColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(textColor.copy(alpha = 0.05f))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = user.profileImagePath,
            contentDescription = "Avatar",
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(textColor.copy(alpha = 0.1f))
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = user.username,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "@${user.username}",
                fontSize = 14.sp,
                color = secondaryColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingsGroup(
    title: String,
    subtitleColor: Color,
    content: @Composable () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = title,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = subtitleColor,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(subtitleColor.copy(alpha = 0.06f))
        ) {
            content()
        }
    }
}

@Composable
private fun SettingsRowItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    textColor: Color,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isDestructive) Color(0xFFFF453A) else textColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = if (isDestructive) Color(0xFFFF453A) else textColor
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = textColor.copy(alpha = 0.3f),
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    textColor: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(22.dp)
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
            if (!subtitle.isNullOrEmpty()) {
                Text(
                    text = subtitle,
                    fontSize = 12.sp,
                    color = textColor.copy(alpha = 0.5f)
                )
            }
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = Color(0xFF34C759)
            )
        )
    }
}

@Composable
private fun SettingsFooter(secondaryColor: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Moments v0.1.0",
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = secondaryColor
        )
    }
}
