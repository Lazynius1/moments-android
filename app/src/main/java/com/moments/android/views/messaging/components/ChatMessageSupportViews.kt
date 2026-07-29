package com.moments.android.views.messaging.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.moments.android.R
import com.moments.android.extensions.MomentsChromeGlass
import com.moments.android.extensions.momentsChromeGlass
import com.moments.android.utilities.EmojiReactionDefaults
import com.moments.android.utilities.EmojiUsageTracker
import com.moments.android.utilities.HapticManager
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.feed.AdaptiveColors
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageStatus
import com.moments.android.views.shared.ScreenshotProtectedView

data class ChatFailedMessageRetryAction(
    val canRetry: (EnhancedMessage) -> Boolean,
    val retry: (EnhancedMessage) -> Unit,
)

val LocalChatFailedMessageRetryAction = staticCompositionLocalOf<ChatFailedMessageRetryAction?> { null }

object MessageReactionMetrics {
    fun emojiSize(compact: Boolean, cluster: Boolean = false): Float =
        when {
            cluster -> 12f
            compact -> 18f
            else -> 20f
        }

    fun countSize(compact: Boolean, cluster: Boolean = false): Float =
        when {
            cluster -> 7f
            compact -> 8f
            else -> 10f
        }

    fun badgeDiameter(compact: Boolean, cluster: Boolean = false): Dp =
        when {
            cluster -> 18.dp
            compact -> 22.dp
            else -> 24.dp
        }

    fun overlapSpacing(compact: Boolean, cluster: Boolean = false): Dp =
        when {
            cluster -> (-5).dp
            compact -> (-5).dp
            else -> (-7).dp
        }

    fun horizontalHangOffset(compact: Boolean, anchoredInsideBounds: Boolean): Dp =
        when {
            anchoredInsideBounds -> 3.dp
            compact -> 1.dp
            else -> 2.dp
        }

    fun hangOffset(compact: Boolean, cluster: Boolean = false): Dp =
        badgeDiameter(compact, cluster) * 0.62f

    fun reactionRowSpacing(compact: Boolean, cluster: Boolean = false): Dp =
        badgeDiameter(compact, cluster) * 0.66f

    /** Mitad de la diferencia entre hit target 44dp y badge cluster. ≡ iOS. */
    fun clusterHitTargetInset(compact: Boolean): Dp {
        val diameter = badgeDiameter(compact, cluster = true)
        return (maxOf(44.dp, diameter) - diameter) / 2f
    }

    fun starBadgeDiameter(compact: Boolean, cluster: Boolean = false): Dp =
        when {
            cluster -> 16.dp
            compact -> 20.dp
            else -> 22.dp
        }

    fun starIconSize(compact: Boolean, cluster: Boolean = false): Dp =
        when {
            cluster -> 8.dp
            compact -> 10.dp
            else -> 11.dp
        }

    fun starUsesLeadingCorner(isOutgoing: Boolean, hasReactions: Boolean): Boolean =
        if (hasReactions) !isOutgoing else isOutgoing

    /** Reserva en la burbuja de texto para que el badge no tape letras cortas. ≡ iOS. */
    fun bubbleContentInsets(
        isOutgoing: Boolean,
        compact: Boolean,
        hasReactions: Boolean,
        hasStar: Boolean = false,
    ): PaddingValues {
        val reactionClearance = badgeDiameter(compact, cluster = false) * 0.42f
        val starClearance = starBadgeDiameter(compact) * 0.42f
        var start = 0.dp
        var bottom = 0.dp
        var end = 0.dp
        if (hasReactions) {
            bottom = maxOf(bottom, reactionClearance * 0.3f)
            if (isOutgoing) start = maxOf(start, reactionClearance * 0.75f)
            else end = maxOf(end, reactionClearance * 0.75f)
        }
        if (hasStar) {
            bottom = maxOf(bottom, starClearance * 0.3f)
            if (starUsesLeadingCorner(isOutgoing, hasReactions)) {
                start = maxOf(start, starClearance * 0.75f)
            } else {
                end = maxOf(end, starClearance * 0.75f)
            }
        }
        return PaddingValues(start = start, top = 0.dp, end = end, bottom = bottom)
    }
}

