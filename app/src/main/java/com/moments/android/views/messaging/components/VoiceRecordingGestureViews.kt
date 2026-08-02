package com.moments.android.views.messaging.components

import android.view.accessibility.AccessibilityManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.dp
import com.moments.android.R
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.services.performance.MotionPolicy
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.rememberAdaptiveColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan
import kotlin.random.Random
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Chrome de grabación ≡ Telegram Android (`RecordCircle` + `BlobDrawable` + gestos
 * de `ChatActivityEnterView`), con accent Moments y **sin glass**.
 *
 * Paridad de plataforma (no clonar métricas iOS):
 * - iOS Moments ↔ Telegram iOS
 * - Android Moments ↔ Telegram Android
 *
 * Gestos (SDK Android):
 * - Cancel: slide izquierda; distancia `min(width*0.35, 140dp)`; suelta si alpha &lt; 0.45
 * - Lock: slide arriba ≥ `dp(57)`
 *
 * Importante: `pointerInput` NO debe re-keyearse con `isRecording` — si no, al
 * arrancar la grabación se cancela el gesto y lock/cancel dejan de responder.
 */
enum class VoiceRecordingFinishAction { SEND, CANCEL }

class VoiceRecordingGestureState {
    var cancelDragOffset by mutableFloatStateOf(0f)
    var cancelProgress by mutableFloatStateOf(0f)
    var lockProgress by mutableFloatStateOf(0f)
    var followX by mutableFloatStateOf(0f)
    var followY by mutableFloatStateOf(0f)
}

object VoiceRecordingBlobMetrics {
    // Moments locked-send / aura (ChatInputViews) — iOS `VoiceRecordingBlobMetrics`.
    val surface = 110.dp
    val aura = 176.dp
    val innerAura = 150.dp
    val momentsIcon = 30.dp

    // Overlay de grabación (gesto Telegram-aligned en Android).
    val overlay = 160.dp
    val circleRadius = 41.dp
    val circleRadiusAmplitude = 30.dp
    val icon = 24.dp
    val waveMinRadius = 47.dp
    val waveMaxRadius = 55.dp
    /** ≡ Telegram `setLockTranslation` threshold `dp(57)`. */
    const val lockDistanceDp = 57f
    /** ≡ Telegram `distCanMove` cap. */
    const val cancelDistanceMaxDp = 140f
    const val cancelDistanceWidthFraction = 0.35f
    /** ≡ Telegram release cancel when `alpha < 0.45`. */
    const val cancelReleaseAlpha = 0.45f
    const val followOvershootDp = 28f
    const val directionThresholdDp = 8f
    const val holdMillis = 150L

    val auraScaleMinimum: Float get() = surface.value / aura.value
    val innerAuraScaleMinimum: Float get() = surface.value / innerAura.value
}

private enum class VoiceRecordingGesturePhase {
    Idle,
    Pressing,
    RecordingHeld,
    RecordingLocked,
}

