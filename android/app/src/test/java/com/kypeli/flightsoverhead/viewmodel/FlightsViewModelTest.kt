package com.kypeli.flightsoverhead.viewmodel

import android.content.Context
import android.content.ContextWrapper
import com.kypeli.flightsoverhead.data.AirlineResolver
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.data.model.User
import com.kypeli.flightsoverhead.repository.AuthRepository
import com.kypeli.flightsoverhead.repository.FlightsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class FlightsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAuthRepository(
        initialUser: User? = User(uid = "test_uid", email = "test@example.com"),
    ) : AuthRepository {
        override val currentUser = MutableStateFlow(initialUser)

        override suspend fun signIn(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signUp(email: String, password: String): Result<Unit> = Result.success(Unit)
        override suspend fun signOut(): Result<Unit> = Result.success(Unit)
        override suspend fun getAccessToken(forceRefresh: Boolean): String? = "test_token"
    }

    private class FakeFlightsRepository : FlightsRepository {
        val flightsFlow = MutableStateFlow<Result<List<Flight>>>(Result.success(emptyList()))
        var fetchResult: Result<List<Flight>> = Result.success(emptyList())
        var fetchCallCount = 0

        override fun getActiveFlightsFlow(): kotlinx.coroutines.flow.Flow<Result<List<Flight>>> {
            return flightsFlow
        }

        override suspend fun fetchActiveFlights(): Result<List<Flight>> {
            fetchCallCount++
            return fetchResult
        }
    }

    private fun createDummyContext(): Context {
        return object : ContextWrapper(null) {}
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun init_withLoggedInUser_observesFlightsAndUpdatesState() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val mockFlight = Flight(
            airline = "Finnair",
            flightNumber = "AY123",
            departure = "Helsinki",
            arrival = "London",
            hex = "4601F6",
        )
        fakeRepo.flightsFlow.value = Result.success(listOf(mockFlight))
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)
        assertEquals("AY123", viewModel.uiState.value.flights[0].flightNumber)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun realTimeUpdate_automaticallyPushesNewFlightListToUiState() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.flights.isEmpty())

        // Backend pushes new flight
        val flight1 = Flight(airline = "Finnair", flightNumber = "AY123", departure = "Helsinki", arrival = "London", hex = "4601F6")
        fakeRepo.flightsFlow.value = Result.success(listOf(flight1))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)
        assertEquals("AY123", viewModel.uiState.value.flights[0].flightNumber)

        // Backend pushes second flight
        val flight2 = Flight(airline = "British Airways", flightNumber = "BAW227", departure = "London", arrival = "New York", hex = "4006EA")
        fakeRepo.flightsFlow.value = Result.success(listOf(flight1, flight2))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.flights.size)
    }

    @Test
    fun refresh_onSuccess_clearsPreviousError() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        fakeRepo.flightsFlow.value = Result.failure(RuntimeException("Auth error"))
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth)
        advanceUntilIdle()

        assertEquals(UiState.Error.Authentication, viewModel.uiState.value.error)

        // Now push successful update
        val mockFlight = Flight(airline = "Finnair", flightNumber = "AY123", departure = "Helsinki", arrival = "London", hex = "4601F6")
        fakeRepo.flightsFlow.value = Result.success(listOf(mockFlight))
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun flow_onFailure_setsAuthenticationError() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        fakeRepo.flightsFlow.value = Result.failure(RuntimeException("Permission denied"))
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth)
        advanceUntilIdle()

        assertEquals(UiState.Error.Authentication, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.flights.isEmpty())
    }

    @Test
    fun authStateChange_whenUserLogsOut_clearsFlights() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val mockFlight = Flight(airline = "Finnair", flightNumber = "AY123", departure = "Helsinki", arrival = "London", hex = "4601F6")
        fakeRepo.flightsFlow.value = Result.success(listOf(mockFlight))
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)

        // User logs out
        fakeAuth.currentUser.value = null
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.flights.isEmpty())
        assertNull(viewModel.uiState.value.error)
    }
}
