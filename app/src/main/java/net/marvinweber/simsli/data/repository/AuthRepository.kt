package net.marvinweber.simsli.data.repository

import android.content.Intent
import kotlinx.coroutines.flow.Flow

sealed class AuthState {
    data object SignedOut : AuthState()
    data class SignedIn(val userId: String, val email: String?) : AuthState()
}

interface AuthRepository {
    /** Emits the current auth status and every subsequent change (login, logout, refresh). */
    val authState: Flow<AuthState>

    suspend fun currentUserId(): String?

    suspend fun currentUserEmail(): String?

    /** Sends a magic link to [email]. Completes when the mail has been sent, not when the user signs in. */
    suspend fun sendMagicLink(email: String): Result<Unit>

    suspend fun signOut(): Result<Unit>

    /**
     * Feeds an intent that may contain a `simsli://auth/callback` deep link to the auth client.
     * Call from onCreate and onNewIntent. Returns true if the intent was an auth deep link.
     */
    suspend fun handleDeepLink(intent: Intent?): Boolean
}
