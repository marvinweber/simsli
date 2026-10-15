package net.marvinweber.simsli.ui.navigation

import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InviteNavTarget {
    data class JoinHousehold(val code: String) : InviteNavTarget
    data class SignIn(val code: String) : InviteNavTarget
}

/**
 * Coordinates household invite deep links (`simsli://invite/<code>`).
 *
 * Behavior per spec (HH-3):
 * - If already signed in: immediately navigates to Household screen to begin join flow.
 * - If not signed in: navigates to Settings screen for sign-in while preserving the code;
 *   upon successful sign-in, automatically transitions to Household screen to resume the join flow.
 */
@Singleton
class InviteDeepLinkManager @Inject constructor(
    private val authRepository: AuthRepository,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SimsliInvite"
    }

    private val _pendingInviteCode = MutableStateFlow<String?>(null)
    val pendingInviteCode: StateFlow<String?> = _pendingInviteCode.asStateFlow()

    private val _navEvents = MutableSharedFlow<InviteNavTarget>(replay = 1, extraBufferCapacity = 1)
    val navEvents: SharedFlow<InviteNavTarget> = _navEvents.asSharedFlow()

    init {
        scope.launch {
            authRepository.authState
                .distinctUntilChanged()
                .collectLatest { state ->
                    if (state is AuthState.SignedIn) {
                        val code = _pendingInviteCode.value
                        if (code != null) {
                            Log.d(TAG, "Auth state changed to SignedIn with pending invite code $code — navigating to JoinHousehold")
                            _navEvents.tryEmit(InviteNavTarget.JoinHousehold(code))
                        }
                    }
                }
        }
    }

    /**
     * Inspects [intent] for an invite deep link (e.g. `simsli://invite/<code>`).
     * Returns true if the intent was an invite deep link.
     */
    fun handleInviteIntent(intent: Intent?): Boolean {
        if (intent == null) return false
        val uriString = intent.dataString ?: intent.data?.toString() ?: return false
        return handleInviteUri(uriString)
    }

    /**
     * Parses and processes an invite URI string (e.g. `simsli://invite/<code>`).
     * Returns true if the URI was a valid invite link.
     */
    fun handleInviteUri(uriString: String?): Boolean {
        val code = parseInviteCode(uriString) ?: return false

        Log.d(TAG, "Handling invite deep link with code: $code")
        _pendingInviteCode.value = code

        if (authRepository.currentAuthState is AuthState.SignedIn) {
            _navEvents.tryEmit(InviteNavTarget.JoinHousehold(code))
        } else {
            _navEvents.tryEmit(InviteNavTarget.SignIn(code))
        }
        return true
    }

    /**
     * Extracts and normalizes an invite code from an invite URI string.
     * Supports formats: `simsli://invite/<code>` and `simsli://invite?code=<code>`.
     */
    fun parseInviteCode(uriString: String?): String? {
        if (uriString.isNullOrBlank()) return null
        val prefix = "simsli://invite"
        if (!uriString.startsWith(prefix, ignoreCase = true)) return null

        val remainder = uriString.substring(prefix.length).trim()
        val clean = remainder.trimStart('/', '?')
        if (clean.isBlank()) return null

        if (clean.startsWith("code=", ignoreCase = true)) {
            val code = clean.substringAfter("=").substringBefore('&').trim()
            return code.uppercase().takeIf { it.isNotBlank() }
        }

        val pathCode = clean.substringBefore('?').substringBefore('/').trim()
        return pathCode.uppercase().takeIf { it.isNotBlank() }
    }

    /**
     * Consumes and clears the pending invite code.
     */
    fun consumePendingInviteCode(): String? {
        val code = _pendingInviteCode.value
        _pendingInviteCode.value = null
        return code
    }

    /**
     * Clears the pending code (e.g. if dismissed by user).
     */
    fun clearPendingInviteCode() {
        _pendingInviteCode.value = null
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun consumeNavEvent() {
        _navEvents.resetReplayCache()
    }
}
