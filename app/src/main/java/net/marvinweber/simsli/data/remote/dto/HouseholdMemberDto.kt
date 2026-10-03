package net.marvinweber.simsli.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HouseholdMemberDto(
    val id: String,
    @SerialName("household_id") val householdId: String,
    @SerialName("user_id") val userId: String,
    val role: String,
    val email: String? = null,
    val name: String? = null,
    @SerialName("joined_at") val joinedAt: String,
    @SerialName("updated_at") val updatedAt: String? = null
)
