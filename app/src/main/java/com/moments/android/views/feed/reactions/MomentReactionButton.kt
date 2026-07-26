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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.components.ModernActionButtons

private val FeedInkDark = Color(0xFF0B1215)

/**
 * Alias de compatibilidad → `ModernActionButtons` (MomentRailComponents.swift).
 */
@Composable
fun PostActionButtons(
    moment: FeedMoment,
    commentCount: Int = moment.commentCount,
    onOpenComments: () -> Unit,
    onShare: () -> Unit = {},
    onContextMenu: () -> Unit = {},
    isSaved: Boolean = false,
    isSaveLoading: Boolean = false,
    onSave: () -> Unit = {},
    isImmersive: Boolean = false,
    modifier: Modifier = Modifier,
) {
    @Suppress("UNUSED_PARAMETER")
    val unusedShare = onShare
    ModernActionButtons(
        moment = moment,
        isSaved = isSaved,
        isSaveLoading = isSaveLoading,
        commentCount = commentCount,
        onComment = onOpenComments,
        onSave = onSave,
        onContextMenu = onContextMenu,
        isImmersive = isImmersive,
        modifier = modifier,
    )
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