/** Port fiel de `org.telegram.ui.Components.BlobDrawable`. */
private class TelegramBlobDrawable(
    private val pointsCount: Int,
    seed: Long = System.nanoTime(),
) {
    var minRadius = 0f
    var maxRadius = 0f
    private val random = Random(seed)
    private val radius = FloatArray(pointsCount)
    private val angle = FloatArray(pointsCount)
    private val radiusNext = FloatArray(pointsCount)
    private val angleNext = FloatArray(pointsCount)
    private val progress = FloatArray(pointsCount)
    private val speed = FloatArray(pointsCount)
    private val path = Path()
    private val lBase = ((4.0 / 3.0) * tan(PI / (2 * pointsCount))).toFloat()
    var amplitude = 0f
        private set
    private var animateToAmplitude = 0f
    private var animateAmplitudeDiff = 0f

    init {
        for (i in 0 until pointsCount) {
            generateBlob(radius, angle, i)
            generateBlob(radiusNext, angleNext, i)
        }
    }

    fun generateBlob() {
        for (i in 0 until pointsCount) {
            generateBlob(radius, angle, i)
            generateBlob(radiusNext, angleNext, i)
            progress[i] = 0f
        }
    }

    private fun generateBlob(radiusArr: FloatArray, angleArr: FloatArray, i: Int) {
        val angleDif = 360f / pointsCount * 0.05f
        val radDif = maxRadius - minRadius
        radiusArr[i] = minRadius + abs(random.nextInt() % 100) / 100f * radDif
        angleArr[i] = 360f / pointsCount * i + abs(random.nextInt() % 100) / 100f * angleDif
        speed[i] = (0.017 + 0.003 * (abs(random.nextInt() % 100) / 100.0)).toFloat()
    }

    fun setValue(value: Float, isBig: Boolean) {
        animateToAmplitude = value.coerceIn(0f, 1f)
        val animationSpeed = if (isBig) 0.35f else 0.55f
        animateAmplitudeDiff = if (animateToAmplitude > amplitude) {
            (animateToAmplitude - amplitude) / (100f + 300f * animationSpeed)
        } else {
            (animateToAmplitude - amplitude) / (100f + 500f * animationSpeed)
        }
    }

    fun updateAmplitude(dtMs: Float) {
        if (animateToAmplitude == amplitude) return
        amplitude += animateAmplitudeDiff * dtMs
        if (animateAmplitudeDiff > 0 && amplitude > animateToAmplitude) amplitude = animateToAmplitude
        if (animateAmplitudeDiff < 0 && amplitude < animateToAmplitude) amplitude = animateToAmplitude
    }

    fun update(amp: Float, speedScale: Float) {
        for (i in 0 until pointsCount) {
            progress[i] += (speed[i] * 0.8f) + amp * speed[i] * 8.2f * speedScale
            if (progress[i] >= 1f) {
                progress[i] = 0f
                radius[i] = radiusNext[i]
                angle[i] = angleNext[i]
                generateBlob(radiusNext, angleNext, i)
            }
        }
    }

    fun buildPath(cx: Float, cy: Float): Path {
        path.reset()
        for (i in 0 until pointsCount) {
            val nextIndex = if (i + 1 < pointsCount) i + 1 else 0
            val p = progress[i]
            val pn = progress[nextIndex]
            val r1 = radius[i] * (1f - p) + radiusNext[i] * p
            val r2 = radius[nextIndex] * (1f - pn) + radiusNext[nextIndex] * pn
            val a1 = angle[i] * (1f - p) + angleNext[i] * p
            val a2 = angle[nextIndex] * (1f - pn) + angleNext[nextIndex] * pn
            val l = lBase * (min(r1, r2) + (max(r1, r2) - min(r1, r2)) / 2f)
            val start = rotate(cx, cy - r1, a1, cx, cy)
            val startCtrl = rotate(cx + l, cy - r1, a1, cx, cy)
            val end = rotate(cx, cy - r2, a2, cx, cy)
            val endCtrl = rotate(cx - l, cy - r2, a2, cx, cy)
            if (i == 0) path.moveTo(start.x, start.y)
            path.cubicTo(startCtrl.x, startCtrl.y, endCtrl.x, endCtrl.y, end.x, end.y)
        }
        path.close()
        return path
    }

    private fun rotate(x: Float, y: Float, degrees: Float, cx: Float, cy: Float): Offset {
        val rad = degrees * (PI.toFloat() / 180f)
        val cosA = cos(rad)
        val sinA = sin(rad)
        val dx = x - cx
        val dy = y - cy
        return Offset(cx + dx * cosA - dy * sinA, cy + dx * sinA + dy * cosA)
    }
}

/**
 * Forma ≡ Telegram `RecordCircle` (BlobDrawable N=11/12).
 * Colores ≡ Moments blob (userAccent + aurora sin 007AFF).
 */
object MomentsVoiceBlobColors {
    val core = Color(0xFF3F6F8F)
    val waveOuter = Color(0xFFAF52DE)
    val waveInner = Color(0xFF02C39A)
    val wavePeak = Color(0xFFFF375F)
}

