package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import java.time.Instant

@Entity(
    tableName = "item_stores",
    primaryKeys = ["itemId", "storeId"]
)
data class DbItemStore(
    val itemId: String,
    val storeId: String,
    val createdAt: Instant
)
