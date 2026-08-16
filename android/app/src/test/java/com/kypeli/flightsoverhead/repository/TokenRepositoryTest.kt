package com.kypeli.flightsoverhead.repository

import com.kypeli.flightsoverhead.api.ApiEndpoints
import com.kypeli.flightsoverhead.data.model.User
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class TokenRepositoryTest {

    private class FakeAuthRepository(
        initialUser: User? = User(uid = "test_uid_1", email = "test@example.com"),
        private val tokenToReturn: String? = "valid_token_123",
    ) : AuthRepository {
        override val currentUser: StateFlow<User?> = MutableStateFlow(initialUser)

        override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun getAccessToken(forceRefresh: Boolean): String? = tokenToReturn
    }

    private fun createMockHttpClient(
        requestCount: AtomicInteger,
        responseStatus: HttpStatusCode = HttpStatusCode.OK,
        responseContent: String = """{"success":true}""",
        onUrlAssert: ((String) -> Unit)? = null,
    ): HttpClient {
        val mockEngine = MockEngine { request ->
            requestCount.incrementAndGet()
            onUrlAssert?.invoke(request.url.toString())
            respond(
                content = responseContent,
                status = responseStatus,
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        }

        return HttpClient(mockEngine) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        prettyPrint = true
                        encodeDefaults = true
                    },
                )
            }
        }
    }

    @Test
    fun registerInstallationId_sendsPostToTokenEndpoint() = runBlocking {
        val requestCount = AtomicInteger(0)
        var requestedUrl = ""
        val httpClient = createMockHttpClient(requestCount) { url -> requestedUrl = url }
        val authRepository = FakeAuthRepository()
        val repository = TokenRepositoryImpl(httpClient, authRepository)

        val result = repository.registerInstallationId("test_fid_1")

        assertTrue(result.isSuccess)
        assertEquals(1, requestCount.get())
        assertEquals(ApiEndpoints.TOKEN.url, requestedUrl)
    }

    @Test
    fun registerInstallationId_duplicateCallsForSameUserAndFid_skipsDuplicateNetworkCall() = runBlocking {
        val requestCount = AtomicInteger(0)
        val httpClient = createMockHttpClient(requestCount)
        val authRepository = FakeAuthRepository()
        val repository = TokenRepositoryImpl(httpClient, authRepository)

        val result1 = repository.registerInstallationId("test_fid_1")
        val result2 = repository.registerInstallationId("test_fid_1")

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        assertEquals(1, requestCount.get())
    }

    @Test
    fun registerInstallationId_differentFid_executesNewNetworkCall() = runBlocking {
        val requestCount = AtomicInteger(0)
        val httpClient = createMockHttpClient(requestCount)
        val authRepository = FakeAuthRepository()
        val repository = TokenRepositoryImpl(httpClient, authRepository)

        val result1 = repository.registerInstallationId("test_fid_1")
        val result2 = repository.registerInstallationId("test_fid_2")

        assertTrue(result1.isSuccess)
        assertTrue(result2.isSuccess)
        assertEquals(2, requestCount.get())
    }

    @Test
    fun registerInstallationId_unauthenticatedUser_failsWithoutNetworkCall() = runBlocking {
        val requestCount = AtomicInteger(0)
        val httpClient = createMockHttpClient(requestCount)
        val authRepository = FakeAuthRepository(initialUser = null, tokenToReturn = null)
        val repository = TokenRepositoryImpl(httpClient, authRepository)

        val result = repository.registerInstallationId("test_fid_1")

        assertTrue(result.isFailure)
        assertEquals(0, requestCount.get())
    }

    @Test
    fun registerInstallationId_concurrentCallsForSameUserAndFid_executesOnlyOneNetworkCall() = runBlocking {
        val requestCount = AtomicInteger(0)
        val httpClient = createMockHttpClient(requestCount)
        val authRepository = FakeAuthRepository()
        val repository = TokenRepositoryImpl(httpClient, authRepository)

        val job1 = async { repository.registerInstallationId("test_fid_concurrent") }
        val job2 = async { repository.registerInstallationId("test_fid_concurrent") }

        val res1 = job1.await()
        val res2 = job2.await()

        assertTrue(res1.isSuccess)
        assertTrue(res2.isSuccess)
        assertEquals(1, requestCount.get())
    }
}
