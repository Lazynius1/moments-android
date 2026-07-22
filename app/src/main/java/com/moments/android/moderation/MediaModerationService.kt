package com.moments.android.moderation

import android.graphics.Bitmap
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.MediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Base64
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume

enum class ModerationCategory(val raw: String) {
    ADULT("adult"),
    VIOLENCE("violence"),
    RACY("racy"),
    MEDICAL("medical"),
    SPOOFED("spoofed"),
    CLEAN("clean"),
    AUDIO_TOXIC("audio_toxic"),
    SYSTEM_ERROR("system_error"),
}

enum class ModerationContentType(val raw: String) {
    MOMENT("moment"),
    STORY("story"),
}

sealed class MediaModerationAction {
    data object Approved : MediaModerationAction()
    data class Deleted(val reason: String, val category: String) : MediaModerationAction()
    data class Warning(val reason: String, val category: String) : MediaModerationAction()
    data class Error(val message: String) : MediaModerationAction()
}

data class MediaModerationResult(
    val visualScore: Double,
    val audioScore: Double?,
    val combinedScore: Double,
    val action: MediaModerationAction,
    val details: Map<String, Any>,
)

private data class BackendMediaModerationResponse(
    val success: Boolean,
    val action: String,
    val reason: String,
    val category: String,
    val provider: String?,
    val fallbackUsed: Boolean?,
    val visualScore: Double?,
    val audioScore: Double?,
    val combinedScore: Double?,
) {
    companion object {
        fun fromJson(json: JSONObject) = BackendMediaModerationResponse(
            success = json.optBoolean("success"),
            action = json.optString("action"),
            reason = json.optString("reason"),
            category = json.optString("category"),
            provider = json.optStringOrNull("provider"),
            fallbackUsed = if (json.has("fallbackUsed") && !json.isNull("fallbackUsed")) json.optBoolean("fallbackUsed") else null,
            visualScore = json.optDoubleOrNull("visualScore"),
            audioScore = json.optDoubleOrNull("audioScore"),
            combinedScore = json.optDoubleOrNull("combinedScore"),
        )
    }
}