@Composable
fun VoiceRecordingTelegramRecordCircle(
    audioPower: Float,
    @Suppress("UNUSED_PARAMETER") accent: Color = MomentsVoiceBlobColors.core,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val reduceMotion = MotionPolicy.reduceMotion
    val waveMin = with(density) { VoiceRecordingBlobMetrics.waveMinRadius.toPx() }
    val waveMax = with(density) { VoiceRecordingBlobMetrics.waveMaxRadius.toPx() }
    val baseCircle = with(density) { VoiceRecordingBlobMetrics.circleRadius.toPx() }
    val ampCircle = with(density) { VoiceRecordingBlobMetrics.circleRadiusAmplitude.toPx() }

    val tinyWave = remember {
        TelegramBlobDrawable(11, 11L).also {
            it.minRadius = waveMin
            it.maxRadius = waveMax
            it.generateBlob()
        }
    }
    val bigWave = remember {
        TelegramBlobDrawable(12, 12L).also {
            it.minRadius = waveMin
            it.maxRadius = waveMax
            it.generateBlob()
        }
    }
    tinyWave.minRadius = waveMin
    tinyWave.maxRadius = waveMax
    bigWave.minRadius = waveMin
    bigWave.maxRadius = waveMax

    var amplitude by remember { mutableFloatStateOf(0f) }
    var wavesEnter by remember { mutableFloatStateOf(0f) }
    var frameTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(audioPower) {
        val target = audioPower.coerceIn(0f, 1f)
        bigWave.setValue(target, isBig = true)
        tinyWave.setValue(target, isBig = false)
    }

    LaunchedEffect(reduceMotion) {
        var lastNanos = 0L
        while (isActive) {
            withFrameNanos { nanos ->
                val dtMs = if (lastNanos == 0L) 16f else ((nanos - lastNanos) / 1_000_000f).coerceIn(1f, 50f)
                lastNanos = nanos
                if (!reduceMotion) {
                    tinyWave.updateAmplitude(dtMs)
                    bigWave.updateAmplitude(dtMs)
                    tinyWave.update(tinyWave.amplitude, 1f)
                    bigWave.update(bigWave.amplitude, 1f)
                    amplitude += (bigWave.amplitude - amplitude) * (dtMs / 350f).coerceIn(0.02f, 0.35f)
                    wavesEnter = (wavesEnter + dtMs / 350f).coerceAtMost(1f)
                } else {
                    amplitude = audioPower.coerceIn(0f, 1f)
                    wavesEnter = 1f
                }
                frameTick++
            }
        }
    }

    Box(modifier.size(VoiceRecordingBlobMetrics.overlay), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            frameTick.let { }
            val cx = size.width / 2f
            val cy = size.height / 2f
            val enter = wavesEnter.coerceIn(0f, 1f)
            val bigScale = (0.807f + amplitude * 0.12f) * enter
            val tinyScale = (0.704f + amplitude * 0.10f) * enter
            val t = amplitude.coerceIn(0f, 1f)
            fun lerp(a: Color, b: Color, x: Float): Color = Color(
                red = a.red + (b.red - a.red) * x,
                green = a.green + (b.green - a.green) * x,
                blue = a.blue + (b.blue - a.blue) * x,
                alpha = a.alpha + (b.alpha - a.alpha) * x,
            )
            val outerTint = lerp(MomentsVoiceBlobColors.waveOuter, MomentsVoiceBlobColors.wavePeak, t * 0.45f)
            val innerTint = lerp(MomentsVoiceBlobColors.waveInner, MomentsVoiceBlobColors.waveOuter, t * 0.35f)

            scale(bigScale, Offset(cx, cy)) {
                drawPath(bigWave.buildPath(cx, cy), color = outerTint.copy(alpha = 0.28f + t * 0.32f))
            }
            scale(tinyScale, Offset(cx, cy)) {
                drawPath(tinyWave.buildPath(cx, cy), color = innerTint.copy(alpha = 0.34f + t * 0.28f))
            }
            drawCircle(
                color = MomentsVoiceBlobColors.core,
                radius = baseCircle + ampCircle * amplitude,
                center = Offset(cx, cy),
            )
        }
        Icon(
            Icons.Default.Mic,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(VoiceRecordingBlobMetrics.icon),
        )
    }
}

