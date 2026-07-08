package com.kypeli.flightsoverhead.di

import androidx.compose.runtime.staticCompositionLocalOf
import com.kypeli.flightsoverhead.data.AirlineResolver

val LocalAirlineResolver = staticCompositionLocalOf<AirlineResolver> {
    error("No AirlineResolver provided")
}
