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
}
