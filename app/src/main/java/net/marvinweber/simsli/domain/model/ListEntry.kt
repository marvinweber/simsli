package net.marvinweber.simsli.domain.model

import java.time.Instant

data class ListEntry(
    val id: String,
    val householdId: String,
    val itemId: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val comment: String? = null,
    val done: Boolean = false,
    val completedAt: Instant? = null,
    val createdAt: Instant,
    val updatedAt: Instant,
    val createdBy: String? = null
)