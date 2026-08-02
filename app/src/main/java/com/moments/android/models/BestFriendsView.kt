package com.moments.android.models

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.coordinators.ProfileImageView
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.views.settings.SettingsSubsectionWrapper
import com.moments.android.views.settings.SettingsSearchField
import com.moments.android.services.firestore.fetchUsersInBatches
import com.moments.android.services.firestore.searchUsersUncapped
import com.moments.android.services.social.BestFriendsService
import com.moments.android.utilities.momentsEmptyStateAppear
import com.moments.android.views.components.UserRowSkeletonList
import com.moments.android.views.messaging.components.momentsScrollEdgeChrome
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Locale

/** Estado de `BestFriendsViewModel`, con el mismo filtrado por texto en las cuatro secciones. */
private data class BestFriendsState(
    val bestFriends: List<AppUser> = emptyList(),
    val following: List<AppUser> = emptyList(),
    val mutuals: List<AppUser> = emptyList(),
    val followers: List<AppUser> = emptyList(),
    val remoteResults: List<AppUser> = emptyList(),
) {
    fun filtered(searchText: String): BestFriendsState {
        val query = searchText.lowercase(Locale.getDefault()).trim()
        if (query.isEmpty()) return this
        fun List<AppUser>.match() = filter {
            it.username.lowercase(Locale.getDefault()).contains(query) ||
                it.bio?.lowercase(Locale.getDefault())?.contains(query) == true
        }
        return copy(
            bestFriends = bestFriends.match(),
            following = following.match(),
            mutuals = mutuals.match(),
            followers = followers.match(),
        )
    }
}

