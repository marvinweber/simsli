package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import net.marvinweber.simsli.data.local.entity.DbStore
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreDao {
    @Query("SELECT * FROM stores WHERE householdId = :householdId AND deletedAt IS NULL ORDER BY sortOrder ASC")
    fun getStoresByHousehold(householdId: String): Flow<List<DbStore>>

    @Query("SELECT * FROM stores WHERE id = :id AND deletedAt IS NULL")
    fun getStoreById(id: String): Flow<DbStore?>

    @Query("SELECT MAX(sortOrder) FROM stores WHERE householdId = :householdId AND deletedAt IS NULL")
    suspend fun getMaxSortOrder(householdId: String): Float?

    @Query("SELECT * FROM stores WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): DbStore?

    @Query("SELECT * FROM stores WHERE householdId = :householdId")
    suspend fun getAllIncludingDeleted(householdId: String): List<DbStore>

    @Query("""
        UPDATE stores
        SET householdId = :newHouseholdId, updatedAt = :updatedAt
        WHERE householdId = :oldHouseholdId
    """)
    suspend fun reassignHousehold(oldHouseholdId: String, newHouseholdId: String, updatedAt: java.time.Instant)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(store: DbStore)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(stores: List<DbStore>)

    @Query("""
        UPDATE stores
        SET sortOrder = :sortOrder, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateSortOrder(id: String, sortOrder: Float, updatedAt: java.time.Instant)

    @Query("UPDATE stores SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun delete(id: String, deletedAt: java.time.Instant)

    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<String, Float>>, updatedAt: java.time.Instant) {
        updates.forEach { (id, sortOrder) ->
            updateSortOrder(id, sortOrder, updatedAt)
        }
    }
}
