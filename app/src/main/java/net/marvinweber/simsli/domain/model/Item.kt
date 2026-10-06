package net.marvinweber.simsli.domain.model

import java.time.Instant

data class Item(
    val id: String,
    val householdId: String,
    val name: String,
    val notes: String? = null,
    val type: ItemType = ItemType.PERMANENT,
    /** Zero or one category (CAT-2); null = uncategorized. */
    val categoryId: String? = null,
    /** Optional unit preset preselected when adding to list (ITEM-5). */
    val defaultUnit: String? = null,
    val sortOrder: Float = 0f,
    val links: List<ItemLink> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant
)