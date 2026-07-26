package com.moments.android.notifications.row

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.models.MomentsNotification
import com.moments.android.notifications.core.NotificationGroup
import com.moments.android.notifications.core.NotificationsViewModel
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.utilities.legacyPoppinsSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

/**
 * Port de EnhancedNotificationRow+Follow.swift
 *
 * fetchMomentPreview / loadMomentImage → [EnhancedNotificationRowPreviews]
 * Botón follow UI (Trailing iOS) vive aquí como [followTrailing].
 */
object EnhancedNotificationRowFollow {

    /** ≡ senderDisplayName(for:) */
    fun senderDisplayName(
        notification: MomentsNotification,
        senderUsernameOverride: String? = null,
        someoneFallback: String,
    ): String {
        val override = senderUsernameOverride?.trim().orEmpty()
        if (override.isNotEmpty()) return override
        val username = notification.senderUsername.trim()
        if (username.isEmpty()) return someoneFallback
        return username
    }

    /**
     * ≡ resolveSenderDisplayData — fetch username si vacío / "alguien".
     * Devuelve override o null si no hace falta / falla.
     */
    suspend fun resolveSenderUsername(group: NotificationGroup): String? {
        val first = group.notifications.firstOrNull() ?: return null
        val senderId = first.senderId.trim()
        if (senderId.isEmpty()) return null
        val normalized = first.senderUsername.trim().lowercase()
        val needsResolution = normalized.isEmpty() || normalized == "alguien" || normalized == "someone"
        if (!needsResolution) return null
        val user = withContext(Dispatchers.IO) {
            runCatching { FirestoreService().fetchUsersByIdsClean(listOf(senderId)).firstOrNull() }
                .getOrNull()
        } ?: return null
        val username = user.username.trim()
        return username.takeIf { it.isNotEmpty() }
    }

    @Composable
    fun followTrailing(group: NotificationGroup, viewModel: NotificationsViewModel, isDark: Boolean) {
        val targetUserId = group.notifications.firstOrNull()?.senderId?.trim().orEmpty()
        if (targetUserId.isEmpty()) return

        var followState by remember(targetUserId) { mutableStateOf(FollowButtonState.CAN_FOLLOW) }
        var showingUnfollowConfirmation by remember { mutableStateOf(false) }
        val scope = rememberCoroutineScope()
        val primaryText = if (isDark) Color.White else Color.Black
        val context = LocalContext.current
        val density = LocalDensity.current
        val fontSp = with(density) { legacyPoppinsSize(context, 12).toSp() }

        // ≡ checkFollowingStatus
        LaunchedEffect(targetUserId) {
            FollowStateStore.state(targetUserId)?.let { followState = it }
            val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return@LaunchedEffect
            val authoritative = PrivacyService.getFollowButtonState(currentUserId, targetUserId)
            val reconciled = FollowStateStore.reconciledState(authoritative, targetUserId)
            followState = reconciled
            FollowStateStore.setState(reconciled, targetUserId)
        }

        // ≡ onReceive(FollowStateStore.didChangeNotification)
        DisposableEffect(targetUserId) {
            val listener: (String, FollowButtonState) -> Unit = { userId, state ->
                if (userId == targetUserId) followState = state
            }
            FollowStateStore.addListener(listener)
            onDispose { FollowStateStore.removeListener(listener) }
        }

        val title = notificationFollowTitle(followState)
        val icon = notificationFollowIcon(followState)
        val passive = notificationFollowIsPassive(followState)
        val enabled = followState.isActionable

        Row(
            modifier = Modifier
                .alpha(if (passive) 0.78f else 1f)
                .then(
                    if (enabled) {
                        Modifier.clickable {
                            // ≡ toggleFollow
                            if (followState == FollowButtonState.FOLLOWING) {
                                showingUnfollowConfirmation = true
                            } else {
                                scope.launch { performFollowToggle(targetUserId, followState, viewModel) { followState = it } }
                            }
                        }
                    } else {
                        Modifier
                    },
                )
                .momentsChromeGlass(CircleShape, interactive = followState.isActionable)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = primaryText,
                modifier = Modifier.size(12.dp),
            )
            Text(
                text = title,
                fontSize = fontSp,
                fontWeight = FontWeight.SemiBold,
                color = primaryText,
                maxLines = 1,
            )
        }

