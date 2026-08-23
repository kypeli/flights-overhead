package com.kypeli.flightsoverhead.service

import android.content.Context
import android.content.ContextWrapper
import org.junit.Assert.assertEquals
import org.junit.Test

class FlightNotificationServiceTest {

    private fun createDummyContext(): Context {
        return object : ContextWrapper(null) {}
    }

    @Test
    fun getNotificationId_delegatesToFlightsNotificationManager() {
        val service = FlightNotificationServiceImpl(createDummyContext())
        val id1 = service.getNotificationId("4601F6")
        val id2 = FlightsNotificationManager.getNotificationId("4601F6")

        assertEquals(id2, id1)
    }
}
