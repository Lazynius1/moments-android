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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moments.android.R
import com.moments.android.services.messaging.VanishMessageTimer
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.pow

/** Port de `Views/Messaging/Components/ChatVanishModeViews.swift`. */
data class VanishPullResult(
    val completed: Boolean,
    val progress: Float,
    val effectivePull: Float,
)

object ChatVanishSwipeMetrics {
    const val activationDistance = 64f
    const val maxPull = 200f
    const val completionThreshold = .9f
    const val minLiftBeforeUIReveal = 58f
    const val hapticStepPoints = 10f
    const val pullAmplification = 1f
    const val maxConversationLift = 168f
    const val liftCurveExponent = 1.08f
    const val liftCurveScale = .48f
    const val revealStartPull = minLiftBeforeUIReveal

    fun rubberBandPull(translation: Float): Float {
        val raw = maxOf(0f, -translation)
        if (raw == 0f) return 0f
        return maxPull * (1f - exp(-raw / (maxPull / 2f)))
    }

    fun pull(fingerUpward: Float): Float = scaledPull(maxOf(0f, fingerUpward))

    fun conversationLift(fingerUpward: Float): Float {
        if (fingerUpward <= 0f) return 0f
        return min(fingerUpward.pow(liftCurveExponent) * liftCurveScale, maxConversationLift)
    }

    fun shouldRevealVanishUI(lift: Float): Boolean = lift >= minLiftBeforeUIReveal

    fun progress(lift: Float): Float = ((lift - minLiftBeforeUIReveal) / activationDistance).coerceIn(0f, 1f)

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
        return VanishPullResult(progress >= completionThreshold, progress, effectiveLiftForCompletion(lift))
    }
}

/** Compose equivalent of the UIKit overlay, positioned by the conversation host. */
@Composable
fun ChatVanishPullOverlay(
    conversationLift: Float,
    progress: Float,
    isActive: Boolean,
    isDragging: Boolean,
    composerBottomInset: Dp = 0.dp,
    modifier: Modifier = Modifier,
) {
    if (!ChatVanishSwipeMetrics.shouldRevealVanishUI(conversationLift)) return
    val adjusted = ChatVanishSwipeMetrics.effectiveLiftForCompletion(conversationLift)
    val revealOpacity = (adjusted / 28f).coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = composerBottomInset + (conversationLift * .5f).dp)
            .scale(.94f + progress.coerceIn(0f, 1f) * .06f),
        contentAlignment = Alignment.Center,
    ) {
        ChatVanishPullRevealContent(progress, isActive, isDragging, revealOpacity)
    }
}

@Composable
fun ChatVanishModeProgressIndicator(progress: Float, modifier: Modifier = Modifier) {
    val primary = if (isSystemInDarkTheme()) Color.White else Color.Black
    Canvas(modifier.size(36.dp)) {
        val strokeWidth = 2.5.dp.toPx()
        drawCircle(primary.copy(alpha = .14f), style = Stroke(strokeWidth))
        drawArc(
            color = primary.copy(alpha = .88f),
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
    val opacity = (ChatVanishSwipeMetrics.effectivePull(pullOffset) / 36f).coerceIn(0f, 1f)
    Box(modifier.fillMaxWidth().scale(.94f + progress.coerceIn(0f, 1f) * .06f), contentAlignment = Alignment.Center) {
        ChatVanishPullRevealContent(progress, isActive, isDragging, opacity)
    }
}

@Composable
private fun ChatVanishPullRevealContent(progress: Float, isActive: Boolean, isDragging: Boolean, opacity: Float) {
    val isDark = isSystemInDarkTheme()
    val secondary = if (isDark) Color.White.copy(alpha = .8f) else Color.Black.copy(alpha = .7f)
    val hintRes = when {
        isDragging && progress >= ChatVanishSwipeMetrics.completionThreshold && isActive -> R.string.chat_vanish_swipe_release_off
        isDragging && progress >= ChatVanishSwipeMetrics.completionThreshold -> R.string.chat_vanish_swipe_release
        isActive -> R.string.chat_vanish_swipe_hint_off
        else -> R.string.chat_vanish_swipe_hint
    }
    val accessibilityRes = if (isActive) R.string.chat_vanish_active_accessibility else R.string.chat_vanish_inactive_accessibility
    val accessibilityText = stringResource(accessibilityRes)
    Column(
        modifier = Modifier.semantics { contentDescription = accessibilityText },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.scale(opacity)) { ChatVanishModeProgressIndicator(progress) }
        Text(
            text = stringResource(hintRes),
            color = secondary.copy(alpha = .62f * opacity),
            fontSize = 10.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 28.dp),
        )
    }
}

@Composable
fun ChatVanishSwipeRevealFooter(pullOffset: Float, progress: Float, isActive: Boolean, isDragging: Boolean, modifier: Modifier = Modifier) {
    ChatVanishPullRevealLayer(pullOffset, progress, isActive, isDragging, modifier)
}

