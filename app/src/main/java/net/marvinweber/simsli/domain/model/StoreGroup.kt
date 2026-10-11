package net.marvinweber.simsli.domain.model

import java.time.Instant

/**
 * Optional household-level grouping of stores ("Supermarkt", "Drogeriemarkt").
 * A store belongs to at most one group or none (STORE-7).
 */
data class StoreGroup(
    val id: String,
    val householdId: String,
    val name: String,
    val sortOrder: Float = 0f,
    val createdAt: Instant,
    val updatedAt: Instant
)
