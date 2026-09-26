package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.marvinweber.simsli.data.local.entity.DbSyncState

@Dao
interface SyncStateDao {
    @Query("SELECT * FROM sync_state WHERE tableName = :tableName")
    suspend fun get(tableName: String): DbSyncState?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(state: DbSyncState)

    /** Forces the next sync to re-pull everything (e.g. after signing in to a different account). */
    @Query("DELETE FROM sync_state")
    suspend fun clearAll()
}
