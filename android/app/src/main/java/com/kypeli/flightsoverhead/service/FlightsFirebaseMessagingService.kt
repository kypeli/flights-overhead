package com.kypeli.flightsoverhead.service

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.kypeli.flightsoverhead.FlightsOverheadApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

class FlightsFirebaseMessagingService : FirebaseMessagingService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onRegistered(installationId: String) {
        super.onRegistered(installationId)
        Timber.d("FCM registered with Installation ID: %s", installationId)

        val app = applicationContext as? FlightsOverheadApplication ?: return
        serviceScope.launch {
            val tokenService = app.viewModelGraph.tokenService
            tokenService.registerInstallationId(installationId)
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Timber.d("FCM message received from: %s", remoteMessage.from)
        if (remoteMessage.data.isNotEmpty()) {
            Timber.d("Message data payload: %s", remoteMessage.data)
        }
        remoteMessage.notification?.let {
            Timber.d("Message notification body: %s", it.body)
        }

        val action = remoteMessage.data["action"] ?: remoteMessage.data["status"]
        if (action.equals("dismiss", ignoreCase = true) ||
            action.equals("cancel", ignoreCase = true) ||
            action.equals("removed", ignoreCase = true) ||
            remoteMessage.data["dismiss"] == "true"
        ) {
            val hex = remoteMessage.data["hex"]
            if (!hex.isNullOrBlank()) {
                FlightsNotificationManager.cancelNotificationForHex(applicationContext, hex)
                return
            }
        }

        val title =
            FlightsNotificationManager.extractNotificationTitle(
                notificationTitle = remoteMessage.notification?.title,
                data = remoteMessage.data,
                context = applicationContext,
            )
        val body =
            FlightsNotificationManager.extractNotificationBody(
                notificationBody = remoteMessage.notification?.body,
                data = remoteMessage.data,
                context = applicationContext,
            )

        FlightsNotificationManager.showFlightNotification(
            context = applicationContext,
            title = title,
            body = body,
            data = remoteMessage.data,
        )
    }
}
