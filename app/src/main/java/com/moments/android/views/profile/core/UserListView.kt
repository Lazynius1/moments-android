package com.moments.android.views.profile.core

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonRemove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.explore.SuggestedUserRow
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.launch
import java.util.Date

/** Contrato de `UserListViewModel` de iOS. */
interface UserListViewModel {
    fun followUser(userId: String)
    fun unfollowUser(userId: String)
    fun cancelFollowRequest(userId: String)
    fun relationshipState(userId: String): FollowButtonState
    fun prefetchRelationshipState(userId: String)
}

class EmptyUserListViewModel : UserListViewModel {
    override fun followUser(userId: String) = Unit
    override fun unfollowUser(userId: String) = Unit
    override fun cancelFollowRequest(userId: String) = Unit
    override fun relationshipState(userId: String) = FollowButtonState.CAN_FOLLOW
    override fun prefetchRelationshipState(userId: String) = Unit
}

enum class UserListRowAction { FOLLOW, UNFOLLOW, NONE }

/** Port de `UsersTabContent`. */
@Composable
fun UsersTabContent(
    title: String,
    users: List<AppUser>,
    visitTimestamps: Map<String, List<Date>>,
    searchText: String,
    viewModel: UserListViewModel,
    rowAction: UserListRowAction,
    activeTab: SocialConnectionTab = SocialConnectionTab.FOLLOWERS,
    includesVisits: Boolean = false,
    isOwnProfile: Boolean = true,
    isListHiddenFromViewer: Boolean = false,
    onUserTap: ((AppUser) -> Unit)? = null,
    onAvatarTap: ((String, Boolean) -> Unit)? = null,
    rowConfiguration: SocialConnectionRowConfiguration? = null,
    recentMomentCounts: Map<String, Int> = emptyMap(),
    onViewSharedActivity: ((AppUser) -> Unit)? = null,
    onRemoveFollower: ((AppUser) -> Unit)? = null,
    mutualUserIds: Set<String> = emptySet(),
    sortMode: SocialConnectionsSortMode = SocialConnectionsSortMode.DEFAULT,
    followerTimestamps: Map<String, Date> = emptyMap(),
    followingTimestamps: Map<String, Date> = emptyMap(),
    usesOwnScroll: Boolean = true,
    onRefresh: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedIncludesVisits = includesVisits
    @Suppress("UNUSED_PARAMETER")
    val unusedVisitTimestamps = visitTimestamps

    val timestamps = when (activeTab) {
        SocialConnectionTab.FOLLOWERS -> followerTimestamps
        SocialConnectionTab.FOLLOWING -> followingTimestamps
        else -> emptyMap()
    }
    val filtered = SocialConnectionsSorting.sortUsers(
        users.filter {
            searchText.isBlank() ||
                it.username.contains(searchText, true) ||
                (it.bio?.contains(searchText, true) == true)
        },
        sortMode,
        timestamps,
    )
    val config = rowConfiguration ?: SocialConnectionRowConfiguration(
        showsRelationshipButton = rowAction != UserListRowAction.NONE,
        showsOverflowMenu = rowAction == UserListRowAction.UNFOLLOW,
        showsBio = true,
    )

    @Composable
    fun Rows() {
        filtered.forEach { user ->
            SocialConnectionUserRow(
                user = user,
                subtitle = null,
                viewModel = viewModel,
                onUserTap = onUserTap,
                configuration = config,
                newContentCount = recentMomentCounts[user.id],
                onViewSharedActivity = onViewSharedActivity,
                onRemoveFollower = onRemoveFollower,
                onAvatarTap = onAvatarTap,
                isMutual = user.id in mutualUserIds,
            )
        }
    }

    when {
        usesOwnScroll -> {
            when {
                filtered.isEmpty() && users.isNotEmpty() -> RefreshableUserListScroll(onRefresh, modifier) {
                    SocialConnectionsNoResultsView(Modifier.heightIn(min = 400.dp))
                }
                filtered.isEmpty() -> RefreshableUserListScroll(onRefresh, modifier) {
                    UserListEmptyState(
                        title = title,
                        tab = activeTab,
                        own = isOwnProfile,
                        hidden = isListHiddenFromViewer,
                        modifier = Modifier.heightIn(min = 400.dp),
                    )
                }
                else -> RefreshableUserListScroll(onRefresh, modifier) {
                    Column(Modifier.padding(vertical = 4.dp)) { Rows() }
                }
            }
        }
        filtered.isEmpty() && users.isNotEmpty() ->
            SocialConnectionsNoResultsView(modifier.fillMaxWidth().heightIn(min = 400.dp))
        filtered.isEmpty() -> UserListEmptyState(
            title = title,
            tab = activeTab,
            own = isOwnProfile,
            hidden = isListHiddenFromViewer,
            modifier = modifier.fillMaxWidth().heightIn(min = 400.dp),
        )
        else -> Column(modifier.padding(vertical = 4.dp)) { Rows() }
    }
}

