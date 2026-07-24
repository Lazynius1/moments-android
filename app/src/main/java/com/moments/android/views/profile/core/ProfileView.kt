package com.moments.android.views.profile.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.models.BestFriendsView
import com.moments.android.reportes.AppealStatusView
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.views.profile.core.sections.ModernProfileContentView
import com.moments.android.views.profile.core.sections.ProfileSavedContentState
import com.moments.android.views.profile.editor.ModernEditProfileView
import com.moments.android.views.profile.incognito.IncognitoModeSheet
import com.moments.android.views.profile.userprofile.sections.ProfileImageViewer
import com.moments.android.views.settings.QRCodeView
import com.moments.android.views.settings.BlockedUsersView
import com.moments.android.views.settings.ChatStorageSettingsView
import com.moments.android.views.settings.DataExportView
import com.moments.android.views.settings.MuteSettingsView
import com.moments.android.views.settings.PasswordChangeView
import com.moments.android.views.settings.SettingsView
import com.moments.android.views.settings.SettingsViewModel
import com.moments.android.views.settings.settingssections.NotificationSettingsView
import com.moments.android.views.settings.settingssections.PersonalInfoView
import com.moments.android.views.login.PrivacyPolicySheet
import com.moments.android.views.story.ArchivedStoriesView
import com.moments.android.views.story.StoriesView
import com.moments.android.views.story.StoryViewModel

/** Paleta que declara `ProfileView.swift`, con equivalente adaptativo Compose. */
object ProfileColors {
    val accent = Color(0xFF007AFF); val purple = Color(0xFF9B59B6); val blue = Color(0xFF6B73FF)
    @Composable fun background() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    @Composable fun textPrimary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF0B1215)
    @Composable fun textSecondary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.64f) else Color(0xFF52626A)
}

/** Enum de tabs definido en `ProfileView.swift` (no en el shell). */
enum class ProfileTabType(val title: Int, val icon: ImageVector) {
    MOMENTS(R.string.profile_shell_tab_moments, Icons.Filled.GridView),
    SAVED(R.string.profile_shell_tab_saved, Icons.Filled.Bookmark),
    TAGGED(R.string.profile_shell_tab_tagged, Icons.Filled.PersonPin),
}

enum class ProfileUserListType(val title: Int) { VISITS(R.string.profile_header_visits), FOLLOWERS(R.string.profile_header_followers), FOLLOWING(R.string.profile_header_following), MUTUALS(R.string.profile_header_mutuals) }

/** Pills con selección por toque; Android conserva la misma semántica de tres tabs que Swift. */
@Composable
fun ProfilePillTabs(selectedTab: ProfileTabType, onSelect: (ProfileTabType) -> Unit, modifier: Modifier = Modifier) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp).background(if (dark) Color.White.copy(.08f) else Color.Black.copy(.06f), RoundedCornerShape(50)), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
        ProfileTabType.entries.forEach { tab ->
            val selected = tab == selectedTab
            Row(
                Modifier.weight(1f).background(if (selected) if (dark) Color(0xFFFAF9F6) else Color(0xFF0B1215) else Color.Transparent, RoundedCornerShape(50)).clickable { onSelect(tab) }.padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(tab.icon, null, modifier = Modifier.padding(end = 6.dp).then(Modifier), tint = if (selected) if (dark) Color(0xFF0B1215) else Color.White else ProfileColors.textSecondary())
                Text(stringResource(tab.title), color = if (selected) if (dark) Color(0xFF0B1215) else Color.White else ProfileColors.textSecondary(), fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center)
            }
        }
    }
}

/** Acciones de navegación/sheets: sustituyen bindings iOS sin crear rutas Android ficticias. */
data class ProfileViewActions(
    val onOpenSettings: () -> Unit = {}, val onEditProfile: () -> Unit = {}, val onShowStory: () -> Unit = {},
    val onShowProfileImage: () -> Unit = {}, val onEditProfileNote: () -> Unit = {}, val onShowNotifications: () -> Unit = {},
    val onShowQr: () -> Unit = {}, val onShowIncognito: () -> Unit = {}, val onOpenSavedManager: () -> Unit = {},
    val onOpenMoment: (List<Moment>, Int, com.moments.android.views.profile.core.sections.ProfileMomentZoomFeedKind) -> Unit = { _, _, _ -> },
    val onRefreshSavedVisibility: (Moment, (Boolean) -> Unit) -> Unit = { _, completion -> completion(false) },
    val onRemoveSaved: (String) -> Unit = {},
)

/**
 * Root Compose equivalente a `ProfileView`: dueño del `ProfileViewModel` y, como en iOS,
 * de sus propias hojas (ajustes, editor, QR, incógnito, foto de perfil e historias).
 */
