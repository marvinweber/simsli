package net.marvinweber.simsli.ui.screens.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.ItemRepository
import net.marvinweber.simsli.data.repository.ListEntryRepository
import net.marvinweber.simsli.data.repository.StoreRepository
import net.marvinweber.simsli.domain.model.Household
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.domain.model.ListEntry
import net.marvinweber.simsli.domain.model.Store
import java.time.Instant
import javax.inject.Inject

data class ListEntryItem(
    val listEntry: ListEntry,
    val item: Item?
)

/** A catalog item as offered in the add-items sheet. */
data class CatalogItemUi(
    val item: Item,
    val isOnActiveList: Boolean
)

data class ListUiState(
    val household: Household? = null,
    val stores: List<Store> = emptyList(),
    val activeEntries: List<ListEntryItem> = emptyList(),
    val recentlyChecked: List<ListEntryItem> = emptyList(),
    val catalog: List<CatalogItemUi> = emptyList(),
    val isLoading: Boolean = true,
    val isAddSheetOpen: Boolean = false,
    val addQuery: String = "",
    val editingEntry: ListEntryItem? = null,
    val error: String? = null
)

sealed class ListUiEvent {
    data object NavigateToSettings : ListUiEvent()
    data class ShowError(val message: String) : ListUiEvent()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ListViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val householdRepository: HouseholdRepository,
    private val storeRepository: StoreRepository,
    private val itemRepository: ItemRepository,
    private val listEntryRepository: ListEntryRepository
) : ViewModel() {

    private val _selectedStoreId = MutableStateFlow<String?>(null)
    val selectedStoreId: StateFlow<String?> = _selectedStoreId.asStateFlow()

    /** Sheet/dialog state kept apart from data state so the data pipeline stays small. */
    private data class SheetUi(
        val addSheetOpen: Boolean = false,
        val addQuery: String = "",
        val editingEntryId: String? = null
    )

    private val sheetUi = MutableStateFlow(SheetUi())

    val uiState: StateFlow<ListUiState> = combine(
        householdRepository.getHousehold(),
        _selectedStoreId,
        sheetUi
    ) { household, selectedStoreId, sheet ->
        Triple(household, selectedStoreId, sheet)
    }.flatMapLatest { (household, selectedStoreId, sheet) ->
        if (household == null) {
            flowOf(ListUiState(isLoading = false))
        } else {
            combine(
                storeRepository.getStoresByHousehold(household.id),
                listEntryRepository.getListEntriesByHouseholdAndStore(household.id, selectedStoreId),
                itemRepository.getItemsByHousehold(household.id)
            ) { stores, listEntries, items ->
                val itemMap = items.associateBy { it.id }
                val entryItems = listEntries.map { entry ->
                    ListEntryItem(entry, itemMap[entry.itemId])
                }
                val activeItemIds = listEntries.filter { !it.done }.map { it.itemId }.toSet()
                ListUiState(
                    household = household,
                    stores = stores,
                    activeEntries = entryItems.filter { !it.listEntry.done },
                    recentlyChecked = entryItems.filter { it.listEntry.done },
                    catalog = items.map { CatalogItemUi(it, it.id in activeItemIds) },
                    isLoading = false,
                    isAddSheetOpen = sheet.addSheetOpen,
                    addQuery = sheet.addQuery,
                    editingEntry = entryItems.firstOrNull { it.listEntry.id == sheet.editingEntryId },
                    error = null
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ListUiState(isLoading = true)
    )

    private val _events = MutableStateFlow<ListUiEvent?>(null)
    val events: StateFlow<ListUiEvent?> = _events.asStateFlow()

    init {
        ensureHouseholdExists()
    }

    private fun ensureHouseholdExists() {
        viewModelScope.launch {
            // When signed in, the household comes from the server via sync —
            // only offline use creates a local one (adopted by the server on first sync).
            val signedIn = authRepository.authState.first() is AuthState.SignedIn
            if (signedIn) return@launch

            if (householdRepository.getHousehold().first() == null) {
                householdRepository.createHousehold("My household")
                    .onFailure { error ->
                        _events.value = ListUiEvent.ShowError("Failed to create household: ${error.message}")
                    }
            }
        }
    }

    fun selectStore(storeId: String?) {
        _selectedStoreId.value = storeId
    }

    fun onSettingsClick() {
        _events.value = ListUiEvent.NavigateToSettings
    }

    fun onEventConsumed() {
        _events.value = null
    }

    // --- Check off / undo ---------------------------------------------------------

    fun onToggleItemDone(listEntryId: String) {
        viewModelScope.launch {
            val current = uiState.value.let { state ->
                (state.activeEntries + state.recentlyChecked)
                    .find { it.listEntry.id == listEntryId }?.listEntry
            } ?: return@launch

            listEntryRepository.updateListEntryDoneStatus(listEntryId, !current.done)
                .onFailure { error ->
                    _events.value = ListUiEvent.ShowError("Failed to update item: ${error.message}")
                }
        }
    }

    // --- Add-items sheet ------------------------------------------------------------

    fun onAddClick() {
        sheetUi.update { it.copy(addSheetOpen = true) }
    }

    fun dismissAddSheet() {
        sheetUi.update { it.copy(addSheetOpen = false, addQuery = "") }
    }

    fun onAddQueryChange(query: String) {
        sheetUi.update { it.copy(addQuery = query) }
    }

    fun addCatalogItemToList(itemId: String) {
        viewModelScope.launch {
            val household = uiState.value.household ?: return@launch
            listEntryRepository.addToList(household.id, itemId)
                .onFailure { error ->
                    _events.value = ListUiEvent.ShowError("Failed to add item: ${error.message}")
                }
        }
    }

    /** Fast path: create a catalog item from the query text and put it on the list. */
    fun createAndAddItem() {
        val name = uiState.value.addQuery.trim()
        if (name.isEmpty()) return
        viewModelScope.launch {
            val household = uiState.value.household ?: return@launch
            val item = Item(
                id = "",
                householdId = household.id,
                name = name,
                notes = null,
                type = ItemType.PERMANENT,
                sortOrder = itemRepository.getMaxItemSortOrder(household.id) + 1f,
                createdAt = Instant.EPOCH,
                updatedAt = Instant.EPOCH
            )
            itemRepository.createItem(item)
                .onSuccess { created ->
                    listEntryRepository.addToList(household.id, created.id)
                        .onSuccess {
                            sheetUi.update { it.copy(addQuery = "") }
                        }
                        .onFailure { error ->
                            _events.value = ListUiEvent.ShowError("Failed to add item: ${error.message}")
                        }
                }
                .onFailure { error ->
                    _events.value = ListUiEvent.ShowError("Failed to create item: ${error.message}")
                }
        }
    }

    // --- Entry editor sheet -----------------------------------------------------------

    fun onEntryClick(listEntryId: String) {
        sheetUi.update { it.copy(editingEntryId = listEntryId) }
    }

    fun dismissEntryEditor() {
        sheetUi.update { it.copy(editingEntryId = null) }
    }

    fun saveEntryDetails(listEntryId: String, quantityText: String, unit: String, comment: String) {
        viewModelScope.launch {
            listEntryRepository.updateEntryDetails(
                listEntryId = listEntryId,
                quantity = quantityText.toDoubleOrNull(),
                unit = unit,
                comment = comment
            ).onFailure { error ->
                _events.value = ListUiEvent.ShowError("Failed to save: ${error.message}")
            }
            dismissEntryEditor()
        }
    }

    fun removeEntry(listEntryId: String) {
        viewModelScope.launch {
            listEntryRepository.deleteListEntry(listEntryId)
                .onFailure { error ->
                    _events.value = ListUiEvent.ShowError("Failed to remove item: ${error.message}")
                }
            dismissEntryEditor()
        }
    }
}
