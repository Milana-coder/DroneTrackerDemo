package com.example.dronetrackerdemo.data

data class AlertResponse(
    val success: Boolean,
    val cached: Boolean = false,
    val error: String? = null,
    val alerts: List<Alert> = emptyList()
)

data class Alert(
    val id: Int,
    val location_title: String?,
    val location_type: String?,
    val started_at: String?,
    val finished_at: String?,
    val updated_at: String?,
    val alert_type: String?,
    val location_uid: String?,
    val location_oblast: String?,
    val location_raion: String?,
    val notes: String?,
    val alert_level: String?,
    val threats: List<Threat>?
)

data class Threat(
    val threat_type: String?,
    val level: String?,
    val started_at: String? = null,
    val source_message: String? = null
)