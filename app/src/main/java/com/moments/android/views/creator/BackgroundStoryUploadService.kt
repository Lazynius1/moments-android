package com.moments.android.views.creator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.activities.LiveActivityThumbnailStore
import com.moments.android.activities.StoryUploadActivityAttributes
import com.moments.android.activities.UploadProgressNotificationHelper
import com.moments.android.coordinators.CoordinatorNavigationEvent
import com.moments.android.coordinators.NavigationEventBus
import com.moments.android.moderation.MediaModerationAction
import com.moments.android.moderation.MediaModerationService
import com.moments.android.moderation.ModerationContentType
import com.moments.android.models.CachedSticker
import com.moments.android.models.CachedUploadMediaItem
import com.moments.android.models.MediaItem
import com.moments.android.models.Point
import com.moments.android.models.StickerData
import com.moments.android.models.StoryTextOverlayMetadata
import com.moments.android.models.StoryUploadPayload
import com.moments.android.models.UploadPayloadDecoder
import com.moments.android.models.cache.CachedAction
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.createStoryWithCustomList
import com.moments.android.services.firestore.createStoryWithVisibility
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.storage.FeedMediaUploadContext
import com.moments.android.services.storage.MediaUploadPayload
import com.moments.android.services.storage.MediaUploadService
import com.moments.android.services.storage.StoragePathBuilder
import com.moments.android.services.storage.StorageService
import com.moments.android.services.storage.StorageUploadDomain
import com.moments.android.services.storage.UploadMediaItem
import com.moments.android.services.storage.UploadMediaKind
import com.moments.android.services.storage.VideoCompressionService
import com.moments.android.services.storage.storageUploadJpegData
import com.moments.android.views.creator.components.sendMentionNotificationsForStory
import com.moments.android.views.feed.uploads.StoryUploadProgressManager
import com.moments.android.views.feed.uploads.UploadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

// MARK: - Modelo de historia en progreso (≡ UploadingStory.swift)

/**
 * Port de `UploadingStory` — estado para header/progress mientras sube.
 * Live Activity thumbnail → [thumbnailBitmap] (sin ActivityKit).
 */
class UploadingStory(
    val userId: String,
    var mediaItem: CreatorMedia,
    val storyText: String? = null,
    val textPosition: Point? = null,
    val selectedTextStyle: String? = null,
    val textOverlayMetadata: StoryTextOverlayMetadata? = null,
    val textOverlays: List<StoryTextOverlayMetadata>? = null,
    var stickerData: List<CachedSticker>? = null,
    var drawingData: ByteArray? = null,
    val audienceSetting: ContentAudience = ContentAudience.EVERYONE,
    val customViewers: List<String>? = null,
    val customListId: String? = null,
    val selectedListName: String? = null,
    var finalRenderedImage: Bitmap? = null,
    val chainId: String? = null,
    val chainPosition: Int? = null,
    val chainTitle: String? = null,
    val allowOthersToContinue: Boolean? = null,
    val continuationAudience: ContentAudience? = null,
    val continuationCustomViewers: List<String>? = null,
    val continuationCustomListId: String? = null,
    val continuationCustomListName: String? = null,
    val expirationHours: Int = 24,
    val storyVideoMode: StoryVideoMode = StoryVideoMode.NORMAL,
    tempId: String? = null,
    plannedStoryId: String? = null,
) {
    val id: String = UUID.randomUUID().toString()
    val tempId: String = tempId ?: "temp_story_${UUID.randomUUID()}"
    val plannedStoryId: String = plannedStoryId ?: UUID.randomUUID().toString()
    val createdAt: Date = Date()

    var uploadProgress by mutableDoubleStateOf(0.0)
    var status by mutableStateOf(UploadStatus.Initializing)
    var errorMessage by mutableStateOf<String?>(null)
    var storyId by mutableStateOf<String?>(null)
    var thumbnailBitmap by mutableStateOf<Bitmap?>(null)
}

/**
 * Port de `BackgroundStoryUploadService.swift` (`Views/Creator`).
 * ActivityKit → notificación ongoing ([UploadProgressNotificationHelper]).
 */
object BackgroundStoryUploadService {

    private data class PreparedStoryMedia(
        val url: String,
        val videoFileSize: Long?,
        val videoResolution: String?,
        val thumbnailUrl: String?,
        val localVideoFile: File?,
    )

    private val inFlightActionIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val firestoreService = FirestoreService()
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null

    /** ≡ iOS `@Published var uploadingStory`. */
    var uploadingStory by mutableStateOf<UploadingStory?>(null)
        private set

    /** ≡ iOS `@Published var isProcessing`. */
    var isProcessing by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun pendingUploadsDir(): File {
        val ctx = appContext ?: error("BackgroundStoryUploadService.initialize required")
        return File(ctx.filesDir, "pending_uploads").also { it.mkdirs() }
    }

    // MARK: - Preparación instantánea (Paso 2)

    /**
     * ≡ iOS `startPreparingStory` — crea [UploadingStory] en estado Initializing.
     */
    fun startPreparingStory(
        media: CreatorMedia,
        storyText: String? = null,
        textPosition: Point? = null,
        selectedTextStyle: String? = null,
        textOverlayMetadata: StoryTextOverlayMetadata? = null,
        textOverlays: List<StoryTextOverlayMetadata>? = null,
        stickers: List<CachedSticker>? = null,
        drawingData: ByteArray? = null,
        audienceSetting: ContentAudience = ContentAudience.EVERYONE,
        customViewers: List<String>? = null,
        customListId: String? = null,
        selectedListName: String? = null,
        chainId: String? = null,
        chainPosition: Int? = null,
        chainTitle: String? = null,
        allowOthersToContinue: Boolean? = null,
        continuationAudience: ContentAudience? = null,
        continuationCustomViewers: List<String>? = null,
        continuationCustomListId: String? = null,
        continuationCustomListName: String? = null,
        expirationHours: Int = 24,
        storyVideoMode: StoryVideoMode = StoryVideoMode.NORMAL,
        tempId: String? = null,
    ): UploadingStory? {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        uploadingStory?.let { cancelUpload(it.tempId) }
        val story = UploadingStory(
            userId = userId,
            mediaItem = media,
            storyText = storyText,
            textPosition = textPosition,
            selectedTextStyle = selectedTextStyle,
            textOverlayMetadata = textOverlayMetadata,
            textOverlays = textOverlays,
            stickerData = stickers,
            drawingData = drawingData,
            audienceSetting = audienceSetting,
            customViewers = customViewers,
            customListId = customListId,
            selectedListName = selectedListName,
            chainId = chainId,
            chainPosition = chainPosition,
            chainTitle = chainTitle,
            allowOthersToContinue = allowOthersToContinue,
            continuationAudience = continuationAudience,
            continuationCustomViewers = continuationCustomViewers,
            continuationCustomListId = continuationCustomListId,
            continuationCustomListName = continuationCustomListName,
            expirationHours = if (expirationHours == 48) 48 else 24,
            storyVideoMode = storyVideoMode,
            tempId = tempId,
        )
        story.status = UploadStatus.Initializing
        story.uploadProgress = 0.0
        uploadingStory = story
        isProcessing = true
        StoryUploadProgressManager.startUpload()
        startLiveActivity(story)
        return story
    }

