package com.moments.android.views.profile.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.story.StoryRingAvatarView
import java.util.Date

/** Contrato de `UserListViewModel` de iOS. */
interface UserListViewModel { fun followUser(userId: String); fun unfollowUser(userId: String); fun cancelFollowRequest(userId: String); fun relationshipState(userId: String): FollowButtonState; fun prefetchRelationshipState(userId: String) }
class EmptyUserListViewModel : UserListViewModel { override fun followUser(userId: String) = Unit; override fun unfollowUser(userId: String) = Unit; override fun cancelFollowRequest(userId: String) = Unit; override fun relationshipState(userId: String) = FollowButtonState.CAN_FOLLOW; override fun prefetchRelationshipState(userId: String) = Unit }
enum class UserListRowAction { FOLLOW, UNFOLLOW, NONE }

@Composable
fun UsersTabContent(
    title: String,
    users: List<AppUser>,
    visitTimestamps: Map<String, List<Date>>,
    searchText: String,
    viewModel: ProfileViewModel,
    rowAction: UserListRowAction,
    activeTab: SocialConnectionTab = SocialConnectionTab.FOLLOWERS,
    isOwnProfile: Boolean = true,
    isListHiddenFromViewer: Boolean = false,
    onUserTap: ((AppUser) -> Unit)? = null,
    onAvatarTap: ((String, Boolean) -> Unit)? = null,
    rowConfiguration: SocialConnectionRowConfiguration? = null,
    recentMomentCounts: Map<String, Int> = emptyMap(),
    onViewSharedActivity: ((AppUser) -> Unit)? = null,
    onRemoveFollower: ((AppUser) -> Unit)? = null,
    mutualUserIds: Set<String> = emptySet(),
    modifier: Modifier = Modifier,
) {
    val filtered = SocialConnectionsSorting.sortUsers(users.filter { searchText.isBlank() || it.username.contains(searchText, true) || (it.bio?.contains(searchText, true) == true) }, SocialConnectionsSortMode.DEFAULT)
    when {
        filtered.isEmpty() && users.isNotEmpty() -> SocialConnectionsNoResultsView(modifier)
        filtered.isEmpty() -> UserListEmptyState(title, activeTab, isOwnProfile, isListHiddenFromViewer, modifier)
        else -> LazyColumn(modifier) { items(filtered, key = { it.id }) { user -> SocialConnectionUserRow(user, null, viewModel, onUserTap, rowConfiguration ?: SocialConnectionRowConfiguration(showsRelationshipButton = rowAction != UserListRowAction.NONE, showsOverflowMenu = rowAction == UserListRowAction.UNFOLLOW), recentMomentCounts[user.id], onViewSharedActivity, onRemoveFollower, onAvatarTap, user.id in mutualUserIds) } }
    }
}

@Composable
fun CommonConnectionsTabContent(commonUsers: List<AppUser>, suggestedUsers: List<AppUser>, viewerInterests: List<String>, viewModel: ProfileViewModel, onUserTap: ((AppUser) -> Unit)? = null, onAvatarTap: ((String, Boolean) -> Unit)? = null, modifier: Modifier = Modifier) {
    if (commonUsers.isEmpty() && suggestedUsers.isEmpty()) { SocialConnectionsNoResultsView(modifier); return }
    LazyColumn(modifier) {
        if (commonUsers.isNotEmpty()) { item { UserListSectionHeader(R.string.user_list_people_in_common) }; items(commonUsers, key = { it.id }) { SocialConnectionUserRow(it, null, viewModel, onUserTap, onAvatarTap = onAvatarTap) } }
        if (suggestedUsers.isNotEmpty()) { item { UserListSectionHeader(R.string.social_connections_suggested) }; items(suggestedUsers, key = { "suggested-${it.id}" }) { user -> SuggestedUserRow(user, viewerInterests.count { interest -> interest in user.interests }, viewModel, onUserTap) } }
    }
}

@Composable
fun SocialConnectionsNoResultsView(modifier: Modifier = Modifier) = Column(modifier.fillMaxSize().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(Icons.Filled.Search, null, tint = sharedPrimary(), modifier = Modifier.size(52.dp)); Text(stringResource(R.string.user_list_no_results_title), color = sharedPrimary(), fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp)); Text(stringResource(R.string.user_list_no_results_description), color = sharedSecondary(), textAlign = TextAlign.Center, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp)) }
@Composable private fun UserListSectionHeader(title: Int) = Text(stringResource(title), color = sharedPrimary(), fontWeight = FontWeight.SemiBold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp))

