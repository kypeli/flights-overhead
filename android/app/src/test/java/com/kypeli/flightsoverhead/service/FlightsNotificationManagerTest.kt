package com.kypeli.flightsoverhead.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FlightsNotificationManagerTest {

    @Test
    fun extractNotificationTitle_prefersExplicitNotificationTitle() {
        val result =
            FlightsNotificationManager.extractNotificationTitle(
                notificationTitle = "Custom Push Title",
                data = mapOf("callsign" to "FIN123", "hex" to "4601F6"),
            )
        assertEquals("Custom Push Title", result)
    }

    @Test
    fun extractNotificationTitle_usesDataTitleIfNoNotificationTitle() {
        val result =
            FlightsNotificationManager.extractNotificationTitle(
                notificationTitle = null,
                data = mapOf("title" to "Data Payload Title", "callsign" to "FIN123"),
            )
        assertEquals("Data Payload Title", result)
    }

    @Test
    fun extractNotificationTitle_formatsFromCallsign() {
        val result =
            FlightsNotificationManager.extractNotificationTitle(
                notificationTitle = null,
                data = mapOf("callsign" to "AY1337", "hex" to "4601F6"),
            )
        assertEquals("Flight AY1337 Overhead", result)
    }

    @Test
    fun extractNotificationTitle_formatsFromHexWhenCallsignMissing() {
        val result =
            FlightsNotificationManager.extractNotificationTitle(
                notificationTitle = null,
                data = mapOf("hex" to "4601F6"),
            )
        assertEquals("Aircraft 4601F6 Overhead", result)
    }

    @Test
    fun extractNotificationTitle_fallbackWhenDataEmpty() {
        val result =
            FlightsNotificationManager.extractNotificationTitle(
                notificationTitle = null,
                data = emptyMap(),
            )
        assertEquals("Flight Overhead", result)
    }

    @Test
    fun extractNotificationBody_prefersExplicitNotificationBody() {
        val result =
            FlightsNotificationManager.extractNotificationBody(
                notificationBody = "Explicit Notification Body",
                data = mapOf("callsign" to "FIN123", "distanceKm" to "3.4"),
            )
        assertEquals("Explicit Notification Body", result)
    }

    @Test
    fun extractNotificationBody_usesDataBodyIfNoNotificationBody() {
        val result =
            FlightsNotificationManager.extractNotificationBody(
                notificationBody = null,
                data = mapOf("body" to "Custom Data Body", "distanceKm" to "3.4"),
            )
        assertEquals("Custom Data Body", result)
    }

    @Test
    fun extractNotificationBody_formatsWithCallsignAndDistance() {
        val result =
            FlightsNotificationManager.extractNotificationBody(
                notificationBody = null,
                data = mapOf("callsign" to "AY1337", "distanceKm" to "4.215"),
            )
        assertEquals("AY1337 is 4.2 km away.", result)
    }

    @Test
    fun extractNotificationBody_formatsWithHexAndDistanceWhenCallsignMissing() {
        val result =
            FlightsNotificationManager.extractNotificationBody(
                notificationBody = null,
                data = mapOf("hex" to "4601F6", "distanceKm" to "1.98"),
            )
        assertEquals("4601F6 is 2.0 km away.", result)
    }

    @Test
    fun extractNotificationBody_formatsWithRouteWhenOriginAndDestPresent() {
        val result =
            FlightsNotificationManager.extractNotificationBody(
                notificationBody = null,
                data = mapOf("callsign" to "AY1337", "origin" to "HEL", "destination" to "LHR"),
            )
        assertEquals("AY1337 is flying from HEL to LHR.", result)
    }

    @Test
    fun extractNotificationBody_fallbackWhenDataEmpty() {
        val result =
            FlightsNotificationManager.extractNotificationBody(
                notificationBody = null,
                data = emptyMap(),
            )
        assertEquals("An aircraft is overhead.", result)
    }
}