/** Port de `CommonConnectionsTabContent`. */
@Composable
fun CommonConnectionsTabContent(
    commonUsers: List<AppUser>,
    suggestedUsers: List<AppUser>,
    viewerInterests: List<String>,
    viewModel: UserListViewModel,
    onUserTap: ((AppUser) -> Unit)? = null,
    onAvatarTap: ((String, Boolean) -> Unit)? = null,
    usesOwnScroll: Boolean = true,
    onRefresh: (suspend () -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val viewerSet = remember(viewerInterests) { viewerInterests.toSet() }

    @Composable
    fun Content() {
        Column(Modifier.padding(vertical = 4.dp)) {
            if (commonUsers.isNotEmpty()) {
                UserListSectionHeader(R.string.user_list_people_in_common)
                commonUsers.forEach { user ->
                    SocialConnectionUserRow(
                        user = user,
                        subtitle = null,
                        viewModel = viewModel,
                        onUserTap = onUserTap,
                        configuration = SocialConnectionRowConfiguration(showsRelationshipButton = true, showsBio = true),
                        onAvatarTap = onAvatarTap,
                    )
                }
            }
            if (suggestedUsers.isNotEmpty()) {
                UserListSectionHeader(R.string.explore_suggested_users_suggested_for_you)
                suggestedUsers.forEach { user ->
                    LaunchedEffect(user.id) { viewModel.prefetchRelationshipState(user.id) }
                    val state = viewModel.relationshipState(user.id)
                    SuggestedUserRow(
                        user = user,
                        commonInterests = user.interests.count { it in viewerSet },
                        buttonState = state,
                        onFollow = {
                            when (state) {
                                FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW ->
                                    viewModel.followUser(user.id)
                                FollowButtonState.REQUEST_PENDING_CANCELLABLE ->
                                    viewModel.cancelFollowRequest(user.id)
                                else -> Unit
                            }
                        },
                        onTap = { onUserTap?.invoke(user) },
                    )
                }
            }
        }
    }

    when {
        commonUsers.isEmpty() && suggestedUsers.isEmpty() -> {
            if (usesOwnScroll) {
                RefreshableUserListScroll(onRefresh, modifier) {
                    SocialConnectionsNoResultsView(Modifier.heightIn(min = 400.dp))
                }
            } else {
                SocialConnectionsNoResultsView(modifier.fillMaxWidth().heightIn(min = 400.dp))
            }
        }
        usesOwnScroll -> RefreshableUserListScroll(onRefresh, modifier) { Content() }
        else -> Content()
    }
}

@Composable
fun SocialConnectionsNoResultsView(modifier: Modifier = Modifier) {
    val dark = isSystemInDarkTheme()
    val iconColor = if (dark) Color.White.copy(0.88f) else Color.Black.copy(0.88f)
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.7f) else Color.Black.copy(0.7f)
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Search, null, tint = iconColor, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.user_list_no_results_title),
            color = primary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            stringResource(R.string.user_list_no_results_description),
            color = secondary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun UserListSectionHeader(title: Int) =
    Text(
        stringResource(title),
        color = if (isSystemInDarkTheme()) Color.White else Color.Black,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp, bottom = 10.dp),
    )

/**
 * Port de `UserListView` — sheet medium/large en el call site (`MomentsModalSheet`).
 * Header + search glass + `UsersTabContent`.
 */
