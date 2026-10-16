package net.marvinweber.simsli

import net.marvinweber.simsli.domain.model.ItemStore
import net.marvinweber.simsli.domain.model.ItemStoreGroup
import net.marvinweber.simsli.domain.model.ItemStoreMembership
import net.marvinweber.simsli.domain.model.Store
import net.marvinweber.simsli.domain.model.StoreFilter
import net.marvinweber.simsli.domain.util.EffectiveStoreSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

/**
 * STORE-7: the derived effective store set —
 * `MEMBER rows ∪ stores of selected groups − EXCLUDED rows`.
 */
class EffectiveStoreSetTest {

    private val t = Instant.EPOCH

    private fun store(id: String, groupId: String?) = Store(
        id = id, householdId = "hh", name = id, sortOrder = 0f, groupId = groupId,
        createdAt = t, updatedAt = t
    )

    private fun row(itemId: String, storeId: String, membership: ItemStoreMembership) =
        ItemStore(itemId = itemId, storeId = storeId, membership = membership, createdAt = t)

    @Test
    fun `explicit stores and group selections mix freely`() {
        val groups = listOf(ItemStoreGroup("item1", "g1", t))
        val stores = listOf(store("s1", "g1"), store("s2", "g1"), store("s3", null))
        val rows = listOf(row("item1", "s3", ItemStoreMembership.MEMBER))

        val result = EffectiveStoreSet.compute(rows, groups, stores)

        assertEquals(setOf("s1", "s2", "s3"), result["item1"])
    }

    @Test
    fun `exclusion removes a group-covered store`() {
        val groups = listOf(ItemStoreGroup("item1", "g1", t))
        val stores = listOf(store("s1", "g1"), store("s2", "g1"))
        val rows = listOf(row("item1", "s2", ItemStoreMembership.EXCLUDED))

        val result = EffectiveStoreSet.compute(rows, groups, stores)

        assertEquals(setOf("s1"), result["item1"])
    }

    @Test
    fun `exclusion survives the store moving to another group`() {
        // s2 moved from g1 to g2 after the item excluded it: the exclusion is
        // per (item, store), not per group.
        val groups = listOf(ItemStoreGroup("item1", "g1", t))
        val stores = listOf(store("s1", "g1"), store("s2", "g2"))
        val rows = listOf(row("item1", "s2", ItemStoreMembership.EXCLUDED))

        val result = EffectiveStoreSet.compute(rows, groups, stores)

        assertEquals(setOf("s1"), result["item1"])
    }

    @Test
    fun `adding a store to a group propagates without per-item writes`() {
        val groups = listOf(ItemStoreGroup("item1", "g1", t))
        val rows = emptyList<ItemStore>()
        val storesBefore = listOf(store("s1", "g1"))
        val storesAfter = storesBefore + store("s2", "g1")

        assertEquals(setOf("s1"), EffectiveStoreSet.compute(rows, groups, storesBefore)["item1"])
        assertEquals(setOf("s1", "s2"), EffectiveStoreSet.compute(rows, groups, storesAfter)["item1"])
    }

    @Test
    fun `metadata rows never contribute membership`() {
        val groups = emptyList<ItemStoreGroup>()
        val stores = listOf(store("s1", null))
        val rows = listOf(row("item1", "s1", ItemStoreMembership.METADATA))

        val result = EffectiveStoreSet.compute(rows, groups, stores)

        // The map is sparse: items without coverage have no entry at all.
        assertEquals(emptySet<String>(), result["item1"].orEmpty())
    }

    @Test
    fun `items without any selection have no coverage`() {
        val result = EffectiveStoreSet.compute(emptyList(), emptyList(), listOf(store("s1", null)))

        assertEquals(emptySet<String>(), result["item1"].orEmpty())
    }

