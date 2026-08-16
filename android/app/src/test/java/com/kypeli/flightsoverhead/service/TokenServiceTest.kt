package com.kypeli.flightsoverhead.service

import com.kypeli.flightsoverhead.data.model.User
import com.kypeli.flightsoverhead.repository.AuthRepository
import com.kypeli.flightsoverhead.repository.TokenRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class TokenServiceTest {

    private class FakeAuthRepository(
        initialUser: User? = null,
    ) : AuthRepository {
        override val currentUser = MutableStateFlow(initialUser)

        override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun getAccessToken(forceRefresh: Boolean): String? = "mock_token"
    }

    private class FakeTokenRepository : TokenRepository {
        var registerCurrentInstallationCallCount = 0
        var registeredInstallationIds = mutableListOf<String>()

        override suspend fun registerCurrentInstallation(): Result<Unit> {
            registerCurrentInstallationCallCount++
            return Result.success(Unit)
        }

        override suspend fun registerInstallationId(installationId: String): Result<Unit> {
            registeredInstallationIds.add(installationId)
            return Result.success(Unit)
        }
    }

    @Test
    fun startObserving_whenUserLogsIn_triggersTokenRegistration() = runBlocking {
        val authRepository = FakeAuthRepository(initialUser = null)
        val tokenRepository = FakeTokenRepository()
        val tokenService = TokenServiceImpl(authRepository, tokenRepository)

        val testScope = CoroutineScope(Dispatchers.Unconfined + Job())
        tokenService.startObserving(testScope)

        assertEquals(0, tokenRepository.registerCurrentInstallationCallCount)

        // Simulate user sign in
        authRepository.currentUser.value = User(uid = "test_user_1", email = "test@example.com")

        assertEquals(1, tokenRepository.registerCurrentInstallationCallCount)
    }

    @Test
    fun startObserving_whenUserIsNull_doesNotTriggerTokenRegistration() = runBlocking {
        val authRepository = FakeAuthRepository(initialUser = null)
        val tokenRepository = FakeTokenRepository()
        val tokenService = TokenServiceImpl(authRepository, tokenRepository)

        val testScope = CoroutineScope(Dispatchers.Unconfined + Job())
        tokenService.startObserving(testScope)

        assertEquals(0, tokenRepository.registerCurrentInstallationCallCount)
    }

    @Test
    fun registerInstallationId_delegatesToTokenRepository() = runBlocking {
        val authRepository = FakeAuthRepository()
        val tokenRepository = FakeTokenRepository()
        val tokenService = TokenServiceImpl(authRepository, tokenRepository)

        val result = tokenService.registerInstallationId("custom_fid_999")

        assertEquals(true, result.isSuccess)
        assertEquals(listOf("custom_fid_999"), tokenRepository.registeredInstallationIds)
    }
}
