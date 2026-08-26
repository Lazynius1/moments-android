package com.moments.android.views.story

import android.content.Context
import android.graphics.BitmapFactory
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.zIndex
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.moments.android.R
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.fromHex
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.extensions.revealContrastingEffectColor
import com.moments.android.models.StickerData
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.views.components.StickerDitherPattern
import com.moments.android.views.components.StickerPolaroidFrameView
import com.moments.android.views.components.StickerQuizCardView
import com.moments.android.views.components.StoryPolaroidFrameStyle
import com.moments.android.views.creator.creatoruikit.storyViewerCanvasCornerRadius
import com.moments.android.views.story.storyviewer.RevealScratchPanOverlay
import com.moments.android.views.story.storyviewer.StoryGestureCoordinator
import com.moments.android.views.story.storyviewer.StoryGestureIntent
import com.moments.android.views.story.storyviewer.StoryGestureSuppressionScope
import com.moments.android.views.story.storyviewer.StoryViewerLayoutHelpers
import com.moments.android.views.story.storyviewer.storyDeckInteractionExclusion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Port de `Views/story/StoryInteractiveStickers.swift`:
 * quiz (voto + confetti), polaroid shake-to-reveal, scratch reveal + superficies.
 */

// MARK: - 1. QUIZ STICKER

