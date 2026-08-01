package com.kypeli.flightsoverhead.data

import android.content.Context
import dev.zacsweers.metro.Inject
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

@Inject
class AirlineResolver(
    private val context: Context,
) {
    companion object {
        private const val ASSET_FILE_NAME = "airlines.json"
        private const val KIWI_LOGO_BASE_URL = "https://images.kiwi.com/airlines/64"
        private val json = Json { ignoreUnknownKeys = true }
    }

    @Serializable
    private data class AirlineEntry(
        @SerialName("id") val id: String = "",
        @SerialName("name") val name: String = "",
        @SerialName("logo") val logoUrl: String = "",
    )

    private data class AirlineInfo(
        val name: String,
        val logoUrl: String,
    )

    private val airlineMap: Map<String, AirlineInfo> by lazy {
        try {
            val jsonString =
                context.assets
                    .open(ASSET_FILE_NAME)
                    .bufferedReader()
                    .use { it.readText() }
            val entries = json.decodeFromString<List<AirlineEntry>>(jsonString)
            entries
                .filter { it.id.isNotBlank() }
                .associateBy({ it.name }, { AirlineInfo(it.name, it.logoUrl) })
        } catch (e: Exception) {
            Timber.e(e, "Failed to load %s", ASSET_FILE_NAME)
            emptyMap()
        }
    }

    /**
     * Resolves the airline logo URL based on the flight number's airline code.
     * e.g. "AA123" -> airline code "AA" -> fetches logo from airlines.json or falls back to Kiwi CDN.
     */
    fun getLogoUrl(airlineName: String): String {
        if (airlineName.isBlank()) {
            return ""
        }

        // Check if we have a direct match in dotmarn's airlines.json
        val info = airlineMap[airlineName]
        if (info != null && info.logoUrl.isNotBlank()) {
            return info.logoUrl
        }

        // If not found in JSON, fall back to the default Kiwi CDN URL pattern
        return "$KIWI_LOGO_BASE_URL/$airlineName.png"
    }
}
