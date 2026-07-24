package com.moments.android.views.creator.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color

/** Port de `StoryTextGradientSettings.swift`. */
object StoryTextGradientSettings {
    const val minStops = 2
    const val maxStops = 6

    val presetMoments = listOf(
        parseStoryColorHex("FF2D55"), parseStoryColorHex("FF9500"),
        parseStoryColorHex("FFD60A"), parseStoryColorHex("5E5CE6"),
    )
    val presetSunset = listOf(
        parseStoryColorHex("FF6B6B"), parseStoryColorHex("FF8E53"),
        parseStoryColorHex("FECA57"), parseStoryColorHex("FF9FF3"),
    )
    val presetOcean = listOf(
        parseStoryColorHex("007AFF"), parseStoryColorHex("00C7BE"),
        parseStoryColorHex("5AC8FA"), parseStoryColorHex("30B0C7"),
    )

    fun defaultStops(anchoredTo: Color): List<Color> =
        listOf(anchoredTo, parseStoryColorHex("FF2D55"), parseStoryColorHex("5E5CE6"))

    fun normalizedStops(stops: List<Color>, fallback: Color): List<Color> =
        if (stops.size >= minStops) stops.take(maxStops) else defaultStops(fallback)

    fun encodeStops(stops: List<Color>): List<String> =
        normalizedStops(stops, Color.White).map { it.toStoryHex() }

    fun decodeStops(hexes: List<String>?, fallback: Color): List<Color> =
        if (hexes == null || hexes.size < minStops) defaultStops(fallback)
        else hexes.take(maxStops).map(::parseStoryColorHex)

    fun gradientPoints(angleDegrees: Int): Pair<Offset, Offset> = when (angleDegrees) {
        90 -> Offset(.5f, 0f) to Offset(.5f, 1f)
        45 -> Offset(0f, 0f) to Offset(1f, 1f)
        else -> Offset(0f, .5f) to Offset(1f, .5f)
    }

    fun cycleAngle(current: Int): Int = when (current) {
        0 -> 90
        90 -> 45
        else -> 0
    }

    fun angleSymbol(degrees: Int): String = when (degrees) {
        90 -> "↕"
        45 -> "↗"
        else -> "↔"
    }
}

val StoryTextRenderConfiguration.resolvedGradientStops: List<Color>
    get() = StoryTextGradientSettings.normalizedStops(gradientStops, textColor)

val StoryTextRenderConfiguration.gradientUnitPoints: Pair<Offset, Offset>
    get() = StoryTextGradientSettings.gradientPoints(gradientAngle)
