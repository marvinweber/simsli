package net.marvinweber.simsli.data.repository

import kotlinx.coroutines.flow.Flow
import net.marvinweber.simsli.domain.model.Item

interface ItemRepository {
    fun getItemsByHousehold(householdId: String): Flow<List<Item>>

    fun getItemById(itemId: String): Flow<Item?>

    suspend fun getMaxItemSortOrder(householdId: String): Float

    suspend fun createItem(item: Item): Result<Item>

    suspend fun updateItem(item: Item): Result<Item>

    suspend fun deleteItem(itemId: String): Result<Unit>

    suspend fun updateItemSortOrder(itemId: String, sortOrder: Float): Result<Unit>
 
    suspend fun findDuplicateOrSimilarItem(
        householdId: String,
        name: String,
        excludeItemId: String? = null
    ): net.marvinweber.simsli.domain.model.ItemMatch?

    suspend fun isItemNameDuplicate(householdId: String, name: String, excludeItemId: String? = null): Boolean
}
