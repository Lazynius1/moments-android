package com.moments.android.views.messaging.services

import android.graphics.Bitmap
import android.net.Uri
import com.moments.android.extensions.extractVideoThumbnailFromFile
import com.moments.android.models.ChatMediaPurpose
import com.moments.android.models.EncryptedChatMediaMetadata
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.storage.MediaUploadService
import com.moments.android.services.storage.StoragePathBuilder
import com.moments.android.services.storage.StorageUploadDomain
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class ChatMediaUploadProgress(val messageId: String, val progress: Double)

/** Equivalente Android de las notificaciones `MediaUploadProgress` de iOS. */
object ChatMediaUploadProgressEvents {
    private val mutableEvents = MutableSharedFlow<ChatMediaUploadProgress>(extraBufferCapacity = 64)
    val events: SharedFlow<ChatMediaUploadProgress> = mutableEvents.asSharedFlow()

    fun emit(messageId: String, progress: Double) {
        mutableEvents.tryEmit(ChatMediaUploadProgress(messageId, progress))
    }
}

/** Port de `ChatService+ChunkedVideoUpload.swift`. */
internal suspend fun ChatService.uploadChunkedEncryptedVideo(
    preparedFile: File,
    senderId: String,
    conversationId: String,
    messageId: String,
    mediaFileId: String,
    fileExtension: String,
    contentType: String,
): ChatMediaUploadResult {
    val encryptedMain = EncryptionService.encryptChatMediaFile(
        inputFile = preparedFile,
        conversationId = conversationId,
        messageId = messageId,
        purpose = ChatMediaPurpose.PRIMARY,
        contentType = contentType,
        fileExtension = fileExtension,
    )
    try {
        val cachedMainFile = ChatCacheStore.copyDecryptedMedia(
            preparedFile,
            conversationId,
            messageId,
            encryptedMain.metadata.purpose,
            encryptedMain.metadata.fileExtension,
        )
        val localPreviewUrl = Uri.fromFile(cachedMainFile).toString()
        val encryptedMainTarget = ChatServiceMediaPipeline.chatEncryptedStorageTarget(
            userId = senderId,
            conversationId = conversationId,
            messageId = messageId,
            fileId = mediaFileId,
            originalContentType = contentType,
        )

        encryptedMediaResolver.stageOutgoingPreview(
            CachedResolvedMedia(mediaUrl = localPreviewUrl, thumbnailUrl = null),
            messageId,
        )
        encryptedMediaResolver.markUploadStarted(messageId)
        try {
            MediaUploadService.uploadEncryptedFile(
                target = encryptedMainTarget,
                fileUri = Uri.fromFile(encryptedMain.ciphertextFile),
                progress = { ChatMediaUploadProgressEvents.emit(messageId, it) },
            )
        } finally {
            encryptedMediaResolver.markUploadFinished(messageId)
        }

        var thumbnailObjectPath: String? = null
        var thumbnailEncryption: EncryptedChatMediaMetadata? = null
        var localThumbnailUrl: String? = null
        val thumbnailData = extractVideoThumbnailFromFile(preparedFile)?.let { bitmap ->
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 78, stream)
                stream.toByteArray()
            }
        }
        if (thumbnailData != null) {
            runCatching {
                val thumbId = UUID.randomUUID().toString()
                val thumbBase = StoragePathBuilder.build(
                    senderId,
                    StorageUploadDomain.ChatThumbnail(
                        conversationId = conversationId,
                        messageId = messageId,
                        thumbId = thumbId,
                    ),
                )
                val encryptedThumb = EncryptionService.encryptChatMedia(
                    data = thumbnailData,
                    conversationId = conversationId,
                    messageId = messageId,
                    purpose = ChatMediaPurpose.THUMBNAIL,
                    contentType = "image/jpeg",
                    fileExtension = "jpg",
                )
                val encryptedThumbTarget = ChatServiceMediaPipeline.chatEncryptedStorageTarget(
                    userId = senderId,
                    conversationId = conversationId,
                    messageId = messageId,
                    fileId = thumbId,
                    originalContentType = "image/jpeg",
                    objectPath = thumbBase.objectPath.replace(".jpg", ".enc"),
                )
                MediaUploadService.uploadEncryptedBlob(encryptedThumbTarget, encryptedThumb.ciphertext)
                thumbnailObjectPath = encryptedThumbTarget.objectPath
                thumbnailEncryption = encryptedThumb.metadata
                val cachedThumb = ChatCacheStore.writeDecryptedMedia(
                    thumbnailData,
                    conversationId,
                    messageId,
                    encryptedThumb.metadata.purpose,
                    encryptedThumb.metadata.fileExtension,
                )
                localThumbnailUrl = Uri.fromFile(cachedThumb).toString()
            }
        }

        val preview = CachedResolvedMedia(localPreviewUrl, localThumbnailUrl)
        encryptedMediaResolver.stageOutgoingPreview(preview, messageId)
        encryptedMediaResolver.cacheResolvedPreview(preview, messageId)
        return ChatMediaUploadResult(
            mediaUrl = localPreviewUrl,
            thumbnailUrl = localThumbnailUrl,
            mediaObjectPath = encryptedMainTarget.objectPath,
            thumbnailObjectPath = thumbnailObjectPath,
            mediaEncryption = encryptedMain.metadata,
            thumbnailEncryption = thumbnailEncryption,
        )
    } finally {
        encryptedMain.ciphertextFile.delete()
    }
}
