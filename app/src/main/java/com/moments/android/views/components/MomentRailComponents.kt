package com.moments.android.views.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.privacy.FollowButtonState
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsPressDefaults
import com.moments.android.utilities.MomentsPressSpec
import com.moments.android.utilities.legacyPoppinsSize
import com.moments.android.utilities.momentsPress
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.feed.reactions.MomentReactionButton
import com.moments.android.views.messaging.components.AttachmentIcon
import com.moments.android.views.messaging.components.AttachmentIconPreset
import com.moments.android.views.messaging.components.AttachmentIconView

private val RailActionCircle = 44.dp

/**
 * Port de `ModernActionButtons` (MomentRailComponents.swift) — Glow Rail.
 * AdaptiveColors vive en `AdaptiveColors.kt` (mismo archivo Swift original).
 */
@Composable
fun ModernActionButtons(
    moment: FeedMoment,
    isSaved: Boolean,
    isSaveLoading: Boolean,
    commentCount: Int,
    onComment: () -> Unit,
    onSave: () -> Unit,
    onContextMenu: () -> Unit,
    isImmersive: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val isDark = isSystemInDarkTheme()
    val uid = FirebaseAuth.getInstance().currentUser?.uid
    val showReactionCount = moment.authorId == uid || !moment.hideLikeCounts
    val immersiveAlpha by animateFloatAsState(
        targetValue = if (isImmersive) 0f else 1f,
        label = "modernActionButtonsImmersive",
    )

    Box(
        modifier
            .fillMaxWidth()
            .padding(end = 16.dp, bottom = 16.dp),
        contentAlignment = Alignment.BottomEnd,
    ) {
        Row(
            Modifier
                .graphicsLayer { alpha = immersiveAlpha }
                .shadow(
                    10.dp,
                    RoundedCornerShape(percent = 50),
                    clip = false,
                    ambientColor = Color.Black.copy(alpha = 0.3f),
                    spotColor = Color.Black.copy(alpha = 0.3f),
                )
                .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MomentReactionButton(
                momentId = moment.id,
                authorId = moment.authorId,
                reactionCount = moment.reactionCount,
                hideLikeCounts = !showReactionCount,
            )

            if (!moment.disableComments) {
                val active = commentCount > 0
                RailIconButton(
                    attachmentIcon = AttachmentIcon.COMMENTS,
                    color = if (active) Color(0xFF007AFF) else colors.primary,
                    secondaryColor = if (active) Color(0xFFAF52DE) else colors.secondary,
                    isActive = active,
                    count = commentCount.takeIf { it > 0 },
                    onClick = onComment,
                )
            }

            if (moment.allowSharing) {
                if (isSaveLoading) {
                    Box(Modifier.size(RailActionCircle), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(22.dp),
                            color = if (isDark) Color.White else Color.Black,
                            strokeWidth = 2.dp,
                        )
                    }
                } else {
                    RailIconButton(
                        attachmentIcon = AttachmentIcon.BOOKMARK,
                        color = if (isSaved) Color(0xFFFFCC00) else colors.primary,
                        secondaryColor = if (isSaved) Color(0xFFFF9500) else colors.secondary,
                        isActive = isSaved,
                        onClick = onSave,
                    )
                }
            }

            RailIconButton(
                systemIcon = Icons.Filled.MoreHoriz,
                color = colors.primary,
                secondaryColor = colors.secondary,
                isActive = false,
                onClick = onContextMenu,
            )
        }
    }
}

/** Port del `iconButton` privado de `ModernActionButtons`. */
@Composable
private fun RailIconButton(
    attachmentIcon: AttachmentIcon? = null,
    systemIcon: ImageVector? = null,
    color: Color,
    secondaryColor: Color,
    isActive: Boolean,
    count: Int? = null,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val interaction = remember { MutableInteractionSource() }
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .size(RailActionCircle)
                .scale(if (isActive) 1.05f else 1f)
                .momentsPress(
                    interaction,
                    MomentsPressSpec(
                        scale = 0.9f,
                        pressedOpacity = 0.88f,
                        haptic = MomentsPressDefaults.PressHaptic.LIGHT,
                    ),
                )
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(alpha = 0.05f) else Color.Black.copy(alpha = 0.05f))
                .clickable(interactionSource = interaction, indication = null, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            // iOS: LinearGradient(color → secondaryColor); tint ≈ color del gradiente
            @Suppress("UNUSED_VARIABLE")
            val gradientHint = Brush.linearGradient(listOf(color, secondaryColor))
            when {
                attachmentIcon != null -> AttachmentIconView(
                    icon = attachmentIcon,
                    preset = AttachmentIconPreset.RAIL,
                    tintColor = color,
                )
                systemIcon != null -> Icon(
                    imageVector = systemIcon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        if (count != null && count > 0) {
            Text(
                text = count.toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (isActive) color else Color.Gray.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
}

/** Port de `ModernFollowButton.Style`. */
enum class ModernFollowButtonStyle {
    STANDARD,
    COMPACT,
}

/** Port de `ModernFollowButton`. */
@Composable
fun ModernFollowButton(
    state: FollowButtonState,
    isLoading: Boolean,
    onClick: () -> Unit,
    style: ModernFollowButtonStyle = ModernFollowButtonStyle.STANDARD,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val isCompact = style == ModernFollowButtonStyle.COMPACT
    val fontSize = if (isCompact) 11 else 14
    val title = when (state) {
        FollowButtonState.FOLLOWING -> stringResource(R.string.user_profile_following)
        FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.feed_follow_request)
        FollowButtonState.REQUEST_PENDING -> stringResource(R.string.feed_follow_requested)
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> stringResource(R.string.feed_follow_cancel_request)
        FollowButtonState.BLOCKED -> stringResource(R.string.user_profile_blocked)
        else -> stringResource(R.string.feed_follow)
    }
    val icon = when (state) {
        FollowButtonState.FOLLOWING -> Icons.Filled.PersonAddAlt1
        FollowButtonState.CAN_REQUEST_FOLLOW -> Icons.Filled.PersonAdd
        FollowButtonState.REQUEST_PENDING -> Icons.Filled.AccessTime
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> Icons.Filled.Close
        FollowButtonState.BLOCKED -> Icons.Filled.Block
        else -> Icons.Filled.PersonAdd
    }
    val isPassive = state == FollowButtonState.REQUEST_PENDING
    Row(
        modifier
            .graphicsLayer { alpha = if (isPassive) 0.78f else 1f }
            .momentsChromeGlass(
                shape = RoundedCornerShape(percent = 50),
                interactive = state.isActionable,
            )
            .clickable(enabled = !isLoading && state.isActionable) {
                HapticManager.shared.mediumImpact()
                onClick()
            }
            .padding(
                horizontal = if (isCompact) 10.dp else 16.dp,
                vertical = if (isCompact) 6.dp else 8.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(if (isCompact) 4.dp else 6.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(if (isCompact) 11.dp else 14.dp),
                color = colors.primary,
                strokeWidth = 1.5.dp,
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(if (isCompact) 11.dp else 14.dp),
            )
            Text(
                text = title,
                color = colors.primary,
                fontSize = with(density) { legacyPoppinsSize(context, fontSize).toSp() },
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
            )
        }
    }
}
