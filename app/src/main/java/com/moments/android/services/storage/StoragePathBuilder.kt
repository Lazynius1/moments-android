package com.moments.android.services.storage

import java.util.UUID

// Convenciones de rutas de Storage (users/{uid}/…). Port de StoragePathBuilder.swift.
sealed class StorageUploadDomain {
    data class ProfileAvatar(val uploadId: String = UUID.randomUUID().toString()) : StorageUploadDomain()
    data class NovaConversationImage(
        val conversationId: String,
        val messageId: String,
        val imageId: String = UUID.randomUUID().toString()
    ) : StorageUploadDomain()
    data class MomentMedia(val momentId: String, val mediaId: String = UUID.randomUUID().toString()) : StorageUploadDomain()
    data class MomentThumbnail(val momentId: String, val mediaId: String = UUID.randomUUID().toString()) : StorageUploadDomain()
    data class MomentHiddenLayerImage(val momentId: String, val layerId: String) : StorageUploadDomain()
    data class MomentHiddenLayerAudio(val momentId: String, val layerId: String) : StorageUploadDomain()
    data class StoryMedia(val storyId: String, val mediaId: String = UUID.randomUUID().toString()) : StorageUploadDomain()
    data class StoryThumbnail(val storyId: String, val mediaId: String = UUID.randomUUID().toString()) : StorageUploadDomain()
    data class StoryFrame(
        val storyId: String,
        val uploadId: String = UUID.randomUUID().toString(),
        val blurred: Boolean = false
    ) : StorageUploadDomain()
    data class StoryStickerAudio(val storyId: String, val uploadId: String = UUID.randomUUID().toString()) : StorageUploadDomain()
    data class ChatMedia(
        val conversationId: String,
        val messageId: String,
        val fileExtension: String,
        val fileId: String = UUID.randomUUID().toString()
    ) : StorageUploadDomain()
    data class ChatThumbnail(
        val conversationId: String,
        val messageId: String,
        val thumbId: String = UUID.randomUUID().toString()
    ) : StorageUploadDomain()
    data class DataExport(val exportId: String = UUID.randomUUID().toString()) : StorageUploadDomain()
}

data class StorageUploadTarget(
    val objectPath: String,
    val contentType: String,
    val customMetadata: Map<String, String>
)

object StoragePathBuilder {

    fun build(userId: String, domain: StorageUploadDomain): StorageUploadTarget {
        val safeUserId = sanitized(userId)
        val path: String
        val contentType: String
        val metadata = linkedMapOf("ownerId" to safeUserId)

        when (domain) {
            is StorageUploadDomain.ProfileAvatar -> {
                path = "users/$safeUserId/profile/avatar/${sanitized(domain.uploadId)}.jpg"
                contentType = "image/jpeg"
                metadata["type"] = "profile_picture"
            }
            is StorageUploadDomain.NovaConversationImage -> {
                path = "users/$safeUserId/nova/${sanitized(domain.conversationId)}/${sanitized(domain.messageId)}/${sanitized(domain.imageId)}.enc"
                contentType = "application/octet-stream"
                metadata["type"] = "nova_conversation_image"
                metadata["conversationId"] = sanitized(domain.conversationId)
                metadata["messageId"] = sanitized(domain.messageId)
                metadata["encrypted"] = "true"
            }
            is StorageUploadDomain.MomentMedia -> {
                path = "users/$safeUserId/moments/${sanitized(domain.momentId)}/media/${sanitized(domain.mediaId)}.mp4"
                contentType = "video/mp4"
                metadata["type"] = "moment_video"
                metadata["momentId"] = sanitized(domain.momentId)
            }
            is StorageUploadDomain.MomentThumbnail -> {
                path = "users/$safeUserId/moments/${sanitized(domain.momentId)}/thumbnails/${sanitized(domain.mediaId)}.jpg"
                contentType = "image/jpeg"
                metadata["type"] = "moment_thumbnail"
                metadata["momentId"] = sanitized(domain.momentId)
            }
            is StorageUploadDomain.MomentHiddenLayerImage -> {
                path = "users/$safeUserId/moments/${sanitized(domain.momentId)}/hidden_layers/${sanitized(domain.layerId)}/media.jpg"
                contentType = "image/jpeg"
                metadata["type"] = "moment_hidden_layer_image"
                metadata["momentId"] = sanitized(domain.momentId)
                metadata["layerId"] = sanitized(domain.layerId)
            }
            is StorageUploadDomain.MomentHiddenLayerAudio -> {
                path = "users/$safeUserId/moments/${sanitized(domain.momentId)}/hidden_layers/${sanitized(domain.layerId)}/audio.m4a"
                contentType = "audio/mp4"
                metadata["type"] = "moment_hidden_layer_audio"
                metadata["momentId"] = sanitized(domain.momentId)
                metadata["layerId"] = sanitized(domain.layerId)
            }
            is StorageUploadDomain.StoryMedia -> {
                path = "users/$safeUserId/stories/${sanitized(domain.storyId)}/media/${sanitized(domain.mediaId)}.mp4"
                contentType = "video/mp4"
                metadata["type"] = "story_video"
                metadata["storyId"] = sanitized(domain.storyId)
            }
            is StorageUploadDomain.StoryThumbnail -> {
                path = "users/$safeUserId/stories/${sanitized(domain.storyId)}/thumbnails/${sanitized(domain.mediaId)}.jpg"
                contentType = "image/jpeg"
                metadata["type"] = "story_thumbnail"
                metadata["storyId"] = sanitized(domain.storyId)
            }
            is StorageUploadDomain.StoryFrame -> {
                val prefix = if (domain.blurred) "blurred_" else ""
                path = "users/$safeUserId/stories/${sanitized(domain.storyId)}/frames/$prefix${sanitized(domain.uploadId)}.jpg"
                contentType = "image/jpeg"
                metadata["type"] = if (domain.blurred) "story_frame_blurred" else "story_frame"
                metadata["storyId"] = sanitized(domain.storyId)
            }
            is StorageUploadDomain.StoryStickerAudio -> {
                path = "users/$safeUserId/stories/${sanitized(domain.storyId)}/audio/${sanitized(domain.uploadId)}.m4a"
                contentType = "audio/mp4"
                metadata["type"] = "story_sticker_audio"
                metadata["storyId"] = sanitized(domain.storyId)
            }
            is StorageUploadDomain.ChatMedia -> {
                val safeExt = sanitizedExtension(domain.fileExtension)
                val safeFileId = sanitized(domain.fileId)
                path = "users/$safeUserId/chat/${sanitized(domain.conversationId)}/${sanitized(domain.messageId)}/$safeFileId.$safeExt"
                contentType = contentTypeForChatExtension(safeExt)
                metadata["type"] = "chat_media"
                metadata["conversationId"] = sanitized(domain.conversationId)
                metadata["messageId"] = sanitized(domain.messageId)
            }
            is StorageUploadDomain.ChatThumbnail -> {
                path = "users/$safeUserId/chat/${sanitized(domain.conversationId)}/${sanitized(domain.messageId)}/thumbnails/${sanitized(domain.thumbId)}.jpg"
                contentType = "image/jpeg"
                metadata["type"] = "chat_thumbnail"
                metadata["conversationId"] = sanitized(domain.conversationId)
                metadata["messageId"] = sanitized(domain.messageId)
            }
            is StorageUploadDomain.DataExport -> {
                path = "users/$safeUserId/exports/${sanitized(domain.exportId)}.zip"
                contentType = "application/zip"
                metadata["type"] = "data_export"
            }
        }

        return StorageUploadTarget(path, contentType, metadata)
    }

