package com.moments.android.services.storage

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.storage.FirebaseStorage
import com.moments.android.services.messaging.EncryptionService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

enum class UploadMediaKind { IMAGE, VIDEO }

data class UploadMediaItem(
    val type: UploadMediaKind,
    val image: Bitmap? = null,
    val videoUri: Uri? = null,
)

sealed class FeedMediaUploadContext {
    data class Moment(val momentId: String, val mediaId: String = UUID.randomUUID().toString()) :
        FeedMediaUploadContext()

    data class Story(val storyId: String, val mediaId: String = UUID.randomUUID().toString()) :
        FeedMediaUploadContext()
}

sealed class ModerationError(message: String) : Exception(message) {
    data class ContentRejected(val reason: String) : ModerationError("Contenido no permitido: $reason")
}

// Port de StorageService.swift. Orquesta MediaUploadService + VideoCompression + (stub) Encryption.
object StorageService {

    private val uploader get() = MediaUploadService
    private val videoCompression get() = VideoCompressionService
    private val encryptionService get() = EncryptionService

    // MARK: - Profile

    suspend fun uploadProfileImage(userId: String, image: Bitmap): String {
        val imageData = image.storageUploadJpegData(compressionQuality = 0.75f, maxPixelDimension = 1080)
            ?: throw StorageError.InvalidData
        val target = StoragePathBuilder.build(userId, StorageUploadDomain.ProfileAvatar())
        return completeWithPublicDownloadURL(
            uploader.upload(target, MediaUploadPayload.Data(imageData)),
        )
    }

    // MARK: - Nova

    suspend fun uploadNovaConversationImage(
        userId: String,
        conversationId: String,
        messageId: String,
        image: Bitmap,
    ): String {
        val imageData = image.storageUploadJpegData(compressionQuality = 0.82f, maxPixelDimension = 1080)
            ?: throw StorageError.InvalidData
        val target = StoragePathBuilder.build(
            userId,
            StorageUploadDomain.NovaConversationImage(conversationId, messageId),
        )
        val purpose = "conversationImage|$conversationId|$messageId"
        val encryptedData = encryptionService.encryptNovaBlob(imageData, userId, purpose)
        return uploader.uploadEncryptedBlob(target, encryptedData)
    }

    suspend fun downloadNovaConversationImage(
        userId: String,
        conversationId: String,
        messageId: String,
        storedPath: String,
    ): Bitmap {
        val objectPath = StoragePathBuilder.extractObjectPath(storedPath)
        if (objectPath.isEmpty()) throw StorageError.InvalidPath

        val data = withContext(Dispatchers.IO) {
            FirebaseStorage.getInstance().reference.child(objectPath)
                .getBytes(15L * 1024 * 1024)
                .await()
        }

        val purpose = "conversationImage|$conversationId|$messageId"
        val decrypted = encryptionService.decryptNovaBlob(data, userId, purpose)
        return BitmapFactory.decodeByteArray(decrypted, 0, decrypted.size)
            ?: throw StorageError.InvalidData
    }

    // MARK: - Feed media

    suspend fun uploadMedia(
        userId: String,
        mediaItem: UploadMediaItem,
        context: FeedMediaUploadContext,
        progress: ((Double) -> Unit)? = null,
    ): String = when (mediaItem.type) {
        UploadMediaKind.IMAGE -> uploadFeedImage(mediaItem.image, userId, context, progress)
        UploadMediaKind.VIDEO -> uploadFeedVideo(mediaItem.videoUri, userId, context, progress)
    }

    suspend fun uploadMomentThumbnail(
        userId: String,
        momentId: String,
        image: Bitmap,
        mediaId: String = UUID.randomUUID().toString(),
        progress: ((Double) -> Unit)? = null,
    ): String {
        val imageData = image.storageUploadJpegData(compressionQuality = 0.8f, maxPixelDimension = 720)
            ?: throw StorageError.InvalidData
        val target = StoragePathBuilder.build(
            userId,
            StorageUploadDomain.MomentThumbnail(momentId, mediaId),
        )
        return completeWithPublicDownloadURL(
            uploader.upload(target, MediaUploadPayload.Data(imageData), progress),
        )
    }