@Composable
fun UserListView(
    title: String,
    users: List<AppUser>,
    visitTimestamps: Map<String, List<Date>>,
    viewModel: UserListViewModel,
    onDismiss: () -> Unit,
    rowAction: UserListRowAction,
    onUserTap: ((AppUser) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var search by remember { mutableStateOf("") }
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.7f) else Color.Black.copy(0.7f)

    Column(modifier.fillMaxSize().background(sharedCanvas())) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 20.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(title, color = primary, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                stringResource(
                    if (users.size == 1) R.string.user_list_person_single else R.string.user_list_person_multiple,
                    users.size,
                ),
                color = secondary,
                fontSize = 13.sp,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 16.dp)
                .clip(RoundedCornerShape(50))
                .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Filled.Search, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
            BasicTextField(
                value = search,
                onValueChange = { search = it },
                singleLine = true,
                textStyle = TextStyle(color = primary, fontSize = 16.sp),
                cursorBrush = SolidColor(primary),
                modifier = Modifier.weight(1f),
                decorationBox = { inner ->
                    if (search.isEmpty()) {
                        Text(
                            stringResource(R.string.user_list_view_search_placeholder),
                            color = Color.Gray,
                            fontSize = 16.sp,
                        )
                    }
                    inner()
                },
            )
            if (search.isNotEmpty()) {
                Icon(
                    Icons.Filled.Close,
                    stringResource(R.string.social_connections_clear_search),
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp).clickable { search = "" },
                )
            }
        }

        UsersTabContent(
            title = title,
            users = users,
            visitTimestamps = visitTimestamps,
            searchText = search,
            viewModel = viewModel,
            rowAction = rowAction,
            onUserTap = onUserTap ?: { onDismiss() },
            modifier = Modifier.weight(1f),
        )
    }
}

/** Port de `ModernProfileUserRowView`. */
@Composable
fun ModernProfileUserRowView(
    user: AppUser,
    visitTimestamps: List<Date>,
    rowAction: UserListRowAction,
    viewModel: UserListViewModel,
    onDismiss: () -> Unit,
    onUserTap: ((AppUser) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var confirmUnfollow by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.7f) else Color.Black.copy(0.7f)
    val pressScale by animateFloatAsState(if (isPressed) 0.98f else 1f, tween(100), label = "userRowScale")
    val pressAlpha by animateFloatAsState(if (isPressed) 0.06f else 0f, tween(100), label = "userRowPress")
    val showFrequent = visitTimestamps.size >= 3 &&
        visitTimestamps.all { Date().time - it.time < 86_400_000 }

    fun open() {
        if (onUserTap != null) onUserTap(user) else onDismiss()
    }

    Row(
        modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .background((if (dark) Color.White else Color.Black).copy(alpha = pressAlpha))
            .pointerInput(user.id) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        try {
                            awaitRelease()
                        } finally {
                            isPressed = false
                        }
                    },
                    onTap = { open() },
                )
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF007AFF).copy(alpha = 0.15f)),
            )
            StoryRingAvatarView(
                user.id,
                44.dp,
                lineWidth = 2.1.dp,
                showBaseStroke = true,
                baseStrokeColor = if (dark) Color.White.copy(0.18f) else Color.Black.copy(0.14f),
                baseStrokeWidth = 0.9.dp,
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(user.username, color = primary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                if (user.isVerified) VerifiedBadge(14.dp)
            }
            user.bio?.takeIf(String::isNotBlank)?.let {
                Text(it, color = secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (showFrequent) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFFFF9500).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFFF9500).copy(alpha = 0.3f), RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        null,
                        tint = Color(0xFFFF9500),
                        modifier = Modifier.size(10.dp),
                    )
                    Text(
                        stringResource(R.string.user_list_frequent_visits),
                        color = if (dark) Color.White.copy(0.8f) else Color.Black.copy(0.8f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
        when (rowAction) {
            UserListRowAction.FOLLOW -> ModernProfileActionChip(
                icon = Icons.Filled.PersonAdd,
                label = stringResource(R.string.user_list_follow),
                primary = primary,
            ) {
                viewModel.followUser(user.id)
                scope.launch { FollowStateStore.setState(FollowButtonState.FOLLOWING, user.id) }
                onDismiss()
            }
            UserListRowAction.UNFOLLOW -> ModernProfileActionChip(
                icon = Icons.Filled.PersonRemove,
                label = stringResource(R.string.user_list_unfollow),
                primary = primary,
            ) { confirmUnfollow = true }
            UserListRowAction.NONE -> Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                null,
                tint = if (dark) Color.White.copy(0.4f) else Color.Black.copy(0.4f),
                modifier = Modifier.size(14.dp),
            )
        }
    }

    if (confirmUnfollow) {
        AlertDialog(
            onDismissRequest = { confirmUnfollow = false },
            title = { Text(stringResource(R.string.social_connection_unfollow_title)) },
            text = { Text(stringResource(R.string.social_connection_unfollow_message)) },
            dismissButton = {
                TextButton({ confirmUnfollow = false }) { Text(stringResource(R.string.common_cancel)) }
            },
            confirmButton = {
                TextButton({
                    confirmUnfollow = false
                    viewModel.unfollowUser(user.id)
                    scope.launch { FollowStateStore.setState(FollowButtonState.CAN_FOLLOW, user.id) }
                    onDismiss()
                }) {
                    Text(stringResource(R.string.social_connection_unfollow_action), color = Color.Red)
                }
            },
        )
    }
}