    /**
     * ≡ iOS `publishPreparedStoryInBackground` — persist outbox + process.
     */
    fun publishPreparedStoryInBackground(
        uploadingStory: UploadingStory,
        preparedMedia: CreatorMedia,
        finalRenderedImage: Bitmap? = null,
        shouldPersistAction: Boolean = true,
    ) {
        uploadingStory.mediaItem = preparedMedia
        uploadingStory.finalRenderedImage = finalRenderedImage
        uploadingStory.status = UploadStatus.Uploading
        uploadingStory.uploadProgress = 0.0
        uploadingStory.thumbnailBitmap = finalRenderedImage
        StoryUploadProgressManager.startUpload()
        StoryUploadProgressManager.updateProgress(0.0)
        this.uploadingStory = uploadingStory
        isProcessing = true
        // Thumbnail ya disponible — reinicia notif de progreso (≡ Live Activity).
        startLiveActivity(uploadingStory)

        uploadScope.launch {
            if (shouldPersistAction) {
                try {
                    persistAction(uploadingStory)
                } catch (t: Throwable) {
                    uploadingStory.status = UploadStatus.Failed
                    uploadingStory.errorMessage = t.message
                    failAction(uploadingStory.tempId, t.message)
                    deleteActionFiles(uploadingStory.tempId)
                    isProcessing = false
                    return@launch
                }
            }
            val action = LocalPersistenceService.loadAction(uploadingStory.tempId)
                ?: run {
                    markStoryAsFailed(uploadingStory, "Missing persisted story upload action")
                    return@launch
                }
            resumeUpload(action)
            isProcessing = false
            if (uploadingStory.status == UploadStatus.Completed ||
                uploadingStory.status == UploadStatus.Moderated
            ) {
                // finishSuccess ya borra outbox; reforzar cleanup de ficheros
                deleteActionFiles(uploadingStory.tempId)
            }
            if (this@BackgroundStoryUploadService.uploadingStory?.tempId == uploadingStory.tempId) {
                this@BackgroundStoryUploadService.uploadingStory = null
            }
        }
    }

    /** ≡ iOS `markStoryAsFailed`. */
    fun markStoryAsFailed(uploadingStory: UploadingStory, errorMessage: String) {
        uploadingStory.status = UploadStatus.Failed
        uploadingStory.errorMessage = errorMessage
        isProcessing = false
        failAction(uploadingStory.tempId, errorMessage)
    }

    /**
     * ≡ iOS `uploadStory` / cola inmediata con media ya preparado.
     */
    fun uploadStory(
        media: CreatorMedia,
        storyText: String? = null,
        textPosition: Point? = null,
        selectedTextStyle: String? = null,
        textOverlayMetadata: StoryTextOverlayMetadata? = null,
        textOverlays: List<StoryTextOverlayMetadata>? = null,
        drawingData: ByteArray? = null,
        stickers: List<CachedSticker>? = null,
        audienceSetting: String = ContentAudience.EVERYONE.raw,
        customViewers: List<String>? = null,
        customListId: String? = null,
        selectedListName: String? = null,
        expirationHours: Int = 24,
        chainId: String? = null,
        chainPosition: Int? = null,
        chainTitle: String? = null,
        allowOthersToContinue: Boolean? = null,
        continuationAudience: String? = null,
        continuationCustomViewers: List<String>? = null,
        continuationCustomListId: String? = null,
        continuationCustomListName: String? = null,
    ): String? {
        val story = startPreparingStory(
            media = media,
            storyText = storyText,
            textPosition = textPosition,
            selectedTextStyle = selectedTextStyle,
            textOverlayMetadata = textOverlayMetadata,
            textOverlays = textOverlays,
            stickers = stickers,
            drawingData = drawingData,
            audienceSetting = ContentAudience.from(audienceSetting),
            customViewers = customViewers,
            customListId = customListId,
            selectedListName = selectedListName,
            chainId = chainId,
            chainPosition = chainPosition,
            chainTitle = chainTitle,
            allowOthersToContinue = allowOthersToContinue,
            continuationAudience = continuationAudience?.let { ContentAudience.from(it) },
            continuationCustomViewers = continuationCustomViewers,
            continuationCustomListId = continuationCustomListId,
            continuationCustomListName = continuationCustomListName,
            expirationHours = expirationHours,
            storyVideoMode = media.storyVideoMode,
        ) ?: return null
        publishPreparedStoryInBackground(
            uploadingStory = story,
            preparedMedia = media,
            finalRenderedImage = null,
            shouldPersistAction = true,
        )
        return story.tempId
    }

    /**
     * ≡ iOS: dismiss editor → bake en background → `publishPreparedStoryInBackground`.
     */
    fun uploadStoryWithPreparation(
        prepareMedia: suspend () -> CreatorMedia,
        storyText: String? = null,
        textPosition: Point? = null,
        selectedTextStyle: String? = null,
        textOverlayMetadata: StoryTextOverlayMetadata? = null,
        textOverlays: List<StoryTextOverlayMetadata>? = null,
        drawingData: ByteArray? = null,
        stickers: List<CachedSticker>? = null,
        audienceSetting: String = ContentAudience.EVERYONE.raw,
        customViewers: List<String>? = null,
        customListId: String? = null,
        selectedListName: String? = null,
        expirationHours: Int = 24,
        chainId: String? = null,
        chainPosition: Int? = null,
        chainTitle: String? = null,
        allowOthersToContinue: Boolean? = null,
        continuationAudience: String? = null,
        continuationCustomViewers: List<String>? = null,
        continuationCustomListId: String? = null,
        continuationCustomListName: String? = null,
        onPrepareFailed: ((Throwable) -> Unit)? = null,
    ): String? {
        if (appContext == null) return null
        val placeholder = CreatorMedia(uri = Uri.EMPTY, isVideo = false)
        val story = startPreparingStory(
            media = placeholder,
            storyText = storyText,
            textPosition = textPosition,
            selectedTextStyle = selectedTextStyle,
            textOverlayMetadata = textOverlayMetadata,
            textOverlays = textOverlays,
            stickers = stickers,
            drawingData = drawingData,
            audienceSetting = ContentAudience.from(audienceSetting),
            customViewers = customViewers,
            customListId = customListId,
            selectedListName = selectedListName,
            chainId = chainId,
            chainPosition = chainPosition,
            chainTitle = chainTitle,
            allowOthersToContinue = allowOthersToContinue,
            continuationAudience = continuationAudience?.let { ContentAudience.from(it) },
            continuationCustomViewers = continuationCustomViewers,
            continuationCustomListId = continuationCustomListId,
            continuationCustomListName = continuationCustomListName,
            expirationHours = expirationHours,
        ) ?: return null
        uploadScope.launch {
            val media = try {
                prepareMedia()
            } catch (t: Throwable) {
                markStoryAsFailed(story, t.message ?: "prepare failed")
                StoryUploadProgressManager.cancelUpload()
                onPrepareFailed?.invoke(t)
                return@launch
            }
            publishPreparedStoryInBackground(
                uploadingStory = story,
                preparedMedia = media,
                finalRenderedImage = null,
                shouldPersistAction = true,
            )
        }
        return story.tempId
    }

