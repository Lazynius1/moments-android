package com.moments.android.views.feed.maps

import kotlin.math.log2
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.round

object MapViewportQuery {
    fun key(region: MapRegionStore.Region): String {
        val latitudeZoom = round(log2(max(region.latitudeDelta, 0.00001)) * 4)
        val longitudeZoom = round(log2(max(region.longitudeDelta, 0.00001)) * 4)
        val latitudeStep = max(0.0001, 2.0.pow(latitudeZoom / 4) / 12)
        val longitudeStep = max(0.0001, 2.0.pow(longitudeZoom / 4) / 12)
        val latitude = round(region.centerLat / latitudeStep).toLong()
        val longitude = round(region.centerLon / longitudeStep).toLong()
        return "$latitude|$longitude|${latitudeZoom.toLong()}|${longitudeZoom.toLong()}"
    }
}
