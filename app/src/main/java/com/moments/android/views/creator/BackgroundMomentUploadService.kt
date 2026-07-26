package com.moments.android.views.creator

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.models.CachedHiddenLayerDraft
import com.moments.android.models.CachedUploadMediaItem
import com.moments.android.models.HiddenLayerImageFrameStyle
import com.moments.android.models.HiddenLayerPresentationStyle
import com.moments.android.models.HiddenLayerTextStyle
import com.moments.android.models.MediaItem
import com.moments.android.models.Moment
import com.moments.android.models.MomentHiddenLayer
import com.moments.android.models.MomentUploadPayload
import com.moments.android.models.UploadPayloadDecoder
import com.moments.android.models.cache.CachedAction
import com.moments.android.moderation.MediaModerationAction
import com.moments.android.moderation.MediaModerationService
import com.moments.android.moderation.ModerationContentType
import com.moments.android.notifications.services.NotificationService
import com.moments.android.services.firestore.FirestoreService
import com.moments.android.services.firestore.createMomentWithCustomList
import com.moments.android.services.firestore.createMomentWithVisibility
import com.moments.android.services.firestore.hideHiddenLayer
import com.moments.android.services.firestore.markHiddenLayerVisible
import com.moments.android.services.firestore.saveHiddenLayers
import com.moments.android.services.firestore.updateMomentHiddenLayerSummary
import com.moments.android.services.persistence.LocalPersistenceService
import com.moments.android.services.privacy.ContentAudience
import com.moments.android.services.social.EchoService
import com.moments.android.services.storage.CreatorMediaLimits
import com.moments.android.services.storage.FeedMediaUploadContext
import com.moments.android.services.storage.MediaUploadService
import com.moments.android.services.storage.StoragePathBuilder
import com.moments.android.services.storage.StorageService
import com.moments.android.services.storage.UploadMediaItem
import com.moments.android.services.storage.UploadMediaKind
import com.moments.android.services.storage.VideoCompressionPreset
import com.moments.android.services.storage.VideoCompressionService
import com.moments.android.services.storage.storageUploadJpegData
import com.moments.android.views.feed.core.FeedViewModel
import com.moments.android.views.feed.uploads.MomentUploadTracker
import com.moments.android.views.feed.uploads.UploadKind
import com.moments.android.views.feed.uploads.UploadProgressItem
import com.moments.android.views.feed.uploads.UploadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.Date
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.math.max
import kotlin.math.min

// MARK: - Modelo de momento en progreso (mismo archivo que iOS)

/**
 * Port de `UploadingMoment` (BackgroundMomentUploadService.swift).
 * Estado observable para el placeholder del feed mientras sube.
 */
class UploadingMoment(
    val userId: String,
    val content: String,
    val mediaItems: List<CreatorMedia>,
    val taggedUsers: List<String>?,
    val mentionedUsers: List<String>?,
    val location: String?,
    val locationCoordinate: Moment.LocationCoordinate?,
    /** Raw ContentAudience / AudienceSetting string (iOS convierte AudienceSetting → raw). */
    val audienceSetting: String,
    val customViewers: List<String>?,
    val customListId: String?,
    val aspectRatio: String,
    val disableComments: Boolean = false,
    val hideLikeCounts: Boolean = false,
    val allowSharing: Boolean = true,
    val scheduledDate: Date? = null,
    val hiddenLayers: List<HiddenLayerDraft> = emptyList(),
    tempId: String? = null,
    plannedMomentId: String? = null,
) {
    val id: String = UUID.randomUUID().toString()
    val tempId: String = tempId ?: "temp_${UUID.randomUUID()}"
    val plannedMomentId: String = plannedMomentId ?: UUID.randomUUID().toString()
    val createdAt: Date = Date()

    var uploadProgress by mutableDoubleStateOf(0.0)
    var status by mutableStateOf(UploadStatus.Uploading)
    var errorMessage by mutableStateOf<String?>(null)
    var momentId by mutableStateOf<String?>(null)

    var thumbnailBitmap by mutableStateOf<Bitmap?>(null)
    /** iOS `currentMediaThumbnailImage`. */
    var currentMediaThumbnailBitmap by mutableStateOf<Bitmap?>(null)
    var mediaCount by mutableIntStateOf(mediaItems.size.coerceAtLeast(1))
    var currentMediaIndex by mutableIntStateOf(0)

    init {
        mediaCount = mediaItems.size.coerceAtLeast(1)
    }

    /** Equiv. iOS `UploadStatus.shouldShowInFeed`. */
    val shouldShowInFeed: Boolean
        get() = when (status) {
            UploadStatus.Initializing, UploadStatus.Uploading, UploadStatus.Processing, UploadStatus.Failed -> true
            UploadStatus.Completed, UploadStatus.Moderated -> false
        }
}

/** iOS `UploadStatus.displayText` (strings creator.upload.*). */
fun UploadStatus.displayText(context: Context): String {
    val res = when (this) {
        UploadStatus.Initializing -> R.string.creator_upload_initializing
        UploadStatus.Uploading -> R.string.creator_upload_uploading
        UploadStatus.Processing -> R.string.creator_upload_processing
        UploadStatus.Completed, UploadStatus.Moderated -> R.string.creator_upload_completed
        UploadStatus.Failed -> R.string.creator_upload_failed
    }
    return context.getString(res)
}

