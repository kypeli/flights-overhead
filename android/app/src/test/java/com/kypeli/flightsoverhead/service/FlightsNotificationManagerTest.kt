package com.kypeli.flightsoverhead.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FlightsNotificationManagerTest {

    @Test
    fun extractFlightNumber_prefersCallsignThenFlightNumberThenHex() {
        val withCallsign = mapOf("callsign" to "AY1337", "flightNumber" to "FN999", "hex" to "4601F6")
        assertEquals("AY1337", FlightsNotificationManager.extractFlightNumber(withCallsign))

        val withFlightNumber = mapOf("flightNumber" to "FN999", "hex" to "4601F6")
        assertEquals("FN999", FlightsNotificationManager.extractFlightNumber(withFlightNumber))

        val withHex = mapOf("hex" to "4601F6")
        assertEquals("4601F6", FlightsNotificationManager.extractFlightNumber(withHex))
    }

    @Test
    fun extractOperator_resolvesOperatorOrRegisteredOwnerOrAirline() {
        val withOperator = mapOf("operator" to "Finnair", "registeredOwner" to "Nordic Aviation")
        assertEquals("Finnair", FlightsNotificationManager.extractOperator(withOperator))

        val withOwner = mapOf("registeredOwner" to "Delta Air Lines")
        assertEquals("Delta Air Lines", FlightsNotificationManager.extractOperator(withOwner))

        val withAirline = mapOf("airline" to "Lufthansa")
        assertEquals("Lufthansa", FlightsNotificationManager.extractOperator(withAirline))
    }

    @Test
    fun extractDestination_formatsCityAndIataCode() {
        val withCityAndIata = mapOf("destCity" to "Tokyo", "destIATA" to "NRT")
        assertEquals("Tokyo (NRT)", FlightsNotificationManager.extractDestination(withCityAndIata))

        val withCityOnly = mapOf("destCity" to "London")
        assertEquals("London", FlightsNotificationManager.extractDestination(withCityOnly))

        val withIataOnly = mapOf("destIATA" to "JFK")
        assertEquals("JFK", FlightsNotificationManager.extractDestination(withIataOnly))

        val withDestinationFallback = mapOf("destination" to "OUL")
        assertEquals("OUL", FlightsNotificationManager.extractDestination(withDestinationFallback))
    }

    @Test
    fun extractNotificationTitle_formatsOperatorAndFlightNumber() {
        val data = mapOf(
            "operator" to "Finnair",
            "callsign" to "AY1337",
            "destCity" to "Tokyo",
            "distanceKm" to "3.5",
        )
        val title = FlightsNotificationManager.extractNotificationTitle(null, data)
        assertEquals("Finnair • AY1337", title)
    }

    @Test
    fun extractNotificationTitle_formatsFlightNumberWhenOperatorMissing() {
        val data = mapOf("callsign" to "FIN123")
        val title = FlightsNotificationManager.extractNotificationTitle(null, data)
        assertEquals("Flight FIN123", title)
    }

    @Test
    fun extractNotificationTitle_formatsAircraftHexWhenCallsignMissing() {
        val data = mapOf("hex" to "4601F6")
        val title = FlightsNotificationManager.extractNotificationTitle(null, data)
        assertEquals("Aircraft 4601F6", title)
    }

    @Test
    fun extractNotificationTitle_fallbackWhenDataEmpty() {
        val title = FlightsNotificationManager.extractNotificationTitle(null, emptyMap())
        assertEquals("Flight Overhead", title)
    }

    @Test
    fun extractNotificationBody_formatsDestinationAndDistance() {
        val data = mapOf(
            "destCity" to "Tokyo",
            "destIATA" to "NRT",
            "distanceKm" to "3.48",
        )
        val body = FlightsNotificationManager.extractNotificationBody(null, data)
        assertEquals("To Tokyo (NRT) • 3.5 km away", body)
    }

    @Test
    fun extractNotificationBody_formatsDistanceWhenDestinationMissing() {
        val data = mapOf("callsign" to "AY1337", "distanceKm" to "5.21")
        val body = FlightsNotificationManager.extractNotificationBody(null, data)
        assertEquals("AY1337 is 5.2 km away.", body)
    }

    @Test
    fun extractNotificationBody_fallbackWhenDataEmpty() {
        val body = FlightsNotificationManager.extractNotificationBody(null, emptyMap())
        assertEquals("An aircraft is overhead.", body)
    }

    @Test
    fun extractBigText_containsAllFourDetails() {
        val data = mapOf(
            "operator" to "Finnair",
            "callsign" to "AY1337",
            "destCity" to "Tokyo",
            "destIATA" to "NRT",
            "distanceKm" to "3.5",
        )
        val bigText = FlightsNotificationManager.extractBigText("Default body", data)
        val expected = """
            Operator: Finnair
            Flight: AY1337
            Destination: Tokyo (NRT)
            Distance: 3.5 km away
        """.trimIndent()
        assertEquals(expected, bigText)
    }

    @Test
    fun getNotificationId_isDeterministicAndCaseInsensitive() {
        val id1 = FlightsNotificationManager.getNotificationId("4601F6")
        val id2 = FlightsNotificationManager.getNotificationId("4601f6")
        val id3 = FlightsNotificationManager.getNotificationId(" 4601F6 ")

        assertEquals(id1, id2)
        assertEquals(id1, id3)
    }

    @Test
    fun getNotificationId_producesDifferentIdsForDifferentHexes() {
        val id1 = FlightsNotificationManager.getNotificationId("4601F6")
        val id2 = FlightsNotificationManager.getNotificationId("4006EA")

        org.junit.Assert.assertNotEquals(id1, id2)
    }
}
