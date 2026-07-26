package com.moments.android.views.creator.creatoruikit

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.views.creator.CreatorAspectRatio
import com.moments.android.views.creator.CreatorMedia
import java.util.UUID

/** Tipos equivalentes a `public.image` / `public.movie` de `CameraCapture.swift`. */
enum class CameraCaptureMediaType { IMAGE, VIDEO }

/**
 * Port de `CameraCapture.swift` (`UIImagePickerController` sourceType `.camera`).
 *
 * Android no tiene un único picker foto+vídeo del sistema: se usan
 * `TakePicture` / captura de vídeo con `EXTRA_DURATION_LIMIT=60` y
 * `EXTRA_VIDEO_QUALITY=1` (≡ `videoMaximumDuration` / `videoQuality.typeHigh`).
 * Si hay un solo tipo → se lanza al instante (como iOS). Si hay ambos →
 * chooser mínimo (necesidad de plataforma; iOS cambia modo dentro del picker).
 */
@Composable
fun CameraCapture(
    mediaTypes: Set<CameraCaptureMediaType> = setOf(CameraCaptureMediaType.IMAGE, CameraCaptureMediaType.VIDEO),
    onCapture: (CreatorMedia) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var pendingImageUri by remember { mutableStateOf<Uri?>(null) }
    var pendingVideoUri by remember { mutableStateOf<Uri?>(null) }
    var didAutoLaunch by remember { mutableStateOf(false) }

    val allowImage = CameraCaptureMediaType.IMAGE in mediaTypes
    val allowVideo = CameraCaptureMediaType.VIDEO in mediaTypes
    val needsChooser = allowImage && allowVideo

    fun deliver(uri: Uri, isVideo: Boolean) {
        val ratio = readCaptureAspectRatio(context, uri, isVideo)
        onCapture(
            CreatorMedia(
                uri = uri,
                isVideo = isVideo,
                durationSeconds = if (isVideo) readVideoDurationSeconds(context, uri) else null,
                aspectRatio = ratio,
                recommendedAspectRatio = ratio,
            ),
        )
    }

    val photoCapture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = pendingImageUri
        pendingImageUri = null
        if (saved && uri != null) {
            deliver(uri, isVideo = false)
        } else {
            uri?.let { context.contentResolver.delete(it, null, null) }
        }
        // ≡ imagePickerControllerDidCancel / didFinish → siempre dismiss
        onDismiss()
    }

    val videoCapture = rememberLauncherForActivityResult(CaptureVideoHighQuality60s()) { saved ->
        val uri = pendingVideoUri
        pendingVideoUri = null
        if (saved && uri != null) {
            deliver(uri, isVideo = true)
        } else {
            uri?.let { context.contentResolver.delete(it, null, null) }
        }
        onDismiss()
    }

    fun launchPhoto() {
        val uri = createCaptureUri(context, isVideo = false) ?: run {
            onDismiss()
            return
        }
        pendingImageUri = uri
        photoCapture.launch(uri)
    }

    fun launchVideo() {
        val uri = createCaptureUri(context, isVideo = true) ?: run {
            onDismiss()
            return
        }
        pendingVideoUri = uri
        videoCapture.launch(uri)
    }

    // Un solo tipo → cámara del sistema al instante (sin chooser inventado).
    LaunchedEffect(mediaTypes) {
        if (didAutoLaunch || needsChooser) return@LaunchedEffect
        didAutoLaunch = true
        when {
            allowImage -> launchPhoto()
            allowVideo -> launchVideo()
            else -> onDismiss()
        }
    }

    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(18.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.14f))
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, contentDescription = null, tint = Color.White)
        }

        if (needsChooser) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    stringResource(R.string.creator_camera),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                CameraCaptureChoice(
                    icon = Icons.Filled.CameraAlt,
                    label = stringResource(R.string.camera_capture_photo),
                    onClick = ::launchPhoto,
                )
                CameraCaptureChoice(
                    icon = Icons.Filled.Videocam,
                    label = stringResource(R.string.camera_capture_video),
                    onClick = ::launchVideo,
                )
            }
        }
    }
}

@Composable
private fun CameraCaptureChoice(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.14f))
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
        Text(label, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * `CaptureVideo` + `videoMaximumDuration = 60` + `videoQuality = .typeHigh`.
 */
private class CaptureVideoHighQuality60s : ActivityResultContract<Uri, Boolean>() {
    override fun createIntent(context: Context, input: Uri): Intent =
        Intent(MediaStore.ACTION_VIDEO_CAPTURE)
            .putExtra(MediaStore.EXTRA_OUTPUT, input)
            .putExtra(MediaStore.EXTRA_DURATION_LIMIT, 60)
            .putExtra(MediaStore.EXTRA_VIDEO_QUALITY, 1)
            .addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)

    override fun parseResult(resultCode: Int, intent: Intent?): Boolean =
        resultCode == Activity.RESULT_OK
}

private fun createCaptureUri(context: Context, isVideo: Boolean): Uri? {
    val now = System.currentTimeMillis()
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, "moments_${now}_${UUID.randomUUID()}")
        put(MediaStore.MediaColumns.MIME_TYPE, if (isVideo) "video/mp4" else "image/jpeg")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                if (isVideo) "Movies/Moments" else "Pictures/Moments",
            )
        }
    }
    val collection = if (isVideo) {
        MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    } else {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
    }
    return context.contentResolver.insert(collection, values)
}

private fun readCaptureAspectRatio(context: Context, uri: Uri, isVideo: Boolean): CreatorAspectRatio =
    runCatching {
        if (isVideo) {
            val retriever = MediaMetadataRetriever()
            try {
                retriever.setDataSource(context, uri)
                val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                    ?.toFloatOrNull() ?: 1f
                val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                    ?.toFloatOrNull()?.coerceAtLeast(1f) ?: 1f
                CreatorAspectRatio.fromRatio(width / height)
            } finally {
                retriever.release()
            }
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val options = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeStream(input, null, options)
                CreatorAspectRatio.fromRatio(
                    options.outWidth.toFloat() / options.outHeight.coerceAtLeast(1).toFloat(),
                )
            } ?: CreatorAspectRatio.SQUARE
        }
    }.getOrDefault(CreatorAspectRatio.SQUARE)

private fun readVideoDurationSeconds(context: Context, uri: Uri): Double? =
    runCatching {
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toDoubleOrNull()
                ?.div(1_000.0)
        } finally {
            retriever.release()
        }
    }.getOrNull()
