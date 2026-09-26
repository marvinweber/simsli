package net.marvinweber.simsli.domain.model

import java.time.Instant

data class ItemStore(
    val itemId: String,
    val storeId: String,
    val createdAt: Instant
)