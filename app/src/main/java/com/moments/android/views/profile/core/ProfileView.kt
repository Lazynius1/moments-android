package com.moments.android.views.profile.core

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.PersonPin
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.incognito.IncognitoModeService
import com.moments.android.utilities.HapticManager
import com.moments.android.views.explore.toExploreFeedMoment
import com.moments.android.views.feed.core.EditMomentPayload
import com.moments.android.views.profile.core.sections.LocalProfileGridHeroCoordinator
import com.moments.android.views.profile.core.sections.ModernProfileContentView
import com.moments.android.views.profile.core.sections.ProfileConnectionsRoute
import com.moments.android.views.profile.core.sections.ProfileGridHeroDetailLayer
import com.moments.android.views.profile.core.sections.ProfileGridHeroTransitionCoordinator
import com.moments.android.views.profile.core.sections.ProfileGridPreviewEditorView
import com.moments.android.views.profile.core.sections.ProfileMomentZoomDestination
import com.moments.android.views.profile.core.gridPreviewSettings
import com.moments.android.views.profile.core.sections.ProfileOwnZoomSource
import com.moments.android.views.profile.core.sections.ProfileSavedContentState
import com.moments.android.views.profile.core.sections.ProfileMomentZoomDetailDestination
import com.moments.android.views.profile.core.sections.ProfileMomentZoomFeedKind
import com.moments.android.views.profile.core.sections.ProfileMomentZoomNavigation
import com.moments.android.views.profile.core.sections.momentZoomDestination
import com.moments.android.views.profile.editor.ModernEditProfileView
import com.moments.android.views.profile.incognito.IncognitoModeSheet
import com.moments.android.views.profile.momentsview.EditMomentView
import com.moments.android.views.profile.userprofile.sections.ProfileImageViewer
import com.moments.android.views.settings.QRCodeView
import com.moments.android.views.shared.LocalActiveMomentZoomSourceId
import com.moments.android.views.shared.MomentsContainerTransformOverlay
import com.moments.android.views.shared.MomentsModalSheet
import com.moments.android.views.shared.MomentsSharedTransitionLayout
import com.moments.android.views.settings.SettingsView
import com.moments.android.views.story.StoriesView
import com.moments.android.views.story.StoryViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** Paleta que declara `ProfileView.swift`, con equivalente adaptativo Compose. */
object ProfileColors {
    val accent = Color(0xFF007AFF); val purple = Color(0xFF9B59B6); val blue = Color(0xFF6B73FF)
    @Composable fun background() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    @Composable fun secondaryBackground() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color(0xFF172126) else Color(0xFFF0F3F4)
    @Composable fun cardBackground() = background().copy(alpha = .80f)
    @Composable fun materialBackground() = background().copy(alpha = .95f)
    @Composable fun textPrimary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White else Color(0xFF0B1215)
    @Composable fun textSecondary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.64f) else Color(0xFF52626A)
    @Composable fun textTertiary() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.42f) else Color(0xFF7C8A91)
    @Composable fun borderColor() = if (androidx.compose.foundation.isSystemInDarkTheme()) Color.White.copy(.12f) else Color.Black.copy(.10f)
    @Composable fun shadowColor() = textPrimary().copy(alpha = .10f)
}

/** Enum de tabs definido en `ProfileView.swift` (no en el shell). */
enum class ProfileTabType(val title: Int, val icon: ImageVector) {
    MOMENTS(R.string.profile_shell_tab_moments, Icons.Filled.GridView),
    SAVED(R.string.profile_shell_tab_saved, Icons.Filled.Bookmark),
    TAGGED(R.string.profile_shell_tab_tagged, Icons.Filled.PersonPin),
}

enum class ProfileUserListType(val title: Int) { VISITS(R.string.profile_header_visits), FOLLOWERS(R.string.profile_header_followers), FOLLOWING(R.string.profile_header_following), MUTUALS(R.string.profile_header_mutuals) }

/**
 * Equivalente de `ProfilePillTabs` Swift: el thumb puede arrastrarse, hace snap al tab
 * más próximo y emite la misma muesca háptica al cambiar de sección.
 */
