package com.kypeli.flightsoverhead

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.kypeli.flightsoverhead.navigation.FlightListScreenKey
import com.kypeli.flightsoverhead.navigation.LoginScreenKey
import com.kypeli.flightsoverhead.ui.screens.FlightListScreen
import com.kypeli.flightsoverhead.ui.screens.LoginScreen
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme
import timber.log.Timber

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FlightsOverheadApplication
        val flightsVm = app.viewModelGraph.flightsViewModel
        val authVm = app.viewModelGraph.authViewModel

        setContent {
            FlightsOverheadTheme {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val permissionLauncher =
                        rememberLauncherForActivityResult(
                            contract = ActivityResultContracts.RequestPermission(),
                        ) { isGranted ->
                            Timber.d("POST_NOTIFICATIONS permission result: %b", isGranted)
                        }

                    LaunchedEffect(Unit) {
                        if (
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.POST_NOTIFICATIONS,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val app = application as? FlightsOverheadApplication
        app?.viewModelGraph?.flightsViewModel?.refresh()
    }
}

