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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PersonAddAlt1
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import com.moments.android.views.components.MomentsCircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FeedMoment
import com.moments.android.services.privacy.ContentAudience
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
/** Tamaño visual del contador (cápsula iOS ~padding 6/2 + font 10). */
private val RailCountBadgeVisual = 20.dp

/**
 * Contador visual del rail (reacciones / comentarios).
 * El hit amplio de estadísticas va aparte en [EpicReactionButton] para no desplazar el rail.
 */
@Composable
fun RailCountBadge(
    text: String,
    background: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Box(
        modifier
            .height(RailCountBadgeVisual)
            .widthIn(min = RailCountBadgeVisual)
            .clip(RoundedCornerShape(percent = 50))
            .background(background)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            HapticManager.shared.lightImpact()
                            onClick()
                        },
                    )
                } else {
                    Modifier
                },
            )
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = Color.White,
            maxLines = 1,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 10.sp,
                platformStyle = PlatformTextStyle(includeFontPadding = false),
            ),
        )
    }
}

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
        // Glass en capa hermana (clip solo del chrome). El Row no se clippea:
        // el picker de reacciones / badges pueden dibujar fuera (como iOS overlays).
        Box(Modifier.graphicsLayer { alpha = immersiveAlpha }) {
            Box(
                Modifier
                    .matchParentSize()
                    .shadow(
                        10.dp,
                        RoundedCornerShape(percent = 50),
                        clip = false,
                        ambientColor = Color.Black.copy(alpha = 0.3f),
                        spotColor = Color.Black.copy(alpha = 0.3f),
                    )
                    .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true),
            )
            Row(
                Modifier.padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MomentReactionButton(
                    moment = moment,
                    showCount = showReactionCount,
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
            RailCountBadge(
                text = count.toString(),
                background = if (isActive) color else Color.Gray.copy(alpha = 0.6f),
                modifier = Modifier.offset(x = 4.dp, y = (-4).dp),
            )
        }
    }
}

/** Port de `ModernFollowButton.Style`. */
enum class ModernFollowButtonStyle {
    STANDARD,
    COMPACT,
    PROFILE_HEADER,
}

/** Controla cuándo ModernFollowButton muestra diálogos de confirmación. */
enum class DestructiveConfirmationMode {
    ALL,
    CANCEL_REQUEST_ONLY,
    NONE,
}

/** Port de `ModernFollowButton`. */
@Composable
fun ModernFollowButton(
    state: FollowButtonState,
    isLoading: Boolean,
    onClick: () -> Unit,
    style: ModernFollowButtonStyle = ModernFollowButtonStyle.STANDARD,
    destructiveConfirmation: DestructiveConfirmationMode = DestructiveConfirmationMode.ALL,
    modifier: Modifier = Modifier,
) {
    val colors = rememberAdaptiveColors()
    val context = LocalContext.current
    val density = LocalDensity.current
    val isCompact = style == ModernFollowButtonStyle.COMPACT
    val isProfileHeader = style == ModernFollowButtonStyle.PROFILE_HEADER
    val showsLeadIcon = !isProfileHeader

    var showUnfollowConfirm by remember { mutableStateOf(false) }
    var showCancelRequestConfirm by remember { mutableStateOf(false) }

    val showsMutuals = state == FollowButtonState.MUTUALS
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

    val title = when (state) {
        FollowButtonState.MUTUALS -> stringResource(R.string.audience_type_mutuals)
        FollowButtonState.FOLLOWING -> stringResource(R.string.user_profile_following)
        FollowButtonState.CAN_REQUEST_FOLLOW -> stringResource(R.string.feed_follow_request)
        FollowButtonState.REQUEST_PENDING -> stringResource(R.string.feed_follow_requested)
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> stringResource(R.string.feed_follow_cancel_request)
        FollowButtonState.BLOCKED -> stringResource(R.string.user_profile_blocked)
        FollowButtonState.OWN_PROFILE -> stringResource(R.string.user_profile_follow_button_own_profile)
        else -> stringResource(R.string.feed_follow)
    }
    val icon = when (state) {
        FollowButtonState.MUTUALS -> Icons.Filled.People
        FollowButtonState.FOLLOWING -> Icons.Filled.PersonAddAlt1
        FollowButtonState.CAN_REQUEST_FOLLOW -> Icons.Filled.PersonAdd
        FollowButtonState.REQUEST_PENDING -> Icons.Filled.AccessTime
        FollowButtonState.REQUEST_PENDING_CANCELLABLE -> Icons.Filled.Close
        FollowButtonState.BLOCKED -> Icons.Filled.Block
        FollowButtonState.OWN_PROFILE -> Icons.Filled.Person
        else -> Icons.Filled.PersonAdd
    }
    val isPassive = state == FollowButtonState.REQUEST_PENDING

    val handleTap: () -> Unit = {
        HapticManager.shared.mediumImpact()
        when {
            state.isFollowingOrMutual && destructiveConfirmation == DestructiveConfirmationMode.ALL ->
                showUnfollowConfirm = true
            state == FollowButtonState.REQUEST_PENDING_CANCELLABLE && destructiveConfirmation != DestructiveConfirmationMode.NONE ->
                showCancelRequestConfirm = true
            else -> onClick()
        }
    }

    Row(
        modifier
            .graphicsLayer { alpha = if (isPassive) 0.78f else 1f }
            .momentsChromeGlass(
                shape = RoundedCornerShape(percent = 50),
                interactive = state.isActionable,
            )
            .clickable(enabled = !isLoading && state.isActionable, onClick = handleTap)
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
            if (showsMutuals) {
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
            if (isProfileHeader && state.isFollowingOrMutual) {
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
