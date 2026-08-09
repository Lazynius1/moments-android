package com.moments.android.views.messaging.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.messaging.core.MessageType

/**
 * Port de `ViewOnceMessageBubble.swift` — píldoras unread / replay / opened / sent.
 *
 * Stub: `matchedTransitionSource` / zoomNamespace iOS no portado (sin API Compose equivalente).
 * Material/blur de la píldora → fill sólido AdaptiveColors (regla canvas).
 */

/** ≡ iOS `Color.blue / .purple / .pink` signature (blue = system ≈ #007AFF). */
private val viewOnceSignatureGradient = Brush.linearGradient(
    listOf(Color(0xFF007AFF), Color(0xFFAF52DE), Color(0xFFFF2D55)),
)

@Composable
fun ViewOnceMessageBubble(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    @Suppress("UNUSED_PARAMETER") otherParticipantName: String,
    progress: Double?,
    onOpenViewer: ((replay: Boolean) -> Unit)? = null,
    currentUserId: String? = null,
    modifier: Modifier = Modifier,
) {
    val uid = currentUserId ?: remember {
        FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
    }
    // ≡ iOS replayAvailable / effectiveViewed (+ hasBeenViewedBy: Compose no observa mutaciones in-place)
    val replayAvailable = message.allowReplay == true &&
        message.replayAvailableInCurrentChatSession &&
        !message.replayConsumedInCurrentChatSession &&
        !message.hasBeenReplayedBy(uid)
    val mediaGone = message.mediaUrl.isNullOrBlank() && message.mediaObjectPath.isNullOrBlank()
    val effectiveViewed = message.isViewed ||
        message.hasBeenViewedBy(uid) ||
        message.replayAvailableInCurrentChatSession ||
        (mediaGone && message.viewedBy.orEmpty().isNotEmpty())

    if (isCurrentUser) {
        ViewOnceSentBubble(message = message, progress = progress, modifier = modifier)
        return
    }

    // iOS apila unread/replay/opened con opacity; Compose muestra el estado activo.
    when {
        effectiveViewed && replayAvailable -> ViewOnceReplayBubble(
            onTap = {
                if (replayAvailable) onOpenViewer?.invoke(true)
            },
            modifier = modifier,
        )
        effectiveViewed -> ViewOnceOpenedBubble(modifier = modifier)
        else -> ViewOnceUnreadBubble(
            message = message,
            onTap = {
                if (!effectiveViewed) onOpenViewer?.invoke(false)
            },
            modifier = modifier,
        )
    }
}

/** Píldora compartida — ≡ `ViewOncePillBubble`. */
@Composable
private fun ViewOncePillBubble(
    glyph: @Composable () -> Unit,
    label: String,
    labelWeight: FontWeight = FontWeight.SemiBold,
    labelOpacity: Float = 1f,
    showsDashedRing: Boolean = true,
    showsUnreadDot: Boolean = false,
    onTap: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    val pillShape = RoundedCornerShape(percent = 50)
    Row(
        modifier
            .clip(pillShape)
            .background(colors.messageBubbleBackground.copy(alpha = 0.3f))
            .border(0.8.dp, colors.messageBubbleStroke, pillShape)
            .then(if (onTap != null) Modifier.clickable(onClick = onTap) else Modifier)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
            if (showsDashedRing) {
                val ringColor = colors.messageTextColor.copy(alpha = 0.7f)
                Canvas(Modifier.size(30.dp)) {
                    drawCircle(
                        color = ringColor,
                        style = Stroke(
                            width = 1.5.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(
                                floatArrayOf(3.dp.toPx(), 3.dp.toPx()),
                            ),
                        ),
                    )
                }
            }
            glyph()
        }
        Text(
            label,
            color = colors.messageTextColor.copy(alpha = labelOpacity),
            fontSize = 14.sp,
            fontWeight = labelWeight,
        )
        if (showsUnreadDot) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(viewOnceSignatureGradient),
            )
        }
    }
}

@Composable
private fun ViewOnceGlyph(icon: ImageVector, tint: Color, size: Dp = 12.dp) {
    Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size))
}

@Composable
private fun ViewOnceUnreadBubble(
    message: EnhancedMessage,
    onTap: () -> Unit,
    modifier: Modifier,
) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    ViewOncePillBubble(
        glyph = {
            ViewOnceGlyph(
                icon = if (message.type == MessageType.VIEW_ONCE_VIDEO) {
                    Icons.Filled.PlayArrow
                } else {
                    Icons.Filled.CameraAlt
                },
                tint = colors.messageTextColor,
                size = 11.dp,
            )
        },
        label = stringResource(message.viewOnceTypeRes),
        showsUnreadDot = true,
        onTap = onTap,
        modifier = modifier,
    )
}

@Composable
private fun ViewOnceReplayBubble(onTap: () -> Unit, modifier: Modifier) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    ViewOncePillBubble(
        glyph = { ViewOnceGlyph(Icons.Filled.Replay, colors.messageTextColor, size = 12.dp) },
        label = stringResource(R.string.chat_view_once_tap_to_replay),
        onTap = onTap,
        modifier = modifier,
    )
}

@Composable
private fun ViewOnceOpenedBubble(modifier: Modifier) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    ViewOncePillBubble(
        glyph = {
            ViewOnceGlyph(
                Icons.Filled.VisibilityOff,
                colors.messageTextColor.copy(alpha = 0.4f),
                size = 11.dp,
            )
        },
        label = stringResource(R.string.chat_view_once_already_viewed),
        labelWeight = FontWeight.Medium,
        labelOpacity = 0.45f,
        modifier = modifier,
    )
}

@Composable
private fun ViewOnceSentBubble(
    message: EnhancedMessage,
    progress: Double?,
    modifier: Modifier,
) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    // ≡ iOS statusText: viewed + allowReplay + replayedBy no vacío → "Replayed"
    val labelRes = when {
        message.isViewed &&
            message.allowReplay == true &&
            !message.replayedBy.isNullOrEmpty() -> R.string.chat_view_once_replayed
        message.isViewed -> R.string.chat_view_once_viewed
        else -> message.viewOnceTypeRes
    }
    ViewOncePillBubble(
        glyph = {
            when {
                message.status == MessageStatus.SENDING && progress != null ->
                    MediaProgressRing(progress = progress, size = 26.dp, lineWidth = 2.dp)
                message.isViewed ->
                    ViewOnceGlyph(
                        Icons.Filled.Check,
                        colors.messageTextColor.copy(alpha = 0.5f),
                        size = 11.dp,
                    )
                else -> ViewOnceGlyph(
                    icon = if (message.type == MessageType.VIEW_ONCE_VIDEO) {
                        Icons.Filled.PlayArrow
                    } else {
                        Icons.Filled.CameraAlt
                    },
                    tint = colors.messageTextColor,
                    size = 11.dp,
                )
            }
        },
        label = stringResource(labelRes),
        labelWeight = if (message.isViewed) FontWeight.Medium else FontWeight.SemiBold,
        labelOpacity = if (message.isViewed) 0.5f else 1f,
        modifier = modifier,
    )
}

private val EnhancedMessage.viewOnceTypeRes: Int
    get() = when (type) {
        MessageType.VIEW_ONCE_IMAGE -> R.string.chat_view_once_photo
        MessageType.VIEW_ONCE_VIDEO -> R.string.chat_view_once_video
        else -> R.string.chat_view_once_media
    }
