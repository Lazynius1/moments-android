package com.moments.android.views.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.Moment
import com.moments.android.models.cache.CachedSearch
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.MomentRefreshOverlayHost
import com.moments.android.views.components.momentRefresh
import com.moments.android.services.content.ForYouPreferences
import com.moments.android.views.explore.exploresections.ExplorePagingFooter
import com.moments.android.views.explore.exploresections.ExploreErrorStateView
import com.moments.android.views.explore.exploresections.ExploreLoadingStateView
import com.moments.android.views.explore.exploresections.ExploreResultsSection
import com.moments.android.views.explore.exploresections.ExploreSuggestionsSection
import com.moments.android.views.feed.core.FeedProfileSheetRoute
import com.moments.android.views.feed.maps.DiscoverMapView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.core.sections.MomentZoomDestination
import com.moments.android.views.profile.core.sections.MomentZoomDetailDestination
import com.moments.android.views.profile.core.sections.MomentZoomOpener
import com.moments.android.views.profile.core.sections.MomentZoomPresentationKind
import com.moments.android.views.profile.core.sections.UserProfileZoomNavigationHost
import com.moments.android.views.shared.AppErrorBanner
import com.moments.android.views.shared.MomentsContainerTransformOverlay
import com.moments.android.views.shared.MomentsSharedTransitionLayout
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Port de `ExploreView.swift`.
 * Profile → `UserProfileView`; momento → `MomentZoomDetailDestination` (explorer / single).
 */