/** Port de `MomentVideoUploadPreparationError`. */
sealed class MomentVideoUploadPreparationError(message: String) : Exception(message) {
    data class CompressedVideoTooLarge(val size: Long, val limit: Long) :
        MomentVideoUploadPreparationError("compressed video too large: $size > $limit")
}

// MARK: - Servicio principal

/**
 * Port de `BackgroundMomentUploadService.swift` (Views/Creator).
 *
 * Live Activity / Dynamic Island (ActivityKit): **fuera de alcance Android** — stubs no-op.
 * Paridad: UploadingMoment en feed, persistencia outbox, Storage → Firestore → hidden layers →
 * moderación → notificaciones tag/mention → EchoService.
 */
object BackgroundMomentUploadService {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningUploadJobs = Collections.synchronizedMap(mutableMapOf<String, Job>())
    private val firestoreService = FirestoreService()

    @Volatile private var appContext: Context? = null
    @Volatile private var feedViewModel: FeedViewModel? = null

    /** iOS `@Published var uploadingMoments`. */
    val uploadingMoments = mutableStateListOf<UploadingMoment>()

    /** iOS `@Published var isProcessing`. */
    var isProcessing by mutableStateOf(false)
        private set

    fun initialize(context: Context) {
        if (appContext == null) appContext = context.applicationContext
    }

    fun setFeedViewModel(viewModel: FeedViewModel?) {
        feedViewModel = viewModel
    }

    /** Compat tipado débil (callers antiguos). */
    fun setFeedViewModel(viewModel: Any?) {
        feedViewModel = viewModel as? FeedViewModel
    }

    fun pauseFeedListeners() {
        feedViewModel?.pauseListenersForUpload()
    }

    fun resumeFeedListeners() {
        feedViewModel?.resumeListenersAfterUpload()
    }