    /** ≡ iOS `retryUpload` — reencola acción fallida. */
    fun retryUpload(actionId: String) {
        LocalPersistenceService.updateActionStatus(actionId, CachedAction.ActionStatus.PENDING)
        val action = LocalPersistenceService.loadAction(actionId) ?: return
        StoryUploadProgressManager.startUpload()
        uploadingStory?.takeIf { it.tempId == actionId }?.let {
            it.status = UploadStatus.Uploading
            it.uploadProgress = 0.0
            it.errorMessage = null
        }
        isProcessing = true
        uploadScope.launch { resumeUpload(action) }
    }

    /** ≡ iOS `cancelUpload` — quita progreso y borra outbox + ficheros. */
    fun cancelUpload(actionId: String) {
        inFlightActionIds.remove(actionId)
        LocalPersistenceService.loadAction(actionId)?.let(::deleteActionFiles)
        LocalPersistenceService.deleteAction(actionId)
        StoryUploadProgressManager.cancelUpload()
        if (uploadingStory?.tempId == actionId) {
            uploadingStory = null
            isProcessing = false
        }
        endLiveActivity()
    }

    /** ≡ iOS `deleteActionFiles(id:)`. */
    fun deleteActionFiles(actionId: String) {
        LocalPersistenceService.loadAction(actionId)?.let(::deleteActionFiles)
        // ≡ iOS: también borra ficheros cuyo nombre contiene el actionId
        val dir = runCatching { pendingUploadsDir() }.getOrNull() ?: return
        dir.listFiles()?.forEach { file ->
            if (file.name.contains(actionId)) file.delete()
        }
    }

    private fun deleteActionFiles(action: CachedAction) {
        val payload = UploadPayloadDecoder.decodeStoryPayload(action.payloadData) ?: return
        val dir = pendingUploadsDir()
        listOfNotNull(
            payload.mediaItem.localFileName,
            payload.mediaItem.thumbnailFileName,
            payload.drawingFileName,
        ).forEach { name ->
            File(dir, name).delete()
        }
        payload.stickers?.forEach { sticker ->
            sticker.localImageName?.let { File(dir, it).delete() }
        }
    }

    // MARK: - Persistencia outbox (≡ persistAction / saveMediaToDisk / saveStickerToDisk)

