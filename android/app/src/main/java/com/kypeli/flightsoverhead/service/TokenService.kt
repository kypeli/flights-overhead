package com.kypeli.flightsoverhead.service

import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import com.kypeli.flightsoverhead.repository.AuthRepository
import com.kypeli.flightsoverhead.repository.TokenRepository
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber

interface TokenService {
    fun startObserving(scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO))
    suspend fun registerInstallationId(installationId: String): Result<Unit>
}

@SingleIn(ViewModelScope::class)
@ContributesBinding(ViewModelScope::class)
@Inject
class TokenServiceImpl(
    private val authRepository: AuthRepository,
    private val tokenRepository: TokenRepository,
) : TokenService {
    private var observationJob: Job? = null

    override fun startObserving(scope: CoroutineScope) {
        observationJob?.cancel()
        observationJob =
            scope.launch {
                authRepository.currentUser.collect { user ->
                    if (user != null) {
                        Timber.d("User authenticated (%s), registering FCM installation token", user.uid)
                        tokenRepository.registerCurrentInstallation()
                    }
                }
            }
    }

    override suspend fun registerInstallationId(installationId: String): Result<Unit> =
        tokenRepository.registerInstallationId(installationId)
}
