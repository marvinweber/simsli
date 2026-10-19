package net.marvinweber.simsli.ui.navigation

import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.HostingMode
import net.marvinweber.simsli.data.repository.ServerRepository
import net.marvinweber.simsli.data.repository.SignOutCoordinator
import net.marvinweber.simsli.di.ApplicationScope
import java.net.URLDecoder
import javax.inject.Inject
import javax.inject.Singleton

sealed interface InviteNavTarget {
    data class JoinHousehold(val code: String) : InviteNavTarget
    data class SignIn(val code: String, val host: String? = null) : InviteNavTarget
}

/**
 * A pending cross-server switch (HH-3): the opened invite belongs to a server
 * other than the one the device is signed in to. Confirming signs out (AUTH-2
 * wipe), applies the hosting choice, and resumes into sign-in.
 */
data class ServerSwitchRequest(
    val code: String,
    val mode: HostingMode,
    val host: String?
)

/**
 * Coordinates household invite deep links (`simsli://invite/<code>`, with an
 * optional `?host=<url>` for self-hosted servers — cloud invites carry no host).
 *
 * Behavior per spec (HH-3):
 * - If not signed in: navigates to the sign-in screen (host preselects Self-Hosted)
 *   while preserving the code; upon successful sign-in, automatically transitions
 *   to the join flow.
 * - If signed in and the invite targets the current server: immediately navigates
 *   to the Household screen to begin the join flow.
 * - If signed in and the invite targets a different server: raises
 *   [pendingServerSwitch]; confirming signs out + wipes, switches the hosting
 *   choice, and resumes into sign-in. No multi-server login (AUTH-5).
 */
@Singleton
class InviteDeepLinkManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverRepository: ServerRepository,
    private val signOutCoordinator: SignOutCoordinator,
    @ApplicationScope private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "SimsliInvite"
    }

    private val _pendingInviteCode = MutableStateFlow<String?>(null)
    val pendingInviteCode: StateFlow<String?> = _pendingInviteCode.asStateFlow()

    private val _pendingServerSwitch = MutableStateFlow<ServerSwitchRequest?>(null)
    val pendingServerSwitch: StateFlow<ServerSwitchRequest?> = _pendingServerSwitch.asStateFlow()

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
        val parsed = parseInviteUri(uriString) ?: return false
        val (code, host) = parsed

        Log.d(TAG, "Handling invite deep link with code: $code, host: $host")
        _pendingInviteCode.value = code

        if (authRepository.currentAuthState !is AuthState.SignedIn) {
            _navEvents.tryEmit(InviteNavTarget.SignIn(code, host))
            return true
        }

        if (isCurrentServer(host)) {
            _navEvents.tryEmit(InviteNavTarget.JoinHousehold(code))
        } else {
            val mode = if (host == null) HostingMode.CLOUD else HostingMode.SELF_HOSTED
            Log.d(TAG, "Invite targets a different server ($host) — requesting server switch")
            // Take the code out of pending so no join flow starts before the
            // switch dialog is resolved; confirmServerSwitch restores it.
            _pendingInviteCode.value = null
            _pendingServerSwitch.value = ServerSwitchRequest(code = code, mode = mode, host = host)
        }
        return true
    }

    /** True when the invite (without host = cloud) targets the server the app currently uses. */
    private fun isCurrentServer(host: String?): Boolean {
        return if (host == null) {
            serverRepository.hostingMode == HostingMode.CLOUD
        } else {
            serverRepository.hostingMode == HostingMode.SELF_HOSTED &&
                serverRepository.serverUrl.trimEnd('/') == host.trim().trimEnd('/')
        }
    }

    /**
     * Confirms the pending cross-server switch: signs out and wipes (AUTH-2),
     * applies the invite's hosting choice, then resumes into sign-in with the code.
     */
    fun confirmServerSwitch() {
        val request = _pendingServerSwitch.value ?: return
        _pendingServerSwitch.value = null
        scope.launch {
            signOutCoordinator.signOutAndWipe()
            serverRepository.setHosting(request.mode, request.host)
            _pendingInviteCode.value = request.code
            _navEvents.tryEmit(InviteNavTarget.SignIn(request.code, request.host))
        }
    }

    fun dismissServerSwitch() {
        _pendingServerSwitch.value = null
    }

    /**
     * Extracts the invite code and optional host from an invite URI string.
     * Supports formats: `simsli://invite/<code>`, `simsli://invite?code=<code>`,
     * both with an optional `?host=<url>` parameter (cloud invites have none).
     */
    fun parseInviteUri(uriString: String?): Pair<String, String?>? {
        val code = parseInviteCode(uriString) ?: return null
        if (uriString == null) return code to null
        val query = uriString.substringAfter('?', "")
        if (query.isEmpty()) return code to null
        val host = query.split('&')
            .firstOrNull { it.startsWith("host=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
            ?.let { encoded ->
                runCatching { URLDecoder.decode(encoded, "UTF-8") }.getOrNull()
            }
        return code to host?.trim()?.takeIf { it.isNotBlank() }
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
