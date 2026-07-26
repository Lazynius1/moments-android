package com.moments.android.views.messaging.services

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.moments.android.views.messaging.core.ChatMediaPurpose
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.EncryptedChatMediaMetadata
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.ChatMediaChunkedCipher
import com.moments.android.services.messaging.ChatMediaDownloadPolicy
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.storage.StoragePathBuilder
import java.io.File
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.max
import kotlin.math.min
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.suspendCancellableCoroutine

data class CachedResolvedMedia(
    val mediaUrl: String?,
    val thumbnailUrl: String?,
)

data class ChatMediaDownloadProgress(val messageId: String, val progress: Double)

/** ≡ NotificationCenter `MediaDownloadProgress`. */
object ChatMediaDownloadProgressEvents {
    private val mutableEvents = MutableSharedFlow<ChatMediaDownloadProgress>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatMediaDownloadProgress> = mutableEvents.asSharedFlow()

    fun emit(messageId: String, progress: Double) {
        mutableEvents.tryEmit(
            ChatMediaDownloadProgress(messageId, min(max(progress, 0.0), 1.0)),
        )
    }
}

/**
 * Port de `ChatService.EncryptedMediaResolver` (`ChatService+EncryptedMediaResolver.swift`).
 */
object ChatEncryptedMediaResolver {

    private val outgoingPreviews = mutableMapOf<String, CachedResolvedMedia>()
    private val activeUploadMessageIds = mutableSetOf<String>()
    private val resolvedMediaCache = mutableMapOf<String, CachedResolvedMedia>()
    private val resolvedThumbnailCache = mutableMapOf<String, String>()

    fun stageOutgoingPreview(preview: CachedResolvedMedia, messageId: String) {
        outgoingPreviews[messageId] = preview
    }

    fun markUploadStarted(messageId: String) {
        activeUploadMessageIds.add(messageId)
    }

    fun markUploadFinished(messageId: String) {
        activeUploadMessageIds.remove(messageId)
    }

    fun cacheResolvedPreview(preview: CachedResolvedMedia, messageId: String) {
        resolvedMediaCache[messageId] = preview
    }

    /** Si ya está en disco, URLs locales sin red. */
    fun warmMessageURLsFromDiskCache(message: EnhancedMessage): CachedResolvedMedia {
        val warmed = ChatCacheStore.localURLsIfPresent(message)
        if (warmed.first != null) {
            resolvedMediaCache[message.id] = CachedResolvedMedia(warmed.first, warmed.second)
        }
        warmed.second?.let { resolvedThumbnailCache[message.id] = it }
        return CachedResolvedMedia(warmed.first, warmed.second)
    }

    suspend fun resolveThumbnailURL(message: EnhancedMessage, forceDownload: Boolean = false): String? {
        message.thumbnailUrl?.takeIf { it.isNotEmpty() }?.let { return it }
        resolvedThumbnailCache[message.id]?.let { return it }
        val thumbObjectPath = message.thumbnailObjectPath?.takeIf { it.isNotEmpty() } ?: return null
        val thumbEncryption = message.thumbnailEncryption ?: return null
        val resolved = resolveEncryptedMediaURL(
            objectPath = thumbObjectPath,
            metadata = thumbEncryption,
            conversationId = message.conversationId,
            messageId = message.id,
            forceDownload = forceDownload,
        )
        if (resolved != null) resolvedThumbnailCache[message.id] = resolved
        return resolved
    }

    suspend fun resolveForMessage(
        message: EnhancedMessage,
        forceDownload: Boolean = false,
    ): CachedResolvedMedia? {
        val mediaObjectPath = message.mediaObjectPath?.takeIf { it.isNotEmpty() } ?: return null
        val mediaEncryption = message.mediaEncryption ?: return null
        return resolveForDisplay(
            messageId = message.id,
            conversationId = message.conversationId,
            mediaObjectPath = mediaObjectPath,
            mediaEncryption = mediaEncryption,
            thumbnailObjectPath = message.thumbnailObjectPath,
            thumbnailEncryption = message.thumbnailEncryption,
            forceDownload = forceDownload,
        )
    }

