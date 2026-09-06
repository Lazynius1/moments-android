package com.moments.android.views.explore

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.statusBarsPadding
import com.moments.android.views.profile.core.sections.UserProfileZoomNavigationHost
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
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.ModernFollowButtonStyle
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.feed.core.FeedProfileSheetRoute
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.launch

/**
 * Port de `SuggestedUsersView.swift`.
 * Full-page navigation with back; list position survives profile navigation.
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

    BackHandler {
        if (selectedProfileRoute != null) selectedProfileRoute = null else onNavigateBack()
    }

    UserProfileZoomNavigationHost(
        profileRoute = selectedProfileRoute,
        onProfileRouteChange = { selectedProfileRoute = it },
        modifier = modifier.fillMaxSize(),
    ) { _ ->
    Column(
        Modifier.fillMaxSize().background(colors.surfaceBackground),
    ) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onNavigateBack) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.common_back), tint = colors.primary)
            }
            Text(stringResource(R.string.explore_suggested_users_title),
                modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp, color = colors.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.size(48.dp))
        }

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
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 20.dp),
                    ) {
                        items(viewModel.users, key = { it.id }) { user ->
                            SuggestedDiscoveryRow(
                                user = user,
                                viewerInterests = viewModel.currentUserInterests,
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

    } // UserProfileZoomNavigationHost
}

@Composable
private fun SuggestedDiscoveryRow(
    user: AppUser,
    viewerInterests: List<String>,
    buttonState: FollowButtonState,
    onFollow: () -> Unit,
    onTap: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val own = viewerInterests.map { it.trim().lowercase() }.toSet()
    val shared = user.interests.filter { it.trim().lowercase() in own }.distinctBy { it.trim().lowercase() }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(52.dp).clip(CircleShape).background(colors.secondary.copy(alpha = 0.12f)).clickable(onClick = onTap),
                    contentAlignment = Alignment.Center) {
                    if (user.profileImagePath.isNullOrBlank()) {
                        Icon(Icons.Filled.Person, contentDescription = user.username, tint = colors.secondary)
                    } else {
                        AsyncImage(model = user.profileImagePath, contentDescription = user.username,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Column(Modifier.weight(1f).clickable(onClick = onTap), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(user.username, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = colors.primary,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        if (user.isVerified) VerifiedBadge(size = 12.dp)
                    }
                    if (shared.isNotEmpty()) {
                        Text(stringResource(R.string.explore_suggested_users_common_interests, shared.size),
                            fontSize = 12.sp, color = colors.secondary)
                    }
                }
                SuggestedUserFollowButton(state = buttonState, targetUserId = user.id, onFollow = onFollow)
            }
            Column(Modifier.fillMaxWidth().clickable(onClick = onTap), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                user.bio?.trim()?.takeIf { it.isNotEmpty() }?.let { bio ->
                    Text(bio, fontSize = 14.sp, color = colors.secondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                val interests = shared.ifEmpty { user.interests }
                if (interests.isNotEmpty()) {
                    Text(interests.take(3).joinToString(" · "), fontSize = 14.sp,
                        color = if (shared.isEmpty()) colors.secondary else colors.primary,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            HorizontalDivider(Modifier.padding(top = 6.dp), color = colors.secondary.copy(alpha = 0.15f))
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

        SuggestedUserFollowButton(
            state = buttonState,
            targetUserId = user.id,
            onFollow = onFollow,
        )
    }
}

/** Port de `SuggestedUserFollowButton` — usa el botón canónico. */
@Composable
internal fun SuggestedUserFollowButton(
    state: FollowButtonState,
    targetUserId: String,
    onFollow: () -> Unit,
) {
    ModernFollowButton(
        state = state,
        isLoading = false,
        targetUserId = targetUserId,
        onClick = onFollow,
        style = ModernFollowButtonStyle.COMPACT,
    )
}
