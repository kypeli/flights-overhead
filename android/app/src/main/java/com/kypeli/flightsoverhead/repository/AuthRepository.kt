package com.kypeli.flightsoverhead.repository

import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.kypeli.flightsoverhead.data.model.User
import com.kypeli.flightsoverhead.di.scope.ViewModelScope
import dev.zacsweers.metro.ContributesBinding
import dev.zacsweers.metro.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface AuthRepository {
    val currentUser: StateFlow<User?>

    suspend fun signIn(
        email: String,
        password: String,
    ): Result<Unit>

    suspend fun signUp(
        email: String,
        password: String,
    ): Result<Unit>

    suspend fun signOut(): Result<Unit>

    suspend fun getAccessToken(forceRefresh: Boolean = false): String?
}

@ContributesBinding(ViewModelScope::class)
@Inject
class AuthRepositoryImpl : AuthRepository {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    override val currentUser: StateFlow<User?>
        field = MutableStateFlow(auth.currentUser?.toUser())

    init {
        auth.addAuthStateListener { firebaseAuth ->
            currentUser.value = firebaseAuth.currentUser?.toUser()
        }
    }

    override suspend fun signIn(
        email: String,
        password: String,
    ): Result<Unit> =
        runCatching {
            auth.signInWithEmailAndPassword(email, password).awaitTask()
        }.map { }

    override suspend fun signUp(
        email: String,
        password: String,
    ): Result<Unit> =
        runCatching {
            auth.createUserWithEmailAndPassword(email, password).awaitTask()
        }.map { }

    override suspend fun signOut(): Result<Unit> =
        runCatching {
            auth.signOut()
        }

    override suspend fun getAccessToken(forceRefresh: Boolean): String? =
        runCatching {
            val user = auth.currentUser ?: return null
            user.getIdToken(forceRefresh).awaitTask().token
        }.getOrNull()
}

private fun com.google.firebase.auth.FirebaseUser.toUser(): User = User(uid = uid, email = email)

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
