package com.moments.android.views.profile.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.models.StalkerAlertView
import com.moments.android.models.VisitsTabContent
import com.moments.android.models.VisitsViewModel
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.privacy.VisibleConnectionTypes
import com.moments.android.views.profile.userprofile.UserProfileViewModel
import com.moments.android.views.settings.ActivityCollapsibleFilterScroll
import com.moments.android.views.settings.SettingsToolbarBackButton
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Date

/** Port de `SocialConnectionTab` (SocialConnectionsView.swift). */
enum class SocialConnectionTab(val titleRes: Int) {
    VISITS(R.string.social_connections_tab_visits),
    IN_COMMON(R.string.social_connections_tab_in_common),
    FOLLOWERS(R.string.social_connections_tab_followers),
    FOLLOWING(R.string.social_connections_tab_following),
    MUTUALS(R.string.social_connections_tab_mutuals);

    companion object {
        val ownProfileTabs = listOf(VISITS, FOLLOWERS, FOLLOWING, MUTUALS)

        /** `tabs(for:includesVisits:)` — el param visibility de iOS se ignora igual. */
        fun tabs(includesVisits: Boolean): List<SocialConnectionTab> =
            if (includesVisits) {
                listOf(VISITS, FOLLOWERS, FOLLOWING, MUTUALS)
            } else {
                listOf(IN_COMMON, FOLLOWERS, FOLLOWING)
            }
    }
}

data class SocialConnectionsRoute(val initialTab: SocialConnectionTab)

data class SocialConnectionTabItem(val tab: SocialConnectionTab, val count: Int)

@Composable
fun SocialConnectionTabItem.titleText(): String = when (tab) {
    SocialConnectionTab.VISITS -> stringResource(
        if (count == 1) R.string.visits_visitor_count_single else R.string.visits_visitor_count_multiple,
        count,
    )
    else -> stringResource(tab.titleRes, SocialConnectionCountFormatter.string(count))
}

/**
 * Port de `SocialConnectionsScreen` 1:1: tabs, búsqueda, sort por pestaña,
 * visitas / en común / followers, SharedActivity y alerta stalker.
 */