@Composable
fun VoiceRecordingGestureButton(
    tint: Color,
    isRecording: Boolean,
    activeInteractionId: String?,
    isLocked: Boolean,
    gestureState: VoiceRecordingGestureState,
    glassInteractive: Boolean,
    audioPower: Float,
    onStart: (String, Boolean) -> Unit,
    onFinish: (String, VoiceRecordingFinishAction) -> Unit,
    onLockChanged: (Boolean) -> Unit,
    onAnchorBoundsChanged: (IntRect) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val touchExploration = remember(context) {
        context.getSystemService(AccessibilityManager::class.java)?.isTouchExplorationEnabled == true
    }
    val reduceMotion = MotionPolicy.reduceMotion

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val cancelDistancePx = min(
        screenWidthPx * VoiceRecordingBlobMetrics.cancelDistanceWidthFraction,
        with(density) { VoiceRecordingBlobMetrics.cancelDistanceMaxDp.dp.toPx() },
    )
    val lockDistancePx = with(density) { VoiceRecordingBlobMetrics.lockDistanceDp.dp.toPx() }
    val followOvershootPx = with(density) { VoiceRecordingBlobMetrics.followOvershootDp.dp.toPx() }
    val directionThresholdPx = with(density) { VoiceRecordingBlobMetrics.directionThresholdDp.dp.toPx() }

    var phase by remember { mutableStateOf(VoiceRecordingGesturePhase.Idle) }
    var interactionId by remember { mutableStateOf<String?>(null) }
    var lastLockTick by remember { mutableIntStateOf(0) }
    var blobFollowX by remember { mutableFloatStateOf(0f) }
    var blobFollowY by remember { mutableFloatStateOf(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }

    val followXAnim = remember { Animatable(0f) }
    val followYAnim = remember { Animatable(0f) }
    val springSpec = spring<Float>(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow)

    // Estado externo vía rememberUpdatedState — NO re-keyear pointerInput.
    val isRecordingState = rememberUpdatedState(isRecording)
    val isLockedState = rememberUpdatedState(isLocked)
    val onStartState = rememberUpdatedState(onStart)
    val onFinishState = rememberUpdatedState(onFinish)
    val onLockChangedState = rememberUpdatedState(onLockChanged)

    val a11yLabel = stringResource(R.string.chat_voice_record_accessibility)
    val a11yHint = stringResource(R.string.chat_voice_record_hold_hint)
    val startedAnnouncement = stringResource(R.string.chat_voice_record_started)
    val cancelledAnnouncement = stringResource(R.string.chat_voice_record_cancelled)
    val lockedAnnouncement = stringResource(R.string.chat_voice_record_locked)

    @Suppress("UNUSED_VARIABLE")
    val unusedGlass = glassInteractive
    @Suppress("UNUSED_VARIABLE")
    val unusedPower = audioPower

    fun resetLocalState() {
        holdJob?.cancel()
        holdJob = null
        phase = VoiceRecordingGesturePhase.Idle
        interactionId = null
        lastLockTick = 0
        blobFollowX = 0f
        blobFollowY = 0f
        gestureState.cancelDragOffset = 0f
        gestureState.cancelProgress = 0f
        gestureState.lockProgress = 0f
        gestureState.followX = 0f
        gestureState.followY = 0f
        scope.launch {
            followXAnim.snapTo(0f)
            followYAnim.snapTo(0f)
        }
    }

    fun settleBlobFollow() {
        if (blobFollowX == 0f && blobFollowY == 0f && gestureState.cancelDragOffset == 0f) return
        blobFollowX = 0f
        blobFollowY = 0f
        gestureState.followX = 0f
        gestureState.followY = 0f
        gestureState.cancelDragOffset = 0f
        gestureState.cancelProgress = 0f
        scope.launch {
            if (reduceMotion) {
                followXAnim.snapTo(0f)
                followYAnim.snapTo(0f)
            } else {
                launch { followXAnim.animateTo(0f, springSpec) }
                launch { followYAnim.animateTo(0f, springSpec) }
            }
        }
    }

    fun updateBlobFollow(x: Float, y: Float) {
        if (reduceMotion) return
        val followY = if (y <= 0f) {
            max(-(lockDistancePx + followOvershootPx), y)
        } else {
            min(26f, y * 0.3f)
        }
        val followX = if (x <= 0f) {
            max(-(cancelDistancePx + followOvershootPx), x)
        } else {
            min(26f, x * 0.3f)
        }
        blobFollowX = followX
        blobFollowY = followY
        gestureState.followX = followX
        gestureState.followY = followY
        gestureState.cancelDragOffset = min(0f, x)
        // ≡ Telegram slideToCancelProgress: 1 = idle, 0 = fully cancelled
        // alpha = 1 + dist/distCanMove con dist negativo al slide left
        val slideAlpha = (1f + min(0f, x) / cancelDistancePx).coerceIn(0f, 1f)
        gestureState.cancelProgress = 1f - slideAlpha
        scope.launch {
            launch { followXAnim.animateTo(followX, springSpec) }
            launch { followYAnim.animateTo(followY, springSpec) }
        }
    }

    fun emitLockTickIfNeeded(lockProgress: Float) {
        val tick = min(4, (lockProgress * 4f).toInt())
        if (tick <= lastLockTick) return
        lastLockTick = tick
        HapticManager.shared.selection(view)
    }

    fun commitLock() {
        if (phase != VoiceRecordingGesturePhase.RecordingHeld) return
        phase = VoiceRecordingGesturePhase.RecordingLocked
        onLockChangedState.value(true)
        gestureState.lockProgress = 1f
        settleBlobFollow()
        HapticManager.shared.success(view)
        view.announceForAccessibility(lockedAnnouncement)
    }

    fun commitCancellation(id: String) {
        if (phase != VoiceRecordingGesturePhase.RecordingHeld) return
        HapticManager.shared.warning(view)
        view.announceForAccessibility(cancelledAnnouncement)
        onFinishState.value(id, VoiceRecordingFinishAction.CANCEL)
        resetLocalState()
    }

    fun updateDrag(dx: Float, dy: Float) {
        val horizontal = abs(dx)
        val vertical = abs(dy)
        if (max(horizontal, vertical) < directionThresholdPx &&
            blobFollowX == 0f && blobFollowY == 0f
        ) {
            return
        }
        // ≡ Telegram setLockTranslation: finger up → y decreases → progress
        val lockProgress = (-dy / lockDistancePx).coerceIn(0f, 1f)
        // ≡ Telegram: lock blocked while slideToCancelProgress < 0.7 (cancelProgress > 0.3)
        val slideAlpha = (1f + min(0f, dx) / cancelDistancePx).coerceIn(0f, 1f)
        val cancelProgress = 1f - slideAlpha
        gestureState.lockProgress = lockProgress
        gestureState.cancelProgress = cancelProgress
        if (lockProgress == 0f) lastLockTick = 0
        updateBlobFollow(dx, dy)
        emitLockTickIfNeeded(lockProgress)
        when {
            lockProgress >= 1f && slideAlpha >= 0.7f -> commitLock()
            // Cancel mid-drag when fully slid (alpha ~0)
            slideAlpha <= 0f -> interactionId?.let { commitCancellation(it) }
        }
    }

    fun accessibilityActivate() {
        if (!touchExploration || isRecordingState.value) return
        val id = UUID.randomUUID().toString()
        interactionId = id
        phase = VoiceRecordingGesturePhase.RecordingLocked
        onLockChangedState.value(true)
        HapticManager.shared.lightImpact(view)
        onStartState.value(id, true)
    }

    LaunchedEffect(isRecording, isLocked, activeInteractionId) {
        if (!isRecording && !isLocked && activeInteractionId == null &&
            phase != VoiceRecordingGesturePhase.Pressing
        ) {
            if (phase != VoiceRecordingGesturePhase.Idle) resetLocalState()
        }
    }

    // Reposo: icono Moments sin glass. Blob solo en overlay al grabar.
    Box(
        modifier
            .size(44.dp)
            .semantics { contentDescription = "$a11yLabel. $a11yHint" }
            .onGloballyPositioned { coordinates ->
                val bounds = coordinates.boundsInWindow()
                onAnchorBoundsChanged(
                    IntRect(
                        left = bounds.left.roundToInt(),
                        top = bounds.top.roundToInt(),
                        right = bounds.right.roundToInt(),
                        bottom = bounds.bottom.roundToInt(),
                    ),
                )
            }
            .then(
                if (touchExploration) {
                    Modifier.clickable(onClick = ::accessibilityActivate)
                } else {
                    // Clave ESTABLE: no incluir isRecording/isLocked.
                    Modifier.pointerInput(cancelDistancePx, lockDistancePx) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            if (touchExploration) return@awaitEachGesture
                            if (isRecordingState.value || isLockedState.value) return@awaitEachGesture

                            val id = UUID.randomUUID().toString()
                            interactionId = id
                            phase = VoiceRecordingGesturePhase.Pressing
                            lastLockTick = 0
                            gestureState.lockProgress = 0f
                            gestureState.cancelDragOffset = 0f
                            gestureState.cancelProgress = 0f
                            var latestDx = 0f
                            var latestDy = 0f
                            var finished = false

                            holdJob?.cancel()
                            holdJob = scope.launch {
                                delay(VoiceRecordingBlobMetrics.holdMillis)
                                if (interactionId != id || phase != VoiceRecordingGesturePhase.Pressing) return@launch
                                phase = VoiceRecordingGesturePhase.RecordingHeld
                                HapticManager.shared.lightImpact(view)
                                view.announceForAccessibility(startedAnnouncement)
                                onStartState.value(id, false)
                                if (latestDx != 0f || latestDy != 0f) updateDrag(latestDx, latestDy)
                            }

                            try {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                    latestDx = change.position.x - down.position.x
                                    latestDy = change.position.y - down.position.y
                                    if (phase == VoiceRecordingGesturePhase.RecordingHeld) {
                                        updateDrag(latestDx, latestDy)
                                        if (phase == VoiceRecordingGesturePhase.Idle) {
                                            finished = true
                                            break
                                        }
                                    }
                                    if (!change.pressed) break
                                    change.consume()
                                }
                            } finally {
                                holdJob?.cancel()
                                holdJob = null
                            }

                            if (finished) return@awaitEachGesture

                            when (phase) {
                                VoiceRecordingGesturePhase.Pressing -> resetLocalState()
                                VoiceRecordingGesturePhase.RecordingHeld -> {
                                    // ≡ Telegram UP: cancel if slide alpha < 0.45
                                    val slideAlpha = (1f + min(0f, latestDx) / cancelDistancePx)
                                        .coerceIn(0f, 1f)
                                    if (slideAlpha < VoiceRecordingBlobMetrics.cancelReleaseAlpha) {
                                        commitCancellation(id)
                                    } else {
                                        onFinishState.value(id, VoiceRecordingFinishAction.SEND)
                                        resetLocalState()
                                    }
                                }
                                VoiceRecordingGesturePhase.RecordingLocked -> {
                                    interactionId = null
                                }
                                VoiceRecordingGesturePhase.Idle -> resetLocalState()
                            }
                        }
                    }
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        AttachmentIconView(
            AttachmentIcon.VOICE,
            AttachmentIconPreset.CHAT_VOICE_INPUT,
            tint,
            modifier = Modifier.alpha(if (isRecording || isLocked) 0f else 1f),
        )
    }
}