    // Media de moment tipo imagen (no vídeo).
    fun momentImageTarget(
        userId: String,
        momentId: String,
        mediaId: String = UUID.randomUUID().toString()
    ): StorageUploadTarget {
        val safeUserId = sanitized(userId)
        val path = "users/$safeUserId/moments/${sanitized(momentId)}/media/${sanitized(mediaId)}.jpg"
        return StorageUploadTarget(
            objectPath = path,
            contentType = "image/jpeg",
            customMetadata = mapOf(
                "ownerId" to safeUserId,
                "type" to "moment_image",
                "momentId" to sanitized(momentId)
            )
        )
    }

    // Media de story tipo imagen (no vídeo).
    fun storyImageTarget(
        userId: String,
        storyId: String,
        mediaId: String = UUID.randomUUID().toString()
    ): StorageUploadTarget {
        val safeUserId = sanitized(userId)
        val path = "users/$safeUserId/stories/${sanitized(storyId)}/media/${sanitized(mediaId)}.jpg"
        return StorageUploadTarget(
            objectPath = path,
            contentType = "image/jpeg",
            customMetadata = mapOf(
                "ownerId" to safeUserId,
                "type" to "story_image",
                "storyId" to sanitized(storyId)
            )
        )
    }

    fun extractObjectPath(urlOrPath: String): String {
        val trimmed = urlOrPath.trim()
        if (trimmed.isEmpty()) return trimmed

        if (trimmed.startsWith("https://firebasestorage.googleapis.com")) {
            val pathComponent = trimmed.substringBefore("?").substringAfter("/o/", missingDelimiterValue = "")
            if (pathComponent.isEmpty()) return trimmed
            return try {
                java.net.URLDecoder.decode(pathComponent, "UTF-8")
            } catch (_: Exception) {
                pathComponent
            }
        }

        if (trimmed.contains("://")) return trimmed
        return trimmed
    }

    fun isUserOwnedStoragePath(objectPath: String, userId: String): Boolean {
        val path = extractObjectPath(objectPath)
        val safeUser = sanitized(userId)
        val prefix = "users/$safeUser/"
        if (path.startsWith(prefix)) return true

        // Legacy
        if (path.startsWith("images/") || path.startsWith("videos/")) {
            return path.contains("_$safeUser.")
        }
        if (path.startsWith("hidden_layers/$safeUser/")) return true
        if (path.startsWith("background_frames/$safeUser/")) return true
        if (path.startsWith("exports/$safeUser/")) return true
        return false
    }

    // Segmento seguro para rutas Storage y mediaItemId de moderación.
    fun storageSafeSegment(value: String): String = sanitized(value)

    private fun sanitized(value: String): String {
        val trimmed = value.trim()
        return buildString {
            for (c in trimmed) {
                append(if (c.isLetterOrDigit() || c == '-' || c == '_') c else '_')
            }
        }
    }

    private fun sanitizedExtension(ext: String): String {
        val lowered = ext.lowercase().trim()
        return when (lowered) {
            "jpg", "jpeg", "png", "gif", "mp4", "m4a", "pdf", "txt", "enc" ->
                if (lowered == "jpeg") "jpg" else lowered
            else -> "bin"
        }
    }

    private fun contentTypeForChatExtension(ext: String): String = when (ext) {
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "mp4" -> "video/mp4"
        "m4a" -> "audio/mp4"
        "pdf" -> "application/pdf"
        "enc" -> "application/octet-stream"
        else -> "application/octet-stream"
    }
}
