package com.kypeli.flightsoverhead.api

import kotlinx.serialization.Serializable

@Serializable
data class DeviceInfoDto(
    val manufacturer: String? = null,
    val model: String? = null,
    val osVersion: String? = null,
    val sdkInt: Int? = null,
    val appVersion: String? = null,
    val appBuild: Long? = null,
)

@Serializable
data class TokenRequestDto(
    val installationId: String,
    val platform: String = "android",
    val device: DeviceInfoDto? = null,
)

@Serializable
data class TokenResponseDto(
    val success: Boolean = true,
)
