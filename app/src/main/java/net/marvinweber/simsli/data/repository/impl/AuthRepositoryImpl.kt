package net.marvinweber.simsli.data.repository.impl

import android.content.Intent
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val supabaseClient: SupabaseClient,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    // The auth plugin persists sessions automatically on Android,
    // so authState replays the restored session on every app start.
    override val authState: Flow<AuthState> = supabaseClient.auth.sessionStatus.map { status ->
        when (status) {
            is SessionStatus.Authenticated -> AuthState.SignedIn(
                userId = status.session.user?.id ?: "",
                email = status.session.user?.email
            )
            else -> AuthState.SignedOut
        }
    }

    override suspend fun currentUserId(): String? {
        return withContext(ioDispatcher) {
            supabaseClient.auth.currentUserOrNull()?.id
        }
    }

    override suspend fun currentUserEmail(): String? {
        return withContext(ioDispatcher) {
            supabaseClient.auth.currentUserOrNull()?.email
        }
    }

    override suspend fun sendMagicLink(email: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                supabaseClient.auth.signInWith(OTP) { this.email = email }
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun signOut(): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                supabaseClient.auth.signOut()
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun handleDeepLink(intent: Intent?): Boolean {
        if (intent?.data?.scheme != "simsli") return false
        return withContext(ioDispatcher) {
            Log.d(TAG, "Handling auth deep link: ${intent.data}")
            supabaseClient.handleDeeplinks(
                intent,
                onSessionSuccess = { Log.i(TAG, "Deep link sign-in succeeded") },
                onError = { error -> Log.e(TAG, "Deep link sign-in failed", error) }
            )
            true
        }
    }

    private companion object {
        const val TAG = "SimsliAuth"
    }
}
