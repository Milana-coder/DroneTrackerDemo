package com.example.dronetrackerdemo.simulator

import com.example.dronetrackerdemo.Drone
import com.google.android.gms.maps.model.LatLng

object DroneSimulator {

    fun move(drones: List<Drone>) {

        drones.forEach { drone ->

            drone.position = LatLng(

                drone.position.latitude + randomStep(),

                drone.position.longitude + randomStep()

            )

        }

    }

    private fun randomStep(): Double {

        return (-5..5).random() * 0.0001

    }
}