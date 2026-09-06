package com.moments.android.views.explore.exploresections

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Collections
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.Moment
import com.moments.android.models.cache.CachedSearch
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.explore.ExploreMomentsGrid
import com.moments.android.views.feed.rememberAdaptiveColors

private val SearchAccent = Color(0xFF667EEA)

/** Port de `SearchDisplayType` (ExploreResultsSection.swift). */
private enum class SearchDisplayType {
    HASHTAG, USERS, MOMENTS, MIXED, EMPTY,
}

/**
 * Port de `SmartSearchResultsView` (ExploreResultsSection.swift).
 * Nombre `ExploreResultsSection` conservado por call sites Android.
 */
@Composable
fun ExploreResultsSection(
    searchQuery: String,
    users: List<AppUser>,
    moments: List<Moment>,
    userButtonStates: Map<String, FollowButtonState>,
    currentUserInterests: List<String> = emptyList(),
    onFollowUser: (String) -> Unit,
    onUserTap: (AppUser) -> Unit,
    onMomentTap: (Moment, Int, List<Moment>) -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    failed: Boolean = false,
    hasMore: Boolean = false,
    filter: String = "mixed",
    onFilter: (String) -> Unit = {},
    onLoadMore: () -> Unit = {},
    onRetry: () -> Unit = {},
) {
    val searchType = resolveSearchDisplayType(searchQuery, users, moments)

    Column(
        modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("mixed" to R.string.explore_global_filter_mixed, "username" to R.string.explore_global_filter_username,
                "hashtag" to R.string.explore_global_filter_hashtag, "location" to R.string.explore_global_filter_location).forEach { (value, label) ->
                FilterChip(selected = filter == value, onClick = { onFilter(value) }, label = { Text(stringResource(label)) })
            }
        }
        if (users.isNotEmpty() || moments.isNotEmpty()) {
            SearchHeader(searchQuery = searchQuery, searchType = searchType, users = users, moments = moments)
        }

        when (searchType) {
            SearchDisplayType.HASHTAG -> HashtagResultsView(
                searchQuery = searchQuery,
                moments = moments,
                onMomentTap = onMomentTap,
            )
            SearchDisplayType.USERS -> UsersResultsView(
                users = users,
                onUserTap = onUserTap,
            )
            SearchDisplayType.MOMENTS -> MomentsResultsView(
                moments = moments,
                onMomentTap = onMomentTap,
            )
            SearchDisplayType.MIXED -> MixedResultsView(
                users = users,
                moments = moments,
                onUserTap = onUserTap,
                onMomentTap = onMomentTap,
            )
            SearchDisplayType.EMPTY -> if (!isLoading && !failed && !hasMore) EmptySearchView()
        }
        ExplorePagingFooter(isLoading, failed, hasMore, onLoadMore, onRetry)
    }
}

@Composable
fun ExplorePagingFooter(isLoading: Boolean, failed: Boolean, hasMore: Boolean, onLoadMore: () -> Unit, onRetry: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when {
            isLoading -> CircularProgressIndicator(Modifier.size(24.dp), color = SearchAccent)
            failed -> {
                Text(stringResource(R.string.explore_global_error))
                TextButton(onClick = onRetry) { Text(stringResource(R.string.explore_global_retry)) }
            }
            hasMore -> TextButton(onClick = onLoadMore) { Text(stringResource(R.string.explore_global_more)) }
        }
    }
}

private fun resolveSearchDisplayType(
    searchQuery: String,
    users: List<AppUser>,
    moments: List<Moment>,
): SearchDisplayType = when {
    users.isEmpty() && moments.isEmpty() -> SearchDisplayType.EMPTY
    searchQuery.startsWith("#") -> SearchDisplayType.HASHTAG
    searchQuery.startsWith("@") -> SearchDisplayType.USERS
    users.isNotEmpty() && moments.isNotEmpty() -> SearchDisplayType.MIXED
    users.isNotEmpty() -> SearchDisplayType.USERS
    moments.isNotEmpty() -> SearchDisplayType.MOMENTS
    else -> SearchDisplayType.EMPTY
}

