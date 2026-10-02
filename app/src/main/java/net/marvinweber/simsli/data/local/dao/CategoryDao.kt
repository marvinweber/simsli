package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import net.marvinweber.simsli.data.local.entity.DbCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE householdId = :householdId AND deletedAt IS NULL ORDER BY sortOrder ASC")
    fun getCategoriesByHousehold(householdId: String): Flow<List<DbCategory>>

    @Query("SELECT * FROM categories WHERE id = :id AND deletedAt IS NULL")
    fun getCategoryById(id: String): Flow<DbCategory?>

    @Query("SELECT MAX(sortOrder) FROM categories WHERE householdId = :householdId AND deletedAt IS NULL")
    suspend fun getMaxSortOrder(householdId: String): Float?

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getByIdIncludingDeleted(id: String): DbCategory?

    @Query("SELECT * FROM categories WHERE householdId = :householdId")
    suspend fun getAllIncludingDeleted(householdId: String): List<DbCategory>

    @Query("""
        UPDATE categories
        SET householdId = :newHouseholdId, updatedAt = :updatedAt
        WHERE householdId = :oldHouseholdId
    """)
    suspend fun reassignHousehold(oldHouseholdId: String, newHouseholdId: String, updatedAt: java.time.Instant)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: DbCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<DbCategory>)

    @Query("""
        UPDATE categories
        SET sortOrder = :sortOrder, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateSortOrder(id: String, sortOrder: Float, updatedAt: java.time.Instant)

    @Query("UPDATE categories SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun delete(id: String, deletedAt: java.time.Instant)

    @Transaction
    suspend fun updateSortOrders(updates: List<Pair<String, Float>>, updatedAt: java.time.Instant) {
        updates.forEach { (id, sortOrder) ->
            updateSortOrder(id, sortOrder, updatedAt)
        }
    }
}
