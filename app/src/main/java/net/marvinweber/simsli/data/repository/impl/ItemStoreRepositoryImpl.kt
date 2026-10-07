package net.marvinweber.simsli.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.repository.ItemStoreRepository
import net.marvinweber.simsli.data.sync.SyncContract
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.di.IoDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemStoreRepositoryImpl @Inject constructor(
    private val itemStoreDao: ItemStoreDao,
    private val outboxDao: OutboxDao,
    private val syncScheduler: SyncScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ItemStoreRepository {

    override suspend fun assignItemToStores(itemId: String, storeIds: List<String>): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                storeIds.forEach { storeId ->
                    itemStoreDao.insert(DbItemStore(itemId, storeId, now))
                    outboxDao.enqueue(
                        DbOutboxEntry(
                            entityType = SyncContract.ENTITY_ITEM_STORE,
                            entityId = SyncContract.itemStoreEntityId(itemId, storeId),
                            operation = SyncContract.OP_UPSERT,
                            createdAt = now
                        )
                    )
                }
                syncScheduler.requestSync("item_store:assign")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun removeAssignment(itemId: String, storeId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                itemStoreDao.delete(itemId, storeId)
                outboxDao.enqueue(
                    DbOutboxEntry(
                        entityType = SyncContract.ENTITY_ITEM_STORE,
                        entityId = SyncContract.itemStoreEntityId(itemId, storeId),
                        operation = SyncContract.OP_DELETE,
                        createdAt = Instant.now()
                    )
                )
                syncScheduler.requestSync("item_store:remove")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getStoreIdsForItem(itemId: String): List<String> {
        return withContext(ioDispatcher) {
            itemStoreDao.getStoreIdsForItem(itemId)
        }
    }

    override fun observeItemStoresByHousehold(householdId: String): Flow<List<net.marvinweber.simsli.domain.model.ItemStore>> {
        return itemStoreDao.observeAllForHousehold(householdId).map { list ->
            list.map { it.toDomain() }
        }
    }
}
