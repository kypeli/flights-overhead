package com.kypeli.flightsoverhead.repository

import com.kypeli.flightsoverhead.api.FlightDto
import com.kypeli.flightsoverhead.api.FlightsApi
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient

import com.kypeli.flightsoverhead.entity.FlightPath
import kotlin.math.roundToInt

interface FlightsRepository {
    suspend fun fetchActiveFlights(): Result<List<Flight>>
}

@ContributesBinding(ViewModelScope::class)
@Inject
class FlightsRepositoryImpl(
    private val httpClient: HttpClient,
    private val authRepository: AuthRepository,
) : FlightsRepository {
    override suspend fun fetchActiveFlights(): Result<List<Flight>> {
        val token = authRepository.getAccessToken()
        return FlightsApi
            .getAboveFlights(httpClient, token)
            .map { dtos -> dtos.map { it.toFlight() } }
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
    )
}

