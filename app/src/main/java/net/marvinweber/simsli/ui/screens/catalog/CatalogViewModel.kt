package net.marvinweber.simsli.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.ItemRepository
import net.marvinweber.simsli.data.repository.ListEntryRepository
import net.marvinweber.simsli.domain.model.Item
import javax.inject.Inject

data class CatalogItemRow(
    val item: Item,
    val isOnActiveList: Boolean
)

data class CatalogUiState(
    val items: List<CatalogItemRow> = emptyList(),
    /** Non-null → the add-to-list sheet is open for this item. */
    val addingItem: CatalogItemRow? = null,
    val isLoading: Boolean = true
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val itemRepository: ItemRepository,
    private val listEntryRepository: ListEntryRepository
) : ViewModel() {

    /** The item the add-to-list sheet is open for, if any. */
    private val addingItemId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<CatalogUiState> = householdRepository.getHousehold()
        .flatMapLatest { household ->
            if (household == null) {
                flowOf(CatalogUiState(isLoading = false))
            } else {
                combine(
                    itemRepository.getItemsByHousehold(household.id),
                    listEntryRepository.getListEntriesByHousehold(household.id),
                    addingItemId
                ) { items, entries, addingId ->
                    val activeItemIds = entries.filter { !it.done }.map { it.itemId }.toSet()
                    CatalogUiState(
                        items = items.map { CatalogItemRow(it, it.id in activeItemIds) },
                        addingItem = addingId?.let { id ->
                            items.firstOrNull { it.id == id }?.let { CatalogItemRow(it, it.id in activeItemIds) }
                        },
                        isLoading = false
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CatalogUiState(isLoading = true)
        )

    /** Opens the entry details sheet (quantity, unit, comment) for adding this item to the list. */
    fun onAddToListClick(itemId: String) {
        addingItemId.value = itemId
    }

    fun dismissAddSheet() {
        addingItemId.value = null
    }

    fun confirmAddToList(itemId: String, quantityText: String, unit: String, comment: String) {
        viewModelScope.launch {
            val household = householdRepository.getHousehold().first() ?: return@launch
            listEntryRepository.addToList(
                householdId = household.id,
                itemId = itemId,
                quantity = quantityText.toDoubleOrNull(),
                unit = unit.ifBlank { null },
                comment = comment.ifBlank { null }
            )
            addingItemId.value = null
        }
    }
}
