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
import com.kypeli.flightsoverhead.ui.theme.FlightsOverheadTheme
import com.kypeli.flightsoverhead.ui.theme.StatusClimbing
import com.kypeli.flightsoverhead.ui.theme.StatusCruising
import com.kypeli.flightsoverhead.ui.theme.StatusDescending

@Composable
fun StatusChip(
    status: String,
    modifier: Modifier = Modifier,
) {
    val (backgroundColor, textColor) =
        when (status.lowercase()) {
            "climbing" -> StatusClimbing.copy(alpha = 0.15f) to StatusClimbing
            "cruising" -> StatusCruising.copy(alpha = 0.15f) to StatusCruising
            "descending" -> StatusDescending.copy(alpha = 0.15f) to StatusDescending
            else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier =
            modifier
                .background(backgroundColor, RoundedCornerShape(4.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = status.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
        )
    }
}

@Preview(showBackground = true)
@Composable
fun StatusChipPreview() {
    FlightsOverheadTheme {
        Box(modifier = Modifier.padding(16.dp)) {
            StatusChip(status = "climbing")
        }
    }
}
