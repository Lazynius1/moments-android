package com.moments.android.views.feed.maps

import com.moments.android.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.Date
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.roundToInt

/** Port de `WeatherService.swift` — Android usa OpenWeather Current API 2.5 (Free). */
enum class WeatherCondition {
    Clear,
    PartlyCloudy,
    Cloudy,
    Rain,
    Snow,
    Thunderstorm,
    Unknown,
}

data class WeatherData(
    val temperature: Double,
    val condition: WeatherCondition,
    val precipitation: Double = 0.0,
    val cloudCover: Double = 0.0,
    val isNight: Boolean = false,
    val latitude: Double,
    val longitude: Double,
    val timestamp: Date = Date(),
) {
    val isDaytime: Boolean get() = !isNight

    val temperatureFormatted: String
        get() = "${temperature.roundToInt()}°"

    /** ≡ iOS `WeatherData.mapOverlayColor` (MapWeatherEffects.swift). */
    val mapOverlayColor: androidx.compose.ui.graphics.Color
        get() = when (condition) {
            WeatherCondition.Clear ->
                if (isNight) androidx.compose.ui.graphics.Color.Blue
                else androidx.compose.ui.graphics.Color.Yellow
            WeatherCondition.PartlyCloudy ->
                if (isNight) androidx.compose.ui.graphics.Color(0xFF5856D6) // indigo
                else androidx.compose.ui.graphics.Color(0xFFFF9500) // orange
            WeatherCondition.Cloudy -> androidx.compose.ui.graphics.Color.Gray
            WeatherCondition.Rain -> androidx.compose.ui.graphics.Color.Blue
            WeatherCondition.Snow -> androidx.compose.ui.graphics.Color.White
            WeatherCondition.Thunderstorm -> androidx.compose.ui.graphics.Color(0xFFAF52DE) // purple
            WeatherCondition.Unknown -> androidx.compose.ui.graphics.Color.Transparent
        }

    /** ≡ iOS `WeatherData.mapOverlayOpacity` (MapWeatherEffects.swift). */
    val mapOverlayOpacity: Float
        get() = when (condition) {
            WeatherCondition.Clear -> if (isNight) 0.1f else 0.05f
            WeatherCondition.PartlyCloudy -> 0.08f
            WeatherCondition.Cloudy -> 0.15f
            WeatherCondition.Rain -> 0.2f
            WeatherCondition.Snow -> 0.25f
            WeatherCondition.Thunderstorm -> 0.3f
            WeatherCondition.Unknown -> 0f
        }
}

object WeatherService {
    private const val PLACEHOLDER = "YOUR_OPENWEATHER_API_KEY"
    private const val CACHE_EXPIRY_MS = 3_600_000L
    private const val MIN_REQUEST_INTERVAL_MS = 10_000L
    private const val MAX_CACHE_SIZE = 50

    private data class CacheEntry(val data: WeatherData, val expiryMs: Long) {
        val isExpired: Boolean get() = System.currentTimeMillis() > expiryMs
    }

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val rateMutex = Mutex()
    private var lastRequestAt = 0L

    fun hasApiKey(): Boolean {
        val key = BuildConfig.OPENWEATHER_API_KEY.trim()
        return key.isNotEmpty() && key != PLACEHOLDER
    }

    suspend fun fetchCondition(latitude: Double, longitude: Double): WeatherCondition? =
        getWeatherSafely(latitude, longitude)?.condition

    /** ≡ iOS `getWeatherSafely` — nunca lanza; null si no hay key / error / rate limit. */
    suspend fun getWeatherSafely(latitude: Double, longitude: Double): WeatherData? {
        if (!hasApiKey()) return null
        return runCatching { getWeather(latitude, longitude) }.getOrNull()
    }

