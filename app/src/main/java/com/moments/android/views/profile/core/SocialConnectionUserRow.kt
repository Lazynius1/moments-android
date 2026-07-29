package com.moments.android.views.profile.core

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.AppUser
import com.moments.android.models.GroupedVisit
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.ModernFollowButtonStyle
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.launch
import java.util.Date

/** Port de `SocialConnectionCountFormatter`. */
object SocialConnectionCountFormatter {
    fun string(count: Int): String =
        MomentsFormat.count(count, style = MomentsFormat.CountStyle.PROFILE_STAT)
}

/** Port de `SocialConnectionsSortMode`. */
enum class SocialConnectionsSortMode {
    DEFAULT,
    ALPHABETICAL,
    NEWEST,
    OLDEST,
}

/** Port de `SocialConnectionsSorting`. */
object SocialConnectionsSorting {
    fun sortUsers(
        users: List<AppUser>,
        mode: SocialConnectionsSortMode,
        timestamps: Map<String, Date> = emptyMap(),
    ): List<AppUser> = when (mode) {
        SocialConnectionsSortMode.DEFAULT -> users
        SocialConnectionsSortMode.ALPHABETICAL -> users.sortedBy { it.username.lowercase() }
        SocialConnectionsSortMode.NEWEST -> users.sortedByDescending { timestamps[it.id] ?: Date(0) }
        SocialConnectionsSortMode.OLDEST -> users.sortedBy { timestamps[it.id] ?: Date(Long.MAX_VALUE) }
    }

    fun sortVisits(
        visits: List<GroupedVisit>,
        mode: SocialConnectionsSortMode,
    ): List<GroupedVisit> = when (mode) {
        SocialConnectionsSortMode.DEFAULT, SocialConnectionsSortMode.NEWEST ->
            visits.sortedByDescending { it.lastVisit }
        SocialConnectionsSortMode.OLDEST -> visits.sortedBy { it.lastVisit }
        SocialConnectionsSortMode.ALPHABETICAL -> visits.sortedBy { it.user.username.lowercase() }
    }
}

data class SocialConnectionRowConfiguration(
    val showsRemoveFollower: Boolean = false,
    val showsRelationshipButton: Boolean = true,
    val showsOverflowMenu: Boolean = false,
    val showsFollowBackHint: Boolean = false,
    val showsBio: Boolean = true,
    val showsNewPosts: Boolean = false,
)

/** Port de `SocialConnectionRowMetrics`. */
object SocialConnectionRowMetrics {
    val avatarSize: Dp = 56.dp
    val horizontalPadding: Dp = 16.dp
    val verticalPadding: Dp = 6.dp
    val contentSpacing: Dp = 12.dp
    val textLineSpacing: Dp = 1.dp
}

object SocialConnectionAvatarTapRouting {
    fun route(
        userId: String,
        hasStory: Boolean,
        openProfile: (String) -> Unit,
        openStories: (String) -> Unit,
    ) {
        val normalized = userId.trim()
        if (normalized.isEmpty()) return
        if (hasStory) openStories(normalized) else openProfile(normalized)
    }
}

/**
 * Port de `SocialConnectionUserRow` 1:1: avatar con badge mutual (cutout),
 * follow compact, remove / overflow, press highlight y diálogos.
 */
