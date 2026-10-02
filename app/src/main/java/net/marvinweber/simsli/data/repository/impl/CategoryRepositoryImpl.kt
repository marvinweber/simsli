package net.marvinweber.simsli.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.dao.CategoryDao
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreCategoryDao
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.mapper.toDb
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.repository.CategoryRepository
import net.marvinweber.simsli.data.sync.SyncContract
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.Category
import net.marvinweber.simsli.domain.model.StoreCategory
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepositoryImpl @Inject constructor(
    private val categoryDao: CategoryDao,
    private val itemDao: ItemDao,
    private val storeCategoryDao: StoreCategoryDao,
    private val outboxDao: OutboxDao,
    private val syncScheduler: SyncScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : CategoryRepository {

    override fun getCategoriesByHousehold(householdId: String): Flow<List<Category>> {
        return categoryDao.getCategoriesByHousehold(householdId).map { categories ->
            categories.map { it.toDomain() }
        }
    }

    override fun getStoreCategories(storeId: String): Flow<List<StoreCategory>> {
        return storeCategoryDao.getStoreCategories(storeId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getCategoryById(id: String): Flow<Category?> {
        return categoryDao.getCategoryById(id).map { it?.toDomain() }
    }

    override suspend fun getMaxCategorySortOrder(householdId: String): Float {
        return withContext(ioDispatcher) {
            categoryDao.getMaxSortOrder(householdId) ?: 0f
        }
    }

    override suspend fun createCategory(category: Category): Result<Category> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                val newCategory = category.copy(
                    id = if (category.id.isEmpty()) UUID.randomUUID().toString() else category.id,
                    createdAt = if (category.createdAt == Instant.EPOCH) now else category.createdAt,
                    updatedAt = now
                )
                categoryDao.insert(newCategory.toDb())
                enqueueUpsert(newCategory.id)
                Result.success(newCategory)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateCategory(category: Category): Result<Category> {
        return withContext(ioDispatcher) {
            try {
                val updatedCategory = category.copy(updatedAt = Instant.now())
                categoryDao.insert(updatedCategory.toDb())
                enqueueUpsert(updatedCategory.id)
                Result.success(updatedCategory)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteCategory(categoryId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()

                // 1. Uncategorize affected items (CAT-3: deleting a category sets its items to uncategorized)
                val affectedItemIds = itemDao.getItemIdsByCategoryId(categoryId)
                if (affectedItemIds.isNotEmpty()) {
                    itemDao.clearCategory(categoryId, now)
                    for (itemId in affectedItemIds) {
                        outboxDao.enqueue(
                            DbOutboxEntry(
                                entityType = SyncContract.ENTITY_ITEM,
                                entityId = itemId,
                                operation = SyncContract.OP_UPSERT,
                                createdAt = now
                            )
                        )
                    }
                }

                // 2. Remove any store_categories orderings for this category
                val affectedStoreIds = storeCategoryDao.getStoreIdsForCategory(categoryId)
                for (storeId in affectedStoreIds) {
                    outboxDao.enqueue(
                        DbOutboxEntry(
                            entityType = SyncContract.ENTITY_STORE_CATEGORY,
                            entityId = SyncContract.storeCategoryEntityId(storeId, categoryId),
                            operation = SyncContract.OP_DELETE,
                            createdAt = now
                        )
                    )
                }
                storeCategoryDao.deleteByCategoryId(categoryId)

                // 3. Soft-delete category locally
                categoryDao.delete(categoryId, now)
                enqueueUpsert(categoryId)

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateCategorySortOrder(categoryId: String, sortOrder: Float): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                categoryDao.updateSortOrder(categoryId, sortOrder, now)
                enqueueUpsert(categoryId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun reorderCategories(updates: List<Pair<String, Float>>): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                categoryDao.updateSortOrders(updates, now)
                for ((id, _) in updates) {
                    outboxDao.enqueue(
                        DbOutboxEntry(
                            entityType = SyncContract.ENTITY_CATEGORY,
                            entityId = id,
                            operation = SyncContract.OP_UPSERT,
                            createdAt = now
                        )
                    )
                }
                syncScheduler.requestSync("category")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun enqueueUpsert(categoryId: String) {
        outboxDao.enqueue(
            DbOutboxEntry(
                entityType = SyncContract.ENTITY_CATEGORY,
                entityId = categoryId,
                operation = SyncContract.OP_UPSERT,
                createdAt = Instant.now()
            )
        )
        syncScheduler.requestSync("category")
    }
}
