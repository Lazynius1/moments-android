package com.moments.android.notifications.row

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.ModernFollowButtonStyle
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
        val scope = rememberCoroutineScope()

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

        ModernFollowButton(
            state = followState,
            isLoading = false,
            onClick = {
                scope.launch { performFollowToggle(targetUserId, followState, viewModel) { followState = it } }
            },
            style = ModernFollowButtonStyle.COMPACT,
        )
    }

    /** ≡ performFollowToggle */
    private suspend fun performFollowToggle(
        targetUserId: String,
        currentState: FollowButtonState,
        viewModel: NotificationsViewModel,
        onState: (FollowButtonState) -> Unit,
    ) {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        when (currentState) {
            FollowButtonState.FOLLOWING, FollowButtonState.MUTUALS -> {
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
