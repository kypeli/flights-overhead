package com.kypeli.flightsoverhead.di.provider

import com.kypeli.flightsoverhead.BuildConfig
import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import dev.zacsweers.metro.BindingContainer
import dev.zacsweers.metro.ContributesTo
import dev.zacsweers.metro.Provides
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.ANDROID
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

@ContributesTo(ViewModelScope::class)
@BindingContainer
object NetworkProvider {
    @Provides
    fun provideHttpClient(): HttpClient =
        HttpClient(OkHttp) {
            // Install JSON content negotiation
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                    },
                )
            }

            // Install HTTP logging
            if (BuildConfig.DEBUG) {
                install(Logging) {
                    logger = Logger.ANDROID
                    level = LogLevel.ALL
                }
            }
        }
}