@Composable
fun VoiceRecordingBlobOverlay(
    anchorBounds: IntRect?,
    audioPower: Float,
    gestureState: VoiceRecordingGestureState,
    isRecording: Boolean = true,
    modifier: Modifier = Modifier,
) {
    if (anchorBounds == null || !isRecording) return
    val density = LocalDensity.current
    val colors = rememberAdaptiveColors()
    val centerX = (anchorBounds.left + anchorBounds.right) / 2
    val centerY = (anchorBounds.top + anchorBounds.bottom) / 2
    val overlayPx = with(density) { VoiceRecordingBlobMetrics.overlay.roundToPx() }
    Box(
        modifier
            .offset {
                IntOffset(
                    centerX - overlayPx / 2 + gestureState.followX.roundToInt(),
                    centerY - overlayPx / 2 + gestureState.followY.roundToInt(),
                )
            }
            .size(VoiceRecordingBlobMetrics.overlay),
        contentAlignment = Alignment.Center,
    ) {
        VoiceRecordingTelegramRecordCircle(
            audioPower = audioPower,
        )
    }
}

/** ≡ iOS `VoiceRecordingReactiveAura` — halo dual reactivo al audioPower. Δ: mesh→radial blobs. */
@Composable
fun VoiceRecordingReactiveAura(
    audioPower: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val isDark = isSystemInDarkTheme()
    val reduceMotion = MotionPolicy.reduceMotion
    val motionMul = if (reduceMotion) 0.18f else 1f
    var fastLevel by remember { mutableFloatStateOf(0f) }
    var slowLevel by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(audioPower) {
        val raw = audioPower.coerceIn(0f, 1f)
        val gated = max(0f, (raw - 0.12f) / 0.88f)
        fastLevel += (gated - fastLevel) * 0.42f
        slowLevel += (max(gated * 0.85f, slowLevel * 0.92f) - slowLevel) * 0.18f
    }

    val innerScale = VoiceRecordingBlobMetrics.innerAuraScaleMinimum +
        fastLevel * motionMul * (1f - VoiceRecordingBlobMetrics.innerAuraScaleMinimum)
    val outerScale = VoiceRecordingBlobMetrics.auraScaleMinimum +
        max(slowLevel, fastLevel * 0.62f) * motionMul * (1f - VoiceRecordingBlobMetrics.auraScaleMinimum)
    val innerOpacity = (if (isDark) 0.34f else 0.28f) + fastLevel * 0.22f
    val outerOpacity = (if (isDark) 0.22f else 0.18f) + slowLevel * 0.18f

    val aurora = listOf(
        Color(0xFF007AFF),
        Color(0xFFAF52DE),
        Color(0xFFFF375F),
        Color(0xFF02C39A),
    )

    Box(
        modifier
            .size(VoiceRecordingBlobMetrics.aura),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(VoiceRecordingBlobMetrics.aura)
                .graphicsLayer {
                    scaleX = outerScale
                    scaleY = outerScale
                    alpha = outerOpacity
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            aurora[0].copy(alpha = 0.55f),
                            aurora[1].copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .size(VoiceRecordingBlobMetrics.innerAura)
                .graphicsLayer {
                    scaleX = innerScale
                    scaleY = innerScale
                    alpha = innerOpacity
                }
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            aurora[3].copy(alpha = 0.6f),
                            aurora[2].copy(alpha = 0.4f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/** ≡ iOS `VoiceRecordingAuroraCircleSurface` — Δ: MeshGradient→radial + chrome fill. */
@Composable
fun VoiceRecordingAuroraCircleSurface(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    val auroraOpacity = if (isDark) 0.5f else 0.42f
    val stroke = Color.White.copy(alpha = if (isDark) 0.22f else 0.34f)
    Box(
        modifier
            .size(VoiceRecordingBlobMetrics.surface)
            .clip(CircleShape)
            .momentsChromeGlass(CircleShape, interactive = true)
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF007AFF).copy(alpha = 0.35f * auroraOpacity),
                        Color(0xFFAF52DE).copy(alpha = 0.28f * auroraOpacity),
                        Color(0xFFFF375F).copy(alpha = 0.22f * auroraOpacity),
                        Color.Transparent,
                    ),
                ),
                CircleShape,
            )
            .border(0.75.dp, stroke, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
