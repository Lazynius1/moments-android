package com.moments.android.services.content

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Port de FilterService.swift — CIFilter → ColorMatrix.
 * Los nombres de filtro y categorías coinciden con iOS para Creator.
 */
object FilterService {

    enum class FilterCategory { BASIC, LOOK }

    enum class FilterType(val raw: String) {
        NORMAL("Normal"),
        VIVID("Vivid"),
        CHROME("Chrome"),
        FADE("Fade"),
        INSTANT("Instant"),
        MONO("Mono"),
        NOIR("Noir"),
        PROCESS("Process"),
        TONAL("Tonal"),
        TRANSFER("Transfer"),
        SEPIA("Sepia"),
        BLOOM("Bloom"),
        COCOA("Cocoa"),
        ARCTIC("Arctic"),
        EMBER("Ember"),
        DRIFT("Drift"),
        MUSE("Muse"),
        VELVET("Velvet"),
        SLATE("Slate"),
        HALO("Halo");

        val category: FilterCategory
            get() = when (this) {
                NORMAL, VIVID, CHROME, FADE, INSTANT, MONO, NOIR, PROCESS, TONAL, TRANSFER, SEPIA ->
                    FilterCategory.BASIC
                else -> FilterCategory.LOOK
            }

        companion object {
            fun from(raw: String?): FilterType =
                entries.firstOrNull { it.raw.equals(raw, ignoreCase = true) } ?: NORMAL
        }
    }

    val basicFilters: List<FilterType> get() = FilterType.entries.filter { it.category == FilterCategory.BASIC }
    val lookFilters: List<FilterType> get() = FilterType.entries.filter { it.category == FilterCategory.LOOK }

    fun applyFilter(type: FilterType, image: Bitmap, intensity: Double = 1.0): Bitmap {
        if (type == FilterType.NORMAL || intensity <= 0.0) return image
        val matrix = colorMatrixFor(type, intensity.coerceIn(0.0, 1.0)) ?: return image
        val output = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        paint.colorFilter = ColorMatrixColorFilter(matrix)
        canvas.drawBitmap(image, 0f, 0f, paint)
        return output
    }

    fun applyFilterToThumbnail(type: FilterType, image: Bitmap): Bitmap =
        applyFilter(type, image, intensity = 1.0)

    private fun colorMatrixFor(type: FilterType, intensity: Double): ColorMatrix? {
        val full = when (type) {
            FilterType.NORMAL -> return null
            FilterType.SEPIA -> ColorMatrix().apply { setSaturation(0f); setScale(1f, 0.95f, 0.82f, 1f) }
            FilterType.MONO, FilterType.TONAL -> ColorMatrix().apply { setSaturation(0f) }
            FilterType.NOIR -> ColorMatrix().apply {
                setSaturation(0f)
                setScale(1.1f, 1.1f, 1.1f, 1f)
            }
            FilterType.VIVID -> ColorMatrix().apply { setSaturation(1.35f) }
            FilterType.CHROME -> ColorMatrix(floatArrayOf(
                1.2f, 0.1f, 0.1f, 0f, 10f,
                0.05f, 1.15f, 0.05f, 0f, 5f,
                0.05f, 0.05f, 1.1f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ))
            FilterType.FADE -> ColorMatrix().apply {
                setSaturation(0.7f)
                setScale(1.05f, 1.02f, 1.0f, 1f)
            }
            FilterType.INSTANT -> ColorMatrix().apply {
                setSaturation(1.15f)
                setScale(1.08f, 1.0f, 0.92f, 1f)
            }
            FilterType.PROCESS -> ColorMatrix().apply {
                setSaturation(1.2f)
                setScale(0.95f, 1.05f, 1.1f, 1f)
            }
            FilterType.TRANSFER -> ColorMatrix().apply {
                setSaturation(0.85f)
                setScale(1.1f, 0.95f, 0.9f, 1f)
            }
            FilterType.BLOOM -> ColorMatrix().apply {
                setSaturation(0.92f)
                setScale(1.05f, 1.05f, 1.02f, 1f)
            }
            FilterType.COCOA -> ColorMatrix().apply {
                setSaturation(1.08f)
                setScale(1.1f, 0.98f, 0.85f, 1f)
            }
            FilterType.ARCTIC -> ColorMatrix().apply {
                setSaturation(0.88f)
                setScale(0.92f, 1.0f, 1.12f, 1f)
            }
            FilterType.EMBER -> ColorMatrix().apply {
                setSaturation(1.12f)
                setScale(1.15f, 1.0f, 0.88f, 1f)
            }
            FilterType.DRIFT -> ColorMatrix().apply {
                setSaturation(0.78f)
                setScale(0.95f, 1.0f, 1.05f, 1f)
            }
            FilterType.MUSE -> ColorMatrix().apply {
                setSaturation(1.02f)
                setScale(1.05f, 1.0f, 1.08f, 1f)
            }
            FilterType.VELVET -> ColorMatrix().apply {
                setSaturation(0.96f)
                setScale(0.95f, 0.92f, 1.05f, 1f)
            }
            FilterType.SLATE -> ColorMatrix().apply {
                setSaturation(0.52f)
                setScale(0.95f, 1.0f, 1.08f, 1f)
            }
            FilterType.HALO -> ColorMatrix().apply {
                setSaturation(1.06f)
                setScale(1.08f, 1.05f, 1.0f, 1f)
            }
        }
        if (intensity >= 0.999) return full
        // Mezcla con identidad según intensity (como blendFilteredImage iOS).
        val identity = ColorMatrix()
        val mixed = FloatArray(20)
        val a = full.array
        val b = identity.array
        for (i in 0 until 20) {
            mixed[i] = (b[i] + (a[i] - b[i]) * intensity.toFloat())
        }
        return ColorMatrix(mixed)
    }

