package net.marvinweber.simsli.data.repository.impl

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.local.mapper.toDb
import net.marvinweber.simsli.data.repository.ListEntryRepository
import net.marvinweber.simsli.data.sync.SyncContract
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.domain.model.ListEntry
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ListEntryRepositoryImpl @Inject constructor(
    private val listEntryDao: ListEntryDao,
    private val itemDao: ItemDao,
    private val outboxDao: OutboxDao,
    private val syncScheduler: SyncScheduler,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ListEntryRepository {

    override fun getListEntriesByHousehold(householdId: String): Flow<List<ListEntry>> {
        return listEntryDao.getListEntriesByHousehold(householdId).map { entries ->
            entries.map { it.toDomain() }
        }
    }

    override fun getListEntriesByHouseholdAndStore(householdId: String, storeId: String): Flow<List<ListEntry>> {
        return listEntryDao.getListEntriesByHouseholdAndStore(householdId, storeId).map { entries ->
            entries.map { it.toDomain() }
        }
    }

    override fun getListEntriesByHouseholdWithoutStore(householdId: String): Flow<List<ListEntry>> {
        return listEntryDao.getListEntriesByHouseholdWithoutStore(householdId).map { entries ->
            entries.map { it.toDomain() }
        }
    }

    override suspend fun createListEntry(listEntry: ListEntry): Result<ListEntry> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                val newEntry = listEntry.copy(
                    id = if (listEntry.id.isEmpty()) UUID.randomUUID().toString() else listEntry.id,
                    createdAt = if (listEntry.createdAt == Instant.EPOCH) now else listEntry.createdAt,
                    updatedAt = now
                )
                listEntryDao.insert(newEntry.toDb())
                enqueue(newEntry.id, SyncContract.OP_UPSERT)
                syncScheduler.requestSync("list_entry:create")
                Result.success(newEntry)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateListEntry(listEntry: ListEntry): Result<ListEntry> {
        return withContext(ioDispatcher) {
            try {
                val updatedEntry = listEntry.copy(updatedAt = Instant.now())
                listEntryDao.insert(updatedEntry.toDb())
                enqueue(updatedEntry.id, SyncContract.OP_UPSERT)
                syncScheduler.requestSync("list_entry:update")
                Result.success(updatedEntry)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun deleteListEntry(listEntryId: String): Result<Unit> =
        deleteListEntries(listOf(listEntryId))

    override suspend fun deleteListEntries(listEntryIds: List<String>): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                if (listEntryIds.isEmpty()) return@withContext Result.success(Unit)
                val now = Instant.now()
                for (id in listEntryIds) {
                    val entry = listEntryDao.getListEntryOnce(id) ?: continue
                    listEntryDao.delete(id)
                    enqueue(id, SyncContract.OP_DELETE)

                    val item = itemDao.getByIdIncludingDeleted(entry.itemId)
                    if (item != null && item.deletedAt == null && item.type == ItemType.ONE_TIME) {
                        itemDao.delete(item.id, now)
                        outboxDao.enqueue(
                            DbOutboxEntry(
                                entityType = SyncContract.ENTITY_ITEM,
                                entityId = item.id,
                                operation = SyncContract.OP_UPSERT,
                                createdAt = now
                            )
                        )
                    }
                }
                syncScheduler.requestSync("list_entry:delete_entries")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateListEntryDoneStatus(listEntryId: String, done: Boolean): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                listEntryDao.updateDoneStatus(
                    id = listEntryId,
                    done = done,
                    completedAt = if (done) now else null,
                    updatedAt = now
                )
                enqueue(listEntryId, SyncContract.OP_UPSERT)
                syncScheduler.requestSync("list_entry:done_status")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun addToList(
        householdId: String,
        itemId: String,
        quantity: Double?,
        unit: String?,
        comment: String?
    ): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                val now = Instant.now()
                val existing = listEntryDao.getEntryByHouseholdAndItemOnce(householdId, itemId)
                if (existing != null) {
                    // Unique (household, item): re-adding a recently checked entry just
                    // moves it back to the active list; an active entry stays untouched.
                    // Details entered with the re-add are applied; blank ones keep the
                    // entry's previous values.
                    if (existing.done) {
                        listEntryDao.insert(
                            existing.copy(
                                done = false,
                                completedAt = null,
                                quantity = quantity ?: existing.quantity,
                                unit = unit?.trim()?.ifBlank { null } ?: existing.unit,
                                comment = comment?.trim()?.ifBlank { null } ?: existing.comment,
                                updatedAt = now
                            )
                        )
                        enqueue(existing.id, SyncContract.OP_UPSERT)
                    }
                } else {
                    val entry = ListEntry(
                        id = UUID.randomUUID().toString(),
                        householdId = householdId,
                        itemId = itemId,
                        quantity = quantity,
                        unit = unit?.trim()?.ifBlank { null },
                        comment = comment?.trim()?.ifBlank { null },
                        done = false,
                        completedAt = null,
                        createdAt = now,
                        updatedAt = now
                    )
                    listEntryDao.insert(entry.toDb())
                    enqueue(entry.id, SyncContract.OP_UPSERT)
                }
                syncScheduler.requestSync("list_entry:add_to_list")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun updateEntryDetails(
        listEntryId: String,
        quantity: Double?,
        unit: String?,
        comment: String?
    ): Result<Unit> {
        return withContext(ioDispatcher) {
            try {
                listEntryDao.updateDetails(
                    id = listEntryId,
                    quantity = quantity,
                    unit = unit?.trim()?.ifBlank { null },
                    comment = comment?.trim()?.ifBlank { null },
                    updatedAt = Instant.now()
                )
                enqueue(listEntryId, SyncContract.OP_UPSERT)
                syncScheduler.requestSync("list_entry:update_details")
                Result.success(Unit)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private suspend fun enqueue(listEntryId: String, operation: String) {
        outboxDao.enqueue(
            DbOutboxEntry(
                entityType = SyncContract.ENTITY_LIST_ENTRY,
                entityId = listEntryId,
                operation = operation,
                createdAt = Instant.now()
            )
        )
    }
}
