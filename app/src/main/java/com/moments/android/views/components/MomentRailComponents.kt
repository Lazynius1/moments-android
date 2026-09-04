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
