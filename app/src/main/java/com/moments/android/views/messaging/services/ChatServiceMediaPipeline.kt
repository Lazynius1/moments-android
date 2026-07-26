package com.moments.android.views.messaging.services

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.moments.android.extensions.extractVideoThumbnailFromFile
import com.moments.android.views.messaging.core.ChatMediaPurpose
import com.moments.android.views.messaging.core.EncryptedChatMediaMetadata
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.storage.MediaUploadPayload
import com.moments.android.services.storage.MediaUploadService
import com.moments.android.services.storage.StoragePathBuilder
import com.moments.android.services.storage.StorageUploadDomain
import com.moments.android.services.storage.StorageUploadTarget
import com.moments.android.services.storage.VideoCompressionPreset
import com.moments.android.services.storage.VideoCompressionService
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.tasks.await

/**
 * Port de `ChatService+MediaPipeline.swift`.
 * `ChatMediaUploadResult` vive aquí (en iOS está en ChatService.swift).
 */
data class ChatMediaUploadResult(
    val mediaUrl: String?,
    val thumbnailUrl: String?,
    val mediaObjectPath: String?,
    val thumbnailObjectPath: String?,
    val mediaEncryption: EncryptedChatMediaMetadata?,
    val thumbnailEncryption: EncryptedChatMediaMetadata?,
)

object ChatServiceMediaPipeline {

    private val uploader get() = MediaUploadService

    fun fileExtensionFor(type: MessageType): String = when (type) {
        MessageType.IMAGE, MessageType.VIEW_ONCE_IMAGE, MessageType.EPHEMERAL -> "jpg"
        MessageType.GIF -> "gif"
        MessageType.STICKER -> "webp"
        MessageType.VIDEO, MessageType.VIEW_ONCE_VIDEO -> "mp4"
        MessageType.AUDIO -> "m4a"
        MessageType.FILE -> "pdf"
        else -> "txt"
    }

    private fun contentTypeFor(type: MessageType): String = when (type) {
        MessageType.IMAGE, MessageType.VIEW_ONCE_IMAGE, MessageType.EPHEMERAL -> "image/jpeg"
        MessageType.VIDEO, MessageType.VIEW_ONCE_VIDEO -> "video/mp4"
        MessageType.AUDIO -> "audio/mp4"
        MessageType.GIF -> "image/gif"
        MessageType.STICKER -> "image/webp"
        MessageType.FILE -> "application/pdf"
        else -> "text/plain"
    }

    private fun shouldEncryptMedia(type: MessageType): Boolean = when (type) {
        MessageType.IMAGE, MessageType.VIDEO, MessageType.AUDIO, MessageType.FILE,
        MessageType.EPHEMERAL, MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO,
        -> true
        else -> false
    }

    suspend fun uploadMedia(
        data: ByteArray,
        type: MessageType,
        conversationId: String,
        messageId: String? = null,
    ): Result<ChatMediaUploadResult> = runCatching {
        val senderId = FirebaseAuth.getInstance().currentUser?.uid
            ?: error("Usuario no autenticado")
        val resolvedMessageId = messageId ?: UUID.randomUUID().toString()
        val ext = fileExtensionFor(type)
        val contentType = contentTypeFor(type)
        val mediaFileId = UUID.randomUUID().toString()
        val tempFiles = mutableListOf<File>()

        try {
            var preparedVideoFile: File? = null
            if (type == MessageType.VIDEO || type == MessageType.VIEW_ONCE_VIDEO) {
                val preparedUri = VideoCompressionService.prepareVideoDataForUpload(
                    data = data,
                    preset = VideoCompressionPreset.CHAT,
                    preferredExtension = "mp4",
                )
                val preparedFile = File(requireNotNull(preparedUri.path))
                tempFiles += preparedFile
                preparedVideoFile = preparedFile
                if (shouldEncryptMedia(type)) {
                    return@runCatching ChatService.uploadChunkedEncryptedVideo(
                        preparedFile = preparedFile,
                        senderId = senderId,
                        conversationId = conversationId,
                        messageId = resolvedMessageId,
                        mediaFileId = mediaFileId,
                        fileExtension = ext,
                        contentType = contentType,
                    )
                }
            }

            if (shouldEncryptMedia(type)) {
                val plaintextData = preparedVideoFile?.readBytes() ?: data
                return@runCatching uploadEncryptedBlobMedia(
                    plaintextData = plaintextData,
                    senderId = senderId,
                    conversationId = conversationId,
                    messageId = resolvedMessageId,
                    mediaFileId = mediaFileId,
                    type = type,
                    fileExtension = ext,
                    contentType = contentType,
                )
            }

            val mediaTarget = StoragePathBuilder.build(
                senderId,
                StorageUploadDomain.ChatMedia(
                    conversationId = conversationId,
                    messageId = resolvedMessageId,
                    fileExtension = ext,
                    fileId = mediaFileId,
                ),
            )
            val payload: MediaUploadPayload = preparedVideoFile?.let {
                MediaUploadPayload.File(Uri.fromFile(it))
            } ?: MediaUploadPayload.Data(data)

            val mediaUrl = uploader.upload(
                mediaTarget,
                payload,
                progress = { ChatMediaUploadProgressEvents.emit(resolvedMessageId, it) },
            )

            // Limpiar temps antes del thumb (≡ iOS limpia tempFilesToCleanup pre-thumb).
            tempFiles.forEach { runCatching { it.delete() } }
            tempFiles.clear()

            val thumbnailUrl = if (type == MessageType.VIDEO || type == MessageType.VIEW_ONCE_VIDEO) {
                generateVideoThumbnailUrl(data, senderId, conversationId, resolvedMessageId)
            } else {
                null
            }
            ChatMediaUploadResult(
                mediaUrl = mediaUrl,
                thumbnailUrl = thumbnailUrl,
                mediaObjectPath = null,
                thumbnailObjectPath = null,
                mediaEncryption = null,
                thumbnailEncryption = null,
            )
        } finally {
            tempFiles.forEach { runCatching { it.delete() } }
        }
    }

