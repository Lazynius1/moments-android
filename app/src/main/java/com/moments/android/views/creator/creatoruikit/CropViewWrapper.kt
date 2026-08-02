package com.moments.android.views.creator.creatoruikit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.views.creator.CreatorAspectRatio
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.max
import androidx.compose.foundation.isSystemInDarkTheme
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Port de `CropViewWrapper.swift` (TOCropViewController).
 *
 * - Default `allowFreeCrop=false` (como iOS); MediaEditing pasa `true`
 * - Free → ciclar preset al tocar el label (≡ lock off + ratio picker)
 * - Rotate/Reset; toolbar negro 0.8; crop mapea guía → bitmap (Fit + pan/zoom)
 */
@Composable
fun CropViewWrapper(
    imageUri: Uri,
    aspectRatio: CreatorAspectRatio,
    allowFreeCrop: Boolean = false,
    onComplete: (Uri, CreatorAspectRatio) -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    var workingBitmap by remember(imageUri) { mutableStateOf<Bitmap?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var guideAspect by remember(aspectRatio) { mutableFloatStateOf(aspectRatio.ratio) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(imageUri) {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        guideAspect = aspectRatio.ratio
        workingBitmap = withContext(Dispatchers.IO) { loadNormalizedBitmap(context, imageUri) }
    }

    fun resetTransforms() {
        scale = 1f
        offsetX = 0f
        offsetY = 0f
        guideAspect = aspectRatio.ratio
    }

    Column(modifier.fillMaxSize().background(Color.Black)) {
        val chromeFg = MomentsChromeGlass.contentColor(isSystemInDarkTheme())
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
                Icon(Icons.Filled.Close, null, tint = chromeFg, modifier = Modifier.size(18.dp))
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
                    .clickable(enabled = !isSaving && workingBitmap != null) {
                        val bmp = workingBitmap ?: return@clickable
                        val size = containerSize
                        isSaving = true
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                cropAndSave(
                                    context = context,
                                    bitmap = bmp,
                                    containerWidthPx = size.width,
                                    containerHeightPx = size.height,
                                    guideAspect = guideAspect,
                                    scale = scale,
                                    offsetX = offsetX,
                                    offsetY = offsetY,
                                    lockedAspect = if (allowFreeCrop) null else aspectRatio,
                                )
                            }
                            isSaving = false
                            if (result != null) onComplete(result.first, result.second)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, null, tint = chromeFg, modifier = Modifier.size(18.dp))
            }
        }

        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .onSizeChanged { size: IntSize ->
                    containerSize = Size(size.width.toFloat(), size.height.toFloat())
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 5f)
                        offsetX += pan.x
                        offsetY += pan.y
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val containerW = constraints.maxWidth.toFloat()
            val containerH = constraints.maxHeight.toFloat()
            val guide = cropGuideRect(containerW, containerH, guideAspect)

            workingBitmap?.let { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX
                            translationY = offsetY
                        },
                )
            }

            Canvas(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                drawRect(Color.Black.copy(alpha = 0.35f))
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(guide.left, guide.top),
                    size = Size(guide.width, guide.height),
                    blendMode = BlendMode.Clear,
                )
            }

            Box(
                Modifier
                    .width(with(density) { guide.width.toDp() })
                    .height(with(density) { guide.height.toDp() })
                    .border(2.dp, Color.White),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.8f))
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { resetTransforms() },
            ) {
                Icon(Icons.Filled.Refresh, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.creator_crop_reset), color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {
                    workingBitmap = workingBitmap?.let { rotateBitmap90(it) }
                    scale = 1f
                    offsetX = 0f
                    offsetY = 0f
                },
            ) {
                Icon(Icons.Filled.RotateRight, null, tint = Color.White, modifier = Modifier.size(22.dp))
                Text(stringResource(R.string.creator_crop_rotate), color = Color.White.copy(0.7f), fontSize = 11.sp)
            }
            Text(
                text = if (allowFreeCrop) {
                    CreatorAspectRatio.fromRatio(guideAspect).displayName
                } else {
                    aspectRatio.displayName
                },
                color = Color.White.copy(0.7f),
                fontSize = 12.sp,
                modifier = if (allowFreeCrop) {
                    Modifier.clickable {
                        val order = CreatorAspectRatio.entries
                        val current = CreatorAspectRatio.fromRatio(guideAspect)
                        val next = order[(order.indexOf(current) + 1) % order.size]
                        guideAspect = next.ratio
                    }
                } else {
                    Modifier
                },
            )
        }
    }
}