    /** ≡ iOS `persistAction`. */
    @Throws(Exception::class)
    suspend fun persistAction(story: UploadingStory) = withContext(Dispatchers.IO) {
        val dir = pendingUploadsDir()
        val cachedMedia = saveMediaToDisk(story.mediaItem, story.tempId)
        val cachedStickers = story.stickerData?.map { saveStickerToDisk(it, story.tempId) }
        val drawingFileName = story.drawingData?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val name = "${story.tempId}_drawing.png"
            File(dir, name).writeBytes(bytes)
            name
        }
        val preparedOverlays = story.textOverlays?.takeIf { it.isNotEmpty() }
        val primary = story.textOverlayMetadata ?: preparedOverlays?.firstOrNull()
        val payload = StoryUploadPayload(
            plannedStoryId = story.plannedStoryId,
            userId = story.userId,
            mediaItem = cachedMedia,
            storyText = story.storyText?.takeIf { it.isNotBlank() } ?: primary?.text,
            textPosition = story.textPosition ?: primary?.normalizedPosition,
            selectedTextStyle = story.selectedTextStyle ?: primary?.styleRaw,
            textOverlayMetadata = primary,
            textOverlays = preparedOverlays,
            audienceSetting = story.audienceSetting.raw,
            customViewers = story.customViewers,
            customListId = story.customListId,
            selectedListName = story.selectedListName,
            expirationHours = story.expirationHours,
            chainId = story.chainId,
            chainPosition = story.chainPosition,
            chainTitle = story.chainTitle,
            allowOthersToContinue = story.allowOthersToContinue,
            continuationAudience = story.continuationAudience?.raw,
            continuationCustomViewers = story.continuationCustomViewers,
            continuationCustomListId = story.continuationCustomListId,
            continuationCustomListName = story.continuationCustomListName,
            drawingFileName = drawingFileName,
            stickers = cachedStickers?.takeIf { it.isNotEmpty() },
            storyVideoMode = story.mediaItem.storyVideoMode.raw,
        )
        val action = CachedAction(
            id = story.tempId,
            type = CachedAction.ActionType.STORY_UPLOAD.raw,
            payloadData = UploadPayloadDecoder.encodeStoryPayload(payload),
        )
        LocalPersistenceService.saveActionOrThrow(action)
    }

    /** ≡ iOS `saveMediaToDisk`. */
    private fun saveMediaToDisk(media: CreatorMedia, actionId: String): CachedUploadMediaItem {
        val ctx = appContext ?: error("BackgroundStoryUploadService.initialize required")
        val dir = pendingUploadsDir()
        val id = UUID.randomUUID().toString()
        val ext = if (media.isVideo) "mp4" else "jpg"
        val fileName = "${actionId}_${id}_${if (media.isVideo) "vid" else "img"}.$ext"
        val out = File(dir, fileName)
        if (!copyUriToFile(ctx, media.uri, out)) {
            throw IllegalStateException("Could not cache story media to disk")
        }
        val thumbnailFileName = media.thumbnailUri?.let { thumbUri ->
            val thumbName = "${actionId}_${id}_thumb.jpg"
            val thumbOut = File(dir, thumbName)
            thumbName.takeIf { copyUriToFile(ctx, thumbUri, thumbOut) }
        }
        return CachedUploadMediaItem(
            type = if (media.isVideo) "video" else "image",
            localFileName = fileName,
            thumbnailFileName = thumbnailFileName,
            aspectRatio = media.aspectRatio.displayName,
            videoDuration = media.durationSeconds,
            videoFileSize = media.videoFileSize ?: out.length().takeIf { it > 0 },
            videoResolution = media.videoResolution,
        )
    }

    /**
     * ≡ iOS `saveStickerToDisk`.
     * Si el sticker ya tiene [CachedSticker.localImageName] en pending dir, se reutiliza.
     */
    private fun saveStickerToDisk(sticker: CachedSticker, actionId: String): CachedSticker {
        val dir = pendingUploadsDir()
        val existing = sticker.localImageName?.let { File(dir, it) }?.takeIf { it.exists() }
        if (existing != null || sticker.isAnimated) return sticker
        // Sin bitmap en CachedSticker: conservar tal cual (el editor ya materializó PNG si hacía falta).
        return sticker.copy(
            // Nombre con actionId para que deleteActionFiles(iOS-style) lo encuentre.
            localImageName = sticker.localImageName,
        )
    }

    private fun copyUriToFile(ctx: Context, uri: Uri, out: File): Boolean = when (uri.scheme) {
        "file" -> {
            val src = uri.path?.let(::File) ?: return false
            if (!src.exists()) return false
            src.inputStream().use { input -> FileOutputStream(out).use { output -> input.copyTo(output) } }
            true
        }
        else -> ctx.contentResolver.openInputStream(uri)?.use { input ->
            FileOutputStream(out).use { output -> input.copyTo(output) }
        } != null
    }

    // MARK: - processStoryUpload / resume

    suspend fun resumeUpload(action: CachedAction) {
        if (action.type != CachedAction.ActionType.STORY_UPLOAD.raw) return
        if (!inFlightActionIds.add(action.id)) {
            LocalPersistenceService.updateActionStatus(action.id, CachedAction.ActionStatus.PENDING)
            return
        }
        LocalPersistenceService.updateActionStatus(action.id, CachedAction.ActionStatus.EXECUTING)
        try {
            val payload = UploadPayloadDecoder.decodeStoryPayload(action.payloadData)
                ?: throw IllegalStateException("Invalid story upload payload")
            val mediaFile = File(pendingUploadsDir(), payload.mediaItem.localFileName)
            if (!mediaFile.exists()) {
                failAction(action.id, "Missing cached media for pending story upload")
                return
            }

            val mode = StoryVideoMode.from(payload.storyVideoMode)
            if (mode == StoryVideoMode.AUTO_SPLIT && payload.mediaItem.type == "video") {
                processAutoSplitStoryUpload(action, payload, mediaFile)
            } else {
                processSingleStoryUpload(action, payload, mediaFile)
            }
        } catch (e: Exception) {
            failAction(action.id, e.message)
        } finally {
            inFlightActionIds.remove(action.id)
        }
    }

    private suspend fun processSingleStoryUpload(
        action: CachedAction,
        payload: StoryUploadPayload,
        mediaFile: File,
    ) {
        updateProgress(action.id, 0.1, UploadStatus.Uploading)
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: payload.userId
        val storyId = payload.plannedStoryId ?: UUID.randomUUID().toString()
        val mediaId = StoragePathBuilder.storageSafeSegment(UUID.randomUUID().toString())

        val prepared = prepareStoryMediaForPublication(
            actionId = action.id,
            userId = userId,
            storyId = storyId,
            mediaId = mediaId,
            payload = payload,
            mediaFile = mediaFile,
            progressRange = 0.2..0.7,
        )
        updateProgress(action.id, 0.8, UploadStatus.Processing)

        // ≡ iOS PASO 3: createStoryInFirestore (+ post-processing flags)
        val shouldPostProcess = shouldUseStoryPostProcessing(payload, prepared.localVideoFile)
        createStoryDocument(
            payload = payload,
            userId = userId,
            storyId = storyId,
            mediaId = mediaId,
            mediaUrl = prepared.url,
            thumbnailUrl = prepared.thumbnailUrl,
            videoFileSize = prepared.videoFileSize,
            videoResolution = prepared.videoResolution ?: payload.mediaItem.videoResolution,
            videoProcessingStatus = if (payload.mediaItem.type == "video") {
                if (shouldPostProcess) MediaItem.VideoProcessingStatus.PENDING
                else MediaItem.VideoProcessingStatus.READY
            } else {
                null
            },
            originalVideoUrl = if (shouldPostProcess) prepared.url else null,
            duration = payload.mediaItem.videoDuration,
        )
        uploadingStory?.takeIf { it.tempId == action.id }?.storyId = storyId

        // ≡ iOS PASO 4: completed
        updateProgress(action.id, 1.0, UploadStatus.Completed)
        StoryUploadProgressManager.finishUpload()

        // ≡ iOS PASO 5: moderación silenciosa (background)
        uploadScope.launch {
            moderateStoryContentSilently(
                storyId = storyId,
                userId = userId,
                mediaId = mediaId,
                mediaUrl = prepared.url,
                isVideo = payload.mediaItem.type == "video",
                cachedStickers = payload.stickers.orEmpty(),
            )
        }

        // ≡ iOS PASO 6: stickers interactivos
        val stickers = payload.stickers?.let {
            StoryStickerRebuild.rebuildStickers(it, pendingUploadsDir())
        }.orEmpty()
        processInteractiveStickers(
            storyId = storyId,
            storyAuthorId = userId,
            audience = ContentAudience.from(payload.audienceSetting),
            customViewers = payload.customViewers,
            customListId = payload.customListId,
            stickers = stickers,
        )

        // ≡ iOS PASO 7: remove after delay
        delay(1_000)
        finishSuccess(action)
    }

    /** ≡ iOS `processAutoSplitStoryUpload`. */
    private suspend fun processAutoSplitStoryUpload(
        action: CachedAction,
        payload: StoryUploadPayload,
        mediaFile: File,
    ) {
        updateProgress(action.id, 0.1, UploadStatus.Processing)
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: payload.userId
        val clips = StoryVideoProcessingService.splitStoryVideo(Uri.fromFile(mediaFile))
        if (clips.isEmpty()) throw IllegalStateException("Invalid auto-split duration")

        val allStickers = payload.stickers.orEmpty()
        val revealStickers = allStickers.filter { it.type == "reveal" }

        clips.forEachIndexed { index, clip ->
            val isFirst = index == 0
            val clipProgressBase = index.toDouble() / clips.size
            val clipProgressEnd = (index + 1).toDouble() / clips.size
            updateProgress(
                action.id,
                0.1 + clipProgressBase * 0.8,
                UploadStatus.Uploading,
            )

            val segmentStoryId = UUID.randomUUID().toString()
            val mediaId = StoragePathBuilder.storageSafeSegment(UUID.randomUUID().toString())
            val segmentFile = clip.media.uri.path?.let(::File)
                ?: throw IllegalStateException("Missing auto-split clip file")

            val segmentStart = 0.1 + clipProgressBase * 0.8
            val segmentEnd = 0.1 + clipProgressEnd * 0.8
            val segmentUploadEnd = segmentStart + (segmentEnd - segmentStart) * 0.55

            val segmentPayload = payload.copy(
                plannedStoryId = segmentStoryId,
                mediaItem = CachedUploadMediaItem(
                    type = "video",
                    localFileName = segmentFile.name,
                    thumbnailFileName = null,
                    aspectRatio = payload.mediaItem.aspectRatio,
                    videoDuration = clip.duration,
                    videoFileSize = segmentFile.length().takeIf { it > 0 },
                    videoResolution = payload.mediaItem.videoResolution,
                ),
                storyText = if (isFirst) payload.storyText else null,
                textPosition = if (isFirst) payload.textPosition else null,
                selectedTextStyle = if (isFirst) payload.selectedTextStyle else null,
                textOverlayMetadata = if (isFirst) payload.textOverlayMetadata else null,
                textOverlays = if (isFirst) payload.textOverlays else null,
                drawingFileName = if (isFirst) payload.drawingFileName else null,
                stickers = if (isFirst) allStickers else revealStickers,
                chainPosition = payload.chainPosition?.let { it + index },
                storyVideoMode = StoryVideoMode.NORMAL.raw,
            )

            // Copiar clip al pending dir si aún no está ahí
            val pendingSegment = File(pendingUploadsDir(), segmentFile.name)
            if (segmentFile.absolutePath != pendingSegment.absolutePath) {
                segmentFile.copyTo(pendingSegment, overwrite = true)
            }

            val prepared = prepareStoryMediaForPublication(
                actionId = action.id,
                userId = userId,
                storyId = segmentStoryId,
                mediaId = mediaId,
                payload = segmentPayload,
                mediaFile = pendingSegment,
                progressRange = segmentStart..segmentUploadEnd,
            )
            val shouldPostProcess = shouldUseStoryPostProcessing(segmentPayload, prepared.localVideoFile)
            createStoryDocument(
                payload = segmentPayload,
                userId = userId,
                storyId = segmentStoryId,
                mediaId = mediaId,
                mediaUrl = prepared.url,
                thumbnailUrl = prepared.thumbnailUrl,
                videoFileSize = prepared.videoFileSize,
                videoResolution = prepared.videoResolution,
                videoProcessingStatus = if (shouldPostProcess) {
                    MediaItem.VideoProcessingStatus.PENDING
                } else {
                    MediaItem.VideoProcessingStatus.READY
                },
                originalVideoUrl = if (shouldPostProcess) prepared.url else null,
                duration = clip.duration,
            )

            uploadScope.launch {
                moderateStoryContentSilently(
                    storyId = segmentStoryId,
                    userId = userId,
                    mediaId = mediaId,
                    mediaUrl = prepared.url,
                    isVideo = true,
                    cachedStickers = segmentPayload.stickers.orEmpty(),
                )
            }

            if (isFirst) {
                val stickers = segmentPayload.stickers?.let {
                    StoryStickerRebuild.rebuildStickers(it, pendingUploadsDir())
                }.orEmpty()
                processInteractiveStickers(
                    storyId = segmentStoryId,
                    storyAuthorId = userId,
                    audience = ContentAudience.from(payload.audienceSetting),
                    customViewers = payload.customViewers,
                    customListId = payload.customListId,
                    stickers = stickers,
                )
            }

            // Limpiar clip copiado (no el media original de la acción)
            if (pendingSegment.absolutePath != mediaFile.absolutePath) {
                pendingSegment.delete()
            }

            updateProgress(action.id, 0.1 + clipProgressEnd * 0.8, UploadStatus.Uploading)
        }

        updateProgress(action.id, 1.0, UploadStatus.Completed)
        StoryUploadProgressManager.finishUpload()
        delay(1_000)
        finishSuccess(action)
    }

    /**
     * ≡ iOS `prepareMediaItem` + `prepareStoryMediaForPublication` + `uploadStoryMedia`.
     * Progreso Storage mapeado al [progressRange] (como iOS).
     * Poster de vídeo → `storyFrame` (no storyThumbnail).
     */
    private suspend fun prepareStoryMediaForPublication(
        actionId: String,
        userId: String,
        storyId: String,
        mediaId: String,
        payload: StoryUploadPayload,
        mediaFile: File,
        progressRange: ClosedFloatingPointRange<Double>,
    ): PreparedStoryMedia {
        val isVideo = payload.mediaItem.type == "video"
        var workingFile = mediaFile
        if (isVideo && needsCompressionBySize(workingFile)) {
            runCatching {
                val compressed = VideoCompressionService.compressVideoForStory(Uri.fromFile(workingFile))
                compressed.path?.let(::File)?.takeIf { it.exists() }?.let { workingFile = it }
            }
        }

        val progressCb: (Double) -> Unit = { p ->
            val clamped = p.coerceIn(0.0, 1.0)
            val total = progressRange.start +
                (clamped * (progressRange.endInclusive - progressRange.start))
            updateProgress(actionId, total, UploadStatus.Uploading)
        }

        updateProgress(actionId, progressRange.start, UploadStatus.Uploading)
        val mediaUrl = if (isVideo) {
            StorageService.uploadMedia(
                userId = userId,
                mediaItem = UploadMediaItem(type = UploadMediaKind.VIDEO, videoUri = Uri.fromFile(workingFile)),
                context = FeedMediaUploadContext.Story(storyId = storyId, mediaId = mediaId),
                progress = progressCb,
            )
        } else {
            val raw = BitmapFactory.decodeFile(workingFile.absolutePath)
                ?: throw IllegalStateException("Invalid cached image for pending story upload")
            val bitmap = optimizeImageForStory(raw)
            StorageService.uploadMedia(
                userId = userId,
                mediaItem = UploadMediaItem(type = UploadMediaKind.IMAGE, image = bitmap),
                context = FeedMediaUploadContext.Story(storyId = storyId, mediaId = mediaId),
                progress = progressCb,
            )
        }
        updateProgress(actionId, progressRange.endInclusive, UploadStatus.Uploading)

        // ≡ iOS: poster vía extractBackgroundFrameImage + uploadBackgroundFrameImage (storyFrame)
        var thumbnailUrl: String? = null
        if (isVideo) {
            val thumbFromCache = payload.mediaItem.thumbnailFileName?.let { name ->
                File(pendingUploadsDir(), name).takeIf { it.exists() }
            }
            val poster = when {
                thumbFromCache != null -> BitmapFactory.decodeFile(thumbFromCache.absolutePath)
                else -> extractBackgroundFrameImage(Uri.fromFile(workingFile))
            }
            if (poster != null) {
                thumbnailUrl = runCatching {
                    uploadBackgroundFrameImage(poster, userId, storyId)
                }.getOrNull()
            }
        }

        return PreparedStoryMedia(
            url = mediaUrl,
            videoFileSize = if (isVideo) workingFile.length().takeIf { it > 0 } else null,
            videoResolution = payload.mediaItem.videoResolution,
            thumbnailUrl = thumbnailUrl,
            localVideoFile = workingFile.takeIf { isVideo },
        )
    }

    // MARK: - Frames de fondo (≡ extract/upload background frame)

    /** ≡ iOS `extractBackgroundFrame` — frame + upload storyFrame. */
    @Suppress("unused")
    private suspend fun extractBackgroundFrame(
        videoUri: Uri,
        userId: String,
        storyId: String,
    ): String? {
        val frame = extractBackgroundFrameImage(videoUri) ?: return null
        return runCatching { uploadBackgroundFrameImage(frame, userId, storyId) }.getOrNull()
    }

    /** ≡ iOS `extractBackgroundFrameImage` — max ~400×400. */
    private fun extractBackgroundFrameImage(videoUri: Uri): Bitmap? {
        val ctx = appContext ?: return null
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                if (videoUri.scheme == "file") {
                    retriever.setDataSource(videoUri.path)
                } else {
                    retriever.setDataSource(ctx, videoUri)
                }
                val frame = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                    retriever.getScaledFrameAtTime(
                        0L,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        400,
                        400,
                    )
                } else {
                    retriever.getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                }
                frame
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrNull()
    }

    /** ≡ iOS `uploadBackgroundFrameImage` — Storage domain storyFrame, jpeg 0.7. */
    private suspend fun uploadBackgroundFrameImage(
        image: Bitmap,
        userId: String,
        storyId: String,
    ): String? {
        val data = image.storageUploadJpegData(compressionQuality = 0.7f, maxPixelDimension = 400)
            ?: return null
        val target = StoragePathBuilder.build(
            userId,
            StorageUploadDomain.StoryFrame(storyId = storyId, blurred = false),
        )
        return MediaUploadService.upload(target, MediaUploadPayload.Data(data))
    }

    /** ≡ iOS `uploadBlurredBackgroundFrameImage` (createStory sigue pasando nil, como iOS). */
    @Suppress("unused")
    private suspend fun uploadBlurredBackgroundFrameImage(
        image: Bitmap,
        userId: String,
        storyId: String,
    ): String? {
        val blurred = makePreblurredStoryBackground(image)
        val data = blurred.storageUploadJpegData(compressionQuality = 0.72f, maxPixelDimension = 620)
            ?: return null
        val target = StoragePathBuilder.build(
            userId,
            StorageUploadDomain.StoryFrame(storyId = storyId, blurred = true),
        )
        return MediaUploadService.upload(target, MediaUploadPayload.Data(data))
    }

    /** ≡ iOS `makePreblurredStoryBackground` — downsample 9:16 + blur aprox. */
    private fun makePreblurredStoryBackground(image: Bitmap): Bitmap {
        val downsampledWidth = 260
        val screenAspect = 1920f / 1080f
        val downsampledHeight = min(max((downsampledWidth * screenAspect).toInt(), 360), 620)
        val dest = Bitmap.createBitmap(downsampledWidth, downsampledHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(dest)
        val destRect = aspectFillRect(
            imageWidth = image.width.toFloat(),
            imageHeight = image.height.toFloat(),
            boundsWidth = downsampledWidth.toFloat(),
            boundsHeight = downsampledHeight.toFloat(),
        )
        canvas.drawBitmap(
            image,
            Rect(0, 0, image.width, image.height),
            destRect,
            Paint(Paint.FILTER_BITMAP_FLAG),
        )
        return boxBlur(dest, radius = 8)
    }

    /** ≡ iOS `aspectFillRect`. */
    private fun aspectFillRect(
        imageWidth: Float,
        imageHeight: Float,
        boundsWidth: Float,
        boundsHeight: Float,
    ): RectF {
        if (imageWidth <= 0f || imageHeight <= 0f) {
            return RectF(0f, 0f, boundsWidth, boundsHeight)
        }
        val widthScale = boundsWidth / imageWidth
        val heightScale = boundsHeight / imageHeight
        val scale = max(widthScale, heightScale)
        val scaledW = imageWidth * scale
        val scaledH = imageHeight * scale
        return RectF(
            boundsWidth / 2f - scaledW / 2f,
            boundsHeight / 2f - scaledH / 2f,
            boundsWidth / 2f + scaledW / 2f,
            boundsHeight / 2f + scaledH / 2f,
        )
    }

    /** Blur por caja (≈ CIGaussianBlur de iOS para poster preblur). */
    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        if (radius < 1) return src
        val w = src.width
        val h = src.height
        val pixels = IntArray(w * h)
        src.getPixels(pixels, 0, w, 0, 0, w, h)
        val out = pixels.copyOf()
        val div = radius * 2 + 1
        // Horizontal
        for (y in 0 until h) {
            for (x in 0 until w) {
                var r = 0; var g = 0; var b = 0; var a = 0
                for (kx in -radius..radius) {
                    val px = (x + kx).coerceIn(0, w - 1)
                    val c = pixels[y * w + px]
                    a += c ushr 24
                    r += (c shr 16) and 0xff
                    g += (c shr 8) and 0xff
                    b += c and 0xff
                }
                out[y * w + x] =
                    ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            }
        }
        // Vertical
        val finalPx = IntArray(w * h)
        for (x in 0 until w) {
            for (y in 0 until h) {
                var r = 0; var g = 0; var b = 0; var a = 0
                for (ky in -radius..radius) {
                    val py = (y + ky).coerceIn(0, h - 1)
                    val c = out[py * w + x]
                    a += c ushr 24
                    r += (c shr 16) and 0xff
                    g += (c shr 8) and 0xff
                    b += c and 0xff
                }
                finalPx[y * w + x] =
                    ((a / div) shl 24) or ((r / div) shl 16) or ((g / div) shl 8) or (b / div)
            }
        }
        val result = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        result.setPixels(finalPx, 0, w, 0, 0, w, h)
        return result
    }

    /** ≡ iOS `needsCompressionBySize` — >100MB o bitrate >15 Mbps. */
    private fun needsCompressionBySize(videoFile: File): Boolean {
        if (!videoFile.exists()) return false
        if (videoFile.length() > 100L * 1024 * 1024) return true
        return runCatching {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(videoFile.absolutePath)
                val bitrate = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toIntOrNull()
                bitrate != null && bitrate > 15_000_000
            } finally {
                runCatching { retriever.release() }
            }
        }.getOrDefault(false)
    }

    /** ≡ iOS `optimizeImageForStory` — cap 1440px. */
    private fun optimizeImageForStory(image: Bitmap): Bitmap {
        val maxDimension = 1440
        val w = image.width
        val h = image.height
        if (w <= maxDimension && h <= maxDimension) return image
        val scale = minOf(maxDimension.toFloat() / w, maxDimension.toFloat() / h)
        val nw = (w * scale).toInt().coerceAtLeast(1)
        val nh = (h * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(image, nw, nh, true)
    }

    /** ≡ iOS `shouldUseStoryPostProcessing` / `shouldUseServerCompression` (>60MB ready). */
    private fun shouldUseStoryPostProcessing(
        payload: StoryUploadPayload,
        localVideo: File?,
    ): Boolean {
        if (payload.mediaItem.type != "video") return false
        val size = localVideo?.length()
            ?: payload.mediaItem.videoFileSize
            ?: return false
        return size > CreatorMedia.MAX_STORY_VIDEO_READY_SIZE_BYTES
    }

    private suspend fun createStoryDocument(
        payload: StoryUploadPayload,
        userId: String,
        storyId: String,
        mediaId: String,
        mediaUrl: String,
        thumbnailUrl: String?,
        videoFileSize: Long?,
        videoResolution: String?,
        videoProcessingStatus: MediaItem.VideoProcessingStatus?,
        originalVideoUrl: String?,
        duration: Double?,
    ) {
        val isVideo = payload.mediaItem.type == "video"
        val aspectRatio = payload.mediaItem.aspectRatio
            ?: detectAspectRatio(isVideo, File(pendingUploadsDir(), payload.mediaItem.localFileName))
        val mediaItem = MediaItem(
            id = mediaId,
            type = if (isVideo) MediaItem.MediaType.VIDEO else MediaItem.MediaType.IMAGE,
            url = mediaUrl,
            thumbnailUrl = thumbnailUrl,
            videoDuration = duration,
            videoFileSize = videoFileSize,
            videoResolution = videoResolution,
            videoProcessingStatus = videoProcessingStatus,
            originalVideoUrl = originalVideoUrl,
            aspectRatio = aspectRatio,
        )
        val audience = ContentAudience.from(payload.audienceSetting)
        val continuationAudience = payload.continuationAudience?.let { ContentAudience.from(it) }
        val expirationHours = payload.expirationHours ?: if (payload.chainId != null) 48 else 24
        val drawingData = payload.drawingFileName?.let { name ->
            File(pendingUploadsDir(), name).takeIf { it.exists() }?.readBytes()
        }
        val primaryOverlay = payload.textOverlays
            ?.takeIf { it.isNotEmpty() }
            ?.minByOrNull { it.layerOrder }
            ?: payload.textOverlayMetadata
        val resolvedTextStyle = primaryOverlay?.styleRaw?.takeIf { it.isNotBlank() }
            ?: payload.selectedTextStyle
        val resolvedTextOverlays = payload.textOverlays
            ?: payload.textOverlayMetadata?.let { listOf(it) }
        // Posiciones del editor Android ya vienen normalizadas (0…1).
        val stickers = payload.stickers?.let {
            StoryStickerRebuild.rebuildStickers(it, pendingUploadsDir())
        }?.takeIf { it.isNotEmpty() }

        if (audience == ContentAudience.CUSTOM_LIST && payload.customListId != null) {
            firestoreService.createStoryWithCustomList(
                userId = userId,
                mediaItem = mediaItem,
                customListId = payload.customListId,
                text = payload.storyText,
                textPosition = payload.textPosition?.let { Point(it.x, it.y) },
                textStyle = resolvedTextStyle,
                textOverlay = primaryOverlay,
                textOverlays = resolvedTextOverlays,
                stickers = stickers,
                drawingData = drawingData,
                aspectRatio = aspectRatio,
                backgroundFrameURL = null,
                backgroundBlurredFrameURL = null,
                chainId = payload.chainId,
                chainPosition = payload.chainPosition,
                chainTitle = payload.chainTitle,
                allowOthersToContinue = payload.allowOthersToContinue,
                continuationAudience = continuationAudience,
                continuationCustomViewers = payload.continuationCustomViewers,
                continuationCustomListId = payload.continuationCustomListId,
                continuationCustomListName = payload.continuationCustomListName,
                expirationHours = expirationHours,
                duration = duration,
                storyId = storyId,
            )
        } else {
            firestoreService.createStoryWithVisibility(
                userId = userId,
                mediaItem = mediaItem,
                audienceSetting = audience,
                customViewers = payload.customViewers,
                text = payload.storyText,
                textPosition = payload.textPosition?.let { Point(it.x, it.y) },
                textStyle = resolvedTextStyle,
                textOverlay = primaryOverlay,
                textOverlays = resolvedTextOverlays,
                stickers = stickers,
                drawingData = drawingData,
                aspectRatio = aspectRatio,
                backgroundFrameURL = null,
                backgroundBlurredFrameURL = null,
                chainId = payload.chainId,
                chainPosition = payload.chainPosition,
                chainTitle = payload.chainTitle,
                allowOthersToContinue = payload.allowOthersToContinue,
                continuationAudience = continuationAudience,
                continuationCustomViewers = payload.continuationCustomViewers,
                continuationCustomListId = payload.continuationCustomListId,
                continuationCustomListName = payload.continuationCustomListName,
                expirationHours = expirationHours,
                duration = duration,
                storyId = storyId,
            )
        }
    }

    private fun detectAspectRatio(isVideo: Boolean, file: File): String? {
        if (!file.exists()) return null
        return if (isVideo) {
            runCatching {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(file.absolutePath)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
                retriever.release()
                if (w != null && h != null && w > 0 && h > 0) "$w:$h" else null
            }.getOrNull()
        } else {
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, opts)
            if (opts.outWidth > 0 && opts.outHeight > 0) "${opts.outWidth}:${opts.outHeight}" else null
        }
    }

    // MARK: - Interactive stickers

    private suspend fun processInteractiveStickers(
        storyId: String,
        storyAuthorId: String,
        audience: ContentAudience,
        customViewers: List<String>?,
        customListId: String?,
        stickers: List<StickerData>,
    ) {
        if (stickers.isEmpty()) return
        if (stickers.any { it.type == "mention" }) {
            sendMentionNotificationsForStory(
                storyId = storyId,
                storyAuthorId = storyAuthorId,
                audience = audience,
                customViewers = customViewers,
                customListId = customListId,
                stickers = stickers,
            )
        }
        setupPollStickers(storyId, storyAuthorId, stickers.filter { it.type == "poll" })
        setupQuestionStickers(storyId, storyAuthorId, stickers.filter { it.type == "question" })
        setupEmojiSliderStickers(storyId, storyAuthorId, stickers.filter { it.type == "emojiSlider" })
        setupQuestionResponseStickers(stickers.filter { it.type == "questionResponse" })
        setupWeatherStickers(stickers.filter { it.type == "weather" })
        setupQuizStickers(storyId, storyAuthorId, stickers.filter { it.type == "quiz" })
        setupAudioStickers(storyId, stickers.filter { it.type == "audio" })
    }

    private suspend fun setupPollStickers(
        storyId: String,
        userId: String,
        stickers: List<StickerData>,
    ) {
        stickers.forEach { sticker ->
            val stickerId = sticker.stickerId ?: return@forEach
            val pollData = sticker.pollOptions ?: return@forEach
            runCatching {
                FirebaseFirestore.getInstance()
                    .collection("users").document(userId)
                    .collection("stories").document(storyId)
                    .collection("pollVotes").document(stickerId)
                    .set(
                        mapOf(
                            "pollData" to pollData,
                            "stickerId" to stickerId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "totalVotes" to 0,
                            "option0Votes" to 0,
                            "option1Votes" to 0,
                        ),
                    ).await()
            }
        }
    }

    private suspend fun setupQuestionStickers(
        storyId: String,
        userId: String,
        stickers: List<StickerData>,
    ) {
        stickers.forEach { sticker ->
            val stickerId = sticker.stickerId ?: return@forEach
            val questionText = sticker.questionText ?: return@forEach
            runCatching {
                FirebaseFirestore.getInstance()
                    .collection("users").document(userId)
                    .collection("stories").document(storyId)
                    .collection("questionResponses").document(stickerId)
                    .set(
                        mapOf(
                            "questionText" to questionText,
                            "stickerId" to stickerId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "responseCount" to 0,
                        ),
                    ).await()
            }
        }
    }

    private suspend fun setupEmojiSliderStickers(
        storyId: String,
        userId: String,
        stickers: List<StickerData>,
    ) {
        stickers.forEach { sticker ->
            val stickerId = sticker.stickerId ?: return@forEach
            val prompt = sticker.sliderPrompt ?: return@forEach
            val emoji = sticker.sliderEmoji ?: return@forEach
            runCatching {
                FirebaseFirestore.getInstance()
                    .collection("users").document(userId)
                    .collection("stories").document(storyId)
                    .collection("emojiSliders").document(stickerId)
                    .set(
                        mapOf(
                            "stickerId" to stickerId,
                            "prompt" to prompt,
                            "emoji" to emoji,
                            "createdAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
            }
        }
    }

    /** ≡ iOS no-op: solo valida questionText. */
    private fun setupQuestionResponseStickers(stickers: List<StickerData>) {
        stickers.forEach { sticker ->
            sticker.questionText ?: return@forEach
        }
    }

    /** ≡ iOS no-op: valida weatherSymbol + questionText. */
    private fun setupWeatherStickers(stickers: List<StickerData>) {
        stickers.forEach { sticker ->
            if (sticker.weatherSymbol == null || sticker.questionText == null) return@forEach
        }
    }

    private suspend fun setupQuizStickers(
        storyId: String,
        userId: String,
        stickers: List<StickerData>,
    ) {
        stickers.forEach { sticker ->
            val stickerId = sticker.stickerId ?: return@forEach
            val question = sticker.quizQuestion ?: return@forEach
            val options = sticker.quizOptions ?: return@forEach
            runCatching {
                FirebaseFirestore.getInstance()
                    .collection("users").document(userId)
                    .collection("stories").document(storyId)
                    .collection("quizResponses").document(stickerId)
                    .set(
                        mapOf(
                            "question" to question,
                            "options" to options,
                            "correctIndex" to (sticker.quizCorrectIndex ?: 0),
                            "stickerId" to stickerId,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "totalResponses" to 0,
                        ),
                    ).await()
            }
        }
    }

    private suspend fun setupAudioStickers(storyId: String, stickers: List<StickerData>) {
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return
        stickers.forEach { sticker ->
            val stickerId = sticker.stickerId ?: return@forEach
            val local = sticker.audioURL?.let(::File)?.takeIf { it.exists() } ?: return@forEach
            runCatching {
                val target = StoragePathBuilder.build(
                    userId,
                    StorageUploadDomain.StoryStickerAudio(storyId, stickerId),
                )
                val remoteUrl = MediaUploadService.upload(target, MediaUploadPayload.File(Uri.fromFile(local)))
                val reference = FirebaseFirestore.getInstance()
                    .collection("users").document(userId)
                    .collection("stories").document(storyId)
                val document = reference.get().await()
                val serialized = (document.get("stickers") as? List<*>)
                    ?.mapNotNull { it as? Map<String, Any?> }
                    ?: return@runCatching
                val updated = serialized.map { entry ->
                    if (entry["stickerId"] == stickerId) entry + ("audioURL" to remoteUrl) else entry
                }
                reference.update("stickers", updated).await()
                local.delete()
            }
        }
    }

    // MARK: - Moderación silenciosa

    private suspend fun moderateStoryContentSilently(
        storyId: String,
        userId: String,
        mediaId: String,
        mediaUrl: String,
        isVideo: Boolean,
        cachedStickers: List<CachedSticker>,
    ) {
        suspendCancellableCoroutine { cont ->
            MediaModerationService.shared.moderateMedia(
                mediaURL = mediaUrl,
                mediaType = if (isVideo) MediaItem.MediaType.VIDEO else MediaItem.MediaType.IMAGE,
                userId = userId,
                contentId = storyId,
                contentType = ModerationContentType.STORY,
                mediaItemId = mediaId,
            ) {
                cont.resume(Unit)
            }
        }
        moderateStoryImageStickersSilently(storyId, userId, cachedStickers)
    }

    private suspend fun moderateStoryImageStickersSilently(
        storyId: String,
        userId: String,
        cachedStickers: List<CachedSticker>,
    ) {
        val moderatable = cachedStickers.filter { it.type == "frame" || it.type == "selfie" }
        if (moderatable.isEmpty()) return
        val moderated = mutableMapOf<String, MediaModerationAction>()
        for (sticker in moderatable) {
            val localName = sticker.localImageName ?: continue
            val bitmap = BitmapFactory.decodeFile(File(pendingUploadsDir(), localName).absolutePath)
                ?: continue
            val action = suspendCancellableCoroutine { cont ->
                MediaModerationService.shared.moderateStickerImage(
                    image = bitmap,
                    preserveAlpha = sticker.type == "selfie",
                    userId = userId,
                    storyId = storyId,
                    stickerId = sticker.id,
                ) { cont.resume(it) }
            }
            when (action) {
                is MediaModerationAction.Deleted -> moderated[sticker.id] = action
                is MediaModerationAction.Warning -> {
                    MediaModerationService.shared.queueStoryStickerReviewItem(
                        userId = userId,
                        storyId = storyId,
                        stickerId = sticker.id,
                        action = action,
                        details = mapOf("provider" to "backend"),
                    )
                }
                else -> Unit
            }
        }
        if (moderated.isEmpty()) return
        suspendCancellableCoroutine { cont ->
            MediaModerationService.shared.hideStoryStickerItems(
                userId = userId,
                storyId = storyId,
                moderatedStickers = moderated,
            ) {
                cont.resume(Unit)
            }
        }
    }

    // MARK: - Progress / finish

    private fun updateProgress(actionId: String, progress: Double, status: UploadStatus) {
        val clamped = progress.coerceIn(0.0, 1.0)
        StoryUploadProgressManager.updateProgress(clamped)
        uploadingStory?.takeIf { it.tempId == actionId }?.let { story ->
            story.uploadProgress = clamped
            story.status = status
        }
        val statusString = when (status) {
            UploadStatus.Initializing, UploadStatus.Uploading ->
                StoryUploadActivityAttributes.ContentState.STATUS_UPLOADING
            UploadStatus.Processing, UploadStatus.Moderated ->
                StoryUploadActivityAttributes.ContentState.STATUS_PROCESSING
            UploadStatus.Completed ->
                StoryUploadActivityAttributes.ContentState.STATUS_COMPLETED
            UploadStatus.Failed ->
                StoryUploadActivityAttributes.ContentState.STATUS_FAILED
        }
        updateLiveActivity(clamped, statusString)
    }

    private fun failAction(actionId: String, message: String?) {
        LocalPersistenceService.updateActionStatus(
            actionId,
            CachedAction.ActionStatus.FAILED,
            error = message,
        )
        StoryUploadProgressManager.cancelUpload()
        uploadingStory?.takeIf { it.tempId == actionId }?.let { story ->
            story.status = UploadStatus.Failed
            story.errorMessage = message
            story.uploadProgress = 0.0
        }
        isProcessing = false
        updateLiveActivity(0.0, StoryUploadActivityAttributes.ContentState.STATUS_FAILED)
        uploadScope.launch {
            delay(2_000)
            endLiveActivity()
        }
    }

    private fun finishSuccess(action: CachedAction) {
        deleteActionFiles(action)
        LocalPersistenceService.deleteAction(action.id)
        StoryUploadProgressManager.finishUpload()
        uploadingStory?.takeIf { it.tempId == action.id }?.let { story ->
            story.status = UploadStatus.Completed
            story.uploadProgress = 1.0
        }
        NavigationEventBus.emit(CoordinatorNavigationEvent.StoryUploaded)
        updateLiveActivity(1.0, StoryUploadActivityAttributes.ContentState.STATUS_COMPLETED)
        uploadScope.launch {
            delay(3_000)
            endLiveActivity()
        }
    }

    // MARK: - Upload progress notification (≡ iOS ActivityKit Live Activity)

    private var liveActivityAttributes: StoryUploadActivityAttributes? = null
    private var liveActivityStoryId: String? = null

    private fun startLiveActivity(story: UploadingStory? = uploadingStory) {
        val ctx = appContext ?: return
        val uploading = story ?: return
        val previewName = uploading.thumbnailBitmap?.let {
            LiveActivityThumbnailStore.save(ctx, it, uploading.tempId)
        }
        val attrs = StoryUploadActivityAttributes(
            storyId = uploading.tempId,
            mediaType = if (uploading.mediaItem.isVideo) "video" else "image",
            previewImageFileName = previewName,
        )
        liveActivityAttributes = attrs
        liveActivityStoryId = uploading.tempId
        UploadProgressNotificationHelper.showStoryUpload(
            ctx,
            attrs,
            StoryUploadActivityAttributes.ContentState(
                progress = uploading.uploadProgress.coerceIn(0.0, 1.0),
                status = StoryUploadActivityAttributes.ContentState.STATUS_UPLOADING,
            ),
        )
    }

    private fun updateLiveActivity(progress: Double, status: String) {
        val ctx = appContext ?: return
        val attrs = liveActivityAttributes ?: return
        val previewName = attrs.previewImageFileName ?: run {
            uploadingStory?.thumbnailBitmap?.let {
                LiveActivityThumbnailStore.save(ctx, it, attrs.storyId)
            }
        }
        val resolved = if (previewName != null && previewName != attrs.previewImageFileName) {
            attrs.copy(previewImageFileName = previewName).also { liveActivityAttributes = it }
        } else {
            attrs
        }
        UploadProgressNotificationHelper.showStoryUpload(
            ctx,
            resolved,
            StoryUploadActivityAttributes.ContentState(progress = progress, status = status),
        )
    }

    private fun endLiveActivity() {
        val ctx = appContext ?: return
        val id = liveActivityStoryId ?: return
        UploadProgressNotificationHelper.cancelStoryUpload(ctx, id)
        liveActivityAttributes = null
        liveActivityStoryId = null
    }
}
