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
     * filters compose without a dedicated query per combination. Store filters
     * test the derived effective store set (STORE-7): MEMBER rows ∪ stores of
     * selected groups − EXCLUDED rows, and favoriteOnly filters to favorited stores
     * (or items with zero favorites).
     */
    @Query("""
        SELECT le.* FROM list_entries le
        INNER JOIN items i ON le.itemId = i.id
        WHERE le.householdId = :householdId
        AND i.deletedAt IS NULL
        AND (:filterByStore = 0 OR (
            (EXISTS (
                SELECT 1 FROM item_stores ist
                WHERE ist.itemId = le.itemId AND ist.storeId = :storeId AND ist.membership = 'MEMBER'
            ) OR EXISTS (
                SELECT 1 FROM item_store_groups isg
                WHERE isg.itemId = le.itemId
                  AND isg.groupId = (SELECT s.groupId FROM stores s WHERE s.id = :storeId)
            ))
            AND NOT EXISTS (
                SELECT 1 FROM item_stores ex
                WHERE ex.itemId = le.itemId AND ex.storeId = :storeId AND ex.membership = 'EXCLUDED'
            )
            AND (:favoriteOnly = 0 OR (
                EXISTS (
                    SELECT 1 FROM item_stores fav
                    WHERE fav.itemId = le.itemId AND fav.storeId = :storeId AND fav.favorite = 1
                ) OR NOT EXISTS (
                    SELECT 1 FROM item_stores anyfav
                    WHERE anyfav.itemId = le.itemId AND anyfav.favorite = 1
                      AND NOT EXISTS (
                          SELECT 1 FROM item_stores anyex
                          WHERE anyex.itemId = le.itemId AND anyex.storeId = anyfav.storeId AND anyex.membership = 'EXCLUDED'
                      )
                      AND (
                          anyfav.membership = 'MEMBER'
                          OR EXISTS (
                              SELECT 1 FROM item_store_groups anyisg
                              INNER JOIN stores anys ON anys.groupId = anyisg.groupId
                              WHERE anyisg.itemId = le.itemId AND anys.id = anyfav.storeId
                          )
                      )
                )
            ))
        ))
        AND (:filterByGroup = 0 OR (
            (
                EXISTS (
                    SELECT 1 FROM item_stores ist
                    INNER JOIN stores s ON s.id = ist.storeId
                    WHERE ist.itemId = le.itemId AND ist.membership = 'MEMBER' AND s.groupId = :groupId
                      AND NOT EXISTS (
                          SELECT 1 FROM item_stores ex2
                          WHERE ex2.itemId = le.itemId AND ex2.storeId = ist.storeId AND ex2.membership = 'EXCLUDED'
                      )
                ) OR (
                    EXISTS (
                        SELECT 1 FROM item_store_groups isg
                        WHERE isg.itemId = le.itemId AND isg.groupId = :groupId
                    )
                    AND EXISTS (
                        SELECT 1 FROM stores s3 WHERE s3.groupId = :groupId AND s3.deletedAt IS NULL
                          AND NOT EXISTS (
                              SELECT 1 FROM item_stores ex3
                              WHERE ex3.itemId = le.itemId AND ex3.storeId = s3.id AND ex3.membership = 'EXCLUDED'
                          )
                    )
                )
            )
            AND (:favoriteOnly = 0 OR (
                EXISTS (
                    SELECT 1 FROM item_stores fav
                    INNER JOIN stores s ON s.id = fav.storeId
                    WHERE fav.itemId = le.itemId AND s.groupId = :groupId AND fav.favorite = 1
                      AND NOT EXISTS (
                          SELECT 1 FROM item_stores ex4
                          WHERE ex4.itemId = le.itemId AND ex4.storeId = fav.storeId AND ex4.membership = 'EXCLUDED'
                      )
                      AND (
                          fav.membership = 'MEMBER'
                          OR EXISTS (
                              SELECT 1 FROM item_store_groups isg4
                              WHERE isg4.itemId = le.itemId AND isg4.groupId = :groupId
                          )
                      )
                ) OR NOT EXISTS (
                    SELECT 1 FROM item_stores anyfav
                    WHERE anyfav.itemId = le.itemId AND anyfav.favorite = 1
                      AND NOT EXISTS (
                          SELECT 1 FROM item_stores anyex
                          WHERE anyex.itemId = le.itemId AND anyex.storeId = anyfav.storeId AND anyex.membership = 'EXCLUDED'
                      )
                      AND (
                          anyfav.membership = 'MEMBER'
                          OR EXISTS (
                              SELECT 1 FROM item_store_groups anyisg
                              INNER JOIN stores anys ON anys.groupId = anyisg.groupId
                              WHERE anyisg.itemId = le.itemId AND anys.id = anyfav.storeId
                          )
                      )
                )
            ))
        ))
        AND (:noStore = 0 OR (
            NOT EXISTS (
                SELECT 1 FROM item_stores ist2
                WHERE ist2.itemId = le.itemId AND ist2.membership = 'MEMBER'
            )
            AND NOT EXISTS (
                SELECT 1 FROM item_store_groups isg2 WHERE isg2.itemId = le.itemId
            )
        ))
        AND (:filterByCategory = 0 OR i.categoryId = :categoryId)
        AND (:noCategory = 0 OR i.categoryId IS NULL)
        ORDER BY le.done ASC, i.sortOrder ASC
    """)
    fun getListEntriesFiltered(
        householdId: String,
        filterByStore: Boolean,
        storeId: String,
        filterByGroup: Boolean,
        groupId: String,
        noStore: Boolean,
        filterByCategory: Boolean,
        categoryId: String,
        noCategory: Boolean,
        favoriteOnly: Boolean = false
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
