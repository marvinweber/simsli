package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ListEntryDto(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("item_id") val itemId: String,
    val quantity: Double? = null,
    val unit: String? = null,
    val comment: String? = null,
    val done: Boolean,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("created_by") val createdBy: String? = null
)
