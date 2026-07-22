package com.moments.android.views.feed.maps

/** Port de `MapLocationServices.swift`. */
object MapLocationServices {
    fun isLocationEnabled(): Boolean = false

    suspend fun requestCurrentLocation(): Pair<Double, Double>? = null
}
