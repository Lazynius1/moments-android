package com.moments.android.views.messaging.services

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageType
import com.moments.android.views.messaging.components.ChatTextBubbleMetrics
import com.moments.android.views.messaging.components.ClusterMediaLayout
import com.moments.android.views.messaging.core.ChatRenderRow
import com.moments.android.views.messaging.core.MessageItem
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/** Estimaciones de altura previas a la medición real de las filas del chat. */
object ChatRowHeightEstimator {
    private const val baseFontSize = 15f
    private val textHorizontalPadding = ChatTextBubbleMetrics.horizontalPadding * 2
    private val textVerticalPadding = ChatTextBubbleMetrics.verticalPadding * 2
    private val replyBlockHeight = 46.dp
    private val reactionsRowHeight = 28.dp

    private const val mediaDefaultAspect = 4f / 5f
    private val mediaMinHeight = 140.dp
    private val mediaMaxHeight = 420.dp
    private const val gifDefaultAspect = 200f / 150f

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
        val bubbleWidth = maxOf(120.dp, containerWidth * ChatTextBubbleMetrics.maxWidthFraction)
        return when (row) {
            is ChatRenderRow.ConversationIntro -> conversationIntroHeight
            is ChatRenderRow.RequestDisclaimer -> requestDisclaimerHeight
            is ChatRenderRow.PendingRequestMessage -> {
                val text = row.message.text.trim()
                if (text.isEmpty()) viewOncePillHeight + viewOnceRowVerticalPadding
                else maxOf(46.dp, textHeight(text, bubbleWidth) + 6.dp)
            }
            is ChatRenderRow.Header -> headerHeight
            is ChatRenderRow.Buzz -> buzzHeight
            ChatRenderRow.Typing -> typingHeight
            ChatRenderRow.HistoryStart -> historyStartHeight
            is ChatRenderRow.Message -> estimatedHeight(row.item, bubbleWidth)
        }
    }

    private fun textHeight(text: String, bubbleWidth: Dp): Dp {
        val availableWidth = max(80f, bubbleWidth.value - textHorizontalPadding.value)
        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply { textSize = baseFontSize }
        val layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, availableWidth.toInt())
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setIncludePad(true)
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
        MessageType.IMAGE, MessageType.VIDEO -> mediaHeight(message, bubbleWidth, mediaDefaultAspect)
        MessageType.GIF, MessageType.STICKER -> mediaHeight(message, bubbleWidth, gifDefaultAspect)
        MessageType.AUDIO -> voiceNoteHeight
        MessageType.LOCATION -> locationHeight + if (message.isLiveLocation == true) liveLocationExtraHeight else 0.dp
        MessageType.FILE -> fileHeight
        MessageType.VIEW_ONCE_IMAGE, MessageType.VIEW_ONCE_VIDEO -> viewOnceHeight(message)
        MessageType.EPHEMERAL -> ephemeralHeight
        MessageType.SHARED_MOMENT, MessageType.SHARED_STORY -> sharedPreviewHeight
        MessageType.CHAT_NOTICE -> chatNoticeHeight
    }

    private fun textHeight(message: EnhancedMessage, bubbleWidth: Dp): Dp {
        if (message.content.isNullOrEmpty()) return fallbackHeight
        var height = textHeight(message.content, bubbleWidth)
        if (message.replyTo != null) height += replyBlockHeight
        if (!message.reactions.isNullOrEmpty()) height += reactionsRowHeight
        return maxOf(height, fallbackHeight * .7f)
    }

    private fun mediaHeight(message: EnhancedMessage, bubbleWidth: Dp, fallbackAspect: Float): Dp {
        val aspect = if ((message.mediaWidth ?: 0) > 0 && (message.mediaHeight ?: 0) > 0) {
            message.mediaWidth!!.toFloat() / message.mediaHeight!!.toFloat()
        } else {
            fallbackAspect
        }
        var height = (bubbleWidth.value / max(aspect, .35f)).dp
        height = height.coerceIn(mediaMinHeight, mediaMaxHeight)
        if (!message.content.isNullOrEmpty()) height += textHeight(message, bubbleWidth) - textVerticalPadding
        if (!message.reactions.isNullOrEmpty()) height += reactionsRowHeight
        return height
    }

    private fun viewOnceHeight(message: EnhancedMessage): Dp {
        var height = viewOncePillHeight + viewOnceRowVerticalPadding
        if (!message.reactions.isNullOrEmpty()) height += reactionsRowHeight
        return height
    }
}