/** Port de `BestFriendsView`: gestión de la lista de mejores amigos. */
@Composable
fun BestFriendsView(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    val secondary = Color.Gray.copy(alpha = 0.8f)

    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val db = remember { FirebaseFirestore.getInstance() }
    val bestFriendsService = remember { BestFriendsService(firestore) }

    var state by remember { mutableStateOf(BestFriendsState()) }
    var searchText by remember { mutableStateOf("") }
    var visibleUserLimit by remember { mutableIntStateOf(30) }
    var isSearchFocused by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showError by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var blockedUserIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid }

    val authError = stringResource(R.string.best_friends_error_auth)
    val followingErrorFmt = stringResource(R.string.best_friends_error_following)
    val followersErrorFmt = stringResource(R.string.best_friends_error_followers)

    fun presentError(message: String) {
        errorMessage = message
        showError = true
    }

    suspend fun reloadBestFriends() {
        val userId = currentUserId ?: run {
            presentError(authError)
            return
        }
        runCatching { bestFriendsService.fetchBestFriends(userId) }
            .onSuccess { state = state.copy(bestFriends = it) }
            .onFailure {
                presentError(it.message ?: authError)
            }
    }

    suspend fun fetchConnections() {
        val userId = currentUserId ?: run {
            presentError(authError)
            isLoading = false
            return
        }
        isLoading = true
        runCatching {
            val blockedSnap = db.collection("users").document(userId).get().await()
            @Suppress("UNCHECKED_CAST")
            val blocked = (blockedSnap.get("blockedUsers") as? List<*>)?.filterIsInstance<String>().orEmpty()
            blockedUserIds = blocked.toSet()

            val followingIds = db.collection("users").document(userId).collection("following")
                .get().await().documents.map { it.id }
            val followerIds = db.collection("users").document(userId).collection("followers")
                .get().await().documents.map { it.id }

            val followingSet = followingIds.toSet()
            val followersSet = followerIds.toSet()
            val mutualIds = followingSet.intersect(followersSet)
            val connectionIds = followingSet - mutualIds
            val followerOnlyIds = followersSet - mutualIds

            val followingUsers = firestore.fetchUsersInBatches(connectionIds.toList())
            val mutualUsers = firestore.fetchUsersInBatches(mutualIds.toList())
            val followerUsers = firestore.fetchUsersInBatches(followerOnlyIds.toList())
            state = state.copy(
                following = followingUsers,
                mutuals = mutualUsers,
                followers = followerUsers,
            )
        }.onFailure { err ->
            val msg = err.message.orEmpty()
            presentError(
                when {
                    msg.contains("following", ignoreCase = true) ->
                        followingErrorFmt.format(msg)
                    msg.contains("followers", ignoreCase = true) ->
                        followersErrorFmt.format(msg)
                    else -> msg.ifBlank { authError }
                },
            )
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        if (currentUserId == null) {
            presentError(authError)
            isLoading = false
            return@LaunchedEffect
        }
        // ≡ iOS onAppear: fetchBestFriends + fetchConnections en paralelo
        launch { reloadBestFriends() }
        launch { fetchConnections() }
    }

    LaunchedEffect(searchText) {
        visibleUserLimit = 30
        val cleanQuery = searchText.lowercase(Locale.getDefault()).trim()
        if (cleanQuery.isEmpty()) {
            state = state.copy(remoteResults = emptyList())
            return@LaunchedEffect
        }
        delay(250)
        val users = runCatching { firestore.searchUsersUncapped(cleanQuery) }.getOrDefault(emptyList())
        val filtered = users.filter { user ->
            if (user.id == currentUserId) return@filter false
            if (user.id in blockedUserIds) return@filter false
            if (currentUserId != null && currentUserId in user.blockedUsers) return@filter false
            true
        }
        state = state.copy(remoteResults = filtered)
    }

    val filtered = state.filtered(searchText)
    val trimmedSearch = searchText.trim()
    val isSearchingMode = isSearchFocused || trimmedSearch.isNotEmpty()
    val selectedIds = state.bestFriends.map { it.id }.toSet()
    val hasAnyUsers = state.bestFriends.isNotEmpty() ||
        state.following.isNotEmpty() ||
        state.mutuals.isNotEmpty() ||
        state.followers.isNotEmpty()

    fun deduplicated(users: List<AppUser>): List<AppUser> {
        val seen = mutableSetOf<String>()
        return users.filter { seen.add(it.id) }
    }

    val selectedUsers = remember(filtered.bestFriends) {
        deduplicated(filtered.bestFriends)
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.username })
    }
    val visibleMutuals = filtered.mutuals.filter { it.id !in selectedIds }
    val visibleConnections = filtered.following.filter { it.id !in selectedIds }
    val suggestedUsers = remember(
        visibleMutuals,
        visibleConnections,
        filtered.followers,
        state.remoteResults,
        selectedIds,
    ) {
        deduplicated(
            visibleMutuals + visibleConnections + filtered.followers + state.remoteResults,
        )
            .filter { it.id !in selectedIds }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.username })
    }
    val displayedSuggested = suggestedUsers.take(visibleUserLimit)

    fun toggle(user: AppUser) {
        val uid = currentUserId ?: run {
            presentError(authError)
            return
        }
        scope.launch {
            runCatching {
                if (user.id in selectedIds) {
                    bestFriendsService.removeBestFriend(uid, user.id)
                } else {
                    bestFriendsService.addBestFriend(uid, user.id)
                }
            }.onFailure {
                presentError(it.message ?: authError)
            }
            reloadBestFriends()
        }
    }

    SettingsSubsectionWrapper(
        title = stringResource(R.string.best_friends_title),
        onNavigateBack = onDismiss,
        modifier = modifier,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .imePadding(),
        ) {
        Column(Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    UserRowSkeletonList(
                        rows = 6,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .padding(top = 12.dp),
                    )
                }
                !hasAnyUsers -> {
                    BestFriendsEmptyState(modifier = Modifier.fillMaxSize())
                }
                else -> {
                    Column(Modifier.fillMaxSize()) {
                        SettingsSearchField(
                            value = searchText,
                            onValueChange = { searchText = it },
                            placeholder = stringResource(R.string.best_friends_search_placeholder),
                            onFocusChanged = { isSearchFocused = it },
                            modifier = Modifier
                                .padding(horizontal = 16.dp)
                                .padding(top = 16.dp, bottom = 8.dp),
                        )

                        val listState = rememberLazyListState()
                        // ≡ iOS loadMoreIfNeeded: +30 al acercarse al final (una sola vez por umbral)
                        LaunchedEffect(listState, displayedSuggested.size, suggestedUsers.size, visibleUserLimit) {
                            snapshotFlow {
                                listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
                            }.collect { lastVisible ->
                                if (lastVisible == null) return@collect
                                val threshold = maxOf(displayedSuggested.size - 5, 0)
                                if (lastVisible >= threshold && visibleUserLimit < suggestedUsers.size) {
                                    visibleUserLimit += 30
                                }
                            }
                        }
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .momentsScrollEdgeChrome()
                                .padding(horizontal = 16.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
                        ) {
                            if (!isSearchingMode && selectedUsers.isNotEmpty()) {
                                item(key = "header-selected") {
                                    UserSectionHeader(
                                        stringResource(R.string.best_friends_title),
                                        secondary,
                                        Modifier.padding(top = 8.dp, bottom = 8.dp),
                                    )
                                }
                                items(selectedUsers, key = { "sel-${it.id}" }) { user ->
                                    SelectableBestFriendRow(
                                        user = user,
                                        isSelected = true,
                                        onToggle = { toggle(user) },
                                    )
                                }
                            }

                            if (!isSearchingMode || displayedSuggested.isNotEmpty()) {
                                item(key = "header-suggested") {
                                    UserSectionHeader(
                                        stringResource(R.string.explore_suggested_users_title),
                                        secondary,
                                        Modifier.padding(top = 24.dp, bottom = 8.dp),
                                    )
                                }
                                items(
                                    displayedSuggested,
                                    key = { "sug-${it.id}" },
                                ) { user ->
                                    SelectableBestFriendRow(
                                        user = user,
                                        isSelected = user.id in selectedIds,
                                        onToggle = { toggle(user) },
                                    )
                                }
                            }

                            if (displayedSuggested.isEmpty() && isSearchingMode) {
                                item(key = "no-results") {
                                    Row(
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 20.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Icon(Icons.Filled.Search, null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                        Text(
                                            stringResource(R.string.best_friends_search_no_results, searchText),
                                            color = Color.Gray,
                                            fontSize = 14.sp,
                                            modifier = Modifier.padding(start = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showError) {
            AlertDialog(
                onDismissRequest = { showError = false },
                title = { Text(stringResource(R.string.common_error)) },
                text = {
                    Text(errorMessage ?: stringResource(R.string.best_friends_error_auth))
                },
                confirmButton = {
                    TextButton(onClick = { showError = false }) {
                        Text(stringResource(R.string.common_ok))
                    }
                },
            )
        }
        }
    }
}

@Composable
private fun UserSectionHeader(title: String, color: Color, modifier: Modifier = Modifier) {
    Text(
        title.uppercase(Locale.getDefault()),
        color = color,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp),
    )
}

@Composable
private fun BestFriendsEmptyState(modifier: Modifier = Modifier) {
    Column(
        modifier
            .padding(16.dp)
            .momentsEmptyStateAppear(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(
            Icons.Filled.PersonOff,
            contentDescription = null,
            tint = Color.Gray,
            modifier = Modifier.size(50.dp),
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                stringResource(R.string.best_friends_empty_title),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                stringResource(R.string.best_friends_empty_description),
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.weight(1f))
    }
}

/** Port de `SelectableBestFriendRow`. */
@Composable
fun SelectableBestFriendRow(
    user: AppUser,
    isSelected: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ProfileImageView(
            imagePath = user.profileImagePath,
            modifier = Modifier.size(40.dp),
        )
        Text(
            user.username,
            color = primary,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) {
                primary
            } else if (dark) {
                Color.White.copy(0.32f)
            } else {
                Color.Black.copy(0.28f)
            },
            modifier = Modifier.size(24.dp),
        )
    }
}

/** Port de `BestFriendRow` (legacy / no usado por el contentView actual de iOS). */
@Composable
fun BestFriendRow(
    user: AppUser,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileImageView(imagePath = user.profileImagePath, modifier = Modifier.size(40.dp))
        Text(user.username, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text(
            stringResource(R.string.best_friends_button_remove),
            color = Color.Red,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color.Red.copy(0.1f), RoundedCornerShape(8.dp))
                .clickable(onClick = onRemove)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Port de `ConnectionRow` (legacy). */
@Composable
fun ConnectionRow(
    user: AppUser,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileImageView(imagePath = user.profileImagePath, modifier = Modifier.size(40.dp))
        Text(user.username, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text(
            stringResource(R.string.best_friends_button_add),
            color = Color(0xFF007AFF),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color(0xFF007AFF).copy(0.1f), RoundedCornerShape(8.dp))
                .clickable(onClick = onAdd)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

/** Port de `FollowerRow` (legacy). */
@Composable
fun FollowerRow(
    user: AppUser,
    onAdd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProfileImageView(imagePath = user.profileImagePath, modifier = Modifier.size(40.dp))
        Text(user.username, fontSize = 14.sp, modifier = Modifier.weight(1f).padding(start = 12.dp))
        Text(
            stringResource(R.string.best_friends_button_add_generic),
            color = Color(0xFFFF9500),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .background(Color(0xFFFF9500).copy(0.1f), RoundedCornerShape(8.dp))
                .clickable(onClick = onAdd)
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}
