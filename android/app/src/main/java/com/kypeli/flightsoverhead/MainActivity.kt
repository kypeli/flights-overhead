package com.kypeli.flightsoverhead

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.kypeli.flightsoverhead.navigation.FlightListScreenKey
import com.kypeli.flightsoverhead.navigation.LoginScreenKey
import com.kypeli.flightsoverhead.ui.screens.FlightListScreen
import com.kypeli.flightsoverhead.ui.screens.LoginScreen
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FlightsOverheadApplication
        val flightsVm = app.viewModelGraph.flightsViewModel
        val authVm = app.viewModelGraph.authViewModel

        setContent {
            FlightsOverheadTheme {
                val currentUser by authVm.currentUser.collectAsState()

                // State-driven backstack
                val initialKey = if (currentUser == null) LoginScreenKey else FlightListScreenKey
                val backStack = rememberNavBackStack(initialKey)

                // Sync back stack when auth state changes
                LaunchedEffect(currentUser) {
                    if (currentUser == null) {
                        if (backStack.lastOrNull() != LoginScreenKey) {
                            backStack.clear()
                            backStack.add(LoginScreenKey)
                        }
                    } else {
                        if (backStack.lastOrNull() != FlightListScreenKey) {
                            backStack.clear()
                            backStack.add(FlightListScreenKey)
                        }
                    }
                }

                val provider =
                    entryProvider {
                        entry<LoginScreenKey> {
                            LoginScreen(viewModel = authVm)
                        }
                        entry<FlightListScreenKey> {
                            FlightListScreen(
                                viewModel = flightsVm,
                                onSignOut = { authVm.signOut() },
                            )
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
