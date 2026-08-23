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
import java.text.NumberFormat
import java.util.Locale

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

                AircraftFlight(flight = flight)
            }

            DepartureArrival(flight = flight)

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
            )

            AltitudeAndModel(flight = flight)
        }
    }
}

@Composable
private fun AircraftFlight(
    flight: Flight,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        FlightPathChip(
            path = flight.flightPath,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val distanceText =
            if (flight.distanceKm > 0.0) {
                if (flight.distanceKm >= 10.0) {
                    "${String.format(Locale.US, "%.0f", flight.distanceKm)} km away"
                } else {
                    "${String.format(Locale.US, "%.1f", flight.distanceKm)} km away"
                }
            } else {
                "—"
            }

        Text(
            text = distanceText,
            style = DataMonoStyle.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun AltitudeAndModel(
    flight: Flight,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
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
            val altitudeText =
                if (flight.altitudeMeters > 0) {
                    "${NumberFormat.getNumberInstance(Locale.US).format(flight.altitudeMeters)} m"
                } else {
                    "—"
                }
            Text(
                text = altitudeText,
                style = DataMonoStyle.copy(fontSize = 13.sp),
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        if (flight.aircraftModel.isNotBlank()) {
            Text(
                text = flight.aircraftModel,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun DepartureArrival(
    flight: Flight,
    modifier: Modifier = Modifier,
) {
    val originCode = flight.originCode.ifBlank { "---" }
    val destCode = flight.destinationCode.ifBlank { "---" }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 22.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start,
        ) {
            RouteAirport(
                code = originCode,
                label = "ORIGIN",
                city = flight.departure.takeIf { it.isNotBlank() },
            )
            Spacer(modifier = Modifier.size(32.dp))
            Icon(
                painter = painterResource(R.drawable.outline_line_end_arrow_notch_24),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(modifier = Modifier.size(32.dp))
            RouteAirport(
                code = destCode,
                label = "DESTINATION",
                city = flight.arrival.takeIf { it.isNotBlank() },
            )
        }
    }
}

@Composable
private fun RouteAirport(
    code: String,
    label: String,
    modifier: Modifier = Modifier,
    city: String? = null,
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
        if (!city.isNullOrBlank() && !city.equals(code, ignoreCase = true)) {
            Text(
                text = city,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
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
        val airlineText = flight.airline.ifBlank { "UNKNOWN OPERATOR" }
        Text(
            text = airlineText.uppercase(),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        val flightNumberText = flight.flightNumber.ifBlank { flight.hex.ifBlank { "---" } }
        Text(
            text = flightNumberText,
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
    val fallbackText =
        flight.flightNumber.take(2).takeIf { it.isNotBlank() && it != "N/" && it != "--" }
            ?: flight.airline.take(2).takeIf { it.isNotBlank() }
            ?: "?"

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
        if (flight.logoUrl.isNotBlank()) {
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
                            text = fallbackText,
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
                    text = fallbackText,
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
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            FlightRow(
                flight =
                    Flight(
                        airline = "Finnair",
                        flightNumber = "AY123",
                        departure = "Helsinki",
                        arrival = "London",
                        originCode = "HEL",
                        destinationCode = "LHR",
                        altitudeMeters = 3658,
                        flightPath = FlightPath.Climbing,
                        distanceKm = 28.0,
                        aircraftModel = "Airbus A350-900",
                        registration = "OH-LWA",
                    ),
            )
            FlightRow(
                flight =
                    Flight(
                        airline = "British Airways",
                        flightNumber = "BAW227",
                        departure = "London",
                        arrival = "New York",
                        originCode = "LHR",
                        destinationCode = "JFK",
                        altitudeMeters = 10668,
                        flightPath = FlightPath.Cruising,
                        distanceKm = 14.2,
                        aircraftModel = "Boeing 777-200",
                        registration = "G-VIIA",
                    ),
            )
        }
    }
}

