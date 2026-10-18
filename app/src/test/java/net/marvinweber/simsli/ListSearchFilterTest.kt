package net.marvinweber.simsli

import net.marvinweber.simsli.domain.model.CategoryFilter
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ItemStoreGroup
import net.marvinweber.simsli.domain.model.ItemStoreMembership
import net.marvinweber.simsli.domain.model.ListEntry
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.model.StoreFilter
import net.marvinweber.simsli.domain.util.EffectiveStoreSet
import net.marvinweber.simsli.ui.screens.list.CatalogItemUi
import net.marvinweber.simsli.ui.screens.list.CategoryGroupUi
import net.marvinweber.simsli.ui.screens.list.ListEntryItem
import net.marvinweber.simsli.ui.screens.list.applyListSearch
import net.marvinweber.simsli.ui.screens.list.matchesCategoryFilter
import net.marvinweber.simsli.ui.screens.list.matchesStoreFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * SEARCH-1: the top-bar search on the shopping list — name filtering of active
 * entries, recently checked entries, and the quick-add suggestion chips.
 */
class ListSearchFilterTest {

    private val t = Instant.EPOCH

    private fun item(id: String, name: String, categoryId: String? = null) = Item(
        id = id, householdId = "hh", name = name, categoryId = categoryId,
        createdAt = t, updatedAt = t
    )

    private fun entry(id: String, itemId: String, done: Boolean = false) = ListEntry(
        id = id, householdId = "hh", itemId = itemId, done = done,
        createdAt = t, updatedAt = t
    )

    private fun store(id: String, groupId: String? = null) = Store(
        id = id, householdId = "hh", name = id, sortOrder = 0f, groupId = groupId,
        createdAt = t, updatedAt = t
    )

    private fun entryItem(itemName: String, itemId: String = itemName, done: Boolean = false) =
        ListEntryItem(entry("e_$itemName", itemId, done), item(itemId, itemName))

    private fun catalogItem(item: Item, entryId: String? = null) =
        CatalogItemUi(item, isOnActiveList = false, entryId = entryId)

    @Test
    fun `matching is case-insensitive on the item name`() {
        val active = listOf(entryItem("Milk"))
        val result = applyListSearch(
            groups = listOf(CategoryGroupUi("c1", "Cat", null, entries = active)),
            activeEntries = active, recentlyChecked = emptyList(), catalog = emptyList(),
            query = "milk",
            effectiveStoreIdsByItem = emptyMap(), stores = emptyList(),
            storeFilter = StoreFilter.All, categoryFilter = CategoryFilter.All
        )

        assertEquals(1, result.activeEntries.size)
        assertEquals(1, result.groups.size)
    }

    @Test
    fun `matching uses the injected fold`() {
        // The production fold (SearchFold, android.icu) doesn't exist on the JVM —
        // the fold is injected so the filtering logic is testable with a stub.
        val active = listOf(entryItem("Äpfel"))
        val result = applyListSearch(
            groups = listOf(CategoryGroupUi("c1", "Obst", null, entries = active)),
            activeEntries = active, recentlyChecked = emptyList(), catalog = emptyList(),
            query = "apfel",
            effectiveStoreIdsByItem = emptyMap(), stores = emptyList(),
            storeFilter = StoreFilter.All, categoryFilter = CategoryFilter.All,
            fold = { it.replace("ä", "a").replace("Ä", "a").lowercase() }
        )

        assertEquals(1, result.activeEntries.size)
    }

    @Test
    fun `recently checked entries are filtered too`() {
        val recent = listOf(entryItem("Oat Milk", done = true), entryItem("Bread", done = true))
        val result = applyListSearch(
            groups = emptyList(), activeEntries = emptyList(), recentlyChecked = recent,
            catalog = emptyList(), query = "oat",
            effectiveStoreIdsByItem = emptyMap(), stores = emptyList(),
            storeFilter = StoreFilter.All, categoryFilter = CategoryFilter.All
        )

        assertEquals(listOf("Oat Milk"), result.recentlyChecked.map { it.item?.name })
    }

    @Test
    fun `empty query is the identity with no suggestions`() {
        val active = listOf(entryItem("Milk"))
        val catalog = listOf(catalogItem(item("i1", "Cookies")))
        val result = applyListSearch(
            groups = listOf(CategoryGroupUi("c1", "Cat", null, entries = active)),
            activeEntries = active, recentlyChecked = emptyList(), catalog = catalog,
            query = "   ",
            effectiveStoreIdsByItem = emptyMap(), stores = emptyList(),
            storeFilter = StoreFilter.All, categoryFilter = CategoryFilter.All
        )

        assertEquals(active, result.activeEntries)
        assertTrue(result.suggestions.isEmpty())
    }

    @Test
    fun `suggestions exclude items that already have an entry`() {
        val catalog = listOf(
            catalogItem(item("i1", "Milk"), entryId = "e1"),
            catalogItem(item("i2", "Oat Milk")),
            catalogItem(item("i3", "Milkshake"), entryId = "e2")
        )
        val result = applyListSearch(
            groups = emptyList(), activeEntries = emptyList(), recentlyChecked = emptyList(),
            catalog = catalog, query = "milk",
            effectiveStoreIdsByItem = emptyMap(), stores = emptyList(),
            storeFilter = StoreFilter.All, categoryFilter = CategoryFilter.All
        )

        assertEquals(listOf("Oat Milk"), result.suggestions.map { it.item.name })
    }

