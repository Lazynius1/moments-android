package com.moments.android.views.messaging.services

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.Query
import com.moments.android.services.messaging.GroupChatScope
import com.moments.android.services.messaging.applyingHistoryCutoff
import com.moments.android.services.messaging.messagingMessages
import com.moments.android.services.messaging.messagingThread
import com.moments.android.views.messaging.core.Conversation
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID

/** Ver una vez reproducido desde la lista de chats, sin entrar en la conversación (≡ iOS). */
data class InboxViewOncePresentation(
    val conversation: Conversation,
    val message: EnhancedMessage,
    val authorName: String,
    val id: String = UUID.randomUUID().toString(),
)

/** Preparación en curso del botón de la fila (spinner + bloqueo de dobles pulsaciones). */
data class InboxViewOncePreparation(
    val conversationId: String,
    val token: String = UUID.randomUUID().toString(),
)

/**
 * Port de `InboxViewOncePlayback.swift`: localiza, descifra y resuelve el ver una vez pendiente
 * con los mismos servicios del chat y replica los efectos del visor del chat (visto, replay, respuestas).
 * No abre la sesión del chat: no marca la conversación como leída ni toca otros mensajes.
 */
object InboxViewOncePlayback {
    private const val TAG = "InboxViewOnce"

    /** Tope de espera antes de abrir el chat como alternativa (descarga colgada, sin red…). */
    const val PREPARATION_TIMEOUT_MS = 20_000L

    /** Efectos de cierre que deben terminar aunque la lista desaparezca. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Último ver una vez recibido sin abrir, con la media ya disponible en local. null si no hay o falla. */
    suspend fun prepare(conversationId: String): EnhancedMessage? {
        if (conversationId.isEmpty()) return null
        val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val service = ChatService

        val documents = runCatching {
            service.preloadEncryption(conversationId)
            service.firestore.messagingThread(conversationId)
                .messagingMessages
                .applyingHistoryCutoff(service.resolvedHistoryCutoff(conversationId))
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .limit(10)
                .get()
                .await()
                .documents
        }.getOrElse { error ->
            Log.e(TAG, "fetch failed conv=$conversationId", error)
            return null
        }

        val document = documents.firstOrNull { doc ->
            isPendingViewOnce(doc.data.orEmpty(), conversationId, currentUserId)
        } ?: return null

        var message = runCatching {
            service.buildEnhancedMessage(document.data.orEmpty(), document.id, conversationId)
        }.getOrElse { error ->
            Log.e(TAG, "build failed msg=${document.id}", error)
            return null
        }
        if (!message.isViewOnce || message.isDeleted || message.isUndecryptable) return null

        // Media cifrada: misma resolución (descarga + descifrado + caché en disco) que la burbuja.
        if (!message.mediaObjectPath.isNullOrEmpty() && message.mediaEncryption != null) {
            val resolved = runCatching { ChatEncryptedMediaResolver.resolveForMessage(message) }
                .getOrElse { error ->
                    Log.e(TAG, "media resolve failed msg=${message.id}", error)
                    null
                }
            val mediaUrl = resolved?.mediaUrl?.takeIf { it.isNotEmpty() } ?: return null
            message = message.copy(
                mediaUrl = mediaUrl,
                thumbnailUrl = resolved.thumbnailUrl ?: message.thumbnailUrl,
            )
        }

        if (message.mediaUrl.isNullOrEmpty()) return null
        return message
    }

    /** Mismo criterio que la preview del inbox: de otro remitente y sin mi uid en `viewedBy`. */
    private fun isPendingViewOnce(raw: Map<String, Any?>, conversationId: String, currentUserId: String): Boolean {
        val data = GroupChatScope.recipientMetadata(raw, conversationId)
        if (data["isDeleted"] == true) return false
        if (MessageType.from(data["type"] as? String).isViewOnce.not()) return false
        val senderId = data["senderId"] as? String ?: return false
        if (senderId == currentUserId) return false
        val vanishedFor = (data["vanishedFor"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        if (currentUserId in vanishedFor) return false
        val viewedBy = (data["viewedBy"] as? List<*>)?.filterIsInstance<String>().orEmpty()
        return currentUserId !in viewedBy
    }

    /** ≡ `handleViewOnceViewerViewed` del chat. */
    fun markViewed(message: EnhancedMessage) {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        if (message.allowReplay == true) {
            ViewOnceReplaySessionStore.markAvailable(message, viewerId)
        }
        scope.launch {
            ChatService.markViewOnceAsViewed(message.conversationId, message.id, viewerId)
                .onFailure { Log.e(TAG, "markViewOnceAsViewed failed msg=${message.id}", it) }
        }
    }

    /**
     * Al cerrar el visor la repetición sigue disponible: se puede repetir al entrar en el chat, que la
     * descarta al salir. Si nunca se entra, se descarta al pasar la app a segundo plano (≡ iOS).
     */
    fun finishSession(@Suppress("UNUSED_PARAMETER") message: EnhancedMessage) {
        installBackgroundObserverIfNeeded()
    }

    @Volatile private var backgroundObserverInstalled = false

    private fun installBackgroundObserverIfNeeded() {
        if (backgroundObserverInstalled) return
        backgroundObserverInstalled = true
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            androidx.lifecycle.ProcessLifecycleOwner.get().lifecycle.addObserver(
                object : androidx.lifecycle.DefaultLifecycleObserver {
                    override fun onStop(owner: androidx.lifecycle.LifecycleOwner) {
                        abandonPendingReplaysOutsideActiveChat()
                    }
                },
            )
        }
    }

    /** Descarta las repeticiones abiertas desde la lista que no se usaron, salvo la del chat abierto. */
    private fun abandonPendingReplaysOutsideActiveChat() {
        val viewerId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        ViewOnceReplaySessionStore.drainAvailableExcluding(ChatSessionEngine.activeConversationId).forEach { pending ->
            scope.launch {
                // Re-marcar visto (idempotente) antes de consumir: la CF exige mi uid en `viewedBy`.
                ChatService.markViewOnceAsViewed(pending.conversationId, pending.messageId, viewerId)
                ViewOnceConsumptionService.consumeAwait(
                    pending.conversationId,
                    pending.messageId,
                    ViewOnceConsumptionReason.ABANDON_REPLAY,
                )
            }
        }
    }

    /** Respuesta o reacción desde el visor, citando el ver una vez como en el chat. */
    fun sendReply(text: String, presentation: InboxViewOncePresentation) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val senderId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        scope.launch {
            ChatService.sendTextMessage(
                conversationId = presentation.message.conversationId,
                senderId = senderId,
                content = trimmed,
                replyTo = presentation.message.id,
                isVanishModeMessage = presentation.conversation.vanishModeActive == true,
            ).onFailure { Log.e(TAG, "reply failed msg=${presentation.message.id}", it) }
        }
    }
}
