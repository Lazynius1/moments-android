package com.moments.android.views.creator.creatoruikit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri

/**
 * Port de `UIImage.creatorNormalizedUp()`.
 *
 * iOS: si `imageOrientation == .up` → self; si no, redibuja con `UIGraphicsImageRenderer`
 * para dejar los píxeles en orientación “up”.
 *
 * Android: aplica la matriz EXIF equivalente (Bitmap no lleva orientation como UIImage).
 */
fun Bitmap.creatorNormalizedUp(
    exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL,
): Bitmap {
    if (
        exifOrientation == ExifInterface.ORIENTATION_NORMAL ||
        exifOrientation == ExifInterface.ORIENTATION_UNDEFINED
    ) {
        return this
    }

    val matrix = Matrix()
    when (exifOrientation) {
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.setScale(-1f, 1f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.setRotate(180f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.setScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.setRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.setRotate(90f)
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.setRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.setRotate(270f)
        else -> return this
    }

    return runCatching {
        Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
    }.getOrDefault(this)
}

/** Lee `TAG_ORIENTATION` del [Uri] (equivalente a `UIImage.imageOrientation`). */
fun Uri.exifOrientation(context: Context): Int =
    runCatching {
        context.contentResolver.openInputStream(this)?.use { stream ->
            ExifInterface(stream).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        } ?: ExifInterface.ORIENTATION_NORMAL
    }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)

/** Atajo: normaliza usando el EXIF del [uri]. */
fun Bitmap.creatorNormalizedUp(context: Context, uri: Uri): Bitmap =
    creatorNormalizedUp(uri.exifOrientation(context))
