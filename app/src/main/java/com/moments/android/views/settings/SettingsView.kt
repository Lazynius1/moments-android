package com.moments.android.views.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.AlertDialog
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.BestFriendsView
import com.moments.android.reportes.ModerationReviewStatusView
import com.moments.android.services.auth.AuthService
import com.moments.android.views.nova.NovaMemoryManagementView
import com.moments.android.views.settings.sections.ConnectionVisibilityView
import com.moments.android.views.settings.sections.SettingsRow
import com.moments.android.views.settings.sections.SettingsFormView
import com.moments.android.views.settings.settingssections.NotificationSettingsView
import com.moments.android.views.settings.settingssections.PersonalInfoView
import com.moments.android.views.settings.savedmoments.SavedMomentsView
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.LocalMomentsSharedAnimatedVisibilityScope
import com.moments.android.views.shared.LocalMomentsSharedTransitionScope
import com.moments.android.views.story.ArchivedStoriesView
import com.moments.android.views.profile.core.ProfileContextFlipConfiguration
import com.moments.android.views.profile.core.ProfileContextFlipTransition
import java.util.Date

/**
 * Paridad iOS `SettingsProfileColors` (SettingsView.swift).
 * Canvas sólido AdaptiveColors — sin material/blur del sheet iOS.
 */
object SettingsProfileColors {
    val backgroundLight = Color(0xFFFAF9F6)
    val backgroundDark = Color(0xFF0B1215)
    val toggleTint = Color(0xFF34C759) // systemGreen
    val purple = Color(0xFF9B59B6)
    val blue = Color(0xFF6B73FF)

    fun accent(isDark: Boolean): Color = if (isDark) Color.White else Color.Black

    fun accentStroke(isDark: Boolean, opacity: Float = 0.3f): Color =
        accent(isDark).copy(alpha = opacity)

    fun accentBackground(isDark: Boolean, opacity: Float = 0.1f): Color =
        accent(isDark).copy(alpha = opacity)

    fun accentContrastingText(isDark: Boolean): Color =
        if (isDark) Color.Black else Color.White

    fun canvas(isDark: Boolean): Color =
        if (isDark) backgroundDark else backgroundLight

    /** Material 3 sólido para agrupar filas sin replicar el glass de iOS. */
    fun surfaceContainer(isDark: Boolean): Color =
        if (isDark) Color(0xFF151D21) else Color(0xFFFFFFFF)

    fun onSurface(isDark: Boolean): Color =
        if (isDark) Color(0xFFF2F4F5) else Color(0xFF171C1F)

    fun onSurfaceVariant(isDark: Boolean): Color =
        if (isDark) Color(0xFFB9C3C8) else Color(0xFF596166)

    fun outlineVariant(isDark: Boolean): Color =
        if (isDark) Color(0xFF354047) else Color(0xFFE0E3E5)
}

/**
 * Destinos push de Ajustes — paridad iOS `SettingsRoute`.
 */
enum class SettingsRoute {
    CONTENT_VISIBILITY,
    CONNECTIONS,
    BEST_FRIENDS,
    BLOCKED_ACCOUNTS,
    MUTE,
    PASSWORD_CHANGE,
    SAVED_MOMENTS,
    USER_ACTIVITY,
    DATA_EXPORT,
    CHAT_STORAGE,
    MODERATION_REVIEWS,
    ARCHIVED_STORIES,
    NOTIFICATION_SETTINGS,
    LOGIN_ACTIVITY,
}

/**
 * Port de `SettingsView.swift`: shell (fondo, loading, form host, sheets, destinations).
 * El formulario vive en [SettingsFormView] (SettingsSections → port por trozos).
 */
