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
import net.marvinweber.simsli.R
import net.marvinweber.simsli.domain.model.MemberRole
import net.marvinweber.simsli.ui.UiMessage
import javax.inject.Inject

data class HouseholdUiState(
    val householdName: String? = null,
    val householdIcon: String? = null,
    val householdCurrency: String = "EUR",
    val householdId: String? = null,
    val memberCount: Int = 1,
    val currentUserRole: MemberRole? = null,
    val isOwner: Boolean = true,
    val isSignedIn: Boolean = false,
    val canInvite: Boolean = true,
    val inviteDisabledReason: UiMessage? = null,
    val canJoin: Boolean = true,
    val joinDisabledReason: UiMessage? = null,
    val isBusy: Boolean = false,
    val statusMessage: UiMessage? = null,
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
    private val statusMessage = MutableStateFlow<UiMessage?>(null)
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
        val statusMessage: UiMessage?,
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
            UiMessage(R.string.hh_invite_disabled_reason)
        } else {
            null
        }

        val canJoin = !isOwner || memberCount <= 1
        val joinDisabledReason = if (!canJoin) {
            UiMessage(R.string.hh_join_disabled_reason, listOf(memberCount.toString()))
        } else {
            null
        }

        HouseholdUiState(
            householdName = household?.name,
            householdIcon = household?.icon,
            householdCurrency = household?.currency ?: "EUR",
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

    fun confirmRename(newEmoji: String?, newCurrency: String) {
        if (!uiState.value.isOwner) {
            statusMessage.value = UiMessage(R.string.hh_error_only_owner_edit, isError = true)
            return
        }
        val newName = renameInput.value?.trim().orEmpty()
        if (newName.isEmpty()) return
        val normalizedIcon = newEmoji?.trim()?.ifBlank { null }
        val normalizedCurrency = newCurrency.trim().ifBlank { "EUR" }

        viewModelScope.launch {
            isBusy.value = true
            renameInput.value = null

            val household = householdRepository.getHousehold().first()
            if (household == null) {
                statusMessage.value = UiMessage(R.string.hh_error_not_set_up, isError = true)
            } else if (household.name == newName && household.icon == normalizedIcon &&
                household.currency == normalizedCurrency
            ) {
                // No change
            } else {
                householdRepository.updateHousehold(
                    household.copy(name = newName, icon = normalizedIcon, currency = normalizedCurrency)
                )
                    .onSuccess {
                        if (uiState.value.isSignedIn) {
                            syncManager.syncNow()
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
            isBusy.value = true
            val result = withContext(ioDispatcher) {
                householdRepository.createInviteToken()
            }
            isBusy.value = false
            result.fold(
                onSuccess = { inviteCode.value = it },
                onFailure = { error ->
                    statusMessage.value = UiMessage(R.string.hh_error_invite_create_failed, listOf(error.message.orEmpty()), isError = true)
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
            statusMessage.value = uiState.value.joinDisabledReason ?: UiMessage(R.string.hh_cannot_join, isError = true)
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
                    statusMessage.value = UiMessage(R.string.hh_joined_successfully)
                    syncManager.syncNow("join")
                },
                onFailure = { error ->
                    statusMessage.value = UiMessage(R.string.hh_error_join_failed, listOf(error.message.orEmpty()), isError = true)
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
                statusMessage.value = UiMessage(R.string.common_error_not_signed_in_sync)
                return@launch
            }
            isBusy.value = true
            val result = withContext(ioDispatcher) {
                syncManager.fullResync("manual")
            }
            isBusy.value = false
            result.fold(
                onSuccess = {
                    statusMessage.value = UiMessage(R.string.common_resync_complete)
                },
                onFailure = { error ->
                    statusMessage.value = UiMessage(R.string.common_error_resync_failed, listOf(error.message.orEmpty()), isError = true)
                }
            )
        }
    }

    fun dismissStatusMessage() {
        statusMessage.value = null
    }
}
