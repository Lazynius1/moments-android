package com.moments.android.views.creator.components
import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Highlight
import androidx.compose.material.icons.filled.NorthEast
import androidx.compose.material.icons.filled.AutoFixHigh
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
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
import androidx.compose.foundation.Image
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.HapticManager
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint
import android.graphics.Path as AndroidPath

/**
 * Port de `StoryDrawingEditorOverlay.swift` (chunk dibujo).
 * PencilKit → Compose Canvas. Glow/arrow aproximados; ColorPicker sistema deferred.
 */
enum class StoryDrawingBrush {
    PEN, ARROW, GLOW, MARKER, PENCIL, ERASER,
}

data class StoryDrawingStroke(
    val brush: StoryDrawingBrush,
    val color: Color,
    val widthPx: Float,
    val points: List<Offset>,
)

object StoryDrawingPalette {
    val swatches: List<String> = listOf(
        "FAF9F6", "0B1215",
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
) {
    val isDark = isSystemInDarkTheme()
    val controlFg = if (isDark) Color.White else Color.Black.copy(0.82f)
    val controlStroke = if (isDark) Color.White.copy(0.12f) else Color.Black.copy(0.08f)
    val secondary = controlFg.copy(if (isDark) 0.58f else 0.62f)

    var brush by remember { mutableStateOf(StoryDrawingBrush.PEN) }
    var colorHex by remember { mutableStateOf("FFFFFF") }
    var brushWidthDp by remember { mutableFloatStateOf(7f) }
    var strokes by remember { mutableStateOf<List<StoryDrawingStroke>>(emptyList()) }
    var redoStack by remember { mutableStateOf<List<StoryDrawingStroke>>(emptyList()) }
    var activeStroke by remember { mutableStateOf<StoryDrawingStroke?>(null) }

    val density = LocalDensity.current
    val brushWidthPx = with(density) { brushWidthDp.dp.toPx() }
    val strokeColor = parseStoryColorHex(colorHex)

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
            val canvasW = constraints.maxWidth.toFloat().coerceAtLeast(1f)
            val canvasH = constraints.maxHeight.toFloat().coerceAtLeast(1f)

            // Drawing surface
            Box(Modifier.fillMaxSize()) {
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
                                    activeStroke = StoryDrawingStroke(
                                        brush = brush,
                                        color = strokeColor,
                                        widthPx = when (brush) {
                                            StoryDrawingBrush.MARKER -> maxOf(10f, brushWidthPx * 2.4f)
                                            StoryDrawingBrush.GLOW -> maxOf(2f, brushWidthPx * 0.3f)
                                            StoryDrawingBrush.ARROW -> maxOf(3f, brushWidthPx)
                                            else -> brushWidthPx
                                        },
                                        points = listOf(offset),
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
                    drawList.forEach { stroke ->
                        drawStoryStroke(stroke)
                    }
                }
            }

            // Vertical size slider (left)
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, bottom = 100.dp),
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

