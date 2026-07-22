package com.moments.android.views.creator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.CachedHiddenLayerDraft
import com.moments.android.models.CachedUploadMediaItem
import com.moments.android.models.HiddenLayerImageFrameStyle
import com.moments.android.models.HiddenLayerPresentationStyle
import com.moments.android.models.HiddenLayerTextStyle
import com.moments.android.models.MediaItem
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.models.MomentUploadPayload
import com.moments.android.models.UploadPayloadDecoder
import com.moments.android.models.cache.CachedAction
import com.moments.android.moderation.MediaModerationAction
import com.moments.android.moderation.MediaModerationService
import com.moments.android.moderation.ModerationContentType
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.createMomentWithCustomList
import com.moments.android.services.firestore.createMomentWithVisibility
import com.moments.android.services.firestore.markHiddenLayerVisible
import com.moments.android.services.firestore.saveHiddenLayers
import com.moments.android.services.firestore.updateMomentHiddenLayerSummary
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.storage.FeedMediaUploadContext
import com.moments.android.services.storage.StoragePathBuilder
import com.moments.android.services.storage.StorageService
import com.moments.android.services.storage.UploadMediaItem
import com.moments.android.services.storage.UploadMediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

/**
 * Port de BackgroundMomentUploadService.swift (`Views/Creator`).
 * Reanuda subidas persistidas en outbox: Storage + Firestore + hidden layers + moderación silenciosa.
 */
object BackgroundMomentUploadService {

    private val inFlightActionIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val firestoreService = FirestoreService()
    private val moderationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null
    @Volatile private var feedViewModel: Any? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    /** iOS `setFeedViewModel` — pausa listeners durante uploads. */
    fun setFeedViewModel(viewModel: Any?) {
        feedViewModel = viewModel
        // Tipado débil para evitar ciclo Creator ↔ Feed; pause/resume vía reflection-safe cast
        when (viewModel) {
            is com.moments.android.views.feed.core.FeedViewModel -> Unit
            else -> Unit
        }
    }

    fun pauseFeedListeners() {
        (feedViewModel as? com.moments.android.views.feed.core.FeedViewModel)?.pauseListenersForUpload()
    }

    fun resumeFeedListeners() {
        (feedViewModel as? com.moments.android.views.feed.core.FeedViewModel)?.resumeListenersAfterUpload()
    }

    /**
     * Port de iOS `uploadMoment(...)`: copia media a pending_uploads, persiste outbox y lanza `resumeUpload`.
     * @return actionId si se encoló; null si no hay sesión / sin media.
     */
    fun uploadMoment(
        content: String,
        mediaItems: List<CreatorMedia>,
        taggedUsers: List<String>? = null,
        mentionedUsers: List<String>? = null,
        location: String? = null,
        locationCoordinate: com.moments.android.models.Moment.LocationCoordinate? = null,
        audienceSetting: String = ContentAudience.EVERYONE.raw,
        customViewers: List<String>? = null,
        customListId: String? = null,
        aspectRatio: String = "1:1",
        disableComments: Boolean = false,
        hideLikeCounts: Boolean = false,
        allowSharing: Boolean = true,
        scheduledDate: Date? = null,
        hiddenLayers: List<CachedHiddenLayerDraft>? = null,
    ): String? {
        val ctx = appContext ?: return null
        if (FirebaseAuth.getInstance().currentUser?.uid == null) return null
        if (mediaItems.isEmpty()) return null

        val dir = pendingUploadsDir()
        val cachedMedia = mediaItems.mapIndexedNotNull { index, item ->
            runCatching {
                val ext = if (item.isVideo) "mp4" else "jpg"
                val name = "moment_${UUID.randomUUID()}_$index.$ext"
                val out = File(dir, name)
                ctx.contentResolver.openInputStream(item.uri)?.use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                CachedUploadMediaItem(
                    type = if (item.isVideo) "video" else "image",
                    localFileName = name,
                    aspectRatio = item.aspectRatio.displayName,
                    videoDuration = item.durationSeconds,
                    tags = item.tags.takeIf { it.isNotEmpty() },
                )
            }.getOrNull()
        }
        if (cachedMedia.isEmpty()) return null

        val actionId = UUID.randomUUID().toString()
        val plannedId = UUID.randomUUID().toString()
        val payload = MomentUploadPayload(
            plannedMomentId = plannedId,
            content = content,
            mediaPaths = cachedMedia,
            taggedUsers = taggedUsers,
            mentionedUsers = mentionedUsers,
            location = location,
            locationCoordinate = locationCoordinate,
            audienceSetting = audienceSetting,
            customViewers = customViewers,
            customListId = customListId,
            aspectRatio = aspectRatio,
            disableComments = disableComments,
            hideLikeCounts = hideLikeCounts,
            allowSharing = allowSharing,
            scheduledDate = scheduledDate,
            hiddenLayers = hiddenLayers?.takeIf { it.isNotEmpty() },
        )
        val action = CachedAction(
            id = actionId,
            type = CachedAction.ActionType.MOMENT_UPLOAD.raw,
            payloadData = UploadPayloadDecoder.encodeMomentPayload(payload),
        )
        LocalPersistenceService.saveAction(action)
        pauseFeedListeners()
        moderationScope.launch {
            try {
                resumeUpload(action)
            } finally {
                resumeFeedListeners()
            }
        }
        return actionId
    }

