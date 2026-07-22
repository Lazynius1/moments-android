package com.moments.android.views.feed.maps

import java.util.Date

/** Port de `WeatherService.swift`. */
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
        get() = "${temperature.toInt()}°"
}

object WeatherService {
    suspend fun fetchCondition(latitude: Double, longitude: Double): WeatherCondition? = null

    suspend fun getWeatherSafely(latitude: Double, longitude: Double): WeatherData? = null
}
