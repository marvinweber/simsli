package net.marvinweber.simsli.ui.screens.catalog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
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
    val isLoading: Boolean = true
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val itemRepository: ItemRepository,
    private val listEntryRepository: ListEntryRepository
) : ViewModel() {

    val uiState: StateFlow<CatalogUiState> = householdRepository.getHousehold()
        .flatMapLatest { household ->
            if (household == null) {
                flowOf(CatalogUiState(isLoading = false))
            } else {
                combine(
                    itemRepository.getItemsByHousehold(household.id),
                    listEntryRepository.getListEntriesByHousehold(household.id)
                ) { items, entries ->
                    val activeItemIds = entries.filter { !it.done }.map { it.itemId }.toSet()
                    CatalogUiState(
                        items = items.map { CatalogItemRow(it, it.id in activeItemIds) },
                        isLoading = false
                    )
                }
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CatalogUiState(isLoading = true)
        )

    fun addItemToList(itemId: String) {
        viewModelScope.launch {
            val household = householdRepository.getHousehold().first() ?: return@launch
            listEntryRepository.addToList(household.id, itemId)
        }
    }
}
