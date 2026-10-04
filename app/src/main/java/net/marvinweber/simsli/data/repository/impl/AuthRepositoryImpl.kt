package net.marvinweber.simsli.data.repository.impl

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.AuthTokenStorage
import net.marvinweber.simsli.data.remote.SimsliRemoteDataSource
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.SignOutResult
import net.marvinweber.simsli.di.IoDispatcher
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val remoteDataSource: SimsliRemoteDataSource,
    private val tokenStorage: AuthTokenStorage,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : AuthRepository {

    override val authState: Flow<AuthState> = tokenStorage.authState

    override suspend fun currentUserId(): String? = tokenStorage.userId

    override suspend fun currentUserEmail(): String? = tokenStorage.userEmail

    override suspend fun sendMagicLink(email: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                remoteDataSource.requestMagicLink(email)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun signOut(): Result<SignOutResult> {
        return withContext(ioDispatcher) {
            var revoked = false
            try {
                remoteDataSource.logout()
                revoked = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Server-side sign-out failed, clearing local session anyway: ${e.message}")
                tokenStorage.clearSession()
            }
            Result.success(if (revoked) SignOutResult.Complete else SignOutResult.LocalOnly)
        }
    }

    override suspend fun handleDeepLink(intent: Intent?): Boolean {
        if (intent?.data?.scheme != "simsli" || intent.data?.host != "auth") return false
        return withContext(ioDispatcher) {
            Log.d(TAG, "Handling auth deep link: ${intent.data}")
            val uri = intent.data ?: return@withContext false

            val token = uri.getQueryParameter("token")
                ?: uri.getQueryParameter("code")
                ?: uri.fragment?.split("&")?.mapNotNull {
                    val parts = it.split("=", limit = 2)
                    if (parts.size == 2 && parts[0] == "token") parts[1] else null
                }?.firstOrNull()

            if (token != null) {
                try {
                    remoteDataSource.verifyMagicLink(token)
                    Log.i(TAG, "Deep link sign-in succeeded")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Deep link sign-in failed", e)
                    false
                }
            } else {
                val fragment = uri.fragment
                if (!fragment.isNullOrBlank()) {
                    val params = fragment.split("&").associate {
                        val kv = it.split("=", limit = 2)
                        if (kv.size == 2) kv[0] to kv[1] else "" to ""
                    }
                    val accessToken = params["access_token"]
                    val refreshToken = params["refresh_token"]
                    if (!accessToken.isNullOrBlank() && !refreshToken.isNullOrBlank()) {
                        tokenStorage.saveSession(
                            accessToken = accessToken,
                            refreshToken = refreshToken,
                            userId = params["user_id"] ?: "",
                            email = params["email"] ?: ""
                        )
                        Log.i(TAG, "Direct token deep-link sign-in succeeded")
                        return@withContext true
                    }
                }
                false
            }
        }
    }

    private companion object {
        const val TAG = "SimsliAuth"
    }
}
