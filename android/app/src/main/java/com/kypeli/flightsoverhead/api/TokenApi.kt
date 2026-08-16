package com.kypeli.flightsoverhead.api

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

object TokenApi {
    suspend fun registerInstallationId(
        httpClient: HttpClient,
        idToken: String,
        installationId: String,
        platform: String = "android",
    ): Result<TokenResponseDto> =
        runCatching {
            httpClient
                .post(ApiEndpoints.TOKEN.url) {
                    header("Authorization", "Bearer $idToken")
                    contentType(ContentType.Application.Json)
                    setBody(TokenRequestDto(installationId = installationId, platform = platform))
                }.body<TokenResponseDto>()
        }
}
