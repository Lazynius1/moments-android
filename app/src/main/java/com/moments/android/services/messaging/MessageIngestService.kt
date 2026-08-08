package com.moments.android.services.messaging

import com.google.firebase.auth.FirebaseAuth
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageSyncCursor
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.views.messaging.services.ChatService
import com.moments.android.views.messaging.services.ChatSessionEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/** Port de `MessageIngestSource` en MessageIngestService.swift. */
enum class MessageIngestSource(val raw: String) {
    PUSH("push"),
    NOTIFICATION_EXTENSION("notificationExtension"),
    CATCH_UP("catchUp"),
    MANUAL("manual"),
}

/** Port de `MessageIngestService.swift`. */
object MessageIngestService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlightKeys = ConcurrentHashMap.newKeySet<String>()
    private val recentlyIngestedKeys = ConcurrentHashMap.newKeySet<String>()

    fun resetOnSignOut() {
        inFlightKeys.clear()
        recentlyIngestedKeys.clear()
        MessageIngestQueue.clear()
        MessageSyncCursorStore.clearAll()
        LocalPersistenceService.clearAllChatCache()
    }

    /**
     * Purga el caché local tras restaurar la identidad de chat.
     *
     * El descifrado ocurre al ingerir y, cuando falla, el contenido se guarda tal cual en cifrado
     * (`decryptChatMessage(...) ?: content`). Los mensajes que entraron por push o catch-up
     * mientras la identidad no estaba disponible quedaron persistidos ilegibles: sin tirar el
     * caché seguirían mostrándose así aunque ya haya clave buena. Al vaciarlo se vuelven a bajar
     * de Firestore y se descifran con la identidad restaurada.
     *
     * También invalida [ChatSessionEngine]: el preload del arranque puede haber materializado
     * ViewModels con esos mensajes cifrados en memoria; sin esto el usuario abre el chat y sigue
     * viendo ciphertext hasta reiniciar la app.
     */
    fun resetAfterIdentityRestore() {
        inFlightKeys.clear()
        recentlyIngestedKeys.clear()
        MessageSyncCursorStore.clearAll()
        LocalPersistenceService.clearAllChatCache()
        ChatSessionEngine.invalidateAll()
    }

    suspend fun drainPendingQueue() {
        if (!LocalFirstMessagingSettings.isEnabled) return
        if (FirebaseAuth.getInstance().currentUser == null) return

        val pending = MessageIngestQueue.drainAll()
        if (pending.isEmpty()) return

        val processed = mutableListOf<PendingMessageIngest>()
        for (item in pending) {
            val didIngest = ingest(
                item.conversationId,
                item.messageId,
                MessageIngestSource.NOTIFICATION_EXTENSION,
            )
            if (didIngest) processed.add(item)
        }

        if (processed.size != pending.size) {
            val processedKeys = processed.map { "${it.conversationId}:${it.messageId}" }.toSet()
            pending.filter { "${it.conversationId}:${it.messageId}" !in processedKeys }.forEach {
                MessageIngestQueue.enqueue(it.conversationId, it.messageId)
            }
        }
    }

    suspend fun ingest(userInfo: Map<String, Any?>): Boolean {
        if (!LocalFirstMessagingSettings.isEnabled) return false

        val type = (userInfo["type"] as? String)?.trim()?.lowercase()
        if (type != "message" && type != "new_message") return false

        val conversationId = userInfo["conversationId"] as? String ?: return false
        val messageId = userInfo["messageId"] as? String ?: return false

        return ingest(conversationId, messageId, MessageIngestSource.PUSH)
    }

    suspend fun ingestBatch(
        messages: List<EnhancedMessage>,
        conversationId: String,
        source: MessageIngestSource,
    ): Int {
        if (!LocalFirstMessagingSettings.isEnabled) return 0
        if (messages.isEmpty()) return 0

        val sorted = messages.sortedWith(
            compareBy<EnhancedMessage> { it.timestamp }.thenBy { it.id },
        )
        LocalPersistenceService.saveMessagesInBackground(sorted, conversationId, sync = false)

        latestSyncCursor(sorted)?.let { latestCursor ->
            val stored = MessageSyncCursorStore.cursor(conversationId)
            val next = if (stored != null) {
                if (latestCursor.isAfter(stored)) latestCursor else stored
            } else {
                latestCursor
            }
            MessageSyncCursorStore.updateCursor(conversationId, next)
            sorted.lastOrNull()?.let { LocalPersistenceService.upsertConversationPreview(it) }
        }

        for (message in sorted) {
            rememberIngestedKey(dedupKey(conversationId, message.id))
        }

        // Doble check fiable: delivered se marca al ingerir por cualquier canal,
        // no solo cuando el sistema entrega el push.
        FirebaseAuth.getInstance().currentUser?.uid?.let { currentUserId ->
            ChatService.markMessagesAsDelivered(sorted, conversationId, currentUserId)
        }

        ChatMediaPrefetcher.prefetchIfNeeded(sorted)

        MessagingEvents.emitMessagesIngested(
            MessagesIngestedEvent(
                conversationId = conversationId,
                messageIds = sorted.map { it.id },
                source = source.raw,
            ),
        )

        return sorted.size
    }

    suspend fun ingest(
        conversationId: String,
        messageId: String,
        source: MessageIngestSource,
    ): Boolean {
        if (!LocalFirstMessagingSettings.isEnabled) return false
        if (FirebaseAuth.getInstance().currentUser == null) return false

        val conv = conversationId.trim()
        val msg = messageId.trim()
        if (conv.isEmpty() || msg.isEmpty()) return false

        val key = dedupKey(conv, msg)
        if (key in recentlyIngestedKeys) return true
        // add() false ⇒ ya in-flight (≡ iOS `inFlightKeys.contains` → false)
        if (!inFlightKeys.add(key)) return false

        try {
            if (LocalPersistenceService.messageExistsInBackground(conv, msg)) {
                rememberIngestedKey(key)
                return true
            }

            val message = ChatService.fetchMessage(conv, msg).getOrNull() ?: return false

            LocalPersistenceService.saveMessagesInBackground(listOf(message), conv, sync = false)
            LocalPersistenceService.upsertConversationPreview(message)
            ChatMediaPrefetcher.prefetchIfNeeded(listOf(message))

            FirebaseAuth.getInstance().currentUser?.uid?.let { currentUserId ->
                ChatService.markMessagesAsDelivered(listOf(message), conv, currentUserId)
            }

            // El cursor NO avanza aquí: FCM/APNs colapsan pushes, y saltar hasta este
            // mensaje dejaría fuera a los intermedios. El catch-up pagina contiguo.
            // Fire-and-forget ≡ `Task { await MessageCatchUpService.shared.sync(...) }` en iOS.
            scope.launch { MessageCatchUpService.sync(conv) }

            rememberIngestedKey(key)

            ChatCommunicationNotificationService.donateFromPush(
                mapOf(
                    "type" to "new_message",
                    "conversationId" to conv,
                    "messageId" to msg,
                    "senderId" to message.senderId,
                ),
                message.content,
            )

            MessagingEvents.emitMessagesIngested(
                MessagesIngestedEvent(conv, listOf(msg), source.raw),
            )

            return true
        } finally {
            inFlightKeys.remove(key)
        }
    }

    private fun dedupKey(conversationId: String, messageId: String) = "$conversationId:$messageId"

    /**
     * El set de dedup no puede crecer sin límite en sesiones largas; al superar el
     * tope se vacía y el dedup cae al check de existencia en Room (barato).
     */
    private fun rememberIngestedKey(key: String) {
        if (recentlyIngestedKeys.size > 4000) {
            recentlyIngestedKeys.clear()
        }
        recentlyIngestedKeys.add(key)
    }

    private fun latestSyncCursor(messages: List<EnhancedMessage>): MessageSyncCursor? {
        var latest: MessageSyncCursor? = null
        for (message in messages) {
            val candidate = MessageSyncCursor(message.timestamp, message.id)
            latest = when {
                latest == null -> candidate
                candidate.isAfter(latest) -> candidate
                else -> latest
            }
        }
        return latest
    }
}
