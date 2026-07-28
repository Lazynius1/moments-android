package com.moments.android.views.messaging.components

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.messaging.VanishMessageTimer
import com.moments.android.utilities.HapticManager
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.shared.MomentsModalSheet
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/**
 * Port de `ChatVanishModeViews.swift` — métricas del pull-to-vanish, overlay/anillo,
 * notices de timeline, sheet de timer e indicadores de inbox.
 *
 * Overlay UIKit (`ChatVanishPullOverlayView`) → Compose [ChatVanishPullOverlay].
 * Timer sheet: iOS `.presentationDetents([.medium])` → [MomentsModalSheet] `largeOnly = false`
 * (medium+large; sin detent medium-only en el host compartido).
 */

data class VanishPullResult(
    val completed: Boolean,
    val progress: Float,
    val effectivePull: Float,
)

object ChatVanishSwipeMetrics {
    const val activationDistance = 64f
    const val maxPull = 200f
    const val completionThreshold = 0.9f
    const val minLiftBeforeUIReveal = 58f
    const val hapticStepPoints = 10f
    const val pullAmplification = 1f
    const val maxConversationLift = 168f
    const val liftCurveExponent = 1.08f
    const val liftCurveScale = 0.48f
    const val revealStartPull = minLiftBeforeUIReveal

    fun rubberBandPull(translation: Float): Float {
        val raw = maxOf(0f, -translation)
        if (raw == 0f) return 0f
        val resistance = 2f
        return maxPull * (1f - exp(-raw / (maxPull / resistance)))
    }

    fun pull(fingerUpward: Float): Float = scaledPull(maxOf(0f, fingerUpward))

    fun conversationLift(fingerUpward: Float): Float {
        if (fingerUpward <= 0f) return 0f
        return min(fingerUpward.pow(liftCurveExponent) * liftCurveScale, maxConversationLift)
    }

    fun shouldRevealVanishUI(lift: Float): Boolean = lift >= minLiftBeforeUIReveal

    fun progress(lift: Float): Float {
        val adjusted = maxOf(0f, lift - minLiftBeforeUIReveal)
        if (adjusted <= 0f) return 0f
        return min(adjusted / activationDistance, 1f)
    }

    fun effectiveLiftForCompletion(lift: Float): Float = maxOf(0f, lift - minLiftBeforeUIReveal)

    fun effectiveFingerPull(upward: Float): Float = effectiveLiftForCompletion(conversationLift(upward))

    fun progressForFingerUpward(upward: Float): Float = progress(conversationLift(upward))

    fun scaledPull(rawOverscroll: Float): Float = maxOf(0f, rawOverscroll) * pullAmplification

    fun effectivePull(pull: Float): Float = maxOf(0f, pull - revealStartPull)

    fun shouldRevealUI(pull: Float): Boolean = shouldRevealVanishUI(conversationLift(pull))

    fun progressForPull(pull: Float): Float = progress(conversationLift(pull))

    fun conversationLiftForPull(pull: Float): Float = conversationLift(pull)

    fun resultForFingerUpward(upward: Float): VanishPullResult {
        val lift = conversationLift(upward)
        val progress = progress(lift)
        return VanishPullResult(
            completed = progress >= completionThreshold,
            progress = progress,
            effectivePull = effectiveLiftForCompletion(lift),
        )
    }
}

/**
 * Estado + NestedScroll del pull-to-vanish.
 *
 * En el último mensaje, el gesto que activa vanish es el pull hacia arriba: el hilo
 * se eleva y deja ver el indicador entre la conversación y el composer.
 */
class ChatVanishPullState(private val density: Float) {
    var rawOverscroll by mutableFloatStateOf(0f)
        private set
    var lift by mutableFloatStateOf(0f)
        private set
    var progress by mutableFloatStateOf(0f)
        private set
    var isDragging by mutableStateOf(false)
        private set
    var didCrossThreshold by mutableStateOf(false)
        private set

    private var lastHapticStep = -1
    private var lastToggleAtMs = 0L
    private val toggleCooldownMs = 2_000L

    val isActive: Boolean get() = lift > 0f || isDragging

    fun reset() {
        rawOverscroll = 0f
        lift = 0f
        progress = 0f
        isDragging = false
        didCrossThreshold = false
        lastHapticStep = -1
    }

