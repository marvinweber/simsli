package net.marvinweber.simsli.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.local.mapper.toDb
import net.marvinweber.simsli.data.repository.StoreRepository
import net.marvinweber.simsli.data.sync.SyncContract
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.Store
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StoreRepositoryImpl @Inject constructor(
    private val storeDao: StoreDao,
    private val outboxDao: OutboxDao,
    private val syncScheduler: SyncScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : StoreRepository {

    override fun getStoresByHousehold(householdId: String): Flow<List<Store>> {
        return storeDao.getStoresByHousehold(householdId).map { stores ->
            stores.map { it.toDomain() }
        }
    }

    override suspend fun getMaxStoreSortOrder(householdId: String): Float {
        return withContext(ioDispatcher) {
            storeDao.getMaxSortOrder(householdId) ?: 0f
        }
    }

    override suspend fun createStore(store: Store): Result<Store> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                val newStore = store.copy(
                    id = if (store.id.isEmpty()) UUID.randomUUID().toString() else store.id,
                    createdAt = if (store.createdAt == Instant.EPOCH) now else store.createdAt,
                    updatedAt = now
                )
                storeDao.insert(newStore.toDb())
                enqueueUpsert(newStore.id)
                Result.success(newStore)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateStore(store: Store): Result<Store> {
        return withContext(ioDispatcher) {
            try {
                val updatedStore = store.copy(updatedAt = Instant.now())
                storeDao.insert(updatedStore.toDb())
                enqueueUpsert(updatedStore.id)
                Result.success(updatedStore)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteStore(storeId: String): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                storeDao.delete(storeId, Instant.now())
                // Local deletes are soft — push the row so deleted_at propagates.
                enqueueUpsert(storeId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateStoreSortOrder(storeId: String, sortOrder: Float): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                storeDao.updateSortOrder(storeId, sortOrder, Instant.now())
                enqueueUpsert(storeId)
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun enqueueUpsert(storeId: String) {
        outboxDao.enqueue(
            DbOutboxEntry(
                entityType = SyncContract.ENTITY_STORE,
                entityId = storeId,
                operation = SyncContract.OP_UPSERT,
                createdAt = Instant.now()
            )
        )
        syncScheduler.requestSync("store")
    }
}
