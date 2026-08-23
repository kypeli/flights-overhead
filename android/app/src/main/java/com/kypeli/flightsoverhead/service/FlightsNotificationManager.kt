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

    fun createNotificationChannel(context: Context) {
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

    fun extractNotificationTitle(
        notificationTitle: String?,
        data: Map<String, String>,
        context: Context? = null,
    ): String {
        if (!notificationTitle.isNullOrBlank()) {
            return notificationTitle
        }
        val customTitle = data["title"]?.trim()
        if (!customTitle.isNullOrEmpty()) {
            return customTitle
        }
        val callsign = data["callsign"]?.trim()
        if (!callsign.isNullOrEmpty()) {
            return "Flight $callsign Overhead"
        }
        val hex = data["hex"]?.trim()
        if (!hex.isNullOrEmpty()) {
            return "Aircraft $hex Overhead"
        }
        return context?.getString(R.string.notification_default_title) ?: "Flight Overhead"
    }

    fun extractNotificationBody(
        notificationBody: String?,
        data: Map<String, String>,
        context: Context? = null,
    ): String {
        if (!notificationBody.isNullOrBlank()) {
            return notificationBody
        }
        val customBody = data["body"]?.trim()
        if (!customBody.isNullOrEmpty()) {
            return customBody
        }
        val callsign = data["callsign"]?.trim()
        val hex = data["hex"]?.trim()
        val subject =
            when {
                !callsign.isNullOrEmpty() -> callsign
                !hex.isNullOrEmpty() -> hex
                else -> "Aircraft"
            }
        val distanceKm = data["distanceKm"]?.toDoubleOrNull()
        if (distanceKm != null) {
            val formatted = String.format(Locale.US, "%.1f", distanceKm)
            return "$subject is $formatted km away."
        }
        val origin = data["origin"]?.trim()
        val destination = data["destination"]?.trim()
        if (!origin.isNullOrEmpty() && !destination.isNullOrEmpty()) {
            return "$subject is flying from $origin to $destination."
        }
        return context?.getString(R.string.notification_default_body) ?: "An aircraft is overhead."
    }

    fun showFlightNotification(
        context: Context,
        title: String,
        body: String,
        data: Map<String, String> = emptyMap(),
    ) {
        createNotificationChannel(context)

        val intent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                data.forEach { (key, value) ->
                    putExtra(key, value)
                }
            }

        val notificationId =
            data["hex"]?.hashCode() ?: ((System.currentTimeMillis() % Int.MAX_VALUE).toInt())

        val pendingIntent =
            PendingIntent.getActivity(
                context,
                notificationId,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.outline_line_end_arrow_notch_24)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(NotificationCompat.BigTextStyle().bigText(body))
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
