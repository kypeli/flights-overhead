package com.kypeli.flightsoverhead.viewmodel

import com.kypeli.flightsoverhead.data.model.User
import com.kypeli.flightsoverhead.repository.AuthRepository
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AuthViewModelTest {

    private class FakeAuthRepository : AuthRepository {
        override val currentUser = MutableStateFlow<User?>(null)
        var signInResult: Result<Unit> = Result.success(Unit)
        var signUpResult: Result<Unit> = Result.success(Unit)
        var signOutResult: Result<Unit> = Result.success(Unit)

        override suspend fun signIn(email: String, password: String): Result<Unit> = signInResult
        override suspend fun signUp(email: String, password: String): Result<Unit> = signUpResult
        override suspend fun signOut(): Result<Unit> = signOutResult
        override suspend fun getAccessToken(forceRefresh: Boolean): String? = null
    }

    @Test
    fun initialState_isNotLoadingAndNoError() {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)

        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun clearError_resetsErrorState() {
        val fakeRepo = FakeAuthRepository()
        val viewModel = AuthViewModel(fakeRepo)

        viewModel.clearError()
        assertNull(viewModel.uiState.value.error)
    }
}