    private suspend fun uploadEncryptedBlobMedia(
        plaintextData: ByteArray,
        senderId: String,
        conversationId: String,
        messageId: String,
        mediaFileId: String,
        type: MessageType,
        fileExtension: String,
        contentType: String,
    ): ChatMediaUploadResult {
        val encryptedMain = EncryptionService.encryptChatMedia(
            data = plaintextData,
            conversationId = conversationId,
            messageId = messageId,
            purpose = ChatMediaPurpose.PRIMARY,
            contentType = contentType,
            fileExtension = fileExtension,
        )
        val cachedMainFile = ChatCacheStore.writeDecryptedMedia(
            plaintextData,
            conversationId,
            messageId,
            encryptedMain.metadata.purpose,
            encryptedMain.metadata.fileExtension,
        )
        val localPreviewUrl = Uri.fromFile(cachedMainFile).toString()
        val encryptedMainTarget = chatEncryptedStorageTarget(
            userId = senderId,
            conversationId = conversationId,
            messageId = messageId,
            fileId = mediaFileId,
            originalContentType = contentType,
        )

        val resolver = ChatService.encryptedMediaResolver
        resolver.stageOutgoingPreview(
            CachedResolvedMedia(mediaUrl = localPreviewUrl, thumbnailUrl = null),
            messageId,
        )
        resolver.markUploadStarted(messageId)
        try {
            uploader.uploadEncryptedBlob(
                target = encryptedMainTarget,
                data = encryptedMain.ciphertext,
                progress = { ChatMediaUploadProgressEvents.emit(messageId, it) },
            )

            var thumbnailObjectPath: String? = null
            var thumbnailEncryption: EncryptedChatMediaMetadata? = null
            var localThumbnailUrl: String? = null

            val thumbnailData = when (type) {
                MessageType.IMAGE, MessageType.VIEW_ONCE_IMAGE, MessageType.EPHEMERAL ->
                    generateImageThumbnailData(plaintextData)
                else -> null
            }
            if (thumbnailData != null) {
                // Thumbnail opcional; el media principal ya está subido.
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
                    val encryptedThumbTarget = chatEncryptedStorageTarget(
                        userId = senderId,
                        conversationId = conversationId,
                        messageId = messageId,
                        fileId = thumbId,
                        originalContentType = "image/jpeg",
                        objectPath = thumbBase.objectPath.replace(".jpg", ".enc"),
                    )
                    uploader.uploadEncryptedBlob(encryptedThumbTarget, encryptedThumb.ciphertext)
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

            val resolvedPreview = CachedResolvedMedia(localPreviewUrl, localThumbnailUrl)
            resolver.stageOutgoingPreview(resolvedPreview, messageId)
            resolver.cacheResolvedPreview(resolvedPreview, messageId)

            return ChatMediaUploadResult(
                mediaUrl = localPreviewUrl,
                thumbnailUrl = localThumbnailUrl,
                mediaObjectPath = encryptedMainTarget.objectPath,
                thumbnailObjectPath = thumbnailObjectPath,
                mediaEncryption = encryptedMain.metadata,
                thumbnailEncryption = thumbnailEncryption,
            )
        } finally {
            resolver.markUploadFinished(messageId)
        }
    }

    /** ≡ `generateImageThumbnailData` — lado mayor ≤ 720, JPEG 0.6. */
    private fun generateImageThumbnailData(imageData: ByteArray): ByteArray? {
        val bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.size) ?: return null
        val maxDimension = 720f
        val longest = max(bitmap.width, bitmap.height).toFloat()
        val scaled = if (longest > maxDimension && longest > 0f) {
            val scale = maxDimension / longest
            Bitmap.createScaledBitmap(
                bitmap,
                (bitmap.width * scale).toInt().coerceAtLeast(1),
                (bitmap.height * scale).toInt().coerceAtLeast(1),
                true,
            )
        } else {
            bitmap
        }
        return try {
            ByteArrayOutputStream().use { stream ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 60, stream)
                stream.toByteArray()
            }
        } finally {
            if (scaled !== bitmap) scaled.recycle()
            bitmap.recycle()
        }
    }

    private suspend fun generateVideoThumbnailUrl(
        videoData: ByteArray,
        senderId: String,
        conversationId: String,
        messageId: String,
    ): String? {
        val tempVideo = File.createTempFile("chat_video_thumb_", ".mp4")
        return try {
            tempVideo.writeBytes(videoData)
            val thumbnailData = generateVideoThumbnailData(tempVideo) ?: return null
            val thumbTarget = StoragePathBuilder.build(
                senderId,
                StorageUploadDomain.ChatThumbnail(conversationId = conversationId, messageId = messageId),
            )
            uploader.upload(thumbTarget, MediaUploadPayload.Data(thumbnailData))
        } finally {
            tempVideo.delete()
        }
    }

    /**
     * ≡ `generateVideoThumbnailData(from:)` — frames 0.15s / 0 / 0.5s; max 720; JPEG 0.78.
     * Usado también por ChunkedVideoUpload.
     */
    suspend fun generateVideoThumbnailData(videoFile: File): ByteArray? {
        val frameCandidatesUs = listOf(150_000L, 0L, 500_000L)
        for (timeUs in frameCandidatesUs) {
            val bitmap = extractVideoThumbnailFromFile(
                file = videoFile,
                timeUs = timeUs,
                maxSizePx = 720,
            ) ?: continue
            return try {
                ByteArrayOutputStream().use { stream ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 78, stream)
                    stream.toByteArray()
                }
            } finally {
                bitmap.recycle()
            }
        }
        return null
    }

    fun chatEncryptedStorageTarget(
        userId: String,
        conversationId: String,
        messageId: String,
        fileId: String,
        originalContentType: String,
        objectPath: String? = null,
    ): StorageUploadTarget {
        val path = objectPath ?: StoragePathBuilder.build(
            userId,
            StorageUploadDomain.ChatMedia(
                conversationId = conversationId,
                messageId = messageId,
                fileExtension = "enc",
                fileId = fileId,
            ),
        ).objectPath
        return StorageUploadTarget(
            objectPath = path,
            contentType = "application/octet-stream",
            customMetadata = mapOf(
                "ownerId" to userId,
                "type" to "chat_media_encrypted",
                "conversationId" to conversationId,
                "messageId" to messageId,
                "encrypted" to "true",
                "originalContentType" to originalContentType,
            ),
        )
    }
}

