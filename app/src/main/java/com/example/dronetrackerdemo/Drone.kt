package com.example.dronetrackerdemo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.google.android.gms.maps.model.LatLng

class Drone(
    val id: Int,
    val name: String,
    startPosition: LatLng,
    var heading: Double = 0.0,
    var speed: Int = 0
) {
    var position by mutableStateOf(startPosition)
}