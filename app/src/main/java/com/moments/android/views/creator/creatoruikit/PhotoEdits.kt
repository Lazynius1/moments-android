package com.moments.android.views.creator.creatoruikit

import androidx.compose.ui.graphics.Color
import com.moments.android.R
import com.moments.android.services.content.FilterService
import kotlin.math.abs

/** ≡ iOS `PhotoEditTab`. */
enum class PhotoEditTab { FILTER, EDIT }

/** ≡ iOS `TiltShiftMode`. */
enum class TiltShiftMode { RADIAL, LINEAR }

/** ≡ iOS `PhotoTintTarget`. */
enum class PhotoTintTarget { SHADOWS, HIGHLIGHTS }

/** ≡ iOS `PhotoTintColor`. */
enum class PhotoTintColor {
    YELLOW, ORANGE, RED, PINK, PURPLE, BLUE, CYAN, GREEN;

    val color: Color
        get() = when (this) {
            YELLOW -> Color(0.96f, 0.84f, 0.22f)
            ORANGE -> Color(0.96f, 0.55f, 0.13f)
            RED -> Color(0.90f, 0.22f, 0.21f)
            PINK -> Color(0.95f, 0.38f, 0.62f)
            PURPLE -> Color(0.56f, 0.27f, 0.68f)
            BLUE -> Color(0.20f, 0.48f, 0.86f)
            CYAN -> Color(0.18f, 0.80f, 0.76f)
            GREEN -> Color(0.22f, 0.80f, 0.45f)
        }
}

/** ≡ iOS `PhotoAdjustAxis`. */
enum class PhotoAdjustAxis {
    STRAIGHTEN, VERTICAL, HORIZONTAL;

    val titleRes: Int
        get() = when (this) {
            STRAIGHTEN -> R.string.creator_adjust_straighten
            VERTICAL -> R.string.creator_adjust_vertical
            HORIZONTAL -> R.string.creator_adjust_horizontal
        }
}

/** ≡ iOS `PhotoEditTool`. */
enum class PhotoEditTool {
    ADJUST, LUX, BRIGHTNESS, CONTRAST, TEXTURE, WARMTH, SATURATION, COLOR,
    FADE, HIGHLIGHTS, SHADOWS, VIGNETTE, BLUR, SHARPEN;

    val titleRes: Int
        get() = when (this) {
            ADJUST -> R.string.creator_adjust_adjust
            LUX -> R.string.creator_adjust_lux
            BRIGHTNESS -> R.string.creator_adjust_brightness
            CONTRAST -> R.string.creator_adjust_contrast
            TEXTURE -> R.string.creator_adjust_texture
            WARMTH -> R.string.creator_adjust_warmth
            SATURATION -> R.string.creator_adjust_saturation
            COLOR -> R.string.creator_adjust_color
            FADE -> R.string.creator_adjust_fade
            HIGHLIGHTS -> R.string.creator_adjust_highlights
            SHADOWS -> R.string.creator_adjust_shadows
            VIGNETTE -> R.string.creator_adjust_vignette
            BLUR -> R.string.creator_adjust_blur
            SHARPEN -> R.string.creator_adjust_sharpen
        }

    val usesZeroToHundred: Boolean
        get() = when (this) {
            LUX, TEXTURE, FADE, VIGNETTE, SHARPEN, BLUR, COLOR -> true
            else -> false
        }
}

