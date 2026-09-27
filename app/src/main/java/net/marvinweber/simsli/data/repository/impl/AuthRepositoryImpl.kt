package net.marvinweber.simsli.data.repository.impl

import android.content.Intent
import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.handleDeeplinks
import io.github.jan.supabase.auth.providers.builtin.OTP
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.SignOutResult
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

    override suspend fun signOut(): Result<SignOutResult> {
        return withContext(ioDispatcher) {
            // auth.signOut() revokes the refresh token server-side, then clears the
            // stored session — but it throws before the clear when the logout call
            // fails (e.g. offline). Signing out must leave a clean device regardless
            // (AUTH-2), so the local session is cleared explicitly in that case.
            val revoked = try {
                supabaseClient.auth.signOut()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Server-side sign-out failed, clearing local session anyway: ${e.message}")
                false
            }
            if (!revoked) {
                try {
                    supabaseClient.auth.clearSession()
                } catch (e: Exception) {
                    return@withContext Result.failure(e)
                }
            }
            Result.success(if (revoked) SignOutResult.Complete else SignOutResult.LocalOnly)
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
