package com.moments.android.views.story.storyviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.moments.android.views.story.StoryDeckGestureGate

/** Port de `Views/story/StoryViewer/StoryGestureCoordinator.swift`. */
enum class StoryGestureIntent {
    DECK_SWIPE, STORY_NAVIGATION_TAP, HOLD_PAUSE, REPLY_SWIPE,
    REVEAL_SCRATCH, INTERACTIVE_STICKER_TAP, INTERACTIVE_STICKER_PAN, CHAIN_CONTROL_TAP,
}

enum class StoryGestureSuppressionScope(val level: Int) {
    ALLOW_ALL(0), SUPPRESS_DECK(1), SUPPRESS_STORY_NAVIGATION(2), SUPPRESS_VIEWER_GESTURES(3),
}

data class StoryGestureRegion(
    val id: String,
    val rect: Rect,
    val intents: Set<StoryGestureIntent>,
    val suppressionScope: StoryGestureSuppressionScope,
)

class StoryGestureCoordinator {
    companion object {
        const val NAVIGATION_SIDE_WIDTH_FRACTION = 0.20f
        const val MIN_NAVIGATION_SIDE_WIDTH = 64f
        const val REVEAL_SIDE_PASSTHROUGH_FRACTION = 0.14f
    }

    private val topProtectedHeight = 180f
    private val topRightProtectedInset = 120f
    private val topRightProtectedHeight = 220f
    private val bottomProtectedInset = 170f

    fun isInTopProtectedChrome(point: Offset, screenSize: Size) =
        point.y < topProtectedHeight || (point.y < topRightProtectedHeight && point.x > screenSize.width - topRightProtectedInset)

    fun isInBottomProtectedChrome(point: Offset, screenSize: Size) = point.y > screenSize.height - bottomProtectedInset
    fun navigationSideWidth(canvasWidth: Float) = maxOf(canvasWidth * NAVIGATION_SIDE_WIDTH_FRACTION, MIN_NAVIGATION_SIDE_WIDTH)
    fun leftNavigationFrame(canvasRect: Rect) = Rect(canvasRect.left, canvasRect.top, canvasRect.left + navigationSideWidth(canvasRect.width), canvasRect.bottom)
    fun rightNavigationFrame(canvasRect: Rect) = Rect(canvasRect.right - navigationSideWidth(canvasRect.width), canvasRect.top, canvasRect.right, canvasRect.bottom)
    fun isInNavigationEdgeBand(point: Offset, canvasRect: Rect) = leftNavigationFrame(canvasRect).contains(point) || rightNavigationFrame(canvasRect).contains(point)

    fun region(point: Offset, regions: List<StoryGestureRegion>, intent: StoryGestureIntent? = null) =
        regions.firstOrNull { it.rect.contains(point) && (intent == null || intent in it.intents) }

    fun shouldAllowDeckSwipeStart(point: Offset, screenRect: Rect, regions: List<StoryGestureRegion>, gate: StoryDeckGestureGate?): Boolean {
        if (gate?.suppressDeckNavigation == true) return false
        if (!isInNavigationEdgeBand(point, screenRect)) return false
        return region(point, regions, StoryGestureIntent.DECK_SWIPE)?.suppressionScope?.level ?: -1 < StoryGestureSuppressionScope.SUPPRESS_DECK.level
    }

    fun shouldAllowHoldStart(point: Offset, screenSize: Size, canvasRect: Rect, regions: List<StoryGestureRegion>, gate: StoryDeckGestureGate?, isKeyboardVisible: Boolean, overlaysBlocked: Boolean): Boolean {
        if (isKeyboardVisible || overlaysBlocked || gate?.suppressViewerGestures == true || gate?.suppressStoryNavigationGestures == true) return false
        return !isInTopProtectedChrome(point, screenSize) && !isInBottomProtectedChrome(point, screenSize) && !isInNavigationEdgeBand(point, canvasRect) && region(point, regions, StoryGestureIntent.HOLD_PAUSE) == null
    }

    fun shouldSuppressNavigationTap(point: Offset, canvasRect: Rect, regions: List<StoryGestureRegion>, gate: StoryDeckGestureGate?): Boolean {
        if (!isInNavigationEdgeBand(point, canvasRect)) return true
        if (gate?.suppressStoryNavigationGestures == true || gate?.suppressViewerGestures == true) return true
        return (region(point, regions, StoryGestureIntent.STORY_NAVIGATION_TAP)?.suppressionScope?.level ?: -1) >= StoryGestureSuppressionScope.SUPPRESS_STORY_NAVIGATION.level
    }
}