        if (showingUnfollowConfirmation) {
            AlertDialog(
                onDismissRequest = { showingUnfollowConfirmation = false },
                title = { Text(stringResource(R.string.user_profile_unfollow_confirm_title)) },
                text = { Text(stringResource(R.string.user_profile_unfollow_confirm_message)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showingUnfollowConfirmation = false
                            scope.launch {
                                performFollowToggle(targetUserId, followState, viewModel) { followState = it }
                            }
                        },
                    ) {
                        Text(
                            stringResource(R.string.user_profile_unfollow_confirm_action),
                            color = Color.Red,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showingUnfollowConfirmation = false }) {
                        Text(stringResource(R.string.common_cancel))
                    }
                },
            )
        }
    }

    /** ≡ notificationFollowTitle */
    @Composable
    fun notificationFollowTitle(state: FollowButtonState): String = when (state) {
        FollowButtonState.FOLLOWING -> stringResource(R.string.user_profile_following)
        FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.feed_follow_request)
        FollowButtonState.REQUEST_PENDING -> stringResource(R.string.feed_follow_requested)
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> stringResource(R.string.feed_follow_cancel_request)
        FollowButtonState.BLOCKED -> stringResource(R.string.user_profile_blocked)
        else -> stringResource(R.string.user_profile_follow)
    }

    /** ≡ notificationFollowIcon (SF Symbol → Material; misma mapa que UserProfileStateViews) */
    fun notificationFollowIcon(state: FollowButtonState): ImageVector = when (state) {
        FollowButtonState.FOLLOWING -> Icons.Filled.CheckCircle
        FollowButtonState.CAN_REQUEST_FOLLOW -> Icons.Filled.PersonAdd
        FollowButtonState.REQUEST_PENDING -> Icons.Filled.Schedule
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> Icons.Filled.Cancel
        FollowButtonState.BLOCKED -> Icons.Filled.PersonOff
        else -> Icons.Filled.PersonAdd
    }

    /** ≡ notificationFollowIsPassive */
    fun notificationFollowIsPassive(state: FollowButtonState): Boolean =
        state == FollowButtonState.REQUEST_PENDING

    /** ≡ performFollowToggle */
    private suspend fun performFollowToggle(
        targetUserId: String,
        currentState: FollowButtonState,
        viewModel: NotificationsViewModel,
        onState: (FollowButtonState) -> Unit,
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        when (currentState) {
            FollowButtonState.FOLLOWING -> {
                val err = awaitCallback { viewModel.unfollowUser(currentUserId, targetUserId, it) }
                if (err == null) {
                    onState(FollowButtonState.CAN_FOLLOW)
                    FollowStateStore.setState(FollowButtonState.CAN_FOLLOW, targetUserId)
                }
            }
            FollowButtonState.REQUEST_PENDING_CANCELLABLE -> {
                val err = awaitCallback { viewModel.cancelFollowRequest(currentUserId, targetUserId, it) }
                if (err == null) {
                    onState(FollowButtonState.CAN_REQUEST_FOLLOW)
                    FollowStateStore.setState(FollowButtonState.CAN_REQUEST_FOLLOW, targetUserId)
                }
            }
            else -> {
                val err = awaitCallback { viewModel.followUser(currentUserId, targetUserId, it) }
                if (err == null) {
                    val newState = if (currentState == FollowButtonState.CAN_REQUEST_FOLLOW) {
                        FollowButtonState.REQUEST_PENDING_CANCELLABLE
                    } else {
                        FollowButtonState.FOLLOWING
                    }
                    onState(newState)
                    FollowStateStore.setState(newState, targetUserId)
                }
            }
        }
    }

    private suspend fun awaitCallback(block: ((Throwable?) -> Unit) -> Unit): Throwable? =
        suspendCoroutine { cont -> block { cont.resume(it) } }
}