    @Test
    fun `suggestions honor the store filter via the effective store set`() {
        val stores = listOf(store("s1", "g1"), store("s2", "g1"), store("s3", null))
        val itemStores = listOf(
            ItemStore("i1", "s3", ItemStoreMembership.MEMBER, createdAt = t)
        )
        val itemStoreGroups = listOf(ItemStoreGroup("i2", "g1", t))
        val effective = EffectiveStoreSet.compute(itemStores, itemStoreGroups, stores)

        val catalog = listOf(
            catalogItem(item("i1", "Milk")),
            catalogItem(item("i2", "Oat Milk")),
            catalogItem(item("i3", "Buttermilk"))
        )
        val result = applyListSearch(
            groups = emptyList(), activeEntries = emptyList(), recentlyChecked = emptyList(),
            catalog = catalog, query = "milk",
            effectiveStoreIdsByItem = effective, stores = stores,
            storeFilter = StoreFilter.ByGroup("g1"), categoryFilter = CategoryFilter.All
        )

        // i1's effective set is {s3} (not in g1), i2's is {s1, s2} (in g1), i3 has none
        assertEquals(listOf("Oat Milk"), result.suggestions.map { it.item.name })
    }

    @Test
    fun `suggestions honor the category filter`() {
        val catalog = listOf(
            catalogItem(item("i1", "Milk", categoryId = "c1")),
            catalogItem(item("i2", "Oat Milk", categoryId = null))
        )
        val result = applyListSearch(
            groups = emptyList(), activeEntries = emptyList(), recentlyChecked = emptyList(),
            catalog = catalog, query = "milk",
            effectiveStoreIdsByItem = emptyMap(), stores = emptyList(),
            storeFilter = StoreFilter.All, categoryFilter = CategoryFilter.ByCategory("c1")
        )

        assertEquals(listOf("Milk"), result.suggestions.map { it.item.name })
    }

    @Test
    fun `groups left empty by the query are dropped`() {
        val groups = listOf(
            CategoryGroupUi("c1", "Dairy", null, entries = listOf(entryItem("Milk"))),
            CategoryGroupUi("c2", "Bakery", null, entries = listOf(entryItem("Bread")))
        )
        val result = applyListSearch(
            groups = groups,
            activeEntries = groups.flatMap { it.entries }, recentlyChecked = emptyList(),
            catalog = emptyList(), query = "bread",
            effectiveStoreIdsByItem = emptyMap(), stores = emptyList(),
            storeFilter = StoreFilter.All, categoryFilter = CategoryFilter.All
        )

        assertEquals(listOf("Bakery"), result.groups.map { it.title })
    }

    @Test
    fun `store filter checks mirror the DAO semantics`() {
        val stores = listOf(store("s1", "g1"), store("s2", null))

        assertTrue(matchesStoreFilter(setOf("s1"), StoreFilter.ByStore("s1"), stores))
        assertFalse(matchesStoreFilter(setOf("s2"), StoreFilter.ByStore("s1"), stores))
        assertTrue(matchesStoreFilter(setOf("s1"), StoreFilter.ByGroup("g1"), stores))
        assertFalse(matchesStoreFilter(setOf("s2"), StoreFilter.ByGroup("g1"), stores))
        assertTrue(matchesStoreFilter(emptySet(), StoreFilter.NoStore, stores))
        assertFalse(matchesStoreFilter(setOf("s1"), StoreFilter.NoStore, stores))
        assertTrue(matchesStoreFilter(setOf("s2"), StoreFilter.All, stores))
    }

    @Test
    fun `category filter checks mirror the DAO semantics`() {
        assertTrue(matchesCategoryFilter("c1", CategoryFilter.ByCategory("c1")))
        assertFalse(matchesCategoryFilter("c2", CategoryFilter.ByCategory("c1")))
        assertTrue(matchesCategoryFilter(null, CategoryFilter.NoCategory))
        assertFalse(matchesCategoryFilter("c1", CategoryFilter.NoCategory))
        assertTrue(matchesCategoryFilter(null, CategoryFilter.All))
    }

    @Test
    fun `suggestions honor the favorite filter`() {
        val stores = listOf(store("s1", "g1"), store("s2", "g1"))
        val catalog = listOf(
            catalogItem(item("i1", "Milk")),      // favorites = {s1}
            catalogItem(item("i2", "Oat Milk")),  // favorites = {s2}
            catalogItem(item("i3", "Soy Milk"))   // no favorites at all
        )
        val effective = mapOf(
            "i1" to setOf("s1", "s2"),
            "i2" to setOf("s1", "s2"),
            "i3" to setOf("s1", "s2")
        )
        val favorites = mapOf(
            "i1" to setOf("s1"),
            "i2" to setOf("s2")
        )

        // When favorite filter is OFF for s1: all 3 items appear
        val offResult = applyListSearch(
            groups = emptyList(), activeEntries = emptyList(), recentlyChecked = emptyList(),
            catalog = catalog, query = "milk",
            effectiveStoreIdsByItem = effective, stores = stores,
            storeFilter = StoreFilter.ByStore("s1"), categoryFilter = CategoryFilter.All,
            favoriteStoreIdsByItem = favorites, favoriteFilter = false
        )
        assertEquals(listOf("Milk", "Oat Milk", "Soy Milk"), offResult.suggestions.map { it.item.name })

        // When favorite filter is ON for s1:
        // i1 matches because s1 is favorite
        // i2 does not match because s2 is favorite, not s1
        // i3 matches because it has NO favorites at all (counts for all its assigned stores)
        val onResult = applyListSearch(
            groups = emptyList(), activeEntries = emptyList(), recentlyChecked = emptyList(),
            catalog = catalog, query = "milk",
            effectiveStoreIdsByItem = effective, stores = stores,
            storeFilter = StoreFilter.ByStore("s1"), categoryFilter = CategoryFilter.All,
            favoriteStoreIdsByItem = favorites, favoriteFilter = true
        )
        assertEquals(listOf("Milk", "Soy Milk"), onResult.suggestions.map { it.item.name })
    }
}
