package com.moments.android.views.creator.components

import com.moments.android.views.creator.StoryStickerDraft
import com.moments.android.views.feed.maps.MapLocationServices
import com.moments.android.views.feed.maps.WeatherCondition
import com.moments.android.views.feed.maps.WeatherService
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Port de `StickerPickerGeneratedStickers.swift`.
 *
 * Los stickers se describen como datos en Android, en vez de rasterizar la
 * tarjeta con UIKit: el renderer de Story conserva la misma presentación y
 * animación a partir de estos campos.
 */
fun createGeneratedTimeStickerDraft(
    normalizedX: Double,
    normalizedY: Double,
    now: Date = Date(),
): StoryStickerDraft {
    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)
    val date = SimpleDateFormat("d MMM", Locale.getDefault()).format(now)
    return StoryStickerDraft(
        type = "time",
        content = "$time · $date",
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        questionText = time,
        caption = date,
    )
}

/**
 * Equivale a `createWeatherSticker`: consulta ubicación/clima y mantiene el
 * fallback de Swift cuando no hay permiso, posición o datos disponibles.
 */
suspend fun createGeneratedWeatherStickerDraft(
    normalizedX: Double,
    normalizedY: Double,
    now: Date = Date(),
): StoryStickerDraft {
    val weather = MapLocationServices.requestCurrentLocation()
        ?.let { (latitude, longitude) -> WeatherService.getWeatherSafely(latitude, longitude) }
        ?: return createGeneratedWeatherFallbackDraft(normalizedX, normalizedY, now)

    val symbol = weatherSymbolFor(weather.condition, now)
    return StoryStickerDraft(
        type = "weather",
        content = symbol,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        questionText = "${weather.temperature.roundToInt()}°C",
        weatherSymbol = symbol,
    )
}

fun createGeneratedWeatherFallbackDraft(
    normalizedX: Double,
    normalizedY: Double,
    now: Date = Date(),
): StoryStickerDraft {
    val symbol = fallbackWeatherSymbol(now)
    return StoryStickerDraft(
        type = "weather",
        content = symbol,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        questionText = symbol,
        weatherSymbol = symbol,
    )
}

/** Equivalente tipado de `getWeatherSymbol(for:)` de Swift. */
fun weatherSymbolFor(condition: WeatherCondition, now: Date = Date()): String = when (condition) {
    WeatherCondition.Clear -> if (isNight(now)) "🌙" else "☀️"
    WeatherCondition.PartlyCloudy -> if (isNight(now)) "☁️" else "🌤️"
    WeatherCondition.Cloudy -> "☁️"
    WeatherCondition.Rain -> "🌧️"
    WeatherCondition.Snow -> "❄️"
    WeatherCondition.Thunderstorm -> "⛈️"
    WeatherCondition.Unknown -> fallbackWeatherSymbol(now)
}

private fun fallbackWeatherSymbol(now: Date): String =
    if (isNight(now)) "🌙" else "🌤️"

private fun isNight(now: Date): Boolean =
    (SimpleDateFormat("H", Locale.getDefault()).format(now).toIntOrNull() ?: 12) !in 6..19
