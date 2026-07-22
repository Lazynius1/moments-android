package com.moments.android.views.feed.moments

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Port de `MomentCarouselLayoutRules.swift`. */
enum class MomentCarouselPresentationMode {
    Fill,
    FitWithBlur,
}

object MomentCarouselLayoutRules {
    private const val horizontalTolerance = 0.035f
    private const val squareCutoff = 1.15f

    val mediaCornerRadius = 12.dp
    const val minAspectRatio = 0.5f
    const val maxAspectRatio = 1.91f

    fun presentationMode(
        mediaAspectRatio: Float,
        canvasAspectRatio: Float,
    ): MomentCarouselPresentationMode {
        if (!mediaAspectRatio.isFinite() || mediaAspectRatio <= 0f ||
            !canvasAspectRatio.isFinite() || canvasAspectRatio <= 0f
        ) {
            return MomentCarouselPresentationMode.Fill
        }

        val clearlyWiderThanCanvas = mediaAspectRatio > (canvasAspectRatio + horizontalTolerance)
        val isClearlyLandscape = mediaAspectRatio > squareCutoff
        return if (clearlyWiderThanCanvas && isClearlyLandscape) {
            MomentCarouselPresentationMode.FitWithBlur
        } else {
            MomentCarouselPresentationMode.Fill
        }
    }

    fun aspectRatioValue(raw: String?): Float {
        if (raw.isNullOrBlank()) return 1f
        val v = if (raw.contains(":")) {
            val parts = raw.split(":")
            val w = parts.getOrNull(0)?.toFloatOrNull() ?: 1f
            val h = parts.getOrNull(1)?.toFloatOrNull() ?: 1f
            if (h != 0f) w / h else 1f
        } else {
            raw.toFloatOrNull() ?: 1f
        }
        return v.coerceIn(minAspectRatio, maxAspectRatio)
    }

    /**
     * Ratio de display en el feed (iOS ModernPostCardView):
     * todo más vertical que 4:5 se cropea a 0.8.
     */
    fun feedDisplayAspectRatio(raw: Float): Float {
        val safe = if (raw > 0f && raw.isFinite()) raw else 1f
        return if (safe < 0.8f) 0.8f else safe
    }
}

object MomentCarouselIndicatorStyle {
    val dotWidth = 6.dp
    val dotHeight = 4.dp
    val spacing = 6.dp
    const val activeScale = 1.15f
    const val inactiveOpacity = 0.35f

    private val palette = listOf(
        Color(0xFF5B2C6F),
        Color(0xFF007BFF),
        Color(0xFF40DFCF),
        Color(0xFFFF6B6B),
        Color(0xFF4ECDC4),
        Color(0xFF45B7D1),
        Color(0xFF96CEB4),
        Color(0xFFFECA57),
    )

    val inactiveColor: Color get() = Color.White.copy(alpha = inactiveOpacity)

    fun activeColor(index: Int): Color = palette[index % palette.size]
}

object FeedMomentCardLayout {
    val listHorizontalPadding = 4.dp
    val headerHorizontalPadding = 8.dp
    val actionRowHorizontalPadding = 4.dp
    val captionHorizontalPadding = 8.dp
    val mediaCornerRadius = MomentCarouselLayoutRules.mediaCornerRadius
    val storyCanvasCornerRadius = mediaCornerRadius
    val peekCornerRadius = mediaCornerRadius

    /** iOS `mediaContentWidth` = screenWidth - listHorizontalPadding * 2. */
    fun mediaContentWidth(screenWidthDp: Float): Float =
        maxOf(screenWidthDp - listHorizontalPadding.value * 2f, 1f)
}
