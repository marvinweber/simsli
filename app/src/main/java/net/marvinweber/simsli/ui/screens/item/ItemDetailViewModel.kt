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
import net.marvinweber.simsli.domain.util.EffectiveStoreSet
import net.marvinweber.simsli.ui.components.WebTitleFetcher
import java.time.Instant
import javax.inject.Inject

/** One store row on the Store Prices screen (STORE-6): DB truth, drafts applied on top. */
data class PriceRow(
    val storeId: String,
    val storeName: String,
    val isFavorite: Boolean,
    val priceMinor: Long?
)

/**
 * Pending edits made on the Store Prices screen (STORE-6). They live only in
 * this ViewModel until the item is saved via its Save button — so leaving the
 * prices screen loses nothing, and the item's pending-change detection covers them.
 */
data class PriceEdits(
    /** storeId → price in minor units; null = cleared. */
    val prices: Map<String, Long?> = emptyMap(),
    /** Edited reference size (null = cleared). */
    val referenceSize: String? = null,
    /** True once the reference size was edited (distinguishes "cleared" from "untouched"). */
    val referenceSizeEdited: Boolean = false
) {
    /** The reference size to show and apply: the edit if present, else the item's stored value. */
    fun effectiveReferenceSize(itemValue: String?): String? =
        if (referenceSizeEdited) referenceSize else itemValue
}

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
    /** Store rows for the Store Prices screen: effective-set stores in store order. */
    val priceRows: List<PriceRow> = emptyList(),
    /** Pending edits from the Store Prices screen, applied on Save. */
    val priceEdits: PriceEdits = PriceEdits(),
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
    private val priceEdits = MutableStateFlow(PriceEdits())

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
        val saving: Boolean,
        val edits: PriceEdits
    )

    val uiState: StateFlow<ItemDetailUiState> = combine(
        householdRepository.getHousehold(),
        loadedItem,
        _links,
        isSaving,
        priceEdits
    ) { household, loaded, links, saving, edits ->
        CombinedParams(household, loaded, links, saving, edits)
    }.flatMapLatest { (household, loaded, links, saving, edits) ->
        val waitingForItem = itemId != null && loaded == null
        if (household == null) {
            flowOf(ItemDetailUiState(isLoading = false, isSaving = saving))
        } else {
            combine(
                storeRepository.getStoresByHousehold(household.id),
                storeGroupRepository.getGroupsByHousehold(household.id),
                categoryRepository.getCategoriesByHousehold(household.id),
                itemStoreRepository.observeItemStoresByHousehold(household.id),
                itemStoreRepository.observeItemStoreGroupsByHousehold(household.id)
            ) { stores, storeGroups, categories, itemStores, itemStoreGroups ->
                // Prices screen rows: effective-set stores in store order with DB truth;
                // draft edits are applied on top at display time.
                val effectiveIds = EffectiveStoreSet.forItem(itemId.orEmpty(), itemStores, itemStoreGroups, stores)
                val rowsByStore = itemStores.associateBy { it.storeId }
                val priceRows = stores
                    .sortedBy { it.sortOrder }
                    .filter { it.id in effectiveIds }
                    .map { store ->
                        val row = rowsByStore[store.id]
                        PriceRow(
                            storeId = store.id,
                            storeName = store.name,
                            isFavorite = row?.favorite == true,
                            priceMinor = row?.priceMinor
                        )
                    }
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
                    priceRows = priceRows,
                    priceEdits = edits,
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

    /** Records a price edit from the Store Prices screen (null = cleared). Not persisted until Save. */
    fun savePriceDraft(storeId: String, minor: Long?) {
        priceEdits.value = priceEdits.value.copy(prices = priceEdits.value.prices + (storeId to minor))
    }

    /** Records a reference-size edit from the Store Prices screen. Not persisted until Save. */
    fun saveReferenceSizeDraft(text: String) {
        val normalized = text.trim().ifBlank { null }
        priceEdits.value = priceEdits.value.copy(referenceSize = normalized, referenceSizeEdited = true)
    }

    private fun clearPriceEdits() {
        priceEdits.value = PriceEdits()
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
            val edits = priceEdits.value
            if (existing == null) {
                val item = Item(
                    id = "",
                    householdId = household.id,
                    name = trimmedName,
                    notes = notes.trim().ifBlank { null },
                    type = type,
                    categoryId = selectedCategoryId,
                    defaultUnit = trimmedUnit,
                    referenceSize = edits.effectiveReferenceSize(null),
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
                        applyPriceEdits(created.id, edits)
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
                        referenceSize = edits.effectiveReferenceSize(existing.referenceSize),
                        links = currentLinks
                    )
                ).onSuccess {
                    if (selection != uiState.value.existingSelection) {
                        itemStoreRepository.saveStoreSelection(existing.id, selection)
                    }
                    applyPriceEdits(existing.id, edits)
                    _events.value = ItemDetailUiEvent.NavigateBack
                }.onFailure { error ->
                    isSaving.value = false
                    _events.value = ItemDetailUiEvent.ShowError("Failed to save item: ${error.message}")
                }
            }
        }
    }

    /** Persists pending prices-screen edits alongside the item save (STORE-6). */
    private suspend fun applyPriceEdits(targetItemId: String, edits: PriceEdits) {
        if (edits.prices.isNotEmpty()) {
            itemStoreRepository.savePrices(targetItemId, edits.prices)
                .onFailure { error ->
                    _events.value = ItemDetailUiEvent.ShowError("Failed to save prices: ${error.message}")
                }
        }
        clearPriceEdits()
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
        clearPriceEdits()
        _events.value = ItemDetailUiEvent.NavigateBack
    }

    fun onEventConsumed() {
        _events.value = null
    }
}
