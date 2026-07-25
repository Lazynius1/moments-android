package com.moments.android.views.profile.userprofile

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.reportes.ReportBottomSheet
import com.moments.android.reportes.ReportTarget
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.utilities.HapticManager
import com.moments.android.views.profile.core.SocialConnectionTab
import com.moments.android.views.profile.core.SocialConnectionsRoute
import com.moments.android.views.profile.core.SocialConnectionsScreen
import com.moments.android.views.profile.core.sections.MomentZoomDestination
import com.moments.android.views.profile.core.sections.MomentZoomDetailDestination
import com.moments.android.views.profile.core.sections.MomentZoomOpener
import com.moments.android.views.profile.core.sections.MomentZoomPresentationKind
import com.moments.android.views.profile.core.sections.ProfileHeaderSkeletonView
import com.moments.android.views.profile.core.sections.ProfileMomentsGridSkeletonView
import com.moments.android.views.profile.userprofile.sections.ProfileImageViewer
import com.moments.android.views.profile.userprofile.sections.UserModernBackgroundView
import com.moments.android.views.profile.userprofile.sections.UserModernBlockedByMeProfileView
import com.moments.android.views.profile.userprofile.sections.UserModernOfflineProfileView
import com.moments.android.views.profile.userprofile.sections.UserModernPrivateProfileView
import com.moments.android.views.profile.userprofile.sections.UserModernPublicProfileView
import com.moments.android.views.profile.userprofile.sections.UserModernUnavailableProfileView
import com.moments.android.views.profile.userprofile.sections.UserRelationshipManagementSheet
import com.moments.android.views.settings.QRCodeView
import com.moments.android.views.story.StoriesView
import kotlinx.coroutines.delay

/**
 * Port de `UserProfileView.swift` — raíz del perfil de otro usuario: enruta entre los estados
 * (cargando / bloqueado por mí / no disponible / sin conexión / privado / público) y aloja las
 * hojas y diálogos (gestión de relación, confirmación de dejar de seguir, foto de perfil, historias,
 * QR, reporte).
 *
 * Puentes conscientes respecto a iOS (documentados también en PORT_FILES.md):
 * - **Mensajería**: no existe `MessagingViewModel.startConversation` en Kotlin todavía, así que
 *   `onOpenMessage` de las secciones queda sin destino hasta que aterrice ese flujo.
 * - **Conexiones sociales**: `SocialConnectionsScreen` de Kotlin exige `ProfileViewModel` (perfil
 *   propio) y no acepta `UserProfileViewModel`; cablearlo exigiría modificar ese archivo, así que
 *   la ruta queda pendiente en vez de forzar una firma inventada.
 * - **Fondo**: iOS usa `EnhancedProfileBackground` con `ProfileTheme`; los temas de perfil están
 *   descartados en el proyecto, así que se usa `UserModernBackgroundView` (foto desenfocada).
 * - El coordinador `ProfileGridHeroTransitionCoordinator` se sustituye por el zoom ya portado
 *   (`MomentZoomOpener` + `MomentZoomDetailDestination`), que es la infraestructura equivalente.
 */

/** Port de `UserProfileColors` (UserProfileView.swift). */
object UserProfileColors {
    val accent = Color(0xFF007AFF)
    val purple = Color(0xFF9B59B6)
    val blue = Color(0xFF6B73FF)

    val textPrimary: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White else Color(0xFF0B1215)
    val textSecondary: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.65f) else Color(0xFF52626A)
    val textTertiary: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.38f) else Color.Black.copy(alpha = 0.32f)
    val borderColor: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.White.copy(alpha = 0.14f) else Color.Black.copy(alpha = 0.10f)
    val shadowColor: Color
        @Composable get() = if (isSystemInDarkTheme()) Color.Black.copy(alpha = 0.30f) else Color.Black.copy(alpha = 0.10f)
    val cardBackground: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF182429) else Color.White.copy(alpha = 0.8f)
    val materialBackground: Color
        @Composable get() = if (isSystemInDarkTheme()) Color(0xFF0B1215).copy(alpha = 0.95f) else Color.White.copy(alpha = 0.95f)
}

/** Port de `UserProfileTabType` (UserProfileView.swift): pestañas del perfil visitado. */
enum class UserProfileTabType(val raw: String, val icon: ImageVector, val titleRes: Int) {
    MOMENTS("moments", Icons.Filled.GridView, R.string.profile_tab_moments),
    TAGGED("tagged", Icons.Filled.PersonPin, R.string.profile_tab_tagged),
}

