package net.marvinweber.simsli.data.repository

import net.marvinweber.simsli.domain.model.Store
import kotlinx.coroutines.flow.Flow

interface StoreRepository {
    fun getStoresByHousehold(householdId: String): Flow<List<Store>>

    suspend fun getMaxStoreSortOrder(householdId: String): Float

    suspend fun createStore(store: Store): Result<Store>

    suspend fun updateStore(store: Store): Result<Store>

    suspend fun deleteStore(storeId: String): Result<Unit>

    suspend fun updateStoreSortOrder(storeId: String, sortOrder: Float): Result<Unit>
}