    /**
     * ≡ iOS `FilterService.applyPhotoEdits`.
     * CIFilter → ColorMatrix / Canvas (sin Core Image).
     */
    fun applyPhotoEdits(edits: com.moments.android.views.creator.creatoruikit.PhotoEdits, image: Bitmap): Bitmap {
        if (edits.isIdentity) return image
        var working = image
        if (edits.hasGeometry) working = applyGeometry(edits, working)
        if (edits.filter != FilterType.NORMAL) {
            working = applyFilter(edits.filter, working, edits.filterIntensity)
        }
        return applyAdjustments(edits, working)
    }

    private fun applyGeometry(
        edits: com.moments.android.views.creator.creatoruikit.PhotoEdits,
        image: Bitmap,
    ): Bitmap {
        val w = image.width.toFloat()
        val h = image.height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val matrix = android.graphics.Matrix()
        matrix.postTranslate(-cx, -cy)
        if (abs(edits.straighten) > 0.001) {
            val angleDeg = (edits.straighten * 45).toFloat()
            val rad = Math.toRadians(angleDeg.toDouble())
            val cover = (abs(kotlin.math.sin(rad)) + abs(kotlin.math.cos(rad))).toFloat()
            matrix.postRotate(angleDeg)
            matrix.postScale(cover, cover)
        }
        matrix.postTranslate(cx, cy)
        if (abs(edits.verticalPerspective) > 0.001 || abs(edits.horizontalPerspective) > 0.001) {
            val v = (edits.verticalPerspective * w * 0.22).toFloat()
            val hh = (edits.horizontalPerspective * h * 0.22).toFloat()
            val src = floatArrayOf(0f, 0f, w, 0f, w, h, 0f, h)
            val dst = floatArrayOf(
                0f + v, 0f - hh,
                w - v, 0f + hh,
                w + v, h - hh,
                0f - v, h + hh,
            )
            val persp = android.graphics.Matrix()
            persp.setPolyToPoly(src, 0, dst, 0, 4)
            matrix.postConcat(persp)
        }
        val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(image, matrix, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private fun applyAdjustments(
        edits: com.moments.android.views.creator.creatoruikit.PhotoEdits,
        image: Bitmap,
    ): Bitmap {
        var working = image
        val matrix = ColorMatrix()
        var mutated = false

        if (edits.lux > 0.001) {
            val lux = edits.lux.toFloat()
            val luxM = ColorMatrix()
            luxM.setSaturation(1f + lux * 0.1f)
            luxM.setScale(1f + lux * 0.03f, 1f + lux * 0.03f, 1f + lux * 0.03f, 1f)
            matrix.postConcat(luxM)
            mutated = true
        }
        if (abs(edits.brightness) > 0.001 || abs(edits.contrast) > 0.001 || abs(edits.saturation) > 0.001) {
            val sat = 1f + edits.saturation.toFloat() * 0.55f
            val bright = edits.brightness.toFloat() * 0.28f * 255f
            val contrast = 1f + edits.contrast.toFloat() * 0.38f
            val translate = (1f - contrast) / 2f * 255f + bright
            val cm = ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, translate,
                    0f, contrast, 0f, 0f, translate,
                    0f, 0f, contrast, 0f, translate,
                    0f, 0f, 0f, 1f, 0f,
                ),
            )
            cm.setSaturation(sat)
            matrix.postConcat(cm)
            mutated = true
        }
        if (abs(edits.warmth) > 0.001) {
            val w = edits.warmth.toFloat()
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f + w * 0.12f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f - w * 0.10f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            mutated = true
        }
        if (abs(edits.highlights) > 0.001 || abs(edits.shadows) > 0.001) {
            val lift = edits.shadows.toFloat() * 0.18f * 255f
            val hi = edits.highlights.toFloat() * 0.12f
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f + hi, 0f, 0f, 0f, lift,
                        0f, 1f + hi, 0f, 0f, lift,
                        0f, 0f, 1f + hi, 0f, lift,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            mutated = true
        }
        if (edits.fade > 0.001) {
            val fade = edits.fade.toFloat()
            val lift = fade * 0.16f * 255f
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f - fade * 0.3f, 0f, 0f, 0f, lift,
                        0f, 1f - fade * 0.3f, 0f, 0f, lift,
                        0f, 0f, 1f - fade * 0.3f, 0f, lift,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            mutated = true
        }
        if (edits.texture > 0.001 || edits.sharpen > 0.001) {
            val amount = (edits.texture * 0.55 + edits.sharpen * 0.9).toFloat()
            matrix.postConcat(
                ColorMatrix(
                    floatArrayOf(
                        1f + amount * 0.15f, 0f, 0f, 0f, 0f,
                        0f, 1f + amount * 0.15f, 0f, 0f, 0f,
                        0f, 0f, 1f + amount * 0.15f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    ),
                ),
            )
            mutated = true
        }

        if (mutated) {
            val out = Bitmap.createBitmap(working.width, working.height, Bitmap.Config.ARGB_8888)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
            paint.colorFilter = ColorMatrixColorFilter(matrix)
            Canvas(out).drawBitmap(working, 0f, 0f, paint)
            working = out
        }

        edits.shadowTint?.let {
            working = applySplitToneApprox(working, it.color, edits.shadowTintAmount.toFloat(), highlights = false)
        }
        edits.highlightTint?.let {
            working = applySplitToneApprox(working, it.color, edits.highlightTintAmount.toFloat(), highlights = true)
        }

        if (edits.vignette > 0.001) {
            working = applyVignette(working, edits.vignette.toFloat())
        }
        if (edits.tiltShiftMode != null && edits.tiltShiftAmount > 0.001) {
            working = applyTiltShiftApprox(working, edits)
        }
        return working
    }

