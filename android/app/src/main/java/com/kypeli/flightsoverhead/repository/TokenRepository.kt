package com.kypeli.flightsoverhead.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.installations.FirebaseInstallations
import com.google.firebase.messaging.FirebaseMessaging
import com.kypeli.flightsoverhead.api.TokenApi
import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import io.ktor.client.HttpClient
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface TokenRepository {
    suspend fun registerCurrentInstallation(): Result<Unit>
    suspend fun registerInstallationId(installationId: String): Result<Unit>
}

@SingleIn(ViewModelScope::class)
@ContributesBinding(ViewModelScope::class)
@Inject
class TokenRepositoryImpl(
    private val httpClient: HttpClient,
    private val authRepository: AuthRepository,
) : TokenRepository {
    private val registrationMutex = Mutex()
    private var lastRegisteredKey: String? = null

    override suspend fun registerCurrentInstallation(): Result<Unit> {
        val installationId =
            try {
                FirebaseInstallations.getInstance().id.awaitTask()
            } catch (e: Exception) {
                Timber.e(e, "Failed to retrieve Firebase Installation ID")
                return Result.failure(e)
            }
        return registerInstallationId(installationId)
    }

    override suspend fun registerInstallationId(installationId: String): Result<Unit> {
        return registrationMutex.withLock {
            val uid = authRepository.currentUser.value?.uid ?: ""
            val registrationKey = "$uid:$installationId"

            if (lastRegisteredKey == registrationKey) {
                Timber.d("Installation ID already registered for user (%s), skipping duplicate request", uid)
                return@withLock Result.success(Unit)
            }

            val idToken = authRepository.getAccessToken()
                ?: return@withLock Result.failure(IllegalStateException("User is not authenticated"))

            TokenApi
                .registerInstallationId(
                    httpClient = httpClient,
                    idToken = idToken,
                    installationId = installationId,
                ).map {
                    lastRegisteredKey = registrationKey
                    Timber.d("Successfully registered Installation ID with backend")
                }.onFailure { error ->
                    Timber.e(error, "Failed to register Installation ID with backend")
                }
        }
    }
}

private suspend fun <T> Task<T>.awaitTask(): T =
    suspendCancellableCoroutine { continuation ->
        addOnCompleteListener { task ->
            if (task.isSuccessful) {
                continuation.resume(task.result)
            } else {
                continuation.resumeWithException(task.exception ?: RuntimeException("Task failed"))
            }
        }
    }
