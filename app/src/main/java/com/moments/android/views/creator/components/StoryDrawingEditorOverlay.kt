package com.moments.android.views.creator.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath


/**
 * Port de `StoryDrawingEditorOverlay.swift`.
 * PencilKit → Compose Canvas (glow/arrow/marker/eraser 1:1 en parámetros).
 */
enum class StoryDrawingBrush {
    PEN, ARROW, GLOW, MARKER, PENCIL, ERASER,
}

/** ≡ `GlowConfig` del canvas PencilKit (bake/live usan `brushWidth`; core×0.3 solo ink PK invisible). */
object StoryDrawingGlowConfig {
    const val coreWidthMultiplier = 0.3f
    const val shadowRadiusMultiplier = 0.35f
    const val shadowRadiusMinimum = 2f

    fun coreWidth(brushWidth: Float): Float = maxOf(2f, brushWidth * coreWidthMultiplier)
    fun shadowRadius(brushWidth: Float): Float =
        maxOf(shadowRadiusMinimum, brushWidth * shadowRadiusMultiplier)
}

data class StoryDrawingStroke(
    val brush: StoryDrawingBrush,
    val color: Color,
    val widthPx: Float,
    val points: List<Offset>,
    /** Ancho del slider (para blur glow ≡ iOS `brushWidth`). */
    val brushSizePx: Float = widthPx,
)

/** ≡ `drawingPalette` (sin Light/Dark Moments, que van aparte). */
object StoryDrawingPalette {
    val swatches: List<String> = listOf(
        "FFFFFF", "000000",
        "FF3B30", "FF9500", "FFCC00", "34C759", "007AFF", "5856D6",
        "AF52DE", "FF2D55", "A2845E", "F2C94C", "00C7BE", "8E8E93",
        "FFD60A", "BF5AF2", "64D2FF", "FF6B6B", "C4B5A5", "1C1C1E",
    )
}

