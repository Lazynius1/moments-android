package com.moments.android.views.feed.maps

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.ModernFollowButton
import com.moments.android.views.components.ModernFollowButtonStyle
import com.moments.android.views.components.MomentsCircularProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.timeAgoDisplay
import com.moments.android.models.Comment
import com.moments.android.models.Moment
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.services.privacy.FollowStateStore
import com.moments.android.services.privacy.PrivacyService
import com.moments.android.views.components.VerifiedBadgeView
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconView
import com.moments.android.views.story.StoryRingAvatarView
import kotlinx.coroutines.launch

/**
 * MARK chunks de `LocationMomentDetailView.swift` (ActionButtons / Expandable / Follow / CommentRow).
 * `LocationMomentCard` vive en [LocationMomentCard.kt].
 */

/** ≡ iOS `LocationActionButtons`. */
@Composable
fun LocationActionButtons(
    moment: Moment,
    commentCount: Int,
    isSaved: Boolean,
    isSaveLoading: Boolean,
    onComment: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .graphicsLayer { scaleX = if (commentCount > 0) 1.05f else 1f; scaleY = scaleX }
                .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = true)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        if (commentCount > 0) {
                            listOf(Color.Blue.copy(alpha = 0.6f), Color(0xFFAF52DE).copy(alpha = 0.6f))
                        } else {
                            colors.buttonStroke
                        },
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onComment,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AttachmentIconView(
                icon = AttachmentIcon.COMMENTS,
                size = 16.dp,
                tintColor = if (commentCount > 0) Color(0xFF007AFF) else colors.primary,
            )
            if (commentCount > 0) {
                Text("$commentCount", color = colors.secondary, fontWeight = FontWeight.Medium, fontSize = 12.sp)
            }
        }

        Box(
            Modifier
                .graphicsLayer { scaleX = if (isSaved) 1.05f else 1f; scaleY = scaleX }
                .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = !isSaveLoading)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        if (isSaved) {
                            listOf(Color.Yellow.copy(alpha = 0.6f), Color(0xFFFF9500).copy(alpha = 0.6f))
                        } else {
                            colors.buttonStroke
                        },
                    ),
                    shape = RoundedCornerShape(12.dp),
                )
                .clickable(
                    enabled = !isSaveLoading,
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onSave,
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (isSaveLoading) {
                CircularProgressIndicator(Modifier.size(14.dp), color = colors.accent, strokeWidth = 2.dp)
            } else {
                AttachmentIconView(
                    icon = AttachmentIcon.BOOKMARK,
                    size = 16.dp,
                    tintColor = if (isSaved) Color(0xFFFFCC00) else colors.primary,
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

/** ≡ iOS `LocationExpandableContentView`. */
@Composable
fun LocationExpandableContentView(
    content: String,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    var isExpanded by remember { mutableStateOf(false) }
    val needsExpansion = content.length > 80
    Column(
        modifier
            .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = false)
            .border(
                width = 0.8.dp,
                brush = Brush.linearGradient(colors.overlayStroke),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            content,
            color = colors.primary,
            fontSize = 13.sp,
            maxLines = if (isExpanded) Int.MAX_VALUE else 2,
        )
        if (needsExpansion) {
            Row(
                Modifier
                    .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { isExpanded = !isExpanded }
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(if (isExpanded) R.string.feed_see_less else R.string.feed_see_more),
                    color = colors.primary,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
                Icon(
                    if (isExpanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = colors.primary,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

/** ≡ iOS `FollowButtonForLocation`. */
@Composable
fun FollowButtonForLocation(
    targetUserId: String,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val firestore = remember { FirestoreService() }
    var followButtonState by remember { mutableStateOf(FollowButtonState.CAN_FOLLOW) }
    var isLoading by remember { mutableStateOf(false) }

    fun checkFollowStatus() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            FollowStateStore.state(targetUserId)?.let { followButtonState = it }
            FollowStateStore.resolve(currentUserId, targetUserId)?.let { followButtonState = it }
        }
    }

    fun performFollowToggle() {
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        isLoading = true
        scope.launch {
            runCatching {
                when (followButtonState) {
                    FollowButtonState.FOLLOWING, FollowButtonState.MUTUALS -> {
                        firestore.unfollowUser(currentUserId, targetUserId)
                        FollowButtonState.CAN_FOLLOW
                    }
                    FollowButtonState.REQUEST_PENDING_CANCELLABLE -> {
                        firestore.cancelFollowRequest(currentUserId, targetUserId)
                        FollowButtonState.CAN_REQUEST_FOLLOW
                    }
                    else -> {
                        firestore.followUser(currentUserId, targetUserId)
                        if (followButtonState == FollowButtonState.CAN_REQUEST_FOLLOW) {
                            FollowButtonState.REQUEST_PENDING_CANCELLABLE
                        } else {
                            FollowButtonState.FOLLOWING
                        }
                    }
                }
            }.onSuccess { newState ->
                followButtonState = newState
                FollowStateStore.setState(newState, targetUserId)
            }
            isLoading = false
        }
    }

    LaunchedEffect(targetUserId) { checkFollowStatus() }

    DisposableEffect(targetUserId) {
        val listener: (String, FollowButtonState) -> Unit = { userId, state ->
            if (userId == targetUserId) followButtonState = state
        }
        FollowStateStore.addListener(listener)
        onDispose { FollowStateStore.removeListener(listener) }
    }

    ModernFollowButton(
        state = followButtonState,
        isLoading = isLoading,
        targetUserId = targetUserId,
        onClick = { performFollowToggle() },
        style = ModernFollowButtonStyle.COMPACT,
        modifier = modifier,
    )
}

/** ≡ iOS `LocationCommentRow`. */
@Composable
fun LocationCommentRow(
    comment: Comment,
    onAvatarTap: ((String, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    Row(
        modifier
            .momentsChromeGlass(RoundedCornerShape(12.dp), interactive = false)
            .border(
                width = 0.5.dp,
                brush = Brush.linearGradient(colors.overlayStroke),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        StoryRingAvatarView(
            userId = comment.authorId,
            size = 36.dp,
            lineWidth = 2.2.dp,
            showBaseStroke = true,
            baseStrokeColor = if (isDark) Color.White.copy(alpha = 0.18f) else Color.Black.copy(alpha = 0.14f),
            baseStrokeWidth = 0.9.dp,
            onTap = { hasStory ->
                if (onAvatarTap != null) {
                    onAvatarTap(comment.authorId, hasStory)
                } else if (!hasStory) {
                    NavigationEventBus.emit(
                        CoordinatorNavigationEvent.NavigateToUserProfileInFeed(comment.authorId),
                    )
                }
            },
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(comment.username, color = colors.primary, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    VerifiedBadgeView(userId = comment.authorId, size = 10.dp)
                }
                Text(
                    comment.timestamp.timeAgoDisplay(),
                    color = if (isDark) Color.White.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                )
                Spacer(Modifier.weight(1f))
            }
            Text(comment.content, color = colors.secondary, fontSize = 13.sp)
        }
    }
}