    /**
     * Port de iOS `uploadMoment(...)` → `UploadingMoment?`.
     * Callers que solo necesitan éxito: `uploadMoment(...) != null`.
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
        hiddenLayers: List<HiddenLayerDraft>? = null,
        recoveryActionId: String? = null,
        shouldPersistAction: Boolean = true,
        plannedMomentId: String? = null,
    ): UploadingMoment? {
        val ctx = appContext ?: return null
        val userId = FirebaseAuth.getInstance().currentUser?.uid ?: return null
        if (mediaItems.isEmpty()) return null

        pauseFeedListeners()

        val uploadingMoment = UploadingMoment(
            userId = userId,
            content = content,
            mediaItems = mediaItems,
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
            hiddenLayers = hiddenLayers.orEmpty(),
            tempId = recoveryActionId,
            plannedMomentId = plannedMomentId,
        )

        uploadingMoments.add(uploadingMoment)
        isProcessing = true
        trackProgress(uploadingMoment)
        startLiveActivity(uploadingMoment) // stub ActivityKit

        val job = scope.launch {
            try {
                if (shouldPersistAction) {
                    try {
                        withContext(Dispatchers.IO) { persistAction(ctx, uploadingMoment) }
                    } catch (_: Exception) {
                        updateProgress(
                            uploadingMoment,
                            0.0,
                            UploadStatus.Failed,
                            ctx.getString(R.string.creator_upload_persistenceFailed),
                        )
                        deleteActionFiles(uploadingMoment.tempId)
                        runningUploadJobs.remove(uploadingMoment.tempId)
                        return@launch
                    }
                }
                processUpload(uploadingMoment)
                if (uploadingMoment.status == UploadStatus.Completed ||
                    uploadingMoment.status == UploadStatus.Moderated
                ) {
                    LocalPersistenceService.deleteAction(uploadingMoment.tempId)
                    deleteActionFiles(uploadingMoment.tempId)
                }
            } finally {
                runningUploadJobs.remove(uploadingMoment.tempId)
            }
        }
        runningUploadJobs[uploadingMoment.tempId] = job
        return uploadingMoment
    }

    // MARK: - Procesamiento completo

    private suspend fun processUpload(uploadingMoment: UploadingMoment) {
        val job = runningUploadJobs[uploadingMoment.tempId]
        try {
            if (uploadingMoment.momentId == null) {
                uploadingMoment.momentId = uploadingMoment.plannedMomentId
            }

            val mediaUrls = uploadMediaFiles(uploadingMoment)
            updateProgress(uploadingMoment, 0.8, UploadStatus.Processing)
            val momentId = createMomentInFirestore(uploadingMoment, mediaUrls)
            uploadingMoment.momentId = momentId

            updateProgress(uploadingMoment, 0.88, UploadStatus.Processing)
            val uploadedHiddenLayers = uploadHiddenLayersIfNeeded(
                uploadingMoment = uploadingMoment,
                momentId = momentId,
            )

            notifyTaggedAndMentioned(uploadingMoment, momentId)

            ioScope.launch {
                moderateContentSilently(momentId, uploadingMoment.userId, mediaUrls, uploadingMoment)
                moderateHiddenLayersSilently(momentId, uploadingMoment.userId, uploadedHiddenLayers)
            }

            updateProgress(uploadingMoment, 1.0, UploadStatus.Completed)
            updateLiveActivityAsync(1.0, "completed")
            delay(3_000)
            endLiveActivityAsync()

            delay(1_000)
            resumeFeedListeners()
            delay(2_000)
            removeUploadingMoment(uploadingMoment)

            EchoService.checkForEchoOverlap(momentId, uploadingMoment.userId)
        } catch (e: Exception) {
            if (job?.isCancelled == true || !scope.isActive) return
            updateProgress(uploadingMoment, 0.0, UploadStatus.Failed, e.message)
            delay(500)
            resumeFeedListeners()
        }
        isProcessing = uploadingMoments.any {
            it.status == UploadStatus.Uploading || it.status == UploadStatus.Processing
        }
    }

    private fun notifyTaggedAndMentioned(uploadingMoment: UploadingMoment, momentId: String) {
        val username = appContext
            ?.getSharedPreferences("moments_prefs", Context.MODE_PRIVATE)
            ?.getString("current_username", null)
        val title = uploadingMoment.content.trim()
        uploadingMoment.taggedUsers.orEmpty().toSet().forEach { taggedUserId ->
            if (taggedUserId != uploadingMoment.userId) {
                NotificationService.sendPhotoTagNotification(
                    targetUserId = taggedUserId,
                    momentId = momentId,
                    momentAuthorId = uploadingMoment.userId,
                    momentAuthorUsername = username,
                    momentTitle = title,
                )
            }
        }
        val explicitTags = uploadingMoment.taggedUsers.orEmpty().toSet()
        uploadingMoment.mentionedUsers.orEmpty().toSet().forEach { mentionedUserId ->
            if (mentionedUserId != uploadingMoment.userId && mentionedUserId !in explicitTags) {
                NotificationService.sendMomentMentionNotification(
                    targetUserId = mentionedUserId,
                    momentId = momentId,
                    momentAuthorId = uploadingMoment.userId,
                    momentAuthorUsername = username,
                    commentText = uploadingMoment.content,
                    senderUsername = username,
                )
            }
        }
    }

    // MARK: - Upload de archivos

    private suspend fun uploadMediaFiles(uploadingMoment: UploadingMoment): List<MediaItem> {
        val ctx = appContext ?: error("BackgroundMomentUploadService.initialize required")
        val uploaded = mutableListOf<MediaItem>()
        val totalFiles = uploadingMoment.mediaItems.size.coerceAtLeast(1)

        uploadingMoment.mediaItems.forEachIndexed { index, media ->
            if (runningUploadJobs[uploadingMoment.tempId]?.isCancelled == true) {
                throw kotlinx.coroutines.CancellationException()
            }
            uploadingMoment.currentMediaIndex = index

            val previewBitmap = decodeMediaPreview(ctx, media)
            uploadingMoment.currentMediaThumbnailBitmap = previewBitmap
            if (uploadingMoment.thumbnailBitmap == null && previewBitmap != null) {
                uploadingMoment.thumbnailBitmap = previewBitmap
            }

            val baseProgress = index.toDouble() / totalFiles * 0.7
            val fileProgressSpan = 0.7 / totalFiles
            val hasValidVideoThumbnail = media.isVideo && previewBitmap
                ?.storageUploadJpegData(compressionQuality = 0.75f, maxPixelDimension = 720) != null
            val thumbnailProgressShare = if (hasValidVideoThumbnail) 0.1 else 0.0
            val mediaProgressShare = 1.0 - thumbnailProgressShare
            updateProgress(uploadingMoment, baseProgress)

            val momentId = uploadingMoment.plannedMomentId
            val mediaId = StoragePathBuilder.storageSafeSegment(media.id)
            val uploadContext = FeedMediaUploadContext.Moment(momentId = momentId, mediaId = mediaId)

            var videoFileSize: Long? = null
            if (media.isVideo) {
                // iOS mide el tamaño del archivo local; la compresión ocurre dentro de StorageService.uploadMedia
                videoFileSize = fileSize(ctx, media.uri)
            }

            var thumbnailUrl: String? = null
            if (hasValidVideoThumbnail && previewBitmap != null) {
                thumbnailUrl = StorageService.uploadMomentThumbnail(
                    userId = uploadingMoment.userId,
                    momentId = momentId,
                    image = previewBitmap,
                    mediaId = "${mediaId}_thumb",
                    progress = { p ->
                        val totalProgress = baseProgress + (fileProgressSpan * thumbnailProgressShare * p)
                        updateProgress(uploadingMoment, totalProgress, UploadStatus.Uploading)
                    },
                )
            }

            val url = if (media.isVideo) {
                StorageService.uploadMedia(
                    userId = uploadingMoment.userId,
                    mediaItem = UploadMediaItem(type = UploadMediaKind.VIDEO, videoUri = media.uri),
                    context = uploadContext,
                    progress = { p ->
                        val mediaStart = baseProgress + (fileProgressSpan * thumbnailProgressShare)
                        val totalProgress = mediaStart + (fileProgressSpan * mediaProgressShare * p)
                        updateProgress(uploadingMoment, totalProgress, UploadStatus.Uploading)
                    },
                )
            } else {
                val bitmap = withContext(Dispatchers.IO) {
                    ctx.contentResolver.openInputStream(media.uri)?.use { BitmapFactory.decodeStream(it) }
                } ?: error("Invalid image")
                if (uploadingMoment.thumbnailBitmap == null) {
                    uploadingMoment.thumbnailBitmap = bitmap
                }
                StorageService.uploadMedia(
                    userId = uploadingMoment.userId,
                    mediaItem = UploadMediaItem(type = UploadMediaKind.IMAGE, image = bitmap),
                    context = uploadContext,
                    progress = { p ->
                        val mediaStart = baseProgress + (fileProgressSpan * thumbnailProgressShare)
                        val totalProgress = mediaStart + (fileProgressSpan * mediaProgressShare * p)
                        updateProgress(uploadingMoment, totalProgress, UploadStatus.Uploading)
                    },
                )
            }

            if (media.isVideo) {
                videoFileSize = fileSize(ctx, media.uri)
            }
            val shouldProcessVideo = media.isVideo &&
                (videoFileSize ?: 0L) > CreatorMediaLimits.MAX_MOMENT_VIDEO_READY_SIZE_BYTES

            uploaded += MediaItem(
                id = mediaId,
                type = if (media.isVideo) MediaItem.MediaType.VIDEO else MediaItem.MediaType.IMAGE,
                url = url,
                aspectRatio = media.aspectRatio.displayName,
                thumbnailUrl = thumbnailUrl,
                videoDuration = media.durationSeconds,
                videoFileSize = videoFileSize,
                videoProcessingStatus = when {
                    !media.isVideo -> null
                    shouldProcessVideo -> MediaItem.VideoProcessingStatus.PENDING
                    else -> MediaItem.VideoProcessingStatus.READY
                },
                originalVideoUrl = if (shouldProcessVideo) url else null,
                tags = media.tags.takeIf { it.isNotEmpty() },
            )
            val fileEnd = (index + 1).toDouble() / totalFiles * 0.7
            updateProgress(uploadingMoment, fileEnd, UploadStatus.Uploading)
        }
        return uploaded
    }

    private fun decodeMediaPreview(ctx: Context, media: CreatorMedia): Bitmap? {
        return when {
            media.thumbnailUri != null -> runCatching {
                ctx.contentResolver.openInputStream(media.thumbnailUri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            !media.isVideo -> runCatching {
                ctx.contentResolver.openInputStream(media.uri)?.use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            else -> runCatching {
                val retriever = android.media.MediaMetadataRetriever()
                try {
                    retriever.setDataSource(ctx, media.uri)
                    retriever.getFrameAtTime(0)
                } finally {
                    retriever.release()
                }
            }.getOrNull()
        }
    }

    /** Port de iOS `prepareVideoURLForMomentUpload` (compresión vía `compressVideo`). */
    private suspend fun prepareVideoURLForMomentUpload(
        media: CreatorMedia,
        uploadingMoment: UploadingMoment,
        baseProgress: Double,
    ): Uri {
        val ctx = appContext ?: return media.uri
        val originalSize = fileSize(ctx, media.uri)
        if (originalSize <= CreatorMediaLimits.MAX_MOMENT_VIDEO_UPLOAD_SIZE_BYTES) {
            return media.uri
        }
        updateProgress(uploadingMoment, baseProgress, UploadStatus.Processing)
        val compressed = compressVideo(media.uri)
        val compressedSize = fileSize(ctx, compressed)
        if (compressedSize > CreatorMediaLimits.MAX_MOMENT_VIDEO_UPLOAD_SIZE_BYTES) {
            throw MomentVideoUploadPreparationError.CompressedVideoTooLarge(
                compressedSize,
                CreatorMediaLimits.MAX_MOMENT_VIDEO_UPLOAD_SIZE_BYTES,
            )
        }
        updateProgress(uploadingMoment, baseProgress, UploadStatus.Uploading)
        return compressed
    }

