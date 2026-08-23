package com.kypeli.flightsoverhead.api

import kotlinx.serialization.Serializable

@Serializable
data class FlightDto(
    val hex: String = "",
    val callsign: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitude: Int = 0,
    val verticalRate: Int = 0,
    val distance: Double = 0.0,
    val distanceKm: Double = 0.0,
    val squawk: String = "",
    val manufacturer: String = "",
    val model: String = "",
    val registration: String = "",
    val icaoType: String = "",
    val registeredOwner: String = "",
    val operator: String = "",
    val originICAO: String = "",
    val originIATA: String = "",
    val originName: String = "",
    val originCity: String = "",
    val destICAO: String = "",
    val destIATA: String = "",
    val destName: String = "",
    val destCity: String = "",
)

