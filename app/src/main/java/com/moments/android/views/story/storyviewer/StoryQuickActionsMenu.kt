package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.components.MomentRowButton
import com.moments.android.views.components.MomentRowButtonFeedback

/** Port de `StoryQuickActionsMenu.swift`. */
@Composable
fun StoryQuickActionsMenu(
    isOwnStory: Boolean,
    canLeaveBestFriends: Boolean,
    textColor: Color,
    dividerColor: Color,
    onViewActivity: () -> Unit,
    onSave: () -> Unit,
    onDelete: () -> Unit,
    onUnfollow: () -> Unit,
    onMute: () -> Unit,
    onReport: () -> Unit,
    onLeaveBestFriends: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    // ≡ iOS `.frame(minWidth: 200).fixedSize(horizontal: true)` — no full-bleed
    Column(
        modifier
            .widthIn(min = 200.dp)
            .width(IntrinsicSize.Max)
            .momentsChromeGlass(shape, interactive = false)
            // ≡ .onTapGesture {} — traga taps del menú (no cierra el overlay)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {},
            ),
    ) {
        if (isOwnStory) {
            StoryMenuActionRow(
                title = stringResource(R.string.story_context_menu_view_activity),
                textColor = textColor,
                destructive = false,
                onClick = onViewActivity,
            )
            MenuDivider(dividerColor)
            StoryMenuActionRow(
                title = stringResource(R.string.story_context_menu_save),
                textColor = textColor,
                destructive = false,
                onClick = onSave,
            )
            MenuDivider(dividerColor)
            StoryMenuActionRow(
                title = stringResource(R.string.story_context_menu_delete),
                textColor = textColor,
                destructive = true,
                onClick = onDelete,
            )
        } else {
            StoryMenuActionRow(
                title = stringResource(R.string.story_context_menu_unfollow),
                textColor = textColor,
                destructive = false,
                onClick = onUnfollow,
            )
            MenuDivider(dividerColor)
            StoryMenuActionRow(
                title = stringResource(R.string.story_context_menu_mute),
                textColor = textColor,
                destructive = false,
                onClick = onMute,
            )
            MenuDivider(dividerColor)
            StoryMenuActionRow(
                title = stringResource(R.string.story_context_menu_report),
                textColor = textColor,
                destructive = true,
                onClick = onReport,
            )
            if (canLeaveBestFriends) {
                MenuDivider(dividerColor)
                StoryMenuActionRow(
                    title = stringResource(R.string.story_context_menu_leave_best_friends),
                    textColor = textColor,
                    destructive = false,
                    onClick = onLeaveBestFriends,
                )
            }
        }
    }
}

@Composable
private fun MenuDivider(color: Color) {
    HorizontalDivider(color = color, thickness = 0.5.dp)
}

/** ≡ `StoryMenuActionRow` + `.buttonStyle(.momentsMenuRow)`. */
@Composable
private fun StoryMenuActionRow(
    title: String,
    textColor: Color,
    destructive: Boolean,
    onClick: () -> Unit,
) {
    MomentRowButton(
        action = onClick,
        feedback = MomentRowButtonFeedback.MENU,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            color = if (destructive) Color(0xFFFF453A) else textColor,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 15.dp),
        )
    }
}
