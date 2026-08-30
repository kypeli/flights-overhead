package com.kypeli.flightsoverhead.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.kypeli.flightsoverhead.api.FlightDto
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

import com.kypeli.flightsoverhead.entity.FlightPath
import kotlin.math.roundToInt

interface FlightsRepository {
    fun getActiveFlightsFlow(): Flow<Result<FlightsSnapshot>>
    suspend fun fetchActiveFlights(): Result<List<Flight>>
}

data class FlightsSnapshot(
    val flights: List<Flight>,
    val isFromCache: Boolean,
)

@ContributesBinding(ViewModelScope::class)
@Inject
class FlightsRepositoryImpl(
    private val firestore: FirebaseFirestore,
) : FlightsRepository {

    override fun getActiveFlightsFlow(): Flow<Result<FlightsSnapshot>> = callbackFlow {
        val listenerRegistration = firestore.collection("active_flights")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val flights = snapshot.documents.map { doc ->
                        doc.toFlightDto().toFlight()
                    }
                    trySend(
                        Result.success(
                            FlightsSnapshot(
                                flights = flights,
                                isFromCache = snapshot.metadata.isFromCache,
                            )
                        )
                    )
                }
            }

        awaitClose {
            listenerRegistration.remove()
        }
    }

    override suspend fun fetchActiveFlights(): Result<List<Flight>> {
        return runCatching {
            val snapshot = firestore.collection("active_flights").get().awaitTask()
            snapshot.documents.map { doc -> doc.toFlightDto().toFlight() }
        }
    }
}

internal fun DocumentSnapshot.toFlightDto(): FlightDto =
    FlightDto(
        hex = getString("hex") ?: id,
        callsign = getString("callsign").orEmpty(),
        latitude = getDouble("latitude") ?: 0.0,
        longitude = getDouble("longitude") ?: 0.0,
        altitude = getLong("altitude")?.toInt() ?: 0,
        verticalRate = getLong("verticalRate")?.toInt() ?: 0,
        distance = getDouble("distance") ?: 0.0,
        distanceKm = getDouble("distanceKm") ?: 0.0,
        squawk = getString("squawk").orEmpty(),
        manufacturer = getString("manufacturer").orEmpty(),
        model = getString("model").orEmpty(),
        registration = getString("registration").orEmpty(),
        icaoType = getString("icaoType").orEmpty(),
        registeredOwner = getString("registeredOwner").orEmpty(),
        operator = getString("operator").orEmpty(),
        originICAO = getString("originICAO").orEmpty(),
        originIATA = getString("originIATA").orEmpty(),
        originName = getString("originName").orEmpty(),
        originCity = getString("originCity").orEmpty(),
        destICAO = getString("destICAO").orEmpty(),
        destIATA = getString("destIATA").orEmpty(),
        destName = getString("destName").orEmpty(),
        destCity = getString("destCity").orEmpty(),
    )

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
    }

internal fun FlightDto.toFlight(): Flight {
    val airlineName =
        operator.trim().takeIf { it.isNotEmpty() }
            ?: registeredOwner.trim().takeIf { it.isNotEmpty() }
            ?: manufacturer.trim().takeIf { it.isNotEmpty() }
            ?: "Unknown Airline"

    val flightNum =
        callsign.trim().takeIf { it.isNotEmpty() }
            ?: registration.trim().takeIf { it.isNotEmpty() }
            ?: hex.trim().uppercase().takeIf { it.isNotEmpty() }
            ?: "N/A"

    val origin =
        originIATA.trim().uppercase().takeIf { it.isNotEmpty() }
            ?: originICAO.trim().uppercase().takeIf { it.isNotEmpty() }
            ?: originCity.trim().take(3).uppercase().takeIf { it.isNotEmpty() }
            ?: "---"

    val destination =
        destIATA.trim().uppercase().takeIf { it.isNotEmpty() }
            ?: destICAO.trim().uppercase().takeIf { it.isNotEmpty() }
            ?: destCity.trim().take(3).uppercase().takeIf { it.isNotEmpty() }
            ?: "---"

    val path =
        when {
            verticalRate > 100 -> FlightPath.Climbing
            verticalRate < -100 -> FlightPath.Descending
            else -> FlightPath.Cruising
        }

    val altitudeInMeters =
        if (altitude > 0) {
            (altitude * 0.3048).roundToInt()
        } else {
            0
        }

    val distKm =
        when {
            distanceKm > 0.0 -> distanceKm
            distance > 0.0 -> distance * 1.852 // Convert NM to KM
            else -> 0.0
        }

    val modelDescription =
        when {
            manufacturer.isNotBlank() && model.isNotBlank() -> "${manufacturer.trim()} ${model.trim()}"
            model.isNotBlank() -> model.trim()
            manufacturer.isNotBlank() -> manufacturer.trim()
            icaoType.isNotBlank() -> icaoType.trim()
            else -> ""
        }

    return Flight(
        airline = airlineName,
        flightNumber = flightNum,
        departure = originCity.trim().ifEmpty { originName.trim() },
        arrival = destCity.trim().ifEmpty { destName.trim() },
        originCode = origin,
        destinationCode = destination,
        altitudeMeters = altitudeInMeters,
        verticalRate = verticalRate,
        flightPath = path,
        distanceKm = distKm,
        aircraftModel = modelDescription,
        registration = registration.trim(),
        hex = hex.trim(),
        callsign = callsign.trim(),
    )
}