    fun applyRawOverscroll(raw: Float, dragging: Boolean, onHapticStep: () -> Unit, onHapticThreshold: () -> Unit) {
        val clamped = raw.coerceAtLeast(0f)
        rawOverscroll = clamped
        // Las métricas se definen en dp, como el diseño de iOS en puntos; el
        // nested-scroll entrega píxeles. Sin esta conversión el gesto se vuelve
        // 2–3× más sensible en pantallas Android densas.
        val nextLiftDp = ChatVanishSwipeMetrics.conversationLift(clamped / density)
        val nextLiftPx = nextLiftDp * density
        lift = nextLiftPx
        progress = ChatVanishSwipeMetrics.progress(nextLiftDp)
        if (dragging && clamped > 0f) isDragging = true
        if (!dragging && clamped <= 0f) isDragging = false
        updateHaptics(nextLiftPx, progress, onHapticStep, onHapticThreshold)
    }

    fun finishRelease(flickVelocityY: Float = 0f): VanishPullResult {
        val completedByThreshold = didCrossThreshold &&
            ChatVanishSwipeMetrics.effectiveLiftForCompletion(lift / density) > 0f
        // ≡ iOS flick: velocity.y < -1400; en reverseLayout el overscroll inferior
        // suele venir con fling positivo — aceptamos ambos signos.
        val completedByFlick = kotlin.math.abs(flickVelocityY) >= 1400f && progress >= 0.5f
        val now = System.currentTimeMillis()
        val withinCooldown = now - lastToggleAtMs < toggleCooldownMs
        val completed = (completedByThreshold || completedByFlick) && !withinCooldown
        val result = VanishPullResult(
            completed = completed,
            progress = progress,
            effectivePull = ChatVanishSwipeMetrics.effectiveLiftForCompletion(lift / density) * density,
        )
        if (completed) lastToggleAtMs = now
        reset()
        return result
    }

    private fun updateHaptics(
        liftValue: Float,
        progressValue: Float,
        onHapticStep: () -> Unit,
        onHapticThreshold: () -> Unit,
    ) {
        val liftDp = liftValue / density
        if (liftDp < 12f) {
            didCrossThreshold = false
            return
        }
        val crossed = progressValue >= ChatVanishSwipeMetrics.completionThreshold
        if (crossed && !didCrossThreshold) {
            didCrossThreshold = true
            onHapticThreshold()
        } else if (!crossed && didCrossThreshold) {
            didCrossThreshold = false
        }
        // Tick desde el inicio del levantamiento, no solo tras revelar el anillo.
        // Una cadencia de 16dp se percibe continua sin saturar el vibrador.
        val step = (liftDp / 16f).toInt()
        if (step != lastHapticStep) {
            lastHapticStep = step
            onHapticStep()
        }
    }
}

@Composable
fun rememberChatVanishPullState(): ChatVanishPullState {
    val density = LocalDensity.current.density
    return remember(density) { ChatVanishPullState(density) }
}

/**
 * NestedScroll ≡ iOS `scrollViewDidScroll` + `bottomOverscroll` + pan activo.
 *
 * Importante:
 * - `OverscrollEffect` nativo consume el sobrante → `overscrollEffect = null` en LazyColumn.
 * - `NestedScroll` conserva coordenadas de pantalla: un gesto hacia arriba tiene
 *   `available.y < 0`, incluso con `reverseLayout`.
 * - El pull se engancha exclusivamente en post-scroll, cuando LazyColumn ya no
 *   puede consumir el delta. Así no compite con el historial.
 * - Una vez enganchado, `onPreScroll` consume el gesto (≡ iOS clampa `contentOffset` a maxY)
 *   para que el pull pueda crecer sin que la lista “pelee” y resetee.
 */