@Composable
private fun SearchHeader(
    searchQuery: String,
    searchType: SearchDisplayType,
    users: List<AppUser>,
    moments: List<Moment>,
) {
    val colors = rememberAdaptiveColors()
    val headerTitle = when (searchType) {
        SearchDisplayType.HASHTAG -> stringResource(R.string.explore_search_hashtag_title, searchQuery)
        SearchDisplayType.USERS -> if (searchQuery.startsWith("@")) {
            stringResource(R.string.explore_search_user_title, searchQuery.drop(1))
        } else {
            stringResource(R.string.explore_search_users_title)
        }
        SearchDisplayType.MOMENTS -> stringResource(R.string.explore_search_moments_title)
        SearchDisplayType.MIXED -> stringResource(R.string.explore_search_results_title, searchQuery)
        SearchDisplayType.EMPTY -> stringResource(R.string.explore_search_empty_title)
    }
    val headerSubtitle = when (searchType) {
        SearchDisplayType.HASHTAG,
        SearchDisplayType.MOMENTS,
        -> stringResource(R.string.explore_search_moments_found, moments.size)
        SearchDisplayType.USERS -> stringResource(R.string.explore_search_users_found, users.size)
        SearchDisplayType.MIXED -> stringResource(R.string.explore_search_mixed_found, users.size, moments.size)
        SearchDisplayType.EMPTY -> stringResource(R.string.explore_search_empty_subtitle)
    }
    val headerIcon: ImageVector = when (searchType) {
        SearchDisplayType.HASHTAG -> Icons.Filled.Tag
        SearchDisplayType.USERS -> Icons.Filled.People
        SearchDisplayType.MOMENTS -> Icons.Filled.Collections
        SearchDisplayType.MIXED -> Icons.Filled.Search
        SearchDisplayType.EMPTY -> Icons.Filled.HelpOutline
    }

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                headerTitle,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                color = colors.primary,
            )
            Text(
                headerSubtitle,
                fontSize = 14.sp,
                color = colors.secondary,
            )
        }
        Box(
            Modifier
                .size(40.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(headerIcon, contentDescription = null, tint = SearchAccent, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun HashtagResultsView(
    searchQuery: String,
    moments: List<Moment>,
    onMomentTap: (Moment, Int, List<Moment>) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.explore_search_moments_with, searchQuery),
                Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = colors.primary,
            )
            CountCapsule(count = moments.size, accent = true)
        }
        MomentsSearchGrid(moments = moments, onMomentTap = onMomentTap)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun UsersResultsView(
    users: List<AppUser>,
    onUserTap: (AppUser) -> Unit,
) {
    FlowRow(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        users.forEach { user ->
            MiniUserCard(user = user, onTap = { onUserTap(user) })
        }
    }
}

@Composable
private fun MomentsResultsView(
    moments: List<Moment>,
    onMomentTap: (Moment, Int, List<Moment>) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.explore_search_results_label),
                Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = colors.primary,
            )
            CountCapsule(count = moments.size, accent = true)
        }
        MomentsSearchGrid(moments = moments, onMomentTap = onMomentTap)
    }
}

@Composable
private fun MixedResultsView(
    users: List<AppUser>,
    moments: List<Moment>,
    onUserTap: (AppUser) -> Unit,
    onMomentTap: (Moment, Int, List<Moment>) -> Unit,
) {
    val colors = rememberAdaptiveColors()
    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        if (users.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.explore_search_users_section),
                        Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = colors.primary,
                    )
                    Text(
                        "${users.size}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.secondary,
                    )
                }
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    users.take(5).forEach { user ->
                        MiniUserCard(user = user, onTap = { onUserTap(user) })
                    }
                }
            }
        }
        if (moments.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.explore_search_moments_tab),
                        Modifier.weight(1f),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 18.sp,
                        color = colors.primary,
                    )
                    Text(
                        "${moments.size}",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        color = colors.secondary,
                    )
                }
                MomentsSearchGrid(moments = moments, onMomentTap = onMomentTap)
            }
        }
    }
}

