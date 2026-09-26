package net.marvinweber.simsli.data.sync

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import net.marvinweber.simsli.data.local.dao.HouseholdDao
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.dao.SyncStateDao
import net.marvinweber.simsli.data.local.entity.DbHousehold
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.entity.DbSyncState
import net.marvinweber.simsli.data.remote.SupabaseRemoteDataSource
import net.marvinweber.simsli.data.remote.mapper.toDb
import net.marvinweber.simsli.data.remote.mapper.toDto
import net.marvinweber.simsli.data.repository.AuthRepository
import net.marvinweber.simsli.data.repository.AuthState
import net.marvinweber.simsli.di.ApplicationScope
import net.marvinweber.simsli.di.IoDispatcher
import net.marvinweber.simsli.domain.model.ItemType
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Two-way delta sync between Room and Supabase:
 *
 *  1. resolveHousehold — makes sure the signed-in user belongs to a household the
 *     local data can live under (adopting the offline household via RPC on first sync)
 *  2. flushOutbox — pushes pending local writes (current row state, last-write-wins)
 *  3. pullDeltas — pulls server rows with updated_at newer than the local watermark
 *  4. gcExpiredEntries — removes checked-off entries past the "Recently checked" TTL
 *
 * Runs are serialized; a failed run leaves its state consistent for a retry.
 * Triggers: sign-in / session restore, debounced [SyncScheduler] requests (local
 * writes + realtime events), and the manual sync button.
 */