@Composable
fun SettingsView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val canvas = SettingsProfileColors.canvas(isDark)
    val primary = SettingsProfileColors.accent(isDark)

    val viewModel = remember { SettingsViewModel() }
    val firebaseUser by AuthService.currentFirebaseUser.collectAsState()

    var isPrivate by remember { mutableStateOf(false) }
    var showFollowing by remember { mutableStateOf(true) }
    var showFollowers by remember { mutableStateOf(true) }
    var showReadReceipts by remember { mutableStateOf(true) }
    var isScheduleEnabled by remember { mutableStateOf(false) }
    var startTime by remember { mutableStateOf(Date()) }
    var endTime by remember { mutableStateOf(Date()) }
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var isShowingQRCode by remember { mutableStateOf(false) }
    var qrFlipBounds by remember { mutableStateOf(Rect.Zero) }
    var route by remember { mutableStateOf<SettingsRoute?>(null) }
    var isShowingAdvancedAccountManagement by remember { mutableStateOf(false) }
    var isShowingNovaMemory by remember { mutableStateOf(false) }
    var isShowingPersonalInfo by remember { mutableStateOf(false) }
    var blockedAccountsCount by remember { mutableIntStateOf(0) }

    val loadErrorFmt = stringResource(R.string.settings_error_load)
    val unknownError = stringResource(R.string.settings_error_unknown)

    fun presentError(message: String) {
        errorMessage = message
        showError = true
    }

    LaunchedEffect(Unit) {
        isLoading = true
        viewModel.fetchUserSettings { result ->
            result
                .onSuccess { user ->
                    isPrivate = user.isPrivate
                    showFollowing = user.showFollowing
                    showFollowers = user.showFollowers
                    blockedAccountsCount = user.blockedUsers.size
                    username = user.username
                    email = user.email
                    val start = user.activeHoursStart
                    val end = user.activeHoursEnd
                    if (!start.isNullOrBlank() && !end.isNullOrBlank()) {
                        val startDate = runCatching { viewModel.dateFormatter.parse(start) }.getOrNull()
                        val endDate = runCatching { viewModel.dateFormatter.parse(end) }.getOrNull()
                        if (startDate != null && endDate != null) {
                            startTime = startDate
                            endTime = endDate
                            isScheduleEnabled = true
                        } else {
                            isScheduleEnabled = false
                            startTime = Date()
                            endTime = Date()
                        }
                    } else {
                        isScheduleEnabled = false
                        startTime = Date()
                        endTime = Date()
                    }
                    showReadReceipts = user.showReadReceipts
                }
                .onFailure { error ->
                    val stillAuthed =
                        FirebaseAuth.getInstance().currentUser != null && firebaseUser != null
                    if (!stillAuthed) {
                        isLoading = false
                        onNavigateBack()
                        return@fetchUserSettings
                    }
                    presentError(
                        loadErrorFmt.format(error.message ?: unknownError),
                    )
                }
            isLoading = false
        }
    }

    LaunchedEffect(firebaseUser) {
        if (firebaseUser == null && !isLoading) {
            showError = false
            errorMessage = null
            onNavigateBack()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(canvas),
    ) {
        if (isLoading) {
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                MomentsCircularProgressIndicator(
                    modifier = Modifier.padding(bottom = 20.dp),
                )
                Text(
                    stringResource(R.string.settings_loading),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = primary,
                )
            }
        } else {
            SettingsFormView(
                viewModel = viewModel,
                isPrivate = isPrivate,
                onIsPrivateChange = { isPrivate = it },
                showFollowing = showFollowing,
                onShowFollowingChange = { showFollowing = it },
                showFollowers = showFollowers,
                onShowFollowersChange = { showFollowers = it },
                isScheduleEnabled = isScheduleEnabled,
                onIsScheduleEnabledChange = { isScheduleEnabled = it },
                startTime = startTime,
                onStartTimeChange = { startTime = it },
                endTime = endTime,
                onEndTimeChange = { endTime = it },
                username = username,
                onUsernameChange = { username = it },
                email = email,
                onEmailChange = { email = it },
                phoneNumber = phoneNumber,
                onPhoneNumberChange = { phoneNumber = it },
                onShowPersonalInfo = { isShowingPersonalInfo = true },
                onShowQRCode = { bounds ->
                    qrFlipBounds = bounds
                    isShowingQRCode = true
                },
                hideQRCodeRow = isShowingQRCode,
                onRoute = { route = it },
                onShowAdvancedAccountManagement = { isShowingAdvancedAccountManagement = true },
                onShowNovaMemory = { isShowingNovaMemory = true },
                showReadReceipts = showReadReceipts,
                onShowReadReceiptsChange = { showReadReceipts = it },
                blockedAccountsCount = blockedAccountsCount,
                onNavigateBack = onNavigateBack,
            )
        }

        route?.let { current ->
            BackHandler { route = null }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(canvas),
            ) {
                SettingsDestinationHost(
                    route = current,
                    viewModel = viewModel,
                    showFollowing = showFollowing,
                    onShowFollowingChange = { showFollowing = it },
                    showFollowers = showFollowers,
                    onShowFollowersChange = { showFollowers = it },
                    isScheduleEnabled = isScheduleEnabled,
                    onIsScheduleEnabledChange = { isScheduleEnabled = it },
                    startTime = startTime,
                    onStartTimeChange = { startTime = it },
                    endTime = endTime,
                    onEndTimeChange = { endTime = it },
                    onDismiss = { route = null },
                )
            }
        }

        if (isShowingQRCode) {
            ProfileContextFlipTransition(
                sourceBounds = qrFlipBounds,
                configuration = ProfileContextFlipConfiguration.SettingsQr,
                onDismiss = { isShowingQRCode = false },
                modifier = Modifier.zIndex(40f),
                source = {
                    SettingsRow(
                        icon = Icons.Filled.QrCode,
                        title = stringResource(R.string.settings_sections_qr_code),
                        subtitle = stringResource(R.string.settings_sections_qr_code_subtitle),
                        onClick = {},
                    )
                },
                destination = { close -> QRCodeView(onNavigateBack = close) },
            )
        }
    }

    // ≡ iOS `.sheet` PersonalInfo · medium+large
    if (isShowingPersonalInfo) {
        MomentsModalSheet(
            onDismissRequest = { isShowingPersonalInfo = false },
            largeOnly = false,
        ) { dismiss ->
            PersonalInfoView(
                username = username,
                email = email,
                onUsernameUpdated = { username = it },
                onNavigateBack = dismiss,
            )
        }
    }

    // ≡ iOS `.sheet` AdvancedAccountManagement · medium+large
    var advancedAccountProcessing by remember { mutableStateOf(false) }
    if (isShowingAdvancedAccountManagement) {
        MomentsModalSheet(
            onDismissRequest = {
                if (!advancedAccountProcessing) isShowingAdvancedAccountManagement = false
            },
            largeOnly = false,
            dismissEnabled = !advancedAccountProcessing,
        ) { dismiss ->
            AdvancedAccountManagementView(
                onNavigateBack = dismiss,
                onProcessingChange = { advancedAccountProcessing = it },
            )
        }
    }

    // ≡ iOS `.sheet` NovaMemory · medium+large
    if (isShowingNovaMemory) {
        MomentsModalSheet(
            onDismissRequest = { isShowingNovaMemory = false },
            largeOnly = false,
        ) { dismiss ->
            NovaMemoryManagementView(
                onDismiss = dismiss,
            )
        }
    }

    if (showError) {
        AlertDialog(
            onDismissRequest = { showError = false },
            title = { Text(stringResource(R.string.settings_error_title)) },
            text = { Text(errorMessage ?: unknownError) },
            confirmButton = {
                TextButton(onClick = { showError = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }
}

@Composable
private fun SettingsDestinationHost(
    route: SettingsRoute,
    viewModel: SettingsViewModel,
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
    onDismiss: () -> Unit,
) {
    when (route) {
        SettingsRoute.CONTENT_VISIBILITY -> ContentVisibilityView(onNavigateBack = onDismiss)
        SettingsRoute.CONNECTIONS -> ConnectionVisibilityView(
            showFollowing = showFollowing,
            onShowFollowingChange = onShowFollowingChange,
            showFollowers = showFollowers,
            onShowFollowersChange = onShowFollowersChange,
            viewModel = viewModel,
            onDismiss = onDismiss,
        )
        SettingsRoute.BEST_FRIENDS -> BestFriendsView(onDismiss = onDismiss)
        SettingsRoute.BLOCKED_ACCOUNTS -> BlockedUsersView(onNavigateBack = onDismiss)
        SettingsRoute.MUTE -> MuteSettingsView(onNavigateBack = onDismiss)
        SettingsRoute.PASSWORD_CHANGE -> {
            AuthService.refreshLinkedProviders()
            if (AuthService.isPasswordLinked) {
                PasswordChangeView(onNavigateBack = onDismiss)
            } else {
                SetPasswordView(onNavigateBack = onDismiss)
            }
        }
        SettingsRoute.SAVED_MOMENTS -> CompositionLocalProvider(
            LocalMomentsSharedTransitionScope provides null,
            LocalMomentsSharedAnimatedVisibilityScope provides null,
        ) {
            // Ajustes puede vivir en una ventana distinta a Perfil. Una transición
            // compartida entre ambas jerarquías provoca un crash al reordenar el
            // grid (por ejemplo al entrar/salir del modo selección).
            SavedMomentsView(onNavigateBack = onDismiss)
        }
        SettingsRoute.USER_ACTIVITY -> UserActivityView(onNavigateBack = onDismiss)
        SettingsRoute.DATA_EXPORT -> DataExportView(onNavigateBack = onDismiss)
        SettingsRoute.CHAT_STORAGE -> ChatStorageSettingsView(onNavigateBack = onDismiss)
        SettingsRoute.MODERATION_REVIEWS -> ModerationReviewStatusView(onBack = onDismiss)
        SettingsRoute.ARCHIVED_STORIES -> ArchivedStoriesView(onNavigateBack = onDismiss)
        SettingsRoute.NOTIFICATION_SETTINGS -> NotificationSettingsView(
            viewModel = viewModel,
            isScheduleEnabled = isScheduleEnabled,
            onIsScheduleEnabledChange = onIsScheduleEnabledChange,
            startTime = startTime,
            onStartTimeChange = onStartTimeChange,
            endTime = endTime,
            onEndTimeChange = onEndTimeChange,
            onNavigateBack = onDismiss,
        )
        SettingsRoute.LOGIN_ACTIVITY -> LoginActivityView(onNavigateBack = onDismiss)
    }
}