@Composable
private fun CountCapsule(count: Int, accent: Boolean) {
    Text(
        "$count",
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = if (accent) SearchAccent else Color.Unspecified,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(SearchAccent.copy(alpha = 0.1f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Port de `MomentsSearchGrid`. */
@Composable
fun MomentsSearchGrid(
    moments: List<Moment>,
    onMomentTap: (Moment, Int, List<Moment>) -> Unit,
    modifier: Modifier = Modifier,
) {
    ExploreMomentsGrid(
        moments = moments,
        onMomentTap = onMomentTap,
        modifier = modifier,
        zoomIDPrefix = "explore-search",
    )
}

/** Port de `MiniUserCard`. */
@Composable
fun MiniUserCard(
    user: AppUser,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier
            .width(80.dp)
            .clickable(onClick = onTap),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncImage(
            model = user.profileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(60.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .background(colors.secondary.copy(alpha = 0.2f)),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                "@${user.username}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            VerifiedBadgeView(userId = user.id, size = 8.dp)
        }
    }
}

/**
 * Port de `SearchResultCard` (ExploreSuggestionsSection.swift) — usado por SmartSearchResultsView.
 */
@Composable
fun SearchResultCard(
    user: AppUser,
    buttonState: FollowButtonState,
    commonInterests: Int,
    onFollow: () -> Unit,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .momentsChromeGlass(RoundedCornerShape(20.dp), interactive = true)
            .border(1.dp, colors.primary.copy(alpha = 0.08f), RoundedCornerShape(20.dp))
            .clickable(onClick = onTap)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        AsyncImage(
            model = user.profileImagePath,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                .background(colors.secondary.copy(alpha = 0.2f)),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    "@${user.username}",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 18.sp,
                    color = colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                VerifiedBadgeView(userId = user.id, size = 14.dp)
            }
            user.bio?.takeIf { it.isNotBlank() }?.let { bio ->
                Text(
                    bio,
                    fontSize = 14.sp,
                    color = colors.secondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (commonInterests > 0) {
                Text(
                    stringResource(R.string.explore_common_interests, commonInterests),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                    color = SearchAccent,
                )
            }
        }
        if (buttonState.isActionable) {
            ExploreSuggestionsFollowButton(
                buttonState = buttonState,
                targetUserId = user.id,
                onTap = onFollow,
            )
        } else {
            PassiveFollowChip(state = buttonState)
        }
    }
}

@Composable
private fun PassiveFollowChip(state: FollowButtonState) {
    val colors = rememberAdaptiveColors()
    val icon = when (state) {
        FollowButtonState.FOLLOWING -> Icons.Filled.Check
        FollowButtonState.REQUEST_PENDING -> Icons.Filled.AccessTime
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> Icons.Filled.Close
        else -> Icons.Filled.Close
    }
    Row(
        Modifier
            .momentsChromeGlass(RoundedCornerShape(50), interactive = false)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(icon, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(14.dp))
        Text(
            state.buttonText,
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            color = colors.secondary,
        )
    }
}

/** Port de `EmptySearchView` (ExploreSuggestionsSection.swift). */
@Composable
fun EmptySearchView(modifier: Modifier = Modifier) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 28.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(76.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Search,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(31.dp),
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.explore_no_users),
                fontWeight = FontWeight.SemiBold,
                fontSize = 18.sp,
                color = colors.primary,
            )
            Text(
                stringResource(R.string.explore_no_users_subtitle),
                fontSize = 14.sp,
                color = colors.secondary,
            )
        }
    }
}

/** Port de `RecentSearchesView` (ExploreResultsSection.swift). */
@Composable
fun RecentSearchesView(
    searches: List<CachedSearch>,
    onSearchSelected: (CachedSearch) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Column(
        modifier
            .fillMaxWidth()
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.explore_recent_searches_title),
                Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = colors.primary,
            )
            if (searches.isNotEmpty()) {
                TextButton(onClick = onClearAll) {
                    Text(
                        stringResource(R.string.explore_recent_searches_clear_all),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = colors.primary,
                    )
                }
            }
        }

        if (searches.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    Icons.Filled.History,
                    contentDescription = null,
                    tint = colors.secondary.copy(alpha = 0.5f),
                    modifier = Modifier.size(32.dp),
                )
                Text(
                    stringResource(R.string.explore_recent_searches_empty),
                    fontSize = 14.sp,
                    color = colors.secondary,
                )
            }
        } else {
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                searches.forEach { search ->
                    RecentSearchRow(
                        search = search,
                        onSelect = { onSearchSelected(search) },
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSearchRow(
    search: CachedSearch,
    onSelect: () -> Unit,
) {
    val colors = rememberAdaptiveColors()
    val icon = when (search.type) {
        "user" -> Icons.Filled.Person
        "hashtag" -> Icons.Filled.Tag
        else -> Icons.Filled.Search
    }
    val typeLabel = when (search.type) {
        "user" -> stringResource(R.string.search_type_user)
        "hashtag" -> stringResource(R.string.search_type_hashtag)
        else -> stringResource(R.string.search_type_recent)
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .momentsChromeGlass(RoundedCornerShape(16.dp), interactive = true)
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier
                .size(36.dp)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = colors.secondary, modifier = Modifier.size(16.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                search.query,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                color = colors.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(typeLabel, fontSize = 12.sp, color = colors.secondary)
        }
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = colors.secondary.copy(alpha = 0.5f),
            modifier = Modifier.size(14.dp),
        )
    }
}