/**
 * Port de `UserProfilePillTabs`: pastillas con pulgar deslizante, tap y arrastre.
 * El `settleSelection` de iOS (umbral 28% del segmento, tap directo bajo 5pt) se conserva.
 */
@Composable
fun UserProfilePillTabs(
    selectedTab: UserProfileTabType,
    onSelectTab: (UserProfileTabType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = UserProfileTabType.entries
    val currentIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)
    val density = LocalDensity.current
    var dragOffset by remember { mutableStateOf(0.dp) }
    val dark = isSystemInDarkTheme()

    BoxWithConstraints(modifier.height(38.dp)) {
        val totalWidth = maxWidth
        val segmentWidth = (totalWidth - 6.dp) / tabs.size
        val startOffset = -(segmentWidth * (tabs.size - 1) / 2f)
        val baseOffset = startOffset + segmentWidth * currentIndex
        val thumbOffset by animateDpAsState(baseOffset + dragOffset, label = "pillThumb")

        Box(
            Modifier
                .fillMaxSize()
                .momentsChromeGlass(RoundedCornerShape(50), interactive = false),
        )

        Box(
            Modifier
                .align(Alignment.Center)
                .offset(x = thumbOffset)
                .width(segmentWidth)
                .height(31.dp)
                .momentsChromeGlass(
                    RoundedCornerShape(50),
                    interactive = true,
                    tint = if (dark) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.06f),
                ),
        )

        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp)
                .pointerInput(currentIndex, segmentWidth) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            val translation = dragOffset
                            val threshold = minOf(segmentWidth * 0.28f, 36.dp)
                            val target = when {
                                kotlin.math.abs(translation.value) > threshold.value &&
                                    kotlin.math.abs(translation.value) < segmentWidth.value * 0.5f -> {
                                    val direction = if (translation > 0.dp) 1 else -1
                                    (currentIndex + direction).coerceIn(0, tabs.lastIndex)
                                }
                                else -> {
                                    val fractional = (baseOffset + translation - startOffset) / segmentWidth
                                    Math.round(fractional).coerceIn(0, tabs.lastIndex)
                                }
                            }
                            dragOffset = 0.dp
                            val targetTab = tabs[target]
                            if (targetTab != selectedTab) {
                                HapticManager.shared.selection()
                                onSelectTab(targetTab)
                            }
                        },
                        onDragCancel = { dragOffset = 0.dp },
                    ) { _, dragAmount ->
                        val limit = segmentWidth * (tabs.size - 1) / 2f
                        val proposed = baseOffset + dragOffset + with(density) { dragAmount.toDp() }
                        dragOffset = proposed.coerceIn(-limit, limit) - baseOffset
                    }
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEach { tab ->
                val isSelected = tab == selectedTab
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            if (tab != selectedTab) {
                                HapticManager.shared.selection()
                                onSelectTab(tab)
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        tab.icon,
                        contentDescription = null,
                        tint = if (isSelected) UserProfileColors.textPrimary else UserProfileColors.textSecondary,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        stringResource(tab.titleRes),
                        color = if (isSelected) UserProfileColors.textPrimary else UserProfileColors.textSecondary,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
        }
    }
}

