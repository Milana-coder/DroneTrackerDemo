
package com.example.dronetrackerdemo

import com.google.android.gms.maps.model.LatLng
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ThreatDistanceCalculator {

    /**
     * Расстояние между населённым пунктом и объектом в километрах.
     */
    fun distanceKm(
        settlement: LatLng,
        threat: LatLng
    ): Double {
        val earthRadiusKm = 6371.0

        val lat1 = Math.toRadians(settlement.latitude)
        val lat2 = Math.toRadians(threat.latitude)
        val deltaLat = Math.toRadians(
            threat.latitude - settlement.latitude
        )
        val deltaLon = Math.toRadians(
            threat.longitude - settlement.longitude
        )

        val a = sin(deltaLat / 2) * sin(deltaLat / 2) +
                cos(lat1) * cos(lat2) *
                sin(deltaLon / 2) * sin(deltaLon / 2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))

        return earthRadiusKm * c
    }
}
