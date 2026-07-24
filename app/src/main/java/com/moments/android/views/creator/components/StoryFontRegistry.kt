package com.moments.android.views.creator.components
import android.content.Context
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import java.util.concurrent.ConcurrentHashMap

/**
 * Port de `StoryFontRegistry.swift` — carga tipografías desde `assets/fonts/`.
 */
object StoryFontRegistry {
    private val cache = ConcurrentHashMap<String, Typeface>()

    fun typeface(context: Context, fileName: String?): Typeface {
        if (fileName.isNullOrBlank()) return Typeface.DEFAULT
        return cache.getOrPut(fileName) {
            runCatching {
                Typeface.createFromAsset(context.assets, "fonts/$fileName")
            }.getOrDefault(Typeface.DEFAULT)
        }
    }
}

@Composable
fun rememberStoryFontFamily(style: StoryTextStyle): FontFamily {
    val context = LocalContext.current
    return remember(style) {
        when {
            style.fontFile != null -> FontFamily(StoryFontRegistry.typeface(context, style.fontFile))
            style == StoryTextStyle.TYPEWRITER -> FontFamily.Monospace
            else -> FontFamily.Default
        }
    }
}

fun parseStoryColorHex(hex: String): Color {
    val cleaned = hex.removePrefix("#").trim()
    val value = cleaned.toLongOrNull(16) ?: return Color.White
    return when (cleaned.length) {
        6 -> Color(0xFF000000L or value)
        8 -> Color(value)
        else -> Color.White
    }
}

fun Color.toStoryHex(): String {
    val a = (alpha * 255).toInt().coerceIn(0, 255)
    val r = (red * 255).toInt().coerceIn(0, 255)
    val g = (green * 255).toInt().coerceIn(0, 255)
    val b = (blue * 255).toInt().coerceIn(0, 255)
    return if (a >= 255) {
        "%02X%02X%02X".format(r, g, b)
    } else {
        "%02X%02X%02X%02X".format(a, r, g, b)
    }
}
