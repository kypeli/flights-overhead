package com.kypeli.flightsoverhead

import android.app.Application
import com.kypeli.flightsoverhead.di.AppGraph
import dev.zacsweers.metro.createGraphFactory

class FlightsOverheadApplication : Application() {
    lateinit var appGraph: AppGraph
        private set

    override fun onCreate() {
        super.onCreate()
        appGraph = createGraphFactory<AppGraph.Factory>().create(this)
    }
}
