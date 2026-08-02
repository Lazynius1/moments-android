package com.moments.android.views.comments

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.services.content.FeedMoment
import com.moments.android.views.feed.rememberAdaptiveColors
import com.moments.android.views.shared.MomentsModalSheet

/**
 * Comentarios en [MomentsModalSheet] (large al abrir, como IG).
 * Composer con [com.moments.android.views.shared.MomentsSheetPinnedFooter].
 */
@Composable
fun ModernCommentsSheet(
    moment: FeedMoment,
    onDismiss: () -> Unit,
    onOpenStory: (userId: String) -> Unit = { userId ->
        NavigationEventBus.emit(CoordinatorNavigationEvent.ShowStoriesStartingAt(userId))
    },
    onOpenProfile: (userId: String) -> Unit = { userId ->
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid != null && uid == userId) {
            NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToOwnProfileTab)
        } else {
            NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToUserProfileInFeed(userId))
        }
    },
) {
    val canvas = rememberAdaptiveColors().surfaceBackground
    MomentsModalSheet(
        onDismissRequest = onDismiss,
        largeOnly = true,
        containerColor = canvas,
        showDragHandle = true,
    ) { dismiss ->
        ModernCommentsView(
            moment = moment,
            onDismiss = dismiss,
            onOpenStory = onOpenStory,
            onOpenProfile = onOpenProfile,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = true),
        )
    }
}
