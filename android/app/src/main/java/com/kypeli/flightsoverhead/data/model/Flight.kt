package com.kypeli.flightsoverhead.data.model

data class Flight(
    val airline: String,
    val flightNumber: String,
    val departure: String,
    val arrival: String,
    val logoUrl: String = "",
)
