package com.moments.android.services.storage

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlin.math.floor
import kotlin.math.max

// Equivalente Android de UIImage+StorageUpload.swift.
// Re-dibuja a bitmap RGB opaco y redimensiona para subidas a Storage/GCS.

// Devuelve un bitmap normalizado (opaco, escalado a maxPixelDimension) o null si es inválido.
fun Bitmap.storageUploadNormalized(maxPixelDimension: Int = 1280): Bitmap? {
    val pixelWidth = width
    val pixelHeight = height
    if (pixelWidth < 2 || pixelHeight < 2) return null

    val longSide = max(pixelWidth, pixelHeight)
    val downscale = if (longSide > maxPixelDimension) maxPixelDimension.toFloat() / longSide else 1f
    val drawWidth = max(2, floor(pixelWidth * downscale).toInt())
    val drawHeight = max(2, floor(pixelHeight * downscale).toInt())

    val output = Bitmap.createBitmap(drawWidth, drawHeight, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(output)
    canvas.drawColor(Color.BLACK) // fondo opaco (equivalente a format.opaque = true)
    canvas.drawBitmap(this, null, android.graphics.Rect(0, 0, drawWidth, drawHeight), null)
    return output
}

// Comprime a JPEG tras normalizar. `compressionQuality` en [0,1] como en iOS.
fun Bitmap.storageUploadJpegData(
    compressionQuality: Float = 0.8f,
    maxPixelDimension: Int = 1280
): ByteArray? {
    val normalized = storageUploadNormalized(maxPixelDimension) ?: return null
    val stream = ByteArrayOutputStream()
    val ok = normalized.compress(Bitmap.CompressFormat.JPEG, (compressionQuality * 100).toInt(), stream)
    return if (ok) stream.toByteArray() else null
}
