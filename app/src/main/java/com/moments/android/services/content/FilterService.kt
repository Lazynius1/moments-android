package com.moments.android.services.content

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint

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
}