@Composable
fun ProfilePillTabs(selectedTab: ProfileTabType, onSelect: (ProfileTabType) -> Unit, modifier: Modifier = Modifier) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val density = LocalDensity.current
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    val tabs = ProfileTabType.entries
    val selectedIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)

    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .height(38.dp),
    ) {
        val insetPx = with(density) { 3.dp.toPx() }
        val segmentPx = ((with(density) { maxWidth.toPx() } - insetPx * 2f) / tabs.size)
            .coerceAtLeast(1f)
        val clampedDrag = dragOffsetPx.coerceIn(-selectedIndex * segmentPx, (tabs.lastIndex - selectedIndex) * segmentPx)
        val targetThumbPx = selectedIndex * segmentPx + clampedDrag
        val thumbPx by animateFloatAsState(
            targetValue = targetThumbPx,
            animationSpec = tween(durationMillis = 180),
            label = "profileTabThumb",
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(if (dark) Color.White.copy(.08f) else Color.Black.copy(.06f), RoundedCornerShape(50))
                .pointerInput(selectedTab) {
                    var accumulated = 0f
                    detectDragGestures(
                        onDragStart = { accumulated = 0f },
                        onDrag = { change, amount ->
                            change.consume()
                            accumulated += amount.x
                            dragOffsetPx = accumulated.coerceIn(
                                -selectedIndex * segmentPx,
                                (tabs.lastIndex - selectedIndex) * segmentPx,
                            )
                        },
                        onDragCancel = { dragOffsetPx = 0f },
                        onDragEnd = {
                            val rawIndex = ((selectedIndex * segmentPx + accumulated) / segmentPx).roundToInt()
                                .coerceIn(0, tabs.lastIndex)
                            val next = tabs[rawIndex]
                            if (next != selectedTab) {
                                HapticManager.shared.selection()
                                onSelect(next)
                            }
                            dragOffsetPx = 0f
                        },
                    )
                },
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset((insetPx + thumbPx).roundToInt(), 0) }
                    .height(31.dp)
                    .fillMaxWidth(1f / tabs.size)
                    .background(
                        if (dark) Color(0xFFFAF9F6) else Color(0xFF0B1215),
                        RoundedCornerShape(50),
                    ),
            )
            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(0.dp)) {
                tabs.forEach { tab ->
                    val selected = tab == selectedTab
                    Row(
                        Modifier
                            .weight(1f)
                            .clickable {
                                if (!selected) HapticManager.shared.selection()
                                onSelect(tab)
                            }
                            .padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(tab.icon, null, modifier = Modifier.padding(end = 6.dp), tint = if (selected) if (dark) Color(0xFF0B1215) else Color.White else ProfileColors.textSecondary())
                        Text(stringResource(tab.title), color = if (selected) if (dark) Color(0xFF0B1215) else Color.White else ProfileColors.textSecondary(), fontSize = 12.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

/** Versión flotante que Swift muestra cuando los tabs quedan fijados bajo el chrome. */
@Composable
fun ProfileFloatingTabBar(
    selectedTab: ProfileTabType,
    onSelect: (ProfileTabType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    Row(
        modifier.padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ProfileTabType.entries.forEach { tab ->
            val selected = tab == selectedTab
            Row(
                Modifier
                    .background(
                        if (selected) {
                            if (dark) Color(0xFFFAF9F6) else Color(0xFF0B1215)
                        } else {
                            if (dark) Color.White.copy(.10f) else Color.White.copy(.88f)
                        },
                        RoundedCornerShape(50),
                    )
                    .clickable {
                        if (!selected) HapticManager.shared.selection()
                        onSelect(tab)
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    tab.icon,
                    contentDescription = null,
                    modifier = Modifier.padding(end = 6.dp),
                    tint = if (selected) {
                        if (dark) Color(0xFF0B1215) else Color.White
                    } else {
                        ProfileColors.textSecondary()
                    },
                )
                Text(
                    stringResource(tab.title),
                    color = if (selected) {
                        if (dark) Color(0xFF0B1215) else Color.White
                    } else {
                        ProfileColors.textSecondary()
                    },
                    fontSize = 12.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                )
            }
        }
    }
}

/** Acciones de navegación/sheets: sustituyen bindings iOS sin crear rutas Android ficticias. */
data class ProfileViewActions(
    val onOpenSettings: () -> Unit = {}, val onEditProfile: () -> Unit = {}, val onShowStory: () -> Unit = {},
    val onShowProfileImage: () -> Unit = {}, val onShowNotifications: () -> Unit = {},
    val onShowQr: () -> Unit = {}, val onShowIncognito: () -> Unit = {}, val onOpenSavedManager: () -> Unit = {},
    val onOpenMoment: (List<Moment>, Int, com.moments.android.views.profile.core.sections.ProfileMomentZoomFeedKind) -> Unit = { _, _, _ -> },
    val onRefreshSavedVisibility: (Moment, (Boolean) -> Unit) -> Unit = { _, completion -> completion(false) },
    val onRemoveSaved: (String) -> Unit = {},
)

/**
 * Root Compose equivalente a `ProfileView`: dueño del `ProfileViewModel` y, como en iOS,
 * de sus propias hojas (ajustes, editor, QR, incógnito, foto de perfil e historias).
 *
 * Puentes: temas de perfil 🚫; hero transition → [ProfileGridHeroDetailLayer];
 * grid preview editor → [ProfileGridPreviewEditorView] sheet large.
 */
@Composable
fun ProfileView(
    savedState: ProfileSavedContentState = ProfileSavedContentState(),
    actions: ProfileViewActions = ProfileViewActions(),
    modifier: Modifier = Modifier,
    /** ≡ iOS `.toolbar(.hidden, for: .tabBar)` en Settings / edit / moment zoom. */
    onSuppressTabBarChange: (Boolean) -> Unit = {},
) {
    val viewModel = remember { ProfileViewModel() }
    val storyViewModel = remember { StoryViewModel() }
    val heroCoordinator = remember { ProfileGridHeroTransitionCoordinator() }
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }

    var profileTab by remember { mutableStateOf(ProfileTabType.MOMENTS) }
    var uid by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid) }
    val unauthenticatedMessage = stringResource(R.string.messaging_error_not_authenticated)

    var showSettings by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }
    var showQr by remember { mutableStateOf(false) }
    var showIncognito by remember { mutableStateOf(false) }
    var showProfileImage by remember { mutableStateOf(false) }
    var showStories by remember { mutableStateOf(false) }
    var momentDestination by remember { mutableStateOf<ProfileMomentZoomDestination?>(null) }
    var openConnectionsRoute by remember { mutableStateOf<ProfileConnectionsRoute?>(null) }

    val suppressTabBar = showSettings || showEditProfile || momentDestination != null
    LaunchedEffect(suppressTabBar) {
        onSuppressTabBarChange(suppressTabBar)
    }
    DisposableEffect(Unit) {
        onDispose { onSuppressTabBarChange(false) }
    }
    var editingMoment by remember { mutableStateOf<Moment?>(null) }
    var pendingDeleteMoment by remember { mutableStateOf<Moment?>(null) }
    var gridPreviewMoment by remember { mutableStateOf<Moment?>(null) }

    val isIncognitoActive by IncognitoModeService.isActive.collectAsState()

    DisposableEffect(Unit) {
        IncognitoModeService.loadState()
        val auth = FirebaseAuth.getInstance()
        val listener = FirebaseAuth.AuthStateListener { uid = it.currentUser?.uid }
        auth.addAuthStateListener(listener)
        onDispose {
            auth.removeAuthStateListener(listener)
            heroCoordinator.resetToIdle()
        }
    }

    LaunchedEffect(Unit) {
        NavigationEventBus.events.collect { event ->
            if (event is CoordinatorNavigationEvent.ShowProfileVisits) {
                openConnectionsRoute = ProfileConnectionsRoute.VISITS
            }
        }
    }

    LaunchedEffect(uid) {
        val currentUid = uid
        if (currentUid == null) {
            viewModel.isLoading = false
            viewModel.errorMessage = unauthenticatedMessage
            return@LaunchedEffect
        }
        viewModel.fetchProfile(currentUid)
        // ≡ iOS: fetchStories(includeConnections: false) + checkActiveStories
        storyViewModel.fetchStories(forUserId = currentUid, includeConnections = false)
    }

    LaunchedEffect(heroCoordinator, viewModel) {
        heroCoordinator.onEdit = { editingMoment = it }
        heroCoordinator.onDelete = { pendingDeleteMoment = it }
        heroCoordinator.onArchive = { viewModel.archiveMomentLocally(it) }
        heroCoordinator.onPin = { moment, shouldPin, replaceOldest ->
            viewModel.handleGridPin(moment, shouldPin, replaceOldest)
        }
        heroCoordinator.onAdjustPreview = { moment ->
            if (!moment.previewImageURLString.isNullOrBlank()) {
                gridPreviewMoment = moment
            }
        }
        heroCoordinator.openZoomDetail = { destination ->
            momentDestination = destination
        }
        heroCoordinator.clearZoomNavigation = { momentDestination = null }
    }

    // ≡ iOS Namespace + navigationTransition(.zoom) → M3 container transform (SharedTransition)
    MomentsSharedTransitionLayout(modifier.fillMaxSize()) {
        CompositionLocalProvider(
            LocalProfileGridHeroCoordinator provides heroCoordinator,
            LocalActiveMomentZoomSourceId provides momentDestination?.zoomSourceID,
        ) {
            Box(Modifier.fillMaxSize()) {
                ModernProfileContentView(
                    viewModel = viewModel,
                    storyViewModel = storyViewModel,
                    selectedTab = profileTab,
                    onSelectTab = { profileTab = it },
                    savedState = savedState,
                    onOpenSavedManager = actions.onOpenSavedManager,
                    onOpenMoment = { moments, index, feedKind ->
                        moments.getOrNull(index)?.let { moment ->
                            momentDestination = ProfileMomentZoomDestination(
                                zoomSourceID = ProfileMomentZoomNavigation.sourceID(moment, index),
                                initialIndex = index,
                                initialMomentId = moment.id,
                                feedKind = feedKind,
                            )
                            actions.onOpenMoment(moments, index, feedKind)
                        }
                    },
                    onRefreshSavedVisibility = actions.onRefreshSavedVisibility,
                    onRemoveSaved = actions.onRemoveSaved,
                    onEditProfile = { actions.onEditProfile(); showEditProfile = true },
                    onShowStory = { actions.onShowStory(); showStories = true },
                    onShowProfileImage = { actions.onShowProfileImage(); showProfileImage = true },
                    onShowNotifications = actions.onShowNotifications,
                    onShowQr = { actions.onShowQr(); showQr = true },
                    onShowIncognito = { actions.onShowIncognito(); showIncognito = true },
                    onShowSettings = { actions.onOpenSettings(); showSettings = true },
                    isIncognitoActive = isIncognitoActive,
                    onMomentLongPress = { moment, index, _ ->
                        heroCoordinator.openMenu(moment, index)
                    },
                    openConnectionsRoute = openConnectionsRoute,
                    onOpenConnectionsRouteConsumed = { openConnectionsRoute = null },
                )

                // ≡ ProfileGridHeroDetailLayer (flying hero + menú owner)
                ProfileGridHeroDetailLayer(
                    coordinator = heroCoordinator,
                    moments = when (profileTab) {
                        ProfileTabType.TAGGED -> viewModel.taggedMoments
                        else -> viewModel.moments
                    },
                    zoomFeedKind = when (profileTab) {
                        ProfileTabType.TAGGED -> ProfileMomentZoomFeedKind.TAGGED_MOMENTS
                        else -> ProfileMomentZoomFeedKind.OWN_MOMENTS
                    },
                )

                // ≡ navigationDestination + .navigationTransition(.zoom "settings-view")
                MomentsContainerTransformOverlay(visible = showSettings) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .momentZoomDestination(ProfileOwnZoomSource.SETTINGS),
                    ) {
                        SettingsView(onNavigateBack = { showSettings = false })
                    }
                }

                // ≡ navigationDestination + .navigationTransition(.zoom "edit-profile-view")
                MomentsContainerTransformOverlay(visible = showEditProfile) {
                    ModernEditProfileView(
                        user = viewModel.userProfile,
                        onSave = { bio, website, interests ->
                            viewModel.updateProfileDetails(bio, website, interests)
                            showEditProfile = false
                        },
                        onDismiss = { showEditProfile = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .momentZoomDestination(ProfileOwnZoomSource.EDIT_PROFILE),
                    )
                }

                // ≡ ProfileMomentZoomDetailDestination — caller-managed, sin AV anidado
                momentDestination?.let { destination ->
                    val liveMoments = when (destination.feedKind) {
                        ProfileMomentZoomFeedKind.OWN_MOMENTS -> viewModel.moments
                        ProfileMomentZoomFeedKind.TAGGED_MOMENTS -> viewModel.taggedMoments
                        ProfileMomentZoomFeedKind.SAVED_MOMENTS -> savedState.moments
                        else -> viewModel.moments
                    }
                    ProfileMomentZoomDetailDestination(
                        destination = destination,
                        moments = liveMoments,
                        onDismiss = { momentDestination = null },
                    )
                }
            }
        }
    }

    if (showQr) {
        MomentsModalSheet(onDismissRequest = { showQr = false }, largeOnly = true) {
            QRCodeView(user = viewModel.userProfile, onNavigateBack = { showQr = false })
        }
    }

    if (showIncognito) {
        // ≡ iOS `.presentationDetents([.fraction(0.64), .large])` → medium+large
        MomentsModalSheet(onDismissRequest = { showIncognito = false }, largeOnly = false) {
            IncognitoModeSheet()
        }
    }

    if (showProfileImage) {
        MomentsModalSheet(
            onDismissRequest = { showProfileImage = false },
            largeOnly = true,
            showDragHandle = false,
        ) {
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
                startWithUserId = uid,
                onDismiss = { showStories = false },
            )
        }
    }

    editingMoment?.let { moment ->
        MomentsModalSheet(
            onDismissRequest = { editingMoment = null },
            largeOnly = true,
        ) {
            EditMomentView(
                moment = moment.toExploreFeedMoment(),
                onSave = { payload ->
                    scope.launch {
                        updateOwnMoment(firestore, viewModel, moment, payload)
                        editingMoment = null
                    }
                },
                onDismiss = { editingMoment = null },
            )
        }
    }

    // ≡ iOS `.sheet(item: $gridPreviewMoment)` + `.presentationDetents([.large])`
    gridPreviewMoment?.let { moment ->
        val imageUrl = moment.previewImageURLString ?: return@let
        MomentsModalSheet(
            onDismissRequest = { gridPreviewMoment = null },
            largeOnly = true,
        ) {
            ProfileGridPreviewEditorView(
                imageUrl = imageUrl,
                initialSettings = moment.gridPreviewSettings,
                onDismiss = { gridPreviewMoment = null },
                onSave = { settings ->
                    viewModel.saveGridPreview(moment, settings)
                },
            )
        }
    }

    pendingDeleteMoment?.let { moment ->
        AlertDialog(
            onDismissRequest = { pendingDeleteMoment = null },
            title = { Text(stringResource(R.string.context_menu_delete_moment)) },
            text = { Text(stringResource(R.string.feed_delete_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteMomentLocally(moment)
                        pendingDeleteMoment = null
                    },
                ) {
                    Text(stringResource(R.string.common_delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteMoment = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

private suspend fun updateOwnMoment(
    firestore: FirestoreService,
    viewModel: ProfileViewModel,
    moment: Moment,
    payload: EditMomentPayload,
) {
    val momentId = moment.id ?: return
    runCatching {
        firestore.updateMomentDetails(
            userId = moment.authorId,
            momentId = momentId,
            content = payload.content,
            audience = payload.audience,
            customListId = payload.customListId,
            customViewers = payload.customViewers,
            taggedUsers = payload.taggedUsers,
            mentionedUsers = payload.mentionedUsers,
            location = payload.locationName.ifBlank { null },
            locationCoordinate = if (payload.locationLatitude != null && payload.locationLongitude != null) {
                Moment.LocationCoordinate(payload.locationLatitude, payload.locationLongitude)
            } else {
                null
            },
            mediaItems = payload.mediaItems,
        )
        viewModel.refreshProfile()
    }
}

/** Host a pantalla completa sobre el perfil (ajustes, editor, etc.). */
@Composable
private fun ProfileFullScreenSheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val isDark = isSystemInDarkTheme()
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        val view = LocalView.current
        DisposableEffect(view, isDark) {
            val window = (view.parent as DialogWindowProvider).window
            val navigationBarColor =
                if (isDark) Color(0xFF070B0D) else Color(0xFFF0EFEC)
            WindowCompat.setDecorFitsSystemWindows(window, false)
            @Suppress("DEPRECATION")
            window.statusBarColor = Color.Transparent.toArgb()
            @Suppress("DEPRECATION")
            window.navigationBarColor = navigationBarColor.toArgb()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                window.isNavigationBarContrastEnforced = false
            }
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !isDark
                isAppearanceLightNavigationBars = !isDark
            }
            onDispose { }
        }
        content()
    }
}
