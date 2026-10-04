package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import net.marvinweber.simsli.domain.model.ItemLink

@Serializable
data class ItemDto(
    val id: String,
    @SerialName("household_id") val householdId: String,
    val name: String,
    val notes: String? = null,
    val type: String,
    @SerialName("category_id") val categoryId: String? = null,
    @SerialName("sort_order") val sortOrder: Double,
    @SerialName("links") val links: List<ItemLink> = emptyList(),
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)
