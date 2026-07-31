package com.moments.android.views.nova.conversation

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.moments.android.R
import com.moments.android.extensions.optStringOrNull
import com.moments.android.services.messaging.EncryptionService
import com.moments.android.services.storage.StorageService
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.nova.NovaConversationTitle
import com.moments.android.views.nova.NovaSavedChatMessage
import com.moments.android.views.nova.NovaSavedConversation
import com.moments.android.views.nova.ai.NovaAIService
import com.moments.android.views.nova.ai.NovaPromptCatalog
import com.moments.android.views.nova.novacore.NovaChatMessage
import com.moments.android.views.nova.novacore.NovaGroundingPayload
import com.moments.android.views.nova.novacore.NovaGroundingSource
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.UUID

/**
 * Port de `Views/Nova/Conversation/NovaConversationStore.swift`.
 * Persistencia cifrada user-scoped + lectura one-shot de colecciones legacy gemini*.
 */
object NovaConversationStore {
    private val db = FirebaseFirestore.getInstance()
    private val encryptionService = EncryptionService
    private val ai = NovaAIService
    private val imageReferenceCache = mutableMapOf<String, String>()
    private val encryptedTextCache = mutableMapOf<String, String>()
    private var appContext: Context? = null

    var isLoading by mutableStateOf(false)
        private set
    var lastError by mutableStateOf<String?>(null)
        private set

