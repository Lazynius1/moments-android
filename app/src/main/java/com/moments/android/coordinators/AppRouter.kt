package com.moments.android.coordinators

import com.moments.android.notifications.services.NotificationNavigationService
import com.moments.android.notifications.services.NotificationOpenIntentStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Navegación tipada centralizada. Migración incremental desde NotificationCenter y
 * [NotificationNavigationService].
 */
object AppRouter {

    sealed class Destination {
        data class Profile(val userId: String) : Destination()
        data class Moment(val id: String, val authorId: String) : Destination()
        data class Conversation(val id: String) : Destination()
        data class Story(val storyId: String, val authorId: String?) : Destination()
        data class StoryChain(val chainId: String, val title: String) : Destination()
        data class FollowRequests(val requestId: String) : Destination()
        data class Notifications(val filter: String?) : Destination()
        data object Creator : Destination()
        data class EchoSuggestion(val echoId: String) : Destination()
        data class Echo(val echoId: String) : Destination()
        data class ShowUserProfile(val userId: String) : Destination()
        data object ShowMessages : Destination()
        data object ShowNotifications : Destination()
        data object ShowProfileVisits : Destination()
        data object ShowStories : Destination()
        data object ScrollFeedToTop : Destination()
        data object OwnProfileTab : Destination()
        data class UserProfileInFeed(val userId: String) : Destination()
        data object ShowExplore : Destination()
    }

    private val _pending = MutableStateFlow<Destination?>(null)
    val pending: StateFlow<Destination?> = _pending.asStateFlow()

    fun navigate(to: Destination) {
        _pending.value = to
        NotificationNavigationService.syncPendingNavigation(from = to)
    }

    fun clearPending() {
        _pending.value = null
        NotificationNavigationService.clearPendingNavigation()
    }

    fun consumePending(): Destination? {
        val value = _pending.value
        _pending.value = null
        return value
    }

    fun dispatchPending(context: AppRouterTabBarContext) {
        val destination = _pending.value ?: return

        when (destination) {
            is Destination.Moment -> {
                context.setSelectedTab(0)
                NavigationEventBus.emit(
                    CoordinatorNavigationEvent.NavigateToMoment(
                        momentId = destination.id,
                        userId = destination.authorId.takeIf { it.isNotEmpty() },
                    ),
                )
            }
            is Destination.Profile -> {
                context.setSelectedTab(0)
                NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToProfile(destination.userId))
            }
            is Destination.Conversation -> {
                NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToConversation(destination.id))
            }
            is Destination.Story -> {
                context.setSelectedTab(0)
                NavigationEventBus.emit(
                    CoordinatorNavigationEvent.NavigateToStoryInFeed(
                        storyId = destination.storyId,
                        authorId = destination.authorId.orEmpty(),
                    ),
                )
            }
            is Destination.StoryChain -> {
                context.setSelectedTab(0)
                NavigationEventBus.emit(
                    CoordinatorNavigationEvent.NavigateToStoryChain(destination.chainId, destination.title),
                )
            }
            is Destination.FollowRequests -> {
                context.setSelectedTab(4)
                NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToFollowRequests(destination.requestId))
            }
            is Destination.Notifications -> {
                context.setSelectedTab(0)
                NotificationOpenIntentStore.enqueue(destination.filter)
                NavigationEventBus.emit(CoordinatorNavigationEvent.NavigateToNotifications(destination.filter))
            }
            Destination.Creator -> {
                context.setSelectedTab(0)
                context.setShowCreatorView(true)
            }
            is Destination.EchoSuggestion -> {
                context.setPendingEchoId(destination.echoId)
                context.setShowEchoInvitation(true)
                context.onEchoInvitationRoute(destination.echoId)
            }
            is Destination.Echo -> {
                context.setPendingEchoId(destination.echoId)
                context.setShowEchoViewer(true)
            }
            is Destination.ShowUserProfile -> {
                NavigationEventBus.emit(CoordinatorNavigationEvent.ShowUserProfile(destination.userId))
            }
            Destination.ShowMessages -> {
                NavigationEventBus.emit(CoordinatorNavigationEvent.ShowMessages)
            }
            Destination.ShowNotifications -> {
                NavigationEventBus.emit(CoordinatorNavigationEvent.ShowNotifications)
            }
            Destination.ShowProfileVisits -> {
                context.setSelectedTab(4)
                NavigationEventBus.emit(CoordinatorNavigationEvent.ShowProfileVisits)
            }
            Destination.ShowStories -> {
                context.setSelectedTab(0)
                NavigationEventBus.emit(CoordinatorNavigationEvent.ShowStories)
            }
            Destination.ScrollFeedToTop -> {
                NavigationEventBus.emit(CoordinatorNavigationEvent.ScrollFeedToTop)
            }
            Destination.OwnProfileTab -> {
                context.setSelectedTab(4)
            }
            is Destination.UserProfileInFeed -> {
                context.setSelectedTab(0)
                context.postDelayed {
                    NavigationEventBus.emit(
                        CoordinatorNavigationEvent.NavigateToUserProfileInFeed(destination.userId),
                    )
                }
            }
            Destination.ShowExplore -> {
                context.setSelectedTab(0)
                NavigationEventBus.emit(CoordinatorNavigationEvent.ShowExploreView)
            }
        }

        clearPending()
    }
}

/** Contexto mutable para [AppRouter.dispatchPending] desde TabBar. */
class AppRouterTabBarContext(
    val setSelectedTab: (Int) -> Unit,
    val setShowCreatorView: (Boolean) -> Unit,
    val setPendingEchoId: (String) -> Unit,
    val setShowEchoInvitation: (Boolean) -> Unit,
    val setShowEchoViewer: (Boolean) -> Unit,
    val onEchoInvitationRoute: (String) -> Unit,
    val postDelayed: (block: () -> Unit) -> Unit = { block -> block() },
)

val AppRouter.Destination.legacyPendingNavigation: NotificationNavigationService.PendingNavigation?
    get() = when (this) {
        is AppRouter.Destination.Profile -> NotificationNavigationService.PendingNavigation.Profile(userId)
        is AppRouter.Destination.Moment -> NotificationNavigationService.PendingNavigation.Moment(id, authorId)
        is AppRouter.Destination.Conversation -> NotificationNavigationService.PendingNavigation.Conversation(id)
        is AppRouter.Destination.Story -> NotificationNavigationService.PendingNavigation.Story(storyId, authorId)
        is AppRouter.Destination.StoryChain -> NotificationNavigationService.PendingNavigation.StoryChain(chainId, title)
        is AppRouter.Destination.FollowRequests -> NotificationNavigationService.PendingNavigation.FollowRequests(requestId)
        is AppRouter.Destination.Notifications -> NotificationNavigationService.PendingNavigation.Notifications(filter)
        AppRouter.Destination.Creator -> NotificationNavigationService.PendingNavigation.Creator
        is AppRouter.Destination.EchoSuggestion -> NotificationNavigationService.PendingNavigation.EchoSuggestion(echoId)
        is AppRouter.Destination.Echo -> NotificationNavigationService.PendingNavigation.Echo(echoId)
        is AppRouter.Destination.ShowUserProfile,
        AppRouter.Destination.ShowMessages,
        AppRouter.Destination.ShowNotifications,
        AppRouter.Destination.ShowProfileVisits,
        AppRouter.Destination.ShowStories,
        AppRouter.Destination.ScrollFeedToTop,
        AppRouter.Destination.OwnProfileTab,
        is AppRouter.Destination.UserProfileInFeed,
        AppRouter.Destination.ShowExplore,
        -> null
    }
