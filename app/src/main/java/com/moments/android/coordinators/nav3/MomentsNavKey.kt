package com.moments.android.coordinators.nav3

import androidx.navigation3.runtime.NavKey
import com.moments.android.coordinators.AppRouter
import kotlinx.serialization.Serializable

/**
 * Destinos tipados Navigation 3 ≡ [AppRouter.Destination].
 *
 * Fase 1 (skill `navigation-3`): keys + puente; TabBar/EventBus siguen vivos.
 * Fase 2: back stacks por tab + [androidx.navigation3.ui.NavDisplay].
 * Deep links: [MomentsDeepLinkParser] → key → [AppRouter.navigate].
 */
@Serializable
sealed interface MomentsNavKey : NavKey {

    @Serializable
    data class Profile(val userId: String) : MomentsNavKey

    @Serializable
    data class Moment(val id: String, val authorId: String = "") : MomentsNavKey

    @Serializable
    data class Conversation(val id: String) : MomentsNavKey

    @Serializable
    data class Story(val storyId: String, val authorId: String? = null) : MomentsNavKey

    @Serializable
    data class StoryChain(val chainId: String, val title: String = "") : MomentsNavKey

    @Serializable
    data class FollowRequests(val requestId: String) : MomentsNavKey

    @Serializable
    data class Notifications(val filter: String? = null) : MomentsNavKey

    @Serializable
    data object Creator : MomentsNavKey

    @Serializable
    data class EchoSuggestion(val echoId: String) : MomentsNavKey

    @Serializable
    data class Echo(val echoId: String) : MomentsNavKey

    @Serializable
    data class ShowUserProfile(val userId: String) : MomentsNavKey

    @Serializable
    data object ShowMessages : MomentsNavKey

    /** Nova desde el header del feed (antes era tab 1). */
    @Serializable
    data object ShowNova : MomentsNavKey

    @Serializable
    data object ShowNotifications : MomentsNavKey

    @Serializable
    data object ShowProfileVisits : MomentsNavKey

    @Serializable
    data object ShowStories : MomentsNavKey

    @Serializable
    data object ScrollFeedToTop : MomentsNavKey

    @Serializable
    data object OwnProfileTab : MomentsNavKey

    @Serializable
    data class UserProfileInFeed(val userId: String) : MomentsNavKey

    @Serializable
    data object ShowExplore : MomentsNavKey
}

/** AppRouter → Nav3 key. */
fun AppRouter.Destination.toMomentsNavKey(): MomentsNavKey = when (this) {
    is AppRouter.Destination.Profile -> MomentsNavKey.Profile(userId)
    is AppRouter.Destination.Moment -> MomentsNavKey.Moment(id, authorId)
    is AppRouter.Destination.Conversation -> MomentsNavKey.Conversation(id)
    is AppRouter.Destination.Story -> MomentsNavKey.Story(storyId, authorId)
    is AppRouter.Destination.StoryChain -> MomentsNavKey.StoryChain(chainId, title)
    is AppRouter.Destination.FollowRequests -> MomentsNavKey.FollowRequests(requestId)
    is AppRouter.Destination.Notifications -> MomentsNavKey.Notifications(filter)
    AppRouter.Destination.Creator -> MomentsNavKey.Creator
    is AppRouter.Destination.EchoSuggestion -> MomentsNavKey.EchoSuggestion(echoId)
    is AppRouter.Destination.Echo -> MomentsNavKey.Echo(echoId)
    is AppRouter.Destination.ShowUserProfile -> MomentsNavKey.ShowUserProfile(userId)
    AppRouter.Destination.ShowMessages -> MomentsNavKey.ShowMessages
    AppRouter.Destination.ShowNova -> MomentsNavKey.ShowNova
    AppRouter.Destination.ShowNotifications -> MomentsNavKey.ShowNotifications
    AppRouter.Destination.ShowProfileVisits -> MomentsNavKey.ShowProfileVisits
    AppRouter.Destination.ShowStories -> MomentsNavKey.ShowStories
    AppRouter.Destination.ScrollFeedToTop -> MomentsNavKey.ScrollFeedToTop
    AppRouter.Destination.OwnProfileTab -> MomentsNavKey.OwnProfileTab
    is AppRouter.Destination.UserProfileInFeed -> MomentsNavKey.UserProfileInFeed(userId)
    AppRouter.Destination.ShowExplore -> MomentsNavKey.ShowExplore
}

/** Nav3 key → AppRouter (dispatch existente). */
fun MomentsNavKey.toAppRouterDestination(): AppRouter.Destination = when (this) {
    is MomentsNavKey.Profile -> AppRouter.Destination.Profile(userId)
    is MomentsNavKey.Moment -> AppRouter.Destination.Moment(id, authorId)
    is MomentsNavKey.Conversation -> AppRouter.Destination.Conversation(id)
    is MomentsNavKey.Story -> AppRouter.Destination.Story(storyId, authorId)
    is MomentsNavKey.StoryChain -> AppRouter.Destination.StoryChain(chainId, title)
    is MomentsNavKey.FollowRequests -> AppRouter.Destination.FollowRequests(requestId)
    is MomentsNavKey.Notifications -> AppRouter.Destination.Notifications(filter)
    MomentsNavKey.Creator -> AppRouter.Destination.Creator
    is MomentsNavKey.EchoSuggestion -> AppRouter.Destination.EchoSuggestion(echoId)
    is MomentsNavKey.Echo -> AppRouter.Destination.Echo(echoId)
    is MomentsNavKey.ShowUserProfile -> AppRouter.Destination.ShowUserProfile(userId)
    MomentsNavKey.ShowMessages -> AppRouter.Destination.ShowMessages
    MomentsNavKey.ShowNova -> AppRouter.Destination.ShowNova
    MomentsNavKey.ShowNotifications -> AppRouter.Destination.ShowNotifications
    MomentsNavKey.ShowProfileVisits -> AppRouter.Destination.ShowProfileVisits
    MomentsNavKey.ShowStories -> AppRouter.Destination.ShowStories
    MomentsNavKey.ScrollFeedToTop -> AppRouter.Destination.ScrollFeedToTop
    MomentsNavKey.OwnProfileTab -> AppRouter.Destination.OwnProfileTab
    is MomentsNavKey.UserProfileInFeed -> AppRouter.Destination.UserProfileInFeed(userId)
    MomentsNavKey.ShowExplore -> AppRouter.Destination.ShowExplore
}

/** Navega vía AppRouter manteniendo el contrato actual. */
fun MomentsNavKey.navigateViaAppRouter() {
    AppRouter.navigate(toAppRouterDestination())
}