    /** Port de iOS `compressVideo` → `VideoCompressionService` (preset moment / 720p). */
    private suspend fun compressVideo(inputUri: Uri): Uri {
        return try {
            VideoCompressionService.prepareVideoForUpload(inputUri, VideoCompressionPreset.MOMENT)
        } catch (e: com.moments.android.services.storage.VideoCompressionError.OutputTooLarge) {
            throw MomentVideoUploadPreparationError.CompressedVideoTooLarge(e.size, e.limit)
        }
    }

    private fun fileSize(ctx: Context, uri: Uri): Long {
        return runCatching {
            ctx.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
        }.getOrDefault(0L)
    }

    /** Port de iOS `convertAudienceSettingToString` — en Android ya guardamos raw string. */
    private fun convertAudienceSettingToString(audienceSetting: String): String = audienceSetting

    // MARK: - Firestore

    private suspend fun createMomentInFirestore(
        uploadingMoment: UploadingMoment,
        mediaUrls: List<MediaItem>,
    ): String {
        val audience = ContentAudience.from(uploadingMoment.audienceSetting)
        val customListId = uploadingMoment.customListId
        return if (
            !customListId.isNullOrBlank() &&
            (audience == ContentAudience.CUSTOM || audience == ContentAudience.CUSTOM_LIST)
        ) {
            firestoreService.createMomentWithCustomList(
                userId = uploadingMoment.userId,
                content = uploadingMoment.content,
                mediaItems = mediaUrls,
                customListId = customListId,
                taggedUsers = uploadingMoment.taggedUsers,
                mentionedUsers = uploadingMoment.mentionedUsers,
                location = uploadingMoment.location,
                locationCoordinate = uploadingMoment.locationCoordinate,
                aspectRatio = uploadingMoment.aspectRatio,
                disableComments = uploadingMoment.disableComments,
                hideLikeCounts = uploadingMoment.hideLikeCounts,
                allowSharing = uploadingMoment.allowSharing,
                scheduledDate = uploadingMoment.scheduledDate,
                momentId = uploadingMoment.plannedMomentId,
            )
        } else {
            firestoreService.createMomentWithVisibility(
                userId = uploadingMoment.userId,
                content = uploadingMoment.content,
                mediaItems = mediaUrls,
                audience = audience,
                customViewers = uploadingMoment.customViewers,
                taggedUsers = uploadingMoment.taggedUsers,
                mentionedUsers = uploadingMoment.mentionedUsers,
                location = uploadingMoment.location,
                locationCoordinate = uploadingMoment.locationCoordinate,
                selectedListId = uploadingMoment.customListId,
                aspectRatio = uploadingMoment.aspectRatio,
                disableComments = uploadingMoment.disableComments,
                hideLikeCounts = uploadingMoment.hideLikeCounts,
                allowSharing = uploadingMoment.allowSharing,
                scheduledDate = uploadingMoment.scheduledDate,
                momentId = uploadingMoment.plannedMomentId,
            )
        }
    }

