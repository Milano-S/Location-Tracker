package com.exclr8.locationtrackerdemo.model

data class GeoFence(
    var name: String = "",
    var latitude : Double = 0.0,
    var longitude: Double = 0.0,
    var radius: Double = 100.0
)
