package com.moments.android.views.story

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Base64
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.revealContrastingEffectColor
import com.moments.android.models.StickerData
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.StickerPolaroidFrameView
import com.moments.android.views.components.StoryPolaroidFrameStyle
import com.moments.android.views.story.storyviewer.StoryGestureSuppressionScope
import com.moments.android.views.story.storyviewer.StoryGestureCoordinator
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Port parcial de `Views/story/StoryInteractiveStickers.swift`.
 *
 * Este chunk contiene el Polaroid: se revela al agitar, pausa el progreso de la
 * story durante la interacción y persiste el resultado por story, como iOS.
 */
@Composable
fun StoryInteractiveStickerLayer(
    storyId: String,
    stickers: List<StickerData>,
    onPauseStory: () -> Unit,
    onResumeStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }
        val displayScale = (widthPx / 375f).coerceAtLeast(0.01f)

        stickers
            .filter { it.type == "frame" }
            .sortedBy { it.zIndex ?: 0 }
            .forEach { sticker ->
                val xPx = (sticker.position.x * widthPx).toFloat()
                val yPx = (sticker.position.y * heightPx).toFloat()
                val frameWidthPx = with(density) { 200.dp.toPx() }
                val frameHeightPx = with(density) { 240.dp.toPx() }

                InteractiveFrameSticker(
                    storyId = "$storyId.${sticker.stickerId.orEmpty()}",
                    imageContent = sticker.content,
                    caption = sticker.caption,
                    frameStyle = StoryPolaroidFrameStyle.fromRawOrDefault(sticker.frameStyle),
                    contentScale = sticker.contentScale?.toFloat() ?: 1f,
                    contentOffsetX = sticker.contentOffsetX?.toFloat() ?: 0f,
                    contentOffsetY = sticker.contentOffsetY?.toFloat() ?: 0f,
                    onPauseStory = onPauseStory,
                    onResumeStory = onResumeStory,
                    modifier = Modifier
                        .size(width = 200.dp, height = 240.dp)
                        .offset {
                            IntOffset(
                                (xPx - frameWidthPx / 2f).roundToInt(),
                                (yPx - frameHeightPx / 2f).roundToInt(),
                            )
                        }
                        .graphicsLayer {
                            scaleX = sticker.scale.toFloat() * displayScale
                            scaleY = sticker.scale.toFloat() * displayScale
                            rotationZ = Math.toDegrees(sticker.rotation).toFloat()
                        },
                )
            }
    }
}

/** Equivalente Compose de `InteractiveFrameSticker`. */
@Composable
fun InteractiveFrameSticker(
    storyId: String = "",
    imageContent: String,
    caption: String? = null,
    frameStyle: StoryPolaroidFrameStyle = StoryPolaroidFrameStyle.CLASSIC,
    contentScale: Float = 1f,
    contentOffsetX: Float = 0f,
    contentOffsetY: Float = 0f,
    isEditing: Boolean = false,
    onPauseStory: (() -> Unit)? = null,
    onResumeStory: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("moments_story_stickers", Context.MODE_PRIVATE)
    }
    val persistenceKey = remember(storyId) { "frame_revealed_$storyId" }
    val bitmap = remember(imageContent) { decodeStickerBitmap(imageContent) }
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val accelerometer = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) }
    val scope = rememberCoroutineScope()
    val listenerRef = remember(storyId) { AtomicReference<SensorEventListener?>(null) }
    var revealProgress by remember(storyId, isEditing) {
        mutableFloatStateOf(if (isEditing) 1f else 0f)
    }
    var lastAcceleration by remember(storyId) { mutableStateOf<FloatArray?>(null) }
    var resumeJob by remember(storyId) { mutableStateOf<Job?>(null) }
    var hasMarkedRevealed by remember(storyId) { mutableStateOf(false) }
    val animatedProgress by animateFloatAsState(
        targetValue = revealProgress,
        animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing),
        label = "frameReveal",
    )

    fun markAsRevealed() {
        if (storyId.isNotEmpty() && !hasMarkedRevealed) {
            prefs.edit().putBoolean(persistenceKey, true).apply()
            hasMarkedRevealed = true
        }
    }

    fun processShake() {
        if (revealProgress >= 1f) return
        onPauseStory?.invoke()
        resumeJob?.cancel()
        resumeJob = scope.launch {
            delay(1_500)
            if (revealProgress < 1f) onResumeStory?.invoke()
        }

        revealProgress = min(revealProgress + 0.038f, 1f)
        if (revealProgress < 1f) {
            HapticManager.shared.lightImpact()
        } else {
            HapticManager.shared.success()
            markAsRevealed()
            resumeJob?.cancel()
            listenerRef.getAndSet(null)?.let(sensorManager::unregisterListener)
            onResumeStory?.invoke()
        }
    }

    LaunchedEffect(storyId, isEditing) {
        if (!isEditing && storyId.isNotEmpty() && prefs.getBoolean(persistenceKey, false)) {
            hasMarkedRevealed = true
            revealProgress = 1f
        }
    }

    LaunchedEffect(accelerometer, isEditing, storyId) {
        if (!isEditing && accelerometer == null && revealProgress < 1f) {
            delay(1_500)
            revealProgress = 1f
            markAsRevealed()
        }
    }

    DisposableEffect(accelerometer, isEditing, storyId) {
        if (isEditing || accelerometer == null || revealProgress >= 1f) {
            onDispose { resumeJob?.cancel() }
        } else {
            val listener = object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    val values = event.values
                    val last = lastAcceleration
                    if (last != null) {
                        val delta = abs(values[0] - last[0]) +
                            abs(values[1] - last[1]) +
                            abs(values[2] - last[2])
                        if (delta > 1.2f) processShake()
                    }
                    lastAcceleration = values.copyOf()
                }

                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
            }
            listenerRef.set(listener)
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_GAME)
            onDispose {
                sensorManager.unregisterListener(listener)
                listenerRef.compareAndSet(listener, null)
                resumeJob?.cancel()
            }
        }
    }

    StickerPolaroidFrameView(
        image = bitmap,
        caption = caption,
        frameStyle = frameStyle,
        contentScale = contentScale,
        contentOffsetX = contentOffsetX,
        contentOffsetY = contentOffsetY,
        progress = animatedProgress,
        modifier = modifier,
    )
}

