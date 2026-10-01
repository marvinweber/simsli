package net.marvinweber.simsli.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import net.marvinweber.simsli.data.local.entity.DbStoreCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface StoreCategoryDao {
    @Query("SELECT * FROM store_categories WHERE storeId = :storeId ORDER BY sortOrder ASC")
    fun getStoreCategories(storeId: String): Flow<List<DbStoreCategory>>

    @Query("SELECT categoryId FROM store_categories WHERE storeId = :storeId")
    suspend fun getCategoryIdsForStore(storeId: String): List<String>

    @Query("SELECT storeId FROM store_categories WHERE categoryId = :categoryId")
    suspend fun getStoreIdsForCategory(categoryId: String): List<String>

    @Query("SELECT * FROM store_categories WHERE storeId = :storeId AND categoryId = :categoryId")
    suspend fun getOnce(storeId: String, categoryId: String): DbStoreCategory?

    @Query("""
        SELECT sc.* FROM store_categories sc
        INNER JOIN categories c ON sc.categoryId = c.id
        WHERE c.householdId = :householdId
    """)
    suspend fun getAllForHousehold(householdId: String): List<DbStoreCategory>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(storeCategory: DbStoreCategory)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(storeCategories: List<DbStoreCategory>)

    @Query("DELETE FROM store_categories WHERE categoryId = :categoryId")
    suspend fun deleteByCategoryId(categoryId: String)

    @Query("DELETE FROM store_categories WHERE storeId = :storeId")
    suspend fun deleteByStoreId(storeId: String)

    @Query("DELETE FROM store_categories WHERE storeId = :storeId AND categoryId = :categoryId")
    suspend fun delete(storeId: String, categoryId: String)
}
