package com.kypeli.flightsoverhead

import android.app.Application
import com.kypeli.flightsoverhead.di.AppGraph
import com.kypeli.flightsoverhead.di.ViewModelGraph
import dev.zacsweers.metro.createGraphFactory

class FlightsOverheadApplication : Application() {
    lateinit var appGraph: AppGraph
        private set
    lateinit var viewModelGraph: ViewModelGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>().create(this)
        viewModelGraph = appGraph.createViewModelGraph()
    }
}
