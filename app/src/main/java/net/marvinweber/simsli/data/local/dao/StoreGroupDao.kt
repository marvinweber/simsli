package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import net.marvinweber.simsli.data.local.entity.DbStoreGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreGroupDao {
    @Query("SELECT * FROM store_groups WHERE householdId = :householdId AND deletedAt IS NULL ORDER BY sortOrder ASC")
    fun getByHousehold(householdId: String): Flow<List<DbStoreGroup>>

    @Query("SELECT MAX(sortOrder) FROM store_groups WHERE householdId = :householdId AND deletedAt IS NULL")
    suspend fun getMaxSortOrder(householdId: String): Float?

    @Query("SELECT * FROM store_groups WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): DbStoreGroup?

    @Query("SELECT * FROM store_groups WHERE householdId = :householdId")
    suspend fun getAllIncludingDeleted(householdId: String): List<DbStoreGroup>

    @Query("""
        UPDATE store_groups
        SET householdId = :newHouseholdId, updatedAt = :updatedAt
        WHERE householdId = :oldHouseholdId
    """)
    suspend fun reassignHousehold(oldHouseholdId: String, newHouseholdId: String, updatedAt: java.time.Instant)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(storeGroup: DbStoreGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(storeGroups: List<DbStoreGroup>)

    @Query("""
        UPDATE store_groups
        SET sortOrder = :sortOrder, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateSortOrder(id: String, sortOrder: Float, updatedAt: java.time.Instant)

    @Query("UPDATE store_groups SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun delete(id: String, deletedAt: java.time.Instant)

    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<String, Float>>, updatedAt: java.time.Instant) {
        updates.forEach { (id, sortOrder) ->
            updateSortOrder(id, sortOrder, updatedAt)
        }
    }
}
