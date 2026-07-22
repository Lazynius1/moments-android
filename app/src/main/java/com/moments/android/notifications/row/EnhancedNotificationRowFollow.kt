package com.moments.android.notifications.row

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.moments.android.notifications.components.GlassmorphicActionButton
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.moments.android.R

/** Port de EnhancedNotificationRow+Follow.swift */
object EnhancedNotificationRowFollow {
    @Composable
    fun followTrailing(group: NotificationGroup, viewModel: NotificationsViewModel, isDark: Boolean) {
        val targetUserId = group.notifications.firstOrNull()?.senderId ?: return
        var followState by remember(targetUserId) { mutableStateOf(FollowButtonState.CAN_FOLLOW) }

        LaunchedEffect(targetUserId) {
            FollowStateStore.state(targetUserId)?.let { followState = it }
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
            val authoritative = PrivacyService.getFollowButtonState(currentUserId, targetUserId)
            followState = FollowStateStore.reconciledState(authoritative, targetUserId)
            FollowStateStore.setState(followState, targetUserId)
        }

        val label = when (followState) {
            FollowButtonState.FOLLOWING -> stringResource(R.string.user_profile_following)
            FollowButtonState.REQUEST_PENDING, FollowButtonState.REQUEST_PENDING_CANCELLABLE ->
                stringResource(R.string.user_profile_requested)
            FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.user_profile_request)
            else -> stringResource(R.string.user_profile_follow)
        }
        val color = if (followState == FollowButtonState.FOLLOWING) Color.Gray else Color(0xFF00A896)

        GlassmorphicActionButton(text = label, color = color, isDark = isDark) {
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@GlassmorphicActionButton
            when (followState) {
                FollowButtonState.FOLLOWING -> viewModel.unfollowUser(currentUserId, targetUserId) {
                    if (it == null) followState = FollowButtonState.CAN_FOLLOW
                }
                FollowButtonState.REQUEST_PENDING_CANCELLABLE -> viewModel.cancelFollowRequest(currentUserId, targetUserId) {
                    if (it == null) followState = FollowButtonState.CAN_REQUEST_FOLLOW
                }
                else -> viewModel.followUser(currentUserId, targetUserId) {
                    if (it == null) {
                        followState = if (followState == FollowButtonState.CAN_REQUEST_FOLLOW) {
                            FollowButtonState.REQUEST_PENDING_CANCELLABLE
                        } else FollowButtonState.FOLLOWING
                    }
                }
            }
        }
    }
}
