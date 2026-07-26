package com.moments.android.views.messaging.services

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.services.messaging.EncryptionService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Date

/**
 * Port de `ChatService+EphemeralCleanup.swift` + `EphemeralCleanupManager`.
 *
 * Texto de expiración: literal iOS (`📸 Momento efímero expirado`) — se cifra y debe
 * coincidir entre plataformas.
 */
suspend fun ChatService.markEphemeralAsViewed(conversationId: String, messageId: String): Result<Unit> =
    runCatching {
        firestore.collection("conversations")
            .document(conversationId)
            .collection("messages")
            .document(messageId)
            .update("isViewed", true)
            .await()
    }

/** ≡ `startEphemeralCleanupTimer()` — primer run a +30s, luego cada 3600s. */
fun ChatService.startEphemeralCleanupTimer() {
    EphemeralCleanupManager.startCleanupSystem()
}

suspend fun ChatService.cleanupExpiredEphemeralMessages() {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val now = Date()
    val documents = runCatching {
        firestore.collectionGroup("messages")
            .whereEqualTo("senderId", currentUserId)
            .whereEqualTo("type", MessageType.EPHEMERAL.raw)
            .whereLessThan("expirationDate", now)
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
            .documents
    }.getOrElse { return }

    for (document in documents) {
        val data = document.data.orEmpty()
        val mediaResources = listOf(
            data["mediaObjectPath"] as? String,
            data["thumbnailObjectPath"] as? String,
            data["mediaUrl"] as? String,
            data["thumbnailUrl"] as? String,
        ).mapNotNull { value -> value?.takeIf { it.isNotEmpty() } }
        val conversationId = data["conversationId"] as? String ?: ""
        val messageId = data["id"] as? String ?: document.id
        cleanupSingleEphemeralMessage(conversationId, messageId, mediaResources)
    }
}

/**
 * ≡ `forceCleanupExpiredEphemeralMessages` — lanza cleanup y tras 2s reporta 0
 * (iOS no cuenta resultados reales).
 */
suspend fun ChatService.forceCleanupExpiredEphemeralMessages(): Int {
    cleanupExpiredEphemeralMessages()
    delay(2_000)
    return 0
}

private suspend fun ChatService.cleanupSingleEphemeralMessage(
    conversationId: String,
    messageId: String,
    mediaResources: List<String>,
): Boolean {
    if (conversationId.isEmpty() || messageId.isEmpty()) return false
    // Literal iOS — no localizar (contenido cifrado compartido).
    val expiredText = "📸 Momento efímero expirado"
    val encryptedExpiredText = runCatching {
        EncryptionService.encryptChatMessage(expiredText, conversationId)
    }.getOrElse {
        // Sin clave utilizable se pospone; el scheduler reintenta.
        return false
    }

    val messageRef = firestore.collection("conversations")
        .document(conversationId)
        .collection("messages")
        .document(messageId)

    val committed = runCatching {
        firestore.batch().apply {
            update(
                messageRef,
                mapOf(
                    "mediaUrl" to FieldValue.delete(),
                    "thumbnailUrl" to FieldValue.delete(),
                    "mediaObjectPath" to FieldValue.delete(),
                    "thumbnailObjectPath" to FieldValue.delete(),
                    "mediaEncryption" to FieldValue.delete(),
                    "thumbnailEncryption" to FieldValue.delete(),
                    "textOverlayLive" to FieldValue.delete(),
                    "textOverlays" to FieldValue.delete(),
                    "stickers" to FieldValue.delete(),
                    "drawingData" to FieldValue.delete(),
                    "content" to encryptedExpiredText,
                    "isDeleted" to true,
                    "deletedAt" to FieldValue.serverTimestamp(),
                ),
            )
        }.commit().await()
    }.isSuccess

    if (!committed) return false
    if (mediaResources.isNotEmpty()) {
        ChatService.deleteMediaFiles(mediaResources)
    }
    return true
}

/** Port de `EphemeralCleanupManager` (MomentsApp @StateObject). */
object EphemeralCleanupManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var started = false

    fun startCleanupSystem() {
        if (started) return
        started = true
        scope.launch(Dispatchers.IO) {
            // Diferir primer cleanup 30s (no competir con arranque en frío).
            delay(30_000)
            ChatService.cleanupExpiredEphemeralMessages()
            while (isActive) {
                delay(3_600_000)
                ChatService.cleanupExpiredEphemeralMessages()
            }
        }
    }

    fun cleanupNow() {
        scope.launch(Dispatchers.IO) {
            ChatService.forceCleanupExpiredEphemeralMessages()
        }
    }
}
