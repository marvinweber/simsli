package net.marvinweber.simsli.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.dao.ItemStoreGroupDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.dao.StoreGroupDao
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.local.mapper.toDb
import net.marvinweber.simsli.data.repository.StoreGroupRepository
import net.marvinweber.simsli.data.sync.SyncContract
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.StoreGroup
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreGroupRepositoryImpl @Inject constructor(
    private val storeGroupDao: StoreGroupDao,
    private val storeDao: StoreDao,
    private val itemStoreGroupDao: ItemStoreGroupDao,
    private val outboxDao: OutboxDao,
    private val syncScheduler: SyncScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : StoreGroupRepository {

    override fun getGroupsByHousehold(householdId: String): Flow<List<StoreGroup>> {
        return storeGroupDao.getByHousehold(householdId).map { groups ->
            groups.map { it.toDomain() }
        }
    }

    override suspend fun getMaxSortOrder(householdId: String): Float {
        return withContext(ioDispatcher) {
            storeGroupDao.getMaxSortOrder(householdId) ?: 0f
        }
    }

    override suspend fun upsertGroup(group: StoreGroup): Result<StoreGroup> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                val newGroup = group.copy(
                    id = if (group.id.isEmpty()) UUID.randomUUID().toString() else group.id,
                    createdAt = if (group.createdAt == Instant.EPOCH) now else group.createdAt,
                    updatedAt = now
                )
                storeGroupDao.insert(newGroup.toDb())
                enqueueUpsert(newGroup.id)
                Result.success(newGroup)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateGroupSortOrders(updates: List<Pair<String, Float>>): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                storeGroupDao.updateSortOrders(updates, Instant.now())
                updates.forEach { (id, _) -> enqueueUpsert(id) }
                syncScheduler.requestSync("store_group:reorder")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteGroup(groupId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()

                // 1. Stores survive, ungrouped (mirrors CategoryRepositoryImpl.deleteCategory)
                val affectedStoreIds = storeDao.getStoreIdsByGroup(groupId)
                if (affectedStoreIds.isNotEmpty()) {
                    storeDao.clearGroup(groupId, now)
                    for (storeId in affectedStoreIds) {
                        outboxDao.enqueue(
                            DbOutboxEntry(
                                entityType = SyncContract.ENTITY_STORE,
                                entityId = storeId,
                                operation = SyncContract.OP_UPSERT,
                                createdAt = now
                            )
                        )
                    }
                }

                // 2. Remove the group's item selections (exclusions survive — they
                //    are per (item, store), independent of coverage)
                val affectedItemIds = itemStoreGroupDao.getItemIdsForGroup(groupId)
                for (itemId in affectedItemIds) {
                    outboxDao.enqueue(
                        DbOutboxEntry(
                            entityType = SyncContract.ENTITY_ITEM_STORE_GROUP,
                            entityId = SyncContract.itemStoreGroupEntityId(itemId, groupId),
                            operation = SyncContract.OP_DELETE,
                            createdAt = now
                        )
                    )
                }
                itemStoreGroupDao.deleteByGroupId(groupId)

                // 3. Soft-delete the group locally
                storeGroupDao.delete(groupId, now)
                enqueueUpsert(groupId)
                syncScheduler.requestSync("store_group:delete")

                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun enqueueUpsert(groupId: String) {
        outboxDao.enqueue(
            DbOutboxEntry(
                entityType = SyncContract.ENTITY_STORE_GROUP,
                entityId = groupId,
                operation = SyncContract.OP_UPSERT,
                createdAt = Instant.now()
            )
        )
        syncScheduler.requestSync("store_group")
    }
}
