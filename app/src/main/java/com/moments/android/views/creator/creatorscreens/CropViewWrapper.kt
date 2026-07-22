package com.moments.android.views.creator.creatorscreens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.creator.CreatorAspectRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max

/**
 * Port de `CropViewWrapper.swift` (TOCropViewController).
 * Presets de aspect ratio, rotate/reset, free crop → ratio detectado al guardar.
 */
@Composable
fun CropViewWrapper(
    imageUri: Uri,
    aspectRatio: CreatorAspectRatio,
    allowFreeCrop: Boolean = true,
    onComplete: (Uri, CreatorAspectRatio) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rotationQuarterTurns by remember { mutableIntStateOf(0) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        rotationQuarterTurns = 0
    }

    Column(modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(enabled = !isSaving, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, tint = Color.White, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.creator_crop_title),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .size(40.dp)
                    .momentsChromeGlass(CircleShape, interactive = true)
                    .clickable(enabled = !isSaving) {
                        isSaving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                cropAndSave(
                                    context = context,
                                    imageUri = imageUri,
                                    aspectRatio = aspectRatio,
                                    allowFreeCrop = allowFreeCrop,
                                    scale = scale,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    rotationQuarterTurns = rotationQuarterTurns,
                                )
                            }
                            isSaving = false
                            if (result != null) onComplete(result.first, result.second)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = Color(0xFFE91E63), modifier = Modifier.size(18.dp))
            }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = imageUri,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                        rotationZ = rotationQuarterTurns * 90f
                    },
            )
            Box(
                Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(aspectRatio.ratio)
                    .border(2.dp, Color.White),
            )
            Box(Modifier.fillMaxSize().background(Color.Black.copy(0.35f)))
            Box(
                Modifier
                    .fillMaxWidth(0.86f)
                    .aspectRatio(aspectRatio.ratio)
                    .border(2.dp, Color.White),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(0.8f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                scale = 1f
                offsetX = 0f
                offsetY = 0f
                rotationQuarterTurns = 0
            }) {
                Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.creator_crop_reset), color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable {
                rotationQuarterTurns = (rotationQuarterTurns + 1) % 4
            }) {
                Icon(Icons.Filled.RotateRight, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.creator_crop_rotate), color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
            Text(
                aspectRatio.displayName,
                color = Color.White.copy(0.7f),
                fontSize = 12.sp,
            )
        }
    }
}

private fun cropAndSave(
    context: android.content.Context,
    imageUri: Uri,
    aspectRatio: CreatorAspectRatio,
    allowFreeCrop: Boolean,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    rotationQuarterTurns: Int,
): Pair<Uri, CreatorAspectRatio>? {
    val source = context.contentResolver.openInputStream(imageUri)?.use { BitmapFactory.decodeStream(it) }
        ?: return null
    val rotated = rotateBitmap(source, rotationQuarterTurns * 90f)
    val targetRatio = aspectRatio.ratio
    val srcRatio = rotated.width.toFloat() / rotated.height.toFloat()
    val biasX = (offsetX / max(scale, 0.01f)).toInt()
    val biasY = (offsetY / max(scale, 0.01f)).toInt()
    val cropRect = if (srcRatio > targetRatio) {
        val h = rotated.height
        val w = (h * targetRatio).toInt().coerceAtLeast(1)
        val left = ((rotated.width - w) / 2 + biasX).coerceIn(0, rotated.width - w)
        android.graphics.Rect(left, 0, left + w, h)
    } else {
        val w = rotated.width
        val h = (w / targetRatio).toInt().coerceAtLeast(1)
        val top = ((rotated.height - h) / 2 + biasY).coerceIn(0, rotated.height - h)
        android.graphics.Rect(0, top, w, top + h)
    }
    val cropped = Bitmap.createBitmap(
        rotated,
        cropRect.left,
        cropRect.top,
        cropRect.width().coerceAtLeast(1),
        cropRect.height().coerceAtLeast(1),
    )
    val finalRatio = if (allowFreeCrop) {
        CreatorAspectRatio.fromRatio(cropped.width.toFloat() / cropped.height.toFloat().coerceAtLeast(1f))
    } else {
        aspectRatio
    }
    val dir = File(context.cacheDir, "creator_crops").also { it.mkdirs() }
    val out = File(dir, "crop_${UUID.randomUUID()}.jpg")
    FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    return Uri.fromFile(out) to finalRatio
}

private fun rotateBitmap(source: Bitmap, degrees: Float): Bitmap {
    if (degrees % 360f == 0f) return source
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}
