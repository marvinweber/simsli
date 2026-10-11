package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.marvinweber.simsli.data.local.entity.DbItemStoreGroup
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemStoreGroupDao {
    @Query("SELECT * FROM item_store_groups WHERE itemId = :itemId")
    suspend fun getItemStoreGroupsForItem(itemId: String): List<DbItemStoreGroup>

    @Query("SELECT * FROM item_store_groups WHERE itemId = :itemId AND groupId = :groupId")
    suspend fun getOnce(itemId: String, groupId: String): DbItemStoreGroup?

    @Query("""
        SELECT isg.* FROM item_store_groups isg
        INNER JOIN items i ON isg.itemId = i.id
        WHERE i.householdId = :householdId
    """)
    suspend fun getAllForHousehold(householdId: String): List<DbItemStoreGroup>

    @Query("""
        SELECT isg.* FROM item_store_groups isg
        INNER JOIN items i ON isg.itemId = i.id
        WHERE i.householdId = :householdId
    """)
    fun observeAllForHousehold(householdId: String): Flow<List<DbItemStoreGroup>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(itemStoreGroup: DbItemStoreGroup)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(itemStoreGroups: List<DbItemStoreGroup>)

    @Query("DELETE FROM item_store_groups WHERE itemId = :itemId")
    suspend fun deleteByItemId(itemId: String)

    @Query("DELETE FROM item_store_groups WHERE groupId = :groupId")
    suspend fun deleteByGroupId(groupId: String)

    @Query("DELETE FROM item_store_groups WHERE itemId = :itemId AND groupId = :groupId")
    suspend fun delete(itemId: String, groupId: String)

    @Query("SELECT itemId FROM item_store_groups WHERE groupId = :groupId")
    suspend fun getItemIdsForGroup(groupId: String): List<String>
}