/** Port de `InteractiveQuizSticker`. */
@Composable
fun InteractiveQuizSticker(
    storyId: String,
    userId: String,
    stickerId: String,
    question: String,
    options: List<String>,
    correctIndex: Int,
    isEditing: Boolean = false,
    styleVariant: Int = 0,
    modifier: Modifier = Modifier,
) {
    var selectedIndex by remember(storyId, stickerId) { mutableStateOf<Int?>(null) }
    var showConfetti by remember(storyId, stickerId) { mutableStateOf(false) }
    var confettiElapsed by remember(storyId, stickerId) { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val currentUserId = FirebaseAuth.getInstance().currentUser?.uid

    fun interactionDocument() =
        if (currentUserId == null) {
            null
        } else {
            FirebaseFirestore.getInstance()
                .collection("users").document(userId)
                .collection("stories").document(storyId)
                .collection("stickerInteractions")
                .document("${stickerId}_$currentUserId")
        }

    fun submitVote(index: Int) {
        if (currentUserId == null || selectedIndex != null) return
        val correct = index == correctIndex
        selectedIndex = index
        if (correct) {
            HapticManager.shared.success()
            showConfetti = true
            scope.launch {
                delay(2_500)
                showConfetti = false
            }
        } else {
            HapticManager.shared.error()
        }
        scope.launch {
            runCatching {
                interactionDocument()?.set(
                    mapOf(
                        "userId" to currentUserId,
                        "stickerId" to stickerId,
                        "type" to "quiz",
                        "selectedIndex" to index,
                        "isCorrect" to correct,
                        "timestamp" to FieldValue.serverTimestamp(),
                    ),
                )?.await()
            }
        }
    }

    LaunchedEffect(storyId, stickerId, isEditing, userId) {
        if (isEditing || userId == "preview" || storyId == "preview") return@LaunchedEffect
        val index = runCatching {
            (interactionDocument()?.get()?.await()?.get("selectedIndex") as? Number)?.toInt()
        }.getOrNull()
        if (index != null) selectedIndex = index
    }

    LaunchedEffect(showConfetti) {
        if (!showConfetti) {
            confettiElapsed = 0f
            return@LaunchedEffect
        }
        var startNanos = 0L
        withFrameNanos { startNanos = it }
        while (showConfetti) {
            withFrameNanos { now ->
                confettiElapsed = ((now - startNanos) / 1_000_000_000.0).toFloat()
            }
        }
    }

    Box(modifier.width(300.dp)) {
        StickerQuizCardView(
            question = question,
            options = options,
            selectedIndex = selectedIndex,
            correctIndex = correctIndex,
            onSelect = ::submitVote,
            styleVariant = styleVariant,
            modifier = Modifier.fillMaxWidth(),
        )
        AnimatedVisibility(
            visible = showConfetti,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.matchParentSize(),
        ) {
            Canvas(Modifier.fillMaxSize()) {
                QuizConfettiRenderer.draw(this, size, confettiElapsed.toDouble())
            }
        }
    }
}

/** Port de `QuizConfettiRenderer` (partículas con seed determinista). */
private object QuizConfettiRenderer {
    private data class Particle(
        val x: Float,
        val vx: Float,
        val vy: Float,
        val colorIndex: Int,
        val size: Float,
        val rotSpeed: Float,
    )

    private val colors = listOf(
        Color(0xFF34C759),
        Color(0xFFFFCC00),
        Color.White,
        Color(0xFF32ADE6),
        Color(0xFF00C7BE),
        Color(0xFFE6FF99),
    )

    private val particles: List<Particle> = run {
        var rng = 0xDEADBEEFUL
        fun rand(): Float {
            rng = rng * 6364136223846793005UL + 1442695040888963407UL
            return (rng shr 33).toUInt().toFloat() / UInt.MAX_VALUE.toFloat()
        }
        List(52) {
            Particle(
                x = rand(),
                vx = (rand() - 0.5f) * 120f,
                vy = -(rand() * 180f + 80f),
                colorIndex = (rand() * colors.size).toInt().coerceIn(0, colors.lastIndex),
                size = rand() * 6f + 5f,
                rotSpeed = (rand() - 0.5f) * 8f,
            )
        }
    }

    fun draw(scope: androidx.compose.ui.graphics.drawscope.DrawScope, canvasSize: Size, elapsed: Double) {
        if (elapsed >= 2.5) return
        val t = elapsed.toFloat()
        val gravity = 220f
        for (p in particles) {
            val x = canvasSize.width * p.x + p.vx * t
            val y = canvasSize.height * 0.3f + p.vy * t + 0.5f * gravity * t * t
            val opacity = (1.0 - t / 2.0).toFloat().coerceAtLeast(0f)
            if (opacity <= 0f || y >= canvasSize.height + 20f) continue
            val color = colors[p.colorIndex % colors.size].copy(alpha = opacity)
            val w = p.size
            val h = p.size * 0.55f
            scope.rotate(degrees = Math.toDegrees((p.rotSpeed * t).toDouble()).toFloat(), pivot = Offset(x, y)) {
                drawRect(
                    color = color,
                    topLeft = Offset(x - w / 2f, y - h / 2f),
                    size = Size(w, h),
                )
            }
        }
    }
}

// MARK: - 2. POLAROID FRAME (SHAKE TO REVEAL)

@Composable
fun StoryInteractiveStickerLayer(
    storyId: String,
    stickers: List<StickerData>,
    onPauseStory: () -> Unit,
    onResumeStory: () -> Unit,
    gestureGate: StoryDeckGestureGate? = null,
    reportsDeckInteractionExclusion: Boolean = true,
    isThumbnail: Boolean = false,
    /** Si se pasa, no hay Box fillMaxSize intermedio → zIndex interleave con texto. */
    containerWidthPx: Float? = null,
    containerHeightPx: Float? = null,
    modifier: Modifier = Modifier,
) {
    if (containerWidthPx != null && containerHeightPx != null) {
        StoryInteractiveFrameStickers(
            storyId = storyId,
            stickers = stickers,
            widthPx = containerWidthPx,
            heightPx = containerHeightPx,
            onPauseStory = onPauseStory,
            onResumeStory = onResumeStory,
            gestureGate = gestureGate,
            reportsDeckInteractionExclusion = reportsDeckInteractionExclusion,
            isThumbnail = isThumbnail,
        )
    } else {
        BoxWithConstraints(modifier) {
            val density = LocalDensity.current
            StoryInteractiveFrameStickers(
                storyId = storyId,
                stickers = stickers,
                widthPx = with(density) { maxWidth.toPx() },
                heightPx = with(density) { maxHeight.toPx() },
                onPauseStory = onPauseStory,
                onResumeStory = onResumeStory,
                gestureGate = gestureGate,
                reportsDeckInteractionExclusion = reportsDeckInteractionExclusion,
                isThumbnail = isThumbnail,
            )
        }
    }
}

@Composable
private fun StoryInteractiveFrameStickers(
    storyId: String,
    stickers: List<StickerData>,
    widthPx: Float,
    heightPx: Float,
    onPauseStory: () -> Unit,
    onResumeStory: () -> Unit,
    gestureGate: StoryDeckGestureGate?,
    reportsDeckInteractionExclusion: Boolean,
    isThumbnail: Boolean,
) {
    val density = LocalDensity.current
    val canvasScaleFactor = StoryViewerLayoutHelpers.stickerDisplayScale(1.0, widthPx, density.density)

    stickers
        .filter { it.type == "frame" }
        .sortedBy { it.zIndex ?: 0 }
        .forEach { sticker ->
            val xPx = (sticker.position.x * widthPx).toFloat()
            val yPx = (sticker.position.y * heightPx).toFloat()
            val frameWidthPx = with(density) { 200.dp.toPx() }
            val frameHeightPx = with(density) { 240.dp.toPx() }
            val exclusionId = "sticker.$storyId.${sticker.stickerId.orEmpty()}"
            val displayScale = sticker.scale.toFloat() * canvasScaleFactor

            val frameModifier = Modifier
                    .zIndex((sticker.zIndex ?: 0).toFloat())
                    .size(width = 200.dp, height = 240.dp)
                    .offset {
                        IntOffset(
                            (xPx - frameWidthPx / 2f).roundToInt(),
                            (yPx - frameHeightPx / 2f).roundToInt(),
                        )
                    }
                    .graphicsLayer {
                        scaleX = displayScale
                        scaleY = displayScale
                        rotationZ = Math.toDegrees(sticker.rotation).toFloat()
                    }
                    .storyDeckInteractionExclusion(
                        id = exclusionId,
                        gate = gestureGate,
                        enabled = reportsDeckInteractionExclusion && !isThumbnail,
                    )
            if (isThumbnail) {
                StickerPolaroidFrameView(
                    image = remember(sticker.content) { decodeStickerBitmap(sticker.content) },
                    caption = sticker.caption,
                    frameStyle = StoryPolaroidFrameStyle.fromRawOrDefault(sticker.frameStyle),
                    contentScale = sticker.contentScale?.toFloat() ?: 1f,
                    contentOffsetX = sticker.contentOffsetX?.toFloat() ?: 0f,
                    contentOffsetY = sticker.contentOffsetY?.toFloat() ?: 0f,
                    progress = 0f,
                    modifier = frameModifier,
                )
            } else {
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
                    modifier = frameModifier,
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
            // ≡ withAnimation(.linear(duration: 3.3)) { revealProgress = 1.0 }
            val anim = Animatable(revealProgress)
            anim.animateTo(1f, tween(durationMillis = 3_300, easing = LinearEasing)) {
                revealProgress = value
            }
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
            // ≡ accelerometerUpdateInterval = 0.1
            sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
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
    reportsDeckInteractionExclusion: Boolean = true,
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
        reportsDeckInteractionExclusion = reportsDeckInteractionExclusion,
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
    reportsDeckInteractionExclusion: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("moments_story_stickers", Context.MODE_PRIVATE)
    }
    val persistenceKey = remember(storyId) { "reveal_revealed_$storyId" }
    val deckExclusionZoneId = remember(storyId) { "reveal.scratch.$storyId" }
    val suppressionSourceId = deckExclusionZoneId
    val points = remember(storyId) { mutableStateListOf<Offset>() }
    val scratchedCells = remember(storyId) { mutableStateListOf<Int>() }
    var isRevealed by remember(storyId) { mutableStateOf(false) }
    var isScratching by remember(storyId) { mutableStateOf(false) }
    var didPauseForScratch by remember(storyId) { mutableStateOf(false) }
    var showHint by remember(storyId) { mutableStateOf(false) }
    var canvasSize by remember(storyId) { mutableStateOf(IntOffset.Zero) }
    val density = LocalDensity.current
    val reduceMotion = MotionPolicy.reduceMotion
    // ≡ animateHint pulse (.easeInOut 0.72 repeatForever); reduceMotion → extremo “on”
    val hintPulseRaw by rememberInfiniteTransition(label = "revealHint").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(720, easing = FastOutSlowInEasing),
            RepeatMode.Reverse,
        ),
        label = "revealHintPulse",
    )
    val hintT = if (reduceMotion || !showHint) 1f else hintPulseRaw

    fun resumeStoryIfNeeded() {
        if (didPauseForScratch && !isRevealed) {
            didPauseForScratch = false
            onResumeStory?.invoke()
        }
    }

    fun hideHintIfNeeded() {
        if (!showHint) return
        showHint = false
    }

    fun completeReveal() {
        if (isRevealed) return
        isScratching = false
        isRevealed = true
        showHint = false
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
        if (isRevealed) return@LaunchedEffect
        showHint = true
        delay(3_800)
        if (points.isEmpty() && !isRevealed) showHint = false
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
        modifier = modifier
            .clip(RoundedCornerShape(storyViewerCanvasCornerRadius))
            .storyDeckInteractionExclusion(
                id = deckExclusionZoneId,
                gate = gestureGate,
                intents = setOf(
                    StoryGestureIntent.DECK_SWIPE,
                    StoryGestureIntent.STORY_NAVIGATION_TAP,
                    StoryGestureIntent.HOLD_PAUSE,
                    StoryGestureIntent.REPLY_SWIPE,
                    StoryGestureIntent.REVEAL_SCRATCH,
                ),
                suppressionScope = StoryGestureSuppressionScope.SUPPRESS_VIEWER_GESTURES,
                horizontalInsetFraction = StoryGestureCoordinator.REVEAL_SIDE_PASSTHROUGH_FRACTION,
                enabled = reportsDeckInteractionExclusion,
            ),
    ) {
        val scratchWidth = with(density) { 35.dp.toPx() }
        Box(Modifier.fillMaxSize()) {
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
                // ≡ revealHint (momentsChromeGlass Capsule + hand.draw)
                AnimatedVisibility(
                    visible = showHint,
                    enter = fadeIn(tween(180)),
                    exit = fadeOut(tween(220)),
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 140.dp),
                ) {
                    RevealViewerHint(progress = hintT)
                }
            }
            RevealScratchPanOverlay(
                isEnabled = !isRevealed,
                onBegan = {
                    hideHintIfNeeded()
                    if (isScratching) return@RevealScratchPanOverlay
                    isScratching = true
                    gestureGate?.setSuppressionScope(
                        StoryGestureSuppressionScope.SUPPRESS_VIEWER_GESTURES,
                        suppressionSourceId,
                    )
                    if (!didPauseForScratch) {
                        didPauseForScratch = true
                        onPauseStory?.invoke()
                    }
                },
                onPoint = { recordPoint(it) },
                onEnded = {
                    if (!isScratching) return@RevealScratchPanOverlay
                    isScratching = false
                    gestureGate?.clearSuppression(suppressionSourceId)
                    resumeStoryIfNeeded()
                },
            )
        }
    }
}