    private fun pendingUploadsDir(): File {
        val ctx = appContext ?: error("BackgroundMomentUploadService.initialize required")
        return File(ctx.filesDir, "pending_uploads").also { it.mkdirs() }
    }

    suspend fun resumeUpload(action: CachedAction) {
        if (action.type != CachedAction.ActionType.MOMENT_UPLOAD.raw) return
        if (!inFlightActionIds.add(action.id)) {
            LocalPersistenceService.updateActionStatus(action.id, CachedAction.ActionStatus.PENDING)
            return
        }
        try {
            val payload = UploadPayloadDecoder.decodeMomentPayload(action.payloadData)
                ?: throw IllegalStateException("Invalid moment upload payload")
            val userId = FirebaseAuth.getInstance().currentUser?.uid
                ?: throw IllegalStateException("Not signed in")
            val mediaItems = buildUploadedMediaItems(payload, userId)
            if (mediaItems.isEmpty()) {
                LocalPersistenceService.updateActionStatus(
                    action.id,
                    CachedAction.ActionStatus.FAILED,
                    error = "Missing cached media for pending moment upload",
                )
                return
            }
            val audience = parseAudience(payload.audienceSetting)
            val momentId = payload.plannedMomentId ?: UUID.randomUUID().toString()
            if (audience == ContentAudience.CUSTOM_LIST && payload.customListId != null) {
                firestoreService.createMomentWithCustomList(
                    userId = userId,
                    content = payload.content,
                    mediaItems = mediaItems,
                    customListId = payload.customListId,
                    taggedUsers = payload.taggedUsers,
                    mentionedUsers = payload.mentionedUsers,
                    location = payload.location,
                    locationCoordinate = payload.locationCoordinate,
                    aspectRatio = payload.aspectRatio,
                    disableComments = payload.disableComments,
                    hideLikeCounts = payload.hideLikeCounts,
                    allowSharing = payload.allowSharing,
                    scheduledDate = payload.scheduledDate,
                    momentId = momentId,
                )
            } else {
                firestoreService.createMomentWithVisibility(
                    userId = userId,
                    content = payload.content,
                    mediaItems = mediaItems,
                    audience = audience,
                    customViewers = payload.customViewers,
                    taggedUsers = payload.taggedUsers,
                    mentionedUsers = payload.mentionedUsers,
                    location = payload.location,
                    locationCoordinate = payload.locationCoordinate,
                    selectedListId = payload.customListId,
                    aspectRatio = payload.aspectRatio,
                    disableComments = payload.disableComments,
                    hideLikeCounts = payload.hideLikeCounts,
                    allowSharing = payload.allowSharing,
                    scheduledDate = payload.scheduledDate,
                    momentId = momentId,
                )
            }
            val hiddenLayers = uploadHiddenLayersIfNeeded(payload, userId, momentId)
            moderationScope.launch {
                moderateContentSilently(momentId, userId, mediaItems)
                moderateHiddenLayersSilently(momentId, userId, hiddenLayers)
            }
            LocalPersistenceService.updateActionStatus(action.id, CachedAction.ActionStatus.COMPLETED)
        } catch (e: Exception) {
            LocalPersistenceService.updateActionStatus(
                action.id,
                CachedAction.ActionStatus.FAILED,
                error = e.message,
            )
        } finally {
            inFlightActionIds.remove(action.id)
        }
    }

