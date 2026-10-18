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
    val membership: String = "MEMBER",
    val favorite: Boolean = false,
    /** Minor units (cents); null = unpriced (STORE-6). */
    val priceMinor: Long? = null,
    val createdAt: Instant
)
