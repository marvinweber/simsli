package net.marvinweber.simsli.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ItemStoreGroupDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.data.local.entity.DbItemStoreGroup
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.repository.ItemStoreRepository
import net.marvinweber.simsli.data.sync.SyncContract
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ItemStoreGroup
import net.marvinweber.simsli.domain.model.ItemStoreMembership
import net.marvinweber.simsli.domain.model.StoreSelection
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ItemStoreRepositoryImpl @Inject constructor(
    private val itemStoreDao: ItemStoreDao,
    private val itemStoreGroupDao: ItemStoreGroupDao,
    private val storeDao: StoreDao,
    private val outboxDao: OutboxDao,
    private val syncScheduler: SyncScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ItemStoreRepository {

    override suspend fun saveStoreSelection(itemId: String, selection: StoreSelection): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                val groupStoreIds = selection.groupIds.flatMap { storeDao.getStoreIdsByGroup(it) }.toSet()

                // --- item_stores reconcile (explicit stores + exclusions) ---
                val currentRows = itemStoreDao.getRowsForItem(itemId)
                for (row in currentRows) {
                    val targetMembership = when {
                        row.storeId in selection.excludedStoreIds -> ItemStoreMembership.EXCLUDED
                        row.storeId in groupStoreIds -> ItemStoreMembership.METADATA
                        row.storeId in selection.memberStoreIds -> ItemStoreMembership.MEMBER
                        else -> null
                    }
                    when {
                        // Metadata-only rows carry favorite/price without membership
                        // (STORE-5/6) — selection saves never touch them.
                        row.membership == ItemStoreMembership.METADATA.name && targetMembership == null -> Unit
                        targetMembership != null && row.membership != targetMembership.name -> {
                            itemStoreDao.insert(DbItemStore(itemId, row.storeId, targetMembership.name, row.createdAt))
                            enqueueItemStoreUpsert(itemId, row.storeId, now)
                        }
                        targetMembership == null -> {
                            itemStoreDao.delete(itemId, row.storeId)
                            enqueueItemStoreDelete(itemId, row.storeId, now)
                        }
                    }
                }
                val knownStoreIds = currentRows.map { it.storeId }.toSet()
                for (storeId in selection.memberStoreIds - knownStoreIds) {
                    if (storeId !in groupStoreIds) {
                        itemStoreDao.insert(DbItemStore(itemId, storeId, ItemStoreMembership.MEMBER.name, now))
                        enqueueItemStoreUpsert(itemId, storeId, now)
                    }
                }
                for (storeId in selection.excludedStoreIds - knownStoreIds) {
                    itemStoreDao.insert(DbItemStore(itemId, storeId, ItemStoreMembership.EXCLUDED.name, now))
                    enqueueItemStoreUpsert(itemId, storeId, now)
                }

                // --- item_store_groups reconcile ---
                val currentGroupIds = itemStoreGroupDao.getItemStoreGroupsForItem(itemId).map { it.groupId }.toSet()
                for (groupId in selection.groupIds - currentGroupIds) {
                    itemStoreGroupDao.insert(DbItemStoreGroup(itemId, groupId, now))
                    outboxDao.enqueue(
                        DbOutboxEntry(
                            entityType = SyncContract.ENTITY_ITEM_STORE_GROUP,
                            entityId = SyncContract.itemStoreGroupEntityId(itemId, groupId),
                            operation = SyncContract.OP_UPSERT,
                            createdAt = now
                        )
                    )
                }
                for (groupId in currentGroupIds - selection.groupIds) {
                    itemStoreGroupDao.delete(itemId, groupId)
                    outboxDao.enqueue(
                        DbOutboxEntry(
                            entityType = SyncContract.ENTITY_ITEM_STORE_GROUP,
                            entityId = SyncContract.itemStoreGroupEntityId(itemId, groupId),
                            operation = SyncContract.OP_DELETE,
                            createdAt = now
                        )
                    )
                }

                syncScheduler.requestSync("item_store:selection")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun getStoreSelection(itemId: String): StoreSelection {
        return withContext(ioDispatcher) {
            val rows = itemStoreDao.getRowsForItem(itemId)
            val groupIds = itemStoreGroupDao.getItemStoreGroupsForItem(itemId).map { it.groupId }.toSet()
            val groupStoreIds = groupIds.flatMap { storeDao.getStoreIdsByGroup(it) }.toSet()
            StoreSelection(
                memberStoreIds = rows.filter { it.membership == ItemStoreMembership.MEMBER.name && it.storeId !in groupStoreIds }.map { it.storeId }.toSet(),
                excludedStoreIds = rows.filter { it.membership == ItemStoreMembership.EXCLUDED.name }.map { it.storeId }.toSet(),
                groupIds = groupIds
            )
        }
    }

    override fun observeItemStoresByHousehold(householdId: String): Flow<List<ItemStore>> {
        return itemStoreDao.observeAllForHousehold(householdId).map { list ->
            list.map { it.toDomain() }
        }
    }

    override fun observeItemStoreGroupsByHousehold(householdId: String): Flow<List<ItemStoreGroup>> {
        return itemStoreGroupDao.observeAllForHousehold(householdId).map { list ->
            list.map { it.toDomain() }
        }
    }

    private suspend fun enqueueItemStoreUpsert(itemId: String, storeId: String, now: Instant) {
        outboxDao.enqueue(
            DbOutboxEntry(
                entityType = SyncContract.ENTITY_ITEM_STORE,
                entityId = SyncContract.itemStoreEntityId(itemId, storeId),
                operation = SyncContract.OP_UPSERT,
                createdAt = now
            )
        )
    }

    private suspend fun enqueueItemStoreDelete(itemId: String, storeId: String, now: Instant) {
        outboxDao.enqueue(
            DbOutboxEntry(
                entityType = SyncContract.ENTITY_ITEM_STORE,
                entityId = SyncContract.itemStoreEntityId(itemId, storeId),
                operation = SyncContract.OP_DELETE,
                createdAt = now
            )
        )
    }
}