    // MARK: - Progress / cancel / retry

    private fun updateProgress(
        moment: UploadingMoment,
        progress: Double,
        status: UploadStatus? = null,
        error: String? = null,
    ) {
        moment.uploadProgress = progress.coerceIn(0.0, 1.0)
        if (status != null) moment.status = status
        if (error != null) moment.errorMessage = error
        trackProgress(moment)
        // iOS Live Activity status strings
        val statusString = when (status) {
            UploadStatus.Initializing, UploadStatus.Uploading -> "uploading"
            UploadStatus.Processing, UploadStatus.Moderated -> "processing"
            UploadStatus.Completed -> "completed"
            UploadStatus.Failed -> "failed"
            null -> if (progress < 0.7) "uploading" else "processing"
        }
        updateLiveActivity(progress, statusString)
    }

    private fun trackProgress(moment: UploadingMoment) {
        MomentUploadTracker.upsert(
            UploadProgressItem(
                id = moment.tempId,
                kind = UploadKind.Moment,
                progress = moment.uploadProgress,
                status = moment.status,
                fileCount = moment.mediaCount,
                content = moment.content,
            ),
        )
    }

    private fun removeUploadingMoment(moment: UploadingMoment) {
        uploadingMoments.removeAll { it.tempId == moment.tempId }
        MomentUploadTracker.remove(moment.tempId)
        isProcessing = uploadingMoments.any {
            it.status == UploadStatus.Uploading || it.status == UploadStatus.Processing
        }
    }

    fun retryUpload(moment: UploadingMoment) {
        if (moment.status != UploadStatus.Failed) return
        moment.status = UploadStatus.Uploading
        moment.uploadProgress = 0.0
        moment.errorMessage = null
        moment.currentMediaIndex = 0
        moment.currentMediaThumbnailBitmap = moment.thumbnailBitmap
        isProcessing = true
        val job = scope.launch {
            processUpload(moment)
            runningUploadJobs.remove(moment.tempId)
        }
        runningUploadJobs[moment.tempId] = job
    }

    fun cancelUpload(moment: UploadingMoment) {
        val storagePrefix = "users/${moment.userId}/moments/${moment.plannedMomentId}/"
        runningUploadJobs.remove(moment.tempId)?.cancel()
        MediaUploadService.cancelUploads(storagePrefix)
        removeUploadingMoment(moment)
    }

    // MARK: - Live Activity stubs (ActivityKit iOS-only 🚫)

    @Suppress("UNUSED_PARAMETER")
    private fun startLiveActivity(uploadingMoment: UploadingMoment) = Unit

    @Suppress("UNUSED_PARAMETER")
    private fun updateLiveActivity(progress: Double, status: String) = Unit

    /** iOS async variant — mismo no-op en Android. */
    @Suppress("UNUSED_PARAMETER")
    private suspend fun updateLiveActivityAsync(progress: Double, status: String) {
        updateLiveActivity(progress, status)
    }

    private fun endLiveActivity() = Unit

    private suspend fun endLiveActivityAsync() {
        endLiveActivity()
    }

    fun cleanupStaleUploadActivities() = Unit

    // MARK: - Persistence

    private fun pendingUploadsDir(): File {
        val ctx = appContext ?: error("BackgroundMomentUploadService.initialize required")
        return File(ctx.filesDir, "pending_uploads").also { it.mkdirs() }
    }

    /** Port de iOS `persistAction`. */
    private fun persistAction(ctx: Context, uploadingMoment: UploadingMoment) {
        val cachedMedia = uploadingMoment.mediaItems.map { media ->
            saveMediaToDisk(ctx, media, uploadingMoment.tempId)
        }
        if (cachedMedia.isEmpty()) error("No media persisted")

        val cachedHiddenLayers = uploadingMoment.hiddenLayers.map { layer ->
            saveHiddenLayerToDisk(ctx, layer, uploadingMoment.tempId)
        }

        val payload = MomentUploadPayload(
            plannedMomentId = uploadingMoment.plannedMomentId,
            content = uploadingMoment.content,
            mediaPaths = cachedMedia,
            taggedUsers = uploadingMoment.taggedUsers,
            mentionedUsers = uploadingMoment.mentionedUsers,
            location = uploadingMoment.location,
            locationCoordinate = uploadingMoment.locationCoordinate,
            audienceSetting = convertAudienceSettingToString(uploadingMoment.audienceSetting),
            customViewers = uploadingMoment.customViewers,
            customListId = uploadingMoment.customListId,
            aspectRatio = uploadingMoment.aspectRatio,
            disableComments = uploadingMoment.disableComments,
            hideLikeCounts = uploadingMoment.hideLikeCounts,
            allowSharing = uploadingMoment.allowSharing,
            scheduledDate = uploadingMoment.scheduledDate,
            hiddenLayers = cachedHiddenLayers.takeIf { it.isNotEmpty() },
        )
        val action = CachedAction(
            id = uploadingMoment.tempId,
            type = CachedAction.ActionType.MOMENT_UPLOAD.raw,
            payloadData = UploadPayloadDecoder.encodeMomentPayload(payload),
        )
        LocalPersistenceService.saveActionOrThrow(action)
    }

