package com.kypeli.flightsoverhead.repository

import com.kypeli.flightsoverhead.api.FlightDto
import com.kypeli.flightsoverhead.entity.FlightPath
import org.junit.Assert.assertEquals
import org.junit.Test

class FlightsRepositoryTest {

    @Test
    fun toFlight_mapsCompleteFlightDtoCorrectly() {
        val dto = FlightDto(
            hex = "4601F6",
            callsign = "FIN123",
            latitude = 60.3172,
            longitude = 24.9633,
            altitude = 12000, // 12,000 ft -> ~3,658 m
            verticalRate = 1200,
            distance = 15.0, // 15.0 NM -> 27.78 km
            distanceKm = 27.78,
            manufacturer = "AIRBUS",
            model = "A350-900",
            registration = "OH-LWA",
            icaoType = "A359",
            registeredOwner = "Finnair",
            operator = "Finnair",
            originICAO = "EFHK",
            originIATA = "HEL",
            originName = "Helsinki-Vantaa Airport",
            originCity = "Helsinki",
            destICAO = "EGLL",
            destIATA = "LHR",
            destName = "Heathrow Airport",
            destCity = "London",
        )

        val flight = dto.toFlight()

        assertEquals("Finnair", flight.airline)
        assertEquals("FIN123", flight.flightNumber)
        assertEquals("Helsinki", flight.departure)
        assertEquals("London", flight.arrival)
        assertEquals("HEL", flight.originCode)
        assertEquals("LHR", flight.destinationCode)
        assertEquals(3658, flight.altitudeMeters)
        assertEquals(1200, flight.verticalRate)
        assertEquals(FlightPath.Climbing, flight.flightPath)
        assertEquals(27.78, flight.distanceKm, 0.01)
        assertEquals("AIRBUS A350-900", flight.aircraftModel)
        assertEquals("OH-LWA", flight.registration)
        assertEquals("4601F6", flight.hex)
    }

    @Test
    fun toFlight_derivesDescendingFlightPathFromNegativeVerticalRate() {
        val dto = FlightDto(
            hex = "4006EA",
            callsign = "BAW227",
            altitude = 5000,
            verticalRate = -800,
            operator = "British Airways",
        )

        val flight = dto.toFlight()

        assertEquals(FlightPath.Descending, flight.flightPath)
        assertEquals(1524, flight.altitudeMeters) // 5000 * 0.3048 = 1524
    }

    @Test
    fun toFlight_derivesCruisingFlightPathWhenNearLevel() {
        val dto = FlightDto(
            hex = "4006EA",
            callsign = "BAW227",
            altitude = 35000,
            verticalRate = 0,
            operator = "British Airways",
        )

        val flight = dto.toFlight()

        assertEquals(FlightPath.Cruising, flight.flightPath)
        assertEquals(10668, flight.altitudeMeters) // 35000 * 0.3048 = 10668
    }

    @Test
    fun toFlight_convertsNauticalMilesToKilometersWhenDistanceKmIsZero() {
        val dto = FlightDto(
            hex = "4006EA",
            distance = 10.0, // 10 NM -> 18.52 km
            distanceKm = 0.0,
        )

        val flight = dto.toFlight()

        assertEquals(18.52, flight.distanceKm, 0.01)
    }

    @Test
    fun toFlight_handlesMissingOriginDestinationWithFallbacks() {
        val dtoWithIcaoOnly = FlightDto(
            hex = "4601F6",
            originICAO = "EFHK",
            destICAO = "EGLL",
        )
        val flightIcao = dtoWithIcaoOnly.toFlight()
        assertEquals("EFHK", flightIcao.originCode)
        assertEquals("EGLL", flightIcao.destinationCode)

        val dtoEmpty = FlightDto(hex = "4601F6")
        val flightEmpty = dtoEmpty.toFlight()
        assertEquals("---", flightEmpty.originCode)
        assertEquals("---", flightEmpty.destinationCode)
        assertEquals("4601F6", flightEmpty.flightNumber)
        assertEquals("Unknown Airline", flightEmpty.airline)
    }
}
