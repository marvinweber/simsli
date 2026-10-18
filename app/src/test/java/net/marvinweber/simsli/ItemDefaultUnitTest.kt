package net.marvinweber.simsli

import net.marvinweber.simsli.data.local.entity.DbItem
import net.marvinweber.simsli.data.local.entity.DbItemStore
import net.marvinweber.simsli.data.local.mapper.toDb
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.remote.mapper.toDb
import net.marvinweber.simsli.data.remote.mapper.toDto
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.domain.model.UnitPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class ItemDefaultUnitTest {

    @Test
    fun `unit presets contains standard shopping units`() {
        val expected = listOf("pcs", "pack", "g", "kg", "ml", "l")
        assertEquals(expected, UnitPresets.ALL)
    }

    @Test
    fun `local item mapper preserves defaultUnit`() {
        val domain = Item(
            id = "1",
            householdId = "hh-1",
            name = "Milch",
            notes = null,
            type = ItemType.PERMANENT,
            categoryId = null,
            defaultUnit = "l",
            sortOrder = 1f,
            links = emptyList(),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val db = domain.toDb()
        assertEquals("l", db.defaultUnit)

        val restored = db.toDomain()
        assertEquals("l", restored.defaultUnit)
    }

    @Test
    fun `remote dto mapper preserves defaultUnit`() {
        val db = DbItem(
            id = "2",
            householdId = "hh-1",
            name = "Bananen",
            notes = null,
            type = ItemType.PERMANENT,
            categoryId = null,
            defaultUnit = "kg",
            sortOrder = 2f,
            links = emptyList(),
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val dto = db.toDto()
        assertEquals("kg", dto.defaultUnit)

        val restored = dto.toDb()
        assertEquals("kg", restored.defaultUnit)
    }

    @Test
    fun `local item mapper preserves referenceSize`() {
        val domain = Item(
            id = "3",
            householdId = "hh-1",
            name = "Milch",
            type = ItemType.PERMANENT,
            referenceSize = "1 l",
            sortOrder = 1f,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val db = domain.toDb()
        assertEquals("1 l", db.referenceSize)

        val restored = db.toDomain()
        assertEquals("1 l", restored.referenceSize)
    }

    @Test
    fun `remote dto mapper preserves referenceSize and priceMinor`() {
        val db = DbItem(
            id = "4",
            householdId = "hh-1",
            name = "Brot",
            type = ItemType.PERMANENT,
            referenceSize = "750 g",
            sortOrder = 2f,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        val dto = db.toDto()
        assertEquals("750 g", dto.referenceSize)
        val restored = dto.toDb()
        assertEquals("750 g", restored.referenceSize)

        val row = DbItemStore(
            itemId = "4",
            storeId = "s-rewe",
            membership = "MEMBER",
            favorite = false,
            priceMinor = 199L,
            createdAt = Instant.EPOCH
        )
        val rowDto = row.toDto()
        assertEquals(199L, rowDto.priceMinor)
        val rowRestored = rowDto.toDb()
        assertEquals(199L, rowRestored.priceMinor)
    }

    @Test
    fun `nullable fields default to null and null drops out of dto json`() {
        val domain = Item(
            id = "5",
            householdId = "hh-1",
            name = "Kaffee",
            type = ItemType.PERMANENT,
            sortOrder = 3f,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH
        )
        assertNull(domain.referenceSize)
        assertNull(domain.toDb().referenceSize)
        assertNull(domain.toDb().toDto().referenceSize)

        val row = DbItemStore(
            itemId = "5",
            storeId = "s-aldi",
            createdAt = Instant.EPOCH
        )
        assertNull(row.priceMinor)
        assertNull(row.toDto().priceMinor)
    }
}
