package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "list_entries")
data class DbListEntry(
    @PrimaryKey
    val id: String,
    val householdId: String,
    val itemId: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val comment: String? = null,
    val done: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String? = null
)
