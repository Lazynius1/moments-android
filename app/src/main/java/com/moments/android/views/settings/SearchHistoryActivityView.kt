package com.moments.android.views.settings

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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.models.cache.CachedSearch
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchUserProfile
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.views.profile.core.sections.profileThumbnailUrl
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val SearchAccentBlue = Color(0xFF3B82F6)

/**
 * Port 1:1 de `SearchHistoryActivityView.swift` (270 líneas).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchHistoryActivityView(
    onNavigateBack: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val textColor = if (isDark) Color.White else Color.Black
    val firestore = remember { FirestoreService() }
    val scope = rememberCoroutineScope()

    var searches by remember { mutableStateOf<List<CachedSearch>>(emptyList()) }
    var userProfiles by remember { mutableStateOf<Map<String, AppUser>>(emptyMap()) }
    var followedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var followerUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var isRefreshing by remember { mutableStateOf(false) }

    fun loadSearches() {
        searches = LocalPersistenceService.loadRecentSearches()
    }

    fun loadConnections() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            coroutineScope {
                val followingDeferred = async {
                    runCatching { firestore.fetchFollowing(currentUserId) }.getOrDefault(emptyList())
                }
                val followersDeferred = async {
                    runCatching { firestore.fetchFollowers(currentUserId) }.getOrDefault(emptyList())
                }
                followedUserIds = followingDeferred.await().map { it.id }.toSet()
                followerUserIds = followersDeferred.await().map { it.id }.toSet()
            }
        }
    }

    fun loadUserProfile(userId: String) {
        if (userProfiles.containsKey(userId)) return
        scope.launch {
            val user = runCatching { firestore.fetchUserProfile(userId) }.getOrNull() ?: return@launch
            userProfiles = userProfiles + (userId to user)
        }
    }

    fun socialStatusRes(userId: String): Int? {
        val isFollowing = followedUserIds.contains(userId)
        val isFollower = followerUserIds.contains(userId)
        return when {
            isFollowing && isFollower -> R.string.social_mutual
            isFollower -> R.string.social_follows_you
            isFollowing -> R.string.social_following
            else -> null
        }
    }

    LaunchedEffect(Unit) {
        loadSearches()
        loadConnections()
    }

    LaunchedEffect(searches) {
        searches.filter { it.type == "user" }.mapNotNull { it.targetId }.distinct().forEach { id ->
            loadUserProfile(id)
        }
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.user_activity_recent_searches_title),
        onNavigateBack = onNavigateBack,
        trailing = if (searches.isNotEmpty()) {
            {
                Text(
                    stringResource(R.string.user_activity_recent_searches_clear_all),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = SearchAccentBlue,
                    modifier = Modifier
                        .clickable {
                            LocalPersistenceService.clearSearchHistory()
                            searches = emptyList()
                        }
                        .padding(end = 8.dp),
                )
            }
        } else {
            null
        },
    ) {
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = {
                scope.launch {
                    isRefreshing = true
                    loadSearches()
                    loadConnections()
                    delay(300)
                    isRefreshing = false
                }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            if (searches.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        tint = Color.Gray.copy(alpha = 0.5f),
                        modifier = Modifier.size(40.dp),
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.user_activity_recent_searches_empty),
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Gray,
                    )
                    Spacer(Modifier.height(200.dp))
                }
            } else {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 8.dp),
                ) {
                    searches.forEachIndexed { index, search ->
                        val profile = search.targetId?.let { userProfiles[it] }
                        val statusRes = search.targetId
                            ?.takeIf { search.type == "user" }
                            ?.let { socialStatusRes(it) }
                        SearchHistoryRowView(
                            search = search,
                            userProfile = profile,
                            socialStatusRes = statusRes,
                            textColor = textColor,
                            isDark = isDark,
                            onDelete = {
                                LocalPersistenceService.deleteSearch(search.id)
                                searches = searches.filterNot { it.id == search.id }
                            },
                        )
                        if (index < searches.lastIndex) {
                            HorizontalDivider(
                                Modifier.padding(start = 68.dp),
                                color = textColor.copy(alpha = 0.12f),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHistoryRowView(
    search: CachedSearch,
    userProfile: AppUser?,
    socialStatusRes: Int?,
    textColor: Color,
    isDark: Boolean,
    onDelete: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val imagePath = userProfile?.profileImagePath
        if (search.type == "user" && !imagePath.isNullOrBlank()) {
            AsyncImage(
                model = profileThumbnailUrl(imagePath),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape),
            )
        } else {
            Box(
                Modifier
                    .size(40.dp)
                    .background(SearchAccentBlue.copy(alpha = 0.1f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    searchTypeIcon(search.type),
                    contentDescription = null,
                    tint = SearchAccentBlue,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                search.query,
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
                color = textColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (search.type == "user") {
                when {
                    socialStatusRes != null -> {
                        Text(stringResource(socialStatusRes), fontSize = 12.sp, color = Color.Gray)
                    }
                    !userProfile?.bio.isNullOrBlank() -> {
                        Text(
                            userProfile?.bio.orEmpty(),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.explore_recent_searches_type_user),
                            fontSize = 12.sp,
                            color = Color.Gray,
                        )
                    }
                }
            } else {
                Text(stringResource(searchTypeLabelRes(search.type)), fontSize = 12.sp, color = Color.Gray)
            }
        }

        Box(
            Modifier
                .size(30.dp)
                .background(
                    if (isDark) Color.White.copy(0.1f) else Color.Black.copy(0.05f),
                    CircleShape,
                )
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun searchTypeIcon(type: String): ImageVector = when (type) {
    "hashtag" -> Icons.Default.Tag
    "user" -> Icons.Default.Person
    "location" -> Icons.Default.LocationOn
    else -> Icons.Default.Search
}

private fun searchTypeLabelRes(type: String): Int = when (type) {
    "hashtag" -> R.string.explore_recent_searches_type_hashtag
    "location" -> R.string.explore_recent_searches_type_location
    else -> R.string.explore_recent_searches_type_text
}