@Composable
fun ChatVanishSwipeHint(pullOffset: Float, progress: Float, isActive: Boolean, isDragging: Boolean, modifier: Modifier = Modifier) {
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
    val bodyColor = if (isDark) Color.White.copy(alpha = .5f) else Color.Black.copy(alpha = .42f)
    val actionColor = LocalChatOutgoingBubbleColor.current
    val timer = VanishMessageTimer.parseEnabledNotice(noticeToken)

    val content: @Composable () -> Unit = when {
        timer != null -> {
            {
                val prefix = stringResource(
                    if (isSelfActor) R.string.chat_vanish_notice_enabled_self else R.string.chat_vanish_notice_enabled_other,
                    *if (isSelfActor) emptyArray() else arrayOf(actorName),
                )
                ChatVanishNoticeAction(
                    body = stringResource(R.string.chat_vanish_notice_enabled, prefix, stringResource(timer.noticeDurationRes)),
                    action = stringResource(R.string.chat_vanish_notice_change),
                    bodyColor = bodyColor,
                    actionColor = actionColor,
                    onClick = onChangeTimer,
                )
            }
        }
        noticeToken == VanishMessageTimer.DISABLED_NOTICE_TOKEN || noticeToken == "chat.vanish.disabled" -> {
            {
                val body = stringResource(
                    if (isSelfActor) R.string.chat_vanish_notice_disabled_self else R.string.chat_vanish_notice_disabled_other,
                    *if (isSelfActor) emptyArray() else arrayOf(actorName),
                )
                ChatVanishNoticeAction(body, stringResource(R.string.chat_vanish_notice_turn_on), bodyColor, actionColor, onTurnOn)
            }
        }
        noticeToken == VanishMessageTimer.SCREENSHOT_NOTICE_TOKEN -> {{ ChatVanishPlainNotice(R.string.chat_vanish_screenshot, bodyColor) }}
        noticeToken == VanishMessageTimer.SCREEN_RECORDING_NOTICE_TOKEN -> {{ ChatVanishPlainNotice(R.string.chat_vanish_screen_recording, bodyColor) }}
        noticeToken == "chat.vanish.enabled" -> {{
            ChatVanishNoticeAction(
                stringResource(R.string.chat_vanish_notice_enabled, stringResource(R.string.chat_vanish_notice_enabled_self), stringResource(R.string.chat_vanish_duration_24h)),
                stringResource(R.string.chat_vanish_notice_change), bodyColor, actionColor, onChangeTimer,
            )
        }}
        else -> {{ ChatVanishPlainNotice(noticeToken, bodyColor) }}
    }
    Box(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun ChatVanishNoticeAction(body: String, action: String, bodyColor: Color, actionColor: Color, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier.then(if (onClick == null) Modifier else Modifier.clickable(onClick = onClick)),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(body, color = bodyColor, fontSize = 10.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.width(4.dp))
        Text(action, color = actionColor, fontSize = 10.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun ChatVanishPlainNotice(@StringRes noticeRes: Int, color: Color) {
    Text(stringResource(noticeRes), color = color, fontSize = 10.sp, textAlign = TextAlign.Center)
}

@Composable
private fun ChatVanishPlainNotice(notice: String, color: Color) {
    Text(notice, color = color, fontSize = 10.sp, textAlign = TextAlign.Center)
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun ChatVanishTimerSheet(
    selectedTimer: VanishMessageTimer,
    onSelect: (VanishMessageTimer?) -> Unit,
    onDismiss: () -> Unit,
) {
    val isDark = isSystemInDarkTheme()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = if (isDark) Color(0xFF0B1215) else Color(0xFFFAF9F6),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
            Text(stringResource(R.string.chat_vanish_timer_sheet_title), fontSize = 17.sp)
            Spacer(Modifier.height(12.dp))
            VanishMessageTimer.entries.forEach { timer ->
                Row(
                    Modifier.fillMaxWidth().clickable { onSelect(timer); onDismiss() }.padding(vertical = 15.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(stringResource(timer.localizationRes), color = if (isDark) Color.White else Color.Black)
                    Spacer(Modifier.weight(1f))
                    if (timer == selectedTimer) Icon(Icons.Default.CheckCircle, null, tint = LocalChatOutgoingBubbleColor.current)
                }
            }
            Row(
                Modifier.fillMaxWidth().clickable { onSelect(null); onDismiss() }.padding(vertical = 15.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) { Text(stringResource(R.string.chat_vanish_timer_off), color = Color(0xFFFF3B30)) }
            Text(
                stringResource(R.string.common_done),
                modifier = Modifier.align(Alignment.End).clickable(onClick = onDismiss).padding(vertical = 12.dp),
                color = LocalChatOutgoingBubbleColor.current,
            )
        }
    }
}

@Composable
fun ChatViewOnceInboxIndicator(modifier: Modifier = Modifier) {
    Box(modifier.size(22.dp).background(Color(0xFF007AFF), CircleShape), contentAlignment = Alignment.Center) {
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
    val ringColor = if (isUnread) Color(0xFF007AFF) else if (isSystemInDarkTheme()) Color.White.copy(alpha = .42f) else Color.Black.copy(alpha = .32f)
    val accessibilityText = stringResource(R.string.chat_vanish_active_accessibility)
    Canvas(
        modifier.size(15.dp).semantics { contentDescription = accessibilityText },
    ) {
        drawCircle(ringColor, style = Stroke(1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.2.dp.toPx(), 2.8.dp.toPx()))))
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
    ChatDisappearingNoticeRow(noticeKey, actorUserId, currentUserId, otherParticipantName, onChangeTimer, onTurnOn, modifier)
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
