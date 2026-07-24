package com.moments.android.views.story.storyviewer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
    Column(modifier.widthIn(min = 200.dp).background(Color(0xE61C1C1E)).padding(vertical = 2.dp)) {
        if (isOwnStory) {
            StoryMenuActionRow("View activity", textColor, false, onViewActivity)
            MenuDivider(dividerColor)
            StoryMenuActionRow("Save", textColor, false, onSave)
            MenuDivider(dividerColor)
            StoryMenuActionRow("Delete", textColor, true, onDelete)
        } else {
            StoryMenuActionRow("Unfollow", textColor, false, onUnfollow)
            MenuDivider(dividerColor)
            StoryMenuActionRow("Mute", textColor, false, onMute)
            MenuDivider(dividerColor)
            StoryMenuActionRow("Report", textColor, true, onReport)
            if (canLeaveBestFriends) {
                MenuDivider(dividerColor)
                StoryMenuActionRow("Leave best friends", textColor, false, onLeaveBestFriends)
            }
        }
    }
}

@Composable
private fun MenuDivider(color: Color) = HorizontalDivider(color = color)

@Composable
private fun StoryMenuActionRow(title: String, textColor: Color, destructive: Boolean, onClick: () -> Unit) {
    Text(
        title,
        color = if (destructive) Color(0xFFFF453A) else textColor,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 18.dp, vertical = 15.dp),
    )
}
