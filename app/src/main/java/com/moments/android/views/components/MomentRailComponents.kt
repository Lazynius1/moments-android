package com.moments.android.views.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.feed.reactions.PostActionButtons

/**
 * Fachada de `MomentRailComponents.swift`.
 * La paleta vive en `views/feed/AdaptiveColors.kt` y el rail concreto en `PostActionButtons`,
 * que son sus equivalentes Android ya cableados; esta entrada preserva la organización iOS.
 */
@Composable
fun ModernActionButtons(
    moment: FeedMoment,
    isSaved: Boolean,
    onComment: () -> Unit,
    onSave: () -> Unit,
    onContextMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PostActionButtons(
        moment = moment,
        onOpenComments = onComment,
        onContextMenu = onContextMenu,
        isSaved = isSaved,
        onSave = onSave,
        modifier = modifier,
    )
}
