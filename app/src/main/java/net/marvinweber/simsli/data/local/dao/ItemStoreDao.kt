package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.marvinweber.simsli.data.local.entity.DbItemStore
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemStoreDao {
    @Query("SELECT * FROM item_stores WHERE itemId = :itemId")
    fun getItemStores(itemId: String): Flow<List<DbItemStore>>

    @Query("SELECT storeId FROM item_stores WHERE itemId = :itemId")
    suspend fun getStoreIdsForItem(itemId: String): List<String>

    @Query("SELECT itemId FROM item_stores WHERE storeId = :storeId")
    suspend fun getItemIdsForStore(storeId: String): List<String>

    @Query("SELECT * FROM item_stores WHERE itemId = :itemId AND storeId = :storeId")
    suspend fun getOnce(itemId: String, storeId: String): DbItemStore?

    @Query("""
        SELECT ist.* FROM item_stores ist
        INNER JOIN items i ON ist.itemId = i.id
        WHERE i.householdId = :householdId
    """)
    suspend fun getAllForHousehold(householdId: String): List<DbItemStore>

    @Query("""
        SELECT ist.* FROM item_stores ist
        INNER JOIN items i ON ist.itemId = i.id
        WHERE i.householdId = :householdId
    """)
    fun observeAllForHousehold(householdId: String): Flow<List<DbItemStore>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(itemStore: DbItemStore)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(itemStores: List<DbItemStore>)

    @Query("DELETE FROM item_stores WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("DELETE FROM item_stores WHERE storeId = :storeId")
    suspend fun deleteByStoreId(storeId: String)

    @Query("DELETE FROM item_stores WHERE itemId = :itemId AND storeId = :storeId")
    suspend fun delete(itemId: String, storeId: String)
}
