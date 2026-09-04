package com.moments.android.views.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

enum class ModernFollowButtonStyle {
    STANDARD,
    COMPACT,
    PROFILE_HEADER,
}

enum class DestructiveConfirmationMode {
    ALL,
    CANCEL_REQUEST_ONLY,
    NONE,
}

/** Botón canónico de relación, conectado al estado compartido viewer-target. */
@Composable
fun ModernFollowButton(
    state: FollowButtonState,
    isLoading: Boolean,
    onClick: () -> Unit,
    targetUserId: String? = null,
    style: ModernFollowButtonStyle = ModernFollowButtonStyle.STANDARD,
    destructiveConfirmation: DestructiveConfirmationMode = DestructiveConfirmationMode.ALL,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val viewerId = FirebaseAuth.getInstance().currentUser?.uid
    val relationshipFlow = remember(viewerId, targetUserId) {
        if (viewerId != null && targetUserId != null) {
            FollowStateStore.observe(viewerId, targetUserId)
        } else {
            flowOf(null)
        }
    }
    val cachedState = remember(viewerId, targetUserId) {
        if (viewerId != null && targetUserId != null) {
            FollowStateStore.state(viewerId, targetUserId)
        } else {
            null
        }
    }
    val observedState by relationshipFlow.collectAsState(initial = cachedState)
    val displayedState = when {
        targetUserId == null -> state
        observedState != null -> observedState
        state != FollowButtonState.CAN_FOLLOW -> state
        else -> null
    }
    val renderState = displayedState ?: state
    val hasResolvedRelationship = targetUserId == null || displayedState != null

    LaunchedEffect(viewerId, targetUserId) {
        if (viewerId != null && targetUserId != null) {
            repeat(3) { attempt ->
                if (FollowStateStore.resolve(viewerId, targetUserId) != null) return@LaunchedEffect
                if (attempt < 2) delay((attempt + 1) * 1_000L)
            }
        }
    }

    val isCompact = style == ModernFollowButtonStyle.COMPACT
    val isProfileHeader = style == ModernFollowButtonStyle.PROFILE_HEADER
    val showsLeadIcon = !isProfileHeader
    var showUnfollowConfirm by remember { mutableStateOf(false) }
    var showCancelRequestConfirm by remember { mutableStateOf(false) }

    val fontSize = when (style) {
        ModernFollowButtonStyle.STANDARD -> 14
        ModernFollowButtonStyle.COMPACT -> 11
        ModernFollowButtonStyle.PROFILE_HEADER -> 13
    }
    val hPadding = when (style) {
        ModernFollowButtonStyle.STANDARD -> 16.dp
        ModernFollowButtonStyle.COMPACT -> 10.dp
        ModernFollowButtonStyle.PROFILE_HEADER -> 18.dp
    }
    val vPadding = when (style) {
        ModernFollowButtonStyle.STANDARD -> 8.dp
        ModernFollowButtonStyle.COMPACT -> 6.dp
        ModernFollowButtonStyle.PROFILE_HEADER -> 10.dp
    }
    val spacing = when (style) {
        ModernFollowButtonStyle.STANDARD -> 6.dp
        ModernFollowButtonStyle.COMPACT -> 4.dp
        ModernFollowButtonStyle.PROFILE_HEADER -> 7.dp
    }

    val title = when (renderState) {
        FollowButtonState.MUTUALS -> stringResource(R.string.audience_type_mutuals)
        FollowButtonState.FOLLOWING -> stringResource(R.string.user_profile_following)
        FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.feed_follow_request)
        FollowButtonState.REQUEST_PENDING -> stringResource(R.string.feed_follow_requested)
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> stringResource(R.string.feed_follow_cancel_request)
        FollowButtonState.BLOCKED -> stringResource(R.string.user_profile_blocked)
        FollowButtonState.OWN_PROFILE -> stringResource(R.string.user_profile_follow_button_own_profile)
        else -> stringResource(R.string.feed_follow)
    }
    val icon = when (renderState) {
        FollowButtonState.MUTUALS -> Icons.Filled.People
        FollowButtonState.FOLLOWING -> Icons.Filled.PersonAddAlt1
        FollowButtonState.CAN_REQUEST_FOLLOW -> Icons.Filled.PersonAdd
        FollowButtonState.REQUEST_PENDING -> Icons.Filled.AccessTime
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> Icons.Filled.Close
        FollowButtonState.BLOCKED -> Icons.Filled.Block
        FollowButtonState.OWN_PROFILE -> Icons.Filled.Person
        else -> Icons.Filled.PersonAdd
    }

    val handleTap: () -> Unit = {
        HapticManager.shared.mediumImpact()
        when {
            !hasResolvedRelationship -> Unit
            renderState.isFollowingOrMutual && destructiveConfirmation == DestructiveConfirmationMode.ALL ->
                showUnfollowConfirm = true
            renderState == FollowButtonState.REQUEST_PENDING_CANCELLABLE &&
                destructiveConfirmation != DestructiveConfirmationMode.NONE ->
                showCancelRequestConfirm = true
            else -> onClick()
        }
    }

    Row(
        modifier
            .alpha(
                when {
                    !hasResolvedRelationship -> 0f
                    renderState == FollowButtonState.REQUEST_PENDING -> 0.78f
                    else -> 1f
                },
            )
            .momentsChromeGlass(
                shape = RoundedCornerShape(percent = 50),
                interactive = renderState.isActionable,
            )
            .clickable(
                enabled = !isLoading && renderState.isActionable && hasResolvedRelationship,
                onClick = handleTap,
            )
            .padding(horizontal = hPadding, vertical = vPadding),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(spacing),
    ) {
        if (isLoading) {
            MomentsCircularProgressIndicator(
                modifier = Modifier.size(if (isCompact) 11.dp else 14.dp),
                strokeWidth = 1.5.dp,
            )
        } else {
            if (renderState == FollowButtonState.MUTUALS) {
                AudienceIconView(
                    audience = ContentAudience.MUTUALS,
                    size = if (isCompact) 11.dp else 13.dp,
                    tintColor = colors.primary,
                )
            } else if (showsLeadIcon) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(if (isCompact) 11.dp else 14.dp),
                )
            }
            Text(
                text = title,
                color = colors.primary,
                fontSize = with(density) { legacyPoppinsSize(context, fontSize).toSp() },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
            if (isProfileHeader && renderState.isFollowingOrMutual) {
                Icon(
                    Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(10.dp),
                )
            }
        }
    }

    if (showUnfollowConfirm) {
        AlertDialog(
            onDismissRequest = { showUnfollowConfirm = false },
            title = { Text(stringResource(R.string.user_profile_unfollow_confirm_title)) },
            text = { Text(stringResource(R.string.user_profile_unfollow_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showUnfollowConfirm = false
                    onClick()
                }) {
                    Text(stringResource(R.string.user_profile_unfollow_confirm_action), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showUnfollowConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showCancelRequestConfirm) {
        AlertDialog(
            onDismissRequest = { showCancelRequestConfirm = false },
            title = { Text(stringResource(R.string.user_profile_cancel_request_confirm_title)) },
            text = { Text(stringResource(R.string.user_profile_cancel_request_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    showCancelRequestConfirm = false
                    onClick()
                }) {
                    Text(stringResource(R.string.user_profile_cancel_request_confirm_action), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelRequestConfirm = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }
}
