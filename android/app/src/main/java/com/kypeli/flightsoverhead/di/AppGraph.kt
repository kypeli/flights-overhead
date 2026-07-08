package com.kypeli.flightsoverhead.di

import android.content.Context
import com.kypeli.flightsoverhead.data.AirlineResolver
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides

@DependencyGraph
interface AppGraph {
    val airlineResolver: AirlineResolver

    @DependencyGraph.Factory
    fun interface Factory {
        fun create(@Provides context: Context): AppGraph
    }
}
