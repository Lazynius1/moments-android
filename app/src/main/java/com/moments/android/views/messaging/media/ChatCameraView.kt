package com.moments.android.views.messaging.media

import android.content.Context
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FlashAuto
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.FlipCameraAndroid
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.coordinators.AsyncProfileImageView
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorFlow
import com.moments.android.views.creator.CreatorMedia
import com.moments.android.views.creator.StoryEditingView
import com.moments.android.views.creator.components.CaptureButton
import com.moments.android.views.creator.creatoruikit.CameraPreviewView
import com.moments.android.views.creator.creatoruikit.creatorMomentsCaptureRect
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import com.moments.android.views.permissions.CameraAccessBoundary
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Port Compose de `ChatCameraView.swift`.
 * Preview con `creatorMomentsCaptureRect` — misma geometría que StoryCamera / editor / viewer.
 */
@Composable
fun ChatCameraView(
    otherUserId: String,
    otherUsername: String,
    onSend: (ByteArray, CameraPickerMediaType, ChatMediaSendMode, ChatMediaOverlayPayload?) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var frontCamera by remember { mutableStateOf(true) }
    var flashMode by remember { mutableStateOf(CameraPickerFlashMode.OFF) }
    var isRecording by remember { mutableStateOf(false) }
    var recordingSeconds by remember { mutableFloatStateOf(0f) }
    var zoomLevel by remember { mutableFloatStateOf(1f) }
    var photoToken by remember { mutableIntStateOf(0) }
    var mediaForEditor by remember { mutableStateOf<CreatorMedia?>(null) }
    var isEditorActive by remember { mutableStateOf(false) }
    var startsInTextMode by remember { mutableStateOf(false) }
    var lastGalleryThumb by remember { mutableStateOf<Uri?>(null) }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let {
            val isVideo = isVideoUri(context, it)
            val ratio = detectAspectRatio(context, it, isVideo)
            mediaForEditor = CreatorMedia(
                uri = it,
                isVideo = isVideo,
                aspectRatio = ratio,
                recommendedAspectRatio = ratio,
            )
            isEditorActive = true
        }
    }
    val zoomState = rememberTransformableState { zoomChange, _, _ ->
        zoomLevel = (zoomLevel * zoomChange).coerceIn(1f, 5f)
    }

    // ≡ iOS loadLastGalleryImage onAppear
    LaunchedEffect(Unit) {
        lastGalleryThumb = withContext(Dispatchers.IO) { latestGalleryImageUri(context) }
    }

    // ≡ recordingTimer 0.1s
    LaunchedEffect(isRecording) {
        recordingSeconds = 0f
        while (isRecording) {
            delay(100)
            recordingSeconds += 0.1f
        }
    }

    // ≡ fullScreenCover dismiss / system back
    BackHandler(onBack = onDismiss)

    if (isEditorActive) {
        // Mismo safe-area que la preview: el chat es edge-to-edge; el editor no debe meterse en status bar.
        ChatCameraEditorHost(
            media = mediaForEditor,
            otherUserId = otherUserId,
            onBack = {
                mediaForEditor = null
                startsInTextMode = false
                isEditorActive = false
            },
            onSend = onSend,
            startsInTextMode = startsInTextMode,
            onStartsInTextModeChange = { startsInTextMode = it },
            onDismiss = onDismiss,
            modifier = modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
        )
        return
    }

    val canvasBg = if (isSystemInDarkTheme()) Color(0xFF0B1215) else Color(0xFFFAF9F6)

    CameraAccessBoundary(requiresMicrophone = true, onCancel = onDismiss) {
        // Chat es edge-to-edge; Creator/StoryCamera ya vienen con status+nav padding del Dialog.
        // Sin esto el canvas (top = 8.dp) se mete en la status bar.
        BoxWithConstraints(
            modifier
                .fillMaxSize()
                .background(canvasBg)
                .windowInsetsPadding(WindowInsets.statusBars.union(WindowInsets.navigationBars)),
        ) {
            val density = LocalDensity.current
            // Insets ya consumidos por windowInsetsPadding (≡ GeometryReader en safe area iOS).
            val bottomInsetPx = 0f
            val captureRect = creatorMomentsCaptureRect(
                inSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
                topInsetPx = 0f,
                bottomInsetPx = bottomInsetPx,
                density = density,
            )
            val corner = storyViewerCanvasCornerRadius
            val shutterCenterInsetPx = with(density) { 10.dp.toPx() }
            val recordingCenterInsetPx = with(density) { 108.dp.toPx() }
            val textModeFromRightPx = with(density) { 26.dp.toPx() }
            val textModeHalfPx = with(density) { 24.dp.toPx() }
            val sideControlsExtraWidthPx = with(density) { 54.dp.toPx() }
            val sideControlsScreenMarginPx = with(density) { 72.dp.toPx() }
            val captureButtonYPx = captureRect.bottom - shutterCenterInsetPx
            val bottomControlsWidthPx = min(
                captureRect.width + sideControlsExtraWidthPx,
                constraints.maxWidth - sideControlsScreenMarginPx,
            ).coerceAtLeast(0f)
            val bottomControlsYPx = constraints.maxHeight - bottomInsetPx - with(density) { 30.dp.toPx() }

            Box(
                Modifier
                    .offset {
                        IntOffset(captureRect.left.roundToInt(), captureRect.top.roundToInt())
                    }
                    .size(
                        width = with(density) { captureRect.width.toDp() },
                        height = with(density) { captureRect.height.toDp() },
                    )
                    .clip(RoundedCornerShape(corner))
                    .background(Color.Black),
            ) {
                CameraPreviewView(
                    cameraPosition = if (frontCamera) {
                        CameraSelector.LENS_FACING_FRONT
                    } else {
                        CameraSelector.LENS_FACING_BACK
                    },
                    flashMode = flashMode.toCameraXFlashMode(),
                    isRecording = isRecording,
                    zoomLevel = zoomLevel,
                    capturePhotoToken = photoToken,
                    captureAudio = true,
                    prefersMaximumCaptureQuality = false,
                    onRecordingStateChange = { isRecording = it },
                    onImageCaptured = { uri ->
                        HapticManager.shared.lightImpact()
                        val ratio = detectAspectRatio(context, uri, isVideo = false)
                        mediaForEditor = CreatorMedia(
                            uri = uri,
                            aspectRatio = ratio,
                            recommendedAspectRatio = ratio,
                        )
                        isEditorActive = true
                    },
                    onVideoCaptured = { uri ->
                        val ratio = detectAspectRatio(context, uri, isVideo = true)
                        mediaForEditor = CreatorMedia(
                            uri = uri,
                            isVideo = true,
                            aspectRatio = ratio,
                            recommendedAspectRatio = ratio,
                        )
                        isEditorActive = true
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .transformable(zoomState)
                        .pointerInput(frontCamera) {
                            detectTapGestures(onDoubleTap = {
                                frontCamera = !frontCamera
                                zoomLevel = 1f
                            })
                        },
                )

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ChatCameraRoundButton(
                        Icons.Filled.Close,
                        stringResource(R.string.chat_camera_close),
                        onDismiss,
                    )
                    ChatCameraHeader(otherUserId, otherUsername)
                    ChatCameraRoundButton(
                        flashMode.icon(),
                        stringResource(R.string.chat_camera_flash),
                    ) { flashMode = flashMode.next() }
                }

                // ≡ recordingStatusView
                if (isRecording) {
                    Row(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = with(density) { recordingCenterInsetPx.toDp() })
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color.Red),
                        )
                        Text(
                            formatRecordingTime(recordingSeconds),
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (captureRect.right - textModeFromRightPx - textModeHalfPx).roundToInt(),
                            (captureRect.center.y - textModeHalfPx).roundToInt(),
                        )
                    }
                    .size(48.dp),
            ) {
                ChatCameraTextModeButton {
                    startsInTextMode = true
                    mediaForEditor = null
                    isEditorActive = true
                }
            }

            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (captureRect.center.x - with(density) { 44.dp.toPx() }).roundToInt(),
                            (captureButtonYPx - with(density) { 44.dp.toPx() }).roundToInt(),
                        )
                    },
            ) {
                CaptureButton(
                    isRecording = isRecording,
                    onTap = { photoToken++ },
                    onLongPressStart = { isRecording = true },
                    onLongPressEnd = { isRecording = false },
                )
            }

            Row(
                Modifier
                    .offset {
                        IntOffset(
                            ((constraints.maxWidth - bottomControlsWidthPx) / 2f).roundToInt(),
                            (bottomControlsYPx - with(density) { 24.dp.toPx() }).roundToInt(),
                        )
                    }
                    .width(with(density) { bottomControlsWidthPx.toDp() }),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ChatCameraGalleryButton(
                    thumbUri = lastGalleryThumb,
                    description = stringResource(R.string.chat_camera_gallery),
                ) {
                    gallery.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                    )
                }
                ChatCameraRoundButton(
                    Icons.Filled.FlipCameraAndroid,
                    stringResource(R.string.chat_camera_switch),
                ) {
                    frontCamera = !frontCamera
                    zoomLevel = 1f
                }
            }
        }
    }
}

