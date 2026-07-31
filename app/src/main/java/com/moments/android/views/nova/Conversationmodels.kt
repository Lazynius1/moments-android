package com.moments.android.views.nova

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.Timestamp
import com.moments.android.views.nova.novacore.NovaChatMessage
import com.moments.android.views.nova.novacore.NovaGroundingPayload
import java.util.Date
import java.util.UUID

/**
 * Port de `Views/Nova/Conversationmodels.swift`.
 * Títulos, conversaciones guardadas y mensajes (legacy base64 / Storage path + grounding).
 */

data class NovaConversationTitle(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastUpdated: Date = Date(),
    val messageCount: Int,
    val userId: String,
) {
    fun toFirestoreData(): Map<String, Any> = mapOf(
        "id" to id,
        "title" to title,
        "lastUpdated" to Timestamp(lastUpdated),
        "messageCount" to messageCount,
        "userId" to userId,
    )

    companion object {
        fun fromFirestoreData(data: Map<String, Any?>): NovaConversationTitle? {
            return NovaConversationTitle(
                id = data["id"] as? String ?: return null,
                title = data["title"] as? String ?: return null,
                lastUpdated = (data["lastUpdated"] as? Timestamp)?.toDate() ?: return null,
                messageCount = (data["messageCount"] as? Number)?.toInt() ?: return null,
                userId = data["userId"] as? String ?: return null,
            )
        }
    }
}

data class NovaSavedConversation(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val messages: List<NovaSavedChatMessage>,
    val createdAt: Date = Date(),
    val lastUpdated: Date = Date(),
    val userId: String,
) {
    fun toFirestoreData(): Map<String, Any> = mapOf(
        "id" to id,
        "title" to title,
        "messages" to messages.map { it.toFirestoreData() },
        "createdAt" to Timestamp(createdAt),
        "lastUpdated" to Timestamp(lastUpdated),
        "userId" to userId,
    )

    companion object {
        fun fromFirestoreData(data: Map<String, Any?>): NovaSavedConversation? {
            val rawMessages = data["messages"] as? List<*> ?: return null
            val messages = rawMessages.mapNotNull { raw ->
                val map = (raw as? Map<*, *>)?.entries
                    ?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }
                    ?.toMap()
                    ?: return@mapNotNull null
                NovaSavedChatMessage.fromFirestoreData(map)
            }
            return NovaSavedConversation(
                id = data["id"] as? String ?: return null,
                title = data["title"] as? String ?: return null,
                messages = messages,
                createdAt = (data["createdAt"] as? Timestamp)?.toDate() ?: return null,
                lastUpdated = (data["lastUpdated"] as? Timestamp)?.toDate() ?: return null,
                userId = data["userId"] as? String ?: return null,
            )
        }
    }
}

data class NovaSavedChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    /** Legacy: inline base64. Current: encrypted Storage object path. */
    val imageData: String? = null,
    /** Encrypted JSON: web sources + Google Search entry point. */
    val groundingData: String? = null,
) {
    fun toFirestoreData(): Map<String, Any> = buildMap {
        put("id", id)
        put("text", text)
        put("isUser", isUser)
        imageData?.let { put("imageData", it) }
        groundingData?.let { put("groundingData", it) }
    }

    fun toChatMessage(
        image: Bitmap? = null,
        imageStoragePath: String? = null,
        grounding: NovaGroundingPayload? = null,
    ): NovaChatMessage = NovaChatMessage(
        id = id,
        text = text,
        isUser = isUser,
        image = image ?: decodeLegacyInlineImage(imageData),
        imageStoragePath = imageStoragePath,
        isHistorical = true,
        groundingSources = grounding?.sources.orEmpty(),
        searchSuggestionsHtml = grounding?.searchSuggestionsHtml,
    )

    companion object {
        fun fromChatMessage(message: NovaChatMessage) = NovaSavedChatMessage(
            id = message.id,
            text = message.text,
            isUser = message.isUser,
            imageData = message.imageStoragePath,
            groundingData = null,
        )

        fun fromFirestoreData(data: Map<String, Any?>): NovaSavedChatMessage? {
            return NovaSavedChatMessage(
                id = data["id"] as? String ?: return null,
                text = data["text"] as? String ?: return null,
                isUser = data["isUser"] as? Boolean ?: return null,
                imageData = data["imageData"] as? String,
                groundingData = data["groundingData"] as? String,
            )
        }

        fun decodeLegacyInlineImage(value: String?): Bitmap? = value?.let { encoded ->
            runCatching {
                val bytes = Base64.decode(encoded, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }.getOrNull()
        }

        fun looksLikeStorageReference(value: String?): Boolean {
            if (value == null) return false
            return value.startsWith("users/") ||
                value.startsWith("https://") ||
                value.startsWith("gs://")
        }
    }
}

fun List<NovaChatMessage>.toNovaSavedMessages(): List<NovaSavedChatMessage> =
    filterNot { it.isSystem }.map(NovaSavedChatMessage::fromChatMessage)

fun List<NovaSavedChatMessage>.toNovaChatMessages(): List<NovaChatMessage> =
    map { it.toChatMessage() }
