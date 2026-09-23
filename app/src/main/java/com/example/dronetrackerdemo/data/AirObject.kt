package com.example.dronetrackerdemo.data

data class AirObject(

    val id: String,

    val name: String,

    val lat: Double,

    val lon: Double,

    val speed: Double,

    val heading: Double,

    val type: String,

    val status: String,

    val confidence: String,

    val sources: Int,

    val region: String?,

    val locality: String?,

    val trail: List<TrailPoint> = emptyList(),

    val speedKnown: Boolean = false

)