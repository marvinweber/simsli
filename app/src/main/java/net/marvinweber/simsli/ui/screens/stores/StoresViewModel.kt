package net.marvinweber.simsli.ui.screens.stores

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.StoreGroupRepository
import net.marvinweber.simsli.data.repository.StoreRepository
import net.marvinweber.simsli.domain.model.Household
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.model.StoreGroup
import net.marvinweber.simsli.domain.util.SearchFold
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class StoresUiState(
    val household: Household? = null,
    val storeGroups: List<StoreGroup> = emptyList(),
    val stores: List<Store> = emptyList(),
    /** The active top-bar search query for the Stores sub-tab (SEARCH-1). */
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val error: String? = null
)

sealed class StoresUiEvent {
    data class ShowError(val message: String) : StoresUiEvent()
    data class ConfirmDeleteStore(val storeId: String, val storeName: String) : StoresUiEvent()
    data class ConfirmDeleteGroup(val groupId: String, val groupName: String) : StoresUiEvent()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class StoresViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val storeRepository: StoreRepository,
    private val storeGroupRepository: StoreGroupRepository
) : ViewModel() {

    val uiState: StateFlow<StoresUiState> = householdRepository.getHousehold()
        .flatMapLatest { household ->
            if (household == null) {
                flowOf(StoresUiState(isLoading = false))
            } else {
                combineStoresAndGroups(household)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = StoresUiState(isLoading = true)
        )

    /** The top-bar search query for the Stores sub-tab (SEARCH-1). */
    private val searchQuery = MutableStateFlow("")

    fun setSearchQuery(query: String) {
        searchQuery.value = query
    }

    private fun combineStoresAndGroups(household: Household) = combine(
        storeRepository.getStoresByHousehold(household.id),
        storeGroupRepository.getGroupsByHousehold(household.id),
        searchQuery
    ) { stores, storeGroups, query ->
        val q = query.trim()
        // While searching, filter stores by name; a group header stays visible when
        // its own name matches or any of its stores match (so matches never orphan).
        val fq = SearchFold.fold(q)
        val filteredStores = if (q.isEmpty()) stores else stores.filter {
            SearchFold.fold(it.name).contains(fq)
        }
        val visibleGroups = if (q.isEmpty()) storeGroups else storeGroups.filter { group ->
            SearchFold.fold(group.name).contains(fq) ||
                filteredStores.any { it.groupId == group.id }
        }
        StoresUiState(
            household = household,
            storeGroups = visibleGroups,
            stores = filteredStores,
            searchQuery = q,
            isLoading = false,
            error = null
        )
    }

    private val _events = MutableStateFlow<StoresUiEvent?>(null)
    val events: StateFlow<StoresUiEvent?> = _events.asStateFlow()

    fun createStore(name: String, groupId: String? = null) {
        viewModelScope.launch {
            val household = uiState.value.household
            if (household == null) {
                _events.value = StoresUiEvent.ShowError("No household found")
                return@launch
            }

            if (name.isBlank()) {
                _events.value = StoresUiEvent.ShowError("Store name cannot be empty")
                return@launch
            }

            val now = Instant.now()
            val store = Store(
                id = UUID.randomUUID().toString(),
                householdId = household.id,
                name = name.trim(),
                sortOrder = storeRepository.getMaxStoreSortOrder(household.id) + 1f,
                groupId = groupId,
                createdAt = now,
                updatedAt = now
            )

            storeRepository.createStore(store)
                .onFailure { error ->
                    _events.value = StoresUiEvent.ShowError("Failed to create store: ${error.message}")
                }
        }
    }

    fun updateStore(storeId: String, name: String, groupId: String?) {
        viewModelScope.launch {
            val store = uiState.value.stores.find { it.id == storeId }
            if (store == null) {
                _events.value = StoresUiEvent.ShowError("Store not found")
                return@launch
            }
            if (name.isBlank()) {
                _events.value = StoresUiEvent.ShowError("Store name cannot be empty")
                return@launch
            }
            storeRepository.updateStore(store.copy(name = name.trim(), groupId = groupId))
                .onFailure { error ->
                    _events.value = StoresUiEvent.ShowError("Failed to update store: ${error.message}")
                }
        }
    }

    fun onDeleteStore(storeId: String) {
        viewModelScope.launch {
            val store = uiState.value.stores.find { it.id == storeId }
            if (store == null) {
                _events.value = StoresUiEvent.ShowError("Store not found")
                return@launch
            }

            _events.value = StoresUiEvent.ConfirmDeleteStore(storeId, store.name)
        }
    }

    fun confirmDeleteStore(storeId: String) {
        viewModelScope.launch {
            storeRepository.deleteStore(storeId)
                .onFailure { error ->
                    _events.value = StoresUiEvent.ShowError("Failed to delete store: ${error.message}")
                }
        }
    }

    fun createGroup(name: String) {
        viewModelScope.launch {
            val household = uiState.value.household
            if (household == null) {
                _events.value = StoresUiEvent.ShowError("No household found")
                return@launch
            }
            if (name.isBlank()) {
                _events.value = StoresUiEvent.ShowError("Group name cannot be empty")
                return@launch
            }
            val now = Instant.now()
            val group = StoreGroup(
                id = UUID.randomUUID().toString(),
                householdId = household.id,
                name = name.trim(),
                sortOrder = storeGroupRepository.getMaxSortOrder(household.id) + 1f,
                createdAt = now,
                updatedAt = now
            )
            storeGroupRepository.upsertGroup(group)
                .onFailure { error ->
                    _events.value = StoresUiEvent.ShowError("Failed to create group: ${error.message}")
                }
        }
    }

    fun updateGroup(groupId: String, name: String) {
        viewModelScope.launch {
            val group = uiState.value.storeGroups.find { it.id == groupId }
            if (group == null) {
                _events.value = StoresUiEvent.ShowError("Group not found")
                return@launch
            }
            if (name.isBlank()) {
                _events.value = StoresUiEvent.ShowError("Group name cannot be empty")
                return@launch
            }
            storeGroupRepository.upsertGroup(group.copy(name = name.trim()))
                .onFailure { error ->
                    _events.value = StoresUiEvent.ShowError("Failed to update group: ${error.message}")
                }
        }
    }

    fun onDeleteGroup(groupId: String) {
        viewModelScope.launch {
            val group = uiState.value.storeGroups.find { it.id == groupId }
            if (group == null) {
                _events.value = StoresUiEvent.ShowError("Group not found")
                return@launch
            }
            _events.value = StoresUiEvent.ConfirmDeleteGroup(groupId, group.name)
        }
    }

    fun confirmDeleteGroup(groupId: String) {
        viewModelScope.launch {
            storeGroupRepository.deleteGroup(groupId)
                .onFailure { error ->
                    _events.value = StoresUiEvent.ShowError("Failed to delete group: ${error.message}")
                }
        }
    }

    /** Gap-based reorder after a group drag (same algorithm as categories). */
    fun onReorderGroups(reorderedGroups: List<StoreGroup>) {
        // Reordering from a filtered view would corrupt sortOrder (the diff runs
        // against the full list) — drag is disabled in the UI while searching.
        if (searchQuery.value.isNotBlank()) return
        viewModelScope.launch {
            val original = uiState.value.storeGroups
            if (original.map { it.id } == reorderedGroups.map { it.id }) return@launch

            val movedItem = findSingleMovedGroup(original, reorderedGroups)
            if (movedItem != null) {
                val targetIndex = reorderedGroups.indexOfFirst { it.id == movedItem.id }
                val newSortOrder = computeGapSortOrder(reorderedGroups, targetIndex)
                if (newSortOrder != null) {
                    storeGroupRepository.updateGroupSortOrders(listOf(movedItem.id to newSortOrder))
                        .onFailure { error ->
                            _events.value = StoresUiEvent.ShowError("Failed to update order: ${error.message}")
                        }
                    return@launch
                }
            }

            val updates = reorderedGroups.mapIndexed { index, group ->
                group.id to ((index + 1) * 1000f)
            }
            storeGroupRepository.updateGroupSortOrders(updates)
                .onFailure { error ->
                    _events.value = StoresUiEvent.ShowError("Failed to update order: ${error.message}")
                }
        }
    }

    private fun findSingleMovedGroup(original: List<StoreGroup>, reordered: List<StoreGroup>): StoreGroup? {
        if (original.size != reordered.size) return null
        return reordered.firstOrNull { item ->
            val origWithoutItem = original.filter { it.id != item.id }.map { it.id }
            val reorderedWithoutItem = reordered.filter { it.id != item.id }.map { it.id }
            origWithoutItem == reorderedWithoutItem
        }
    }

    private fun computeGapSortOrder(list: List<StoreGroup>, targetIndex: Int): Float? {
        if (list.size <= 1) return null
        return when (targetIndex) {
            0 -> list[1].sortOrder - 1000f
            list.lastIndex -> list[list.lastIndex - 1].sortOrder + 1000f
            else -> {
                val prev = list[targetIndex - 1].sortOrder
                val next = list[targetIndex + 1].sortOrder
                val diff = next - prev
                if (diff > 0.001f) prev + diff / 2f else null
            }
        }
    }

    fun onEventConsumed() {
        _events.value = null
    }
}
