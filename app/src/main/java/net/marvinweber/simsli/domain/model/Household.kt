package net.marvinweber.simsli.domain.model

import java.time.Instant

data class Household(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant
)