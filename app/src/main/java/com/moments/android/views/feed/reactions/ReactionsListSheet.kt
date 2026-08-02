package com.moments.android.views.feed.reactions

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsEmptyStateAppear
import com.moments.android.utilities.momentsPressSubtle
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.shared.MomentsModalSheet
import kotlinx.coroutines.launch

private data class ReactionGroup(
    val type: ReactionType,
    val users: List<String>,
    val count: Int,
)

/**
 * Port de `ReactionsListSheet` (`MomentReactionButton.swift`).
 */
@Composable
fun ReactionsListSheet(
    momentId: String,
    authorId: String,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val colors = rememberAdaptiveColors()
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val uid = FirebaseAuth.getInstance().currentUser?.uid

    var reactions by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var userProfiles by remember { mutableStateOf<Map<String, AppUser>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(true) }
    var followStates by remember { mutableStateOf<Map<String, FollowButtonState>>(emptyMap()) }
    var followLoadingStates by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var pendingUnfollowUserId by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }

    val reactionGroups = remember(reactions) {
        reactions.mapNotNull { (raw, userIds) ->
            val type = ReactionType.fromRaw(raw) ?: return@mapNotNull null
            if (userIds.isEmpty()) return@mapNotNull null
            ReactionGroup(type, userIds, userIds.size)
        }.sortedByDescending { it.count }
    }

    val filteredReactionGroups = remember(reactionGroups, searchText, userProfiles) {
        if (searchText.isEmpty()) reactionGroups
        else {
            reactionGroups.mapNotNull { group ->
                val filtered = group.users.filter { userId ->
                    val user = userProfiles[userId] ?: return@filter false
                    user.username.contains(searchText, ignoreCase = true) ||
                        (user.bio?.contains(searchText, ignoreCase = true) == true)
                }
                if (filtered.isEmpty()) null
                else ReactionGroup(group.type, filtered, filtered.size)
            }
        }
    }

    LaunchedEffect(momentId, authorId) {
        isLoading = true
        val fetched = runCatching { firestore.fetchReactions(momentId, authorId) }
            .getOrDefault(emptyMap())
        reactions = fetched
        val allUserIds = fetched.values.flatten().distinct()
        if (allUserIds.isEmpty()) {
            isLoading = false
            return@LaunchedEffect
        }
        val users = runCatching { firestore.fetchUsers(allUserIds) }.getOrDefault(emptyList())
        userProfiles = users.associateBy { it.id }
        val viewer = uid
        if (viewer != null) {
            val next = followStates.toMutableMap()
            for (userId in userProfiles.keys) {
                if (userId == viewer) continue
                FollowStateStore.state(userId)?.let { next[userId] = it }
                val authoritative = PrivacyService.getFollowButtonState(viewer, userId)
                val reconciled = FollowStateStore.reconciledState(authoritative, userId)
                next[userId] = reconciled
                FollowStateStore.setState(reconciled, userId)
            }
            followStates = next
        }
        isLoading = false
    }

    DisposableEffect(Unit) {
        val listener: (String, FollowButtonState) -> Unit = { userId, state ->
            followStates = followStates + (userId to state)
        }
        FollowStateStore.addListener(listener)
        onDispose { FollowStateStore.removeListener(listener) }
    }

    // Search field arriba — medium OK; si hubiera footer abajo → largeOnly.
    MomentsModalSheet(onDismissRequest = onDismiss, largeOnly = false) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 0.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.reactions_title),
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = stringResource(
                        if (filteredReactionGroups.size == 1) R.string.reactions_type_count_single
                        else R.string.reactions_type_count,
                        filteredReactionGroups.size,
                    ),
                    color = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.7f),
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                )
            }

            Row(
                Modifier
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
                    .fillMaxWidth()
                    .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                BasicTextField(
                    value = searchText,
                    onValueChange = { searchText = it },
                    singleLine = true,
                    cursorBrush = SolidColor(if (isDark) Color.White else Color.Black),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = if (isDark) Color.White else Color.Black,
                        fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                    ),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (searchText.isEmpty()) {
                            Text(
                                text = stringResource(R.string.reactions_search_placeholder),
                                color = Color.Gray,
                                fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                            )
                        }
                        inner()
                    },
                )
                if (searchText.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { searchText = "" },
                    )
                }
            }

            when {
                isLoading -> {
                    Column(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(20.dp))
                        Text(
                            stringResource(R.string.reactions_loading),
                            color = colors.secondary,
                            fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() },
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                filteredReactionGroups.isEmpty() && reactionGroups.isEmpty() -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .momentsEmptyStateAppear(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(76.dp)
                                .momentsChromeGlass(CircleShape, interactive = false),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = if (isDark) Color.White else Color.Black,
                                modifier = Modifier.size(31.dp),
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.reactions_empty_title),
                            color = if (isDark) Color.White else Color.Black,
                            fontSize = with(density) { legacyPoppinsSize(context, 18).toSp() },
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.reactions_empty_subtitle),
                            color = if (isDark) Color.White.copy(0.58f) else Color.Black.copy(0.52f),
                            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 28.dp),
                        )
                    }
                }
                filteredReactionGroups.isEmpty() -> {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .padding(horizontal = 28.dp)
                            .momentsEmptyStateAppear(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            Modifier
                                .size(76.dp)
                                .momentsChromeGlass(CircleShape, interactive = false),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Search, null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(31.dp))
                        }
                        Spacer(Modifier.height(16.dp))
                        Text(
                            stringResource(R.string.reactions_search_no_results_title),
                            color = if (isDark) Color.White else Color.Black,
                            fontSize = with(density) { legacyPoppinsSize(context, 18).toSp() },
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            stringResource(R.string.reactions_search_no_results_subtitle),
                            color = if (isDark) Color.White.copy(0.58f) else Color.Black.copy(0.52f),
                            fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                            textAlign = TextAlign.Center,
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        items(filteredReactionGroups, key = { it.type.rawValue }) { group ->
                            ReactionGroupBlock(
                                group = group,
                                userProfiles = userProfiles,
                                followStates = followStates,
                                followLoadingStates = followLoadingStates,
                                currentUserId = uid,
                                onFollowClick = { userId ->
                                    val state = followStates[userId] ?: FollowButtonState.CAN_FOLLOW
                                    if (state == FollowButtonState.FOLLOWING) {
                                        pendingUnfollowUserId = userId
                                    } else {
                                        scope.launch {
                                            performFollowAction(
                                                firestore = firestore,
                                                userId = userId,
                                                currentUserId = uid,
                                                currentState = state,
                                                onLoading = { loading ->
                                                    followLoadingStates = followLoadingStates + (userId to loading)
                                                },
                                                onState = { next ->
                                                    followStates = followStates + (userId to next)
                                                },
                                            )
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    pendingUnfollowUserId?.let { unfollowId ->
        AlertDialog(
            onDismissRequest = { pendingUnfollowUserId = null },
            title = { Text(stringResource(R.string.user_profile_unfollow_confirm_title)) },
            text = { Text(stringResource(R.string.user_profile_unfollow_confirm_message)) },
            confirmButton = {
                TextButton({
                    pendingUnfollowUserId = null
                    scope.launch {
                        val state = followStates[unfollowId] ?: FollowButtonState.FOLLOWING
                        performFollowAction(
                            firestore = firestore,
                            userId = unfollowId,
                            currentUserId = uid,
                            currentState = state,
                            onLoading = { loading ->
                                followLoadingStates = followLoadingStates + (unfollowId to loading)
                            },
                            onState = { next ->
                                followStates = followStates + (unfollowId to next)
                            },
                        )
                    }
                }) {
                    Text(stringResource(R.string.user_profile_unfollow_confirm_action))
                }
            },
            dismissButton = {
                TextButton({ pendingUnfollowUserId = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}

@Composable
private fun ReactionGroupBlock(
    group: ReactionGroup,
    userProfiles: Map<String, AppUser>,
    followStates: Map<String, FollowButtonState>,
    followLoadingStates: Map<String, Boolean>,
    currentUserId: String?,
    onFollowClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val visibleUsers = group.users.take(10)

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(group.type.color.copy(0.2f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(group.type.filledIcon, color = group.type.color, fontSize = with(density) { legacyPoppinsSize(context, 18).toSp() }, fontWeight = FontWeight.Bold)
            }
            Column {
                Text(
                    group.type.displayName,
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    stringResource(
                        if (group.count == 1) R.string.reactions_people_count_single else R.string.reactions_people_count,
                        group.count,
                    ),
                    color = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.7f),
                    fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                )
            }
        }

        Column {
            visibleUsers.forEachIndexed { index, userId ->
                ReactionUserRow(
                    userId = userId,
                    reactionType = group.type,
                    profile = userProfiles[userId],
                    followState = followStates[userId] ?: FollowButtonState.CAN_FOLLOW,
                    followLoading = followLoadingStates[userId] == true,
                    currentUserId = currentUserId,
                    onFollowClick = { onFollowClick(userId) },
                )
                if (index < visibleUsers.lastIndex) {
                    HorizontalDivider(
                        Modifier.padding(start = 52.dp),
                        color = if (isDark) Color.White.copy(0.18f) else Color.Black.copy(0.12f),
                    )
                }
            }
            if (group.users.size > 10) {
                Text(
                    stringResource(R.string.reactions_more, group.users.size - 10),
                    color = if (isDark) Color.White.copy(0.55f) else Color.Black.copy(0.45f),
                    fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ReactionUserRow(
    userId: String,
    reactionType: ReactionType,
    profile: AppUser?,
    followState: FollowButtonState,
    followLoading: Boolean,
    currentUserId: String?,
    onFollowClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .background(Color.Gray.copy(0.15f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (!profile?.profileImagePath.isNullOrBlank()) {
                AsyncImage(
                    model = profile!!.profileImagePath,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape),
                )
            } else {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = if (isDark) Color.White.copy(0.55f) else Color.Black.copy(0.35f),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = profile?.username ?: stringResource(R.string.messaging_user_default),
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = with(density) { legacyPoppinsSize(context, 14).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
                if (profile?.isVerified == true) {
                    VerifiedBadge(size = 12.dp)
                }
            }
            Text(
                stringResource(R.string.reactions_user_reacted, reactionType.displayName),
                color = if (isDark) Color.White.copy(0.7f) else Color.Black.copy(0.7f),
                fontSize = with(density) { legacyPoppinsSize(context, 11).toSp() },
            )
        }

        if (userId != currentUserId) {
            ReactionFollowChip(
                state = followState,
                loading = followLoading,
                onClick = onFollowClick,
            )
        }

        Text(reactionType.filledIcon, color = reactionType.color, fontSize = with(density) { legacyPoppinsSize(context, 16).toSp() })
    }
}

@Composable
private fun ReactionFollowChip(
    state: FollowButtonState,
    loading: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val isDark = isSystemInDarkTheme()
    val (icon, title) = followChrome(state)
    val passive = state == FollowButtonState.REQUEST_PENDING

    Box(
        Modifier
            .graphicsLayer { alpha = if (passive) 0.78f else 1f }
            .momentsPressSubtle()
            .clip(RoundedCornerShape(12.dp))
            .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = state.isActionable)
            .clickable(enabled = !loading && state.isActionable, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(14.dp),
                color = if (isDark) Color.White else Color.Black,
                strokeWidth = 1.5.dp,
            )
        } else {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(icon, null, tint = if (isDark) Color.White else Color.Black, modifier = Modifier.size(12.dp))
                Text(
                    title,
                    color = if (isDark) Color.White else Color.Black,
                    fontSize = with(density) { legacyPoppinsSize(context, 12).toSp() },
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun followChrome(state: FollowButtonState): Pair<ImageVector, String> {
    val title = when (state) {
        FollowButtonState.FOLLOWING -> stringResource(R.string.user_profile_following)
        FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.feed_follow_request)
        FollowButtonState.REQUEST_PENDING -> stringResource(R.string.feed_follow_requested)
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> stringResource(R.string.feed_follow_cancel_request)
        FollowButtonState.BLOCKED -> stringResource(R.string.explore_button_blocked)
        else -> stringResource(R.string.user_profile_follow)
    }
    val icon = when (state) {
        FollowButtonState.FOLLOWING -> Icons.Filled.Person
        FollowButtonState.CAN_REQUEST_FOLLOW -> Icons.Filled.PersonAdd
        FollowButtonState.REQUEST_PENDING -> Icons.Filled.AccessTime
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> Icons.Filled.Close
        FollowButtonState.BLOCKED -> Icons.Filled.Close
        else -> Icons.Filled.PersonAdd
    }
    return icon to title
}

private suspend fun performFollowAction(
    firestore: FirestoreService,
    userId: String,
    currentUserId: String?,
    currentState: FollowButtonState,
    onLoading: (Boolean) -> Unit,
    onState: (FollowButtonState) -> Unit,
) {
    val uid = currentUserId ?: return
    if (!currentState.isActionable) return
    onLoading(true)
    val result = runCatching {
        when (currentState) {
            FollowButtonState.FOLLOWING -> {
                firestore.unfollowUser(uid, userId)
                FollowButtonState.CAN_FOLLOW
            }
            FollowButtonState.REQUEST_PENDING_CANCELLABLE -> {
                firestore.cancelFollowRequest(uid, userId)
                FollowButtonState.CAN_REQUEST_FOLLOW
            }
            FollowButtonState.CAN_REQUEST_FOLLOW -> {
                firestore.followUser(uid, userId)
                FollowButtonState.REQUEST_PENDING_CANCELLABLE
            }
            else -> {
                firestore.followUser(uid, userId)
                FollowButtonState.FOLLOWING
            }
        }
    }
    onLoading(false)
    result.onSuccess { next ->
        if (currentState == FollowButtonState.FOLLOWING) HapticManager.shared.lightImpact()
        else if (currentState == FollowButtonState.CAN_FOLLOW || currentState == FollowButtonState.CAN_REQUEST_FOLLOW) {
            HapticManager.shared.mediumImpact()
        }
        onState(next)
        FollowStateStore.setState(next, userId)
    }
}
