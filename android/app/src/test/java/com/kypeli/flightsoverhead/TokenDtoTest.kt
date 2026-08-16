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
    fun deserialize_tokenResponseDto_parsesSuccessField() {
        val jsonString = """{"success": true}"""
        val response = json.decodeFromString<TokenResponseDto>(jsonString)

        assertTrue(response.success)
    }
}
