package com.moments.android.views.profile.core

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.People
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.models.AppUser
import com.moments.android.models.GroupedVisit
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.components.VerifiedBadge
import com.moments.android.views.feed.core.sections.ModernFollowButton
import com.moments.android.views.story.StoryRingAvatarView
import java.util.Date

/** Port de `SocialConnectionUserRow.swift`. */
object SocialConnectionCountFormatter { fun string(count: Int): String = MomentsFormat.count(count, style = MomentsFormat.CountStyle.PROFILE_STAT) }
enum class SocialConnectionsSortMode { DEFAULT, ALPHABETICAL, NEWEST, OLDEST }
object SocialConnectionsSorting {
    fun sortUsers(users: List<AppUser>, mode: SocialConnectionsSortMode, timestamps: Map<String, Date> = emptyMap()): List<AppUser> = when (mode) { SocialConnectionsSortMode.DEFAULT -> users; SocialConnectionsSortMode.ALPHABETICAL -> users.sortedBy { it.username.lowercase() }; SocialConnectionsSortMode.NEWEST -> users.sortedByDescending { timestamps[it.id] ?: Date(0) }; SocialConnectionsSortMode.OLDEST -> users.sortedBy { timestamps[it.id] ?: Date(Long.MAX_VALUE) } }
    fun sortVisits(visits: List<GroupedVisit>, mode: SocialConnectionsSortMode): List<GroupedVisit> = when (mode) { SocialConnectionsSortMode.DEFAULT, SocialConnectionsSortMode.NEWEST -> visits.sortedByDescending { it.lastVisit }; SocialConnectionsSortMode.OLDEST -> visits.sortedBy { it.lastVisit }; SocialConnectionsSortMode.ALPHABETICAL -> visits.sortedBy { it.user.username.lowercase() } }
}
data class SocialConnectionRowConfiguration(val showsRemoveFollower: Boolean = false, val showsRelationshipButton: Boolean = true, val showsOverflowMenu: Boolean = false, val showsFollowBackHint: Boolean = false, val showsBio: Boolean = true, val showsNewPosts: Boolean = false)
object SocialConnectionAvatarTapRouting { fun route(userId: String, hasStory: Boolean, openProfile: (String) -> Unit, openStories: (String) -> Unit) { userId.trim().takeIf(String::isNotEmpty)?.let { if (hasStory) openStories(it) else openProfile(it) } } }

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun SocialConnectionUserRow(
    user: AppUser,
    subtitle: String?,
    viewModel: ProfileViewModel,
    onUserTap: ((AppUser) -> Unit)?,
    configuration: SocialConnectionRowConfiguration = SocialConnectionRowConfiguration(),
    newContentCount: Int? = null,
    onViewSharedActivity: ((AppUser) -> Unit)? = null,
    onRemoveFollower: ((AppUser) -> Unit)? = null,
    onAvatarTap: ((String, Boolean) -> Unit)? = null,
    isMutual: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var followState by remember(user.id) { mutableStateOf(viewModel.relationshipState(user.id)) }
    var showUnfollowConfirmation by remember { mutableStateOf(false) }
    var showRemoveConfirmation by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val primary = sharedPrimary(); val secondary = sharedSecondary()
    DisposableEffect(user.id) { val listener: (String, FollowButtonState) -> Unit = { id, state -> if (id == user.id) followState = state }; FollowStateStore.addListener(listener); viewModel.prefetchRelationshipState(user.id); onDispose { FollowStateStore.removeListener(listener) } }
    val followBack = configuration.showsFollowBackHint && followState in setOf(FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW)
    val bio = if (configuration.showsBio) subtitle?.takeIf(String::isNotBlank) ?: user.bio?.takeIf(String::isNotBlank) else null
    val newPosts = newContentCount?.takeIf { configuration.showsNewPosts && it > 0 }

    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(62.dp), contentAlignment = Alignment.TopStart) {
            StoryRingAvatarView(user.id, 56.dp, lineWidth = 2.2.dp, showBaseStroke = true, baseStrokeColor = primary.copy(alpha = .14f), baseStrokeWidth = .9.dp, onTap = { hasStory -> onAvatarTap?.invoke(user.id, hasStory) ?: SocialConnectionAvatarTapRouting.route(user.id, hasStory, { onUserTap?.invoke(user) }, {}) })
            if (isMutual) Icon(Icons.Filled.People, null, tint = primary, modifier = Modifier.size(18.dp).clip(CircleShape).background(primary.copy(alpha = .08f)).padding(4.dp))
        }
        Column(Modifier.weight(1f).combinedClickable(onClick = { onUserTap?.invoke(user) }, onLongClick = {}), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) { Text(user.username, color = primary, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis); if (user.isVerified) VerifiedBadge(size = 13.dp) }
            when { followBack -> Text(stringResource(R.string.social_connection_follow_also), color = Color(0xFF0095F6), fontSize = 14.sp, maxLines = 1); bio != null -> Text(bio, color = secondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            newPosts?.let { Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) { Text(stringResource(if (it == 1) R.string.social_connection_new_post_single else R.string.social_connection_new_post_multiple, it), color = secondary, fontSize = 14.sp); Icon(Icons.Filled.Circle, null, tint = Color(0xFF0095F6), modifier = Modifier.size(7.dp)) } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (configuration.showsRelationshipButton && followState != FollowButtonState.OWN_PROFILE) ModernFollowButton(followState, false, { when (followState) { FollowButtonState.FOLLOWING -> showUnfollowConfirmation = true; FollowButtonState.CAN_FOLLOW, FollowButtonState.CAN_REQUEST_FOLLOW -> { followState = if (followState == FollowButtonState.CAN_REQUEST_FOLLOW) FollowButtonState.REQUEST_PENDING_CANCELLABLE else FollowButtonState.FOLLOWING; viewModel.followUser(user.id) }; FollowButtonState.REQUEST_PENDING_CANCELLABLE -> { followState = FollowButtonState.CAN_REQUEST_FOLLOW; viewModel.cancelFollowRequest(user.id) }; else -> Unit } })
            if (configuration.showsRemoveFollower) Text(stringResource(R.string.social_connection_remove_follower), color = secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clip(RoundedCornerShape(50)).clickable { showRemoveConfirmation = true }.padding(horizontal = 10.dp, vertical = 7.dp))
            else if (configuration.showsOverflowMenu) Box { Icon(Icons.Filled.MoreVert, stringResource(R.string.social_connection_more_actions), tint = primary, modifier = Modifier.size(28.dp).clickable { menuExpanded = true }.padding(4.dp)); DropdownMenu(menuExpanded, { menuExpanded = false }) { if (onViewSharedActivity != null) DropdownMenuItem(text = { Text(stringResource(R.string.social_connection_shared_activity)) }, onClick = { menuExpanded = false; onViewSharedActivity(user) }) } }
        }
    }
    if (showUnfollowConfirmation) AlertDialog({ showUnfollowConfirmation = false }, title = { Text(stringResource(R.string.social_connection_unfollow_title)) }, text = { Text(stringResource(R.string.social_connection_unfollow_message)) }, dismissButton = { TextButton({ showUnfollowConfirmation = false }) { Text(stringResource(R.string.common_cancel)) } }, confirmButton = { TextButton({ showUnfollowConfirmation = false; followState = FollowButtonState.CAN_FOLLOW; viewModel.unfollowUser(user.id); viewModel.prefetchRelationshipState(user.id) }) { Text(stringResource(R.string.social_connection_unfollow_action), color = Color.Red) } })
    if (showRemoveConfirmation) AlertDialog({ showRemoveConfirmation = false }, title = { Text(stringResource(R.string.social_connection_remove_follower_title)) }, text = { Text(stringResource(R.string.social_connection_remove_follower_message, user.username)) }, dismissButton = { TextButton({ showRemoveConfirmation = false }) { Text(stringResource(R.string.common_cancel)) } }, confirmButton = { TextButton({ showRemoveConfirmation = false; onRemoveFollower?.invoke(user) }) { Text(stringResource(R.string.social_connection_remove_follower), color = Color.Red) } })
}
