package net.marvinweber.simsli.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.local.mapper.toDb
import net.marvinweber.simsli.data.repository.ItemRepository
import net.marvinweber.simsli.data.sync.SyncContract
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.Item
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemRepositoryImpl @Inject constructor(
    private val itemDao: ItemDao,
    private val listEntryDao: ListEntryDao,
    private val itemStoreDao: ItemStoreDao,
    private val outboxDao: OutboxDao,
    private val syncScheduler: SyncScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ItemRepository {

    override fun getItemsByHousehold(householdId: String): Flow<List<Item>> {
        return itemDao.getItemsByHousehold(householdId).map { items ->
            items.map { it.toDomain() }
        }
    }

    override fun getItemById(itemId: String): Flow<Item?> {
        return itemDao.getItemById(itemId).map { it?.toDomain() }
    }

    override suspend fun getMaxItemSortOrder(householdId: String): Float {
        return withContext(ioDispatcher) {
            itemDao.getMaxSortOrder(householdId) ?: 0f
        }
    }

    override suspend fun createItem(item: Item): Result<Item> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                val newItem = item.copy(
                    id = if (item.id.isEmpty()) UUID.randomUUID().toString() else item.id,
                    createdAt = if (item.createdAt == Instant.EPOCH) now else item.createdAt,
                    updatedAt = now
                )
                itemDao.insert(newItem.toDb())
                enqueueUpsert(newItem.id)
                Result.success(newItem)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateItem(item: Item): Result<Item> {
        return withContext(ioDispatcher) {
            try {
                val updatedItem = item.copy(updatedAt = Instant.now())
                itemDao.insert(updatedItem.toDb())
                enqueueUpsert(updatedItem.id)
                Result.success(updatedItem)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteItem(itemId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                // 1. Soft-delete the item itself
                itemDao.delete(itemId, now)
                enqueueUpsert(itemId)

                // 2. Remove all list entries for this item
                val listEntries = listEntryDao.getEntriesByItemIdOnce(itemId)
                for (entry in listEntries) {
                    listEntryDao.delete(entry.id)
                    outboxDao.enqueue(
                        DbOutboxEntry(
                            entityType = SyncContract.ENTITY_LIST_ENTRY,
                            entityId = entry.id,
                            operation = SyncContract.OP_DELETE,
                            createdAt = now
                        )
                    )
                }

                // 3. Remove all store assignments for this item
                val storeIds = itemStoreDao.getStoreIdsForItem(itemId)
                itemStoreDao.deleteByItemId(itemId)
                for (storeId in storeIds) {
                    outboxDao.enqueue(
                        DbOutboxEntry(
                            entityType = SyncContract.ENTITY_ITEM_STORE,
                            entityId = SyncContract.itemStoreEntityId(itemId, storeId),
                            operation = SyncContract.OP_DELETE,
                            createdAt = now
                        )
                    )
                }

                syncScheduler.requestSync("item:delete")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateItemSortOrder(itemId: String, sortOrder: Float): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                itemDao.updateSortOrder(itemId, sortOrder, Instant.now())
                enqueueUpsert(itemId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun isItemNameDuplicate(
        householdId: String,
        name: String,
        excludeItemId: String?
    ): Boolean = withContext(ioDispatcher) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return@withContext false
        val activeItems = itemDao.getActiveItemsByHouseholdOnce(householdId)
        activeItems.any { dbItem ->
            (excludeItemId == null || dbItem.id != excludeItemId) &&
                dbItem.name.trim().equals(trimmed, ignoreCase = true)
        }
    }

    private suspend fun enqueueUpsert(itemId: String) {
        outboxDao.enqueue(
            DbOutboxEntry(
                entityType = SyncContract.ENTITY_ITEM,
                entityId = itemId,
                operation = SyncContract.OP_UPSERT,
                createdAt = Instant.now()
            )
        )
        syncScheduler.requestSync("item")
    }
}
