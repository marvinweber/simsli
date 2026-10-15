package net.marvinweber.simsli.ui.screens.list

import net.marvinweber.simsli.domain.model.CategoryFilter
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.model.StoreFilter
import net.marvinweber.simsli.domain.util.SearchFold
import java.util.Locale

/** The shopping list content after applying the top-bar search query (SEARCH-1). */
internal data class ListSearchResult(
    val groups: List<CategoryGroupUi>,
    val activeEntries: List<ListEntryItem>,
    val recentlyChecked: List<ListEntryItem>,
    /** Catalog items matching the query that have no entry — the quick-add chips. */
    val suggestions: List<CatalogItemUi>
)

/**
 * Applies the top-bar search query to the list content. The query filters by
 * item name (Unicode-folded substring — case-, diacritic-, and script-insensitive,
 * see [SearchFold]) on top of the store/category filters, which are already
 * applied upstream in the DAO. An empty/blank query is the identity — nothing
 * is filtered and no quick-add chips are offered.
 *
 * The fold is injected: production passes [SearchFold]::fold, but that runs on
 * android.icu, which doesn't exist on the JVM — unit tests pass a plain stub.
 */
internal fun applyListSearch(
    groups: List<CategoryGroupUi>,
    activeEntries: List<ListEntryItem>,
    recentlyChecked: List<ListEntryItem>,
    catalog: List<CatalogItemUi>,
    query: String,
    effectiveStoreIdsByItem: Map<String, Set<String>>,
    stores: List<Store>,
    storeFilter: StoreFilter,
    categoryFilter: CategoryFilter,
    fold: (String) -> String = { it.lowercase(Locale.ROOT) }
): ListSearchResult {
    val q = fold(query.trim())
    if (q.isEmpty()) {
        return ListSearchResult(groups, activeEntries, recentlyChecked, suggestions = emptyList())
    }

    fun nameMatches(item: net.marvinweber.simsli.domain.model.Item?) =
        item?.name?.let { fold(it).contains(q) } == true

    val filteredActive = activeEntries.filter { nameMatches(it.item) }
    val filteredRecent = recentlyChecked.filter { nameMatches(it.item) }
    val filteredGroups = groups
        .map { group -> group.copy(entries = group.entries.filter { nameMatches(it.item) }) }
        .filter { it.entries.isNotEmpty() }

    val suggestions = catalog.filter { candidate ->
        candidate.entryId == null &&
            nameMatches(candidate.item) &&
            matchesStoreFilter(
                effectiveStoreIdsByItem[candidate.item.id].orEmpty(),
                storeFilter,
                stores
            ) &&
            matchesCategoryFilter(candidate.item.categoryId, categoryFilter)
    }

    return ListSearchResult(filteredGroups, filteredActive, filteredRecent, suggestions)
}

/**
 * Store-filter check for a single item, mirroring the EXISTS semantics of
 * [net.marvinweber.simsli.data.local.dao.ListEntryDao.getListEntriesFiltered]:
 * an item matches `ByGroup` when its effective store set (MEMBER ∪ group
 * stores − EXCLUDED, see [net.marvinweber.simsli.domain.util.EffectiveStoreSet])
 * contains any store of the group; `NoStore` when the set is empty.
 */
internal fun matchesStoreFilter(
    effectiveStoreIds: Set<String>,
    filter: StoreFilter,
    stores: List<Store>
): Boolean = when (filter) {
    StoreFilter.All -> true
    is StoreFilter.ByStore -> filter.storeId in effectiveStoreIds
    is StoreFilter.ByGroup -> effectiveStoreIds.any { id ->
        stores.firstOrNull { it.id == id }?.groupId == filter.groupId
    }
    StoreFilter.NoStore -> effectiveStoreIds.isEmpty()
}

/** Category-filter check for a single item, mirroring the DAO's category conditions. */
internal fun matchesCategoryFilter(categoryId: String?, filter: CategoryFilter): Boolean = when (filter) {
    CategoryFilter.All -> true
    is CategoryFilter.ByCategory -> categoryId == filter.categoryId
    CategoryFilter.NoCategory -> categoryId == null
}
