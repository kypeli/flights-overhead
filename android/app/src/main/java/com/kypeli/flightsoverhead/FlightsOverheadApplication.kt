package com.kypeli.flightsoverhead

import android.app.Application
import com.kypeli.flightsoverhead.di.AppGraph
import com.kypeli.flightsoverhead.di.ViewModelGraph
import dev.zacsweers.metro.createGraphFactory
import timber.log.Timber

class FlightsOverheadApplication : Application() {
    lateinit var appGraph: AppGraph
        private set
    lateinit var viewModelGraph: ViewModelGraph
        private set

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        appGraph = createGraphFactory<AppGraph.Factory>().create(this)
        viewModelGraph = appGraph.createViewModelGraph()
        viewModelGraph.tokenService.startObserving()
    }
}
