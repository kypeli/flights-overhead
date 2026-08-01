package com.kypeli.flightsoverhead.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kypeli.flightsoverhead.entity.FlightPath
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme
import com.kypeli.flightsoverhead.ui.theme.StatusClimbing
import com.kypeli.flightsoverhead.ui.theme.StatusCruising
import com.kypeli.flightsoverhead.ui.theme.StatusDescending

/**
 * A UI chip component displaying an aircraft's current [FlightPath] phase
 * with color-coded background and text status styling.
 *
 * @param path The current [FlightPath] of the aircraft.
 * @param modifier The [Modifier] to be applied to the chip container.
 */
@Composable
fun FlightPathChip(
    path: FlightPath,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor) =
        when (path) {
            FlightPath.Climbing -> StatusClimbing.copy(alpha = 0.15f) to StatusClimbing
            FlightPath.Cruising -> StatusCruising.copy(alpha = 0.15f) to StatusCruising
            FlightPath.Descending -> StatusDescending.copy(alpha = 0.15f) to StatusDescending
        }

    Box(
        modifier =
            modifier
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = path.name.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun FlightPathChipPreview() {
    FlightsOverheadTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            FlightPathChip(path = FlightPath.Climbing)
        }
    }
}
