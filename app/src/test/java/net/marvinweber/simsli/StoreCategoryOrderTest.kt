package net.marvinweber.simsli

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import net.marvinweber.simsli.data.local.dao.CategoryDao
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.dao.StoreCategoryDao
import net.marvinweber.simsli.data.local.dao.StoreDao
import net.marvinweber.simsli.data.local.entity.DbOutboxEntry
import net.marvinweber.simsli.data.local.entity.DbStoreCategory
import net.marvinweber.simsli.data.repository.impl.CategoryRepositoryImpl
import net.marvinweber.simsli.data.repository.impl.StoreRepositoryImpl
import net.marvinweber.simsli.data.sync.SyncContract
import net.marvinweber.simsli.data.sync.SyncDiagnostics
import net.marvinweber.simsli.data.sync.SyncScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class StoreCategoryOrderTest {

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> dummyProxy(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, _, _ -> null } as T
    }

    private fun createDummySyncScheduler(): SyncScheduler {
        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        val dummyDiag = allocateInstance.invoke(unsafe, SyncDiagnostics::class.java) as SyncDiagnostics
        return object : SyncScheduler(dummyDiag) {
            override fun requestSync(reason: String) {
                // no-op in tests
            }
        }
    }

    @Test
    fun `saveStoreCategories inserts new rows and enqueues outbox upserts`() = runBlocking {
        val existingStoreCategories = mutableListOf<DbStoreCategory>()
        val outboxEntries = mutableListOf<DbOutboxEntry>()

        val storeCategoryDao = Proxy.newProxyInstance(
            StoreCategoryDao::class.java.classLoader,
            arrayOf(StoreCategoryDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getCategoryIdsForStore" -> existingStoreCategories.filter { it.storeId == args[0] }.map { it.categoryId }
                "getOnce" -> existingStoreCategories.firstOrNull { it.storeId == args[0] && it.categoryId == args[1] }
                "insert" -> {
                    val entity = args[0] as DbStoreCategory
                    existingStoreCategories.removeAll { it.storeId == entity.storeId && it.categoryId == entity.categoryId }
                    existingStoreCategories.add(entity)
                    Unit
                }
                "delete" -> {
                    val storeId = args[0] as String
                    val categoryId = args[1] as String
                    existingStoreCategories.removeAll { it.storeId == storeId && it.categoryId == categoryId }
                    Unit
                }
                else -> null
            }
        } as StoreCategoryDao

        val outboxDao = Proxy.newProxyInstance(
            OutboxDao::class.java.classLoader,
            arrayOf(OutboxDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "enqueue" -> {
                    outboxEntries.add(args[0] as DbOutboxEntry)
                    Unit
                }
                else -> null
            }
        } as OutboxDao

        val syncScheduler = createDummySyncScheduler()
        val categoryRepo = CategoryRepositoryImpl(
            categoryDao = dummyProxy<CategoryDao>(),
            itemDao = dummyProxy<ItemDao>(),
            storeCategoryDao = storeCategoryDao,
            outboxDao = outboxDao,
            syncScheduler = syncScheduler,
            ioDispatcher = Dispatchers.Unconfined
        )

        val result = categoryRepo.saveStoreCategories(
            storeId = "store1",
            orderedCategoryIds = listOf("cat_produce", "cat_dairy")
        )

        assertTrue(result.isSuccess)
        assertEquals(2, existingStoreCategories.size)
        assertEquals("cat_produce", existingStoreCategories[0].categoryId)
        assertEquals(1000f, existingStoreCategories[0].sortOrder)
        assertEquals("cat_dairy", existingStoreCategories[1].categoryId)
        assertEquals(2000f, existingStoreCategories[1].sortOrder)

        assertEquals(2, outboxEntries.size)
        assertEquals(SyncContract.OP_UPSERT, outboxEntries[0].operation)
        assertEquals("store1:cat_produce", outboxEntries[0].entityId)
        assertEquals(SyncContract.OP_UPSERT, outboxEntries[1].operation)
        assertEquals("store1:cat_dairy", outboxEntries[1].entityId)
    }

    @Test
    fun `saveStoreCategories deletes unchecked categories and enqueues outbox deletes`() = runBlocking {
        val now = Instant.now()
        val existingStoreCategories = mutableListOf(
            DbStoreCategory("store1", "cat_produce", 1000f, now),
            DbStoreCategory("store1", "cat_dairy", 2000f, now),
            DbStoreCategory("store1", "cat_bakery", 3000f, now)
        )
        val outboxEntries = mutableListOf<DbOutboxEntry>()

        val storeCategoryDao = Proxy.newProxyInstance(
            StoreCategoryDao::class.java.classLoader,
            arrayOf(StoreCategoryDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getCategoryIdsForStore" -> existingStoreCategories.filter { it.storeId == args[0] }.map { it.categoryId }
                "getOnce" -> existingStoreCategories.firstOrNull { it.storeId == args[0] && it.categoryId == args[1] }
                "insert" -> {
                    val entity = args[0] as DbStoreCategory
                    existingStoreCategories.removeAll { it.storeId == entity.storeId && it.categoryId == entity.categoryId }
                    existingStoreCategories.add(entity)
                    Unit
                }
                "delete" -> {
                    val storeId = args[0] as String
                    val categoryId = args[1] as String
                    existingStoreCategories.removeAll { it.storeId == storeId && it.categoryId == categoryId }
                    Unit
                }
                else -> null
            }
        } as StoreCategoryDao

        val outboxDao = Proxy.newProxyInstance(
            OutboxDao::class.java.classLoader,
            arrayOf(OutboxDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "enqueue" -> {
                    outboxEntries.add(args[0] as DbOutboxEntry)
                    Unit
                }
                else -> null
            }
        } as OutboxDao

        val syncScheduler = createDummySyncScheduler()
        val categoryRepo = CategoryRepositoryImpl(
            categoryDao = dummyProxy<CategoryDao>(),
            itemDao = dummyProxy<ItemDao>(),
            storeCategoryDao = storeCategoryDao,
            outboxDao = outboxDao,
            syncScheduler = syncScheduler,
            ioDispatcher = Dispatchers.Unconfined
        )

        // Dairy removed, bakery moved before produce
        val result = categoryRepo.saveStoreCategories(
            storeId = "store1",
            orderedCategoryIds = listOf("cat_bakery", "cat_produce")
        )

        assertTrue(result.isSuccess)
        assertEquals(2, existingStoreCategories.size)
        val bakery = existingStoreCategories.first { it.categoryId == "cat_bakery" }
        val produce = existingStoreCategories.first { it.categoryId == "cat_produce" }
        assertEquals(1000f, bakery.sortOrder)
        assertEquals(2000f, produce.sortOrder)

        // Dairy was deleted
        val deleted = outboxEntries.filter { it.operation == SyncContract.OP_DELETE }
        assertEquals(1, deleted.size)
        assertEquals("store1:cat_dairy", deleted[0].entityId)

        // Bakery and Produce were updated with new sort orders
        val upserted = outboxEntries.filter { it.operation == SyncContract.OP_UPSERT }
        assertEquals(2, upserted.size)
    }

    @Test
    fun `deleteStore removes store categories and enqueues outbox deletes`() = runBlocking {
        val storeId = "store_rewe"
        val existingStoreCategories = mutableListOf(
            DbStoreCategory(storeId, "cat_produce", 1000f, Instant.now()),
            DbStoreCategory(storeId, "cat_dairy", 2000f, Instant.now())
        )
        val outboxEntries = mutableListOf<DbOutboxEntry>()

        val storeCategoryDao = Proxy.newProxyInstance(
            StoreCategoryDao::class.java.classLoader,
            arrayOf(StoreCategoryDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getCategoryIdsForStore" -> existingStoreCategories.filter { it.storeId == args[0] }.map { it.categoryId }
                "deleteByStoreId" -> {
                    existingStoreCategories.removeAll { it.storeId == args[0] }
                    Unit
                }
                else -> null
            }
        } as StoreCategoryDao

        val itemStoreDao = Proxy.newProxyInstance(
            ItemStoreDao::class.java.classLoader,
            arrayOf(ItemStoreDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "getItemIdsForStore" -> emptyList<String>()
                "deleteByStoreId" -> Unit
                else -> null
            }
        } as ItemStoreDao

        val storeDao = Proxy.newProxyInstance(
            StoreDao::class.java.classLoader,
            arrayOf(StoreDao::class.java)
        ) { _, method, _ ->
            when (method.name) {
                "delete" -> Unit
                else -> null
            }
        } as StoreDao

        val outboxDao = Proxy.newProxyInstance(
            OutboxDao::class.java.classLoader,
            arrayOf(OutboxDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "enqueue" -> {
                    outboxEntries.add(args[0] as DbOutboxEntry)
                    Unit
                }
                else -> null
            }
        } as OutboxDao

        val storeRepo = StoreRepositoryImpl(
            storeDao = storeDao,
            itemStoreDao = itemStoreDao,
            storeCategoryDao = storeCategoryDao,
            outboxDao = outboxDao,
            syncScheduler = createDummySyncScheduler(),
            ioDispatcher = Dispatchers.Unconfined
        )

        val result = storeRepo.deleteStore(storeId)

        assertTrue(result.isSuccess)
        assertTrue(existingStoreCategories.isEmpty())

        val deletedCategories = outboxEntries.filter {
            it.entityType == SyncContract.ENTITY_STORE_CATEGORY && it.operation == SyncContract.OP_DELETE
        }
        assertEquals(2, deletedCategories.size)
        assertTrue(deletedCategories.any { it.entityId == "store_rewe:cat_produce" })
        assertTrue(deletedCategories.any { it.entityId == "store_rewe:cat_dairy" })
    }
}
