package com.example.dronetrackerdemo.data

import com.google.android.gms.maps.model.LatLng

data class OblastPolygon(
    val name: String,
    val polygons: List<List<LatLng>>
)

data class RaionPolygon(
    val name: String,
    val oblastName: String,
    val polygons: List<List<LatLng>>
)