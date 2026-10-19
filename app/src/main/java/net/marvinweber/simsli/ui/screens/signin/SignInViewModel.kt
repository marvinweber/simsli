package net.marvinweber.simsli.ui.screens.signin

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.marvinweber.simsli.R
import net.marvinweber.simsli.data.remote.SimsliApiException
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.EndpointHealth
import net.marvinweber.simsli.data.repository.EndpointStatus
import net.marvinweber.simsli.data.repository.HostingMode
import net.marvinweber.simsli.data.repository.ServerRepository
import net.marvinweber.simsli.ui.UiMessage
import net.marvinweber.simsli.ui.navigation.InviteDeepLinkManager
import javax.inject.Inject

data class SignInUiState(
    val hostingMode: HostingMode = HostingMode.CLOUD,
    val selfHostedUrlInput: String = "",
    val emailInput: String = "",
    val isBusy: Boolean = false,
    val probe: EndpointHealth? = null,
    val statusMessage: UiMessage? = null,
    val pendingInviteCode: String? = null,
    /** Arrived via a `?host=` invite link — the server was preselected for the user. */
    val fromHostLink: Boolean = false
)

@HiltViewModel
class SignInViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val serverRepository: ServerRepository,
    private val inviteDeepLinkManager: InviteDeepLinkManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    /** Host from an invite deep link that routed here (`sign_in?host={host}`). */
    private val hostFromNav: String? = savedStateHandle.get<String>("host")?.takeIf { it.isNotBlank() }

    private val hostingMode = MutableStateFlow(
        hostFromNav?.let { HostingMode.SELF_HOSTED } ?: serverRepository.hostingMode
    )
    private val selfHostedUrlInput = MutableStateFlow(
        hostFromNav ?: serverRepository.serverUrl.takeIf { serverRepository.hostingMode == HostingMode.SELF_HOSTED }.orEmpty()
    )
    private val emailInput = MutableStateFlow("")
    private val isBusy = MutableStateFlow(false)
    private val probe = MutableStateFlow<EndpointHealth?>(null)
    private val statusMessage = MutableStateFlow<UiMessage?>(null)

    /** Exposed for the screen: leave the sign-in screen once sign-in succeeds. */
    val authState: StateFlow<AuthState> = authRepository.authState.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = authRepository.currentAuthState
    )

    val uiState: StateFlow<SignInUiState> = combine(
        hostingMode,
        selfHostedUrlInput,
        emailInput,
        isBusy,
        combine(probe, statusMessage, inviteDeepLinkManager.pendingInviteCode) { p, s, invite ->
            Triple(p, s, invite)
        }
    ) { mode, url, email, busy, (p, s, invite) ->
        SignInUiState(
            hostingMode = mode,
            selfHostedUrlInput = url,
            emailInput = email,
            isBusy = busy,
            probe = p,
            statusMessage = s,
            pendingInviteCode = invite,
            fromHostLink = hostFromNav != null
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SignInUiState(
            hostingMode = hostingMode.value,
            selfHostedUrlInput = selfHostedUrlInput.value,
            fromHostLink = hostFromNav != null
        )
    )

    init {
        if (hostFromNav != null) {
            probeUrl(hostFromNav)
        }
    }

    fun onHostingModeChange(mode: HostingMode) {
        if (hostingMode.value == mode) return
        hostingMode.value = mode
        probe.value = null
        if (mode == HostingMode.SELF_HOSTED && selfHostedUrlInput.value.isBlank()) {
            selfHostedUrlInput.value = serverRepository.serverUrl
                .takeIf { serverRepository.hostingMode == HostingMode.SELF_HOSTED }
                .orEmpty()
        }
    }

    fun onSelfHostedUrlChange(url: String) {
        selfHostedUrlInput.value = url
    }

    fun onEmailChange(email: String) {
        emailInput.value = email
    }

    /** On-demand health probe of the entered self-hosted URL (button / IME action). */
    fun probeUrl(url: String = selfHostedUrlInput.value) {
        viewModelScope.launch {
            probe.value = EndpointHealth(url = url, status = EndpointStatus.CHECKING)
            probe.value = serverRepository.probe(url)
        }
    }

    fun dismissStatusMessage() {
        statusMessage.value = null
    }

    fun sendMagicLink() {
        viewModelScope.launch {
            val email = emailInput.value.trim()
            if (!email.contains('@')) {
                statusMessage.value = UiMessage(R.string.settings_error_invalid_email, isError = true)
                return@launch
            }

            val mode = hostingMode.value
            var targetUrl: String? = null
            if (mode == HostingMode.SELF_HOSTED) {
                // Probe before persisting: an unreachable URL must not become the endpoint.
                val health = serverRepository.probe(selfHostedUrlInput.value)
                probe.value = health
                if (health.status != EndpointStatus.ONLINE) {
                    statusMessage.value = UiMessage(R.string.sign_in_error_server_unreachable, isError = true)
                    return@launch
                }
                targetUrl = health.url
            }

            isBusy.value = true
            serverRepository.setHosting(mode, targetUrl)
            authRepository.sendMagicLink(email)
                .onSuccess {
                    statusMessage.value = UiMessage(R.string.settings_magic_link_sent)
                    emailInput.value = ""
                }
                .onFailure { error ->
                    statusMessage.value = if (error is SimsliApiException && error.statusCode == 403) {
                        // Cloud's invite-only gate (closed beta): the server rejects
                        // uninvited emails with 403 — surface it as the friendly upsell.
                        UiMessage(R.string.sign_in_error_invite_only, isError = true)
                    } else {
                        UiMessage(R.string.settings_error_magic_link_failed, listOf(error.message.orEmpty()), isError = true)
                    }
                }
            isBusy.value = false
        }
    }
}