@Composable
fun SocialConnectionsScreen(
    route: SocialConnectionsRoute,
    username: String,
    availableTabs: List<SocialConnectionTab>,
    includesVisits: Boolean,
    isOwnProfile: Boolean,
    currentUser: AppUser?,
    inCommonUsers: List<AppUser>,
    followers: List<AppUser>,
    following: List<AppUser>,
    mutuals: List<AppUser>,
    suggestedUsers: List<AppUser>,
    visitTimestamps: Map<String, List<Date>>,
    listViewModel: UserListViewModel,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenStories: (String) -> Unit,
    onOpenChat: (AppUser) -> Unit,
    onOpenMoment: (Moment) -> Unit,
    viewerInterests: List<String> = emptyList(),
    connectionVisibility: VisibleConnectionTypes? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val visitsViewModel = remember { VisitsViewModel() }
    var selectedTabIndex by remember(route, availableTabs) {
        mutableIntStateOf(availableTabs.indexOf(route.initialTab).coerceAtLeast(0))
    }
    var searchText by remember { mutableStateOf("") }
    var sortModes by remember { mutableStateOf<Map<SocialConnectionTab, SocialConnectionsSortMode>>(emptyMap()) }
    var followerTimestamps by remember { mutableStateOf<Map<String, Date>>(emptyMap()) }
    var followingTimestamps by remember { mutableStateOf<Map<String, Date>>(emptyMap()) }
    var recentMomentCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    var isLoadingFollowerTimestamps by remember { mutableStateOf(false) }
    var sharedUser by remember { mutableStateOf<AppUser?>(null) }
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.45f) else Color.Black.copy(0.45f)

    val selectedTab = availableTabs.getOrNull(selectedTabIndex)
    val currentSortMode = selectedTab?.let { sortModes[it] } ?: SocialConnectionsSortMode.DEFAULT

    val activeGroupedVisits = if (isOwnProfile) {
        (listViewModel as? ProfileViewModel)?.groupedVisits.orEmpty()
    } else {
        visitsViewModel.groupedVisits
    }
    val isVisitsLoading = if (isOwnProfile) {
        (listViewModel as? ProfileViewModel)?.isLoadingVisits == true
    } else {
        visitsViewModel.isLoading
    }

    fun canViewList(tab: SocialConnectionTab): Boolean {
        val visibility = connectionVisibility ?: return true
        return when (tab) {
            SocialConnectionTab.FOLLOWERS -> visibility.canViewFollowers
            SocialConnectionTab.FOLLOWING -> visibility.canViewFollowing
            else -> true
        }
    }

    fun countFor(tab: SocialConnectionTab): Int = when (tab) {
        SocialConnectionTab.VISITS -> activeGroupedVisits.size
        SocialConnectionTab.IN_COMMON -> inCommonUsers.size
        SocialConnectionTab.FOLLOWERS ->
            if (connectionVisibility?.canViewFollowers == false) 0 else followers.size
        SocialConnectionTab.FOLLOWING ->
            if (connectionVisibility?.canViewFollowing == false) 0 else following.size
        SocialConnectionTab.MUTUALS -> mutuals.size
    }

    val tabItems = availableTabs.map { SocialConnectionTabItem(it, countFor(it)) }

    val shouldShowSearchBar = when (selectedTab) {
        null -> true
        SocialConnectionTab.IN_COMMON -> false
        else -> canViewList(selectedTab) && (selectedTab != SocialConnectionTab.VISITS || includesVisits)
    }
    val shouldShowSortControl = when (selectedTab) {
        null -> true
        SocialConnectionTab.IN_COMMON -> false
        else -> canViewList(selectedTab)
    }

    fun usersFor(tab: SocialConnectionTab): List<AppUser> {
        if (!canViewList(tab)) return emptyList()
        return when (tab) {
            SocialConnectionTab.VISITS -> emptyList()
            SocialConnectionTab.IN_COMMON -> inCommonUsers
            SocialConnectionTab.FOLLOWERS -> followers
            SocialConnectionTab.FOLLOWING -> following
            SocialConnectionTab.MUTUALS -> mutuals
        }
    }

    fun orderedUsers(tab: SocialConnectionTab): List<AppUser> {
        val base = usersFor(tab)
        if (isOwnProfile) return base
        val viewerId = currentUser?.id ?: return base
        val viewerIndex = base.indexOfFirst { it.id == viewerId }
        if (viewerIndex < 0) return base
        return base.toMutableList().also {
            val viewer = it.removeAt(viewerIndex)
            it.add(0, viewer)
        }
    }

    fun rowConfiguration(tab: SocialConnectionTab): SocialConnectionRowConfiguration = when (tab) {
        SocialConnectionTab.IN_COMMON -> SocialConnectionRowConfiguration(showsRelationshipButton = true, showsBio = true)
        SocialConnectionTab.FOLLOWERS -> if (!isOwnProfile) {
            SocialConnectionRowConfiguration(showsRelationshipButton = true, showsBio = true)
        } else {
            SocialConnectionRowConfiguration(
                showsRemoveFollower = true,
                showsRelationshipButton = false,
                showsFollowBackHint = true,
                showsBio = true,
            )
        }
        SocialConnectionTab.FOLLOWING, SocialConnectionTab.MUTUALS -> SocialConnectionRowConfiguration(
            showsRelationshipButton = true,
            showsOverflowMenu = isOwnProfile,
            showsBio = true,
            showsNewPosts = isOwnProfile,
        )
        SocialConnectionTab.VISITS -> SocialConnectionRowConfiguration(
            showsRelationshipButton = true,
            showsFollowBackHint = true,
            showsBio = true,
        )
    }

    fun defaultRowAction(tab: SocialConnectionTab): UserListRowAction = when (tab) {
        SocialConnectionTab.VISITS, SocialConnectionTab.IN_COMMON -> UserListRowAction.FOLLOW
        SocialConnectionTab.FOLLOWERS -> if (isOwnProfile) UserListRowAction.FOLLOW else UserListRowAction.UNFOLLOW
        SocialConnectionTab.FOLLOWING, SocialConnectionTab.MUTUALS -> UserListRowAction.UNFOLLOW
    }

    fun loadFollowerTimestampsIfNeeded() {
        val tab = selectedTab
        if (!includesVisits) return
        if (tab != SocialConnectionTab.FOLLOWERS &&
            currentSortMode != SocialConnectionsSortMode.NEWEST &&
            currentSortMode != SocialConnectionsSortMode.OLDEST
        ) {
            return
        }
        if (followerTimestamps.isNotEmpty() || isLoadingFollowerTimestamps) return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        isLoadingFollowerTimestamps = true
        scope.launch {
            runCatching {
                firestore.fetchFollowersWithTimestamps(userId).associate { it.first.id to it.second }
            }.onSuccess { followerTimestamps = it }
            isLoadingFollowerTimestamps = false
        }
    }

    fun loadFollowingTimestampsIfNeeded() {
        if (!includesVisits) return
        if (selectedTab != SocialConnectionTab.FOLLOWING) return
        if (currentSortMode != SocialConnectionsSortMode.NEWEST &&
            currentSortMode != SocialConnectionsSortMode.OLDEST
        ) {
            return
        }
        if (followingTimestamps.isNotEmpty()) return
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching {
                firestore.fetchFollowingWithTimestamps(userId).associate { it.first.id to it.second }
            }.onSuccess { followingTimestamps = it }
        }
    }

    fun loadFollowingInsightsIfNeeded() {
        if (!isOwnProfile || selectedTab != SocialConnectionTab.FOLLOWING) return
        val authorIds = following.map { it.id }
        if (authorIds.isEmpty()) {
            recentMomentCounts = emptyMap()
            return
        }
        val since = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -7) }.time
        scope.launch {
            recentMomentCounts = firestore.fetchRecentMomentCounts(authorIds, since)
        }
    }

    suspend fun refreshSocialTab(tab: SocialConnectionTab) {
        when (tab) {
            SocialConnectionTab.VISITS -> {
                if (isOwnProfile) {
                    val own = listViewModel as? ProfileViewModel
                    own?.refreshVisits()
                    while (own?.isLoadingVisits == true) delay(100)
                } else {
                    visitsViewModel.fetchVisits()
                }
            }
            else -> {
                when (listViewModel) {
                    is ProfileViewModel -> {
                        listViewModel.refreshProfile()
                        while (listViewModel.isRefreshing) delay(100)
                    }
                    is UserProfileViewModel -> {
                        listViewModel.refreshProfile()
                        while (listViewModel.isRefreshing || listViewModel.isLoading) delay(100)
                    }
                }
                followerTimestamps = emptyMap()
                followingTimestamps = emptyMap()
                loadFollowerTimestampsIfNeeded()
                loadFollowingTimestampsIfNeeded()
                loadFollowingInsightsIfNeeded()
            }
        }
        delay(200)
    }

    LaunchedEffect(Unit) {
        if (includesVisits) {
            if (isOwnProfile) {
                (listViewModel as? ProfileViewModel)?.refreshVisits()
            } else {
                visitsViewModel.fetchVisits()
            }
        }
        loadFollowerTimestampsIfNeeded()
        loadFollowingInsightsIfNeeded()
    }

    LaunchedEffect(selectedTabIndex) {
        searchText = ""
        loadFollowerTimestampsIfNeeded()
        loadFollowingInsightsIfNeeded()
    }

    LaunchedEffect(currentSortMode) {
        if (currentSortMode == SocialConnectionsSortMode.NEWEST ||
            currentSortMode == SocialConnectionsSortMode.OLDEST
        ) {
            loadFollowerTimestampsIfNeeded()
            loadFollowingTimestampsIfNeeded()
        }
    }

    sharedUser?.let { other ->
        SharedActivityView(
            currentUser = currentUser,
            otherUser = other,
            viewModel = listViewModel,
            onDismiss = { sharedUser = null },
            onOpenProfile = onOpenProfile,
            onOpenChat = onOpenChat,
            onOpenMoment = onOpenMoment,
            modifier = modifier,
        )
        return
    }

    Box(modifier.fillMaxSize().background(sharedCanvas())) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SettingsToolbarBackButton(onNavigateBack = onDismiss)
                Text(
                    username,
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 17.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }

            if (availableTabs.isEmpty()) {
                Box(Modifier.fillMaxSize())
            } else if (selectedTab != null) {
                ActivityCollapsibleFilterScroll(
                    onRefresh = { refreshSocialTab(selectedTab) },
                    modifier = Modifier.weight(1f),
                    header = {
                        SocialInlineHeader(
                            tabItems = tabItems,
                            selectedIndex = selectedTabIndex,
                            onSelect = { selectedTabIndex = it },
                            shouldShowSearchBar = shouldShowSearchBar,
                            shouldShowSortControl = shouldShowSortControl,
                            searchText = searchText,
                            onSearchChange = { searchText = it },
                            currentSortMode = currentSortMode,
                            onSortMode = { mode ->
                                selectedTab.let { sortModes = sortModes + (it to mode) }
                            },
                            primary = primary,
                            secondary = secondary,
                        )
                    },
                    floatingHeader = {
                        SocialFloatingChrome(
                            tabItems = tabItems,
                            selectedIndex = selectedTabIndex,
                            onSelect = { selectedTabIndex = it },
                            shouldShowSortControl = shouldShowSortControl,
                            currentSortMode = currentSortMode,
                            onSortMode = { mode ->
                                selectedTab.let { sortModes = sortModes + (it to mode) }
                            },
                            primary = primary,
                            dark = dark,
                        )
                    },
                ) {
                    when (selectedTab) {
                        SocialConnectionTab.VISITS -> VisitsTabContent(
                            groupedVisits = activeGroupedVisits,
                            isLoading = isVisitsLoading,
                            listViewModel = listViewModel,
                            searchText = searchText,
                            sortMode = currentSortMode,
                            onUserTap = onOpenProfile,
                            onAvatarTap = { id, hasStory ->
                                SocialConnectionAvatarTapRouting.route(id, hasStory, onOpenProfile, onOpenStories)
                            },
                        )
                        SocialConnectionTab.IN_COMMON -> CommonConnectionsTabContent(
                            commonUsers = inCommonUsers,
                            suggestedUsers = suggestedUsers,
                            viewerInterests = viewerInterests,
                            viewModel = listViewModel,
                            onUserTap = { onOpenProfile(it.id) },
                            onAvatarTap = { id, hasStory ->
                                SocialConnectionAvatarTapRouting.route(id, hasStory, onOpenProfile, onOpenStories)
                            },
                            usesOwnScroll = false,
                        )
                        SocialConnectionTab.FOLLOWERS,
                        SocialConnectionTab.FOLLOWING,
                        SocialConnectionTab.MUTUALS,
                        -> UsersTabContent(
                            title = stringResource(
                                when (selectedTab) {
                                    SocialConnectionTab.FOLLOWERS -> R.string.profile_header_followers
                                    SocialConnectionTab.FOLLOWING -> R.string.profile_header_following
                                    else -> R.string.profile_header_mutuals
                                },
                            ),
                            users = orderedUsers(selectedTab),
                            visitTimestamps = visitTimestamps,
                            searchText = searchText,
                            viewModel = listViewModel,
                            rowAction = defaultRowAction(selectedTab),
                            activeTab = selectedTab,
                            isOwnProfile = isOwnProfile,
                            isListHiddenFromViewer = !canViewList(selectedTab),
                            onUserTap = { onOpenProfile(it.id) },
                            onAvatarTap = { id, hasStory ->
                                SocialConnectionAvatarTapRouting.route(id, hasStory, onOpenProfile, onOpenStories)
                            },
                            rowConfiguration = rowConfiguration(selectedTab),
                            recentMomentCounts = recentMomentCounts,
                            onViewSharedActivity = if (isOwnProfile &&
                                selectedTab in setOf(SocialConnectionTab.FOLLOWING, SocialConnectionTab.MUTUALS)
                            ) {
                                { sharedUser = it }
                            } else {
                                null
                            },
                            onRemoveFollower = if (isOwnProfile && selectedTab == SocialConnectionTab.FOLLOWERS) {
                                { (listViewModel as? ProfileViewModel)?.removeFollower(it.id) }
                            } else {
                                null
                            },
                            mutualUserIds = mutuals.map { it.id }.toSet(),
                            sortMode = currentSortMode,
                            followerTimestamps = followerTimestamps,
                            followingTimestamps = followingTimestamps,
                            usesOwnScroll = false,
                        )
                    }
                }
            }
        }

        if (includesVisits && visitsViewModel.showStalkerAlert) {
            visitsViewModel.detectedStalker?.let { stalker ->
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.8f))
                        .clickable { visitsViewModel.showStalkerAlert = false },
                    contentAlignment = Alignment.Center,
                ) {
                    StalkerAlertView(stalker = stalker, onDismiss = { visitsViewModel.showStalkerAlert = false })
                }
            }
        }
    }
}

