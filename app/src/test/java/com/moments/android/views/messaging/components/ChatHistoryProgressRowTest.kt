package com.moments.android.views.messaging.components

import org.junit.Assert.assertEquals
import org.junit.Test

class ChatHistoryProgressRowTest {
    private val progress = "row:synthetic:history-loading"
    private val messages = listOf("row:message:first", "row:message:second")

    @Test fun showingProgressDoesNotSignalThatHistoryHasArrived() {
        assertEquals(ChatListUpdateKind.RECONFIGURE_ROWS, normalizeTransactionKind(
            ChatListUpdateKind.APPEND_MESSAGES, null, messages, listOf(progress) + messages, emptySet(),
        ))
    }

    @Test fun hidingProgressDoesNotReuseThePreviousPrependIntent() {
        assertEquals(ChatListUpdateKind.RECONFIGURE_ROWS, normalizeTransactionKind(
            ChatListUpdateKind.PREPEND_HISTORY, null, listOf(progress) + messages, messages, emptySet(),
        ))
    }

    @Test fun actualHistoryStillRestoresItsAnchorWhenProgressDisappears() {
        assertEquals(ChatListUpdateKind.PREPEND_HISTORY, normalizeTransactionKind(
            ChatListUpdateKind.PREPEND_HISTORY, messages.first(), listOf(progress) + messages,
            listOf("row:message:older") + messages, emptySet(),
        ))
    }
}