@Composable
fun GlassmorphicReplyBar(
    message: EnhancedMessage,
    otherParticipantName: String,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        ReplyBarBody(message, otherParticipantName, large = true, onCancel = onCancel, modifier = modifier)
    }
    if (message.isVanishModeMessage) {
        ScreenshotProtectedView(isProtected = true, cornerRadius = 12.dp) { content() }
    } else {
        content()
    }
}

@Composable
fun GlassmorphicReplyPreview(
    message: EnhancedMessage,
    isParentMessageFromCurrentUser: Boolean,
    otherParticipantName: String,
    onTap: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val content: @Composable () -> Unit = {
        ReplyBarBody(
            message = message,
            otherParticipantName = otherParticipantName,
            large = false,
            onTap = onTap,
            modifier = modifier.widthIn(min = 120.dp, max = 220.dp),
        )
    }
    if (message.isVanishModeMessage) {
        ScreenshotProtectedView(isProtected = true, cornerRadius = 10.dp) { content() }
    } else {
        content()
    }
}

@Composable
fun StackedReplyQuote(
    repliedMessage: EnhancedMessage,
    isOutgoingRow: Boolean,
    otherParticipantName: String,
    onTap: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val colors = AdaptiveColors(isSystemInDarkTheme())
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val repliedToSelf = repliedMessage.senderId == currentUserId
    val you = stringResource(R.string.chat_reply_you)
    val caption = if (isOutgoingRow) {
        stringResource(R.string.chat_reply_you_replied_to, if (repliedToSelf) you else otherParticipantName)
    } else {
        stringResource(R.string.chat_reply_replied_to, otherParticipantName)
    }
    val preview = repliedMessage.preview(context)
    val body: @Composable () -> Unit = {
        Column(
            Modifier
                .widthIn(max = 240.dp)
                .clickable(enabled = onTap != null) { onTap?.invoke(repliedMessage.id) }
                .semantics { contentDescription = "$caption, $preview" },
            horizontalAlignment = if (isOutgoingRow) Alignment.End else Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Row(
                Modifier.padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = colors.messageTextColor.copy(0.5f), modifier = Modifier.size(11.dp))
                Text(caption, color = colors.messageTextColor.copy(0.5f), fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            }
            Row(
                Modifier
                    .clip(RoundedCornerShape(13.dp))
                    .background(colors.messageBubbleBackground.copy(alpha = 0.55f))
                    .border(0.5.dp, colors.messageBubbleStroke.copy(alpha = 0.4f), RoundedCornerShape(13.dp))
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ReplyThumb(repliedMessage, 26.dp)
                Text(preview, color = colors.messageTextColor.copy(0.65f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
    if (repliedMessage.isVanishModeMessage) {
        ScreenshotProtectedView(isProtected = true, cornerRadius = 13.dp) { body() }
    } else {
        body()
    }
}

@Composable
fun EmbeddedReplyView(
    repliedMessage: EnhancedMessage,
    isOutgoingBubble: Boolean,
    otherParticipantName: String,
    onTap: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colors = AdaptiveColors(dark)
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val repliedToSelf = repliedMessage.senderId == currentUserId
    val accent = if (repliedToSelf) colors.userAccentColor else colors.receivedAccentColor
    val tint = if (isOutgoingBubble) Color.White.copy(0.18f) else if (dark) Color.White.copy(0.10f) else Color.Black.copy(0.06f)
    val barColor = if (isOutgoingBubble) Color.White.copy(0.9f) else accent
    val titleColor = if (isOutgoingBubble) Color.White.copy(0.95f) else accent
    val bodyColor = if (isOutgoingBubble) Color.White.copy(0.8f) else colors.messageTextColor.copy(0.7f)
    val title = if (repliedToSelf) stringResource(R.string.chat_reply_you) else otherParticipantName
    val preview = repliedMessage.preview(context)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(7.dp))
            .background(tint)
            .clickable(enabled = onTap != null) { onTap?.invoke() }
            .padding(horizontal = 8.dp, vertical = 5.dp)
            .semantics { contentDescription = "$title, $preview" },
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.dp).height(32.dp).clip(RoundedCornerShape(1.5.dp)).background(barColor))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(title, color = titleColor, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(preview, color = bodyColor, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        ReplyThumb(repliedMessage, 32.dp)
    }
}

@Composable
private fun ReplyBarBody(
    message: EnhancedMessage,
    otherParticipantName: String,
    large: Boolean,
    onTap: (() -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = AdaptiveColors(isSystemInDarkTheme())
    val currentUserId = remember { FirebaseAuth.getInstance().currentUser?.uid.orEmpty() }
    val fromSelf = message.senderId == currentUserId
    val accent = if (fromSelf) colors.userAccentColor else colors.receivedAccentColor
    val name = if (fromSelf) stringResource(R.string.chat_reply_you) else otherParticipantName
    val preview = message.preview(context)
    val corner = if (large) 12.dp else 10.dp
    val bg = if (large) colors.replyBarBackground else colors.messageBubbleBackground.copy(alpha = 0.4f)
    Row(
        modifier
            .then(if (large) Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp) else Modifier)
            .clip(RoundedCornerShape(corner))
            .background(bg)
            .border(0.5.dp, colors.messageBubbleStroke.copy(alpha = if (large) 1f else 0.5f), RoundedCornerShape(corner))
            .clickable(enabled = onTap != null) { onTap?.invoke() }
            .padding(vertical = if (large) 6.dp else 6.dp, horizontal = if (large) 10.dp else 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .padding(vertical = if (large) 2.dp else 0.dp)
                .width(if (large) 3.5.dp else 2.5.dp)
                .height(if (large) 40.dp else 30.dp)
                .clip(RoundedCornerShape(50))
                .background(accent),
        )
        Spacer(Modifier.width(if (large) 12.dp else 8.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(if (large) 2.dp else 1.dp)) {
            Text(name, color = accent, fontSize = if (large) 13.sp else 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text(
                preview,
                color = if (large) colors.replyBarText else colors.messageTextColor.copy(0.8f),
                fontSize = if (large) 14.sp else 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        ReplyThumb(message, if (large) 40.dp else 30.dp)
        onCancel?.let {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = colors.replyBarSecondaryText,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(20.dp)
                    .clickable(onClick = it),
            )
        }
    }
}

@Composable
private fun ReplyThumb(message: EnhancedMessage, size: Dp) {
    if (message.isViewOnce) return
    val url = message.thumbnailUrl ?: message.mediaUrl
    if (url.isNullOrBlank()) return
    AsyncImage(
        model = url,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .padding(start = 4.dp)
            .size(size)
            .clip(RoundedCornerShape(if (size >= 36.dp) 6.dp else 4.dp)),
    )
}

@Composable
fun ChatQuickReactionsBar(
    onReaction: (String) -> Unit,
    onMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val tracker = remember { EmojiUsageTracker() }
    val emojis = remember { tracker.orderedEmojis(EmojiReactionDefaults.chat) }
    Row(
        modifier
            .shadow(8.dp, CircleShape)
            .clip(RoundedCornerShape(50))
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        emojis.forEach { emoji ->
            Text(
                emoji,
                fontSize = 28.sp,
                modifier = Modifier.clickable {
                    HapticManager.shared.mediumImpact()
                    onReaction(emoji)
                },
            )
        }
        Icon(
            Icons.Default.Add,
            stringResource(R.string.chat_action_more_reactions),
            tint = MomentsChromeGlass.contentColor(dark),
            modifier = Modifier
                .size(36.dp)
                .clickable {
                    HapticManager.shared.lightImpact()
                    onMore()
                }
                .padding(6.dp),
        )
    }
}

@Composable
fun MessageReactionChip(
    reactions: Map<String, List<String>>,
    onTap: (String) -> Unit,
    compact: Boolean = false,
    cluster: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val dark = isSystemInDarkTheme()
    val entries = reactions.entries
        .map { it.key to it.value.size }
        .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
        .take(5)
    val diameter = MessageReactionMetrics.badgeDiameter(compact, cluster)
    val hit = if (cluster) maxOf(44.dp, diameter) else diameter
    val overlap = MessageReactionMetrics.overlapSpacing(compact, cluster)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(overlap)) {
        entries.forEach { (emoji, count) ->
            Box(
                Modifier
                    .size(hit)
                    .clickable { onTap(emoji) }
                    .semantics { contentDescription = if (count > 1) "$emoji $count" else emoji },
                contentAlignment = Alignment.Center,
            ) {
                if (count > 1) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(emoji, fontSize = MessageReactionMetrics.emojiSize(compact, cluster).sp)
                        Text(
                            count.toString(),
                            fontSize = MessageReactionMetrics.countSize(compact, cluster).sp,
                            fontWeight = FontWeight.Bold,
                            color = if (dark) Color.White.copy(0.9f) else Color.Black.copy(0.65f),
                        )
                    }
                } else {
                    Text(emoji, fontSize = MessageReactionMetrics.emojiSize(compact, cluster).sp)
                }
            }
        }
    }
}

@Composable
fun MessageStarBadge(
    compact: Boolean = false,
    cluster: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val diameter = MessageReactionMetrics.starBadgeDiameter(compact, cluster)
    val icon = MessageReactionMetrics.starIconSize(compact, cluster)
    Box(modifier.size(diameter), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Default.Star,
            contentDescription = stringResource(R.string.chat_action_star),
            tint = Color(0xFFFFD60A),
            modifier = Modifier.size(icon),
        )
    }
}

/**
 * ≡ iOS `messageReactionOverlay` + cutouts.
 * Cutout: Swift `blendMode(.destinationOut)` + compositingGroup →
 * [CompositingStrategy.Offscreen] + [BlendMode.Clear] (mismo patrón que ConversationContextMenu).
 */
@Composable
fun MessageReactionOverlayBox(
    isOutgoing: Boolean,
    reactions: Map<String, List<String>>?,
    isStarred: Boolean = false,
    compact: Boolean = false,
    anchoredInsideBounds: Boolean = false,
    onTap: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    val density = LocalDensity.current
    val hasReactions = !reactions.isNullOrEmpty()
    val hang = MessageReactionMetrics.hangOffset(compact)
    val rowSpacing = MessageReactionMetrics.reactionRowSpacing(compact)
    val horizontal = MessageReactionMetrics.horizontalHangOffset(compact, anchoredInsideBounds)
    val starLeading = MessageReactionMetrics.starUsesLeadingCorner(isOutgoing, hasReactions)
    val gap = 1.5.dp
    val visibleEntries = remember(reactions) {
        reactions.orEmpty().entries
            .map { it.key to it.value.size }
            .sortedWith(compareByDescending<Pair<String, Int>> { it.second }.thenBy { it.first })
            .take(5)
    }
    val visibleCount = visibleEntries.size
    Box(Modifier.padding(bottom = if ((hasReactions || isStarred) && !anchoredInsideBounds) rowSpacing else 0.dp)) {
        Box(
            Modifier
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    val gapPx = with(density) { gap.toPx() }
                    val hangPx = with(density) { hang.toPx() }
                    val horizontalPx = with(density) { horizontal.toPx() }
                    if (hasReactions && visibleCount > 0) {
                        val diameterPx = with(density) {
                            MessageReactionMetrics.badgeDiameter(compact).toPx()
                        }
                        val overlapPx = with(density) {
                            MessageReactionMetrics.overlapSpacing(compact).toPx()
                        }
                        val chipWidth = diameterPx + (visibleCount - 1) * (diameterPx + overlapPx)
                        val cutoutW = chipWidth + gapPx * 2f
                        val cutoutH = diameterPx + gapPx * 2f
                        val cutoutX = if (isOutgoing) horizontalPx - gapPx else -horizontalPx + gapPx
                        val cutoutY = if (anchoredInsideBounds) -3f + gapPx else hangPx + gapPx
                        val left = if (isOutgoing) {
                            cutoutX
                        } else {
                            size.width - cutoutW + cutoutX
                        }
                        val top = size.height - cutoutH + cutoutY
                        drawRoundRect(
                            color = Color.Black,
                            topLeft = Offset(left, top),
                            size = Size(cutoutW, cutoutH),
                            cornerRadius = CornerRadius(cutoutH / 2f),
                            blendMode = BlendMode.Clear,
                        )
                    }
                    if (isStarred) {
                        val starD = with(density) {
                            MessageReactionMetrics.starBadgeDiameter(compact).toPx()
                        }
                        val cutD = starD + gapPx * 2f
                        val cutoutX = if (starLeading) horizontalPx - gapPx else -horizontalPx + gapPx
                        val cutoutY = if (anchoredInsideBounds) -3f + gapPx else hangPx + gapPx
                        val left = if (starLeading) {
                            cutoutX
                        } else {
                            size.width - cutD + cutoutX
                        }
                        val top = size.height - cutD + cutoutY
                        drawCircle(
                            color = Color.Black,
                            radius = cutD / 2f,
                            center = Offset(left + cutD / 2f, top + cutD / 2f),
                            blendMode = BlendMode.Clear,
                        )
                    }
                },
        ) {
            content()
        }
        reactions?.takeIf { it.isNotEmpty() }?.let { nonEmpty ->
            MessageReactionChip(
                reactions = nonEmpty,
                onTap = onTap,
                compact = compact,
                modifier = Modifier
                    .align(if (isOutgoing) Alignment.BottomStart else Alignment.BottomEnd)
                    .offset(
                        x = if (isOutgoing) horizontal else -horizontal,
                        y = if (anchoredInsideBounds) (-3).dp else hang,
                    )
                    .zIndex(5f),
            )
        }
        if (isStarred) {
            MessageStarBadge(
                compact = compact,
                modifier = Modifier
                    .align(if (starLeading) Alignment.BottomStart else Alignment.BottomEnd)
                    .offset(
                        x = if (starLeading) horizontal else -horizontal,
                        y = if (anchoredInsideBounds) (-3).dp else hang,
                    )
                    .zIndex(6f),
            )
        }
    }
}

@Composable
fun MessageTimestamp(
    message: EnhancedMessage,
    isCurrentUser: Boolean,
    showSeenLabel: Boolean = false,
    overrideStatus: MessageStatus? = null,
    modifier: Modifier = Modifier,
) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    val status = overrideStatus ?: message.status
    val time = remember(message.timestamp) {
        MomentsFormat.smartDate(message.timestamp, MomentsFormat.DateContext.TIME_ONLY)
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(time, fontSize = 11.sp, color = colors.timestampColor)
        if (message.editedAt != null) {
            Text(stringResource(R.string.chat_edited), fontSize = 11.sp, color = colors.timestampColor)
        }
        if (isCurrentUser) {
            if (showSeenLabel && status == MessageStatus.READ) {
                Text(stringResource(R.string.chat_seen), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.timestampColor.copy(0.9f))
            } else {
                MessageStatusIcon(status)
            }
        }
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus) {
    val colors = AdaptiveColors(isSystemInDarkTheme())
    when (status) {
        MessageStatus.PENDING -> Icon(Icons.Default.Schedule, null, tint = colors.timestampColor.copy(0.8f), modifier = Modifier.size(10.dp))
        MessageStatus.SENDING -> CircularProgressIndicator(Modifier.size(10.dp), strokeWidth = 1.dp, color = colors.timestampColor)
        MessageStatus.SENT -> Icon(Icons.Default.Check, null, tint = colors.timestampColor, modifier = Modifier.size(10.dp))
        MessageStatus.DELIVERED -> Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
            Icon(Icons.Default.Check, null, tint = colors.timestampColor, modifier = Modifier.size(10.dp))
            Icon(Icons.Default.Check, null, tint = colors.timestampColor, modifier = Modifier.size(10.dp))
        }
        MessageStatus.READ -> Row(horizontalArrangement = Arrangement.spacedBy((-3).dp)) {
            Icon(Icons.Default.Check, null, tint = colors.userAccentColor, modifier = Modifier.size(10.dp))
            Icon(Icons.Default.Check, null, tint = colors.userAccentColor, modifier = Modifier.size(10.dp))
        }
        MessageStatus.FAILED -> Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Warning, null, tint = Color.Red, modifier = Modifier.size(10.dp))
            Text(stringResource(R.string.chat_error), color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
fun GlassmorphicReactionsOverlay(onReaction: (String) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .shadow(10.dp, CircleShape)
            .clip(RoundedCornerShape(50))
            .momentsChromeGlass(RoundedCornerShape(50), interactive = true)
            .border(0.5.dp, Color.White.copy(0.2f), RoundedCornerShape(50))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmojiReactionDefaults.chat.forEach { emoji ->
            Text(
                emoji,
                fontSize = 26.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color.White.copy(0.12f))
                    .clickable { onReaction(emoji) }
                    .padding(8.dp),
            )
        }
    }
}

/** ≡ iOS `typealias GlassmorphicReactionsView = MessageReactionChip`. */
@Composable
fun GlassmorphicReactionsView(
    reactions: Map<String, List<String>>,
    onTap: (String) -> Unit,
    compact: Boolean = false,
    cluster: Boolean = false,
    modifier: Modifier = Modifier,
) = MessageReactionChip(reactions, onTap, compact, cluster, modifier)