@Composable
private fun SocialInlineHeader(
    tabItems: List<SocialConnectionTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    shouldShowSearchBar: Boolean,
    shouldShowSortControl: Boolean,
    searchText: String,
    onSearchChange: (String) -> Unit,
    currentSortMode: SocialConnectionsSortMode,
    onSortMode: (SocialConnectionsSortMode) -> Unit,
    primary: Color,
    secondary: Color,
) {
    Column {
        SocialConnectionUnderlineTabBar(tabItems, selectedIndex, onSelect)
        if (shouldShowSearchBar || shouldShowSortControl) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 8.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (shouldShowSearchBar) {
                    SocialSearchBar(
                        searchText = searchText,
                        onSearchChange = onSearchChange,
                        primary = primary,
                        secondary = secondary,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (shouldShowSortControl) {
                    SocialSortIconButton(currentSortMode, onSortMode, primary)
                }
            }
        }
    }
}

@Composable
private fun SocialFloatingChrome(
    tabItems: List<SocialConnectionTabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    shouldShowSortControl: Boolean,
    currentSortMode: SocialConnectionsSortMode,
    onSortMode: (SocialConnectionsSortMode) -> Unit,
    primary: Color,
    dark: Boolean,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabItems.forEachIndexed { index, item ->
            val selected = selectedIndex == index
            val chipDark = if (selected) !dark else dark
            Text(
                item.titleText(),
                color = when {
                    selected && dark -> Color.Black
                    selected -> Color.White
                    dark -> Color.White.copy(0.72f)
                    else -> Color.Black.copy(0.62f)
                },
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                    .then(
                        if (selected) {
                            Modifier.background(
                                if (chipDark) Color(0xFF0B1215) else Color(0xFFFAF9F6),
                                RoundedCornerShape(50),
                            )
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        if (shouldShowSortControl) {
            SocialSortIconButton(currentSortMode, onSortMode, primary)
        }
    }
}

@Composable
private fun SocialSearchBar(
    searchText: String,
    onSearchChange: (String) -> Unit,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Search, null, tint = secondary, modifier = Modifier.size(14.dp))
        BasicTextField(
            value = searchText,
            onValueChange = onSearchChange,
            singleLine = true,
            textStyle = TextStyle(color = primary, fontSize = 14.sp),
            cursorBrush = SolidColor(primary),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                if (searchText.isEmpty()) {
                    Text(stringResource(R.string.social_connections_search), color = secondary, fontSize = 14.sp)
                }
                inner()
            },
        )
        if (searchText.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                stringResource(R.string.social_connections_clear_search),
                tint = secondary,
                modifier = Modifier.size(14.dp).clickable { onSearchChange("") },
            )
        }
    }
}

