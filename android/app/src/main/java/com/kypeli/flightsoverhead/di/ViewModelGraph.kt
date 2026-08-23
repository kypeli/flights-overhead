package com.kypeli.flightsoverhead.di

import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import com.kypeli.flightsoverhead.repository.TokenRepository
import com.kypeli.flightsoverhead.service.FlightNotificationService
import com.kypeli.flightsoverhead.service.TokenService
import com.kypeli.flightsoverhead.viewmodel.AuthViewModel
import com.kypeli.flightsoverhead.viewmodel.FlightsViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension

@GraphExtension(ViewModelScope::class)
interface ViewModelGraph {
    val flightsViewModel: FlightsViewModel
    val authViewModel: AuthViewModel
    val tokenRepository: TokenRepository
    val tokenService: TokenService
    val flightNotificationService: FlightNotificationService

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    interface Factory {
        fun createViewModelGraph(): ViewModelGraph
    }
}
