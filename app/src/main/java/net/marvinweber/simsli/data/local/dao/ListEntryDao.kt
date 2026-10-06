package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import net.marvinweber.simsli.data.local.entity.DbListEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface ListEntryDao {
    @Query("""
        SELECT le.* FROM list_entries le
        INNER JOIN items i ON le.itemId = i.id
        WHERE le.householdId = :householdId
        AND i.deletedAt IS NULL
        ORDER BY le.done ASC, i.sortOrder ASC
    """)
    fun getListEntriesByHousehold(householdId: String): Flow<List<DbListEntry>>

    /**
     * One query for every store/category filter combination (LIST-5): the
     * boolean switches enable the EXISTS/NOT EXISTS/IS NULL clauses, so the
     * filters compose without a dedicated query per combination.
     */
    @Query("""
        SELECT le.* FROM list_entries le
        INNER JOIN items i ON le.itemId = i.id
        WHERE le.householdId = :householdId
        AND i.deletedAt IS NULL
        AND (:filterByStore = 0 OR EXISTS (
            SELECT 1 FROM item_stores ist
            WHERE ist.itemId = le.itemId AND ist.storeId = :storeId
        ))
        AND (:noStore = 0 OR NOT EXISTS (
            SELECT 1 FROM item_stores ist2 WHERE ist2.itemId = le.itemId
        ))
        AND (:filterByCategory = 0 OR i.categoryId = :categoryId)
        AND (:noCategory = 0 OR i.categoryId IS NULL)
        ORDER BY le.done ASC, i.sortOrder ASC
    """)
    fun getListEntriesFiltered(
        householdId: String,
        filterByStore: Boolean,
        storeId: String,
        noStore: Boolean,
        filterByCategory: Boolean,
        categoryId: String,
        noCategory: Boolean
    ): Flow<List<DbListEntry>>

    @Query("SELECT * FROM list_entries WHERE id = :id")
    fun getListEntryById(id: String): Flow<DbListEntry?>

    @Query("SELECT * FROM list_entries WHERE id = :id")
    suspend fun getListEntryOnce(id: String): DbListEntry?

    @Query("SELECT * FROM list_entries WHERE householdId = :householdId")
    suspend fun getAllByHousehold(householdId: String): List<DbListEntry>

    @Query("""
        UPDATE list_entries
        SET householdId = :newHouseholdId, updatedAt = :updatedAt
        WHERE householdId = :oldHouseholdId
    """)
    suspend fun reassignHousehold(oldHouseholdId: String, newHouseholdId: String, updatedAt: java.time.Instant)

    @Query("SELECT * FROM list_entries WHERE householdId = :householdId AND itemId = :itemId")
    fun getListEntryByHouseholdAndItem(householdId: String, itemId: String): Flow<DbListEntry?>

    @Query("SELECT * FROM list_entries WHERE householdId = :householdId AND itemId = :itemId LIMIT 1")
    suspend fun getEntryByHouseholdAndItemOnce(householdId: String, itemId: String): DbListEntry?

    @Query("SELECT * FROM list_entries WHERE itemId = :itemId")
    suspend fun getEntriesByItemIdOnce(itemId: String): List<DbListEntry>

    /** Checked-off entries past the "Recently checked" TTL — garbage collection removes them everywhere. */
    @Query("""
        SELECT * FROM list_entries
        WHERE done = 1 AND completedAt IS NOT NULL AND completedAt < :cutoff
    """)
    suspend fun getExpiredDoneEntries(cutoff: java.time.Instant): List<DbListEntry>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(listEntry: DbListEntry)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(listEntries: List<DbListEntry>)

    @Query("""
        UPDATE list_entries
        SET done = :done, completedAt = :completedAt, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateDoneStatus(id: String, done: Boolean, completedAt: java.time.Instant?, updatedAt: java.time.Instant)

    @Query("""
        UPDATE list_entries
        SET quantity = :quantity, unit = :unit, comment = :comment, done = 0, completedAt = NULL, updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateDetails(id: String, quantity: Double?, unit: String?, comment: String?, updatedAt: java.time.Instant)

    @Query("DELETE FROM list_entries WHERE id = :id")
    suspend fun delete(id: String)

    @Transaction
    suspend fun markAllDone(householdId: String, done: Boolean, completedAt: java.time.Instant?, updatedAt: java.time.Instant) {
        // This would need to be implemented with proper transaction support
    }
}
