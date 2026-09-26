package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
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
    val sortOrder: Float,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