@Composable
fun rememberVanishPullNestedScrollConnection(
    state: ChatVanishPullState,
    listState: LazyListState,
    enabled: () -> Boolean,
    onReleased: (VanishPullResult) -> Unit,
): NestedScrollConnection {
    val view = LocalView.current
    val enabledState = rememberUpdatedState(enabled)
    val onReleasedState = rememberUpdatedState(onReleased)
    return remember(state, listState) {
        object : NestedScrollConnection {
            /**
             * No depender del estado publicado por el controlador aquí. Ese estado se
             * actualiza desde un `snapshotFlow` tras el layout y, durante el primer
             * frame en el borde, todavía podía indicar que la lista no estaba abajo.
             * En ese caso el delta sobrante se perdía antes de que vanish pudiera
             * engancharlo.
             */
            private fun isAtVisualBottom(): Boolean =
                listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset <= 8

            private fun canEngage(): Boolean {
                if (!enabledState.value()) return false
                if (state.rawOverscroll > 0f || state.isDragging) return true
                // `reverseLayout` deja el mensaje reciente en el índice 0. Consultar
                // la posición actual evita que `isStrictlyAtBottom` llegue un frame
                // tarde y no hace falta `canScrollBackward`: para contenidos cortos
                // puede ser false en ambos extremos.
                return isAtVisualBottom()
            }

            private fun applyFingerDelta(deltaY: Float) {
                if (deltaY == 0f && state.rawOverscroll <= 0f) return
                // Coordenadas de Compose: el dedo hacia arriba es Y negativa.
                // Convertimos ese pull en una distancia positiva para vanish.
                val next = (state.rawOverscroll - deltaY).coerceAtLeast(0f)
                if (next <= 0f) {
                    if (state.rawOverscroll > 0f || state.isDragging) state.reset()
                    return
                }
                state.applyRawOverscroll(
                    raw = next,
                    dragging = true,
                    onHapticStep = { HapticManager.shared.vanishPullStep(view) },
                    onHapticThreshold = { HapticManager.shared.vanishPullThresholdReached(view) },
                )
            }

            private fun finish(velocityY: Float): Velocity {
                if (!state.isActive) return Velocity.Zero
                val result = state.finishRelease(flickVelocityY = velocityY)
                onReleasedState.value(result)
                return Velocity(0f, velocityY)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                // ≡ iOS pan activo: el gesto lo maneja vanish, no la lista.
                if (state.rawOverscroll > 0f || state.isDragging) {
                    applyFingerDelta(available.y)
                    return available
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero
                if (state.rawOverscroll > 0f || state.isDragging) {
                    applyFingerDelta(available.y)
                    return Offset(0f, available.y)
                }
                // Solo queda delta aquí cuando la lista alcanzó el borde real.
                // En nestedScroll el gesto hacia arriba es negativo también para
                // LazyColumn(reverseLayout = true).
                if (canEngage() && available.y < 0f) {
                    applyFingerDelta(available.y)
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (!state.isActive) return Velocity.Zero
                return finish(available.y)
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (!state.isActive) return Velocity.Zero
                return finish(available.y)
            }
        }
    }
}

/** Compose ≡ UIKit `ChatVanishPullOverlayView` (posicionado por el host del chat). */
@Composable
fun ChatVanishPullOverlay(
    conversationLift: Float,
    progress: Float,
    isActive: Boolean,
    isDragging: Boolean,
    composerBottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    // En reposo Vanish no participa en el layout: no debe dejar ni hint ni
    // espacio entre el último mensaje y el composer. Solo existe al levantar
    // realmente el hilo durante un drag.
    if (!isDragging || progress <= 0f) return
    val liftDp = with(LocalDensity.current) { conversationLift.toDp() }
    val revealOpacity = (progress / 0.44f).coerceIn(0f, 1f)
    // Centro del hueco entre composer y hilo levantado (≈ centerY from bottom iOS).
    Box(
        modifier
            .fillMaxWidth()
            // `conversationLift` llega en píxeles de graphicsLayer; convertirlo
            // antes de combinarlo con dp evita que el indicador salte al centro.
            .padding(bottom = composerBottomInset + (liftDp * 0.2f))
            .alpha(revealOpacity)
            .scale(0.94f + progress.coerceIn(0f, 1f) * 0.06f),
        contentAlignment = Alignment.Center,
    ) {
        ChatVanishPullRevealContent(progress, isActive, isDragging)
    }
}

@Composable
fun ChatVanishModeProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    val primary = AdaptiveColors(isSystemInDarkTheme()).primary
    Canvas(modifier.size(36.dp)) {
        val strokeWidth = 2.5.dp.toPx()
        drawCircle(primary.copy(alpha = 0.14f), style = Stroke(strokeWidth))
        drawArc(
            color = primary.copy(alpha = 0.88f),
            startAngle = -90f,
            sweepAngle = 360f * progress.coerceIn(0f, 1f),
            useCenter = false,
            style = Stroke(strokeWidth, cap = StrokeCap.Round),
        )
    }
}

@Composable
fun ChatVanishPullRevealLayer(
    pullOffset: Float,
    progress: Float,
    isActive: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val revealOpacity = (ChatVanishSwipeMetrics.effectivePull(pullOffset) / 36f).coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .alpha(revealOpacity)
            .scale(0.94f + progress.coerceIn(0f, 1f) * 0.06f),
        contentAlignment = Alignment.Center,
    ) {
        ChatVanishPullRevealContent(progress, isActive, isDragging)
    }
}

