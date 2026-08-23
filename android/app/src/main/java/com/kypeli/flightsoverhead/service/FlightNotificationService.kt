package com.kypeli.flightsoverhead.service

import android.content.Context
import android.content.Intent
import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn

interface FlightNotificationService {
    fun showFlightNotification(title: String, body: String, data: Map<String, String> = emptyMap())
    fun cancelNotification(intent: Intent?)
    fun cancelNotificationForHex(hex: String)
    fun cancelNotificationsForInactiveFlights(activeHexes: Collection<String>)
    fun getNotificationId(hex: String): Int
}

@SingleIn(ViewModelScope::class)
@ContributesBinding(ViewModelScope::class)
@Inject
class FlightNotificationServiceImpl(
    private val context: Context,
) : FlightNotificationService {

    override fun showFlightNotification(title: String, body: String, data: Map<String, String>) {
        FlightsNotificationManager.showFlightNotification(context, title, body, data)
    }

    override fun cancelNotification(intent: Intent?) {
        FlightsNotificationManager.cancelNotification(context, intent)
    }

    override fun cancelNotificationForHex(hex: String) {
        FlightsNotificationManager.cancelNotificationForHex(context, hex)
    }

    override fun cancelNotificationsForInactiveFlights(activeHexes: Collection<String>) {
        FlightsNotificationManager.cancelNotificationsForInactiveFlights(context, activeHexes)
    }

    override fun getNotificationId(hex: String): Int {
        return FlightsNotificationManager.getNotificationId(hex)
    }
}
