package com.moments.android.notifications.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.NotificationType
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.ModernFollowButtonStyle
import com.moments.android.views.components.MomentRowButton
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.story.StoryRingAvatarView
import com.moments.android.views.story.StoryRingLayout
import kotlinx.coroutines.launch
import kotlin.coroutines.resume

/** ≡ GroupedFollowerItem */
data class GroupedFollowerItem(
    val id: String,
    val username: String,
)

private object GroupedFollowersLayout {
    /** Foto 44 + aro fuera; no usar 52 fijo o Compose recorta el stroke. */
    val avatarSize = 44.dp
    val avatarLineWidth = 2.2.dp
    val rowHeight = StoryRingLayout.outerFrameSize(avatarSize, avatarLineWidth)
    val rowSpacing = 6.dp
    const val maxVisibleRows = 10
}

/**
 * Port de NotificationGroupedFollowersOverlay.swift.
 *
 * @param onOpenStory ≡ StoriesView(startWithUserId:) — si null y hay story, no inventamos ruta
 * (mismo criterio que FeedMomentComponents).
 */
@Composable
fun NotificationGroupedFollowersOverlay(
    group: NotificationGroup,
    viewModel: NotificationsViewModel,
    isDark: Boolean,
    onDismiss: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenStory: ((String) -> Unit)? = null,
) {
    val unknownUser = stringResource(R.string.notifications_grouped_followers_unknown_user)
    val items = remember(group) {
        val seen = mutableSetOf<String>()
        group.notifications.mapNotNull { notification ->
            val id = notification.senderId.trim()
            if (id.isEmpty() || !seen.add(id)) return@mapNotNull null
            val username = notification.senderUsername.trim()
            GroupedFollowerItem(id, username.ifEmpty { unknownUser })
        }
    }

    val overlayTitle = when (group.notifications.firstOrNull()?.type) {
        NotificationType.MUTUAL_CONNECTION ->
            stringResource(R.string.notifications_grouped_followers_title_mutual)
        else ->
            stringResource(R.string.notifications_grouped_followers_title_followers)
    }

    val primaryText = if (isDark) Color.White else Color.Black
    val scope = rememberCoroutineScope()
    val followStates = remember { mutableStateMapOf<String, FollowButtonState>() }
    val loadingStates = remember { mutableStateMapOf<String, Boolean>() }

    val visibleRows = minOf(items.size, GroupedFollowersLayout.maxVisibleRows)
    val listAreaHeight = if (visibleRows <= 0) {
        0.dp
    } else {
        GroupedFollowersLayout.rowHeight * visibleRows +
            GroupedFollowersLayout.rowSpacing * (visibleRows - 1)
    }
    val listNeedsScroll = items.size > GroupedFollowersLayout.maxVisibleRows

    LaunchedEffect(items) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
        items.forEach { item ->
            FollowStateStore.state(item.id)?.let { followStates[item.id] = it }
            val network = PrivacyService.getFollowButtonState(currentUserId, item.id)
            val reconciled = FollowStateStore.reconciledState(network, item.id)
            followStates[item.id] = reconciled
            FollowStateStore.setState(reconciled, item.id)
        }
    }

    DisposableEffect(Unit) {
        val listener: (String, FollowButtonState) -> Unit = { userId, state ->
            followStates[userId] = state
        }
        FollowStateStore.addListener(listener)
        onDispose { FollowStateStore.removeListener(listener) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            val cardShape = RoundedCornerShape(28.dp)
            Column(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 320.dp)
                    .fillMaxWidth()
                    .shadow(
                        24.dp,
                        cardShape,
                        ambientColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
                        spotColor = Color.Black.copy(alpha = if (isDark) 0.24f else 0.12f),
                    )
                    .momentsChromeGlass(cardShape, interactive = false)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = {},
                    ),
            ) {
                Text(
                    text = overlayTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = primaryText,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp)
                        .padding(top = 20.dp, bottom = 12.dp),
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(listAreaHeight),
                    verticalArrangement = Arrangement.spacedBy(GroupedFollowersLayout.rowSpacing),
                    userScrollEnabled = listNeedsScroll,
                ) {
                    items(items, key = { it.id }) { item ->
                        FollowerRow(
                            item = item,
                            state = followStates[item.id] ?: FollowButtonState.CAN_FOLLOW,
                            isLoading = loadingStates[item.id] == true,
                            isDark = isDark,
                            primaryText = primaryText,
                            onOpenProfile = {
                                val trimmed = item.id.trim()
                                if (trimmed.isNotEmpty()) onOpenProfile(trimmed)
                            },
                            onAvatarTap = { hasStory ->
                                if (hasStory) {
                                    onOpenStory?.invoke(item.id)
                                } else {
                                    val trimmed = item.id.trim()
                                    if (trimmed.isNotEmpty()) onOpenProfile(trimmed)
                                }
                            },
                            onFollowClick = {
                                val state = followStates[item.id] ?: FollowButtonState.CAN_FOLLOW
                                scope.launch {
                                    performFollowToggle(
                                        userId = item.id,
                                        viewModel = viewModel,
                                        followStates = followStates,
                                        loadingStates = loadingStates,
                                    )
                                }
                            },
                        )
                    }
                }

                MomentRowButton(action = onDismiss) {
                    Text(
                        text = stringResource(R.string.common_close),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        color = primaryText,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .padding(vertical = 16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FollowerRow(
    item: GroupedFollowerItem,
    state: FollowButtonState,
    isLoading: Boolean,
    isDark: Boolean,
    primaryText: Color,
    onOpenProfile: () -> Unit,
    onAvatarTap: (Boolean) -> Unit,
    onFollowClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(GroupedFollowersLayout.rowHeight)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StoryRingAvatarView(
            userId = item.id,
            size = GroupedFollowersLayout.avatarSize,
            lineWidth = GroupedFollowersLayout.avatarLineWidth,
            showBaseStroke = true,
            baseStrokeColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.14f),
            baseStrokeWidth = 0.9.dp,
            onTap = onAvatarTap,
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onOpenProfile),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.username,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            VerifiedBadgeView(userId = item.id, size = 12.dp)
        }

        CompactFollowButton(
            state = state,
            isLoading = isLoading,
            onClick = onFollowClick,
        )
    }
}

