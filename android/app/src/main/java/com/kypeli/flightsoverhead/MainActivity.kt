package com.kypeli.flightsoverhead

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.kypeli.flightsoverhead.ui.screens.FlightListScreen
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme
import kotlinx.serialization.Serializable

@Serializable
private object FlightListScreenKey : NavKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val vm = (application as FlightsOverheadApplication).viewModelGraph.flightsViewModel

        setContent {
            FlightsOverheadTheme {
                val backStack = rememberNavBackStack(FlightListScreenKey)
                val provider =
                    entryProvider {
                        entry<FlightListScreenKey> {
                            FlightListScreen(viewModel = vm)
                        }
                    }

                NavDisplay(
                    backStack = backStack,
                    entryProvider = provider,
                )
            }
        }
    }
}
