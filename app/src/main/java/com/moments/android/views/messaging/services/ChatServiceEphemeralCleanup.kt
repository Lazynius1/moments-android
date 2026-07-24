package com.moments.android.views.messaging.services

import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.moments.android.MomentsApplication
import com.moments.android.R
import com.moments.android.models.MessageType
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.storage.MediaUploadService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.util.Date

/** Port de `ChatService+EphemeralCleanup.swift`. */
suspend fun ChatService.markEphemeralAsViewed(conversationId: String, messageId: String): Result<Unit> = runCatching {
    firestore.collection("conversations").document(conversationId).collection("messages").document(messageId)
        .update("isViewed", true)
        .await()
}

suspend fun ChatService.cleanupExpiredEphemeralMessages() {
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid ?: return
    val documents = runCatching {
        firestore.collectionGroup("messages")
            .whereEqualTo("senderId", currentUserId)
            .whereEqualTo("type", MessageType.EPHEMERAL.raw)
            .whereLessThan("expirationDate", Date())
            .whereEqualTo("isDeleted", false)
            .get()
            .await()
            .documents
    }.getOrElse { return }

    for (document in documents) {
        val data = document.data.orEmpty()
        val mediaResources = listOfNotNull(
            data["mediaObjectPath"] as? String,
            data["thumbnailObjectPath"] as? String,
            data["mediaUrl"] as? String,
            data["thumbnailUrl"] as? String,
        ).filter(String::isNotBlank)
        val conversationId = data["conversationId"] as? String ?: ""
        val messageId = data["id"] as? String ?: document.id
        cleanupSingleEphemeralMessage(conversationId, messageId, mediaResources)
    }
}

suspend fun ChatService.forceCleanupExpiredEphemeralMessages(): Int {
    cleanupExpiredEphemeralMessages()
    delay(2_000)
    return 0
}

private suspend fun ChatService.cleanupSingleEphemeralMessage(
    conversationId: String,
    messageId: String,
    mediaResources: List<String>,
) {
    if (conversationId.isBlank() || messageId.isBlank()) return
    val expiredText = MomentsApplication.instance?.getString(R.string.chat_ephemeral_expired_content) ?: return
    val encryptedExpiredText = runCatching {
        EncryptionService.encryptChatMessage(expiredText, conversationId)
    }.getOrElse { return }
    val messageRef = firestore.collection("conversations").document(conversationId)
        .collection("messages").document(messageId)

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
    if (committed) for (resource in mediaResources) deleteEphemeralMediaResource(resource)
}

private suspend fun deleteEphemeralMediaResource(resource: String) {
    runCatching {
        val uri = Uri.parse(resource)
        if (uri.scheme == "file") File(requireNotNull(uri.path)).delete()
        else MediaUploadService.delete(resource)
    }
}

object EphemeralCleanupManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var started = false

    fun startCleanupSystem() {
        if (started) return
        started = true
        scope.launch {
            delay(30_000)
            ChatService.cleanupExpiredEphemeralMessages()
            while (isActive) {
                delay(3_600_000)
                ChatService.cleanupExpiredEphemeralMessages()
            }
        }
    }

    fun cleanupNow() {
        scope.launch { ChatService.cleanupExpiredEphemeralMessages() }
    }
}
