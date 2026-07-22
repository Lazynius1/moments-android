package com.moments.android.views.feed.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.notifications.screens.NotificationSummaryService
import com.moments.android.notifications.services.NotificationBadgeService
import com.moments.android.notifications.services.NotificationNavigationService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.views.feed.stories.FeedStoryRingCoordinator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * Port 1:1 de `FeedNotificationRoutingModifier.swift`.
 *
 * Compose no tiene ViewModifier + Binding; esta effect replica la misma superficie
 * de dependencias y el mismo comportamiento de routing / lifecycle.
 */
@Composable
fun FeedNotificationRoutingEffect(
    context: Context,
    scope: CoroutineScope,
    setShowMessages: (Boolean) -> Unit,
    setShowNotifications: (Boolean) -> Unit,
    setShowCreatorView: (Boolean) -> Unit,
    setShowExplore: (Boolean) -> Unit,
    setShowMomentDetail: (Boolean) -> Unit,
    setTargetConversationId: (String?) -> Unit,
    setTargetMomentId: (String?) -> Unit,
    setTargetMomentUserId: (String?) -> Unit,
    setShowNotificationSummary: (Boolean) -> Unit,
    notificationSummaryService: NotificationSummaryService,
    badgeService: NotificationBadgeService,
    storyRingCoordinator: FeedStoryRingCoordinator,
    firestoreService: FirestoreService,
    onOpenUserProfile: (String) -> Unit,
    onOpenStory: (storyId: String, authorId: String?) -> Unit,
    onOpenStoryChain: (chainId: String, chainTitle: String) -> Unit,
) {
    val lifecycleOwner = LocalLifecycleOwner.current

    // willResignActive / willEnterForeground
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    notificationSummaryService.markAppClosed(context)
                }
                Lifecycle.Event.ON_START -> {
                    scope.launch {
                        delay(500)
                        notificationSummaryService.checkShouldShowSummary(
                            context = context,
                            unreadNotifications = badgeService.unreadNotificationsCount.value,
                            unreadMessages = badgeService.unreadMessagesCount.value,
                            onShow = { setShowNotificationSummary(true) },
                        )
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // navigationService.$pendingNavigation
    LaunchedEffect(Unit) {
        NotificationNavigationService.pendingNavigation
            .filterNotNull()
            .collectLatest { navigation ->
                when (navigation) {
                    is NotificationNavigationService.PendingNavigation.Conversation -> {
                        setTargetConversationId(navigation.conversationId)
                        setShowMessages(true)
                    }
                    is NotificationNavigationService.PendingNavigation.Moment -> {
                        setTargetMomentId(navigation.momentId)
                        setTargetMomentUserId(navigation.userId)
                        setShowMomentDetail(true)
                    }
                    is NotificationNavigationService.PendingNavigation.Profile -> {
                        // iOS: case .profile: break
                    }
                    is NotificationNavigationService.PendingNavigation.Story -> {
                        onOpenStory(navigation.storyId, navigation.authorId)
                    }
                    is NotificationNavigationService.PendingNavigation.Notifications -> {
                        setShowNotifications(true)
                    }
                    is NotificationNavigationService.PendingNavigation.Creator -> {
                        setShowCreatorView(true)
                    }
                    is NotificationNavigationService.PendingNavigation.StoryChain -> {
                        onOpenStoryChain(navigation.chainId, navigation.chainTitle)
                    }
                    else -> Unit
                }
                NotificationNavigationService.clearPendingNavigation()
            }
    }

    // NotificationCenter publishers → NavigationEventBus
    LaunchedEffect(Unit) {
        NavigationEventBus.events.collectLatest { event ->
            when (event) {
                CoordinatorNavigationEvent.ShowMessages -> setShowMessages(true)
                // iOS: ShowNotifications + NavigateToNotifications + OpenNotifications
                CoordinatorNavigationEvent.ShowNotifications,
                is CoordinatorNavigationEvent.NavigateToNotifications,
                CoordinatorNavigationEvent.OpenNotifications,
                -> setShowNotifications(true)
                CoordinatorNavigationEvent.ShowCreatorView -> setShowCreatorView(true)
                CoordinatorNavigationEvent.ShowExploreView -> setShowExplore(true)
                CoordinatorNavigationEvent.StoryUploaded -> {
                    val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return@collectLatest
                    storyRingCoordinator.loadStoryUsers(scope, userId)
                }
                is CoordinatorNavigationEvent.NavigateToUserProfileInFeed -> {
                    if (event.userId.isNotEmpty()) onOpenUserProfile(event.userId)
                }
                is CoordinatorNavigationEvent.NavigateToStoryChainInFeed -> {
                    onOpenStoryChain(event.chainId, event.chainTitle)
                }
                is CoordinatorNavigationEvent.NavigateToStoryInFeed -> {
                    val authorId = event.authorId.trim().ifEmpty { null }
                    onOpenStory(event.storyId, authorId)
                }
                is CoordinatorNavigationEvent.NavigateToConversation -> {
                    if (event.conversationId.isNotEmpty()) {
                        setTargetConversationId(event.conversationId)
                        setShowMessages(true)
                    }
                }
                is CoordinatorNavigationEvent.NavigateToMoment -> {
                    val momentId = event.momentId
                    if (momentId.isEmpty()) return@collectLatest
                    val userId = event.userId?.trim().orEmpty()
                    if (userId.isNotEmpty()) {
                        setTargetMomentId(momentId)
                        setTargetMomentUserId(userId)
                        setShowMomentDetail(true)
                        return@collectLatest
                    }
                    scope.launch {
                        val authorId = firestoreService.fetchMomentAuthorId(momentId)
                        if (!authorId.isNullOrEmpty()) {
                            setTargetMomentId(momentId)
                            setTargetMomentUserId(authorId)
                            setShowMomentDetail(true)
                        }
                    }
                }
                else -> Unit
            }
        }
    }
}