    @Test
    fun `computeFavorites filters out non-favorites and excluded stores`() {
        val rows = listOf(
            ItemStore("item1", "s1", ItemStoreMembership.MEMBER, favorite = true, createdAt = t),
            ItemStore("item1", "s2", ItemStoreMembership.MEMBER, favorite = false, createdAt = t),
            ItemStore("item1", "s3", ItemStoreMembership.METADATA, favorite = true, createdAt = t),
            ItemStore("item1", "s4", ItemStoreMembership.EXCLUDED, favorite = true, createdAt = t),
            ItemStore("item2", "s1", ItemStoreMembership.MEMBER, favorite = false, createdAt = t)
        )
        val favorites = EffectiveStoreSet.computeFavorites(rows)
        assertEquals(setOf("s1", "s3"), favorites["item1"])
        assertEquals(null, favorites["item2"])
    }

    @Test
    fun `matchesStoreFilter with favorite filter OFF matches effective stores`() {
        val stores = listOf(store("s1", "g1"), store("s2", "g1"), store("s3", null))
        val effective = setOf("s1", "s2")
        val favorites = setOf("s1")

        assertTrue(
            EffectiveStoreSet.matchesStoreFilter(
                effectiveStoreIds = effective,
                favoriteStoreIds = favorites,
                filter = StoreFilter.ByStore("s2"),
                stores = stores,
                favoriteOnly = false
            )
        )
    }

    @Test
    fun `matchesStoreFilter with favorite filter ON requires store to be favorited if item has favorites`() {
        val stores = listOf(store("s1", "g1"), store("s2", "g1"), store("s3", null))
        val effective = setOf("s1", "s2")
        val favorites = setOf("s1")

        // s1 is favorited -> matches
        assertTrue(
            EffectiveStoreSet.matchesStoreFilter(
                effectiveStoreIds = effective,
                favoriteStoreIds = favorites,
                filter = StoreFilter.ByStore("s1"),
                stores = stores,
                favoriteOnly = true
            )
        )

        // s2 is in effective set, but NOT favorited and item has other favorites -> does NOT match
        assertFalse(
            EffectiveStoreSet.matchesStoreFilter(
                effectiveStoreIds = effective,
                favoriteStoreIds = favorites,
                filter = StoreFilter.ByStore("s2"),
                stores = stores,
                favoriteOnly = true
            )
        )
    }

    @Test
    fun `matchesStoreFilter with favorite filter ON matches all assigned stores if item has NO favorites`() {
        val stores = listOf(store("s1", "g1"), store("s2", "g1"))
        val effective = setOf("s1", "s2")
        val favorites = emptySet<String>()

        // No favorites for this item: item counts for all its stores even when favorite filter is ON
        assertTrue(
            EffectiveStoreSet.matchesStoreFilter(
                effectiveStoreIds = effective,
                favoriteStoreIds = favorites,
                filter = StoreFilter.ByStore("s1"),
                stores = stores,
                favoriteOnly = true
            )
        )
        assertTrue(
            EffectiveStoreSet.matchesStoreFilter(
                effectiveStoreIds = effective,
                favoriteStoreIds = favorites,
                filter = StoreFilter.ByStore("s2"),
                stores = stores,
                favoriteOnly = true
            )
        )
    }

    @Test
    fun `matchesStoreFilter with favorite filter ON checks group favorites`() {
        val stores = listOf(store("s1", "g1"), store("s2", "g1"), store("s3", "g2"))
        val effective = setOf("s1", "s2", "s3")

        // Favorited s3 in g2, but filtering by g1: item has favorites, but none in g1 -> does NOT match g1
        assertFalse(
            EffectiveStoreSet.matchesStoreFilter(
                effectiveStoreIds = effective,
                favoriteStoreIds = setOf("s3"),
                filter = StoreFilter.ByGroup("g1"),
                stores = stores,
                favoriteOnly = true
            )
        )

        // Favorited s1 in g1: matches g1
        assertTrue(
            EffectiveStoreSet.matchesStoreFilter(
                effectiveStoreIds = effective,
                favoriteStoreIds = setOf("s1"),
                filter = StoreFilter.ByGroup("g1"),
                stores = stores,
                favoriteOnly = true
            )
        )
    }
}
