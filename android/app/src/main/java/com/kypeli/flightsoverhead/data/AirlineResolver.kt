package com.kypeli.flightsoverhead.data

import android.content.Context
import dev.zacsweers.metro.Inject
import org.json.JSONArray
import java.io.IOException

private const val ASSET_FILE_NAME = "airlines.json"
private const val KIWI_LOGO_BASE_URL = "https://images.kiwi.com/airlines/64"

@Inject
class AirlineResolver(
    private val context: Context
) {
    // Thread-safe cache of resolved logos
    @Volatile
    private var airlineLogoMap: Map<String, String>? = null

    /**
     * Initializes and loads the airline logo mapping from assets.
     */
    private fun loadAirlineMap(): Map<String, String> {
        val currentMap = airlineLogoMap
        if (currentMap != null) return currentMap

        synchronized(this) {
            val doubleCheckMap = airlineLogoMap
            if (doubleCheckMap != null) return doubleCheckMap

            val newMap = mutableMapOf<String, String>()
            try {
                val jsonString = context.assets.open(ASSET_FILE_NAME).bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val entry = jsonArray.getJSONObject(i)
                    val id = entry.optString("id").uppercase()
                    val logo = entry.optString("logo")
                    if (id.isNotBlank() && !logo.isNullOrBlank()) {
                        newMap[id] = logo
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
            } catch (e: Exception) {
                e.printStackTrace()
            }
            airlineLogoMap = newMap
            return newMap
        }
    }

    /**
     * Resolves the airline logo URL based on the flight number's airline code.
     * e.g. "AA123" -> airline code "AA" -> fetches logo from airlines.json or falls back to Kiwi CDN.
     */
    fun getLogoUrl(flightNumber: String): String {
        // Extract letters prefix from flight number (e.g., "AA123" -> "AA", "FIN123" -> "FIN")
        val code = flightNumber.takeWhile { it.isLetter() }.uppercase()
        if (code.isBlank()) {
            return ""
        }

        val map = loadAirlineMap()
        // Check if we have a direct match in dotmarn's airlines.json
        val customLogoUrl = map[code]
        if (customLogoUrl != null) {
            return customLogoUrl
        }

        // If not found in JSON, fall back to the default Kiwi CDN URL pattern
        return "$KIWI_LOGO_BASE_URL/$code.png"
    }
}
