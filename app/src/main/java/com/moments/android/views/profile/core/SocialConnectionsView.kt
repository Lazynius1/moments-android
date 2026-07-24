package com.moments.android.views.profile.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.privacy.VisibleConnectionTypes
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

/** Port de `SocialConnectionsView.swift`: tabs, búsqueda, sort, visitas y actividad compartida. */
enum class SocialConnectionTab(val titleRes: Int) {
    VISITS(R.string.social_connections_tab_visits), IN_COMMON(R.string.social_connections_tab_in_common), FOLLOWERS(R.string.social_connections_tab_followers), FOLLOWING(R.string.social_connections_tab_following), MUTUALS(R.string.social_connections_tab_mutuals);
    companion object { val ownProfileTabs = listOf(VISITS, FOLLOWERS, FOLLOWING, MUTUALS); fun tabs(includesVisits: Boolean) = if (includesVisits) listOf(VISITS, FOLLOWERS, FOLLOWING, MUTUALS) else listOf(IN_COMMON, FOLLOWERS, FOLLOWING) }
}
data class SocialConnectionsRoute(val initialTab: SocialConnectionTab)
data class SocialConnectionTabItem(val tab: SocialConnectionTab, val count: Int)

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
    visitTimestamps: Map<String, List<java.util.Date>>,
    listViewModel: ProfileViewModel,
    connectionVisibility: VisibleConnectionTypes? = null,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenStories: (String) -> Unit,
    onOpenChat: (AppUser) -> Unit,
    onOpenMoment: (Moment) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var selectedIndex by remember(route, availableTabs) { mutableStateOf(availableTabs.indexOf(route.initialTab).coerceAtLeast(0)) }
    var search by remember { mutableStateOf("") }
    var sortMode by remember { mutableStateOf(SocialConnectionsSortMode.DEFAULT) }
    var sortExpanded by remember { mutableStateOf(false) }
    var timestamps by remember { mutableStateOf<Map<String, java.util.Date>>(emptyMap()) }
    var sharedUser by remember { mutableStateOf<AppUser?>(null) }
    val tab = availableTabs.getOrNull(selectedIndex)
    val primary = sharedPrimary(); val secondary = sharedSecondary()

    sharedUser?.let { other -> SharedActivityView(currentUser, other, listViewModel, { sharedUser = null }, onOpenProfile, onOpenChat, onOpenMoment, modifier); return }
    fun refresh() { if (tab == SocialConnectionTab.VISITS) listViewModel.refreshVisits() else listViewModel.refreshProfile() }
    fun loadTimestamps() { val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return; scope.launch { val values = if (tab == SocialConnectionTab.FOLLOWING) FirestoreService().fetchFollowingWithTimestamps(userId) else FirestoreService().fetchFollowersWithTimestamps(userId); timestamps = values.associate { it.first.id to it.second } } }
    val baseUsers = when (tab) { SocialConnectionTab.VISITS -> listViewModel.groupedVisits.map { it.user }; SocialConnectionTab.IN_COMMON -> inCommonUsers; SocialConnectionTab.FOLLOWERS -> if (connectionVisibility?.canViewFollowers == false) emptyList() else followers; SocialConnectionTab.FOLLOWING -> if (connectionVisibility?.canViewFollowing == false) emptyList() else following; SocialConnectionTab.MUTUALS -> mutuals; null -> emptyList() }
    val ordered = SocialConnectionsSorting.sortUsers(baseUsers, sortMode, timestamps).filter { it.username.contains(search, ignoreCase = true) }
    val items = availableTabs.map { item -> SocialConnectionTabItem(item, when (item) { SocialConnectionTab.VISITS -> listViewModel.groupedVisits.size; SocialConnectionTab.IN_COMMON -> inCommonUsers.size; SocialConnectionTab.FOLLOWERS -> followers.size; SocialConnectionTab.FOLLOWING -> following.size; SocialConnectionTab.MUTUALS -> mutuals.size }) }

    Column(modifier.fillMaxSize().background(sharedCanvas())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Filled.ArrowBack, stringResource(R.string.common_back), tint = primary, modifier = Modifier.size(28.dp).clickable(onClick = onDismiss)); Text(username, color = primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 12.dp).weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis) }
        SocialConnectionUnderlineTabBar(items, selectedIndex) { selectedIndex = it; search = ""; sortMode = SocialConnectionsSortMode.DEFAULT; timestamps = emptyMap() }
        if (tab != SocialConnectionTab.IN_COMMON && tab != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(search, { search = it }, Modifier.weight(1f), placeholder = { Text(stringResource(R.string.social_connections_search)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, trailingIcon = { if (search.isNotEmpty()) Icon(Icons.Filled.Close, stringResource(R.string.social_connections_clear_search), modifier = Modifier.clickable { search = "" }) }, singleLine = true)
            Box { Icon(Icons.Filled.Sort, stringResource(R.string.social_connections_sort), tint = primary, modifier = Modifier.size(36.dp).clickable { sortExpanded = true }.padding(7.dp)); DropdownMenu(sortExpanded, { sortExpanded = false }) { SocialConnectionsSortMode.entries.forEach { mode -> DropdownMenuItem(text = { Text(stringResource(sortLabel(mode))) }, onClick = { sortExpanded = false; sortMode = mode; if (mode == SocialConnectionsSortMode.NEWEST || mode == SocialConnectionsSortMode.OLDEST) loadTimestamps() }) } } }
        }
        when {
            tab == SocialConnectionTab.VISITS && listViewModel.isLoadingVisits -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            tab == null -> Box(Modifier.fillMaxSize())
            ordered.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(stringResource(R.string.social_connections_empty), color = secondary) }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                if (tab == SocialConnectionTab.IN_COMMON && suggestedUsers.isNotEmpty()) item { Text(stringResource(R.string.social_connections_suggested), color = primary, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(16.dp)) }
                items(ordered, key = { it.id }) { user -> SocialConnectionUserRow(user, if (tab == SocialConnectionTab.VISITS) visitTimestamps[user.id]?.maxOrNull()?.timeAgoDisplay() else null, listViewModel, { onOpenProfile(it.id) }, configuration = socialRowConfiguration(tab, isOwnProfile), newContentCount = null, onViewSharedActivity = if (isOwnProfile && tab in setOf(SocialConnectionTab.FOLLOWING, SocialConnectionTab.MUTUALS)) { { sharedUser = it } } else null, onRemoveFollower = if (isOwnProfile && tab == SocialConnectionTab.FOLLOWERS) { { listViewModel.removeFollower(it.id) } } else null, onAvatarTap = { id, hasStory -> SocialConnectionAvatarTapRouting.route(id, hasStory, onOpenProfile, onOpenStories) }, isMutual = user.id in mutuals.map { it.id }.toSet()) }
                if (tab == SocialConnectionTab.IN_COMMON) items(suggestedUsers, key = { "suggested-${it.id}" }) { user -> SocialConnectionUserRow(user, null, listViewModel, { onOpenProfile(it.id) }, onAvatarTap = { id, hasStory -> SocialConnectionAvatarTapRouting.route(id, hasStory, onOpenProfile, onOpenStories) }) }
            }
        }
    }
}

