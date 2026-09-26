package net.marvinweber.simsli.domain.model

import java.time.Instant

data class Store(
    val id: String,
    val householdId: String,
    val name: String,
    val sortOrder: Float = 0f,
    val createdAt: Instant,
    val updatedAt: Instant
)