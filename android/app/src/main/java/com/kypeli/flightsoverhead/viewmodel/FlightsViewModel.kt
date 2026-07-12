package com.kypeli.flightsoverhead.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kypeli.flightsoverhead.data.AirlineResolver
import com.kypeli.flightsoverhead.data.model.Flight
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository
                .fetchActiveFlights()
                .map {
                    it.map { flight ->
                        flight.copy(logoUrl = airlineResolver.getLogoUrl(flight.flightNumber))
                    }
                }.onSuccess { flights ->
                    _uiState.update { currentState ->
                        currentState.copy(flights = flights)
                    }
                }
        }
    }
}

data class UiState(
    val flights: List<Flight> = emptyList(),
)
