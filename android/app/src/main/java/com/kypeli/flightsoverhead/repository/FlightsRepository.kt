package com.kypeli.flightsoverhead.repository

import com.kypeli.flightsoverhead.api.FlightDto
import com.kypeli.flightsoverhead.api.FlightsApi
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient

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

private fun FlightDto.toFlight(): Flight =
    Flight(
        airline = operator,
        flightNumber = callsign,
        departure = originCity,
        arrival = destCity,
    )
