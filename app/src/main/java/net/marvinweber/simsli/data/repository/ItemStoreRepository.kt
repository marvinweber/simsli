package net.marvinweber.simsli.data.repository

import kotlinx.coroutines.flow.Flow

interface ItemStoreRepository {
    /** Assigns [itemId] to every store in [storeIds] (idempotent). */
    suspend fun assignItemToStores(itemId: String, storeIds: List<String>): Result<Unit>

    suspend fun removeAssignment(itemId: String, storeId: String): Result<Unit>

    suspend fun getStoreIdsForItem(itemId: String): List<String>
    
    fun observeItemStoresByHousehold(householdId: String): Flow<List<net.marvinweber.simsli.domain.model.ItemStore>>
}
