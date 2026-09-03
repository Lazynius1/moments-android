package com.moments.android.views.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.ChromeIconDescription
import com.moments.android.extensions.MomentsGlassButtonPreset
import com.moments.android.extensions.ProfileChromeIconButton
import com.moments.android.extensions.ProfileGlassPillTrack
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.models.Moment
import com.moments.android.services.cache.UserCacheService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Port de `ModernExploreDetailHeader.swift`.
 * Glass capsule con back, autor, ubicación y seguir.
 */
@Composable
fun ModernExploreDetailHeader(
    moment: Moment?,
    topInset: Dp = 0.dp,
    onDismiss: () -> Unit,
    onAvatarTap: (userId: String, hasStory: Boolean) -> Unit,
    onLocationTap: ((location: String, coordinate: Moment.LocationCoordinate?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    var liveUsername by remember(moment?.authorId) { mutableStateOf("") }
    var followButtonState by remember(moment?.authorId) { mutableStateOf(FollowButtonState.CAN_FOLLOW) }
    var isFollowLoading by remember { mutableStateOf(false) }

    LaunchedEffect(moment?.authorId) {
        liveUsername = ""
        FollowStateStore.state(moment?.authorId.orEmpty())?.let { followButtonState = it }
        liveUsername = resolveAuthorUsername(moment)
        followButtonState = refreshFollowState(moment, currentUserId)
    }

    Column(modifier.fillMaxWidth()) {
        Spacer(Modifier.height(topInset))
        ProfileGlassPillTrack(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ProfileChromeIconButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    onClick = onDismiss,
                    foregroundColor = colors.primary,
                    preset = MomentsGlassButtonPreset.NAVIGATION_BACK,
                    standaloneGlass = false,
                    contentDescriptionKey = ChromeIconDescription.BACK,
                )

                val m = moment
                if (m != null) {
                    val authorId = m.authorId.trim()
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f),
                    ) {
                        StoryRingAvatarView(
                            userId = authorId,
                            size = 36.dp,
                            lineWidth = 2.2.dp,
                            showBaseStroke = true,
                            baseStrokeColor = Color.White.copy(alpha = 0.15f),
                            baseStrokeWidth = 0.5.dp,
                            onTap = { hasStory ->
                                if (authorId.isNotEmpty()) onAvatarTap(authorId, hasStory)
                            },
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Row(
                                    Modifier.clickable {
                                        if (authorId.isNotEmpty()) onAvatarTap(authorId, false)
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Text(
                                        displayUsername(liveUsername, m),
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 15.sp,
                                        color = colors.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    VerifiedBadgeView(userId = authorId, size = 13.dp)
                                }
                                Text(
                                    "·",
                                    fontSize = 10.sp,
                                    color = colors.secondary.copy(alpha = 0.7f),
                                )
                                Text(
                                    m.timestamp.timeAgoDisplay(),
                                    fontSize = 10.sp,
                                    color = colors.secondary.copy(alpha = 0.7f),
                                )
                            }
                            val location = m.location?.trim().orEmpty()
                            if (location.isNotEmpty() && onLocationTap != null) {
                                Row(
                                    Modifier.clickable {
                                        onLocationTap(location, m.locationCoordinate)
                                    },
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        Icons.Filled.LocationOn,
                                        contentDescription = null,
                                        tint = colors.secondary.copy(alpha = 0.85f),
                                        modifier = Modifier.size(9.dp),
                                    )
                                    Text(
                                        location,
                                        fontSize = 10.sp,
                                        color = colors.secondary.copy(alpha = 0.85f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }

                    if (m.authorId != currentUserId) {
                        ModernFollowButton(
                            state = followButtonState,
                            isLoading = isFollowLoading,
                            onClick = {
                                scope.launch {
                                    performFollowToggle(
                                        firestore = firestore,
                                        moment = m,
                                        currentUserId = currentUserId,
                                        previousState = followButtonState,
                                        onState = { followButtonState = it },
                                        onLoading = { isFollowLoading = it },
                                    )
                                }
                            },
                        )
                    }
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

private fun displayUsername(liveUsername: String, moment: Moment): String {
    val fresh = liveUsername.trim()
    return if (fresh.isEmpty()) moment.username else fresh
}

private suspend fun resolveAuthorUsername(moment: Moment?): String {
    val authorId = moment?.authorId?.trim().orEmpty()
    if (authorId.isEmpty()) return ""
    val user = suspendCancellableCoroutine { cont ->
        UserCacheService.refreshUser(authorId) { cont.resume(it) }
    }
    return user?.username?.trim().orEmpty()
}

private suspend fun refreshFollowState(
    moment: Moment?,
    currentUserId: String?,
): FollowButtonState {
    if (moment == null || currentUserId == null || moment.authorId == currentUserId) {
        return FollowButtonState.CAN_FOLLOW
    }
    val state = PrivacyService.getFollowButtonState(currentUserId, moment.authorId)
    FollowStateStore.setState(state, moment.authorId)
    return state
}

private suspend fun performFollowToggle(
    firestore: FirestoreService,
    moment: Moment,
    currentUserId: String?,
    previousState: FollowButtonState,
    onState: (FollowButtonState) -> Unit,
    onLoading: (Boolean) -> Unit,
) {
    if (currentUserId == null) return
    if (!previousState.isActionable) return

    val optimistic = when (previousState) {
        FollowButtonState.FOLLOWING, FollowButtonState.MUTUALS -> FollowButtonState.CAN_FOLLOW
        FollowButtonState.CAN_REQUEST_FOLLOW -> FollowButtonState.REQUEST_PENDING_CANCELLABLE
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> FollowButtonState.CAN_REQUEST_FOLLOW
        FollowButtonState.CAN_FOLLOW -> FollowButtonState.FOLLOWING
        else -> previousState
    }
    onState(optimistic)
    FollowStateStore.setState(optimistic, moment.authorId)
    onLoading(true)
    try {
        when (previousState) {
            FollowButtonState.FOLLOWING, FollowButtonState.MUTUALS ->
                firestore.unfollowUser(currentUserId, moment.authorId)
            FollowButtonState.REQUEST_PENDING_CANCELLABLE ->
                firestore.cancelFollowRequest(currentUserId, moment.authorId)
            else ->
                firestore.followUser(currentUserId, moment.authorId)
        }
    } catch (_: Exception) {
        onState(previousState)
        FollowStateStore.setState(previousState, moment.authorId)
    } finally {
        onLoading(false)
    }
}