private fun cropGuideRect(containerW: Float, containerH: Float, guideAspect: Float): Rect {
    val aspect = guideAspect.coerceAtLeast(0.01f)
    var guideWidth = containerW * 0.86f
    var guideHeight = guideWidth / aspect
    if (guideHeight > containerH * 0.86f) {
        guideHeight = containerH * 0.86f
        guideWidth = guideHeight * aspect
    }
    val left = (containerW - guideWidth) / 2f
    val top = (containerH - guideHeight) / 2f
    return Rect(left, top, left + guideWidth, top + guideHeight)
}

private fun loadNormalizedBitmap(context: Context, uri: Uri): Bitmap? {
    val raw = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) }
        ?: return null
    return raw.creatorNormalizedUp(context, uri)
}

private fun rotateBitmap90(source: Bitmap): Bitmap {
    val matrix = android.graphics.Matrix().apply { postRotate(90f) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

private fun cropAndSave(
    context: Context,
    bitmap: Bitmap,
    containerWidthPx: Float,
    containerHeightPx: Float,
    guideAspect: Float,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    lockedAspect: CreatorAspectRatio?,
): Pair<Uri, CreatorAspectRatio>? {
    if (containerWidthPx <= 0f || containerHeightPx <= 0f) return null

    val guide = cropGuideRect(containerWidthPx, containerHeightPx, guideAspect)
    val imgW = bitmap.width.toFloat()
    val imgH = bitmap.height.toFloat()
    val fitScale = min(containerWidthPx / imgW, containerHeightPx / imgH)
    val drawnW = imgW * fitScale
    val drawnH = imgH * fitScale
    val drawnLeft = (containerWidthPx - drawnW) / 2f
    val drawnTop = (containerHeightPx - drawnH) / 2f
    val s = max(scale, 0.01f)

    fun mapToBitmap(px: Float, py: Float): Pair<Float, Float> {
        val localX = (px - containerWidthPx / 2f - offsetX) / s + containerWidthPx / 2f
        val localY = (py - containerHeightPx / 2f - offsetY) / s + containerHeightPx / 2f
        return (localX - drawnLeft) / fitScale to (localY - drawnTop) / fitScale
    }

    val (x0, y0) = mapToBitmap(guide.left, guide.top)
    val (x1, y1) = mapToBitmap(guide.right, guide.bottom)

    var left = min(x0, x1).roundToInt().coerceIn(0, bitmap.width - 1)
    var top = min(y0, y1).roundToInt().coerceIn(0, bitmap.height - 1)
    var right = max(x0, x1).roundToInt().coerceIn(left + 1, bitmap.width)
    var bottom = max(y0, y1).roundToInt().coerceIn(top + 1, bitmap.height)

    lockedAspect?.let { locked ->
        val target = locked.ratio
        val selW = (right - left).toFloat()
        val selH = (bottom - top).toFloat().coerceAtLeast(1f)
        val selRatio = selW / selH
        if (selRatio > target) {
            val newW = (selH * target).roundToInt().coerceAtLeast(1)
            val cx = (left + right) / 2
            left = (cx - newW / 2).coerceIn(0, bitmap.width - newW)
            right = left + newW
        } else {
            val newH = (selW / target).roundToInt().coerceAtLeast(1)
            val cy = (top + bottom) / 2
            top = (cy - newH / 2).coerceIn(0, bitmap.height - newH)
            bottom = top + newH
        }
    }

    val cropped = Bitmap.createBitmap(
        bitmap,
        left,
        top,
        (right - left).coerceAtLeast(1),
        (bottom - top).coerceAtLeast(1),
    )
    val finalRatio = lockedAspect
        ?: CreatorAspectRatio.fromRatio(
            cropped.width.toFloat() / cropped.height.toFloat().coerceAtLeast(1f),
        )

    val dir = File(context.cacheDir, "creator_crops").also { it.mkdirs() }
    val out = File(dir, "crop_${UUID.randomUUID()}.jpg")
    FileOutputStream(out).use { cropped.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    return Uri.fromFile(out) to finalRatio
}
