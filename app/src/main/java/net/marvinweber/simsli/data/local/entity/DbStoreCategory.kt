package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import java.time.Instant

@Entity(
    tableName = "store_categories",
    primaryKeys = ["storeId", "categoryId"]
)
data class DbStoreCategory(
    val storeId: String,
    val categoryId: String,
    val sortOrder: Float,
    val createdAt: Instant
)
