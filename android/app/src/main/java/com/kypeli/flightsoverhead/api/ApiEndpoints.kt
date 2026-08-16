package com.kypeli.flightsoverhead.api

enum class ApiEndpoints(
    val url: String,
) {
    OVERHEAD_FLIGHTS(url = "https://overheadflights-g5q7shkmca-lz.a.run.app"),
    TOKEN(url = "https://token-g5q7shkmca-lz.a.run.app"),
    PUSH_NOTIFICATION(url = "https://pushnotification-g5q7shkmca-lz.a.run.app"),
}
