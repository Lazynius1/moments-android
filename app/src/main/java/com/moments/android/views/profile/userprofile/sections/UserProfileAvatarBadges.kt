package com.moments.android.views.profile.userprofile.sections

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.models.AppUser
import com.moments.android.views.story.StoryRingAvatarView

/**
 * Port de `UserModernAvatarWithBadges`: anillo de historia + pulsación larga a foto completa.
 * Los badges de soporte, la corona Plus y el indicador de supporter no se portan:
 * badges/Plus/temas de perfil no se usan en el proyecto.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UserModernAvatarWithBadges(
    userProfile: AppUser?,
    size: Dp,
    storyRingRefreshTrigger: Int,
    onOpenStories: () -> Unit,
    onShowProfileImageFullscreen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.combinedClickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onLongClick = onShowProfileImageFullscreen,
            onClick = {},
        ),
    ) {
        StoryRingAvatarView(
            userId = userProfile?.id.orEmpty(),
            size = size,
            lineWidth = 3.dp,
            refreshTrigger = storyRingRefreshTrigger,
            isOwnStory = false,
            onTap = { hasStory ->
                if (hasStory) onOpenStories() else onShowProfileImageFullscreen()
            },
        )
    }
}
