package com.moments.android.coordinators

import com.moments.android.utilities.MomentMentionNavigation
import com.moments.android.utilities.MomentMentionProfileNavigator

/**
 * Puente temporal para migrar emisores NotificationCenter → [AppRouter].
 * No registrar listeners que el propio [AppRouter.dispatchPending] vuelva a emitir (evita bucles).
 */
object LegacyNavigationBridge {

    fun profile(userId: String) {
        AppRouter.navigate(AppRouter.Destination.Profile(userId))
    }

    fun moment(id: String, authorId: String = "") {
        AppRouter.navigate(AppRouter.Destination.Moment(id = id, authorId = authorId))
    }

    fun conversation(id: String) {
        AppRouter.navigate(AppRouter.Destination.Conversation(id))
    }

    fun ownProfileTab() {
        AppRouter.navigate(AppRouter.Destination.OwnProfileTab)
    }

    fun userProfileInFeed(userId: String) {
        AppRouter.navigate(AppRouter.Destination.UserProfileInFeed(userId))
    }

    fun showExplore() {
        AppRouter.navigate(AppRouter.Destination.ShowExplore)
    }

    fun showUserProfile(userId: String) {
        AppRouter.navigate(AppRouter.Destination.ShowUserProfile(userId))
    }

    fun showMessages() {
        AppRouter.navigate(AppRouter.Destination.ShowMessages)
    }

    fun showNotifications() {
        AppRouter.navigate(AppRouter.Destination.ShowNotifications)
    }

    fun storyChain(chainId: String, title: String) {
        AppRouter.navigate(AppRouter.Destination.StoryChain(chainId = chainId, title = title))
    }

    /** Registra el navegador de menciones (@username) usado por [MomentMentionNavigation]. */
    fun wireMentionNavigation() {
        MomentMentionNavigation.profileNavigator = MomentMentionProfileNavigator { userId ->
            showUserProfile(userId)
        }
    }
}
