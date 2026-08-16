package com.kypeli.flightsoverhead.api

import kotlinx.serialization.Serializable

@Serializable
data class TokenRequestDto(
    val installationId: String,
    val platform: String = "android",
)

@Serializable
data class TokenResponseDto(
    val success: Boolean = true,
)