/** Port de MediaModerationService.swift — moderación de media vía Cloud Functions + Firestore. */
class MediaModerationService private constructor() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val functionsRegion = "europe-southwest1"
    private val backendModerationFunctionName = "moderateMediaContent"
    private val activeModerationTasks = mutableSetOf<String>()
    private val taskLock = Mutex()

    fun moderateMedia(
        mediaURL: String,
        mediaType: MediaItem.MediaType,
        userId: String,
        contentId: String? = null,
        contentType: ModerationContentType = ModerationContentType.MOMENT,
        mediaItemId: String? = null,
        completion: (MediaModerationAction) -> Unit,
    ) {
        val taskId = "${userId}_${contentId ?: UUID.randomUUID()}_${mediaItemId ?: mediaURL}"
        scope.launch {
            val shouldSkip = taskLock.withLock {
                if (activeModerationTasks.contains(taskId)) true
                else {
                    activeModerationTasks.add(taskId)
                    false
                }
            }
            if (shouldSkip) {
                withContext(Dispatchers.Main) { completion(MediaModerationAction.Approved) }
                return@launch
            }
            try {
                val result = when (mediaType) {
                    MediaItem.MediaType.IMAGE -> moderateImageAdvanced(
                        url = mediaURL,
                        userId = userId,
                        contentId = contentId,
                        contentType = contentType,
                        mediaItemId = mediaItemId,
                    )
                    MediaItem.MediaType.VIDEO -> moderateVideoAdvanced(
                        url = mediaURL,
                        userId = userId,
                        contentId = contentId,
                        contentType = contentType,
                        mediaItemId = mediaItemId,
                    )
                }
                withContext(Dispatchers.Main) { completion(result) }
            } finally {
                taskLock.withLock { activeModerationTasks.remove(taskId) }
            }
        }
    }

    fun moderateStickerImage(
        image: Bitmap,
        preserveAlpha: Boolean = false,
        userId: String,
        storyId: String,
        stickerId: String,
        completion: (MediaModerationAction) -> Unit,
    ) {
        scope.launch {
            val stream = java.io.ByteArrayOutputStream()
            if (preserveAlpha) {
                if (!image.compress(Bitmap.CompressFormat.PNG, 85, stream)) {
                    image.compress(Bitmap.CompressFormat.JPEG, 85, stream)
                }
            } else {
                if (!image.compress(Bitmap.CompressFormat.JPEG, 85, stream)) {
                    image.compress(Bitmap.CompressFormat.PNG, 85, stream)
                }
            }
            val imageData = stream.toByteArray()
            val result = requestBackendModeration(
                mediaType = "story_sticker",
                imageBase64 = Base64.getEncoder().encodeToString(imageData),
            )
            logModerationEvent(
                userId = userId,
                mediaURL = "story_sticker:$stickerId",
                mediaType = "story_sticker",
                contentType = "story",
                action = actionToString(result.action),
                reason = getReasonFromAction(result.action),
                category = getCategoryFromAction(result.action),
                contentId = storyId,
                visionData = mapOf(
                    "provider" to (result.details["provider"] ?: "backend"),
                    "fallbackUsed" to (result.details["fallbackUsed"] ?: false),
                    "stickerId" to stickerId,
                ),
            )
            withContext(Dispatchers.Main) { completion(result.action) }
        }
    }

    fun hideStoryStickerItems(
        userId: String,
        storyId: String,
        moderatedStickers: Map<String, MediaModerationAction>,
        completion: ((Boolean) -> Unit)? = null,
    ) {
        if (moderatedStickers.isEmpty()) {
            completion?.invoke(false)
            return
        }
        scope.launch {
            val db = FirebaseFirestore.getInstance()
            val storyRef = db.collection("users").document(userId).collection("stories").document(storyId)
            val moderatableTypes = setOf("frame", "selfie")
            try {
                val document = storyRef.get().awaitTask()
                if (!document.exists()) {
                    withContext(Dispatchers.Main) { completion?.invoke(false) }
                    return@launch
                }
                @Suppress("UNCHECKED_CAST")
                val contentData = document.data as Map<String, Any?>? ?: emptyMap()
                val stickers = (contentData["stickers"] as? List<Map<String, Any?>>)?.map { it.toMutableMap() }?.toMutableList()
                    ?: mutableListOf()
                if (stickers.isEmpty()) {
                    withContext(Dispatchers.Main) { completion?.invoke(false) }
                    return@launch
                }
                var updated = false
                var newlyHiddenCount = 0
                for (index in stickers.indices) {
                    val stickerId = stickers[index]["stickerId"] as? String ?: continue
                    val action = moderatedStickers[stickerId] ?: continue
                    val previousState = stickers[index]["moderationState"] as? String
                    if (previousState != "hidden") newlyHiddenCount++
                    stickers[index]["moderationState"] = "hidden"
                    stickers[index]["moderationReason"] = getReasonFromAction(action)
                    stickers[index]["moderationCategory"] = getCategoryFromAction(action)
                    stickers[index]["moderatedAt"] = Timestamp(Date())
                    updated = true
                }
                if (!updated) {
                    withContext(Dispatchers.Main) { completion?.invoke(false) }
                    return@launch
                }
                val totalModeratableCount = stickers.count { (it["type"] as? String) in moderatableTypes }
                val primaryAction = moderatedStickers.values.firstOrNull()
                    ?: MediaModerationAction.Deleted("Contenido inapropiado", "general")
                val updateData = mutableMapOf<String, Any>(
                    "stickers" to stickers,
                    "moderatedAt" to FieldValue.serverTimestamp(),
                    "moderatedBy" to "auto_moderation",
                )
                when (primaryAction) {
                    is MediaModerationAction.Deleted -> {
                        updateData["moderationReason"] = primaryAction.reason
                        updateData["moderationCategory"] = primaryAction.category
                    }
                    is MediaModerationAction.Warning -> {
                        updateData["moderationReason"] = "Advertencia: ${primaryAction.reason}"
                        updateData["moderationCategory"] = primaryAction.category
                    }
                    else -> Unit
                }
                storyRef.update(updateData).awaitTask()
                queueHiddenModerationReview(
                    userId = userId,
                    contentId = storyId,
                    contentType = ModerationContentType.STORY,
                    mediaItemId = null,
                    mediaURL = "story_sticker:${moderatedStickers.keys.firstOrNull() ?: "unknown"}",
                    mediaType = "story_sticker",
                    action = primaryAction,
                )
                if (newlyHiddenCount > 0) {
                    createModerationNotification(
                        userId = userId,
                        contentId = storyId,
                        contentType = ModerationContentType.STORY,
                        moderationType = "partial",
                        moderatedMediaCount = newlyHiddenCount,
                        totalMediaCount = totalModeratableCount,
                        moderatedMediaIndex = null,
                        moderationCategory = getCategoryFromAction(primaryAction),
                        moderationScope = "storySticker",
                    )
                }
                withContext(Dispatchers.Main) { completion?.invoke(true) }
            } catch (_: Exception) {
                withContext(Dispatchers.Main) { completion?.invoke(false) }
            }
        }
    }

    fun queueStoryStickerReviewItem(
        userId: String,
        storyId: String,
        stickerId: String,
        action: MediaModerationAction,
        details: Map<String, Any> = emptyMap(),
    ) {
        val warning = action as? MediaModerationAction.Warning ?: return
        scope.launch {
            val db = FirebaseFirestore.getInstance()
            val storyRef = db.collection("users").document(userId).collection("stories").document(storyId)
            val queueRef = db.collection("moderationReviewQueue").document()
            val batch = db.batch()
            batch.set(
                queueRef,
                mapOf(
                    "status" to "pending",
                    "reviewSource" to "auto_warning",
                    "userId" to userId,
                    "contentId" to storyId,
                    "contentType" to ModerationContentType.STORY.raw,
                    "moderationScope" to "storySticker",
                    "mediaType" to "story_sticker",
                    "stickerId" to stickerId,
                    "reason" to warning.reason,
                    "category" to warning.category,
                    "provider" to (details["provider"] ?: "backend"),
                    "fallbackUsed" to (details["fallbackUsed"] ?: false),
                    "contentVisible" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            )
            batch.set(
                storyRef,
                mapOf(
                    "reviewRequired" to true,
                    "moderationReason" to "Advertencia: ${warning.reason}",
                    "moderationCategory" to warning.category,
                    "moderatedAt" to FieldValue.serverTimestamp(),
                    "moderatedBy" to "auto_moderation",
                    "isModerationHidden" to false,
                ),
                com.google.firebase.firestore.SetOptions.merge(),
            )
            batch.commit().awaitTask()
        }
    }

    private suspend fun moderateImageAdvanced(
        url: String,
        userId: String,
        contentId: String?,
        contentType: ModerationContentType,
        mediaItemId: String?,
    ): MediaModerationAction {
        val result = requestBackendModeration(
            mediaURL = url,
            mediaType = "image",
            contentId = contentId,
            contentType = contentType,
            mediaItemId = mediaItemId,
        )
        logModerationEvent(
            userId = userId,
            mediaURL = url,
            mediaType = "image",
            contentType = contentType.raw,
            action = actionToString(result.action),
            reason = getReasonFromAction(result.action),
            category = getCategoryFromAction(result.action),
            contentId = contentId,
            visionData = result.details,
        )
        return handleModerationResult(
            result = result,
            userId = userId,
            contentId = contentId,
            contentType = contentType,
            mediaItemId = mediaItemId,
            mediaURL = url,
            mediaType = "image",
        )
    }

    private suspend fun moderateVideoAdvanced(
        url: String,
        userId: String,
        contentId: String?,
        contentType: ModerationContentType,
        mediaItemId: String?,
    ): MediaModerationAction {
        val result = requestBackendModeration(
            mediaURL = url,
            mediaType = "video",
            contentId = contentId,
            contentType = contentType,
            mediaItemId = mediaItemId,
        )
        logModerationEvent(
            userId = userId,
            mediaURL = url,
            mediaType = "video",
            contentType = contentType.raw,
            action = actionToString(result.action),
            reason = getReasonFromAction(result.action),
            category = getCategoryFromAction(result.action),
            contentId = contentId,
            visionData = result.details,
        )
        return handleModerationResult(
            result = result,
            userId = userId,
            contentId = contentId,
            contentType = contentType,
            mediaItemId = mediaItemId,
            mediaURL = url,
            mediaType = "video",
        )
    }

    private suspend fun handleModerationResult(
        result: MediaModerationResult,
        userId: String,
        contentId: String?,
        contentType: ModerationContentType,
        mediaItemId: String?,
        mediaURL: String,
        mediaType: String,
    ): MediaModerationAction {
        if (contentId == null) return result.action
        when (val action = result.action) {
            is MediaModerationAction.Deleted -> {
                if (contentType == ModerationContentType.MOMENT && mediaItemId != null) {
                    if (isHiddenLayerMediaItemId(mediaItemId)) {
                        return MediaModerationAction.Approved
                    }
                    if (!isHiddenLayerMediaItemId(mediaItemId)) {
                        hideMomentMediaItem(
                            userId = userId,
                            contentId = contentId,
                            mediaItemId = mediaItemId,
                            action = action,
                            mediaURL = mediaURL,
                            mediaType = mediaType,
                            visualScore = result.visualScore,
                            audioScore = result.audioScore,
                            combinedScore = result.combinedScore,
                            details = result.details,
                        )
                        return MediaModerationAction.Approved
                    }
                }
                hideContentUsingOnlyMe(
                    userId = userId,
                    contentId = contentId,
                    contentType = contentType,
                    action = action,
                    mediaURL = mediaURL,
                    mediaType = mediaType,
                    visualScore = result.visualScore,
                    audioScore = result.audioScore,
                    combinedScore = result.combinedScore,
                    details = result.details,
                )
                return MediaModerationAction.Approved
            }
            is MediaModerationAction.Warning -> {
                queueContentForManualReview(
                    userId = userId,
                    contentId = contentId,
                    contentType = contentType,
                    mediaItemId = mediaItemId,
                    mediaURL = mediaURL,
                    mediaType = mediaType,
                    action = action,
                    visualScore = result.visualScore,
                    audioScore = result.audioScore,
                    combinedScore = result.combinedScore,
                    details = result.details,
                )
                return MediaModerationAction.Approved
            }
            else -> return result.action
        }
    }

    private suspend fun requestBackendModeration(
        mediaURL: String? = null,
        mediaType: String,
        imageBase64: String? = null,
        contentId: String? = null,
        contentType: ModerationContentType? = null,
        mediaItemId: String? = null,
    ): MediaModerationResult {
        val url = cloudFunctionURL(backendModerationFunctionName)
            ?: return systemWarningResult("backend_unavailable")
        val body = JSONObject().apply {
            put("mediaType", mediaType)
            mediaURL?.let { put("mediaURL", it) }
            imageBase64?.let { put("imageBase64", it) }
            contentId?.let { put("contentId", it) }
            contentType?.let { put("contentType", it.raw) }
            mediaItemId?.let { put("mediaItemId", it) }
        }
        val token = fetchIdToken() ?: return systemWarningResult("backend_missing_auth")
        return try {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Authorization", "Bearer $token")
                doOutput = true
                connectTimeout = 120_000
                readTimeout = 120_000
                outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val responseText = (if (connection.responseCode in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            connection.disconnect()
            if (responseText.isEmpty()) return systemWarningResult("backend_empty_response")
            mediaModerationResult(BackendMediaModerationResponse.fromJson(JSONObject(responseText)))
        } catch (error: Exception) {
            systemWarningResult("backend_request_failed", error.localizedMessage ?: "")
        }
    }

    private fun mediaModerationResult(response: BackendMediaModerationResponse): MediaModerationResult {
        val action: MediaModerationAction = when (response.action) {
            "deleted" -> MediaModerationAction.Deleted(response.reason, response.category)
            "warning" -> MediaModerationAction.Warning(response.reason, response.category)
            else -> MediaModerationAction.Approved
        }
        return MediaModerationResult(
            visualScore = response.visualScore ?: 0.0,
            audioScore = response.audioScore,
            combinedScore = response.combinedScore ?: response.visualScore ?: 0.0,
            action = action,
            details = mapOf(
                "provider" to (response.provider ?: "backend"),
                "fallbackUsed" to (response.fallbackUsed ?: false),
            ),
        )
    }

    private fun systemWarningResult(provider: String, error: String = ""): MediaModerationResult =
        MediaModerationResult(
            visualScore = 0.0,
            audioScore = null,
            combinedScore = 0.0,
            action = MediaModerationAction.Warning(
                reason = "Revisión manual pendiente por indisponibilidad temporal del sistema de moderación",
                category = ModerationCategory.SYSTEM_ERROR.raw,
            ),
            details = buildMap {
                put("provider", provider)
                if (error.isNotEmpty()) put("error", error)
            },
        )

    private suspend fun queueContentForManualReview(
        userId: String,
        contentId: String,
        contentType: ModerationContentType,
        mediaItemId: String?,
        mediaURL: String,
        mediaType: String,
        action: MediaModerationAction,
        visualScore: Double? = null,
        audioScore: Double? = null,
        combinedScore: Double? = null,
        details: Map<String, Any> = emptyMap(),
    ) {
        val warning = action as? MediaModerationAction.Warning ?: return
        val db = FirebaseFirestore.getInstance()
        val collectionName = if (contentType == ModerationContentType.MOMENT) "moments" else "stories"
        val contentRef = db.collection("users").document(userId).collection(collectionName).document(contentId)
        val contentUpdate = mutableMapOf<String, Any>(
            "reviewRequired" to true,
            "moderationReason" to "Advertencia: ${warning.reason}",
            "moderationCategory" to warning.category,
            "moderationDetails" to details,
            "moderatedAt" to FieldValue.serverTimestamp(),
            "moderatedBy" to "auto_moderation",
            "isModerationHidden" to false,
        )
        visualScore?.let { contentUpdate["visualScore"] = it }
        audioScore?.let { contentUpdate["audioScore"] = it }
        combinedScore?.let { contentUpdate["combinedScore"] = it }
        val batch = db.batch()
        batch.set(
            db.collection("moderationReviewQueue").document(),
            makeModerationQueuePayload(
                userId, contentId, contentType, mediaItemId, mediaURL, mediaType,
                warning.reason, warning.category, details, visualScore, audioScore, combinedScore,
                contentVisible = true, reviewSource = "auto_warning",
            ),
        )
        batch.set(contentRef, contentUpdate, com.google.firebase.firestore.SetOptions.merge())
        batch.commit().awaitTask()
    }

    private fun queueHiddenModerationReview(
        userId: String,
        contentId: String,
        contentType: ModerationContentType,
        mediaItemId: String? = null,
        mediaURL: String,
        mediaType: String,
        action: MediaModerationAction,
        visualScore: Double? = null,
        audioScore: Double? = null,
        combinedScore: Double? = null,
        details: Map<String, Any> = emptyMap(),
    ) {
        scope.launch {
            FirebaseFirestore.getInstance().collection("moderationReviewQueue").add(
                makeModerationQueuePayload(
                    userId, contentId, contentType, mediaItemId, mediaURL, mediaType,
                    getReasonFromAction(action), getCategoryFromAction(action), details,
                    visualScore, audioScore, combinedScore,
                    contentVisible = false, reviewSource = "auto_hidden",
                ),
            ).awaitTask()
        }
    }

    private fun makeModerationQueuePayload(
        userId: String,
        contentId: String,
        contentType: ModerationContentType,
        mediaItemId: String?,
        mediaURL: String,
        mediaType: String,
        reason: String,
        category: String,
        details: Map<String, Any>,
        visualScore: Double?,
        audioScore: Double?,
        combinedScore: Double?,
        contentVisible: Boolean,
        reviewSource: String,
    ): Map<String, Any> {
        val queueData = mutableMapOf<String, Any>(
            "status" to "pending",
            "reviewSource" to reviewSource,
            "userId" to userId,
            "contentId" to contentId,
            "contentType" to contentType.raw,
            "moderationScope" to moderationScope(contentType, mediaItemId, mediaType),
            "mediaURL" to mediaURL,
            "mediaType" to mediaType,
            "reason" to reason,
            "category" to category,
            "provider" to (details["provider"] ?: "backend"),
            "fallbackUsed" to (details["fallbackUsed"] ?: false),
            "contentVisible" to contentVisible,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        mediaItemId?.let { queueData["mediaItemId"] = it }
        visualScore?.let { queueData["visualScore"] = it }
        audioScore?.let { queueData["audioScore"] = it }
        combinedScore?.let { queueData["combinedScore"] = it }
        if (details.isNotEmpty()) queueData["details"] = details
        return queueData
    }

    private fun moderationScope(contentType: ModerationContentType, mediaItemId: String?, mediaType: String): String {
        if (mediaType == "story_sticker") return "storySticker"
        if (contentType == ModerationContentType.STORY) return "story"
        if (mediaItemId != null && isHiddenLayerMediaItemId(mediaItemId)) return "postHiddenLayer"
        return "post"
    }

    private suspend fun hideMomentMediaItem(
        userId: String,
        contentId: String,
        mediaItemId: String,
        action: MediaModerationAction,
        mediaURL: String,
        mediaType: String,
        visualScore: Double? = null,
        audioScore: Double? = null,
        combinedScore: Double? = null,
        details: Map<String, Any> = emptyMap(),
    ) {
        val db = FirebaseFirestore.getInstance()
        val contentRef = db.collection("users").document(userId).collection("moments").document(contentId)
        val document = contentRef.get().awaitTask()
        if (!document.exists()) {
            hideContentUsingOnlyMe(userId, contentId, ModerationContentType.MOMENT, action, mediaURL, mediaType, visualScore, audioScore, combinedScore, details)
            return
        }
        @Suppress("UNCHECKED_CAST")
        val contentData = document.data as Map<String, Any?>? ?: return
        val mediaItems = (contentData["mediaItems"] as? List<Map<String, Any?>>)?.map { it.toMutableMap() }?.toMutableList()
            ?: run {
                hideContentUsingOnlyMe(userId, contentId, ModerationContentType.MOMENT, action, mediaURL, mediaType, visualScore, audioScore, combinedScore, details)
                return
            }
        if (mediaItems.isEmpty()) {
            hideContentUsingOnlyMe(userId, contentId, ModerationContentType.MOMENT, action, mediaURL, mediaType, visualScore, audioScore, combinedScore, details)
            return
        }
        val confidence = determineConfidence(visualScore ?: 0.0, audioScore ?: 0.0, combinedScore ?: 0.0)
        val matchedIndex = mediaItems.indexOfFirst {
            (it["id"] as? String) == mediaItemId || (it["url"] as? String) == mediaURL
        }
        if (matchedIndex < 0) {
            hideContentUsingOnlyMe(userId, contentId, ModerationContentType.MOMENT, action, mediaURL, mediaType, visualScore, audioScore, combinedScore, details)
            return
        }
        val item = mediaItems[matchedIndex]
        item["moderationState"] = MediaItem.ModerationState.HIDDEN.raw
        item["moderationReason"] = getReasonFromAction(action)
        item["moderationCategory"] = getCategoryFromAction(action)
        item["moderationConfidence"] = confidence
        item["moderatedAt"] = Timestamp(Date())
        mediaItems[matchedIndex] = item
        val hiddenCount = mediaItems.count { (it["moderationState"] as? String) == MediaItem.ModerationState.HIDDEN.raw }
        if (hiddenCount >= mediaItems.size) {
            contentRef.update("mediaItems", mediaItems).awaitTask()
            hideContentUsingOnlyMe(userId, contentId, ModerationContentType.MOMENT, action, mediaURL, mediaType, visualScore, audioScore, combinedScore, details)
            return
        }
        val updateData = mutableMapOf<String, Any>(
            "mediaItems" to mediaItems,
            "moderatedAt" to FieldValue.serverTimestamp(),
            "moderatedBy" to "auto_moderation",
        )
        when (action) {
            is MediaModerationAction.Deleted -> {
                updateData["moderationReason"] = action.reason
                updateData["moderationCategory"] = action.category
                updateData["confidence"] = confidence
            }
            is MediaModerationAction.Warning -> {
                updateData["moderationReason"] = "Advertencia: ${action.reason}"
                updateData["moderationCategory"] = action.category
                updateData["confidence"] = confidence
            }
            else -> Unit
        }
        contentRef.update(updateData).awaitTask()
        queueHiddenModerationReview(userId, contentId, ModerationContentType.MOMENT, mediaItemId, mediaURL, mediaType, action, visualScore, audioScore, combinedScore, details)
        createModerationNotification(
            userId = userId,
            contentId = contentId,
            contentType = ModerationContentType.MOMENT,
            moderationType = "partial",
            moderatedMediaCount = hiddenCount,
            totalMediaCount = mediaItems.size,
            moderatedMediaIndex = matchedIndex + 1,
            moderationCategory = getCategoryFromAction(action),
            moderationScope = "post",
        )
    }

    private suspend fun hideContentUsingOnlyMe(
        userId: String,
        contentId: String,
        contentType: ModerationContentType,
        action: MediaModerationAction,
        mediaURL: String,
        mediaType: String,
        visualScore: Double? = null,
        audioScore: Double? = null,
        combinedScore: Double? = null,
        details: Map<String, Any> = emptyMap(),
    ) {
        val db = FirebaseFirestore.getInstance()
        val collectionName = if (contentType == ModerationContentType.MOMENT) "moments" else "stories"
        val contentRef = db.collection("users").document(userId).collection(collectionName).document(contentId)
        val document = contentRef.get().awaitTask()
        if (!document.exists()) return
        @Suppress("UNCHECKED_CAST")
        val contentData = document.data as Map<String, Any?>? ?: return
        val originalAudience = contentData["audience"] as? String ?: "everyone"
        val hideData = mutableMapOf<String, Any>(
            "audience" to "onlyMe",
            "moderatedAt" to FieldValue.serverTimestamp(),
            "moderatedBy" to "auto_moderation",
            "originalAudience" to originalAudience,
            "isModerationHidden" to true,
            "reviewRequired" to true,
            "canRestore" to true,
        )
        when (action) {
            is MediaModerationAction.Deleted -> {
                hideData["moderationReason"] = action.reason
                hideData["moderationCategory"] = action.category
                hideData["confidence"] = determineConfidence(visualScore ?: 0.0, audioScore ?: 0.0, combinedScore ?: 0.0)
            }
            is MediaModerationAction.Warning -> {
                hideData["moderationReason"] = "Advertencia: ${action.reason}"
                hideData["moderationCategory"] = action.category
                hideData["confidence"] = "medium"
            }
            else -> {
                hideData["moderationReason"] = "Contenido flagged por moderación automática"
                hideData["moderationCategory"] = "general"
                hideData["confidence"] = "unknown"
            }
        }
        hideData["moderationDetails"] = details
        hideData["originalMediaURL"] = mediaURL
        hideData["mediaType"] = mediaType
        visualScore?.let { hideData["visualScore"] = it }
        audioScore?.let { hideData["audioScore"] = it }
        combinedScore?.let { hideData["combinedScore"] = it }
        contentRef.update(hideData).awaitTask()
        queueHiddenModerationReview(userId, contentId, contentType, null, mediaURL, mediaType, action, visualScore, audioScore, combinedScore, details)
        createModerationNotification(
            userId = userId,
            contentId = contentId,
            contentType = contentType,
            moderationType = "full",
            moderatedMediaCount = 0,
            totalMediaCount = 0,
            moderatedMediaIndex = null,
            moderationCategory = null,
            moderationScope = if (contentType == ModerationContentType.STORY) "story" else "post",
        )
    }

    private fun createModerationNotification(
        userId: String,
        contentId: String,
        contentType: ModerationContentType,
        moderationType: String,
        moderatedMediaCount: Int,
        totalMediaCount: Int,
        moderatedMediaIndex: Int?,
        moderationCategory: String?,
        moderationScope: String,
    ) {
        val db = FirebaseFirestore.getInstance()
        val notificationId = "moderation_${contentId}_${System.currentTimeMillis() / 1000}"
        val notificationRef = db.collection("users").document(userId).collection("notifications").document(notificationId)
        val notificationData = mutableMapOf<String, Any>(
            "type" to "mediaModeration",
            "senderId" to "system_moderation",
            "senderUsername" to "Moments",
            "moderationType" to moderationType,
            "moderationScope" to moderationScope,
            "moderatedMediaCount" to moderatedMediaCount,
            "totalMediaCount" to totalMediaCount,
            "timestamp" to FieldValue.serverTimestamp(),
            "isPending" to true,
        )
        if (contentType == ModerationContentType.STORY) notificationData["storyId"] = contentId
        else notificationData["momentId"] = contentId
        moderatedMediaIndex?.let { notificationData["moderatedMediaIndex"] = it }
        moderationCategory?.let { notificationData["moderationCategory"] = it }
        notificationRef.set(notificationData)
    }

    private fun logModerationEvent(
        userId: String,
        mediaURL: String,
        mediaType: String,
        contentType: String,
        action: String,
        reason: String,
        category: String,
        contentId: String?,
        visionData: Map<String, Any>? = null,
    ) {
        scope.launch {
            val logData = mutableMapOf<String, Any>(
                "userId" to userId,
                "mediaURL" to mediaURL,
                "mediaType" to mediaType,
                "contentType" to contentType,
                "action" to action,
                "reason" to reason,
                "category" to category,
                "timestamp" to FieldValue.serverTimestamp(),
            )
            contentId?.let { logData["contentId"] = it }
            visionData?.let {
                logData["visionData"] = it
                (it["provider"] as? String)?.let { provider -> logData["provider"] = provider }
                (it["fallbackUsed"] as? Boolean)?.let { fallback -> logData["fallbackUsed"] = fallback }
            }
            FirebaseFirestore.getInstance().collection("mediaModerationLogs").add(logData)
        }
    }

    private fun isHiddenLayerMediaItemId(mediaItemId: String): Boolean = mediaItemId.startsWith("hiddenLayer_")

    private fun actionToString(action: MediaModerationAction): String = when (action) {
        MediaModerationAction.Approved -> "approved"
        is MediaModerationAction.Deleted -> "auto_deleted_silent"
        is MediaModerationAction.Warning -> "flagged_for_review"
        is MediaModerationAction.Error -> "moderation_error"
    }

    private fun getReasonFromAction(action: MediaModerationAction): String = when (action) {
        is MediaModerationAction.Deleted -> action.reason
        is MediaModerationAction.Warning -> action.reason
        MediaModerationAction.Approved -> "Contenido apropiado"
        is MediaModerationAction.Error -> action.message
    }

    private fun getCategoryFromAction(action: MediaModerationAction): String = when (action) {
        is MediaModerationAction.Deleted -> action.category
        is MediaModerationAction.Warning -> action.category
        MediaModerationAction.Approved -> ModerationCategory.CLEAN.raw
        is MediaModerationAction.Error -> ModerationCategory.SYSTEM_ERROR.raw
    }

    private fun determineConfidence(visualScore: Double, audioScore: Double, combinedScore: Double): String {
        val maxScore = maxOf(visualScore, audioScore, combinedScore)
        return when {
            maxScore >= 0.9 -> "very_high"
            maxScore >= 0.7 -> "high"
            maxScore >= 0.5 -> "medium"
            maxScore >= 0.3 -> "low"
            else -> "very_low"
        }
    }

    private fun cloudFunctionURL(functionName: String): String? {
        val projectId = FirebaseApp.getInstance().options.projectId ?: return null
        return "https://$functionsRegion-$projectId.cloudfunctions.net/$functionName"
    }

    private suspend fun fetchIdToken(): String? = suspendCancellableCoroutine { cont ->
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null) {
            cont.resume(null)
            return@suspendCancellableCoroutine
        }
        user.getIdToken(false).addOnCompleteListener { task ->
            cont.resume(if (task.isSuccessful) task.result?.token else null)
        }
    }

    companion object {
        val shared: MediaModerationService by lazy { MediaModerationService() }
    }
}

private fun JSONObject.optStringOrNull(key: String): String? =
    if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

private fun JSONObject.optDoubleOrNull(key: String): Double? =
    if (!has(key) || isNull(key)) null else optDouble(key)

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { cont ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) cont.resume(task.result)
            else cont.cancel(task.exception ?: RuntimeException("Task failed"))
        }
    }