    suspend fun resolveForDisplay(
        messageId: String,
        conversationId: String,
        mediaObjectPath: String,
        mediaEncryption: EncryptedChatMediaMetadata,
        thumbnailObjectPath: String?,
        thumbnailEncryption: EncryptedChatMediaMetadata?,
        forceDownload: Boolean = false,
    ): CachedResolvedMedia {
        outgoingPreviews[messageId]?.let { return it }
        resolvedMediaCache[messageId]?.let { cached ->
            if (cachedMediaFileExists(cached.mediaUrl)) return cached
            resolvedMediaCache.remove(messageId)
        }

        val diskMain = ChatCacheStore.decryptedMediaFile(
            conversationId,
            messageId,
            mediaEncryption.purpose,
            mediaEncryption.fileExtension,
        )
        if (diskMain.exists()) {
            ChatCacheStore.touchAccessDate(diskMain)
            val resolved = CachedResolvedMedia(Uri.fromFile(diskMain).toString(), null)
            resolvedMediaCache[messageId] = resolved
            return resolved
        }

        if (messageId in activeUploadMessageIds) {
            return CachedResolvedMedia(null, null)
        }

        val resolved = resolveEncryptedMedia(
            conversationId = conversationId,
            messageId = messageId,
            mediaObjectPath = mediaObjectPath,
            mediaEncryption = mediaEncryption,
            thumbnailObjectPath = thumbnailObjectPath,
            thumbnailEncryption = thumbnailEncryption,
            forceDownload = forceDownload,
        )
        if (resolved.mediaUrl != null || resolved.thumbnailUrl != null) {
            resolvedMediaCache[messageId] = resolved
        }
        return resolved
    }

    private suspend fun resolveEncryptedMedia(
        conversationId: String,
        messageId: String,
        mediaObjectPath: String,
        mediaEncryption: EncryptedChatMediaMetadata,
        thumbnailObjectPath: String?,
        thumbnailEncryption: EncryptedChatMediaMetadata?,
        forceDownload: Boolean,
    ): CachedResolvedMedia = coroutineScope {
        // ≡ async let mainURL / thumbURL (paralelo).
        val mainDeferred = async {
            resolveEncryptedMediaURL(
                objectPath = mediaObjectPath,
                metadata = mediaEncryption,
                conversationId = conversationId,
                messageId = messageId,
                forceDownload = forceDownload,
            )
        }
        val thumbDeferred = async {
            resolveEncryptedThumbnailURL(
                objectPath = thumbnailObjectPath,
                metadata = thumbnailEncryption,
                conversationId = conversationId,
                messageId = messageId,
                forceDownload = forceDownload,
            )
        }
        CachedResolvedMedia(mainDeferred.await(), thumbDeferred.await())
    }

    private suspend fun resolveEncryptedThumbnailURL(
        objectPath: String?,
        metadata: EncryptedChatMediaMetadata?,
        conversationId: String,
        messageId: String,
        forceDownload: Boolean,
    ): String? {
        if (objectPath == null || metadata == null) return null
        return resolveEncryptedMediaURL(objectPath, metadata, conversationId, messageId, forceDownload)
    }