    private suspend fun buildUploadedMediaItems(
        payload: MomentUploadPayload,
        userId: String,
    ): List<MediaItem> {
        val dir = pendingUploadsDir()
        val uploaded = mutableListOf<MediaItem>()
        for (cached in payload.mediaPaths) {
            val file = File(dir, cached.localFileName)
            if (!file.exists()) continue
            val momentId = payload.plannedMomentId ?: UUID.randomUUID().toString()
            val mediaId = StoragePathBuilder.storageSafeSegment(UUID.randomUUID().toString())
            val context = FeedMediaUploadContext.Moment(momentId = momentId, mediaId = mediaId)
            val isVideo = cached.type == "video"
            var thumbnailUrl: String? = null
            if (isVideo && cached.thumbnailFileName != null) {
                val thumbFile = File(dir, cached.thumbnailFileName)
                if (thumbFile.exists()) {
                    val thumbBitmap = BitmapFactory.decodeFile(thumbFile.absolutePath)
                    if (thumbBitmap != null) {
                        thumbnailUrl = StorageService.uploadMomentThumbnail(
                            userId = userId,
                            momentId = momentId,
                            image = thumbBitmap,
                            mediaId = "${mediaId}_thumb",
                        )
                    }
                }
            }
            val url = if (isVideo) {
                StorageService.uploadMedia(
                    userId = userId,
                    mediaItem = UploadMediaItem(type = UploadMediaKind.VIDEO, videoUri = Uri.fromFile(file)),
                    context = context,
                )
            } else {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: continue
                StorageService.uploadMedia(
                    userId = userId,
                    mediaItem = UploadMediaItem(type = UploadMediaKind.IMAGE, image = bitmap),
                    context = context,
                )
            }
            uploaded += MediaItem(
                id = mediaId,
                type = if (isVideo) MediaItem.MediaType.VIDEO else MediaItem.MediaType.IMAGE,
                url = url,
                aspectRatio = cached.aspectRatio ?: payload.aspectRatio,
                thumbnailUrl = thumbnailUrl,
                videoDuration = cached.videoDuration,
                videoFileSize = cached.videoFileSize,
                videoResolution = cached.videoResolution,
                videoProcessingStatus = if (isVideo) MediaItem.VideoProcessingStatus.READY else null,
                tags = cached.tags,
            )
        }
        return uploaded
    }

    private suspend fun uploadHiddenLayersIfNeeded(
        payload: MomentUploadPayload,
        userId: String,
        momentId: String,
    ): List<MomentHiddenLayer> {
        val drafts = payload.hiddenLayers?.take(3).orEmpty()
        if (drafts.isEmpty()) return emptyList()

        val uploaded = mutableListOf<MomentHiddenLayer>()
        drafts.forEachIndexed { index, draft ->
            runCatching {
                buildHiddenLayer(draft, index, userId, momentId)
            }.onSuccess { uploaded += it }
        }

        if (uploaded.isEmpty()) {
            firestoreService.updateMomentHiddenLayerSummary(userId, momentId, 0)
            return emptyList()
        }

        firestoreService.saveHiddenLayers(userId, momentId, uploaded)
        return uploaded
    }