    suspend fun uploadStoryThumbnail(
        userId: String,
        storyId: String,
        image: Bitmap,
        mediaId: String = UUID.randomUUID().toString(),
        progress: ((Double) -> Unit)? = null,
    ): String {
        val imageData = image.storageUploadJpegData(compressionQuality = 0.8f, maxPixelDimension = 720)
            ?: throw StorageError.InvalidData
        val target = StoragePathBuilder.build(
            userId,
            StorageUploadDomain.StoryThumbnail(storyId, mediaId),
        )
        return completeWithPublicDownloadURL(
            uploader.upload(target, MediaUploadPayload.Data(imageData), progress),
        )
    }

    suspend fun uploadHiddenLayerImage(
        userId: String,
        momentId: String,
        layerId: String,
        image: Bitmap,
    ): String {
        val imageData = image.storageUploadJpegData(compressionQuality = 0.82f, maxPixelDimension = 1080)
            ?: throw StorageError.InvalidData
        val target = StoragePathBuilder.build(
            userId,
            StorageUploadDomain.MomentHiddenLayerImage(momentId, layerId),
        )
        return completeWithPublicDownloadURL(
            uploader.upload(target, MediaUploadPayload.Data(imageData)),
        )
    }

    suspend fun uploadHiddenLayerAudio(
        userId: String,
        momentId: String,
        layerId: String,
        audioUri: Uri,
    ): String {
        val target = StoragePathBuilder.build(
            userId,
            StorageUploadDomain.MomentHiddenLayerAudio(momentId, layerId),
        )
        return completeWithPublicDownloadURL(
            uploader.upload(target, MediaUploadPayload.File(audioUri)),
        )
    }

    // MARK: - Delete

    suspend fun deleteMedia(path: String) {
        if (path.isEmpty()) throw StorageError.InvalidPath
        try {
            uploader.delete(path)
        } catch (_: Exception) {
            throw StorageError.DeleteFailed
        }
    }

    suspend fun deleteProfileImage(userId: String, oldImagePath: String?) {
        val oldPath = oldImagePath?.takeIf { it.isNotEmpty() } ?: return
        val ok = oldPath.contains("firebasestorage.googleapis.com")
            || oldPath.startsWith("images/")
            || oldPath.startsWith("users/")
        if (!ok) return
        deleteMedia(oldPath)
    }

    // MARK: - Private

    private suspend fun completeWithPublicDownloadURL(value: String): String {
        if (value.startsWith("https://") || value.startsWith("http://")) return value
        return uploader.resolveDownloadURL(value)
    }

    private suspend fun uploadFeedImage(
        image: Bitmap?,
        userId: String,
        context: FeedMediaUploadContext,
        progress: ((Double) -> Unit)?,
    ): String {
        val imageData = image?.storageUploadJpegData(compressionQuality = 0.8f, maxPixelDimension = 1280)
            ?: throw StorageError.InvalidData

        val target = when (context) {
            is FeedMediaUploadContext.Moment ->
                StoragePathBuilder.momentImageTarget(userId, context.momentId, context.mediaId)
            is FeedMediaUploadContext.Story ->
                StoragePathBuilder.storyImageTarget(userId, context.storyId, context.mediaId)
        }

        return completeWithPublicDownloadURL(
            uploader.upload(target, MediaUploadPayload.Data(imageData), progress),
        )
    }

    private suspend fun uploadFeedVideo(
        videoUri: Uri?,
        userId: String,
        context: FeedMediaUploadContext,
        progress: ((Double) -> Unit)?,
    ): String {
        val sourceUri = videoUri ?: throw StorageError.InvalidData
        if (sourceUri.scheme == "file") {
            val path = sourceUri.path ?: throw StorageError.InvalidData
            if (!File(path).exists()) throw StorageError.InvalidData
        }

        val preset = when (context) {
            is FeedMediaUploadContext.Moment -> VideoCompressionPreset.MOMENT
            is FeedMediaUploadContext.Story -> VideoCompressionPreset.STORY
        }
        val preparedUri = videoCompression.prepareVideoForUpload(sourceUri, preset)

        val target = when (context) {
            is FeedMediaUploadContext.Moment -> StoragePathBuilder.build(
                userId,
                StorageUploadDomain.MomentMedia(context.momentId, context.mediaId),
            )
            is FeedMediaUploadContext.Story -> StoragePathBuilder.build(
                userId,
                StorageUploadDomain.StoryMedia(context.storyId, context.mediaId),
            )
        }

        try {
            return completeWithPublicDownloadURL(
                uploader.upload(target, MediaUploadPayload.File(preparedUri), progress),
            )
        } finally {
            if (preparedUri != sourceUri && preparedUri.scheme == "file") {
                preparedUri.path?.let { runCatching { File(it).delete() } }
            }
        }
    }
}
