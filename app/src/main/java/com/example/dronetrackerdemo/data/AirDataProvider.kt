package com.example.dronetrackerdemo.data

import com.example.dronetrackerdemo.Drone
import com.google.android.gms.maps.model.LatLng


object AirDataProvider {


    fun getObjects(): List<Drone> {


        return listOf(

            Drone(
                id = 1,
                name = "Object 1",
                startPosition = LatLng(49.9935, 36.2304),
                heading = 90.0,
                speed = 120
            ),

            Drone(
                id = 2,
                name = "Object 2",
                startPosition = LatLng(50.0150, 36.2700),
                heading = 180.0,
                speed = 100
            ),

            )
    }

}