@Composable
fun UserListView(title: String, users: List<AppUser>, visitTimestamps: Map<String, List<Date>>, viewModel: ProfileViewModel, onDismiss: () -> Unit, rowAction: UserListRowAction, onUserTap: ((AppUser) -> Unit)? = null, modifier: Modifier = Modifier) {
    var search by remember { mutableStateOf("") }
    Column(modifier.fillMaxSize().background(sharedCanvas())) { Text(title, color = sharedPrimary(), fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.CenterHorizontally).padding(top = 20.dp)); Text(stringResource(if (users.size == 1) R.string.user_list_person_single else R.string.user_list_person_multiple, users.size), color = sharedSecondary(), fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(bottom = 18.dp)); OutlinedTextField(search, { search = it }, Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), placeholder = { Text(stringResource(R.string.social_connections_search)) }, leadingIcon = { Icon(Icons.Filled.Search, null) }, trailingIcon = { if (search.isNotBlank()) Icon(Icons.Filled.Close, stringResource(R.string.social_connections_clear_search), modifier = Modifier.clickable { search = "" }) }, singleLine = true); UsersTabContent(title, users, visitTimestamps, search, viewModel, rowAction, onUserTap = onUserTap, modifier = Modifier.weight(1f)) }
}

@Composable
fun ModernProfileUserRowView(user: AppUser, visitTimestamps: List<Date>, rowAction: UserListRowAction, viewModel: ProfileViewModel, onDismiss: () -> Unit, onUserTap: ((AppUser) -> Unit)? = null, modifier: Modifier = Modifier) {
    var confirmUnfollow by remember { mutableStateOf(false) }
    Row(modifier.fillMaxWidth().clickable { onUserTap?.invoke(user) ?: onDismiss() }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) { StoryRingAvatarView(user.id, 44.dp, lineWidth = 2.1.dp, showBaseStroke = true, baseStrokeColor = sharedPrimary().copy(.14f), baseStrokeWidth = .9.dp); Column(Modifier.weight(1f)) { Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) { Text(user.username, color = sharedPrimary(), fontSize = 16.sp, fontWeight = FontWeight.SemiBold); if (user.isVerified) VerifiedBadge(14.dp) }; user.bio?.takeIf(String::isNotBlank)?.let { Text(it, color = sharedSecondary(), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; if (visitTimestamps.size >= 3 && visitTimestamps.all { Date().time - it.time < 86_400_000 }) Text(stringResource(R.string.user_list_frequent_visits), color = Color(0xFFFF9500), fontSize = 10.sp) }; when (rowAction) { UserListRowAction.FOLLOW -> TextButton({ viewModel.followUser(user.id); onDismiss() }) { Icon(Icons.Filled.PersonAdd, null); Text(stringResource(R.string.user_list_follow)) }; UserListRowAction.UNFOLLOW -> TextButton({ confirmUnfollow = true }) { Icon(Icons.Filled.PersonRemove, null); Text(stringResource(R.string.user_list_unfollow)) }; UserListRowAction.NONE -> Icon(Icons.Filled.ChevronRight, null, tint = sharedSecondary()) } }
    if (confirmUnfollow) AlertDialog({ confirmUnfollow = false }, title = { Text(stringResource(R.string.social_connection_unfollow_title)) }, text = { Text(stringResource(R.string.social_connection_unfollow_message)) }, dismissButton = { TextButton({ confirmUnfollow = false }) { Text(stringResource(R.string.common_cancel)) } }, confirmButton = { TextButton({ confirmUnfollow = false; viewModel.unfollowUser(user.id); onDismiss() }) { Text(stringResource(R.string.social_connection_unfollow_action), color = Color.Red) } })
}

@Composable private fun SuggestedUserRow(user: AppUser, commonInterests: Int, viewModel: ProfileViewModel, onTap: ((AppUser) -> Unit)?) = SocialConnectionUserRow(user, if (commonInterests > 0) stringResource(R.string.user_list_common_interests, commonInterests) else null, viewModel, onTap)
@Composable private fun UserListEmptyState(title: String, tab: SocialConnectionTab, own: Boolean, hidden: Boolean, modifier: Modifier) { val titleRes = when { hidden -> R.string.user_list_hidden_title; !own -> R.string.user_list_visitor_empty_title; else -> R.string.user_list_empty_title }; val descriptionRes = when { hidden -> R.string.user_list_hidden_description; !own -> R.string.user_list_visitor_empty_description; else -> R.string.user_list_empty_description }; Column(modifier.fillMaxSize().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Icon(if (tab == SocialConnectionTab.VISITS) Icons.Filled.Close else Icons.Filled.PersonAdd, null, tint = sharedPrimary(), modifier = Modifier.size(48.dp)); Text(stringResource(titleRes, title.lowercase()), color = sharedPrimary(), fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp)); Text(stringResource(descriptionRes, title.lowercase()), color = sharedSecondary(), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp)) } }