    private suspend fun resolveEncryptedMediaURL(
        objectPath: String,
        metadata: EncryptedChatMediaMetadata,
        conversationId: String,
        messageId: String,
        forceDownload: Boolean,
    ): String? {
        val cacheFile = ChatCacheStore.decryptedMediaFile(
            conversationId,
            messageId,
            metadata.purpose,
            metadata.fileExtension,
        )
        if (cacheFile.exists()) {
            ChatCacheStore.touchAccessDate(cacheFile)
            return Uri.fromFile(cacheFile).toString()
        }

        val shouldDownload = when (metadata.purpose) {
            ChatMediaPurpose.THUMBNAIL ->
                ChatMediaDownloadPolicy.shouldDownloadThumbnailPreview(forceDownload)
            ChatMediaPurpose.PRIMARY ->
                ChatMediaDownloadPolicy.shouldDownloadAutomatically(forceDownload)
        }
        if (!shouldDownload) return null

        return runCatching {
            val maxSize = max(metadata.plaintextSize + 256L * 1024L, 8L * 1024L * 1024L)
            val reportsProgress = metadata.purpose == ChatMediaPurpose.PRIMARY
            ChatCacheStore.ensureDirectories()

            if (metadata.version == ChatMediaChunkedCipher.METADATA_VERSION &&
                metadata.algorithm == ChatMediaChunkedCipher.ALGORITHM
            ) {
                val encryptedFile = downloadEncryptedBlobToFile(
                    objectPath = objectPath,
                    maxSize = maxSize,
                    messageId = messageId,
                    reportsProgress = reportsProgress,
                )
                try {
                    if (reportsProgress) postDownloadProgress(messageId, 0.88)
                    EncryptionService.decryptChatMediaFile(
                        inputFile = encryptedFile,
                        outputFile = cacheFile,
                        conversationId = conversationId,
                        metadata = metadata,
                        messageId = messageId,
                    )
                } finally {
                    encryptedFile.delete()
                }
            } else {
                val encryptedData = downloadEncryptedBlob(
                    objectPath = objectPath,
                    maxSize = maxSize,
                    messageId = messageId,
                    reportsProgress = reportsProgress,
                )
                if (reportsProgress) postDownloadProgress(messageId, 0.88)
                val decrypted = EncryptionService.decryptChatMedia(
                    encryptedData = encryptedData,
                    metadata = metadata,
                    conversationId = conversationId,
                    messageId = messageId,
                )
                cacheFile.writeBytes(decrypted)
            }
            if (reportsProgress) postDownloadProgress(messageId, 0.96)
            ChatCacheStore.enforceQuota()
            if (reportsProgress) postDownloadProgress(messageId, 1.0)
            Uri.fromFile(cacheFile).toString()
        }.getOrNull()
    }

    private fun postDownloadProgress(messageId: String, progress: Double) {
        ChatMediaDownloadProgressEvents.emit(messageId, progress)
    }

    /** `true` si file:// existe en disco; URLs remotas se consideran válidas. */
    private fun cachedMediaFileExists(urlString: String?): Boolean {
        if (urlString.isNullOrEmpty()) return false
        val uri = Uri.parse(urlString)
        if (uri.scheme != "file") return true
        val path = uri.path ?: return false
        return File(path).exists()
    }

    private suspend fun downloadEncryptedBlob(
        objectPath: String,
        maxSize: Long,
        messageId: String,
        reportsProgress: Boolean,
    ): ByteArray {
        val temp = downloadEncryptedBlobToFile(objectPath, maxSize, messageId, reportsProgress)
        return try {
            temp.readBytes()
        } finally {
            temp.delete()
        }
    }

    private suspend fun downloadEncryptedBlobToFile(
        objectPath: String,
        maxSize: Long,
        messageId: String,
        reportsProgress: Boolean,
    ): File {
        val path = StoragePathBuilder.extractObjectPath(objectPath)
        val temp = File.createTempFile("chat-enc-${UUID.randomUUID()}", ".bin")
        return suspendCancellableCoroutine { cont ->
            val task = FirebaseStorage.getInstance().reference.child(path).getFile(temp)
            if (reportsProgress) {
                task.addOnProgressListener { snapshot ->
                    val total = snapshot.totalByteCount
                    if (total > 0) {
                        val fraction = snapshot.bytesTransferred.toDouble() / total.toDouble()
                        postDownloadProgress(messageId, max(0.03, fraction * 0.85))
                    }
                }
            }
            task.addOnSuccessListener {
                if (!cont.isActive) {
                    temp.delete()
                    return@addOnSuccessListener
                }
                if (temp.length() > maxSize) {
                    temp.delete()
                    cont.resumeWithException(
                        IllegalStateException("El archivo cifrado supera el tamaño máximo permitido"),
                    )
                } else {
                    cont.resume(temp)
                }
            }
            task.addOnFailureListener { error ->
                temp.delete()
                if (cont.isActive) {
                    cont.resumeWithException(
                        error ?: IllegalStateException("No se pudieron descargar los datos cifrados"),
                    )
                }
            }
            cont.invokeOnCancellation {
                task.cancel()
                temp.delete()
            }
        }
    }
}
