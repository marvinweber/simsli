package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import net.marvinweber.simsli.data.local.entity.DbItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {
    @Query("SELECT * FROM items WHERE householdId = :householdId AND deletedAt IS NULL ORDER BY sortOrder ASC")
    fun getItemsByHousehold(householdId: String): Flow<List<DbItem>>

    @Query("SELECT * FROM items WHERE id = :id AND deletedAt IS NULL")
    fun getItemById(id: String): Flow<DbItem?>

    @Query("SELECT MAX(sortOrder) FROM items WHERE householdId = :householdId AND deletedAt IS NULL")
    suspend fun getMaxSortOrder(householdId: String): Float?

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): DbItem?

    @Query("SELECT * FROM items WHERE householdId = :householdId")
    suspend fun getAllIncludingDeleted(householdId: String): List<DbItem>

    @Query("SELECT * FROM items WHERE householdId = :householdId AND deletedAt IS NULL")
    suspend fun getActiveItemsByHouseholdOnce(householdId: String): List<DbItem>

    @Query("""
        UPDATE items
        SET householdId = :newHouseholdId, updatedAt = :updatedAt
        WHERE householdId = :oldHouseholdId
    """)
    suspend fun reassignHousehold(oldHouseholdId: String, newHouseholdId: String, updatedAt: java.time.Instant)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: DbItem)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<DbItem>)

    @Query("""
        UPDATE items
        SET sortOrder = :sortOrder, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateSortOrder(id: String, sortOrder: Float, updatedAt: java.time.Instant)

    @Query("UPDATE items SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun delete(id: String, deletedAt: java.time.Instant)

    @Query("SELECT id FROM items WHERE categoryId = :categoryId AND deletedAt IS NULL")
    suspend fun getItemIdsByCategoryId(categoryId: String): List<String>

    @Query("UPDATE items SET categoryId = NULL, updatedAt = :updatedAt WHERE categoryId = :categoryId AND deletedAt IS NULL")
    suspend fun clearCategory(categoryId: String, updatedAt: java.time.Instant)

    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<String, Float>>, updatedAt: java.time.Instant) {
        updates.forEach { (id, sortOrder) ->
            updateSortOrder(id, sortOrder, updatedAt)
        }
    }
}