    fun initialize(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun loadConversationTitles(userId: String): List<NovaConversationTitle> = loading {
        val merged = linkedMapOf<String, NovaConversationTitle>()
        val newSnapshot = userConversations(userId)
            .orderBy("lastUpdated", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(20)
            .get()
            .await()
        for (document in newSnapshot.documents) {
            document.data?.toStringAnyMap()?.let { decryptConversationTitle(it, userId) }?.let {
                merged[it.id] = it
            }
        }

        if (!legacyDrained(userId)) {
            val legacy = legacyTitles()
                .whereEqualTo("userId", userId)
                .orderBy("lastUpdated", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(20)
                .get()
                .await()
            if (legacy.documents.isEmpty()) markLegacyDrained(userId)
            for (document in legacy.documents) {
                document.data?.toStringAnyMap()?.let { decryptTitle(it, userId) }?.let { title ->
                    if (title.id !in merged) merged[title.id] = title
                }
            }
        }

        merged.values.sortedByDescending { it.lastUpdated }.take(20)
    }.getOrElse { emptyList() }

    suspend fun saveConversation(userId: String, messages: List<NovaChatMessage>): String? = loading {
        if (messages.isEmpty()) return@loading null
        val conversationId = UUID.randomUUID().toString()
        val title = generateTitle(messages)
        val encryptedTitle = encryptionService.encryptNovaData(title, userId)
        val encryptedMessages = encryptMessages(messages, userId, conversationId)
        val saved = NovaSavedConversation(
            id = conversationId,
            title = encryptedTitle,
            messages = encryptedMessages,
            createdAt = Date(),
            lastUpdated = Date(),
            userId = userId,
        )
        saveUserConversation(saved, userId)
        conversationId
    }.getOrNull()

    suspend fun updateConversation(
        conversationId: String,
        userId: String,
        messages: List<NovaChatMessage>,
    ): Boolean = loading {
        if (messages.isEmpty()) return@loading false
        val source = loadConversationSource(conversationId, userId) ?: return@loading false
        val old = source.conversation
        val existingTitle = encryptionService.decryptNovaData(old.title, userId)
        val shouldRefresh = old.messages.size < 6 && messages.size >= 6
        val resolvedTitle = if (shouldRefresh) {
            generateTitle(messages, existingTitle)
        } else {
            existingTitle
        }
        val updated = NovaSavedConversation(
            id = conversationId,
            title = encryptionService.encryptNovaData(resolvedTitle, userId),
            messages = encryptMessages(messages, userId, conversationId),
            createdAt = old.createdAt,
            lastUpdated = Date(),
            userId = userId,
        )
        when (source.location) {
            SourceLocation.USER_SCOPED -> updateUserConversation(updated, userId)
            SourceLocation.LEGACY -> updateLegacyBatch(
                conversationId,
                NovaConversationTitle(
                    id = conversationId,
                    title = updated.title,
                    lastUpdated = updated.lastUpdated,
                    messageCount = messages.size,
                    userId = userId,
                ),
                updated,
            )
        }
        true
    }.getOrDefault(false)

    suspend fun loadConversation(conversationId: String, userId: String): List<NovaChatMessage> = loading {
        val source = loadConversationSource(conversationId, userId) ?: return@loading emptyList()
        source.conversation.messages.map { saved ->
            val text = encryptionService.decryptNovaData(saved.text, userId)
            val payload = decryptImageData(saved.imageData, userId)
            val grounding = decryptGroundingData(saved.groundingData, userId)
            val image = resolveHistoricalImage(payload, saved.id, source.conversation.id, userId)
            image.storagePath?.let {
                imageReferenceCache[cacheKey(source.conversation.id, saved.id)] = it
            }
            NovaSavedChatMessage(
                id = saved.id,
                text = text,
                isUser = saved.isUser,
                imageData = payload,
            ).toChatMessage(image.image, image.storagePath, grounding)
        }
    }.getOrElse { emptyList() }

    suspend fun deleteConversation(conversationId: String, userId: String): Boolean = loading {
        val scoped = userConversation(conversationId, userId).get().await()
        if (scoped.exists()) {
            scoped.data?.toStringAnyMap()?.let(NovaSavedConversation::fromFirestoreData)?.let {
                deleteStoredImages(it, userId)
            }
            scoped.reference.delete().await()
            clearImageCache(conversationId)
            return@loading true
        }

        val titleDocument = legacyTitles().document(conversationId).get().await()
        val title = titleDocument.data?.toStringAnyMap()?.let(NovaConversationTitle::fromFirestoreData)
        if (!titleDocument.exists() || title?.userId != userId) return@loading false

        legacyConversations().document(conversationId).get().await()
            .data?.toStringAnyMap()?.let(NovaSavedConversation::fromFirestoreData)
            ?.let { deleteStoredImages(it, userId) }

        db.batch().apply {
            delete(titleDocument.reference)
            delete(legacyConversations().document(conversationId))
        }.commit().await()
        clearImageCache(conversationId)
        true
    }.getOrDefault(false)

    // MARK: - Private

    private suspend fun encryptMessages(
        messages: List<NovaChatMessage>,
        userId: String,
        conversationId: String,
    ): List<NovaSavedChatMessage> =
        messages.filterNot { it.isSystem }.map { message ->
            val text = encryptedText(message, userId)
            val reference = resolveImageReference(message, userId, conversationId)
            NovaSavedChatMessage(
                id = message.id,
                text = text,
                isUser = message.isUser,
                imageData = encryptImageData(reference, userId),
                groundingData = encryptGroundingData(message, userId),
            )
        }

    private suspend fun encryptedText(message: NovaChatMessage, userId: String): String {
        val key = "$userId|${message.id}|${message.text.length}|${message.text.hashCode()}"
        encryptedTextCache[key]?.let { return it }
        val value = encryptionService.encryptNovaData(message.text, userId)
        if (encryptedTextCache.size >= ENCRYPTED_TEXT_CACHE_LIMIT) encryptedTextCache.clear()
        encryptedTextCache[key] = value
        return value
    }

    private suspend fun encryptImageData(imageData: String?, userId: String): String? {
        if (imageData == null) return null
        return encryptionService.encryptNovaData(imageData, userId)
    }

    private suspend fun decryptImageData(imageData: String?, userId: String): String? {
        if (imageData == null) return null
        return encryptionService.decryptNovaData(imageData, userId)
    }

    private suspend fun resolveImageReference(
        message: NovaChatMessage,
        userId: String,
        conversationId: String,
    ): String? {
        val key = cacheKey(conversationId, message.id)
        imageReferenceCache[key]?.takeIf { it.isNotEmpty() }?.let { return it }
        message.imageStoragePath?.takeIf { it.isNotEmpty() }?.let {
            imageReferenceCache[key] = it
            return it
        }
        val image = message.image ?: return null
        return StorageService.uploadNovaConversationImage(userId, conversationId, message.id, image)
            .also { imageReferenceCache[key] = it }
    }

    private suspend fun resolveHistoricalImage(
        payload: String?,
        messageId: String,
        conversationId: String,
        userId: String,
    ): ResolvedImage {
        if (payload.isNullOrEmpty()) return ResolvedImage(null, null)
        if (!NovaSavedChatMessage.looksLikeStorageReference(payload)) {
            return ResolvedImage(NovaSavedChatMessage.decodeLegacyInlineImage(payload), null)
        }
        return runCatching {
            ResolvedImage(
                StorageService.downloadNovaConversationImage(userId, conversationId, messageId, payload),
                payload,
            )
        }.getOrElse { ResolvedImage(null, payload) }
    }

    private suspend fun deleteStoredImages(conversation: NovaSavedConversation, userId: String) {
        for (saved in conversation.messages) {
            val payload = decryptImageData(saved.imageData, userId)
            if (NovaSavedChatMessage.looksLikeStorageReference(payload)) {
                runCatching { StorageService.deleteMedia(payload!!) }
            }
        }
    }

    private suspend fun encryptGroundingData(message: NovaChatMessage, userId: String): String? {
        if (message.groundingSources.isEmpty() && message.searchSuggestionsHtml == null) return null
        val json = JSONObject().apply {
            put("searchSuggestionsHTML", message.searchSuggestionsHtml)
            put(
                "sources",
                JSONArray(
                    message.groundingSources.map { source ->
                        JSONObject().put("title", source.title).put("url", source.url)
                    },
                ),
            )
        }.toString()
        return encryptionService.encryptNovaData(json, userId)
    }

    private suspend fun decryptGroundingData(data: String?, userId: String): NovaGroundingPayload? =
        runCatching {
            if (data == null) return null
            val objectData = JSONObject(encryptionService.decryptNovaData(data, userId))
            val sources = objectData.optJSONArray("sources")?.let { array ->
                List(array.length()) { index -> array.getJSONObject(index) }.map {
                    NovaGroundingSource(it.optString("title"), it.optString("url"))
                }
            }.orEmpty()
            NovaGroundingPayload(sources, objectData.optStringOrNull("searchSuggestionsHTML"))
        }.getOrNull()

    private suspend fun decryptTitle(data: Map<String, Any?>, userId: String): NovaConversationTitle? =
        NovaConversationTitle.fromFirestoreData(data)?.let {
            it.copy(title = encryptionService.decryptNovaData(it.title, userId))
        }

    private suspend fun decryptConversationTitle(
        data: Map<String, Any?>,
        userId: String,
    ): NovaConversationTitle? =
        NovaSavedConversation.fromFirestoreData(data)?.let { conversation ->
            NovaConversationTitle(
                id = conversation.id,
                title = encryptionService.decryptNovaData(conversation.title, userId),
                lastUpdated = conversation.lastUpdated,
                messageCount = conversation.messages.size,
                userId = conversation.userId,
            )
        }

    private suspend fun loadConversationSource(id: String, userId: String): LoadedConversation? {
        val userDocument = userConversation(id, userId).get().await()
        if (userDocument.exists()) {
            userDocument.data?.toStringAnyMap()?.let(NovaSavedConversation::fromFirestoreData)
                ?.takeIf { it.userId == userId }
                ?.let { return LoadedConversation(SourceLocation.USER_SCOPED, it) }
        }
        val legacyDocument = legacyConversations().document(id).get().await()
        if (legacyDocument.exists()) {
            legacyDocument.data?.toStringAnyMap()?.let(NovaSavedConversation::fromFirestoreData)
                ?.takeIf { it.userId == userId }
                ?.let { return LoadedConversation(SourceLocation.LEGACY, it) }
        }
        return null
    }

    private suspend fun generateTitle(
        messages: List<NovaChatMessage>,
        currentTitle: String? = null,
    ): String {
        val userMessages = messages.filter { it.isUser && it.text.isNotBlank() }
        val first = userMessages.firstOrNull()?.text ?: return fallbackTitle()
        if (userMessages.size <= 2) return makeSnippet(first).ifEmpty { fallbackTitle() }
        val transcript = messages
            .filter { !it.isSystem && it.text.isNotEmpty() }
            .take(8)
            .joinToString("\n") { "${if (it.isUser) "User" else "Nova"}: ${it.text}" }
        val prompt = """
            |${NovaPromptCatalog.conversationTitlePrompt}
            |Conversation:
            |$transcript
            |Current title: ${currentTitle ?: "none"}
        """.trimMargin()
        return runCatching { sanitize(ai.generateTitle(prompt)) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
            ?.take(50)
            ?: fallbackTitle()
    }

    private fun makeSnippet(value: String): String =
        sanitize(value.trim().replace('\n', ' ').split(Regex("\\s+")).take(5).joinToString(" "))

    private fun sanitize(value: String): String =
        value.trim().replace("\"", "").replace("'", "")

    private fun fallbackTitle(): String =
        requireContext().getString(
            R.string.nova_conversation_fallback_title,
            MomentsFormat.smartDate(Date(), MomentsFormat.DateContext.TIME_ONLY),
        )

    private suspend fun saveUserConversation(conversation: NovaSavedConversation, userId: String) {
        userConversation(conversation.id, userId)
            .set(conversation.toFirestoreData(), SetOptions.merge())
            .await()
    }

    private suspend fun updateUserConversation(conversation: NovaSavedConversation, userId: String) {
        userConversation(conversation.id, userId)
            .set(conversation.toFirestoreData(), SetOptions.merge())
            .await()
    }

    private suspend fun updateLegacyBatch(
        id: String,
        title: NovaConversationTitle,
        conversation: NovaSavedConversation,
    ) {
        db.batch().apply {
            update(legacyTitles().document(id), title.toFirestoreData())
            update(legacyConversations().document(id), conversation.toFirestoreData())
        }.commit().await()
    }

    private fun userConversations(userId: String) =
        db.collection("users").document(userId).collection("novaConversations")

    private fun userConversation(id: String, userId: String) =
        userConversations(userId).document(id)

    private fun legacyTitles() = db.collection("geminiConversationTitles")

    private fun legacyConversations() = db.collection("geminiConversations")

    private fun cacheKey(conversationId: String, messageId: String) = "$conversationId|$messageId"

    private fun clearImageCache(conversationId: String) {
        imageReferenceCache.keys.filter { it.startsWith("$conversationId|") }
            .forEach(imageReferenceCache::remove)
    }

    private fun legacyDrainedKey(userId: String) = "novaLegacyTitlesDrained-$userId"

    private fun legacyDrained(userId: String): Boolean =
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(legacyDrainedKey(userId), false)

    private fun markLegacyDrained(userId: String) {
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(legacyDrainedKey(userId), true)
            .apply()
    }

    private fun requireContext(): Context =
        appContext ?: error("NovaConversationStore.initialize(context) required")

    private fun Map<String, Any>.toStringAnyMap(): Map<String, Any?> =
        entries.associate { it.key to it.value }

    private suspend fun <T> loading(block: suspend () -> T): Result<T> {
        isLoading = true
        return runCatching { block() }
            .onFailure { lastError = it.message }
            .also { isLoading = false }
    }

    private data class ResolvedImage(val image: Bitmap?, val storagePath: String?)
    private data class LoadedConversation(val location: SourceLocation, val conversation: NovaSavedConversation)
    private enum class SourceLocation { USER_SCOPED, LEGACY }

    private const val PREFS = "nova_conversations"
    private const val ENCRYPTED_TEXT_CACHE_LIMIT = 600
}
