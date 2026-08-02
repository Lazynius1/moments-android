package com.moments.android.views.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.feed.core.FeedProfileSheetRoute
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.profile.userprofile.UserProfileView
import kotlinx.coroutines.launch

/**
 * Port de `SuggestedUsersView.swift`.
 * Header centrado (sheet iOS sin back); lista + infinite scroll + pull-to-refresh.
 * Tap → `UserProfileView` (≡ userProfileNavigationDestination).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SuggestedUsersView(
    onNavigateBack: () -> Unit = {},
    onSelectUser: (String) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val viewModel = remember { SuggestedUsersViewModel() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var selectedProfileRoute by remember { mutableStateOf<FeedProfileSheetRoute?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadInitialUsers()
    }

    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = info.totalItemsCount
            total > 0 && lastVisible >= total - 2
        }
    }
    LaunchedEffect(shouldLoadMore, viewModel.users) {
        if (shouldLoadMore && viewModel.users.isNotEmpty() && !viewModel.isLoadingMore) {
            viewModel.loadMoreUsers()
        }
    }

    Column(
        modifier
            .fillMaxSize()
            .background(colors.surfaceBackground),
    ) {
        // ≡ headerView (sin back — dismiss del sheet)
        Text(
            stringResource(R.string.explore_suggested_users_title),
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp)
                .padding(top = 20.dp, bottom = 16.dp),
            fontWeight = FontWeight.SemiBold,
            fontSize = 20.sp,
            color = colors.primary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )

        when {
            viewModel.isLoading && viewModel.users.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                    ) {
                        MomentsCircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                        )
                        Text(
                            stringResource(R.string.explore_suggested_users_loading),
                            fontSize = 16.sp,
                            color = if (isDark) Color.White else Color.Black,
                        )
                    }
                }
            }
            !viewModel.isLoading && viewModel.users.isEmpty() -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier = Modifier.padding(24.dp),
                    ) {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            tint = if (isDark) Color.White else Color.Black,
                            modifier = Modifier.size(60.dp),
                        )
                        Text(
                            stringResource(R.string.explore_suggested_users_empty),
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 18.sp,
                            color = colors.primary,
                        )
                        Text(
                            stringResource(R.string.explore_suggested_users_empty_description),
                            fontSize = 14.sp,
                            color = if (isDark) Color.White else Color.Black,
                        )
                    }
                }
            }
            else -> {
                PullToRefreshBox(
                    isRefreshing = viewModel.isLoading,
                    onRefresh = { scope.launch { viewModel.refreshUsers() } },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
                    ) {
                        items(viewModel.users, key = { it.id }) { user ->
                            SuggestedUserRow(
                                user = user,
                                commonInterests = user.interests.toSet()
                                    .intersect(viewModel.currentUserInterests.toSet()).size,
                                buttonState = viewModel.userButtonStates[user.id]
                                    ?: FollowButtonState.CAN_FOLLOW,
                                onFollow = { viewModel.followUser(user.id) },
                                onTap = {
                                    val id = user.id.trim()
                                    if (id.isNotEmpty()) {
                                        selectedProfileRoute = FeedProfileSheetRoute(id)
                                        onSelectUser(id)
                                    }
                                },
                            )
                        }
                        if (viewModel.isLoadingMore) {
                            item {
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 20.dp),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MomentsCircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.size(8.dp))
                                    Text(
                                        stringResource(R.string.explore_suggested_users_loading_more),
                                        fontSize = 14.sp,
                                        color = if (isDark) Color.White else Color.Black,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // ≡ userProfileNavigationDestination
    selectedProfileRoute?.let { route ->
        Dialog(
            onDismissRequest = { selectedProfileRoute = null },
            properties = DialogProperties(usePlatformDefaultWidth = false),
        ) {
            Surface(Modifier.fillMaxSize()) {
                UserProfileView(
                    userId = route.userId,
                    onDismiss = { selectedProfileRoute = null },
                )
            }
        }
    }
}

/** Port de `SuggestedUserRow` (Explore + CommonConnections). */
@Composable
internal fun SuggestedUserRow(
    user: AppUser,
    commonInterests: Int,
    buttonState: FollowButtonState,
    onFollow: () -> Unit,
    onTap: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()

    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onTap)
            .padding(vertical = 10.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(50.dp)
                .clip(CircleShape)
                .background(Color.Gray.copy(alpha = 0.3f))
                .clickable(onClick = onTap),
            contentAlignment = Alignment.Center,
        ) {
            if (!user.profileImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = user.profileImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (isDark) Color.White.copy(alpha = 0.6f) else Color.Black.copy(alpha = 0.6f),
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.clickable(onClick = onTap),
            ) {
                Text(
                    user.username,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = colors.primary,
                )
                if (user.isVerified) {
                    VerifiedBadge(size = 12.dp)
                }
            }
            if (commonInterests > 0) {
                Text(
                    stringResource(R.string.explore_suggested_users_common_interests, commonInterests),
                    fontSize = 12.sp,
                    color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f),
                )
            }
            if (user.interests.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    user.interests.take(2).forEach { interest ->
                        Text(
                            interest,
                            fontSize = 11.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.72f),
                            modifier = Modifier
                                .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                    if (user.interests.size > 2) {
                        Text(
                            "+${user.interests.size - 2}",
                            fontSize = 11.sp,
                            color = if (isDark) Color.White.copy(alpha = 0.8f) else Color.Black.copy(alpha = 0.8f),
                            modifier = Modifier
                                .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        SuggestedUserFollowButton(state = buttonState, onFollow = onFollow)
    }
}

/** Port de `SuggestedUserFollowButton`. */
@Composable
internal fun SuggestedUserFollowButton(
    state: FollowButtonState,
    onFollow: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val isPassive = state == FollowButtonState.REQUEST_PENDING
    val (icon, text) = suggestedFollowChrome(state)

    Row(
        Modifier
            .graphicsLayer { alpha = if (isPassive) 0.78f else 1f }
            .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = state.isActionable)
            .clickable(enabled = state.isActionable, onClick = onFollow)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (isDark) Color.White else Color.Black,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = if (isDark) Color.White else Color.Black,
        )
    }
}

@Composable
private fun suggestedFollowChrome(state: FollowButtonState): Pair<ImageVector, String> {
    val text = when (state) {
        FollowButtonState.CAN_FOLLOW -> stringResource(R.string.feed_follow)
        FollowButtonState.FOLLOWING -> stringResource(R.string.user_profile_following)
        FollowButtonState.REQUEST_PENDING -> stringResource(R.string.feed_follow_requested)
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> stringResource(R.string.feed_follow_cancel_request)
        FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.feed_follow_request)
        FollowButtonState.OWN_PROFILE -> stringResource(R.string.explore_button_own_profile)
        FollowButtonState.BLOCKED -> stringResource(R.string.explore_button_blocked)
    }
    val icon = when (state) {
        FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW -> Icons.Filled.PersonAdd
        FollowButtonState.FOLLOWING -> Icons.Filled.Person
        FollowButtonState.REQUEST_PENDING -> Icons.Filled.AccessTime
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> Icons.Filled.Close
        FollowButtonState.OWN_PROFILE -> Icons.Filled.Person
        FollowButtonState.BLOCKED -> Icons.Filled.Close
    }
    return icon to text
}
