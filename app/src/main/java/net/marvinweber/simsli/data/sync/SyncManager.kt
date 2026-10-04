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
import net.marvinweber.simsli.data.local.SimsliDatabase
import net.marvinweber.simsli.data.local.dao.CategoryDao
import net.marvinweber.simsli.data.local.dao.HouseholdDao
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreCategoryDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.dao.SyncStateDao
import net.marvinweber.simsli.data.local.entity.DbHousehold
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.entity.DbSyncState
import net.marvinweber.simsli.data.remote.SimsliRemoteDataSource
import net.marvinweber.simsli.data.remote.dto.CategoryDto
import net.marvinweber.simsli.data.remote.dto.FlushRequestDto
import net.marvinweber.simsli.data.remote.dto.HouseholdDto
import net.marvinweber.simsli.data.remote.dto.ItemDto
import net.marvinweber.simsli.data.remote.dto.ItemStoreDto
import net.marvinweber.simsli.data.remote.dto.ListEntryDto
import net.marvinweber.simsli.data.remote.dto.StoreCategoryDto
import net.marvinweber.simsli.data.remote.dto.StoreDto
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
 * Two-way delta sync between Room and the Simsli Go backend:
 *
 *  1. resolveHousehold — ensures the signed-in user belongs to a household on the server
 *  2. flushOutbox — pushes pending local writes in an atomic flush request
 *  3. pullDeltas — pulls server rows with updated_at newer than the local watermark
 *  4. gcExpiredEntries — removes checked-off entries past the "Recently checked" TTL
 *
 * Runs are serialized; a failed run leaves its state consistent for a retry.
 */