@Composable
fun SocialConnectionUserRow(
    user: AppUser,
    subtitle: String?,
    viewModel: UserListViewModel,
    onUserTap: ((AppUser) -> Unit)?,
    configuration: SocialConnectionRowConfiguration = SocialConnectionRowConfiguration(),
    newContentCount: Int? = null,
    onViewSharedActivity: ((AppUser) -> Unit)? = null,
    onRemoveFollower: ((AppUser) -> Unit)? = null,
    onAvatarTap: ((String, Boolean) -> Unit)? = null,
    isMutual: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var followState by remember(user.id) { mutableStateOf(viewModel.relationshipState(user.id)) }
    var isFollowLoading by remember { mutableStateOf(false) }
    var showUnfollowConfirmation by remember { mutableStateOf(false) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var isPressed by remember { mutableStateOf(false) }
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(alpha = 0.55f) else Color.Black.copy(alpha = 0.55f)
    val pressAlpha by animateFloatAsState(
        if (isPressed) 0.06f else 0f,
        animationSpec = tween(100),
        label = "socialRowPress",
    )

    DisposableEffect(user.id) {
        val listener: (String, FollowButtonState) -> Unit = { id, state ->
            if (id == user.id) followState = state
        }
        FollowStateStore.addListener(listener)
        viewModel.prefetchRelationshipState(user.id)
        followState = viewModel.relationshipState(user.id)
        onDispose { FollowStateStore.removeListener(listener) }
    }

    val followBackHint = configuration.showsFollowBackHint &&
        followState in setOf(FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW)
    val resolvedBio = if (configuration.showsBio) {
        subtitle?.takeIf(String::isNotBlank) ?: user.bio?.takeIf(String::isNotBlank)
    } else {
        null
    }
    val secondaryLine: Pair<String, Boolean>? = when {
        followBackHint -> stringResource(R.string.social_connection_follow_also) to true
        resolvedBio != null -> resolvedBio to false
        else -> null
    }
    val newPostsCount = newContentCount?.takeIf { configuration.showsNewPosts && it > 0 }
    val supportsRelationship = configuration.showsRelationshipButton &&
        followState != FollowButtonState.OWN_PROFILE

    fun handleAvatarTap(hasStory: Boolean) {
        if (onAvatarTap != null) {
            onAvatarTap(user.id, hasStory)
            return
        }
        if (hasStory) return
        onUserTap?.invoke(user)
    }

    fun performFollow() {
        isFollowLoading = true
        viewModel.followUser(user.id)
        val next = if (followState == FollowButtonState.CAN_REQUEST_FOLLOW) {
            FollowButtonState.REQUEST_PENDING_CANCELLABLE
        } else {
            FollowButtonState.FOLLOWING
        }
        scope.launch { FollowStateStore.setState(next, user.id) }
        followState = next
        isFollowLoading = false
    }

    fun performRelationshipAction() {
        if (isFollowLoading) return
        when (followState) {
            FollowButtonState.FOLLOWING -> showUnfollowConfirmation = true
            FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW -> performFollow()
            FollowButtonState.REQUEST_PENDING_CANCELLABLE -> {
                viewModel.cancelFollowRequest(user.id)
                scope.launch { FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, user.id) }
                followState = FollowButtonState.CAN_REQUEST_FOLLOW
            }
            else -> Unit
        }
    }

    Row(
        modifier
            .fillMaxWidth()
            .padding(
                horizontal = SocialConnectionRowMetrics.horizontalPadding,
                vertical = SocialConnectionRowMetrics.verticalPadding,
            ),
        horizontalArrangement = Arrangement.spacedBy(SocialConnectionRowMetrics.contentSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MutualAwareAvatar(
            userId = user.id,
            isMutual = isMutual,
            primary = primary,
            dark = dark,
            onTap = ::handleAvatarTap,
        )

        Column(
            Modifier
                .weight(1f)
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
                        onTap = { onUserTap?.invoke(user) },
                    )
                },
            verticalArrangement = Arrangement.spacedBy(SocialConnectionRowMetrics.textLineSpacing),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    user.username,
                    color = primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (user.isVerified) VerifiedBadge(size = 13.dp)
            }
            secondaryLine?.let { (text, accent) ->
                Text(
                    text,
                    color = if (accent) Color(0xFF0095F6) else secondary,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            newPostsCount?.let { count ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            if (count == 1) {
                                R.string.social_connection_new_post_single
                            } else {
                                R.string.social_connection_new_post_multiple
                            },
                            count,
                        ),
                        color = secondary,
                        fontSize = 14.sp,
                        maxLines = 1,
                    )
                    Box(
                        Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0095F6)),
                    )
                }
            }
        }

        Spacer(Modifier.size(4.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (supportsRelationship) {
                ModernFollowButton(
                    state = followState,
                    isLoading = isFollowLoading,
                    onClick = ::performRelationshipAction,
                    style = ModernFollowButtonStyle.COMPACT,
                )
            }
            when {
                configuration.showsRemoveFollower -> {
                    Text(
                        stringResource(R.string.social_connection_remove_follower),
                        color = secondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .border(
                                1.dp,
                                (if (dark) Color.White else Color.Black).copy(alpha = 0.12f),
                                RoundedCornerShape(50),
                            )
                            .clickable { showRemoveConfirmation = true }
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                    )
                }
                configuration.showsOverflowMenu -> {
                    Box {
                        Icon(
                            Icons.Filled.MoreHoriz,
                            contentDescription = stringResource(R.string.social_connection_more_actions),
                            tint = primary,
                            modifier = Modifier
                                .size(width = 24.dp, height = 32.dp)
                                .clickable { menuExpanded = true }
                                .padding(horizontal = 4.dp),
                        )
                        DropdownMenu(menuExpanded, { menuExpanded = false }) {
                            if (onViewSharedActivity != null) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.social_connection_shared_activity)) },
                                    onClick = {
                                        menuExpanded = false
                                        onViewSharedActivity(user)
                                    },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showUnfollowConfirmation) {
        AlertDialog(
            onDismissRequest = { showUnfollowConfirmation = false },
            title = { Text(stringResource(R.string.social_connection_unfollow_title)) },
            text = { Text(stringResource(R.string.social_connection_unfollow_message)) },
            dismissButton = {
                TextButton({ showUnfollowConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                TextButton({
                    showUnfollowConfirmation = false
                    viewModel.unfollowUser(user.id)
                    viewModel.prefetchRelationshipState(user.id)
                }) {
                    Text(stringResource(R.string.social_connection_unfollow_action), color = Color.Red)
                }
            },
        )
    }
    if (showRemoveConfirmation) {
        AlertDialog(
            onDismissRequest = { showRemoveConfirmation = false },
            title = { Text(stringResource(R.string.social_connection_remove_follower_title)) },
            text = {
                Text(stringResource(R.string.social_connection_remove_follower_message, user.username))
            },
            dismissButton = {
                TextButton({ showRemoveConfirmation = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
            confirmButton = {
                TextButton({
                    showRemoveConfirmation = false
                    onRemoveFollower?.invoke(user)
                }) {
                    Text(stringResource(R.string.social_connection_remove_follower), color = Color.Red)
                }
            },
        )
    }
}

/** Avatar + badge mutual con cutout (`reversedMask` iOS). */
@Composable
private fun MutualAwareAvatar(
    userId: String,
    isMutual: Boolean,
    primary: Color,
    dark: Boolean,
    onTap: (Boolean) -> Unit,
) {
    val density = LocalDensity.current
    val avatar = @Composable {
        StoryRingAvatarView(
            userId = userId,
            size = SocialConnectionRowMetrics.avatarSize,
            lineWidth = 2.2.dp,
            showBaseStroke = true,
            baseStrokeColor = if (dark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.14f),
            baseStrokeWidth = 0.9.dp,
            onTap = onTap,
        )
    }

    if (!isMutual) {
        avatar()
        return
    }

    Box {
        Box(
            Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val cutR = with(density) { 10.5.dp.toPx() }
                    drawCircle(
                        color = Color.Black,
                        radius = cutR,
                        center = Offset(with(density) { (-1.5).dp.toPx() } + cutR, with(density) { (-1.5).dp.toPx() } + cutR),
                        blendMode = BlendMode.Clear,
                    )
                },
        ) {
            avatar()
        }
        Box(
            Modifier
                .offset(x = 0.dp, y = 0.dp)
                .size(18.dp)
                .clip(CircleShape)
                .momentsChromeGlass(CircleShape, interactive = false),
            contentAlignment = Alignment.Center,
        ) {
            AttachmentIconView(
                icon = AttachmentIcon.MUTUALS,
                size = 10.dp,
                tintColor = primary,
            )
        }
    }
}
