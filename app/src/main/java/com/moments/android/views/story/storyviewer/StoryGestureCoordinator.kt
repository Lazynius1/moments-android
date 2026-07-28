package com.moments.android.views.story.storyviewer

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import com.moments.android.views.story.StoryDeckGestureGate

/** Port de `Views/story/StoryViewer/StoryGestureCoordinator.swift`. */
enum class StoryGestureIntent {
    DECK_SWIPE,
    STORY_NAVIGATION_TAP,
    HOLD_PAUSE,
    REPLY_SWIPE,
    REVEAL_SCRATCH,
    INTERACTIVE_STICKER_TAP,
    INTERACTIVE_STICKER_PAN,
    CHAIN_CONTROL_TAP,
}

enum class StoryGestureSuppressionScope(val level: Int) {
    ALLOW_ALL(0),
    SUPPRESS_DECK(1),
    SUPPRESS_STORY_NAVIGATION(2),
    SUPPRESS_VIEWER_GESTURES(3),
}

data class StoryGestureRegion(
    val id: String,
    val rect: Rect,
    val intents: Set<StoryGestureIntent>,
    val suppressionScope: StoryGestureSuppressionScope,
)

/**
 * Árbitro único de gestos del visor (tap, hold, deck, reply, stickers, reveal).
 * ≡ `struct StoryGestureCoordinator` en Swift.
 */
class StoryGestureCoordinator {
    companion object {
        const val NAVIGATION_SIDE_WIDTH_FRACTION = 0.20f
        const val MIN_NAVIGATION_SIDE_WIDTH = 64f
        const val REVEAL_SIDE_PASSTHROUGH_FRACTION = 0.14f
    }

    val topProtectedHeight = 180f
    val topRightProtectedInset = 120f
    val topRightProtectedHeight = 220f
    val bottomProtectedInset = 170f

    fun isInTopProtectedChrome(point: Offset, screenSize: Size): Boolean {
        if (point.y < topProtectedHeight) return true
        if (point.y < topRightProtectedHeight && point.x > screenSize.width - topRightProtectedInset) {
            return true
        }
        return false
    }

    fun isInBottomProtectedChrome(point: Offset, screenSize: Size): Boolean =
        point.y > screenSize.height - bottomProtectedInset

    fun navigationSideWidth(canvasWidth: Float): Float =
        maxOf(canvasWidth * NAVIGATION_SIDE_WIDTH_FRACTION, MIN_NAVIGATION_SIDE_WIDTH)

    fun leftNavigationFrame(canvasRect: Rect): Rect {
        val w = navigationSideWidth(canvasRect.width)
        return Rect(
            left = canvasRect.left,
            top = canvasRect.top,
            right = canvasRect.left + w,
            bottom = canvasRect.bottom,
        )
    }

    fun rightNavigationFrame(canvasRect: Rect): Rect {
        val w = navigationSideWidth(canvasRect.width)
        return Rect(
            left = canvasRect.right - w,
            top = canvasRect.top,
            right = canvasRect.right,
            bottom = canvasRect.bottom,
        )
    }

    fun isInNavigationEdgeBand(point: Offset, canvasRect: Rect): Boolean =
        leftNavigationFrame(canvasRect).contains(point) || rightNavigationFrame(canvasRect).contains(point)

    fun region(
        point: Offset,
        regions: List<StoryGestureRegion>,
        intent: StoryGestureIntent? = null,
    ): StoryGestureRegion? = regions.firstOrNull { region ->
        if (!region.rect.contains(point)) return@firstOrNull false
        if (intent == null) true else intent in region.intents
    }

    fun shouldAllowDeckSwipeStart(
        point: Offset,
        screenRect: Rect,
        regions: List<StoryGestureRegion>,
        gate: StoryDeckGestureGate?,
    ): Boolean {
        if (gate?.suppressDeckNavigation == true) return false
        val leftBand = Rect(
            left = screenRect.left,
            top = screenRect.top,
            right = screenRect.left + navigationSideWidth(screenRect.width),
            bottom = screenRect.bottom,
        )
        val rightBand = Rect(
            left = screenRect.right - navigationSideWidth(screenRect.width),
            top = screenRect.top,
            right = screenRect.right,
            bottom = screenRect.bottom,
        )
        if (!leftBand.contains(point) && !rightBand.contains(point)) return false
        val hit = region(point, regions, StoryGestureIntent.DECK_SWIPE)
        return if (hit != null) {
            hit.suppressionScope.level < StoryGestureSuppressionScope.SUPPRESS_DECK.level
        } else {
            true
        }
    }

    fun shouldAllowHoldStart(
        point: Offset,
        screenSize: Size,
        canvasRect: Rect,
        regions: List<StoryGestureRegion>,
        gate: StoryDeckGestureGate?,
        isKeyboardVisible: Boolean,
        overlaysBlocked: Boolean,
    ): Boolean {
        if (isKeyboardVisible || overlaysBlocked) return false
        if (gate?.suppressViewerGestures == true || gate?.suppressStoryNavigationGestures == true) return false
        if (isInTopProtectedChrome(point, screenSize)) return false
        if (isInBottomProtectedChrome(point, screenSize)) return false
        if (isInNavigationEdgeBand(point, canvasRect)) return false
        return region(point, regions, StoryGestureIntent.HOLD_PAUSE) == null
    }

    fun shouldAllowUnifiedViewerDragStart(
        point: Offset,
        screenSize: Size,
        canvasRect: Rect,
        regions: List<StoryGestureRegion>,
        gate: StoryDeckGestureGate?,
        overlaysBlocked: Boolean,
    ): Boolean {
        if (overlaysBlocked) return false
        if (gate?.suppressViewerGestures == true || gate?.suppressStoryNavigationGestures == true) return false
        if (isInTopProtectedChrome(point, screenSize)) return false
        if (isInNavigationEdgeBand(point, canvasRect)) return false
        return region(point, regions, StoryGestureIntent.REPLY_SWIPE) == null
    }

    fun shouldSuppressNavigationTap(
        point: Offset,
        canvasRect: Rect,
        regions: List<StoryGestureRegion>,
        gate: StoryDeckGestureGate?,
    ): Boolean {
        if (!leftNavigationFrame(canvasRect).contains(point) &&
            !rightNavigationFrame(canvasRect).contains(point)
        ) {
            return true
        }
        if (gate?.suppressStoryNavigationGestures == true || gate?.suppressViewerGestures == true) {
            return true
        }
        val hit = region(point, regions, StoryGestureIntent.STORY_NAVIGATION_TAP)
        return if (hit != null) {
            hit.suppressionScope.level >= StoryGestureSuppressionScope.SUPPRESS_STORY_NAVIGATION.level
        } else {
            false
        }
    }

    fun isInTopOrBottomProtectedChrome(point: Offset, screenSize: Size): Boolean {
        if (point.y < topProtectedHeight) return true
        if (point.y < topRightProtectedHeight && point.x > screenSize.width - topRightProtectedInset) {
            return true
        }
        if (point.y > screenSize.height - bottomProtectedInset) return true
        return false
    }
}
