package net.marvinweber.simsli.domain.model

import java.time.Instant

/** An item's selection of a whole store group — coverage is derived from it, never denormalized (STORE-7). */
data class ItemStoreGroup(
    val itemId: String,
    val groupId: String,
    val createdAt: Instant
)
