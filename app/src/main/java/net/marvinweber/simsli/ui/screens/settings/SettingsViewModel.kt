package net.marvinweber.simsli.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.debug.DemoDataSeeder
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.EndpointHealth
import net.marvinweber.simsli.data.repository.EndpointStatus
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.ServerRepository
import net.marvinweber.simsli.data.repository.SignOutCoordinator
import net.marvinweber.simsli.data.repository.SignOutResult
import net.marvinweber.simsli.data.sync.SyncDiagnostics
import net.marvinweber.simsli.data.sync.SyncManager
import net.marvinweber.simsli.domain.model.HouseholdMember
import net.marvinweber.simsli.R
import net.marvinweber.simsli.domain.model.MemberRole
import net.marvinweber.simsli.ui.UiMessage
import javax.inject.Inject

data class SettingsUiState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val householdName: String? = null,
    val householdIcon: String? = null,
    val householdId: String? = null,
    val memberCount: Int = 1,
    val currentUserRole: MemberRole? = null,
    val isOwner: Boolean = true,
    val canInvite: Boolean = true,
    val inviteDisabledReason: UiMessage? = null,
    val canJoin: Boolean = true,
    val joinDisabledReason: UiMessage? = null,
    val isBusy: Boolean = false,
    val statusMessage: UiMessage? = null,
    val renameInput: String? = null,  // non-null → edit-household dialog open, holds the name field content
    val inviteCode: String? = null,   // non-null → invite dialog open, shows the generated code
    val showJoinWarning: Boolean = false, // non-null / true → show warning before entering join code
    val joinInput: String? = null,    // non-null → join dialog open, holds the field content
    val showSeedConfirm: Boolean = false, // debug only: confirm before wiping + seeding
    val showSignOutConfirm: Boolean = false,
    val showFullResyncConfirm: Boolean = false,
    val endpointHealth: EndpointHealth = EndpointHealth()
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val syncManager: SyncManager,
    private val demoDataSeeder: DemoDataSeeder,
    private val serverRepository: ServerRepository,
    private val signOutCoordinator: SignOutCoordinator,
    private val syncDiagnostics: SyncDiagnostics,
    private val inviteDeepLinkManager: net.marvinweber.simsli.ui.navigation.InviteDeepLinkManager
) : ViewModel() {

    private val isBusy = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<UiMessage?>(null)
    private val renameInput = MutableStateFlow<String?>(null)
    private val inviteCode = MutableStateFlow<String?>(null)
    private val showJoinWarning = MutableStateFlow(false)
    private val joinInput = MutableStateFlow<String?>(null)
    private val seedConfirmOpen = MutableStateFlow(false)
    private val signOutConfirmOpen = MutableStateFlow(false)
    private val fullResyncConfirmOpen = MutableStateFlow(false)
    private val endpointHealth = MutableStateFlow(EndpointHealth())
    private val members = MutableStateFlow<List<HouseholdMember>>(emptyList())

    /** True while a sync run is executing (or queued) — drives the top-bar progress indicator. */
    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing

    /** True while the server cannot be reached — drives the top-bar warning icon. */
    val isOffline: StateFlow<Boolean> = syncManager.isOffline

    /** True while signed-in writes await their sync run — drives the top-bar pending icon. */
    val hasPendingWrites: StateFlow<Boolean> = syncManager.hasPendingWrites

    /** Dialog visibility + content, bundled to keep every combine on a typed overload. */
    private data class DialogInputs(
        val inviteCode: String?,
        val showJoinWarning: Boolean,
        val joinInput: String?,
        val seedConfirmOpen: Boolean,
        val signOutConfirmOpen: Boolean
    )

    private data class FormInputs(
        val isBusy: Boolean,
        val statusMessage: UiMessage?,
        val renameInput: String?
    )

    /** Local-only inputs bundled so the outer combine stays within its arity limit. */
    private data class LocalInputs(
        val form: FormInputs,
        val dialog: Pair<DialogInputs, Boolean>
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        authRepository.authState,
        householdRepository.getHousehold(),
        endpointHealth,
        members,
        combine(
            combine(isBusy, statusMessage, renameInput, ::FormInputs),
            combine(
                combine(inviteCode, showJoinWarning, joinInput, seedConfirmOpen, signOutConfirmOpen, ::DialogInputs),
                fullResyncConfirmOpen
            ) { dialog, fullResync -> dialog to fullResync },
            ::LocalInputs
        )
    ) { authState, household, health, membersList, inputs ->
        val currentUserId = (authState as? AuthState.SignedIn)?.userId
        val currentMember = membersList.firstOrNull { it.userId == currentUserId }
        val role = currentMember?.role ?: if (authState is AuthState.SignedIn) null else MemberRole.OWNER
        val isOwner = role == MemberRole.OWNER || authState !is AuthState.SignedIn
        val memberCount = if (membersList.isNotEmpty()) membersList.size else 1

        val canInvite = if (authState is AuthState.SignedIn) {
            role == MemberRole.OWNER
        } else {
            true
        }
        val inviteDisabledReason = if (authState is AuthState.SignedIn && role != MemberRole.OWNER) {
            UiMessage(R.string.settings_invite_disabled_reason)
        } else {
            null
        }

        val canJoin = !isOwner || memberCount <= 1
        val joinDisabledReason = if (isOwner && memberCount > 1) {
            UiMessage(R.string.settings_join_disabled_reason)
        } else {
            null
        }

        SettingsUiState(
            isSignedIn = authState is AuthState.SignedIn,
            userEmail = (authState as? AuthState.SignedIn)?.email,
            householdName = household?.name,
            householdIcon = household?.icon,
            householdId = household?.id,
            memberCount = memberCount,
            currentUserRole = role,
            isOwner = isOwner,
            canInvite = canInvite,
            inviteDisabledReason = inviteDisabledReason,
            canJoin = canJoin,
            joinDisabledReason = joinDisabledReason,
            isBusy = inputs.form.isBusy,
            statusMessage = inputs.form.statusMessage,
            renameInput = inputs.form.renameInput,
            showSeedConfirm = inputs.dialog.first.seedConfirmOpen,
            showSignOutConfirm = inputs.dialog.first.signOutConfirmOpen,
            showFullResyncConfirm = inputs.dialog.second,
            inviteCode = inputs.dialog.first.inviteCode,
            showJoinWarning = inputs.dialog.first.showJoinWarning,
            joinInput = inputs.dialog.first.joinInput,
            endpointHealth = health
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingsUiState()
    )

    init {
        checkEndpointHealth()
        viewModelScope.launch {
            combine(
                authRepository.authState,
                householdRepository.getHousehold()
            ) { auth, household -> auth to household }
                .collectLatest { (auth, household) ->
                    if (auth is AuthState.SignedIn && household != null) {
                        householdRepository.getMembers(household.id)
                            .onSuccess { members.value = it }
                            .onFailure { members.value = emptyList() }
                    } else {
                        members.value = emptyList()
                    }
                }
        }
    }

    fun refreshHouseholdData() {
        viewModelScope.launch {
            val household = householdRepository.getHousehold().first()
            if (household != null && uiState.value.isSignedIn) {
                householdRepository.getMembers(household.id)
                    .onSuccess { members.value = it }
            }
        }
    }

    fun checkEndpointHealth() {
        viewModelScope.launch {
            endpointHealth.value = endpointHealth.value.copy(
                status = EndpointStatus.CHECKING,
                detail = null
            )
            endpointHealth.value = serverRepository.probe()
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
            signOutCoordinator.signOutAndWipe()
                .onSuccess { result ->
                    statusMessage.value = when (result) {
                        is SignOutResult.Complete -> UiMessage(R.string.settings_signed_out_wiped)
                        is SignOutResult.LocalOnly ->
                            UiMessage(R.string.settings_signed_out_offline)
                    }
                }
                .onFailure { statusMessage.value = UiMessage(R.string.settings_error_sign_out_failed, listOf(it.message.orEmpty()), isError = true) }
            isBusy.value = false
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            if (!uiState.value.isSignedIn) {
                statusMessage.value = UiMessage(R.string.common_error_not_signed_in_sync)
                return@launch
            }
            isBusy.value = true
            syncManager.syncNow("manual")
                .onSuccess {
                    statusMessage.value = UiMessage(R.string.settings_sync_complete)
                    refreshHouseholdData()
                }
                .onFailure { statusMessage.value = UiMessage(R.string.common_error_sync_failed, listOf(it.message.orEmpty()), isError = true) }
            isBusy.value = false
        }
    }

    /** Recovery for suspected sync drift: re-downloads the full household state from the server. */
    fun startFullResync() {
        fullResyncConfirmOpen.value = true
    }

    fun dismissFullResync() {
        fullResyncConfirmOpen.value = false
    }

    fun confirmFullResync() {
        fullResyncConfirmOpen.value = false
        viewModelScope.launch {
            if (!uiState.value.isSignedIn) {
                statusMessage.value = UiMessage(R.string.common_error_not_signed_in_sync)
                return@launch
            }
            isBusy.value = true
            syncManager.fullResync("manual")
                .onSuccess {
                    statusMessage.value = UiMessage(R.string.common_resync_complete)
                    refreshHouseholdData()
                }
                .onFailure { statusMessage.value = UiMessage(R.string.common_error_resync_failed, listOf(it.message.orEmpty()), isError = true) }
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

    fun confirmRename(icon: String?) {
        if (!uiState.value.isOwner) {
            statusMessage.value = UiMessage(R.string.hh_error_only_owner_edit, isError = true)
            return
        }
        val newName = renameInput.value?.trim().orEmpty()
        if (newName.isEmpty()) return
        val normalizedIcon = icon?.trim()?.ifBlank { null }
        viewModelScope.launch {
            isBusy.value = true
            renameInput.value = null

            val household = householdRepository.getHousehold().first()
            if (household == null) {
                statusMessage.value = UiMessage(R.string.hh_error_not_set_up, isError = true)
            } else if (household.name == newName && household.icon == normalizedIcon) {
                // No change — avoid a pointless write+sync round-trip
            } else {
                householdRepository.updateHousehold(household.copy(name = newName, icon = normalizedIcon))
                    .onSuccess {
                        if (uiState.value.isSignedIn) {
                            syncManager.syncNow()   // push the changes right away
                            statusMessage.value = UiMessage(R.string.hh_updated)
                        } else {
                            statusMessage.value = UiMessage(R.string.hh_updated_sign_in_to_sync)
                        }
                    }
                    .onFailure { statusMessage.value = UiMessage(R.string.common_error_update_failed, listOf(it.message.orEmpty()), isError = true) }
            }
            isBusy.value = false
        }
    }

    fun startInvite() {
        viewModelScope.launch {
            if (!uiState.value.isSignedIn) {
                statusMessage.value = UiMessage(R.string.hh_error_sign_in_first_invite)
                return@launch
            }
            if (!uiState.value.canInvite) {
                statusMessage.value = uiState.value.inviteDisabledReason ?: UiMessage(R.string.hh_invite_disabled_reason)
                return@launch
            }
            if (householdRepository.getHousehold().first() == null) {
                statusMessage.value = UiMessage(R.string.hh_error_not_set_up, isError = true)
                return@launch
            }
            isBusy.value = true
            householdRepository.createInviteToken()
                .onSuccess { inviteCode.value = it }   // opens the dialog
                .onFailure { statusMessage.value = UiMessage(R.string.settings_error_invite_create_failed, listOf(it.message.orEmpty()), isError = true) }
            isBusy.value = false
        }
    }

    fun dismissInvite() {
        inviteCode.value = null
    }

    fun startJoin() {
        if (!uiState.value.canJoin) {
            statusMessage.value = uiState.value.joinDisabledReason
            return
        }
        showJoinWarning.value = true
    }

    fun confirmJoinWarning() {
        showJoinWarning.value = false
        joinInput.value = ""
    }

    fun dismissJoinWarning() {
        showJoinWarning.value = false
    }

    // --- Debug: demo data (debug builds only; the row is gated in the screen) -------------------

    /** Ring-buffer dump of recent sync/realtime lines, for diagnosing sync anomalies. */
    fun syncDiagnosticsSnapshot(): String = syncDiagnostics.snapshot()

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
                .onSuccess { statusMessage.value = UiMessage(R.string.settings_demo_seeded) }
                .onFailure { statusMessage.value = UiMessage(R.string.settings_error_seed_failed, listOf(it.message.orEmpty()), isError = true) }
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
                statusMessage.value = UiMessage(R.string.settings_error_not_signed_in_join)
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
                            statusMessage.value = UiMessage(R.string.settings_joined_household, listOf(name ?: ""))
                            refreshHouseholdData()
                        }
                        .onFailure { statusMessage.value = UiMessage(R.string.settings_joined_sync_failed, listOf(it.message.orEmpty()), isError = true) }
                }
                .onFailure { statusMessage.value = UiMessage(R.string.settings_error_join_failed, listOf(it.message.orEmpty()), isError = true) }
            isBusy.value = false
        }
    }
}
