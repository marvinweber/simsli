package net.marvinweber.simsli.data.local

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import net.marvinweber.simsli.BuildConfig
import net.marvinweber.simsli.data.repository.AuthState
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthTokenStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _authState = MutableStateFlow(computeAuthState())
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    var serverUrl: String
        get() = prefs.getString(KEY_SERVER_URL, null) ?: BuildConfig.SERVER_URL
        set(value) {
            prefs.edit().putString(KEY_SERVER_URL, value.trimEnd('/')).apply()
        }

    /**
     * Install-stable identifier for sync diagnostics (never cleared by sign-out).
     * Sent as `X-Simsli-Device-Id` so server logs can attribute requests to a device.
     */
    val deviceId: String
        get() = prefs.getString(KEY_DEVICE_ID, null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString(KEY_DEVICE_ID, it).apply()
        }

    /** Short form of [deviceId] for log lines. */
    val shortDeviceId: String
        get() = deviceId.take(8)

    val accessToken: String?
        get() = prefs.getString(KEY_ACCESS_TOKEN, null)

    val refreshToken: String?
        get() = prefs.getString(KEY_REFRESH_TOKEN, null)

    val userId: String?
        get() = prefs.getString(KEY_USER_ID, null)

    val userEmail: String?
        get() = prefs.getString(KEY_USER_EMAIL, null)

    fun saveSession(accessToken: String, refreshToken: String, userId: String, email: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .putString(KEY_USER_ID, userId)
            .putString(KEY_USER_EMAIL, email)
            .apply()
        _authState.value = AuthState.SignedIn(userId = userId, email = email)
    }

    fun updateTokens(accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_REFRESH_TOKEN, refreshToken)
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_USER_ID)
            .remove(KEY_USER_EMAIL)
            .apply()
        _authState.value = AuthState.SignedOut
    }

    private fun computeAuthState(): AuthState {
        val uid = prefs.getString(KEY_USER_ID, null)
        val token = prefs.getString(KEY_ACCESS_TOKEN, null)
        val email = prefs.getString(KEY_USER_EMAIL, null)
        return if (!uid.isNullOrBlank() && !token.isNullOrBlank()) {
            AuthState.SignedIn(userId = uid, email = email)
        } else {
            AuthState.SignedOut
        }
    }

    companion object {
        private const val PREFS_NAME = "simsli_auth_prefs"
        private const val KEY_SERVER_URL = "simsli_server_url"
        private const val KEY_DEVICE_ID = "simsli_device_id"
        private const val KEY_ACCESS_TOKEN = "simsli_access_token"
        private const val KEY_REFRESH_TOKEN = "simsli_refresh_token"
        private const val KEY_USER_ID = "simsli_user_id"
        private const val KEY_USER_EMAIL = "simsli_user_email"

        // Default local dev server: 10.0.2.2 reaches host machine on Android emulator
        const val DEFAULT_SERVER_URL = "http://10.0.2.2:8080"
        const val MIN_SERVER_API_VERSION = 1
    }
}