@Composable
private fun ChatVanishPullRevealContent(progress: Float, isActive: Boolean, isDragging: Boolean) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    val hintRes = when {
        isDragging && progress >= ChatVanishSwipeMetrics.completionThreshold && isActive ->
            R.string.chat_vanish_swipe_release_off
        isDragging && progress >= ChatVanishSwipeMetrics.completionThreshold ->
            R.string.chat_vanish_swipe_release
        isActive -> R.string.chat_vanish_swipe_hint_off
        else -> R.string.chat_vanish_swipe_hint
    }
    val accessibilityText = stringResource(
        if (isActive) R.string.chat_vanish_active_accessibility
        else R.string.chat_vanish_inactive_accessibility,
    )
    Column(
        modifier = Modifier.semantics { contentDescription = accessibilityText },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ChatVanishModeProgressIndicator(progress)
        Text(
            text = stringResource(hintRes),
            color = colors.secondary.copy(alpha = 0.62f),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

@Composable
fun ChatVanishSwipeRevealFooter(
    pullOffset: Float,
    progress: Float,
    isActive: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    ChatVanishPullRevealLayer(pullOffset, progress, isActive, isDragging, modifier)
}

@Composable
fun ChatVanishSwipeHint(
    pullOffset: Float,
    progress: Float,
    isActive: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    ChatVanishSwipeRevealFooter(pullOffset, progress, isActive, isDragging, modifier)
}

@Composable
fun ChatDisappearingNoticeRow(
    noticeToken: String,
    actorUserId: String?,
    currentUserId: String,
    otherParticipantName: String,
    onChangeTimer: (() -> Unit)? = null,
    onTurnOn: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isSelfActor = actorUserId.isNullOrBlank() || actorUserId == currentUserId
    val actorName = otherParticipantName.trim().ifBlank { stringResource(R.string.messaging_user_default) }
    val isDark = isSystemInDarkTheme()
    val bodyColor = if (isDark) Color.White.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.42f)
    val actionColor = LocalChatOutgoingBubbleColor.current
    val timer = VanishMessageTimer.parseEnabledNotice(noticeToken)

    val content: @Composable () -> Unit = when {
        timer != null -> {
            {
                val prefix = if (isSelfActor) {
                    stringResource(R.string.chat_vanish_notice_enabled_self)
                } else {
                    stringResource(R.string.chat_vanish_notice_enabled_other, actorName)
                }
                val body = prefix +
                    stringResource(timer.noticeDurationRes) +
                    stringResource(R.string.chat_vanish_notice_enabled_suffix)
                ChatVanishNoticeAction(
                    body = body,
                    action = stringResource(R.string.chat_vanish_notice_change),
                    bodyColor = bodyColor,
                    actionColor = actionColor,
                    onClick = onChangeTimer,
                )
            }
        }
        noticeToken == VanishMessageTimer.DISABLED_NOTICE_TOKEN || noticeToken == "chat.vanish.disabled" -> {
            {
                val body = if (isSelfActor) {
                    stringResource(R.string.chat_vanish_notice_disabled_self)
                } else {
                    stringResource(R.string.chat_vanish_notice_disabled_other, actorName)
                }
                ChatVanishNoticeAction(
                    body = body,
                    action = stringResource(R.string.chat_vanish_notice_turn_on),
                    bodyColor = bodyColor,
                    actionColor = actionColor,
                    onClick = onTurnOn,
                )
            }
        }
        noticeToken == VanishMessageTimer.SCREENSHOT_NOTICE_TOKEN -> {
            { ChatVanishPlainNotice(R.string.chat_vanish_screenshot, bodyColor) }
        }
        noticeToken == VanishMessageTimer.SCREEN_RECORDING_NOTICE_TOKEN -> {
            { ChatVanishPlainNotice(R.string.chat_vanish_screen_recording, bodyColor) }
        }
        noticeToken == "chat.vanish.enabled" -> {
            {
                val prefix = stringResource(R.string.chat_vanish_notice_enabled_self)
                val body = prefix +
                    stringResource(R.string.chat_vanish_duration_24h) +
                    stringResource(R.string.chat_vanish_notice_enabled_suffix)
                ChatVanishNoticeAction(
                    body = body,
                    action = stringResource(R.string.chat_vanish_notice_change),
                    bodyColor = bodyColor,
                    actionColor = actionColor,
                    onClick = onChangeTimer,
                )
            }
        }
        noticeToken.startsWith("chat.vanish.") -> {
            { ChatVanishPlainNotice(noticeToken, bodyColor) }
        }
        else -> {
            { ChatVanishPlainNotice(noticeToken, bodyColor) }
        }
    }
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
private fun ChatVanishNoticeAction(
    body: String,
    action: String,
    bodyColor: Color,
    actionColor: Color,
    onClick: (() -> Unit)?,
) {
    // ≡ iOS Text concatenation: body + " " + action inline (no Row — evita “Change” vertical).
    val annotated = buildAnnotatedString {
        withStyle(SpanStyle(color = bodyColor, fontSize = 10.sp, fontWeight = FontWeight.Normal)) {
            append(body)
            if (!body.endsWith(" ")) append(" ")
        }
        withStyle(SpanStyle(color = actionColor, fontSize = 10.sp, fontWeight = FontWeight.Medium)) {
            append(action)
        }
    }
    Text(
        text = annotated,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
    )
}

@Composable
private fun ChatVanishPlainNotice(@StringRes noticeRes: Int, color: Color) {
    Text(stringResource(noticeRes), color = color, fontSize = 10.sp, textAlign = TextAlign.Center)
}

@Composable
private fun ChatVanishPlainNotice(notice: String, color: Color) {
    Text(notice, color = color, fontSize = 10.sp, textAlign = TextAlign.Center)
}

/**
 * Port de `ChatVanishTimerSheet`.
 * Presentación: `.presentationDetents([.medium])` → [MomentsModalSheet] `largeOnly = false`.
 */
@Composable
fun ChatVanishTimerSheet(
    selectedTimer: VanishMessageTimer,
    onSelect: (VanishMessageTimer?) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    MomentsModalSheet(
        onDismissRequest = onDismiss,
        largeOnly = false,
        showDragHandle = true,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chat_vanish_timer_sheet_title),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.common_done),
                    color = LocalChatOutgoingBubbleColor.current,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(vertical = 8.dp, horizontal = 4.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            VanishMessageTimer.entries.forEach { timer ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(timer)
                            onDismiss()
                        }
                        .padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(timer.localizationRes),
                        color = if (isDark) Color.White else Color.Black,
                    )
                    Spacer(Modifier.weight(1f))
                    if (timer == selectedTimer) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = LocalChatOutgoingBubbleColor.current,
                        )
                    }
                }
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelect(null)
                        onDismiss()
                    }
                    .padding(vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.chat_vanish_timer_off), color = Color(0xFFFF3B30))
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun ChatViewOnceInboxIndicator(modifier: Modifier = Modifier) {
    // iOS liquidGlass → círculo sólido (sin material/blur).
    Box(
        modifier
            .size(22.dp)
            .background(Color(0xFF007AFF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.chat_view_once_tap_to_view),
            tint = Color.White,
            modifier = Modifier.size(13.dp),
        )
    }
}