@Composable
private fun ChatCameraHeader(userId: String, username: String) =
    Row(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.Black.copy(.28f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AsyncProfileImageView(userId, Modifier.size(26.dp))
        Column {
            Text(stringResource(R.string.chat_camera_header), color = Color.White.copy(.65f))
            Text(username, color = Color.White)
        }
    }

@Composable
private fun ChatCameraRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) =
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(.15f))
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = Color.White)
    }

/** ≡ iOS galleryButton con lastGalleryImage. */
@Composable
private fun ChatCameraGalleryButton(
    thumbUri: Uri?,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        if (thumbUri != null) {
            AsyncImage(
                model = thumbUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape),
            )
        } else {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Image, null, tint = Color.White)
            }
        }
    }
}

@Composable
private fun ChatCameraTextModeButton(onClick: () -> Unit) =
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(.15f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.chat_camera_text_mode), color = Color.White)
    }

@Composable
private fun ChatCameraEditorHost(
    media: CreatorMedia?,
    otherUserId: String,
    onBack: () -> Unit,
    onSend: (ByteArray, CameraPickerMediaType, ChatMediaSendMode, ChatMediaOverlayPayload?) -> Unit,
    startsInTextMode: Boolean,
    onStartsInTextModeChange: (Boolean) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier,
) {
    var mediaItems by remember(media) { mutableStateOf(listOfNotNull(media)) }
    var flow by remember { mutableStateOf(CreatorFlow.STORY_EDITING) }
    StoryEditingView(
        selectedMediaItems = mediaItems,
        onSelectedMediaItemsChange = { mediaItems = it },
        onCurrentFlowChange = { next -> if (next == CreatorFlow.STORY_CAMERA) onBack() else flow = next },
        startInTextMode = startsInTextMode,
        onStartInTextModeChange = onStartsInTextModeChange,
        onDismiss = onDismiss,
        chatRecipientUserId = otherUserId,
        onChatSend = onSend,
        modifier = modifier,
    )
}

