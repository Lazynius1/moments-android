package com.moments.android.coordinators

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Emisor de eventos de navegación (equivalente a NotificationCenter en iOS TabBar / AppRouter).
 */
object NavigationEventBus {
    private val _events = MutableSharedFlow<CoordinatorNavigationEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<CoordinatorNavigationEvent> = _events.asSharedFlow()

    fun emit(event: CoordinatorNavigationEvent) {
        _events.tryEmit(event)
    }
}

sealed class CoordinatorNavigationEvent {
    data class NavigateToMoment(val momentId: String, val userId: String? = null) : CoordinatorNavigationEvent()
    data class NavigateToProfile(val userId: String) : CoordinatorNavigationEvent()
    data class NavigateToConversation(val conversationId: String) : CoordinatorNavigationEvent()
    data class NavigateToStoryInFeed(val storyId: String, val authorId: String) : CoordinatorNavigationEvent()
    data class NavigateToStoryChain(val chainId: String, val chainTitle: String) : CoordinatorNavigationEvent()
    data class NavigateToFollowRequests(val requestId: String) : CoordinatorNavigationEvent()
    data class NavigateToNotifications(val filter: String?) : CoordinatorNavigationEvent()
    data class ShowUserProfile(val userId: String) : CoordinatorNavigationEvent()
    data object ShowMessages : CoordinatorNavigationEvent()
    data object ShowNotifications : CoordinatorNavigationEvent()
    /** Paridad iOS NotificationCenter `"OpenNotifications"`. */
    data object OpenNotifications : CoordinatorNavigationEvent()
    data object ShowProfileVisits : CoordinatorNavigationEvent()
    data object ShowStories : CoordinatorNavigationEvent()
    data object ScrollFeedToTop : CoordinatorNavigationEvent()
    data class NavigateToUserProfileInFeed(val userId: String) : CoordinatorNavigationEvent()
    data object ShowExploreView : CoordinatorNavigationEvent()
    data object ShowCreatorView : CoordinatorNavigationEvent()
    data object StoryUploaded : CoordinatorNavigationEvent()
    data class NavigateToStoryChainInFeed(val chainId: String, val chainTitle: String) : CoordinatorNavigationEvent()
    data object ReturnToFeedAfterMomentPublish : CoordinatorNavigationEvent()
    data class OpenCreatorForChain(
        val chainId: String,
        val chainTitle: String,
        val chainPosition: Int,
    ) : CoordinatorNavigationEvent()
    data class SetContentType(val contentType: String) : CoordinatorNavigationEvent()
    data class SetChainContext(
        val chainId: String,
        val chainTitle: String,
        val chainPosition: Int,
    ) : CoordinatorNavigationEvent()
    data class NavigateToUserProfile(val userId: String) : CoordinatorNavigationEvent()
    data object NavigateToOwnProfileTab : CoordinatorNavigationEvent()
    /** iOS NotificationCenter "NotificationsCleared". */
    data object NotificationsCleared : CoordinatorNavigationEvent()
    /** iOS Notification.Name.forceFeedRefresh */
    data object ForceFeedRefresh : CoordinatorNavigationEvent()
}
