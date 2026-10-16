package net.marvinweber.simsli

import kotlinx.coroutines.runBlocking
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ItemStoreGroupDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.data.local.entity.DbItemStoreGroup
import net.marvinweber.simsli.data.repository.impl.ItemStoreRepositoryImpl
import net.marvinweber.simsli.data.sync.SyncDiagnostics
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.domain.model.ItemStoreMembership
import net.marvinweber.simsli.domain.model.StoreSelection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class ItemStoreRepositoryTest {

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> dummyProxy(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, _, _ -> null } as T
    }

    @Test
    fun `selecting a group downgrades existing member stores to metadata`() = runBlocking {
        val existingItemStores = mutableListOf(
            DbItemStore("item1", "store_rewe", ItemStoreMembership.MEMBER.name, createdAt = Instant.now()),
            DbItemStore("item1", "store_aldi", ItemStoreMembership.MEMBER.name, createdAt = Instant.now())
        )
        val existingItemStoreGroups = mutableListOf<DbItemStoreGroup>()

        val itemStoreDaoProxy = Proxy.newProxyInstance(
            ItemStoreDao::class.java.classLoader,
            arrayOf(ItemStoreDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getRowsForItem" -> existingItemStores.filter { it.itemId == args[0] }
                "insert" -> {
                    val entity = args[0] as DbItemStore
                    existingItemStores.removeAll { it.itemId == entity.itemId && it.storeId == entity.storeId }
                    existingItemStores.add(entity)
                    Unit
                }
                "delete" -> {
                    val itemId = args[0] as String
                    val storeId = args[1] as String
                    existingItemStores.removeAll { it.itemId == itemId && it.storeId == storeId }
                    Unit
                }
                else -> null
            }
        } as ItemStoreDao

        val itemStoreGroupDaoProxy = Proxy.newProxyInstance(
            ItemStoreGroupDao::class.java.classLoader,
            arrayOf(ItemStoreGroupDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getItemStoreGroupsForItem" -> existingItemStoreGroups.filter { it.itemId == args[0] }
                "insert" -> {
                    val entity = args[0] as DbItemStoreGroup
                    existingItemStoreGroups.removeAll { it.itemId == entity.itemId && it.groupId == entity.groupId }
                    existingItemStoreGroups.add(entity)
                    Unit
                }
                "delete" -> {
                    val itemId = args[0] as String
                    val groupId = args[1] as String
                    existingItemStoreGroups.removeAll { it.itemId == itemId && it.groupId == groupId }
                    Unit
                }
                else -> null
            }
        } as ItemStoreGroupDao

        val storeDaoProxy = Proxy.newProxyInstance(
            StoreDao::class.java.classLoader,
            arrayOf(StoreDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getStoreIdsByGroup" -> {
                    val groupId = args[0] as String
                    if (groupId == "group_supermarkt") listOf("store_rewe", "store_aldi") else emptyList()
                }
                else -> null
            }
        } as StoreDao

        val outboxDaoProxy = Proxy.newProxyInstance(
            OutboxDao::class.java.classLoader,
            arrayOf(OutboxDao::class.java)
        ) { _, _, _ -> Unit } as OutboxDao

        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        val dummyDiag = allocateInstance.invoke(unsafe, SyncDiagnostics::class.java) as SyncDiagnostics
        val dummySyncScheduler = object : SyncScheduler(dummyDiag) {
            override fun requestSync(reason: String) {
                // no-op in tests
            }
        }

        val repository = ItemStoreRepositoryImpl(
            itemStoreDao = itemStoreDaoProxy,
            itemStoreGroupDao = itemStoreGroupDaoProxy,
            storeDao = storeDaoProxy,
            outboxDao = outboxDaoProxy,
            syncScheduler = dummySyncScheduler,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )

        // User selects group_supermarkt without explicit member stores
        val selection = StoreSelection(
            memberStoreIds = emptySet(),
            excludedStoreIds = emptySet(),
            groupIds = setOf("group_supermarkt")
        )

        val result = repository.saveStoreSelection("item1", selection)
        result.getOrThrow()

        // Both stores should still exist in item_stores, but downgraded to METADATA
        val reweRow = existingItemStores.find { it.storeId == "store_rewe" }
        val aldiRow = existingItemStores.find { it.storeId == "store_aldi" }
        assertEquals(ItemStoreMembership.METADATA.name, reweRow?.membership)
        assertEquals(ItemStoreMembership.METADATA.name, aldiRow?.membership)

        // getStoreSelection should only return group_supermarkt, not the stores
        val loadedSelection = repository.getStoreSelection("item1")
        assertEquals(setOf("group_supermarkt"), loadedSelection.groupIds)
        assertEquals(emptySet<String>(), loadedSelection.memberStoreIds)
        assertEquals(emptySet<String>(), loadedSelection.excludedStoreIds)
    }

    private data class RepositoryFixture(
        val repository: ItemStoreRepositoryImpl,
        val itemStores: MutableList<DbItemStore>,
        val itemStoreGroups: MutableList<DbItemStoreGroup>
    )

    private fun createFixture(
        initialStores: List<DbItemStore> = emptyList(),
        initialGroups: List<DbItemStoreGroup> = emptyList(),
        groupStoresMap: Map<String, List<String>> = mapOf("group_supermarkt" to listOf("store_rewe", "store_aldi"))
    ): RepositoryFixture {
        val itemStores = initialStores.toMutableList()
        val itemStoreGroups = initialGroups.toMutableList()

        val itemStoreDaoProxy = Proxy.newProxyInstance(
            ItemStoreDao::class.java.classLoader,
            arrayOf(ItemStoreDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getRowsForItem" -> itemStores.filter { it.itemId == args[0] }
                "insert" -> {
                    val entity = args[0] as DbItemStore
                    itemStores.removeAll { it.itemId == entity.itemId && it.storeId == entity.storeId }
                    itemStores.add(entity)
                    Unit
                }
                "delete" -> {
                    val itemId = args[0] as String
                    val storeId = args[1] as String
                    itemStores.removeAll { it.itemId == itemId && it.storeId == storeId }
                    Unit
                }
                else -> null
            }
        } as ItemStoreDao

        val itemStoreGroupDaoProxy = Proxy.newProxyInstance(
            ItemStoreGroupDao::class.java.classLoader,
            arrayOf(ItemStoreGroupDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getItemStoreGroupsForItem" -> itemStoreGroups.filter { it.itemId == args[0] }
                "insert" -> {
                    val entity = args[0] as DbItemStoreGroup
                    itemStoreGroups.removeAll { it.itemId == entity.itemId && it.groupId == entity.groupId }
                    itemStoreGroups.add(entity)
                    Unit
                }
                "delete" -> {
                    val itemId = args[0] as String
                    val groupId = args[1] as String
                    itemStoreGroups.removeAll { it.itemId == itemId && it.groupId == groupId }
                    Unit
                }
                else -> null
            }
        } as ItemStoreGroupDao

        val storeDaoProxy = Proxy.newProxyInstance(
            StoreDao::class.java.classLoader,
            arrayOf(StoreDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getStoreIdsByGroup" -> {
                    val groupId = args[0] as String
                    groupStoresMap[groupId] ?: emptyList()
                }
                else -> null
            }
        } as StoreDao

        val outboxDaoProxy = Proxy.newProxyInstance(
            OutboxDao::class.java.classLoader,
            arrayOf(OutboxDao::class.java)
        ) { _, _, _ -> Unit } as OutboxDao

        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        val dummyDiag = allocateInstance.invoke(unsafe, SyncDiagnostics::class.java) as SyncDiagnostics
        val dummySyncScheduler = object : SyncScheduler(dummyDiag) {
            override fun requestSync(reason: String) {}
        }

        val repo = ItemStoreRepositoryImpl(
            itemStoreDao = itemStoreDaoProxy,
            itemStoreGroupDao = itemStoreGroupDaoProxy,
            storeDao = storeDaoProxy,
            outboxDao = outboxDaoProxy,
            syncScheduler = dummySyncScheduler,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )

        return RepositoryFixture(repo, itemStores, itemStoreGroups)
    }

    @Test
    fun `favoriting a group-covered store creates METADATA row with favorite=true`() = runBlocking {
        val fixture = createFixture()
        // Item has group_supermarkt selected and store_rewe marked favorite
        val selection = StoreSelection(
            memberStoreIds = emptySet(),
            excludedStoreIds = emptySet(),
            groupIds = setOf("group_supermarkt"),
            favoriteStoreIds = setOf("store_rewe")
        )
        fixture.repository.saveStoreSelection("item1", selection).getOrThrow()

        val reweRow = fixture.itemStores.find { it.storeId == "store_rewe" }
        assertEquals(ItemStoreMembership.METADATA.name, reweRow?.membership)
        assertEquals(true, reweRow?.favorite)

        val aldiRow = fixture.itemStores.find { it.storeId == "store_aldi" }
        assertEquals(null, aldiRow) // not pinned, no row needed

        val loaded = fixture.repository.getStoreSelection("item1")
        assertEquals(setOf("store_rewe"), loaded.favoriteStoreIds)
    }

    @Test
    fun `unassigning an explicit favorited store downgrades to dormant METADATA`() = runBlocking {
        val initialStores = listOf(
            DbItemStore("item1", "store_dm", ItemStoreMembership.MEMBER.name, favorite = true, createdAt = Instant.now()),
            DbItemStore("item1", "store_rewe", ItemStoreMembership.MEMBER.name, favorite = false, createdAt = Instant.now())
        )
        val fixture = createFixture(initialStores = initialStores)

        // User removes both stores from memberStoreIds, but store_dm favorite survives invisibly
        val selection = StoreSelection(
            memberStoreIds = emptySet(),
            excludedStoreIds = emptySet(),
            groupIds = emptySet(),
            favoriteStoreIds = setOf("store_dm")
        )
        fixture.repository.saveStoreSelection("item1", selection).getOrThrow()

        val dmRow = fixture.itemStores.find { it.storeId == "store_dm" }
        assertEquals(ItemStoreMembership.METADATA.name, dmRow?.membership)
        assertEquals(true, dmRow?.favorite)

        val reweRow = fixture.itemStores.find { it.storeId == "store_rewe" }
        assertEquals(null, reweRow) // non-favorited unassigned store deleted

        val loaded = fixture.repository.getStoreSelection("item1")
        assertEquals(setOf("store_dm"), loaded.favoriteStoreIds)
    }

    @Test
    fun `excluding a favorited store clears the favorite`() = runBlocking {
        val initialStores = listOf(
            DbItemStore("item1", "store_rewe", ItemStoreMembership.METADATA.name, favorite = true, createdAt = Instant.now())
        )
        val fixture = createFixture(initialStores = initialStores)

        // User excludes store_rewe
        val selection = StoreSelection(
            memberStoreIds = emptySet(),
            excludedStoreIds = setOf("store_rewe"),
            groupIds = setOf("group_supermarkt"),
            favoriteStoreIds = emptySet() // exclusion clears favorite
        )
        fixture.repository.saveStoreSelection("item1", selection).getOrThrow()

        val reweRow = fixture.itemStores.find { it.storeId == "store_rewe" }
        assertEquals(ItemStoreMembership.EXCLUDED.name, reweRow?.membership)
        assertEquals(false, reweRow?.favorite)

        val loaded = fixture.repository.getStoreSelection("item1")
        assertTrue(loaded.favoriteStoreIds.isEmpty())
    }
}
