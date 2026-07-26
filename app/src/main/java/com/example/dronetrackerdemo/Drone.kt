package com.example.dronetrackerdemo

import com.google.android.gms.maps.model.LatLng

data class Drone(

    val id: Int,

    val name: String,

    var position: LatLng,

    var speed: Float,

    var heading: Float,

    var active: Boolean = true

)