@Composable
fun StoryDrawingEditorOverlay(
    baseDrawing: Bitmap?,
    onCancel: () -> Unit,
    onDone: (Bitmap?) -> Unit,
    modifier: Modifier = Modifier,
    /** ≡ iOS `canvasRect` — acota el área de dibujo al captureRect compartido. */
    canvasRect: Rect? = null,
    onOpenColorPicker: (() -> Unit)? = null,
) {
    val isDark = isSystemInDarkTheme()
    val controlFg = StoryEditorChromeColor.icon(isDark)
    val secondary = controlFg.copy(alpha = if (isDark) 0.58f else 0.62f)
    val dividerColor = controlFg.copy(alpha = if (isDark) 0.16f else 0.12f)
    val controlStroke = controlFg.copy(alpha = if (isDark) 0.12f else 0.10f)

    var brush by remember { mutableStateOf(StoryDrawingBrush.PEN) }
    var colorHex by remember { mutableStateOf("FFFFFF") }
    var brushWidthDp by remember { mutableFloatStateOf(7f) }
    var strokes by remember { mutableStateOf<List<StoryDrawingStroke>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<StoryDrawingStroke>>(emptyList()) }
    var activeStroke by remember { mutableStateOf<StoryDrawingStroke?>(null) }
    var isColorPickerOpen by remember { mutableStateOf(false) }

    val density = LocalDensity.current
    val brushWidthPx = with(density) { brushWidthDp.dp.toPx() }
    val strokeColor = parseStoryColorHex(colorHex)
    val paletteColors = remember {
        StoryDrawingPalette.swatches.map(::parseStoryColorHex)
    }

    fun commitStroke(stroke: StoryDrawingStroke) {
        if (stroke.points.size < 2) return
        strokes = strokes + stroke
        redoStack = emptyList()
        activeStroke = null
    }

    fun undo() {
        if (strokes.isEmpty()) return
        val last = strokes.last()
        strokes = strokes.dropLast(1)
        redoStack = redoStack + last
    }

    fun redo() {
        if (redoStack.isEmpty()) return
        val next = redoStack.last()
        redoStack = redoStack.dropLast(1)
        strokes = strokes + next
    }

    Box(modifier.fillMaxSize()) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val fullW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val fullH = constraints.maxHeight.toFloat().coerceAtLeast(1f)
            val drawRect = canvasRect ?: Rect(0f, 0f, fullW, fullH)
            val canvasW = drawRect.width.coerceAtLeast(1f)
            val canvasH = drawRect.height.coerceAtLeast(1f)
            // ≡ StoryTextEditor: centrar chrome (92dp) en el hueco bajo el canvas
            val chromeHeightPx = with(density) { 92.dp.toPx() }
            val canvasBottomGapPx = (fullH - drawRect.bottom).coerceAtLeast(0f)
            val bottomChromePadding = with(density) {
                maxOf(
                    8.dp,
                    ((canvasBottomGapPx - chromeHeightPx) / 2f).coerceAtLeast(0f).toDp(),
                )
            }

            // ≡ iOS: base + strokes clipped to captureRect
            Box(
                Modifier
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            drawRect.left.roundToInt(),
                            drawRect.top.roundToInt(),
                        )
                    }
                    .size(
                        width = with(density) { canvasW.toDp() },
                        height = with(density) { canvasH.toDp() },
                    )
                    .clip(RoundedCornerShape(storyViewerCanvasCornerRadius)),
            ) {
                if (baseDrawing != null) {
                    Image(
                        bitmap = baseDrawing.asImageBitmap(),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .pointerInput(brush, colorHex, brushWidthPx) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    val width = when (brush) {
                                        StoryDrawingBrush.MARKER -> maxOf(10f, brushWidthPx * 2.4f)
                                        StoryDrawingBrush.GLOW -> brushWidthPx
                                        StoryDrawingBrush.ARROW -> maxOf(3f, brushWidthPx)
                                        else -> brushWidthPx
                                    }
                                    activeStroke = StoryDrawingStroke(
                                        brush = brush,
                                        color = strokeColor,
                                        widthPx = width,
                                        points = listOf(offset),
                                        brushSizePx = brushWidthPx,
                                    )
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val current = activeStroke ?: return@detectDragGestures
                                    activeStroke = current.copy(points = current.points + change.position)
                                },
                                onDragEnd = {
                                    val finished = activeStroke
                                    activeStroke = null
                                    if (finished != null) commitStroke(finished)
                                },
                                onDragCancel = { activeStroke = null },
                            )
                        },
                ) {
                    val drawList = strokes + listOfNotNull(activeStroke)
                    drawList.forEach { stroke -> drawStoryStroke(stroke) }
                }
            }

            // Left size slider
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp, bottom = 100.dp),
            ) {
                StoryVerticalBrushSlider(
                    value = brushWidthDp,
                    onValueChange = { brushWidthDp = it },
                    range = 2f..26f,
                    modifier = Modifier
                        .width(44.dp)
                        .height(220.dp),
                )
            }

            // Top chrome — mismo layout que StoryTextEditor (statusBarsPadding + 16)
            Row(
                Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp)
                    .align(Alignment.TopCenter),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DrawingChromeButton(Icons.Filled.Close, controlFg, controlStroke, onCancel)
                Spacer(Modifier.width(10.dp))
                DrawingChromeButton(Icons.AutoMirrored.Filled.Undo, controlFg, controlStroke, ::undo)
                Spacer(Modifier.width(10.dp))
                DrawingChromeButton(Icons.AutoMirrored.Filled.Redo, controlFg, controlStroke, ::redo)
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.common_done),
                    color = controlFg,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    modifier = Modifier
                        .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
                        .clickable {
                            val exported = rasterizeDrawing(
                                base = baseDrawing,
                                strokes = strokes,
                                viewWidth = canvasW,
                                viewHeight = canvasH,
                            )
                            onDone(exported)
                        }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }

            // Bottom: palette (40) + toolbar (44), spacing 8
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = bottomChromePadding),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // ≡ customColorPicker (ColorPicker nativo iOS → panel HSB)
                    Box(
                        Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.sweepGradient(
                                    listOf(
                                        Color.Red, Color.Yellow, Color.Green,
                                        Color.Cyan, Color.Blue, Color.Magenta, Color.Red,
                                    ),
                                ),
                            )
                            .border(1.dp, controlStroke, CircleShape)
                            .clickable {
                                if (onOpenColorPicker != null) {
                                    onOpenColorPicker()
                                } else {
                                    isColorPickerOpen = !isColorPickerOpen
                                }
                            },
                    )
                    Box(Modifier.width(1.dp).height(20.dp).background(dividerColor))
                    DrawingColorSwatch(
                        hex = "FAF9F6",
                        selected = colorHex.equals("FAF9F6", ignoreCase = true),
                        onSelect = {
                            colorHex = it
                            isColorPickerOpen = false
                        },
                    )
                    DrawingColorSwatch(
                        hex = "0B1215",
                        selected = colorHex.equals("0B1215", ignoreCase = true),
                        onSelect = {
                            colorHex = it
                            isColorPickerOpen = false
                        },
                    )
                    Box(Modifier.width(1.dp).height(20.dp).background(dividerColor))
                    StoryDrawingPalette.swatches.forEach { hex ->
                        DrawingColorSwatch(
                            hex = hex,
                            selected = hex.equals(colorHex, ignoreCase = true),
                            onSelect = {
                                colorHex = it
                                isColorPickerOpen = false
                            },
                        )
                    }
                }

                Row(
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .height(44.dp)
                        .momentsChromeGlass(RoundedCornerShape(14.dp), interactive = false)
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(14.dp),
                            ambientColor = Color.Black.copy(alpha = if (isDark) 0.20f else 0.10f),
                            spotColor = Color.Black.copy(alpha = if (isDark) 0.20f else 0.10f),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrushTool(Icons.Filled.Brush, StoryDrawingBrush.PEN, brush, controlFg, secondary, strokeColor) {
                        brush = it
                    }
                    BrushDivider(dividerColor)
                    BrushTool(Icons.Filled.NorthEast, StoryDrawingBrush.ARROW, brush, controlFg, secondary, strokeColor) {
                        brush = it
                    }
                    BrushDivider(dividerColor)
                    BrushTool(Icons.Filled.Highlight, StoryDrawingBrush.MARKER, brush, controlFg, secondary, strokeColor) {
                        brush = it
                    }
                    BrushDivider(dividerColor)
                    BrushTool(Icons.Filled.Edit, StoryDrawingBrush.PENCIL, brush, controlFg, secondary, strokeColor) {
                        brush = it
                    }
                    BrushDivider(dividerColor)
                    BrushTool(Icons.Filled.AutoAwesome, StoryDrawingBrush.GLOW, brush, controlFg, secondary, strokeColor) {
                        brush = it
                    }
                    BrushDivider(dividerColor)
                    BrushTool(Icons.Filled.AutoFixHigh, StoryDrawingBrush.ERASER, brush, controlFg, secondary, strokeColor) {
                        brush = it
                    }
                }
            }

            if (isColorPickerOpen && onOpenColorPicker == null) {
                StoryColorPickerPanel(
                    selectedColor = strokeColor,
                    onSelectedColorChange = { colorHex = it.toStoryHex() },
                    swatchColors = paletteColors,
                    suggestedColors = listOf(
                        parseStoryColorHex("FAF9F6"),
                        parseStoryColorHex("0B1215"),
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 18.dp)
                        .padding(bottom = bottomChromePadding + 100.dp),
                )
            }
        }
    }
}