    /** Port de iOS `saveMediaToDisk`. */
    private fun saveMediaToDisk(
        ctx: Context,
        media: CreatorMedia,
        actionId: String,
    ): CachedUploadMediaItem {
        val id = UUID.randomUUID().toString()
        val fileName = "${actionId}_${id}_${if (media.isVideo) "vid.mp4" else "img.jpg"}"
        val dir = pendingUploadsDir()
        val out = File(dir, fileName)

        if (media.isVideo) {
            ctx.contentResolver.openInputStream(media.uri)?.use { input ->
                FileOutputStream(out).use { output -> input.copyTo(output) }
            } ?: error("Missing video for persist")
        } else {
            val bitmap = ctx.contentResolver.openInputStream(media.uri)?.use { BitmapFactory.decodeStream(it) }
                ?: error("Missing image for persist")
            val jpeg = bitmap.storageUploadJpegData(compressionQuality = 0.8f, maxPixelDimension = 4096)
                ?: error("Cannot encode image")
            out.writeBytes(jpeg)
        }

        var thumbName: String? = null
        if (media.thumbnailUri != null) {
            thumbName = "${actionId}_${id}_thumb.jpg"
            val thumbOut = File(dir, thumbName!!)
            ctx.contentResolver.openInputStream(media.thumbnailUri)?.use { input ->
                FileOutputStream(thumbOut).use { output -> input.copyTo(output) }
            }
        } else if (media.isVideo) {
            val frame = decodeMediaPreview(ctx, media)
            val thumbBytes = frame?.storageUploadJpegData(compressionQuality = 0.75f, maxPixelDimension = 720)
            if (thumbBytes != null) {
                thumbName = "${actionId}_${id}_thumb.jpg"
                File(dir, thumbName!!).writeBytes(thumbBytes)
            }
        }

        return CachedUploadMediaItem(
            type = if (media.isVideo) "video" else "image",
            localFileName = fileName,
            thumbnailFileName = thumbName,
            aspectRatio = media.aspectRatio.displayName,
            videoDuration = media.durationSeconds,
            videoFileSize = if (media.isVideo) fileSize(ctx, media.uri) else null,
            tags = media.tags.takeIf { it.isNotEmpty() },
        )
    }

    /** Port de iOS `saveHiddenLayerToDisk`. */
    private fun saveHiddenLayerToDisk(
        ctx: Context,
        layer: HiddenLayerDraft,
        actionId: String,
    ): CachedHiddenLayerDraft {
        val filePrefix = "${actionId}_hidden_${layer.id}"
        var imageFileName: String? = null
        var audioFileName: String? = null
        val dir = pendingUploadsDir()

        layer.localImage?.let { image ->
            val data = image.storageUploadJpegData(compressionQuality = 0.85f, maxPixelDimension = 2048)
            if (data != null) {
                imageFileName = "${filePrefix}_img.jpg"
                File(dir, imageFileName!!).writeBytes(data)
            }
        }

        layer.localAudioUri?.let { audioUri ->
            audioFileName = "${filePrefix}_audio.m4a"
            val dest = File(dir, audioFileName!!)
            if (dest.exists()) dest.delete()
            ctx.contentResolver.openInputStream(audioUri)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: error("Missing hidden layer audio")
        }

        return layer.toCached(
            localImageFileName = imageFileName,
            localAudioFileName = audioFileName,
        )
    }

    private fun deleteActionFiles(id: String) {
        val dir = runCatching { pendingUploadsDir() }.getOrNull() ?: return
        // iOS: file.lastPathComponent.contains(id)
        dir.listFiles()?.filter { it.name.contains(id) }?.forEach { it.delete() }
    }

    /** iOS `loadCachedImage(from:)`. */
    private fun loadCachedBitmap(file: File?): Bitmap? {
        if (file == null || !file.exists()) return null
        val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return null
        return if (bitmap.width > 0 && bitmap.height > 0) bitmap else null
    }

    // MARK: - Resume (OfflineSync / recovery) — paridad iOS: reconstruir + uploadMoment