private fun CameraPickerFlashMode.next(): CameraPickerFlashMode = when (this) {
    CameraPickerFlashMode.OFF -> CameraPickerFlashMode.ON
    CameraPickerFlashMode.ON -> CameraPickerFlashMode.AUTO
    CameraPickerFlashMode.AUTO -> CameraPickerFlashMode.OFF
}

private fun CameraPickerFlashMode.toCameraXFlashMode(): Int = when (this) {
    CameraPickerFlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    CameraPickerFlashMode.ON -> ImageCapture.FLASH_MODE_ON
    CameraPickerFlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
}

private fun CameraPickerFlashMode.icon() = when (this) {
    CameraPickerFlashMode.OFF -> Icons.Filled.FlashOff
    CameraPickerFlashMode.ON -> Icons.Filled.FlashOn
    CameraPickerFlashMode.AUTO -> Icons.Filled.FlashAuto
}

private fun isVideoUri(context: Context, uri: Uri) =
    context.contentResolver.getType(uri)?.startsWith("video/") == true

private fun formatRecordingTime(seconds: Float): String {
    val total = seconds.toInt().coerceAtLeast(0)
    return "%d:%02d".format(total / 60, total % 60)
}

private fun latestGalleryImageUri(context: Context): Uri? {
    val projection = arrayOf(MediaStore.Images.Media._ID)
    val sort = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    context.contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sort,
    )?.use { cursor ->
        if (cursor.moveToFirst()) {
            val id = cursor.getLong(0)
            return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id.toString())
        }
    }
    return null
}

/** ≡ iOS CreatorMedia.AspectRatio.fromRatio. */
private fun detectAspectRatio(context: Context, uri: Uri, isVideo: Boolean): CreatorAspectRatio {
    return runCatching {
        if (isVideo) {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                val w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toFloatOrNull() ?: 0f
                val h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toFloatOrNull() ?: 0f
                if (h > 0f) CreatorAspectRatio.fromRatio(w / h) else CreatorAspectRatio.NINE_BY_SIXTEEN
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(stream, null, opts)
                val w = opts.outWidth.toFloat()
                val h = opts.outHeight.toFloat()
                if (h > 0f) CreatorAspectRatio.fromRatio(w / h) else CreatorAspectRatio.NINE_BY_SIXTEEN
            } ?: CreatorAspectRatio.NINE_BY_SIXTEEN
        }
    }.getOrDefault(CreatorAspectRatio.NINE_BY_SIXTEEN)
}