@Composable
fun ProfileView(
    savedState: ProfileSavedContentState = ProfileSavedContentState(),
    actions: ProfileViewActions = ProfileViewActions(),
    modifier: Modifier = Modifier,
) {
    val viewModel = remember { ProfileViewModel() }
    val storyViewModel = remember { StoryViewModel() }
    var profileTab by remember { mutableStateOf(ProfileTabType.MOMENTS) }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var showSettings by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showIncognito by remember { mutableStateOf(false) }
    var showProfileImage by remember { mutableStateOf(false) }
    var showStories by remember { mutableStateOf(false) }
    var settingsRoute by remember { mutableStateOf<String?>(null) }

    val isIncognitoActive by IncognitoModeService.isActive.collectAsState()

    LaunchedEffect(uid) {
        if (uid == null) {
            // Sin usuario no se inventa navegación: mantiene el error de perfil localizado por el host.
            return@LaunchedEffect
        }
        viewModel.fetchProfile(uid)
        viewModel.fetchTaggedMoments(uid)
        storyViewModel.load(listOf(uid), uid)
    }

    ModernProfileContentView(
        viewModel = viewModel, storyViewModel = storyViewModel, selectedTab = profileTab, onSelectTab = { profileTab = it },
        savedState = savedState, onOpenSavedManager = actions.onOpenSavedManager, onOpenMoment = actions.onOpenMoment,
        onRefreshSavedVisibility = actions.onRefreshSavedVisibility, onRemoveSaved = actions.onRemoveSaved,
        onEditProfile = { showEditProfile = true }, onShowStory = { showStories = true },
        onShowProfileImage = { showProfileImage = true },
        onEditProfileNote = actions.onEditProfileNote, onShowNotifications = actions.onShowNotifications,
        onShowQr = { showQr = true }, onShowIncognito = { showIncognito = true },
        onShowSettings = { showSettings = true }, isIncognitoActive = isIncognitoActive, modifier = modifier,
    )

    if (showSettings) {
        ProfileFullScreenSheet(onDismiss = { showSettings = false }) {
            SettingsView(
                onNavigateBack = { showSettings = false },
                onNavigateToRoute = { settingsRoute = it },
            )
        }
    }

    // Destinos de Ajustes: en iOS son `navigationDestination`; aquí, hojas sobre la de ajustes.
    settingsRoute?.let { route ->
        val close = { settingsRoute = null }
        ProfileFullScreenSheet(onDismiss = close) {
            when (route) {
                "best_friends" -> BestFriendsView(onDismiss = close)
                "blocked_users" -> BlockedUsersView(onNavigateBack = close)
                "chat_storage" -> ChatStorageSettingsView(onNavigateBack = close)
                "data_export" -> DataExportView(onNavigateBack = close)
                "mute_settings" -> MuteSettingsView(onNavigateBack = close)
                "password_change" -> PasswordChangeView(onNavigateBack = close)
                "qr_code" -> QRCodeView(user = viewModel.userProfile, onNavigateBack = close)
                "archived_stories" -> ArchivedStoriesView(onNavigateBack = close)
                "privacy_policy" -> PrivacyPolicySheet(onDismiss = close)
                "moderation_reviews" -> AppealStatusView(onBack = close)
                "personal_info" -> PersonalInfoView(
                    username = viewModel.userProfile?.username.orEmpty(),
                    email = viewModel.userProfile?.email.orEmpty(),
                    onNavigateBack = close,
                )
                "notifications_settings" -> NotificationSettingsView(
                    viewModel = remember { SettingsViewModel() },
                    onNavigateBack = close,
                )
                "user_activity" -> com.moments.android.views.settings.UserActivityView(onNavigateBack = close)
                else -> SettingsRouteMissingView(onDismiss = close)
            }
        }
    }

    if (showEditProfile) {
        ProfileFullScreenSheet(onDismiss = { showEditProfile = false }) {
            ModernEditProfileView(
                user = viewModel.userProfile,
                onSave = { bio, website, interests ->
                    viewModel.updateProfileDetails(bio, website, interests)
                    showEditProfile = false
                },
                onDismiss = { showEditProfile = false },
            )
        }
    }

    if (showQr) {
        ProfileFullScreenSheet(onDismiss = { showQr = false }) {
            QRCodeView(user = viewModel.userProfile, onNavigateBack = { showQr = false })
        }
    }

    if (showIncognito) {
        ProfileFullScreenSheet(onDismiss = { showIncognito = false }) {
            IncognitoModeSheet()
        }
    }

    if (showProfileImage) {
        ProfileFullScreenSheet(onDismiss = { showProfileImage = false }) {
            ProfileImageViewer(
                profileImagePath = viewModel.userProfile?.profileImagePath,
                username = viewModel.userProfile?.username.orEmpty(),
                onDismiss = { showProfileImage = false },
            )
        }
    }

    if (showStories && uid != null) {
        ProfileFullScreenSheet(onDismiss = { showStories = false }) {
            StoriesView(
                startAtUserId = uid,
                ringNavigationUserIds = listOf(uid),
                onDismiss = { showStories = false },
            )
        }
    }
}

/** Destino de Ajustes cuya pantalla aún no está portada; no se finge navegación. */
@Composable
private fun SettingsRouteMissingView(onDismiss: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(ProfileColors.background())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            stringResource(R.string.common_close),
            color = ProfileColors.accent,
            modifier = Modifier.clickable(onClick = onDismiss),
        )
    }
}

@Composable
private fun ProfileFullScreenSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        content()
    }
}
