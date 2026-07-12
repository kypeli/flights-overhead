package com.kypeli.flightsoverhead.data

import android.content.Context
import android.util.Log
import dev.zacsweers.metro.Inject
import org.json.JSONArray

private const val ASSET_FILE_NAME = "airlines.json"
private const val KIWI_LOGO_BASE_URL = "https://images.kiwi.com/airlines/64"
private const val TAG = "AirlineResolver"

@Inject
class AirlineResolver(
    private val context: Context,
) {
    private data class AirlineInfo(
        val name: String,
        val logoUrl: String,
    )

    private val airlineMap: Map<String, AirlineInfo> by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        buildMap {
            try {
                val jsonString =
                    context.assets
                        .open(ASSET_FILE_NAME)
                        .bufferedReader()
                        .use { it.readText() }
                val jsonArray = JSONArray(jsonString)
                for (i in 0 until jsonArray.length()) {
                    val entry = jsonArray.getJSONObject(i)
                    val id = entry.optString("id").uppercase()
                    val name = entry.optString("name")
                    val logo = entry.optString("logo")
                    if (id.isNotBlank()) {
                        put(id, AirlineInfo(name, logo))
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load $ASSET_FILE_NAME", e)
            }
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

        // Check if we have a direct match in dotmarn's airlines.json
        val info = airlineMap[code]
        if (info != null && info.logoUrl.isNotBlank()) {
            return info.logoUrl
        }

        // If not found in JSON, fall back to the default Kiwi CDN URL pattern
        return "$KIWI_LOGO_BASE_URL/$code.png"
    }

    /**
     * Resolves the airline name based on the flight number's airline code.
     */
    fun getAirlineName(flightNumber: String): String? {
        val code = flightNumber.takeWhile { it.isLetter() }.uppercase()
        if (code.isBlank()) return null

        return airlineMap[code]?.name?.takeIf { it.isNotBlank() }
    }
}
