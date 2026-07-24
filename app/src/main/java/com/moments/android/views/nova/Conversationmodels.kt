package com.moments.android.views.nova

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.google.firebase.Timestamp
import com.moments.android.views.nova.novacore.NovaChatMessage
import com.moments.android.views.nova.novacore.NovaGroundingPayload
import java.util.Date
import java.util.UUID

data class NovaConversationTitle(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val lastUpdated: Date = Date(),
    val messageCount: Int,
    val userId: String,
) {
    fun toFirestoreData(): Map<String, Any> = mapOf("id" to id, "title" to title, "lastUpdated" to Timestamp(lastUpdated), "messageCount" to messageCount, "userId" to userId)

    companion object {
        fun fromFirestoreData(data: Map<String, Any?>): NovaConversationTitle? = kotlin.run {
            NovaConversationTitle(
                id = data["id"] as? String ?: return@run null,
                title = data["title"] as? String ?: return@run null,
                lastUpdated = (data["lastUpdated"] as? Timestamp)?.toDate() ?: return@run null,
                messageCount = (data["messageCount"] as? Number)?.toInt() ?: return@run null,
                userId = data["userId"] as? String ?: return@run null,
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
    fun toFirestoreData(): Map<String, Any> = mapOf("id" to id, "title" to title, "messages" to messages.map { it.toFirestoreData() }, "createdAt" to Timestamp(createdAt), "lastUpdated" to Timestamp(lastUpdated), "userId" to userId)

    companion object {
        fun fromFirestoreData(data: Map<String, Any?>): NovaSavedConversation? {
            val rawMessages = data["messages"] as? List<*> ?: return null
            return NovaSavedConversation(
                id = data["id"] as? String ?: return null,
                title = data["title"] as? String ?: return null,
                messages = rawMessages.mapNotNull { (it as? Map<*, *>)?.entries?.mapNotNull { (key, value) -> (key as? String)?.let { it to value } }?.toMap()?.let(NovaSavedChatMessage::fromFirestoreData) },
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
    val imageData: String? = null,
    val groundingData: String? = null,
) {
    fun toFirestoreData(): Map<String, Any> = buildMap {
        put("id", id); put("text", text); put("isUser", isUser)
        imageData?.let { put("imageData", it) }
        groundingData?.let { put("groundingData", it) }
    }

    fun toChatMessage(image: Bitmap? = null, imageStoragePath: String? = null, grounding: NovaGroundingPayload? = null): NovaChatMessage = NovaChatMessage(
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
        fun fromChatMessage(message: NovaChatMessage) = NovaSavedChatMessage(message.id, message.text, message.isUser, message.imageStoragePath)
        fun fromFirestoreData(data: Map<String, Any?>): NovaSavedChatMessage? = kotlin.run {
            NovaSavedChatMessage(
                id = data["id"] as? String ?: return@run null,
                text = data["text"] as? String ?: return@run null,
                isUser = data["isUser"] as? Boolean ?: return@run null,
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
        fun looksLikeStorageReference(value: String?) = value?.let { it.startsWith("users/") || it.startsWith("https://") || it.startsWith("gs://") } == true
    }
}

fun List<NovaChatMessage>.toNovaSavedMessages() = filterNot { it.isSystem }.map(NovaSavedChatMessage::fromChatMessage)
fun List<NovaSavedChatMessage>.toNovaChatMessages() = map(NovaSavedChatMessage::toChatMessage)