    /**
     * Port de iOS `resumeUpload(from:)`:
     * decodifica payload, reconstruye media desde disco y vuelve a entrar por `uploadMoment`
     * (`shouldPersistAction = false`, `recoveryActionId = action.id`).
     * No sube directo a Firestore (eso era un invento Android).
     */
    suspend fun resumeUpload(action: CachedAction) {
        if (action.type != CachedAction.ActionType.MOMENT_UPLOAD.raw) return

        try {
            val payload = UploadPayloadDecoder.decodeMomentPayload(action.payloadData)
                ?: throw IllegalStateException("Invalid moment upload payload")

            // Duplicate check (iOS)
            val alreadyUploading = uploadingMoments.any { moment ->
                val active = moment.status == UploadStatus.Uploading ||
                    moment.status == UploadStatus.Processing
                active &&
                    moment.content == payload.content &&
                    moment.audienceSetting == payload.audienceSetting
            }
            if (alreadyUploading) {
                LocalPersistenceService.updateActionStatus(
                    action.id,
                    CachedAction.ActionStatus.PENDING,
                )
                return
            }

            val dir = pendingUploadsDir()
            val mediaItems = mutableListOf<CreatorMedia>()
            for (item in payload.mediaPaths) {
                val file = File(dir, item.localFileName)
                if (!file.exists()) continue
                val isVideo = item.type == "video"
                val thumbFile = item.thumbnailFileName?.let { File(dir, it) }
                val aspect = item.aspectRatio?.let { name ->
                    CreatorAspectRatio.entries.find { it.displayName == name }
                } ?: if (!isVideo) {
                    loadCachedBitmap(file)?.let { bmp ->
                        CreatorAspectRatio.fromRatio(
                            bmp.width.toFloat() / bmp.height.toFloat().coerceAtLeast(1f),
                        )
                    }
                } else {
                    null
                } ?: CreatorAspectRatio.SQUARE

                mediaItems += CreatorMedia(
                    uri = Uri.fromFile(file),
                    isVideo = isVideo,
                    durationSeconds = item.videoDuration,
                    thumbnailUri = thumbFile?.takeIf { it.exists() }?.let { Uri.fromFile(it) },
                    aspectRatio = aspect,
                    tags = item.tags.orEmpty(),
                )
            }

            if (mediaItems.isEmpty()) {
                LocalPersistenceService.updateActionStatus(
                    action.id,
                    CachedAction.ActionStatus.FAILED,
                    error = "Missing cached media for pending moment upload",
                )
                return
            }

            val hiddenLayers = (payload.hiddenLayers.orEmpty()).mapNotNull { item ->
                val localImage = item.localImageFileName?.let { name ->
                    loadCachedBitmap(File(dir, name))
                }
                val localAudioUri = item.localAudioFileName?.let { name ->
                    val f = File(dir, name)
                    if (f.exists()) Uri.fromFile(f) else null
                }
                HiddenLayerDraft(
                    id = item.id,
                    type = MomentHiddenLayer.LayerType.from(item.type),
                    anchorX = item.anchorX,
                    anchorY = item.anchorY,
                    width = item.width,
                    height = item.height,
                    shape = MomentHiddenLayer.LayerShape.from(item.shape),
                    zIndex = item.zIndex,
                    text = item.text,
                    caption = item.caption,
                    imageOffsetX = item.imageOffsetX,
                    imageOffsetY = item.imageOffsetY,
                    imageScale = item.imageScale,
                    imageFrameStyle = HiddenLayerImageFrameStyle.from(item.imageFrameStyle)
                        ?: HiddenLayerImageFrameStyle.CLASSIC,
                    localImage = localImage,
                    localAudioUri = localAudioUri,
                    duration = item.duration,
                    textStyle = HiddenLayerTextStyle.from(item.textStyle)
                        ?: HiddenLayerTextStyle.CLEAN,
                    presentationStyle = HiddenLayerPresentationStyle.from(item.presentationStyle),
                    unlockMode = MomentHiddenLayer.UnlockMode.from(item.unlockMode),
                    unlockAt = item.unlockAt,
                    authorTimezoneIdentifier = item.authorTimezoneIdentifier,
                )
            }

            withContext(Dispatchers.Main.immediate) {
                uploadMoment(
                    content = payload.content,
                    mediaItems = mediaItems,
                    taggedUsers = payload.taggedUsers,
                    mentionedUsers = payload.mentionedUsers,
                    location = payload.location,
                    locationCoordinate = payload.locationCoordinate,
                    audienceSetting = payload.audienceSetting,
                    customViewers = payload.customViewers,
                    customListId = payload.customListId,
                    aspectRatio = payload.aspectRatio,
                    disableComments = payload.disableComments,
                    hideLikeCounts = payload.hideLikeCounts,
                    allowSharing = payload.allowSharing,
                    scheduledDate = payload.scheduledDate,
                    hiddenLayers = hiddenLayers,
                    recoveryActionId = action.id,
                    shouldPersistAction = false,
                    plannedMomentId = payload.plannedMomentId,
                )
            }
        } catch (e: Exception) {
            LocalPersistenceService.updateActionStatus(
                action.id,
                CachedAction.ActionStatus.FAILED,
                error = e.message,
            )
        }
    }

    /** Port de iOS `uploadHiddenLayersIfNeeded(uploadingMoment:momentId:)`. */
    private suspend fun uploadHiddenLayersIfNeeded(
        uploadingMoment: UploadingMoment,
        momentId: String,
    ): List<MomentHiddenLayer> {
        val readyDrafts = uploadingMoment.hiddenLayers
            .filter { it.isReadyToPublish }
            .take(3)
        if (readyDrafts.isEmpty()) return emptyList()

        val userId = uploadingMoment.userId
        val uploaded = mutableListOf<MomentHiddenLayer>()
        readyDrafts.forEachIndexed { index, draft ->
            runCatching { buildHiddenLayer(draft, index, userId, momentId) }.onSuccess { uploaded += it }
        }

        if (uploaded.isEmpty()) {
            firestoreService.updateMomentHiddenLayerSummary(userId, momentId, 0)
            return emptyList()
        }
        firestoreService.saveHiddenLayers(userId, momentId, uploaded)
        return uploaded
    }