@Composable
fun ExploreView(
    initialSearchQuery: String? = null,
    isDismissable: Boolean = false,
    onDismiss: () -> Unit = {},
    contentPadding: PaddingValues = PaddingValues(0.dp),
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    val layoutDirection = LocalLayoutDirection.current
    val viewModel = remember { ExploreViewModel() }
    var searchText by remember { mutableStateOf(initialSearchQuery.orEmpty()) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var showPrivateProfileAlert by remember { mutableStateOf(false) }
    var showSuggestedUsers by remember { mutableStateOf(false) }
    var showDiscoverMap by remember { mutableStateOf(false) }
    var selectedProfileRoute by remember { mutableStateOf<FeedProfileSheetRoute?>(null) }
    var zoomDestination by remember { mutableStateOf<MomentZoomDestination?>(null) }

    fun openProfile(userId: String) {
        val trimmed = userId.trim()
        if (trimmed.isEmpty()) return
        selectedProfileRoute = FeedProfileSheetRoute(trimmed)
        // iOS: checkCanViewContent con resultado ignorado (solo prefetch/cache)
        scope.launch { viewModel.canViewContent(trimmed) }
    }

    fun openMomentZoom(
        moment: Moment,
        index: Int,
        sourceMoments: List<Moment>,
        presentation: MomentZoomPresentationKind,
        zoomIDPrefix: String,
    ) {
        val resolvedIndex = sourceMoments.indexOfFirst { it.id == moment.id }
            .takeIf { it >= 0 } ?: index
        MomentZoomOpener.open(
            moment = moment,
            moments = sourceMoments,
            initialIndex = resolvedIndex,
            presentation = presentation,
            setDestination = { zoomDestination = it },
            zoomIDPrefix = zoomIDPrefix,
        )
    }

    LaunchedEffect(Unit) {
        val q = initialSearchQuery?.trim().orEmpty()
        if (q.isNotEmpty()) {
            searchText = q
            if (viewModel.moments.isNotEmpty()) {
                viewModel.smartSearch(q)
            } else {
                delay(500)
                viewModel.smartSearch(q)
            }
        }
        if (viewModel.moments.isEmpty()) {
            viewModel.fetchMomentsByInterests()
        }
    }

    LaunchedEffect(searchText) {
        viewModel.smartSearch(searchText)
    }

    MomentsSharedTransitionLayout(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground)
            // Solo bottom/horizontal del host — el top lo gestiona ExploreTopBar (sin doble statusBars).
            .padding(
                start = contentPadding.calculateLeftPadding(layoutDirection),
                end = contentPadding.calculateRightPadding(layoutDirection),
                bottom = contentPadding.calculateBottomPadding(),
            ),
    ) {
    UserProfileZoomNavigationHost(
        profileRoute = selectedProfileRoute,
        onProfileRouteChange = { selectedProfileRoute = it },
        modifier = Modifier.fillMaxSize(),
    ) { _ ->
    Box(
        Modifier
            .fillMaxSize()
            .momentRefresh {
                if (searchText.isBlank()) viewModel.refreshAllContent() else viewModel.retrySearch()
                delay(900)
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            ExploreTopBar(
                isDismissable = isDismissable,
                onDismiss = {
                    HapticManager.shared.lightImpact(view)
                    onDismiss()
                },
                onOpenMap = {
                    HapticManager.shared.mediumImpact(view)
                    showDiscoverMap = true
                },
            )
            ExploreSearchField(
                value = searchText,
                onValueChange = { searchText = it },
                onFocusChange = { isSearchFocused = it },
                onSubmit = {
                    viewModel.saveSearchRecord(searchText, "text")
                },
                onClear = { searchText = "" },
            )

            // ≡ iOS `.searchSuggestions` — solo con query vacía
            if (searchText.isBlank() && isSearchFocused && viewModel.recentSearches.isNotEmpty()) {
                ExploreRecentSearchSuggestions(
                    searches = viewModel.recentSearches,
                    socialStatus = { id -> viewModel.getSocialStatus(id) },
                    onSelect = { search ->
                        searchText = search.query
                        viewModel.saveSearchRecord(search.query, search.type, search.targetId)
                        viewModel.smartSearch(search.query)
                        isSearchFocused = false
                    },
                    onDelete = { viewModel.deleteSearch(it) },
                    onClearAll = { viewModel.clearAllSearches() },
                    onProfileTap = { openProfile(it) },
                )
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    searchText.isBlank() && viewModel.isLoading && viewModel.moments.isEmpty() && viewModel.errorMessage == null -> {
                        ExploreLoadingStateView(Modifier.fillMaxSize())
                    }
                    searchText.isBlank() && viewModel.errorMessage != null && viewModel.moments.isEmpty() -> {
                        ExploreErrorStateView(
                            message = viewModel.errorMessage.orEmpty(),
                            onRetry = { viewModel.fetchMomentsByInterests() },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            if (searchText.isBlank()) {
                                ExploreSuggestionsSection(
                                    users = viewModel.suggestedUsers,
                                    onUserTap = { user ->
                                        openProfile(user.id)
                                    },
                                    onShowMore = { showSuggestedUsers = true },
                                )
                                LaunchedEffect(viewModel.suggestedUsers) {
                                    viewModel.suggestedUsers.forEach { viewModel.checkUserButtonState(it.id) }
                                    viewModel.filterFollowedUsersFromSuggestions()
                                }
                                if (viewModel.moments.isNotEmpty()) {
                                    ExploreMomentsGrid(
                                        moments = viewModel.moments,
                                        onMomentTap = { moment, index, source ->
                                            scope.launch {
                                                if (viewModel.canViewContent(moment.authorId)) {
                                                    ForYouPreferences.recordOpenedMoment(moment.toExploreFeedMoment())
                                                    openMomentZoom(
                                                        moment = moment,
                                                        index = index,
                                                        sourceMoments = source,
                                                        presentation = MomentZoomPresentationKind.Explorer,
                                                        zoomIDPrefix = "explore",
                                                    )
                                                } else {
                                                    showPrivateProfileAlert = true
                                                }
                                            }
                                        },
                                        modifier = Modifier.padding(bottom = 80.dp),
                                    )
                                }
                                ExplorePagingFooter(viewModel.isLoadingMoreExplore, viewModel.explorePageFailed,
                                    viewModel.hasMoreExplore, viewModel::loadMoreExplore, viewModel::loadMoreExplore)
                            } else {
                                ExploreResultsSection(
                                    searchQuery = searchText,
                                    isLoading = viewModel.isSearching,
                                    failed = viewModel.searchFailed,
                                    hasMore = viewModel.hasMoreSearchResults,
                                    filter = if (searchText.startsWith("#")) "hashtag" else if (searchText.startsWith("@")) "username" else viewModel.searchFilter,
                                    onFilter = { filter ->
                                        if (searchText.startsWith("#") || searchText.startsWith("@")) searchText = searchText.drop(1)
                                        viewModel.setSearchFilter(filter)
                                    },
                                    onLoadMore = viewModel::loadMoreSearchResults,
                                    onRetry = viewModel::retrySearch,
                                    users = viewModel.searchedUsers,
                                    moments = viewModel.filteredMoments,
                                    userButtonStates = viewModel.userButtonStates,
                                    currentUserInterests = viewModel.currentUserInterests,
                                    onFollowUser = viewModel::followUser,
                                    onUserTap = { user ->
                                        viewModel.saveSearchRecord(user.username, "user", user.id)
                                        openProfile(user.id)
                                    },
                                    onMomentTap = { moment, index, source ->
                                        scope.launch {
                                            if (viewModel.canViewContent(moment.authorId)) {
                                                ForYouPreferences.recordOpenedMoment(moment.toExploreFeedMoment())
                                                openMomentZoom(
                                                    moment = moment,
                                                    index = index,
                                                    sourceMoments = source,
                                                    presentation = MomentZoomPresentationKind.Single,
                                                    zoomIDPrefix = "explore-search",
                                                )
                                            } else {
                                                showPrivateProfileAlert = true
                                            }
                                        }
                                    },
                                )
                                LaunchedEffect(viewModel.searchedUsers, viewModel.filteredMoments) {
                                    viewModel.searchedUsers.forEach { viewModel.checkUserButtonState(it.id) }
                                    viewModel.filteredMoments.forEach { viewModel.loadAuthorProfile(it.authorId) }
                                }
                            }
                        }
                    }
                }

                // ≡ AppErrorBanner overlay when content already loaded
                val bannerError = viewModel.errorMessage
                if (bannerError != null && viewModel.moments.isNotEmpty()) {
                    AppErrorBanner(
                        message = bannerError,
                        onRetry = { viewModel.fetchMomentsByInterests() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
        }

        MomentRefreshOverlayHost(Modifier.align(Alignment.TopCenter))

        // ≡ MomentZoomDetailDestination + navigationTransition(.zoom)
        MomentsContainerTransformOverlay(visible = zoomDestination != null) {
            val destination = zoomDestination
            if (destination != null) {
                val pool = if (searchText.isBlank()) viewModel.moments else viewModel.filteredMoments
                MomentZoomDetailDestination(
                    destination = destination,
                    moments = MomentZoomOpener.resolvedMoments(destination, pool),
                    onDismiss = { zoomDestination = null },
                )
            }
        }
    }
    MomentsContainerTransformOverlay(visible = showSuggestedUsers) {
        SuggestedUsersView(
            onNavigateBack = { showSuggestedUsers = false },
            modifier = Modifier.fillMaxSize(),
        )
    }
    } // UserProfileZoomNavigationHost
    } // MomentsSharedTransitionLayout

    if (showPrivateProfileAlert) {
        AlertDialog(
            onDismissRequest = { showPrivateProfileAlert = false },
            title = { Text(stringResource(R.string.explore_private_profile_title)) },
            text = { Text(stringResource(R.string.explore_private_profile_message)) },
            confirmButton = {
                TextButton(onClick = { showPrivateProfileAlert = false }) {
                    Text(stringResource(R.string.common_ok))
                }
            },
        )
    }

    // ≡ iOS navigationDestination DiscoverMapView (fullscreen)
    if (showDiscoverMap) {
        Dialog(
            onDismissRequest = { showDiscoverMap = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            DiscoverMapView(
                onDismiss = { showDiscoverMap = false },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * ≡ iOS large title + toolbar: fila de acciones (back/mapa) y debajo el título.
 * Safe area una sola vez aquí (el tab host ya no pasa top inset).
 */
@Composable
private fun ExploreTopBar(
    isDismissable: Boolean,
    onDismiss: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp),
    ) {
        // Nivel 1 — toolbar (mapa / back), pegado a status bar
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isDismissable) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = stringResource(R.string.common_back),
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onOpenMap) {
                Icon(Icons.Filled.Map, contentDescription = null, tint = Color(0xFF0A84FF))
            }
        }
        // Nivel 2 — large title
        Text(
            stringResource(R.string.explore_title),
            Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, bottom = 4.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = colors.primary,
        )
    }
}

@Composable
private fun ExploreSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onClear: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.06f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = colors.secondary, modifier = Modifier.padding(end = 8.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(color = colors.primary, fontSize = 16.sp),
            cursorBrush = SolidColor(colors.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { onFocusChange(it.isFocused) },
            decorationBox = { inner ->
                if (value.isEmpty()) {
                    Text(stringResource(R.string.explore_search_placeholder), color = colors.secondary)
                }
                inner()
            },
        )
        if (value.isNotEmpty()) {
            IconButton(onClick = onClear, modifier = Modifier.height(24.dp)) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = colors.secondary)
            }
        }
    }
}

