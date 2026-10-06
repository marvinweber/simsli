package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import net.marvinweber.simsli.domain.model.ItemLink
import net.marvinweber.simsli.domain.model.ItemType
import java.time.Instant

@Entity(tableName = "items")
data class DbItem(
    @PrimaryKey
    val id: String,
    val householdId: String,
    val name: String,
    val notes: String? = null,
    val type: ItemType,
    /** Zero or one category (CAT-2); null = uncategorized. */
    val categoryId: String? = null,
    val defaultUnit: String? = null,
    val sortOrder: Float,
    val links: List<ItemLink> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