@Singleton
class SyncManager @Inject constructor(
    private val authRepository: AuthRepository,
    private val remoteDataSource: SimsliRemoteDataSource,
    private val db: SimsliDatabase,
    private val householdDao: HouseholdDao,
    private val storeDao: StoreDao,
    private val itemDao: ItemDao,
    private val itemStoreDao: ItemStoreDao,
    private val listEntryDao: ListEntryDao,
    private val categoryDao: CategoryDao,
    private val storeCategoryDao: StoreCategoryDao,
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

    /**
     * Wipes all local app data — every Room table, including outbox and watermarks.
     */
    suspend fun wipeLocalData() {
        mutex.withLock {
            withContext(ioDispatcher) {
                Log.d(TAG, "Wiping all local data")
                db.clearAllTables()
            }
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
            // id, keeping every local UUID reference valid.
            memberships.isEmpty() && localHousehold != null -> {
                val wasSynced = syncStateDao.get(SyncContract.KEY_INITIAL_UPLOAD_DONE) != null
                if (wasSynced) {
                    Log.d(TAG, "Household: user was removed from household, wiping local data")
                    db.clearAllTables()
                    val now = Instant.now()
                    val newHh = DbHousehold(
                        id = UUID.randomUUID().toString(),
                        name = "My household",
                        createdAt = now,
                        updatedAt = now
                    )
                    householdDao.insert(newHh)
                } else {
                    Log.d(TAG, "Household: adopting offline household ${localHousehold.id} server-side")
                    remoteDataSource.createHouseholdWithOwner(localHousehold.id, localHousehold.name)
                }
            }

            // Fresh account on a fresh device
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

            // Account already belongs to a household
            else -> {
                val remoteHouseholdId = memberships.first().householdId
                if (localHousehold == null) {
                    Log.d(TAG, "Household: pulling remote household $remoteHouseholdId (no local one)")
                    householdDao.insert(remoteDataSource.getHousehold(remoteHouseholdId).toDb())
                } else if (localHousehold.id != remoteHouseholdId) {
                    Log.d(TAG, "Household: id mismatch, switching to remote household $remoteHouseholdId")
                    db.clearAllTables()
                    householdDao.insert(remoteDataSource.getHousehold(remoteHouseholdId).toDb())
                }
            }
        }
    }

    // --- 2. Outbox (local -> remote) ----------------------------------------------

    private suspend fun enqueueInitialUploadIfNeeded() {
        if (syncStateDao.get(SyncContract.KEY_INITIAL_UPLOAD_DONE) != null ||
            syncStateDao.get(SyncContract.TABLE_ITEMS) != null
        ) {
            return
        }

        val household = householdDao.getHouseholdOnce() ?: return

        Log.d(TAG, "Initial upload: enqueuing all local rows")

        val pendingCategories = outboxDao.getPendingEntityIds(SyncContract.ENTITY_CATEGORY).toSet()
        categoryDao.getAllIncludingDeleted(household.id)
            .filter { it.id !in pendingCategories }
            .forEach { outboxDao.enqueue(upsertEntry(SyncContract.ENTITY_CATEGORY, it.id)) }

        val pendingStores = outboxDao.getPendingEntityIds(SyncContract.ENTITY_STORE).toSet()
        storeDao.getAllIncludingDeleted(household.id)
            .filter { it.id !in pendingStores }
            .forEach { outboxDao.enqueue(upsertEntry(SyncContract.ENTITY_STORE, it.id)) }

        val pendingItems = outboxDao.getPendingEntityIds(SyncContract.ENTITY_ITEM).toSet()
        itemDao.getAllIncludingDeleted(household.id)
            .filter { it.id !in pendingItems }
            .forEach { outboxDao.enqueue(upsertEntry(SyncContract.ENTITY_ITEM, it.id)) }

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

        val pendingStoreCategories = outboxDao.getPendingEntityIds(SyncContract.ENTITY_STORE_CATEGORY).toSet()
        storeCategoryDao.getAllForHousehold(household.id)
            .filter { SyncContract.storeCategoryEntityId(it.storeId, it.categoryId) !in pendingStoreCategories }
            .forEach {
                outboxDao.enqueue(
                    upsertEntry(
                        SyncContract.ENTITY_STORE_CATEGORY,
                        SyncContract.storeCategoryEntityId(it.storeId, it.categoryId)
                    )
                )
            }

        syncStateDao.upsert(DbSyncState(SyncContract.KEY_INITIAL_UPLOAD_DONE, Instant.now()))
    }

    private fun entityTypeDependencyRank(entityType: String): Int = when (entityType) {
        SyncContract.ENTITY_HOUSEHOLD -> 0
        SyncContract.ENTITY_STORE -> 1
        SyncContract.ENTITY_CATEGORY -> 1
        SyncContract.ENTITY_ITEM -> 2
        SyncContract.ENTITY_LIST_ENTRY -> 3
        SyncContract.ENTITY_ITEM_STORE -> 3
        SyncContract.ENTITY_STORE_CATEGORY -> 3
        else -> 4
    }

    private suspend fun flushOutbox(userId: String) {
        val entries = outboxDao.getAll().sortedWith(
            compareBy(
                { entityTypeDependencyRank(it.entityType) },
                { it.id }
            )
        )
        if (entries.isEmpty()) return
        Log.d(TAG, "Outbox: flushing ${entries.size} entries")

        val household = householdDao.getHouseholdOnce() ?: return

        val stores = mutableListOf<StoreDto>()
        val categories = mutableListOf<CategoryDto>()
        val storeCategories = mutableListOf<StoreCategoryDto>()
        val items = mutableListOf<ItemDto>()
        val itemStores = mutableListOf<ItemStoreDto>()
        val listEntries = mutableListOf<ListEntryDto>()
        val deletedStores = mutableListOf<String>()
        val deletedCategories = mutableListOf<String>()
        val deletedItems = mutableListOf<String>()
        val deletedEntries = mutableListOf<String>()
        var hhDto: HouseholdDto? = null

        for (entry in entries) {
            when (entry.entityType) {
                SyncContract.ENTITY_HOUSEHOLD -> {
                    val local = householdDao.getHouseholdByIdIncludingDeleted(entry.entityId)
                    if (local != null) hhDto = local.toDto()
                }
                SyncContract.ENTITY_STORE -> {
                    val local = storeDao.getByIdIncludingDeleted(entry.entityId)
                    if (local != null) stores.add(local.toDto())
                }
                SyncContract.ENTITY_CATEGORY -> {
                    val local = categoryDao.getByIdIncludingDeleted(entry.entityId)
                    if (local != null) categories.add(local.toDto())
                }
                SyncContract.ENTITY_ITEM -> {
                    val local = itemDao.getByIdIncludingDeleted(entry.entityId)
                    if (local != null) items.add(local.toDto())
                }
                SyncContract.ENTITY_LIST_ENTRY -> {
                    val local = listEntryDao.getListEntryOnce(entry.entityId)
                    if (local != null) {
                        listEntries.add(local.toDto(fallbackCreatedBy = userId))
                    } else {
                        deletedEntries.add(entry.entityId)
                    }
                }
                SyncContract.ENTITY_ITEM_STORE -> {
                    val (itemId, storeId) = SyncContract.parseItemStoreEntityId(entry.entityId) ?: continue
                    val local = itemStoreDao.getOnce(itemId, storeId)
                    if (local != null) {
                        itemStores.add(local.toDto())
                    }
                }
                SyncContract.ENTITY_STORE_CATEGORY -> {
                    val (storeId, categoryId) = SyncContract.parseStoreCategoryEntityId(entry.entityId) ?: continue
                    val local = storeCategoryDao.getOnce(storeId, categoryId)
                    if (local != null) {
                        storeCategories.add(local.toDto())
                    }
                }
            }
        }

        val req = FlushRequestDto(
            householdId = household.id,
            household = hhDto,
            stores = stores.ifEmpty { null },
            categories = categories.ifEmpty { null },
            storeCategories = storeCategories.ifEmpty { null },
            items = items.ifEmpty { null },
            itemStores = itemStores.ifEmpty { null },
            listEntries = listEntries.ifEmpty { null },
            deletedStores = deletedStores.ifEmpty { null },
            deletedCategories = deletedCategories.ifEmpty { null },
            deletedItems = deletedItems.ifEmpty { null },
            deletedEntries = deletedEntries.ifEmpty { null }
        )

        try {
            remoteDataSource.flush(req)
            for (entry in entries) {
                outboxDao.delete(entry.id)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            for (entry in entries) {
                outboxDao.incrementAttempts(entry.id)
            }
            throw IllegalStateException("Outbox flush failed: ${e.message}", e)
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

        val watermarkKeys = listOf(
            SyncContract.TABLE_HOUSEHOLDS,
            SyncContract.TABLE_STORES,
            SyncContract.TABLE_CATEGORIES,
            SyncContract.TABLE_ITEMS,
            SyncContract.TABLE_LIST_ENTRIES
        )
        val since = watermarkKeys.mapNotNull { syncStateDao.get(it)?.lastSyncedAt }.minOrNull() ?: Instant.EPOCH

        Log.d(TAG, "Pulling deltas since $since")
        val deltas = remoteDataSource.getDeltas(household.id, since)

        // 1. Household
        deltas.household?.let {
            householdDao.insert(it.toDb())
        }

        // 2. Stores
        deltas.stores?.map { it.toDb() }?.let { rows ->
            if (rows.isNotEmpty()) Log.d(TAG, "Pull: ${rows.size} stores")
            storeDao.insertAll(rows)
        }

        // 3. Categories
        deltas.categories?.map { it.toDb() }?.let { rows ->
            if (rows.isNotEmpty()) Log.d(TAG, "Pull: ${rows.size} categories")
            categoryDao.insertAll(rows)
        }

        // 4. Items
        deltas.items?.map { it.toDb() }?.let { rows ->
            if (rows.isNotEmpty()) Log.d(TAG, "Pull: ${rows.size} items")
            itemDao.insertAll(rows)
        }

        // 5. List entries
        deltas.listEntries?.map { it.toDb() }?.let { rows ->
            if (rows.isNotEmpty()) Log.d(TAG, "Pull: ${rows.size} list_entries")
            listEntryDao.insertAll(rows)
        }

        // 6. Full reconcile item_stores
        deltas.itemStores?.let { remoteRows ->
            val localKeys = itemStoreDao.getAllForHousehold(household.id)
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

        // 7. Full reconcile store_categories
        deltas.storeCategories?.let { remoteRows ->
            val localKeys = storeCategoryDao.getAllForHousehold(household.id)
                .map { it.storeId to it.categoryId }
                .toSet()
            storeCategoryDao.insertAll(remoteRows.map { it.toDb() })
            val remoteKeys = remoteRows.map { it.storeId to it.categoryId }.toSet()
            val removed = localKeys - remoteKeys
            if (remoteRows.isNotEmpty() || removed.isNotEmpty()) {
                Log.d(TAG, "Reconcile store_categories: ${remoteRows.size} remote, $removed removed locally")
            }
            removed.forEach { (storeId, categoryId) ->
                storeCategoryDao.delete(storeId, categoryId)
            }
        }

        val serverTime = try {
            Instant.parse(deltas.serverTime)
        } catch (e: Exception) {
            Instant.now()
        }
        watermarkKeys.forEach { key ->
            syncStateDao.upsert(DbSyncState(key, serverTime))
        }
    }

    // --- 4. Garbage collection ------------------------------------------------------

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
    }
}
