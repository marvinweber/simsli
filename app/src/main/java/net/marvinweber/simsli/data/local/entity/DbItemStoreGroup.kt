package net.marvinweber.simsli.data.local.entity

import androidx.room.Entity
import java.time.Instant

/** An item's selection of a whole store group (STORE-7) — full-reconcile, like item_stores. */
@Entity(
    tableName = "item_store_groups",
    primaryKeys = ["itemId", "groupId"]
)
data class DbItemStoreGroup(
    val itemId: String,
    val groupId: String,
    val createdAt: Instant
)
