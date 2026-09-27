package net.marvinweber.simsli.data.repository

import android.content.Intent
import kotlinx.coroutines.flow.Flow

sealed class AuthState {
    data object SignedOut : AuthState()
    data class SignedIn(val userId: String, val email: String?) : AuthState()
}

/** Sign-out outcome: the device always ends up signed out; the variants tell how far the revoke got. */
sealed interface SignOutResult {
    /** Session revoked server-side and cleared locally. */
    data object Complete : SignOutResult

    /** Session cleared locally, but the server revoke failed (typically offline) —
     *  the refresh token expires server-side on its own. */
    data object LocalOnly : SignOutResult
}

interface AuthRepository {
    /** Emits the current auth status and every subsequent change (login, logout, refresh). */
    val authState: Flow<AuthState>

    suspend fun currentUserId(): String?

    suspend fun currentUserEmail(): String?

    /** Sends a magic link to [email]. Completes when the mail has been sent, not when the user signs in. */
    suspend fun sendMagicLink(email: String): Result<Unit>

    /**
     * Signs out on this device in every case — only a failure to clear the stored
     * session itself produces a [Result.failure]. [SignOutResult.LocalOnly] reports
     * that the refresh token could not be revoked server-side.
     */
    suspend fun signOut(): Result<SignOutResult>

    /**
     * Feeds an intent that may contain a `simsli://auth/callback` deep link to the auth client.
     * Call from onCreate and onNewIntent. Returns true if the intent was an auth deep link.
     */
    suspend fun handleDeepLink(intent: Intent?): Boolean
}