@Composable
private fun CompactFollowButton(
    state: FollowButtonState,
    isLoading: Boolean,
    onClick: () -> Unit,
) {
    ModernFollowButton(
        state = state,
        isLoading = isLoading,
        onClick = onClick,
        style = ModernFollowButtonStyle.COMPACT,
    )
}

private suspend fun performFollowToggle(
    userId: String,
    viewModel: NotificationsViewModel,
    followStates: MutableMap<String, FollowButtonState>,
    loadingStates: MutableMap<String, Boolean>,
) {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val currentState = followStates[userId] ?: FollowButtonState.CAN_FOLLOW
    loadingStates[userId] = true

    when (currentState) {
        FollowButtonState.FOLLOWING, FollowButtonState.MUTUALS -> {
            val err = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                viewModel.unfollowUser(currentUserId, userId) { e ->
                    cont.resume(e) {}
                }
            }
            loadingStates[userId] = false
            if (err == null) {
                followStates[userId] = FollowButtonState.CAN_FOLLOW
                FollowStateStore.setState(FollowButtonState.CAN_FOLLOW, userId)
            }
        }
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> {
            val err = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                viewModel.cancelFollowRequest(currentUserId, userId) { e ->
                    cont.resume(e) {}
                }
            }
            loadingStates[userId] = false
            if (err == null) {
                followStates[userId] = FollowButtonState.CAN_REQUEST_FOLLOW
                FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, userId)
            }
        }
        else -> {
            val err = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
                viewModel.followUser(currentUserId, userId) { e ->
                    cont.resume(e) {}
                }
            }
            loadingStates[userId] = false
            if (err == null) {
                val newState = if (currentState == FollowButtonState.CAN_REQUEST_FOLLOW) {
                    FollowButtonState.REQUEST_PENDING_CANCELLABLE
                } else {
                    FollowButtonState.FOLLOWING
                }
                followStates[userId] = newState
                FollowStateStore.setState(newState, userId)
            }
        }
    }
}
