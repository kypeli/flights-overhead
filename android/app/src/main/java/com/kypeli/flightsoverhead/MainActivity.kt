package com.kypeli.flightsoverhead

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.ui.screens.FlightListScreen
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val vm = (application as FlightsOverheadApplication).viewModelGraph.flightsViewModel

        setContent {
            FlightsOverheadTheme {
                // CompositionLocalProvider(LocalMetroViewModelFactory provides vmFactory) {
                var flights by remember {
                    mutableStateOf(
                        listOf(
                            Flight("American Airlines", "AA123", "SFO", "LAX"),
                            Flight("United Airlines", "UA456", "SFO", "ORD"),
                            Flight("Delta Airlines", "DL789", "SFO", "JFK"),
                            Flight("Southwest", "WN101", "SFO", "LAS"),
                        ),
                    )
                }

                FlightListScreen(
                    viewModel = vm,
                    onRefresh = {
                        // For demonstration purposes, toggle between list and empty state
                        flights =
                            if (flights.isEmpty()) {
                                listOf(
                                    Flight("American Airlines", "AA123", "SFO", "LAX"),
                                    Flight("United Airlines", "UA456", "SFO", "ORD"),
                                    Flight("Delta Airlines", "DL789", "SFO", "JFK"),
                                    Flight("Southwest", "WN101", "SFO", "LAS"),
                                )
                            } else {
                                emptyList()
                            }
                    },
                )
                //       }
            }
        }
    }
}
