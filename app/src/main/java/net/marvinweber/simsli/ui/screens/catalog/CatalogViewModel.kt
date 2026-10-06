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
import net.marvinweber.simsli.data.repository.CategoryRepository
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.ItemRepository
import net.marvinweber.simsli.data.repository.ListEntryRepository
import net.marvinweber.simsli.data.sync.SyncManager
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Item
import javax.inject.Inject

data class CatalogItemRow(
    val item: Item,
    val isOnActiveList: Boolean
)

data class CatalogCategoryGroup(
    val key: String,
    val title: String,
    val emoji: String?,
    val isImplicit: Boolean,
    val items: List<CatalogItemRow>
)

data class CatalogUiState(
    val groups: List<CatalogCategoryGroup> = emptyList(),
    val totalItemCount: Int = 0,
    /** Non-null → the add-to-list sheet is open for this item. */
    val addingItem: CatalogItemRow? = null,
    val isLoading: Boolean = true
)

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class CatalogViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val itemRepository: ItemRepository,
    private val categoryRepository: CategoryRepository,
    private val listEntryRepository: ListEntryRepository,
    private val syncManager: SyncManager
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
                    categoryRepository.getCategoriesByHousehold(household.id),
                    listEntryRepository.getListEntriesByHousehold(household.id),
                    addingItemId
                ) { items, categories, entries, addingId ->
                    val activeItemIds = entries.filter { !it.done }.map { it.itemId }.toSet()
                    val rows = items.map { CatalogItemRow(it, it.id in activeItemIds) }

                    val sortedCategories = categories.sortedBy { it.sortOrder }
                    val categoryById = sortedCategories.associateBy { it.id }

                    val groups = mutableListOf<CatalogCategoryGroup>()

                    // 1. Explicit categories (in global sortOrder) that contain items
                    sortedCategories.forEach { category ->
                        val categoryRows = rows.filter { it.item.categoryId == category.id }
                        if (categoryRows.isNotEmpty()) {
                            groups.add(
                                CatalogCategoryGroup(
                                    key = category.id,
                                    title = category.name,
                                    emoji = category.emoji,
                                    isImplicit = false,
                                    items = categoryRows.sortedBy { it.item.sortOrder }
                                )
                            )
                        }
                    }

                    // 2. Uncategorized items
                    val uncategorizedRows = rows.filter { it.item.categoryId == null || it.item.categoryId !in categoryById }
                    if (uncategorizedRows.isNotEmpty()) {
                        groups.add(
                            CatalogCategoryGroup(
                                key = "uncategorized",
                                title = "Uncategorized",
                                emoji = null,
                                isImplicit = true,
                                items = uncategorizedRows.sortedBy { it.item.sortOrder }
                            )
                        )
                    }

                    CatalogUiState(
                        groups = groups,
                        totalItemCount = rows.size,
                        addingItem = addingId?.let { id ->
                            rows.firstOrNull { it.item.id == id }
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

    fun refresh() {
        viewModelScope.launch {
            syncManager.syncNow("manual")
        }
    }
}
