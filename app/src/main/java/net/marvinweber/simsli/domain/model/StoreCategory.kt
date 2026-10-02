package net.marvinweber.simsli.domain.model

import java.time.Instant

/** Explicit per-store category ordering (the store's aisle order, STORE-4). */
data class StoreCategory(
    val storeId: String,
    val categoryId: String,
    val sortOrder: Float,
    val createdAt: Instant
)
