package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import net.marvinweber.simsli.data.local.entity.DbHousehold
import kotlinx.coroutines.flow.Flow

@Dao
interface HouseholdDao {
    @Query("SELECT * FROM households WHERE deletedAt IS NULL LIMIT 1")
    fun getHousehold(): Flow<DbHousehold?>

    @Query("SELECT * FROM households WHERE deletedAt IS NULL LIMIT 1")
    suspend fun getHouseholdOnce(): DbHousehold?

    @Query("SELECT * FROM households WHERE id = :id AND deletedAt IS NULL")
    fun getHouseholdById(id: String): Flow<DbHousehold?>

    @Query("SELECT * FROM households WHERE id = :id")
    suspend fun getHouseholdByIdIncludingDeleted(id: String): DbHousehold?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(household: DbHousehold)

    @Update
    suspend fun update(household: DbHousehold)

    @Query("UPDATE households SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun delete(id: String, deletedAt: java.time.Instant)
}