    private suspend fun buildHiddenLayer(
        draft: HiddenLayerDraft,
        index: Int,
        userId: String,
        momentId: String,
    ): MomentHiddenLayer {
        var mediaURL: String? = null
        var thumbnailURL: String? = null
        var moderationState = MomentHiddenLayer.ModerationState.VISIBLE

        when (draft.type) {
            MomentHiddenLayer.LayerType.TEXT -> Unit
            MomentHiddenLayer.LayerType.IMAGE -> {
                val image = draft.localImage ?: error("Missing hidden layer image")
                val resized = resizeHiddenLayerImage(image)
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
                val audioUri = draft.localAudioUri ?: error("Missing hidden layer audio")
                mediaURL = StorageService.uploadHiddenLayerAudio(
                    userId = userId,
                    momentId = momentId,
                    layerId = draft.id,
                    audioUri = audioUri,
                )
            }
        }

        return MomentHiddenLayer(
            id = draft.id,
            type = draft.type,
            anchorX = min(0.94, max(0.06, draft.anchorX)),
            anchorY = min(0.94, max(0.06, draft.anchorY)),
            width = min(0.55, max(0.12, draft.width)),
            height = min(0.42, max(0.10, draft.height)),
            shape = draft.shape,
            zIndex = index,
            text = if (draft.type == MomentHiddenLayer.LayerType.TEXT) {
                draft.text.trim().take(120).ifEmpty { null }
            } else {
                null
            },
            mediaURL = mediaURL,
            thumbnailURL = thumbnailURL,
            duration = draft.duration,
            caption = if (draft.type == MomentHiddenLayer.LayerType.IMAGE) {
                draft.caption.trim().take(40).ifEmpty { null }
            } else {
                null
            },
            imageOffsetX = if (draft.type == MomentHiddenLayer.LayerType.IMAGE) draft.imageOffsetX else null,
            imageOffsetY = if (draft.type == MomentHiddenLayer.LayerType.IMAGE) draft.imageOffsetY else null,
            imageScale = if (draft.type == MomentHiddenLayer.LayerType.IMAGE) draft.imageScale else null,
            imageFrameStyle = if (draft.type == MomentHiddenLayer.LayerType.IMAGE) draft.imageFrameStyle else null,
            textStyle = draft.textStyle,
            presentationStyle = draft.presentationStyle,
            unlockMode = draft.unlockMode,
            unlockAt = if (draft.unlockMode == MomentHiddenLayer.UnlockMode.SCHEDULED) draft.unlockAt else null,
            authorTimezoneIdentifier = draft.authorTimezoneIdentifier,
            moderationState = moderationState,
        )
    }

    /** iOS `resizeHiddenLayerImage` maxLongSide = 900. */
    private fun resizeHiddenLayerImage(source: Bitmap, maxLongSide: Int = 900): Bitmap {
        val longest = max(source.width, source.height)
        if (longest <= maxLongSide) return source
        val scale = maxLongSide.toFloat() / longest
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
        uploadingMoment: UploadingMoment,
    ) {
        for (item in mediaItems) {
            val action = suspendCancellableCoroutine { cont ->
                MediaModerationService.shared.moderateMedia(
                    mediaURL = item.url,
                    mediaType = item.type,
                    userId = userId,
                    contentId = momentId,
                    contentType = ModerationContentType.MOMENT,
                    mediaItemId = item.id,
                ) { cont.resume(it) }
            }
            if (action is MediaModerationAction.Deleted) {
                withContext(Dispatchers.Main.immediate) {
                    uploadingMoments.find { it.id == uploadingMoment.id }?.status = UploadStatus.Moderated
                }
            }
        }
    }

    private suspend fun moderateHiddenLayersSilently(
        momentId: String,
        userId: String,
        layers: List<MomentHiddenLayer>,
    ) {
        var hiddenLayerCount = 0
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
                is MediaModerationAction.Deleted -> {
                    hiddenLayerCount += 1
                    firestoreService.hideHiddenLayer(
                        userId = userId,
                        momentId = momentId,
                        layerId = layer.id,
                        reason = action.reason,
                        category = action.category,
                    )
                }
                else -> {
                    // iOS `.error` → mark visible
                    firestoreService.markHiddenLayerVisible(userId, momentId, layer.id)
                }
            }
        }
        if (hiddenLayerCount > 0) {
            createHiddenLayerModerationNotification(userId, momentId, hiddenLayerCount)
        }
    }

    /** Port de iOS `createHiddenLayerModerationNotification`. */
    private fun createHiddenLayerModerationNotification(
        userId: String,
        momentId: String,
        moderatedLayerCount: Int,
    ) {
        val notificationId =
            "moderation_hidden_layer_${momentId}_${System.currentTimeMillis() / 1000}"
        FirebaseFirestore.getInstance()
            .collection("users")
            .document(userId)
            .collection("notifications")
            .document(notificationId)
            .set(
                mapOf(
                    "type" to "mediaModeration",
                    "senderId" to "system_moderation",
                    "senderUsername" to "Moments",
                    "momentId" to momentId,
                    "moderationType" to "partial",
                    "moderationScope" to "postHiddenLayer",
                    "moderatedMediaCount" to moderatedLayerCount,
                    "totalMediaCount" to moderatedLayerCount,
                    "timestamp" to FieldValue.serverTimestamp(),
                    "isPending" to true,
                ),
            )
    }
}
