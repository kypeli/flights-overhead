package com.kypeli.flightsoverhead.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kypeli.flightsoverhead.data.AirlineResolver
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.ui.components.EmptyState
import com.kypeli.flightsoverhead.ui.components.FlightRow
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme
import com.kypeli.flightsoverhead.viewmodel.FlightsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightListScreen(
    viewModel: FlightsViewModel,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "SkyTrack Pro",
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh",
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        titleContentColor = MaterialTheme.colorScheme.onSurface,
                        actionIconContentColor = MaterialTheme.colorScheme.primary,
                    ),
            )
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        FlightsScreenContent(
            flights = uiState.flights,
            modifier = Modifier.padding(innerPadding),
        )
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
            onRefresh = {},
        )
    } else {
        LazyColumn(
            modifier =
                modifier
                    .fillMaxSize(),
        ) {
            items(flights) { flight ->
                FlightRow(flight = flight)
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FlightListScreenPreview() {
    val mockFlights =
        listOf(
            Flight("American Airlines", "AA123", "SFO", "LAX"),
            Flight("United Airlines", "UA456", "SFO", "ORD"),
            Flight("Delta Airlines", "DL789", "SFO", "JFK"),
            Flight("Southwest", "WN101", "SFO", "LAS"),
        )
    val context = LocalContext.current
    val dummyResolver = remember { AirlineResolver(context) }
    FlightsOverheadTheme {
        FlightsScreenContent(flights = mockFlights)
    }
}

@Preview(showBackground = true)
@Composable
fun FlightListEmptyPreview() {
    FlightsOverheadTheme {
        FlightsScreenContent(flights = emptyList())
    }
}
