package net.marvinweber.simsli.domain.util

import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ItemStoreGroup
import net.marvinweber.simsli.domain.model.ItemStoreMembership
import net.marvinweber.simsli.domain.model.Store

/**
 * The derived effective store set per item (STORE-7, ADR 0020):
 *
 *     effective = item_stores(MEMBER) ∪ stores of selected groups − item_stores(EXCLUDED)
 *
 * Selection is stored, membership derived — never denormalized, so adding a
 * store to a group automatically covers every item that selected the group.
 * Pure and unit-testable; callers pass live (non-deleted) stores.
 */
object EffectiveStoreSet {

    fun compute(
        itemStores: List<ItemStore>,
        itemStoreGroups: List<ItemStoreGroup>,
        stores: List<Store>
    ): Map<String, Set<String>> {
        val storesByGroup = stores
            .filter { it.groupId != null }
            .groupBy({ it.groupId!! }, { it.id })
        val excludedByItem = itemStores
            .filter { it.membership == ItemStoreMembership.EXCLUDED }
            .groupBy({ it.itemId }, { it.storeId })

        val result = mutableMapOf<String, MutableSet<String>>()

        fun cover(itemId: String, storeId: String) {
            if (storeId in excludedByItem[itemId].orEmpty()) return
            result.getOrPut(itemId) { mutableSetOf() }.add(storeId)
        }

        for (ist in itemStores) {
            if (ist.membership == ItemStoreMembership.MEMBER) cover(ist.itemId, ist.storeId)
        }
        for (isg in itemStoreGroups) {
            for (storeId in storesByGroup[isg.groupId].orEmpty()) {
                cover(isg.itemId, storeId)
            }
        }
        return result
    }

    /** The effective store set of a single item (empty when it has no coverage). */
    fun forItem(
        itemId: String,
        itemStores: List<ItemStore>,
        itemStoreGroups: List<ItemStoreGroup>,
        stores: List<Store>
    ): Set<String> = compute(itemStores, itemStoreGroups, stores)[itemId].orEmpty()

    /**
     * Map from itemId to the set of storeIds that are favorited for that item,
     * excluding EXCLUDED stores.
     */
    fun computeFavorites(itemStores: List<ItemStore>): Map<String, Set<String>> {
        val excludedByItem = itemStores
            .filter { it.membership == ItemStoreMembership.EXCLUDED }
            .groupBy({ it.itemId }, { it.storeId })

        return itemStores
            .filter { it.favorite && it.membership != ItemStoreMembership.EXCLUDED }
            .filter { it.storeId !in excludedByItem[it.itemId].orEmpty() }
            .groupBy({ it.itemId }, { it.storeId })
            .mapValues { it.value.toSet() }
    }

    /**
     * Checks if an item matches the given [net.marvinweber.simsli.domain.model.StoreFilter],
     * optionally restricted to favorites (LIST-5, STORE-5).
     *
     * Rule (§2 Concepts):
     * - Favorite filter OFF: item matches if the store (or any group store) is in its effective store set.
     * - Favorite filter ON: item matches if the store (or any group store) is favorited for this item;
     *   an item with NO favorites at all counts for all its stores.
     */
    fun matchesStoreFilter(
        effectiveStoreIds: Set<String>,
        favoriteStoreIds: Set<String>,
        filter: net.marvinweber.simsli.domain.model.StoreFilter,
        stores: List<Store>,
        favoriteOnly: Boolean
    ): Boolean {
        val baseMatch = when (filter) {
            net.marvinweber.simsli.domain.model.StoreFilter.All -> true
            is net.marvinweber.simsli.domain.model.StoreFilter.ByStore -> filter.storeId in effectiveStoreIds
            is net.marvinweber.simsli.domain.model.StoreFilter.ByGroup -> effectiveStoreIds.any { id ->
                stores.firstOrNull { it.id == id }?.groupId == filter.groupId
            }
            net.marvinweber.simsli.domain.model.StoreFilter.NoStore -> effectiveStoreIds.isEmpty()
        }
        if (!baseMatch) return false
        if (!favoriteOnly) return true

        val favoritesInEffective = effectiveStoreIds.intersect(favoriteStoreIds)
        if (favoritesInEffective.isEmpty()) {
            return true
        }

        return when (filter) {
            is net.marvinweber.simsli.domain.model.StoreFilter.ByStore -> filter.storeId in favoritesInEffective
            is net.marvinweber.simsli.domain.model.StoreFilter.ByGroup -> favoritesInEffective.any { id ->
                stores.firstOrNull { it.id == id }?.groupId == filter.groupId
            }
            else -> true
        }
    }
}
