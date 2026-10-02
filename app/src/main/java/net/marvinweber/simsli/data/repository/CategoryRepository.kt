package net.marvinweber.simsli.data.repository

import kotlinx.coroutines.flow.Flow
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.StoreCategory

interface CategoryRepository {
    fun getCategoriesByHousehold(householdId: String): Flow<List<Category>>

    fun getStoreCategories(storeId: String): Flow<List<StoreCategory>>

    suspend fun getCategoryById(id: String): Flow<Category?>

    suspend fun getMaxCategorySortOrder(householdId: String): Float

    suspend fun createCategory(category: Category): Result<Category>

    suspend fun updateCategory(category: Category): Result<Category>

    suspend fun deleteCategory(categoryId: String): Result<Unit>

    suspend fun updateCategorySortOrder(categoryId: String, sortOrder: Float): Result<Unit>

    suspend fun reorderCategories(updates: List<Pair<String, Float>>): Result<Unit>
}
