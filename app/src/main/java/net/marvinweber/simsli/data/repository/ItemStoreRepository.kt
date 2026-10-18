package net.marvinweber.simsli.data.repository

import kotlinx.coroutines.flow.Flow
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ItemStoreGroup
import net.marvinweber.simsli.domain.model.StoreSelection

interface ItemStoreRepository {

    /**
     * Reconciles the item's full store selection (STORE-7): explicit stores
     * (MEMBER), exclusions (EXCLUDED), and group selections — one write path,
     * so saves are idempotent and order-independent. Prices on `item_stores`
     * rows are never touched here: rows carrying a price survive coverage
     * loss as dormant METADATA rows (STORE-6).
     */
    suspend fun saveStoreSelection(itemId: String, selection: StoreSelection): Result<Unit>

    /**
     * Sets or clears per-store prices (STORE-6) in minor units. Absent keys
     * leave the store untouched; a null value clears the price. Never changes
     * membership (no pinning, STORE-7) — pricing a store outside the selection
     * creates a METADATA row; clearing the last metadata of a row deletes it.
     */
    suspend fun savePrices(itemId: String, prices: Map<String, Long?>): Result<Unit>

    suspend fun getStoreSelection(itemId: String): StoreSelection

    fun observeItemStoresByHousehold(householdId: String): Flow<List<ItemStore>>

    fun observeItemStoreGroupsByHousehold(householdId: String): Flow<List<ItemStoreGroup>>
}