@Composable
fun SocialConnectionUnderlineTabBar(items: List<SocialConnectionTabItem>, selected: Int, onSelect: (Int) -> Unit) = Row(Modifier.fillMaxWidth()) { items.forEachIndexed { index, item -> Text(stringResource(item.tab.titleRes, SocialConnectionCountFormatter.string(item.count)), color = if (index == selected) sharedPrimary() else sharedSecondary(), fontWeight = if (index == selected) FontWeight.SemiBold else FontWeight.Normal, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).clickable { onSelect(index) }.padding(horizontal = 8.dp, vertical = 12.dp)) } }
private fun sortLabel(mode: SocialConnectionsSortMode): Int = when (mode) { SocialConnectionsSortMode.DEFAULT -> R.string.social_connections_sort_default; SocialConnectionsSortMode.ALPHABETICAL -> R.string.social_connections_sort_alphabetical; SocialConnectionsSortMode.NEWEST -> R.string.social_connections_sort_newest; SocialConnectionsSortMode.OLDEST -> R.string.social_connections_sort_oldest }
private fun socialRowConfiguration(tab: SocialConnectionTab?, own: Boolean) = when (tab) { SocialConnectionTab.FOLLOWERS -> SocialConnectionRowConfiguration(showsRemoveFollower = own, showsRelationshipButton = !own, showsFollowBackHint = own); SocialConnectionTab.FOLLOWING, SocialConnectionTab.MUTUALS -> SocialConnectionRowConfiguration(showsOverflowMenu = own, showsNewPosts = own); SocialConnectionTab.VISITS -> SocialConnectionRowConfiguration(showsFollowBackHint = true); else -> SocialConnectionRowConfiguration() }
