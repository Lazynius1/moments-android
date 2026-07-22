package com.moments.android.notifications.services

import com.moments.android.coordinators.AppRouter
import com.moments.android.coordinators.legacyPendingNavigation
import com.moments.android.views.messaging.services.ChatNavigationIntentStore
import com.moments.android.services.messaging.MessagingEvents
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Port de NotificationNavigationService.swift — emite rutas para MainActivity / NavHost.
 */
object NotificationNavigationService {
    sealed class PendingNavigation {
        data class Moment(val momentId: String, val userId: String) : PendingNavigation()
        data class Profile(val userId: String) : PendingNavigation()
        data class Conversation(val conversationId: String) : PendingNavigation()
        data class Story(val storyId: String, val authorId: String?) : PendingNavigation()
        data class StoryChain(val chainId: String, val chainTitle: String) : PendingNavigation()
        data class FollowRequests(val requestId: String) : PendingNavigation()
        data class Notifications(val filter: String?) : PendingNavigation()
        data object Creator : PendingNavigation()
        data class EchoSuggestion(val echoId: String) : PendingNavigation()
        data class Echo(val echoId: String) : PendingNavigation()
    }

    private val _pendingNavigation = MutableStateFlow<PendingNavigation?>(null)
    val pendingNavigation: StateFlow<PendingNavigation?> = _pendingNavigation.asStateFlow()

    private val _navigationEvents = MutableSharedFlow<PendingNavigation>(extraBufferCapacity = 8)
    val navigationEvents: SharedFlow<PendingNavigation> = _navigationEvents.asSharedFlow()

    fun navigateToMoment(momentId: String, userId: String) {
        AppRouter.navigate(AppRouter.Destination.Moment(momentId, userId))
    }

    fun navigateToProfile(userId: String) {
        AppRouter.navigate(AppRouter.Destination.Profile(userId))
    }

    fun navigateToNotifications(filter: String?) {
        NotificationOpenIntentStore.enqueue(filter)
        AppRouter.navigate(AppRouter.Destination.Notifications(filter))
    }

    fun navigateToStory(storyId: String, authorId: String? = null) {
        AppRouter.navigate(AppRouter.Destination.Story(storyId, authorId))
    }

    fun navigateToConversation(conversationId: String) {
        AppRouter.navigate(AppRouter.Destination.Conversation(conversationId))
    }

    fun navigateToCreator() {
        AppRouter.navigate(AppRouter.Destination.Creator)
    }

    fun handleNotificationData(userInfo: Map<String, Any?>) {
        val type = userInfo["type"] as? String ?: return
        when (normalizedType(type)) {
            "reaction", "comment" -> {
                val momentId = firstString(userInfo, listOf("momentId", "targetId"))
                val userId = firstString(userInfo, listOf("targetAuthorId", "momentOwnerId"))
                if (momentId != null && userId != null) navigateToMoment(momentId, userId)
            }
            "storyReaction" -> {
                val storyId = userInfo["storyId"] as? String ?: return
                val authorId = listOf("storyAuthorId", "storyOwnerId", "targetAuthorId")
                    .firstNotNullOfOrNull { userInfo[it] as? String }
                navigateToStory(storyId, authorId)
            }
            "newFollower", "requestAccepted", "mutualConnection" -> {
                firstString(userInfo, listOf("followerId", "senderId", "targetId"))?.let { navigateToProfile(it) }
            }
            "message" -> {
                (userInfo["conversationId"] as? String)?.let { navigateToConversation(it) }
            }
            "messageReaction" -> {
                val conversationId = userInfo["conversationId"] as? String ?: return
                firstString(userInfo, listOf("messageId", "targetMessageId"))?.let { messageId ->
                    ChatNavigationIntentStore.enqueueHighlight(conversationId, messageId)
                }
                navigateToConversation(conversationId)
            }
            "chat_buzz" -> {
                val conversationId = userInfo["conversationId"] as? String ?: return
                val buzzEventId = firstString(userInfo, listOf("buzzEventId"))
                ChatNavigationIntentStore.enqueueBuzz(conversationId, buzzEventId)
                MessagingEvents.emitChatBuzzHighlight(conversationId, buzzEventId)
                navigateToConversation(conversationId)
            }
            "followRequest" -> {
                val requestId = userInfo["requestId"] as? String ?: return
                AppRouter.navigate(AppRouter.Destination.FollowRequests(requestId))
            }
            "mention" -> handleMention(userInfo)
            "gentle_reminder" -> navigateToCreator()
            "storyChainContinued" -> {
                val chainId = userInfo["chainId"] as? String
                val chainTitle = userInfo["chainTitle"] as? String
                if (chainId != null && chainTitle != null) {
                    AppRouter.navigate(AppRouter.Destination.StoryChain(chainId, chainTitle))
                } else {
                    (userInfo["storyId"] as? String)?.let { navigateToStory(it, userInfo["senderId"] as? String) }
                }
            }
            "echoSuggestion" -> {
                (userInfo["echoId"] as? String)?.let {
                    AppRouter.navigate(AppRouter.Destination.EchoSuggestion(it))
                }
            }
            "like", "photoTag" -> {
                val momentId = firstString(userInfo, listOf("momentId", "targetId"))
                val userId = firstString(userInfo, listOf("targetAuthorId", "momentOwnerId", "senderId"))
                if (momentId != null && userId != null) navigateToMoment(momentId, userId)
                else navigateToNotifications(null)
            }
            "mediaModeration" -> handleModeration(userInfo)
            else -> navigateToNotifications(null)
        }
    }

