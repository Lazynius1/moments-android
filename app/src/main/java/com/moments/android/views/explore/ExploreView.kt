package com.moments.android.views.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moments.android.R
import com.moments.android.models.Moment
import com.moments.android.models.cache.CachedSearch
import com.moments.android.views.explore.exploresections.ExploreResultsSection
import com.moments.android.views.explore.exploresections.ExploreSuggestionsSection
import com.moments.android.views.feed.maps.DiscoverMapView
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.launch

/**
 * Port de `ExploreView.swift`.
 * Profile sheet / Stories / zoom transitions: stubs honestos hasta sus lotes.
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
    val viewModel = remember { ExploreViewModel() }
    var searchText by remember { mutableStateOf(initialSearchQuery.orEmpty()) }
    var showPrivateProfileAlert by remember { mutableStateOf(false) }
    var showSuggestedUsers by remember { mutableStateOf(false) }
    var showDiscoverMap by remember { mutableStateOf(false) }
    var detailMoment by remember { mutableStateOf<Moment?>(null) }

    LaunchedEffect(Unit) {
        if (viewModel.moments.isEmpty()) {
            viewModel.fetchMomentsByInterests()
        }
        val q = initialSearchQuery?.trim().orEmpty()
        if (q.isNotEmpty()) {
            searchText = q
            viewModel.smartSearch(q)
        }
    }

    LaunchedEffect(searchText) {
        viewModel.smartSearch(searchText)
    }

    Box(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground)
            .padding(contentPadding),
    ) {
        Column(Modifier.fillMaxSize()) {
            ExploreTopBar(
                isDismissable = isDismissable,
                onDismiss = onDismiss,
                onOpenMap = { showDiscoverMap = true },
            )
            ExploreSearchField(
                value = searchText,
                onValueChange = { searchText = it },
                onSubmit = {
                    viewModel.saveSearchRecord(searchText, "text")
                },
                onClear = { searchText = "" },
            )

            when {
                viewModel.isLoading && viewModel.moments.isEmpty() && viewModel.errorMessage == null -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(12.dp))
                            Text(stringResource(R.string.explore_loading), color = colors.secondary)
                        }
                    }
                }
                viewModel.errorMessage != null && viewModel.moments.isEmpty() -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(stringResource(R.string.explore_error_title), fontWeight = FontWeight.SemiBold, color = colors.primary)
                        Spacer(Modifier.height(8.dp))
                        Text(viewModel.errorMessage.orEmpty(), color = colors.secondary)
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = { viewModel.fetchMomentsByInterests() }) {
                            Text(stringResource(R.string.explore_error_retry))
                        }
                    }
                }
                else -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                    ) {
                        if (searchText.isBlank()) {
                            if (viewModel.recentSearches.isNotEmpty()) {
                                RecentSearchesBlock(
                                    searches = viewModel.recentSearches,
                                    onSelect = { search ->
                                        searchText = search.query
                                        viewModel.saveSearchRecord(search.query, search.type, search.targetId)
                                        viewModel.smartSearch(search.query)
                                    },
                                    onDelete = { viewModel.deleteSearch(it) },
                                    onClearAll = { viewModel.clearAllSearches() },
                                )
                            }
                            ExploreSuggestionsSection(
                                users = viewModel.suggestedUsers,
                                userButtonStates = viewModel.userButtonStates,
                                currentUserInterests = viewModel.currentUserInterests,
                                onFollowUser = viewModel::followUser,
                                onUserTap = { user ->
                                    scope.launch {
                                        if (!viewModel.canViewContent(user.id)) {
                                            showPrivateProfileAlert = true
                                        }
                                        // Profile sheet = lote Profile (stub honesto: no-op navegación)
                                    }
                                },
                                onShowMore = { showSuggestedUsers = true },
                                modifier = Modifier.padding(top = 8.dp),
                            )
                            LaunchedEffect(viewModel.suggestedUsers) {
                                viewModel.suggestedUsers.forEach { viewModel.checkUserButtonState(it.id) }
                            }
                            if (viewModel.moments.isNotEmpty()) {
                                Spacer(Modifier.height(16.dp))
                                ExploreMomentsGrid(
                                    moments = viewModel.moments,
                                    onMomentTap = { moment, _, _ ->
                                        scope.launch {
                                            if (viewModel.canViewContent(moment.authorId)) {
                                                detailMoment = moment
                                            } else {
                                                showPrivateProfileAlert = true
                                            }
                                        }
                                    },
                                )
                            }
                        } else {
                            ExploreResultsSection(
                                searchQuery = searchText,
                                users = viewModel.searchedUsers,
                                moments = viewModel.filteredMoments,
                                userButtonStates = viewModel.userButtonStates,
                                onFollowUser = viewModel::followUser,
                                onUserTap = { user ->
                                    viewModel.saveSearchRecord(user.username, "user", user.id)
                                    scope.launch {
                                        if (!viewModel.canViewContent(user.id)) {
                                            showPrivateProfileAlert = true
                                        }
                                    }
                                },
                                onMomentTap = { moment, _, _ ->
                                    scope.launch {
                                        if (viewModel.canViewContent(moment.authorId)) {
                                            detailMoment = moment
                                        } else {
                                            showPrivateProfileAlert = true
                                        }
                                    }
                                },
                            )
                            LaunchedEffect(viewModel.searchedUsers) {
                                viewModel.searchedUsers.forEach { viewModel.checkUserButtonState(it.id) }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPrivateProfileAlert) {
        AlertDialog(
            onDismissRequest = { showPrivateProfileAlert = false },
            title = { Text(stringResource(R.string.explore_private_profile_title)) },
            text = { Text(stringResource(R.string.explore_private_profile_message)) },
            confirmButton = {
                TextButton(onClick = { showPrivateProfileAlert = false }) {
                    Text(stringResource(R.string.common_close))
                }
            },
        )
    }

    if (showSuggestedUsers) {
        Dialog(
            onDismissRequest = { showSuggestedUsers = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            SuggestedUsersView(
                onNavigateBack = { showSuggestedUsers = false },
                onSelectUser = { showSuggestedUsers = false },
            )
        }
    }

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

    detailMoment?.let { moment ->
        Dialog(
            onDismissRequest = { detailMoment = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            val gridMoments = if (searchText.isBlank()) viewModel.moments else viewModel.filteredMoments
            val moments = gridMoments.ifEmpty { listOf(moment) }
            ExploreMomentDetailView(
                moments = moments,
                initialIndex = moments.indexOfFirst { it.id == moment.id }.coerceAtLeast(0),
                onNavigateBack = { detailMoment = null },
            )
        }
    }
}

@Composable
private fun ExploreTopBar(
    isDismissable: Boolean,
    onDismiss: () -> Unit,
    onOpenMap: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isDismissable) {
            IconButton(onClick = onDismiss) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = stringResource(R.string.common_back))
            }
        }
        Text(
            stringResource(R.string.explore_title),
            Modifier.weight(1f).padding(start = if (isDismissable) 0.dp else 12.dp),
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp,
            color = colors.primary,
        )
        IconButton(onClick = onOpenMap) {
            Icon(Icons.Filled.Map, contentDescription = null, tint = colors.accent)
        }
    }
}

@Composable
private fun ExploreSearchField(
    value: String,
    onValueChange: (String) -> Unit,
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
            modifier = Modifier.weight(1f),
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

@Composable
private fun RecentSearchesBlock(
    searches: List<CachedSearch>,
    onSelect: (CachedSearch) -> Unit,
    onDelete: (CachedSearch) -> Unit,
    onClearAll: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.explore_recent_searches_title),
                Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                color = colors.primary,
            )
            TextButton(onClick = onClearAll) {
                Text(stringResource(R.string.explore_recent_searches_clear_all))
            }
        }
        searches.take(8).forEach { search ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(search) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(search.query, Modifier.weight(1f), color = colors.primary)
                IconButton(onClick = { onDelete(search) }) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = colors.secondary)
                }
            }
        }
    }
}
