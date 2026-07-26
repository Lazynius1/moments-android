package com.moments.android.services.network

import android.graphics.BitmapFactory
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.models.BlockActionPayload
import com.moments.android.views.messaging.core.ChatMediaPurpose
import com.moments.android.models.CommentMentionEntity
import com.moments.android.models.CommentPayload
import com.moments.android.models.DeleteCommentPayload
import com.moments.android.models.DeleteMomentPayload
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.models.FollowActionPayload
import com.moments.android.models.FollowRequestActionPayload
import com.moments.android.models.MarkAsReadPayload
import com.moments.android.models.MediaMessagePayload
import com.moments.android.models.MessagePayload
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.models.ProfileUpdatePayload
import com.moments.android.models.ReactionPayload
import com.moments.android.models.ReportActionPayload
import com.moments.android.models.SavePayload
import com.moments.android.models.cache.CachedAction
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.addComment
import com.moments.android.services.firestore.deleteComment
import com.moments.android.services.firestore.toggleSaveMoment
import com.moments.android.services.firestore.updateProfilePicture
import com.moments.android.services.messaging.ChatCacheStore
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.storage.StorageService
import com.moments.android.views.creator.BackgroundMomentUploadService
import com.moments.android.views.creator.BackgroundStoryUploadService
import com.moments.android.views.messaging.services.ChatService
import java.util.Date
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.tasks.await
import org.json.JSONObject

/**
 * Port de `OfflineSyncService.swift`.
 * Cola offline + backoff exponencial (30s…1h, max 8 intentos) + optimización de toggles.
 */
object OfflineSyncService {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val syncMutex = Mutex()

    private var isAutomaticSyncEnabled = false
    private var connectivityListenerStarted = false

    private const val MAX_ATTEMPTS_PER_ACTION = 8
    private const val BASE_RETRY_DELAY_SEC = 30L
    private const val MAX_RETRY_DELAY_SEC = 3600L

    private val firestoreService = FirestoreService()
    private val db = FirebaseFirestore.getInstance()

    init {
        // iOS: `setupConnectivityListener()` en `init` (solo actúa si automatic sync está on).
        setupConnectivityListener()
    }

    fun enableAutomaticSync() {
        if (isAutomaticSyncEnabled) return
        isAutomaticSyncEnabled = true

        // Disparar sincronización inicial cuando la UI ya tuvo tiempo de arrancar.
        scope.launch {
            delay(2_000)
            syncPendingActions()
        }
    }

    private fun setupConnectivityListener() {
        if (connectivityListenerStarted) return
        connectivityListenerStarted = true
        NetworkMonitor.isConnectedFlow
            .onEach { connected ->
                if (isAutomaticSyncEnabled && connected) {
                    scope.launch { syncPendingActions() }
                }
            }
            .launchIn(scope)
    }

    suspend fun retryFromUserAction() {
        syncPendingActions(requireAutomaticSync = false, ignoreBackoff = true)
    }

    suspend fun syncPendingActions(
        requireAutomaticSync: Boolean = true,
        ignoreBackoff: Boolean = false,
    ) {
        if (requireAutomaticSync) {
            if (!isAutomaticSyncEnabled || !NetworkMonitor.isConnected) return
        } else if (!NetworkMonitor.isConnected) {
            return
        }

        if (!syncMutex.tryLock()) return
        try {
            var pendingActions = LocalPersistenceService.loadPendingActions()
            if (pendingActions.isEmpty()) return

            pendingActions = optimizePendingActions(pendingActions)
            if (pendingActions.isEmpty()) return

            for (action in pendingActions) {
                if (!NetworkMonitor.isConnected) break
                if (action.retryCount >= MAX_ATTEMPTS_PER_ACTION) {
                    handleExhaustedAction(action)
                    continue
                }
                if (!ignoreBackoff && !isReadyForAttempt(action)) continue

                LocalPersistenceService.markActionAttempt(action.id)
                executeAction(action)
            }
        } finally {
            syncMutex.unlock()
        }
    }

    private fun isReadyForAttempt(action: CachedAction): Boolean {
        if (action.retryCount <= 0 || action.lastAttemptAt == null) return true
        val delaySec = min(
            BASE_RETRY_DELAY_SEC * 2.0.pow(action.retryCount - 1).toLong(),
            MAX_RETRY_DELAY_SEC,
        )
        val elapsed = (Date().time - action.lastAttemptAt.time) / 1000
        return elapsed >= delaySec
    }

