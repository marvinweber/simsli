package net.marvinweber.simsli.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.BuildConfig
import net.marvinweber.simsli.data.debug.DemoDataSeeder
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.SignOutResult
import net.marvinweber.simsli.data.sync.SyncManager
import net.marvinweber.simsli.di.IoDispatcher
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject

enum class EndpointStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
    ERROR
}

data class EndpointHealth(
    val url: String = BuildConfig.SUPABASE_URL,
    val status: EndpointStatus = EndpointStatus.CHECKING,
    val detail: String? = null
)

data class SettingsUiState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val householdName: String? = null,
    val emailInput: String = "",
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val renameInput: String? = null,  // non-null → rename dialog open, holds the field content
    val inviteCode: String? = null,   // non-null → invite dialog open, shows the generated code
    val joinInput: String? = null,    // non-null → join dialog open, holds the field content
    val showSeedConfirm: Boolean = false, // debug only: confirm before wiping + seeding
    val showSignOutConfirm: Boolean = false,
    val endpointHealth: EndpointHealth = EndpointHealth()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val syncManager: SyncManager,
    private val demoDataSeeder: DemoDataSeeder,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val emailInput = MutableStateFlow("")
    private val isBusy = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val renameInput = MutableStateFlow<String?>(null)
    private val inviteCode = MutableStateFlow<String?>(null)
    private val joinInput = MutableStateFlow<String?>(null)
    private val seedConfirmOpen = MutableStateFlow(false)
    private val signOutConfirmOpen = MutableStateFlow(false)
    private val endpointHealth = MutableStateFlow(EndpointHealth())

    /** Dialog visibility + content, bundled to keep every combine on a typed overload. */
    private data class DialogInputs(
        val inviteCode: String?,
        val joinInput: String?,
        val seedConfirmOpen: Boolean,
        val signOutConfirmOpen: Boolean
    )

    /** Local-only inputs bundled so the outer combine stays within its arity limit. */
    private data class Inputs(
        val emailInput: String,
        val isBusy: Boolean,
        val statusMessage: String?,
        val renameInput: String?,
        val dialog: DialogInputs
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.authState,
        householdRepository.getHousehold(),
        endpointHealth,
        combine(
            emailInput, isBusy, statusMessage, renameInput,
            combine(inviteCode, joinInput, seedConfirmOpen, signOutConfirmOpen, ::DialogInputs),
            ::Inputs
        )
    ) { authState, household, health, inputs ->
        SettingsUiState(
            isSignedIn = authState is AuthState.SignedIn,
            userEmail = (authState as? AuthState.SignedIn)?.email,
            householdName = household?.name,
            emailInput = inputs.emailInput,
            isBusy = inputs.isBusy,
            statusMessage = inputs.statusMessage,
            renameInput = inputs.renameInput,
            showSeedConfirm = inputs.dialog.seedConfirmOpen,
            showSignOutConfirm = inputs.dialog.signOutConfirmOpen,
            inviteCode = inputs.dialog.inviteCode,
            joinInput = inputs.dialog.joinInput,
            endpointHealth = health
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        checkEndpointHealth()
    }

    fun checkEndpointHealth() {
        viewModelScope.launch {
            endpointHealth.value = endpointHealth.value.copy(
                status = EndpointStatus.CHECKING,
                detail = null
            )
            val result = withContext(ioDispatcher) {
                probeEndpoint(BuildConfig.SUPABASE_URL, BuildConfig.SUPABASE_ANON_KEY)
            }
            endpointHealth.value = result
        }
    }

    private fun probeEndpoint(supabaseUrl: String, anonKey: String): EndpointHealth {
        if (supabaseUrl.isBlank()) {
            return EndpointHealth(
                url = "(not configured)",
                status = EndpointStatus.ERROR,
                detail = "URL is missing"
            )
        }
        return try {
            val start = System.currentTimeMillis()
            val healthUrl = if (supabaseUrl.endsWith("/")) "${supabaseUrl}auth/v1/health" else "$supabaseUrl/auth/v1/health"
            val conn = (URL(healthUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 4000
                readTimeout = 4000
                requestMethod = "GET"
                instanceFollowRedirects = true
                if (anonKey.isNotBlank()) {
                    setRequestProperty("apikey", anonKey)
                }
            }
            val code = conn.responseCode
            val latency = System.currentTimeMillis() - start
            if (code in 200..299) {
                EndpointHealth(
                    url = supabaseUrl,
                    status = EndpointStatus.ONLINE,
                    detail = "Online (${latency}ms)"
                )
            } else if (code == 503) {
                EndpointHealth(
                    url = supabaseUrl,
                    status = EndpointStatus.ERROR,
                    detail = "Paused (HTTP 503)"
                )
            } else {
                EndpointHealth(
                    url = supabaseUrl,
                    status = EndpointStatus.ERROR,
                    detail = "HTTP $code"
                )
            }
        } catch (e: Exception) {
            EndpointHealth(
                url = supabaseUrl,
                status = EndpointStatus.OFFLINE,
                detail = "Unreachable"
            )
        }
    }

    fun onEmailChange(email: String) {
        emailInput.value = email
    }

    fun sendMagicLink() {
        viewModelScope.launch {
            val email = emailInput.value.trim()
            if (!email.contains('@')) {
                statusMessage.value = "Please enter a valid email address"
                return@launch
            }
            isBusy.value = true
            authRepository.sendMagicLink(email)
                .onSuccess {
                    statusMessage.value = "Magic link sent — check your inbox."
                    emailInput.value = ""
                }
                .onFailure { statusMessage.value = "Failed to send magic link: ${it.message}" }
            isBusy.value = false
        }
    }

    fun startSignOut() {
        signOutConfirmOpen.value = true
    }

    fun dismissSignOut() {
        signOutConfirmOpen.value = false
    }

    fun confirmSignOut() {
        signOutConfirmOpen.value = false
        signOut()
    }

    fun dismissStatusMessage() {
        statusMessage.value = null
    }

    fun signOut() {
        viewModelScope.launch {
            isBusy.value = true
            authRepository.signOut()
                .onSuccess { result ->
                    // The session is gone now, so pending syncs bail out on the missing
                    // user id; the wipe itself waits on the sync mutex (DATA-1 — any
                    // unsynced writes are lost, accepted by spec).
                    syncManager.wipeLocalData()
                    statusMessage.value = when (result) {
                        is SignOutResult.Complete -> "Signed out — local data wiped"
                        is SignOutResult.LocalOnly ->
                            "Signed out — the server couldn't be reached to revoke the session"
                    }
                }
                .onFailure { statusMessage.value = "Sign out failed: ${it.message}" }
            isBusy.value = false
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            if (!uiState.value.isSignedIn) {
                statusMessage.value = "Not signed in — sync needs an account."
                return@launch
            }
            isBusy.value = true
            syncManager.syncNow("manual")
                .onSuccess { statusMessage.value = "Sync complete" }
                .onFailure { statusMessage.value = "Sync failed: ${it.message}" }
            isBusy.value = false
        }
    }

    fun startRename(currentName: String?) {
        renameInput.value = currentName.orEmpty()
    }

    fun onRenameChange(name: String) {
        renameInput.value = name
    }

    fun dismissRename() {
        renameInput.value = null
    }

    fun confirmRename() {
        val newName = renameInput.value?.trim().orEmpty()
        if (newName.isEmpty()) return
        viewModelScope.launch {
            isBusy.value = true
            renameInput.value = null

            val household = householdRepository.getHousehold().first()
            if (household == null) {
                statusMessage.value = "No household set up yet"
            } else {
                householdRepository.updateHousehold(household.copy(name = newName))
                    .onSuccess {
                        if (uiState.value.isSignedIn) {
                            syncManager.syncNow()   // push the rename right away
                            statusMessage.value = "Household renamed"
                        } else {
                            statusMessage.value = "Household renamed — sign in to sync it"
                        }
                    }
                    .onFailure { statusMessage.value = "Rename failed: ${it.message}" }
            }
            isBusy.value = false
        }
    }

    fun startInvite() {
        viewModelScope.launch {
            if (!uiState.value.isSignedIn) {
                statusMessage.value = "Sign in first to invite someone."
                return@launch
            }
            if (householdRepository.getHousehold().first() == null) {
                statusMessage.value = "No household set up yet"
                return@launch
            }
            isBusy.value = true
            householdRepository.createInviteToken()
                .onSuccess { inviteCode.value = it }   // opens the dialog
                .onFailure { statusMessage.value = "Couldn't create invite: ${it.message}" }
            isBusy.value = false
        }
    }

    fun dismissInvite() {
        inviteCode.value = null
    }

    fun startJoin() {
        joinInput.value = ""
    }

    // --- Debug: demo data (debug builds only; the row is gated in the screen) -------------------

    fun startSeedDemo() {
        seedConfirmOpen.value = true
    }

    fun dismissSeedDemo() {
        seedConfirmOpen.value = false
    }

    fun confirmSeedDemo() {
        viewModelScope.launch {
            isBusy.value = true
            seedConfirmOpen.value = false   // close dialog before async work
            demoDataSeeder.seed()
                .onSuccess { statusMessage.value = "Demo data seeded — local data was reset" }
                .onFailure { statusMessage.value = "Seeding failed: ${it.message}" }
            isBusy.value = false
        }
    }

    fun onJoinChange(code: String) {
        joinInput.value = code
    }

    fun dismissJoin() {
        joinInput.value = null
    }

    fun confirmJoin() {
        val token = joinInput.value?.trim()?.uppercase().orEmpty()
        if (token.isEmpty()) return
        viewModelScope.launch {
            if (!uiState.value.isSignedIn) {
                statusMessage.value = "Not signed in — joining needs an account."
                return@launch
            }
            isBusy.value = true
            joinInput.value = null   // close dialog before async work

            householdRepository.joinHousehold(token)
                .onSuccess {
                    // The membership exists now; sync adopts the joined household
                    // and pulls its data (see SyncManager.resolveHousehold).
                    syncManager.syncNow("invite")
                        .onSuccess {
                            val name = householdRepository.getHousehold().first()?.name
                            statusMessage.value = "Joined household \"$name\""
                        }
                        .onFailure { statusMessage.value = "Joined, but sync failed: ${it.message}" }
                }
                .onFailure { statusMessage.value = "Join failed: ${it.message}" }
            isBusy.value = false
        }
    }
}
