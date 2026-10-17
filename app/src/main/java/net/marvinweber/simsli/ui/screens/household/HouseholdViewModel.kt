package net.marvinweber.simsli.ui.screens.household

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.sync.SyncManager
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.HouseholdMember
import net.marvinweber.simsli.domain.model.MemberRole
import javax.inject.Inject

data class HouseholdUiState(
    val householdName: String? = null,
    val householdIcon: String? = null,
    val householdId: String? = null,
    val memberCount: Int = 1,
    val currentUserRole: MemberRole? = null,
    val isOwner: Boolean = true,
    val isSignedIn: Boolean = false,
    val canInvite: Boolean = true,
    val inviteDisabledReason: String? = null,
    val canJoin: Boolean = true,
    val joinDisabledReason: String? = null,
    val isBusy: Boolean = false,
    val statusMessage: String? = null,
    val renameInput: String? = null,
    val inviteCode: String? = null,
    val showJoinWarning: Boolean = false,
    val joinInput: String? = null,
    val pendingJoinCode: String? = null,
    val showFullResyncConfirm: Boolean = false
)

@HiltViewModel
class HouseholdViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val authRepository: AuthRepository,
    private val syncManager: SyncManager,
    private val inviteDeepLinkManager: net.marvinweber.simsli.ui.navigation.InviteDeepLinkManager,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val isBusy = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val renameInput = MutableStateFlow<String?>(null)
    private val inviteCode = MutableStateFlow<String?>(null)
    private val showJoinWarning = MutableStateFlow(false)
    private val joinInput = MutableStateFlow<String?>(null)
    private val pendingJoinCode = MutableStateFlow<String?>(null)
    private val fullResyncConfirmOpen = MutableStateFlow(false)
    private val members = MutableStateFlow<List<HouseholdMember>>(emptyList())

    val isSyncing: StateFlow<Boolean> = syncManager.isSyncing

    /** True while the server cannot be reached — drives the top-bar warning icon. */
    val isOffline: StateFlow<Boolean> = syncManager.isOffline

    /** True while signed-in writes await their sync run — drives the top-bar pending icon. */
    val hasPendingWrites: StateFlow<Boolean> = syncManager.hasPendingWrites

    private data class DialogState(
        val inviteCode: String?,
        val showJoinWarning: Boolean,
        val joinInput: String?,
        val fullResyncConfirmOpen: Boolean,
        val pendingJoinCode: String?
    )

    private data class LocalState(
        val isBusy: Boolean,
        val statusMessage: String?,
        val renameInput: String?,
        val dialogs: DialogState
    )

    val uiState: StateFlow<HouseholdUiState> = combine(
        authRepository.authState,
        householdRepository.getHousehold(),
        members,
        combine(
            isBusy,
            statusMessage,
            renameInput,
            combine(inviteCode, showJoinWarning, joinInput, fullResyncConfirmOpen, pendingJoinCode) { invite, warning, join, resync, pending ->
                DialogState(invite, warning, join, resync, pending)
            },
            ::LocalState
        )
    ) { authState, household, membersList, local ->
        val isSignedIn = authState is AuthState.SignedIn
        val currentUserId = (authState as? AuthState.SignedIn)?.userId
        val currentMember = membersList.firstOrNull { it.userId == currentUserId }
        val role = currentMember?.role ?: if (isSignedIn) null else MemberRole.OWNER
        val isOwner = role == MemberRole.OWNER || !isSignedIn
        val memberCount = if (membersList.isNotEmpty()) membersList.size else 1

        val canInvite = if (isSignedIn) role == MemberRole.OWNER else true
        val inviteDisabledReason = if (isSignedIn && role != MemberRole.OWNER) {
            "Only owners and admins can invite members"
        } else {
            null
        }

        val canJoin = !isOwner || memberCount <= 1
        val joinDisabledReason = if (!canJoin) {
            "You are the owner of a shared household with $memberCount members. Transfer or delete it before joining another."
        } else {
            null
        }

        HouseholdUiState(
            householdName = household?.name,
            householdIcon = household?.icon,
            householdId = household?.id,
            memberCount = memberCount,
            currentUserRole = role,
            isOwner = isOwner,
            isSignedIn = isSignedIn,
            canInvite = canInvite,
            inviteDisabledReason = inviteDisabledReason,
            canJoin = canJoin,
            joinDisabledReason = joinDisabledReason,
            isBusy = local.isBusy,
            statusMessage = local.statusMessage,
            renameInput = local.renameInput,
            inviteCode = local.dialogs.inviteCode,
            showJoinWarning = local.dialogs.showJoinWarning,
            joinInput = local.dialogs.joinInput,
            pendingJoinCode = local.dialogs.pendingJoinCode,
            showFullResyncConfirm = local.dialogs.fullResyncConfirmOpen
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HouseholdUiState())

    init {
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

        viewModelScope.launch {
            inviteDeepLinkManager.pendingInviteCode.collectLatest { code ->
                if (code != null) {
                    val isSignedIn = authRepository.currentUserId() != null
                    if (isSignedIn) {
                        inviteDeepLinkManager.consumePendingInviteCode()
                        startJoinWithCode(code)
                    }
                }
            }
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

    fun confirmRename(newEmoji: String?) {
        if (!uiState.value.isOwner) {
            statusMessage.value = "Only the household owner can edit the household"
            return
        }
        val newName = renameInput.value?.trim().orEmpty()
        if (newName.isEmpty()) return
        val normalizedIcon = newEmoji?.trim()?.ifBlank { null }

        viewModelScope.launch {
            isBusy.value = true
            renameInput.value = null

            val household = householdRepository.getHousehold().first()
            if (household == null) {
                statusMessage.value = "No household set up yet"
            } else if (household.name == newName && household.icon == normalizedIcon) {
                // No change
            } else {
                householdRepository.updateHousehold(household.copy(name = newName, icon = normalizedIcon))
                    .onSuccess {
                        if (uiState.value.isSignedIn) {
                            syncManager.syncNow()
                            statusMessage.value = "Household updated"
                        } else {
                            statusMessage.value = "Household updated — sign in to sync it"
                        }
                    }
                    .onFailure { statusMessage.value = "Update failed: ${it.message}" }
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
            isBusy.value = true
            val result = withContext(ioDispatcher) {
                householdRepository.createInviteToken()
            }
            isBusy.value = false
            result.fold(
                onSuccess = { inviteCode.value = it },
                onFailure = { error ->
                    statusMessage.value = "Failed to generate invite code: ${error.message}"
                }
            )
        }
    }

    fun dismissInvite() {
        inviteCode.value = null
    }

    fun startJoinWithCode(code: String) {
        val currentCanJoin = uiState.value.canJoin
        if (!currentCanJoin) {
            statusMessage.value = uiState.value.joinDisabledReason ?: "Cannot join another household"
            return
        }
        pendingJoinCode.value = code
        showJoinWarning.value = true
    }

    fun startJoin() {
        pendingJoinCode.value = null
        showJoinWarning.value = true
    }

    fun confirmJoinWarning() {
        showJoinWarning.value = false
        val directCode = pendingJoinCode.value
        if (directCode != null) {
            pendingJoinCode.value = null
            executeJoin(directCode)
        } else {
            joinInput.value = ""
        }
    }

    fun dismissJoinWarning() {
        showJoinWarning.value = false
        pendingJoinCode.value = null
    }

    fun onJoinChange(code: String) {
        joinInput.value = code
    }

    fun dismissJoin() {
        joinInput.value = null
    }

    fun confirmJoin() {
        val code = joinInput.value?.trim() ?: return
        if (code.isBlank()) return
        joinInput.value = null
        executeJoin(code)
    }

    private fun executeJoin(code: String) {
        viewModelScope.launch {
            isBusy.value = true
            val result = withContext(ioDispatcher) {
                householdRepository.joinHousehold(code)
            }
            isBusy.value = false
            result.fold(
                onSuccess = {
                    statusMessage.value = "Joined household successfully"
                    syncManager.syncNow("join")
                },
                onFailure = { error ->
                    statusMessage.value = "Failed to join household: ${error.message}"
                }
            )
        }
    }

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
                statusMessage.value = "Not signed in — sync needs an account."
                return@launch
            }
            isBusy.value = true
            val result = withContext(ioDispatcher) {
                syncManager.fullResync("manual")
            }
            isBusy.value = false
            result.fold(
                onSuccess = {
                    statusMessage.value = "Full resync complete — data re-downloaded from server"
                },
                onFailure = { error ->
                    statusMessage.value = "Full resync failed: ${error.message}"
                }
            )
        }
    }

    fun dismissStatusMessage() {
        statusMessage.value = null
    }
}
