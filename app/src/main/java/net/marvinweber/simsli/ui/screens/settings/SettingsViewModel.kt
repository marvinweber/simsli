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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.BuildConfig
import net.marvinweber.simsli.data.debug.DemoDataSeeder
import net.marvinweber.simsli.data.local.AuthTokenStorage
import net.marvinweber.simsli.data.remote.SimsliRemoteDataSource
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.SignOutResult
import net.marvinweber.simsli.data.sync.SyncDiagnostics
import net.marvinweber.simsli.data.sync.SyncManager
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.HouseholdMember
import net.marvinweber.simsli.domain.model.MemberRole
import javax.inject.Inject

enum class EndpointStatus {
    CHECKING,
    ONLINE,
    OFFLINE,
    ERROR
}

data class EndpointHealth(
    val url: String = BuildConfig.SERVER_URL,
    val status: EndpointStatus = EndpointStatus.CHECKING,
    val detail: String? = null,
    val serverVersion: String? = null,
    val apiVersion: Int? = null,
    val serverMode: String? = null,
    val isOutdatedServer: Boolean = false,
    val isOutdatedApp: Boolean = false,
    val warningMessage: String? = null
)

data class SettingsUiState(
    val isSignedIn: Boolean = false,
    val userEmail: String? = null,
    val householdName: String? = null,
    val householdId: String? = null,
    val memberCount: Int = 1,
    val currentUserRole: MemberRole? = null,
    val isOwner: Boolean = true,
    val canInvite: Boolean = true,
    val inviteDisabledReason: String? = null,
    val canJoin: Boolean = true,
    val joinDisabledReason: String? = null,
    val emailInput: String = "",
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val renameInput: String? = null,  // non-null → rename dialog open, holds the field content
    val inviteCode: String? = null,   // non-null → invite dialog open, shows the generated code
    val showJoinWarning: Boolean = false, // non-null / true → show warning before entering join code
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
    private val remoteDataSource: SimsliRemoteDataSource,
    private val tokenStorage: AuthTokenStorage,
    private val syncDiagnostics: SyncDiagnostics,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val emailInput = MutableStateFlow("")
    private val isBusy = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val renameInput = MutableStateFlow<String?>(null)
    private val inviteCode = MutableStateFlow<String?>(null)
    private val showJoinWarning = MutableStateFlow(false)
    private val joinInput = MutableStateFlow<String?>(null)
    private val seedConfirmOpen = MutableStateFlow(false)
    private val signOutConfirmOpen = MutableStateFlow(false)
    private val endpointHealth = MutableStateFlow(EndpointHealth())
    private val members = MutableStateFlow<List<HouseholdMember>>(emptyList())

    /** Dialog visibility + content, bundled to keep every combine on a typed overload. */
    private data class DialogInputs(
        val inviteCode: String?,
        val showJoinWarning: Boolean,
        val joinInput: String?,
        val seedConfirmOpen: Boolean,
        val signOutConfirmOpen: Boolean
    )

    /** Local-only inputs bundled so the outer combine stays within its arity limit. */
    private data class LocalInputs(
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
        members,
        combine(
            emailInput, isBusy, statusMessage, renameInput,
            combine(inviteCode, showJoinWarning, joinInput, seedConfirmOpen, signOutConfirmOpen, ::DialogInputs),
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
            "Only owners and admins (in the future) can invite members"
        } else {
            null
        }

        val canJoin = !isOwner || memberCount <= 1
        val joinDisabledReason = if (isOwner && memberCount > 1) {
            "You cannot join another household as you are a member of a non empty household"
        } else {
            null
        }

        SettingsUiState(
            isSignedIn = authState is AuthState.SignedIn,
            userEmail = (authState as? AuthState.SignedIn)?.email,
            householdName = household?.name,
            householdId = household?.id,
            memberCount = memberCount,
            currentUserRole = role,
            isOwner = isOwner,
            canInvite = canInvite,
            inviteDisabledReason = inviteDisabledReason,
            canJoin = canJoin,
            joinDisabledReason = joinDisabledReason,
            emailInput = inputs.emailInput,
            isBusy = inputs.isBusy,
            statusMessage = inputs.statusMessage,
            renameInput = inputs.renameInput,
            showSeedConfirm = inputs.dialog.seedConfirmOpen,
            showSignOutConfirm = inputs.dialog.signOutConfirmOpen,
            inviteCode = inputs.dialog.inviteCode,
            showJoinWarning = inputs.dialog.showJoinWarning,
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
            val result = withContext(ioDispatcher) {
                probeEndpoint(tokenStorage.serverUrl)
            }
            endpointHealth.value = result
        }
    }

    private suspend fun probeEndpoint(serverUrl: String): EndpointHealth {
        if (serverUrl.isBlank()) {
            return EndpointHealth(
                url = "(not configured)",
                status = EndpointStatus.ERROR,
                detail = "URL is missing"
            )
        }
        val start = System.currentTimeMillis()
        return try {
            val info = remoteDataSource.getServerInfo(serverUrl)
            val latency = System.currentTimeMillis() - start
            val mismatch = hasMajorOrMinorMismatch(info.version, BuildConfig.VERSION_NAME)
            val isOutdatedServer = mismatch < 0
            val isOutdatedApp = mismatch > 0
            val warning = when {
                isOutdatedServer -> "Server update recommended (v${info.version} → v${BuildConfig.VERSION_NAME})"
                isOutdatedApp -> "App update recommended (v${BuildConfig.VERSION_NAME} → v${info.version})"
                else -> null
            }
            EndpointHealth(
                url = serverUrl,
                status = EndpointStatus.ONLINE,
                detail = "Online (${latency}ms)",
                serverVersion = info.version,
                apiVersion = info.apiVersion,
                serverMode = info.serverMode,
                isOutdatedServer = isOutdatedServer,
                isOutdatedApp = isOutdatedApp,
                warningMessage = warning
            )
        } catch (e: Exception) {
            EndpointHealth(
                url = serverUrl,
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
                .onSuccess {
                    statusMessage.value = "Sync complete"
                    refreshHouseholdData()
                }
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
            if (!uiState.value.canInvite) {
                statusMessage.value = uiState.value.inviteDisabledReason ?: "Only owners and admins can invite members."
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
                            refreshHouseholdData()
                        }
                        .onFailure { statusMessage.value = "Joined, but sync failed: ${it.message}" }
                }
                .onFailure { statusMessage.value = "Join failed: ${it.message}" }
            isBusy.value = false
        }
    }

    companion object {
        fun hasMajorOrMinorMismatch(v1: String, v2: String): Int {
            val p1 = v1.split('.').mapNotNull { it.toIntOrNull() }
            val p2 = v2.split('.').mapNotNull { it.toIntOrNull() }
            val major1 = p1.getOrElse(0) { 0 }
            val major2 = p2.getOrElse(0) { 0 }
            if (major1 != major2) return major1.compareTo(major2)
            val minor1 = p1.getOrElse(1) { 0 }
            val minor2 = p2.getOrElse(1) { 0 }
            if (minor1 != minor2) return minor1.compareTo(minor2)
            return 0
        }
    }
}
