package com.moments.android.views.messaging.services

import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.moments.android.models.ChatMediaPurpose
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.EncryptedChatMediaMetadata
import com.moments.android.services.storage.StoragePathBuilder
import kotlinx.coroutines.tasks.await
import java.io.File

import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.ChatMediaDownloadPolicy
import com.moments.android.services.messaging.ChatMediaChunkedCipher
import com.moments.android.services.messaging.EncryptionService

data class CachedResolvedMedia(
    val mediaUrl: String?,
    val thumbnailUrl: String?,
)

/** Port de ChatService+EncryptedMediaResolver.swift. */
object ChatEncryptedMediaResolver {

    private val resolvedMediaCache = mutableMapOf<String, CachedResolvedMedia>()
    private val resolvedThumbnailCache = mutableMapOf<String, String>()
    private val outgoingPreviews = mutableMapOf<String, CachedResolvedMedia>()
    private val activeUploadMessageIds = mutableSetOf<String>()

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

    fun warmMessageURLsFromDiskCache(message: EnhancedMessage): CachedResolvedMedia {
        val warmed = ChatCacheStore.localURLsIfPresent(message)
        if (warmed.first != null) {
            resolvedMediaCache[message.id] = CachedResolvedMedia(warmed.first, warmed.second)
        }
        warmed.second?.let { resolvedThumbnailCache[message.id] = it }
        return CachedResolvedMedia(warmed.first, warmed.second)
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

        if (activeUploadMessageIds.contains(messageId)) {
            return CachedResolvedMedia(null, null)
        }

        val mainUrl = resolveEncryptedMediaURL(
            objectPath = mediaObjectPath,
            metadata = mediaEncryption,
            conversationId = conversationId,
            messageId = messageId,
            forceDownload = forceDownload,
        )
        val thumbUrl = resolveEncryptedThumbnailURL(
            objectPath = thumbnailObjectPath,
            metadata = thumbnailEncryption,
            conversationId = conversationId,
            messageId = messageId,
            forceDownload = forceDownload,
        )
        val resolved = CachedResolvedMedia(mainUrl, thumbUrl)
        if (resolved.mediaUrl != null || resolved.thumbnailUrl != null) {
            resolvedMediaCache[messageId] = resolved
        }
        return resolved
    }

    private suspend fun resolveEncryptedThumbnailURL(
        objectPath: String?,
        metadata: EncryptedChatMediaMetadata?,
        conversationId: String,
        messageId: String,
        forceDownload: Boolean,
    ): String? {
        if (objectPath.isNullOrEmpty() || metadata == null) return null
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
            ChatMediaPurpose.THUMBNAIL -> ChatMediaDownloadPolicy.shouldDownloadThumbnailPreview(forceDownload)
            ChatMediaPurpose.PRIMARY -> ChatMediaDownloadPolicy.shouldDownloadAutomatically(forceDownload)
        }
        if (!shouldDownload) return null

        return runCatching {
            ChatCacheStore.ensureDirectories()
            val maxSize = maxOf(metadata.plaintextSize + 256L * 1024L, 8L * 1024L * 1024L)
            val encryptedFile = downloadEncryptedBlobToFile(objectPath, maxSize)
            try {
                if (metadata.version == ChatMediaChunkedCipher.METADATA_VERSION &&
                    metadata.algorithm == ChatMediaChunkedCipher.ALGORITHM
                ) {
                    EncryptionService.decryptChatMediaFile(
                        inputFile = encryptedFile,
                        outputFile = cacheFile,
                        conversationId = conversationId,
                        metadata = metadata,
                    )
                } else {
                    val encryptedData = encryptedFile.readBytes()
                    val decrypted = EncryptionService.decryptChatMedia(
                        encryptedData = encryptedData,
                        metadata = metadata,
                        conversationId = conversationId,
                        messageId = messageId,
                    )
                    cacheFile.writeBytes(decrypted)
                }
                ChatCacheStore.enforceQuota()
                Uri.fromFile(cacheFile).toString()
            } finally {
                encryptedFile.delete()
            }
        }.getOrNull()
    }

    private suspend fun downloadEncryptedBlobToFile(objectPath: String, maxSize: Long): File {
        val path = StoragePathBuilder.extractObjectPath(objectPath)
        val temp = File.createTempFile("chat-enc-", ".bin")
        FirebaseStorage.getInstance().reference.child(path).getFile(temp).await()
        if (temp.length() > maxSize) {
            temp.delete()
            error("Encrypted blob exceeds max size")
        }
        return temp
    }

    private fun cachedMediaFileExists(urlString: String?): Boolean {
        if (urlString.isNullOrEmpty()) return false
        if (!urlString.startsWith("file://")) return true
        val path = Uri.parse(urlString).path ?: return false
        return File(path).exists()
    }
}