    private suspend fun buildHiddenLayer(
        draft: CachedHiddenLayerDraft,
        index: Int,
        userId: String,
        momentId: String,
    ): MomentHiddenLayer {
        val layerType = MomentHiddenLayer.LayerType.from(draft.type)
        var mediaURL: String? = null
        var thumbnailURL: String? = null
        var moderationState = MomentHiddenLayer.ModerationState.VISIBLE

        when (layerType) {
            MomentHiddenLayer.LayerType.TEXT -> Unit
            MomentHiddenLayer.LayerType.IMAGE -> {
                val fileName = draft.localImageFileName
                    ?: throw IllegalStateException("Missing hidden layer image file")
                val imageFile = File(pendingUploadsDir(), fileName)
                val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
                    ?: throw IllegalStateException("Invalid hidden layer image")
                val resized = resizeHiddenLayerImage(bitmap)
                mediaURL = StorageService.uploadHiddenLayerImage(
                    userId = userId,
                    momentId = momentId,
                    layerId = draft.id,
                    image = resized,
                )
                thumbnailURL = mediaURL
                moderationState = MomentHiddenLayer.ModerationState.PENDING
            }
            MomentHiddenLayer.LayerType.AUDIO -> {
                val fileName = draft.localAudioFileName
                    ?: throw IllegalStateException("Missing hidden layer audio file")
                val audioFile = File(pendingUploadsDir(), fileName)
                mediaURL = StorageService.uploadHiddenLayerAudio(
                    userId = userId,
                    momentId = momentId,
                    layerId = draft.id,
                    audioUri = Uri.fromFile(audioFile),
                )
            }
        }

        return MomentHiddenLayer(
            id = draft.id,
            type = layerType,
            anchorX = min(0.94, max(0.06, draft.anchorX)),
            anchorY = min(0.94, max(0.06, draft.anchorY)),
            width = min(0.55, max(0.12, draft.width)),
            height = min(0.42, max(0.10, draft.height)),
            shape = MomentHiddenLayer.LayerShape.from(draft.shape),
            zIndex = index,
            text = if (layerType == MomentHiddenLayer.LayerType.TEXT) {
                draft.text.trim().take(120).ifEmpty { null }
            } else {
                null
            },
            mediaURL = mediaURL,
            thumbnailURL = thumbnailURL,
            duration = draft.duration,
            caption = if (layerType == MomentHiddenLayer.LayerType.IMAGE) {
                draft.caption.trim().take(40).ifEmpty { null }
            } else {
                null
            },
            imageOffsetX = if (layerType == MomentHiddenLayer.LayerType.IMAGE) draft.imageOffsetX else null,
            imageOffsetY = if (layerType == MomentHiddenLayer.LayerType.IMAGE) draft.imageOffsetY else null,
            imageScale = if (layerType == MomentHiddenLayer.LayerType.IMAGE) draft.imageScale else null,
            imageFrameStyle = if (layerType == MomentHiddenLayer.LayerType.IMAGE) {
                HiddenLayerImageFrameStyle.from(draft.imageFrameStyle)
            } else {
                null
            },
            textStyle = HiddenLayerTextStyle.from(draft.textStyle),
            presentationStyle = HiddenLayerPresentationStyle.from(draft.presentationStyle),
            unlockMode = MomentHiddenLayer.UnlockMode.from(draft.unlockMode),
            unlockAt = if (MomentHiddenLayer.UnlockMode.from(draft.unlockMode) == MomentHiddenLayer.UnlockMode.SCHEDULED) {
                draft.unlockAt
            } else {
                null
            },
            authorTimezoneIdentifier = draft.authorTimezoneIdentifier,
            moderationState = moderationState,
        )
    }

    private fun resizeHiddenLayerImage(source: Bitmap): Bitmap {
        val maxDimension = 1080
        val longest = max(source.width, source.height)
        if (longest <= maxDimension) return source
        val scale = maxDimension.toFloat() / longest
        return Bitmap.createScaledBitmap(
            source,
            (source.width * scale).toInt().coerceAtLeast(1),
            (source.height * scale).toInt().coerceAtLeast(1),
            true,
        )
    }

    private suspend fun moderateContentSilently(
        momentId: String,
        userId: String,
        mediaItems: List<MediaItem>,
    ) {
        for (item in mediaItems) {
            suspendCancellableCoroutine { cont ->
                MediaModerationService.shared.moderateMedia(
                    mediaURL = item.url,
                    mediaType = item.type,
                    userId = userId,
                    contentId = momentId,
                    contentType = ModerationContentType.MOMENT,
                    mediaItemId = item.id,
                ) { cont.resume(it) }
            }
        }
    }

    private suspend fun moderateHiddenLayersSilently(
        momentId: String,
        userId: String,
        layers: List<MomentHiddenLayer>,
    ) {
        for (layer in layers) {
            if (layer.type != MomentHiddenLayer.LayerType.IMAGE) continue
            val mediaURL = layer.mediaURL ?: continue
            val action = suspendCancellableCoroutine { cont ->
                MediaModerationService.shared.moderateMedia(
                    mediaURL = mediaURL,
                    mediaType = MediaItem.MediaType.IMAGE,
                    userId = userId,
                    contentId = momentId,
                    contentType = ModerationContentType.MOMENT,
                    mediaItemId = "hiddenLayer_${layer.id}",
                ) { cont.resume(it) }
            }
            when (action) {
                is MediaModerationAction.Approved,
                is MediaModerationAction.Warning -> {
                    firestoreService.markHiddenLayerVisible(userId, momentId, layer.id)
                }
                else -> Unit
            }
        }
    }

    private fun parseAudience(raw: String): ContentAudience =
        ContentAudience.from(raw)
}
