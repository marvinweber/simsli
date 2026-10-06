package net.marvinweber.simsli

import net.marvinweber.simsli.data.local.entity.DbItem
import net.marvinweber.simsli.data.local.mapper.toDb
import net.marvinweber.simsli.data.local.mapper.toDomain
import net.marvinweber.simsli.data.remote.dto.ItemDto
import net.marvinweber.simsli.data.remote.mapper.toDb
import net.marvinweber.simsli.data.remote.mapper.toDto
import net.marvinweber.simsli.domain.model.Item
import net.marvinweber.simsli.domain.model.ItemType
import net.marvinweber.simsli.domain.model.UnitPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
}
