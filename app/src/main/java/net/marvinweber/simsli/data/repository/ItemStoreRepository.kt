package net.marvinweber.simsli.data.repository

import kotlinx.coroutines.flow.Flow
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ItemStoreGroup
import net.marvinweber.simsli.domain.model.StoreSelection

interface ItemStoreRepository {

    /**
     * Reconciles the item's full store selection (STORE-7): explicit stores
     * (MEMBER), exclusions (EXCLUDED), and group selections — one write path,
     * so saves are idempotent and order-independent.
     */
    suspend fun saveStoreSelection(itemId: String, selection: StoreSelection): Result<Unit>

    suspend fun getStoreSelection(itemId: String): StoreSelection

    fun observeItemStoresByHousehold(householdId: String): Flow<List<ItemStore>>

    fun observeItemStoreGroupsByHousehold(householdId: String): Flow<List<ItemStoreGroup>>
}
