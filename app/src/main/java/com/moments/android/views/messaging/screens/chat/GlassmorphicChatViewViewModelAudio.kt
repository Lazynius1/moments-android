package com.moments.android.views.messaging.screens.chat

import com.moments.android.models.Conversation
import com.moments.android.models.EnhancedMessage
import com.moments.android.models.MessageSyncCursor
import com.moments.android.views.messaging.components.ClusterMessageGrouper
import com.moments.android.views.messaging.core.ChatRenderRow
import com.moments.android.views.messaging.core.EnhancedChatViewModel
import com.moments.android.views.messaging.core.MessageItem
import java.util.Calendar
import java.util.Date

/** Port de `GlassmorphicChatView+ViewModelAudio.swift`.
 * Mantiene la presentación agrupada separada de la lista Firestore base y contabiliza los
 * envíos de esta sesión antes de delegar al pipeline de mensajes temporales.
 */
class MomentsChatViewModel(
    conversation: Conversation,
    currentUserId: String,
) : EnhancedChatViewModel(conversation, currentUserId) {
    var groupedMessages: List<Pair<Date, List<EnhancedMessage>>> = emptyList()
        private set
    var chatRenderRows: List<ChatRenderRow> = emptyList()
        private set
    var messagesSentThisSession: Int = 0
        private set

    fun syncMessagePresentation() {
        val sorted = messages.value.sortedWith(compareBy<EnhancedMessage> { MessageSyncCursor(it.timestamp, it.id) })
        val grouped = mutableListOf<Pair<Date, List<EnhancedMessage>>>()
        val rows = mutableListOf<ChatRenderRow>()
        var currentDay: Date? = null
        var bucket = mutableListOf<EnhancedMessage>()

        fun flushDay() {
            val day = currentDay ?: return
            if (bucket.isEmpty()) return
            val items = bucket.toList()
            grouped += day to items
            rows += ChatRenderRow.Header(day)
            ClusterMessageGrouper.group(items).forEach { groupedItem ->
                rows += when (groupedItem) {
                    is com.moments.android.views.messaging.components.ClusterMessageItem.Single -> ChatRenderRow.Message(MessageItem.Single(groupedItem.message))
                    is com.moments.android.views.messaging.components.ClusterMessageItem.MediaCluster -> ChatRenderRow.Message(MessageItem.MediaCluster(groupedItem.messages))
                }
            }
        }

        sorted.forEach { message ->
            val day = Calendar.getInstance().apply { time = message.timestamp; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.time
            if (day != currentDay) {
                flushDay()
                currentDay = day
                bucket = mutableListOf()
            }
            bucket += message
        }
        flushDay()
        groupedMessages = grouped
        chatRenderRows = rows
    }

    fun updateGroupedMessages() = syncMessagePresentation()

    override fun sendTextMessage(text: String, replyTo: String?) {
        if (text.isBlank()) return
        messagesSentThisSession += 1
        super.sendTextMessage(text, replyTo)
    }

    fun trackMediaMessageSent() {
        messagesSentThisSession += 1
    }

    override fun sendVideoMessage(data: ByteArray, mediaBatchId: String?, replyTo: String?) {
        trackMediaMessageSent()
        super.sendVideoMessage(data, mediaBatchId, replyTo)
    }
}