/** ≡ `revealHint` iOS: icono + texto en cápsula chrome glass. */
@Composable
private fun RevealViewerHint(
    progress: Float,
    modifier: Modifier = Modifier,
) {
    val t = progress.coerceIn(0f, 1f)
    val rotation = lerp(6f, -8f, t)
    val offsetX = lerp(-5f, 6f, t)
    val offsetY = lerp(-2f, 2f, t)
    val scale = lerp(0.985f, 1.03f, t)
    val alpha = lerp(0.82f, 1f, t)
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .momentsChromeGlass(RoundedCornerShape(percent = 50), interactive = false)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        val chromeFg = MomentsChromeGlass.contentColor(isSystemInDarkTheme())
        Icon(
            imageVector = Icons.Filled.PanTool,
            contentDescription = null,
            tint = chromeFg.copy(alpha = 0.96f),
            modifier = Modifier
                .size(13.dp)
                .graphicsLayer { rotationZ = rotation }
                .offset(x = offsetX.dp, y = offsetY.dp),
        )
        Text(
            text = stringResource(R.string.reveal_viewer_hint),
            color = chromeFg.copy(alpha = 0.96f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Port del fondo y patrones de `RevealSurfaceView` (+ reduceMotion / tiempo real). */
@Composable
fun RevealSurfaceView(
    type: String?,
    pattern: String?,
    primaryColor: String?,
    secondaryColor: String?,
    effectColor: String?,
    modifier: Modifier = Modifier,
    effectsActive: Boolean = true,
) {
    val primary = Color.fromHex(primaryColor ?: "#000000")
    val secondary = Color.fromHex(secondaryColor ?: "#000000")
    val effect = when {
        pattern == "holographic" -> Color.fromHex(primaryColor ?: "#C8C8C8")
        !effectColor.isNullOrBlank() -> Color.fromHex(effectColor)
        !secondaryColor.isNullOrBlank() && !secondaryColor.equals(primaryColor, ignoreCase = true) -> secondary
        else -> primary.revealContrastingEffectColor()
    }
    val holographicAccent = Color.fromHex(effectColor ?: secondaryColor ?: "#C8C8C8")
    val resolvedPattern = pattern?.takeIf { it != "none" }
    val showLegacyDither = (pattern.isNullOrBlank() || pattern == "none") &&
        (type == null || type == "scratch" || type == "none")
    val reduceMotion = MotionPolicy.reduceMotion
    val animateEffects = effectsActive && !reduceMotion

    // Reloj leído DENTRO del DrawScope (snapshot) → invalida Canvas cada frame.
    // Antes: Float capturado en composición + `modifier` reutilizado en Box/Canvas → patrones
    // estáticos o superficie vacía.
    val timeState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(animateEffects, resolvedPattern) {
        if (!animateEffects) return@LaunchedEffect
        while (true) {
            withFrameNanos { nanos ->
                timeState.floatValue = nanos / 1_000_000_000f
            }
        }
    }

    // Nunca reutilizar el mismo Modifier en dos nodos (regla Compose).
    Box(modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize()) {
            if (type == "gradient") {
                drawRect(Brush.linearGradient(listOf(primary, secondary)))
            } else {
                drawRect(primary)
            }

            val clock = timeState.floatValue.toDouble()
            val patternToDraw = when {
                !animateEffects && resolvedPattern in setOf(
                    "grid", "matrix", "scanlines", "waves", "holographic",
                ) -> "lines"
                !animateEffects && resolvedPattern == "noise" -> null
                !animateEffects && resolvedPattern == "static" -> "staticReduced"
                resolvedPattern == "holographic" && animateEffects -> null // capa aparte
                else -> resolvedPattern
            }

            when (patternToDraw) {
                "dots" -> Unit // StickerDitherPattern debajo
                "lines" -> drawRevealLines(effect)
                "grid" -> drawRevealGrid(effect, clock)
                "noise" -> drawRevealNoise(effect, clock)
                "static" -> drawRevealStatic(effect, clock)
                "staticReduced" -> drawRect(Color.Black.copy(alpha = 0.08f))
                "scanlines" -> drawRevealScanlines(effect, clock)
                "waves" -> drawRevealWaves(effect, clock)
                "matrix" -> drawRevealMatrix(effect, clock)
            }
        }
        if (resolvedPattern == "dots" || showLegacyDither) {
            StickerDitherPattern(
                color = if (showLegacyDither) Color.White.copy(alpha = 0.7f) else effect,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (resolvedPattern == "holographic" && animateEffects) {
            RevealHolographicPattern(
                color = effect,
                accentColor = holographicAccent,
                modifier = Modifier.fillMaxSize(),
            )
        } else if (resolvedPattern == "holographic" && !animateEffects) {
            Canvas(Modifier.fillMaxSize()) { drawRevealLines(effect) }
        }
    }
}

/** ≡ RevealLinesPattern */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRevealLines(color: Color) {
    val spacing = 15.dp.toPx()
    val step = spacing.toInt().coerceAtLeast(1)
    for (x in -size.height.toInt()..size.width.toInt() step step) {
        drawLine(
            color.copy(alpha = 0.4f),
            Offset(x.toFloat(), 0f),
            Offset(x + size.height, size.height),
            1.dp.toPx(),
        )
    }
}

/** ≡ RevealGridPattern con tiempo real */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRevealGrid(color: Color, time: Double) {
    val spacing = 30.dp.toPx()
    var x = 0f
    while (x < size.width) {
        drawLine(color.copy(alpha = 0.3f), Offset(x, 0f), Offset(x, size.height), 0.5.dp.toPx())
        x += spacing
    }
    var y = 0f
    while (y < size.height) {
        drawLine(color.copy(alpha = 0.3f), Offset(0f, y), Offset(size.width, y), 0.5.dp.toPx())
        y += spacing
    }
    val h = size.height.coerceAtLeast(1f)
    val scanY = ((time * 60.0) % h.toDouble()).toFloat()
    drawRect(color.copy(alpha = 0.6f), topLeft = Offset(0f, scanY), size = Size(size.width, 2.dp.toPx()))
    var gx = 0f
    while (gx < size.width + spacing) {
        var gy = 0f
        while (gy < size.height + spacing) {
            drawCircle(color.copy(alpha = 0.5f), radius = 1.dp.toPx(), center = Offset(gx, gy))
            gy += spacing
        }
        gx += spacing
    }
}

/** ≡ RevealNoisePattern + SeededRandom(seed: 42) */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRevealNoise(color: Color, time: Double) {
    val rng = SeededRandom(42)
    val particleCount = MotionPolicy.revealParticleCount(size.width, size.height)
    for (index in 0 until particleCount) {
        val baseX = rng.next().toFloat() * size.width
        val baseY = rng.next().toFloat() * size.height
        val speedX = rng.next() * 3.5 + 1.5
        val speedY = rng.next() * 4.0 + 2.0
        val driftPhase = rng.next() * Math.PI * 2
        val offsetX = sin(time * 0.25 * speedX + driftPhase) * 12.0
        val offsetY = cos(time * 0.18 * speedY + driftPhase) * 15.0
        var x = ((baseX + offsetX) % size.width).toFloat()
        var y = ((baseY + offsetY) % size.height).toFloat()
        if (x < 0f) x += size.width
        if (y < 0f) y += size.height
        val dotSize = (rng.next() * 2.2 + 1.0).toFloat()
        val shimmer = 0.4 + sin(time * 0.85 * speedX + driftPhase) * 0.4
        val opacity = ((0.35 + rng.next() * 0.45) * shimmer).toFloat().coerceIn(0f, 1f)
        val tone = if (index % 7 == 0) color.copy(alpha = opacity * 0.65f) else color.copy(alpha = opacity)
        drawCircle(tone, radius = dotSize / 2f, center = Offset(x, y))
    }
    val area = maxOf(size.width * size.height, 1f)
    val microCount = minOf(maxOf((area / 150).toInt(), 40), 250)
    repeat(microCount) {
        var mx = ((rng.next().toFloat() * size.width + sin(time * 0.15).toFloat()) % size.width)
        var my = ((rng.next().toFloat() * size.height + cos(time * 0.1).toFloat()) % size.height)
        if (mx < 0f) mx += size.width
        if (my < 0f) my += size.height
        drawRect(color.copy(alpha = 0.22f), topLeft = Offset(mx, my), size = Size(0.8.dp.toPx(), 0.8.dp.toPx()))
    }
}

/** ≡ RevealStaticPattern (nieve aleatoria + rolling + viñeta) */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRevealStatic(color: Color, time: Double) {
    val flicker = 0.96 + Math.random() * 0.08
    drawRect(color.copy(alpha = (0.12 * flicker).toFloat()))
    val snowCount = (size.width * size.height / 120f).toInt().coerceAtLeast(0)
    repeat(snowCount) {
        val dotW = (2.0 + Math.random() * 2.0).toFloat()
        val dotH = (1.0 + Math.random()).toFloat()
        val x = (Math.random() * size.width).toFloat()
        val y = (Math.random() * size.height).toFloat()
        val rand = Math.random()
        val tone = when {
            rand > 0.6 -> Color.Black
            rand > 0.2 -> Color.White
            else -> Color.Gray
        }
        drawRect(
            tone.copy(alpha = (0.1 + Math.random() * 0.6).toFloat()),
            topLeft = Offset(x, y),
            size = Size(dotW, dotH),
        )
    }
    val rollSpan = size.height + 1200f
    val rollY = ((time * 120.0) % rollSpan.toDouble()).toFloat() - 600f
    drawRect(Color.Black.copy(alpha = 0.2f), topLeft = Offset(0f, rollY), size = Size(size.width, 1.5.dp.toPx()))
    drawRect(
        Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.1f)),
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.width * 0.8f,
        ),
    )
}

/** ≡ RevealScanlinesPattern */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRevealScanlines(color: Color, time: Double) {
    val spacing = 8.dp.toPx()
    val offset = ((time * 20.0) % spacing.toDouble()).toFloat()
    var y = -spacing
    while (y < size.height + spacing) {
        drawRect(color.copy(alpha = 0.3f), topLeft = Offset(0f, y + offset), size = Size(size.width, 2.5.dp.toPx()))
        y += spacing
    }
    val interferenceY = ((time * 40.0) % (size.height + 400.0)).toFloat() - 200f
    drawRect(color.copy(alpha = 0.05f), topLeft = Offset(0f, interferenceY), size = Size(size.width, 60.dp.toPx()))
}

/** ≡ RevealWavesPattern */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRevealWaves(color: Color, time: Double) {
    val spacing = 30.dp.toPx()
    var y = -40f
    while (y < size.height + 40f) {
        val wave = Path().apply {
            moveTo(0f, y)
            var x = 0f
            while (x <= size.width + 20f) {
                val relativeX = x / 20.0
                val sine = sin(relativeX + time * 2.5) * 8.0
                lineTo(x, (y + sine).toFloat())
                x += 10f
            }
        }
        drawPath(wave, color.copy(alpha = 0.4f), style = Stroke(width = 2.5.dp.toPx()))
        y += spacing
    }
}

/** ≡ RevealMatrixPattern */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawRevealMatrix(color: Color, time: Double) {
    val columns = (size.width / 20f).toInt()
    for (i in 0 until columns) {
        val x = i * 20f + 10f
        val speed = (sin(i * 0.5) + 2.0) * 80.0
        val yOffset = ((time * speed) % (size.height + 200.0)).toFloat() - 100f
        for (segment in 0 until 12) {
            val segmentY = yOffset - segment * 15f
            if (segmentY > 0f && segmentY < size.height) {
                val opacity = (1.0 - segment / 12.0).toFloat()
                drawRect(
                    color.copy(alpha = opacity * 0.6f),
                    topLeft = Offset(x - 4f, segmentY),
                    size = Size(8f, 12f),
                )
                if (segment == 0) {
                    drawRect(
                        Color.White.copy(alpha = 0.4f),
                        topLeft = Offset(x - 4f, segmentY),
                        size = Size(8f, 12f),
                    )
                }
            }
        }
    }
}

/** Port de `SeededRandom` en StoryInteractiveStickers.swift. */
private class SeededRandom(seed: Int) {
    private var state: ULong = abs(seed).toULong()

    fun next(): Double {
        state += 0x9E3779B97F4A7C15uL
        var z = state
        z = (z xor (z shr 30)) * 0xBF58476D1CE4E5B9uL
        z = (z xor (z shr 27)) * 0x94D049BB133111EBuL
        return (z xor (z shr 31)).toDouble() / ULong.MAX_VALUE.toDouble()
    }
}


/** ≡ `RevealHolographicPattern` iOS — fondo plateado + ola HSV + glitter/rays animados. */
@Composable
private fun RevealHolographicPattern(
    color: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    if (MotionPolicy.reduceMotion) {
        Canvas(modifier) { drawRevealLines(color) }
        return
    }
    val context = LocalContext.current
    val sensorManager = remember(context) {
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    }
    val rotationVector = remember(sensorManager) { sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var pitch by remember { mutableFloatStateOf(0f) }
    var roll by remember { mutableFloatStateOf(0f) }
    var rotationRate by remember { mutableFloatStateOf(0f) }
    val timeState = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { nanos ->
                timeState.floatValue = nanos / 1_000_000_000f
            }
        }
    }

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

    fun hueOf(c: Color): Float {
        val hsv = FloatArray(3)
        android.graphics.Color.RGBToHSV(
            (c.red * 255).toInt().coerceIn(0, 255),
            (c.green * 255).toInt().coerceIn(0, 255),
            (c.blue * 255).toInt().coerceIn(0, 255),
            hsv,
        )
        return hsv[0] / 360f
    }
    val baseHue = hueOf(color)
    val accentHue = hueOf(accentColor)

    BoxWithConstraints(modifier) {
        val isPreview = maxWidth < 150.dp
        val blurRadius = if (isPreview) 8.dp else 18.dp

        Box(Modifier.fillMaxSize()) {
            Canvas(Modifier.fillMaxSize()) {
                drawRect(
                    Brush.linearGradient(
                        listOf(Color(0xFFD1D1D1), Color(0xFFB3B3B3), Color(0xFFC7C7C7)),
                    ),
                )
            }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .blur(blurRadius),
            ) {
                val t = timeState.floatValue
                val cell = if (isPreview) 10.dp.toPx() else 20.dp.toPx()
                val cols = (size.width / cell).toInt() + 2
                val rows = (size.height / cell).toInt() + 2
                val tiltHue = roll / Math.PI.toFloat() * 0.4f + pitch / (Math.PI.toFloat() / 2f) * 0.2f
                val waveT = t * 0.4f
                repeat(cols) { column ->
                    repeat(rows) { row ->
                        val posX = column.toFloat() / cols
                        val posY = row.toFloat() / rows
                        var hue = (posX + posY * 0.5f + tiltHue + accentHue * 0.3f) % 1f
                        if (hue < 0f) hue += 1f
                        val saturation = (
                            0.5f + 0.4f * sin(
                                posX * Math.PI.toFloat() * 3f +
                                    posY * Math.PI.toFloat() * 2f +
                                    waveT,
                            )
                            ).coerceIn(0f, 1f)
                        drawRect(
                            Color.hsv(hue * 360f, saturation, 0.95f, 0.5f),
                            topLeft = Offset(column * cell, row * cell),
                            size = Size(cell + 1f, cell + 1f),
                        )
                    }
                }
            }
            Canvas(Modifier.fillMaxSize()) {
                val t = timeState.floatValue
                val count = if (isPreview) 1000 else 5000
                val rng = SeededRandom(77)
                val tiltHue = roll / Math.PI.toFloat() * 0.4f + pitch / (Math.PI.toFloat() / 2f) * 0.2f
                val motionIntensity = ((rotationRate - 0.1f) / 1.5f).coerceIn(0f, 1f)
                val effectiveMotion = if (rotationVector == null || isPreview) {
                    (0.35f + 0.25f * abs(sin(t.toDouble() * 1.2)).toFloat()).coerceIn(0f, 1f)
                } else {
                    maxOf(motionIntensity, 0.2f + 0.15f * abs(sin(t.toDouble() * 1.5)).toFloat())
                }
                val glintAlpha = effectiveMotion * 0.95f
                repeat(count) { i ->
                    val x = rng.next().toFloat() * size.width
                    val y = rng.next().toFloat() * size.height
                    val dotSizePx = (rng.next() * (if (isPreview) 1.0 else 1.4) + 0.4).toFloat()
                    val phase = rng.next()
                    var hue = ((baseHue + tiltHue * 0.35f + phase * 0.15).toFloat()) % 1f
                    if (hue < 0f) hue += 1f
                    val isBright = i % 8 == 0
                    val radius = (dotSizePx / 2f).coerceAtLeast(0.4f)
                    if (isBright) {
                        val staticShimmer = abs(sin(t * 2.0 + phase * 10.0)) * 0.3
                        val finalBrightness = (0.7 + staticShimmer + effectiveMotion * 0.3).toFloat()
                            .coerceIn(0f, 1f)
                        drawCircle(
                            Color.hsv(hue * 360f, 0.8f, finalBrightness, 0.95f),
                            radius = radius,
                            center = Offset(x, y),
                        )
                        if (dotSizePx > 1.2f && (glintAlpha > 0.1f || isPreview)) {
                            val gAlpha = if (isPreview) 0.3f else glintAlpha
                            val rayLen = (dotSizePx * (2.5 + effectiveMotion * 4.0)).toFloat()
                            val opacity = (gAlpha * (0.5 + rng.next() * 0.5)).toFloat().coerceIn(0f, 1f)
                            for (arm in 0 until 4) {
                                val armAngle = arm * Math.PI / 2.0 + (t * 0.4) + phase
                                drawLine(
                                    Color.White.copy(alpha = opacity),
                                    Offset(x, y),
                                    Offset(
                                        (x + cos(armAngle) * rayLen).toFloat(),
                                        (y + sin(armAngle) * rayLen).toFloat(),
                                    ),
                                    strokeWidth = 0.4f,
                                )
                            }
                        }
                    } else {
                        drawCircle(
                            Color.hsv(hue * 360f, 0.4f, 0.9f, 0.8f),
                            radius = radius,
                            center = Offset(x, y),
                        )
                    }
                }
            }
        }
    }
}
