package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(tableName = "stores")
data class DbStore(
    @PrimaryKey
    val id: String,
    val householdId: String,
    val name: String,
    val sortOrder: Float,
    val createdAt: Instant,
    val updatedAt: Instant,
    val deletedAt: Instant? = null
)