// MARK: - ChatService surface (≡ extension methods)

suspend fun ChatService.uploadMedia(
    data: ByteArray,
    type: MessageType,
    conversationId: String,
    messageId: String? = null,
): Result<ChatMediaUploadResult> =
    ChatServiceMediaPipeline.uploadMedia(data, type, conversationId, messageId)

fun ChatService.getFileExtension(type: MessageType): String =
    ChatServiceMediaPipeline.fileExtensionFor(type)

suspend fun ChatService.deleteMediaFile(url: String): Result<Unit> = runCatching {
    if (url.isEmpty()) error("URL inválida")
    val uri = Uri.parse(url)
    if (uri.scheme == "file") {
        val path = uri.path ?: return@runCatching
        val file = File(path)
        if (file.exists()) {
            check(file.delete()) { "No se pudo borrar el archivo local" }
        }
        return@runCatching
    }
    val objectPath = StoragePathBuilder.extractObjectPath(url)
    val ref = if (objectPath.startsWith("http://") || objectPath.startsWith("https://")) {
        FirebaseStorage.getInstance().getReferenceFromUrl(objectPath)
    } else {
        FirebaseStorage.getInstance().reference.child(objectPath)
    }
    ref.delete().await()
}

suspend fun ChatService.deleteMediaFiles(urls: List<String>): Result<Unit> = runCatching {
    var firstError: Throwable? = null
    for (url in urls) {
        deleteMediaFile(url).onFailure { if (firstError == null) firstError = it }
    }
    firstError?.let { throw it }
}
