package com.moments.android.services.messaging

import com.moments.android.views.messaging.core.ChatMediaPurpose
import com.moments.android.views.messaging.core.EncryptedChatMediaMetadata

/**
 * Port de `MomentsNotificationService/SharedChatDecryptor.swift`.
 *
 * En iOS el NSE no enlaza el [EncryptionService] completo; aquí reutilizamos
 * el mismo AES-GCM / Keystore vía [EncryptionService] (mismo service/prefix).
 */
object SharedChatDecryptor {

    /**
     * ≡ SharedChatDecryptor.MediaMetadata — subset del mapa Firestore
     * `thumbnailEncryption` / `mediaEncryption`.
     */
    data class MediaMetadata(
        val purpose: String,
        val contentType: String,
        val fileExtension: String,
        val plaintextSize: Long,
        val version: String = "1.0",
        val algorithm: String = "AES.GCM+HKDF-SHA256",
        val mediaId: String = "",
    ) {
        fun toEncryptedChatMediaMetadata(): EncryptedChatMediaMetadata? {
            val purposeEnum = ChatMediaPurpose.from(purpose) ?: return null
            return EncryptedChatMediaMetadata(
                version = version,
                algorithm = algorithm,
                purpose = purposeEnum,
                mediaId = mediaId,
                contentType = contentType,
                fileExtension = fileExtension,
                plaintextSize = plaintextSize,
            )
        }

        companion object {
            fun fromMap(map: Map<String, Any?>?): MediaMetadata? {
                if (map == null) return null
                val purpose = (map["purpose"] as? String)?.trim().orEmpty()
                val contentType = (map["contentType"] as? String)?.trim().orEmpty()
                val fileExtension = (map["fileExtension"] as? String)?.trim().orEmpty()
                if (purpose.isEmpty() || contentType.isEmpty() || fileExtension.isEmpty()) return null
                val plaintextSize = when (val size = map["plaintextSize"]) {
                    is Number -> size.toLong()
                    is String -> size.toLongOrNull() ?: return null
                    else -> return null
                }
                return MediaMetadata(
                    purpose = purpose,
                    contentType = contentType,
                    fileExtension = fileExtension,
                    plaintextSize = plaintextSize,
                    version = (map["version"] as? String) ?: "1.0",
                    algorithm = (map["algorithm"] as? String) ?: "AES.GCM+HKDF-SHA256",
                    mediaId = (map["mediaId"] as? String).orEmpty(),
                )
            }
        }
    }

    /** ≡ SharedChatDecryptor.decrypt(_:conversationId:) */
    suspend fun decrypt(base64Content: String, conversationId: String): String? {
        val trimmed = base64Content.trim()
        if (trimmed.isEmpty()) return null
        val decrypted = EncryptionService.decryptChatMessage(trimmed, conversationId)?.trim().orEmpty()
        return decrypted.takeIf { it.isNotEmpty() && it != trimmed }
    }

    /** ≡ SharedChatDecryptor.decryptMedia(_:metadata:conversationId:messageId:) */
    suspend fun decryptMedia(
        encryptedData: ByteArray,
        metadata: MediaMetadata,
        conversationId: String,
        messageId: String,
    ): ByteArray? {
        val encMeta = metadata.toEncryptedChatMediaMetadata() ?: return null
        return runCatching {
            EncryptionService.decryptChatMedia(
                encryptedData = encryptedData,
                metadata = encMeta,
                conversationId = conversationId,
                messageId = messageId,
            )
        }.getOrNull()
    }
}
