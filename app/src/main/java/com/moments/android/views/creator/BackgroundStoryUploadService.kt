package com.moments.android.views.creator

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.models.CachedUploadMediaItem
import com.moments.android.models.MediaItem
import com.moments.android.models.Point
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
import com.moments.android.services.storage.StoragePathBuilder
import com.moments.android.services.storage.StorageService
import com.moments.android.services.storage.UploadMediaItem
import com.moments.android.services.storage.UploadMediaKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.UUID

/**
 * Port de BackgroundStoryUploadService.swift (`Views/Creator`).
 * Reanuda subidas persistidas: media principal + overlays/drawing/stickers + Firestore story doc.
 * Post-setup interactivo (poll/quiz/audio CF hooks) sigue en Creator UI al publicar en foreground.
 */
object BackgroundStoryUploadService {

    private val inFlightActionIds = Collections.synchronizedSet(mutableSetOf<String>())
    private val firestoreService = FirestoreService()
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile private var appContext: Context? = null

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    private fun pendingUploadsDir(): File {
        val ctx = appContext ?: error("BackgroundStoryUploadService.initialize required")
        return File(ctx.filesDir, "pending_uploads").also { it.mkdirs() }
    }

    /**
     * Port simplificado del flujo iOS `startPreparingStory` + `persistAction` + `publishPreparedStoryInBackground`
     * para chunk-1 del editor (sin bake de overlays/vídeo).
     * @return actionId si se encoló; null si no hay sesión / media.
     */
    fun uploadStory(
        media: CreatorMedia,
        storyText: String? = null,
        textPosition: Point? = null,
        selectedTextStyle: String? = null,
        textOverlayMetadata: StoryTextOverlayMetadata? = null,
        textOverlays: List<StoryTextOverlayMetadata>? = null,
        drawingData: ByteArray? = null,
        audienceSetting: String = ContentAudience.EVERYONE.raw,
        customViewers: List<String>? = null,
        customListId: String? = null,
        selectedListName: String? = null,
        expirationHours: Int = 24,
    ): String? {
        val ctx = appContext ?: return null
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        val dir = pendingUploadsDir()
        val cached = runCatching {
            val ext = if (media.isVideo) "mp4" else "jpg"
            val name = "story_${UUID.randomUUID()}.$ext"
            val out = File(dir, name)
            val copied = when (media.uri.scheme) {
                "file" -> {
                    val src = media.uri.path?.let { File(it) } ?: return@runCatching null
                    src.inputStream().use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output) }
                    }
                    true
                }
                else -> {
                    ctx.contentResolver.openInputStream(media.uri)?.use { input ->
                        FileOutputStream(out).use { output -> input.copyTo(output) }
                    } != null
                }
            }
            if (!copied) return@runCatching null
            CachedUploadMediaItem(
                type = if (media.isVideo) "video" else "image",
                localFileName = name,
                aspectRatio = media.aspectRatio.displayName,
                videoDuration = media.durationSeconds,
            )
        }.getOrNull() ?: return null

        val drawingFileName = drawingData?.takeIf { it.isNotEmpty() }?.let { bytes ->
            val name = "drawing_${UUID.randomUUID()}.png"
            File(dir, name).writeBytes(bytes)
            name
        }

        val actionId = UUID.randomUUID().toString()
        val plannedId = UUID.randomUUID().toString()
        val preparedOverlays = textOverlays?.takeIf { it.isNotEmpty() }
        val primary = textOverlayMetadata ?: preparedOverlays?.firstOrNull()
        val payload = StoryUploadPayload(
            plannedStoryId = plannedId,
            userId = userId,
            mediaItem = cached,
            storyText = storyText?.takeIf { it.isNotBlank() } ?: primary?.text,
            textPosition = textPosition ?: primary?.normalizedPosition,
            selectedTextStyle = selectedTextStyle ?: primary?.styleRaw,
            textOverlayMetadata = primary,
            textOverlays = preparedOverlays,
            audienceSetting = audienceSetting,
            customViewers = customViewers,
            customListId = customListId,
            selectedListName = selectedListName,
            expirationHours = expirationHours,
            drawingFileName = drawingFileName,
        )
        val action = CachedAction(
            id = actionId,
            type = CachedAction.ActionType.STORY_UPLOAD.raw,
            payloadData = UploadPayloadDecoder.encodeStoryPayload(payload),
        )
        LocalPersistenceService.saveAction(action)
        uploadScope.launch {
            resumeUpload(action)
        }
        return actionId
    }

    suspend fun resumeUpload(action: CachedAction) {
        if (action.type != CachedAction.ActionType.STORY_UPLOAD.raw) return
        if (!inFlightActionIds.add(action.id)) {
            LocalPersistenceService.updateActionStatus(action.id, CachedAction.ActionStatus.PENDING)
            return
        }
        try {
            val payload = UploadPayloadDecoder.decodeStoryPayload(action.payloadData)
                ?: throw IllegalStateException("Invalid story upload payload")
            val userId = FirebaseAuth.getInstance().currentUser?.uid ?: payload.userId
            val mediaFile = File(pendingUploadsDir(), payload.mediaItem.localFileName)
            if (!mediaFile.exists()) {
                LocalPersistenceService.updateActionStatus(
                    action.id,
                    CachedAction.ActionStatus.FAILED,
                    error = "Missing cached media for pending story upload",
                )
                return
            }
            val storyId = payload.plannedStoryId ?: UUID.randomUUID().toString()
            val mediaId = StoragePathBuilder.storageSafeSegment(UUID.randomUUID().toString())
            val isVideo = payload.mediaItem.type == "video"
            var thumbnailUrl: String? = null
            if (isVideo && payload.mediaItem.thumbnailFileName != null) {
                val thumbFile = File(pendingUploadsDir(), payload.mediaItem.thumbnailFileName)
                if (thumbFile.exists()) {
                    BitmapFactory.decodeFile(thumbFile.absolutePath)?.let { bitmap ->
                        thumbnailUrl = StorageService.uploadStoryThumbnail(
                            userId = userId,
                            storyId = storyId,
                            image = bitmap,
                            mediaId = "${mediaId}_thumb",
                        )
                    }
                }
            }
            val uploadContext = FeedMediaUploadContext.Story(storyId = storyId, mediaId = mediaId)
            val mediaUrl = if (isVideo) {
                StorageService.uploadMedia(
                    userId = userId,
                    mediaItem = UploadMediaItem(type = UploadMediaKind.VIDEO, videoUri = Uri.fromFile(mediaFile)),
                    context = uploadContext,
                )
            } else {
                val bitmap = BitmapFactory.decodeFile(mediaFile.absolutePath)
                    ?: throw IllegalStateException("Invalid cached image for pending story upload")
                StorageService.uploadMedia(
                    userId = userId,
                    mediaItem = UploadMediaItem(type = UploadMediaKind.IMAGE, image = bitmap),
                    context = uploadContext,
                )
            }
            val mediaItem = MediaItem(
                id = mediaId,
                type = if (isVideo) MediaItem.MediaType.VIDEO else MediaItem.MediaType.IMAGE,
                url = mediaUrl,
                thumbnailUrl = thumbnailUrl,
                videoDuration = payload.mediaItem.videoDuration,
                videoFileSize = payload.mediaItem.videoFileSize,
                videoResolution = payload.mediaItem.videoResolution,
                videoProcessingStatus = if (isVideo) MediaItem.VideoProcessingStatus.READY else null,
            )
            val audience = ContentAudience.from(payload.audienceSetting)
            val continuationAudience = payload.continuationAudience?.let { ContentAudience.from(it) }
            val expirationHours = payload.expirationHours ?: if (payload.chainId != null) 48 else 24
            val drawingData = payload.drawingFileName?.let { name ->
                File(pendingUploadsDir(), name).takeIf { it.exists() }?.readBytes()
            }
            val resolvedTextOverlays = payload.textOverlays
                ?: payload.textOverlayMetadata?.let { listOf(it) }
            val stickers = payload.stickers?.let {
                StoryStickerRebuild.rebuildStickers(it, pendingUploadsDir())
            }?.takeIf { list -> list.isNotEmpty() }
            if (audience == ContentAudience.CUSTOM_LIST && payload.customListId != null) {
                firestoreService.createStoryWithCustomList(
                    userId = userId,
                    mediaItem = mediaItem,
                    customListId = payload.customListId,
                    text = payload.storyText,
                    textPosition = payload.textPosition?.let { Point(it.x, it.y) },
                    textStyle = payload.selectedTextStyle,
                    textOverlay = payload.textOverlayMetadata,
                    textOverlays = resolvedTextOverlays,
                    stickers = stickers,
                    drawingData = drawingData,
                    chainId = payload.chainId,
                    chainPosition = payload.chainPosition,
                    chainTitle = payload.chainTitle,
                    allowOthersToContinue = payload.allowOthersToContinue,
                    continuationAudience = continuationAudience,
                    continuationCustomViewers = payload.continuationCustomViewers,
                    continuationCustomListId = payload.continuationCustomListId,
                    continuationCustomListName = payload.continuationCustomListName,
                    expirationHours = expirationHours,
                    duration = payload.mediaItem.videoDuration,
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
                    textStyle = payload.selectedTextStyle,
                    textOverlay = payload.textOverlayMetadata,
                    textOverlays = resolvedTextOverlays,
                    stickers = stickers,
                    drawingData = drawingData,
                    chainId = payload.chainId,
                    chainPosition = payload.chainPosition,
                    chainTitle = payload.chainTitle,
                    allowOthersToContinue = payload.allowOthersToContinue,
                    continuationAudience = continuationAudience,
                    continuationCustomViewers = payload.continuationCustomViewers,
                    continuationCustomListId = payload.continuationCustomListId,
                    continuationCustomListName = payload.continuationCustomListName,
                    expirationHours = expirationHours,
                    duration = payload.mediaItem.videoDuration,
                    storyId = storyId,
                )
            }
            LocalPersistenceService.updateActionStatus(action.id, CachedAction.ActionStatus.COMPLETED)
            // iOS: NotificationCenter.post("StoryUploaded")
            com.moments.android.coordinators.NavigationEventBus.emit(
                com.moments.android.coordinators.CoordinatorNavigationEvent.StoryUploaded,
            )
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
}