/** Port de `exploreRecentSearchSuggestions` + header. */
@Composable
private fun ExploreRecentSearchSuggestions(
    searches: List<CachedSearch>,
    socialStatus: (String) -> ExploreSocialStatus?,
    onSelect: (CachedSearch) -> Unit,
    onDelete: (CachedSearch) -> Unit,
    onClearAll: () -> Unit,
    onProfileTap: (String) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.explore_recent_searches_title),
                Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = colors.primary,
            )
            TextButton(onClick = onClearAll) {
                Text(
                    stringResource(R.string.explore_recent_searches_clear_all),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = colors.primary,
                )
            }
        }
        searches.forEach { search ->
            ExploreRecentSearchRow(
                search = search,
                socialStatus = search.targetId?.let { socialStatus(it) },
                typeIcon = searchTypeIcon(search.type),
                onSelect = { onSelect(search) },
                onDelete = { onDelete(search) },
                onProfileTap = onProfileTap,
            )
        }
    }
}

/** Port de `ExploreRecentSearchRow`. */
@Composable
private fun ExploreRecentSearchRow(
    search: CachedSearch,
    socialStatus: ExploreSocialStatus?,
    typeIcon: ImageVector,
    onSelect: () -> Unit,
    onDelete: () -> Unit,
    onProfileTap: (String) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            Modifier
                .weight(1f)
                .clickable(onClick = onSelect),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (search.type == "user" && !search.targetId.isNullOrBlank()) {
                StoryRingAvatarView(
                    userId = search.targetId,
                    size = 32.dp,
                    lineWidth = 2.dp,
                    onTap = { onProfileTap(search.targetId) },
                )
            } else {
                Box(
                    Modifier
                        .size(32.dp)
                        .momentsChromeGlass(CircleShape, interactive = false),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(typeIcon, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(14.dp))
                }
            }
            Column {
                Text(
                    search.query,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                socialStatus?.let { status ->
                    Text(
                        stringResource(
                            when (status) {
                                ExploreSocialStatus.MUTUAL -> R.string.social_mutual
                                ExploreSocialStatus.FOLLOWS_YOU -> R.string.social_follows_you
                                ExploreSocialStatus.FOLLOWING -> R.string.social_following
                            },
                        ),
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp,
                        color = colors.secondary,
                    )
                }
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = colors.primary, modifier = Modifier.size(15.dp))
        }
    }
}

private fun searchTypeIcon(type: String): ImageVector = when (type) {
    "hashtag" -> Icons.Filled.Tag
    "location" -> Icons.Filled.Place
    "user" -> Icons.Filled.Person
    else -> Icons.Filled.Schedule
}
