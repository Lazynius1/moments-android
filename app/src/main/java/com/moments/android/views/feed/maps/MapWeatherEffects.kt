package com.moments.android.views.feed.maps

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Port de `MapWeatherEffects.swift` — efectos climáticos sobre el mapa (stub). */
@Composable
fun MapWeatherEffects(
    weather: WeatherData?,
    modifier: Modifier = Modifier,
) {
    // No-op until weather overlay is wired
}

@Composable
fun MapWeatherEffectsView(
    weather: WeatherData,
    modifier: Modifier = Modifier,
) {
    MapWeatherEffects(weather = weather, modifier = modifier)
}
