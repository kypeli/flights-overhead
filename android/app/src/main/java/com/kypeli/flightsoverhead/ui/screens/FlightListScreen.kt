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
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kypeli.flightsoverhead.data.AirlineResolver
import com.kypeli.flightsoverhead.di.LocalAirlineResolver
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.ui.components.EmptyState
import com.kypeli.flightsoverhead.ui.components.FlightRow
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FlightListScreen(
    flights: List<Flight>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
        if (flights.isEmpty()) {
            EmptyState(
                onRefresh = onRefresh,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
            ) {
                items(flights) { flight ->
                    FlightRow(flight = flight)
                }
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
        CompositionLocalProvider(LocalAirlineResolver provides dummyResolver) {
            FlightListScreen(flights = mockFlights, onRefresh = {})
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FlightListEmptyPreview() {
    val context = LocalContext.current
    val dummyResolver = remember { AirlineResolver(context) }
    FlightsOverheadTheme {
        CompositionLocalProvider(LocalAirlineResolver provides dummyResolver) {
            FlightListScreen(flights = emptyList(), onRefresh = {})
        }
    }
}
