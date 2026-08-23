package com.kypeli.flightsoverhead.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.kypeli.flightsoverhead.MainActivity
import com.kypeli.flightsoverhead.R
import timber.log.Timber
import java.util.Locale

object FlightsNotificationManager {
    const val CHANNEL_ID = "flights_overhead_channel"
    const val EXTRA_NOTIFICATION_ID = "com.kypeli.flightsoverhead.EXTRA_NOTIFICATION_ID"

    fun createNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.notification_channel_name)
            val descriptionText = context.getString(R.string.notification_channel_description)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun extractFlightNumber(data: Map<String, String>): String? {
        val callsign = data["callsign"]?.trim()?.takeIf { it.isNotEmpty() }
        val flightNumber = data["flightNumber"]?.trim()?.takeIf { it.isNotEmpty() }
        val hex = data["hex"]?.trim()?.takeIf { it.isNotEmpty() }
        return callsign ?: flightNumber ?: hex
    }

    fun extractOperator(data: Map<String, String>): String? {
        val operator = data["operator"]?.trim()?.takeIf { it.isNotEmpty() }
        val owner = data["registeredOwner"]?.trim()?.takeIf { it.isNotEmpty() }
        val airline = data["airline"]?.trim()?.takeIf { it.isNotEmpty() }
        return operator ?: owner ?: airline
    }

    fun extractDestination(data: Map<String, String>): String? {
        val city = data["destCity"]?.trim()?.takeIf { it.isNotEmpty() }
        val iata = (data["destIATA"] ?: data["destination"])?.trim()?.takeIf { it.isNotEmpty() }
        val icao = data["destICAO"]?.trim()?.takeIf { it.isNotEmpty() }
        val name = data["destName"]?.trim()?.takeIf { it.isNotEmpty() }

        return when {
            city != null && iata != null && !city.equals(iata, ignoreCase = true) -> "$city ($iata)"
            city != null -> city
            name != null && iata != null && !name.equals(iata, ignoreCase = true) -> "$name ($iata)"
            iata != null -> iata
            icao != null -> icao
            name != null -> name
            else -> null
        }
    }

    fun extractDistanceText(data: Map<String, String>): String? {
        val distanceKm = data["distanceKm"]?.toDoubleOrNull()
        if (distanceKm != null) {
            val formatted = String.format(Locale.US, "%.1f", distanceKm)
            return "$formatted km away"
        }
        return data["distance"]?.trim()?.takeIf { it.isNotEmpty() }
    }

    fun extractNotificationTitle(
        notificationTitle: String?,
        data: Map<String, String>,
        context: Context? = null,
    ): String {
        val customTitle = data["title"]?.trim()?.takeIf { it.isNotEmpty() }
        if (customTitle != null) {
            return customTitle
        }

        val operator = extractOperator(data)
        val flightNumber = extractFlightNumber(data)
        val callsign = data["callsign"]?.trim()?.takeIf { it.isNotEmpty() }

        if (operator != null && flightNumber != null) {
            return "$operator • $flightNumber"
        }
        if (operator != null) {
            return operator
        }
        if (callsign != null) {
            return "Flight $callsign"
        }
        if (flightNumber != null) {
            return "Aircraft $flightNumber"
        }

        if (!notificationTitle.isNullOrBlank()) {
            return notificationTitle
        }

        return context?.getString(R.string.notification_default_title) ?: "Flight Overhead"
    }

    fun extractNotificationBody(
        notificationBody: String?,
        data: Map<String, String>,
        context: Context? = null,
    ): String {
        val customBody = data["body"]?.trim()?.takeIf { it.isNotEmpty() }
        if (customBody != null) {
            return customBody
        }

        val destination = extractDestination(data)
        val distance = extractDistanceText(data)

        if (destination != null && distance != null) {
            return "To $destination • $distance"
        }
        if (destination != null) {
            return "To $destination"
        }
        if (distance != null) {
            val subject = extractFlightNumber(data) ?: "Aircraft"
            return "$subject is $distance."
        }

        if (!notificationBody.isNullOrBlank()) {
            return notificationBody
        }

        return context?.getString(R.string.notification_default_body) ?: "An aircraft is overhead."
    }

    fun extractBigText(
        body: String,
        data: Map<String, String>,
    ): String {
        val operator = extractOperator(data)
        val flightNumber = extractFlightNumber(data)
        val destination = extractDestination(data)
        val distance = extractDistanceText(data)

        if (operator == null && flightNumber == null && destination == null && distance == null) {
            return body
        }

        val parts = mutableListOf<String>()
        if (operator != null) parts.add("Operator: $operator")
        if (flightNumber != null) parts.add("Flight: $flightNumber")
        if (destination != null) parts.add("Destination: $destination")
        if (distance != null) parts.add("Distance: $distance")

        return parts.joinToString("\n")
    }

    fun cancelNotification(context: Context, intent: Intent?) {
        if (intent == null) return
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, -1)
        if (notificationId != -1) {
            try {
                NotificationManagerCompat.from(context).cancel(notificationId)
                Timber.d("Notification ID %d dismissed on tap", notificationId)
            } catch (e: Exception) {
                Timber.w(e, "Failed to dismiss notification ID %d", notificationId)
            }
        }
    }

    fun showFlightNotification(
        context: Context,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ) {
        createNotificationChannel(context)

        val notificationId =
            data["hex"]?.hashCode() ?: ((System.currentTimeMillis() % Int.MAX_VALUE).toInt())

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
                data.forEach { (key, value) ->
                    putExtra(key, value)
                }
            }

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val bigText = extractBigText(body, data)

        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_line_end_arrow_notch_24)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true)
                .build()

        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Timber.d("Flight notification displayed with ID %d: %s", notificationId, title)
        } catch (e: SecurityException) {
            Timber.w(e, "Permission denied while displaying notification")
        } catch (e: Exception) {
            Timber.e(e, "Failed to display flight notification")
        }
    }
}
