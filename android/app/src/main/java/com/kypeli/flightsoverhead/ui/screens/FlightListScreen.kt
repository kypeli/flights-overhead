package com.kypeli.flightsoverhead.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.ui.components.AuthenticationErrorState
import com.kypeli.flightsoverhead.ui.components.EmptyState
import com.kypeli.flightsoverhead.ui.components.FlightRow
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme
import com.kypeli.flightsoverhead.viewmodel.FlightsViewModel
import com.kypeli.flightsoverhead.viewmodel.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightListScreen(
    viewModel: FlightsViewModel,
    onSignOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            FlightListTopAppBar(
                onSignOut = onSignOut,
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        val error = uiState.error
        if (error != null) {
            when (error) {
                UiState.Error.Authentication -> {
                    AuthenticationErrorState(
                        onRetry = { viewModel.refresh() },
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        } else {
            FlightsScreenContent(
                flights = uiState.flights,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun FlightsScreenContent(
    flights: List<Flight>,
    modifier: Modifier = Modifier,
) {
    if (flights.isEmpty()) {
        EmptyState(
            modifier = modifier,
        )
    } else {
        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(flights) { flight ->
                FlightRow(flight = flight)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FlightListTopAppBar(
    onSignOut: () -> Unit,
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = "SkyTrack Pro",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "FLIGHTS OVERHEAD",
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        actions = {
            IconButton(onClick = onSignOut) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                    contentDescription = "Sign Out",
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.onSurface,
                actionIconContentColor = MaterialTheme.colorScheme.primary,
            ),
    )
}

@Preview(
    name = "Phone light",
    showBackground = true,
    widthDp = 412,
    heightDp = 840,
)
@Composable
fun FlightListScreenPreview() {
    val mockFlights =
        listOf(
            Flight(
                airline = "Finnair",
                flightNumber = "AY123",
                departure = "Helsinki",
                arrival = "London",
                originCode = "HEL",
                destinationCode = "LHR",
                altitudeMeters = 3658,
                flightPath = com.kypeli.flightsoverhead.entity.FlightPath.Climbing,
                distanceKm = 28.0,
                aircraftModel = "Airbus A350-900",
                registration = "OH-LWA",
            ),
            Flight(
                airline = "British Airways",
                flightNumber = "BAW227",
                departure = "London",
                arrival = "New York",
                originCode = "LHR",
                destinationCode = "JFK",
                altitudeMeters = 10668,
                flightPath = com.kypeli.flightsoverhead.entity.FlightPath.Cruising,
                distanceKm = 14.2,
                aircraftModel = "Boeing 777-200",
                registration = "G-VIIA",
            ),
            Flight(
                airline = "Lufthansa",
                flightNumber = "DLH456",
                departure = "Frankfurt",
                arrival = "Helsinki",
                originCode = "FRA",
                destinationCode = "HEL",
                altitudeMeters = 1850,
                flightPath = com.kypeli.flightsoverhead.entity.FlightPath.Descending,
                distanceKm = 8.5,
                aircraftModel = "Airbus A321",
                registration = "D-AIRA",
            ),
            Flight(
                airline = "Scandinavian Airlines",
                flightNumber = "SAS789",
                departure = "Stockholm",
                arrival = "Oslo",
                originCode = "ARN",
                destinationCode = "OSL",
                altitudeMeters = 9450,
                flightPath = com.kypeli.flightsoverhead.entity.FlightPath.Cruising,
                distanceKm = 42.0,
                aircraftModel = "Airbus A320neo",
                registration = "SE-ROA",
            ),
        )
    FlightsOverheadTheme {
        Scaffold(
            topBar = {
                FlightListTopAppBar(onSignOut = {})
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            FlightsScreenContent(
                flights = mockFlights,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Preview(
    name = "Tablet light",
    showBackground = true,
    widthDp = 840,
    heightDp = 900,
)
@Composable
fun FlightListScreenTabletPreview() {
    val mockFlights =
        listOf(
            Flight(
                airline = "Finnair",
                flightNumber = "AY123",
                departure = "Helsinki",
                arrival = "London",
                originCode = "HEL",
                destinationCode = "LHR",
                altitudeMeters = 3658,
                flightPath = com.kypeli.flightsoverhead.entity.FlightPath.Climbing,
                distanceKm = 28.0,
                aircraftModel = "Airbus A350-900",
            ),
            Flight(
                airline = "British Airways",
                flightNumber = "BAW227",
                departure = "London",
                arrival = "New York",
                originCode = "LHR",
                destinationCode = "JFK",
                altitudeMeters = 10668,
                flightPath = com.kypeli.flightsoverhead.entity.FlightPath.Cruising,
                distanceKm = 14.2,
                aircraftModel = "Boeing 777-200",
            ),
        )
    FlightsOverheadTheme {
        Scaffold(
            topBar = {
                FlightListTopAppBar(onSignOut = {})
            },
            containerColor = MaterialTheme.colorScheme.background,
        ) { innerPadding ->
            FlightsScreenContent(
                flights = mockFlights,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Preview(
    name = "Phone dark",
    showBackground = true,
    widthDp = 412,
    heightDp = 840,
    uiMode = android.content.res.Configuration.UI_MODE_NIGHT_YES,
)
@Composable
fun FlightListEmptyPreview() {
    FlightsOverheadTheme {
        FlightsScreenContent(flights = emptyList())
    }
}
