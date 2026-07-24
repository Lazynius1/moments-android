package com.moments.android.models

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.fetchMutuals
import com.moments.android.services.firestore.fetchUser
import com.moments.android.services.firestore.searchUsers
import com.moments.android.services.social.BestFriendsService
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Estado de `BestFriendsViewModel`, con el mismo filtrado por texto en las cuatro secciones. */
private data class BestFriendsState(
    val bestFriends: List<AppUser> = emptyList(),
    val following: List<AppUser> = emptyList(),
    val mutuals: List<AppUser> = emptyList(),
    val followers: List<AppUser> = emptyList(),
    val remoteResults: List<AppUser> = emptyList(),
) {
    fun filtered(searchText: String): BestFriendsState {
        val query = searchText.trim().lowercase()
        if (query.isEmpty()) return this
        fun List<AppUser>.match() = filter {
            it.username.lowercase().contains(query) || it.bio?.lowercase()?.contains(query) == true
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
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (dark) Color.White else Color(0xFF0B1215)
    val secondary = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF52626A)

    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val bestFriendsService = remember { BestFriendsService(firestore) }

    var state by remember { mutableStateOf(BestFriendsState()) }
    var searchText by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    suspend fun reloadBestFriends() {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching { bestFriendsService.fetchBestFriends(userId) }
            .onSuccess { state = state.copy(bestFriends = it) }
            .onFailure { errorMessage = it.message }
    }

    LaunchedEffect(Unit) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid
        if (userId == null) {
            errorMessage = "auth"
            isLoading = false
            return@LaunchedEffect
        }
        isLoading = true
        val profile = runCatching { firestore.fetchUser(userId) }.getOrNull()
        val blocked = profile?.blockedUsers.orEmpty().toSet()
        val following = runCatching { firestore.fetchFollowing(userId) }.getOrDefault(emptyList())
        val followers = runCatching { firestore.fetchFollowers(userId) }.getOrDefault(emptyList())
        val mutuals = runCatching { firestore.fetchMutuals(userId) }.getOrDefault(emptyList())
        state = state.copy(
            following = following.filterNot { it.id in blocked },
            followers = followers.filterNot { it.id in blocked },
            mutuals = mutuals.filterNot { it.id in blocked },
        )
        reloadBestFriends()
        isLoading = false
    }

    // Búsqueda global con rebote, como el `searchWorkItem` de iOS.
    LaunchedEffect(searchText) {
        val query = searchText.trim()
        if (query.length < 2) {
            state = state.copy(remoteResults = emptyList())
            return@LaunchedEffect
        }
        delay(350)
        state = state.copy(
            remoteResults = runCatching { firestore.searchUsers(query) }.getOrDefault(emptyList()),
        )
    }

    val visible = state.filtered(searchText)
    val bestFriendIds = state.bestFriends.map { it.id }.toSet()

    fun toggle(user: AppUser) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            runCatching {
                if (user.id in bestFriendIds) {
                    bestFriendsService.removeBestFriend(currentUserId, user.id)
                } else {
                    bestFriendsService.addBestFriend(currentUserId, user.id)
                }
            }.onFailure { errorMessage = it.message }
            reloadBestFriends()
        }
    }

    Column(modifier.fillMaxSize().background(canvas)) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.common_close),
                tint = primary,
                modifier = Modifier.size(30.dp).clickable(onClick = onDismiss),
            )
            Text(
                stringResource(R.string.best_friends_title),
                color = primary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            Box(Modifier.size(30.dp))
        }

        OutlinedTextField(
            value = searchText,
            onValueChange = { searchText = it },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
            placeholder = { Text(stringResource(R.string.best_friends_search_placeholder)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
        )

        errorMessage?.let {
            Text(it, color = Color(0xFFFF3B30), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        }

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = primary)
            }
            return@Column
        }

        val titleBestFriends = stringResource(R.string.best_friends_title)
        val titleMutuals = stringResource(R.string.best_friends_section_mutuals)
        val titleFollowing = stringResource(R.string.best_friends_section_following)
        val titleFollowers = stringResource(R.string.best_friends_section_followers)
        val titleSuggested = stringResource(R.string.explore_suggested_users_title)

        LazyColumn(Modifier.fillMaxSize()) {
            section(titleBestFriends, visible.bestFriends, secondary) { user ->
                SelectableBestFriendRow(user, isSelected = true, onToggle = { toggle(user) })
            }
            section(titleMutuals, visible.mutuals.filterNot { it.id in bestFriendIds }, secondary) { user ->
                SelectableBestFriendRow(user, isSelected = false, onToggle = { toggle(user) })
            }
            section(titleFollowing, visible.following.filterNot { it.id in bestFriendIds }, secondary) { user ->
                SelectableBestFriendRow(user, isSelected = false, onToggle = { toggle(user) })
            }
            section(titleFollowers, visible.followers.filterNot { it.id in bestFriendIds }, secondary) { user ->
                SelectableBestFriendRow(user, isSelected = false, onToggle = { toggle(user) })
            }
            val remote = state.remoteResults.filterNot { candidate ->
                candidate.id in bestFriendIds ||
                    visible.following.any { it.id == candidate.id } ||
                    visible.followers.any { it.id == candidate.id } ||
                    visible.mutuals.any { it.id == candidate.id }
            }
            section(titleSuggested, remote, secondary) { user ->
                SelectableBestFriendRow(user, isSelected = false, onToggle = { toggle(user) })
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.section(
    title: String,
    users: List<AppUser>,
    titleColor: Color,
    row: @Composable (AppUser) -> Unit,
) {
    if (users.isEmpty()) return
    item(key = "header-$title") {
        Text(
            title.uppercase(),
            color = titleColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
        )
    }
    items(users, key = { "$title-${it.id}" }) { user -> row(user) }
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
    val primary = if (dark) Color.White else Color(0xFF0B1215)
    val secondary = if (dark) Color.White.copy(alpha = 0.6f) else Color(0xFF52626A)

    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(primary.copy(alpha = 0.1f))) {
            user.profileImagePath?.takeIf { it.isNotBlank() }?.let {
                AsyncImage(
                    model = it,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(user.username, color = primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (isSelected) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFF34C759), modifier = Modifier.size(13.dp))
                }
            }
            user.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Icon(
            if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) Color(0xFF34C759) else secondary,
            modifier = Modifier.size(24.dp),
        )
    }
}