private fun decodeStickerBitmap(content: String) = runCatching {
    val bytes = Base64.decode(content, Base64.DEFAULT)
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
}.getOrNull()

/** Capa única de Reveal. El catálogo iOS limita este sticker a una story. */
@Composable
fun StoryRevealStickerOverlay(
    storyId: String,
    stickers: List<StickerData>,
    gestureGate: StoryDeckGestureGate? = null,
    onPauseStory: () -> Unit,
    onResumeStory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val sticker = stickers.firstOrNull { it.type == "reveal" } ?: return
    InteractiveRevealSticker(
        storyId = storyId,
        gestureGate = gestureGate,
        onPauseStory = onPauseStory,
        onResumeStory = onResumeStory,
        revealType = sticker.revealType,
        revealPattern = sticker.revealPattern,
        revealPrimaryColor = sticker.revealPrimaryColor,
        revealSecondaryColor = sticker.revealSecondaryColor,
        revealEffectColor = sticker.revealEffectColor,
        modifier = modifier,
    )
}

/**
 * Equivalente Compose del bloque `InteractiveRevealSticker` de Swift.
 * Rascar al menos el 65 % de la cuadrícula 12×12 revela la story y lo persiste.
 */
@Composable
fun InteractiveRevealSticker(
    storyId: String = "",
    gestureGate: StoryDeckGestureGate? = null,
    onPauseStory: (() -> Unit)? = null,
    onResumeStory: (() -> Unit)? = null,
    revealType: String? = null,
    revealPattern: String? = null,
    revealPrimaryColor: String? = null,
    revealSecondaryColor: String? = null,
    revealEffectColor: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("moments_story_stickers", Context.MODE_PRIVATE)
    }
    val persistenceKey = remember(storyId) { "reveal_revealed_$storyId" }
    val suppressionSourceId = remember(storyId) { "reveal.scratch.$storyId" }
    val points = remember(storyId) { mutableStateListOf<Offset>() }
    val scratchedCells = remember(storyId) { mutableStateListOf<Int>() }
    var isRevealed by remember(storyId) { mutableStateOf(false) }
    var isScratching by remember(storyId) { mutableStateOf(false) }
    var didPauseForScratch by remember(storyId) { mutableStateOf(false) }
    var showHint by remember(storyId) { mutableStateOf(false) }
    var canvasSize by remember(storyId) { mutableStateOf(IntOffset.Zero) }
    val density = LocalDensity.current
    val hintPulse = rememberInfiniteTransition(label = "revealHint").animateFloat(
        initialValue = 0.985f,
        targetValue = 1.03f,
        animationSpec = infiniteRepeatable(tween(720), RepeatMode.Reverse),
        label = "revealHintPulse",
    )

    fun resumeStoryIfNeeded() {
        if (didPauseForScratch && !isRevealed) {
            didPauseForScratch = false
            onResumeStory?.invoke()
        }
    }

    fun completeReveal() {
        if (isRevealed) return
        isScratching = false
        isRevealed = true
        didPauseForScratch = false
        gestureGate?.clearSuppression(suppressionSourceId)
        HapticManager.shared.success()
        onResumeStory?.invoke()
        if (storyId.isNotEmpty()) prefs.edit().putBoolean(persistenceKey, true).apply()
    }

    fun recordPoint(point: Offset) {
        if (isRevealed || canvasSize.x <= 0 || canvasSize.y <= 0) return
        points += point
        val gridSize = 12
        val column = ((point.x / canvasSize.x) * gridSize).toInt()
        val row = ((point.y / canvasSize.y) * gridSize).toInt()
        if (column in 0 until gridSize && row in 0 until gridSize) {
            val index = row * gridSize + column
            if (index !in scratchedCells) scratchedCells += index
        }
        if (scratchedCells.size.toFloat() / (gridSize * gridSize) > 0.65f) completeReveal()
    }

    LaunchedEffect(storyId) {
        isRevealed = storyId.isNotEmpty() && prefs.getBoolean(persistenceKey, false)
        if (!isRevealed) {
            showHint = true
            delay(3_800)
            if (points.isEmpty() && !isRevealed) showHint = false
        }
    }

    DisposableEffect(storyId) {
        onDispose {
            if (isScratching) {
                isScratching = false
                gestureGate?.clearSuppression(suppressionSourceId)
                resumeStoryIfNeeded()
            }
        }
    }

    AnimatedVisibility(
        visible = !isRevealed,
        exit = fadeOut(tween(600)),
        modifier = modifier,
    ) {
        val scratchWidth = with(density) { 35.dp.toPx() }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val sideInsetDp = maxWidth * StoryGestureCoordinator.REVEAL_SIDE_PASSTHROUGH_FRACTION
            val sideInsetPx = with(density) { sideInsetDp.toPx() }
            Box(
            Modifier
                .fillMaxSize()
                .onSizeChanged { canvasSize = IntOffset(it.width, it.height) }
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    if (points.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(points.first().x, points.first().y)
                            points.drop(1).forEach { lineTo(it.x, it.y) }
                        }
                        drawPath(
                            path = path,
                            color = Color.Transparent,
                            style = Stroke(width = scratchWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
                            blendMode = BlendMode.Clear,
                        )
                    }
                },
        ) {
            RevealSurfaceView(
                type = revealType,
                pattern = revealPattern,
                primaryColor = revealPrimaryColor,
                secondaryColor = revealSecondaryColor,
                effectColor = revealEffectColor,
                modifier = Modifier.fillMaxSize(),
            )
            if (showHint) {
                Text(
                    text = "☝  ${stringResource(R.string.reveal_viewer_hint)}",
                    color = Color.White.copy(alpha = 0.96f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp)
                        .graphicsLayer {
                            scaleX = hintPulse.value
                            scaleY = hintPulse.value
                            alpha = 0.9f
                        },
                )
            }
        }
            // Swift deja los bordes libres para la navegación anterior/siguiente.
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth()
                    .padding(horizontal = sideInsetDp)
                    .pointerInput(storyId, isRevealed) {
                    detectDragGestures(
                        onDragStart = { point ->
                            showHint = false
                            isScratching = true
                            gestureGate?.setSuppressionScope(
                                StoryGestureSuppressionScope.SUPPRESS_VIEWER_GESTURES,
                                suppressionSourceId,
                            )
                            if (!didPauseForScratch) {
                                didPauseForScratch = true
                                onPauseStory?.invoke()
                            }
                            recordPoint(Offset(point.x + sideInsetPx, point.y))
                        },
                        onDragEnd = {
                            isScratching = false
                            gestureGate?.clearSuppression(suppressionSourceId)
                            resumeStoryIfNeeded()
                        },
                        onDragCancel = {
                            isScratching = false
                            gestureGate?.clearSuppression(suppressionSourceId)
                            resumeStoryIfNeeded()
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            recordPoint(Offset(change.position.x + sideInsetPx, change.position.y))
                        },
                    )
                },
            )
        }
    }
}

