package com.moments.android.debugscreenshots

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import coil.intercept.Interceptor
import coil.request.ErrorResult
import coil.request.ImageResult
import java.io.ByteArrayOutputStream
import java.io.IOException
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Imágenes de demo en `src/debug/assets/screenshots/` (no entran en release) y sus URLs
 * ficticias: https sobre un dominio `.invalid` que nunca resuelve; las sirven el
 * interceptor de Coil y [ScreenshotNetworkGuard].
 */
object ScreenshotImages {
    const val HOST = "moments-screenshots.invalid"
    private const val ASSET_DIR = "screenshots"

    val avatars = listOf(
        "ss_me",
        "ss_avatar_lucia", "ss_avatar_theo", "ss_avatar_aiko", "ss_avatar_mateo",
        "ss_avatar_nora", "ss_avatar_sam", "ss_avatar_ines", "ss_avatar_kai",
    )

    val moments = listOf(
        "ss_moment_beach_sunset", "ss_moment_rooftop_dinner", "ss_moment_mountain_lake",
        "ss_moment_latte", "ss_moment_concert", "ss_moment_night_market",
        "ss_moment_dog_park", "ss_moment_road_trip", "ss_moment_picnic",
        "ss_moment_ski", "ss_moment_birthday", "ss_moment_lisbon_tram",
    )

    val stories = listOf("ss_story_festival", "ss_story_sunrise", "ss_story_cooking")

    val extras = listOf("ss_hidden_polaroid", "ss_chat_photo", "ss_wallpaper")

    fun url(asset: String): String = "https://$HOST/$asset.jpg"

    fun assetName(url: String): String =
        url.substringBefore('?').substringBefore('#').substringAfterLast('/').substringBeforeLast('.')

    private fun assetUri(asset: String): Uri = Uri.parse("file:///android_asset/$ASSET_DIR/$asset.jpg")

    fun assetBytes(context: Context, asset: String): ByteArray? = runCatching {
        context.assets.open("$ASSET_DIR/$asset.jpg").use { it.readBytes() }
    }.getOrNull()

    fun bitmap(context: Context, asset: String, maxSide: Int = 1920): Bitmap? {
        val bytes = assetBytes(context, asset) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        var sample = 1
        while (longest / (sample * 2) >= maxSide) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
    }

    /** JPEG reducido en base64 para stickers que guardan la imagen en `content`. */
    fun base64JPEG(context: Context, asset: String, maxSide: Int = 480): String {
        val source = bitmap(context, asset) ?: return ""
        val longest = max(source.width, source.height)
        val scale = if (longest > maxSide) maxSide.toFloat() / longest else 1f
        val resized = Bitmap.createScaledBitmap(
            source,
            (source.width * scale).roundToInt().coerceAtLeast(1),
            (source.height * scale).roundToInt().coerceAtLeast(1),
            true,
        )
        val out = ByteArrayOutputStream()
        resized.compress(Bitmap.CompressFormat.JPEG, 82, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    /** Coil: URLs de demo → assets; dominios de Google / Firebase → error inmediato. */
    object CoilInterceptor : Interceptor {
        override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
            val request = chain.request
            val raw = when (val data = request.data) {
                is String -> data
                is Uri -> data.toString()
                else -> null
            } ?: return chain.proceed(request)
            val host = runCatching { Uri.parse(raw).host }.getOrNull()?.lowercase()
                ?: return chain.proceed(request)
            if (host == HOST) {
                val rewritten = request.newBuilder().data(assetUri(assetName(raw))).build()
                return chain.proceed(rewritten)
            }
            if (ScreenshotNetworkGuard.isBlockedHost(host)) {
                return ErrorResult(null, request, IOException("Bloqueado en modo capturas: $host"))
            }
            return chain.proceed(request)
        }
    }
}
