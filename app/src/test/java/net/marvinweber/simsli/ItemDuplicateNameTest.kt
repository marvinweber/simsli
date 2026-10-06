package net.marvinweber.simsli

import kotlinx.coroutines.runBlocking
import net.marvinweber.simsli.data.local.dao.ItemDao
import net.marvinweber.simsli.data.local.dao.ItemStoreDao
import net.marvinweber.simsli.data.local.dao.ListEntryDao
import net.marvinweber.simsli.data.local.dao.OutboxDao
import net.marvinweber.simsli.data.local.entity.DbItem
import net.marvinweber.simsli.data.repository.impl.ItemRepositoryImpl
import net.marvinweber.simsli.data.sync.SyncScheduler
import net.marvinweber.simsli.domain.model.ItemType
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.Instant

class ItemDuplicateNameTest {

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> dummyProxy(): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, _, _ -> null } as T
    }

    private fun createRepository(items: List<DbItem>): ItemRepositoryImpl {
        val itemDaoProxy = Proxy.newProxyInstance(
            ItemDao::class.java.classLoader,
            arrayOf(ItemDao::class.java)
        ) { _, method, args ->
            when (method.name) {
                "getActiveItemsByHouseholdOnce" -> {
                    val householdId = args[0] as String
                    items.filter { it.householdId == householdId && it.deletedAt == null }
                }
                else -> null
            }
        } as ItemDao

        val dummyOutboxDao: OutboxDao = dummyProxy()
        val dummyListEntryDao: ListEntryDao = dummyProxy()
        val dummyItemStoreDao: ItemStoreDao = dummyProxy()

        val unsafeField = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe")
        unsafeField.isAccessible = true
        val unsafe = unsafeField.get(null)
        val allocateInstance = unsafe.javaClass.getMethod("allocateInstance", Class::class.java)
        val dummySyncScheduler = allocateInstance.invoke(unsafe, SyncScheduler::class.java) as SyncScheduler

        return ItemRepositoryImpl(
            itemDao = itemDaoProxy,
            outboxDao = dummyOutboxDao,
            listEntryDao = dummyListEntryDao,
            itemStoreDao = dummyItemStoreDao,
            syncScheduler = dummySyncScheduler,
            ioDispatcher = kotlinx.coroutines.Dispatchers.Unconfined
        )
    }

    private fun testItem(
        id: String,
        name: String,
        householdId: String = "hh-1",
        deletedAt: Instant? = null
    ) = DbItem(
        id = id,
        householdId = householdId,
        name = name,
        notes = null,
        type = ItemType.PERMANENT,
        categoryId = null,
        sortOrder = 1f,
        links = emptyList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
        deletedAt = deletedAt
    )

    @Test
    fun `isItemNameDuplicate detects exact name duplicate`() = runBlocking {
        val repo = createRepository(listOf(testItem("1", "Milch")))
        assertTrue(repo.isItemNameDuplicate("hh-1", "Milch"))
    }

    @Test
    fun `isItemNameDuplicate detects case-insensitive duplicate`() = runBlocking {
        val repo = createRepository(listOf(testItem("1", "Milch")))
        assertTrue(repo.isItemNameDuplicate("hh-1", "milch"))
        assertTrue(repo.isItemNameDuplicate("hh-1", "MILCH"))
    }

    @Test
    fun `isItemNameDuplicate detects duplicate with umlauts and special casing`() = runBlocking {
        val repo = createRepository(listOf(testItem("1", "Äpfel")))
        assertTrue(repo.isItemNameDuplicate("hh-1", "äpfel"))
        assertTrue(repo.isItemNameDuplicate("hh-1", "ÄPFEL"))
    }

    @Test
    fun `isItemNameDuplicate detects duplicate with whitespace trimming`() = runBlocking {
        val repo = createRepository(listOf(testItem("1", "Milch")))
        assertTrue(repo.isItemNameDuplicate("hh-1", "  Milch  "))
        assertTrue(repo.isItemNameDuplicate("hh-1", " milch\t"))
    }

    @Test
    fun `isItemNameDuplicate returns false when name does not exist`() = runBlocking {
        val repo = createRepository(listOf(testItem("1", "Milch")))
        assertFalse(repo.isItemNameDuplicate("hh-1", "Brot"))
    }

    @Test
    fun `isItemNameDuplicate ignores the item itself when excludeItemId is provided`() = runBlocking {
        val repo = createRepository(listOf(testItem("1", "Milch")))
        // Editing "Milch" with its own id
        assertFalse(repo.isItemNameDuplicate("hh-1", "Milch", excludeItemId = "1"))
        assertFalse(repo.isItemNameDuplicate("hh-1", "milch", excludeItemId = "1"))
    }

    @Test
    fun `isItemNameDuplicate detects duplicate when editing and another item has that name`() = runBlocking {
        val repo = createRepository(
            listOf(
                testItem("1", "Milch"),
                testItem("2", "Brot")
            )
        )
        // Renaming "Brot" (id 2) to "Milch"
        assertTrue(repo.isItemNameDuplicate("hh-1", "Milch", excludeItemId = "2"))
    }

    @Test
    fun `isItemNameDuplicate ignores soft-deleted items`() = runBlocking {
        val repo = createRepository(listOf(testItem("1", "Milch", deletedAt = Instant.now())))
        assertFalse(repo.isItemNameDuplicate("hh-1", "Milch"))
    }

    @Test
    fun `isItemNameDuplicate returns false for empty or blank names`() = runBlocking {
        val repo = createRepository(listOf(testItem("1", "Milch")))
        assertFalse(repo.isItemNameDuplicate("hh-1", ""))
        assertFalse(repo.isItemNameDuplicate("hh-1", "   "))
    }

    @Test
    fun `isItemNameDuplicate respects household isolation`() = runBlocking {
        val repo = createRepository(listOf(testItem("1", "Milch", householdId = "hh-2")))
        assertFalse(repo.isItemNameDuplicate("hh-1", "Milch"))
        assertTrue(repo.isItemNameDuplicate("hh-2", "Milch"))
    }
}
