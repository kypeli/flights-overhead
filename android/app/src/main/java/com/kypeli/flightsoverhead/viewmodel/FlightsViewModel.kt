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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

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
    private var resumeStaleTimeoutJob: Job? = null
    private var previousActiveHexes: Set<String>? = null

    init {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                if (user != null) {
                    startObserving()
                } else {
                    stopObserving()
                    _uiState.update {
                        it.copy(flights = emptyList(), error = null, isLoading = true, isRefreshing = false)
                    }
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

    /**
     * Arms the "stale data" loading state when the app returns to the foreground.
     * It stays armed until a fresh (non-cached) Firestore snapshot arrives; a
     * timeout guard covers the case where nothing changed server-side and no new
     * snapshot ever fires (e.g. empty sky, or the connection never dropped).
     */
    fun onAppResume() {
        if (authRepository.currentUser.value == null) return
        resumeStaleTimeoutJob?.cancel()
        resumeStaleTimeoutJob = null
        _uiState.update { it.copy(isRefreshing = true) }
        resumeStaleTimeoutJob =
            viewModelScope.launch {
                delay(RESUME_STALE_TIMEOUT)
                resumeStaleTimeoutJob = null
                _uiState.update { it.copy(isRefreshing = false) }
            }
    }

    private fun startObserving() {
        observeJob?.cancel()
        // Only show the full-screen loader when there is nothing to display yet
        // (initial load / re-login after sign-out). Re-arming the listener over
        // existing data (e.g. refresh, notification tap) must not flash it.
        _uiState.update {
            if (it.flights.isEmpty() && it.error == null) it.copy(isLoading = true) else it
        }
        observeJob =
            viewModelScope.launch {
                repository
                    .getActiveFlightsFlow()
                    .collect { result ->
                        result
                            .onSuccess { snapshot ->
                                val enriched =
                                    snapshot.flights.map { flight ->
                                        flight.copy(logoUrl = airlineResolver.getLogoUrl(flight.airline, flight.flightNumber))
                                    }
                                _uiState.update {
                                    it.copy(
                                        flights = enriched,
                                        error = null,
                                        isLoading = false,
                                        isRefreshing = if (snapshot.isFromCache) it.isRefreshing else false,
                                    )
                                }
                                if (!snapshot.isFromCache) {
                                    cancelResumeStaleTimeout()
                                }

                                val currentHexes = snapshot.flights.mapNotNull { it.hex.trim().takeIf { h -> h.isNotEmpty() } }.toSet()
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
                                cancelResumeStaleTimeout()
                                _uiState.update {
                                    it.copy(error = UiState.Error.Authentication, isLoading = false, isRefreshing = false)
                                }
                            }
                    }
            }
    }

    private fun stopObserving() {
        observeJob?.cancel()
        observeJob = null
        cancelResumeStaleTimeout()
        previousActiveHexes = null
    }

    private fun cancelResumeStaleTimeout() {
        resumeStaleTimeoutJob?.cancel()
        resumeStaleTimeoutJob = null
    }

    private companion object {
        private val RESUME_STALE_TIMEOUT: Duration = 5.seconds
    }
}

data class UiState(
    val flights: List<Flight> = emptyList(),
    val error: Error? = null,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
) {
    enum class Error {
        Authentication,
    }
}