/** Port del fondo y patrones estáticos de `RevealSurfaceView`. */
@Composable
fun RevealSurfaceView(
    type: String?,
    pattern: String?,
    primaryColor: String?,
    secondaryColor: String?,
    effectColor: String?,
    modifier: Modifier = Modifier,
) {
    val primary = Color.fromHex(primaryColor ?: "#000000")
    val secondary = Color.fromHex(secondaryColor ?: "#000000")
    val effect = when {
        pattern == "holographic" -> primary
        !effectColor.isNullOrBlank() -> Color.fromHex(effectColor)
        !secondaryColor.isNullOrBlank() && !secondaryColor.equals(primaryColor, ignoreCase = true) -> secondary
        else -> primary.revealContrastingEffectColor()
    }
    val resolvedPattern = pattern?.takeIf { it != "none" }
        ?: if (type == null || type == "scratch" || type == "none") "dots" else null
    val patternPhase = rememberInfiniteTransition(label = "revealPattern").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(8_000), RepeatMode.Restart),
        label = "revealPatternPhase",
    ).value

    Canvas(modifier) {
        if (type == "gradient") {
            drawRect(Brush.linearGradient(listOf(primary, secondary)))
        } else {
            drawRect(primary)
        }
        when (resolvedPattern) {
            "dots" -> {
                val spacing = 12.dp.toPx()
                for (x in 0..(size.width / spacing).toInt()) {
                    for (y in 0..(size.height / spacing).toInt()) {
                        drawCircle(effect.copy(alpha = 0.7f), radius = 1.2.dp.toPx(), center = Offset(x * spacing, y * spacing))
                    }
                }
            }
            "grid" -> {
                val spacing = 30.dp.toPx()
                for (x in 0..(size.width / spacing).toInt()) drawLine(effect.copy(alpha = 0.3f), Offset(x * spacing, 0f), Offset(x * spacing, size.height), 0.5.dp.toPx())
                for (y in 0..(size.height / spacing).toInt()) drawLine(effect.copy(alpha = 0.3f), Offset(0f, y * spacing), Offset(size.width, y * spacing), 0.5.dp.toPx())
            }
            "lines" -> {
                val spacing = 15.dp.toPx()
                for (x in -size.height.toInt()..size.width.toInt() step spacing.toInt().coerceAtLeast(1)) {
                    drawLine(effect.copy(alpha = 0.4f), Offset(x.toFloat(), 0f), Offset(x + size.height, size.height), 1.dp.toPx())
                }
            }
            "noise" -> {
                val count = (size.width * size.height / 1_500f).toInt().coerceIn(80, 420)
                repeat(count) { index ->
                    val seed = index * 0.618034f
                    val x = ((seed * size.width + sin(patternPhase * 9f + index) * 12f) % size.width + size.width) % size.width
                    val y = (((seed * 1.73f) * size.height + cos(patternPhase * 7f + index) * 15f) % size.height + size.height) % size.height
                    val alpha = (0.25f + abs(sin(index + patternPhase * 12f)) * 0.42f).coerceIn(0f, 0.7f)
                    drawCircle(effect.copy(alpha = alpha), radius = 1.2.dp.toPx() + (index % 3), center = Offset(x, y))
                }
            }
            "static" -> {
                val count = (size.width * size.height / 1_200f).toInt().coerceIn(100, 500)
                repeat(count) { index ->
                    val x = ((sin(index * 12.9898f + patternPhase * 42f) * 43_758.547f) % 1f).let { if (it < 0) it + 1f else it } * size.width
                    val y = ((sin(index * 78.233f + patternPhase * 31f) * 12_345.679f) % 1f).let { if (it < 0) it + 1f else it } * size.height
                    val tone = when (index % 3) { 0 -> Color.White; 1 -> Color.Gray; else -> Color.Black }
                    drawRect(tone.copy(alpha = 0.18f + (index % 5) * 0.1f), topLeft = Offset(x, y), size = androidx.compose.ui.geometry.Size(2.dp.toPx(), 1.dp.toPx()))
                }
            }
            "scanlines" -> {
                val spacing = 8.dp.toPx()
                val offset = (patternPhase * spacing).coerceAtMost(spacing)
                var y = -spacing
                while (y < size.height + spacing) {
                    drawRect(effect.copy(alpha = 0.3f), topLeft = Offset(0f, y + offset), size = androidx.compose.ui.geometry.Size(size.width, 2.5.dp.toPx()))
                    y += spacing
                }
                drawRect(effect.copy(alpha = 0.05f), topLeft = Offset(0f, (patternPhase * (size.height + 400f)) - 200f), size = androidx.compose.ui.geometry.Size(size.width, 60.dp.toPx()))
            }
            "waves" -> {
                val spacing = 30.dp.toPx()
                for (y in -40.dp.toPx().toInt()..(size.height + 40.dp.toPx()).toInt() step spacing.toInt()) {
                    val wave = Path().apply {
                        moveTo(0f, y.toFloat())
                        var x = 0f
                        while (x <= size.width + 20.dp.toPx()) {
                            lineTo(x, y + sin(x / 20.dp.toPx() + patternPhase * 16f) * 8.dp.toPx())
                            x += 10.dp.toPx()
                        }
                    }
                    drawPath(wave, effect.copy(alpha = 0.4f), style = Stroke(width = 2.5.dp.toPx()))
                }
            }
            "matrix" -> {
                val columns = (size.width / 20.dp.toPx()).toInt()
                repeat(columns) { column ->
                    val x = column * 20.dp.toPx() + 10.dp.toPx()
                    val speed = (sin(column * 0.5f) + 2f) * 80f
                    val head = ((patternPhase * 8_000f * speed / 1_000f) % (size.height + 200f)) - 100f
                    repeat(12) { segment ->
                        val y = head - segment * 15.dp.toPx()
                        if (y in 0f..size.height) {
                            drawRect(effect.copy(alpha = (1f - segment / 12f) * 0.6f), topLeft = Offset(x - 4.dp.toPx(), y), size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 12.dp.toPx()))
                            if (segment == 0) drawRect(Color.White.copy(alpha = 0.4f), topLeft = Offset(x - 4.dp.toPx(), y), size = androidx.compose.ui.geometry.Size(8.dp.toPx(), 12.dp.toPx()))
                        }
                    }
                }
            }
        }
    }
    if (resolvedPattern == "holographic") {
        RevealHolographicPattern(
            color = effect,
            accentColor = secondary,
            modifier = modifier,
        )
    }
}

