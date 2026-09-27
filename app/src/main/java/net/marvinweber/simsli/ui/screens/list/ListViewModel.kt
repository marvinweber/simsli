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

/** A catalog item as offered in the quick-add sheet. */
data class CatalogItemUi(
    val item: Item,
    val isOnActiveList: Boolean,
    /** The item's list entry, if any (active or recently checked) — such suggestions open the entry editor. */
    val entryId: String? = null
)

/** What the quick-add sheet has selected from the suggestions, if anything. */
sealed interface AddSelection {
    data class Existing(val item: Item) : AddSelection
    data class New(val name: String) : AddSelection
}

/** What the shopping list is filtered to (LIST-5). */
sealed interface StoreFilter {
    /** Everything. */
    data object All : StoreFilter

    /** Only items assigned to this store. */
    data class ByStore(val storeId: String) : StoreFilter

    /** Only items without any store assignment. */
    data object NoStore : StoreFilter
}

data class ListUiState(
    val household: Household? = null,
    val stores: List<Store> = emptyList(),
    val activeEntries: List<ListEntryItem> = emptyList(),
    val recentlyChecked: List<ListEntryItem> = emptyList(),
    val catalog: List<CatalogItemUi> = emptyList(),
    val isLoading: Boolean = true,
    val isAddSheetOpen: Boolean = false,
    val addQuery: String = "",
    val addSelection: AddSelection? = null,
    val isAddInProgress: Boolean = false,
    val editingEntry: ListEntryItem? = null,
    val error: String? = null
)

sealed class ListUiEvent {
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

    private val _storeFilter = MutableStateFlow<StoreFilter>(StoreFilter.All)
    val storeFilter: StateFlow<StoreFilter> = _storeFilter.asStateFlow()

    /** Sheet/dialog state kept apart from data state so the data pipeline stays small. */
    private data class SheetUi(
        val addSheetOpen: Boolean = false,
        val addQuery: String = "",
        val addSelection: AddSelection? = null,
        val isAddInProgress: Boolean = false,
        val editingEntryId: String? = null
    )

    private val sheetUi = MutableStateFlow(SheetUi())

    val uiState: StateFlow<ListUiState> = combine(
        householdRepository.getHousehold(),
        _storeFilter,
        sheetUi
    ) { household, storeFilter, sheet ->
        Triple(household, storeFilter, sheet)
    }.flatMapLatest { (household, storeFilter, sheet) ->
        if (household == null) {
            flowOf(ListUiState(isLoading = false))
        } else {
            val entriesFlow = when (storeFilter) {
                is StoreFilter.All -> listEntryRepository.getListEntriesByHousehold(household.id)
                is StoreFilter.ByStore ->
                    listEntryRepository.getListEntriesByHouseholdAndStore(household.id, storeFilter.storeId)
                is StoreFilter.NoStore -> listEntryRepository.getListEntriesByHouseholdWithoutStore(household.id)
            }
            combine(
                storeRepository.getStoresByHousehold(household.id),
                entriesFlow,
                itemRepository.getItemsByHousehold(household.id)
            ) { stores, listEntries, items ->
                val itemMap = items.associateBy { it.id }
                val entryItems = listEntries.map { entry ->
                    ListEntryItem(entry, itemMap[entry.itemId])
                }
                val activeItemIds = listEntries.filter { !it.done }.map { it.itemId }.toSet()
                val entryIdByItemId = listEntries.associate { it.itemId to it.id }
                ListUiState(
                    household = household,
                    stores = stores,
                    activeEntries = entryItems.filter { !it.listEntry.done },
                    recentlyChecked = entryItems.filter { it.listEntry.done },
                    catalog = items.map {
                        CatalogItemUi(it, it.id in activeItemIds, entryIdByItemId[it.id])
                    },
                    isLoading = false,
                    isAddSheetOpen = sheet.addSheetOpen,
                    addQuery = sheet.addQuery,
                    addSelection = sheet.addSelection,
                    isAddInProgress = sheet.isAddInProgress,
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

    fun selectFilter(filter: StoreFilter) {
        _storeFilter.value = filter
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

    // --- Quick-add sheet (LIST-2) -----------------------------------------------------

    fun onAddClick() {
        sheetUi.update { it.copy(addSheetOpen = true, addSelection = null) }
    }

    fun dismissAddSheet() {
        sheetUi.update { it.copy(addSheetOpen = false, addQuery = "", addSelection = null) }
    }

    fun onAddQueryChange(query: String) {
        sheetUi.update { it.copy(addQuery = query) }
    }

    /** A suggestion whose item already has an entry opens that entry's editor; one without goes into the quick form. */
    fun onSuggestionClick(catalogItem: CatalogItemUi) {
        val entryId = catalogItem.entryId
        if (entryId != null) {
            sheetUi.update {
                it.copy(
                    addSheetOpen = false,
                    addQuery = "",
                    addSelection = null,
                    editingEntryId = entryId
                )
            }
        } else {
            sheetUi.update { it.copy(addSelection = AddSelection.Existing(catalogItem.item)) }
        }
    }

    fun onSelectNew() {
        val name = sheetUi.value.addQuery.trim()
        if (name.isNotEmpty()) {
            sheetUi.update { it.copy(addSelection = AddSelection.New(name)) }
        }
    }

    fun backToSearch() {
        sheetUi.update { it.copy(addSelection = null) }
    }

    /**
     * Adds the current selection to the list with the entered details. New items become
     * ONE_TIME unless [saveToCatalog] (PERMANENT). Resets to the search state unless
     * [closeAfter] closes the sheet.
     */
    fun addSelected(
        saveToCatalog: Boolean,
        quantityText: String,
        unit: String,
        comment: String,
        closeAfter: Boolean
    ) {
        val selection = sheetUi.value.addSelection ?: return
        if (sheetUi.value.isAddInProgress) return
        viewModelScope.launch {
            val household = uiState.value.household ?: return@launch
            sheetUi.update { it.copy(isAddInProgress = true) }
            when (selection) {
                is AddSelection.Existing -> listEntryRepository.addToList(
                    householdId = household.id,
                    itemId = selection.item.id,
                    quantity = quantityText.toDoubleOrNull(),
                    unit = unit.ifBlank { null },
                    comment = comment.ifBlank { null }
                )
                is AddSelection.New -> itemRepository.createItem(
                    Item(
                        id = "",
                        householdId = household.id,
                        name = selection.name,
                        notes = null,
                        type = if (saveToCatalog) ItemType.PERMANENT else ItemType.ONE_TIME,
                        sortOrder = itemRepository.getMaxItemSortOrder(household.id) + 1f,
                        createdAt = Instant.EPOCH,
                        updatedAt = Instant.EPOCH
                    )
                ).fold(
                    onSuccess = { created ->
                        listEntryRepository.addToList(
                            householdId = household.id,
                            itemId = created.id,
                            quantity = quantityText.toDoubleOrNull(),
                            unit = unit.ifBlank { null },
                            comment = comment.ifBlank { null }
                        )
                    },
                    onFailure = { error -> Result.failure(error) }
                )
            }.onSuccess {
                if (closeAfter) {
                    dismissAddSheet()
                } else {
                    sheetUi.update {
                        it.copy(addSelection = null, addQuery = "", isAddInProgress = false)
                    }
                }
            }.onFailure { error ->
                _events.value = ListUiEvent.ShowError("Failed to add item: ${error.message}")
                sheetUi.update { it.copy(isAddInProgress = false) }
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
