package com.moments.android.views.profile.core.sections

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.compose.rememberAsyncImagePainter
import coil.request.ImageRequest
import coil.size.Size as CoilSize
import com.moments.android.R
import com.moments.android.models.MomentGridPreviewSettings
import com.moments.android.utilities.HapticManager
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * Port de `ProfileGridPreviewEditorView.swift`.
 * Crop cuadrado, pan/pinch con límites, fill/fit + fondo, double-tap reset.
 */
@Composable
fun ProfileGridPreviewEditorView(
    imageUrl: String,
    initialSettings: MomentGridPreviewSettings,
    onDismiss: () -> Unit,
    onSave: (MomentGridPreviewSettings) -> Unit,
) {
    val dark = isSystemInDarkTheme()
    val canvas = if (dark) Color(0xFF0B1215) else Color(0xFFFAF9F6)
    val primary = if (dark) Color.White else Color.Black
    val secondary = if (dark) Color.White.copy(0.62f) else Color.Black.copy(0.55f)

    var fitMode by remember { mutableStateOf(initialSettings.fitMode) }
    var background by remember { mutableStateOf(initialSettings.background) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var isInteracting by remember { mutableStateOf(false) }
    var imageSizePx by remember { mutableStateOf(Size.Zero) }
    var appliedInitial by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val painter = rememberAsyncImagePainter(
        model = ImageRequest.Builder(context)
            .data(imageUrl)
            .size(CoilSize.ORIGINAL)
            .build(),
    )
    val painterState = painter.state
    val isLoading = painterState is AsyncImagePainter.State.Loading ||
        (painterState is AsyncImagePainter.State.Empty)

    LaunchedEffect(painterState) {
        val success = painterState as? AsyncImagePainter.State.Success ?: return@LaunchedEffect
        val d = success.result.drawable
        imageSizePx = Size(d.intrinsicWidth.toFloat().coerceAtLeast(1f), d.intrinsicHeight.toFloat().coerceAtLeast(1f))
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        when {
            isLoading && imageSizePx == Size.Zero -> {
                CircularProgressIndicator(Modifier.align(Alignment.Center), color = primary)
            }
            else -> {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val density = LocalDensity.current
                    val cropSideDp = previewCropSide(
                        maxWidth = maxWidth,
                        maxHeight = maxHeight,
                    )
                    val cropSidePx = with(density) { cropSideDp.toPx() }

                    var lastFit by remember { mutableStateOf(fitMode) }
                    LaunchedEffect(imageSizePx, cropSidePx) {
                        if (imageSizePx.width <= 1f || cropSidePx <= 1f || appliedInitial) return@LaunchedEffect
                        applyInitialTransform(
                            initial = initialSettings,
                            imageSize = imageSizePx,
                            cropSide = cropSidePx,
                            fitMode = fitMode,
                        ).also { (s, o) ->
                            scale = s
                            offset = o
                        }
                        appliedInitial = true
                        lastFit = fitMode
                    }
                    LaunchedEffect(fitMode) {
                        if (!appliedInitial || imageSizePx.width <= 1f || cropSidePx <= 1f) return@LaunchedEffect
                        if (fitMode == lastFit) return@LaunchedEffect
                        lastFit = fitMode
                        applyInitialTransform(
                            initial = MomentGridPreviewSettings(
                                scale = if (initialSettings.isDefault) 1.0 else initialSettings.scale,
                                offsetX = if (initialSettings.isDefault) 0.0 else initialSettings.offsetX,
                                offsetY = if (initialSettings.isDefault) 0.0 else initialSettings.offsetY,
                                fitMode = fitMode,
                                background = background,
                            ),
                            imageSize = imageSizePx,
                            cropSide = cropSidePx,
                            fitMode = fitMode,
                        ).also { (s, o) ->
                            scale = s
                            offset = o
                        }
                    }

                    Column(
                        Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        PreviewEditorHeader(
                            primary = primary,
                            onDismiss = onDismiss,
                            onSave = {
                                HapticManager.shared.mediumImpact()
                                onSave(
                                    MomentGridPreviewSettings(
                                        scale = scale.toDouble(),
                                        offsetX = (offset.x / cropSidePx).toDouble(),
                                        offsetY = (offset.y / cropSidePx).toDouble(),
                                        fitMode = fitMode,
                                        background = background,
                                    ),
                                )
                                onDismiss()
                            },
                        )

                        Spacer(Modifier.height(10.dp))

                        PreviewCropArea(
                            imageUrl = imageUrl,
                            imageSize = imageSizePx,
                            cropSide = cropSideDp,
                            cropSidePx = cropSidePx,
                            scale = scale,
                            offset = offset,
                            fitMode = fitMode,
                            background = background,
                            dark = dark,
                            isInteracting = isInteracting,
                            onScaleOffsetChange = { s, o, interacting ->
                                scale = s
                                offset = o
                                isInteracting = interacting
                            },
                            onDoubleTapReset = {
                                HapticManager.shared.mediumImpact()
                                scale = 1f
                                offset = Offset.Zero
                            },
                        )

                        Spacer(Modifier.weight(1f))

                        PreviewControls(
                            fitMode = fitMode,
                            background = background,
                            primary = primary,
                            secondary = secondary,
                            dark = dark,
                            onToggleFit = {
                                HapticManager.shared.lightImpact()
                                fitMode = if (fitMode == MomentGridPreviewSettings.FitMode.FILL) {
                                    MomentGridPreviewSettings.FitMode.FIT
                                } else {
                                    MomentGridPreviewSettings.FitMode.FILL
                                }
                            },
                            onToggleBackground = {
                                if (fitMode != MomentGridPreviewSettings.FitMode.FIT) return@PreviewControls
                                HapticManager.shared.lightImpact()
                                background = if (background == MomentGridPreviewSettings.Background.BLACK) {
                                    MomentGridPreviewSettings.Background.WHITE
                                } else {
                                    MomentGridPreviewSettings.Background.BLACK
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun previewCropSide(maxWidth: Dp, maxHeight: Dp): Dp {
    val horizontalInset = 12.dp
    val headerBlock = 48.dp
    val controlsBlock = 104.dp
    val verticalSpacing = 10.dp
    val bottomInset = 10.dp
    val widthLimit = maxWidth - horizontalInset * 2
    val heightLimit = maxHeight - headerBlock - controlsBlock - verticalSpacing - bottomInset
    val side = minOf(widthLimit, heightLimit)
    return maxOf(220.dp, side)
}

@Composable
private fun PreviewEditorHeader(
    primary: Color,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeaderCircleButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.common_cancel), tint = primary)
        }
        Spacer(Modifier.weight(1f))
        Text(
            stringResource(R.string.profile_grid_preview_title),
            color = primary,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.weight(1f))
        HeaderCircleButton(onClick = onSave) {
            Icon(Icons.Default.Check, contentDescription = stringResource(R.string.common_save), tint = primary)
        }
    }
}

@Composable
private fun HeaderCircleButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Box(
        Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color.Gray.copy(0.22f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun PreviewCropArea(
    imageUrl: String,
    imageSize: Size,
    cropSide: Dp,
    cropSidePx: Float,
    scale: Float,
    offset: Offset,
    fitMode: MomentGridPreviewSettings.FitMode,
    background: MomentGridPreviewSettings.Background,
    dark: Boolean,
    isInteracting: Boolean,
    onScaleOffsetChange: (Float, Offset, Boolean) -> Unit,
    onDoubleTapReset: () -> Unit,
) {
    val minScale = if (fitMode == MomentGridPreviewSettings.FitMode.FILL) 1f else 0.5f

    Box(
        Modifier
            .size(cropSide)
            .shadow(18.dp, RoundedCornerShape(4.dp), ambientColor = Color.Black.copy(0.18f), spotColor = Color.Black.copy(0.18f))
            .clip(RoundedCornerShape(4.dp))
            .border(1.dp, Color.White.copy(0.35f), RoundedCornerShape(4.dp))
            .clipToBounds()
            .pointerInput(imageSize, cropSidePx, fitMode, scale) {
                detectTransformGestures { _, pan, zoom, _ ->
                    val damped = zoom.toDouble().pow(0.9).toFloat()
                    val proposedScale = (scale * damped).coerceIn(minScale, 4f)
                    val ratio = proposedScale / max(scale, 0.001f)
                    val proposed = Offset(offset.x * ratio + pan.x, offset.y * ratio + pan.y)
                    val clamped = limitOffset(proposed, imageSize, proposedScale, cropSidePx, fitMode)
                    onScaleOffsetChange(proposedScale, clamped, true)
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        tryAwaitRelease()
                        onScaleOffsetChange(scale, offset, false)
                    },
                    onDoubleTap = { onDoubleTapReset() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        // Blur backdrop ≡ Image(uiImage.blur)
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .blur(18.dp),
            contentScale = ContentScale.Crop,
        )
        Box(
            Modifier
                .fillMaxSize()
                .background((if (dark) Color.Black else Color.White).copy(0.12f)),
        )
        if (fitMode == MomentGridPreviewSettings.FitMode.FIT) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        if (background == MomentGridPreviewSettings.Background.BLACK) Color.Black else Color.White,
                    ),
            )
        }
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale * (if (isInteracting) 1.005f else 1f)
                    scaleY = scale * (if (isInteracting) 1.005f else 1f)
                    translationX = offset.x
                    translationY = offset.y
                }
                .fillMaxSize(),
            contentScale = if (fitMode == MomentGridPreviewSettings.FitMode.FIT) {
                ContentScale.Fit
            } else {
                ContentScale.Crop
            },
        )
        // Mask dim around crop already clipped to square — iOS destinationOut outside window;
        // here crop IS the window, so only dim isn't needed inside. Keep subtle edge via border.

        AnimatedVisibility(
            visible = isInteracting,
            enter = fadeIn(),
            exit = fadeOut(),
        ) {
            PreviewGridOverlay(cropSide)
        }
    }
}

@Composable
private fun PreviewGridOverlay(cropSide: Dp) {
    Canvas(Modifier.size(cropSide)) {
        val third = size.width / 3f
        val stroke = Color.White.copy(0.18f)
        drawLine(stroke, Offset(third, 0f), Offset(third, size.height), 0.5.dp.toPx())
        drawLine(stroke, Offset(third * 2, 0f), Offset(third * 2, size.height), 0.5.dp.toPx())
        drawLine(stroke, Offset(0f, third), Offset(size.width, third), 0.5.dp.toPx())
        drawLine(stroke, Offset(0f, third * 2), Offset(size.width, third * 2), 0.5.dp.toPx())
    }
}

@Composable
private fun PreviewControls(
    fitMode: MomentGridPreviewSettings.FitMode,
    background: MomentGridPreviewSettings.Background,
    primary: Color,
    secondary: Color,
    dark: Boolean,
    onToggleFit: () -> Unit,
    onToggleBackground: () -> Unit,
) {
    val bgEnabled = fitMode == MomentGridPreviewSettings.FitMode.FIT
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            PreviewChip(
                emphasized = true,
                enabled = true,
                dark = dark,
                primary = primary,
                onClick = onToggleFit,
                modifier = Modifier.weight(1f),
            ) {
                GridPreviewModeChipIcon(fitMode = fitMode, tint = primary)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(
                        if (fitMode == MomentGridPreviewSettings.FitMode.FILL) {
                            R.string.profile_grid_preview_mode_fill
                        } else {
                            R.string.profile_grid_preview_mode_fit
                        },
                    ),
                    color = primary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            PreviewChip(
                emphasized = false,
                enabled = bgEnabled,
                dark = dark,
                primary = primary,
                onClick = onToggleBackground,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(
                            if (background == MomentGridPreviewSettings.Background.WHITE) Color.White else Color.Black,
                        )
                        .border(
                            1.dp,
                            Color.White.copy(
                                if (background == MomentGridPreviewSettings.Background.WHITE) 0.28f else 0.18f,
                            ),
                            CircleShape,
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.profile_grid_preview_background),
                    color = primary.copy(if (bgEnabled) 0.88f else 0.24f),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        Text(
            stringResource(R.string.profile_grid_preview_hint),
            Modifier
                .fillMaxWidth()
                .height(36.dp)
                .graphicsLayer { alpha = if (fitMode == MomentGridPreviewSettings.FitMode.FILL) 1f else 0f },
            color = secondary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun PreviewChip(
    emphasized: Boolean,
    enabled: Boolean,
    dark: Boolean,
    primary: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val bgAlpha = when {
        !enabled -> if (dark) 0.04f else 0.02f
        emphasized -> if (dark) 0.14f else 0.08f
        else -> if (dark) 0.11f else 0.06f
    }
    Row(
        modifier
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(primary.copy(bgAlpha))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        content()
    }
}

@Composable
private fun GridPreviewModeChipIcon(
    fitMode: MomentGridPreviewSettings.FitMode,
    tint: Color,
) {
    Canvas(Modifier.size(18.dp)) {
        val leg = 5.5.dp.toPx()
        val stroke = Stroke(width = 1.8.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
        val corners = when (fitMode) {
            MomentGridPreviewSettings.FitMode.FILL -> listOf(
                Alignment.TopEnd to Offset(size.width, 0f),
                Alignment.BottomStart to Offset(0f, size.height),
            )
            MomentGridPreviewSettings.FitMode.FIT -> listOf(
                Alignment.TopStart to Offset(0f, 0f),
                Alignment.TopEnd to Offset(size.width, 0f),
                Alignment.BottomStart to Offset(0f, size.height),
                Alignment.BottomEnd to Offset(size.width, size.height),
            )
        }
        corners.forEach { (align, origin) ->
            val path = Path()
            when (align) {
                Alignment.TopStart -> {
                    path.moveTo(origin.x, origin.y + leg)
                    path.lineTo(origin.x, origin.y)
                    path.lineTo(origin.x + leg, origin.y)
                }
                Alignment.TopEnd -> {
                    path.moveTo(origin.x - leg, origin.y)
                    path.lineTo(origin.x, origin.y)
                    path.lineTo(origin.x, origin.y + leg)
                }
                Alignment.BottomStart -> {
                    path.moveTo(origin.x, origin.y - leg)
                    path.lineTo(origin.x, origin.y)
                    path.lineTo(origin.x + leg, origin.y)
                }
                else -> {
                    path.moveTo(origin.x - leg, origin.y)
                    path.lineTo(origin.x, origin.y)
                    path.lineTo(origin.x, origin.y - leg)
                }
            }
            drawPath(path, tint, style = stroke)
        }
    }
}

private fun displaySize(imageSize: Size, cropSide: Float, fitMode: MomentGridPreviewSettings.FitMode): Size {
    if (imageSize.width <= 0f || imageSize.height <= 0f) return Size(cropSide, cropSide)
    val widthScale = cropSide / imageSize.width
    val heightScale = cropSide / imageSize.height
    val applied = if (fitMode == MomentGridPreviewSettings.FitMode.FILL) {
        max(widthScale, heightScale)
    } else {
        min(widthScale, heightScale)
    }
    return Size(imageSize.width * applied, imageSize.height * applied)
}

private fun limitOffset(
    proposed: Offset,
    imageSize: Size,
    scale: Float,
    cropSide: Float,
    fitMode: MomentGridPreviewSettings.FitMode,
): Offset {
    val base = displaySize(imageSize, cropSide, fitMode)
    val scaledW = base.width * scale
    val scaledH = base.height * scale
    val maxX = max(0f, (scaledW - cropSide) / 2f)
    val maxY = max(0f, (scaledH - cropSide) / 2f)
    return Offset(
        proposed.x.coerceIn(-maxX, maxX),
        proposed.y.coerceIn(-maxY, maxY),
    )
}

private fun applyInitialTransform(
    initial: MomentGridPreviewSettings,
    imageSize: Size,
    cropSide: Float,
    fitMode: MomentGridPreviewSettings.FitMode,
): Pair<Float, Offset> {
    val minScale = if (fitMode == MomentGridPreviewSettings.FitMode.FILL) 1f else 0.5f
    if (initial.isDefault) return 1f to Offset.Zero
    val s = max(minScale, initial.scale.toFloat())
    val o = limitOffset(
        Offset(initial.offsetX.toFloat() * cropSide, initial.offsetY.toFloat() * cropSide),
        imageSize,
        s,
        cropSide,
        fitMode,
    )
    return s to o
}
