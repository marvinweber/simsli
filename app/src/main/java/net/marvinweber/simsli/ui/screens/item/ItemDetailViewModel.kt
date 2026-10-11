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
import net.marvinweber.simsli.data.repository.StoreGroupRepository
import net.marvinweber.simsli.data.repository.StoreRepository
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.Household
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemLink
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.model.StoreGroup
import net.marvinweber.simsli.domain.model.StoreSelection
import net.marvinweber.simsli.ui.components.WebTitleFetcher
import java.time.Instant
import javax.inject.Inject

data class ItemDetailUiState(
    val household: Household? = null,
    val existingItem: Item? = null,
    val existingSelection: StoreSelection = StoreSelection(),
    val stores: List<Store> = emptyList(),
    val storeGroups: List<StoreGroup> = emptyList(),
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
    data class ShowDuplicateWarning(
        val matchedName: String,
        val matchType: net.marvinweber.simsli.domain.model.ItemMatchType
    ) : ItemDetailUiEvent()
}

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class ItemDetailViewModel @Inject constructor(
    private val householdRepository: HouseholdRepository,
    private val storeRepository: StoreRepository,
    private val storeGroupRepository: StoreGroupRepository,
    private val itemRepository: ItemRepository,
    private val itemStoreRepository: ItemStoreRepository,
    private val categoryRepository: CategoryRepository,
    private val webTitleFetcher: WebTitleFetcher,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val itemId: String? = savedStateHandle.get<String>("itemId")?.takeUnless { it.isBlank() }

    /** Loaded atomically (item + store selection) so the edit form prefills in one step. */
    private data class LoadedItem(val item: Item, val selection: StoreSelection)

    private val loadedItem = MutableStateFlow<LoadedItem?>(null)
    private val _links = MutableStateFlow<List<ItemLink>>(emptyList())
    private val isSaving = MutableStateFlow(false)

    init {
        if (itemId != null) {
            viewModelScope.launch {
                val item = itemRepository.getItemById(itemId).first() ?: return@launch
                val selection = itemStoreRepository.getStoreSelection(itemId)
                _links.value = item.links
                loadedItem.value = LoadedItem(item, selection)
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
                storeGroupRepository.getGroupsByHousehold(household.id),
                categoryRepository.getCategoriesByHousehold(household.id)
            ) { stores, storeGroups, categories ->
                ItemDetailUiState(
                    household = household,
                    existingItem = loaded?.item,
                    existingSelection = loaded?.selection ?: StoreSelection(),
                    stores = stores,
                    storeGroups = storeGroups,
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
        selection: StoreSelection,
        selectedCategoryId: String? = null,
        defaultUnit: String? = null,
        force: Boolean = false
    ) {
        if (isSaving.value) return
        viewModelScope.launch {
            val household = uiState.value.household
            if (household == null) {
                _events.value = ItemDetailUiEvent.ShowError("No household found")
                return@launch
            }
            val trimmedName = name.trim()
            if (trimmedName.isBlank()) {
                _events.value = ItemDetailUiEvent.ShowError("Item name cannot be empty")
                return@launch
            }

            val existing = uiState.value.existingItem
            if (!force) {
                val match = itemRepository.findDuplicateOrSimilarItem(
                    householdId = household.id,
                    name = trimmedName,
                    excludeItemId = existing?.id
                )
                if (match != null) {
                    _events.value = ItemDetailUiEvent.ShowDuplicateWarning(
                        matchedName = match.matchedName,
                        matchType = match.type
                    )
                    return@launch
                }
            }

            isSaving.value = true
            val currentLinks = _links.value
            val trimmedUnit = defaultUnit?.trim()?.ifBlank { null }
            if (existing == null) {
                val item = Item(
                    id = "",
                    householdId = household.id,
                    name = trimmedName,
                    notes = notes.trim().ifBlank { null },
                    type = type,
                    categoryId = selectedCategoryId,
                    defaultUnit = trimmedUnit,
                    sortOrder = itemRepository.getMaxItemSortOrder(household.id) + 1f,
                    links = currentLinks,
                    createdAt = Instant.EPOCH,
                    updatedAt = Instant.EPOCH
                )
                itemRepository.createItem(item)
                    .onSuccess { created ->
                        if (selection != StoreSelection()) {
                            itemStoreRepository.saveStoreSelection(created.id, selection)
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
                        name = trimmedName,
                        notes = notes.trim().ifBlank { null },
                        type = type,
                        categoryId = selectedCategoryId,
                        defaultUnit = trimmedUnit,
                        links = currentLinks
                    )
                ).onSuccess {
                    if (selection != uiState.value.existingSelection) {
                        itemStoreRepository.saveStoreSelection(existing.id, selection)
                    }
                    _events.value = ItemDetailUiEvent.NavigateBack
                }.onFailure { error ->
                    isSaving.value = false
                    _events.value = ItemDetailUiEvent.ShowError("Failed to save item: ${error.message}")
                }
            }
        }
    }

    fun deleteItem() {
        val id = itemId ?: return
        if (isSaving.value) return
        viewModelScope.launch {
            isSaving.value = true
            itemRepository.deleteItem(id)
                .onSuccess {
                    _events.value = ItemDetailUiEvent.NavigateBack
                }
                .onFailure { error ->
                    isSaving.value = false
                    _events.value = ItemDetailUiEvent.ShowError("Failed to delete item: ${error.message}")
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
