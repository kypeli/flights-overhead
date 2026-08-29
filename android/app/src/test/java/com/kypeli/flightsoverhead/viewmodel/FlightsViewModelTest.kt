package com.kypeli.flightsoverhead.viewmodel

import android.content.Context
import android.content.ContextWrapper
import com.kypeli.flightsoverhead.data.AirlineResolver
import com.kypeli.flightsoverhead.data.model.Flight
import com.kypeli.flightsoverhead.data.model.User
import com.kypeli.flightsoverhead.repository.AuthRepository
import com.kypeli.flightsoverhead.repository.FlightsRepository
import com.kypeli.flightsoverhead.repository.FlightsSnapshot
import com.kypeli.flightsoverhead.service.FlightNotificationService
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

private fun successSnapshot(
    flights: List<Flight>,
    isFromCache: Boolean = false,
): Result<FlightsSnapshot> = Result.success(FlightsSnapshot(flights = flights, isFromCache = isFromCache))

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
        val flightsFlow = MutableStateFlow<Result<FlightsSnapshot>>(successSnapshot(emptyList()))
        var fetchResult: Result<List<Flight>> = Result.success(emptyList())
        var fetchCallCount = 0

        override fun getActiveFlightsFlow(): kotlinx.coroutines.flow.Flow<Result<FlightsSnapshot>> {
            return flightsFlow
        }

        override suspend fun fetchActiveFlights(): Result<List<Flight>> {
            fetchCallCount++
            return fetchResult
        }
    }

    private class FakeFlightNotificationService : FlightNotificationService {
        val cancelledHexes = mutableListOf<String>()
        val inactiveCalls = mutableListOf<Collection<String>>()
        val shownNotifications = mutableListOf<String>()

        override fun showFlightNotification(title: String, body: String, data: Map<String, String>) {
            shownNotifications.add(title)
        }

        override fun cancelNotification(intent: android.content.Intent?) {}

        override fun cancelNotificationForHex(hex: String) {
            cancelledHexes.add(hex)
        }

        override fun cancelNotificationsForInactiveFlights(activeHexes: Collection<String>) {
            inactiveCalls.add(activeHexes)
        }

        override fun getNotificationId(hex: String): Int {
            return hex.trim().uppercase().hashCode()
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
        val fakeNotificationService = FakeFlightNotificationService()
        val mockFlight = Flight(
            airline = "Finnair",
            flightNumber = "AY123",
            departure = "Helsinki",
            arrival = "London",
            hex = "4601F6",
        )
        fakeRepo.flightsFlow.value = successSnapshot(listOf(mockFlight))
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)
        assertEquals("AY123", viewModel.uiState.value.flights[0].flightNumber)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun realTimeUpdate_automaticallyPushesNewFlightListToUiState() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.flights.isEmpty())

        // Backend pushes new flight
        val flight1 = Flight(airline = "Finnair", flightNumber = "AY123", departure = "Helsinki", arrival = "London", hex = "4601F6")
        fakeRepo.flightsFlow.value = successSnapshot(listOf(flight1))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)
        assertEquals("AY123", viewModel.uiState.value.flights[0].flightNumber)

        // Backend pushes second flight
        val flight2 = Flight(airline = "British Airways", flightNumber = "BAW227", departure = "London", arrival = "New York", hex = "4006EA")
        fakeRepo.flightsFlow.value = successSnapshot(listOf(flight1, flight2))
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.flights.size)
    }

    @Test
    fun flightRemovedFromActiveList_dismissesNotificationForInactiveFlight() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        val airlineResolver = AirlineResolver(createDummyContext())

        val flight1 = Flight(airline = "Finnair", flightNumber = "AY123", departure = "Helsinki", arrival = "London", hex = "4601F6")
        val flight2 = Flight(airline = "British Airways", flightNumber = "BAW227", departure = "London", arrival = "New York", hex = "4006EA")
        fakeRepo.flightsFlow.value = successSnapshot(listOf(flight1, flight2))

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.flights.size)
        assertTrue(fakeNotificationService.cancelledHexes.isEmpty())

        // Flight 1 is no longer active (only Flight 2 remains)
        fakeRepo.flightsFlow.value = successSnapshot(listOf(flight2))
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)
        assertEquals("BAW227", viewModel.uiState.value.flights[0].flightNumber)
        assertTrue(fakeNotificationService.cancelledHexes.contains("4601F6"))
    }

    @Test
    fun allFlightsRemoved_dismissesAllFlightNotifications() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        val airlineResolver = AirlineResolver(createDummyContext())

        val flight1 = Flight(airline = "Finnair", flightNumber = "AY123", departure = "Helsinki", arrival = "London", hex = "4601F6")
        fakeRepo.flightsFlow.value = successSnapshot(listOf(flight1))

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)

        // All flights become inactive
        fakeRepo.flightsFlow.value = successSnapshot(emptyList())
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.flights.isEmpty())
        assertTrue(fakeNotificationService.cancelledHexes.contains("4601F6"))
    }

    @Test
    fun refresh_onSuccess_clearsPreviousError() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        fakeRepo.flightsFlow.value = Result.failure(RuntimeException("Auth error"))
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        assertEquals(UiState.Error.Authentication, viewModel.uiState.value.error)

        // Now push successful update
        val mockFlight = Flight(airline = "Finnair", flightNumber = "AY123", departure = "Helsinki", arrival = "London", hex = "4601F6")
        fakeRepo.flightsFlow.value = successSnapshot(listOf(mockFlight))
        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun flow_onFailure_setsAuthenticationError() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        fakeRepo.flightsFlow.value = Result.failure(RuntimeException("Permission denied"))
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        assertEquals(UiState.Error.Authentication, viewModel.uiState.value.error)
        assertTrue(viewModel.uiState.value.flights.isEmpty())
    }

    @Test
    fun authStateChange_whenUserLogsOut_clearsFlightsAndCancelsInactiveNotifications() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        val mockFlight = Flight(airline = "Finnair", flightNumber = "AY123", departure = "Helsinki", arrival = "London", hex = "4601F6")
        fakeRepo.flightsFlow.value = successSnapshot(listOf(mockFlight))
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.flights.size)

        // User logs out
        fakeAuth.currentUser.value = null
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.flights.isEmpty())
        assertNull(viewModel.uiState.value.error)
        assertTrue(fakeNotificationService.inactiveCalls.last().isEmpty())
    }

    @Test
    fun initialLoad_isLoadingClearedByFirstEmissionEvenWhenFromCache() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        val mockFlight = Flight(
            airline = "Finnair",
            flightNumber = "AY123",
            departure = "Helsinki",
            arrival = "London",
            hex = "4601F6",
        )
        fakeRepo.flightsFlow.value = successSnapshot(listOf(mockFlight), isFromCache = true)
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        assertTrue(viewModel.uiState.value.isLoading)

        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals(1, viewModel.uiState.value.flights.size)
    }

    @Test
    fun onAppResume_armsIsRefreshing() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isRefreshing)

        viewModel.onAppResume()

        assertTrue(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun onAppResume_cachedEmissionKeepsIsRefreshingServerEmissionClearsIt() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        val flight1 = Flight(airline = "Finnair", flightNumber = "AY123", departure = "Helsinki", arrival = "London", hex = "4601F6")
        fakeRepo.flightsFlow.value = successSnapshot(listOf(flight1))
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        viewModel.onAppResume()
        assertTrue(viewModel.uiState.value.isRefreshing)

        // A cache re-emission must not clear the stale window. runCurrent() only
        // runs tasks due now, so the 5s timeout armed by onAppResume() can't
        // fast-forward and mask the emission under test.
        fakeRepo.flightsFlow.value = successSnapshot(listOf(flight1), isFromCache = true)
        runCurrent()
        assertTrue(viewModel.uiState.value.isRefreshing)

        // A server emission clears it
        fakeRepo.flightsFlow.value = successSnapshot(listOf(flight1), isFromCache = false)
        runCurrent()
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun onAppResume_isRefreshingClearsAfterTimeoutWhenNoServerEmissionArrives() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = User(uid = "1", email = "test@example.com"))
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        viewModel.onAppResume()
        assertTrue(viewModel.uiState.value.isRefreshing)

        // Still armed before the 5s timeout elapses
        advanceTimeBy(4.seconds)
        assertTrue(viewModel.uiState.value.isRefreshing)

        // Cleared once the timeout has passed, even without a server emission
        advanceTimeBy(2.seconds)
        assertFalse(viewModel.uiState.value.isRefreshing)
    }

    @Test
    fun onAppResume_whileSignedOut_isNoOp() = runTest {
        val fakeAuth = FakeAuthRepository(initialUser = null)
        val fakeRepo = FakeFlightsRepository()
        val fakeNotificationService = FakeFlightNotificationService()
        val airlineResolver = AirlineResolver(createDummyContext())

        val viewModel = FlightsViewModel(fakeRepo, airlineResolver, fakeAuth, fakeNotificationService)
        advanceUntilIdle()

        viewModel.onAppResume()

        assertFalse(viewModel.uiState.value.isRefreshing)
        assertTrue(viewModel.uiState.value.flights.isEmpty())
    }
}