    suspend fun getWeather(latitude: Double, longitude: Double): WeatherData {
        val key = cacheKey(latitude, longitude)
        cache[key]?.takeIf { !it.isExpired }?.let { return it.data }

        rateMutex.withLock {
            val now = System.currentTimeMillis()
            val wait = MIN_REQUEST_INTERVAL_MS - (now - lastRequestAt)
            if (wait > 0) {
                cache[key]?.takeIf { !it.isExpired }?.let { return it.data }
            }
            lastRequestAt = System.currentTimeMillis()
        }

        val data = withContext(Dispatchers.IO) {
            fetchCurrentWeather(latitude, longitude)
        }
        putCache(key, data)
        return data
    }

    private fun putCache(key: String, data: WeatherData) {
        if (cache.size >= MAX_CACHE_SIZE) {
            val expired = cache.filterValues { it.isExpired }.keys
            expired.forEach { cache.remove(it) }
            if (cache.size >= MAX_CACHE_SIZE) {
                cache.keys.take(cache.size - MAX_CACHE_SIZE + 1).forEach { cache.remove(it) }
            }
        }
        cache[key] = CacheEntry(data, System.currentTimeMillis() + CACHE_EXPIRY_MS)
    }

    private fun cacheKey(lat: Double, lon: Double): String =
        "%.2f,%.2f".format(lat, lon)

    private fun fetchCurrentWeather(latitude: Double, longitude: Double): WeatherData {
        val apiKey = BuildConfig.OPENWEATHER_API_KEY.trim()
        val url = URL(
            "https://api.openweathermap.org/data/2.5/weather" +
                "?lat=$latitude&lon=$longitude&units=metric&appid=$apiKey",
        )
        val connection = url.openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            val code = connection.responseCode
            val body = (if (code == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }
                .orEmpty()
            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("OpenWeather HTTP $code")
            }
            return parseCurrentWeather(JSONObject(body), latitude, longitude)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseCurrentWeather(json: JSONObject, latitude: Double, longitude: Double): WeatherData {
        val main = json.optJSONObject("main")
        val clouds = json.optJSONObject("clouds")
        val rain = json.optJSONObject("rain")
        val snow = json.optJSONObject("snow")
        val sys = json.optJSONObject("sys")
        val weatherArr = json.optJSONArray("weather")
        val weather0 = weatherArr?.optJSONObject(0)
        val conditionId = weather0?.optInt("id") ?: 0

        val temp = main?.optDouble("temp") ?: 0.0
        val cloudCover = (clouds?.optInt("all") ?: 0) / 100.0
        val precipitation = when {
            rain != null -> rain.optDouble("1h", rain.optDouble("3h", 0.0))
            snow != null -> snow.optDouble("1h", snow.optDouble("3h", 0.0))
            else -> estimatePrecipitation(conditionId)
        }
        val sunrise = sys?.optLong("sunrise")?.takeIf { it > 0 }?.times(1000)
        val sunset = sys?.optLong("sunset")?.takeIf { it > 0 }?.times(1000)
        val now = System.currentTimeMillis()
        val isNight = when {
            sunrise != null && sunset != null -> now < sunrise || now > sunset
            else -> {
                val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                hour >= 18 || hour < 6
            }
        }

        return WeatherData(
            temperature = temp,
            condition = mapConditionId(conditionId),
            precipitation = precipitation,
            cloudCover = cloudCover,
            isNight = isNight,
            latitude = latitude,
            longitude = longitude,
            timestamp = Date(),
        )
    }

    /** https://openweathermap.org/weather-conditions */
    private fun mapConditionId(id: Int): WeatherCondition = when (id) {
        in 200..299 -> WeatherCondition.Thunderstorm
        in 300..399, in 500..599 -> WeatherCondition.Rain
        in 600..699 -> WeatherCondition.Snow
        800 -> WeatherCondition.Clear
        801 -> WeatherCondition.PartlyCloudy
        in 802..804 -> WeatherCondition.Cloudy
        in 700..799 -> WeatherCondition.Cloudy
        else -> WeatherCondition.Unknown
    }

    private fun estimatePrecipitation(conditionId: Int): Double = when (conditionId) {
        in 200..299 -> 5.0
        in 300..399 -> 1.5
        in 500..504 -> 2.0
        in 520..531 -> 3.0
        in 600..699 -> 3.0
        else -> 0.0
    }
}
