package com.moments.android.views.feed.reactions

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.feed.FeedInk

private val FeedInkDark = Color(0xFF0B1215)
private val ActionCircle = 44.dp

/**
 * Port de `ModernActionButtons` (MomentRailComponents.swift).
 * Rail glass: reacción + comentarios + bookmark + ellipsis (sin paperplane).
 */
@Composable
fun PostActionButtons(
    moment: FeedMoment,
    onOpenComments: () -> Unit,
    onShare: () -> Unit = {},
    onContextMenu: () -> Unit = {},
    isSaved: Boolean = false,
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedShare = onShare
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.White else Color.Black

    Row(
        modifier
            .padding(end = 16.dp, bottom = 16.dp)
            .shadow(10.dp, RoundedCornerShape(percent = 50), clip = false, ambientColor = Color.Black.copy(0.3f), spotColor = Color.Black.copy(0.3f))
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = true)
            .padding(6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MomentReactionButton(
            momentId = moment.id,
            authorId = moment.authorId,
            reactionCount = moment.reactionCount,
            hideLikeCounts = moment.hideLikeCounts,
        )
        if (!moment.disableComments) {
            RailIconButton(
                drawable = R.drawable.attachment_comments_icon,
                tint = if (moment.commentCount > 0) Color(0xFF007AFF) else primary,
                secondaryTint = if (moment.commentCount > 0) Color(0xFFAF52DE) else primary.copy(alpha = 0.7f),
                active = moment.commentCount > 0,
                count = moment.commentCount.takeIf { it > 0 },
                onClick = onOpenComments,
            )
        }
        RailIconButton(
            drawable = R.drawable.attachment_bookmark_icon,
            tint = if (isSaved) Color(0xFFFFCC00) else primary,
            secondaryTint = if (isSaved) Color(0xFFFF9500) else primary.copy(alpha = 0.7f),
            active = isSaved,
            onClick = onSave,
        )
        RailIconButton(
            icon = Icons.Filled.MoreHoriz,
            tint = primary,
            secondaryTint = primary.copy(alpha = 0.7f),
            active = false,
            onClick = onContextMenu,
        )
    }
}

@Composable
private fun RailIconButton(
    drawable: Int? = null,
    icon: ImageVector? = null,
    tint: Color,
    secondaryTint: Color,
    active: Boolean,
    count: Int? = null,
    onClick: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    Box(contentAlignment = Alignment.TopEnd) {
        Box(
            Modifier
                .size(ActionCircle)
                .scale(if (active) 1.05f else 1f)
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(0.05f) else Color.Black.copy(0.05f))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            when {
                drawable != null -> {
                    Icon(
                        painter = painterResource(drawable),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(22.dp),
                    )
                }
                icon != null -> {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
        if (count != null && count > 0) {
            Text(
                count.toString(),
                color = Color.White,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(RoundedCornerShape(50))
                    .background(if (active) tint else Color.Gray.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
    }
    @Suppress("UNUSED_VARIABLE")
    val _secondary = secondaryTint
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MomentReactionButton(
    momentId: String,
    @Suppress("UNUSED_PARAMETER") authorId: String,
    reactionCount: Int,
    hideLikeCounts: Boolean,
    modifier: Modifier = Modifier,
    sizeDp: Float = ReactionButtonMetrics.buttonSizeDp,
    emojiSizeSp: Float = ReactionButtonMetrics.emojiSizeSp,
) {
    val isDark = isSystemInDarkTheme()
    var hasReacted by remember(momentId) { mutableStateOf(false) }
    var currentReaction by remember(momentId) { mutableStateOf<ReactionType?>(null) }
    var showPicker by remember { mutableStateOf(false) }
    var localCount by remember(momentId) { mutableStateOf(reactionCount) }
    val inactiveEmojiColor = if (isDark) Color.White else FeedInkDark

    Box(modifier) {
        Box(
            Modifier
                .size(sizeDp.dp)
                .scale(if (hasReacted) 1.05f else 1f)
                .clip(CircleShape)
                .background(if (isDark) Color.White.copy(0.05f) else Color.Black.copy(0.05f))
                .combinedClickable(
                    onClick = {
                        if (hasReacted) {
                            hasReacted = false
                            currentReaction = null
                            localCount = (localCount - 1).coerceAtLeast(0)
                        } else {
                            showPicker = true
                        }
                    },
                    onLongClick = { showPicker = true },
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (hasReacted) (currentReaction?.filledIcon ?: "❤️") else "♡",
                fontSize = emojiSizeSp.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasReacted) currentReaction?.color ?: Color(0xFFFF2D55) else inactiveEmojiColor,
            )
        }
        if (!hideLikeCounts && localCount > 0) {
            Text(
                localCount.toString(),
                color = Color.White,
                fontSize = ReactionButtonMetrics.badgeFontSp.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .clip(RoundedCornerShape(50))
                    .background(currentReaction?.color ?: Color.Gray.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }

        if (showPicker) {
            ReactionPickerRow(
                onSelect = { type ->
                    val wasReacted = hasReacted
                    hasReacted = true
                    currentReaction = type
                    if (!wasReacted) localCount += 1
                    showPicker = false
                },
                onDismiss = { showPicker = false },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = (sizeDp + 8).dp),
            )
        }
    }
}

/** Port parcial del picker de `EpicReactionPickerView`. */
@Composable
fun ReactionPickerRow(
    onSelect: (ReactionType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val options = listOf(
        ReactionType.Feel,
        ReactionType.Fire,
        ReactionType.Wow,
        ReactionType.Laugh,
        ReactionType.Vibe,
        ReactionType.Glow,
    )
    Row(
        modifier
            .shadow(12.dp, RoundedCornerShape(28.dp), clip = false)
            .momentsChromeGlass(RoundedCornerShape(28.dp), interactive = true)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEach { type ->
            Text(
                text = type.icon,
                fontSize = 26.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onSelect(type) }
                    .padding(6.dp),
            )
        }
        Text(
            text = "✕",
            fontSize = 14.sp,
            color = Color.Gray,
            modifier = Modifier
                .clip(CircleShape)
                .clickable(onClick = onDismiss)
                .padding(6.dp),
        )
    }
}
