package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry

@Dao
interface OutboxDao {
    @Insert
    suspend fun enqueue(entry: DbOutboxEntry)

    @Query("SELECT * FROM outbox ORDER BY id ASC")
    suspend fun getAll(): List<DbOutboxEntry>

    @Query("SELECT entityId FROM outbox WHERE entityType = :entityType")
    suspend fun getPendingEntityIds(entityType: String): List<String>

    @Query("DELETE FROM outbox WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE outbox SET attempts = attempts + 1 WHERE id = :id")
    suspend fun incrementAttempts(id: Long)

    @Query("SELECT COUNT(*) FROM outbox")
    suspend fun count(): Int

    /** Emits on every outbox change — drives the pending-writes indicator (SYNC-5). */
    @Query("SELECT COUNT(*) FROM outbox")
    fun countFlow(): Flow<Int>
}