@Composable
private fun DrawingColorSwatch(
    hex: String,
    selected: Boolean,
    onSelect: (String) -> Unit,
) {
    val swatch = parseStoryColorHex(hex)
    val light = isPerceptuallyLight(swatch)
    Box(
        Modifier
            .size(24.dp)
            .shadow(
                elevation = 2.dp,
                shape = CircleShape,
                ambientColor = Color.Black.copy(alpha = if (light) 0.16f else 0.10f),
                spotColor = Color.Black.copy(alpha = if (light) 0.16f else 0.10f),
            )
            .clip(CircleShape)
            .background(swatch)
            .border(
                width = if (selected) 2.5.dp else 1.dp,
                color = when {
                    selected && light -> Color.Black.copy(alpha = 0.9f)
                    selected -> Color.White
                    light -> Color.Black.copy(alpha = 0.5f)
                    else -> Color.White.copy(alpha = 0.92f)
                },
                shape = CircleShape,
            )
            .clickable {
                onSelect(hex)
                HapticManager.shared.lightImpact()
            },
    )
}

@Composable
private fun DrawingChromeButton(
    icon: ImageVector,
    tint: Color,
    stroke: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .momentsChromeGlass(CircleShape, interactive = true)
            .border(1.dp, stroke, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RowScope.BrushTool(
    icon: ImageVector,
    type: StoryDrawingBrush,
    selected: StoryDrawingBrush,
    active: Color,
    inactive: Color,
    selectedColor: Color,
    onSelect: (StoryDrawingBrush) -> Unit,
) {
    val isSelected = selected == type
    Box(
        Modifier
            .weight(1f)
            .fillMaxHeight()
            .clickable { onSelect(type) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            null,
            tint = if (isSelected) active else inactive,
            modifier = Modifier
                .size(18.dp)
                .then(
                    if (isSelected && type == StoryDrawingBrush.GLOW) {
                        Modifier.shadow(
                            elevation = 6.dp,
                            shape = CircleShape,
                            ambientColor = selectedColor.copy(alpha = 0.8f),
                            spotColor = selectedColor.copy(alpha = 0.8f),
                        )
                    } else {
                        Modifier
                    },
                ),
        )
    }
}

@Composable
private fun BrushDivider(color: Color) {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(color),
    )
}

/** ≡ `TaperedSliderTrack` (StoryTextEditor.swift). */
private fun taperedSliderTrackPath(width: Float, height: Float): Path {
    val topWidth = 12f
    val bottomWidth = 2.5f
    val midX = width / 2f
    val topCenterY = topWidth / 2f
    val bottomCenterY = height - bottomWidth / 2f
    return Path().apply {
        // Top semicircle
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                midX - topWidth / 2f,
                0f,
                midX + topWidth / 2f,
                topWidth,
            ),
            startAngleDegrees = 180f,
            sweepAngleDegrees = 180f,
            forceMoveTo = true,
        )
        lineTo(midX + bottomWidth / 2f, bottomCenterY)
        arcTo(
            rect = androidx.compose.ui.geometry.Rect(
                midX - bottomWidth / 2f,
                height - bottomWidth,
                midX + bottomWidth / 2f,
                height,
            ),
            startAngleDegrees = 0f,
            sweepAngleDegrees = 180f,
            forceMoveTo = false,
        )
        lineTo(midX - topWidth / 2f, topCenterY)
        close()
    }
}

