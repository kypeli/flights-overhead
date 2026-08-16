package com.kypeli.flightsoverhead.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header

object FlightsApi {
    suspend fun getAboveFlights(
        httpClient: HttpClient,
        token: String?,
    ): Result<List<FlightDto>> =
        runCatching {
            httpClient
                .get(ApiEndpoints.OVERHEAD_FLIGHTS.url) {
                    if (token != null) {
                        header("Authorization", "Bearer $token")
                    }
                }.body<List<FlightDto>>()
        }
}
