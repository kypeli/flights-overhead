package com.kypeli.flightsoverhead.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kypeli.flightsoverhead.data.AirlineResolver
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.repository.AuthRepository
import com.kypeli.flightsoverhead.repository.FlightsRepository
import dev.zacsweers.metro.Inject
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    refresh()
                } else {
                    _uiState.update { it.copy(flights = emptyList(), error = null) }
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            repository
                .fetchActiveFlights()
                .map {
                    it.map { flight ->
                        flight.copy(logoUrl = airlineResolver.getLogoUrl(flight.airline, flight.flightNumber))
                    }
                }.onSuccess { flights ->
                    _uiState.update {
                        it.copy(flights = flights)
                    }
                }.onFailure {
                    _uiState.update {
                        it.copy(error = UiState.Error.Authentication)
                    }
                }
        }
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
