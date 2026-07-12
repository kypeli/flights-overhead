package com.kypeli.flightsoverhead.di

import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import com.kypeli.flightsoverhead.viewmodel.FlightsViewModel
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.GraphExtension

@GraphExtension(ViewModelScope::class)
interface ViewModelGraph {
    val flightsViewModel: FlightsViewModel

    @ContributesTo(AppScope::class)
    @GraphExtension.Factory
    interface Factory {
        fun createViewModelGraph(): ViewModelGraph
    }
}