    private fun handleMention(userInfo: Map<String, Any?>) {
        val momentId = userInfo["momentId"] as? String
        if (!momentId.isNullOrEmpty()) {
            val userId = listOf("targetAuthorId", "momentOwnerId", "senderId")
                .firstNotNullOfOrNull { userInfo[it] as? String }.orEmpty()
            if (userId.isNotEmpty()) navigateToMoment(momentId, userId) else navigateToNotifications(null)
            return
        }
        val storyId = userInfo["storyId"] as? String
        if (!storyId.isNullOrEmpty()) {
            val authorId = listOf("storyAuthorId", "targetAuthorId", "senderId")
                .firstNotNullOfOrNull { userInfo[it] as? String }
            navigateToStory(storyId, authorId)
            return
        }
        (userInfo["senderId"] as? String)?.let { navigateToProfile(it) }
    }

    private fun handleModeration(userInfo: Map<String, Any?>) {
        val momentId = firstString(userInfo, listOf("momentId", "targetId"))
        if (!momentId.isNullOrEmpty()) {
            val userId = firstString(userInfo, listOf("targetAuthorId", "momentOwnerId", "senderId")).orEmpty()
            if (userId.isNotEmpty()) navigateToMoment(momentId, userId) else navigateToNotifications(null)
            return
        }
        val storyId = userInfo["storyId"] as? String
        if (!storyId.isNullOrEmpty()) {
            navigateToStory(storyId, userInfo["storyAuthorId"] as? String ?: userInfo["targetAuthorId"] as? String)
        } else {
            navigateToNotifications(null)
        }
    }

    fun clearPendingNavigation() {
        _pendingNavigation.value = null
    }

    fun syncPendingNavigation(from: AppRouter.Destination) {
        val legacy = from.legacyPendingNavigation
        _pendingNavigation.value = legacy
        legacy?.let { _navigationEvents.tryEmit(it) }
    }

    private fun normalizedType(rawType: String): String = when (rawType) {
        "moment_reaction" -> "reaction"
        "moment_comment" -> "comment"
        "story_reaction" -> "storyReaction"
        "story_chain_continued" -> "storyChainContinued"
        "new_follower" -> "newFollower"
        "follow_request" -> "followRequest"
        "new_message" -> "message"
        "message_reaction" -> "messageReaction"
        "photo_tag" -> "photoTag"
        "media_moderation" -> "mediaModeration"
        "echo_suggestion" -> "echoSuggestion"
        else -> rawType
    }

    private fun firstString(userInfo: Map<String, Any?>, keys: List<String>): String? {
        for (key in keys) {
            val value = (userInfo[key] as? String)?.trim()
            if (!value.isNullOrEmpty()) return value
        }
        return null
    }
}