    private fun applySplitToneApprox(
        image: Bitmap,
        tint: androidx.compose.ui.graphics.Color,
        amount: Float,
        highlights: Boolean,
    ): Bitmap {
        val tr = (tint.red * 255f).toInt()
        val tg = (tint.green * 255f).toInt()
        val tb = (tint.blue * 255f).toInt()
        val strength = amount.coerceIn(0f, 1f) * 0.55f
        val pixels = IntArray(image.width * image.height)
        image.getPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        for (i in pixels.indices) {
            val p = pixels[i]
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            val luma = (0.299f * r + 0.587f * g + 0.114f * b) / 255f
            val mask = if (highlights) luma else 1f - luma
            val t = (mask * strength).coerceIn(0f, 1f)
            val nr = (r + (tr - r) * t).toInt().coerceIn(0, 255)
            val ng = (g + (tg - g) * t).toInt().coerceIn(0, 255)
            val nb = (b + (tb - b) * t).toInt().coerceIn(0, 255)
            pixels[i] = (p and 0xFF000000.toInt()) or (nr shl 16) or (ng shl 8) or nb
        }
        val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        out.setPixels(pixels, 0, image.width, 0, 0, image.width, image.height)
        return out
    }

    private fun applyVignette(image: Bitmap, amount: Float): Bitmap {
        val out = image.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val cx = image.width / 2f
        val cy = image.height / 2f
        val radius = max(cx, cy) * 1.15f
        val paint = Paint()
        paint.shader = android.graphics.RadialGradient(
            cx, cy, radius,
            intArrayOf(android.graphics.Color.TRANSPARENT, android.graphics.Color.argb((amount * 180).toInt().coerceIn(0, 180), 0, 0, 0)),
            floatArrayOf(0.45f, 1f),
            android.graphics.Shader.TileMode.CLAMP,
        )
        canvas.drawRect(0f, 0f, image.width.toFloat(), image.height.toFloat(), paint)
        return out
    }

