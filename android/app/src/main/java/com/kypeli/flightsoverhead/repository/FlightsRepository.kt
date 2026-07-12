package com.kypeli.flightsoverhead.repository

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
) : FlightsRepository {
    override suspend fun fetchActiveFlights(): Result<List<Flight>> =
        Result.success(
            listOf(
                Flight("American Airlines", "AA123", "SFO", "LAX"),
                Flight("United Airlines", "UA456", "SFO", "ORD"),
                Flight("Delta Airlines", "DL789", "SFO", "JFK"),
                Flight("Southwest", "WN101", "SFO", "LAS"),
            ),
        )
}
