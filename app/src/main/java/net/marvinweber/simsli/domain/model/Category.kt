package net.marvinweber.simsli.domain.model

import java.time.Instant

data class Category(
    val id: String,
    val householdId: String,
    val name: String,
    val emoji: String? = null,
    val sortOrder: Float = 0f,
    val createdAt: Instant,
    val updatedAt: Instant
)
