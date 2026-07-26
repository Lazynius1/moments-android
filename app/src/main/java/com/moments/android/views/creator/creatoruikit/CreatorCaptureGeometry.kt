package com.moments.android.views.creator.creatoruikit

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moments.android.views.feed.moments.FeedMomentCardLayout
import kotlin.math.round

/**
 * Port de `CreatorCaptureGeometry.swift`.
 * Los offsets iOS están en points → aquí se convierten con [Density] (`4.dp` ≡ `4pt`).
 */
val CREATOR_MOMENTS_CAPTURE_ASPECT_RATIO = 9f / 16f
val CREATOR_MOMENTS_CAPTURE_TOP_OFFSET = 8.dp
val CREATOR_MOMENTS_CAPTURE_SIDE_INSET = 4.dp
val CREATOR_MOMENTS_CAPTURE_BOTTOM_SLACK = 20.dp
val CREATOR_MOMENTS_STORY_OUTPUT_PIXEL_SIZE = Size(1080f, 1920f)
val storyViewerCanvasCornerRadius: Dp get() = FeedMomentCardLayout.storyCanvasCornerRadius

/** ≡ `CreatorMomentsCameraChromeInsets` (points → dp). */
object CreatorMomentsCameraChromeInsets {
    val top = 58.dp
    val bottom = 62.dp
    val horizontal = 52.dp
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

fun creatorMomentsCaptureRect(
    inSize: Size,
    topInsetPx: Float,
    bottomInsetPx: Float,
    density: Density,
): Rect {
    @Suppress("UNUSED_VARIABLE")
    val unusedTopInset = topInsetPx // iOS también recibe topInset pero no lo usa en el cálculo
    val sideInsetPx = with(density) { CREATOR_MOMENTS_CAPTURE_SIDE_INSET.toPx() }
    val topOffsetPx = with(density) { CREATOR_MOMENTS_CAPTURE_TOP_OFFSET.toPx() }
    val bottomSlackPx = with(density) { CREATOR_MOMENTS_CAPTURE_BOTTOM_SLACK.toPx() }
    val availableWidth = (inSize.width - sideInsetPx * 2f).coerceAtLeast(0f)
    val desiredHeight = availableWidth / CREATOR_MOMENTS_CAPTURE_ASPECT_RATIO
    val maximumHeight = (inSize.height - topOffsetPx - bottomInsetPx - bottomSlackPx).coerceAtLeast(0f)
    val height = minOf(desiredHeight, maximumHeight)
    val width = height * CREATOR_MOMENTS_CAPTURE_ASPECT_RATIO
    return Rect(
        left = (inSize.width - width) / 2f,
        top = topOffsetPx,
        right = (inSize.width + width) / 2f,
        bottom = topOffsetPx + height,
    )
}

fun creatorMomentsLensInterfaceSafeArea(canvasSize: Size, density: Density): Rect {
    val h = with(density) { CreatorMomentsCameraChromeInsets.horizontal.toPx() }
    val t = with(density) { CreatorMomentsCameraChromeInsets.top.toPx() }
    val b = with(density) { CreatorMomentsCameraChromeInsets.bottom.toPx() }
    return Rect(
        left = h,
        top = t,
        right = (canvasSize.width - h).coerceAtLeast(0f),
        bottom = (canvasSize.height - b).coerceAtLeast(0f),
    )
}

fun creatorMomentsStoryOutputResolution(canvasSize: Size): Size {
    if (canvasSize.width <= 0f || canvasSize.height <= 0f) return CREATOR_MOMENTS_STORY_OUTPUT_PIXEL_SIZE
    val scale = CREATOR_MOMENTS_STORY_OUTPUT_PIXEL_SIZE.width / canvasSize.width
    return Size(round(canvasSize.width * scale), round(canvasSize.height * scale))
}

fun storyViewerCaptureRect(
    inSize: Size,
    safeAreaTopPx: Float,
    safeAreaBottomPx: Float,
    density: Density,
): Rect {
    val base = creatorMomentsCaptureRect(inSize, safeAreaTopPx, safeAreaBottomPx, density)
    return Rect(base.left, base.top + safeAreaTopPx, base.right, base.bottom + safeAreaTopPx)
}
