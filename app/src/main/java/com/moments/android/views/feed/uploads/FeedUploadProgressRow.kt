package com.moments.android.views.feed.uploads

import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.UploadingMoment
import kotlinx.coroutines.delay

/**
 * Port de `UploadProgressRow` en `FeedUploadProgressRow.swift`.
 * Nombre de archivo Android = FeedUploadProgressRow; struct iOS = UploadProgressRow.
 */
@Composable
fun UploadProgressRow(
    uploadingMoment: UploadingMoment,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.fromHex("FAF9F6") else Color.fromHex("0B1215")
    val cardShape = RoundedCornerShape(16.dp)
    var checkScale by remember { mutableFloatStateOf(1f) }
    val animatedCheckScale by animateFloatAsState(
        targetValue = checkScale,
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 400f),
        label = "uploadCheckScale",
    )

    val status = uploadingMoment.status
    val showProgress = status == UploadStatus.Uploading || status == UploadStatus.Processing

    Column(
        modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(10.dp, cardShape, ambientColor = Color.Black.copy(0.1f), spotColor = Color.Black.copy(0.1f))
            .momentsChromeGlass(cardShape, interactive = false)
            .border(0.5.dp, statusBorderColor(status), cardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UploadThumbnailBitmap(uploadingMoment.thumbnailBitmap)

            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = uploadingMoment.content.ifBlank {
                            stringResource(R.string.feed_uploading_new_moment)
                        },
                        color = primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    UploadStatusView(
                        status = status,
                        progress = uploadingMoment.uploadProgress,
                        checkScale = animatedCheckScale,
                        onRetry = onRetry,
                        onCancel = onCancel,
                        onCompletedAppear = {
                            HapticManager.shared.success()
                            checkScale = 1.4f
                            delay(200)
                            checkScale = 1f
                        },
                    )
                }

                if (showProgress) {
                    ProgressBarView(
                        status = status,
                        progress = uploadingMoment.uploadProgress,
                        mediaCount = uploadingMoment.mediaCount,
                        errorMessage = uploadingMoment.errorMessage,
                        primary = primary,
                    )
                }
            }
        }
    }
}

/** Compat con [UploadProgressItem] / MomentUploadTracker. */
@Composable
fun FeedUploadProgressRow(
    item: UploadProgressItem,
    modifier: Modifier = Modifier,
    onRetry: () -> Unit = {},
    onCancel: () -> Unit = {},
) {
    val isDark = isSystemInDarkTheme()
    val primary = if (isDark) Color.fromHex("FAF9F6") else Color.fromHex("0B1215")
    val cardShape = RoundedCornerShape(16.dp)
    val status = item.status
    val showProgress = status == UploadStatus.Uploading || status == UploadStatus.Processing

    Column(
        modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .shadow(10.dp, cardShape, ambientColor = Color.Black.copy(0.1f), spotColor = Color.Black.copy(0.1f))
            .momentsChromeGlass(cardShape, interactive = false)
            .border(0.5.dp, statusBorderColor(status), cardShape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            UploadThumbnailUrl(item.thumbnailUrl)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.content.ifBlank { stringResource(R.string.feed_uploading_new_moment) },
                        color = primary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    UploadStatusView(
                        status = status,
                        progress = item.progress,
                        checkScale = 1f,
                        onRetry = onRetry,
                        onCancel = onCancel,
                        onCompletedAppear = {},
                    )
                }
                if (showProgress) {
                    ProgressBarView(
                        status = status,
                        progress = item.progress,
                        mediaCount = item.fileCount,
                        errorMessage = null,
                        primary = primary,
                    )
                }
            }
        }
    }
}

