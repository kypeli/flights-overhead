package com.kypeli.flightsoverhead.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.SubcomposeAsyncImage
import com.kypeli.flightsoverhead.R
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.entity.FlightPath
import com.kypeli.flightsoverhead.ui.theme.DataMonoStyle
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme

@Composable
fun FlightRow(
    flight: Flight,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border =
            BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            ),
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                AirlineLogo(flight = flight)

                FlightDetails(
                    flight = flight,
                    modifier = Modifier.weight(1f),
                )

                AircraftFlight()
            }

            DepartureArrival()

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )

            Altitude()
        }
    }
}

@Composable
private fun AircraftFlight() {
    Column(horizontalAlignment = Alignment.End) {
        FlightPathChip(
            path = FlightPath.Climbing,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        Text(
            text = "28 mi away",
            style = DataMonoStyle.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun Altitude() {
    Row(
        modifier = Modifier.padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.outline_altitude_24),
            contentDescription = null,
            modifier =
                Modifier
                    .size(20.dp)
                    .padding(end = 4.dp),
            tint = MaterialTheme.colorScheme.secondary,
        )
        Text(
            text = "ALTITUDE",
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = "12,000 ft",
            style = DataMonoStyle.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

@Composable
private fun DepartureArrival() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            RouteAirport(
                code = "HEL",
                label = "ORIGIN",
            )
            Spacer(modifier = Modifier.size(32.dp))
            Icon(
                painter = painterResource(R.drawable.outline_line_end_arrow_notch_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(32.dp))
            RouteAirport(
                code = "TRE",
                label = "DESTINATION",
            )
        }
    }
}

@Composable
private fun RouteAirport(
    code: String,
    label: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 10.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = code,
            style = DataMonoStyle.copy(fontSize = 27.sp, fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun FlightDetails(
    flight: Flight,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 12.dp),
    ) {
        Text(
            text = flight.airline.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = flight.flightNumber,
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AirlineLogo(
    flight: Flight,
    modifier: Modifier = Modifier,
) {
    val logoShape = RoundedCornerShape(16.dp)

    Box(
        modifier =
            modifier
                .size(56.dp)
                .clip(logoShape)
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = logoShape,
                ).border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f),
                    shape = logoShape,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (flight.logoUrl.isNotEmpty()) {
            SubcomposeAsyncImage(
                model = flight.logoUrl,
                contentDescription = "Airline Logo",
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                contentScale = ContentScale.Fit,
                loading = {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp,
                        )
                    }
                },
                error = {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = flight.flightNumber.take(2),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                },
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colorScheme.primary),
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
    FlightsOverheadTheme {
        FlightRow(
            flight =
                Flight(
                    airline = "SkyTrack Pro",
                    flightNumber = "AY001",
                    departure = "SFO",
                    arrival = "JFK",
                    logoUrl = "",
                ),
        )
    }
}
