package com.moments.android.views.creator.components

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** Port de `StoryDominantColorsExtractor.swift`. */
object StoryDominantColorsExtractor {
    fun extract(image: Bitmap?, maxColors: Int = 6): List<Color> {
        image ?: return emptyList()
        if (image.width <= 0 || image.height <= 0) return emptyList()

        val sampleSize = 48
        val sample = Bitmap.createScaledBitmap(image, sampleSize, sampleSize, true)
        val pixels = IntArray(sampleSize * sampleSize)
        sample.getPixels(pixels, 0, sampleSize, 0, 0, sampleSize, sampleSize)
        if (sample !== image) sample.recycle()
        val buckets = mutableMapOf<Int, DominantColorBucket>()

        for (y in 0 until sampleSize step 2) {
            for (x in 0 until sampleSize step 2) {
                val pixel = pixels[y * sampleSize + x]
                if (AndroidColor.alpha(pixel) <= 40) continue
                val red = AndroidColor.red(pixel)
                val green = AndroidColor.green(pixel)
                val blue = AndroidColor.blue(pixel)
                val key = ((red / 32) * 32 shl 16) or
                    ((green / 32) * 32 shl 8) or
                    ((blue / 32) * 32)
                val bucket = buckets.getOrPut(key) { DominantColorBucket() }
                bucket.red += red
                bucket.green += green
                bucket.blue += blue
                bucket.count++
            }
        }
        return buckets.values
            .sortedByDescending { it.count }
            .take(maxColors)
            .map { bucket ->
                val count = bucket.count.coerceAtLeast(1)
                Color(
                    red = bucket.red.toFloat() / count / 255f,
                    green = bucket.green.toFloat() / count / 255f,
                    blue = bucket.blue.toFloat() / count / 255f,
                )
            }
    }

    /** Port de `sampleColor(at:in:viewSize:)`; vista con contentMode fill. */
    fun sampleColor(location: Offset, image: Bitmap, viewSize: Size): Color {
        if (image.width <= 0 || image.height <= 0 || viewSize.width <= 0f || viewSize.height <= 0f) {
            return Color.White
        }
        val scale = maxOf(viewSize.width / image.width, viewSize.height / image.height)
        val displayedWidth = image.width * scale
        val displayedHeight = image.height * scale
        val localX = (location.x - (viewSize.width - displayedWidth) / 2f) / displayedWidth
        val localY = (location.y - (viewSize.height - displayedHeight) / 2f) / displayedHeight
        if (localX !in 0f..1f || localY !in 0f..1f) return Color.White
        val pixelX = (localX * image.width).toInt().coerceIn(0, image.width - 1)
        val pixelY = (localY * image.height).toInt().coerceIn(0, image.height - 1)
        val pixel = image.getPixel(pixelX, pixelY)
        return Color(
            red = AndroidColor.red(pixel) / 255f,
            green = AndroidColor.green(pixel) / 255f,
            blue = AndroidColor.blue(pixel) / 255f,
            alpha = AndroidColor.alpha(pixel) / 255f,
        )
    }

    private class DominantColorBucket(
        var red: Int = 0,
        var green: Int = 0,
        var blue: Int = 0,
        var count: Int = 0,
    )
}
