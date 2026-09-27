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
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.debug.DemoDataSeeder
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.SignOutResult
import net.marvinweber.simsli.data.sync.SyncManager
import javax.inject.Inject

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
    val showSeedConfirm: Boolean = false  // debug only: confirm before wiping + seeding
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val syncManager: SyncManager,
    private val demoDataSeeder: DemoDataSeeder
) : ViewModel() {

    private val emailInput = MutableStateFlow("")
    private val isBusy = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val renameInput = MutableStateFlow<String?>(null)
    private val inviteCode = MutableStateFlow<String?>(null)
    private val joinInput = MutableStateFlow<String?>(null)
    private val seedConfirmOpen = MutableStateFlow(false)

    /** Dialog visibility + content, bundled to keep every combine on a typed overload. */
    private data class DialogInputs(
        val inviteCode: String?,
        val joinInput: String?,
        val seedConfirmOpen: Boolean
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
        combine(
            emailInput, isBusy, statusMessage, renameInput,
            combine(inviteCode, joinInput, seedConfirmOpen, ::DialogInputs), ::Inputs
        )
    ) { authState, household, inputs ->
        SettingsUiState(
            isSignedIn = authState is AuthState.SignedIn,
            userEmail = (authState as? AuthState.SignedIn)?.email,
            householdName = household?.name,
            emailInput = inputs.emailInput,
            isBusy = inputs.isBusy,
            statusMessage = inputs.statusMessage,
            renameInput = inputs.renameInput,
            showSeedConfirm = inputs.dialog.seedConfirmOpen,
            inviteCode = inputs.dialog.inviteCode,
            joinInput = inputs.dialog.joinInput
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

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
