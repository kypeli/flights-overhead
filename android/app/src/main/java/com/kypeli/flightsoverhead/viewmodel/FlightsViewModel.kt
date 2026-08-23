package com.kypeli.flightsoverhead.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kypeli.flightsoverhead.data.AirlineResolver
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.repository.AuthRepository
import com.kypeli.flightsoverhead.repository.FlightsRepository
import com.kypeli.flightsoverhead.service.FlightNotificationService
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@Inject
class FlightsViewModel(
    private val repository: FlightsRepository,
    private val airlineResolver: AirlineResolver,
    private val authRepository: AuthRepository,
    private val notificationService: FlightNotificationService,
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var previousActiveHexes: Set<String>? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    startObserving()
                } else {
                    stopObserving()
                    _uiState.update { it.copy(flights = emptyList(), error = null) }
                    notificationService.cancelNotificationsForInactiveFlights(emptySet())
                }
            }
        }
    }

    fun refresh() {
        if (authRepository.currentUser.value != null) {
            startObserving()
        }
    }

    private fun startObserving() {
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.getActiveFlightsFlow()
                .collect { result ->
                    result.onSuccess { flights ->
                        val enriched = flights.map { flight ->
                            flight.copy(logoUrl = airlineResolver.getLogoUrl(flight.airline, flight.flightNumber))
                        }
                        _uiState.update {
                            it.copy(flights = enriched, error = null)
                        }

                        val currentHexes = flights.mapNotNull { it.hex.trim().takeIf { h -> h.isNotEmpty() } }.toSet()
                        val prevHexes = previousActiveHexes
                        if (prevHexes != null) {
                            val removedHexes = prevHexes - currentHexes
                            for (removedHex in removedHexes) {
                                notificationService.cancelNotificationForHex(removedHex)
                            }
                        }
                        notificationService.cancelNotificationsForInactiveFlights(currentHexes)
                        previousActiveHexes = currentHexes
                    }.onFailure {
                        _uiState.update {
                            it.copy(error = UiState.Error.Authentication)
                        }
                    }
                }
        }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        previousActiveHexes = null
    }
}

data class UiState(
    val flights: List<Flight> = emptyList(),
    val error: Error? = null,
) {
    enum class Error {
        Authentication,
    }
}
