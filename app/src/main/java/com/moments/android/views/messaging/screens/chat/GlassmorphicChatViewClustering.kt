package com.moments.android.views.messaging.screens.chat

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.Moment
import com.moments.android.models.Story
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.views.feed.sharing.SharedStoryAccessDenialReason
import com.moments.android.views.feed.sharing.SharedStoryAccessEvaluator
import com.moments.android.views.feed.sharing.SharedStoryAccessOutcome
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageItem
import com.moments.android.views.messaging.components.ClusterMessageGrouper
import com.moments.android.views.messaging.components.ClusterMessageItem
import com.moments.android.views.messaging.services.ChatBuzzEvent
import com.moments.android.views.messaging.services.ChatBuzzProcessedStore
import java.util.Date

/** Lógica pura de `GlassmorphicChatView+Clustering.swift`; el host Compose inyecta scroll/highlight. */
class GlassmorphicChatViewClustering(
    private val messageRowId: (String) -> String?,
    private val scrollToRow: (id: String, animated: Boolean) -> Unit,
    private val highlight: (Set<String>) -> Unit,
    private val currentUserId: () -> String,
    private val buzzEvents: () -> List<ChatBuzzEvent>,
    private val pendingReplayBuzz: () -> ChatBuzzEvent?,
    private val reduceMotion: () -> Boolean,
) {
    fun clusterMessages(input: List<EnhancedMessage>): List<MessageItem> = ClusterMessageGrouper.group(input).map { item ->
        when (item) {
            is ClusterMessageItem.Single -> MessageItem.Single(item.message)
            is ClusterMessageItem.MediaCluster -> MessageItem.MediaCluster(item.messages)
        }
    }

    fun jumpToMessage(messageId: String) {
        scrollToRow(messageRowId(messageId) ?: messageId, !reduceMotion())
        highlight(setOf(messageId))
    }

    fun highlightMessages(messageIds: Set<String>, scroll: Boolean = false) {
        if (messageIds.isEmpty()) return
        if (scroll) messageIds.firstOrNull()?.let { scrollToRow(messageRowId(it) ?: it, !reduceMotion()) }
        highlight(messageIds)
    }

    fun resolvePendingBuzzEventForReplay(buzzEventId: String?, playBuzzOnOpen: Boolean): ChatBuzzEvent? {
        if (playBuzzOnOpen) {
            resolvePendingBuzzEvent(buzzEventId)?.let { return it }
            if (buzzEventId != null) return null
        }
        return pendingReplayBuzz()
    }

    /** ≡ iOS `resolvePendingBuzzEvent(for:)`. */
    fun resolvePendingBuzzEvent(buzzEventId: String?): ChatBuzzEvent? {
        if (!buzzEventId.isNullOrEmpty()) {
            return buzzEvents().firstOrNull { it.id == buzzEventId }
        }
        val cutoff = Date(System.currentTimeMillis() - ChatBuzzProcessedStore.replayWindowMillis)
        return buzzEvents()
            .filter { it.senderId != currentUserId() && it.createdAt >= cutoff }
            .maxByOrNull { it.createdAt }
    }
}

/** Resultado de `handleMomentNavigationFromChat`. */
sealed class ChatMomentNavigationResult {
    data class Open(val moment: Moment) : ChatMomentNavigationResult()
    data object Failed : ChatMomentNavigationResult()
    data object Ignored : ChatMomentNavigationResult()
}

/** Resultado de `handleStoryNavigationFromChat`. */
sealed class ChatStoryNavigationResult {
    data class Open(val story: Story) : ChatStoryNavigationResult()
    data class Unavailable(val reason: SharedStoryAccessDenialReason) : ChatStoryNavigationResult()
    data object Ignored : ChatStoryNavigationResult()
}

/**
 * ≡ iOS `handleMomentNavigationFromChat` / `handleStoryNavigationFromChat`
 * (`GlassmorphicChatView+Clustering.swift`).
 */
object GlassmorphicChatSharedContentNavigation {
    private val firestore = FirestoreService()

    suspend fun handleMomentNavigationFromChat(message: EnhancedMessage): ChatMomentNavigationResult {
        val data = message.sharedMomentData ?: return ChatMomentNavigationResult.Ignored
        val momentId = data["momentId"]?.takeIf { it.isNotBlank() } ?: return ChatMomentNavigationResult.Ignored
        val authorId = data["momentAuthorId"]?.takeIf { it.isNotBlank() } ?: message.senderId
        return runCatching {
            val moment = firestore.fetchMoment(momentId, authorId)
            ChatMomentNavigationResult.Open(
                if (moment.id.isNullOrBlank()) moment.copy(id = momentId) else moment,
            )
        }.getOrElse { ChatMomentNavigationResult.Failed }
    }

    suspend fun handleStoryNavigationFromChat(message: EnhancedMessage): ChatStoryNavigationResult {
        val data = message.sharedStoryData ?: return ChatStoryNavigationResult.Ignored
        val storyId = data["storyId"]?.takeIf { it.isNotBlank() } ?: return ChatStoryNavigationResult.Ignored
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return ChatStoryNavigationResult.Ignored
        val authorId = data["storyAuthorId"]?.takeIf { it.isNotBlank() } ?: message.senderId
        if (authorId.isBlank()) {
            return ChatStoryNavigationResult.Unavailable(SharedStoryAccessDenialReason.Restricted)
        }
        val payloadExpiration = data["storyExpiration"]?.toDoubleOrNull()
        return when (
            val outcome = SharedStoryAccessEvaluator.evaluate(
                authorId = authorId,
                storyId = storyId,
                payloadExpirationSeconds = payloadExpiration,
                viewerId = viewerId,
            )
        ) {
            is SharedStoryAccessOutcome.Allowed -> ChatStoryNavigationResult.Open(outcome.story)
            is SharedStoryAccessOutcome.Denied -> ChatStoryNavigationResult.Unavailable(outcome.reason)
        }
    }
}
