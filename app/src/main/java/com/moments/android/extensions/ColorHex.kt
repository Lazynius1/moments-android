package com.moments.android.extensions

import androidx.compose.ui.graphics.Color
import kotlin.math.roundToInt

/**
 * Port de `Color+Hex.swift` — parsing y utilidades de contraste para Compose y `android.graphics.Color`.
 */
fun Color.Companion.fromHex(hex: String): Color {
    val cleaned = hex.trim().filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
    if (cleaned.isEmpty()) return Color(1f, 1f, 1f, 0f)

    val (a, r, g, b) = when (cleaned.length) {
        3 -> {
            val int = cleaned.toInt(16)
            Tuple4(255, (int shr 8) * 17, (int shr 4 and 0xF) * 17, (int and 0xF) * 17)
        }
        6 -> {
            val int = cleaned.toLong(16)
            Tuple4(255, (int shr 16).toInt(), (int shr 8 and 0xFF).toInt(), (int and 0xFF).toInt())
        }
        8 -> {
            val int = cleaned.toLong(16)
            Tuple4(
                (int shr 24).toInt(),
                (int shr 16 and 0xFF).toInt(),
                (int shr 8 and 0xFF).toInt(),
                (int and 0xFF).toInt(),
            )
        }
        else -> Tuple4(1, 1, 1, 0)
    }

    return Color(
        red = r / 255f,
        green = g / 255f,
        blue = b / 255f,
        alpha = a / 255f,
    )
}

fun Color.toHex(includeAlpha: Boolean = false): String {
    val r = (red * 255f).roundToInt().coerceIn(0, 255)
    val g = (green * 255f).roundToInt().coerceIn(0, 255)
    val b = (blue * 255f).roundToInt().coerceIn(0, 255)
    return if (includeAlpha) {
        val a = (alpha * 255f).roundToInt().coerceIn(0, 255)
        String.format("%02X%02X%02X%02X", a, r, g, b)
    } else {
        String.format("%02X%02X%02X", r, g, b)
    }
}

val Color.isLightColor: Boolean
    get() {
        val luminance = 0.299 * red + 0.587 * green + 0.114 * blue
        return luminance > 0.6
    }

fun Color.revealContrastingEffectColor(): Color =
    if (isLightColor) Color.fromHex("#1A1A1A") else Color.fromHex("#F2F2F2")

/** Convierte hex a `android.graphics.Color` ARGB int. */
fun parseAndroidColor(hex: String): Int {
    val compose = Color.fromHex(hex)
    return android.graphics.Color.argb(
        (compose.alpha * 255f).roundToInt(),
        (compose.red * 255f).roundToInt(),
        (compose.green * 255f).roundToInt(),
        (compose.blue * 255f).roundToInt(),
    )
}

private data class Tuple4(val a: Int, val r: Int, val g: Int, val b: Int)
