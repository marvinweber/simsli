package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "categories")
data class DbCategory(
    @PrimaryKey
    val id: String,
    val householdId: String,
    val name: String,
    val emoji: String? = null,
    val sortOrder: Float,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