@Composable
private fun StoryVerticalBrushSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    range: ClosedRange<Float>,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val trackH = h - with(density) { 32.dp.toPx() }
        val progress = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val knobY = with(density) { 16.dp.toPx() } + (1f - progress) * trackH

        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(range) {
                    detectDragGestures(
                        onDragStart = { dragging = true },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                        onDrag = { change, _ ->
                            change.consume()
                            val top = 16.dp.toPx()
                            val bottom = size.height - 16.dp.toPx()
                            val y = change.position.y.coerceIn(top, bottom)
                            val inv = 1f - ((y - top) / (bottom - top).coerceAtLeast(1f))
                            onValueChange(range.start + inv * (range.endInclusive - range.start))
                        },
                    )
                },
        ) {
            Canvas(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .width(16.dp)
                    .height(with(density) { trackH.toDp() }),
            ) {
                drawPath(
                    path = taperedSliderTrackPath(size.width, size.height),
                    color = Color.White.copy(alpha = 0.32f),
                )
            }
            Box(
                Modifier
                    .offset(y = with(density) { (knobY - 14.dp.toPx()).toDp() })
                    .align(Alignment.TopCenter)
                    .size(if (dragging) 31.dp else 28.dp)
                    .shadow(
                        elevation = if (dragging) 5.dp else 3.dp,
                        shape = CircleShape,
                        ambientColor = Color.Black.copy(alpha = if (dragging) 0.35f else 0.22f),
                        spotColor = Color.Black.copy(alpha = if (dragging) 0.35f else 0.22f),
                    )
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawStoryStroke(stroke: StoryDrawingStroke) {
    if (stroke.points.size < 2) return
    val path = smoothPath(stroke.points)
    when (stroke.brush) {
        StoryDrawingBrush.ERASER -> {
            drawIntoCanvas { canvas ->
                val paint = AndroidPaint().apply {
                    isAntiAlias = true
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND
                    strokeJoin = AndroidPaint.Join.ROUND
                    strokeWidth = stroke.widthPx
                    xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
                }
                canvas.nativeCanvas.drawPath(path.asAndroidPath(), paint)
            }
        }
        StoryDrawingBrush.GLOW -> {
            // ≡ CAShapeLayer bake: white stroke + colored shadow (opacity 1), lineWidth = brushWidth
            drawIntoCanvas { canvas ->
                val blur = StoryDrawingGlowConfig.shadowRadius(stroke.brushSizePx)
                val glow = AndroidPaint().apply {
                    isAntiAlias = true
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND
                    strokeJoin = AndroidPaint.Join.ROUND
                    strokeWidth = stroke.widthPx
                    color = stroke.color.copy(alpha = 1f).toArgb()
                    maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
                }
                val core = AndroidPaint().apply {
                    isAntiAlias = true
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND
                    strokeJoin = AndroidPaint.Join.ROUND
                    strokeWidth = stroke.widthPx
                    color = Color.White.toArgb()
                }
                val androidPath = path.asAndroidPath()
                canvas.nativeCanvas.drawPath(androidPath, glow)
                canvas.nativeCanvas.drawPath(androidPath, core)
            }
        }
        StoryDrawingBrush.MARKER -> {
            drawPath(
                path = path,
                color = stroke.color.copy(alpha = 0.40f),
                style = Stroke(width = stroke.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        StoryDrawingBrush.PENCIL -> {
            drawPath(
                path = path,
                color = stroke.color.copy(alpha = 0.78f),
                style = Stroke(width = stroke.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        StoryDrawingBrush.PEN, StoryDrawingBrush.ARROW -> {
            drawPath(
                path = path,
                color = stroke.color,
                style = Stroke(width = stroke.widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            if (stroke.brush == StoryDrawingBrush.ARROW) {
                drawArrowHead(stroke.points, stroke.color, stroke.widthPx)
            }
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawArrowHead(
    points: List<Offset>,
    color: Color,
    width: Float,
) {
    if (points.size < 2) return
    val tip = points.last()
    val prev = points[points.lastIndex - 1]
    val angle = atan2(tip.y - prev.y, tip.x - prev.x)
    val headLength = maxOf(12f, width * 3.2f)
    val spread = (PI / 7).toFloat()
    val left = Offset(
        tip.x - headLength * cos(angle - spread),
        tip.y - headLength * sin(angle - spread),
    )
    val right = Offset(
        tip.x - headLength * cos(angle + spread),
        tip.y - headLength * sin(angle + spread),
    )
    val strokeW = maxOf(3f, width * 0.9f)
    drawPath(
        path = Path().apply {
            moveTo(left.x, left.y)
            lineTo(tip.x, tip.y)
            lineTo(right.x, right.y)
        },
        color = color,
        style = Stroke(width = strokeW, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}

private fun smoothPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    if (points.size == 1) {
        path.lineTo(points.first().x + 0.1f, points.first().y)
        return path
    }
    for (i in 1 until points.size) {
        val mid = Offset(
            (points[i - 1].x + points[i].x) / 2f,
            (points[i - 1].y + points[i].y) / 2f,
        )
        path.quadraticTo(points[i - 1].x, points[i - 1].y, mid.x, mid.y)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

/**
 * Rasteriza base + strokes al tamaño de la vista (o del base bitmap).
 */
fun rasterizeDrawing(
    base: Bitmap?,
    strokes: List<StoryDrawingStroke>,
    viewWidth: Float,
    viewHeight: Float,
): Bitmap? {
    if (strokes.isEmpty() && base == null) return null
    if (strokes.isEmpty()) return base?.copy(Bitmap.Config.ARGB_8888, false)

    val outW = base?.width?.takeIf { it > 0 } ?: viewWidth.toInt().coerceAtLeast(1)
    val outH = base?.height?.takeIf { it > 0 } ?: viewHeight.toInt().coerceAtLeast(1)
    val scaleX = outW / viewWidth.coerceAtLeast(1f)
    val scaleY = outH / viewHeight.coerceAtLeast(1f)

    val out = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
    val canvas = AndroidCanvas(out)
    base?.let {
        val scaled = if (it.width == outW && it.height == outH) {
            it
        } else {
            Bitmap.createScaledBitmap(it, outW, outH, true)
        }
        canvas.drawBitmap(scaled, 0f, 0f, null)
        if (scaled !== it) scaled.recycle()
    }

    strokes.forEach { stroke ->
        val scaled = stroke.copy(
            widthPx = stroke.widthPx * ((scaleX + scaleY) / 2f),
            brushSizePx = stroke.brushSizePx * ((scaleX + scaleY) / 2f),
            points = stroke.points.map { Offset(it.x * scaleX, it.y * scaleY) },
        )
        drawStrokeOnAndroidCanvas(canvas, scaled)
    }
    return out
}

private fun drawStrokeOnAndroidCanvas(canvas: AndroidCanvas, stroke: StoryDrawingStroke) {
    if (stroke.points.size < 2) return
    val path = smoothAndroidPath(stroke.points)
    when (stroke.brush) {
        StoryDrawingBrush.ERASER -> {
            val paint = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                strokeWidth = stroke.widthPx
                xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR)
            }
            canvas.drawPath(path, paint)
        }
        StoryDrawingBrush.GLOW -> {
            val blur = StoryDrawingGlowConfig.shadowRadius(stroke.brushSizePx)
            val glow = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                strokeWidth = stroke.widthPx
                color = stroke.color.copy(alpha = 1f).toArgb()
                maskFilter = BlurMaskFilter(blur, BlurMaskFilter.Blur.NORMAL)
            }
            val core = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                strokeWidth = stroke.widthPx
                color = Color.White.toArgb()
            }
            canvas.drawPath(path, glow)
            canvas.drawPath(path, core)
        }
        StoryDrawingBrush.MARKER -> {
            val paint = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                strokeWidth = stroke.widthPx
                color = stroke.color.copy(alpha = 0.40f).toArgb()
            }
            canvas.drawPath(path, paint)
        }
        StoryDrawingBrush.PENCIL -> {
            val paint = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                strokeWidth = stroke.widthPx
                color = stroke.color.copy(alpha = 0.78f).toArgb()
            }
            canvas.drawPath(path, paint)
        }
        StoryDrawingBrush.PEN, StoryDrawingBrush.ARROW -> {
            val paint = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                strokeWidth = stroke.widthPx
                color = stroke.color.toArgb()
            }
            canvas.drawPath(path, paint)
            if (stroke.brush == StoryDrawingBrush.ARROW) {
                drawAndroidArrowHead(canvas, stroke.points, stroke.color, stroke.widthPx)
            }
        }
    }
}

private fun smoothAndroidPath(points: List<Offset>): AndroidPath {
    val path = AndroidPath()
    if (points.isEmpty()) return path
    path.moveTo(points.first().x, points.first().y)
    for (i in 1 until points.size) {
        val midX = (points[i - 1].x + points[i].x) / 2f
        val midY = (points[i - 1].y + points[i].y) / 2f
        path.quadTo(points[i - 1].x, points[i - 1].y, midX, midY)
    }
    path.lineTo(points.last().x, points.last().y)
    return path
}

private fun drawAndroidArrowHead(canvas: AndroidCanvas, points: List<Offset>, color: Color, width: Float) {
    if (points.size < 2) return
    val tip = points.last()
    val prev = points[points.lastIndex - 1]
    val angle = atan2((tip.y - prev.y).toDouble(), (tip.x - prev.x).toDouble())
    val headLength = maxOf(12.0, width * 3.2)
    val spread = PI / 7
    val left = Offset(
        (tip.x - headLength * cos(angle - spread)).toFloat(),
        (tip.y - headLength * sin(angle - spread)).toFloat(),
    )
    val right = Offset(
        (tip.x - headLength * cos(angle + spread)).toFloat(),
        (tip.y - headLength * sin(angle + spread)).toFloat(),
    )
    val path = AndroidPath().apply {
        moveTo(left.x, left.y)
        lineTo(tip.x, tip.y)
        lineTo(right.x, right.y)
    }
    val paint = AndroidPaint().apply {
        isAntiAlias = true
        style = AndroidPaint.Style.STROKE
        strokeCap = AndroidPaint.Cap.ROUND
        strokeJoin = AndroidPaint.Join.ROUND
        strokeWidth = maxOf(3f, width * 0.9f)
        this.color = color.toArgb()
    }
    canvas.drawPath(path, paint)
}

private fun isPerceptuallyLight(color: Color): Boolean {
    return (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue) > 0.78f
}