            // Top chrome
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 14.dp)
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
                        .border(1.dp, controlStroke, RoundedCornerShape(50))
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

            // Bottom palette + brushes
            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StoryDrawingPalette.swatches.forEach { hex ->
                        val selected = hex.equals(colorHex, ignoreCase = true)
                        val swatch = parseStoryColorHex(hex)
                        val light = isPerceptuallyLight(swatch)
                        Box(
                            Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .border(
                                    width = if (selected) 2.5.dp else 1.dp,
                                    color = when {
                                        selected && light -> Color.Black.copy(0.9f)
                                        selected -> Color.White
                                        light -> Color.Black.copy(0.5f)
                                        else -> Color.White.copy(0.92f)
                                    },
                                    shape = CircleShape,
                                )
                                .clickable {
                                    colorHex = hex
                                    HapticManager.shared.lightImpact()
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
                        .border(1.dp, controlStroke, RoundedCornerShape(14.dp)),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BrushTool(Icons.Filled.Brush, StoryDrawingBrush.PEN, brush, controlFg, secondary) { brush = it }
                    BrushDivider(controlFg)
                    BrushTool(Icons.Filled.NorthEast, StoryDrawingBrush.ARROW, brush, controlFg, secondary) { brush = it }
                    BrushDivider(controlFg)
                    BrushTool(Icons.Filled.Highlight, StoryDrawingBrush.MARKER, brush, controlFg, secondary) { brush = it }
                    BrushDivider(controlFg)
                    BrushTool(Icons.Filled.Edit, StoryDrawingBrush.PENCIL, brush, controlFg, secondary) { brush = it }
                    BrushDivider(controlFg)
                    BrushTool(Icons.Filled.AutoAwesome, StoryDrawingBrush.GLOW, brush, controlFg, secondary) { brush = it }
                    BrushDivider(controlFg)
                    BrushTool(Icons.Filled.AutoFixHigh, StoryDrawingBrush.ERASER, brush, controlFg, secondary) { brush = it }
                }
            }
        }
    }
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
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun BrushDivider(fg: Color) {
    Box(
        Modifier
            .width(1.dp)
            .height(24.dp)
            .background(fg.copy(0.14f)),
    )
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
        val h = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val trackH = h - with(LocalDensity.current) { 32.dp.toPx() }
        val progress = ((value - range.start) / (range.endInclusive - range.start)).coerceIn(0f, 1f)
        val knobY = with(LocalDensity.current) { 16.dp.toPx() } + (1f - progress) * trackH

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
            Box(
                Modifier
                    .align(Alignment.Center)
                    .width(16.dp)
                    .fillMaxHeight()
                    .padding(vertical = 16.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.White.copy(0.32f)),
            )
            Box(
                Modifier
                    .offset(y = with(LocalDensity.current) { (knobY - 14.dp.toPx()).toDp() })
                    .align(Alignment.TopCenter)
                    .size(if (dragging) 30.dp else 28.dp)
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
            drawIntoCanvas { canvas ->
                val glow = AndroidPaint().apply {
                    isAntiAlias = true
                    style = AndroidPaint.Style.STROKE
                    strokeCap = AndroidPaint.Cap.ROUND
                    strokeJoin = AndroidPaint.Join.ROUND
                    strokeWidth = stroke.widthPx * 3.2f
                    color = stroke.color.copy(alpha = 0.55f).toArgb()
                    maskFilter = BlurMaskFilter(
                        maxOf(2f, stroke.widthPx * 1.2f),
                        BlurMaskFilter.Blur.NORMAL,
                    )
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
    val len = width * 3.2f
    val left = Offset(
        tip.x - len * cos(angle - 0.45f).toFloat(),
        tip.y - len * sin(angle - 0.45f).toFloat(),
    )
    val right = Offset(
        tip.x - len * cos(angle + 0.45f).toFloat(),
        tip.y - len * sin(angle + 0.45f).toFloat(),
    )
    val head = Path().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    drawPath(head, color)
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
            val glow = AndroidPaint().apply {
                isAntiAlias = true
                style = AndroidPaint.Style.STROKE
                strokeCap = AndroidPaint.Cap.ROUND
                strokeJoin = AndroidPaint.Join.ROUND
                strokeWidth = stroke.widthPx * 3.2f
                color = stroke.color.copy(alpha = 0.55f).toArgb()
                maskFilter = BlurMaskFilter(maxOf(2f, stroke.widthPx * 1.2f), BlurMaskFilter.Blur.NORMAL)
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
    val len = width * 3.2f
    val left = Offset(
        (tip.x - len * cos(angle - 0.45)).toFloat(),
        (tip.y - len * sin(angle - 0.45)).toFloat(),
    )
    val right = Offset(
        (tip.x - len * cos(angle + 0.45)).toFloat(),
        (tip.y - len * sin(angle + 0.45)).toFloat(),
    )
    val path = AndroidPath().apply {
        moveTo(tip.x, tip.y)
        lineTo(left.x, left.y)
        lineTo(right.x, right.y)
        close()
    }
    val paint = AndroidPaint().apply {
        isAntiAlias = true
        style = AndroidPaint.Style.FILL
        this.color = color.toArgb()
    }
    canvas.drawPath(path, paint)
}

private fun isPerceptuallyLight(color: Color): Boolean {
    return (0.299f * color.red + 0.587f * color.green + 0.114f * color.blue) > 0.78f
}