/** ≡ iOS `PhotoEdits`. */
data class PhotoEdits(
    val filter: FilterService.FilterType = FilterService.FilterType.NORMAL,
    val filterIntensity: Double = 1.0,
    val straighten: Double = 0.0,
    val verticalPerspective: Double = 0.0,
    val horizontalPerspective: Double = 0.0,
    val lux: Double = 0.0,
    val brightness: Double = 0.0,
    val contrast: Double = 0.0,
    val texture: Double = 0.0,
    val warmth: Double = 0.0,
    val saturation: Double = 0.0,
    val fade: Double = 0.0,
    val highlights: Double = 0.0,
    val shadows: Double = 0.0,
    val vignette: Double = 0.0,
    val sharpen: Double = 0.0,
    val tintTarget: PhotoTintTarget = PhotoTintTarget.SHADOWS,
    val shadowTint: PhotoTintColor? = null,
    val highlightTint: PhotoTintColor? = null,
    val shadowTintAmount: Double = 0.5,
    val highlightTintAmount: Double = 0.5,
    val tiltShiftMode: TiltShiftMode? = null,
    val tiltShiftAmount: Double = 0.5,
    val tiltShiftCenterX: Double = 0.5,
    val tiltShiftCenterY: Double = 0.5,
    val tiltShiftRadius: Double = 0.28,
    val tiltShiftAngle: Double = 0.0,
) {
    val hasGeometry: Boolean
        get() = abs(straighten) > 0.001 ||
            abs(verticalPerspective) > 0.001 ||
            abs(horizontalPerspective) > 0.001

    val isIdentity: Boolean
        get() = filter == FilterService.FilterType.NORMAL &&
            !hasGeometry &&
            lux == 0.0 &&
            abs(brightness) < 0.001 &&
            abs(contrast) < 0.001 &&
            texture == 0.0 &&
            abs(warmth) < 0.001 &&
            abs(saturation) < 0.001 &&
            fade == 0.0 &&
            abs(highlights) < 0.001 &&
            abs(shadows) < 0.001 &&
            vignette == 0.0 &&
            sharpen == 0.0 &&
            shadowTint == null &&
            highlightTint == null &&
            (tiltShiftMode == null || tiltShiftAmount < 0.001)

    fun isApplied(tool: PhotoEditTool): Boolean = when (tool) {
        PhotoEditTool.ADJUST -> hasGeometry
        PhotoEditTool.LUX -> lux > 0.001
        PhotoEditTool.BRIGHTNESS -> abs(brightness) > 0.001
        PhotoEditTool.CONTRAST -> abs(contrast) > 0.001
        PhotoEditTool.TEXTURE -> texture > 0.001
        PhotoEditTool.WARMTH -> abs(warmth) > 0.001
        PhotoEditTool.SATURATION -> abs(saturation) > 0.001
        PhotoEditTool.COLOR -> shadowTint != null || highlightTint != null
        PhotoEditTool.FADE -> fade > 0.001
        PhotoEditTool.HIGHLIGHTS -> abs(highlights) > 0.001
        PhotoEditTool.SHADOWS -> abs(shadows) > 0.001
        PhotoEditTool.VIGNETTE -> vignette > 0.001
        PhotoEditTool.BLUR -> tiltShiftMode != null && tiltShiftAmount > 0.001
        PhotoEditTool.SHARPEN -> sharpen > 0.001
    }

    fun canReset(tab: PhotoEditTab, tool: PhotoEditTool?, axis: PhotoAdjustAxis): Boolean = when (tab) {
        PhotoEditTab.FILTER -> filter != FilterService.FilterType.NORMAL
        PhotoEditTab.EDIT -> when (tool) {
            null -> false
            PhotoEditTool.ADJUST -> when (axis) {
                PhotoAdjustAxis.STRAIGHTEN -> abs(straighten) > 0.001
                PhotoAdjustAxis.VERTICAL -> abs(verticalPerspective) > 0.001
                PhotoAdjustAxis.HORIZONTAL -> abs(horizontalPerspective) > 0.001
            }
            PhotoEditTool.COLOR -> if (tintTarget == PhotoTintTarget.SHADOWS) {
                shadowTint != null
            } else {
                highlightTint != null
            }
            else -> isApplied(tool)
        }
    }

    fun reset(tab: PhotoEditTab, tool: PhotoEditTool?, axis: PhotoAdjustAxis): PhotoEdits = when (tab) {
        PhotoEditTab.FILTER -> copy(filter = FilterService.FilterType.NORMAL, filterIntensity = 1.0)
        PhotoEditTab.EDIT -> when (tool) {
            null -> this
            PhotoEditTool.ADJUST -> when (axis) {
                PhotoAdjustAxis.STRAIGHTEN -> copy(straighten = 0.0)
                PhotoAdjustAxis.VERTICAL -> copy(verticalPerspective = 0.0)
                PhotoAdjustAxis.HORIZONTAL -> copy(horizontalPerspective = 0.0)
            }
            PhotoEditTool.LUX -> copy(lux = 0.0)
            PhotoEditTool.BRIGHTNESS -> copy(brightness = 0.0)
            PhotoEditTool.CONTRAST -> copy(contrast = 0.0)
            PhotoEditTool.TEXTURE -> copy(texture = 0.0)
            PhotoEditTool.WARMTH -> copy(warmth = 0.0)
            PhotoEditTool.SATURATION -> copy(saturation = 0.0)
            PhotoEditTool.COLOR -> if (tintTarget == PhotoTintTarget.SHADOWS) {
                copy(shadowTint = null, shadowTintAmount = 0.5)
            } else {
                copy(highlightTint = null, highlightTintAmount = 0.5)
            }
            PhotoEditTool.FADE -> copy(fade = 0.0)
            PhotoEditTool.HIGHLIGHTS -> copy(highlights = 0.0)
            PhotoEditTool.SHADOWS -> copy(shadows = 0.0)
            PhotoEditTool.VIGNETTE -> copy(vignette = 0.0)
            PhotoEditTool.BLUR -> copy(
                tiltShiftMode = null,
                tiltShiftAmount = 0.5,
                tiltShiftCenterX = 0.5,
                tiltShiftCenterY = 0.5,
                tiltShiftRadius = 0.28,
                tiltShiftAngle = 0.0,
            )
            PhotoEditTool.SHARPEN -> copy(sharpen = 0.0)
        }
    }
}
