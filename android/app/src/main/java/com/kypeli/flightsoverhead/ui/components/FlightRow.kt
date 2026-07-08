package com.kypeli.flightsoverhead.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Alignment.Companion
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.CompositionLocalProvider
import coil3.compose.SubcomposeAsyncImage
import com.kypeli.flightsoverhead.R
import com.kypeli.flightsoverhead.data.AirlineResolver
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.di.LocalAirlineResolver
import com.kypeli.flightsoverhead.ui.theme.DataMonoStyle
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme
import com.kypeli.flightsoverhead.ui.theme.OnSurface
import com.kypeli.flightsoverhead.ui.theme.Outline
import com.kypeli.flightsoverhead.ui.theme.Primary
import com.kypeli.flightsoverhead.ui.theme.Secondary

@Composable
fun FlightRow(
    flight: Flight,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .padding(12.dp)
                .fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AirlineLogo(
            flightNumber = flight.flightNumber,
            modifier = Modifier.padding(end = 8.dp),
        )

        Column(modifier = Modifier.weight(1.0f)) {
            FlightDetails(flight)
            DepartureArrival()
            Altitude()
        }

        AircraftFlight()
    }
}

@Composable
private fun RowScope.AircraftFlight() {
    Column(modifier = Modifier.align(Alignment.Top), horizontalAlignment = Alignment.End) {
        StatusChip(status = "climbing", modifier = Modifier.padding(bottom = 4.dp))
        Text(
            text = "28 mi away",
            style = DataMonoStyle,
            color = Primary,
        )
    }
}

@Composable
private fun Altitude() {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.outline_altitude_24),
            contentDescription = null,
            modifier =
                Modifier
                    .size(24.dp)
                    .padding(end = 8.dp),
        )
        Text(
            text = "12,000 ft",
            style = DataMonoStyle,
            color = Secondary,
        )
    }
}

@Composable
private fun DepartureArrival() {
    Row(
        modifier = Modifier.padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "HEL",
            style = DataMonoStyle.copy(fontSize = 24.sp),
            color = OnSurface,
        )
        Icon(
            painter = painterResource(R.drawable.outline_line_end_arrow_notch_24),
            contentDescription = null,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        Text(
            text = "TRE",
            style = DataMonoStyle.copy(fontSize = 24.sp),
            color = OnSurface,
        )
    }
}

@Composable
private fun ColumnScope.FlightDetails(flight: Flight) {
    Text(
        text = flight.airline.uppercase(),
        style = MaterialTheme.typography.headlineSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = flight.flightNumber,
        style = MaterialTheme.typography.headlineMedium,
        modifier = Modifier.padding(bottom = 12.dp),
    )
}

@Composable
private fun AirlineLogo(
    flightNumber: String,
    modifier: Modifier = Modifier,
) {
    val resolver = LocalAirlineResolver.current
    val logoUrl = remember(flightNumber) { resolver.getLogoUrl(flightNumber) }
    val airlineCode = remember(flightNumber) { flightNumber.takeWhile { it.isLetter() }.uppercase() }

    Box(
        modifier =
            modifier
                .border(
                    width = 1.dp,
                    color = Outline,
                    shape = MaterialTheme.shapes.extraSmall,
                ).background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.extraSmall,
                ).size(64.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (logoUrl.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = logoUrl,
                contentDescription = "Airline Logo",
                modifier = Modifier.fillMaxSize().padding(4.dp),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                },
                error = {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = airlineCode.take(2),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(Primary),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "?",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FlightRowPreview() {
    val context = LocalContext.current
    val dummyResolver = remember { AirlineResolver(context) }
    FlightsOverheadTheme {
        CompositionLocalProvider(LocalAirlineResolver provides dummyResolver) {
            FlightRow(
                flight =
                    Flight(
                        airline = "SkyTrack Pro",
                        flightNumber = "AY001",
                        departure = "SFO",
                        arrival = "JFK",
                    ),
            )
        }
    }
}