@Composable
fun ChatVanishInboxIndicator(isUnread: Boolean, modifier: Modifier = Modifier) {
    val ringColor = when {
        isUnread -> Color(0xFF007AFF)
        isSystemInDarkTheme() -> Color.White.copy(alpha = 0.42f)
        else -> Color.Black.copy(alpha = 0.32f)
    }
    val accessibilityText = stringResource(R.string.chat_vanish_active_accessibility)
    Canvas(
        modifier
            .size(15.dp)
            .semantics { contentDescription = accessibilityText },
    ) {
        drawCircle(
            ringColor,
            style = Stroke(
                width = 1.5.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(2.2.dp.toPx(), 2.8.dp.toPx()),
                ),
            ),
        )
    }
}

@Composable
fun ChatNoticeTimelineRow(
    noticeKey: String,
    actorUserId: String?,
    currentUserId: String,
    otherParticipantName: String,
    onChangeTimer: (() -> Unit)? = null,
    onTurnOn: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ChatDisappearingNoticeRow(
        noticeToken = noticeKey,
        actorUserId = actorUserId,
        currentUserId = currentUserId,
        otherParticipantName = otherParticipantName,
        onChangeTimer = onChangeTimer,
        onTurnOn = onTurnOn,
        modifier = modifier,
    )
}

private val VanishMessageTimer.localizationRes: Int
    get() = when (this) {
        VanishMessageTimer.ONCE_SEEN -> R.string.chat_vanish_timer_once_seen
        VanishMessageTimer.HOURS_24 -> R.string.chat_vanish_timer_24h
        VanishMessageTimer.DAYS_7 -> R.string.chat_vanish_timer_7d
    }

private val VanishMessageTimer.noticeDurationRes: Int
    get() = when (this) {
        VanishMessageTimer.ONCE_SEEN -> R.string.chat_vanish_duration_once_seen
        VanishMessageTimer.HOURS_24 -> R.string.chat_vanish_duration_24h
        VanishMessageTimer.DAYS_7 -> R.string.chat_vanish_duration_7d
    }
