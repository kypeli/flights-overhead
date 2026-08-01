package com.kypeli.flightsoverhead.entity

/**
 * Represents the vertical trajectory or phase of flight for an aircraft.
 */
enum class FlightPath {
    /**
     * The aircraft is ascending to its target cruising altitude.
     */
    Climbing,

    /**
     * The aircraft is maintaining a steady altitude along its en-route segment.
     */
    Cruising,

    /**
     * The aircraft is descending toward its destination airport.
     */
    Descending,
}