    private fun applyTiltShiftApprox(
        image: Bitmap,
        edits: com.moments.android.views.creator.creatoruikit.PhotoEdits,
    ): Bitmap {
        val radius = (edits.tiltShiftAmount * 8).toInt().coerceIn(1, 12)
        val blurred = boxBlur(image, radius)
        val out = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        val pixelsSharp = IntArray(image.width * image.height)
        val pixelsBlur = IntArray(image.width * image.height)
        image.getPixels(pixelsSharp, 0, image.width, 0, 0, image.width, image.height)
        blurred.getPixels(pixelsBlur, 0, image.width, 0, 0, image.width, image.height)
        val cx = edits.tiltShiftCenterX * image.width
        val cy = edits.tiltShiftCenterY * image.height
        val r = edits.tiltShiftRadius * min(image.width, image.height)
        val inner = r * 0.52
        val nx = kotlin.math.cos(edits.tiltShiftAngle)
        val ny = kotlin.math.sin(edits.tiltShiftAngle)
        val linear = edits.tiltShiftMode == com.moments.android.views.creator.creatoruikit.TiltShiftMode.LINEAR
        val mixed = IntArray(pixelsSharp.size)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val i = y * image.width + x
                val dist = if (linear) {
                    abs((x - cx) * nx + (y - cy) * ny)
                } else {
                    kotlin.math.hypot(x - cx, y - cy)
                }
                val t = ((dist - inner) / max(r - inner, 0.001)).coerceIn(0.0, 1.0)
                mixed[i] = lerpArgb(pixelsSharp[i], pixelsBlur[i], t.toFloat())
            }
        }
        out.setPixels(mixed, 0, image.width, 0, 0, image.width, image.height)
        return out
    }

    private fun boxBlur(src: Bitmap, radius: Int): Bitmap {
        val w = src.width
        val h = src.height
        val pix = IntArray(w * h)
        src.getPixels(pix, 0, w, 0, 0, w, h)
        val out = pix.copyOf()
        val r = radius.coerceAtLeast(1)
        for (y in 0 until h) {
            for (x in 0 until w) {
                var rs = 0
                var gs = 0
                var bs = 0
                var a = 0
                var n = 0
                for (dy in -r..r) {
                    val yy = (y + dy).coerceIn(0, h - 1)
                    val p = pix[yy * w + x]
                    a += p ushr 24
                    rs += (p shr 16) and 0xFF
                    gs += (p shr 8) and 0xFF
                    bs += p and 0xFF
                    n++
                }
                out[y * w + x] = (a / n shl 24) or (rs / n shl 16) or (gs / n shl 8) or (bs / n)
            }
        }
        val dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        dst.setPixels(out, 0, w, 0, 0, w, h)
        return dst
    }

    private fun lerpArgb(a: Int, b: Int, t: Float): Int {
        val tt = t.coerceIn(0f, 1f)
        fun ch(shift: Int): Int {
            val av = (a shr shift) and 0xFF
            val bv = (b shr shift) and 0xFF
            return (av + (bv - av) * tt).toInt()
        }
        return (ch(24) shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

