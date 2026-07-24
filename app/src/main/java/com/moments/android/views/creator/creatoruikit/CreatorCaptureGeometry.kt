package com.moments.android.views.creator.creatoruikit

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Dp
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import kotlin.math.round

/** Port de `CreatorCaptureGeometry.swift`; todas las medidas están en px del canvas Compose. */
const val CREATOR_MOMENTS_CAPTURE_ASPECT_RATIO = 9f / 16f
const val CREATOR_MOMENTS_CAPTURE_TOP_OFFSET_PX = 8f
const val CREATOR_MOMENTS_CAPTURE_SIDE_INSET_PX = 4f
val CREATOR_MOMENTS_STORY_OUTPUT_PIXEL_SIZE = Size(1080f, 1920f)
val storyViewerCanvasCornerRadius: Dp get() = FeedMomentCardLayout.storyCanvasCornerRadius

object CreatorMomentsCameraChromeInsets {
    const val topPx = 58f
    const val bottomPx = 62f
    const val horizontalPx = 52f
}

fun creatorMomentsAspectRect(aspectRatio: Float, inSize: Size): Rect {
    if (inSize.width <= 0f || inSize.height <= 0f || aspectRatio <= 0f) return Rect.Zero
    val candidateHeight = inSize.width / aspectRatio
    return if (candidateHeight <= inSize.height) {
        Rect(0f, (inSize.height - candidateHeight) / 2f, inSize.width, (inSize.height + candidateHeight) / 2f)
    } else {
        val width = inSize.height * aspectRatio
        Rect((inSize.width - width) / 2f, 0f, (inSize.width + width) / 2f, inSize.height)
    }
}

fun creatorMomentsCaptureRect(inSize: Size, topInsetPx: Float, bottomInsetPx: Float): Rect {
    val availableWidth = (inSize.width - CREATOR_MOMENTS_CAPTURE_SIDE_INSET_PX * 2f).coerceAtLeast(0f)
    val desiredHeight = availableWidth / CREATOR_MOMENTS_CAPTURE_ASPECT_RATIO
    val maximumHeight = (inSize.height - CREATOR_MOMENTS_CAPTURE_TOP_OFFSET_PX - bottomInsetPx - 20f).coerceAtLeast(0f)
    val height = minOf(desiredHeight, maximumHeight)
    val width = height * CREATOR_MOMENTS_CAPTURE_ASPECT_RATIO
    return Rect(
        left = (inSize.width - width) / 2f,
        top = CREATOR_MOMENTS_CAPTURE_TOP_OFFSET_PX,
        right = (inSize.width + width) / 2f,
        bottom = CREATOR_MOMENTS_CAPTURE_TOP_OFFSET_PX + height,
    )
}

fun creatorMomentsLensInterfaceSafeArea(canvasSize: Size): Rect = Rect(
    left = CreatorMomentsCameraChromeInsets.horizontalPx,
    top = CreatorMomentsCameraChromeInsets.topPx,
    right = (canvasSize.width - CreatorMomentsCameraChromeInsets.horizontalPx).coerceAtLeast(0f),
    bottom = (canvasSize.height - CreatorMomentsCameraChromeInsets.bottomPx).coerceAtLeast(0f),
)

fun creatorMomentsStoryOutputResolution(canvasSize: Size): Size {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return CREATOR_MOMENTS_STORY_OUTPUT_PIXEL_SIZE
    val scale = CREATOR_MOMENTS_STORY_OUTPUT_PIXEL_SIZE.width / canvasSize.width
    return Size(round(canvasSize.width * scale), round(canvasSize.height * scale))
}

fun storyViewerCaptureRect(inSize: Size, safeAreaTopPx: Float, safeAreaBottomPx: Float): Rect {
    val base = creatorMomentsCaptureRect(inSize, safeAreaTopPx, safeAreaBottomPx)
    return Rect(base.left, base.top + safeAreaTopPx, base.right, base.bottom + safeAreaTopPx)
}
