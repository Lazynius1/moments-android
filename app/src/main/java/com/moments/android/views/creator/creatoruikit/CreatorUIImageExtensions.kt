package com.moments.android.views.creator.creatoruikit

import android.graphics.Bitmap
import android.graphics.Matrix

/** Port de `UIImage.creatorNormalizedUp()`: normaliza un Bitmap usando el valor EXIF recibido. */
fun Bitmap.creatorNormalizedUp(exifOrientation: Int = 1): Bitmap {
    if (exifOrientation == 1) return this
    val matrix = Matrix().apply {
        when (exifOrientation) {
            2 -> preScale(-1f, 1f)
            3 -> postRotate(180f)
            4 -> preScale(1f, -1f)
            5 -> { postRotate(90f); preScale(-1f, 1f) }
            6 -> postRotate(90f)
            7 -> { postRotate(-90f); preScale(-1f, 1f) }
            8 -> postRotate(-90f)
        }
    }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
