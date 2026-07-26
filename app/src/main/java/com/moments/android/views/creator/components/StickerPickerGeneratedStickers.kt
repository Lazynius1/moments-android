package com.moments.android.views.creator.components

import androidx.compose.ui.graphics.Color
import com.moments.android.utilities.MomentsFormat
import com.moments.android.views.creator.StoryStickerDraft
import com.moments.android.views.feed.maps.MapLocationServices
import com.moments.android.views.feed.maps.WeatherCondition
import com.moments.android.views.feed.maps.WeatherService
import java.util.Calendar
import java.util.Date
import kotlin.math.roundToInt

/**
 * Port de `StickerPickerGeneratedStickers.swift` (extension de `StickerPickerView`).
 *
 * En Android no se rasteriza la tarjeta con Canvas/UIKit: el draft alimenta
 * `AnimatedWeatherSticker` / time sticker del renderer (misma data iOS).
 *
 * Nota: `MapLocationServices` / `WeatherService` siguen [~]; sin ubicación/clima
 * real se usa el placeholder `🌤️` como en Swift.
 */

/** ≡ `WeatherError`. */
enum class WeatherStickerError {
    NO_LOCATION_PERMISSION,
    NO_LOCATION,
    UNSUPPORTED_VERSION,
}

/**
 * ≡ `createTimeSticker` → draft.
 * `questionText` = hora, `caption` = fecha (visor).
 */
fun createGeneratedTimeStickerDraft(
    normalizedX: Double,
    normalizedY: Double,
    now: Date = Date(),
): StoryStickerDraft {
    val time = MomentsFormat.smartDate(from = now, context = MomentsFormat.DateContext.TIME_ONLY)
    val date = MomentsFormat.smartDate(from = now, context = MomentsFormat.DateContext.DAY_MONTH_LABEL)
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
 * ≡ `createWeatherSticker` / `getCurrentWeather` + data|placeholder.
 */
suspend fun createGeneratedWeatherStickerDraft(
    normalizedX: Double,
    normalizedY: Double,
    now: Date = Date(),
): StoryStickerDraft {
    val location = MapLocationServices.requestCurrentLocation()
        ?: return createGeneratedWeatherFallbackDraft(normalizedX, normalizedY)

    val weather = WeatherService.getWeatherSafely(location.first, location.second)
        ?: return createGeneratedWeatherFallbackDraft(normalizedX, normalizedY)

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

/**
 * ≡ `createWeatherStickerWithPlaceholder`.
 * iOS fija siempre `🌤️` (no depende de noche).
 */
fun createGeneratedWeatherFallbackDraft(
    normalizedX: Double,
    normalizedY: Double,
): StoryStickerDraft {
    val symbol = "🌤️"
    return StoryStickerDraft(
        type = "weather",
        content = symbol,
        normalizedX = normalizedX,
        normalizedY = normalizedY,
        questionText = symbol,
        weatherSymbol = symbol,
    )
}

/** Equivalente tipado de `getWeatherSymbol(for:)` vía enum. */
fun weatherSymbolFor(condition: WeatherCondition, now: Date = Date()): String = when (condition) {
    WeatherCondition.Clear -> if (isNight(now)) "🌙" else "☀️"
    WeatherCondition.PartlyCloudy -> if (isNight(now)) "☁️" else "🌤️"
    WeatherCondition.Cloudy -> if (isNight(now)) "☁️" else "🌤️"
    WeatherCondition.Rain -> "🌧️"
    WeatherCondition.Snow -> "❄️"
    WeatherCondition.Thunderstorm -> "⛈️"
    WeatherCondition.Unknown -> if (isNight(now)) "🌙" else "🌤️"
}

/**
 * ≡ `getWeatherSymbol(for condition: String)` — matching por displayName
 * (clear/sunny/cloud/rain/… + fog/wind/hot/cold).
 */
fun weatherSymbolForConditionName(condition: String, now: Date = Date()): String {
    val lowercased = condition.lowercase()
    val night = isNight(now)

    return when {
        lowercased.contains("clear") || lowercased.contains("sunny") ->
            if (night) "🌙" else "☀️"
        lowercased.contains("cloud") ->
            if (night) "☁️" else "🌤️"
        lowercased.contains("rain") || lowercased.contains("drizzle") -> "🌧️"
        lowercased.contains("snow") || lowercased.contains("sleet") -> "❄️"
        lowercased.contains("storm") || lowercased.contains("thunder") -> "⛈️"
        lowercased.contains("fog") || lowercased.contains("haze") -> "🌫️"
        lowercased.contains("wind") || lowercased.contains("breeze") -> "💨"
        lowercased.contains("hot") -> "🔥"
        lowercased.contains("cold") -> "🥶"
        else -> if (night) "🌙" else "🌤️"
    }
}

/**
 * ≡ `getWeatherGradientColors(for:)` — colores system* @ 0.9 alpha.
 * Usado por renderers / previews; `AnimatedWeatherSticker` tiene su propia tabla.
 */
fun weatherGradientColors(symbol: String): Pair<Color, Color> {
    val a = 0.9f
    return when (symbol) {
        "☀️" -> Color(0xFFFF9500).copy(alpha = a) to Color(0xFFFFCC00).copy(alpha = a) // orange/yellow
        "🌧️", "⛈️" -> Color(0xFF007AFF).copy(alpha = a) to Color(0xFF5856D6).copy(alpha = a) // blue/indigo
        "❄️", "🌨️" -> Color(0xFF32ADE6).copy(alpha = a) to Color(0xFF007AFF).copy(alpha = a) // cyan/blue
        "☁️", "⛅" -> Color(0xFF8E8E93).copy(alpha = a) to Color(0xFF007AFF).copy(alpha = a) // gray/blue
        "🔥" -> Color(0xFFFF3B30).copy(alpha = a) to Color(0xFFFF9500).copy(alpha = a) // red/orange
        "🥶" -> Color(0xFF32ADE6).copy(alpha = a) to Color(0xFF007AFF).copy(alpha = a) // cyan/blue
        else -> Color(0xFF007AFF).copy(alpha = a) to Color(0xFF32ADE6).copy(alpha = a) // blue/cyan
    }
}

/** ≡ noche entre 20:00 y 6:00. */
fun isWeatherNight(now: Date = Date()): Boolean = isNight(now)

private fun isNight(now: Date): Boolean {
    val hour = Calendar.getInstance().apply { time = now }.get(Calendar.HOUR_OF_DAY)
    return hour >= 20 || hour < 6
}
