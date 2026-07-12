package com.kypeli.flightsoverhead.api

import kotlinx.serialization.Serializable

@Serializable
data class FlightDto(
    val hex: String,
    val callsign: String,
    val latitude: Double,
    val longitude: Double,
    val altitude: Int,
    val manufacturer: String,
    val model: String,
    val registration: String,
    val icaoType: String,
    val registeredOwner: String,
    val operator: String,
    val originICAO: String,
    val originIATA: String,
    val originName: String,
    val originCity: String,
    val destICAO: String,
    val destIATA: String,
    val destName: String,
    val destCity: String,
)
