
package com.example.dronetrackerdemo

data class LocationSettings(
    val settlementName: String = "",
    val warningDistanceKm: Int = 30,
    val voiceAlertsEnabled: Boolean = true
)
