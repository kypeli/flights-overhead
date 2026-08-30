package com.kypeli.flightsoverhead.data.model

import com.kypeli.flightsoverhead.entity.FlightPath

data class Flight(
    val airline: String,
    val flightNumber: String,
    val departure: String,
    val arrival: String,
    val originCode: String = "",
    val destinationCode: String = "",
    val altitudeMeters: Int = 0,
    val verticalRate: Int = 0,
    val flightPath: FlightPath = FlightPath.Cruising,
    val distanceKm: Double = 0.0,
    val aircraftModel: String = "",
    val registration: String = "",
    val hex: String = "",
    val logoUrl: String = "",
    val callsign: String = "",
) {
    val flightradar24Url: String?
        get() {
            val identifier =
                callsign.takeIf { it.isNotBlank() }
                    ?: flightNumber.takeIf { it.isNotBlank() && it != "N/A" && it != "---" }
                    ?: hex.takeIf { it.isNotBlank() }
            return identifier?.let { "https://www.flightradar24.com/${it.trim()}/" }
        }
}

