package net.marvinweber.simsli.domain.model

import java.time.Instant

data class Item(
    val id: String,
    val householdId: String,
    val name: String,
    val notes: String? = null,
    val type: ItemType = ItemType.PERMANENT,
    val sortOrder: Float = 0f,
    val createdAt: Instant,
    val updatedAt: Instant
)