/** Equivalente Android del patrón holográfico: brillo iridiscente que responde a la inclinación. */
@Composable
private fun RevealHolographicPattern(
    color: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val rotationVector = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }
    var rotationRate by remember { mutableFloatStateOf(0f) }
    val phase = rememberInfiniteTransition(label = "holographicPattern").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(10_000), RepeatMode.Restart),
        label = "holographicPhase",
    ).value

    DisposableEffect(rotationVector) {
        if (rotationVector == null) return@DisposableEffect onDispose { }
        var previousPitch = 0f
        var previousRoll = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val matrix = FloatArray(9)
                val orientation = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                SensorManager.getOrientation(matrix, orientation)
                pitch = orientation[1]
                roll = orientation[2]
                rotationRate = (abs(pitch - previousPitch) + abs(roll - previousRoll)).coerceAtMost(1f)
                previousPitch = pitch
                previousRoll = roll
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sensorManager.registerListener(listener, rotationVector, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sensorManager.unregisterListener(listener) }
    }

    Canvas(modifier) {
        drawRect(
            Brush.linearGradient(
                listOf(Color(0xFFD1D1D1), Color(0xFFB3B3B3), Color(0xFFC7C7C7)),
            ),
        )
        val cell = if (size.width < 150.dp.toPx()) 10.dp.toPx() else 20.dp.toPx()
        val cols = (size.width / cell).toInt() + 2
        val rows = (size.height / cell).toInt() + 2
        val tiltHue = roll / Math.PI.toFloat() * 0.4f + pitch / (Math.PI.toFloat() / 2f) * 0.2f
        repeat(cols) { column ->
            repeat(rows) { row ->
                var hue = (column.toFloat() / cols + row.toFloat() / rows * 0.5f + tiltHue + phase + accentColor.red * 0.3f) % 1f
                if (hue < 0f) hue += 1f
                val saturation = (0.5f + 0.4f * sin(column * 0.7f + row * 0.5f + phase * 6.28f)).coerceIn(0f, 1f)
                drawRect(
                    Color(android.graphics.Color.HSVToColor(floatArrayOf(hue * 360f, saturation, 0.95f))).copy(alpha = 0.5f),
                    topLeft = Offset(column * cell, row * cell),
                    size = androidx.compose.ui.geometry.Size(cell + 1f, cell + 1f),
                )
            }
        }
        val glitterCount = if (size.width < 150.dp.toPx()) 280 else 1_200
        repeat(glitterCount) { index ->
            val x = ((sin(index * 12.9898f) * 43_758.547f) % 1f).let { if (it < 0f) it + 1f else it } * size.width
            val y = ((sin(index * 78.233f) * 12_345.679f) % 1f).let { if (it < 0f) it + 1f else it } * size.height
            val radius = 0.4.dp.toPx() + (index % 5) * 0.22.dp.toPx()
            val alpha = if (index % 8 == 0) 0.78f + rotationRate * 0.18f else 0.55f
            drawCircle(color.copy(alpha = alpha.coerceAtMost(0.96f)), radius, Offset(x, y))
        }
    }
}