@Singleton
class SyncManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val remoteDataSource: SupabaseRemoteDataSource,
    private val householdDao: HouseholdDao,
    private val storeDao: StoreDao,
    private val itemDao: ItemDao,
    private val itemStoreDao: ItemStoreDao,
    private val listEntryDao: ListEntryDao,
    private val syncStateDao: SyncStateDao,
    private val outboxDao: OutboxDao,
    private val syncScheduler: SyncScheduler,
    @ApplicationScope private val externalScope: CoroutineScope,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) {
    private val mutex = Mutex()

    /** Number of sync runs currently executing or waiting on the mutex. */
    private val pendingSyncs = MutableStateFlow(0)

    /** True while a sync is running (or queued behind one) — drives the UI progress indicator. */
    val isSyncing: StateFlow<Boolean> = pendingSyncs
        .map { it > 0 }
        .stateIn(externalScope, SharingStarted.Eagerly, false)

    /** Watches auth state and syncs after every sign-in (including session restore at app start). */
    fun start() {
        externalScope.launch {
            authRepository.authState.collect { state ->
                Log.d(TAG, "Auth state: $state")
                if (state is AuthState.SignedIn) {
                    syncNow("auth")
                }
            }
        }
        externalScope.launch {
            // Debounced: a burst of writes / realtime events collapses into one sync.
            syncScheduler.requests.collectLatest {
                delay(SyncScheduler.DEBOUNCE_MS)
                syncNow("debounced")
            }
        }
    }

    suspend fun syncNow(trigger: String = "manual"): Result<Unit> {
        pendingSyncs.update { it + 1 }
        val startedAt = System.currentTimeMillis()
        try {
            return mutex.withLock {
                withContext(ioDispatcher) {
                    try {
                        val userId = authRepository.currentUserId()
                            ?: return@withContext Result.success(Unit) // signed out — nothing to sync

                        Log.d(TAG, "Sync ($trigger) started")
                        resolveHousehold(userId)
                        enqueueInitialUploadIfNeeded()
                        flushOutbox(userId)
                        pullDeltas()
                        gcExpiredEntries()
                        Result.success(Unit).also {
                            Log.d(TAG, "Sync ($trigger) finished in ${System.currentTimeMillis() - startedAt}ms")
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Sync ($trigger) failed after ${System.currentTimeMillis() - startedAt}ms: ${e.message}")
                        Result.failure(e)
                    }
                }
            }
        } finally {
            pendingSyncs.update { (it - 1).coerceAtLeast(0) }
        }
    }

    private companion object {
        const val TAG = "SimsliSync"
    }

    // --- 1. Household resolution ------------------------------------------------

    private suspend fun resolveHousehold(userId: String) {
        val localHousehold = householdDao.getHouseholdOnce()
        val memberships = remoteDataSource.getMyMemberships(userId)

        when {
            // First sync of an offline household: the server adopts the local household
            // id (create_household_with_owner), keeping every local UUID reference valid.
            memberships.isEmpty() && localHousehold != null -> {
                Log.d(TAG, "Household: adopting offline household ${localHousehold.id} server-side")
                remoteDataSource.createHouseholdWithOwner(localHousehold.id, localHousehold.name)
            }

            // Fresh account on a fresh device: one shared id on both sides.
            memberships.isEmpty() && localHousehold == null -> {
                Log.d(TAG, "Household: fresh account + fresh device, creating household")
                val now = Instant.now()
                val household = DbHousehold(
                    id = UUID.randomUUID().toString(),
                    name = "My household",
                    createdAt = now,
                    updatedAt = now
                )
                householdDao.insert(household)
                remoteDataSource.createHouseholdWithOwner(household.id, household.name)
            }

            // Account already belongs to a household (second device / joined via invite).
            else -> {
                val remoteHouseholdId = memberships.first().householdId
                if (localHousehold == null) {
                    Log.d(TAG, "Household: pulling remote household $remoteHouseholdId (no local one)")
                    householdDao.insert(remoteDataSource.getHousehold(remoteHouseholdId).toDb())
                } else if (localHousehold.id != remoteHouseholdId) {
                    // v1 is single-household: remap local data onto the server household
                    // so nothing is lost. True multi-household is a later milestone.
                    Log.d(TAG, "Household: id mismatch, adopting remote household $remoteHouseholdId")
                    adoptRemoteHousehold(localHousehold.id, remoteHouseholdId)
                    // Bring in the authoritative household row — without it, pullDeltas
                    // has no non-deleted household to work with in this same run.
                    householdDao.insert(remoteDataSource.getHousehold(remoteHouseholdId).toDb())
                }
            }
        }
    }

    private suspend fun adoptRemoteHousehold(oldHouseholdId: String, newHouseholdId: String) {
        val now = Instant.now()
        itemDao.reassignHousehold(oldHouseholdId, newHouseholdId, now)
        storeDao.reassignHousehold(oldHouseholdId, newHouseholdId, now)
        listEntryDao.reassignHousehold(oldHouseholdId, newHouseholdId, now)
        householdDao.delete(oldHouseholdId, now)
        // The remapped rows have no outbox entries; enqueueInitialUploadIfNeeded()
        // picks them up because the watermark is still unset during this first sync.
    }

    // --- 2. Outbox (local -> remote) ----------------------------------------------

    /**
     * Data written before the outbox existed is only discoverable here: while no
     * watermark exists (never pulled), enqueue every local row for upload.
     */
    private suspend fun enqueueInitialUploadIfNeeded() {
        if (syncStateDao.get(SyncContract.TABLE_ITEMS) != null) return

        val household = householdDao.getHouseholdOnce() ?: return

        Log.d(TAG, "Initial upload: no items watermark yet, enqueuing all local rows")

        val pendingItems = outboxDao.getPendingEntityIds(SyncContract.ENTITY_ITEM).toSet()
        itemDao.getAllIncludingDeleted(household.id)
            .filter { it.id !in pendingItems }
            .forEach { outboxDao.enqueue(upsertEntry(SyncContract.ENTITY_ITEM, it.id)) }

        val pendingStores = outboxDao.getPendingEntityIds(SyncContract.ENTITY_STORE).toSet()
        storeDao.getAllIncludingDeleted(household.id)
            .filter { it.id !in pendingStores }
            .forEach { outboxDao.enqueue(upsertEntry(SyncContract.ENTITY_STORE, it.id)) }

        val pendingEntries = outboxDao.getPendingEntityIds(SyncContract.ENTITY_LIST_ENTRY).toSet()
        listEntryDao.getAllByHousehold(household.id)
            .filter { it.id !in pendingEntries }
            .forEach { outboxDao.enqueue(upsertEntry(SyncContract.ENTITY_LIST_ENTRY, it.id)) }

        val pendingItemStores = outboxDao.getPendingEntityIds(SyncContract.ENTITY_ITEM_STORE).toSet()
        itemStoreDao.getAllForHousehold(household.id)
            .filter { SyncContract.itemStoreEntityId(it.itemId, it.storeId) !in pendingItemStores }
            .forEach {
                outboxDao.enqueue(
                    upsertEntry(
                        SyncContract.ENTITY_ITEM_STORE,
                        SyncContract.itemStoreEntityId(it.itemId, it.storeId)
                    )
                )
            }
    }

    /**
     * Pushes the outbox in insertion order (parents before children) and stops at the
     * first failure — later entries can depend on earlier rows server-side.
     */
    private suspend fun flushOutbox(userId: String) {
        val entries = outboxDao.getAll()
        if (entries.isNotEmpty()) {
            Log.d(TAG, "Outbox: flushing ${entries.size} entries")
        }
        for (entry in entries) {
            Log.d(TAG, "Outbox: pushing ${entry.entityType}/${entry.entityId} (${entry.operation})")
            try {
                when (entry.entityType) {
                    SyncContract.ENTITY_HOUSEHOLD -> {
                        val local = householdDao.getHouseholdByIdIncludingDeleted(entry.entityId)
                        if (local != null) remoteDataSource.upsertHousehold(local.toDto())
                    }

                    SyncContract.ENTITY_ITEM -> {
                        val local = itemDao.getByIdIncludingDeleted(entry.entityId)
                        if (local != null) remoteDataSource.upsertItem(local.toDto())
                    }

                    SyncContract.ENTITY_STORE -> {
                        val local = storeDao.getByIdIncludingDeleted(entry.entityId)
                        if (local != null) remoteDataSource.upsertStore(local.toDto())
                    }

                    SyncContract.ENTITY_LIST_ENTRY -> {
                        val local = listEntryDao.getListEntryOnce(entry.entityId)
                        if (local != null) {
                            remoteDataSource.upsertListEntry(local.toDto(fallbackCreatedBy = userId))
                        } else {
                            // Deleted locally before it could be pushed — delete server-side too.
                            remoteDataSource.deleteListEntry(entry.entityId)
                        }
                    }

                    SyncContract.ENTITY_ITEM_STORE -> {
                        val (itemId, storeId) = SyncContract.parseItemStoreEntityId(entry.entityId)
                            ?: error("Malformed item_store entity id: ${entry.entityId}")
                        val local = itemStoreDao.getOnce(itemId, storeId)
                        if (local != null) {
                            remoteDataSource.upsertItemStores(listOf(local.toDto()))
                        } else {
                            remoteDataSource.deleteItemStore(itemId, storeId)
                        }
                    }
                }
                outboxDao.delete(entry.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                outboxDao.incrementAttempts(entry.id)
                throw IllegalStateException(
                    "Outbox flush failed at ${entry.entityType}/${entry.entityId}: ${e.message}",
                    e
                )
            }
        }
    }

    private fun upsertEntry(entityType: String, entityId: String) = DbOutboxEntry(
        entityType = entityType,
        entityId = entityId,
        operation = SyncContract.OP_UPSERT,
        createdAt = Instant.now()
    )

    // --- 3. Delta pull (remote -> local) -------------------------------------------

    private suspend fun pullDeltas() {
        val household = householdDao.getHouseholdOnce() ?: return

        pullHousehold(household.id)
        pullStores(household.id)
        pullItems(household.id)
        pullListEntries(household.id)
        reconcileItemStores(household.id)
    }

    private suspend fun pullHousehold(householdId: String) {
        val since = syncStateDao.get(SyncContract.TABLE_HOUSEHOLDS)?.lastSyncedAt ?: Instant.EPOCH
        val rows = remoteDataSource.fetchHouseholdsSince(householdId, since).map { it.toDb() }
        if (rows.isNotEmpty()) {
            Log.d(TAG, "Pull: ${rows.size} households (since=$since)")
        }
        rows.forEach { householdDao.insert(it) }   // insert uses OnConflictStrategy.REPLACE
        rows.maxOfOrNull { it.updatedAt }?.let {
            syncStateDao.upsert(DbSyncState(SyncContract.TABLE_HOUSEHOLDS, it))
        }
    }

    private suspend fun pullStores(householdId: String) {
        val since = syncStateDao.get(SyncContract.TABLE_STORES)?.lastSyncedAt ?: Instant.EPOCH
        val rows = remoteDataSource.fetchStoresSince(householdId, since).map { it.toDb() }
        if (rows.isNotEmpty()) {
            Log.d(TAG, "Pull: ${rows.size} stores (since=$since)")
        }
        storeDao.insertAll(rows)
        rows.maxOfOrNull { it.updatedAt }?.let {
            syncStateDao.upsert(DbSyncState(SyncContract.TABLE_STORES, it))
        }
    }

    private suspend fun pullItems(householdId: String) {
        val since = syncStateDao.get(SyncContract.TABLE_ITEMS)?.lastSyncedAt ?: Instant.EPOCH
        val rows = remoteDataSource.fetchItemsSince(householdId, since).map { it.toDb() }
        if (rows.isNotEmpty()) {
            Log.d(TAG, "Pull: ${rows.size} items (since=$since)")
        }
        itemDao.insertAll(rows)
        rows.maxOfOrNull { it.updatedAt }?.let {
            syncStateDao.upsert(DbSyncState(SyncContract.TABLE_ITEMS, it))
        }
    }

    private suspend fun pullListEntries(householdId: String) {
        val since = syncStateDao.get(SyncContract.TABLE_LIST_ENTRIES)?.lastSyncedAt ?: Instant.EPOCH
        val rows = remoteDataSource.fetchListEntriesSince(householdId, since).map { it.toDb() }
        if (rows.isNotEmpty()) {
            Log.d(TAG, "Pull: ${rows.size} list_entries (since=$since)")
        }
        listEntryDao.insertAll(rows)
        rows.maxOfOrNull { it.updatedAt }?.let {
            syncStateDao.upsert(DbSyncState(SyncContract.TABLE_LIST_ENTRIES, it))
        }
    }

    /** item_stores has no updated_at server-side, so syncs reconcile the full assignment set. */
    private suspend fun reconcileItemStores(householdId: String) {
        val itemIds = itemDao.getAllIncludingDeleted(householdId).map { it.id }
        val remoteRows = remoteDataSource.fetchItemStores(itemIds)
        val localKeys = itemStoreDao.getAllForHousehold(householdId)
            .map { it.itemId to it.storeId }
            .toSet()

        itemStoreDao.insertAll(remoteRows.map { it.toDb() })

        val remoteKeys = remoteRows.map { it.itemId to it.storeId }.toSet()
        val removed = localKeys - remoteKeys
        if (remoteRows.isNotEmpty() || removed.isNotEmpty()) {
            Log.d(TAG, "Reconcile item_stores: ${remoteRows.size} remote, $removed removed locally")
        }
        removed.forEach { (itemId, storeId) ->
            itemStoreDao.delete(itemId, storeId)
        }
    }

    // --- 4. Garbage collection ------------------------------------------------------

    /**
     * Removes checked-off entries past the TTL — locally, and (idempotently) on the
     * server via the outbox. Every device runs the same deterministic rule, so all
     * of them converge on the same deletions without coordination.
     * ONE_TIME items are retired together with their last entry.
     */
    private suspend fun gcExpiredEntries() {
        val cutoff = Instant.now().minus(SyncContract.RECENTLY_CHECKED_TTL)
        val expired = listEntryDao.getExpiredDoneEntries(cutoff)
        if (expired.isEmpty()) return

        Log.d(TAG, "GC: removing ${expired.size} expired entries (cutoff=$cutoff)")

        val now = Instant.now()
        for (entry in expired) {
            listEntryDao.delete(entry.id)
            outboxDao.enqueue(
                DbOutboxEntry(
                    entityType = SyncContract.ENTITY_LIST_ENTRY,
                    entityId = entry.id,
                    operation = SyncContract.OP_DELETE,
                    createdAt = now
                )
            )

            val item = itemDao.getByIdIncludingDeleted(entry.itemId)
            if (item != null && item.deletedAt == null && item.type == ItemType.ONE_TIME) {
                itemDao.delete(item.id, now)   // soft delete — pushed as row state below
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
    }
}
