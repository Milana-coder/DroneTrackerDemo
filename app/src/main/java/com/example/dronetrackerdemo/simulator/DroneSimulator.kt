package com.example.dronetrackerdemo.simulator

import com.example.dronetrackerdemo.Drone
import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2

object DroneSimulator {

    fun move(drones: List<Drone>) {

        drones.forEach { drone ->

            val oldPosition = drone.position

            val latStep = randomStep()
            val lonStep = randomStep()

            val newPosition = LatLng(
                oldPosition.latitude + latStep,
                oldPosition.longitude + lonStep
            )

            // Определяем направление движения
            val angle = Math.toDegrees(
                atan2(lonStep, latStep)
            )

            drone.heading = (angle + 360) % 360

            drone.position = newPosition
        }
    }

    private fun randomStep(): Double {

        return (-5..5).random() * 0.0001

    }
}