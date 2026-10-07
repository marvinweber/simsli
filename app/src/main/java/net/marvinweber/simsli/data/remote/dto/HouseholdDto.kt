package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HouseholdDto(
    val id: String,
    val name: String,
    // Non-null with "" default: explicitNulls = false would drop a null on encode,
    // making "clear icon" unrepresentable on push. "" = clear (server stores NULL).
    @SerialName("icon") val icon: String = "",
    @SerialName("created_at") val createdAt: String,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)