    private suspend fun handleExhaustedAction(action: CachedAction) {
        when (action.type) {
            CachedAction.ActionType.MESSAGE.raw -> {
                decodeMessagePayload(action.payloadData)?.let { payload ->
                    markQueuedMessageFailed(payload.message.conversationId, payload.message.id)
                }
            }
            CachedAction.ActionType.MEDIA_MESSAGE.raw -> {
                decodeMediaMessagePayload(action.payloadData)?.let { payload ->
                    markQueuedMessageFailed(payload.conversationId, payload.messageId)
                }
            }
        }
        LocalPersistenceService.deleteAction(action.id)
    }

    private fun markQueuedMessageFailed(conversationId: String, messageId: String) {
        LocalPersistenceService.updateCachedMessageStatus(conversationId, messageId, MessageStatus.FAILED)
        ChatService.updateLocalMessageStatus(conversationId, messageId, MessageStatus.FAILED)
    }

    private suspend fun executeAction(action: CachedAction) {
        LocalPersistenceService.updateActionStatus(action.id, CachedAction.ActionStatus.EXECUTING)
        when (action.type) {
            CachedAction.ActionType.MOMENT_UPLOAD.raw ->
                BackgroundMomentUploadService.resumeUpload(action)

            CachedAction.ActionType.STORY_UPLOAD.raw ->
                BackgroundStoryUploadService.resumeUpload(action)

            CachedAction.ActionType.REACTION.raw -> {
                decodeReactionPayload(action.payloadData)?.let { payload ->
                    runCatching {
                        firestoreService.addReaction(
                            payload.momentId, payload.reaction, payload.userId, payload.authorId,
                        )
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.COMMENT.raw -> {
                decodeCommentPayload(action.payloadData)?.let { payload ->
                    runCatching {
                        firestoreService.addComment(
                            momentId = payload.momentId,
                            userId = payload.authorId,
                            authorId = payload.senderId,
                            content = payload.content,
                            parentCommentId = payload.parentCommentId,
                            commentId = payload.commentId,
                            mentions = payload.mentions,
                        )
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.MESSAGE.raw -> {
                decodeMessagePayload(action.payloadData)?.let { payload ->
                    ChatService.sendMessage(payload.message, payload.useServerTimestamp)
                        .onSuccess { sent ->
                            if (sent.status != MessageStatus.PENDING) {
                                LocalPersistenceService.deleteAction(action.id)
                            }
                        }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.MEDIA_MESSAGE.raw -> {
                decodeMediaMessagePayload(action.payloadData)?.let { payload ->
                    val type = MessageType.from(payload.typeRaw)
                    // iOS: bytes desde ChatCacheStore.decryptedMediaURL → Data(contentsOf:)
                    val mediaFile = ChatCacheStore.decryptedMediaFile(
                        payload.conversationId,
                        payload.messageId,
                        ChatMediaPurpose.PRIMARY,
                        payload.fileExtension,
                    )
                    val mediaData = runCatching { mediaFile.readBytes() }.getOrNull()
                    if (mediaData == null) {
                        // Sin bytes no hay reenvío posible: marcar fallido y retirar.
                        markQueuedMessageFailed(payload.conversationId, payload.messageId)
                        LocalPersistenceService.deleteAction(action.id)
                    } else {
                        val result = if (type == MessageType.AUDIO) {
                            ChatService.sendAudioMessage(
                                payload.conversationId, payload.senderId, mediaData,
                                payload.duration ?: 0.0, payload.audioWaveform, payload.messageId,
                                payload.isVanishModeMessage,
                            )
                        } else {
                            ChatService.sendMediaMessage(
                                payload.conversationId, payload.senderId, type, mediaData,
                                payload.fileName, payload.messageId, payload.mediaBatchId,
                                payload.isVanishModeMessage, payload.vanishExpiresAt, payload.replyTo,
                            )
                        }
                        result.onSuccess { sent ->
                            if (sent.status != MessageStatus.PENDING) {
                                LocalPersistenceService.deleteAction(action.id)
                            }
                        }
                    }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.DELETE_COMMENT.raw -> {
                decodeDeleteCommentPayload(action.payloadData)?.let { payload ->
                    runCatching {
                        firestoreService.deleteComment(
                            payload.momentId, payload.commentId, payload.userId, payload.authorId,
                        )
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.FOLLOW.raw -> {
                decodeFollowPayload(action.payloadData)?.let { payload ->
                    runCatching {
                        if (payload.isFollow) {
                            firestoreService.followUser(payload.followerId, payload.followedId)
                        } else {
                            firestoreService.unfollowUser(payload.followerId, payload.followedId)
                        }
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.SAVE.raw -> {
                decodeSavePayload(action.payloadData)?.let { payload ->
                    runCatching {
                        firestoreService.toggleSaveMoment(payload.userId, payload.momentId)
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.BLOCK.raw -> {
                decodeBlockPayload(action.payloadData)?.let { payload ->
                    runCatching {
                        if (payload.isBlock) {
                            firestoreService.blockUser(payload.currentUserId, payload.targetUserId)
                        } else {
                            firestoreService.unblockUser(payload.currentUserId, payload.targetUserId)
                        }
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.UPDATE_PROFILE.raw -> {
                decodeProfileUpdatePayload(action.payloadData)?.let { payload ->
                    if (payload.isImageUpdate && payload.profileImageLocalPath != null) {
                        val bitmap = BitmapFactory.decodeFile(payload.profileImageLocalPath)
                        if (bitmap == null) {
                            LocalPersistenceService.deleteAction(action.id)
                        } else {
                            runCatching {
                                val url = StorageService.uploadProfileImage(payload.userId, bitmap)
                                firestoreService.updateProfilePicture(payload.userId, url)
                            }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                        }
                    } else {
                        runCatching {
                            firestoreService.updateProfileDetails(
                                userId = payload.userId,
                                oldBio = payload.oldBio,
                                newBio = payload.bio,
                                oldWebsite = payload.oldWebsiteUrl,
                                newWebsite = payload.websiteUrl,
                            )
                            payload.interests?.let { interests ->
                                db.collection("users").document(payload.userId)
                                    .update("interests", interests).awaitTask()
                            }
                        }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                    }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.ACCEPT_FOLLOW_REQUEST.raw -> {
                decodeFollowRequestPayload(action.payloadData)?.let { payload ->
                    runCatching {
                        firestoreService.acceptFollowRequest(
                            payload.notificationId, payload.recipientId, payload.senderId,
                        )
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                        .onFailure { err ->
                            if (err.message?.contains("Solicitud no encontrada") == true) {
                                LocalPersistenceService.deleteAction(action.id)
                            }
                        }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.REJECT_FOLLOW_REQUEST.raw -> {
                decodeFollowRequestPayload(action.payloadData)?.let { payload ->
                    runCatching {
                        firestoreService.rejectFollowRequest(
                            payload.notificationId, payload.recipientId, payload.senderId,
                        )
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.REPORT_CONTENT.raw -> {
                decodeReportPayload(action.payloadData)?.let { payload ->
                    runCatching {
                        // iOS escribe NSNull() en resolvedAt/moderatorId.
                        val reportData = hashMapOf<String, Any?>(
                            "reporterId" to payload.reporterId,
                            "reportedUserId" to payload.reportedUserId,
                            "reportedContentType" to payload.reportedContentType,
                            "reportedContentId" to payload.reportedContentId,
                            "category" to payload.category,
                            "description" to payload.description,
                            "status" to "pending",
                            "priority" to payload.priority,
                            "timestamp" to FieldValue.serverTimestamp(),
                            "resolvedAt" to null,
                            "moderatorId" to null,
                            "moderatorNotes" to "",
                        )
                        db.collection("reports").add(reportData).awaitTask()
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.MARK_AS_READ.raw -> {
                decodeMarkAsReadPayload(action.payloadData)?.let { payload ->
                    runCatching {
                        db.collection("users").document(payload.userId)
                            .collection("notifications").document(payload.notificationId)
                            .update("isPending", false).awaitTask()
                    }.onSuccess { LocalPersistenceService.deleteAction(action.id) }
                } ?: LocalPersistenceService.deleteAction(action.id)
            }

            CachedAction.ActionType.DELETE_MOMENT.raw -> {
                decodeDeleteMomentPayload(action.payloadData)?.let { payload ->
                    // 1. Eliminar documento de Firestore
                    val deleted = runCatching {
                        db.collection("users").document(payload.userId)
                            .collection("moments").document(payload.momentId)
                            .delete().awaitTask()
                    }.isSuccess
                    // 2. Storage fire & forget (no bloquea la cola si falla) — igual que iOS.
                    payload.imagePath?.takeIf { it.isNotEmpty() }?.let { path ->
                        runCatching { StorageService.deleteMedia(path) }
                    }
                    payload.videoUrl?.takeIf { it.isNotEmpty() }?.let { path ->
                        runCatching { StorageService.deleteMedia(path) }
                    }
                    if (deleted) LocalPersistenceService.deleteAction(action.id)
                } ?: LocalPersistenceService.deleteAction(action.id)
            }
        }
        // Si no se borró, vuelve a pendiente (iOS: updateActionStatus .pending al final).
        if (LocalPersistenceService.hasPendingAction(action.id)) {
            LocalPersistenceService.updateActionStatus(action.id, CachedAction.ActionStatus.PENDING)
        }
    }

    private suspend fun optimizePendingActions(actions: List<CachedAction>): List<CachedAction> {
        val actionsToDelete = mutableSetOf<String>()

        val commentActions = actions.filter { it.type == CachedAction.ActionType.COMMENT.raw }
        val deleteCommentActions = actions.filter { it.type == CachedAction.ActionType.DELETE_COMMENT.raw }
        for (deleteAction in deleteCommentActions) {
            val deletePayload = decodeDeleteCommentPayload(deleteAction.payloadData) ?: continue
            commentActions.firstOrNull { creation ->
                decodeCommentPayload(creation.payloadData)?.commentId == deletePayload.commentId
            }?.let { creation ->
                actionsToDelete.add(creation.id)
                actionsToDelete.add(deleteAction.id)
            }
        }

        optimizeToggleGroup(actions, CachedAction.ActionType.REACTION.raw) { action ->
            decodeReactionPayload(action.payloadData)?.let { "${it.momentId}_${it.userId}_${it.reaction}" } ?: "unknown"
        }.let { actionsToDelete.addAll(it) }

        optimizeToggleGroup(actions, CachedAction.ActionType.SAVE.raw) { action ->
            decodeSavePayload(action.payloadData)?.let { "${it.momentId}_${it.userId}" } ?: "unknown"
        }.let { actionsToDelete.addAll(it) }

        actions.filter { it.type == CachedAction.ActionType.FOLLOW.raw }
            .groupBy { action ->
                decodeFollowPayload(action.payloadData)?.let { "${it.followerId}_${it.followedId}" } ?: "unknown"
            }
            .values
            .filter { it.size > 1 }
            .forEach { group ->
                group.sortedBy { it.createdAt }.dropLast(1).forEach { actionsToDelete.add(it.id) }
            }

        actionsToDelete.forEach { LocalPersistenceService.deleteAction(it) }
        return actions.filter { it.id !in actionsToDelete }
    }

    private fun optimizeToggleGroup(
        actions: List<CachedAction>,
        type: String,
        keyFn: (CachedAction) -> String,
    ): Set<String> {
        val toDelete = mutableSetOf<String>()
        actions.filter { it.type == type }.groupBy(keyFn).values.forEach { group ->
            if (group.size <= 1) return@forEach
            if (group.size % 2 == 0) group.forEach { toDelete.add(it.id) }
            else group.sortedBy { it.createdAt }.dropLast(1).forEach { toDelete.add(it.id) }
        }
        return toDelete
    }

    // --- Payload JSON decoders (org.json, sin kotlinx.serialization) ---

    private fun decodeMessagePayload(data: ByteArray): MessagePayload? = runCatching {
        val obj = JSONObject(String(data))
        MessagePayload(
            message = EnhancedMessage.fromJson(obj.getJSONObject("message")),
            useServerTimestamp = obj.optBoolean("useServerTimestamp", true),
        )
    }.getOrNull()

    private fun decodeMediaMessagePayload(data: ByteArray): MediaMessagePayload? = runCatching {
        val o = JSONObject(String(data))
        MediaMessagePayload(
            conversationId = o.getString("conversationId"),
            senderId = o.getString("senderId"),
            messageId = o.getString("messageId"),
            typeRaw = o.getString("typeRaw"),
            fileExtension = o.getString("fileExtension"),
            fileName = o.optString("fileName").takeIf { o.has("fileName") },
            duration = o.optDouble("duration").takeIf { o.has("duration") },
            audioWaveform = o.optJSONArray("audioWaveform")?.let { arr ->
                (0 until arr.length()).map { arr.getDouble(it).toFloat() }
            },
            mediaBatchId = o.optString("mediaBatchId").takeIf { o.has("mediaBatchId") },
            isVanishModeMessage = o.optBoolean("isVanishModeMessage"),
            vanishExpiresAt = o.optLong("vanishExpiresAt").takeIf { o.has("vanishExpiresAt") }?.let { Date(it) },
            replyTo = o.optString("replyTo").takeIf { o.has("replyTo") },
        )
    }.getOrNull()

    private fun decodeReactionPayload(data: ByteArray): ReactionPayload? = runCatching {
        val o = JSONObject(String(data))
        ReactionPayload(o.getString("momentId"), o.getString("reaction"), o.getString("authorId"), o.getString("userId"))
    }.getOrNull()

    private fun decodeCommentPayload(data: ByteArray): CommentPayload? = runCatching {
        val o = JSONObject(String(data))
        CommentPayload(
            momentId = o.getString("momentId"),
            authorId = o.getString("authorId"),
            senderId = o.getString("senderId"),
            content = o.getString("content"),
            parentCommentId = o.optString("parentCommentId").takeIf { o.has("parentCommentId") },
            commentId = o.optString("commentId").takeIf { o.has("commentId") },
            mentions = o.optJSONArray("mentions")?.let { arr ->
                (0 until arr.length()).map { i ->
                    val m = arr.getJSONObject(i)
                    CommentMentionEntity(m.getString("userId"), m.getString("username"), m.getInt("rangeStart"), m.getInt("rangeLength"))
                }
            },
        )
    }.getOrNull()

    private fun decodeDeleteCommentPayload(data: ByteArray): DeleteCommentPayload? = runCatching {
        val o = JSONObject(String(data))
        DeleteCommentPayload(o.getString("momentId"), o.getString("commentId"), o.getString("userId"), o.getString("authorId"))
    }.getOrNull()

    private fun decodeFollowPayload(data: ByteArray): FollowActionPayload? = runCatching {
        val o = JSONObject(String(data))
        FollowActionPayload(o.getString("followerId"), o.getString("followedId"), o.getString("followedUsername"), o.getBoolean("isFollow"))
    }.getOrNull()

    private fun decodeSavePayload(data: ByteArray): SavePayload? = runCatching {
        val o = JSONObject(String(data))
        SavePayload(o.getString("userId"), o.getString("momentId"))
    }.getOrNull()

    private fun decodeBlockPayload(data: ByteArray): BlockActionPayload? = runCatching {
        val o = JSONObject(String(data))
        BlockActionPayload(o.getString("currentUserId"), o.getString("targetUserId"), o.getBoolean("isBlock"))
    }.getOrNull()

    private fun decodeProfileUpdatePayload(data: ByteArray): ProfileUpdatePayload? = runCatching {
        val o = JSONObject(String(data))
        ProfileUpdatePayload(
            userId = o.getString("userId"),
            bio = o.optString("bio").takeIf { o.has("bio") },
            oldBio = o.optString("oldBio").takeIf { o.has("oldBio") },
            websiteUrl = o.optString("websiteUrl").takeIf { o.has("websiteUrl") },
            oldWebsiteUrl = o.optString("oldWebsiteUrl").takeIf { o.has("oldWebsiteUrl") },
            interests = o.optJSONArray("interests")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } },
            profileImageLocalPath = o.optString("profileImageLocalPath").takeIf { o.has("profileImageLocalPath") },
            isImageUpdate = o.optBoolean("isImageUpdate"),
        )
    }.getOrNull()

    private fun decodeFollowRequestPayload(data: ByteArray): FollowRequestActionPayload? = runCatching {
        val o = JSONObject(String(data))
        FollowRequestActionPayload(o.getString("notificationId"), o.getString("senderId"), o.getString("recipientId"), o.getBoolean("isAccept"))
    }.getOrNull()

    private fun decodeReportPayload(data: ByteArray): ReportActionPayload? = runCatching {
        val o = JSONObject(String(data))
        ReportActionPayload(
            o.getString("reporterId"), o.getString("reportedUserId"), o.getString("reportedContentType"),
            o.getString("reportedContentId"), o.getString("category"), o.getString("description"), o.getString("priority"),
        )
    }.getOrNull()

    private fun decodeMarkAsReadPayload(data: ByteArray): MarkAsReadPayload? = runCatching {
        val o = JSONObject(String(data))
        MarkAsReadPayload(o.getString("notificationId"), o.getString("userId"))
    }.getOrNull()

    private fun decodeDeleteMomentPayload(data: ByteArray): DeleteMomentPayload? = runCatching {
        val o = JSONObject(String(data))
        DeleteMomentPayload(
            o.getString("momentId"), o.getString("userId"),
            o.optString("imagePath").takeIf { o.has("imagePath") },
            o.optString("videoUrl").takeIf { o.has("videoUrl") },
        )
    }.getOrNull()

    private suspend fun com.google.android.gms.tasks.Task<*>.awaitTask() {
        await()
    }
}
