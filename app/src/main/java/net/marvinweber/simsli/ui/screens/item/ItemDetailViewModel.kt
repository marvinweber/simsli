package net.marvinweber.simsli.ui.screens.item

import androidx.lifecycle.SavedStateHandle
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.marvinweber.simsli.data.repository.CategoryRepository
import net.marvinweber.simsli.data.repository.HouseholdRepository
import net.marvinweber.simsli.data.repository.ItemRepository
import net.marvinweber.simsli.data.repository.ItemStoreRepository
import net.marvinweber.simsli.data.repository.StoreRepository
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Household
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemLink
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.ui.components.WebTitleFetcher
import java.time.Instant
import javax.inject.Inject

data class ItemDetailUiState(
    val household: Household? = null,
    val existingItem: Item? = null,
    val existingStoreIds: Set<String> = emptySet(),
    val stores: List<Store> = emptyList(),
    val categories: List<Category> = emptyList(),
    val links: List<ItemLink> = emptyList(),
    /** True while an existing item is being loaded for editing. */
    val isWaitingForItem: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val error: String? = null
)

sealed class ItemDetailUiEvent {
    data object NavigateBack : ItemDetailUiEvent()
    data class ShowError(val message: String) : ItemDetailUiEvent()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val storeRepository: StoreRepository,
    private val itemRepository: ItemRepository,
    private val itemStoreRepository: ItemStoreRepository,
    private val categoryRepository: CategoryRepository,
    private val webTitleFetcher: WebTitleFetcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String? = savedStateHandle.get<String>("itemId")?.takeUnless { it.isBlank() }

    /** Loaded atomically (item + store assignments) so the edit form prefills in one step. */
    private data class LoadedItem(val item: Item, val storeIds: Set<String>)

    private val loadedItem = MutableStateFlow<LoadedItem?>(null)
    private val _links = MutableStateFlow<List<ItemLink>>(emptyList())
    private val isSaving = MutableStateFlow(false)

    init {
        if (itemId != null) {
            viewModelScope.launch {
                val item = itemRepository.getItemById(itemId).first() ?: return@launch
                val storeIds = itemStoreRepository.getStoreIdsForItem(itemId).toSet()
                _links.value = item.links
                loadedItem.value = LoadedItem(item, storeIds)
                item.links.forEach { link ->
                    if (link.title.isNullOrBlank()) {
                        fetchTitleForUrl(link.url)
                    }
                }
            }
        }
    }

    private data class CombinedParams(
        val household: Household?,
        val loaded: LoadedItem?,
        val links: List<ItemLink>,
        val saving: Boolean
    )

    val uiState: StateFlow<ItemDetailUiState> = combine(
        householdRepository.getHousehold(),
        loadedItem,
        _links,
        isSaving
    ) { household, loaded, links, saving ->
        CombinedParams(household, loaded, links, saving)
    }.flatMapLatest { (household, loaded, links, saving) ->
        val waitingForItem = itemId != null && loaded == null
        if (household == null) {
            flowOf(ItemDetailUiState(isLoading = false, isSaving = saving))
        } else {
            combine(
                storeRepository.getStoresByHousehold(household.id),
                categoryRepository.getCategoriesByHousehold(household.id)
            ) { stores, categories ->
                ItemDetailUiState(
                    household = household,
                    existingItem = loaded?.item,
                    existingStoreIds = loaded?.storeIds ?: emptySet(),
                    stores = stores,
                    categories = categories,
                    links = links,
                    isWaitingForItem = waitingForItem,
                    isLoading = false,
                    isSaving = saving,
                    error = null
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ItemDetailUiState(isLoading = true)
    )

    private val _events = MutableStateFlow<ItemDetailUiEvent?>(null)
    val events: StateFlow<ItemDetailUiEvent?> = _events.asStateFlow()

    fun addLink(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return
        val current = _links.value
        if (current.any { it.url.equals(trimmed, ignoreCase = true) }) return
        _links.value = current + ItemLink(url = trimmed, title = null)
        fetchTitleForUrl(trimmed)
    }

    private fun fetchTitleForUrl(url: String) {
        viewModelScope.launch {
            val title = webTitleFetcher.fetchTitle(url)
            if (!title.isNullOrBlank()) {
                _links.value = _links.value.map {
                    if (it.url.equals(url, ignoreCase = true)) it.copy(title = title) else it
                }
            }
        }
    }

    fun removeLink(url: String) {
        _links.value = _links.value.filterNot { it.url.equals(url, ignoreCase = true) }
    }

    /** Creates or updates the item (catalog metadata only — entry data lives on the list). */
    fun saveItem(
        name: String,
        notes: String,
        type: ItemType,
        selectedStoreIds: List<String>,
        selectedCategoryId: String? = null
    ) {
        if (isSaving.value) return
        viewModelScope.launch {
            val household = uiState.value.household
            if (household == null) {
                _events.value = ItemDetailUiEvent.ShowError("No household found")
                return@launch
            }
            if (name.isBlank()) {
                _events.value = ItemDetailUiEvent.ShowError("Item name cannot be empty")
                return@launch
            }

            isSaving.value = true
            val existing = uiState.value.existingItem
            val currentLinks = _links.value
            if (existing == null) {
                val item = Item(
                    id = "",
                    householdId = household.id,
                    name = name.trim(),
                    notes = notes.trim().ifBlank { null },
                    type = type,
                    categoryId = selectedCategoryId,
                    sortOrder = itemRepository.getMaxItemSortOrder(household.id) + 1f,
                    links = currentLinks,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH
                )
                itemRepository.createItem(item)
                    .onSuccess { created ->
                        if (selectedStoreIds.isNotEmpty()) {
                            itemStoreRepository.assignItemToStores(created.id, selectedStoreIds)
                        }
                        _events.value = ItemDetailUiEvent.NavigateBack
                    }
                    .onFailure { error ->
                        isSaving.value = false
                        _events.value = ItemDetailUiEvent.ShowError("Failed to create item: ${error.message}")
                    }
            } else {
                itemRepository.updateItem(
                    existing.copy(
                        name = name.trim(),
                        notes = notes.trim().ifBlank { null },
                        type = type,
                        categoryId = selectedCategoryId,
                        links = currentLinks
                    )
                ).onSuccess {
                    val selected = selectedStoreIds.toSet()
                    val current = uiState.value.existingStoreIds
                    val toAdd = selected - current
                    val toRemove = current - selected
                    if (toAdd.isNotEmpty()) {
                        itemStoreRepository.assignItemToStores(existing.id, toAdd.toList())
                    }
                    toRemove.forEach { itemStoreRepository.removeAssignment(existing.id, it) }
                    _events.value = ItemDetailUiEvent.NavigateBack
                }.onFailure { error ->
                    isSaving.value = false
                    _events.value = ItemDetailUiEvent.ShowError("Failed to save item: ${error.message}")
                }
            }
        }
    }

    fun onBack() {
        _events.value = ItemDetailUiEvent.NavigateBack
    }

    fun onEventConsumed() {
        _events.value = null
    }
}
