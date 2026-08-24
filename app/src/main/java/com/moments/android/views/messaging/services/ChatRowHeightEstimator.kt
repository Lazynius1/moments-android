package com.moments.android.views.messaging.services

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.views.messaging.core.EnhancedMessage
import com.moments.android.views.messaging.core.MessageType
import com.moments.android.views.messaging.components.ChatTextBubbleMetrics
import com.moments.android.views.messaging.components.ClusterMediaLayout
import com.moments.android.views.messaging.core.ChatRenderRow
import com.moments.android.views.messaging.core.MessageItem
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Port de `ChatRowHeightEstimator.swift`.
 * Estimaciones previas a la medición real (UICollectionView estimatedHeights / LazyColumn).
 *
 * Unidades: se trabaja en “pt” vía `Dp.value` (1:1 como CGFloat iOS). StaticLayout mide
 * en ese espacio ficticio; el resultado se reinterpreta como Dp — no usar px de pantalla.
 */
object ChatRowHeightEstimator {
    private val maxBubbleWidthFraction = ChatTextBubbleMetrics.maxWidthFraction
    private val textHorizontalPadding = ChatTextBubbleMetrics.horizontalPadding * 2
    private val textVerticalPadding = ChatTextBubbleMetrics.verticalPadding * 2
    private const val BASE_FONT_SIZE = 15f
    private val replyBlockHeight = 46.dp
    private val reactionsRowHeight = 28.dp

    private const val MEDIA_DEFAULT_ASPECT = 4f / 5f
    private val mediaMinHeight = 140.dp
    private val mediaMaxHeight = 420.dp
    private const val GIF_DEFAULT_ASPECT = 200f / 150f

    private val voiceNoteHeight = 68.dp
    private val locationHeight = 205.dp
    private val liveLocationExtraHeight = 40.dp
    private val fileHeight = 72.dp
    private val viewOncePillHeight = 50.dp
    private val viewOnceRowVerticalPadding = 10.dp
    private val ephemeralHeight = 150.dp
    private val sharedPreviewHeight = 220.dp
    private val chatNoticeHeight = 36.dp

    private val headerHeight = 32.dp
    private val buzzHeight = 44.dp
    private val typingHeight = 40.dp
    private val historyStartHeight = 50.dp
    private val conversationIntroHeight = 190.dp
    private val requestDisclaimerHeight = 58.dp

    val fallbackHeight = 60.dp

    fun estimatedHeight(row: ChatRenderRow, containerWidth: Dp): Dp {
        val bubbleWidth = maxOf(120.dp, containerWidth * maxBubbleWidthFraction)
        return when (row) {
            is ChatRenderRow.ConversationIntro -> conversationIntroHeight
            is ChatRenderRow.RequestDisclaimer -> requestDisclaimerHeight
            is ChatRenderRow.PendingRequestMessage -> {
                val text = row.message.text.trim()
                if (text.isEmpty()) {
                    viewOncePillHeight + viewOnceRowVerticalPadding
                } else {
                    maxOf(46.dp, textHeight(text, bubbleWidth, minAvailableWidth = 80f) + 6.dp)
                }
            }
            is ChatRenderRow.IncomingRequestActions -> 178.dp
            is ChatRenderRow.OutgoingRequestControls -> 82.dp
            is ChatRenderRow.Header -> headerHeight
            is ChatRenderRow.Buzz -> buzzHeight
            ChatRenderRow.Typing -> typingHeight
            ChatRenderRow.HistoryStart -> historyStartHeight
            is ChatRenderRow.Message -> estimatedHeight(row.item, bubbleWidth)
        }
    }

    /**
     * ≡ `textHeight(for:bubbleWidth:)` simple — min available width 80.
     * Mensaje usa min 40 (ver overload de mensaje).
     */
    private fun textHeight(text: String, bubbleWidth: Dp, minAvailableWidth: Float): Dp {
        val availableWidth = max(minAvailableWidth, bubbleWidth.value - textHorizontalPadding.value)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = BASE_FONT_SIZE }
        val widthPx = max(1, availableWidth.toInt())
        val layout = StaticLayout.Builder
            .obtain(text, 0, text.length, paint, widthPx)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
            .setLineSpacing(0f, 1f)
            .build()
        return ceil(layout.height.toDouble()).toFloat().dp + textVerticalPadding
    }

    private fun estimatedHeight(item: MessageItem, bubbleWidth: Dp): Dp = when (item) {
        is MessageItem.Single -> estimatedHeight(item.message, bubbleWidth)
        is MessageItem.MediaCluster -> estimatedClusterHeight(item.messages.size)
    }

    private fun estimatedClusterHeight(count: Int): Dp {
        val visible = min(max(count, 1), ClusterMediaLayout.maxVisible)
        return ClusterMediaLayout.frontHeight +
            ClusterMediaLayout.fanTopPadding(visible) +
            ClusterMediaLayout.fanBottomPadding +
            6.dp
    }

    private fun estimatedHeight(message: EnhancedMessage, bubbleWidth: Dp): Dp = when (message.type) {
        MessageType.TEXT -> textHeight(message, bubbleWidth)
        MessageType.IMAGE, MessageType.VIDEO ->
            mediaHeight(message, bubbleWidth, MEDIA_DEFAULT_ASPECT)
        MessageType.GIF, MessageType.STICKER ->
            mediaHeight(message, bubbleWidth, GIF_DEFAULT_ASPECT)
        MessageType.AUDIO -> voiceNoteHeight
        MessageType.LOCATION ->
            if (message.isLiveLocation == true) locationHeight + liveLocationExtraHeight
            else locationHeight
        MessageType.FILE -> fileHeight
        MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO -> viewOnceHeight(message)
        MessageType.EPHEMERAL -> ephemeralHeight
        MessageType.SHARED_MOMENT, MessageType.SHARED_STORY -> sharedPreviewHeight
        MessageType.CHAT_NOTICE -> chatNoticeHeight
    }

    private fun textHeight(message: EnhancedMessage, bubbleWidth: Dp): Dp {
        val text = message.content.orEmpty()
        if (text.isEmpty()) return fallbackHeight

        // iOS: max(40, bubbleWidth - padding)
        var height = textHeight(text, bubbleWidth, minAvailableWidth = 40f)
        if (message.replyTo != null) height += replyBlockHeight
        if (!message.reactions.isNullOrEmpty()) height += reactionsRowHeight
        return maxOf(height, fallbackHeight * 0.7f)
    }

    private fun mediaHeight(
        message: EnhancedMessage,
        bubbleWidth: Dp,
        fallbackAspect: Float,
    ): Dp {
        val aspect = run {
            val w = message.mediaWidth
            val h = message.mediaHeight
            if (w != null && h != null && w > 0 && h > 0) w.toFloat() / h.toFloat()
            else fallbackAspect
        }

        var height = (bubbleWidth.value / max(aspect, 0.35f)).dp
        height = minOf(maxOf(height, mediaMinHeight), mediaMaxHeight)

        val caption = message.content
        if (!caption.isNullOrEmpty()) {
            // Misma suma que iOS (incluye reply/reactions del textHeight de mensaje;
            // luego puede volver a sumar reactions — paridad con el bug iOS).
            height += textHeight(message, bubbleWidth) - textVerticalPadding
        }
        if (!message.reactions.isNullOrEmpty()) {
            height += reactionsRowHeight
        }
        return height
    }

    private fun viewOnceHeight(message: EnhancedMessage): Dp {
        var height = viewOncePillHeight + viewOnceRowVerticalPadding
        if (!message.reactions.isNullOrEmpty()) {
            height += reactionsRowHeight
        }
        return height
    }
}