@Composable
private fun SocialSortIconButton(
    currentSortMode: SocialConnectionsSortMode,
    onSortMode: (SocialConnectionsSortMode) -> Unit,
    primary: Color,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Icon(
            Icons.Filled.SwapVert,
            stringResource(R.string.social_connections_sort),
            tint = primary,
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .momentsChromeGlass(CircleShape, interactive = true)
                .clickable { expanded = true }
                .padding(10.dp),
        )
        DropdownMenu(expanded, { expanded = false }) {
            SocialConnectionsSortMode.entries.forEach { mode ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(sortLabel(mode)) +
                                if (mode == currentSortMode) " ✓" else "",
                        )
                    },
                    onClick = {
                        expanded = false
                        onSortMode(mode)
                    },
                )
            }
        }
    }
}

/** Port de `SocialConnectionUnderlineTabBar`. */
@Composable
fun SocialConnectionUnderlineTabBar(
    items: List<SocialConnectionTabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.45f) else Color.Black.copy(0.45f)
    val divider = if (dark) Color.White.copy(0.12f) else Color.Black.copy(0.1f)
    val listState = rememberLazyListState()

    LaunchedEffect(selected, items.size) {
        if (selected in items.indices) listState.animateScrollToItem(selected)
    }

    Box(Modifier.fillMaxWidth()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(divider)
                .align(Alignment.BottomCenter),
        )
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(items, key = { _, item -> item.tab }) { index, item ->
                Column(
                    modifier = Modifier
                        .widthIn(min = 88.dp)
                        .clickable { onSelect(index) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        item.titleText(),
                        color = if (index == selected) primary else secondary,
                        fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 13.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    )
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(1.5.dp)
                            .background(if (index == selected) primary else Color.Transparent),
                    )
                }
            }
        }
    }
}

private fun sortLabel(mode: SocialConnectionsSortMode): Int = when (mode) {
    SocialConnectionsSortMode.DEFAULT -> R.string.social_connections_sort_default
    SocialConnectionsSortMode.ALPHABETICAL -> R.string.social_connections_sort_alphabetical
    SocialConnectionsSortMode.NEWEST -> R.string.social_connections_sort_newest
    SocialConnectionsSortMode.OLDEST -> R.string.social_connections_sort_oldest
}
