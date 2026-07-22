package com.moments.android.activities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

/**
 * Port de LiveActivityThumbnailStore.swift.
 * Android: almacena miniaturas en cache interno (no App Group / Live Activity widget).
 * N/A: widget UI de Live Activities en iOS.
 */
object LiveActivityThumbnailStore {
    private const val FOLDER_NAME = "LiveActivityThumbnails"
    private const val MAX_DIMENSION = 200
    private const val JPEG_QUALITY = 60

    private fun folder(context: Context): File {
        val folder = File(context.cacheDir, FOLDER_NAME)
        if (!folder.exists()) folder.mkdirs()
        return folder
    }

    /** Guarda una copia redimensionada y devuelve el nombre de fichero para los attributes de subida. */
    fun save(context: Context, bitmap: Bitmap, id: String): String? {
        val width = bitmap.width
        val height = bitmap.height
        if (width <= 0 || height <= 0) return null
        val scale = minOf(MAX_DIMENSION.toFloat() / width, MAX_DIMENSION.toFloat() / height, 1f)
        val newWidth = (width * scale).toInt().coerceAtLeast(1)
        val newHeight = (height * scale).toInt().coerceAtLeast(1)
        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        val fileName = "$id.jpg"
        val file = File(folder(context), fileName)
        return try {
            FileOutputStream(file).use { stream ->
                if (!resized.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, stream)) return null
            }
            fileName
        } catch (_: Exception) {
            null
        }
    }

    fun load(context: Context, fileName: String): Bitmap? {
        val file = File(folder(context), fileName)
        if (!file.exists()) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    /** Borra la miniatura al terminar/cancelar la subida. */
    fun remove(context: Context, id: String) {
        val file = File(folder(context), "$id.jpg")
        if (file.exists()) file.delete()
    }
}
