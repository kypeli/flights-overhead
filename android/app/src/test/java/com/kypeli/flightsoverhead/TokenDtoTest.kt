package com.kypeli.flightsoverhead

import com.kypeli.flightsoverhead.api.TokenRequestDto
import com.kypeli.flightsoverhead.api.TokenResponseDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokenDtoTest {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
        encodeDefaults = true
    }

    @Test
    fun serialize_tokenRequestDto_containsRequiredFields() {
        val request = TokenRequestDto(installationId = "sample_fid_123", platform = "android")
        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"installationId\":\"sample_fid_123\""))
        assertTrue(jsonString.contains("\"platform\":\"android\""))
    }

    @Test
    fun serialize_tokenRequestDto_defaultPlatformIsAndroid() {
        val request = TokenRequestDto(installationId = "sample_fid_456")
        assertEquals("android", request.platform)
        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"installationId\":\"sample_fid_456\""))
        assertTrue(jsonString.contains("\"platform\":\"android\""))
    }

    @Test
    fun serialize_tokenRequestDto_withDeviceInfo_containsDeviceFields() {
        val deviceInfo = com.kypeli.flightsoverhead.api.DeviceInfoDto(
            manufacturer = "Google",
            model = "Pixel 8 Pro",
            osVersion = "15",
            sdkInt = 35,
            appVersion = "1.0",
            appBuild = 1L,
        )
        val request = TokenRequestDto(
            installationId = "sample_fid_device",
            platform = "android",
            device = deviceInfo,
        )
        val jsonString = json.encodeToString(request)

        assertTrue(jsonString.contains("\"installationId\":\"sample_fid_device\""))
        assertTrue(jsonString.contains("\"manufacturer\":\"Google\""))
        assertTrue(jsonString.contains("\"model\":\"Pixel 8 Pro\""))
        assertTrue(jsonString.contains("\"osVersion\":\"15\""))
        assertTrue(jsonString.contains("\"sdkInt\":35"))
        assertTrue(jsonString.contains("\"appVersion\":\"1.0\""))
        assertTrue(jsonString.contains("\"appBuild\":1"))
    }

    @Test
    fun deserialize_tokenResponseDto_parsesSuccessField() {
        val jsonString = """{"success": true}"""
        val response = json.decodeFromString<TokenResponseDto>(jsonString)

        assertTrue(response.success)
    }
}
