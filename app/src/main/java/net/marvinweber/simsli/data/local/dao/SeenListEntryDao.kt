package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.marvinweber.simsli.data.local.entity.DbSeenListEntry

@Dao
interface SeenListEntryDao {
    @Query("SELECT * FROM seen_list_entries")
    fun observeAll(): Flow<List<DbSeenListEntry>>

    /** IGNORE: re-marking an already-seen entry is a no-op (keeps the mark-seen → recompose loop convergent). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: DbSeenListEntry)

    /** Removes rows whose list entry no longer exists locally (hard-deleted by sync or GC). */
    @Query("DELETE FROM seen_list_entries WHERE entryId NOT IN (SELECT id FROM list_entries)")
    suspend fun deleteOrphaned()
}
