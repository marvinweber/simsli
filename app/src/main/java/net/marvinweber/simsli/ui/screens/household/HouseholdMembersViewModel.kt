package net.marvinweber.simsli.ui.screens.household

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
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.domain.model.HouseholdMember
import net.marvinweber.simsli.domain.model.MemberRole
import javax.inject.Inject

data class HouseholdMembersUiState(
    val isLoading: Boolean = false,
    val householdName: String? = null,
    val householdId: String? = null,
    val members: List<HouseholdMember> = emptyList(),
    val isOwner: Boolean = false,
    val currentUserId: String? = null,
    val memberToRemove: HouseholdMember? = null,
    val isRemoving: Boolean = false,
    val statusMessage: String? = null
)

@HiltViewModel
class HouseholdMembersViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val authRepository: AuthRepository,
    private val syncScheduler: SyncScheduler
) : ViewModel() {

    private val isLoading = MutableStateFlow(true)
    private val members = MutableStateFlow<List<HouseholdMember>>(emptyList())
    private val memberToRemove = MutableStateFlow<HouseholdMember?>(null)
    private val isRemoving = MutableStateFlow(false)
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<HouseholdMembersUiState> = combine(
        householdRepository.getHousehold(),
        isLoading,
        members,
        combine(memberToRemove, isRemoving, statusMessage, ::Triple)
    ) { household, loading, memberList, (toRemove, removing, message) ->
        val currentUserId = authRepository.currentUserId()
        val currentMember = memberList.firstOrNull { it.userId == currentUserId }
        val isOwner = currentMember?.role == MemberRole.OWNER

        HouseholdMembersUiState(
            isLoading = loading,
            householdName = household?.name,
            householdId = household?.id,
            members = memberList,
            isOwner = isOwner,
            currentUserId = currentUserId,
            memberToRemove = toRemove,
            isRemoving = removing,
            statusMessage = message
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = HouseholdMembersUiState(isLoading = true)
    )

    init {
        loadMembers()
    }

    fun loadMembers() {
        viewModelScope.launch {
            isLoading.value = true
            val household = householdRepository.getHousehold().first()
            if (household != null) {
                householdRepository.getMembers(household.id)
                    .onSuccess { memberList ->
                        members.value = memberList
                    }
                    .onFailure { error ->
                        statusMessage.value = "Failed to load members: ${error.message}"
                    }
            }
            isLoading.value = false
        }
    }

    fun requestRemoveMember(member: HouseholdMember) {
        memberToRemove.value = member
    }

    fun dismissRemoveMember() {
        memberToRemove.value = null
    }

    fun confirmRemoveMember() {
        val target = memberToRemove.value ?: return
        viewModelScope.launch {
            isRemoving.value = true
            val household = householdRepository.getHousehold().first()
            if (household == null) {
                statusMessage.value = "No household found"
                memberToRemove.value = null
                isRemoving.value = false
                return@launch
            }

            householdRepository.removeMember(household.id, target.userId)
                .onSuccess {
                    statusMessage.value = "${target.email ?: "Member"} removed"
                    memberToRemove.value = null
                    // Reload members list
                    householdRepository.getMembers(household.id)
                        .onSuccess { members.value = it }
                    syncScheduler.requestSync("member_removed")
                }
                .onFailure { error ->
                    statusMessage.value = "Failed to remove member: ${error.message}"
                    memberToRemove.value = null
                }
            isRemoving.value = false
        }
    }

    fun dismissStatusMessage() {
        statusMessage.value = null
    }
}