/** Port de `UserProfileFloatingTabBar`: pastillas sueltas para el chrome fijado. */
@Composable
fun UserProfileFloatingTabBar(
    selectedTab: UserProfileTabType,
    onSelectTab: (UserProfileTabType) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        UserProfileTabType.entries.forEach { tab ->
            val isSelected = tab == selectedTab
            Row(
                Modifier
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .clickable {
                        if (tab != selectedTab) {
                            HapticManager.shared.selection()
                            onSelectTab(tab)
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    tint = if (isSelected) UserProfileColors.textPrimary else UserProfileColors.textSecondary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    stringResource(tab.titleRes),
                    color = if (isSelected) UserProfileColors.textPrimary else UserProfileColors.textSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UserProfileView(
    userId: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = remember(userId) { UserProfileViewModel(userId) }
    val insets = WindowInsets.systemBars.asPaddingValues()
    val safeAreaTop = insets.calculateTopPadding()
    val safeAreaBottom = insets.calculateBottomPadding()

    var selectedTab by remember { mutableStateOf(UserProfileTabType.MOMENTS) }
    var showingRelationshipSheet by remember { mutableStateOf(false) }
    var showingUnfollowConfirmation by remember { mutableStateOf(false) }
    var showingStories by remember { mutableStateOf(false) }
    var showProfileImageFullscreen by remember { mutableStateOf(false) }
    var showingQrCode by remember { mutableStateOf(false) }
    var showingReport by remember { mutableStateOf(false) }
    var momentZoomDestination by remember { mutableStateOf<MomentZoomDestination?>(null) }
    var socialConnectionsRoute by remember { mutableStateOf<SocialConnectionsRoute?>(null) }
    var openedProfileId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(userId) {
        viewModel.fetchProfile()
        viewModel.checkFollowButtonState()
        // iOS espera 1s antes de registrar la visita (evita contarla al pasar de largo).
        delay(1_000)
        viewModel.registerVisit()
    }

    // Port de `handleFollowAction()`.
    val handleFollowAction: () -> Unit = {
        when (viewModel.followButtonState) {
            FollowButtonState.FOLLOWING -> {
                viewModel.loadRelationshipManagementState()
                showingRelationshipSheet = true
            }
            FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW -> viewModel.followUser(userId)
            FollowButtonState.REQUEST_PENDING_CANCELLABLE -> viewModel.cancelFollowRequest(userId)
            else -> Unit
        }
    }

    Box(modifier.fillMaxSize()) {
        UserModernBackgroundView(
            profileImagePath = viewModel.userProfile?.profileImagePath,
            scrollOffset = 0f,
        )

        when {
            viewModel.isLoading -> {
                Column(Modifier.fillMaxSize().padding(top = safeAreaTop + 12.dp)) {
                    ProfileHeaderSkeletonView()
                    ProfileMomentsGridSkeletonView(Modifier.padding(top = 20.dp))
                }
            }

            viewModel.isBlockedByCurrentUser -> {
                UserModernBlockedByMeProfileView(
                    userProfile = viewModel.userProfile,
                    safeAreaTop = safeAreaTop,
                    safeAreaBottom = safeAreaBottom,
                    onUnblock = { viewModel.unblockUser(userId) },
                    onDismiss = onDismiss,
                )
            }

            viewModel.isProfileUnavailable -> {
                UserModernUnavailableProfileView(
                    safeAreaTop = safeAreaTop,
                    safeAreaBottom = safeAreaBottom,
                    onDismiss = onDismiss,
                )
            }

            viewModel.isOffline && viewModel.userProfile == null -> {
                // Sin caché y sin red: honesto sobre el motivo, en vez de "privado"/"no disponible".
                UserModernOfflineProfileView(
                    safeAreaTop = safeAreaTop,
                    safeAreaBottom = safeAreaBottom,
                    onRetry = { viewModel.fetchProfile() },
                    onDismiss = onDismiss,
                )
            }

            !viewModel.canViewContent -> {
                UserModernPrivateProfileView(
                    userProfile = viewModel.userProfile,
                    userId = userId,
                    followButtonState = viewModel.followButtonState,
                    safeAreaTop = safeAreaTop,
                    safeAreaBottom = safeAreaBottom,
                    onFollowAction = handleFollowAction,
                    onDismiss = onDismiss,
                    onOpenStories = { showingStories = true },
                    onOpenMessage = { },
                )
            }

            else -> {
                UserModernPublicProfileView(
                    viewModel = viewModel,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    safeAreaBottom = safeAreaBottom,
                    onFollowAction = handleFollowAction,
                    onDismiss = onDismiss,
                    onOpenStories = { showingStories = true },
                    onOpenMessage = { },
                    onShowProfileImageFullscreen = { showProfileImageFullscreen = true },
                    onShowQrCode = { showingQrCode = true },
                    onShowReport = { showingReport = true },
                    onOpenSocial = { tab -> socialConnectionsRoute = SocialConnectionsRoute(initialTab = tab) },
                    onOpenMoment = { moments, index ->
                        moments.getOrNull(index)?.let { moment ->
                            MomentZoomOpener.open(
                                moment = moment,
                                moments = moments,
                                initialIndex = index,
                                presentation = MomentZoomPresentationKind.Single,
                                setDestination = { momentZoomDestination = it },
                            )
                        }
                    },
                    onMomentLongPress = { _, _ -> },
                )
            }
        }
    }

    momentZoomDestination?.let { destination ->
        val pool: List<Moment> = if (selectedTab == UserProfileTabType.TAGGED) viewModel.taggedMoments else viewModel.moments
        Dialog(
            onDismissRequest = { momentZoomDestination = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            MomentZoomDetailDestination(
                destination = destination,
                moments = MomentZoomOpener.resolvedMoments(destination, pool),
                onDismiss = { momentZoomDestination = null },
            )
        }
    }

    // Port de `.navigationDestination(item: $socialConnectionsRoute)`: seguidores/seguidos/en común
    // del perfil visitado. `listViewModel` acepta el VM del visitante gracias a `UserListViewModel`
    // (mismo protocolo que iOS); las pestañas excluyen visitas, como en el original.
    socialConnectionsRoute?.let { route ->
        Dialog(
            onDismissRequest = { socialConnectionsRoute = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SocialConnectionsScreen(
                route = route,
                username = viewModel.userProfile?.username.orEmpty(),
                availableTabs = SocialConnectionTab.tabs(includesVisits = false),
                includesVisits = false,
                isOwnProfile = false,
                currentUser = viewModel.viewerProfile,
                inCommonUsers = viewModel.commonConnections,
                followers = viewModel.followers,
                following = viewModel.following,
                mutuals = viewModel.mutuals,
                suggestedUsers = viewModel.suggestedConnectionsForViewer,
                visitTimestamps = emptyMap(),
                listViewModel = viewModel,
                connectionVisibility = viewModel.visibleConnectionTypes,
                onDismiss = { socialConnectionsRoute = null },
                onOpenProfile = { openedProfileId = it },
                onOpenStories = { },
                onOpenChat = { },
                onOpenMoment = { },
            )
        }
    }

    // Abrir el perfil de alguien de la lista: se apila otra instancia, como el push de iOS.
    openedProfileId?.let { nested ->
        Dialog(
            onDismissRequest = { openedProfileId = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            UserProfileView(userId = nested, onDismiss = { openedProfileId = null })
        }
    }

    if (showingRelationshipSheet) {
        ModalBottomSheet(
            onDismissRequest = { showingRelationshipSheet = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        ) {
            UserRelationshipManagementSheet(
                username = viewModel.userProfile?.username ?: stringResource(R.string.user_profile_user),
                profileImagePath = viewModel.userProfile?.profileImagePath,
                isBestFriend = viewModel.isInBestFriends,
                isMuted = viewModel.isMutedByCurrentUser,
                isMutual = viewModel.isMutualRelationship,
                customListCount = viewModel.customListMembershipCount,
                customLists = viewModel.customListsContainingProfile,
                isUpdatingBestFriend = viewModel.isUpdatingBestFriend,
                isUpdatingMute = viewModel.isUpdatingMute,
                isUpdatingLists = viewModel.isUpdatingLists,
                onToggleBestFriend = { viewModel.toggleBestFriend() },
                onToggleMute = { viewModel.toggleMute() },
                onRemoveFromList = { viewModel.removeFromCustomList(it) },
                onUnfollow = {
                    showingRelationshipSheet = false
                    showingUnfollowConfirmation = true
                },
            )
        }
    }

    if (showingUnfollowConfirmation) {
        AlertDialog(
            onDismissRequest = { showingUnfollowConfirmation = false },
            title = { Text(stringResource(R.string.user_profile_unfollow_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        if (viewModel.userProfile?.isPrivate == true) R.string.user_profile_unfollow_confirm_private_message
                        else R.string.user_profile_unfollow_confirm_message,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showingUnfollowConfirmation = false
                    viewModel.unfollowUser(userId)
                    viewModel.refreshProfile()
                }) {
                    Text(stringResource(R.string.user_profile_unfollow_confirm_action), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showingUnfollowConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showProfileImageFullscreen) {
        Dialog(
            onDismissRequest = { showProfileImageFullscreen = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            ProfileImageViewer(
                profileImagePath = viewModel.userProfile?.profileImagePath,
                username = viewModel.userProfile?.username ?: stringResource(R.string.user_profile_user),
                onDismiss = { showProfileImageFullscreen = false },
            )
        }
    }

    if (showingStories) {
        Dialog(
            onDismissRequest = { showingStories = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            StoriesView(
                startAtUserId = userId,
                ringNavigationUserIds = listOf(userId),
                onDismiss = { showingStories = false },
            )
        }
    }

    if (showingQrCode) {
        Dialog(
            onDismissRequest = { showingQrCode = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            QRCodeView(user = viewModel.userProfile, onNavigateBack = { showingQrCode = false })
        }
    }

    if (showingReport) {
        ReportBottomSheet(
            target = ReportTarget.UserTarget(userId = userId, username = viewModel.userProfile?.username),
            onDismiss = { showingReport = false },
        )
    }
}
