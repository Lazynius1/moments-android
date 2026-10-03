package com.moments.android.views.messaging.components

import androidx.compose.ui.geometry.Rect
import com.moments.android.views.messaging.core.EnhancedMessage
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatMessageMenuLayoutTest {
    private val metrics = MenuLayoutMetrics(36f, 16f, 10f, 64f, 250f, 16f, 300f, 240f)

    private fun layout(bottomInset: Float, height: Float = 60f, expanded: Boolean = false): ChatMessageMenuLayout = menuLayout(
        ChatMessageMenuSelection(
            rowId = "row:test",
            message = EnhancedMessage("test", "conversation", "me"),
            anchorFrame = Rect(100f, 600f - height, 350f, 600f),
            isOutgoing = true,
        ),
        rowCount = 7,
        containerWidth = 400f,
        containerHeight = 800f,
        topMarginPx = 36f,
        bottomMarginPx = bottomInset,
        metrics = metrics,
        reactionsExpanded = expanded,
    )


    @Test fun messageStaysInPlaceWhenTheWholeMenuFits() {
        for (outgoing in listOf(true, false)) {
            val result = menuLayout(
                ChatMessageMenuSelection(
                    rowId = "row:test",
                    message = EnhancedMessage("test", "conversation", "me"),
                    anchorFrame = Rect(100f, 250f, 350f, 310f),
                    isOutgoing = outgoing,
                ),
                rowCount = 7,
                containerWidth = 400f,
                containerHeight = 800f,
                topMarginPx = 36f,
                bottomMarginPx = 36f,
                metrics = metrics,
                reactionsExpanded = false,
            )
            assertTrue(result.messageOffsetY == 0f)
        }
    }

    @Test fun actionsAndReactionsStayAboveOpenKeyboard() {
        val result = layout(340f)
        assertTrue(result.menuCenter.y + result.actionsMaxHeight / 2f <= 460f)
        assertTrue(result.reactionsCenter.y - 32f >= 36f)
        assertTrue(result.messageOffsetY < 0f)
    }

    @Test fun tallMediaUsesScrollableActionsAboveKeyboard() {
        val result = layout(340f, height = 300f)
        assertTrue(result.actionsMaxHeight < 7 * 36f + 16f)
        assertTrue(result.actionsMaxHeight >= 52f)
        assertTrue(result.menuCenter.y + result.actionsMaxHeight / 2f <= 460f)
    }

    @Test fun closedKeyboardLeavesAllActionsVisible() {
        val result = layout(36f)
        assertTrue(result.actionsMaxHeight == 268f)
        assertTrue(result.menuCenter.y + result.actionsMaxHeight / 2f <= 764f)
    }

    @Test fun expandedReactionsStayAboveKeyboard() {
        val result = layout(340f, expanded = true)
        assertTrue(result.reactionsCenter.y - 125f >= 36f)
        assertTrue(result.reactionsCenter.y + 125f <= 460f)
    }
    @Test fun connectorFollowsMessageWidthInsteadOfFixedRailEnd() {
        val shortMessage = reactionConnectorSourceX(Rect(260f, 0f, 380f, 40f), 40f, 340f, 23f, true, 400f)
        val longMessage = reactionConnectorSourceX(Rect(100f, 0f, 380f, 40f), 40f, 340f, 23f, true, 400f)
        assertTrue(shortMessage == 197f)
        assertTrue(longMessage == 37f)
    }

    @Test fun wideMessageConnectorCanReachRailEdgesWithoutBeingPushedOverMessage() {
        assertTrue(reactionConnectorSourceX(Rect(0f, 0f, 390f, 40f), 40f, 340f, 23f, true, 400f) == 0f)
        assertTrue(reactionConnectorSourceX(Rect(0f, 0f, 390f, 40f), 40f, 340f, 23f, false, 400f) == 337f)
    }

    @Test fun wideReceivedConnectorKeepsItsWholeOutlineInsideViewport() {
        val railLeft = 76f
        val source = reactionConnectorSourceX(Rect(48f, 0f, 390f, 40f), railLeft, 320f, 32f, false, 400f)
        assertTrue(railLeft + source + 28f <= 396f)
    }

}