@Composable
private fun UploadStatusView(
    status: UploadStatus,
    progress: Double,
    checkScale: Float,
    onRetry: () -> Unit,
    onCancel: () -> Unit,
    onCompletedAppear: suspend () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (status) {
            UploadStatus.Initializing -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color.Blue,
                    strokeWidth = 2.dp,
                )
                Text(
                    stringResource(R.string.feed_uploading_initializing),
                    color = Color.Blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            UploadStatus.Uploading -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color.Blue,
                    strokeWidth = 2.dp,
                )
                Text(
                    stringResource(R.string.feed_uploading_progress, (progress * 100).toInt()),
                    color = Color.Blue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            UploadStatus.Processing -> {
                val spin = rememberInfiniteTransition(label = "processingGear")
                val angle by spin.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        tween(1_000, easing = LinearEasing),
                        RepeatMode.Restart,
                    ),
                    label = "gearAngle",
                )
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = Color(0xFFFF9500),
                    modifier = Modifier.size(12.dp).rotate(angle),
                )
                Text(
                    stringResource(R.string.feed_uploading_processing),
                    color = Color(0xFFFF9500),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            UploadStatus.Completed, UploadStatus.Moderated -> {
                LaunchedEffect(Unit) { onCompletedAppear() }
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Color.Green,
                    modifier = Modifier.size(14.dp).scale(checkScale),
                )
                Text(
                    stringResource(R.string.feed_uploading_published),
                    color = Color.Green,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            UploadStatus.Failed -> {
                Row(
                    Modifier.clickable(onClick = onRetry),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        stringResource(R.string.feed_uploading_retry),
                        color = Color.Red,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = Color.Red.copy(0.7f),
                    modifier = Modifier
                        .size(14.dp)
                        .clickable(onClick = onCancel),
                )
            }
        }
    }
}

@Composable
private fun ProgressBarView(
    status: UploadStatus,
    progress: Double,
    mediaCount: Int,
    errorMessage: String?,
    primary: Color,
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress.toFloat().coerceIn(0f, 1f),
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "uploadProgress",
    )
    val barBrush = progressBrush(status)
    val track = primary.copy(0.1f)
    val shadowColor = when (status) {
        UploadStatus.Uploading -> Color.fromHex("007AFF").copy(0.5f)
        UploadStatus.Processing -> Color(0xFFFF9500).copy(0.5f)
        else -> Color.Transparent
    }

    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(track),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animatedProgress)
                    .shadow(4.dp, RoundedCornerShape(2.dp), ambientColor = shadowColor, spotColor = shadowColor)
                    .clip(RoundedCornerShape(2.dp))
                    .background(barBrush),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Text(
                statusDetailText(status, errorMessage),
                color = primary.copy(0.65f),
                fontSize = 10.sp,
            )
            Spacer(Modifier.weight(1f))
            if (mediaCount > 1) {
                Text(
                    stringResource(R.string.feed_uploading_files, mediaCount),
                    color = primary.copy(0.65f),
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun statusDetailText(status: UploadStatus, errorMessage: String?): String = when (status) {
    UploadStatus.Initializing -> stringResource(R.string.feed_uploading_initializing)
    UploadStatus.Uploading -> stringResource(R.string.feed_uploading_uploading)
    UploadStatus.Processing -> stringResource(R.string.feed_uploading_creating)
    UploadStatus.Completed, UploadStatus.Moderated -> stringResource(R.string.feed_uploading_available)
    UploadStatus.Failed -> errorMessage ?: stringResource(R.string.feed_uploading_error)
}

private fun progressBrush(status: UploadStatus): Brush = when (status) {
    UploadStatus.Initializing, UploadStatus.Uploading -> Brush.horizontalGradient(
        listOf(Color.fromHex("007AFF"), Color.fromHex("00D2B4")),
    )
    UploadStatus.Processing -> Brush.horizontalGradient(
        listOf(Color(0xFFFF9500), Color(0xFFFFCC00)),
    )
    else -> Brush.horizontalGradient(listOf(Color.Green, Color.Green))
}

private fun statusBorderColor(status: UploadStatus): Color = when (status) {
    UploadStatus.Initializing, UploadStatus.Uploading, UploadStatus.Processing ->
        Color.White.copy(0.15f)
    UploadStatus.Completed, UploadStatus.Moderated -> Color.Green.copy(0.3f)
    UploadStatus.Failed -> Color.Red.copy(0.3f)
}

@Composable
private fun UploadThumbnailBitmap(bitmap: Bitmap?) {
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
    } else {
        Box(
            Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color.Gray.copy(0.3f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = Color.Gray,
                modifier = Modifier.size(12.dp),
            )
        }
    }
}

@Composable
private fun UploadThumbnailUrl(url: String?) {
    if (!url.isNullOrBlank()) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
    } else {
        UploadThumbnailBitmap(null)
    }
}
