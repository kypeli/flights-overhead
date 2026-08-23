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
            val map = mutableMapOf<String, AirlineInfo>()
            for (entry in entries) {
                if (entry.id.isBlank() && entry.name.isBlank()) continue
                val info = AirlineInfo(entry.name, entry.logoUrl)
                if (entry.name.isNotBlank()) {
                    map[entry.name.trim().lowercase()] = info
                    map[entry.name.trim()] = info
                }
                if (entry.id.isNotBlank()) {
                    map[entry.id.trim().lowercase()] = info
                    map[entry.id.trim()] = info
                }
            }
            map
        } catch (e: Exception) {
            Timber.e(e, "Failed to load %s", ASSET_FILE_NAME)
            emptyMap()
        }
    }

    /**
     * Resolves the airline logo URL based on the airline name or flight number.
     * e.g. "Finnair" -> logo for AY, or "AY123" -> logo for AY from airlines.json.
     */
    fun getLogoUrl(airlineName: String, flightNumber: String = ""): String {
        val trimmedAirline = airlineName.trim()
        val trimmedFlightNumber = flightNumber.trim()

        if (trimmedAirline.isNotBlank()) {
            val info = airlineMap[trimmedAirline.lowercase()] ?: airlineMap[trimmedAirline]
            if (info != null && info.logoUrl.isNotBlank()) {
                return info.logoUrl
            }
        }

        if (trimmedFlightNumber.isNotBlank()) {
            val prefix3 = trimmedFlightNumber.take(3).lowercase()
            val info3 = airlineMap[prefix3]
            if (info3 != null && info3.logoUrl.isNotBlank()) {
                return info3.logoUrl
            }

            val prefix2 = trimmedFlightNumber.take(2).lowercase()
            val info2 = airlineMap[prefix2]
            if (info2 != null && info2.logoUrl.isNotBlank()) {
                return info2.logoUrl
            }
        }

        if (trimmedAirline.isNotBlank()) {
            return "$KIWI_LOGO_BASE_URL/$trimmedAirline.png"
        }

        return ""
    }
}