@Composable
private fun ModernProfileActionChip(
    icon: ImageVector,
    label: String,
    primary: Color,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(12.dp))
            .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = true)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = primary, modifier = Modifier.size(12.dp))
        Text(label, color = primary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun UserListEmptyState(
    title: String,
    tab: SocialConnectionTab,
    own: Boolean,
    hidden: Boolean,
    modifier: Modifier,
) {
    val dark = isSystemInDarkTheme()
    val iconColor = if (dark) Color.White.copy(0.88f) else Color.Black.copy(0.88f)
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.7f) else Color.Black.copy(0.7f)

    val (titleRes, descriptionRes, icon) = when {
        hidden && tab == SocialConnectionTab.FOLLOWERS -> Triple(
            R.string.user_list_empty_hidden_followers_title,
            R.string.user_list_empty_hidden_followers_description,
            Icons.Filled.VisibilityOff,
        )
        hidden && tab == SocialConnectionTab.FOLLOWING -> Triple(
            R.string.user_list_empty_hidden_following_title,
            R.string.user_list_empty_hidden_following_description,
            Icons.Filled.VisibilityOff,
        )
        !own && tab == SocialConnectionTab.FOLLOWERS -> Triple(
            R.string.user_list_empty_visitor_followers_title,
            R.string.user_list_empty_visitor_followers_description,
            Icons.Filled.People,
        )
        !own && tab == SocialConnectionTab.FOLLOWING -> Triple(
            R.string.user_list_empty_visitor_following_title,
            R.string.user_list_empty_visitor_following_description,
            Icons.Filled.People,
        )
        !own && tab == SocialConnectionTab.MUTUALS -> Triple(
            R.string.user_list_empty_visitor_mutuals_title,
            R.string.user_list_empty_visitor_mutuals_description,
            Icons.Filled.Sync,
        )
        else -> Triple(
            R.string.user_list_empty_title,
            R.string.user_list_empty_description,
            when (tab) {
                SocialConnectionTab.VISITS -> Icons.Filled.VisibilityOff
                SocialConnectionTab.MUTUALS -> Icons.Filled.Sync
                else -> Icons.Filled.People
            },
        )
    }

    val isGeneric = titleRes == R.string.user_list_empty_title
    Column(
        modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(icon, null, tint = iconColor, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(20.dp))
        Text(
            if (isGeneric) stringResource(titleRes, title.lowercase()) else stringResource(titleRes),
            color = primary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 18.sp,
            textAlign = TextAlign.Center,
        )
        Text(
            if (isGeneric) stringResource(descriptionRes, title.lowercase()) else stringResource(descriptionRes),
            color = secondary,
            textAlign = TextAlign.Center,
            fontSize = 14.sp,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RefreshableUserListScroll(
    onRefresh: (suspend () -> Unit)?,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
        ) {
            content()
        }
    }
    if (onRefresh != null) {
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                scope.launch {
                    refreshing = true
                    onRefresh()
                    refreshing = false
                }
            },
            modifier = modifier.fillMaxSize(),
        ) {
            body()
        }
    } else {
        Box(modifier.fillMaxSize()) { body() }
    }
}
